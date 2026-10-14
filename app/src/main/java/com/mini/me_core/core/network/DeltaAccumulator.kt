package com.mini.me_core.core.network

/**
 * 流式增量语义归一化累积器（预防「字段语义假设」类 bug）。
 *
 * 背景：同一个流式字段在不同 provider / 兼容网关下可能是三种语义——
 *  - [Semantic.INCREMENTAL] 增量：每个 chunk 是新增片段（OpenAI `delta.content` / Anthropic `delta.text`）；
 *  - [Semantic.FULL_SNAPSHOT] 全量快照：每个 chunk 是完整内容（Gemini `thought` / `functionCall`）；
 *  - [Semantic.AUTO_DETECT] 自动判别：兼容网关常全量重发完整内容（DeepSeek `reasoning_content`）。
 * 若把「全量重发」当「增量」累积，会出现内容成倍放大（如思考过程重复几百行 base64）。
 *
 * 本类在累积层做语义归一化 + 护栏（长度上限 / 重复去重 / 裸 base64 折叠），
 * 使下游（workflow 累积、UI 展示、落库）不再依赖对上游语义的假设。
 */
class DeltaAccumulator(
    private val semantic: Semantic = Semantic.AUTO_DETECT,
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val foldBase64: Boolean = true,
    private val base64MinLength: Int = DEFAULT_BASE64_MIN_LENGTH
) {

    enum class Semantic {
        /** 每个 chunk 是新增片段，直接追加。 */
        INCREMENTAL,
        /** 每个 chunk 是完整内容，整体替换。 */
        FULL_SNAPSHOT,
        /** 自动判别：与上次完整快照比对，识别「全量重发/回退重复」并去重，其余按增量累积。 */
        AUTO_DETECT
    }

    /** 归一化结果：Append 为真增量（直接累积），Duplicate 为全量重发/重复（已去重，仅统计）。 */
    sealed class NormalizedDelta {
        data class Append(val text: String) : NormalizedDelta()
        data class Duplicate(val count: Int) : NormalizedDelta()

        /** 本次实际追加的字符数（Duplicate 表示被去重、未产生追加）。供流式 emit 节流统计。 */
        fun appendedLength(): Int = when (this) {
            is Append -> text.length
            is Duplicate -> 0
        }
    }

    private val buffer = StringBuilder()
    /**
     * 全量文本的缓存：避免同一 chunk 内多处读取重复 `toString()`（每次都是 O(n) 拷贝）。
     * 内容变更（append / 截断 / reset）时置空失效。
     */
    private var cachedText: String? = null
    private var duplicateCountInternal = 0
    private var truncatedInternal = false
    /** 归一化前累计接收的原始字符数（观测埋点：与 [text] 长度对比可得「放大/折叠比率」）。 */
    private var rawCharsInternal = 0L

    /**
     * 归一化后的完整累积内容（已截断 / 折叠）。
     *
     * 首次读取或内容变更后才做一次 `toString()`，结果缓存给同一 chunk 内的后续读取复用。
     */
    val text: String
        get() = cachedText ?: buffer.toString().also { cachedText = it }

    /** 是否因超过 [maxChars] 被截断。 */
    val isTruncated: Boolean get() = truncatedInternal

    /** 累计识别到的「全量重发/重复」次数（AUTO_DETECT 下才有意义，供观测埋点）。 */
    val duplicateCount: Int get() = duplicateCountInternal

    /** 归一化前累计接收的原始字符数（供观测埋点计算放大/折叠比率）。 */
    val rawCharsReceived: Long get() = rawCharsInternal

    /**
     * 接收一个流式 chunk，返回归一化结果。
     * 空 chunk 不产生累积（返回空 [NormalizedDelta.Append]）。
     */
    fun accept(chunk: String): NormalizedDelta {
        if (chunk.isEmpty()) return NormalizedDelta.Append("")
        rawCharsInternal += chunk.length
        // AUTO_DETECT：与「当前累积全文」比对，全量重发（相同）或回退到已有前缀 → 去重。
        //
        // 性能要点（长对话内存治理）：这里一律用 CharSequence 零分配比较，不再维护
        // 「上次完整快照」的字符串副本。旧实现每 chunk 都执行 buffer.toString()，
        // 一次 20 万字符的回复会产生约 5 万次 O(n) 全量拷贝（累计数十 GB 分配），
        // 是长输出被 LMKD 静默杀进程的主因之一。改用 contentEquals / startsWith 后
        // 每个 chunk 的开销降为 O(chunk 长度)，整体从 O(n²) 变为 O(n)。
        if (semantic == Semantic.AUTO_DETECT && buffer.isNotEmpty()) {
            val duplicated = chunk.contentEquals(buffer) ||
                (chunk.length <= buffer.length && startsWith(buffer, chunk))
            if (duplicated) {
                duplicateCountInternal++
                return NormalizedDelta.Duplicate(duplicateCountInternal)
            }
        }
        val toAppend = when (semantic) {
            Semantic.INCREMENTAL -> chunk
            Semantic.FULL_SNAPSHOT -> {
                buffer.setLength(0)
                chunk
            }
            Semantic.AUTO_DETECT -> {
                // 新 chunk 扩展了当前累积 → 真增量，只取新增尾巴；否则为新段，整体追加。
                if (buffer.isNotEmpty() && chunk.length > buffer.length && startsWith(chunk, buffer)) {
                    chunk.substring(buffer.length)
                } else {
                    chunk
                }
            }
        }
        buffer.append(if (foldBase64) foldBase64Segments(toAppend) else toAppend)
        cachedText = null
        if (buffer.length > maxChars) {
            buffer.setLength(maxChars)
            truncatedInternal = true
        }
        return NormalizedDelta.Append(toAppend)
    }

    /** 清空累积与统计（网络重试时调用，语义同 `StringBuilder.setLength(0)`）。 */
    fun reset() {
        buffer.setLength(0)
        cachedText = null
        duplicateCountInternal = 0
        truncatedInternal = false
        rawCharsInternal = 0
    }

    /**
     * 零分配的 CharSequence 前缀判断。
     *
     * String 自带的 startsWith 只接受 String 参数，若为了比较而 `toString()` 整个累积区，
     * 就等于每个 chunk 拷贝一次全文；这里逐字符比较，只分配调用栈。
     */
    private fun startsWith(source: CharSequence, prefix: CharSequence): Boolean {
        if (prefix.length > source.length) return false
        for (i in 0 until prefix.length) {
            if (source[i] != prefix[i]) return false
        }
        return true
    }

    private fun foldBase64Segments(input: String): String {
        if (base64MinLength <= 0 || input.length < base64MinLength) return input
        return base64SegmentRegex.replace(input, BASE64_OMITTED)
    }

    private val base64SegmentRegex by lazy {
        Regex("[A-Za-z0-9+/]{$base64MinLength,}={0,2}")
    }

    private companion object {
        /** 与落库层 [com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase.MAX_CONTENT_CHARS] 对齐。 */
        const val DEFAULT_MAX_CHARS = 200_000
        const val DEFAULT_BASE64_MIN_LENGTH = 256
        /** 裸 base64 占位符（文案与 MessagePersistenceUseCase.IMAGE_OMITTED_MARKER 保持一致，用户理解统一）。 */
        const val BASE64_OMITTED = "[图片已省略：内嵌图片数据过大]"
    }
}
