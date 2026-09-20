/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.detail

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.yumemi.android.code_check.CodeCheckApplication
import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.data.FetchResult
import jp.co.yumemi.android.code_check.data.RepositoryDetailDataSource
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Navigationの引数キー。表示対象を[SavedStateHandle]から取り出すために使う。 */
private const val KEY_REPOSITORY_ITEM = "repositoryItem"

/**
 * 詳細画面で追加取得する購読者数を保持する。
 *
 * 取得は生成時に1回だけ行う。回転ではViewModelが残るため取得をやり直さない。
 * プロセス再生成ではViewModelも作り直されるが、取得に使う条件はNavigationの引数から
 * 復元されるため、あらためて取得する。
 *
 * [viewModelScope]を使うため、ViewModelの破棄で取得へ中断を要求する。
 * 中断の要求は即時停止を保証しないので、結果の反映は購読側の生存期間で制御する。
 *
 * @property repository 購読者数の取得先
 */
class RepositoryDetailViewModel(
    private val repository: RepositoryDetailDataSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())

    /** 画面の描画と通知に使う、読み取り専用の状態。 */
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        val fullName = savedStateHandle.get<RepositoryItem>(KEY_REPOSITORY_ITEM)?.fullName
        if (fullName == null) {
            // 引数を復元できない場合は取得できないため、失敗として扱う。件数は補完しない。
            _uiState.value = failedState()
        } else {
            viewModelScope.launch { fetchSubscribersCount(fullName) }
        }
    }

    /**
     * 表示中または表示要求済みの通知を消費する。
     *
     * 現在保持している通知とIDが一致する場合だけ消費し、別の通知は残す。
     *
     * @param id 表示中または表示要求済みの通知ID
     */
    @MainThread
    fun onNotificationShown(id: String) {
        _uiState.update { if (it.notification?.id == id) it.copy(notification = null) else it }
    }

    /**
     * 購読者数を取得し、結果を状態へ反映する。
     *
     * @param fullName `owner/repo`形式のリポジトリ名
     */
    private suspend fun fetchSubscribersCount(fullName: String) {
        when (val result = repository.getSubscribersCount(fullName)) {
            is FetchResult.Success ->
                _uiState.value =
                    DetailUiState(watcherCount = WatcherCountState.Success(result.value))

            is FetchResult.Failure -> _uiState.value = failedState(result.reason)
        }
    }

    /**
     * 取得できなかった状態を作る。
     *
     * 件数は0やStar数で補完せず、失敗を伝える通知を伴う。
     *
     * @param reason 文言を選ぶための失敗理由。分類できない場合は[FailureReason.UNKNOWN]
     */
    private fun failedState(reason: FailureReason = FailureReason.UNKNOWN): DetailUiState =
        DetailUiState(
            watcherCount = WatcherCountState.Failed,
            notification = DetailNotification(UUID.randomUUID().toString(), reason),
        )

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application =
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                            as CodeCheckApplication
                    RepositoryDetailViewModel(
                        repository = application.repositoryDetailDataSource,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
