package org.minime.core.schema

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * diff 主题包序列化器（R4.3-A，设计文档 §10 用例 ①-⑦）。
 *
 * 对拍口径：
 * - round-trip 除 typography lineHeight 乘数（4/3 类分数，Double 微误差）走 delta ≤0.001 外，
 *   其余类型精确相等（设计偏离 2）；
 * - 单位映射全部对照 DTCG 2025.10 规范明文（dp≡px / 1rem≡16sp / ms 首选）推导期望值，
 *   不用拍脑袋数。
 */
class DiffSerializerTest {

    private val serializer = DiffSerializer
    private val base = DiffSerializer.DiffBaseMeta(packId = "built-in", packVersion = "1.0.0", fingerprint = "sha256:test")

    /** 全类型样本覆写（覆盖 color/dimension(dp·sp·px)/duration/number/boolean/string/fontWeight/typography/spring/glass） */
    private fun sampleOverlays(): Map<String, TokenValue> = mapOf(
        "color.semantic.accent" to ColorValue.fromHex("#0066CC"),
        "color.semantic.text.primary" to ColorValue.fromHex("#1A1A1A80"), // 半透明（alpha 通道往返）
        "shape.radius.card" to DimensionValue(16f, "dp"),
        "elevation.hairline" to DimensionValue(1f, "px"), // 原生 px 例外路径
        "typography.body" to TypographyValue(
            fontSize = DimensionValue(15f, "sp"),
            lineHeight = DimensionValue(20f, "sp"),
            fontWeight = "600",
            fontFamily = "sans",
            letterSpacing = DimensionValue(0.2f, "sp"),
        ),
        "typography.fontFamily.mono" to StringValue("monospace"),
        "motion.duration.slow" to DurationValue(300f),
        "motion.spatial.default" to SpringValue(stiffness = 400f, damping = 0.9f, initialVelocity = 0f),
        "glass.thin" to GlassValue(
            blur = DimensionValue(12f, "dp"),
            saturation = 1.7f,
            tint = ColorValue.fromHex("#FFFFFF1A"),
            tintAlpha = 0.72f,
            hairline = true,
        ),
        "glass.enabled" to BooleanValue(true),
        "opacity.dimmed" to NumberValue(0.45f),
    )

    // ---------- ① round-trip 全类型 ----------

    @Test
    fun `round trip preserves every token type`() {
        val overlays = sampleOverlays()
        val json = serializer.serialize(overlays, base, exportedAt = "2026-10-22T14:30:00+08:00")
        val parsed = serializer.deserialize(json)

        assertEquals(overlays.size, parsed.overlays.size)
        assertEquals(DiffSerializer.FORMAT, parsed.meta.format)
        assertTrue(parsed.hasMinimeNamespace)

        for ((path, expected) in overlays) {
            val actual = parsed.overlays[path] ?: error("往返丢失路径: $path")
            when (expected) {
                is TypographyValue -> {
                    actual as TypographyValue
                    // lineHeight 乘数（20/15）Double 微误差 → delta；其余字段精确
                    assertEquals(expected.fontSize.value, actual.fontSize!!.value, 0.001f)
                    assertEquals(expected.lineHeight!!.value, actual.lineHeight!!.value, 0.001f)
                    assertEquals(expected.fontWeight, actual.fontWeight)
                    assertEquals(expected.fontFamily, actual.fontFamily)
                    assertEquals(expected.letterSpacing!!.value, actual.letterSpacing!!.value, 0.001f)
                }
                else -> assertEquals(expected, actual, "路径 $path 往返不一致")
            }
        }
    }

    @Test
    fun `typography null fields are omitted and restored`() {
        val overlays = mapOf(
            "typography.caption2" to TypographyValue(
                fontSize = DimensionValue(11f, "sp"),
                lineHeight = null,
                fontWeight = null,
                fontFamily = null,
                letterSpacing = null,
            ),
        )
        val json = serializer.serialize(overlays, base, exportedAt = "2026-10-22T14:30:00+08:00")
        val body = JSONObject(json).getJSONObject("\$tokens").getJSONObject("typography").getJSONObject("caption2")
        // 可空字段省略（DTCG §9.8 缺字段 = 不设置）
        assertTrue(!body.getJSONObject("\$value").has("lineHeight"))
        assertTrue(!body.getJSONObject("\$value").has("fontWeight"))
        // fontSize 必填，仍在
        assertTrue(body.getJSONObject("\$value").has("fontSize"))

        val parsed = serializer.deserialize(json)
        val restored = parsed.overlays["typography.caption2"] as TypographyValue
        assertNull(restored.lineHeight)
        assertNull(restored.fontWeight)
        assertNull(restored.fontFamily)
        assertNull(restored.letterSpacing)
        assertEquals(11f, restored.fontSize.value, 0.001f)
    }

