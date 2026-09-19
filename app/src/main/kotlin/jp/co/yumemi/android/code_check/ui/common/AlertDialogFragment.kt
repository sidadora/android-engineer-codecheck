/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.common

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val TAG = "AlertDialog"

/** 必須IDの欠落・0は設定不備として扱う。 */
@StringRes
private fun Bundle.requireStringRes(key: String): Int {
    val resourceId = getInt(key, 0)
    require(resourceId != 0) { "ダイアログの$key が設定されていません" }
    return resourceId
}

private fun Bundle.requireBoolean(key: String): Boolean {
    require(containsKey(key)) { "ダイアログの$key が設定されていません" }
    return getBoolean(key)
}

@StringRes
private fun Bundle.optionalStringRes(key: String): Int? {
    if (!containsKey(key)) return null
    return requireStringRes(key)
}

/**
 * 結果を通知するダイアログ。
 *
 * どの通知に対して表示したかを[notificationId]で識別できる。復元後も引数から読めるため、
 * 呼び出し元は「今どの通知を表示中か」を判定できる。
 */
class AlertDialogFragment : DialogFragment() {
    /** この表示が対応する通知の識別子。通知を伴わない用途ではnull。 */
    val notificationId: String?
        get() = arguments?.getString(KEY_NOTIFICATION_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        isCancelable = args.requireBoolean(KEY_CANCELABLE)

        val builder =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(args.requireStringRes(KEY_TITLE))
                .setMessage(args.requireStringRes(KEY_MESSAGE))
                .setPositiveButton(args.requireStringRes(KEY_POSITIVE_BUTTON)) { _, _ ->
                    sendResult(RESULT_POSITIVE)
                }

        val negativeButtonRes = args.optionalStringRes(KEY_NEGATIVE_BUTTON)
        if (negativeButtonRes != null) {
            builder.setNegativeButton(negativeButtonRes) { _, _ ->
                sendResult(RESULT_NEGATIVE)
            }
        }

        return builder.create()
    }

    /** 戻る操作・画面外タップによるキャンセルを通知する。 */
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        sendResult(RESULT_CANCELLED)
    }

    private fun sendResult(result: String) {
        val requestKey = requireArguments().getString(KEY_REQUEST_KEY) ?: return
        setFragmentResult(
            requestKey,
            Bundle().apply {
                putString(KEY_RESULT, result)
                putString(KEY_NOTIFICATION_ID, notificationId)
            },
        )
    }

    companion object {
        const val RESULT_POSITIVE = "positive"
        const val RESULT_NEGATIVE = "negative"
        const val RESULT_CANCELLED = "cancelled"

        /** 操作結果（RESULT_*）を格納するBundleのキー。 */
        const val KEY_RESULT = "result"

        /** 対応する通知の識別子を格納するBundleのキー。 */
        const val KEY_NOTIFICATION_ID = "notificationId"

        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_POSITIVE_BUTTON = "positiveButton"
        private const val KEY_NEGATIVE_BUTTON = "negativeButton"
        private const val KEY_CANCELABLE = "cancelable"
        private const val KEY_REQUEST_KEY = "requestKey"

        /**
         * 同じタグのダイアログが無ければ表示を要求する。
         *
         * コミットは非同期のため、この関数が`true`を返した時点ではまだ追加されていない。
         * 呼び出し元は追加が完了するまでの重複要求を自分で防ぐこと。
         *
         * @param notificationId この表示が対応する通知の識別子
         * @param negativeButtonRes nullなら否定ボタンを表示しない
         * @param requestKey nullなら操作結果を通知しない
         * @return 表示を要求できた場合はtrue。状態保存後や同じタグが既にある場合はfalse
         */
        @MainThread
        fun show(
            fragmentManager: FragmentManager,
            tag: String,
            @StringRes titleRes: Int,
            @StringRes messageRes: Int,
            @StringRes positiveButtonRes: Int = android.R.string.ok,
            @StringRes negativeButtonRes: Int? = null,
            cancelable: Boolean = true,
            requestKey: String? = null,
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
                    putString(KEY_REQUEST_KEY, requestKey)
                    putString(KEY_NOTIFICATION_ID, notificationId)
                }
            if (negativeButtonRes != null) {
                dialogArguments.putInt(KEY_NEGATIVE_BUTTON, negativeButtonRes)
            }

            val dialog = AlertDialogFragment()
            dialog.arguments = dialogArguments
            // StateFlowの購読から呼ぶため、トランザクション実行中でないことを前提にできない。
            // 同期コミットは使わず、非同期コミットで要求する。
            dialog.show(fragmentManager, tag)
            return true
        }
    }
}
