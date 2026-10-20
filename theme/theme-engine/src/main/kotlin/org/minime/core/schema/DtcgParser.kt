package org.minime.core.schema

import org.json.JSONArray
import org.json.JSONObject
import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException

/**
 * DTCG Format 解析器（白皮书 §5.1）。
 *
 * 严格遵守的规范部分：`version` / `$value` / `$type` / `$description` / `$extensions` /
 * 别名 `{path}` / 组级 `$type` 继承 / 嵌套与扁平路径等价。
 * 宽容部分（DTCG 2025.10 草案新值形态）：颜色与尺寸的**对象形态**（sRGB 分量 / value+unit），
 * rem/em/pt 等非 dp/sp/px 单位明确拒绝（转换需要根字号上下文，不做隐式猜测）。
 *
 * 结构规则（MiniMe Resolver 稳定子集）：
 * - `sets` + `resolutionOrder`（set/modifier 二选一，全部 set 必须被引用且仅一次）；
 * - `$extensions.minime` 下 `schema`（自定义型字段声明）与 `glass`（表面消费配置）为保留键，
 *   其余键为开放词汇令牌组。
 */
class DtcgParser {

    fun parse(json: String): ThemePack {
        val root: JSONObject = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ThemeCompileException(CompileErrorCode.PACK_CORRUPT, "主题包不是合法 JSON: ${e.message}")
        }

