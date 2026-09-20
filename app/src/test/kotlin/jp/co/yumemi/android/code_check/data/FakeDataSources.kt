/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.data

import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.CompletableDeferred

/**
 * 検索の取得先を通信なしで差し替える。
 *
 * 既定では[result]を即座に返す。[suspendOn]で指定した検索条件だけは、
 * [complete]が呼ばれるまで待つ。完了の順序を入れ替える検証に使う。
 */
class FakeRepositorySearchDataSource : RepositorySearchDataSource {
    /** [suspendOn]で保留していない検索条件に対して返す結果。 */
    var result: FetchResult<List<RepositoryItem>> = FetchResult.Success(emptyList())

    /** 呼ばれた検索条件を呼び出し順に記録する。呼び出し回数の確認に使う。 */
    val queries = mutableListOf<String>()

    private val gates = mutableMapOf<String, CompletableDeferred<FetchResult<List<RepositoryItem>>>>()

    /**
     * [query]の検索を、[complete]が呼ばれるまで完了させないようにする。
     *
     * @param query 保留する検索条件
     */
    fun suspendOn(query: String) {
        gates[query] = CompletableDeferred()
    }

    /**
     * [suspendOn]で保留した検索を完了させる。
     *
     * @param query 保留していた検索条件
     * @param value その検索の結果として返す値
     * @throws IllegalArgumentException 保留していない検索条件を指定した場合
     */
    fun complete(
        query: String,
        value: FetchResult<List<RepositoryItem>>,
    ) {
        val gate = requireNotNull(gates[query]) { "保留していない検索条件です: $query" }
        gate.complete(value)
    }

    override suspend fun searchRepositories(query: String): FetchResult<List<RepositoryItem>> {
        queries += query
        return gates[query]?.await() ?: result
    }
}

/**
 * 購読者数の取得先を通信なしで差し替える。
 *
 * 取得結果は[result]で指定する。呼ばれたリポジトリ名を[fullNames]に記録する。
 */
class FakeRepositoryDetailDataSource : RepositoryDetailDataSource {
    /** 取得結果として返す値。 */
    var result: FetchResult<Long> = FetchResult.Success(0L)

    /** 呼ばれたリポジトリ名を呼び出し順に記録する。呼び出し回数の確認に使う。 */
    val fullNames = mutableListOf<String>()

    override suspend fun getSubscribersCount(fullName: String): FetchResult<Long> {
        fullNames += fullName
        return result
    }
}
