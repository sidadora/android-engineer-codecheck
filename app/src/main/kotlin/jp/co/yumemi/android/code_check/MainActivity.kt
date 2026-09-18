/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Date

/**
 * 起動時に表示されるActivity。
 *
 * レイアウトのNavHostFragmentに、検索画面と詳細画面を切り替えて表示する。
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // targetSdk 35以上のアプリは、Android 15以降の端末で既定がエッジツーエッジ表示になり、
        // システムバーの裏にも描画されるため、重ならないよう余白を補う。
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    companion object {
        /**
         * 最後に検索結果の変換が完了した日時。
         *
         * [RepositorySearchViewModel.searchRepositories]が更新し、未設定のまま参照すると例外になる。
         * Todo : 上記例外は別Issueで対応
         */
        lateinit var lastSearchDate: Date
    }
}
