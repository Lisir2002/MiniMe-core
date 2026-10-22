package org.minime.core.schema

import org.json.JSONArray
import org.json.JSONObject
import org.minime.core.api.CompileErrorCode
import org.minime.core.tokens.TokenVocabulary
import java.security.MessageDigest

/**
 * diff 主题包（R4.3，设计文档 §4/§5/§6）。
 *
 * 格式 = **DTCG 2025.10 稳定版对齐 + minime 专有扩展**（2026-10-22 维护者裁定 1）：
 * - `$tokens` 树的值部分严守 DTCG Format Module 2025.10（designtokens.org/TR/2025.10/format/）：
 *   组嵌套 / 令牌 `$value` / `$type` 组级继承 / 颜色对象形态 / dimension·duration 对象形态；
 * - minime 专有数据收在顶层 `minime` 命名空间（diffVersion / 基础包指纹 / 导出时间），
 *   对应 DTCG §3.2「tools may add metadata」与 `$extensions` 精神，不发明并列标准段；
 * - 复合值**组装 DTCG 复合体**（裁定 2）：typography 用标准 typography 复合体
 *   （lineHeight 为 fontSize 乘数，§9.8）；glass 用 minime 扩展 `$type: "glass"` 复合体；
 *   spring 无 DTCG 类型且与 cubicBezier 不等价，三条 number 平铺；
 * - 布尔用扩展 `$type: "boolean"`（DTCG 无 boolean 类型，§8.8 additional types 非规范；裁定 3）。
 *
 * 单位映射（D4，全部有规范明文依据）：
 * - dp → px / px → dp：DTCG §8.2.1「px 的 Android 等价是 dp」；
 * - sp → rem(÷16) / rem → sp(×16)：§8.2.1「1rem 的 Android 等价是 16sp」；整数 sp/16 为
 *   精确二进制小数（1/16 步进），往返无损；
 * - ms → ms：§8.5 duration 首选毫秒；
 * - **例外**：`elevation.hairline` 是 MiniMe 原生 px 路径（真实像素发丝线，非 dp），
 *   导出 px 原样、导入保留 px（见 [PX_NATIVE_PATHS]）。
 *
 * 保真口径（偏离 2）：typography lineHeight 乘数除法（如 20/15=4/3）无有限十进制精确表示，
 * Double 全精度序列化后往返有 ~1e-15 相对误差——round-trip 测试对 typography 字段走
 * delta 断言（≤0.001），其余类型精确。
 */
object DiffSerializer {

    /** minime 命名空间 format 标识值（导入三形态判定的主键） */
    const val FORMAT: String = "minime-diff"

    /** diff 包结构版本；结构不兼容演进时递增，deserialize 拒绝更高版本 */
    const val DIFF_VERSION: Int = 1

    /** MiniMe 原生 px 单位路径（导入时不按 DTCG px≡dp 规范转换，保留 px 语义） */
    val PX_NATIVE_PATHS: Set<String> = setOf("elevation.hairline")

    // ---------- 数据结构 ----------

    /** 基础包元信息（导出时填充 minime 命名空间；导入时仅作警告级比对，见设计 §6.3） */
    data class DiffBaseMeta(
        val packId: String,
        val packVersion: String?,
        val fingerprint: String?,
    )

    /** diff 包 minime 命名空间解析结果 */
    data class DiffFileMeta(
        val format: String?,          // "minime-diff"；null = 形态 B（纯 DTCG，无 minime 段）
        val diffVersion: Int,
        val engineVersion: String?,
        val basePackId: String?,
        val basePackVersion: String?,
        val baseFingerprint: String?,
        val exportedAt: String?,
        val declaredCount: Int?,      // minime.count 冗余自检字段
    )

    /** 反序列化产物：扁平覆写表（与 ThemeManager.applyOverrides 同型）+ 文件元信息 */
    data class ParsedDiff(
        val overlays: Map<String, TokenValue>,
        val meta: DiffFileMeta,
        val hasMinimeNamespace: Boolean,
    )

    /** diff 包格式错误（code 沿用引擎 §5.2 码表风格 + DIFF_VERSION_UNSUPPORTED） */
    class DiffFormatException(val code: String, val detail: String) : Exception("$code: $detail")

