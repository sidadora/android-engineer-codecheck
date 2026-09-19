/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.yumemi.android.code_check.R
import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.databinding.FragmentRepositorySearchBinding
import jp.co.yumemi.android.code_check.model.RepositoryItem
import jp.co.yumemi.android.code_check.ui.common.AlertDialogFragment
import kotlinx.coroutines.launch

// 通知ダイアログのタグ。同時に表示するのは1件だけなので、種類ごとには分けない。
private const val NOTIFICATION_DIALOG_TAG = "searchNotification"

/** Enterとして扱うキーコード。テンキーのEnterも同じ操作として扱う。 */
private val enterKeyCodes = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

private fun KeyEvent.isEnterKey(): Boolean = keyCode in enterKeyCodes

@StringRes
private fun SearchNotification.titleRes(): Int =
    when (this) {
        is SearchNotification.InputRequired -> R.string.search_input_required_title
        is SearchNotification.SearchFailed -> R.string.search_error_title
    }

@StringRes
private fun SearchNotification.messageRes(): Int =
    when (this) {
        is SearchNotification.InputRequired -> R.string.search_input_required_message
        is SearchNotification.SearchFailed -> reason.messageRes()
    }

/**
 * 失敗の理由に対応する、利用者向けの文言を返す。
 *
 * 次に取れる行動が伝わることを優先し、状態コードなどの内部の情報は含めない。
 */
@StringRes
private fun FailureReason.messageRes(): Int =
    when (this) {
        FailureReason.NETWORK -> R.string.search_failure_network_message
        FailureReason.RATE_LIMIT -> R.string.search_failure_rate_limit_message
        FailureReason.REQUEST_REJECTED -> R.string.search_failure_request_rejected_message
        FailureReason.SERVER -> R.string.search_failure_server_message
        FailureReason.RESPONSE_FORMAT -> R.string.search_failure_response_format_message
        FailureReason.UNKNOWN -> R.string.search_failure_message
    }

/**
 * GitHubのリポジトリをキーワードで検索し、結果を一覧表示する画面。
 *
 * 入力イベントの受け取り、状態の描画、ダイアログ、画面遷移だけを担う。
 * 検索の実行と状態の保持は[RepositorySearchViewModel]が持つ。
 */
class RepositorySearchFragment : Fragment(R.layout.fragment_repository_search) {
    private val viewModel: RepositorySearchViewModel by viewModels {
        RepositorySearchViewModel.Factory
    }

    /**
     * 表示を要求済みで、まだFragmentManagerへ追加されていない通知の識別子。
     *
     * `show`のコミットは非同期のため、追加が完了するまで`findFragmentByTag`では拾えない。
     * その隙間の重複要求を防ぐ。Viewの寿命で保持し、追加完了・ダイアログ除去・View破棄で解除する。
     */
    private var pendingNotificationId: String? = null

    private var dialogRemovalCallbacks: FragmentManager.FragmentLifecycleCallbacks? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentRepositorySearchBinding.bind(view)
        val layoutManager = LinearLayoutManager(requireContext())
        val adapter =
            RepositoryListAdapter(
                object : RepositoryListAdapter.OnItemClickListener {
                    override fun onItemClick(item: RepositoryItem) {
                        navigateToRepositoryDetail(item)
                    }
                },
            )

