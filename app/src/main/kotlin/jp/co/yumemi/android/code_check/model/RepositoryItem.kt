/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.model

import android.os.Parcel
import android.os.Parcelable

/**
 * 検索結果1件分のリポジトリ情報。
 *
 * 検索画面から詳細画面へNavigationの引数として渡すため、[Parcelable]を実装する。
 * このプロジェクトのビルド環境ではkotlin-parcelizeプラグインを有効にできなかったため
 * [android.os.Parcelable]を手動で実装している。
 *
 * @property fullName `owner/repo`形式のリポジトリ名
 * @property ownerAvatarUrl オーナーのアバター画像のURL。オーナー情報がない場合はnull
 * @property language GitHub APIのlanguageの値。設定がない場合はnull
 * @property stargazersCount スター数
 * @property watchersCount GitHub APIのwatchers_countの値
 * @property forksCount フォーク数
 * @property openIssuesCount GitHub APIのopen_issues_countの値
 */
data class RepositoryItem(
    val fullName: String,
    val ownerAvatarUrl: String?,
    val language: String?,
    val stargazersCount: Long,
    val watchersCount: Long,
    val forksCount: Long,
    val openIssuesCount: Long,
) : Parcelable {
    /**
     * [android.os.Parcel]から各プロパティを復元する。
     *
     * 読み出す順序は[writeToParcel]の書き込み順序と一致させる必要がある。
     * [writeToParcel]が非nullで書き込むのは[fullName]だけで、
     * [ownerAvatarUrl]と[language]はnullを許容する。この契約を根拠に読み出す。
     *
     * @param parcel [writeToParcel]が書き込んだ内容を保持する[android.os.Parcel]
     */
    constructor(parcel: Parcel) : this(
        fullName = parcel.readString()!!,
        ownerAvatarUrl = parcel.readString(),
        language = parcel.readString(),
        stargazersCount = parcel.readLong(),
        watchersCount = parcel.readLong(),
        forksCount = parcel.readLong(),
        openIssuesCount = parcel.readLong(),
    )

    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeString(fullName)
        parcel.writeString(ownerAvatarUrl)
        parcel.writeString(language)
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
