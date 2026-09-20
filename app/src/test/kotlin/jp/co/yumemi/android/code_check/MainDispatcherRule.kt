/*
 * Copyright © 2021 YUMEMI Inc. All rights reserved.
 */
package jp.co.yumemi.android.code_check

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * テストの実行中だけ[Dispatchers.Main]をテスト用のディスパッチャへ差し替える。
 *
 * `viewModelScope`は[Dispatchers.Main]を使うため、差し替えないとViewModelのテストが動かない。
 *
 * 既定は[StandardTestDispatcher]とする。起動したコルーチンをすぐには実行せず、
 * テストが進めるまで待つため、実行中や読み込み中といった途中の状態を観測できる。
 *
 * 別のディスパッチャが必要な場合は、テスト側で新たに生成せず[testDispatcher]を渡すこと。
 * [TestDispatcher]はスケジューラを共有するが、共有されるのは
 * [Dispatchers.setMain]より後に生成した場合だけで、
 * テストクラスのフィールドで生成すると差し替えより前に作られる。
 *
 * @property testDispatcher [Dispatchers.Main]へ差し替えるディスパッチャ
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
