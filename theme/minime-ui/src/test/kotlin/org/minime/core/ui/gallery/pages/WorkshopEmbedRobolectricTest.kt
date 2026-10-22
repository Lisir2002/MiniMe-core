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
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.TypographyValue
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

    // ---------- R4.3 diff 导入导出（设计 §8；真机等价验收） ----------

    /** 造一个含 2 条覆写的 diff 包文本（导出器与导入器分离，验证「导入」而非既有态） */
    private fun sampleDiffJson(context: Context): String {
        val exporter = MiniMeThemeBootstrap.create(context).also { it.initialize() }
        exporter.applyOverrides(
            mapOf(
                "color.primitive.systemBlue" to ColorValue.fromHex("#FF0000"),
                "typography.body" to TypographyValue(
                    fontSize = DimensionValue(15f, "sp"),
                    lineHeight = DimensionValue(20f, "sp"),
                ),
            ),
        )
        return exporter.exportDiff("2026-10-22T14:30:00+08:00")
    }

    @Test
    fun 导入确认对话框呈现摘要指纹与样本() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diffJson = sampleDiffJson(context)
        val manager = MiniMeThemeBootstrap.create(context).also { it.initialize() }

        rule.setContent {
            MiniMeTheme(manager = manager) {
                WorkshopSection(
                    manager = manager,
                    pendingImportJson = diffJson,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        rule.onNodeWithText("导入 diff 主题包").assertExists()
        rule.onNodeWithText("共 2 条覆写 · 基于 built-in 1.0.0").assertExists()
        // 指纹 banner：MATCH（导出器与导入器同为内置包）
        rule.onNodeWithText("基础包匹配（built-in，同包 diff）").assertExists()
        // 分类 chips（工坊六分类口径）
        rule.onNodeWithText("色板 1").assertExists()
        rule.onNodeWithText("字阶 1").assertExists()
        // 样本（前 8 条，path = 值摘要）
        rule.onNodeWithText("color.primitive.systemBlue = #FF0000FF").assertExists()
        rule.onNodeWithText("typography.body = 15sp/20sp").assertExists()
        // 预览零改态
        Assert.assertTrue(manager.overlays.value.isEmpty())
    }

    @Test
    fun 确认应用后覆写生效且回调消费() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diffJson = sampleDiffJson(context)
        val manager = MiniMeThemeBootstrap.create(context).also { it.initialize() }
        var consumed = false

        rule.setContent {
            MiniMeTheme(manager = manager) {
                WorkshopSection(
                    manager = manager,
                    pendingImportJson = diffJson,
                    onPendingImportConsumed = { consumed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        rule.onNodeWithText("应用").performClick()
        rule.waitForIdle()

        Assert.assertTrue("应用后应回调消费（宿主据此关对话框）", consumed)
        Assert.assertEquals(setOf("color.primitive.systemBlue", "typography.body"), manager.overlays.value.keys)
    }

    @Test
    fun 取消导入零改态() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diffJson = sampleDiffJson(context)
        val manager = MiniMeThemeBootstrap.create(context).also { it.initialize() }
        var consumed = false

        rule.setContent {
            MiniMeTheme(manager = manager) {
                WorkshopSection(
                    manager = manager,
                    pendingImportJson = diffJson,
                    onPendingImportConsumed = { consumed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        rule.onNodeWithText("取消").performClick()
        rule.waitForIdle()

        Assert.assertTrue(consumed)
        Assert.assertTrue("取消后 overlays 零改态", manager.overlays.value.isEmpty())
    }

    @Test
    fun 坏包预览以错误态呈现且无应用钮() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = MiniMeThemeBootstrap.create(context).also { it.initialize() }

        rule.setContent {
            MiniMeTheme(manager = manager) {
                WorkshopSection(
                    manager = manager,
                    pendingImportJson = "not json {{{",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        rule.onNodeWithText("导入 diff 主题包").assertExists()
        rule.onNodeWithText("导入被拒绝 · PACK_CORRUPT").assertExists()
        // 坏包无「应用」钮（错误态）
        rule.onNodeWithText("应用").assertDoesNotExist()
    }
    @Test
    fun 导出钮回调回传diff文本() {
        lateinit var manager: ThemeManager
        var savedJson: String? = null
        var savedName: String? = null
        rule.setContent {
            MiniMeTheme(manager = newManager()) {
                manager = newManager()
                WorkshopSection(
                    manager = manager,
                    onSaveDiff = { json, name ->
                        savedJson = json
                        savedName = name
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 无覆写时导出钮禁用（点击无回调）
        rule.onNodeWithText("导出 diff").performClick()
        rule.waitForIdle()
        Assert.assertNull("无覆写时导出不可用", savedJson)

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        rule.waitForIdle()
        rule.onNodeWithText("导出 diff").performClick()
        rule.waitForIdle()

        Assert.assertNotNull("onSaveDiff 应被回调", savedJson)
        Assert.assertTrue(savedJson!!.contains("minime-diff"))
        Assert.assertTrue(savedJson!!.contains("${'$'}tokens"))
        Assert.assertTrue(savedName!!.startsWith("minime-diff-"))
        Assert.assertTrue(savedName!!.endsWith(".tokens.json"))
    }
}
