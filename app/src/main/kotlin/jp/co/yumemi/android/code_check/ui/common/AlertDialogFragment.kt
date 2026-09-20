/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.common

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val TAG = "AlertDialog"

/**
 * 必須の文字列リソースIDを取得し、0でないことを確認する。
 *
 * リソースの存在や種類までは検証しない。
 *
 * @param key リソースIDを格納したキー
 * @return 0以外のリソースID
 * @throws IllegalArgumentException 取得したIDが0の場合。キーの欠落も含む
 */
@StringRes
private fun Bundle.requireStringRes(key: String): Int {
    val resourceId = getInt(key, 0)
    require(resourceId != 0) { "ダイアログの$key が設定されていません" }
    return resourceId
}

/**
 * 必須キーの存在を確認し、Boolean値を取得する。
 *
 * @param key Boolean値を格納したキー
 * @return 指定キーから読み出した値
 * @throws IllegalArgumentException キーが存在しない場合
 */
private fun Bundle.requireBoolean(key: String): Boolean {
    require(containsKey(key)) { "ダイアログの$key が設定されていません" }
    return getBoolean(key)
}

/**
 * タイトル・本文・ボタンを引数で受け取り、通知を表示するダイアログ。
 *
 * [notificationId]は引数に保持し、復元後も表示対象の通知を識別できる。
 */
class AlertDialogFragment : DialogFragment() {
    /** この表示が対応する通知の識別子。通知を伴わない用途ではnull。 */
    val notificationId: String?
        get() = arguments?.getString(KEY_NOTIFICATION_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        isCancelable = args.requireBoolean(KEY_CANCELABLE)

        // 肯定ボタンは閉じるだけのため、リスナーを設定しない。
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(args.requireStringRes(KEY_TITLE))
            .setMessage(args.requireStringRes(KEY_MESSAGE))
            .setPositiveButton(args.requireStringRes(KEY_POSITIVE_BUTTON), null)
            .create()
    }

    companion object {
        /** 対応する通知の識別子を格納するBundleのキー。 */
        const val KEY_NOTIFICATION_ID = "notificationId"

        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_POSITIVE_BUTTON = "positiveButton"
        private const val KEY_CANCELABLE = "cancelable"

        /**
         * 同じタグのFragmentが存在せず、状態保存前の場合にダイアログの表示を要求する。
         *
         * メインスレッドから呼ぶこと。
         * 追加は非同期のため、呼び出し元は追加が反映されるまでの重複要求を防ぐこと。
         *
         * @param fragmentManager ダイアログを追加するFragmentManager
         * @param tag 表示するダイアログを識別するタグ
         * @param titleRes タイトルの文字列リソースID
         * @param messageRes 本文の文字列リソースID
         * @param positiveButtonRes 肯定ボタンの文字列リソースID。既定はOK
         * @param cancelable 戻る操作などによるキャンセルを許可するか
         * @param notificationId 表示対象の通知ID。識別が不要な場合はnull
         * @return 表示要求を発行した場合はtrue。状態保存後、または同じタグのFragmentが
         *   存在する場合はfalse。trueは表示完了を意味しない
         */
        @MainThread
        fun show(
            fragmentManager: FragmentManager,
            tag: String,
            @StringRes titleRes: Int,
            @StringRes messageRes: Int,
            @StringRes positiveButtonRes: Int = android.R.string.ok,
            cancelable: Boolean = true,
            notificationId: String? = null,
        ): Boolean {
            if (fragmentManager.isStateSaved) {
                Log.d(TAG, "状態保存後のため表示要求を見送った: $tag")
                return false
            }
            if (fragmentManager.findFragmentByTag(tag) != null) return false

            val dialogArguments =
                Bundle().apply {
                    putInt(KEY_TITLE, titleRes)
                    putInt(KEY_MESSAGE, messageRes)
                    putInt(KEY_POSITIVE_BUTTON, positiveButtonRes)
                    putBoolean(KEY_CANCELABLE, cancelable)
                    putString(KEY_NOTIFICATION_ID, notificationId)
                }

            val dialog = AlertDialogFragment()
            dialog.arguments = dialogArguments

            // 呼び出し元でトランザクションが実行中の場合もあるため、追加は非同期で要求する。
            dialog.show(fragmentManager, tag)
            return true
        }
    }
}