    // ---------- 序列化 ----------

    /**
     * 覆写表 → diff 包 JSON。
     *
     * @param overlays     扁平路径 → 值（路径须在 [TokenVocabulary] 标准词汇表内，越界抛错）
     * @param base         基础包元信息（packId / version / 指纹，null 指纹则不含该字段）
     * @param exportedAt   ISO 8601 导出时间（调用方注入，保纯函数可测）
     */
    fun serialize(
        overlays: Map<String, TokenValue>,
        base: DiffBaseMeta,
        exportedAt: String,
    ): String {
        // 路径白名单 + 声明类型（防御性双保险：ThemeManager 入口已校验，此处独立成纯函数再查一次）
        val types = LinkedHashMap<String, TokenType>(overlays.size)
        for (path in overlays.keys) {
            val type = TokenVocabulary.ALL[path]
                ?: throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                    "覆写路径不在标准词汇表: $path（共 ${overlays.size} 条，标准词汇表 ${TokenVocabulary.SIZE} 条）",
                )
            types[path] = type
        }

        val root = JSONObject()
        root.put("\$schema", SCHEMA_URL)
        root.put("\$description", "MiniMe 主题覆写 diff 包：仅含相对基础包的覆写令牌（DTCG 2025.10 对齐）")

        // minime 专有命名空间（DTCG §3.2 元数据扩展精神）
        val minime = JSONObject()
        minime.put("format", FORMAT)
        minime.put("diffVersion", DIFF_VERSION)
        base.fingerprint?.let { minime.put("baseFingerprint", it) }
        minime.put("basePackId", base.packId)
        base.packVersion?.let { minime.put("basePackVersion", it) }
        minime.put("exportedAt", exportedAt)
        minime.put("count", overlays.size)
        root.put("minime", minime)

