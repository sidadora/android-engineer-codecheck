/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.common

import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * 通知ダイアログの表示状態を管理し、表示要求と消費の可否を判断する。
 *
 * 通知の型は画面ごとに持つため、ここでは識別子と文字列リソースだけを扱う。
 * 1つのタグにつき同時に表示するダイアログは1つとし、別の通知が出ている間は表示しない。
 *
 * すべてメインスレッドから呼ぶこと。
 *
 * @property fragmentManager ダイアログを追加する対象。画面のchildFragmentManagerを想定する
 * @property tag この画面の通知ダイアログを識別するタグ
 */
class NotificationDialogHost(
    private val fragmentManager: FragmentManager,
    private val tag: String,
) {
    /**
     * 表示を検討する通知。
     *
     * 画面ごとの通知の型をここが知らずに済むよう、表示に必要な情報だけを受け取る。
     *
     * @property id 通知を識別するID
     * @property titleRes タイトルの文字列リソースID
     * @property messageRes 本文の文字列リソースID
     */
    data class Request(
        val id: String,
        @param:StringRes val titleRes: Int,
        @param:StringRes val messageRes: Int,
    )

    /**
     * 表示を要求済みで、まだFragmentManagerへ追加されていない通知の識別子。
     *
     * `show`のコミットは非同期のため、追加が完了するまで`findFragmentByTag`では拾えない。
     * その隙間の重複要求を防ぐ。
     */
    private var pendingId: String? = null

    private var removalCallbacks: FragmentManager.FragmentLifecycleCallbacks? = null

    /**
     * ダイアログの除去を監視する。Viewの生成時に呼び、破棄時に[stop]すること。
     *
     * ボタン操作やキャンセルのコールバックは除去より前に呼ばれるため、それを合図にするとまだ表示中だと誤判定する。
     * 除去の完了を検知できる`onFragmentDetached`を使う。
     *
     * @param onRemoved 通知ダイアログが除去されたときに呼ばれる。保留中の通知を再評価する
     */
    @MainThread
    fun start(onRemoved: () -> Unit) {
        val callbacks =
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDetached(
                    fragmentManager: FragmentManager,
                    fragment: Fragment,
                ) {
                    if (fragment !is AlertDialogFragment || fragment.tag != tag) return

                    // 除去されたダイアログの通知だけを解除し、別の予約は残す。
                    if (fragment.notificationId == pendingId) pendingId = null
                    onRemoved()
                }
            }
        fragmentManager.registerFragmentLifecycleCallbacks(callbacks, false)
        removalCallbacks = callbacks
    }

    /** 監視を終了し、予約を破棄する。 */
    @MainThread
    fun stop() {
        removalCallbacks?.let(fragmentManager::unregisterFragmentLifecycleCallbacks)
        removalCallbacks = null
        pendingId = null
    }

    /**
     * ダイアログの表示状況に応じて、通知の表示要求と消費を行う。
     *
     * 同じ通知が表示中・予約中の場合、または表示要求を発行できた場合に[onShown]を呼ぶ。
     * 別の通知が表示中・予約中の場合や、表示要求を発行できない場合は何もしない。
     * 保留した通知は、ダイアログの除去後や画面状態の再購読時に再評価すること。
     *
     * @param request 表示を検討する通知。nullの場合は表示要求を行わず、確定した予約の解除だけ行う
     * @param onShown 通知を消費するときに、その通知のIDを渡して呼ぶ
     */
    @MainThread
    fun evaluate(
        request: Request?,
        onShown: (String) -> Unit,
    ) {
        // 通知の有無にかかわらず、追加が完了した予約は解除する。
        releaseSettledReservation()
        if (request == null) return

        val occupiedId = occupiedId()
        when {
            // 同じ通知が表示中・予約中なら処理済みとして扱う。
            occupiedId == request.id -> onShown(request.id)

            // 別の通知が表示中・予約中の間は消費しない。除去されたときに再評価する。
            occupiedId != null -> Unit

            // 表示を要求できたときだけ消費する。見送った場合は保持したままにする。
            show(request.id, request.titleRes, request.messageRes) -> onShown(request.id)

            else -> Unit
        }
    }

    /** FragmentManagerが保持している通知ダイアログの識別子。 */
    private fun shownId(): String? {
        val dialog = fragmentManager.findFragmentByTag(tag) as? AlertDialogFragment
        return dialog?.notificationId
    }

    /** 表示中または表示予約中の通知の識別子。状態は変更しない。 */
    @MainThread
    private fun occupiedId(): String? = shownId() ?: pendingId

    /**
     * 追加が完了した予約を解除する。
     *
     * 追加が完了すればFragmentManagerから識別子を読めるため、予約を持ち続ける必要がない。
     * 解除が起きるのは表示中の識別子がある場合だけで、そのとき[occupiedId]はその識別子を返す。
     * 戻り値は解除の前後で変わらないため、呼ぶ順序に依存しない。
     */
    @MainThread
    private fun releaseSettledReservation() {
        val shownId = shownId()
        if (shownId != null && shownId == pendingId) pendingId = null
    }

    /**
     * 通知ダイアログの表示を要求する。
     *
     * @param id 表示対象の通知の識別子
     * @param titleRes タイトルの文字列リソースID
     * @param messageRes 本文の文字列リソースID
     * @return 表示を要求できた場合はtrue。状態保存後などで見送った場合はfalse
     */
    @MainThread
    private fun show(
        id: String,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
    ): Boolean {
        val issued =
            AlertDialogFragment.show(
                fragmentManager = fragmentManager,
                tag = tag,
                titleRes = titleRes,
                messageRes = messageRes,
                notificationId = id,
            )
        if (issued) pendingId = id
        return issued
    }
}
