/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.detail

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import coil.load
import jp.co.yumemi.android.code_check.MainActivity.Companion.lastSearchDate
import jp.co.yumemi.android.code_check.R
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

        // オーナー情報がない場合に、無関係な既定画像を表示しないよう明示的に扱う。
        val ownerAvatarUrl = item.ownerAvatarUrl
        if (ownerAvatarUrl != null) {
            binding.ownerIcon.load(ownerAvatarUrl)
        } else {
            binding.ownerIcon.setImageDrawable(null)
        }
        binding.repositoryName.text = item.fullName
        binding.repositoryLanguage.text = item.languageText
        binding.starCount.text =
            getString(R.string.repository_stars_format, item.stargazersCount)
        binding.watcherCount.text =
            getString(R.string.repository_watchers_format, item.watchersCount)
        binding.forkCount.text =
            getString(R.string.repository_forks_format, item.forksCount)
        binding.openIssueCount.text =
            getString(
                R.string.repository_open_issues_format,
                item.openIssuesCount,
            )
    }
}
