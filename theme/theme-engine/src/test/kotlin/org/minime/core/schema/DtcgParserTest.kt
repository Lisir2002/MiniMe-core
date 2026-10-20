package org.minime.core.schema

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** DTCG Format 解析器（§5.1 规范边界 + MiniMe 结构规则） */
class DtcgParserTest {

    private val parser = DtcgParser()

    private fun minimal(core: String, extra: String = ""): String = """
        {
          "version": "2025.10",
          "manifest": { "name": "t", "version": "1.0.0", "engineVersion": ">=1.0 <2.0" },
          "sets": { "core": $core }$extra,
          "resolutionOrder": [ { "set": "core" } ]
        }
    """.trimIndent()

    private fun parseExpectCode(json: String): CompileErrorCode =
        try {
            parser.parse(json)
            error("应当抛出 ThemeCompileException")
        } catch (e: ThemeCompileException) {
            e.code
        }

    // ---------- 结构 ----------

    @Test
    fun `minimal pack parses`() {
        val pack = parser.parse(
            minimal("""{ "color.primitive.systemBlue": { "${'$'}value": "#007AFF", "${'$'}type": "color" } }"""),
        )
        assertEquals("t", pack.manifest.name)
        assertEquals(1, pack.sets.size)
        assertEquals(1, pack.resolutionOrder.size)
        assertEquals(setOf("core"), pack.resolutionOrder.map { it.set }.toSet())
        val token = pack.sets["core"]!!.tokens["color.primitive.systemBlue"]!!
        assertEquals(TokenType.COLOR, token.declaredType)
        assertEquals(ColorValue.fromHex("#007AFF"), token.literal)
    }

