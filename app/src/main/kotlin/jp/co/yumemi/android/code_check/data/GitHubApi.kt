package jp.co.yumemi.android.code_check.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException

private const val TAG = "GitHubApi"
private const val BASE_URL = "https://api.github.com"
private const val ACCEPT_GITHUB_JSON = "application/vnd.github.v3+json"

/**
 * HTTPエラーのレスポンスを、利用者へ伝える理由へ分類する。
 *
 * 403はレート制限以外の理由でも返るため、状態コードだけでは判別しない。
 * 422は検索条件の不正だけでなく要求が過剰な場合にも返るため、原因を特定せず
 * [FailureReason.REQUEST_REJECTED]として扱う。
 * 分類は状態コードとヘッダーだけで行い、レスポンス本文やメッセージ文字列は見ない。
 */
private fun HttpResponse.toFailureReason(): FailureReason =
    when {
        status == HttpStatusCode.TooManyRequests -> FailureReason.RATE_LIMIT
        status == HttpStatusCode.Forbidden && isRateLimited() -> FailureReason.RATE_LIMIT
        status == HttpStatusCode.UnprocessableEntity -> FailureReason.REQUEST_REJECTED
        status.value in 500..599 -> FailureReason.SERVER
        else -> FailureReason.UNKNOWN
    }

/**
 * レート制限による拒否だと、ヘッダーから判断できるかどうかを返す。
 *
 * 回数の上限に達した場合は残りの要求数が0になり、待ち時間が示される場合はRetry-Afterが付く。
 * ただしRetry-Afterは必ず付くとは限らず、ヘッダーだけでは判別できないレート制限もある。
 * 判断できない場合にレート制限と決めつけないため、この関数はfalseを返し、
 * 呼び出し元は[FailureReason.UNKNOWN]として扱う。
 */
private fun HttpResponse.isRateLimited(): Boolean {
    val remainingRequests = headers["x-ratelimit-remaining"]
    val retryAfter = headers[HttpHeaders.RetryAfter]
    return remainingRequests == "0" || retryAfter != null
}

/**
 * GitHubのREST APIを呼び出し、レスポンス本文をそのまま返す。
 *
 * 通信とHTTPの判定だけを担い、本文の解析は行わない。
 * 呼び出し元のコルーチンがキャンセルされた場合は[kotlinx.coroutines.CancellationException]を伝播させる。
 *
 * @property httpClient 呼び出しに使うクライアント。寿命の管理は生成側が持つ
 */
class GitHubApi(
    private val httpClient: HttpClient,
) {
    /**
     * リポジトリを検索する。
     *
     * @param query `q`パラメータへそのまま渡す検索条件。空文字や空白のみは呼び出し前に除くこと
     */
    suspend fun searchRepositories(query: String): FetchResult<String> {
        val url = "$BASE_URL/search/repositories"
        return get(url) { parameter("q", query) }
    }

    private suspend fun get(
        url: String,
        configure: HttpRequestBuilder.() -> Unit,
    ): FetchResult<String> =
        try {
            val response =
                httpClient.get(url) {
                    header(HttpHeaders.Accept, ACCEPT_GITHUB_JSON)
                    configure()
                }

            // expectSuccessは既定でfalseのため、HTTPエラーでも例外にならず本文が返る。
            if (response.status.isSuccess()) {
                FetchResult.Success(response.body<String>())
            } else {
                Log.w(TAG, "APIがエラーを返しました: ${response.status}")
                FetchResult.Failure(response.toFailureReason())
            }
        } catch (e: IOException) {
            // 例外メッセージにはURLが含まれ、URLには検索条件が入る。種別だけを記録する。
            Log.w(TAG, "通信に失敗しました: ${e::class.java.simpleName}")
            FetchResult.Failure(FailureReason.NETWORK)
        }
}
