/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import coil.load
import jp.co.yumemi.android.code_check.MainActivity.Companion.lastSearchDate
import jp.co.yumemi.android.code_check.databinding.FragmentRepositoryDetailBinding

private const val TAG = "RepositoryDetail"

/**
 * 検索結果で選択したリポジトリの詳細を表示する画面。
 *
 * 表示するリポジトリはNavigationの引数`repositoryItem`で受け取る。
 */
class RepositoryDetailFragment : Fragment(R.layout.fragment_repository_detail) {
    private val args: RepositoryDetailFragmentArgs by navArgs()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val searchedAt = lastSearchDate
        if (searchedAt != null) {
            Log.d(TAG, "検索日時: $searchedAt")
        } else {
            Log.d(TAG, "検索日時がありません")
        }

        // 表示の設定はこの中で完結し、View破棄後に参照しないためローカル変数で保持する。
        val binding = FragmentRepositoryDetailBinding.bind(view)

        val item = args.repositoryItem

        binding.ownerIcon.load(item.ownerAvatarUrl)
        binding.repositoryName.text = item.fullName
        binding.repositoryLanguage.text = item.languageText
        binding.starCount.text = "${item.stargazersCount} stars"
        binding.watcherCount.text = "${item.watchersCount} watchers"
        binding.forkCount.text = "${item.forksCount} forks"
        binding.openIssueCount.text = "${item.openIssuesCount} open issues"
    }
}
