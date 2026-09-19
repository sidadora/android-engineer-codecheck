/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.yumemi.android.code_check.databinding.FragmentRepositorySearchBinding

// 検索エラーの通知ダイアログを識別するタグ。重複表示の判定に使う。
private const val SEARCH_ERROR_DIALOG_TAG = "searchError"

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

        binding.searchInputText
            .setOnEditorActionListener { editText, actionId, _ ->
                val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                if (isSearchAction) {
                    // 失敗時は一覧を更新せず、前回の検索結果をそのまま残す。
                    when (
                        val result = viewModel.searchRepositories(editText.text.toString())
                    ) {
                        is RepositorySearchResult.Success -> adapter.submitList(result.items)
                        RepositorySearchResult.Failure -> showSearchErrorDialog()
                    }
                }
                isSearchAction
            }

        binding.repositoryListView.also {
            it.layoutManager = layoutManager
            it.addItemDecoration(dividerItemDecoration)
            it.adapter = adapter
        }
    }

    /**
     * 検索が失敗したことを知らせる。
     *
     * この画面のViewが表示状態でなければ通知しない。
     * 詳細画面へ遷移するとこの画面のViewは破棄されるため、別画面にダイアログが出ることはない。
     * この通知は閉じるだけで処理が進まないため、状態保存後などに破棄されても操作を妨げない。
     */
    private fun showSearchErrorDialog() {
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        AlertDialogFragment.show(
            fragmentManager = childFragmentManager,
            tag = SEARCH_ERROR_DIALOG_TAG,
            titleRes = R.string.search_error_title,
            messageRes = R.string.search_failure_message,
        )
    }

    /**
     * 詳細画面へ遷移する。
     *
     * @param item 詳細画面に表示するリポジトリ
     */
    private fun navigateToRepositoryDetail(item: RepositoryItem) {
        val action =
            RepositorySearchFragmentDirections
                .actionRepositorySearchFragmentToRepositoryDetailFragment(repositoryItem = item)
        findNavController().navigate(action)
    }
}
