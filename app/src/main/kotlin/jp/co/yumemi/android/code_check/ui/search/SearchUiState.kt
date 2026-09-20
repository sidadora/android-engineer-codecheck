/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.model.RepositoryItem

/**
 * 検索画面の表示と通知に使う状態。
 *
 * @property content 検索の進行状況と結果。ローディング・一覧・該当なし表示を決める
 * @property notification 未消費の通知。最新の1件だけを保持し、通知がなければnull
 */
data class SearchUiState(
    val content: SearchContent = SearchContent.NotSearched,
    val notification: SearchNotification? = null,
)

/**
 * 検索の進行状況と結果を表す状態。
 *
 * 未検索・実行中・成功・該当0件・失敗を区別する。
 */
sealed interface SearchContent {
    /** 検索中でも結果表示中でもない初期状態。復元する検索条件がない場合もこの状態になる。 */
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
     * @property searchedAtMillis 検索結果を取得した時刻。Unixエポックからのミリ秒で、詳細画面へ渡す
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
 * ダイアログで伝える通知。
 *
 * IDは表示中・表示予約中のダイアログとの照合に使う。
 * 発行側は、プロセス再生成で復元された通知との衝突を避けるためUUIDを使う。
 */
sealed interface SearchNotification {
    val id: String

    /**
     * 空文字・空白のみの入力に対して、入力を促す通知。
     *
     * この入力による検索は開始しない。既に実行中の検索は中断しない。
     *
     * @property id 通知を識別するID
     */
    data class InputRequired(
        override val id: String,
    ) : SearchNotification

    /**
     * 検索の失敗を伝える通知。
     *
     * @property id 通知を識別するID
     * @property reason 表示文言を選ぶための失敗理由
     */
    data class SearchFailed(
        override val id: String,
        val reason: FailureReason,
    ) : SearchNotification
}
