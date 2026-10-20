package org.minime.gallery

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.gallery.nav.GallerySection

/**
 * Gallery 导航与验收面仪器测试（R3 验收项）。
 *
 * 直接驱动真实 [MainActivity]（真实 ThemeManager + 真实主题）——
 * 仪器测试与真人操作同一条路径，不做 mock 主题。
 *
 * 覆盖：① 侧边栏可达全部已建分区；② squircle vs 几何圆角 A/B 并排；
 * ③ 顶栏主题入口切换真实生效；④ 令牌页全量滚动无崩溃（静态页稳定性的粗检）。
 *
 * 需真机 / 模拟器执行（本仓库无设备时至少保证编译通过）。
 */
@RunWith(AndroidJUnit4::class)
class GalleryNavigationInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 侧边栏可达全部已建分区() {
        // 窄屏（默认测试设备）：顶栏菜单打开抽屉
        rule.onNodeWithContentDescription("打开分区导航").performClick()

        GallerySection.entries.forEach { section ->
            rule.onNodeWithText(section.title).performClick()
            // 抽屉关闭后顶栏显示该分区标题
            rule.waitForIdle()
            rule.onNodeWithText(section.title).assertExists()
            // 打开下一个
            rule.onNodeWithContentDescription("打开分区导航").performClick()
        }
    }

    @Test
    fun squircle与几何圆角AB并排展示() {
        // A/B 在 shape 子系统（color 62 行之后）——滚动到可见
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("连续圆角"))
        rule.onNodeWithText("连续圆角").assertExists()
        rule.onNodeWithText("几何圆角").assertExists()
    }

    @Test
    fun 顶栏主题入口切换真实生效() {
        // 默认内置包为亮色：按钮文案「亮色」→ 点击 → 「暗色」
        rule.onNodeWithText("亮色").assertExists()
        rule.onNodeWithContentDescription("切换亮暗色").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("暗色").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("暗色").assertExists()

        // 侧边栏摘要同步反映激活修饰符（dark）
        rule.onNodeWithContentDescription("打开分区导航").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("包版本 1.0.0 · dark").assertExists()
    }

    @Test
    fun 令牌页全量滚动无崩溃() {
        // 从顶部滚到底部（172 行 + 区头），静态页稳定性粗检
        repeat(30) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("以上 172 条"))
            rule.waitForIdle()
        }
        rule.onNodeWithText("以上 172 条 = 引擎词汇表全量（§6.1.4）。主题包可覆盖其中任意值，覆写后本页实时反映（R4 工坊落地）。")
            .assertExists()
    }
}
