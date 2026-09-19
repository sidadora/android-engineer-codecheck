package jp.co.yumemi.android.code_check.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.yumemi.android.code_check.databinding.ItemRepositoryBinding
import jp.co.yumemi.android.code_check.model.RepositoryItem

/**
 * 検索結果のリポジトリを、リポジトリ名の一覧として表示するアダプター。
 *
 * @property itemClickListener 項目がタップされたことを通知する先
 */
class RepositoryListAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<RepositoryItem, RepositoryListAdapter.ViewHolder>(repositoryDiffCallback) {
    init {
        // 一覧は状態の購読後に投入されるため、空のまま復元されるとスクロール位置が失われる。
        // 1件以上そろうまでRecyclerViewの復元を待たせる。
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    class ViewHolder(
        private val binding: ItemRepositoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: RepositoryItem,
            itemClickListener: OnItemClickListener,
        ) {
            binding.repositoryName.text = item.fullName
            binding.root.setOnClickListener { itemClickListener.onItemClick(item) }
        }
    }

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
    ): ViewHolder =
        ViewHolder(
            ItemRepositoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), itemClickListener)
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
