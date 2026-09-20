/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package android.util

import java.util.concurrent.CopyOnWriteArrayList

/**
 * JVMのUnitTestで[android.util.Log]を差し替えるスタブ。
 *
 * モック用のandroid.jarは呼び出すと例外を送出するため、本番コードのログ出力を通過できない。
 * 同じ完全修飾名でテストソースへ置き、本番コードが呼ぶメソッドだけを実装する。
 *
 * 記録はJVM内で共有され、テストをまたいで残る。内容を検証する場合は[clear]で消してから実行すること。
 */
object Log {
    /**
     * 記録した1件のログ。
     *
     * @property tag 呼び出し時のタグ
     * @property message 呼び出し時のメッセージ
     */
    data class Entry(
        val tag: String,
        val message: String,
    )

    /** 記録するスレッドを限定しないため、並行に追加できるリストを使う。 */
    private val recorded = CopyOnWriteArrayList<Entry>()

    /**
     * 警告を記録する。出力は行わない。
     *
     * 本番コードはandroid.jarに対してコンパイルされ、`invokestatic`で呼び出す。
     * objectの通常のメソッドはインスタンスメソッドになるため、[JvmStatic]でstaticな入口を作る。
     *
     * @param tag 呼び出し元が指定したタグ
     * @param message 呼び出し元が指定したメッセージ
     * @return 常に0。呼び出し元が戻り値を使わないため意味を持たせない
     */
    @JvmStatic
    fun w(
        tag: String,
        message: String,
    ): Int {
        recorded += Entry(tag, message)
        return 0
    }

    /** 記録したログ。以降の記録に影響されないコピーを返す。 */
    fun entries(): List<Entry> = recorded.toList()

    /** 記録を消す。 */
    fun clear() = recorded.clear()
}
