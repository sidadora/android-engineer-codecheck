/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.detail

import jp.co.yumemi.android.code_check.data.FailureReason

/**
 * 詳細画面の表示と通知に使う状態。
 *
 * 名前・言語・Star数などはNavigationの引数で受け取るため、追加で取得する値だけを持つ。
 *
 * @property watcherCount 購読者数の取得状態
 * @property notification 未消費の通知。最新の1件だけを保持し、通知がなければnull
 */
data class DetailUiState(
    val watcherCount: WatcherCountState = WatcherCountState.Loading,
    val notification: DetailNotification? = null,
)

/**
 * 購読者数の取得状態。
 *
 * 取得中と失敗を件数0と混同しないよう、それぞれ別の状態として持つ。
 */
sealed interface WatcherCountState {
    /** 取得中。生成直後はこの状態から始まる。 */
    data object Loading : WatcherCountState

    /**
     * 取得できた状態。
     *
     * @property count 購読者数。0も正常な件数として扱う
     */
    data class Success(
        val count: Long,
    ) : WatcherCountState

    /** 取得に失敗した状態。0やStar数で補完しない。 */
    data object Failed : WatcherCountState
}

/**
 * ダイアログで伝える通知。
 *
 * IDは表示中・表示予約中のダイアログとの照合に使う。
 * 発行側は、プロセス再生成で復元された通知との衝突を避けるためUUIDを使う。
 *
 * @property id 通知を識別するID
 * @property reason 表示文言を選ぶための失敗理由
 */
data class DetailNotification(
    val id: String,
    val reason: FailureReason,
)
