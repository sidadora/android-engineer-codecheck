/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * 検索結果のリポジトリを、リポジトリ名の一覧として表示するアダプター。
 *
 * @property itemClickListener 項目がタップされたことを通知する先
 */
class RepositoryListAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<RepositoryItem, RepositoryListAdapter.ViewHolder>(repositoryDiffCallback) {
    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view)

    interface OnItemClickListener {
        /**
         * 一覧の項目がタップされたときに呼ばれる。
         *
         * @param item タップされた項目が表すリポジトリ
         */
        fun onItemClick(item: RepositoryItem)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_repository, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
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
        private val repositoryDiffCallback =
            object : DiffUtil.ItemCallback<RepositoryItem>() {
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
