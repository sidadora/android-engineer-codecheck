package jp.co.yumemi.android.code_check

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.yumemi.android.code_check.data.DefaultGitHubRepository
import jp.co.yumemi.android.code_check.data.GitHubApi
import jp.co.yumemi.android.code_check.data.GitHubRepository
import jp.co.yumemi.android.code_check.data.RepositoryResponseParser
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

// API 24以下の端末向けに、画像取得でISRG Root X1を追加する。
private const val MAX_SDK_WITHOUT_ISRG_ROOT_X1 = Build.VERSION_CODES.N

/**
 * アプリ内で共有するAPIクライアントとRepositoryを組み立てて保持する。
 *
 * Coilには、画像取得用の通信設定を適用したImageLoaderの生成方法を提供する。
 */
class CodeCheckApplication :
    Application(),
    ImageLoaderFactory {
    /**
     * プロセスの存続中に共有するAPI通信クライアント。明示的なcloseは行わない。
     *
     * 画像取得用のクライアントとは分け、画像用の追加CA設定は適用しない。
     */
    private val httpClient: HttpClient by lazy { HttpClient(Android) }

    /** 画面が使うデータ取得の窓口。アプリ内で共有する。 */
    val gitHubRepository: GitHubRepository by lazy {
        DefaultGitHubRepository(
            api = GitHubApi(httpClient),
            parser = RepositoryResponseParser(),
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient { buildImageOkHttpClient() }
            .build()

    /**
     * 画像取得用のHTTPクライアントを生成する。
     *
     * API 24以下では、システムCAに加えて同梱のISRG Root X1を信頼する。
     * ホスト名検証はOkHttpの既定設定を維持する。
     *
     * 追加の信頼設定は画像取得クライアント全体に適用し、特定ホストには限定しない。
     * 別クライアントを使う検索APIの通信には適用しない。
     *
     * @return API 24以下では追加CAを設定したクライアント、それ以外では既定設定のクライアント
     */
    private fun buildImageOkHttpClient(): OkHttpClient {
        if (Build.VERSION.SDK_INT > MAX_SDK_WITHOUT_ISRG_ROOT_X1) {
            return OkHttpClient()
        }

        // システムの証明書を保ったまま、ISRG Root X1を追加する。
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
     * 同梱したISRG Root X1をX.509証明書として読み込む。
     *
     * 読み込みや解析の失敗は呼び出し元へ伝播させ、検証を緩める代替処理は行わない。
     *
     * @return 同梱したISRG Root X1の証明書
     */
    private fun readIsrgRootX1(): X509Certificate =
        resources.openRawResource(R.raw.isrg_root_x1).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
}
