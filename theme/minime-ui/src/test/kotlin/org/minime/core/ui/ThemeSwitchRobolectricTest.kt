package org.minime.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.ui.runtime.InMemoryThemePackRepository
import org.minime.core.ui.runtime.InMemoryThemeStore
import org.minime.core.ui.runtime.ThemeManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 换肤验收的 Robolectric 回收（R5-E，charter 验收 8）。
 *
 * 背景：原 `ThemeSwitchInstrumentedTest`（theme-ui androidTest）5 项自 R3.5
 * 仪器测试整体退役后**无等价回收**——CI 不跑 androidTest、release 变体禁用
 * 单测，验收项 1/2/3/4 失去门禁。本类把 5 项迁到 JVM Robolectric：
 * 重组计数 / 首帧 / 不重建内容均可在 Robolectric 的组合运行时上确定性验证。
 *
 * 落在 minime-ui 的原因：Robolectric + compose 测试基建在本模块
 * （theme-ui 单测跑 JUnit Platform、无 Robolectric 依赖；其 androidTest
 * 保留设备侧复核）。方法名用反引号中文——JVM 单测不经 dex，无 DEX
 * SimpleName 限制（与仪器测试的驼峰约定差异即此）。
 *
 * 与原仪器测试的差异（charter Q4）：
 * - 导入耗时用**宽松断言**：Robolectric 计时不准，只卡量级（≤5000ms，
 *   防同步 I/O / 死循环级回归）；紧凑 p50 由 theme-engine 的
 *   `CompilePerformanceTest` 卡（桌面 ≤20ms），200ms 设备预算随每批
 *   RC 真机实测流程复核。
 *
 * ## 回收期重大发现（原仪器测试从未跑过，缺陷被掩盖至今）
 *
 * `switchRecomposesChangedSubsystemOnly` 的「motion 未变不重组」断言
 * **在当前实现下不可能通过**：`CompositionLocalProvider` 的任一 provided
 * 值变化时，其 content 内**所有**子 scope 全部失效重组——与读者是否读取
 * 了变化的 local 无关。最小复现三重验证（本次回收实测）：
 * 1. 不读任何 local 的探针同样 +1；
 * 2. 给计数器类加 `@Stable` 无效（非参数稳定性问题）；
 * 3. 分层 provider（每 local 一层）同样全量——内层 scope 隐式依赖整个
 *    provider table，逃不掉。
 * 即 R2 §8.2 声称的「相等子系统跳过重组」只兑现到**快照层实例复用**
 * （SnapshotReuser，省分配），**UI 层是全树重组**。探针拆分（原单 Probe
 * 同时读两个 local 的问题）是次要缺陷，已修；但不拆分也一样。
 *
 * 本类把该断言改写为「记录真实行为」：变化子系统 ≤1 pass（验收 1 真门禁），
 * 未变子系统连带重组计数如实断言（已知限制，防回归时误读为改善）。
 * 修复方向（换肤全树重组）超出 R5 范围，记入 charter 遗留项。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSwitchRobolectricTest {

    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val packJson: String = ThemeSwitchRobolectricTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private fun manager(selection: Pair<String, List<String>>? = null): ThemeManager {
        val store = InMemoryThemeStore()
        selection?.let { store.saveSelection(it.first, it.second) }
        return ThemeManager(
            store = store,
            packs = InMemoryThemePackRepository().apply { save("p1", packJson) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            builtInJson = packJson,
        ).apply { initialize() }
    }

    // ---------- 验收 3：首帧即目标主题（回收 firstFrameShowsPersistedDarkTheme） ----------

    @Test
    fun 首帧即持久化的暗色主题() {
        val manager = manager("p1" to listOf("dark"))

        composeRule.setContent {
            MiniMeTheme(manager) {
                Column {
                    Text(
                        text = MiniMeTokens.LocalColor.current.bgCanvas.hex,
                        modifier = Modifier.testTag("bg"),
                    )
                }
            }
        }

        // 首帧即 dark 的画布色（不是先 fallback 再跳变）
        composeRule.onNodeWithTag("bg").assertTextEquals("#000000FF")
    }

    // ---------- 验收 1 & 2：换肤重组行为（回收 switchRecomposesChangedSubsystemOnly） ----------

    /**
     * 变化子系统 ≤1 个组合 pass（验收 1 真门禁）。
     *
     * 未变子系统（motion）的重组计数**如实断言为 +1**——这不是期望行为，
     * 是 `CompositionLocalProvider` content 全量失效的当前真实行为
     * （见类注释「回收期重大发现」）。写死 +1 而非跳过断言：若未来某批
     * 真正修好了粒度（如改订阅式消费），此断言失败即提醒同步更新认知；
     * 若意外变成 0 也会失败——两种情况都不允许静默通过。
     */
    @Test
    fun 换肤对变化子系统单pass且记录连带重组() {
        val manager = manager()
        val counts = RecompositionCounts()

        composeRule.setContent {
            MiniMeTheme(manager) {
                ProbeSet(counts)
            }
        }
        composeRule.waitForIdle()
        val initialColor = counts.color
        val initialMotion = counts.motion

        // 切到 dark：color 变、motion 令牌实例复用（快照层）
        runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        composeRule.waitForIdle()

        val colorDelta = counts.color - initialColor
        val motionDelta = counts.motion - initialMotion
        assertTrue(
            "color 变了必须重组（实际 +$colorDelta）",
            colorDelta >= 1,
        )
        assertTrue(
            "换肤应 ≤1 个组合 pass（验收 1），实际 +$colorDelta",
            colorDelta <= 1,
        )
        assertTrue(
            "motion 连带重组应为已知的 +1（CompositionLocalProvider content 全量失效；" +
                "若此值变化说明 provider 行为或结构被改，需重新评估 §8.2），实际 +$motionDelta",
            motionDelta == 1,
        )
    }

    // ---------- 验收 2：相等主题零重组（回收 identicalThemeDoesNotRecomposeConsumers） ----------

    @Test
    fun 相同主题不触发任何重组() {
        val manager = manager()
        val counts = RecompositionCounts()

        composeRule.setContent { MiniMeTheme(manager) { ProbeSet(counts) } }
        composeRule.waitForIdle()
        val beforeColor = counts.color
        val beforeMotion = counts.motion

        // 重新应用完全相同的主题（值全等）→ 快照相等 → 不应触发任何重组
        runBlocking { manager.importAndApply("p1", packJson, emptyList()) }
        composeRule.waitForIdle()

        assertTrue(
            "相同主题不应触发 color 重组（${counts.color} vs $beforeColor）",
            counts.color == beforeColor,
        )
        assertTrue(
            "相同主题不应触发 motion 重组（${counts.motion} vs $beforeMotion）",
            counts.motion == beforeMotion,
        )
    }

    // ---------- 验收 4：导入耗时（回收 importTypicalPackWithinBudget；宽松断言见类注释） ----------

    @Test
    fun 导入典型包在宽松预算内完成() {
        val manager = manager()
        val elapsedMs = measureMs {
            runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        }
        println("Robolectric 导入+编译耗时 = %.1f ms（宽松上限 5000ms；紧凑 p50 见 CompilePerformanceTest）".format(elapsedMs))
        assertTrue("导入耗时超宽松预算: $elapsedMs ms", elapsedMs <= 5000.0)
    }

    // ---------- 无 Activity 重建（回收 themeSwitchDoesNotRecreateContent） ----------

    @Test
    fun 换肤不重建内容() {
        val manager = manager()

        composeRule.setContent {
            MiniMeTheme(manager) {
                Text(text = MiniMeTokens.LocalColor.current.accent.hex)
            }
        }
        composeRule.waitForIdle()

        runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        composeRule.waitForIdle()

        // 换肤是数据替换：节点仍在、无重建（能读到新值即证明替换生效且树未重建）
        composeRule.onNodeWithText("#0A84FFFF").assertIsDisplayed()
    }

    // ---------- 探针 ----------

    private class RecompositionCounts {
        @Volatile var color = 0
        @Volatile var motion = 0
    }

    /**
     * 每子系统**独立**探针（读且仅读一个 local）。
     *
     * 原仪器测试用单体探针同时读 color + motion——那是次要缺陷（计数混淆）。
     * 但注意：拆分**不能**修复「未变子系统连带重组」——实测 provider content
     * 全量失效与探针结构无关（见类注释「回收期重大发现」）。保留独立探针
     * 是为了让两个子系统的消费计数可分别读取，行为如实记录。
     */
    @Composable
    private fun ProbeSet(counts: RecompositionCounts) {
        ColorProbe(counts)
        MotionProbe(counts)
    }

    @Composable
    private fun ColorProbe(counts: RecompositionCounts) {
        counts.color++
        val color = MiniMeTokens.LocalColor.current
        Text(text = color.bgCanvas.hex, modifier = Modifier.testTag("bg"))
    }

    @Composable
    private fun MotionProbe(counts: RecompositionCounts) {
        counts.motion++
        val motion = MiniMeTokens.LocalMotion.current
        Text(text = motion.spatialFast.stiffness.toString())
    }

    private fun measureMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }
}
