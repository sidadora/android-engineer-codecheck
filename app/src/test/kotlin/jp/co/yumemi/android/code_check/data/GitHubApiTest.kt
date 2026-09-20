/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** 検索条件は分類に影響しないため、固定の値を使う。 */
private const val QUERY = "kotlin"

/** リポジトリ名は分類に影響しないため、固定の値を使う。 */
private const val FULL_NAME = "JetBrains/kotlin"

/** 本番コードが残り要求数として参照するヘッダー名。名前が変わればテストが落ちるようにする。 */
private const val HEADER_RATE_LIMIT_REMAINING = "x-ratelimit-remaining"

/**
 * [GitHubApi]がHTTPステータスとヘッダーから失敗理由を分類することを確認する。
 *
 * 分類はレスポンス本文を見ないため、本文は空のままステータスとヘッダーだけを変える。
 * 実際の通信・TLS・リダイレクト・タイムアウトは経由しない。
 */
class GitHubApiTest {
    /** テスト内で生成したクライアント。所有権はテスト側にあるため、終了時に閉じる。 */
    private val clients = mutableListOf<HttpClient>()

    @Before
    fun clearLog() {
        Log.clear()
    }

    @After
    fun closeClients() {
        clients.forEach(HttpClient::close)
        clients.clear()
    }

    /**
     * 常に同じレスポンスを返す[GitHubApi]を組み立てる。
     *
     * @param status 返すHTTPステータス
     * @param headers 返すレスポンスヘッダー
     * @param body 返すレスポンス本文
     * @return 固定のレスポンスを返すクライアントを持つ[GitHubApi]
     */
    private fun apiReturning(
        status: HttpStatusCode,
        headers: Headers = Headers.Empty,
        body: String = "",
    ): GitHubApi {
        val client = HttpClient(MockEngine { respond(content = body, status = status, headers = headers) })
        clients += client
        return GitHubApi(client)
    }

    /**
     * 固定のレスポンスを返すクライアントで検索を実行する。
     *
     * @param status 返すHTTPステータス
     * @param headers 返すレスポンスヘッダー
     * @param body 返すレスポンス本文
     * @return 検索の結果
     */
    private suspend fun searchReturning(
        status: HttpStatusCode,
        headers: Headers = Headers.Empty,
        body: String = "",
    ): FetchResult<String> = apiReturning(status, headers, body).searchRepositories(QUERY)

    // ---- 403の分類 ----

    @Test
    fun `403 でレート制限のヘッダーがなければ分類できない失敗にする`() =
        runTest {
            val result = searchReturning(HttpStatusCode.Forbidden)

            assertEquals(FetchResult.Failure(FailureReason.UNKNOWN), result)
        }

    @Test
    fun `403 で残り要求数が 0 ならレート制限として分類する`() =
        runTest {
            val result =
                searchReturning(
                    HttpStatusCode.Forbidden,
                    headersOf(HEADER_RATE_LIMIT_REMAINING, "0"),
                )

            assertEquals(FetchResult.Failure(FailureReason.RATE_LIMIT), result)
        }

    @Test
    fun `403 で Retry-After があればレート制限として分類する`() =
        runTest {
            val result =
                searchReturning(
                    HttpStatusCode.Forbidden,
                    headersOf(HttpHeaders.RetryAfter, "60"),
                )

            assertEquals(FetchResult.Failure(FailureReason.RATE_LIMIT), result)
        }

    @Test
    fun `403 で残り要求数が 0 でなければ分類できない失敗にする`() =
        runTest {
            val result =
                searchReturning(
                    HttpStatusCode.Forbidden,
                    headersOf(HEADER_RATE_LIMIT_REMAINING, "5"),
                )

            assertEquals(FetchResult.Failure(FailureReason.UNKNOWN), result)
        }

    // ---- ステータスごとの分類 ----

