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
import jp.co.yumemi.android.code_check.MainActivity.Companion.lastSearchDate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.*

/**
 * GitHubのリポジトリ検索APIを呼び出し、画面表示用の[RepositoryItem]に変換する。
 *
 * @property context 言語表示用の文字列リソースを取得するために使う
 */
class RepositorySearchViewModel(
    val context: Context
) : ViewModel() {

    /**
     * [query]でGitHubのリポジトリを検索し、表示用の一覧を返す。
     *
     * `runBlocking`を使っているため、結果を受け取るまで呼び出し元のスレッドをブロックする。
     * 変換が完了すると[MainActivity.lastSearchDate]を更新する。
     * 発生した例外は捕捉せず呼び出し元へ送出する。
     *
     * @param query GitHubのリポジトリ検索APIの`q`パラメータに渡す検索条件
     * @return APIが返した順序のままの検索結果。該当がなければ空のリスト
     */
    fun searchRepositories(query: String): List<RepositoryItem> = runBlocking {
        val client = HttpClient(Android)

        return@runBlocking GlobalScope.async {
            val response: HttpResponse = client.get(
                "https://api.github.com/search/repositories"
            ) {
                header("Accept", "application/vnd.github.v3+json")
                parameter("q", query)
            }

            val jsonBody = JSONObject(response.body<String>())

            val jsonItems = jsonBody.optJSONArray("items")!!

            val repositories = mutableListOf<RepositoryItem>()

            for (i in 0 until jsonItems.length()) {
                val jsonItem = jsonItems.optJSONObject(i)!!
                val fullName = jsonItem.optString("full_name")
                val ownerAvatarUrl =
                    jsonItem.optJSONObject("owner")!!.optString("avatar_url")
                val language = jsonItem.optString("language")
                val stargazersCount = jsonItem.optLong("stargazers_count")
                val watchersCount = jsonItem.optLong("watchers_count")
                val forksCount = jsonItem.optLong("forks_conut")
                val openIssuesCount = jsonItem.optLong("open_issues_count")

                repositories.add(
                    RepositoryItem(
                        fullName = fullName,
                        ownerAvatarUrl = ownerAvatarUrl,
                        languageText = context.getString(
                            R.string.repository_language_format,
                            language
                        ),
                        stargazersCount = stargazersCount,
                        watchersCount = watchersCount,
                        forksCount = forksCount,
                        openIssuesCount = openIssuesCount
                    )
                )
            }

            lastSearchDate = Date()

            return@async repositories.toList()
        }.await()
    }
}

/**
 * 画面に表示する1件のリポジトリ情報。
 *
 * 検索画面から詳細画面へNavigationの引数として渡すため[Parcelable]を実装する。
 * このプロジェクトのビルド環境ではkotlin-parcelizeプラグインを有効にできなかったため
 * [Parcelable]を手動で実装している。
 *
 * @property fullName `owner/repo`形式のリポジトリ名
 * @property ownerAvatarUrl オーナーのアバター画像のURL
 * @property languageText 文字列リソースで書式設定された、言語表示用の文字列
 * @property stargazersCount スター数
 * @property watchersCount GitHub APIの`watchers_count`の値
 * @property forksCount フォーク数
 * @property openIssuesCount GitHub APIの`open_issues_count`の値
 */
data class RepositoryItem(
    val fullName: String,
    val ownerAvatarUrl: String,
    val languageText: String,
    val stargazersCount: Long,
    val watchersCount: Long,
    val forksCount: Long,
    val openIssuesCount: Long,
) : Parcelable {
    /**
     * [Parcel]から各プロパティを復元する。
     *
     * 読み出す順序は[writeToParcel]の書き込み順序と一致させる必要がある。
     *
     * @param parcel [writeToParcel]が書き込んだ内容を保持する[Parcel]
     */
    constructor(parcel: Parcel) : this(
        fullName = parcel.readString()!!,
        ownerAvatarUrl = parcel.readString()!!,
        languageText = parcel.readString()!!,
        stargazersCount = parcel.readLong(),
        watchersCount = parcel.readLong(),
        forksCount = parcel.readLong(),
        openIssuesCount = parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(fullName)
        parcel.writeString(ownerAvatarUrl)
        parcel.writeString(languageText)
        parcel.writeLong(stargazersCount)
        parcel.writeLong(watchersCount)
        parcel.writeLong(forksCount)
        parcel.writeLong(openIssuesCount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RepositoryItem> {
        override fun createFromParcel(parcel: Parcel): RepositoryItem = RepositoryItem(parcel)

        override fun newArray(size: Int): Array<RepositoryItem?> = arrayOfNulls(size)
    }
}
