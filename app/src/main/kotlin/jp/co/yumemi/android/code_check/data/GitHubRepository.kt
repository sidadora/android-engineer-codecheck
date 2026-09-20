/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.data

import android.util.Log
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException

private const val TAG = "GitHubRepository"

/**
 * 検索画面が使うデータ取得の窓口。
 *
 * 呼び出し元から通信と解析の詳細を隠し、取得結果を[FetchResult]で返す。
 */
interface RepositorySearchDataSource {
    /**
     * 指定した条件でリポジトリを検索する。
     *
     * @param query 加工せずAPIへ渡す検索条件。
     * 空文字・空白のみの入力は呼び出し元で拒否すること
     *
     * @return 成功時はAPIの順序を保った一覧。
     * 該当0件は空の一覧、通信・HTTP・解析の失敗は[FetchResult.Failure]
     */
    suspend fun searchRepositories(query: String): FetchResult<List<RepositoryItem>>
}

/**
 * 詳細画面が使うデータ取得の窓口。
 *
 * 呼び出し元から通信と解析の詳細を隠し、取得結果を[FetchResult]で返す。
 */
interface RepositoryDetailDataSource {
    /**
     * [fullName]のリポジトリの購読者数を取得する。
     *
     * 検索APIが返す`watchers_count`はStar数と同じ値のため、この経路で取得する。
     *
     * @param fullName `owner/repo`形式のリポジトリ名
     *
     * @return 成功時は購読者数。0も正常な件数として返す。
     * 通信・HTTP・解析の失敗は[FetchResult.Failure]
     */
    suspend fun getSubscribersCount(fullName: String): FetchResult<Long>
}

/**
 * APIから取得した本文を解析し、画面が使う値へ変換する。
 *
 * 通信・HTTPの失敗はそのまま返し、解析時のJSONExceptionは
 * [FailureReason.RESPONSE_FORMAT]へ変換する。キャンセルは伝播させる。
 *
 * @property api 検索APIへの通信を担当するクライアント
 * @property parser レスポンスの検証とモデルへの変換を担当するパーサー
 * @property parseDispatcher 解析を実行するディスパッチャ。既定はDispatchers.Default
 */
class DefaultGitHubRepository(
    private val api: GitHubApi,
    private val parser: RepositoryResponseParser,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RepositorySearchDataSource,
    RepositoryDetailDataSource {
    override suspend fun searchRepositories(query: String): FetchResult<List<RepositoryItem>> =
        when (val response = api.searchRepositories(query)) {
            is FetchResult.Failure -> response
            is FetchResult.Success ->
                withContext(parseDispatcher) {
                    try {
                        FetchResult.Success(parser.parse(response.value))
                    } catch (e: JSONException) {
                        // パーサーの例外にはレスポンス本文や項目の値を含めないため、そのまま記録する。
                        Log.w(TAG, "検索結果の解析に失敗しました", e)
                        FetchResult.Failure(FailureReason.RESPONSE_FORMAT)
                    }
                }
        }

    override suspend fun getSubscribersCount(fullName: String): FetchResult<Long> =
        when (val response = api.getRepository(fullName)) {
            is FetchResult.Failure -> response
            is FetchResult.Success ->
                withContext(parseDispatcher) {
                    try {
                        FetchResult.Success(parser.parseSubscribersCount(response.value))
                    } catch (e: JSONException) {
                        // パーサーの例外にはレスポンス本文や項目の値を含めないため、そのまま記録する。
                        Log.w(TAG, "リポジトリ詳細の解析に失敗しました", e)
                        FetchResult.Failure(FailureReason.RESPONSE_FORMAT)
                    }
                }
        }
}