    // ---------- ② 单位映射（DTCG 规范明文） ----------

    @Test
    fun `dimension export maps dp to px and sp to rem per spec`() {
        val json = serializer.serialize(
            mapOf("shape.radius.card" to DimensionValue(16f, "dp")), base, "2026-10-22T14:30:00+08:00",
        )
        val card = JSONObject(json).getJSONObject("\$tokens").getJSONObject("shape")
            .getJSONObject("radius").getJSONObject("card").getJSONObject("\$value")
        // dp → px 同值（§8.2.1：px 的 Android 等价是 dp）
        assertEquals(16.0, card.getDouble("value"), 1e-9)
        assertEquals("px", card.getString("unit"))

        // sp → rem ÷16（§8.2.1：1rem ≡ 16sp）；15sp = 0.9375rem（1/16 步进精确）
        val json2 = serializer.serialize(
            mapOf("typography.body" to TypographyValue(fontSize = DimensionValue(15f, "sp"))),
            base, "2026-10-22T14:30:00+08:00",
        )
        val fontSize = JSONObject(json2).getJSONObject("\$tokens").getJSONObject("typography")
            .getJSONObject("body").getJSONObject("\$value").getJSONObject("fontSize")
        assertEquals(0.9375, fontSize.getDouble("value"), 1e-9)
        assertEquals("rem", fontSize.getString("unit"))
    }

    @Test
    fun `dimension import maps px to dp except hairline`() {
        // 手写 DTCG 文件：shape.radius.card 用 px、elevation.hairline 用 px
        val json = """
        {
          "${'$'}tokens": {
            "shape": { "${'$'}type": "dimension", "radius": { "card": { "${'$'}value": { "value": 16, "unit": "px" } } } },
            "elevation": { "${'$'}type": "dimension", "hairline": { "${'$'}value": { "value": 1, "unit": "px" } } }
          }
        }
        """.trimIndent()
        val parsed = serializer.deserialize(json)
        // 规范等价：px → dp（hairline 之外的路径）
        assertEquals(DimensionValue(16f, "dp"), parsed.overlays["shape.radius.card"])
        // 例外：hairline 是 MiniMe 原生 px（真实像素发丝线）
        assertEquals(DimensionValue(1f, "px"), parsed.overlays["elevation.hairline"])
    }

    @Test
    fun `dimension import maps rem to sp times sixteen`() {
        // DTCG §8.2.1：1rem ≡ 16sp。外部 DTCG 工具产物常用 rem（相对根字号 16px）
        val json = """
        {
          "${'$'}tokens": {
            "shape": { "${'$'}type": "dimension", "radius": { "card": { "${'$'}value": { "value": 1, "unit": "rem" } } } },
            "typography": {
              "body": {
                "${'$'}type": "typography",
                "${'$'}value": {
                  "fontSize": { "value": 0.9375, "unit": "rem" },
                  "lineHeight": 1.3333333333333333,
                  "letterSpacing": { "value": 0.0125, "unit": "rem" }
                }
              }
            }
          }
        }
        """.trimIndent()
        val parsed = serializer.deserialize(json)
        // 1rem × 16 = 16sp（规范等价换算，非拍脑袋）
        assertEquals(DimensionValue(16f, "sp"), parsed.overlays["shape.radius.card"])
        val body = parsed.overlays["typography.body"] as TypographyValue
        // 0.9375rem × 16 = 15sp（1/16 步进精确二进制小数）
        assertEquals(DimensionValue(15f, "sp"), body.fontSize)
        // 0.0125rem × 16 = 0.2sp
        assertEquals(DimensionValue(0.2f, "sp"), body.letterSpacing)
        // lineHeight 乘数（§9.8）× fontSize 15sp ≈ 20sp（Double 微误差 delta 兜底）
        assertEquals(20f, body.lineHeight!!.value, 0.001f)
    }