        // 体积上限：tokens（sets 子树）≤ 512KB（§5.2）
        val setsObj = root.optJSONObject("sets")
            ?: throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "主题包缺少 sets")
        val setsBytes = setsObj.toString().toByteArray(Charsets.UTF_8).size
        if (setsBytes > SIZE_LIMIT_BYTES) {
            throw ThemeCompileException(
                CompileErrorCode.SIZE_LIMIT_EXCEEDED,
                "主题包令牌体积超限: $setsBytes B > $SIZE_LIMIT_BYTES B（512KB）",
            )
        }

        val dcgVersion = root.nonBlankString("version")
            ?: throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "主题包缺少 version（DTCG 格式版本）")

        val manifest = parseManifest(root.optJSONObject("manifest"))

        val sets = LinkedHashMap<String, SetModel>()
        for (name in setsObj.keys()) {
            val setJson = setsObj.optJSONObject(name)
                ?: throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "set \"$name\" 必须是对象")
            sets[name] = SetModel(name, parseGroup(setJson, "", null))
        }

        val resolutionOrder = parseResolutionOrder(root.optJSONArray("resolutionOrder"))

        val (extensions, glassConfig, schemaDeclarations) = parseExtensions(root.optJSONObject("\$extensions"))

        return ThemePack(
            dcgVersion = dcgVersion,
            manifest = manifest,
            sets = sets,
            resolutionOrder = resolutionOrder,
            extensions = extensions,
            glassConfig = glassConfig,
            schemaDeclarations = schemaDeclarations,
        )
    }

    // ---------- manifest ----------

    private fun parseManifest(obj: JSONObject?): Manifest {
        if (obj == null) throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "主题包缺少 manifest")
        val name = obj.nonBlankString("name")
        val version = obj.nonBlankString("version")
        val engineVersion = obj.nonBlankString("engineVersion")
        if (name == null || version == null || engineVersion == null) {
            throw ThemeCompileException(
                CompileErrorCode.SCHEMA_INVALID,
                "manifest 必填字段缺失（name/version/engineVersion）: name=${obj.opt("name")}, version=${obj.opt("version")}, engineVersion=${obj.opt("engineVersion")}",
            )
        }
        return Manifest(
            name = name,
            version = version,
            author = obj.nonBlankString("author"),
            engineVersion = engineVersion,
            description = obj.nonBlankString("description"),
            preview = obj.nonBlankString("preview"),
        )
    }

    // ---------- resolutionOrder ----------

    private fun parseResolutionOrder(arr: JSONArray?): List<ResolutionEntry> {
        if (arr == null || arr.length() == 0) {
            throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "主题包缺少 resolutionOrder")
        }
        val out = ArrayList<ResolutionEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
                ?: throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "resolutionOrder[$i] 必须是对象")
            val set = o.nonBlankString("set")
            val modifier = o.nonBlankString("modifier")
            out += when {
                set != null && modifier != null ->
                    throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "resolutionOrder[$i] 同时声明 set 与 modifier")
                set != null -> ResolutionEntry(set = set, modifier = null)
                modifier != null -> ResolutionEntry(set = null, modifier = modifier)
                else -> throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "resolutionOrder[$i] 缺少 set/modifier")
            }
        }
        return out
    }

    // ---------- $extensions ----------

    private data class ParsedExtensions(
        val groups: Map<String, ExtensionGroup>,
        val glassConfig: GlassConfig,
        val schemaDeclarations: SchemaDeclarations,
    )

    private fun parseExtensions(extensions: JSONObject?): ParsedExtensions {
        if (extensions == null) return ParsedExtensions(emptyMap(), GlassConfig(), SchemaDeclarations())
        val minime = extensions.optJSONObject("minime")
            ?: return ParsedExtensions(emptyMap(), GlassConfig(), SchemaDeclarations())

        var springFields: List<String>? = null
        var glassFields: List<String>? = null
        var consumers: List<String> = emptyList()

        minime.optJSONObject("schema")?.let { schema ->
            springFields = schema.optJSONObject("spring")?.optJSONArray("fields")?.toStringList()
            glassFields = schema.optJSONObject("glass")?.optJSONArray("fields")?.toStringList()
        }
        minime.optJSONObject("glass")?.let { glass ->
            consumers = glass.optJSONArray("consumers")?.toStringList() ?: emptyList()
        }

        val groups = LinkedHashMap<String, ExtensionGroup>()
        for (key in minime.keys()) {
            if (key == RESERVED_SCHEMA || key == RESERVED_GLASS) continue
            val g = minime.optJSONObject(key)
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "${'$'}extensions.minime.$key 必须是令牌组对象（保留键: $RESERVED_SCHEMA/$RESERVED_GLASS）",
                )
            groups[key] = ExtensionGroup(key, parseGroup(g, key, null))
        }
        return ParsedExtensions(
            groups = groups,
            glassConfig = GlassConfig(consumers),
            schemaDeclarations = SchemaDeclarations(springFields, glassFields),
        )
    }

    // ---------- 组 / 令牌 ----------

    private fun parseGroup(obj: JSONObject, prefix: String, inherited: TokenType?): Map<String, RawToken> {
        val out = LinkedHashMap<String, RawToken>()
        val groupType = obj.optString("\$type").takeIf { it.isNotEmpty() }?.let { raw ->
            TokenType.fromRaw(raw)
                ?: throw ThemeCompileException(CompileErrorCode.TYPE_UNKNOWN, "未知组级 \$type: \"$raw\"（$prefix）")
        } ?: inherited

        for (key in obj.keys()) {
            if (key == "\$type" || key == "\$description") continue
            if (key.startsWith("\$")) {
                throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "未知保留键: \"$key\"（$prefix）")
            }
            val value = obj.get(key)
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            if (value is JSONObject) {
                if (value.has("\$value")) {
                    out.putChecked(parseToken(path, value, groupType))
                } else {
                    parseGroup(value, path, groupType).forEach { out.putChecked(it.value) }
                }
            } else {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "令牌 \"$path\" 必须是含 \$value 的对象（当前为 ${value.javaClass.simpleName}）",
                )
            }
        }
        return out
    }

    private fun MutableMap<String, RawToken>.putChecked(token: RawToken) {
        if (containsKey(token.path)) {
            throw ThemeCompileException(CompileErrorCode.SCHEMA_INVALID, "重复令牌路径: ${token.path}")
        }
        put(token.path, token)
    }

    private fun parseToken(path: String, obj: JSONObject, inherited: TokenType?): RawToken {
        // 令牌级保留键白名单（DTCG：$ 前缀为保留；未知键拒绝以捕获拼写错误，扩展走 $extensions）
        for (key in obj.keys()) {
            if (key.startsWith("\$") && key !in TOKEN_RESERVED_KEYS) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "令牌 \"$path\" 含未知保留键: \"$key\"（允许: $TOKEN_RESERVED_KEYS）",
                )
            }
        }

        val type = when (val raw = obj.optString("\$type").takeIf { it.isNotEmpty() }) {
            null -> inherited
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "令牌 \"$path\" 缺少 \$type（所在组也未声明 \$type）",
                )
            else -> TokenType.fromRaw(raw)
                ?: throw ThemeCompileException(CompileErrorCode.TYPE_UNKNOWN, "未知令牌类型: \"$raw\"（$path）")
        }

        val raw = obj.get("\$value")

        if (raw is String && raw.trimStart().startsWith("{")) {
            return RawToken(path, type, alias = extractAliasPath(raw))
        }
        if (!type.isComposite) {
            return RawToken(path, type, literal = parseScalar(type, raw, path))
        }

        // 复合型：整体别名 或 字段对象
        val schema = CompositeSchemas.forType(type)
            ?: error("引擎内部错误: 复合型 $type 缺少 schema")
        if (raw !is JSONObject) {
            throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "复合型 \"$path\" 的 \$value 必须是对象或别名（当前为 ${raw.javaClass.simpleName}）",
            )
        }
        val fields = LinkedHashMap<String, RawField>()
        for (key in raw.keys()) {
            if (key.startsWith("\$")) continue
            val expected = schema[key]
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "复合型 \"$path\" 含未声明字段: \"$key\"（$type schema: ${schema.keys}）",
                )
            val fv = raw.get(key)
            fields[key] = if (fv is String && fv.trimStart().startsWith("{")) {
                RawField(alias = extractAliasPath(fv))
            } else {
                RawField(literal = parseScalar(expected, fv, "$path.$key"))
            }
        }
        return RawToken(path, type, fields = fields)
    }

    // ---------- 标量值解析 ----------

    private fun parseScalar(type: TokenType, value: Any, path: String): TokenValue = when (type) {
        TokenType.COLOR -> when (value) {
            is String -> try {
                ColorValue.fromHex(value)
            } catch (e: IllegalArgumentException) {
                throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": ${e.message}")
            }
            is JSONObject -> parseColorObject(value, path)
            else -> throw mismatch(type, value, path)
        }

        TokenType.DIMENSION -> when (value) {
            is String -> parseDimension(value, path)
            is JSONObject -> {
                val unit = value.nonBlankString("unit")
                val v = value.opt("value")
                if (unit == null || v !is Number) throw mismatch(type, value, path)
                try {
                    DimensionValue(v.toFloat(), unit)
                } catch (e: IllegalArgumentException) {
                    throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": ${e.message}")
                }
            }
            else -> throw mismatch(type, value, path)
        }

        TokenType.DURATION -> when (value) {
            is String -> parseDuration(value, path)
            is Number -> DurationValue(value.toFloat())
            else -> throw mismatch(type, value, path)
        }

        TokenType.NUMBER -> when (value) {
            is Number -> NumberValue(value.toFloat())
            is String -> value.toFloatOrNull()?.let { NumberValue(it) } ?: throw mismatch(type, value, path)
            else -> throw mismatch(type, value, path)
        }

        TokenType.BOOLEAN -> when (value) {
            is Boolean -> BooleanValue(value)
            is String -> when (value.trim().lowercase()) {
                "true" -> BooleanValue(true)
                "false" -> BooleanValue(false)
                else -> throw mismatch(type, value, path)
            }
            else -> throw mismatch(type, value, path)
        }

        TokenType.STRING, TokenType.FONT_FAMILY -> when (value) {
            is String -> StringValue(value)
            else -> throw mismatch(type, value, path)
        }

        TokenType.FONT_WEIGHT -> when (value) {
            is String -> if (value.matches(FONT_WEIGHT)) StringValue(value) else throw mismatch(type, value, path)
            is Number -> StringValue(value.toInt().toString())
            else -> throw mismatch(type, value, path)
        }

        TokenType.CUBIC_BEZIER -> when (value) {
            is JSONArray -> {
                if (value.length() != 4 || (0 until 4).any { value.opt(it) !is Number }) {
                    throw mismatch(type, value, path)
                }
                CubicBezierValue(
                    value.getDouble(0).toFloat(),
                    value.getDouble(1).toFloat(),
                    value.getDouble(2).toFloat(),
                    value.getDouble(3).toFloat(),
                )
            }
            else -> throw mismatch(type, value, path)
        }

        TokenType.TYPOGRAPHY, TokenType.SHADOW, TokenType.SPRING, TokenType.GLASS ->
            error("引擎内部错误: 复合型 $type 不应进入标量解析（$path）")
    }

    private fun mismatch(type: TokenType, value: Any, path: String) = ThemeCompileException(
        CompileErrorCode.TYPE_MISMATCH,
        "令牌 \"$path\" 期望 $type，实际值为 ${describe(value)}",
    )

    private fun describe(value: Any): String = when (value) {
        is JSONObject -> "对象"
        is JSONArray -> "数组"
        else -> "\"$value\"（${value.javaClass.simpleName}）"
    }

    private fun parseColorObject(obj: JSONObject, path: String): ColorValue {
        val colorSpace = obj.nonBlankString("colorSpace")
            ?: throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": 对象形态颜色缺少 colorSpace")
        val comps = obj.optJSONArray("components") ?: throw ThemeCompileException(
            CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": 对象形态颜色缺少 components",
        )
        val list = (0 until comps.length()).map { comps.opt(it) }
        if (list.any { it !is Number }) {
            throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": components 必须为数值数组")
        }
        return try {
            ColorValue.fromObject(colorSpace, list.map { (it as Number).toDouble() })
        } catch (e: IllegalArgumentException) {
            throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": ${e.message}")
        }
    }

    private fun parseDimension(raw: String, path: String): DimensionValue {
        val m = DIMENSION.matchEntire(raw.trim())
            ?: throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "令牌 \"$path\": 非法 dimension \"$raw\"（期望如 \"12dp\"）",
            )
        return try {
            DimensionValue(m.groupValues[1].toFloat(), m.groupValues[2])
        } catch (e: IllegalArgumentException) {
            throw ThemeCompileException(CompileErrorCode.TYPE_MISMATCH, "令牌 \"$path\": ${e.message}")
        }
    }

    private fun parseDuration(raw: String, path: String): DurationValue {
        val m = DURATION.matchEntire(raw.trim())
            ?: throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "令牌 \"$path\": 非法 duration \"$raw\"（期望如 \"250ms\" 或 \"0.25s\"）",
            )
        val v = m.groupValues[1].toFloat()
        return DurationValue(if (m.groupValues[2] == "s") v * 1000f else v)
    }

    companion object {
        const val SIZE_LIMIT_BYTES: Int = 512 * 1024

        /** 令牌级保留键白名单（DTCG Format：$ 前缀保留，其余为自定义扩展） */
        private val TOKEN_RESERVED_KEYS = setOf("\$value", "\$type", "\$description", "\$extensions")

        private const val RESERVED_SCHEMA = "schema"
        private const val RESERVED_GLASS = "glass"

        private val ALIAS_PATH = Regex("^\\{([A-Za-z0-9._-]+)\\}$")
        private val DIMENSION = Regex("^(\\d+(?:\\.\\d+)?)(dp|sp|px)$")
        private val DURATION = Regex("^(\\d+(?:\\.\\d+)?)(ms|s)$")
        private val FONT_WEIGHT = Regex("^([1-9]\\d{0,3}|normal|bold|lighter|bolder)$")

        /** 别名目标路径提取（必须是 {token.path} 完整形态，§5.1） */
        fun extractAliasPath(raw: String): String {
            val m = ALIAS_PATH.matchEntire(raw.trim())
                ?: throw ThemeCompileException(
                    CompileErrorCode.ALIAS_SYNTAX,
                    "非法别名语法: \"$raw\"（别名必须是 {token.path} 形态）",
                )
            return m.groupValues[1]
        }

        internal fun JSONArray.toStringList(): List<String> = (0 until length()).map { optString(it) }

        internal fun JSONObject.nonBlankString(key: String): String? =
            optString(key).takeIf { it.isNotBlank() }
    }
}