        root.put("\$tokens", buildTree(overlays, types))
        return root.toString(2)
    }

    /** 扁平路径 → DTCG 组嵌套树；组内所有后代同型时声明组级 $type（DTCG §6.7.3 类型继承） */
    private fun buildTree(
        overlays: Map<String, TokenValue>,
        types: Map<String, TokenType>,
    ): JSONObject {
        class Node {
            val children = LinkedHashMap<String, Node>()
            var leafPath: String? = null
        }
        val rootNode = Node()
        for (path in types.keys) {
            val segments = path.split(".")
            var node = rootNode
            for ((i, seg) in segments.withIndex()) {
                node = node.children.getOrPut(seg) { Node() }
                if (i == segments.lastIndex) node.leafPath = path
            }
        }

        fun leafTypesOf(node: Node): Set<TokenType> {
            node.leafPath?.let { return setOf(types.getValue(it)) }
            return node.children.values.flatMapTo(LinkedHashSet()) { leafTypesOf(it) }
        }

        fun render(node: Node): JSONObject {
            val obj = JSONObject()
            // 组级 $type：仅当本组全部后代叶同型（混合组如 typography（typography 复合体 + fontFamily 叶）不声明，叶级自带）
            if (node.children.isNotEmpty()) {
                leafTypesOf(node).singleOrNull()?.let { obj.put("\$type", it.rawName) }
            }
            for ((key, child) in node.children) {
                val leafPath = child.leafPath
                if (leafPath != null) {
                    obj.put(key, renderToken(overlays.getValue(leafPath), types.getValue(leafPath)))
                } else {
                    obj.put(key, render(child))
                }
            }
            return obj
        }
        return render(rootNode)
    }

    // ---------- 叶令牌渲染 ----------

    private fun renderToken(value: TokenValue, type: TokenType): JSONObject {
        val obj = JSONObject()
        obj.put("\$type", type.rawName)
        when (value) {
            is ColorValue -> obj.put("\$value", renderColor(value))
            is DimensionValue -> obj.put("\$value", renderDimension(value))
            is DurationValue -> obj.put("\$value", JSONObject().put("value", value.ms.toDouble()).put("unit", "ms"))
            is NumberValue -> obj.put("\$value", value.value.toDouble())
            is BooleanValue -> {
                obj.put("\$value", value.value)
                obj.put("\$description", "MiniMe 扩展类型：DTCG 2025.10 未定义 boolean（§8.8 additional types 非规范）")
            }
            is StringValue -> obj.put("\$value", value.value)
            is CubicBezierValue -> obj.put(
                "\$value",
                JSONArray(listOf(value.x1.toDouble(), value.y1.toDouble(), value.x2.toDouble(), value.y2.toDouble())),
            )
            is TypographyValue -> obj.put("\$value", renderTypography(value))
            is SpringValue -> obj.put("\$value", renderSpring(value))
            is GlassValue -> {
                obj.put("\$value", renderGlass(value))
                obj.put("\$description", "MiniMe 扩展复合体：DTCG 2025.10 未定义 glass 类型")
            }
            is ShadowValue -> obj.put("\$value", renderShadow(value))
        }
        return obj
    }

    /** DTCG 颜色对象形态（§8.1 + Color module）：components 0-1 + alpha + hex（hex 为保真源，D5） */
    private fun renderColor(value: ColorValue): JSONObject = JSONObject()
        .put("colorSpace", "srgb")
        .put("components", JSONArray(listOf(value.red / 255.0, value.green / 255.0, value.blue / 255.0)))
        .put("alpha", value.alpha.toDouble())
        .put("hex", value.hex)

    /** DTCG dimension 对象形态（§8.2）：dp→px 同值；sp→rem(÷16)；px→px 原样（hairline 例外） */
    private fun renderDimension(value: DimensionValue): JSONObject = when (value.unit) {
        "dp" -> JSONObject().put("value", value.value.toDouble()).put("unit", "px")
        "sp" -> JSONObject().put("value", (value.value / 16f).toDouble()).put("unit", "rem")
        "px" -> JSONObject().put("value", value.value.toDouble()).put("unit", "px")
        else -> error("引擎内部错误: 未预期单位 ${value.unit}")
    }

    /**
     * DTCG typography 复合体（§9.8）：fontFamily / fontSize(dimension) / fontWeight(number) /
     * letterSpacing(dimension) / lineHeight(**number 乘数**)。可空字段省略（导入侧缺字段 = null，往返保真）。
     */
    private fun renderTypography(value: TypographyValue): JSONObject {
        val obj = JSONObject()
        value.fontFamily?.let { obj.put("fontFamily", it) }
        obj.put("fontSize", renderDimension(value.fontSize))
        value.fontWeight?.let { obj.put("fontWeight", it.toIntOrNull() ?: it) }
        value.letterSpacing?.let { obj.put("letterSpacing", renderDimension(it)) }
        // lineHeight 乘数 = lineHeight_sp ÷ fontSize_sp（同单位相除；Double 全精度，4/3 类分数微误差见类注释偏离 2）
        value.lineHeight?.let {
            obj.put("lineHeight", it.value.toDouble() / value.fontSize.value.toDouble())
        }
        return obj
    }

    /** MiniMe 扩展 spring：三条 number（DTCG 无 spring 类型；不与 cubicBezier 混用） */
    private fun renderSpring(value: SpringValue): JSONObject = JSONObject()
        .put("stiffness", value.stiffness.toDouble())
        .put("damping", value.damping.toDouble())
        .put("initialVelocity", value.initialVelocity.toDouble())

    /** MiniMe 扩展 glass 复合体：blur(dimension) / saturation / tint(color) / tintAlpha / hairline(boolean) */
    private fun renderGlass(value: GlassValue): JSONObject = JSONObject()
        .put("blur", renderDimension(value.blur))
        .put("saturation", value.saturation.toDouble())
        .put("tint", renderColor(value.tint))
        .put("tintAlpha", value.tintAlpha.toDouble())
        .put("hairline", value.hairline)

    /** DTCG shadow 复合体（词汇表暂无 shadow 路径，防御性支持） */
    private fun renderShadow(value: ShadowValue): JSONObject {
        val obj = JSONObject().put("color", renderColor(value.color))
        value.blur?.let { obj.put("blur", renderDimension(it)) }
        value.offsetX?.let { obj.put("offsetX", renderDimension(it)) }
        value.offsetY?.let { obj.put("offsetY", renderDimension(it)) }
        return obj
    }

    // ---------- 反序列化 ----------

    /**
     * diff 包 JSON → 覆写表 + 元信息。
     *
     * 三形态（设计 §6.4）：
     * - A. minime diff 包（`minime.format == "minime-diff"`）：校验 diffVersion / count；
     * - B. 纯 DTCG 文件（`$tokens`/`$value` 树、无 minime 段）：全量提取叶令牌为覆写表；
     * - C. 坏包：抛 [DiffFormatException]（调用方零改态）。
     *
     * 宽容项：令牌树根可以是 `$tokens`（导出形态）或文件根本身（外部 DTCG 惯例）。
     * 拒绝项：别名引用（`{path}` / `$ref`）——diff 语义是字面值替换，不注入引用结构（D6）。
     */
    fun deserialize(json: String): ParsedDiff {
        val root: JSONObject = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw DiffFormatException(CompileErrorCode.PACK_CORRUPT.name, "diff 包不是合法 JSON: ${e.message}")
        }

        val minimeObj = root.optJSONObject("minime")
        val meta = if (minimeObj != null) parseMinimeNamespace(minimeObj) else DiffFileMeta(
            format = null, diffVersion = 0, engineVersion = null,
            basePackId = null, basePackVersion = null, baseFingerprint = null,
            exportedAt = null, declaredCount = null,
        )
        if (meta.format != null && meta.format != FORMAT) {
            throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name, "未知 minime.format: \"$meta.format\"（期望 \"$FORMAT\"）")
        }
        if (meta.diffVersion > DIFF_VERSION) {
            throw DiffFormatException(
                "DIFF_VERSION_UNSUPPORTED",
                "diff 包结构版本 diffVersion=${meta.diffVersion} 高于本引擎支持的 $DIFF_VERSION",
            )
        }

        // 令牌树根：$tokens 优先，否则根本身（跳过 $ 前缀键）
        val treeObj = root.optJSONObject("\$tokens") ?: root

        val overlays = LinkedHashMap<String, TokenValue>()
        walkGroup(treeObj, prefix = "", inherited = null, into = overlays, rootAsTree = treeObj === root)

        if (overlays.isEmpty()) {
            throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name, "diff 包未包含任何令牌（\$tokens 树为空）")
        }
        meta.declaredCount?.let { declared ->
            if (declared != overlays.size) {
                throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                    "minime.count=$declared 与实际令牌数 ${overlays.size} 不符",
                )
            }
        }
        return ParsedDiff(overlays = overlays, meta = meta, hasMinimeNamespace = minimeObj != null)
    }

    private fun parseMinimeNamespace(obj: JSONObject): DiffFileMeta {
        val version = obj.opt("diffVersion")
        val diffVersion = when (version) {
            is Number -> version.toInt()
            else -> 0
        }
        return DiffFileMeta(
            format = obj.optString("format").takeIf { it.isNotEmpty() },
            diffVersion = diffVersion,
            engineVersion = obj.optString("engineVersion").takeIf { it.isNotEmpty() },
            basePackId = obj.optString("basePackId").takeIf { it.isNotEmpty() },
            basePackVersion = obj.optString("basePackVersion").takeIf { it.isNotEmpty() },
            baseFingerprint = obj.optString("baseFingerprint").takeIf { it.isNotEmpty() },
            exportedAt = obj.optString("exportedAt").takeIf { it.isNotEmpty() },
            declaredCount = (obj.opt("count") as? Number)?.toInt(),
        )
    }

    /** 组递归（同 DtcgParser.parseGroup 的判定规则：有 $value 是令牌，无则是组） */
    private fun walkGroup(
        obj: JSONObject,
        prefix: String,
        inherited: TokenType?,
        into: MutableMap<String, TokenValue>,
        rootAsTree: Boolean = false,
    ) {
        val groupType = obj.optString("\$type").takeIf { it.isNotEmpty() }?.let { raw ->
            TokenType.fromRaw(raw)
                ?: throw DiffFormatException("TYPE_UNKNOWN", "未知组级 \$type: \"$raw\"（${prefix.ifEmpty { "根" }}）")
        } ?: inherited

        for (key in obj.keys()) {
            if (key.startsWith("\$")) continue // $type/$description/$schema 等
            if (rootAsTree && key == "minime") continue // 形态 B 根级遍历：跳过 minime 命名空间段
            val value = obj.get(key)
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            if (value is JSONObject) {
                if (value.has("\$value")) {
                    into[path] = parseDiffToken(path, value, groupType)
                } else {
                    walkGroup(value, path, groupType, into)
                }
            } else {
                throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                    "令牌 \"$path\" 必须是含 \$value 的对象（当前为 ${value.javaClass.simpleName}）",
                )
            }
        }
    }

    private fun parseDiffToken(path: String, obj: JSONObject, inherited: TokenType?): TokenValue {
        val type = when (val raw = obj.optString("\$type").takeIf { it.isNotEmpty() }) {
            null -> inherited
                ?: throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name, "令牌 \"$path\" 缺少 \$type（所在组也未声明）")
            else -> TokenType.fromRaw(raw)
                ?: throw DiffFormatException("TYPE_UNKNOWN", "未知令牌类型: \"$raw\"（$path）")
        }
        val raw = obj.get("\$value")

        // 别名拒绝（D6：diff 语义是字面值替换）
        if (raw is String && raw.trimStart().startsWith("{")) {
            throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                "diff 包不支持别名引用 \"$raw\"（$path）：覆写语义是字面值替换",
            )
        }

        if (!type.isComposite) return parseDiffScalar(path, type, raw)
        return parseDiffComposite(path, type, raw)
    }

    /** 标量导入（含 DTCG → MiniMe 单位映射：px→dp、rem→sp、ms/s→ms） */
    private fun parseDiffScalar(path: String, type: TokenType, value: Any): TokenValue = when (type) {
        TokenType.COLOR -> when (value) {
            is String -> try {
                ColorValue.fromHex(value)
            } catch (e: IllegalArgumentException) {
                throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": ${e.message}")
            }
            is JSONObject -> parseDiffColorObject(value, path)
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.DIMENSION -> when (value) {
            is String -> parseDiffDimensionString(value, path)
            is JSONObject -> {
                val unit = value.optString("unit").takeIf { it.isNotEmpty() }
                val v = value.opt("value")
                if (unit == null || v !is Number) throw diffMismatch(type, value, path)
                val (mappedUnit, multiplier) = mapImportUnit(path, unit)
                DimensionValue(v.toFloat() * multiplier, mappedUnit)
            }
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.DURATION -> when (value) {
            is String -> {
                val m = Regex("^(\\d+(?:\\.\\d+)?)(ms|s)$").matchEntire(value.trim())
                    ?: throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": 非法 duration \"$value\"（期望 \"250ms\" 或 \"0.25s\"）")
                DurationValue(if (m.groupValues[2] == "s") m.groupValues[1].toFloat() * 1000f else m.groupValues[1].toFloat())
            }
            is JSONObject -> {
                val unit = value.optString("unit").takeIf { it.isNotEmpty() } ?: "ms"
                val v = value.opt("value") as? Number ?: throw diffMismatch(type, value, path)
                DurationValue(if (unit == "s") v.toFloat() * 1000f else v.toFloat())
            }
            is Number -> DurationValue(value.toFloat())
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.NUMBER -> when (value) {
            is Number -> NumberValue(value.toFloat())
            is String -> value.toFloatOrNull()?.let { NumberValue(it) }
                ?: throw diffMismatch(type, value, path)
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.BOOLEAN -> when (value) {
            is Boolean -> BooleanValue(value)
            is String -> when (value.trim().lowercase()) {
                "true" -> BooleanValue(true)
                "false" -> BooleanValue(false)
                else -> throw diffMismatch(type, value, path)
            }
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.STRING, TokenType.FONT_FAMILY -> when (value) {
            is String -> StringValue(value)
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.FONT_WEIGHT -> when (value) {
            is String -> if (value.matches(FONT_WEIGHT_RE)) StringValue(value) else throw diffMismatch(type, value, path)
            is Number -> StringValue(value.toInt().toString())
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.CUBIC_BEZIER -> when (value) {
            is JSONArray -> {
                if (value.length() != 4 || (0 until 4).any { value.opt(it) !is Number }) throw diffMismatch(type, value, path)
                CubicBezierValue(
                    value.getDouble(0).toFloat(), value.getDouble(1).toFloat(),
                    value.getDouble(2).toFloat(), value.getDouble(3).toFloat(),
                )
            }
            else -> throw diffMismatch(type, value, path)
        }

        TokenType.TYPOGRAPHY, TokenType.SHADOW, TokenType.SPRING, TokenType.GLASS ->
            error("引擎内部错误: 复合型 $type 不应进入标量导入（$path）")
    }

    /** DTCG unit → MiniMe（单位, 数值倍数）（D4 规范等价 + hairline 例外） */
    private fun mapImportUnit(path: String, dtcgUnit: String): Pair<String, Float> = when (dtcgUnit) {
        "px" -> (if (path in PX_NATIVE_PATHS) "px" else "dp") to 1f  // §8.2.1 px≡dp；hairline 是原生 px
        "rem" -> "sp" to 16f                                          // §8.2.1 1rem≡16sp（值 ×16）
        "dp" -> "dp" to 1f
        "sp" -> "sp" to 1f
        else -> throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": 不支持的 dimension 单位 \"$dtcgUnit\"（DTCG 仅 px/rem）")
    }

    private fun parseDiffDimensionString(raw: String, path: String): DimensionValue {
        val m = Regex("^(\\d+(?:\\.\\d+)?)(dp|sp|px)$").matchEntire(raw.trim())
            ?: throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": 非法 dimension \"$raw\"")
        return DimensionValue(m.groupValues[1].toFloat(), m.groupValues[2])
    }

    private fun parseDiffColorObject(obj: JSONObject, path: String): ColorValue {
        // hex 优先（保真源，D5）；components 兜底（DTCG 消费方产物可能只有 components）
        val hex = obj.optString("hex").takeIf { it.isNotEmpty() }
        if (hex != null) {
            return try {
                ColorValue.fromHex(hex)
            } catch (e: IllegalArgumentException) {
                throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": ${e.message}")
            }
        }
        val colorSpace = obj.optString("colorSpace").takeIf { it.isNotEmpty() }
            ?: throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": 对象形态颜色缺少 colorSpace/hex")
        val comps = obj.optJSONArray("components")
            ?: throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": 对象形态颜色缺少 components")
        val list = (0 until comps.length()).map { comps.opt(it) }
        if (list.any { it !is Number }) {
            throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": components 必须为数值数组")
        }
        return try {
            ColorValue.fromObject(colorSpace, list.map { (it as Number).toDouble() })
        } catch (e: IllegalArgumentException) {
            throw DiffFormatException("TYPE_MISMATCH", "令牌 \"$path\": ${e.message}")
        }
    }

    /** 复合体导入：按 CompositeSchemas 拆字段（typography/glass/spring/shadow） */
    private fun parseDiffComposite(path: String, type: TokenType, value: Any): TokenValue {
        val schema = CompositeSchemas.forType(type)
            ?: error("引擎内部错误: 复合型 $type 缺少 schema")
        if (value !is JSONObject) {
            throw DiffFormatException("TYPE_MISMATCH", "复合型 \"$path\" 的 \$value 必须是对象（当前为 ${value.javaClass.simpleName}）")
        }
        // 未知字段拒绝（同 DtcgParser 纪律：捕获拼写错误）
        for (key in value.keys()) {
            if (key.startsWith("\$")) continue
            if (key !in schema) {
                throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                    "复合型 \"$path\" 含未声明字段: \"$key\"（$type schema: ${schema.keys}）",
                )
            }
        }
        // 必填校验
        val required = CompositeSchemas.REQUIRED_FIELDS[type] ?: emptySet()
        for (field in required) {
            if (!value.has(field)) {
                throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name, "复合型 \"$path\" 缺少必填字段 \"$field\"")
            }
        }

        val fields = LinkedHashMap<String, TokenValue>(schema.size)
        for ((field, fieldType) in schema) {
            if (!value.has(field)) continue // 可缺字段（typography 可空字段 = null，DTCG §9.8）
            val fv = value.get(field)
            if (fv is String && fv.trimStart().startsWith("{")) {
                throw DiffFormatException(CompileErrorCode.SCHEMA_INVALID.name,
                    "diff 包不支持字段别名 \"$fv\"（$path.$field）",
                )
            }
            // DTCG typography lineHeight 是 fontSize 乘数（number 形态，§9.8）；引擎内部 schema
            // 声明为 dimension（sp）——导入侧特例放行 number，组装时乘 fontSize 还原（见下方 TYPOGRAPHY 分支）
            if (type == TokenType.TYPOGRAPHY && field == "lineHeight" && fv is Number) {
                fields[field] = NumberValue(fv.toFloat())
                continue
            }
            fields[field] = parseDiffScalar("$path.$field", fieldType, fv)
        }

        return when (type) {
            TokenType.TYPOGRAPHY -> {
                val fontSize = fields["fontSize"] as DimensionValue
                val lineHeight = fields["lineHeight"]?.let { lh ->
                    // DTCG typography lineHeight 是 fontSize 乘数（§9.8）：导入时 × fontSize 还原 sp
                    // 宽容：若文件给的是 dimension/字符串（某些工具未按乘数），直接用
                    when (lh) {
                        is NumberValue -> DimensionValue(fontSize.value * lh.value, "sp")
                        else -> lh as DimensionValue
                    }
                }
                TypographyValue(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = (fields["fontWeight"] as? StringValue)?.value,
                    fontFamily = (fields["fontFamily"] as? StringValue)?.value,
                    letterSpacing = fields["letterSpacing"] as? DimensionValue,
                )
            }
            TokenType.SPRING -> SpringValue(
                stiffness = (fields["stiffness"] as NumberValue).value,
                damping = (fields["damping"] as NumberValue).value,
                initialVelocity = (fields["initialVelocity"] as NumberValue).value,
            )
            TokenType.GLASS -> GlassValue(
                blur = fields["blur"] as DimensionValue,
                saturation = (fields["saturation"] as NumberValue).value,
                tint = fields["tint"] as ColorValue,
                tintAlpha = (fields["tintAlpha"] as NumberValue).value,
                hairline = (fields["hairline"] as BooleanValue).value,
            )
            TokenType.SHADOW -> ShadowValue(
                color = fields["color"] as ColorValue,
                blur = fields["blur"] as? DimensionValue,
                offsetX = fields["offsetX"] as? DimensionValue,
                offsetY = fields["offsetY"] as? DimensionValue,
            )
            else -> error("引擎内部错误: $type 不是复合型")
        }
    }

    private fun diffMismatch(type: TokenType, value: Any, path: String) = DiffFormatException(
        "TYPE_MISMATCH",
        "令牌 \"$path\" 期望 $type，实际值为 ${describeDiffValue(value)}",
    )

    private fun describeDiffValue(value: Any): String = when (value) {
        is JSONObject -> "对象"
        is JSONArray -> "数组"
        else -> "\"$value\"（${value.javaClass.simpleName}）"
    }

    // ---------- 基础包指纹 ----------

    /**
     * 基础包指纹（设计 §6.3）：sha256(manifest.name|version|全部 set 令牌 path=规范化值)。
     * 语义指纹（非字节指纹）——同一包的不同 JSON 序列化（空格/键序）得到相同指纹。
     */
    fun computeFingerprint(pack: ThemePack): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(pack.manifest.name.toByteArray())
        md.update(0x1F)
        md.update(pack.manifest.version.toByteArray())
        for (set in pack.sets.values) {
            for ((path, token) in set.tokens) {
                md.update(path.toByteArray()); md.update('='.code.toByte())
                md.update(normalizeTokenForFingerprint(token).toByteArray()); md.update(0x1E)
            }
        }
        return "sha256:" + md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeTokenForFingerprint(token: RawToken): String = buildString {
        append(token.path).append(':').append(token.declaredType.rawName).append('=')
        when {
            token.literal != null -> append(token.literal.toString())
            token.alias != null -> append('{').append(token.alias).append('}')
            token.fields != null -> token.fields.entries
                .sortedBy { it.key }
                .joinTo(this, ",") { (k, f) ->
                    "$k=${f.literal?.toString() ?: f.alias?.let { a -> "{$a}" } ?: "?"}"
                }
        }
    }

    private val SCHEMA_URL = "https://tr.designtokens.org/format/"
    private val FONT_WEIGHT_RE = Regex("^([1-9]\\d{0,3}|normal|bold|lighter|bolder)$")
}
