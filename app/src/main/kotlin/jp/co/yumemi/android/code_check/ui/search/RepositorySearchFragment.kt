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
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.yumemi.android.code_check.R
import jp.co.yumemi.android.code_check.databinding.FragmentRepositorySearchBinding
import jp.co.yumemi.android.code_check.model.RepositoryItem
import jp.co.yumemi.android.code_check.ui.common.AlertDialogFragment
import jp.co.yumemi.android.code_check.ui.search.RepositorySearchResult.FailureReason

// 検索エラーの通知ダイアログを識別するタグ。重複表示の判定に使う。
private const val SEARCH_ERROR_DIALOG_TAG = "searchError"

// 空文字、スペースで検索した際のアラートダイアログを識別するタグ
private const val SEARCH_INPUT_REQUIRED_DIALOG_TAG = "searchInputRequired"

/**
 * GitHubのリポジトリをキーワードで検索し、結果を一覧表示する画面。
 *
 * キーボードの検索キーで検索を実行し、一覧の項目を選択すると詳細画面へ遷移する。
 */
class RepositorySearchFragment : Fragment(R.layout.fragment_repository_search) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentRepositorySearchBinding.bind(view)

        val viewModel = RepositorySearchViewModel(requireContext())

        val layoutManager = LinearLayoutManager(requireContext())

        val dividerItemDecoration =
            DividerItemDecoration(requireContext(), layoutManager.orientation)

        val adapter =
            RepositoryListAdapter(
                object : RepositoryListAdapter.OnItemClickListener {
                    override fun onItemClick(item: RepositoryItem) {
                        navigateToRepositoryDetail(item)
                    }
                },
            )

        fun search(query: String) {
            // 空・空白のみはAPIがエラーとして返すため、通信する前に入力を促す。
            // 検索を実行しないので、一覧も検索日時も変わらない。
            if (query.isBlank()) {
                showInputRequiredDialog()
                return
            }

            // 失敗時は一覧も該当なしの表示も更新せず、前回の検索結果をそのまま残す。
            when (val result = viewModel.searchRepositories(query)) {
                is RepositorySearchResult.Success -> {
                    adapter.submitList(result.items)
                    binding.emptyResultText.isVisible = result.items.isEmpty()
                }

                is RepositorySearchResult.Failure -> showSearchErrorDialog(result.reason)
            }
        }

        binding.searchInputText
            .setOnEditorActionListener { editText, actionId, event ->
                when {
                    // Android 14以降は単一行入力のEnterキーでもアクションIDが渡るため、
                    // IMEの検索操作とEnterキーはイベントの有無で判別する。
                    event == null -> {
                        val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                        if (isSearchAction) search(editText.text.toString())
                        isSearchAction
                    }
                    // Enterキーは押下・リピート・離すのそれぞれで呼ばれるため、最初の押下だけ検索する。
                    // 残りも同じ操作の一部として消費し、二重実行と改行の入力を防ぐ。
                    event.isEnterKey() -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            search(editText.text.toString())
                        }
                        true
                    }

                    else -> false
                }
            }

        binding.repositoryListView.also {
            it.layoutManager = layoutManager
            it.addItemDecoration(dividerItemDecoration)
            it.adapter = adapter
        }
    }

    /** 検索条件が未入力であることを知らせる。 */
    private fun showInputRequiredDialog() {
        showNotification(
            tag = SEARCH_INPUT_REQUIRED_DIALOG_TAG,
            titleRes = R.string.search_input_required_title,
            messageRes = R.string.search_input_required_message,
        )
    }

    /**
     * 検索が失敗したことを、原因に応じた文言で知らせる。
     *
     * @param reason 検索が失敗した理由
     */
    private fun showSearchErrorDialog(reason: FailureReason) {
        showNotification(
            tag = SEARCH_ERROR_DIALOG_TAG,
            titleRes = R.string.search_error_title,
            messageRes = reason.messageRes(),
        )
    }

    /**
     * 閉じるだけで処理が進まない通知をダイアログで表示する。
     *
     * この画面のViewが表示状態でなければ通知しない。
     * 詳細画面へ遷移するとこの画面のViewは破棄されるため、別画面にダイアログが出ることはない。
     * 状態保存後などに破棄されても操作を妨げないため、表示の延期や再送は行わない。
     */
    private fun showNotification(
        tag: String,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
    ) {
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        AlertDialogFragment.show(
            fragmentManager = childFragmentManager,
            tag = tag,
            titleRes = titleRes,
            messageRes = messageRes,
        )
    }

    /**
     * 詳細画面へ遷移する。
     *
     * @param item 詳細画面に表示するリポジトリ
     */
    private fun navigateToRepositoryDetail(item: RepositoryItem) {
        val navController = findNavController()
        // 連続タップ防止
        // 遷移元にいるときだけ遷移し、詳細画面の重複と不正なaction指定による例外を防ぐ。
        if (navController.currentDestination?.id != R.id.repository_search_fragment) return

        val action =
            RepositorySearchFragmentDirections
                .actionRepositorySearchFragmentToRepositoryDetailFragment(repositoryItem = item)
        navController.navigate(action)
    }
}

/** Enterとして扱うキーコード。テンキーのEnterも同じ操作として扱う。 */
private val enterKeyCodes = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

private fun KeyEvent.isEnterKey(): Boolean = keyCode in enterKeyCodes

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