    @Test
    fun `429 はレート制限として分類する`() =
        runTest {
            val result = searchReturning(HttpStatusCode.TooManyRequests)

            assertEquals(FetchResult.Failure(FailureReason.RATE_LIMIT), result)
        }

    @Test
    fun `422 は要求の拒否として分類する`() =
        runTest {
            val result = searchReturning(HttpStatusCode.UnprocessableEntity)

            assertEquals(FetchResult.Failure(FailureReason.REQUEST_REJECTED), result)
        }

    @Test
    fun `500 はサーバー側の失敗として分類する`() =
        runTest {
            val result = searchReturning(HttpStatusCode.InternalServerError)

            assertEquals(FetchResult.Failure(FailureReason.SERVER), result)
        }

    @Test
    fun `599 もサーバー側の失敗として分類する`() =
        runTest {
            val result = searchReturning(HttpStatusCode(599, "Server Error"))

            assertEquals(FetchResult.Failure(FailureReason.SERVER), result)
        }

    @Test
    fun `401 は分類できない失敗にする`() =
        runTest {
            val result = searchReturning(HttpStatusCode.Unauthorized)

            assertEquals(FetchResult.Failure(FailureReason.UNKNOWN), result)
        }

    @Test
    fun `404 は分類できない失敗にする`() =
        runTest {
            val result = searchReturning(HttpStatusCode.NotFound)

            assertEquals(FetchResult.Failure(FailureReason.UNKNOWN), result)
        }

    // ---- 成功 ----

    @Test
    fun `200 ならレスポンス本文をそのまま返す`() =
        runTest {
            val body = """{"items":[]}"""

            val result = searchReturning(HttpStatusCode.OK, body = body)

            assertEquals(FetchResult.Success(body), result)
        }

    @Test
    fun `200 なら JSON として解析できない本文もそのまま返す`() =
        runTest {
            // 本文の妥当性はParserの責務のため、GitHubApiは中身を見ずに渡す。
            val result = searchReturning(HttpStatusCode.OK, body = "not json")

            assertEquals(FetchResult.Success("not json"), result)
        }

    // ---- 通信の失敗 ----

    @Test
    fun `通信そのものに失敗すると通信エラーとして分類する`() =
        runTest {
            val client = HttpClient(MockEngine { throw IOException("接続できません") })
            clients += client

            val result = GitHubApi(client).searchRepositories(QUERY)

            assertEquals(FetchResult.Failure(FailureReason.NETWORK), result)
        }

    // ---- 詳細取得 ----

    @Test
    fun `詳細取得も検索と同じ分類を行う`() =
        runTest {
            val result = apiReturning(HttpStatusCode.Forbidden).getRepository(FULL_NAME)

            assertEquals(FetchResult.Failure(FailureReason.UNKNOWN), result)
        }

    // ---- ログへ出す内容 ----

    @Test
    fun `通信に失敗したログへ URL と検索条件を出さない`() =
        runTest {
            val query = "SECRET_QUERY_12345"
            val client =
                HttpClient(
                    MockEngine {
                        throw IOException("https://api.github.com/search/repositories?q=$query へ接続できません")
                    },
                )
            clients += client

            GitHubApi(client).searchRepositories(query)

            // 記録が空だと以降の条件が空振りするため、ログが残っていること自体も確認する。
            assertTrue("通信失敗のログが記録されていません", Log.entries().isNotEmpty())
            assertTrue(Log.entries().none { it.message.contains(query) })
            assertTrue(Log.entries().none { it.message.contains("api.github.com") })
        }

    @Test
    fun `エラー応答のログへレスポンス本文を出さない`() =
        runTest {
            val body = """{"message":"SECRET_BODY_12345"}"""

            searchReturning(HttpStatusCode.InternalServerError, body = body)

            assertTrue("エラー応答のログが記録されていません", Log.entries().isNotEmpty())
            assertTrue(Log.entries().none { it.message.contains("SECRET_BODY_12345") })
        }
}
