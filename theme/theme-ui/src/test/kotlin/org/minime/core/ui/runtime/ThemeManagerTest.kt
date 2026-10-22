package org.minime.core.ui.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.CompileErrorCode
import org.minime.core.schema.ColorValue
import org.minime.core.tokens.ThemeSnapshot
import kotlin.coroutines.ContinuationInterceptor

/**
 * 主题管理器：冷启动恢复 / 缓存 / 节流 / 降级（§8.1 时序 + §8.2 性能设计点 + G1）。
 *
 * 全部为纯 JVM 单元测试——ThemeManager 刻意不引用任何 Android 类，
 * 因此这些最容易出 bug 的状态逻辑可以在本机完整验证（不需要设备/模拟器）。
 *
 * 调度器取自 `runTest` 的 TestScope（其自带单线程测试调度器），
 * 因此 [advanceUntilIdle] 能精确驱动「后台编译 + 节流取消」的时序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeManagerTest {

    private val json: String = ThemeManagerTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private fun TestScope.manager(
        store: ThemeStore = InMemoryThemeStore(),
        packs: ThemePackRepository = InMemoryThemePackRepository(),
        builtIn: String? = json,
    ): ThemeManager = ThemeManager(
        store = store,
        packs = packs,
        scope = this, // TestScope 自身：launch 成为 runTest 直接子协程，advanceUntilIdle 可精确推进
        builtInJson = builtIn,
        computeDispatcher = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
    )

    // ---------- 冷启动 ----------

    @Test
    fun `initialize applies persisted selection synchronously`() = runTest {
        val store = InMemoryThemeStore("p1", listOf("dark"))
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(store, packs)

        manager.initialize() // 同步：调用返回时快照已就绪（首帧即目标主题）

        assertEquals(listOf("dark"), manager.snapshot.value.meta.activeModifiers)
        assertEquals("#000000FF", manager.snapshot.value.color.bgCanvas.hex)
        assertTrue(manager.status.value is ThemeStatus.Ready)
    }

    @Test
    fun `initialize with no selection uses built-in pack`() = runTest {
        val manager = manager()
        manager.initialize()
        assertEquals(ThemeManager.BUILT_IN, manager.currentPackId)
        assertEquals("MiniMe Pure White", manager.snapshot.value.meta.packName)
    }

    @Test
    fun `initialize with corrupt pack never leaves app themeless`() = runTest {
        val store = InMemoryThemeStore("broken", emptyList())
        val packs = InMemoryThemePackRepository().apply { save("broken", "{ 不是 JSON") }
        val manager = manager(store, packs)

        manager.initialize()

        // G1：包坏了也要有主题（退到内置包）
        // 降级链：目标包坏了 → 退到内置包（有主题）+ 同时暴露失败（可诊断）
        assertEquals(ThemeManager.BUILT_IN, manager.currentPackId)
        assertEquals("MiniMe Pure White", manager.snapshot.value.meta.packName)
        val status = manager.status.value
        assertTrue(status is ThemeStatus.Failed, "应报告失败: $status")
        status as ThemeStatus.Failed
        assertEquals(CompileErrorCode.PACK_CORRUPT, status.code)
        assertEquals(ThemeManager.BUILT_IN, status.fellBackTo, "应记录实际生效的包")
    }

    @Test
    fun `initialize with missing pack and no built-in keeps fallback snapshot`() = runTest {
        val store = InMemoryThemeStore("ghost", emptyList())
        val manager = manager(store, InMemoryThemePackRepository(), builtIn = null)

        manager.initialize()

        assertEquals(ThemeManager.FALLBACK_ID, manager.currentPackId)
        assertEquals("MiniMe Built-in Fallback", manager.snapshot.value.meta.packName)
        assertTrue(manager.snapshot.value.raw.isNotEmpty(), "fallback 快照非空")
    }

    // ---------- 切换与缓存 ----------

    @Test
    fun `select compiles in background then applies`() = runTest {
        val packs = InMemoryThemePackRepository().apply { save("dark-pack", json) }
        val manager = manager(packs = packs)
        manager.initialize()

        manager.select("dark-pack", listOf("dark"))
        assertTrue(manager.status.value is ThemeStatus.Loading, "先进入 Loading（导入不卡 UI）")
        assertEquals("#FFFFFFFF", manager.snapshot.value.color.bgCanvas.hex, "编译完成前不切换")

        advanceUntilIdle()

        assertEquals("#000000FF", manager.snapshot.value.color.bgCanvas.hex)
        assertEquals(listOf("dark"), manager.snapshot.value.meta.activeModifiers)
    }

    @Test
    fun `select persists selection for next cold start`() = runTest {
        val store = InMemoryThemeStore()
        val packs = InMemoryThemePackRepository().apply { save("p9", json) }
        val manager = manager(store, packs)
        manager.initialize()

        manager.select("p9", listOf("dark", "highContrast"))
        advanceUntilIdle()

        assertEquals("p9", store.currentPackId())
        assertEquals(listOf("dark", "highContrast"), store.currentModifiers())
    }

    @Test
    fun `cached selection switches without recompiling`() = runTest {
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(packs = packs)
        manager.initialize()

        manager.select("p1", emptyList())
        advanceUntilIdle()
        val first: ThemeSnapshot = manager.snapshot.value

        manager.select(ThemeManager.BUILT_IN, emptyList())
        advanceUntilIdle()
        manager.select("p1", emptyList())
        advanceUntilIdle()

        assertSame(first, manager.snapshot.value, "命中缓存应返回同一实例（切换 = 对象替换，零 I/O）")
    }

    @Test
    fun `unknown pack reports PACK_NOT_FOUND without changing snapshot`() = runTest {
        val manager = manager()
        manager.initialize()
        val before = manager.snapshot.value

        manager.select("ghost", emptyList())
        advanceUntilIdle()

        assertSame(before, manager.snapshot.value, "失败不应改变当前快照")
        val status = manager.status.value
        assertTrue(status is ThemeStatus.Failed)
        assertEquals(CompileErrorCode.PACK_NOT_FOUND, (status as ThemeStatus.Failed).code)
    }

    @Test
    fun `broken pack keeps previous snapshot`() = runTest {
        val badJson = """
            {"version":"2025.10","manifest":{"name":"x","version":"1.0.0","engineVersion":">=1.0 <2.0"},
             "sets":{"core":{"color.semantic.accent":{"${'$'}value":"{color.primitive.nope}","${'$'}type":"color"}}},
             "resolutionOrder":[{"set":"core"}]}
        """.trimIndent()
        val packs = InMemoryThemePackRepository().apply {
            save("p1", json)
            save("bad", badJson)
        }
        val manager = manager(packs = packs)
        manager.initialize()
        manager.select("p1", emptyList())
        advanceUntilIdle()
        val good = manager.snapshot.value

        manager.select("bad", emptyList())
        advanceUntilIdle()

        assertSame(good, manager.snapshot.value, "编译失败保持上一个可用快照（G1）")
        val status = manager.status.value as ThemeStatus.Failed
        assertEquals(CompileErrorCode.ALIAS_BROKEN, status.code)
    }

    // ---------- 节流 ----------

    @Test
    fun `rapid selection coalesces to the last one`() = runTest {
        val packs = InMemoryThemePackRepository().apply {
            save("light-pack", json)
            save("dark-pack", json)
        }
        val manager = manager(packs = packs)
        manager.initialize()

        manager.select("light-pack", emptyList())
        manager.select("dark-pack", listOf("dark")) // 后到者取消前者
        advanceUntilIdle()

        assertEquals("dark-pack", manager.currentPackId, "连点只让最后一次生效（无中间态闪变）")
        assertEquals("#000000FF", manager.snapshot.value.color.bgCanvas.hex)
    }

    // ---------- 导入 ----------

    @Test
    fun `importAndApply persists pack and applies it`() = runTest {
        val store = InMemoryThemeStore()
        val packs = InMemoryThemePackRepository()
        val manager = manager(store, packs)
        manager.initialize()

        manager.importAndApply("imported", json, listOf("dark"))

        assertEquals("imported", store.currentPackId())
        assertTrue("imported" in packs.installed())
        assertEquals("#000000FF", manager.snapshot.value.color.bgCanvas.hex)
        assertTrue(manager.status.value is ThemeStatus.Ready)
    }

    @Test
    fun `importAndApply rejects broken pack and keeps current theme`() = runTest {
        val manager = manager()
        manager.initialize()
        val before = manager.snapshot.value

        val thrown = runCatching { manager.importAndApply("bad", "{ 非法") }
        assertTrue(thrown.isFailure, "非法包应抛出编译异常")
        assertSame(before, manager.snapshot.value, "导入失败不改变当前主题")
    }

    // ---------- 缓存失效 ----------

    @Test
    fun `invalidate lets updated pack take effect`() = runTest {
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(packs = packs)
        manager.initialize()
        manager.select("p1", emptyList())
        advanceUntilIdle()
        assertEquals("#007AFFFF", manager.snapshot.value.color.accent.hex)

        // 包内容更新（accent 源色改红）后必须 invalidate，否则命中旧缓存拿到旧值
        packs.save("p1", json.replace("#007AFF", "#FF0000"))
        manager.invalidate("p1")
        manager.select("p1", emptyList())
        advanceUntilIdle()

        assertEquals("#FF0000FF", manager.snapshot.value.color.accent.hex, "失效后应重新编译出新值")
    }

    @Test
    fun `stale cache would hide pack update when not invalidated`() = runTest {
        // 反证：不调用 invalidate 时命中缓存，包更新不生效（说明 invalidate 是必需的）
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(packs = packs)
        manager.initialize()
        manager.select("p1", emptyList())
        advanceUntilIdle()

        packs.save("p1", json.replace("#007AFF", "#FF0000"))
        manager.select("p1", emptyList())
        advanceUntilIdle()

        assertEquals("#007AFFFF", manager.snapshot.value.color.accent.hex, "未失效 → 命中缓存 → 仍是旧值")
    }

    // ---------- 子系统复用接入 ----------

    @Test
    fun `switching to dark reuses unchanged subsystem instances`() = runTest {
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(packs = packs)
        manager.initialize()
        val light = manager.snapshot.value

        manager.select("p1", listOf("dark"))
        advanceUntilIdle()
        val dark = manager.snapshot.value

        assertSame(light.motion, dark.motion, "motion 未变 → 复用旧实例 → Compose 跳过该子树重组")
        assertSame(light.interaction, dark.interaction)
        assertSame(light.size, dark.size)
        assertTrue(light.color !== dark.color, "color 变了")
    }

    // ---------- R4 运行时覆写层 ----------

    @Test
    fun `applyOverrides applies with alias following`() = runTest {
        val manager = manager()
        manager.initialize()
        assertEquals("#007AFFFF", manager.snapshot.value.color.accent.hex)

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        advanceUntilIdle()

        // §5.1 规则 1 运行时证明：primitive 被覆写 → 语义别名 accent 跟随
        assertEquals("#FF0000FF", manager.snapshot.value.color.accent.hex)
        assertEquals(
            mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")),
            manager.overlays.value,
        )
        assertTrue(manager.status.value is ThemeStatus.Ready)
    }

    @Test
    fun `resetOverrides restores base values`() = runTest {
        val manager = manager()
        manager.initialize()

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        advanceUntilIdle()
        manager.resetOverrides()
        advanceUntilIdle()

        assertEquals("#007AFFFF", manager.snapshot.value.color.accent.hex, "全部还原 = 回基础包面貌")
        assertTrue(manager.overlays.value.isEmpty())
        assertTrue(manager.status.value is ThemeStatus.Ready)
    }

    @Test
    fun `resetOverrides by path keeps the rest`() = runTest {
        val manager = manager()
        manager.initialize()

        manager.applyOverrides(
            mapOf(
                "color.primitive.systemBlue" to ColorValue.fromHex("#FF0000"),
                "color.primitive.systemGreen" to ColorValue.fromHex("#00FF00"),
            )
        )
        advanceUntilIdle()
        manager.resetOverrides(listOf("color.primitive.systemBlue"))
        advanceUntilIdle()

        assertEquals("#007AFFFF", manager.snapshot.value.color.accent.hex, "systemBlue 还原")
        assertEquals(
            "#00FF00FF",
            manager.snapshot.value.color.stateSuccess.hex,
            "systemGreen 覆写仍在（state.success = {color.primitive.systemGreen}）",
        )
        assertEquals(setOf("color.primitive.systemGreen"), manager.overlays.value.keys)
    }

    @Test
    fun `failed override rolls back to previous good state`() = runTest {
        val glassPack = """
            {"version":"2025.10","manifest":{"name":"glass-pack","version":"1.0.0","engineVersion":">=1.0 <2.0"},
             "sets":{"core":{
               "color.primitive.systemBlue":{"${'$'}value":"#007AFF","${'$'}type":"color"},
               "color.semantic.accent":{"${'$'}value":"{color.primitive.systemBlue}","${'$'}type":"color"},
               "color.semantic.text.primary":{"${'$'}value":"#000000","${'$'}type":"color"},
               "glass.enabled":{"${'$'}value":true,"${'$'}type":"boolean"},
               "glass.regular":{"${'$'}type":"glass","${'$'}value":{"blur":"20dp","saturation":1.8,
                 "tint":"{color.semantic.accent}","tintAlpha":0.72,"hairline":true}}}},
             "resolutionOrder":[{"set":"core"}],
             "${'$'}extensions":{"minime":{"glass":{"consumers":["color.semantic.text.primary"]}}}}
        """.trimIndent()
        val store = InMemoryThemeStore("glass-pack", emptyList())
        val packs = InMemoryThemePackRepository().apply { save("glass-pack", glassPack) }
        val manager = manager(store, packs)
        manager.initialize()

        // 先应用一个合法覆写（accent 变红）作为「上一版好状态」
        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        advanceUntilIdle()
        val good = manager.snapshot.value
        assertEquals("#FF0000FF", good.color.accent.hex)

        // 再应用一个会把玻璃表面消费令牌改半透明的覆写 → GLASS_OPAQUE_VIOLATION
        manager.applyOverrides(mapOf("color.semantic.text.primary" to ColorValue.fromHex("#00000080")))
        advanceUntilIdle()

        // 防改坏：快照保持上一版好状态，覆写表回滚（与快照一致），失败可诊断
        assertSame(good, manager.snapshot.value, "编译失败保持上一个可用快照（G1）")
        assertEquals(setOf("color.primitive.systemBlue"), manager.overlays.value.keys, "覆写表回滚到上一版")
        val status = manager.status.value as ThemeStatus.Failed
        assertEquals(CompileErrorCode.GLASS_OPAQUE_VIOLATION, status.code)
    }

    @Test
    fun `select clears overlays`() = runTest {
        val packs = InMemoryThemePackRepository().apply {
            save("p1", json)
            save("dark-pack", json)
        }
        val manager = manager(packs = packs)
        manager.initialize()

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        advanceUntilIdle()
        assertEquals("#FF0000FF", manager.snapshot.value.color.accent.hex)

        // 换包 = 工坊会话结束：覆写清零，新包以原初面貌生效
        manager.select("dark-pack", listOf("dark"))
        advanceUntilIdle()

        assertTrue(manager.overlays.value.isEmpty(), "select 成功后覆写清零")
        assertEquals("#0A84FFFF", manager.snapshot.value.color.accent.hex)
    }

    @Test
    fun `rapid overrides coalesce to the last one`() = runTest {
        val manager = manager()
        manager.initialize()

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#00FF00")))
        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#0000FF")))
        advanceUntilIdle()

        assertEquals("#0000FFFF", manager.snapshot.value.color.accent.hex, "连续覆写只让最后一次生效（无排队编译）")
        assertEquals(
            mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#0000FF")),
            manager.overlays.value,
        )
    }

    @Test
    fun `overlay after cache-hit select uses the cached pack`() = runTest {
        val packs = InMemoryThemePackRepository().apply { save("p1", json) }
        val manager = manager(packs = packs)
        manager.initialize()
        manager.select("p1", emptyList())
        advanceUntilIdle()

        manager.select(ThemeManager.BUILT_IN, emptyList())
        advanceUntilIdle()
        manager.select("p1", emptyList()) // 缓存命中（零 I/O）：currentJson 须随条目恢复
        advanceUntilIdle()

        manager.applyOverrides(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        advanceUntilIdle()

        assertEquals("#FF0000FF", manager.snapshot.value.color.accent.hex, "命中后再覆写仍以 p1 为基础包")
    }
}
