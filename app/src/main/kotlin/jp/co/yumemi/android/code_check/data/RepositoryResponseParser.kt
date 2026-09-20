/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.data

import jp.co.yumemi.android.code_check.model.RepositoryItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** JSONObject.NULLをKotlinのnullへ変換し、それ以外の値はそのまま返す。 */
private fun Any?.asValueOrNull(): Any? = if (this == null || this == JSONObject.NULL) null else this

/** 値がJSONObjectなら返し、nullまたは別の型ならnullを返す。 */
private fun Any?.asJsonObjectOrNull(): JSONObject? = asValueOrNull() as? JSONObject

/** 値そのものを含めず、エラーメッセージ用の型名を返す。 */
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

/**
 * エラーメッセージに使う項目のパスを組み立てる。
 *
 * @param parentPath 親要素のパス。ルートの場合は空文字
 * @param name 項目名
 * @return 親のパスと項目名をドットで連結した文字列。ルートの場合は項目名のみ
 */
private fun fieldPath(
    parentPath: String,
    name: String,
): String = if (parentPath.isEmpty()) name else "$parentPath.$name"

/**
 * 必須の文字列項目を取得する。
 *
 * @param parentPath エラーメッセージに使う親要素のパス
 * @param name 取得する項目名
 * @return 項目の文字列
 * @throws JSONException 項目が欠落している、JSONのnull、または文字列以外の場合
 */
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
 * 必須の件数項目を非負のLongとして取得する。
 *
 * IntまたはLongの値だけを受け入れ、それ以外の型からの数値変換は行わない。
 *
 * @param parentPath エラーメッセージに使う親要素のパス
 * @param name 取得する項目名
 * @return 非負の件数
 * @throws JSONException 項目が欠落している、JSONのnull、
 *   Int・Long以外の型、または負の値の場合
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

/**
 * 必須の配列項目を取得する。
 *
 * @param parentPath エラーメッセージに使う親要素のパス
 * @param name 取得する項目名
 * @return 項目のJSON配列
 * @throws JSONException 項目が欠落している、JSONのnull、または配列以外の場合
 */
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
 * JSONのnullを許容する必須のオブジェクト項目を取得する。
 *
 * @param parentPath エラーメッセージに使う親要素のパス
 * @param name 取得する項目名
 * @return 項目のJSONObject。JSONのnullの場合はnull
 * @throws JSONException 項目が欠落している、またはオブジェクト・JSONのnull以外の場合
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

/**
 * リポジトリ検索APIのレスポンスを検証し、[RepositoryItem]へ変換する。
 *
 * 表示用の文字列は組み立てず、データのまま返す。文言の決定は画面側の責務とする。
 * 解析できない場合は[JSONException]を送出し、結果型への変換は呼び出し元が行う。
 */
class RepositoryResponseParser {
    /**
     * レスポンス本文を検証し、リポジトリ一覧へ変換する。
     *
     * 必須項目の欠落・型違いは異常とみなし、空文字や0で補完しない。
     * 1件でも変換できない要素があれば例外とし、部分的な成功にはしない。
     *
     * 要素ごとにキャンセルを確認する。ただし[JSONObject]の生成は本文全体を一度に解析する
     * 単一の呼び出しで中断点がないため、その最中はキャンセルできない。
     *
     * @param responseBody リポジトリ検索APIのレスポンス本文
     * @return APIが返した順序のままの検索結果。該当がなければ空のリスト
     * @throws JSONException レスポンスが想定の形式でない場合
     */
    suspend fun parse(responseBody: String): List<RepositoryItem> {
        val root =
            try {
                JSONObject(responseBody)
            } catch (_: JSONException) {
                // JSONTokenerの例外メッセージには入力全体が含まれる。
                // ログに本文が出ないよう、原因を連鎖させずメッセージを差し替える。
                throw JSONException("レスポンス本文をJSONオブジェクトとして解析できません")
            }

        val jsonItems = root.requireArray("", "items")

        val items = ArrayList<RepositoryItem>(jsonItems.length())
        for (index in 0 until jsonItems.length()) {
            currentCoroutineContext().ensureActive()

            val path = "items[$index]"
            // 添字が範囲内でも、その要素がJSONObjectである保証はない。
            val value = jsonItems.opt(index)
            val jsonItem =
                value.asJsonObjectOrNull()
                    ?: throw JSONException(
                        "$path: オブジェクトが必要です（実際は${value.jsonTypeName()}）",
                    )
            items += toRepositoryItem(jsonItem, path)
        }
        return items
    }

    /**
     * 検索結果1件分のJSONを[RepositoryItem]へ変換する。
     *
     * ownerとlanguageはJSONのnullを許容するが、キーの欠落は拒否する。
     * ownerがオブジェクトの場合は、avatar_urlも検証する。
     *
     * @param jsonItem items配列に含まれる1件分のJSONオブジェクト
     * @param path エラーメッセージに使う要素の位置
     * @return 検証済みのリポジトリ情報
     * @throws JSONException 必須項目の欠落、許容しないnull、型違い、または不正な件数がある場合
     */
    private fun toRepositoryItem(
        jsonItem: JSONObject,
        path: String,
    ): RepositoryItem {
        val fullName = jsonItem.requireString(path, "full_name")
        val ownerAvatarUrl =
            jsonItem
                .requireNullableObject(path, "owner")
                ?.requireString("$path.owner", "avatar_url")
        val language = jsonItem.requireNullableString(path, "language")
        val stargazersCount = jsonItem.requireCount(path, "stargazers_count")
        val watchersCount = jsonItem.requireCount(path, "watchers_count")
        val forksCount = jsonItem.requireCount(path, "forks_count")
        val openIssuesCount = jsonItem.requireCount(path, "open_issues_count")

        return RepositoryItem(
            fullName = fullName,
            ownerAvatarUrl = ownerAvatarUrl,
            language = language,
            stargazersCount = stargazersCount,
            watchersCount = watchersCount,
            forksCount = forksCount,
            openIssuesCount = openIssuesCount,
        )
    }
}
