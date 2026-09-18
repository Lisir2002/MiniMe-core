package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 轻量 Markdown 文本（分子组 · AppMarkdownText）：纯 Compose 渲染段落 / 标题 /
 * 粗体 / 行内代码 / 代码块 / 无序与有序列表 / 引用块 / 表格 / 行内链接，
 * 覆盖 AI 对话与卡片正文主场景，不引入重量级渲染库。
 * 供 [AppChatBubble] 内部使用，也可独立嵌入卡片正文。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = appPalette().ink,
    fontWeight: FontWeight? = null,
    codeBlockBackground: Color = appPalette().surfaceDim,
    inlineCodeColor: Color = appPalette().primary,
    inlineCodeBackground: Color = appPalette().surfaceDim,
    linkColor: Color = appPalette().primary,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val segments = remember(text) { parseSegments(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MdSegment.CodeBlock -> CodeBlockView(
                    code = segment.code,
                    background = codeBlockBackground,
                )
                is MdSegment.Heading -> HeadingView(
                    level = segment.level,
                    content = segment.content,
                    color = color,
                    fontWeight = fontWeight,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                )
                is MdSegment.Blockquote -> BlockquoteView(
                    content = segment.content,
                    style = style,
                    color = color,
                    fontWeight = fontWeight,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                )
                is MdSegment.Table -> TableView(
                    headers = segment.headers,
                    rows = segment.rows,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                )
                is MdSegment.Paragraph -> ParagraphView(
                    content = segment.content,
                    style = style,
                    color = color,
                    fontWeight = fontWeight,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                )
                is MdSegment.MarkdownList -> MarkdownListView(
                    items = segment.items,
                    style = style,
                    color = color,
                    fontWeight = fontWeight,
                    inlineCodeColor = inlineCodeColor,
                    inlineCodeBackground = inlineCodeBackground,
                    linkColor = linkColor,
                    onLinkClick = onLinkClick,
                )
                is MdSegment.Image -> ImagePlaceholderView(
                    alt = segment.alt,
                    url = segment.url,
                    color = color,
                )
                is MdSegment.MathBlock -> MathBlockView(
                    formula = segment.formula,
                )
            }
        }
    }
}

// ===== 解析层（轻量、无外部依赖）=====

internal sealed interface MdSegment {
    data class Paragraph(val content: String) : MdSegment
    data class CodeBlock(val code: String) : MdSegment
    data class Heading(val level: Int, val content: String) : MdSegment
    data class Blockquote(val content: String) : MdSegment
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdSegment

    /** 列表项：[level] 为嵌套层级（0 一级，每多缩进 2 空格 +1），[marker] 为项目符号或有序号。 */
    data class ListItem(val level: Int, val marker: String, val text: String)

    /** 无序/有序列表块，支持多级嵌套。 */
    data class MarkdownList(val items: List<ListItem>) : MdSegment

    /** 图片占位：暂不下载，仅以 [alt] + “图片”标记渲染。 */
    data class Image(val alt: String, val url: String) : MdSegment

    /** 块级数学公式 $$...$$，二期接 KaTeX，先按灰底等宽渲染。 */
    data class MathBlock(val formula: String) : MdSegment
}

