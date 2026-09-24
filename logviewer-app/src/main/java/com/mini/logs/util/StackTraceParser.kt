package com.mini.logs.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * 堆栈语法高亮解析器。
 *
 * 规则：
 * - 异常类型行（第一行，含 Exception/Error/Throwable）：error 色 Bold
 * - `at ...` 行：类名 brandPrimary 色，方法名 textPrimary Bold，行号 textTertiary
 * - `Caused by:`：error 色 Bold
 * - `... N more`：textTertiary
 */
object StackTraceParser {

    private val EXCEPTION_LINE = Regex(
        """^([a-zA-Z_][a-zA-Z0-9_.]*(?:Exception|Error|Throwable))(?::\s*(.*))?$"""
    )

    // at com.mini.app.Foo.bar(Foo.kt:42)
    private val AT_LINE = Regex(
        """^(\s*at\s+)([A-Za-z_][A-Za-z0-9_.$]*)\.([A-Za-z_][A-Za-z0-9_$]*)\(([^)]*)\)(.*)$"""
    )

    private val CAUSED_BY = Regex("^(\s*Caused by:\s*)(.*)$")

    private val MORE = Regex("^(\s*)\.\.\.\s+\d+\s+more\s*$")

    /**
     * 把完整堆栈渲染为带样式的 AnnotatedString。
     */
    fun style(
        text: String,
        error: Color,
        brandPrimary: Color,
        textPrimary: Color,
        textTertiary: Color,
    ): AnnotatedString = buildAnnotatedString {
        val lines = text.split('\n')
        lines.forEachIndexed { idx, line ->
            append(styleLine(line, idx == 0, error, brandPrimary, textPrimary, textTertiary))
            if (idx != lines.lastIndex) append('\n')
        }
    }

    /**
     * 渲染单行。[isFirst] 为 true 时按异常类型行处理。
     */
    fun styleLine(
        line: String,
        isFirst: Boolean,
        error: Color,
        brandPrimary: Color,
        textPrimary: Color,
        textTertiary: Color,
    ): AnnotatedString = buildAnnotatedString {
        // ... N more
        MORE.matchEntire(line)?.let { m ->
            pushStyle(SpanStyle(color = textTertiary))
            append(line)
            pop()
            return@buildAnnotatedString
        }
        // Caused by: xxx
        CAUSED_BY.matchEntire(line)?.let { m ->
            pushStyle(SpanStyle(color = error, fontWeight = FontWeight.Bold))
            append(m.groupValues[1])
            pop()
            pushStyle(SpanStyle(color = textPrimary))
            append(m.groupValues[2])
            pop()
            return@buildAnnotatedString
        }
        // at pkg.Class.method(File:line)
        AT_LINE.matchEntire(line)?.let { m ->
            append(m.groupValues[1]) // "  at "
            val qualifier = m.groupValues[2] // com.mini.app.Foo
            val dotIndex = qualifier.lastIndexOf('.')
            if (dotIndex > 0) {
                pushStyle(SpanStyle(color = textTertiary))
                append(qualifier.substring(0, dotIndex + 1))
                pop()
            }
            pushStyle(SpanStyle(color = brandPrimary, fontWeight = FontWeight.Bold))
            append(qualifier.substring(dotIndex + 1))
            pop()
            append(".")
            pushStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold))
            append(m.groupValues[3]) // method
            pop()
            append("(")
            // 参数内容：文件名:行号 → 行号 tertiary
            val location = m.groupValues[4]
            val colonIdx = location.lastIndexOf(':')
            if (colonIdx > 0) {
                pushStyle(SpanStyle(color = textPrimary))
                append(location.substring(0, colonIdx + 1))
                pop()
                pushStyle(SpanStyle(color = textTertiary))
                append(location.substring(colonIdx + 1))
                pop()
            } else {
                pushStyle(SpanStyle(color = textPrimary))
                append(location)
                pop()
            }
            append(")")
            pushStyle(SpanStyle(color = textPrimary))
            append(m.groupValues[5])
            pop()
            return@buildAnnotatedString
        }
        // 首行 / 异常类型行
        if (isFirst || EXCEPTION_LINE.matches(line)) {
            val m = EXCEPTION_LINE.find(line)
            if (m != null) {
                pushStyle(SpanStyle(color = error, fontWeight = FontWeight.Bold))
                append(m.groupValues[1])
                pop()
                val rest = m.groupValues[2]
                if (rest.isNotEmpty()) {
                    pushStyle(SpanStyle(color = textPrimary))
                    append(": ")
                    append(rest)
                    pop()
                }
                return@buildAnnotatedString
            }
        }
        // 默认
        pushStyle(SpanStyle(color = textPrimary))
        append(line)
        pop()
    }
}
