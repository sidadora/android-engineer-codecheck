/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import jp.co.yumemi.android.code_check.TopActivity.Companion.lastSearchDate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.*

/**
 * TwoFragment で使う
 */
class OneViewModel(
    val context: Context
) : ViewModel() {

    // 検索結果
    fun searchResults(inputText: String): List<item> = runBlocking {
        val client = HttpClient(Android)

        return@runBlocking GlobalScope.async {
            val response: HttpResponse = client.get("https://api.github.com/search/repositories") {
                header("Accept", "application/vnd.github.v3+json")
                parameter("q", inputText)
            }

            val jsonBody = JSONObject(response.body<String>())

            val jsonItems = jsonBody.optJSONArray("items")!!

            val items = mutableListOf<item>()

            /**
             * アイテムの個数分ループする
             */
            for (i in 0 until jsonItems.length()) {
                val jsonItem = jsonItems.optJSONObject(i)!!
                val name = jsonItem.optString("full_name")
                val ownerIconUrl = jsonItem.optJSONObject("owner")!!.optString("avatar_url")
                val language = jsonItem.optString("language")
                val stargazersCount = jsonItem.optLong("stargazers_count")
                val watchersCount = jsonItem.optLong("watchers_count")
                val forksCount = jsonItem.optLong("forks_conut")
                val openIssuesCount = jsonItem.optLong("open_issues_count")

                items.add(
                    item(
                        name = name,
                        ownerIconUrl = ownerIconUrl,
                        language = context.getString(R.string.written_language, language),
                        stargazersCount = stargazersCount,
                        watchersCount = watchersCount,
                        forksCount = forksCount,
                        openIssuesCount = openIssuesCount
                    )
                )
            }

            lastSearchDate = Date()

            return@async items.toList()
        }.await()
    }
}

/**
 * AGP 9の環境では kotlin-parcelize プラグインが機能せず、ビルドに失敗する
 * id は解決されるが compiler plugin が登録されず Unresolved reference になる。
 * そのため @Parcelize を使わず Parcelable を手動で実装
 */
data class item(
    val name: String,
    val ownerIconUrl: String,
    val language: String,
    val stargazersCount: Long,
    val watchersCount: Long,
    val forksCount: Long,
    val openIssuesCount: Long,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        name = parcel.readString()!!,
        ownerIconUrl = parcel.readString()!!,
        language = parcel.readString()!!,
        stargazersCount = parcel.readLong(),
        watchersCount = parcel.readLong(),
        forksCount = parcel.readLong(),
        openIssuesCount = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(ownerIconUrl)
        parcel.writeString(language)
        parcel.writeLong(stargazersCount)
        parcel.writeLong(watchersCount)
        parcel.writeLong(forksCount)
        parcel.writeLong(openIssuesCount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<item> {
        override fun createFromParcel(parcel: Parcel): item = item(parcel)
        override fun newArray(size: Int): Array<item?> = arrayOfNulls(size)
    }
}