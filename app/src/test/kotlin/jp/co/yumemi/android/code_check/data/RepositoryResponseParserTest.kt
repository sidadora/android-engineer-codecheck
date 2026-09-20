/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.data

import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全項目が正常な1件。変更したい項目だけを書き換えた文字列を各テストで用意する。 */
private const val VALID_ITEM = """
    {
      "full_name": "JetBrains/kotlin",
      "owner": { "avatar_url": "https://example.com/avatar.png" },
      "language": "Kotlin",
      "stargazers_count": 53434,
      "forks_count": 6421,
      "open_issues_count": 444
    }
"""

/** [VALID_ITEM]のowner。差し替えを読みやすくするために切り出す。 */
private const val VALID_OWNER = """"owner": { "avatar_url": "https://example.com/avatar.png" }"""

/** 検索APIのレスポンスを組み立てる。 */
private fun itemsOf(vararg items: String): String = """{"items":[${items.joinToString(",")}]}"""

class RepositoryResponseParserTest {
    private val parser = RepositoryResponseParser()

    /**
     * 解析が失敗することを確認し、そのメッセージを検証できるよう例外を返す。
     *
     * @param responseBody 解析に失敗する想定のレスポンス本文
     * @return 送出された[JSONException]
     */
    private suspend fun parseFailure(responseBody: String): JSONException {
        val error = runCatching { parser.parse(responseBody) }.exceptionOrNull()
        assertTrue("JSONExceptionが必要です（実際は$error）", error is JSONException)
        return error as JSONException
    }

    /** [parseFailure]の購読者数版。 */
    private fun subscribersCountFailure(responseBody: String): JSONException {
        val error = runCatching { parser.parseSubscribersCount(responseBody) }.exceptionOrNull()
        assertTrue("JSONExceptionが必要です（実際は$error）", error is JSONException)
        return error as JSONException
    }

    // ---- parse: 正常系 ----

    @Test
    fun `正常な1件を全項目そろったRepositoryItemへ変換する`() =
        runTest {
            val actual = parser.parse(itemsOf(VALID_ITEM))

            assertEquals(
                listOf(
                    RepositoryItem(
                        fullName = "JetBrains/kotlin",
                        ownerAvatarUrl = "https://example.com/avatar.png",
                        language = "Kotlin",
                        stargazersCount = 53434,
                        forksCount = 6421,
                        openIssuesCount = 444,
                    ),
                ),
                actual,
            )
        }

    @Test
    fun `itemsが空配列なら空のリストを返す`() =
        runTest {
            assertEquals(emptyList<RepositoryItem>(), parser.parse("""{"items":[]}"""))
        }

    @Test
    fun `複数件をAPIが返した順序のまま返す`() =
        runTest {
            val body =
                itemsOf(
                    VALID_ITEM.replace("JetBrains/kotlin", "first/repo"),
                    VALID_ITEM.replace("JetBrains/kotlin", "second/repo"),
                    VALID_ITEM.replace("JetBrains/kotlin", "third/repo"),
                )

            assertEquals(
                listOf("first/repo", "second/repo", "third/repo"),
                parser.parse(body).map { it.fullName },
            )
        }

    @Test
    fun `ownerがJSONのnullならownerAvatarUrlをnullにする`() =
        runTest {
            val body = itemsOf(VALID_ITEM.replace(VALID_OWNER, """"owner": null"""))

            assertEquals(null, parser.parse(body).single().ownerAvatarUrl)
        }

    @Test
    fun `languageがJSONのnullならlanguageをnullにする`() =
        runTest {
            val body = itemsOf(VALID_ITEM.replace(""""language": "Kotlin"""", """"language": null"""))

            assertEquals(null, parser.parse(body).single().language)
        }

    // ---- parse: 必須項目の欠落 ----

    @Test
    fun `itemsキーがなければ失敗する`() =
        runTest {
            val error = parseFailure("""{"total_count":0}""")

            assertTrue(error.message!!.contains("items"))
        }

