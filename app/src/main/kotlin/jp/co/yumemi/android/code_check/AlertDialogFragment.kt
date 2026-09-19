/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val TAG = "AlertDialog"

/**
 * 結果を通知するダイアログ
 */
class AlertDialogFragment : DialogFragment() {
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
        setFragmentResult(requestKey, bundleOf(KEY_RESULT to result))
    }

    companion object {
        const val RESULT_POSITIVE = "positive"
        const val RESULT_NEGATIVE = "negative"
        const val RESULT_CANCELLED = "cancelled"

        /** 操作結果（RESULT_*）を格納するBundleのキー。 */
        const val KEY_RESULT = "result"

        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_POSITIVE_BUTTON = "positiveButton"
        private const val KEY_NEGATIVE_BUTTON = "negativeButton"
        private const val KEY_CANCELABLE = "cancelable"
        private const val KEY_REQUEST_KEY = "requestKey"

        /**
         * 同じFragmentManagerに同じタグがなければ、同期的に表示する。
         *
         * 接続済みで、トランザクション実行中でないFragmentManagerから呼ぶこと。
         * 状態保存後の要求は破棄するため、表示の延期・再送が不要な通知に使う。
         *
         * @param negativeButtonRes nullなら否定ボタンを表示しない
         * @param requestKey nullなら操作結果を通知しない
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
        ) {
            if (fragmentManager.isStateSaved) {
                Log.d(TAG, "状態保存後のため表示要求を破棄した: $tag")
                return
            }
            if (fragmentManager.findFragmentByTag(tag) != null) return

            val dialogArguments =
                bundleOf(
                    KEY_TITLE to titleRes,
                    KEY_MESSAGE to messageRes,
                    KEY_POSITIVE_BUTTON to positiveButtonRes,
                    KEY_CANCELABLE to cancelable,
                    KEY_REQUEST_KEY to requestKey,
                )
            if (negativeButtonRes != null) {
                dialogArguments.putInt(KEY_NEGATIVE_BUTTON, negativeButtonRes)
            }

            val dialog = AlertDialogFragment()
            dialog.arguments = dialogArguments
            dialog.showNow(fragmentManager, tag)
        }
    }
}

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
private fun Bundle.optionalStringRes(key: String): Int? =
    if (containsKey(key)) requireStringRes(key) else null
