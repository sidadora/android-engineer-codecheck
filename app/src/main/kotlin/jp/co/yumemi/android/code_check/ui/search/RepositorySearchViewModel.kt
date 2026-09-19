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

/** プロセス再生成をまたいで復元する、成功した検索条件。 */
private const val KEY_EXECUTED_QUERY = "executedQuery"

/**
 * 検索画面の状態を保持し、検索の実行と中断を制御する。
 *
 * [search]と要求IDの読み書きはメインスレッドからのみ行う契約とする。
 * [viewModelScope]は`Dispatchers.Main.immediate`で動くため、追加の同期は行わない。
 */
class RepositorySearchViewModel(
    private val repository: GitHubRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** 実行中の検索を識別する。応答を反映する直前に照合する。 */
    private var currentRequestId: String? = null

    init {
        // 成功した検索条件だけが保存されている。回転ではViewModelが残るためここは通らない。
        savedStateHandle.get<String>(KEY_EXECUTED_QUERY)?.let(::startSearch)
    }

    /**
     * 検索条件を受け取って検索を開始する。
     *
     * 空文字・空白のみは通信せず、入力を促す通知だけを出す。このとき一覧・検索日時・
     * 保存情報は変更しない。実行中の検索も中断しないため、その検索が後から失敗すると
     * 入力を促す通知が失敗の通知へ置き換わることがある。
     *
     * @param query 入力欄の内容。有効な場合は正規化せずそのまま送信する
     */
    @MainThread
    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(notification = SearchNotification.InputRequired(newId())) }
            return
        }
        startSearch(query)
    }

    /** 表示できた通知を消費する。確認中に別の通知へ置き換わっていた場合は消費しない。 */
    @MainThread
    fun onNotificationShown(id: String) {
        _uiState.update { if (it.notification?.id == id) it.copy(notification = null) else it }
    }

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

    private fun successContent(
        query: String,
        items: List<RepositoryItem>,
    ): SearchContent =
        if (items.isEmpty()) {
            SearchContent.Empty(query)
        } else {
            SearchContent.Success(items, System.currentTimeMillis())
        }

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