    @Test
    fun `full_nameキーがなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""full_name": "JetBrains/kotlin",""", "")))

            assertTrue(error.message!!.contains("items[0].full_name"))
        }

    @Test
    fun `ownerキーがなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace("$VALID_OWNER,", "")))

            assertEquals("items[0].owner: 項目がありません", error.message)
        }

    @Test
    fun `languageキーがなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""language": "Kotlin",""", "")))

            assertEquals("items[0].language: 項目がありません", error.message)
        }

    @Test
    fun `ownerがオブジェクトでもavatar_urlがなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""avatar_url": "https://example.com/avatar.png"""", "")))

            assertTrue(error.message!!.contains("items[0].owner.avatar_url"))
        }

    // ---- parse: 明示的なnull ----

    @Test
    fun `full_nameがJSONのnullなら空文字で補完せず失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""full_name": "JetBrains/kotlin"""", """"full_name": null""")))

            assertTrue(error.message!!.contains("items[0].full_name"))
        }

    // ---- parse: 型違い ----

    @Test
    fun `itemsが配列でなければ失敗する`() =
        runTest {
            val error = parseFailure("""{"items":{"full_name":"a/b"}}""")

            assertTrue(error.message!!.contains("配列が必要です"))
        }

    @Test
    fun `full_nameが文字列でなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""full_name": "JetBrains/kotlin"""", """"full_name": 100""")))

            assertTrue(error.message!!.contains("文字列が必要です"))
        }

    @Test
    fun `ownerがオブジェクトでもJSONのnullでもなければ失敗する`() =
        runTest {
            val body = itemsOf(VALID_ITEM.replace(VALID_OWNER, """"owner": "JetBrains""""))

            val error = parseFailure(body)

            assertTrue(error.message!!.contains("オブジェクトまたはnullが必要です"))
        }

    @Test
    fun `languageが文字列でもJSONのnullでもなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""language": "Kotlin"""", """"language": 100""")))

            assertTrue(error.message!!.contains("文字列またはnullが必要です"))
        }

    @Test
    fun `件数が整数でなければ失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""stargazers_count": 53434""", """"stargazers_count": "53434"""")))

            assertTrue(error.message!!.contains("非負整数が必要です"))
        }

    // ---- parse: 件数の値 ----

    @Test
    fun `件数が負の値なら失敗する`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""forks_count": 6421""", """"forks_count": -1""")))

            assertEquals("items[0].forks_count: 非負整数が必要です（負の値）", error.message)
        }

    @Test
    fun `件数が小数なら失敗する`() =
        runTest {
            // 小数として解析された値の型はorg_jsonの実装で異なるため、型名は検証しない。
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""open_issues_count": 444""", """"open_issues_count": 1.5""")))

            assertTrue(error.message!!.contains("items[0].open_issues_count"))
        }

    // ---- parse: 配列の要素 ----

    @Test
    fun `itemsの要素がオブジェクトでなければ失敗する`() =
        runTest {
            val error = parseFailure("""{"items":["JetBrains/kotlin"]}""")

            assertTrue(error.message!!.contains("items[0]: オブジェクトが必要です"))
        }

    // ---- parse: 部分的な成功にしない ----

    @Test
    fun `2件目が不正なら1件目だけを返さず失敗する`() =
        runTest {
            val body =
                itemsOf(
                    VALID_ITEM,
                    VALID_ITEM.replace(""""full_name": "JetBrains/kotlin",""", ""),
                )

            val error = parseFailure(body)

            assertTrue(error.message!!.contains("items[1]"))
        }

    // ---- parse: レスポンス本文がJSONとして不正 ----

    @Test
    fun `JSONとして解析できない本文なら失敗する`() =
        runTest {
            val error = parseFailure("not json")

            assertEquals("レスポンス本文をJSONオブジェクトとして解析できません", error.message)
        }

    @Test
    fun `ルートがJSON配列なら失敗する`() =
        runTest {
            val error = parseFailure("""[{"full_name":"a/b"}]""")

            assertEquals("レスポンス本文をJSONオブジェクトとして解析できません", error.message)
        }

    // ---- レスポンス本文や値がエラーメッセージへ混入しないこと ----

    @Test
    fun `解析できない本文の内容をエラーメッセージに含めない`() =
        runTest {
            val error = parseFailure("""not json but contains SECRET_TOKEN_12345""")

            assertFalse(error.message!!.contains("SECRET_TOKEN_12345"))
        }

    @Test
    fun `型違いの項目の値をエラーメッセージに含めない`() =
        runTest {
            val error = parseFailure(itemsOf(VALID_ITEM.replace(""""language": "Kotlin"""", """"language": 1234567890""")))

            assertFalse(error.message!!.contains("1234567890"))
        }

    // ---- parseSubscribersCount ----

    @Test
    fun `subscribers_countを購読者数として返す`() {
        assertEquals(1489L, parser.parseSubscribersCount("""{"subscribers_count":1489}"""))
    }

    @Test
    fun `subscribers_countが0でも正常な件数として返す`() {
        assertEquals(0L, parser.parseSubscribersCount("""{"subscribers_count":0}"""))
    }

    @Test
    fun `subscribers_countキーがなければ失敗する`() {
        val error = subscribersCountFailure("""{"watchers_count":53434}""")

        assertTrue(error.message!!.contains("subscribers_count"))
    }

    @Test
    fun `subscribers_countが負の値なら失敗する`() {
        val error = subscribersCountFailure("""{"subscribers_count":-1}""")

        assertEquals("subscribers_count: 非負整数が必要です（負の値）", error.message)
    }
}
