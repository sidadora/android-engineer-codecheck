package jp.co.yumemi.android.code_check.data

import android.util.Log
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException

private const val TAG = "GitHubRepository"

/**
 * 画面が使うデータ取得の窓口。
 *
 * 画面側はこのインターフェースだけに依存するため、テストでは差し替えて
 * 通信なしに状態遷移を確認できる。
 */
interface GitHubRepository {
    /**
     * [query]でリポジトリを検索する。
     *
     * @return 成功時はAPIが返した順序のままの一覧。該当がなければ空のリスト
     */
    suspend fun searchRepositories(query: String): FetchResult<List<RepositoryItem>>
}

/**
 * [GitHubApi]で取得し、[RepositoryResponseParser]で変換する実装。
 *
 * @property parseDispatcher 解析を実行するディスパッチャ。メインスレッドを占有しないために使う
 */
class DefaultGitHubRepository(
    private val api: GitHubApi,
    private val parser: RepositoryResponseParser,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GitHubRepository {
    override suspend fun searchRepositories(query: String): FetchResult<List<RepositoryItem>> =
        when (val response = api.searchRepositories(query)) {
            is FetchResult.Failure -> response
            is FetchResult.Success ->
                withContext(parseDispatcher) {
                    try {
                        FetchResult.Success(parser.parse(response.value))
                    } catch (e: JSONException) {
                        // 例外メッセージは本文を含めない内容へ差し替え済みのため、そのまま記録する。
                        Log.w(TAG, "検索結果の解析に失敗しました", e)
                        FetchResult.Failure(FailureReason.RESPONSE_FORMAT)
                    }
                }
        }
}
