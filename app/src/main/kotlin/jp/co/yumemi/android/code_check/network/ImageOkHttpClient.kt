/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.network

import android.content.res.Resources
import android.os.Build
import jp.co.yumemi.android.code_check.R
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// API 24以下の端末向けに、画像取得でISRG Root X1を追加する。
private const val MAX_SDK_WITHOUT_ISRG_ROOT_X1 = Build.VERSION_CODES.N

/**
 * 画像取得用のHTTPクライアントを生成する。
 *
 * API 24以下では、システムCAに加えて同梱のISRG Root X1を信頼する。
 * ホスト名検証はOkHttpの既定設定を維持する。
 *
 * 追加の信頼設定は画像取得クライアント全体に適用し、特定ホストには限定しない。
 * 別クライアントを使う検索APIの通信には適用しない。
 *
 * @param resources 同梱した証明書を読み込むためのリソース
 * @return API 24以下では追加CAを設定したクライアント、それ以外では既定設定のクライアント
 */
fun buildImageOkHttpClient(resources: Resources): OkHttpClient {
    if (Build.VERSION.SDK_INT > MAX_SDK_WITHOUT_ISRG_ROOT_X1) {
        return OkHttpClient()
    }

    // システムの証明書を保ったまま、ISRG Root X1を追加する。
    val certificates =
        HandshakeCertificates
            .Builder()
            .addPlatformTrustedCertificates()
            .addTrustedCertificate(readIsrgRootX1(resources))
            .build()

    return OkHttpClient
        .Builder()
        .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
        .build()
}

/**
 * 同梱したISRG Root X1をX.509証明書として読み込む。
 *
 * 読み込みや解析の失敗は呼び出し元へ伝播させ、検証を緩める代替処理は行わない。
 *
 * @param resources 同梱した証明書を読み込むためのリソース
 * @return 同梱したISRG Root X1の証明書
 */
private fun readIsrgRootX1(resources: Resources): X509Certificate =
    resources.openRawResource(R.raw.isrg_root_x1).use { input ->
        CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
    }
