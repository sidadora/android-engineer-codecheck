package jp.co.yumemi.android.code_check.ui.common

import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * 通知ダイアログの表示状態を管理する。
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
     * 肯定ボタンや`onCancel`の結果は除去より前に届くため、それを合図にすると
     * まだ表示中だと誤判定する。除去の完了を検知できる`onFragmentDetached`を使う。
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
     * 表示中または表示予約中の通知の識別子を返す。
     *
     * 追加が完了していれば、あわせて予約を解除する。
     */
    @MainThread
    fun occupiedId(): String? {
        val shownId =
            (fragmentManager.findFragmentByTag(tag) as? AlertDialogFragment)?.notificationId
        if (shownId != null && shownId == pendingId) pendingId = null
        return shownId ?: pendingId
    }

    /**
     * 通知ダイアログの表示を要求する。
     *
     * @return 表示を要求できた場合はtrue。状態保存後などで見送った場合はfalse。
     *   呼び出し元はtrueのときだけ通知を消費すること
     */
    @MainThread
    fun show(
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