internal fun parseSegments(text: String): List<MdSegment> {
    val result = mutableListOf<MdSegment>()
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val trimmedStart = lines[i].trimStart()
        when {
            trimmedStart.startsWith("$$") -> {
                val first = trimmedStart.removePrefix("$$").trim()
                val buf = StringBuilder(first)
                i++
                var closed = first.endsWith("$$")
                while (i < lines.size && !closed) {
                    val l = lines[i]
                    buf.append('\n').append(l)
                    if (l.trim().endsWith("$$")) closed = true
                    i++
                }
                val f = buf.toString().trim().removeSuffix("$$").trim()
                if (f.isNotEmpty()) result += MdSegment.MathBlock(f)
            }
            trimmedStart.startsWith("```") -> {
                val buf = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(lines[i])
                    i++
                }
                i++ // 跳过结尾 ```
                if (buf.isNotEmpty()) result += MdSegment.CodeBlock(buf.toString())
            }
            trimmedStart.startsWith("#") -> {
                val level = trimmedStart.takeWhile { it == '#' }.length.coerceAtMost(6)
                val content = trimmedStart.drop(level).trim()
                if (content.isNotEmpty()) result += MdSegment.Heading(level, content)
                i++
            }
            trimmedStart.startsWith(">") -> {
                val buf = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    val body = lines[i].trimStart().drop(1).trimStart()
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(body)
                    i++
                }
                if (buf.isNotEmpty()) result += MdSegment.Blockquote(buf.toString())
            }
            isTableStart(trimmedStart, lines, i) -> {
                val headers = splitTableRow(trimmedStart)
                i += 2 // 跳过表头与分隔行
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().contains("|")) {
                    val cells = splitTableRow(lines[i].trim())
                    if (cells.isNotEmpty()) rows += cells
                    i++
                }
                if (headers.isNotEmpty()) result += MdSegment.Table(headers, rows)
            }
            isListStart(trimmedStart) -> {
                // 多级列表：连续的 - / * / N. 行，缩进每 2 空格提升一级
                val items = mutableListOf<MdSegment.ListItem>()
                val baseIndent = leadingSpaces(lines[i])
                while (i < lines.size) {
                    val raw = lines[i]
                    val ts = raw.trimStart()
                    if (ts.isEmpty()) break
                    val indent = leadingSpaces(raw)
                    val bullet = ts.startsWith("- ") || ts.startsWith("* ")
                    val ordered = ORDERED_ITEM_REGEX.matches(ts)
                    if (!bullet && !ordered) break
                    val relLevel = ((indent - baseIndent) / 2).coerceAtLeast(0)
                    val body = if (bullet) ts.drop(2) else ts.substringAfter(".").trimStart()
                    val marker = if (bullet) "•" else ts.substringBefore(".").trim()
                    items += MdSegment.ListItem(relLevel, marker, body)
                    i++
                }
                if (items.isNotEmpty()) result += MdSegment.MarkdownList(items)
            }
            isImageLine(trimmedStart) -> {
                val m = IMAGE_LINE_REGEX.find(trimmedStart)!!
                result += MdSegment.Image(m.groupValues[1], m.groupValues[2])
                i++
            }
            else -> {
                val buf = StringBuilder()
                while (
                    i < lines.size &&
                    lines[i].isNotBlank() &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].trimStart().startsWith("#") &&
                    !lines[i].trimStart().startsWith(">") &&
                    !isTableStart(lines[i].trimStart(), lines, i) &&
                    !isListStart(lines[i].trimStart()) &&
                    !isImageLine(lines[i].trimStart())
                ) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(lines[i].trimEnd())
                    i++
                }
                if (buf.isNotEmpty()) result += MdSegment.Paragraph(buf.toString())
                while (i < lines.size && lines[i].isBlank()) i++
            }
        }
    }
    return result
}

/** 表格判定：当前行含 `|`，且下一行为 `|---|` 分隔行。 */
private fun isTableStart(trimmed: String, lines: List<String>, index: Int): Boolean {
    if (!trimmed.contains("|")) return false
    val next = lines.getOrNull(index + 1)?.trim() ?: return false
    return next.matches(Regex("^[|:\\-\\s]+$")) && next.contains('-')
}

/** 按 `|` 切分表格行，去掉首尾空单元格（行首尾的管道符）。 */
private fun splitTableRow(line: String): List<String> {
    return line.split("|")
        .map { it.trim() }
        .dropWhile { it.isEmpty() }
        .dropLastWhile { it.isEmpty() }
}

/** 有序列表项：`1. ` / `12. ` 开头。 */
private val ORDERED_ITEM_REGEX = Regex("""\d+\.\s+.*""")

/** 整行图片：`![alt](url)`。 */
private val IMAGE_LINE_REGEX = Regex("""^!\[([^\]]*)\]\(([^)]*)\)\s*$""")

/** 行首是否为列表项（无序短横线/星号或有序 N.）。 */
private fun isListStart(trimmed: String): Boolean =
    trimmed.startsWith("- ") || trimmed.startsWith("* ") || ORDERED_ITEM_REGEX.matches(trimmed)

/** 整行是否为图片占位。 */
private fun isImageLine(trimmed: String): Boolean = IMAGE_LINE_REGEX.matches(trimmed)

/** 统计行首空格数。 */
private fun leadingSpaces(s: String): Int {
    var n = 0
    while (n < s.length && s[n] == ' ') n++
    return n
}

/** 行内可转义字符：\* \_ \[ \] \` \$ \~ \#。 */
private val INLINE_ESCAPABLE = charArrayOf('*', '_', '[', ']', '`', '$', '~', '#')

