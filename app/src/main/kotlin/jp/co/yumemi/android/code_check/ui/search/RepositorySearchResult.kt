package jp.co.yumemi.android.code_check.ui.search

import jp.co.yumemi.android.code_check.model.RepositoryItem

/**
 * リポジトリ検索の結果。
 *
 * 該当0件という正常な結果と、通信・HTTPエラー・レスポンスの解析の失敗を区別するために使う。
 */
sealed interface RepositorySearchResult {
    /**
     * 検索が成功した状態。
     *
     * @property items APIが返した順序のままの検索結果。該当がなければ空のリスト
     */
    data class Success(
        val items: List<RepositoryItem>,
    ) : RepositorySearchResult

    /**
     * 通信、HTTPエラー、レスポンスの解析のいずれかで結果を取得できなかった状態。
     *
     * @property reason 利用者へ伝える内容を選ぶための分類
     */
    data class Failure(
        val reason: FailureReason,
    ) : RepositorySearchResult

    /**
     * 検索が失敗した理由。
     *
     * 利用者が次に取れる行動が変わるものだけを分ける。
     * 表示する文言の決定は画面側の責務とし、ここでは分類だけを持つ。
     */
    enum class FailureReason {
        /** 接続できなかった、通信の途中で失敗した、またはタイムアウトした。 */
        NETWORK,

        /** GitHub APIの利用が一時的に制限されている。 */
        RATE_LIMIT,

        /**
         * 要求がGitHubに受け付けられなかった。
         *
         * 検索条件が不正な場合と、要求が過剰な場合のどちらもあり得るため、原因は特定しない。
         */
        REQUEST_REJECTED,

        /** サーバー側のエラー応答を受け取った。 */
        SERVER,

        /** レスポンスが想定の形式ではなく、解析できなかった。 */
        RESPONSE_FORMAT,

        /** 上記のいずれにも分類できなかった。 */
        UNKNOWN,
    }
}
