/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import androidx.lifecycle.SavedStateHandle
import jp.co.yumemi.android.code_check.MainDispatcherRule
import jp.co.yumemi.android.code_check.data.FailureReason
import jp.co.yumemi.android.code_check.data.FakeRepositorySearchDataSource
import jp.co.yumemi.android.code_check.data.FetchResult
import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 本番側の保存キー。テストから参照できないため文字列で持ち、キーの変更も検知できるようにする。
 */
private const val KEY_EXECUTED_QUERY = "executedQuery"

/** 先に開始する検索。あとから完了させる側。 */
private const val OLD_QUERY = "kotlin"

/** あとから開始する検索。先に完了させる側。 */
private const val NEW_QUERY = "python"

/** 一覧の中身は検証対象ではないため、名前だけを変えた最小の値を使う。 */
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
class RepositorySearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRepositorySearchDataSource()
    private val savedStateHandle = SavedStateHandle()
    private val viewModel by lazy { RepositorySearchViewModel(repository, savedStateHandle) }

    /** 保存されている検索条件。 */
    private fun executedQuery(): String? = savedStateHandle.get<String>(KEY_EXECUTED_QUERY)

    /** 検索が成功する状態にして、1件の結果を返させる。 */
    private fun succeedWith(vararg fullNames: String) {
        repository.result = FetchResult.Success(fullNames.map(::repositoryItem))
    }

    /** 発行されている通知のID。発行されていなければ失敗させる。 */
    private fun notificationId(): String {
        val notification = requireNotNull(viewModel.uiState.value.notification)
        return notification.id
    }

    /** 検索が失敗する状態にする。 */
    private fun failWith(reason: FailureReason) {
        repository.result = FetchResult.Failure(reason)
    }

    // ---- 初期状態と復元 ----

    @Test
    fun `保存条件がなければ未検索のまま取得先を呼ばない`() =
        runTest {
            assertEquals(SearchUiState(), viewModel.uiState.value)
            assertEquals(emptyList<String>(), repository.queries)
        }

    @Test
    fun `保存条件があれば生成した時点で再取得を開始する`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            val restored =
                RepositorySearchViewModel(
                    repository,
                    SavedStateHandle(mapOf(KEY_EXECUTED_QUERY to "kotlin")),
                )

            assertEquals(SearchContent.Loading("kotlin"), restored.uiState.value.content)
        }

    @Test
    fun `保存条件があれば再取得の結果を反映する`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            val restored =
                RepositorySearchViewModel(
                    repository,
                    SavedStateHandle(mapOf(KEY_EXECUTED_QUERY to "kotlin")),
                )

            advanceUntilIdle()

            assertEquals(
                SearchContent.Success::class.java,
                restored.uiState.value.content.javaClass,
            )
        }

    // ---- 状態遷移 ----

    @Test
    fun `検索を開始した直後は実行中の状態になる`() =
        runTest {
            succeedWith("JetBrains/kotlin")

            viewModel.search("kotlin")

            assertEquals(SearchContent.Loading("kotlin"), viewModel.uiState.value.content)
        }

    @Test
    fun `1件以上取得できたら一覧を持つ成功状態になる`() =
        runTest {
            succeedWith("JetBrains/kotlin", "square/okhttp")

            viewModel.search("kotlin")
            advanceUntilIdle()

            val content = viewModel.uiState.value.content
            assertEquals(
                listOf("JetBrains/kotlin", "square/okhttp"),
                (content as SearchContent.Success).items.map { it.fullName },
            )
        }

    @Test
    fun `0件で取得できたら該当なしの状態になる`() =
        runTest {
            succeedWith()

            viewModel.search("kotlin")
            advanceUntilIdle()

            assertEquals(SearchContent.Empty("kotlin"), viewModel.uiState.value.content)
        }

    @Test
    fun `取得に失敗したら失敗状態になる`() =
        runTest {
            failWith(FailureReason.NETWORK)

            viewModel.search("kotlin")
            advanceUntilIdle()

            assertEquals(SearchContent.Failed, viewModel.uiState.value.content)
        }

    // ---- 無効な入力 ----

    @Test
    fun `空文字で検索すると入力を促す通知を発行する`() =
        runTest {
            viewModel.search("")

            assertTrue(viewModel.uiState.value.notification is SearchNotification.InputRequired)
        }

    @Test
    fun `空白のみで検索すると入力を促す通知を発行する`() =
        runTest {
            viewModel.search("   ")

            assertTrue(viewModel.uiState.value.notification is SearchNotification.InputRequired)
        }

    @Test
    fun `無効な入力では取得先を呼ばない`() =
        runTest {
            viewModel.search("")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), repository.queries)
        }

    @Test
    fun `無効な入力では直前の検索状態を変えない`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            viewModel.search("kotlin")
            advanceUntilIdle()
            val before = viewModel.uiState.value.content

            viewModel.search("   ")

            assertEquals(before, viewModel.uiState.value.content)
        }

    @Test
    fun `無効な入力では保存した検索条件を変えない`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            viewModel.search("kotlin")
            advanceUntilIdle()

            viewModel.search("")

            assertEquals("kotlin", executedQuery())
        }

    // ---- 失敗時の一覧クリア ----

    @Test
    fun `成功したあとに失敗すると前回の一覧を残さない`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            viewModel.search("kotlin")
            advanceUntilIdle()

            failWith(FailureReason.SERVER)
            viewModel.search("python")
            advanceUntilIdle()

            assertEquals(SearchContent.Failed, viewModel.uiState.value.content)
        }

    // ---- 通知 ----

    @Test
    fun `取得に失敗すると理由を伴う通知を発行する`() =
        runTest {
            failWith(FailureReason.RATE_LIMIT)

            viewModel.search("kotlin")
            advanceUntilIdle()

            val notification = viewModel.uiState.value.notification
            assertEquals(
                FailureReason.RATE_LIMIT,
                (notification as SearchNotification.SearchFailed).reason,
            )
        }

    @Test
    fun `表示した通知のIDを渡すと通知を消費する`() =
        runTest {
            failWith(FailureReason.NETWORK)
            viewModel.search("kotlin")
            advanceUntilIdle()

            viewModel.onNotificationShown(notificationId())

            assertNull(viewModel.uiState.value.notification)
        }

    @Test
    fun `別の通知のIDを渡しても通知を消費しない`() =
        runTest {
            failWith(FailureReason.NETWORK)
            viewModel.search("kotlin")
            advanceUntilIdle()
            val notification = viewModel.uiState.value.notification

            viewModel.onNotificationShown("別の通知のID")

            assertEquals(notification, viewModel.uiState.value.notification)
        }

    @Test
    fun `新しい検索を開始すると未消費の通知を取り下げる`() =
        runTest {
            failWith(FailureReason.NETWORK)
            viewModel.search("kotlin")
            advanceUntilIdle()

            viewModel.search("python")

            assertNull(viewModel.uiState.value.notification)
        }

    @Test
    fun `消費した通知と同じIDの通知を再び発行しない`() =
        runTest {
            failWith(FailureReason.NETWORK)
            viewModel.search("kotlin")
            advanceUntilIdle()
            val first = notificationId()
            viewModel.onNotificationShown(first)

            viewModel.search("python")
            advanceUntilIdle()

            assertNotEquals(first, notificationId())
        }

    // ---- 検索条件の保存 ----

    @Test
    fun `取得に成功すると検索条件を保存する`() =
        runTest {
            succeedWith("JetBrains/kotlin")

            viewModel.search("kotlin")
            advanceUntilIdle()

            assertEquals("kotlin", executedQuery())
        }

    @Test
    fun `0件で成功した場合も検索条件を保存する`() =
        runTest {
            succeedWith()

            viewModel.search("kotlin")
            advanceUntilIdle()

            assertEquals("kotlin", executedQuery())
        }

    @Test
    fun `検索を開始した時点で保存した検索条件を削除する`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            viewModel.search("kotlin")
            advanceUntilIdle()

            viewModel.search("python")

            assertNull(executedQuery())
        }

    @Test
    fun `成功したあとに失敗しても古い検索条件が復活しない`() =
        runTest {
            succeedWith("JetBrains/kotlin")
            viewModel.search("kotlin")
            advanceUntilIdle()

            failWith(FailureReason.NETWORK)
            viewModel.search("python")
            advanceUntilIdle()

            assertNull(executedQuery())
        }

    // ---- 検索の競合 ----

    /**
     * 2つの検索を完了させずに開始し、あとから開始した検索を先に完了させる。
     *
     * 古い検索が新しい検索より後に完了する状況を作る。
     *
     * @param oldResult 古い検索があとから返す結果
     */
    private fun TestScope.completeOldSearchLast(oldResult: FetchResult<List<RepositoryItem>>) {
        repository.suspendOn(OLD_QUERY)
        repository.suspendOn(NEW_QUERY)

        viewModel.search(OLD_QUERY)
        advanceUntilIdle()
        viewModel.search(NEW_QUERY)
        advanceUntilIdle()

        repository.complete(NEW_QUERY, FetchResult.Success(listOf(repositoryItem("new/repo"))))
        advanceUntilIdle()
        repository.complete(OLD_QUERY, oldResult)
        advanceUntilIdle()
    }

    @Test
    fun `古い検索があとから完了しても新しい検索の結果が残る`() =
        runTest {
            completeOldSearchLast(FetchResult.Success(listOf(repositoryItem("old/repo"))))

            val content = viewModel.uiState.value.content
            assertEquals(
                listOf("new/repo"),
                (content as SearchContent.Success).items.map { it.fullName },
            )
        }

    @Test
    fun `古い検索の失敗があとから届いても通知を発行しない`() =
        runTest {
            completeOldSearchLast(FetchResult.Failure(FailureReason.NETWORK))

            assertNull(viewModel.uiState.value.notification)
        }

    @Test
    fun `古い検索の成功があとから届いても保存した検索条件を上書きしない`() =
        runTest {
            completeOldSearchLast(FetchResult.Success(listOf(repositoryItem("old/repo"))))

            assertEquals(NEW_QUERY, executedQuery())
        }
}
