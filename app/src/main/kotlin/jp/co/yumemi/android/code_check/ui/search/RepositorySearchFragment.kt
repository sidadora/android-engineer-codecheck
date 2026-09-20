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
import jp.co.yumemi.android.code_check.ui.common.NotificationDialogHost
import kotlinx.coroutines.launch

/** 通知の種類によらず、共通のタグで重複表示を防ぐ。 */
private const val NOTIFICATION_DIALOG_TAG = "searchNotification"

/** 通常のEnterまたはテンキーのEnterかを判定する。 */
private val enterKeyCodes = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

private fun KeyEvent.isEnterKey(): Boolean = keyCode in enterKeyCodes

/** 通知に対応するダイアログのタイトルのリソースIDを返す。 */
@StringRes
private fun SearchNotification.titleRes(): Int =
    when (this) {
        is SearchNotification.InputRequired -> R.string.search_input_required_title
        is SearchNotification.SearchFailed -> R.string.search_error_title
    }

/** 通知に対応するダイアログの本文のリソースIDを返す。 */
@StringRes
private fun SearchNotification.messageRes(): Int =
    when (this) {
        is SearchNotification.InputRequired -> R.string.search_input_required_message
        is SearchNotification.SearchFailed -> reason.messageRes()
    }

/** 失敗理由に対応する案内文のリソースIDを返す。 */
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
 * 検索画面の入力・表示・画面遷移を担当する。
 *
 * 検索処理と状態管理は[RepositorySearchViewModel]へ委譲する。
 */
class RepositorySearchFragment : Fragment(R.layout.fragment_repository_search) {
    private val viewModel: RepositorySearchViewModel by viewModels {
        RepositorySearchViewModel.Factory
    }

    private var dialogHost: NotificationDialogHost? = null

    /**
     * 一覧と検索入力を設定し、検索状態の購読と通知ダイアログの監視を開始する。
     *
     * @param view 検索画面のルートView
     * @param savedInstanceState 再生成時に渡される保存状態。初回生成時はnull
     */
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

        val host = NotificationDialogHost(childFragmentManager, NOTIFICATION_DIALOG_TAG)
        dialogHost = host
        host.start { evaluateNotification(viewModel.uiState.value.notification) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(binding, adapter, state.content)
                    evaluateNotification(state.notification)
                }
            }
        }
    }

    /**
     * Viewの破棄に合わせて通知ダイアログの監視を終了し、Hostへの参照を解放する。
     */
    override fun onDestroyView() {
        dialogHost?.stop()
        dialogHost = null
        super.onDestroyView()
    }

    /**
     * 検索状態に応じて、ローディング・該当なしメッセージ・一覧の表示を更新する。
     *
     * 一覧には成功時の検索結果だけを表示し、それ以外の状態では空にする。
     *
     * @param binding 表示を更新する検索画面のView Binding
     * @param adapter 検索結果を一覧へ反映するアダプター
     * @param content 現在の検索状態
     */
    private fun render(
        binding: FragmentRepositorySearchBinding,
        adapter: RepositoryListAdapter,
        content: SearchContent,
    ) {
        binding.loadingIndicator.isVisible = content is SearchContent.Loading
        binding.emptyResultText.isVisible = content is SearchContent.Empty

        adapter.submitList((content as? SearchContent.Success)?.items.orEmpty())
    }

    /**
     * ダイアログの表示状況に応じて、通知の表示要求と消費を行う。
     *
     * 同じ通知が表示中・予約中の場合、または表示要求を発行できた場合に通知を消費する。
     * 別の通知が表示中・予約中の場合や、表示要求を発行できない場合は保留する。
     * 保留した通知は、ダイアログの除去後や画面状態の再購読時に再評価する。
     *
     * @param notification 表示を検討する通知。nullの場合は表示要求を行わない
     */
    private fun evaluateNotification(notification: SearchNotification?) {
        val host = dialogHost ?: return
        val occupiedId = host.occupiedId()
        if (notification == null) return

        when {
            // 同じ通知が表示中・予約中なら処理済みとして扱う。
            occupiedId == notification.id -> viewModel.onNotificationShown(notification.id)

            // 別の通知が表示中・予約中の間は消費しない。除去されたときに再評価する。
            occupiedId != null -> Unit

            // 表示を要求できたときだけ消費する。見送った場合は保持したままにする。
            host.show(
                id = notification.id,
                titleRes = notification.titleRes(),
                messageRes = notification.messageRes(),
            ) -> viewModel.onNotificationShown(notification.id)

            else -> Unit
        }
    }

    /**
     * 選択したリポジトリと検索結果の取得日時を渡し、詳細画面へ遷移する。
     *
     * 検索が成功した状態で、現在表示中の画面が検索画面の場合にのみ遷移する。
     *
     * @param item 一覧で選択されたリポジトリ
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
