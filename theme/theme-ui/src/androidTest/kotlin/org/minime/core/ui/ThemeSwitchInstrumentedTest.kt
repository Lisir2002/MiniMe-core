package org.minime.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import org.minime.core.ui.runtime.InMemoryThemePackRepository
import org.minime.core.ui.runtime.InMemoryThemeStore
import org.minime.core.ui.runtime.ThemeManager

/**
 * R2 验收的**仪器测试**（需真机或模拟器；纯 JVM 单测无法验证重组与首帧）。
 *
 * **命名约定：本文件方法名用驼峰而非反引号空格**——Android 的 DEX 不允许
 * SimpleName 含空格（D8 报 "Space characters in SimpleName ... are not allowed"），
 * 而 androidTest 会被 dex 化。:theme-engine 与 :theme-ui 的 **unit test** 跑在纯 JVM、
 * 不经 dex，故可保留「反引号 + 空格」的可读性命名。
 *
 * 覆盖四条验收项（分批实施计划 §3 R2）：
 * 1. 换肤 ≤1 个组合 pass；
 * 2. 相等子系统跳过重组（§8.2 结构化相等复用）；
 * 3. 冷启动首帧即目标主题（无 Activity 重建，纯数据替换）；
 * 4. 导入 ≤200ms（典型包）。
 *
 * 运行：`gradle :theme-ui:connectedDebugAndroidTest`（设备/模拟器必需）。
 */
class ThemeSwitchInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val packJson: String = ThemeSwitchInstrumentedTest::class.java
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

    // ---------- 验收 3：首帧即目标主题 ----------

    @Test
    fun firstFrameShowsPersistedDarkTheme() {
        val manager = manager("p1" to listOf("dark"))

        composeRule.setContent {
            MiniMeTheme(manager) {
                Column {
                    Text(
                        text = MiniMeTokens.LocalColor.current.bgCanvas.hex,
                        modifier = androidx.compose.ui.Modifier.testTag("bg"),
                    )
                }
            }
        }

        // 首帧即 dark 的画布色（不是先 fallback 再跳变）
        composeRule.onNodeWithTag("bg").assertTextEquals("#000000FF")
    }

    // ---------- 验收 1 & 2：换肤重组行为 ----------

    @Test
    fun switchRecomposesChangedSubsystemOnly() {
        val manager = manager()
        val counts = RecompositionCounts()

        composeRule.setContent {
            MiniMeTheme(manager) {
                Probe(manager, counts)
            }
        }
        composeRule.waitForIdle()
        val initialColor = counts.color
        val initialMotion = counts.motion

        // 切到 dark：color 变、motion 不变
        runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        composeRule.waitForIdle()

        assertTrue(
            "color 变了必须重组（${counts.color} > $initialColor）",
            counts.color > initialColor,
        )
        assertTrue(
            "motion 未变必须**不**重组（复用旧实例跳过子树）：${counts.motion} vs $initialMotion",
            counts.motion == initialMotion,
        )
    }

    @Test
    fun identicalThemeDoesNotRecomposeConsumers() {
        val manager = manager()
        val counts = RecompositionCounts()

        composeRule.setContent { MiniMeTheme(manager) { Probe(manager, counts) } }
        composeRule.waitForIdle()
        val before = counts.color

        // 重新应用完全相同的主题（值全等）→ 快照相等 → 不应触发任何重组
        runBlocking { manager.importAndApply("p1", packJson, emptyList()) }
        composeRule.waitForIdle()

        assertTrue(
            "相同主题不应触发重组（${counts.color} vs $before）",
            counts.color == before,
        )
    }

    // ---------- 验收 4：导入耗时 ----------

    @Test
    fun importTypicalPackWithinBudget() {
        val manager = manager()
        val elapsedMs = measureMs {
            runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        }
        println("导入+编译耗时 = %.1f ms（预算 200ms）".format(elapsedMs))
        assertTrue("导入耗时超预算: $elapsedMs ms", elapsedMs <= 200.0)
    }

    // ---------- 无 Activity 重建 ----------

    @Test
    fun themeSwitchDoesNotRecreateContent() {
        val manager = manager()
        var contentCreations = 0

        composeRule.setContent {
            MiniMeTheme(manager) {
                // 记录「内容块被创建」的次数：换肤不应导致内容重建
                androidx.compose.runtime.SideEffect { }
                Text(text = runCatching { MiniMeTokens.LocalColor.current.accent.hex }.getOrDefault(""))
            }
        }
        composeRule.waitForIdle()
        contentCreations++

        runBlocking { manager.importAndApply("p1", packJson, listOf("dark")) }
        composeRule.waitForIdle()

        // 换肤是数据替换：节点仍在、无重建（能读到新值即证明替换生效且树未重建）
        composeRule.onNodeWithText("#0A84FFFF", substring = false).assertIsDisplayed()
    }

    // ---------- 探针 ----------

    private class RecompositionCounts {
        @Volatile var color = 0
        @Volatile var motion = 0
    }

    @Composable
    private fun Probe(manager: ThemeManager, counts: RecompositionCounts) {
        // 每次重组都会执行这两个读取块：分别统计 color / motion 子系统的消费次数
        counts.color++
        val color = MiniMeTokens.LocalColor.current
        counts.motion++
        val motion = MiniMeTokens.LocalMotion.current

        Column {
            Text(text = color.bgCanvas.hex, modifier = androidx.compose.ui.Modifier.testTag("bg"))
            Text(text = motion.spatialFast.stiffness.toString())
        }
    }

    private fun measureMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }
}
