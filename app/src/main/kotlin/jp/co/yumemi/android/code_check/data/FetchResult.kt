package jp.co.yumemi.android.code_check.data

/**
 * データ取得の結果。
 *
 * 取得できた値と、取得できなかった理由を区別する。
 * 該当0件のような正常な結果は[Success]の値として表し、失敗とは扱わない。
 */
sealed interface FetchResult<out T> {
    /** 取得できた状態。 */
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
 * 取得に失敗した理由。
 *
 * 利用者が次に取れる行動が変わるものだけを分ける。
 * 表示する文言の決定は画面側の責務とし、ここでは分類だけを持つ。
 * 検索と詳細取得の双方から参照する。
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
