/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

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
import io.ktor.http.isSuccess
import jp.co.yumemi.android.code_check.MainActivity.Companion.lastSearchDate
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
     * 成功した場合のみ[MainActivity.lastSearchDate]を更新する。
     * 通信の失敗、HTTPエラー、レスポンスが想定の形式でない場合は[RepositorySearchResult.Failure]を返す。
     * それ以外の例外は呼び出し元へ伝播する。
     *
     * @param query GitHubのリポジトリ検索APIの`q`パラメータに渡す検索条件
     * @return 成功時は検索結果、失敗時は[RepositorySearchResult.Failure]
     */
    fun searchRepositories(query: String): RepositorySearchResult =
        runBlocking {
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
                                RepositorySearchResult.Failure
                            } else {
                                val repositories =
                                    responseParser.parse(response.body<String>())

                                lastSearchDate = Date()

                                RepositorySearchResult.Success(repositories)
                            }
                        } catch (e: IOException) {
                            Log.w(TAG, "検索の通信に失敗しました", e)
                            RepositorySearchResult.Failure
                        } catch (e: JSONException) {
                            Log.w(TAG, "検索結果の解析に失敗しました", e)
                            RepositorySearchResult.Failure
                        }
                    }
                }.await()
        }
}