        binding.repositoryListView.also {
            it.layoutManager = layoutManager
            it.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))
            it.adapter = adapter
        }

        binding.searchInputText.setOnEditorActionListener { editText, actionId, event ->
            when {
                // Android 14以降は単一行入力のEnterキーでもアクションIDが渡るため、
                // IMEの検索操作とEnterキーはイベントの有無で判別する。
                event == null -> {
                    val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                    if (isSearchAction) viewModel.search(editText.text.toString())
                    isSearchAction
                }
                // Enterキーは押下・リピート・離すのそれぞれで呼ばれるため、最初の押下だけ検索する。
                // 残りも同じ操作の一部として消費し、二重実行と改行の入力を防ぐ。
                event.isEnterKey() -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        viewModel.search(editText.text.toString())
                    }
                    true
                }

                else -> false
            }
        }

        observeDialogRemoval()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(binding, adapter, state.content)
                    evaluateNotification(state.notification)
                }
            }
        }
    }

    override fun onDestroyView() {
        dialogRemovalCallbacks?.let(childFragmentManager::unregisterFragmentLifecycleCallbacks)
        dialogRemovalCallbacks = null
        pendingNotificationId = null
        super.onDestroyView()
    }

    private fun render(
        binding: FragmentRepositorySearchBinding,
        adapter: RepositoryListAdapter,
        content: SearchContent,
    ) {
        binding.loadingIndicator.isVisible = content is SearchContent.Loading
        binding.emptyResultText.isVisible = content is SearchContent.Empty
        // 実行中・失敗・未検索では一覧を空にし、前回の結果を残さない。
        adapter.submitList((content as? SearchContent.Success)?.items.orEmpty())
    }

    /**
     * ダイアログがFragmentManagerから外れたときに、保留中の通知を再評価する。
     *
     * 肯定ボタンや`onCancel`の結果は除去より前に届くため、それを合図にすると
     * まだ表示中だと誤判定する。除去の完了を検知できる`onFragmentDetached`を使う。
     */
    private fun observeDialogRemoval() {
        val callbacks =
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDetached(
                    fragmentManager: FragmentManager,
                    fragment: Fragment,
                ) {
                    if (fragment !is AlertDialogFragment) return
                    if (fragment.tag != NOTIFICATION_DIALOG_TAG) return

                    // 除去されたダイアログの通知だけを解除し、別の予約は残す。
                    if (fragment.notificationId == pendingNotificationId) {
                        pendingNotificationId = null
                    }
                    evaluateNotification(viewModel.uiState.value.notification)
                }
            }
        childFragmentManager.registerFragmentLifecycleCallbacks(callbacks, false)
        dialogRemovalCallbacks = callbacks
    }

    private fun evaluateNotification(notification: SearchNotification?) {
        val shownId =
            (childFragmentManager.findFragmentByTag(NOTIFICATION_DIALOG_TAG) as? AlertDialogFragment)
                ?.notificationId
        // 追加が完了したら予約を解除する。
        if (shownId != null && shownId == pendingNotificationId) pendingNotificationId = null

        if (notification == null) return
        val occupiedId = shownId ?: pendingNotificationId

        when {
            // 同じ通知が表示中・予約中なら処理済みとして扱う。
            occupiedId == notification.id -> viewModel.onNotificationShown(notification.id)

            // 別の通知が表示中・予約中の間は消費しない。除去されたときに再評価する。
            occupiedId != null -> Unit

            else -> showNotification(notification)
        }
    }

    private fun showNotification(notification: SearchNotification) {
        val issued =
            AlertDialogFragment.show(
                fragmentManager = childFragmentManager,
                tag = NOTIFICATION_DIALOG_TAG,
                titleRes = notification.titleRes(),
                messageRes = notification.messageRes(),
                notificationId = notification.id,
            )
        // 表示を要求できたときだけ消費する。状態保存後などで見送った場合は保持したままにする。
        if (!issued) return

        pendingNotificationId = notification.id
        viewModel.onNotificationShown(notification.id)
    }

    /**
     * 詳細画面へ遷移する。
     *
     * 表示中の一覧を取得した時刻も渡し、後続の検索が完了しても変わらないようにする。
     */
    private fun navigateToRepositoryDetail(item: RepositoryItem) {
        val content = viewModel.uiState.value.content
        if (content !is SearchContent.Success) return

        val navController = findNavController()
        // 遷移元にいるときだけ遷移し、連続タップによる重複と不正なaction指定を防ぐ。
        if (navController.currentDestination?.id != R.id.repository_search_fragment) return

        val action =
            RepositorySearchFragmentDirections
                .actionRepositorySearchFragmentToRepositoryDetailFragment(
                    repositoryItem = item,
                    searchedAtMillis = content.searchedAtMillis,
                )
        navController.navigate(action)
    }
}
