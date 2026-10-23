package org.minime.core.ui.gallery.nav

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.api.MiniMeEngine
import org.minime.core.ui.MiniMeTheme
import org.minime.core.ui.MiniMeThemeBootstrap
import org.minime.core.ui.gallery.ThemeModifiers
import org.minime.core.ui.gallery.pages.SectionContent
import org.minime.core.ui.gallery.pages.TokensOverviewSection
import org.minime.core.ui.runtime.ThemeManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gallery 页面群的嵌入态验收（R3 验收项的 Robolectric 移植）。
 *
 * 背景：R3.5 用户裁定「不做独立 UI App」——原驱动 `MainActivity` 的 4 项仪器测试
 * 随之失去 LAUNCHER 载体，改造为 **JVM 自包含**：真实 `ThemeManager` + 真实默认包 +
 * 真实 composable 树，按接入方主程序的真实用法（宿主自备布局 + 状态外置）驱动。
 * 覆盖等价、无需设备、CI 可跑。
 *
 * 实现说明：
 * 1. 页面文本多为拼接长句（intro / 页脚），匹配一律 `substring = true`
 *    （`onNodeWithText` / `hasText` 默认精确匹配，缩短断言会永不命中）；
 * 2. 测试屏 `w840dp-h800dp`——Robolectric 无真实字体度量，文本测得偏大使侧栏项
 *    偏高，默认 470dp 高度放不下 6 个分区（后两项被布局挤成零高、点击落空）。
 *
 * 覆盖：① 侧边栏可达全部已建分区；② squircle vs 几何圆角 A/B 并排；
 * ③ 顶栏主题入口切换真实生效；④ 令牌页全量滚动无崩溃。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h800dp")
class GalleryEmbedRobolectricTest {

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    private fun newManager(): ThemeManager =
        MiniMeThemeBootstrap.create(ApplicationProvider.getApplicationContext<Context>())
            .also { it.initialize() }

    /** 亮 / 暗一键切换（其余修饰符原样保留——原 app 壳逻辑，嵌入态由宿主复刻） */
    private fun ThemeManager.toggleDark() {
        val current = snapshot.value.meta.activeModifiers
        val dark = ThemeModifiers.DARK in current
        select(
            packId = ThemeManager.BUILT_IN,
            modifiers = if (dark) current - ThemeModifiers.DARK else current + ThemeModifiers.DARK,
        )
    }

    @Test
    fun 侧边栏可达全部已建分区() {
        val manager = newManager()
        rule.setContent {
            MiniMeTheme(manager) {
                var current by remember { mutableStateOf(GallerySection.DEFAULT) }
                val snapshot by manager.snapshot.collectAsState()
                Row(Modifier.fillMaxSize()) {
                    // 侧边栏在前：组合序在前，onAllNodesWithText(标题).onFirst() 稳定命中侧栏项
                    GallerySidebar(
                        current = current,
                        meta = snapshot.meta,
                        engineVersion = MiniMeEngine.VERSION,
                        onSelect = { current = it },
                        modifier = Modifier.width(280.dp),
                    )
                    SectionContent(
                        section = current,
                        manager = manager,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 每个分区：点侧边栏项 → 内容区出现该分区独有文本（均在首屏，无需滚动）
        GallerySection.entries.forEach { section ->
            // 侧边栏项与分区页眉同名（如「令牌总览」），取首个（组合序在前 = 侧边栏）
            rule.onAllNodesWithText(section.title).onFirst().performClick()
            rule.waitForIdle()
            // 标记文本可能同现于徽标与计划文案（如「随 R8 交付」）——存在即通过
            val hits = rule.onAllNodesWithText(sectionMarker(section), substring = true)
                .fetchSemanticsNodes().size
            assert(hits > 0) { "分区 ${section.title} 切换后未出现标记文本: ${sectionMarker(section)}" }
        }
    }

    @Test
    fun squircle与几何圆角AB并排展示() {
        val manager = newManager()
        rule.setContent {
            MiniMeTheme(manager) { TokensOverviewSection(Modifier.fillMaxSize()) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("连续圆角", substring = true))
        // shape 每个 radius 令牌一对 A/B（4 个 radius → 4 对）：两类标签均存在且成对
        val squircle = rule.onAllNodesWithText("连续圆角", substring = true).fetchSemanticsNodes().size
        val geometric = rule.onAllNodesWithText("几何圆角", substring = true).fetchSemanticsNodes().size
        assert(squircle > 0) { "未找到 squircle A/B 样本" }
        assert(squircle == geometric) { "A/B 不成对: 连续圆角=$squircle, 几何圆角=$geometric" }
    }

    @Test
    fun 顶栏主题入口切换真实生效() {
        val manager = newManager()
        rule.setContent {
            MiniMeTheme(manager) {
                val snapshot by manager.snapshot.collectAsState()
                Column(Modifier.fillMaxWidth()) {
                    GalleryTopBar(
                        title = GallerySection.TOKENS.title,
                        subtitle = GallerySection.TOKENS.subtitle,
                        meta = snapshot.meta,
                        compact = false,
                        onToggleDark = { manager.toggleDark() },
                        onOpenDrawer = {},
                    )
                    GallerySidebar(
                        current = GallerySection.TOKENS,
                        meta = snapshot.meta,
                        engineVersion = MiniMeEngine.VERSION,
                        onSelect = {},
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }

        // 默认内置包为亮色：按钮文案「亮色」→ 点击 → 「暗色」
        rule.onNodeWithText("亮色").assertExists()
        rule.onNodeWithContentDescription("切换亮暗色").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("暗色").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("暗色").assertExists()

        // 侧边栏摘要同步反映激活修饰符（dark）
        rule.onNodeWithText("包版本 1.0.0 · dark").assertExists()
    }

    @Test
    fun 令牌页全量滚动无崩溃() {
        val manager = newManager()
        rule.setContent {
            MiniMeTheme(manager) { TokensOverviewSection(Modifier.fillMaxSize()) }
        }
        // 反复滚到底部（词汇表全量行 + 区头），静态页稳定性粗检
        repeat(10) {
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("引擎词汇表全量", substring = true))
            rule.waitForIdle()
        }
        rule.onNodeWithText("引擎词汇表全量", substring = true).assertExists()
    }

    /** 每个分区在内容区的独有文本（避开与侧边栏同名的页眉/副标题） */
    private fun sectionMarker(section: GallerySection): String = when (section) {
        GallerySection.TOKENS -> "全部值实时读自当前生效快照"
        GallerySection.COMPONENTS -> "随 R6–R9 交付"
        GallerySection.PROTOTYPES -> "随 R8 交付"
        GallerySection.MOTION -> "随 R5 交付"
        // R4.2 起 WORKSHOP 已是真实工坊页（非占位）；marker 取预览列必然渲染的
        // WCAG 矩阵标题（不依赖 fallback 态，且避开侧边栏同名 label「主题工坊」）
        GallerySection.WORKSHOP -> "WCAG 对比度矩阵"
        GallerySection.DEBUG -> "包版本"
    }
}
