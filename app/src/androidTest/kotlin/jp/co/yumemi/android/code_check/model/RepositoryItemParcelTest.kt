/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.model

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 書き込みと読み出しがずれた場合に必ず値が食い違うよう、項目ごとに異なる値を使う。 */
private val FILLED_ITEM =
    RepositoryItem(
        fullName = "JetBrains/kotlin",
        ownerAvatarUrl = "https://example.com/avatar.png",
        language = "Kotlin",
        stargazersCount = 111,
        forksCount = 222,
        openIssuesCount = 333,
    )

/**
 * [Parcel]へ書き出し、読み直した結果を返す。
 *
 * @param item 書き出す値
 * @return 読み直した値
 */
private fun roundTrip(item: RepositoryItem): RepositoryItem {
    val parcel = Parcel.obtain()
    return try {
        item.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        RepositoryItem.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}

/**
 * [RepositoryItem]の[Parcel]への書き込みと読み出しが対称であることを確認する。
 *
 * 手書きの実装のため、読み書きの順序がずれてもコンパイルは通る。
 * 項目ごとに異なる値を使い、ずれた場合に値の食い違いとして現れるようにする。
 */
@RunWith(AndroidJUnit4::class)
class RepositoryItemParcelTest {
    @Test
    fun `全項目が埋まっていれば同じ値へ復元する`() {
        assertEquals(FILLED_ITEM, roundTrip(FILLED_ITEM))
    }

    @Test
    fun `オーナー画像がなくても他の項目がずれない`() {
        val item = FILLED_ITEM.copy(ownerAvatarUrl = null)

        assertEquals(item, roundTrip(item))
    }

    @Test
    fun `言語がなくても他の項目がずれない`() {
        val item = FILLED_ITEM.copy(language = null)

        assertEquals(item, roundTrip(item))
    }

    @Test
    fun `オーナー画像と言語の両方がなくても復元する`() {
        val item = FILLED_ITEM.copy(ownerAvatarUrl = null, language = null)

        assertEquals(item, roundTrip(item))
    }

    @Test
    fun `件数が0でも0のまま復元する`() {
        val item = FILLED_ITEM.copy(stargazersCount = 0, forksCount = 0, openIssuesCount = 0)

        assertEquals(item, roundTrip(item))
    }

    @Test
    fun `件数がIntの範囲を超えても復元する`() {
        val beyondInt = Int.MAX_VALUE.toLong() + 1
        val item = FILLED_ITEM.copy(stargazersCount = beyondInt)

        assertEquals(beyondInt, roundTrip(item).stargazersCount)
    }
}
