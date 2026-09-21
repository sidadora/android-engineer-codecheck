/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.yumemi.android.code_check.data.DefaultGitHubRepository
import jp.co.yumemi.android.code_check.data.GitHubApi
import jp.co.yumemi.android.code_check.data.RepositoryDetailDataSource
import jp.co.yumemi.android.code_check.data.RepositoryResponseParser
import jp.co.yumemi.android.code_check.data.RepositorySearchDataSource
import jp.co.yumemi.android.code_check.network.buildImageOkHttpClient

/**
 * アプリ内で共有するAPIクライアントとRepositoryを組み立てて保持する。
 *
 * Coilへ渡すImageLoaderも生成する。通信設定の組み立ては[buildImageOkHttpClient]へ委ねる。
 */
class CodeCheckApplication :
    Application(),
    ImageLoaderFactory {
    /**
     * プロセスの存続中に共有するAPI通信クライアント。明示的なcloseは行わない。
     *
     * 画像取得用のクライアントとは分け、画像用の追加CA設定は適用しない。
     */
    private val httpClient: HttpClient by lazy { HttpClient(Android) }

    /** アプリ内で1つだけ生成し、用途ごとの窓口から共有する。 */
    private val gitHubRepository: DefaultGitHubRepository by lazy {
        DefaultGitHubRepository(
            api = GitHubApi(httpClient),
            parser = RepositoryResponseParser(),
        )
    }

    /** 検索画面が使うデータ取得の窓口。 */
    val repositorySearchDataSource: RepositorySearchDataSource get() = gitHubRepository

    /** 詳細画面が使うデータ取得の窓口。 */
    val repositoryDetailDataSource: RepositoryDetailDataSource get() = gitHubRepository

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient { buildImageOkHttpClient(resources) }
            .build()
}
