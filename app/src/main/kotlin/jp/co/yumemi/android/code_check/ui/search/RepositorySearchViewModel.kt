/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import jp.co.yumemi.android.code_check.MainActivity.Companion.lastSearchDate
import jp.co.yumemi.android.code_check.ui.search.RepositorySearchResult.FailureReason
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.io.IOException
import java.util.Date

private const val TAG = "RepositorySearch"

/**
 * GitHubのリポジトリ検索APIを呼び出し、結果を[RepositorySearchResult]として返す。
 *
 * レスポンスの解析と表示用データへの変換は[RepositoryResponseParser]に委譲する。
 *
 * @param context レスポンスの変換に使う[RepositoryResponseParser]の生成にのみ渡す
 */
class RepositorySearchViewModel(
    context: Context,
) : ViewModel() {
    private val responseParser = RepositoryResponseParser(context)

    /**
     * [query]でGitHubのリポジトリを検索し、結果を返す。
     *
     * `runBlocking`を使っているため、結果を受け取るまで呼び出し元のスレッドをブロックする。
     * 成功した場合のみ[lastSearchDate]を更新する。
     * 通信の失敗、HTTPエラー、レスポンスが想定の形式でない場合は[RepositorySearchResult.Failure]を返し、
     * 利用者へ伝える内容を選べるよう[RepositorySearchResult.FailureReason]で理由を区別する。
     * それ以外の例外は呼び出し元へ伝播する。
     *
     * 空文字や空白のみの条件はAPIがエラーとして返すため、呼び出し前に検証しておくこと。
     * この関数は[query]を正規化せず、渡された内容をそのまま`q`パラメータへ送る。
     *
     * @param query GitHubのリポジトリ検索APIの`q`パラメータに渡す検索条件
     * @return 成功時は検索結果、失敗時は理由を伴う[RepositorySearchResult.Failure]
     */
    fun searchRepositories(query: String): RepositorySearchResult =
        runBlocking {
            // Todo : Issue #4,#6で検索処理をライフサイクルに対応するスコープへ移す。
            return@runBlocking GlobalScope
                .async {
                    // クライアントを使うコルーチン内で生成し、useでどの経路でも終了処理を行う。
                    HttpClient(Android).use { client ->
                        try {
                            val response: HttpResponse =
                                client.get(
                                    "https://api.github.com/search/repositories",
                                ) {
                                    header("Accept", "application/vnd.github.v3+json")
                                    parameter("q", query)
                                }

                            // expectSuccessは既定でfalseのため、HTTPエラーでも例外にならず本文が返る。
                            if (!response.status.isSuccess()) {
                                Log.w(TAG, "検索APIがエラーを返しました: ${response.status}")
                                RepositorySearchResult.Failure(response.toFailureReason())
                            } else {
                                val repositories =
                                    responseParser.parse(response.body<String>())

                                lastSearchDate = Date()

                                RepositorySearchResult.Success(repositories)
                            }
                        } catch (e: IOException) {
                            Log.w(TAG, "検索の通信に失敗しました: ${e::class.java.simpleName}")
                            RepositorySearchResult.Failure(FailureReason.NETWORK)
                        } catch (e: JSONException) {
                            Log.w(TAG, "検索結果の解析に失敗しました", e)
                            RepositorySearchResult.Failure(FailureReason.RESPONSE_FORMAT)
                        }
                    }
                }.await()
        }
}

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
