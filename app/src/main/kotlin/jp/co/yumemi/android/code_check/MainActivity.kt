/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.appbar.MaterialToolbar

/**
 * 起動時に表示されるActivity。
 *
 * レイアウトのNavHostFragmentに、検索画面と詳細画面を切り替えて表示する。
 * 画面名と戻る操作は、レイアウトのToolbarとNavigationへ委ねる。
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    /** 戻る矢印を出さない開始位置を保持する設定。[onSupportNavigateUp]と共有する。 */
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画面名を中央へ寄せるため、テーマのActionBarではなくレイアウトのToolbarを使う。
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navController = navController()
        // 開始位置の検索画面では戻る矢印を出さない。
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // targetSdk 35以上のアプリは、Android 15以降の端末で既定がエッジツーエッジ表示になり、
        // システムバーの裏にも描画されるため、重ならないよう余白を補う。
        // 上端だけはToolbarに持たせ、Toolbarの背景をステータスバーの裏まで広げる。
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            toolbar.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    /**
     * Toolbarの戻る矢印を、システムの戻る操作と同じ経路へ流す。
     *
     * @return Navigationが戻り先を処理した場合はtrue
     */
    override fun onSupportNavigateUp(): Boolean = navController().navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    /**
     * レイアウトのNavHostFragmentが持つNavControllerを返す。
     *
     * FragmentContainerViewで配置しているため、Viewからではなく
     * FragmentManagerから取得する。
     */
    private fun navController(): NavController {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        return requireNotNull(navHost).findNavController()
    }
}
