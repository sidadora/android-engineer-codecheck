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
import jp.co.yumemi.android.code_check.R
import jp.co.yumemi.android.code_check.databinding.FragmentRepositoryDetailBinding
import java.util.Date

private const val TAG = "RepositoryDetail"

/**
 * 検索結果で選択したリポジトリの詳細を表示する画面。
 *
 * 表示する内容と、その一覧を取得した時刻はNavigationの引数で受け取る。
 */
class RepositoryDetailFragment : Fragment(R.layout.fragment_repository_detail) {
    private val args: RepositoryDetailFragmentArgs by navArgs()

    /**
     * Navigationの引数から詳細情報を表示し、オーナー画像の読み込みを開始する。
     *
     * 検索結果の取得日時はログへ出力する。
     *
     * @param view 詳細画面のルートView
     * @param savedInstanceState 再生成時に渡される保存状態。初回生成時はnull
     */
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // 詳細の取得時刻ではなく、表示中の検索結果に対応する取得日時を記録する。
        Log.d(TAG, "検索日時: ${Date(args.searchedAtMillis)}")

        // Bindingは初期表示の設定にだけ使い、Fragmentのフィールドには保持しない。
        val binding = FragmentRepositoryDetailBinding.bind(view)

        val item = args.repositoryItem

        // オーナー情報がない場合は、代替画像を表示せず画像を消す。
        val ownerAvatarUrl = item.ownerAvatarUrl
        if (ownerAvatarUrl != null) {
            binding.ownerIcon.load(ownerAvatarUrl)
        } else {
            binding.ownerIcon.setImageDrawable(null)
        }
        binding.repositoryName.text = item.fullName
        binding.repositoryLanguage.text = languageText(item.language)
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

    /** 言語の表示文言を組み立てる。設定がない場合は補完せず、その旨を示す。 */
    private fun languageText(language: String?): String =
        if (language == null) {
            getString(R.string.repository_language_unknown)
        } else {
            getString(R.string.repository_language_format, language)
        }
}
