/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * リポジトリ検索APIのレスポンスを検証し、表示用の[RepositoryItem]へ変換する。
 *
 * 表示用の文字列はここで組み立てるため、文字列リソースを引くための[Context]を保持する。
 * 解析できない場合は[JSONException]を送出し、[RepositorySearchResult]への変換は呼び出し元が行う。
 *
 * @property context 言語表示用の文字列リソースを取得するために使う
 */
internal class RepositoryResponseParser(
    private val context: Context,
) {
    /**
     * レスポンス本文を解析し、表示用の一覧へ変換する。
     *
     * 必須項目の欠落・型違いは異常とみなし、空文字や0で補完しない。
     * 1件でも変換できない要素があれば例外とし、部分的な成功にはしない。
     *
     * @param responseBody リポジトリ検索APIのレスポンス本文
     * @return APIが返した順序のままの検索結果。該当がなければ空のリスト
     * @throws JSONException レスポンスが想定の形式でない場合
     */
    fun parse(responseBody: String): List<RepositoryItem> {
        val root =
            try {
                JSONObject(responseBody)
            } catch (e: JSONException) {
                // JSONTokenerの例外メッセージには入力全体が含まれる。
                // ログに本文が出ないよう、原因を連鎖させずメッセージを差し替える。
                throw JSONException("レスポンス本文をJSONオブジェクトとして解析できません")
            }

        val jsonItems = root.requireArray("", "items")

        return List(jsonItems.length()) { index ->
            val itemPath = "items[$index]"
            // 添字が範囲内でも、その要素がJSONObjectである保証はない。
            val value = jsonItems.opt(index)
            val jsonItem =
                value.asJsonObjectOrNull()
                    ?: throw JSONException("$itemPath: オブジェクトが必要です（実際は${value.jsonTypeName()}）")
            toRepositoryItem(jsonItem, itemPath)
        }
    }

    /**
     * 検索結果1件分のJSONを、表示用の[RepositoryItem]に変換する。
     *
     * `owner`と`language`はAPIの契約でnullが許容されるため、正常な値として扱う。
     * いずれもキー自体の欠落は異常とする。
     *
     * @param jsonItem リポジトリ検索APIのレスポンス内、`items`配列の1要素
     * @param itemPath エラーメッセージに含める、この要素の位置
     * @return 画面表示に使う1件分のリポジトリ情報
     * @throws JSONException 必須項目の欠落や型違いがある場合
     */
    private fun toRepositoryItem(
        jsonItem: JSONObject,
        itemPath: String,
    ): RepositoryItem {
        val fullName = jsonItem.requireString(itemPath, "full_name")
        val ownerAvatarUrl =
            jsonItem
                .requireNullableObject(itemPath, "owner")
                ?.requireString("$itemPath.owner", "avatar_url")
        val language = jsonItem.requireNullableString(itemPath, "language")
        val stargazersCount = jsonItem.requireCount(itemPath, "stargazers_count")
        val watchersCount = jsonItem.requireCount(itemPath, "watchers_count")
        val forksCount = jsonItem.requireCount(itemPath, "forks_count")
        val openIssuesCount = jsonItem.requireCount(itemPath, "open_issues_count")

        return RepositoryItem(
            fullName = fullName,
            ownerAvatarUrl = ownerAvatarUrl,
            languageText =
                if (language == null) {
                    context.getString(R.string.repository_language_unknown)
                } else {
                    context.getString(R.string.repository_language_format, language)
                },
            stargazersCount = stargazersCount,
            watchersCount = watchersCount,
            forksCount = forksCount,
            openIssuesCount = openIssuesCount,
        )
    }
}

/**
 * JSONのnullと欠落をどちらもnullとして返す。
 *
 * `JSONObject.NULL`はtoString()が"null"を返すため、`optString`等では文字列"null"になってしまう。
 */
private fun Any?.asValueOrNull(): Any? = if (this == null || this == JSONObject.NULL) null else this

private fun Any?.asJsonObjectOrNull(): JSONObject? = asValueOrNull() as? JSONObject

/**
 * エラーメッセージに載せる型の名前。
 *
 * 値そのものは、想定外のオブジェクトがログへ展開されるのを避けるため含めない。
 */
private fun Any?.jsonTypeName(): String =
    when (asValueOrNull()) {
        null -> "null"
        is String -> "文字列"
        is Int, is Long -> "整数"
        is Double -> "浮動小数点数"
        is Boolean -> "真偽値"
        is JSONObject -> "オブジェクト"
        is JSONArray -> "配列"
        else -> "不明な型"
    }

/** エラーメッセージ用に、親のパスと項目名をつなぐ。 */
private fun fieldPath(
    parentPath: String,
    name: String,
): String = if (parentPath.isEmpty()) name else "$parentPath.$name"

/** 必須の文字列項目を取り出す。欠落・JSONのnull・文字列以外はいずれも異常として扱う。 */
private fun JSONObject.requireString(
    parentPath: String,
    name: String,
): String {
    val value = opt(name).asValueOrNull()
    return value as? String
        ?: throw JSONException(
            "${fieldPath(parentPath, name)}: 文字列が必要です（実際は${value.jsonTypeName()}）",
        )
}

/**
 * 必須の件数項目を取り出す。
 *
 * IntとLongに解析された非負の値だけを受け入れる。
 * 小数・指数表記・Longの範囲外はDoubleとして解析されるため、値が変化しないよう異常として扱う。
 */
private fun JSONObject.requireCount(
    parentPath: String,
    name: String,
): Long {
    val path = fieldPath(parentPath, name)
    val value = opt(name).asValueOrNull()
    val count =
        when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw JSONException("$path: 非負整数が必要です（実際は${value.jsonTypeName()}）")
        }
    if (count < 0) throw JSONException("$path: 非負整数が必要です（負の値）")
    return count
}

/** 必須の配列項目を取り出す。 */
private fun JSONObject.requireArray(
    parentPath: String,
    name: String,
): JSONArray {
    val value = opt(name).asValueOrNull()
    return value as? JSONArray
        ?: throw JSONException(
            "${fieldPath(parentPath, name)}: 配列が必要です（実際は${value.jsonTypeName()}）",
        )
}

/**
 * 必須だがJSONのnullが許容される文字列項目を取り出す。
 *
 * キーの欠落は異常とし、JSONのnullはnullを返す。文字列以外の値は異常として扱う。
 */
private fun JSONObject.requireNullableString(
    parentPath: String,
    name: String,
): String? {
    val path = fieldPath(parentPath, name)
    if (!has(name)) throw JSONException("$path: 項目がありません")
    val value = opt(name).asValueOrNull() ?: return null
    return value as? String
        ?: throw JSONException("$path: 文字列またはnullが必要です（実際は${value.jsonTypeName()}）")
}

/**
 * 必須だがJSONのnullが許容されるオブジェクト項目を取り出す。
 *
 * キーの欠落は異常とし、JSONのnullはnullを返す。オブジェクト以外の値は異常として扱う。
 */
private fun JSONObject.requireNullableObject(
    parentPath: String,
    name: String,
): JSONObject? {
    val path = fieldPath(parentPath, name)
    if (!has(name)) throw JSONException("$path: 項目がありません")
    val value = opt(name).asValueOrNull() ?: return null
    return value as? JSONObject
        ?: throw JSONException("$path: オブジェクトまたはnullが必要です（実際は${value.jsonTypeName()}）")
}