/** 行内转义处理：`\*` `\_` `\[` `\]` 还原为字面字符。 */
internal fun unescapeInline(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length && text[i + 1] in INLINE_ESCAPABLE) {
            sb.append(text[i + 1])
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

/** 过滤未在白名单的 HTML 标签，避免裸 `<br>` `<sub>` 漏出。 */
internal fun stripUnknownHtml(text: String): String {
    val whitelist = setOf("<br>", "<br/>", "<br />", "<sub>", "</sub>", "<sup>", "</sup>")
    var s = text
    Regex("<[^>]+>").findAll(s).forEach { m ->
        if (m.value !in whitelist) s = s.replace(m.value, "")
    }
    return s
}

// ===== 渲染层 =====

@Composable
private fun HeadingView(
    level: Int,
    content: String,
    color: Color,
    fontWeight: FontWeight?,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = buildInline(
            content,
            style,
            color,
            inlineCodeColor,
            inlineCodeBackground,
            linkColor,
            onLinkClick,
        ),
        style = style.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier.padding(top = AppSpacing.Xs, bottom = if (level <= 2) AppSpacing.Xs else 0.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockquoteView(
    content: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(appPalette().labelTertiary, RoundedCornerShape(AppRadius.Sm)),
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = buildInline(
                content,
                style,
                appPalette().labelSecondary,
                inlineCodeColor,
                inlineCodeBackground,
                linkColor,
                onLinkClick,
            ),
            style = style,
            fontWeight = fontWeight,
            color = appPalette().labelSecondary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun TableView(
    headers: List<String>,
    rows: List<List<String>>,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    val cellStyle = MaterialTheme.typography.bodySmall
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(appPalette().surfaceDim)
            .padding(AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            headers.forEach { header ->
                Text(
                    text = header,
                    style = cellStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = appPalette().ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                headers.indices.forEach { column ->
                    Text(
                        text = row.getOrNull(column) ?: "",
                        style = cellStyle,
                        color = appPalette().labelSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParagraphView(
    content: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        content.lines().forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            val bullet = when {
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "•"
                trimmed.matches(Regex("""\d+\.\s+.*""")) -> trimmed.substringBefore(".").trim()
                else -> null
            }
            if (bullet != null) {
                val body = if (bullet == "•") trimmed.drop(2) else trimmed.substringAfter(".").trimStart()
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    Text(
                        text = bullet,
                        style = style,
                        fontWeight = fontWeight,
                        color = color,
                    )
                    Text(
                        text = buildInline(
                            body,
                            style,
                            color,
                            inlineCodeColor,
                            inlineCodeBackground,
                            linkColor,
                            onLinkClick,
                        ),
                        style = style,
                        fontWeight = fontWeight,
                        color = color,
                    )
                }
            } else {
                Text(
                    text = buildInline(
                        rawLine,
                        style,
                        color,
                        inlineCodeColor,
                        inlineCodeBackground,
                        linkColor,
                        onLinkClick,
                    ),
                    style = style,
                    fontWeight = fontWeight,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun MarkdownListView(
    items: List<MdSegment.ListItem>,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight?,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(start = (item.level * 16).dp),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.marker,
                    style = style,
                    fontWeight = fontWeight,
                    color = color,
                )
                Text(
                    text = buildInline(
                        item.text,
                        style,
                        color,
                        inlineCodeColor,
                        inlineCodeBackground,
                        linkColor,
                        onLinkClick,
                    ),
                    style = style,
                    fontWeight = fontWeight,
                    color = color,
                )
            }
        }
    }
}

/** 图片占位：不真下载，仅显示 alt 文字 + “图片”标记。 */
@Composable
private fun ImagePlaceholderView(alt: String, url: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "🖼",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = if (alt.isBlank()) "图片" else "$alt（图片）",
            style = MaterialTheme.typography.bodySmall,
            color = appPalette().labelSecondary,
        )
    }
}

@Composable
private fun MathBlockView(formula: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(appPalette().surfaceDim)
            .padding(AppSpacing.Md),
    ) {
        Text(
            text = formula,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = appPalette().primary,
        )
    }
}

@Composable
private fun CodeBlockView(code: String, background: Color) {    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(AppRadius.Sm))
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Md),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = appPalette().ink,
        )
    }
}
/**
 * 行内样式：`**粗体**`、`` `行内代码` ``、`[链接](url)`。
 * 有 [onLinkClick] 时链接可点击（[LinkAnnotation.Clickable]），否则仅品牌色 + 下划线。
 */
private fun buildInline(
    raw: String,
    style: TextStyle,
    baseColor: Color,
    inlineCodeColor: Color,
    inlineCodeBackground: Color,
    linkColor: Color,
    onLinkClick: ((String) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw[i] == '\\' && i + 1 < raw.length && raw[i + 1] in INLINE_ESCAPABLE -> {
                // 行内转义：\* \_ \[ \] 还原为字面字符，不触发粗体/链接解析
                append(raw[i + 1])
                i += 2
            }
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
            raw.startsWith("~~", i) -> {
                val end = raw.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(raw[i])
                    i++
                }
            }
            (raw[i] == '*' || raw[i] == '_') && i + 1 < raw.length && raw[i + 1] != ' ' && raw[i + 1] != raw[i] -> {
                val ch = raw[i]
                val end = raw.indexOf(ch, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = baseColor)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
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
            raw.startsWith("\$", i) && i + 1 < raw.length && raw[i + 1] != '\n' -> {
                val end = raw.indexOf("\$", i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFBF5AF2),
                            background = Color(0x22BF5AF2),
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
            raw.startsWith("[", i) -> {
                val close = raw.indexOf("]", i)
                if (close > i + 1) {
                    val open = raw.indexOf("(", close)
                    val urlEnd = if (open == close + 1) raw.indexOf(")", open) else -1
                    if (urlEnd > open) {
                        val label = raw.substring(i + 1, close)
                        val url = raw.substring(open + 1, urlEnd)
                        if (onLinkClick != null) {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = url,
                                    styles = TextLinkStyles(
                                        SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = LinkInteractionListener { onLinkClick(url) },
                                ),
                            ) {
                                append(label)
                            }
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append(label)
                            }
                        }
                        i = urlEnd + 1
                    } else {
                        append(raw[i])
                        i++
                    }
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
