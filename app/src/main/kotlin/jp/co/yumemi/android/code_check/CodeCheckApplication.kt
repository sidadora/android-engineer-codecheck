package jp.co.yumemi.android.code_check

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// ISRG Root X1がシステムの信頼ストアへ収録されたのはAndroid 7.1.1（API 25）から。
// これ以下では収録されないため、画像取得のときだけ信頼アンカーとして補う。
private const val MAX_SDK_WITHOUT_ISRG_ROOT_X1 = Build.VERSION_CODES.N

/**
 * アプリ全体で共有する[ImageLoader]を提供する。
 *
 * [ImageLoader]はメモリ・ディスクキャッシュと接続プールを持つため、1つだけ生成して共有する。
 */
class CodeCheckApplication :
    Application(),
    ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient { buildImageOkHttpClient() }
            .build()

    /**
     * 画像取得に使う[OkHttpClient]を作る。
     *
     * オーナーアイコンの配信元はISRG Root X1を信頼アンカーとする証明書を使う。
     * 収録前のAndroidではシステムの信頼ストアだけでは検証できないため、
     * そのバージョンに限りアンカーを追加する。
     * システムの証明書による検証とOkHttp既定のホスト名検証は変更しない。
     *
     * この設定は画像取得のクライアント全体に適用され、特定のホストには限定していない。
     * 検索APIの通信はKtorが別のクライアントで行うため、この設定の影響を受けない。
     */
    private fun buildImageOkHttpClient(): OkHttpClient {
        if (Build.VERSION.SDK_INT > MAX_SDK_WITHOUT_ISRG_ROOT_X1) {
            return OkHttpClient()
        }

        // システムの証明書を保ったまま、不足するアンカーだけを足す。
        val certificates =
            HandshakeCertificates
                .Builder()
                .addPlatformTrustedCertificates()
                .addTrustedCertificate(readIsrgRootX1())
                .build()

        return OkHttpClient
            .Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .build()
    }

    /**
     * 同梱したISRG Root X1を読み出す。
     *
     * APKに含めた資源のため、失敗した場合はビルドの不備とみなして例外を伝播させる。
     * 検証を緩める代替経路は用意しない。
     */
    private fun readIsrgRootX1(): X509Certificate =
        resources.openRawResource(R.raw.isrg_root_x1).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
}
