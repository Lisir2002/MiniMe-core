package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.CompileErrorCode
import org.minime.core.fallback.FallbackTheme
import org.minime.core.schema.TokenValue
import org.minime.core.tokens.TokenVocabulary

/**
 * 加载器降级路径（G1/R11 三条路径逐条覆盖）：
 * 1. 包不存在（null / 空串）→ PACK_NOT_FOUND；
 * 2. 包损坏（非 JSON / 缺 sets）→ PACK_CORRUPT / SCHEMA_INVALID；
 * 3. 编译异常（§5.2 任一阻断行）→ 相应 errorCode。
 *
 * 铁律：**降级永远有交付物**——degraded = true 且 snapshot 为内置 fallback（非 null、非空）。
 */
class ThemeLoaderTest {

    private val loader = ThemeLoader()

    // ---------- 路径 1：包不存在 ----------

    @Test
    fun `null pack degrades to fallback with PACK_NOT_FOUND`() {
        val result = loader.load(null)
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.PACK_NOT_FOUND, result.error?.code)
        assertSame(FallbackTheme.snapshot, result.snapshot, "降级必须交付内置 fallback 快照")
        assertTrue(result.snapshot.raw.isNotEmpty())
        assertEquals(emptyList<org.minime.core.api.ImportWarning>(), result.warnings)
    }

    @Test
    fun `blank pack degrades to fallback`() {
        val result = loader.load("   ")
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.PACK_NOT_FOUND, result.error?.code)
        assertEquals(FallbackTheme.snapshot, result.snapshot)
    }

    // ---------- 路径 2：包损坏 ----------

    @Test
    fun `malformed json degrades with PACK_CORRUPT`() {
        val result = loader.load("{ 这不是 JSON")
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.PACK_CORRUPT, result.error?.code)
        assertNotNull(result.snapshot)
    }

    @Test
    fun `json missing sets degrades with SCHEMA_INVALID`() {
        val result = loader.load(
            """
            {"version":"2025.10","manifest":{"name":"x","version":"1.0.0","engineVersion":">=1.0 <2.0"}}
            """.trimIndent(),
        )
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.SCHEMA_INVALID, result.error?.code)
    }

    // ---------- 路径 3：编译异常 ----------

    @Test
    fun `broken alias degrades with ALIAS_BROKEN`() {
        val json = brokenAliasPack()
        val result = loader.load(json)
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.ALIAS_BROKEN, result.error?.code)
        assertSame(FallbackTheme.snapshot, result.snapshot)
    }

    @Test
    fun `incompatible engine version degrades with ENGINE_VERSION_INCOMPATIBLE`() {
        val result = loader.load(
            """
            {
              "version": "2025.10",
              "manifest": {"name":"future","version":"9.9.9","engineVersion":">=9.0.0 <10.0.0"},
              "sets": {"core": {"color.primitive.a": {"${'$'}value": "#000000", "${'$'}type": "color"}}},
              "resolutionOrder": [{"set":"core"}]
            }
            """.trimIndent(),
        )
        assertTrue(result.degraded)
        assertEquals(CompileErrorCode.ENGINE_VERSION_INCOMPATIBLE, result.error?.code)
    }

    // ---------- 成功路径 ----------

    @Test
    fun `bundled default loads without degradation`() {
        val result = loader.loadBundledDefault()
        assertFalse(result.degraded, "内置默认包应编译成功: ${result.error?.message}")
        assertEquals("MiniMe Pure White", result.snapshot.meta.packName)
        assertTrue(result.snapshot.raw.size >= TokenVocabulary.SIZE)
    }

    @Test
    fun `bundled default supports modifiers`() {
        val dark = loader.loadBundledDefault(listOf("dark"))
        assertFalse(dark.degraded)
        assertEquals(listOf("dark"), dark.snapshot.meta.activeModifiers)
        assertEquals("#000000FF", dark.snapshot.color.bgCanvas.hex)
    }

    @Test
    fun `minimal pack compiles with fallback backfill`() {
        // 只写一条令牌：其余 195 条由 fallback 基座回填（不拒绝、不缺失）
        val result = loader.load(
            """
            {
              "version": "2025.10",
              "manifest": {"name":"minimal","version":"0.1.0","engineVersion":">=1.0 <2.0"},
              "sets": {"core": {"color.primitive.systemBlue": {"${'$'}value": "#123456", "${'$'}type": "color"}}},
              "resolutionOrder": [{"set":"core"}]
            }
            """.trimIndent(),
        )
        assertFalse(result.degraded, "最小包应可编译: ${result.error?.message}")
        val raw: Map<String, TokenValue> = result.snapshot.raw
        assertEquals(196, raw.size, "fallback 回填后仍为完整生效表")
        assertEquals("#123456FF", result.snapshot.color.primitiveOrNull("systemBlue")?.hex)
        // 语义层 accent 未覆写 → 取 fallback 值（不因包小而缺失）
        assertEquals("#007AFFFF", result.snapshot.color.accent.hex)
    }

    private fun brokenAliasPack(): String = """
        {
          "version": "2025.10",
          "manifest": {"name":"broken","version":"1.0.0","engineVersion":">=1.0 <2.0"},
          "sets": {
            "core": {
              "color.semantic.accent": {"${'$'}value": "{color.primitive.nope}", "${'$'}type": "color"}
            }
          },
          "resolutionOrder": [{"set":"core"}]
        }
    """.trimIndent()
}
