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

class RepositorySearchFragment: Fragment(R.layout.fragment_repository_search){

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        val binding= FragmentRepositorySearchBinding.bind(view)

        val viewModel= RepositorySearchViewModel(requireContext())

        val layoutManager= LinearLayoutManager(requireContext())
        val dividerItemDecoration=
            DividerItemDecoration(requireContext(), layoutManager.orientation)
        val adapter= RepositoryListAdapter(object : RepositoryListAdapter.OnItemClickListener{
            override fun onItemClick(item: RepositoryItem){
                navigateToRepositoryDetail(item)
            }
        })

        binding.searchInputText
            .setOnEditorActionListener{ editText, actionId, _ ->
                if (actionId== EditorInfo.IME_ACTION_SEARCH){
                    editText.text.toString().let {
                        viewModel.searchRepositories(it).apply{
                            adapter.submitList(this)
                        }
                    }
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }

        binding.repositoryListView.also{
            it.layoutManager= layoutManager
            it.addItemDecoration(dividerItemDecoration)
            it.adapter= adapter
        }
    }

    fun navigateToRepositoryDetail(item: RepositoryItem)
    {
        val action= RepositorySearchFragmentDirections
            .actionRepositorySearchFragmentToRepositoryDetailFragment(repositoryItem= item)
        findNavController().navigate(action)
    }
}

val repositoryDiffCallback= object: DiffUtil.ItemCallback<RepositoryItem>(){
    override fun areItemsTheSame(oldItem: RepositoryItem, newItem: RepositoryItem): Boolean
    {
        return oldItem.fullName== newItem.fullName
    }

    override fun areContentsTheSame(oldItem: RepositoryItem, newItem: RepositoryItem): Boolean
    {
        return oldItem== newItem
    }

}

class RepositoryListAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<RepositoryItem, RepositoryListAdapter.ViewHolder>(repositoryDiffCallback){

    class ViewHolder(view: View): RecyclerView.ViewHolder(view)

    interface OnItemClickListener{
    	fun onItemClick(item: RepositoryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
    {
    	val view= LayoutInflater.from(parent.context)
            .inflate(R.layout.item_repository, parent, false)
    	return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int)
    {
    	val item= getItem(position)
        (holder.itemView.findViewById<View>(R.id.repository_name) as TextView).text=
            item.fullName

    	holder.itemView.setOnClickListener{
     		itemClickListener.onItemClick(item)
    	}
    }
}
