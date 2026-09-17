/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import jp.co.yumemi.android.code_check.databinding.FragmentRepositorySearchBinding

/**
 * GitHubのリポジトリをキーワードで検索し、結果を一覧表示する画面。
 *
 * キーボードの検索キーで検索を実行し、一覧の項目を選択すると詳細画面へ遷移する。
 */
class RepositorySearchFragment : Fragment(R.layout.fragment_repository_search) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentRepositorySearchBinding.bind(view)

        val viewModel = RepositorySearchViewModel(requireContext())

        val layoutManager = LinearLayoutManager(requireContext())

        val dividerItemDecoration =
            DividerItemDecoration(requireContext(), layoutManager.orientation)

        val adapter = RepositoryListAdapter(
            object : RepositoryListAdapter.OnItemClickListener {
                override fun onItemClick(item: RepositoryItem) {
                    navigateToRepositoryDetail(item)
                }
            }
        )

        binding.searchInputText
            .setOnEditorActionListener { editText, actionId, _ ->
                val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                if (isSearchAction) {
                    adapter.submitList(
                        viewModel.searchRepositories(editText.text.toString())
                    )
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
     * 詳細画面へ遷移する。
     *
     * @param item 詳細画面に表示するリポジトリ
     */
    private fun navigateToRepositoryDetail(item: RepositoryItem) {
        val action = RepositorySearchFragmentDirections
            .actionRepositorySearchFragmentToRepositoryDetailFragment(repositoryItem = item)
        findNavController().navigate(action)
    }
}

/**
 * 検索結果のリポジトリを、リポジトリ名の一覧として表示するアダプター。
 *
 * @property itemClickListener 項目がタップされたことを通知する先
 */
class RepositoryListAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<RepositoryItem, RepositoryListAdapter.ViewHolder>(repositoryDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    interface OnItemClickListener {
        /**
         * 一覧の項目がタップされたときに呼ばれる。
         *
         * @param item タップされた項目が表すリポジトリ
         */
        fun onItemClick(item: RepositoryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_repository, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.findViewById<TextView>(R.id.repository_name).text = item.fullName

        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(item)
        }
    }

    companion object {
        /**
         * 一覧の差分を判定するために使うコールバック。
         *
         * [RepositoryItem.fullName]が一致する項目を同一とみなし、内容の比較は全プロパティの等価性で行う。
         */
        private val repositoryDiffCallback = object : DiffUtil.ItemCallback<RepositoryItem>() {
            override fun areItemsTheSame(
                oldItem: RepositoryItem,
                newItem: RepositoryItem,
            ): Boolean = oldItem.fullName == newItem.fullName

            override fun areContentsTheSame(
                oldItem: RepositoryItem,
                newItem: RepositoryItem,
            ): Boolean = oldItem == newItem
        }
    }
}