    @Test
    fun `manifest required fields`() {
        val bad = """
        {
          "version": "2025.10",
          "manifest": { "name": "t", "version": "1.0.0" },
          "sets": { "core": {} },
          "resolutionOrder": [ { "set": "core" } ]
        }
        """.trimIndent()
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(bad))
    }

    @Test
    fun `missing sets and version rejected`() {
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode("""{ "version": "2025.10", "resolutionOrder": [] }"""),
        )
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode("""{ "manifest": {}, "sets": {}, "resolutionOrder": [] }"""),
        )
    }

    @Test
    fun `non-JSON is corrupt`() {
        assertEquals(CompileErrorCode.PACK_CORRUPT, parseExpectCode("not json {"))
    }

    @Test
    fun `resolutionOrder entry shape`() {
        val both = """
        { "version": "2025.10", "manifest": { "name": "t", "version": "1", "engineVersion": "*" },
          "sets": { "core": {} },
          "resolutionOrder": [ { "set": "core", "modifier": "dark" } ] }
        """.trimIndent()
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(both))
        val neither = """
        { "version": "2025.10", "manifest": { "name": "t", "version": "1", "engineVersion": "*" },
          "sets": { "core": {} },
          "resolutionOrder": [ {} ] }
        """.trimIndent()
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(neither))
        val missing = """
        { "version": "2025.10", "manifest": { "name": "t", "version": "1", "engineVersion": "*" },
          "sets": { "core": {} } }
        """.trimIndent()
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(missing))
    }

    // ---------- 组与令牌 ----------

    @Test
    fun `group level type inheritance`() {
        val pack = parser.parse(
            minimal(
                """
                {
                  "color": { "${'$'}type": "color", "primitive": { "systemBlue": { "${'$'}value": "#007AFF" } } }
                }
                """.trimIndent(),
            ),
        )
        val token = pack.sets["core"]!!.tokens["color.primitive.systemBlue"]!!
        assertEquals(TokenType.COLOR, token.declaredType)
        assertEquals(ColorValue.fromHex("#007AFF"), token.literal)
    }

    @Test
    fun `token without type and no group type rejected`() {
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode(minimal("""{ "a": { "${'$'}value": "1" } }""")),
        )
    }

    @Test
    fun `unknown type rejected`() {
        assertEquals(
            CompileErrorCode.TYPE_UNKNOWN,
            parseExpectCode(minimal("""{ "a": { "${'$'}value": "1", "${'$'}type": "gradient" } }""")),
        )
    }

    @Test
    fun `nested and flat paths are equivalent`() {
        val nested = parser.parse(
            minimal("""{ "color": { "primitive": { "systemBlue": { "${'$'}value": "#007AFF", "${'$'}type": "color" } } } }"""),
        )
        val flat = parser.parse(
            minimal("""{ "color.primitive.systemBlue": { "${'$'}value": "#007AFF", "${'$'}type": "color" } }"""),
        )
        assertEquals(
            nested.sets["core"]!!.tokens.keys,
            flat.sets["core"]!!.tokens.keys,
        )
        assertEquals(
            nested.sets["core"]!!.tokens.values.first().literal,
            flat.sets["core"]!!.tokens.values.first().literal,
        )
    }

    @Test
    fun `duplicate path rejected`() {
        val dup = """
        {
          "color.primitive.systemBlue": { "${'$'}value": "#007AFF", "${'$'}type": "color" },
          "color": { "primitive": { "systemBlue": { "${'$'}value": "#0000FF", "${'$'}type": "color" } } }
        }
        """.trimIndent()
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(minimal(dup)))
    }

    @Test
    fun `unknown reserved key rejected`() {
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode(minimal("""{ "a": { "${'$'}value": "1", "${'$'}type": "number", "${'$'}extended": true } }""")),
        )
    }

    @Test
    fun `scalar token must be object with value`() {
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode(minimal("""{ "a": "raw-string" }""")),
        )
    }

    // ---------- 值形态 ----------

    @Test
    fun `color object form and dimension object form`() {
        val pack = parser.parse(
            minimal(
                """
                {
                  "a": { "${'$'}value": { "colorSpace": "srgb", "components": [0, 0.478, 1] }, "${'$'}type": "color" },
                  "b": { "${'$'}value": { "value": 16, "unit": "dp" }, "${'$'}type": "dimension" }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(ColorValue.fromHex("#007AFF"), pack.sets["core"]!!.tokens["a"]!!.literal)
        assertEquals(DimensionValue(16f, "dp"), pack.sets["core"]!!.tokens["b"]!!.literal)
    }

    @Test
    fun `rem em pt units rejected`() {
        for (unit in listOf("rem", "em", "pt")) {
            assertEquals(
                CompileErrorCode.TYPE_MISMATCH,
                parseExpectCode(minimal("""{ "a": { "${'$'}value": "1$unit", "${'$'}type": "dimension" } }""")),
                "单位 $unit 应被拒绝",
            )
        }
    }

    @Test
    fun `dimension requires unit`() {
        assertEquals(
            CompileErrorCode.TYPE_MISMATCH,
            parseExpectCode(minimal("""{ "a": { "${'$'}value": "16", "${'$'}type": "dimension" } }""")),
        )
    }

    @Test
    fun `duration string and number forms`() {
        val pack = parser.parse(
            minimal(
                """
                {
                  "a": { "${'$'}value": "250ms", "${'$'}type": "duration" },
                  "b": { "${'$'}value": "0.25s", "${'$'}type": "duration" },
                  "c": { "${'$'}value": 150, "${'$'}type": "duration" }
                }
                """.trimIndent(),
            ),
        )
        val tokens = pack.sets["core"]!!.tokens
        assertEquals(DurationValue(250f), tokens["a"]!!.literal)
        assertEquals(DurationValue(250f), tokens["b"]!!.literal)
        assertEquals(DurationValue(150f), tokens["c"]!!.literal)
    }

    @Test
    fun `boolean string form`() {
        val pack = parser.parse(
            minimal("""{ "a": { "${'$'}value": "false", "${'$'}type": "boolean" } }"""),
        )
        assertEquals(BooleanValue(false), pack.sets["core"]!!.tokens["a"]!!.literal)
    }

    @Test
    fun `cubicBezier array form`() {
        val pack = parser.parse(
            minimal("""{ "a": { "${'$'}value": [0.2, 0, 0, 1], "${'$'}type": "cubicBezier" } }"""),
        )
        assertEquals(CubicBezierValue(0.2f, 0f, 0f, 1f), pack.sets["core"]!!.tokens["a"]!!.literal)
    }

    // ---------- 别名 ----------

    @Test
    fun `alias extraction`() {
        val pack = parser.parse(
            minimal("""{ "color.semantic.accent": { "${'$'}value": "{color.primitive.systemBlue}", "${'$'}type": "color" } }"""),
        )
        assertEquals("color.primitive.systemBlue", pack.sets["core"]!!.tokens["color.semantic.accent"]!!.alias)
        assertNull(pack.sets["core"]!!.tokens["color.semantic.accent"]!!.literal)
    }

    @Test
    fun `alias syntax must be complete path`() {
        // 注意：仅当值以 "{" 开头才按别名解析；"path}" 是合法字符串字面量（DTCG 语义）
        for (bad in listOf("{path", "{ pa.th }x", "{pa th}", "{}")) {
            assertEquals(
                CompileErrorCode.ALIAS_SYNTAX,
                parseExpectCode(minimal("""{ "a": { "${'$'}value": "$bad", "${'$'}type": "string" } }""")),
                "别名 \"$bad\" 应被拒绝",
            )
        }
    }

    @Test
    fun `string not starting with brace is literal`() {
        val pack = parser.parse(minimal("""{ "a": { "${'$'}value": "path}", "${'$'}type": "string" } }"""))
        assertEquals(StringValue("path}"), pack.sets["core"]!!.tokens["a"]!!.literal)
    }

    @Test
    fun `alias to wrong literal type caught at parse`() {
        // number 型令牌写颜色字面量 → 解析期 TYPE_MISMATCH
        assertEquals(
            CompileErrorCode.TYPE_MISMATCH,
            parseExpectCode(minimal("""{ "a": { "${'$'}value": "#FFFFFF", "${'$'}type": "number" } }""")),
        )
    }

    // ---------- 复合型 ----------

    @Test
    fun `composite typography with field alias`() {
        val pack = parser.parse(
            minimal(
                """
                {
                  "typography.fontFamily.sans": { "${'$'}value": "sans-serif", "${'$'}type": "fontFamily" },
                  "typography.body": {
                    "${'$'}type": "typography",
                    "${'$'}value": { "fontSize": "17sp", "fontWeight": "400", "fontFamily": "{typography.fontFamily.sans}" }
                  }
                }
                """.trimIndent(),
            ),
        )
        val body = pack.sets["core"]!!.tokens["typography.body"]!!
        assertNotNull(body.fields)
        assertEquals("typography.fontFamily.sans", body.fields!!["fontFamily"]!!.alias)
        assertEquals(DimensionValue(17f, "sp"), body.fields!!["fontSize"]!!.literal)
    }

    @Test
    fun `composite undeclared field rejected`() {
        assertEquals(
            CompileErrorCode.SCHEMA_INVALID,
            parseExpectCode(
                minimal(
                    """
                    { "a": { "${'$'}type": "spring", "${'$'}value": { "stiffness": "380", "damping": "0.86", "bogus": "1" } } }
                    """.trimIndent(),
                ),
            ),
        )
    }

    @Test
    fun `composite requires object value`() {
        assertEquals(
            CompileErrorCode.TYPE_MISMATCH,
            parseExpectCode(minimal("""{ "a": { "${'$'}type": "spring", "${'$'}value": "380" } }""")),
        )
    }

    @Test
    fun `composite whole alias allowed`() {
        val pack = parser.parse(
            minimal(
                """
                {
                  "motion.spatial.a": { "${'$'}type": "spring", "${'$'}value": { "stiffness": "380", "damping": "0.86", "initialVelocity": "0" } },
                  "motion.spatial.b": { "${'$'}type": "spring", "${'$'}value": "{motion.spatial.a}" }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("motion.spatial.a", pack.sets["core"]!!.tokens["motion.spatial.b"]!!.alias)
    }

    // ---------- 体积上限 ----------

    @Test
    fun `size limit 512KB`() {
        // 构造略超 512KB 的 sets 子树（合法 JSON，无尾逗号）。
        // 注意：上限按 org.json 紧凑重序列化后的 sets 子树字节数计（去空白），
        // 输入需显著大于 512KB 才能触发（此处目标 620KB）。
        val big = buildString {
            append("{")
            var i = 0
            while (length < 620 * 1024) {
                if (i > 0) append(",")
                append(""""k$i": { "${'$'}value": 1, "${'$'}type": "number" }""")
                i++
            }
            append("}")
        }
        assertEquals(CompileErrorCode.SIZE_LIMIT_EXCEEDED, parseExpectCode(minimal(big)))
    }

    // ---------- $extensions ----------

    @Test
    fun `extensions parsed with reserved keys`() {
        val pack = parser.parse(
            minimal(
                """{ "color.primitive.systemBlue": { "${'$'}value": "#007AFF", "${'$'}type": "color" } }""",
                extra = """
                ,
                "${'$'}extensions": {
                  "minime": {
                    "editor": { "syntax.keyword": { "${'$'}value": "#C7254E", "${'$'}type": "color" } },
                    "terminal": { "ansi.red": { "${'$'}value": "#FF453A", "${'$'}type": "color" } },
                    "chart": { "c1": { "${'$'}value": "{color.primitive.systemBlue}", "${'$'}type": "color" } },
                    "schema": { "spring": { "fields": ["stiffness", "damping", "initialVelocity"] } },
                    "glass": { "consumers": ["color.semantic.text.primary"] }
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(setOf("editor", "terminal", "chart"), pack.extensions.keys)
        assertTrue("editor.syntax.keyword" in pack.extensions["editor"]!!.tokens)
        assertTrue("terminal.ansi.red" in pack.extensions["terminal"]!!.tokens)
        assertEquals("color.primitive.systemBlue", pack.extensions["chart"]!!.tokens["chart.c1"]!!.alias)
        assertEquals(listOf("color.semantic.text.primary"), pack.glassConfig.consumers)
        assertEquals(
            listOf("stiffness", "damping", "initialVelocity"),
            pack.schemaDeclarations.spring,
        )
    }

    @Test
    fun `extensions non-object group rejected`() {
        val json = minimal("{}", extra = """, "${'$'}extensions": { "minime": { "editor": "oops" } }""")
        assertEquals(CompileErrorCode.SCHEMA_INVALID, parseExpectCode(json))
    }

    @Test
    fun `pack without extensions is valid`() {
        val pack = parser.parse(minimal("{}"))
        assertTrue(pack.extensions.isEmpty())
        assertTrue(pack.glassConfig.consumers.isEmpty())
        assertNull(pack.schemaDeclarations.spring)
    }
}
