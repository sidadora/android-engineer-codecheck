/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.detail

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import coil.load
import jp.co.yumemi.android.code_check.R
import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.databinding.FragmentRepositoryDetailBinding
import jp.co.yumemi.android.code_check.ui.common.NotificationDialogHost
import jp.co.yumemi.android.code_check.ui.common.NotificationDialogHost.Request
import kotlinx.coroutines.launch
import java.util.Date

private const val TAG = "RepositoryDetail"

/** この画面の通知ダイアログのタグ。検索画面とは別にする。 */
private const val NOTIFICATION_DIALOG_TAG = "detailNotification"

/**
 * 失敗理由に対応する案内文のリソースIDを返す。
 *
 * Watcher数だけが取得できなかった部分的な失敗であり、他の情報は表示され続ける。
 * 利用者が取れる行動が変わる場合にだけ文言を分ける。
 */
@StringRes
private fun FailureReason.messageRes(): Int =
    when (this) {
        // 時間を置けば解決する見込みがあるため、この理由だけ案内を分ける。
        FailureReason.RATE_LIMIT -> R.string.detail_watchers_failure_rate_limit_message
        // 他の理由は利用者が取れる行動が変わらないため、共通の案内にする。
        else -> R.string.detail_watchers_failure_message
    }

/** 通知を[NotificationDialogHost]へ渡す形へ変換する。 */
private fun DetailNotification.toRequest(): Request = Request(id, R.string.detail_error_title, reason.messageRes())

/**
 * 検索結果で選択したリポジトリの詳細を表示する画面。
 *
 * 名前・言語・Star数などはNavigationの引数で受け取り、購読者数だけ
 * [RepositoryDetailViewModel]が追加で取得する。
 */
class RepositoryDetailFragment : Fragment(R.layout.fragment_repository_detail) {
    private val args: RepositoryDetailFragmentArgs by navArgs()

    private val viewModel: RepositoryDetailViewModel by viewModels {
        RepositoryDetailViewModel.Factory
    }

    private var dialogHost: NotificationDialogHost? = null

    /**
     * Navigationの引数から詳細情報を表示し、購読者数の取得状態の購読と
     * 通知ダイアログの監視を開始する。
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

        // BindingはViewの寿命に紐づく購読からも参照するため、フィールドには保持しない。
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
        binding.forkCount.text =
            getString(R.string.repository_forks_format, item.forksCount)
        binding.openIssueCount.text =
            getString(
                R.string.repository_open_issues_format,
                item.openIssuesCount,
            )

        val host = NotificationDialogHost(childFragmentManager, NOTIFICATION_DIALOG_TAG)
        dialogHost = host
        host.start { evaluateNotification(viewModel.uiState.value.notification) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.watcherCount.text = watcherCountText(state.watcherCount)
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

    /** 言語の表示文言を組み立てる。設定がない場合は補完せず、その旨を示す。 */
    private fun languageText(language: String?): String =
        if (language == null) {
            getString(R.string.repository_language_unknown)
        } else {
            getString(R.string.repository_language_format, language)
        }

    /**
     * 購読者数の表示文言を組み立てる。
     *
     * 取得中と失敗を0やStar数で補完せず、その状態が分かる文言にする。
     *
     * @param state 購読者数の取得状態
     */
    private fun watcherCountText(state: WatcherCountState): String =
        when (state) {
            WatcherCountState.Loading -> getString(R.string.repository_watchers_loading)
            WatcherCountState.Failed -> getString(R.string.repository_watchers_unavailable)
            is WatcherCountState.Success ->
                getString(R.string.repository_watchers_format, state.count)
        }

    /**
     * 通知をHostの形へ変換し、表示要求と消費の判断を委ねる。
     *
     * 表示状況の判定はHostが持つため、ここでは文字列リソースの選択だけを行う。
     *
     * @param notification 表示を検討する通知。nullの場合もHostへ渡し、確定した予約を解除させる
     */
    private fun evaluateNotification(notification: DetailNotification?) {
        dialogHost?.evaluate(
            request = notification?.toRequest(),
            onShown = viewModel::onNotificationShown,
        )
    }
}