    @Test
    fun `duration round trip keeps milliseconds`() {
        val json = serializer.serialize(
            mapOf("motion.duration.slow" to DurationValue(300f)), base, "2026-10-22T14:30:00+08:00",
        )
        val value = JSONObject(json).getJSONObject("\$tokens").getJSONObject("motion")
            .getJSONObject("duration").getJSONObject("slow").getJSONObject("\$value")
        assertEquals(300.0, value.getDouble("value"), 1e-9)
        assertEquals("ms", value.getString("unit"))
        val parsed = serializer.deserialize(json)
        assertEquals(DurationValue(300f), parsed.overlays["motion.duration.slow"])
    }

    @Test
    fun `color export fills DTCG object form and import prefers hex`() {
        val json = serializer.serialize(
            mapOf("color.semantic.accent" to ColorValue.fromHex("#0066CC")), base, "2026-10-22T14:30:00+08:00",
        )
        val value = JSONObject(json).getJSONObject("\$tokens").getJSONObject("color")
            .getJSONObject("semantic").getJSONObject("accent").getJSONObject("\$value")
        assertEquals("srgb", value.getString("colorSpace"))
        assertEquals(3, value.getJSONArray("components").length())
        // 内部规范化 8 位 hex（DTCG #RRGGBBAA 形态，保真源 D5）
        assertEquals("#0066CCFF", value.getString("hex"))
        assertEquals(1.0, value.getDouble("alpha"), 1e-9)

        // 半透明 alpha 往返（hex 第 7-8 位是保真源）
        val json2 = serializer.serialize(
            mapOf("color.semantic.text.primary" to ColorValue.fromHex("#1A1A1A80")),
            base, "2026-10-22T14:30:00+08:00",
        )
        val parsed2 = serializer.deserialize(json2)
        assertEquals(ColorValue.fromHex("#1A1A1A80"), parsed2.overlays["color.semantic.text.primary"])

        // components 兜底：外部 DTCG 工具产物可能只有 components 无 hex（0.4×255=102=0x66，0.8×255=204=0xCC）
        val componentsOnly = """
        {
          "${'$'}tokens": {
            "color": { "${'$'}type": "color", "semantic": { "accent": { "${'$'}value": { "colorSpace": "srgb", "components": [0.0, 0.4, 0.8] } } } }
          }
        }
        """.trimIndent()
        val parsed3 = serializer.deserialize(componentsOnly)
        assertEquals(ColorValue.fromHex("#0066CC"), parsed3.overlays["color.semantic.accent"])
    }

    // ---------- ③ 坏包拒绝 ----------

