package org.minime.core.ui.gallery.pages

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.ui.MiniMeTheme
import org.minime.core.ui.MiniMeThemeBootstrap
import org.minime.core.ui.runtime.FileThemePackRepository
import org.minime.core.ui.runtime.SharedPreferencesThemeStore
import org.minime.core.ui.runtime.ThemeManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 工坊嵌入态验收（R4.2 的真机等价：JVM 自包含驱动）。
 *
 * 与 R3 Gallery 嵌入测试同模式：真实 `ThemeManager` + 真实默认包 + 真实 composable 树，
 * 覆盖 charter R4.2 验收项「覆写从面板到快照端到端 + 一键还原 + fallback 禁用」。
 * 真机抽查项（低端机拖动零 ANR）由维护者执行——见批次 charter §5。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h800dp")
class WorkshopEmbedRobolectricTest {

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    private fun newManager(): ThemeManager =
        MiniMeThemeBootstrap.create(ApplicationProvider.getApplicationContext<Context>())
            .also { it.initialize() }

    @Test
    fun 工坊可组合且呈现分类tab与WCAG矩阵() {
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                WorkshopSection(manager = newManager(), modifier = Modifier.fillMaxSize())
            }
        }
        rule.onNodeWithText("主题工坊").assertExists()
        rule.onNodeWithText("色板").assertExists()
        rule.onNodeWithText("玻璃").assertExists()
        rule.onNodeWithText("WCAG 对比度矩阵").assertExists()
    }

    @Test
    fun 点击树叶拖滑杆施加覆写() {
        lateinit var manager: ThemeManager
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                manager = newManager()
                WorkshopSection(manager = manager, modifier = Modifier.fillMaxSize())
            }
        }

        // 切到「圆角」分类 → 点叶 shape.radius.card → 编辑器出现（编辑器卡特有文本）
        rule.onNodeWithText("圆角").performClick()
        rule.onNodeWithTag("workshop-leaf-shape.radius.card").performClick()
        rule.onAllNodesWithText("当前值").onFirst().assertExists()

        // 拖滑杆（down 即跳转 + move）：覆写写入 manager.overlays（同步合并态）
        val before = manager.overlays.value
        rule.onAllNodesWithTag("workshop-slider").onFirst().performTouchInput {
            down(center)
            moveTo(Offset(center.x + 120f, center.y))
            up()
        }
        rule.waitForIdle()

        val after = manager.overlays.value
        Assert.assertTrue("拖滑杆应产生覆写", after != before)
        Assert.assertTrue("覆写路径应为 shape.radius.card", after.keys == setOf("shape.radius.card"))
    }

    @Test
    fun 全部还原清空覆写() {
        lateinit var manager: ThemeManager
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                manager = newManager()
                WorkshopSection(manager = manager, modifier = Modifier.fillMaxSize())
            }
        }

        rule.onNodeWithText("圆角").performClick()
        rule.onNodeWithTag("workshop-leaf-shape.radius.card").performClick()
        rule.onAllNodesWithTag("workshop-slider").onFirst().performTouchInput {
            down(center)
            moveTo(Offset(center.x + 120f, center.y))
            up()
        }
        rule.waitForIdle()
        Assert.assertTrue(manager.overlays.value.isNotEmpty())

        rule.onNodeWithText("全部还原").performClick()
        rule.waitForIdle()
        Assert.assertTrue(manager.overlays.value.isEmpty())
    }

    @Test
    fun fallback态工坊明示覆写不可用() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 无主题包 + 无内置包 → fallback 基座态
        val manager = ThemeManager(
            store = SharedPreferencesThemeStore(context),
            packs = FileThemePackRepository(context),
            scope = CoroutineScope(Dispatchers.Unconfined),
            builtInJson = null,
        )
        manager.initialize()

        rule.setContent {
            MiniMeTheme(manager = manager) {
                WorkshopSection(manager = manager, modifier = Modifier.fillMaxSize())
            }
        }
        rule.onNodeWithText("当前为 fallback 基座（无主题包），覆写不可用").assertExists()
    }
}
