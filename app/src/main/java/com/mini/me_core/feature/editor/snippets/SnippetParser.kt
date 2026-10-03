package com.mini.me_core.feature.editor.snippets

import com.mini.me_core.feature.editor.snippets.model.ParsedSnippet
import com.mini.me_core.feature.editor.snippets.model.TabStop
import com.mini.me_core.feature.editor.snippets.model.VariableRef

/**
 * 片段正文解析器（纯 Kotlin，可在 JVM 单测中运行）。
 *
 * 解析 VS Code 风格占位符语法：
 *  - `${1:默认值}` 编号占位符
 *  - `${1}` 编号占位符（无默认值）
 *  - `${0}` 最终光标位置
 *  - `${1|选项1,选项2|}` 多选占位符
 *  - `${TM_FILENAME}` / `$TM_FILENAME` 变量引用
 *
 * 解析结果同时给出：保留占位符语法的 [ParsedSnippet.rawBody]（交给 sora-editor 展开），
 * 以及用默认值填充后的 [ParsedSnippet.expandedText]、结构化的 [TabStop]/[VariableRef] 列表
 * （用于校验、预览与单元测试）。
 *
 * 注意：本解析器只做结构化提取与语法校验，不负责编辑器内的位置计算——
 * 那部分由 sora-editor 的 SnippetController 在展开时完成。
 */
object SnippetParser {

    /** 解析失败异常，message 面向用户可读。 */
    class SnippetParseException(message: String) : Exception(message)

    private val VAR_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * 解析片段正文（行列表）。
     * @throws SnippetParseException 占位符语法非法（括号不匹配等）
     */
    fun parseBody(lines: List<String>): ParsedSnippet {
        if (lines.isEmpty()) throw SnippetParseException("片段正文为空")
        val raw = lines.joinToString("\n")
        return parseRaw(raw)
    }

    /** 解析单行拼接后的正文。 */
    fun parseRaw(raw: String): ParsedSnippet {
        val out = StringBuilder(raw.length)
        val tabStops = mutableListOf<TabStop>()
        val variables = mutableListOf<VariableRef>()
        parseInto(raw, 0, raw.length, out, tabStops, variables, depth = 0)
        return ParsedSnippet(
            rawBody = raw,
            expandedText = out.toString(),
            tabStops = tabStops,
            variables = variables,
        )
    }

    /**
     * 递归解析 [start, end) 区间。
     * @param depth 当前嵌套深度（占位符默认值内可再含占位符）
     */
    private fun parseInto(
        s: String,
        start: Int,
        end: Int,
        out: StringBuilder,
        tabStops: MutableList<TabStop>,
        variables: MutableList<VariableRef>,
        depth: Int,
    ) {
        var i = start
        while (i < end) {
            val c = s[i]
            // 转义字符：\$ \} 等，反斜杠后的字符原样输出
            if (c == '\\' && i + 1 < end) {
                out.append(s[i + 1])
                i += 2
                continue
            }
            if (c == '$' && i + 1 < end && s[i + 1] == '{') {
                val close = matchBrace(s, i + 2, end)
                val innerStart = i + 2
                val inner = s.substring(innerStart, close)
                parseInner(inner, out, tabStops, variables, depth)
                i = close + 1
                continue
            }
            if (c == '$') {
                // $VAR_NAME 形式的变量
                val m = VAR_NAME.matchAt(s, i + 1)
                if (m != null) {
                    val vStart = out.length
                    // 变量在预览文本中暂为空（展开时由变量解析器填入）
                    val vEnd = out.length
                    variables.add(VariableRef(name = m.value, start = vStart, end = vEnd))
                    i = i + 1 + m.value.length
                    continue
                }
            }
            out.append(c)
            i++
        }
    }

    /** 找到 `${` 之后与第一个 `{` 配对的 `}` 位置（按嵌套深度计数）。 */
    private fun matchBrace(s: String, from: Int, end: Int): Int {
        var depth = 1
        var i = from
        while (i < end) {
            when (s[i]) {
                '\\' -> i++ // 转义，跳过下一个字符
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        throw SnippetParseException("占位符括号未闭合：缺少 '}'")
    }

    /**
     * 解析 `${...}` 内部内容。
     * 形如：`1`、`1:默认值`、`1|a,b|`、`VAR`、`VAR:默认值`。
     */
    private fun parseInner(
        inner: String,
        out: StringBuilder,
        tabStops: MutableList<TabStop>,
        variables: MutableList<VariableRef>,
        depth: Int,
    ) {
        // 多选占位符：1|a,b,c| —— 注意 | 只在紧邻编号之后出现
        if (inner.contains('|') && inner.takeWhile { it != ':' && it != '|' }.all { it.isDigit() }) {
            val bar = inner.indexOf('|')
            val index = inner.substring(0, bar).toIntOrNull()
            if (index != null) {
                // 结尾应有匹配的 |
                val rest = inner.substring(bar + 1)
                if (rest.endsWith('|')) {
                    val choicesBody = rest.substring(0, rest.length - 1)
                    val choices = choicesBody.split(',').map { it.trim() }
                    val tStart = out.length
                    out.append(choices.firstOrNull().orEmpty())
                    tabStops.add(
                        TabStop(
                            index = index,
                            start = tStart,
                            end = out.length,
                            defaultValue = choices.firstOrNull().orEmpty(),
                            choices = choices,
                        ),
                    )
                    return
                }
            }
        }

        val colon = inner.indexOf(':')
        val head = if (colon >= 0) inner.substring(0, colon) else inner
        val tail = if (colon >= 0) inner.substring(colon + 1) else ""

        // 纯数字 head → 编号占位符
        val index = head.toIntOrNull()
        if (index != null) {
            val tStart = out.length
            if (tail.isNotEmpty()) {
                // 默认值可能再含嵌套占位符，递归解析
                parseInto(tail, 0, tail.length, out, tabStops, variables, depth + 1)
            }
            tabStops.add(
                TabStop(
                    index = index,
                    start = tStart,
                    end = out.length,
                    defaultValue = out.substring(tStart),
                ),
            )
            return
        }

        // 否则视为变量：${VAR} 或 ${VAR:默认值}
        if (VAR_VALUE.matches(head)) {
            val vStart = out.length
            if (tail.isNotEmpty()) {
                // 变量带默认值，递归解析默认值
                parseInto(tail, 0, tail.length, out, tabStops, variables, depth + 1)
            }
            variables.add(VariableRef(name = head, start = vStart, end = out.length))
            return
        }

        // 无法识别的内容：原样输出，避免吞掉文本
        out.append(inner)
    }

    private val VAR_VALUE = Regex("[A-Z_][A-Z0-9_]*")
}
