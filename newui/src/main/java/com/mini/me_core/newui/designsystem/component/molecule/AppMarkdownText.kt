package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 轻量 Markdown 文本（分子组 · AppMarkdownText）：纯 Compose 渲染段落 / 粗体 / 行内代码 /
 * 代码块 / 无序列表，覆盖 AI 对话与卡片正文主场景，不引入重量级渲染库。
 * 供 [AppChatBubble] 内部使用，也可独立嵌入卡片正文。
 */
@Composable
fun AppMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = AppColor.BrandInk,
    fontWeight: FontWeight? = null,
    codeBlockBackground: Color = AppColor.BrandSurfaceDim,
    inlineCodeColor: Color = AppColor.BrandPrimary,
    inlineCodeBackground: Color = AppColor.BrandSurfaceDim,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        parseSegments(text).forEach { segment ->
            when (segment) {
                is MdSegment.CodeBlock -> CodeBlockView(
                    code = segment.code,
                    background = codeBlockBackground,
                )
                is MdSegment.Paragraph -> ParagraphView(
                    content = segment.content,
                    style = style,
                    color = color,
                    fontWeight = fontWeight,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                )
            }
        }
    }
}

// ===== 解析层（轻量、无外部依赖）=====

private sealed interface MdSegment {
    data class Paragraph(val content: String) : MdSegment
    data class CodeBlock(val code: String) : MdSegment
}

private fun parseSegments(text: String): List<MdSegment> {
    val result = mutableListOf<MdSegment>()
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val trimmedStart = lines[i].trimStart()
        if (trimmedStart.startsWith("```")) {
            val buf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(lines[i])
                i++
            }
            i++ // 跳过结尾 ```
            if (buf.isNotEmpty()) result += MdSegment.CodeBlock(buf.toString())
        } else {
            val buf = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank() && !lines[i].trimStart().startsWith("```")) {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(lines[i].trimEnd())
                i++
            }
            if (buf.isNotEmpty()) result += MdSegment.Paragraph(buf.toString())
            while (i < lines.size && lines[i].isBlank()) i++
        }
    }
    return result
}

// ===== 渲染层 =====

@Composable
private fun ParagraphView(
    content: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        content.lines().forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    Text(
                        text = "•",
                        style = style,
                        fontWeight = fontWeight,
                        color = color,
                    )
                    Text(
                        text = buildInline(trimmed.drop(2), style, color, inlineCodeColor, inlineCodeBackground),
                        style = style,
                        fontWeight = fontWeight,
                        color = color,
                    )
                }
            } else {
                Text(
                    text = buildInline(rawLine, style, color, inlineCodeColor, inlineCodeBackground),
                    style = style,
                    fontWeight = fontWeight,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(code: String, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(AppRadius.Sm))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Md),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AppColor.BrandInk,
        )
    }
}

/** 行内样式：`**粗体**` 与 `` `行内代码` ``。 */
private fun buildInline(
    raw: String,
    style: TextStyle,
    baseColor: Color,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(raw[i])
                    i++
                }
            }
            raw.startsWith("`", i) -> {
                val end = raw.indexOf("`", i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = inlineCodeColor,
                            background = inlineCodeBackground,
                        ),
                    ) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(raw[i])
                    i++
                }
            }
            else -> {
                append(raw[i])
                i++
            }
        }
    }
}
