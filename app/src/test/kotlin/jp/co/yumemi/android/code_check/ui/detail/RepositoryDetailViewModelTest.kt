/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.detail

import androidx.lifecycle.SavedStateHandle
import jp.co.yumemi.android.code_check.MainDispatcherRule
import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.data.FakeRepositoryDetailDataSource
import jp.co.yumemi.android.code_check.data.FetchResult
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 本番側のNavigation引数キー。テストから参照できないため文字列で持ち、キーの変更も検知できるようにする。
 */
private const val KEY_REPOSITORY_ITEM = "repositoryItem"

/** 詳細画面が使うのは[RepositoryItem.fullName]だけのため、他の項目は最小の値にする。 */
private fun repositoryItem(fullName: String): RepositoryItem =
    RepositoryItem(
        fullName = fullName,
        ownerAvatarUrl = null,
        language = null,
        stargazersCount = 0,
        forksCount = 0,
        openIssuesCount = 0,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepositoryDetailDataSource()

    /**
     * ViewModelを生成する。
     *
     * @param item Navigationの引数として渡すリポジトリ。nullの場合は引数を渡さない
     */
    private fun createViewModel(item: RepositoryItem? = repositoryItem("JetBrains/kotlin")): RepositoryDetailViewModel {
        val savedStateHandle =
            if (item == null) {
                SavedStateHandle()
            } else {
                SavedStateHandle(mapOf(KEY_REPOSITORY_ITEM to item))
            }
        return RepositoryDetailViewModel(repository, savedStateHandle)
    }

    /** 発行されている通知。発行されていなければ失敗させる。 */
    private fun notification(viewModel: RepositoryDetailViewModel): DetailNotification =
        requireNotNull(viewModel.uiState.value.notification)

    /** 取得が成功する状態にする。 */
    private fun succeedWith(count: Long) {
        repository.result = FetchResult.Success(count)
    }

    /** 取得が失敗する状態にする。 */
    private fun failWith(reason: FailureReason) {
        repository.result = FetchResult.Failure(reason)
    }

    // ---- 検索ViewModelと共通の性質 ----

    @Test
    fun `取得に成功すると購読者数を持つ状態になる`() =
        runTest {
            succeedWith(1489)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(WatcherCountState.Success(1489), viewModel.uiState.value.watcherCount)
        }

    @Test
    fun `取得に失敗すると失敗状態と通知だけを持つ`() =
        runTest {
            failWith(FailureReason.NETWORK)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(
                DetailUiState(
                    watcherCount = WatcherCountState.Failed,
                    notification = DetailNotification(notification(viewModel).id, FailureReason.NETWORK),
                ),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `レート制限で失敗すると理由をそのまま通知へ伝える`() =
        runTest {
            failWith(FailureReason.RATE_LIMIT)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(FailureReason.RATE_LIMIT, notification(viewModel).reason)
        }

    @Test
    fun `表示した通知のIDを渡すと通知を消費する`() =
        runTest {
            failWith(FailureReason.NETWORK)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onNotificationShown(notification(viewModel).id)

            assertNull(viewModel.uiState.value.notification)
        }

    @Test
    fun `別の通知のIDを渡しても通知を消費しない`() =
        runTest {
            failWith(FailureReason.NETWORK)
            val viewModel = createViewModel()
            advanceUntilIdle()
            val before = viewModel.uiState.value.notification

            viewModel.onNotificationShown("別の通知のID")

            assertEquals(before, viewModel.uiState.value.notification)
        }

    // ---- 詳細画面に固有の性質 ----

    @Test
    fun `生成した直後は取得中の状態になる`() =
        runTest {
            succeedWith(1489)

            val viewModel = createViewModel()

            assertEquals(WatcherCountState.Loading, viewModel.uiState.value.watcherCount)
        }

    @Test
    fun `引数のリポジトリ名で取得する`() =
        runTest {
            succeedWith(1489)
            createViewModel(repositoryItem("square/okhttp"))

            advanceUntilIdle()

            assertEquals("square/okhttp", repository.fullNames.firstOrNull())
        }

    @Test
    fun `引数がなければ取得中にならず失敗状態になる`() =
        runTest {
            val viewModel = createViewModel(item = null)

            assertEquals(WatcherCountState.Failed, viewModel.uiState.value.watcherCount)
        }

    @Test
    fun `引数がなければ取得先を呼ばない`() =
        runTest {
            createViewModel(item = null)

            advanceUntilIdle()

            assertEquals(emptyList<String>(), repository.fullNames)
        }

    @Test
    fun `引数がない場合の通知理由は分類できないものになる`() =
        runTest {
            val viewModel = createViewModel(item = null)

            assertEquals(FailureReason.UNKNOWN, notification(viewModel).reason)
        }

    @Test
    fun `購読者数が 0 でも正常な値として扱う`() =
        runTest {
            succeedWith(0)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(WatcherCountState.Success(0), viewModel.uiState.value.watcherCount)
        }

    @Test
    fun `取得に成功した場合は通知を発行しない`() =
        runTest {
            succeedWith(1489)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertNull(viewModel.uiState.value.notification)
        }

    @Test
    fun `通信に失敗すると理由をそのまま通知へ伝える`() =
        runTest {
            failWith(FailureReason.NETWORK)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(FailureReason.NETWORK, notification(viewModel).reason)
        }

    @Test
    fun `生成時に 1 回だけ取得する`() =
        runTest {
            succeedWith(1489)
            createViewModel()

            advanceUntilIdle()

            assertEquals(1, repository.fullNames.size)
        }

    @Test
    fun `取得に失敗しても購読者数を 0 で補完しない`() =
        runTest {
            failWith(FailureReason.SERVER)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.watcherCount is WatcherCountState.Success)
        }
}