    @Test
    fun `rejects non-JSON input`() {
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize("not json {{{") }
        assertEquals("PACK_CORRUPT", e.code)
    }

    @Test
    fun `rejects empty token tree`() {
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize("""{ "${'$'}tokens": {} }""") }
        assertEquals("SCHEMA_INVALID", e.code)
    }

    @Test
    fun `rejects unknown token type`() {
        val json = """{ "${'$'}tokens": { "shape": { "${'$'}type": "blurple", "radius": { "card": { "${'$'}value": "16px" } } } } }"""
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize(json) }
        assertEquals("TYPE_UNKNOWN", e.code)
    }

    @Test
    fun `rejects count mismatch`() {
        val overlays = mapOf("shape.radius.card" to DimensionValue(16f, "dp"))
        val json = serializer.serialize(overlays, base, "2026-10-22T14:30:00+08:00")
        val bad = JSONObject(json)
        bad.getJSONObject("minime").put("count", 99)
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize(bad.toString()) }
        assertEquals("SCHEMA_INVALID", e.code)
        assertTrue(e.detail.contains("count=99"))
    }

    @Test
    fun `rejects unsupported diff version`() {
        val overlays = mapOf("shape.radius.card" to DimensionValue(16f, "dp"))
        val json = serializer.serialize(overlays, base, "2026-10-22T14:30:00+08:00")
        val bad = JSONObject(json)
        bad.getJSONObject("minime").put("diffVersion", 2)
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize(bad.toString()) }
        assertEquals("DIFF_VERSION_UNSUPPORTED", e.code)
    }

    @Test
    fun `serialize rejects path outside vocabulary`() {
        val e = assertThrows<DiffSerializer.DiffFormatException> {
            serializer.serialize(mapOf("chart.palette.rainbow" to ColorValue.fromHex("#FF0000")), base, "t")
        }
        assertEquals("SCHEMA_INVALID", e.code)
        assertTrue(e.detail.contains("chart.palette.rainbow"))
    }

    @Test
    fun `rejects alias references`() {
        val json = """{ "${'$'}tokens": { "shape": { "${'$'}type": "dimension", "radius": { "card": { "${'$'}value": "{shape.radius.button}" } } } } }"""
        val e = assertThrows<DiffSerializer.DiffFormatException> { serializer.deserialize(json) }
        assertEquals("SCHEMA_INVALID", e.code)
        assertTrue(e.detail.contains("别名"))
    }

    // ---------- ⑤ 形态 B：纯 DTCG ----------

    @Test
    fun `pure DTCG file without minime namespace imports as form B`() {
        val json = """
        {
          "color": { "${'$'}type": "color", "accent": { "${'$'}value": "#FF9500" } },
          "shape": { "${'$'}type": "dimension", "radius": { "card": { "${'$'}value": { "value": 20, "unit": "px" } } } }
        }
        """.trimIndent()
        val parsed = serializer.deserialize(json)
        assertTrue(!parsed.hasMinimeNamespace)
        assertNull(parsed.meta.format)
        assertEquals(ColorValue.fromHex("#FF9500"), parsed.overlays["color.accent"])
        assertEquals(DimensionValue(20f, "dp"), parsed.overlays["shape.radius.card"])
    }

    @Test
    fun `token level type overrides group level`() {
        // DTCG §6.7.3：令牌显式 $type 优先于组级
        val json = """
        {
          "${'$'}tokens": {
            "mixed": {
              "${'$'}type": "number",
              "alpha": { "${'$'}value": 0.5 },
              "flag": { "${'$'}type": "boolean", "${'$'}value": true }
            }
          }
        }
        """.trimIndent()
        val parsed = serializer.deserialize(json)
        assertEquals(NumberValue(0.5f), parsed.overlays["mixed.alpha"])
        assertEquals(BooleanValue(true), parsed.overlays["mixed.flag"])
    }

    // ---------- ⑥ 指纹 ----------

    private fun defaultPack(): ThemePack =
        DtcgParser().parse(
            javaClass.getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() },
        )

    @Test
    fun `fingerprint is semantic and stable`() {
        val pack = defaultPack()
        val f1 = serializer.computeFingerprint(pack)
        val f2 = serializer.computeFingerprint(pack)
        assertEquals(f1, f2, "同包指纹必须确定")
        assertTrue(f1.startsWith("sha256:"), "指纹前缀")
        assertEquals(7 + 64, f1.length, "sha256: + 64 hex")

        // 语义指纹：重新序列化（键序/空白不同）不改变指纹——ThemePack 模型级计算天然如此，
        // 这里验证不同包（改 manifest.version）指纹不同
        val mutated = pack.copy(
            manifest = pack.manifest.copy(version = pack.manifest.version + "-mutated"),
        )
        assertTrue(serializer.computeFingerprint(mutated) != f1, "不同包指纹必须不同")
    }

    @Test
    fun `fingerprint changes when a token value changes`() {
        val pack = defaultPack()
        val before = serializer.computeFingerprint(pack)
        // 改一个 set 的一条令牌字面量
        val setName = pack.sets.keys.first()
        val set = pack.sets.getValue(setName)
        val tokenPath = set.tokens.keys.first()
        val token = set.tokens.getValue(tokenPath)
        val mutatedSet = set.copy(
            tokens = set.tokens + (tokenPath to token.copy(literal = NumberValue(0.123f))),
        )
        val mutated = pack.copy(sets = pack.sets + (setName to mutatedSet))
        assertTrue(serializer.computeFingerprint(mutated) != before, "令牌值变更必须反映到指纹")
    }
}
