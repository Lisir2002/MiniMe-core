package com.mini.me_core.feature.update.presentation

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.mini.me_core.core.theme.Spacing

/**
 * 基础 Markdown 渲染：支持标题(一/二/三级井号)、无序列表(短横或星号)、加粗(双星号)、内联链接。
 * 仅用于 Release Notes，可滚动，无行数/高度限制。
 */
@Composable
fun MarkdownNotes(
    markdown: String?,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    val text = markdown?.trim().orEmpty()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    val lines = if (text.isEmpty()) {
        emptyList()
    } else {
        text.lines().map { parseLine(it, linkColor) }
    }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
    ) {
        if (text.isEmpty()) {
            androidx.compose.material3.Text(
                text = "（无更新说明）",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
        lines.forEach { line ->
            when (line) {
                is AnnotatedLine.Heading -> androidx.compose.material3.Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (line.level) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 14.sp
                        },
                    ),
                    color = onSurface,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                )
                is AnnotatedLine.Bullet -> androidx.compose.material3.Text(
                    text = buildAnnotatedString {
                        append("•  ")
                        append(line.text)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                is AnnotatedLine.Body -> {
                    if (line.span.text.isBlank()) {
                        androidx.compose.material3.Text(
                            text = "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        androidx.compose.material3.Text(
                            text = line.span,
                            style = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceVariant),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed class AnnotatedLine {
    data class Heading(val level: Int, val text: AnnotatedString) : AnnotatedLine()
    data class Bullet(val text: AnnotatedString) : AnnotatedLine()
    data class Body(val span: AnnotatedString) : AnnotatedLine()
}

private fun parseLine(raw: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedLine {
    val trimmed = raw.trimEnd()
    return when {
        trimmed.startsWith("### ") -> AnnotatedLine.Heading(3, parseInline(trimmed.removePrefix("### "), linkColor))
        trimmed.startsWith("## ") -> AnnotatedLine.Heading(2, parseInline(trimmed.removePrefix("## "), linkColor))
        trimmed.startsWith("# ") -> AnnotatedLine.Heading(1, parseInline(trimmed.removePrefix("# "), linkColor))
        trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
            AnnotatedLine.Bullet(parseInline(trimmed.drop(2), linkColor))
        else -> AnnotatedLine.Body(parseInline(trimmed, linkColor))
    }
}

/** 解析行内加粗(双星号) 与内联链接([text](url))。 */
private fun parseInline(input: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
            )
        )
        while (i < input.length) {
            when {
                input.startsWith("**", i) -> {
                    val end = input.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(boldStyle) { append(input.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(input[i]); i++
                    }
                }
                input[i] == '[' -> {
                    val close = input.indexOf(']', i)
                    val paren = if (close > 0 && close + 1 < input.length && input[close + 1] == '(') {
                        input.indexOf(')', close + 2)
                    } else -1
                    if (close > 0 && paren > close) {
                        val label = input.substring(i + 1, close)
                        val url = input.substring(close + 2, paren)
                        withLink(LinkAnnotation.Url(url, styles = linkStyles)) {
                            append(label)
                        }
                        i = paren + 1
                    } else {
                        append(input[i]); i++
                    }
                }
                else -> {
                    append(input[i]); i++
                }
            }
        }
    }
