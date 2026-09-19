/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

/**
 * リポジトリ検索の結果。
 *
 * 該当0件という正常な結果と、通信・解析の失敗を区別するために使う。
 */
sealed interface RepositorySearchResult {
    /**
     * 検索が成功した状態。
     *
     * @property items APIが返した順序のままの検索結果。該当がなければ空のリスト
     */
    data class Success(
        val items: List<RepositoryItem>,
    ) : RepositorySearchResult

    /** 通信またはレスポンスの解析に失敗し、結果を取得できなかった状態。 */
    data object Failure : RepositorySearchResult
}
