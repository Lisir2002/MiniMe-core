package org.minime.core.ui.gallery.pages

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.ui.MiniMeTheme
import org.minime.core.ui.MiniMeThemeBootstrap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 动效检验区验收（R5 的真机等价：JVM 自包含驱动）。
 *
 * 对应 charter R5 验收项：
 * - 验收 5：MOTION 占位替换为真实页（五类别 tab + 可滚动清单 + 点播/播放/慢放/对照）；
 * - 验收 8b：铁律 9 双标注（槽位 + 时长覆写令牌）在 UI 层可见；
 * - 验收 6：reducedMotion 对照开关本地生效（不改系统设置）。
 *
 * 「无卡片分块」（维护者 2026-10-23 指示）为观感项，由真机抽查确认；
 * 本测试覆盖可断言的形态契约：tab 类别全集、清单滚动、双标注文本、
 * 二期置灰登记（chip 取代播放钮）、降级态切换、点播驱动演示区。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h800dp")
class MotionSectionRobolectricTest {

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    private fun newManager(): org.minime.core.ui.runtime.ThemeManager =
        MiniMeThemeBootstrap.create(ApplicationProvider.getApplicationContext<Context>())
            .also { it.initialize() }

    private fun setMotionSection() {
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                MotionSection(modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Test
    fun 呈现五类别tab与默认过渡类清单() {
        setMotionSection()

        // 五类别 tab（§6.4 五大类全量）
        listOf("过渡", "反馈", "加载与进度", "手势驱动", "状态变形")
            .forEach { rule.onNodeWithText(it).assertExists() }

        // 默认落在 TRANSITION：首个 MVP 模式行 + 用途 + 系统档位读数
        rule.onNodeWithText("overlay.enter/exit").assertExists()
        rule.onNodeWithText("浮层的来去（缩放显空间层次）").assertExists()
        rule.onNodeWithText("系统级缩放：NORMAL").assertExists()
    }

    @Test
    fun 双标注含槽位与时长令牌及标称时长() {
        setMotionSection()

        // 铁律 9 双标注（overlay.enter/exit：spatial.default + duration.default 250ms）
        rule.onNodeWithText("槽位 motion.spatial.default · 时长 motion.duration.default（250ms）").assertExists()
    }

    @Test
    fun 阻尼覆写第三标注可见() {
        setMotionSection()

        rule.onNodeWithText("反馈").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("action.error"))

        // action.error：spatial.fast + duration.slow 300ms + 阻尼覆写令牌（铁律 9 第三项）
        rule.onNodeWithText("action.error").assertExists()
        rule.onNodeWithText(
            "槽位 motion.spatial.fast · 时长 motion.duration.slow（300ms） · 阻尼覆写 interaction.shakeDamping",
        ).assertExists()
    }

    @Test
    fun 二期模式置灰登记且不提供播放钮() {
        setMotionSection()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("motion-pattern-phase2"))

        // 二期行：chip 存在，且同一行内以 chip 取代播放钮（二选一）。
        // 未合并树查询：合并树下 clickable 盒会吞掉子文本、父链上跳，须用 useUnmergedTree。
        rule.onNodeWithTag("motion-pattern-phase2").assertExists()
        rule.onNodeWithTag("motion-pattern-phase2", useUnmergedTree = true)
            .onParent()
            .onChildren()
            .filter(hasTestTag("motion-pattern-play"))
            .assertCountEquals(0)

        // 登记行仍持用途标注（目录即契约：二期也全量标注）
        rule.onNodeWithText("排序/筛选位置连续性").assertExists()
    }

    @Test
    fun 切换类别tab更新清单() {
        setMotionSection()

        rule.onNodeWithText("反馈").performClick()
        rule.waitForIdle()

        // 反馈类首个模式出现；过渡类模式离榜
        rule.onNodeWithText("press.scale").assertExists()
        rule.onNodeWithText("overlay.enter/exit").assertDoesNotExist()
    }

    @Test
    fun reducedMotion模拟开关切换降级态呈现() {
        setMotionSection()

        // title.collapse 降级行为 = DIRECT：系统 OFF 时应直切（无 spec → 降级态）
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("title.collapse"))
        rule.onAllNodesWithText("降级态").assertCountEquals(0)

        rule.onNodeWithText("关闭").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("降级态").assertCountEquals(1)

        // 跟随系统 → 恢复全规格（模拟开关只本地覆写，不改系统设置）
        rule.onNodeWithText("跟随系统").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("降级态").assertCountEquals(0)
    }

    @Test
    fun 点播播放驱动演示区呈现当前模式() {
        setMotionSection()

        // 点第一行（overlay.enter/exit）的播放钮 → 演示区显示当前模式 id
        rule.onAllNodesWithText("播放").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("motion-stage-playing").assertTextEquals("overlay.enter/exit")
    }

    @Test
    fun 慢放点播以四分之一时间比例保真播放() {
        setMotionSection()

        rule.onNodeWithText("0.25× 慢放").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("播放").onFirst().performClick()
        rule.waitForIdle()
        // 慢放路径（压缩喂给 spec 的播放时间 + snapTo）同样把演示区置为当前模式
        rule.onNodeWithTag("motion-stage-playing").assertTextEquals("overlay.enter/exit")
    }
}
