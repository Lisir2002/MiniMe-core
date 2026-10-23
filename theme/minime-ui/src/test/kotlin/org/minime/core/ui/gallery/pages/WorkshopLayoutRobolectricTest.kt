package org.minime.core.ui.gallery.pages

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.ui.MiniMeTheme
import org.minime.core.ui.MiniMeThemeBootstrap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 工坊布局契约（R5.5 charter §6 验收 1/3/4/5 的机器固化）。
 *
 * 基类 = **小屏 360×640**（重构的首要约束——维护者「等比例写死宽高，影响小屏
 * 手机的操作体验」）；宽屏方法以方法级 `@Config` 覆盖 qualifiers 重放同一断言，
 * 证明宽窄同构（charter D2：一套布局消两端）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class WorkshopLayoutRobolectricTest {

    @get:Rule
    val rule = createComposeRule()

    private fun newManager(): org.minime.core.ui.runtime.ThemeManager =
        MiniMeThemeBootstrap.create(ApplicationProvider.getApplicationContext<Context>())
            .also { it.initialize() }

    private fun setWorkshop() {
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                WorkshopSection(manager = newManager(), modifier = Modifier.fillMaxSize())
            }
        }
    }

    /** 验收 1：演示带 = 内容区 1/5（band / (band + flow) ≈ 0.2，±0.05 容差） */
    @Test
    fun 演示带占内容区五分之一() {
        setWorkshop()
        val band = rule.onNodeWithTag(WorkshopLayoutSpec.DEMO_BAND_TAG).fetchSemanticsNode()
        val flow = rule.onNodeWithTag(WorkshopLayoutSpec.PARAM_FLOW_TAG).fetchSemanticsNode()
        val bandH = band.size.height.toFloat()
        val flowH = flow.size.height.toFloat()
        Assert.assertTrue("演示带应有正高度", bandH > 0f)
        Assert.assertTrue("参数流应有正高度", flowH > 0f)
        val ratio = bandH / (bandH + flowH)
        Assert.assertTrue(
            "演示带应占内容区 ≈1/5，实测 $ratio",
            ratio in 0.15f..0.25f,
        )
    }

    /** 验收 5：宽屏同构——同一断言在 840×800 宽屏下同样成立 */
    @Test
    @Config(qualifiers = "w840dp-h800dp")
    fun 宽屏同构演示带比例不变() {
        setWorkshop()
        val band = rule.onNodeWithTag(WorkshopLayoutSpec.DEMO_BAND_TAG).fetchSemanticsNode()
        val flow = rule.onNodeWithTag(WorkshopLayoutSpec.PARAM_FLOW_TAG).fetchSemanticsNode()
        val ratio = band.size.height.toFloat() / (band.size.height + flow.size.height).toFloat()
        Assert.assertTrue("宽屏下演示带应占内容区 ≈1/5，实测 $ratio", ratio in 0.15f..0.25f)
    }

    /** 验收 3：手风琴同刻单开——点 B 行后编辑上下文只剩 B */
    @Test
    fun 手风琴同刻单开() {
        setWorkshop()
        rule.onNodeWithText("圆角").performClick()
        // 小屏参数流视口小：叶行需先滚动入视口（产品现实——树即长列表）
        rule.onNodeWithTag(WorkshopLayoutSpec.PARAM_FLOW_TAG)
            .performScrollToNode(hasTestTag("workshop-leaf-shape.radius.button"))
        rule.onNodeWithTag("workshop-leaf-shape.radius.button").performClick()
        Assert.assertEquals(
            "点第一个叶后应有一个编辑上下文",
            1,
            rule.onAllNodesWithText("正在编辑：", substring = true).fetchSemanticsNodes().size,
        )
        // 点另一个叶：展开转移（单 selectedPath 天然保证）
        rule.onNodeWithTag(WorkshopLayoutSpec.PARAM_FLOW_TAG)
            .performScrollToNode(hasTestTag("workshop-leaf-shape.radius.card"))
        rule.onNodeWithTag("workshop-leaf-shape.radius.card").performClick()
        val nodes = rule.onAllNodesWithText("正在编辑：", substring = true).fetchSemanticsNodes()
        Assert.assertEquals("同刻只应有一个编辑上下文", 1, nodes.size)
        Assert.assertTrue(
            "编辑上下文应已转移到新行",
            rule.onAllNodesWithText("正在编辑：shape.radius.card").fetchSemanticsNodes().isNotEmpty(),
        )
        // 再点选中行 = 收起
        rule.onNodeWithTag("workshop-leaf-shape.radius.card").performClick()
        Assert.assertEquals(
            "再点选中行应收起编辑器",
            0,
            rule.onAllNodesWithText("正在编辑：", substring = true).fetchSemanticsNodes().size,
        )
    }

    /** 验收 4：分类 tab → 演示带默认组联动（调什么看什么） */
    @Test
    fun 切类联动演示带默认组() {
        setWorkshop()
        // 初始 = 色板 → 默认配色组（accent 色块）
        rule.onNodeWithText("accent").assertExists()
        // 切「圆角」→ 默认卡片组（radiusButton 样本）
        rule.onNodeWithText("圆角").performClick()
        rule.onNodeWithText("radiusButton").assertExists()
        // 切「动效」→ 默认动效点播组（播放钮）
        rule.onNodeWithText("动效").performClick()
        rule.onNodeWithText("播放").assertExists()
        // 手动 chip 切换优先：动效类下切到「文本」组仍可见文本样本
        rule.onNodeWithText("文本").performClick()
        rule.onNodeWithText("largeTitle").assertExists()
    }

    /** 联动真源：默认组映射是函数（机器可验，防手改漂移） */
    @Test
    fun 默认组映射覆盖全部分类() {
        // 每个分类都有默认组（无异常抛出即全覆盖；具体对应见 defaultDemoGroup KDoc）
        WorkshopCategory.entries.forEach { Assert.assertNotNull(defaultDemoGroup(it)) }
    }
}
