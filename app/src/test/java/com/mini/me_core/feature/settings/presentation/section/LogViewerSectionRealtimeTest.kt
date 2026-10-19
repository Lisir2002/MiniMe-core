package com.mini.me_core.feature.settings.presentation.section

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.feature.settings.data.repository.LogFilterSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 日志查看器「实时性」回归。
 *
 * 背景（用户反馈「在日志查看器看不见最新的所有日志」）：旧实现靠
 * `FileObserver(logDir, CLOSE_WRITE)` 感知新日志，而 FileLogger 的常驻 BufferedWriter
 * 在正常轮询期间**从不关闭文件**，CLOSE_WRITE 永不触发，且 hasNewLogs 徽标没有任何
 * UI 消费——查看器内容停在进入页面那一刻。修复为「前台期间定时静默重载」，
 * 本测试钉死其核心契约：**新写入的日志行，下一次重载必须能读到**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LogViewerSectionRealtimeTest {

    private lateinit var section: LogViewerSection

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FileLogger.init(context)

        val logSettings = mockk<LogSettingsRepository>(relaxed = true)
        every { logSettings.levelFlow } returns flowOf(LogLevel.VERBOSE)
        val logFilterSettings = mockk<LogFilterSettingsRepository>(relaxed = true)
        coEvery { logFilterSettings.readSelectedLevels() } returns emptySet()
        coEvery { logFilterSettings.readSelectedTags() } returns emptySet()

        section = LogViewerSection(
            scope = CoroutineScope(Dispatchers.Unconfined),
            logSettingsRepository = logSettings,
            logFilterSettingsRepository = logFilterSettings,
        )
    }

    @Test
    fun reloadPicksUpLinesWrittenAfterInitialLoad() = runBlocking {
        val first = "RT-FIRST-${System.nanoTime()}"
        FileLogger.i(TAG, first)
        section.refreshLogs()
        awaitContent(first)

        // 初始加载之后再写新行：旧实现下这些行永远看不到（FileObserver 收不到事件）。
        val second = "RT-SECOND-${System.nanoTime()}"
        FileLogger.i(TAG, second)

        section.refreshLogsSilently()
        awaitContent(second)
        assertTrue("旧行仍应在内容中", section.logViewerState.value.content.contains(first))
        assertFalse("静默重载不应留下 loading 态", section.logViewerState.value.loading)
    }

    @Test
    fun enablingLiveTailImmediatelyReloadsLatest() = runBlocking {
        val marker = "RT-TAIL-${System.nanoTime()}"
        FileLogger.i(TAG, marker)
        FileLogger.flush()

        section.toggleLiveTail()

        awaitContent(marker)
        assertTrue("开启尾随后应补读到最新行", section.logViewerState.value.content.contains(marker))
        assertTrue("尾随开关应置位", section.logViewerState.value.liveTailEnabled)
    }

    private suspend fun awaitContent(marker: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (section.logViewerState.value.content.contains(marker)) return
            delay(50)
        }
        throw AssertionError(
            "等待超时：内容中未出现 $marker（当前内容长度=${section.logViewerState.value.content.length}）"
        )
    }

    private companion object {
        const val TAG = "LogViewerRealtimeTest"
    }
}
