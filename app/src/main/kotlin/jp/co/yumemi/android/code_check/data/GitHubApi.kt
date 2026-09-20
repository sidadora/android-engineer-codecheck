/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
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
 * HTTPステータスとヘッダーから失敗理由を分類する。
 *
 * 403はレート制限を示すヘッダーも確認し、422は原因を断定せず要求拒否として扱う。
 * レスポンス本文は判定に使わない。
 *
 * @return 分類した失敗理由。判別できない場合は[FailureReason.UNKNOWN]
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
 * 403レスポンスのヘッダーに、レート制限を示す情報があるかを判定する。
 *
 * @return 残り要求数が0、またはRetry-Afterがある場合はtrue。
 *  falseでもレート制限ではないと断定できない
 */
private fun HttpResponse.isRateLimited(): Boolean {
    val remainingRequests = headers["x-ratelimit-remaining"]
    val retryAfter = headers[HttpHeaders.RetryAfter]
    return remainingRequests == "0" || retryAfter != null
}

/**
 * GitHub APIへ通信し、成功時のレスポンス本文または失敗理由を返す。
 *
 * 本文の解析は行わず、キャンセルは呼び出し元へ伝播させる。
 *
 * @property httpClient 通信に使うクライアント。生成側が寿命を管理し、
 *   HTTPエラーをステータスで判定できる設定にする
 */
class GitHubApi(
    private val httpClient: HttpClient,
) {
    /**
     * 検索条件を指定してリポジトリ検索APIを呼び出す。
     *
     * @param query qパラメータへ加工せず渡す検索条件。
     *  空文字・空白のみの入力は呼び出し元で拒否すること
     * @return 成功時は未解析のレスポンス本文、失敗時は通信またはHTTPエラーの分類
     */
    suspend fun searchRepositories(query: String): FetchResult<String> {
        val url = "$BASE_URL/search/repositories"
        return get(url) { parameter("q", query) }
    }

    /**
     * GETリクエストを送り、レスポンス本文または失敗理由を返す。
     *
     * IOExceptionは通信失敗へ変換し、キャンセルやその他の例外は伝播させる。
     *
     * @param url リクエスト先のURL
     * @param configure クエリパラメータなど、リクエスト固有の設定
     * @return 成功時は本文、失敗時は通信またはHTTPエラーの分類
     */
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

            // HTTPエラーを例外化しないクライアント設定を前提に、ステータスで成否を判定する。
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
