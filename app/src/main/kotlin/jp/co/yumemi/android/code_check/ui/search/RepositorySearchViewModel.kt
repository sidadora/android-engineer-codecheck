/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.yumemi.android.code_check.CodeCheckApplication
import jp.co.yumemi.android.code_check.data.FetchResult
import jp.co.yumemi.android.code_check.data.GitHubRepository
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** プロセス再生成後の再検索に使う、成功時の検索条件の保存キー。 */
private const val KEY_EXECUTED_QUERY = "executedQuery"

/**
 * 検索処理と画面状態を管理する。
 *
 * 状態と要求IDの更新はメインスレッドで行う。
 *
 * @property repository リポジトリ情報の取得先
 * @property savedStateHandle プロセス再生成後の再検索に使う検索条件の保存先
 */
class RepositorySearchViewModel(
    private val repository: GitHubRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())

    /** 画面の描画と通知に使う、読み取り専用の検索状態。 */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 前の検索へキャンセルを要求するためのJob。即時停止を保証するものではない。 */
    private var searchJob: Job? = null

    /** 最新の検索要求のID。古い応答の反映を防ぐために使う。 */
    private var currentRequestId: String? = null

    init {
        // 成功した検索条件だけが保存されている。回転ではViewModelが残るためここは通らない。
        savedStateHandle.get<String>(KEY_EXECUTED_QUERY)?.let(::startSearch)
    }

    /**
     * 入力を検証し、検索を開始する。
     *
     * 空文字・空白のみの場合は入力案内を通知し、検索状態・保存情報・実行中の検索は変更しない。
     *
     * @param query 入力欄の内容。空白のみでなければ加工せず送信する
     */
    @MainThread
    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(notification = SearchNotification.InputRequired(newId())) }
            return
        }
        startSearch(query)
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
     * 前の検索へキャンセルを要求し、新しい検索を開始する。
     *
     * 未表示の通知と保存済みの検索条件を消し、検索中の状態へ切り替える。
     * 結果を反映する直前に要求IDとキャンセル状態を確認する。
     *
     * @param query APIへ加工せず渡す、検証済みの検索条件
     */
    @MainThread
    private fun startSearch(query: String) {
        val requestId = newId()
        currentRequestId = requestId
        searchJob?.cancel()

        // 前の検索に関する未表示の通知は、新しい検索の開始時に取り下げる。
        _uiState.value = SearchUiState(content = SearchContent.Loading(query))
        // 結果が確定するまで復元対象を持たない。失敗しても以前の成功条件を復活させない。
        savedStateHandle[KEY_EXECUTED_QUERY] = null

        searchJob =
            viewModelScope.launch {
                val result = repository.searchRepositories(query)

                // キャンセル後も通信や解析が最後まで走ることがあるため、反映の直前に確認する。
                if (requestId != currentRequestId) return@launch
                ensureActive()

                applyResult(query, result)
            }
    }

    /**
     * 検索結果を画面状態と保存情報へ反映する。
     *
     * 成功時は検索条件を保存し、失敗時は失敗状態と通知を設定する。
     * 最新の検索要求であり、キャンセルされていないことを確認してから呼ぶ。
     *
     * @param query 結果に対応する検索条件
     * @param result データ取得の成功値または失敗理由
     */
    @MainThread
    private fun applyResult(
        query: String,
        result: FetchResult<List<RepositoryItem>>,
    ) {
        when (result) {
            is FetchResult.Success -> {
                savedStateHandle[KEY_EXECUTED_QUERY] = query
                _uiState.value = SearchUiState(content = successContent(query, result.value))
            }

            is FetchResult.Failure ->
                _uiState.value =
                    SearchUiState(
                        content = SearchContent.Failed,
                        notification = SearchNotification.SearchFailed(newId(), result.reason),
                    )
        }
    }

    /**
     * 成功した検索結果を、件数に応じた画面状態へ変換する。
     *
     * @param query 結果に対応する検索条件
     * @param items 取得したリポジトリ一覧
     * @return 0件の場合はEmpty、1件以上の場合は現在時刻を取得日時としたSuccess
     */
    private fun successContent(
        query: String,
        items: List<RepositoryItem>,
    ): SearchContent =
        if (items.isEmpty()) {
            SearchContent.Empty(query)
        } else {
            SearchContent.Success(items, System.currentTimeMillis())
        }

    /** 検索要求と通知の識別に使うUUID文字列を生成する。 */
    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application =
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                                as CodeCheckApplication
                    RepositorySearchViewModel(
                        repository = application.gitHubRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
