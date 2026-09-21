/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check.ui.search

import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.HumanReadables
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.yumemi.android.code_check.MainActivity
import jp.co.yumemi.android.code_check.R
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 通信の完了を待つ上限。実測では数秒で終わるが、回線が遅い場合も吸収できる長さにする。 */
private const val WAIT_TIMEOUT_MILLIS = 20_000L

/** 一覧の件数を確認する間隔。 */
private const val POLL_INTERVAL_MILLIS = 100L

/** 結果の内容に依存しないよう、常に多数ヒットする条件を使う。 */
private const val SEARCH_QUERY = "kotlin"

/**
 * 一覧に項目が表示されるまでメインスレッドを進める。
 *
 * 検索は`viewModelScope`のコルーチンで実行され、Espressoの同期対象にならない。
 * IdlingResourceを使うには本番コードへ登録口が必要になるため、
 * アダプターの件数を直接監視する。
 *
 * @param timeoutMillis 待つ上限
 * @throws PerformException 上限までに1件も表示されなかった場合
 */
private fun waitForItems(timeoutMillis: Long): ViewAction =
    object : ViewAction {
        override fun getConstraints() = isAssignableFrom(RecyclerView::class.java)

        override fun getDescription() = "一覧に項目が表示されるまで待つ"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val recyclerView = view as RecyclerView
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            do {
                if ((recyclerView.adapter?.itemCount ?: 0) > 0) return
                uiController.loopMainThreadForAtLeast(POLL_INTERVAL_MILLIS)
            } while (SystemClock.uptimeMillis() < deadline)

            throw PerformException
                .Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .withCause(IllegalStateException("${timeoutMillis}ミリ秒以内に項目が表示されませんでした"))
                .build()
        }
    }

/**
 * 一覧の先頭の項目をタップする。
 *
 * 一覧の各項目は同じIDを持つため、IDだけでは一意に指定できない。
 * 位置で取り出した項目のクリックを直接実行することで、
 * スクロール位置や表示割合に左右されないようにする。
 *
 * @throws PerformException 先頭の項目を取得できなかった場合
 */
private fun clickFirstItem(): ViewAction =
    object : ViewAction {
        override fun getConstraints() = isAssignableFrom(RecyclerView::class.java)

        override fun getDescription() = "一覧の先頭の項目をタップする"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val recyclerView = view as RecyclerView
            val holder =
                recyclerView.findViewHolderForAdapterPosition(0)
                    ?: throw PerformException
                        .Builder()
                        .withActionDescription(description)
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(IllegalStateException("先頭の項目を取得できませんでした"))
                        .build()

            holder.itemView.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

/**
 * Toolbarが表示している画面名のView。
 *
 * 画面名のTextViewにはIDが振られないため、Toolbarの直接の子であることで絞り込む。
 */
private fun toolbarTitle(): Matcher<View> = allOf(instanceOf(TextView::class.java), withParent(withId(R.id.toolbar)), isDisplayed())

/**
 * Toolbarの戻る矢印。
 *
 * 開始位置ではToolbarがこのViewを生成しないため、存在の有無で出し分けを判定できる。
 */
private fun toolbarNavigationButton(): Matcher<View> = allOf(instanceOf(ImageButton::class.java), withParent(withId(R.id.toolbar)))

/**
 * 表示中のTextViewの文字列を取り出す。
 *
 * 検索結果の内容に依存せず、画面どうしの表示が一致することを確かめるために使う。
 *
 * @param matcher 対象のTextViewを一意に指すMatcher
 * @return 対象が持つ文字列
 */
private fun textOf(matcher: Matcher<View>): String {
    var text = ""
    onView(matcher).perform(
        object : ViewAction {
            override fun getConstraints() = isAssignableFrom(TextView::class.java)

            override fun getDescription() = "TextViewの文字列を取り出す"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                text = (view as TextView).text.toString()
            }
        },
    )
    return text
}

/**
 * 検索画面を起点にした画面名・画面遷移・戻る操作を、実際の通信を使って確認する。
 *
 * 検索結果の内容には依存せず、先頭の項目を使う。
 * 通信を伴う確認は1つのテストにまとめ、公開APIの呼び出し回数を増やさない。
 */
@RunWith(AndroidJUnit4::class)
class RepositorySearchUiTest {
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun `検索画面は画面名を表示し戻る矢印を出さない`() {
        onView(toolbarTitle()).check(matches(withText(R.string.search_screen_title)))
        // 開始位置のため戻り先がなく、矢印そのものが作られない。
        onView(toolbarNavigationButton()).check(doesNotExist())
    }

    @Test
    fun `検索した一覧の先頭から詳細画面へ遷移する`() {
        openFirstRepositoryDetail()

        // 件数はNavigationの引数から表示するため、購読者数以外は詳細取得の通信結果に依存しない。
        onView(withId(R.id.star_count))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Stars:"))))
        onView(withId(R.id.fork_count))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Forks:"))))
        onView(withId(R.id.open_issue_count))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Open issues:"))))
        // 購読者数は取得中・失敗でも文言の先頭が変わらないため、見出しだけを確認する。
        onView(withId(R.id.watcher_count))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("Watchers:"))))

        // 画面名はNavigationのラベルへ引数を差し込んで表示する。
        // 画面内の名前とは別の引数で渡しているため、同じ値になることを確かめる。
        val displayedName = textOf(allOf(withId(R.id.repository_name), isDisplayed()))
        onView(toolbarTitle()).check(matches(withText(displayedName)))
    }

    @Test
    fun `詳細画面の戻る矢印で検索画面へ戻る`() {
        openFirstRepositoryDetail()

        onView(toolbarNavigationButton()).perform(click())

        onView(toolbarTitle()).check(matches(withText(R.string.search_screen_title)))
        // 戻ったあとも検索結果を持ち続ける。
        onView(withId(R.id.repository_list_view))
            .check(matches(isDisplayed()))
            .perform(waitForItems(WAIT_TIMEOUT_MILLIS))
    }

    /**
     * 検索を実行し、一覧の先頭の詳細画面を開く。
     *
     * 検索と詳細取得でGitHubのAPIをそれぞれ1回ずつ呼ぶ。
     */
    private fun openFirstRepositoryDetail() {
        onView(withId(R.id.search_input_text))
            .perform(replaceText(SEARCH_QUERY), pressImeActionButton())

        onView(withId(R.id.repository_list_view))
            .perform(waitForItems(WAIT_TIMEOUT_MILLIS))

        onView(withId(R.id.repository_list_view))
            .perform(clickFirstItem())
    }
}
