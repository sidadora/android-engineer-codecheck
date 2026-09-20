package jp.co.yumemi.android.code_check.data

/**
 * データ取得の成功値または失敗理由を表す。
 *
 * 該当0件は成功として扱い、失敗と区別する。
 *
 * @param T 成功時に返すデータの型
 */
sealed interface FetchResult<out T> {

    /**
     * データ取得に成功した結果。
     *
     * @property value 取得したデータ
     */
    data class Success<out T>(
        val value: T,
    ) : FetchResult<T>

    /**
     * 通信、HTTPエラー、レスポンスの解析のいずれかで取得できなかった状態。
     *
     * @property reason 利用者へ伝える内容を選ぶための分類
     */
    data class Failure(
        val reason: FailureReason,
    ) : FetchResult<Nothing>
}

/**
 * データ取得の失敗理由。
 *
 * 表示文言はUI側で決定し、ここでは分類だけを定義する。
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
