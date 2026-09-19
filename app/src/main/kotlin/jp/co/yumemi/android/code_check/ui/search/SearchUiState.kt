package jp.co.yumemi.android.code_check.ui.search

import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.model.RepositoryItem

/**
 * 検索画面の状態。
 *
 * @property content 一覧まわりの表示内容
 * @property notification 未表示の通知。保持するのは最新の1件だけ
 */
data class SearchUiState(
    val content: SearchContent = SearchContent.NotSearched,
    val notification: SearchNotification? = null,
)

/**
 * 一覧まわりの表示内容。
 *
 * 初期表示と失敗を「該当0件」と混同しないよう、それぞれ別の状態として持つ。
 */
sealed interface SearchContent {
    /** まだ一度も検索していない。 */
    data object NotSearched : SearchContent

    /**
     * 検索の実行中。初回・再検索のどちらもこの状態になる。
     *
     * @property query 実行中の検索条件
     */
    data class Loading(
        val query: String,
    ) : SearchContent

    /**
     * 1件以上取得できた状態。
     *
     * @property items APIが返した順序のままの一覧
     * @property searchedAtMillis この一覧を取得した時刻。詳細画面へそのまま渡す
     */
    data class Success(
        val items: List<RepositoryItem>,
        val searchedAtMillis: Long,
    ) : SearchContent

    /**
     * 取得できたが該当が0件だった状態。
     *
     * @property query 0件だった検索条件
     */
    data class Empty(
        val query: String,
    ) : SearchContent

    /** 取得に失敗した状態。一覧は空にする。 */
    data object Failed : SearchContent
}

/**
 * 利用者へダイアログで伝える通知。
 *
 * [id]は表示中・表示予約中のダイアログと照合するために使う。
 * プロセス再生成で復元されたダイアログと新しい通知が衝突しないよう、
 * 連番ではなく再起動をまたいで重複しない値を使う。
 */
sealed interface SearchNotification {
    val id: String

    /** 検索条件が未入力だった。通信は行っていない。 */
    data class InputRequired(
        override val id: String,
    ) : SearchNotification

    /**
     * 検索に失敗した。
     *
     * @property reason 文言を選ぶための分類
     */
    data class SearchFailed(
        override val id: String,
        val reason: FailureReason,
    ) : SearchNotification
}
