package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ==================== 数据模型 ====================

/** Markdown 块级元素。 */
sealed interface MdBlock {
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Quote(val inlines: List<MdInline>) : MdBlock
    data class Bullet(val items: List<List<MdInline>>) : MdBlock
    data class Ordered(val items: List<List<MdInline>>, val start: Int) : MdBlock
    data class Task(val items: List<TaskItem>) : MdBlock
    data class Table(val header: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
    data object Spacer : MdBlock
}

/** 任务列表项。 */
data class TaskItem(val inlines: List<MdInline>, val checked: Boolean)

/** 行内元素。 */
sealed interface MdInline {
    data class Text(val s: String) : MdInline
    data class Bold(val s: String) : MdInline
    data class Italic(val s: String) : MdInline
    data class Code(val s: String) : MdInline
    data class Link(val url: String, val text: String) : MdInline
    data class Img(val url: String, val alt: String) : MdInline
}

// ==================== 解析器（纯函数，无 Android 依赖） ====================

object MiniMeMarkdownParser {

    fun parse(text: String): List<MdBlock> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines()
        val out = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                isFence(line) -> {
                    val fenceChar = line.trimStart().take(1)
                    val lang = line.trim().removePrefix(fenceChar.repeat(3)).trim()
                    val buf = mutableListOf<String>()
                    i++
                    while (i < lines.size) {
                        if (lines[i].trim().startsWith(fenceChar.repeat(3))) { i++; break }
                        buf.add(lines[i]); i++
                    }
                    out.add(MdBlock.Code(lang.ifBlank { null }, buf.joinToString("\n")))
                }
                isTableStart(line) && hasTableSep(lines, i) -> {
                    val res = parseTable(lines, i)
                    out.add(res.block); i = res.next
                }
                isHeading(line) -> {
                    val t = line.trimStart()
                    val lvl = t.takeWhile { it == '#' }.length
                    out.add(MdBlock.Heading(lvl, InlineMdTokenizer.parse(t.drop(lvl).trim())))
                    i++
                }
                isQuote(line) -> {
                    val buf = mutableListOf<String>()
                    while (i < lines.size && isQuote(lines[i])) {
                        buf.add(lines[i].trimStart().removePrefix(">").trimStart()); i++
                    }
                    out.add(MdBlock.Quote(InlineMdTokenizer.parse(buf.joinToString(" "))))
                }
                isTaskItem(line) -> {
                    val items = mutableListOf<TaskItem>()
                    while (i < lines.size && isTaskItem(lines[i])) {
                        val t = lines[i].trimStart()
                        val checked = t.startsWith("- [x]") || t.startsWith("- [X]")
                        val content = t.removePrefix("- [ ] ").removePrefix("- [x] ").removePrefix("- [X] ")
                        items.add(TaskItem(InlineMdTokenizer.parse(content), checked))
                        i++
                    }
                    out.add(MdBlock.Task(items))
                }
                isBullet(line) -> {
                    val items = mutableListOf<List<MdInline>>()
                    while (i < lines.size && isBullet(lines[i])) {
                        val t = lines[i].trimStart()
                        items.add(InlineMdTokenizer.parse(t.drop(2).trim()))
                        i++
                    }
                    out.add(MdBlock.Bullet(items))
                }
                isOrdered(line) -> {
                    val items = mutableListOf<List<MdInline>>()
                    var start = 1
                    while (i < lines.size && isOrdered(lines[i])) {
                        val t = lines[i].trimStart()
                        if (items.isEmpty()) start = t.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                        val content = t.removePrefix(t.takeWhile { it.isDigit() })
                            .removePrefix(".").removePrefix(")").trim()
                        items.add(InlineMdTokenizer.parse(content))
                        i++
                    }
                    out.add(MdBlock.Ordered(items, start))
                }
                isStandaloneImage(line) -> {
                    val m = IMAGE_REGEX.find(line.trim())
                    if (m != null) out.add(MdBlock.Image(m.groupValues[2], m.groupValues[1]))
                    i++
                }
                line.isBlank() -> { out.add(MdBlock.Spacer); i++ }
                else -> {
                    val buf = mutableListOf<String>()
                    while (i < lines.size && isPlainLine(lines[i])) { buf.add(lines[i]); i++ }
                    out.add(MdBlock.Paragraph(InlineMdTokenizer.parse(buf.joinToString(" ").trim())))
                }
            }
        }
        // 合并连续 Spacer，去首尾
        val merged = mutableListOf<MdBlock>()
        for (b in out) {
            if (b is MdBlock.Spacer && merged.lastOrNull() is MdBlock.Spacer) continue
            merged.add(b)
        }
        while (merged.isNotEmpty() && merged.first() is MdBlock.Spacer) merged.removeAt(0)
        while (merged.isNotEmpty() && merged.last() is MdBlock.Spacer) merged.removeAt(merged.size - 1)
        return merged
    }

    private fun isFence(l: String) = l.trim().startsWith("```") || l.trim().startsWith("~~~")
    private fun isHeading(l: String): Boolean {
        val t = l.trimStart(); if (!t.startsWith("#")) return false
        val lv = t.takeWhile { it == '#' }.length
        return lv in 1..6 && t.length > lv && t[lv] == ' '
    }
    private fun isQuote(l: String): Boolean {
        val t = l.trimStart(); return t.startsWith(">") && (t.length == 1 || t[1] == ' ')
    }
    private fun isTaskItem(l: String): Boolean {
        val t = l.trimStart()
        return t.startsWith("- [ ] ") || t.startsWith("- [x] ") || t.startsWith("- [X] ")
    }
    private fun isBullet(l: String): Boolean {
        val t = l.trimStart()
        return (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) && t.length > 2 && !isTaskItem(l)
    }
    private fun isOrdered(l: String): Boolean {
        val t = l.trimStart()
        val n = t.takeWhile { it.isDigit() }.length
        if (n == 0 || t.length <= n + 1) return false
        val sep = t[n]
        return (sep == '.' || sep == ')') && t[n + 1] == ' '
    }
    private val IMAGE_REGEX = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)")
    private fun isStandaloneImage(l: String): Boolean {
        val t = l.trim(); return t.startsWith("![") && IMAGE_REGEX.matches(t)
    }
    private fun isTableStart(l: String) = l.trim().startsWith("|")
    private fun hasTableSep(lines: List<String>, start: Int): Boolean {
        if (start + 1 >= lines.size) return false
        val sep = lines[start + 1].trim()
        return sep.startsWith("|") && sep.contains("-")
    }
    private fun isPlainLine(l: String): Boolean {
        return l.isNotBlank() && !isFence(l) && !isHeading(l) && !isQuote(l) &&
            !isBullet(l) && !isOrdered(l) && !isTaskItem(l) && !isTableStart(l) && !isStandaloneImage(l)
    }

    private data class TableResult(val block: MdBlock.Table, val next: Int)
    private fun parseTable(lines: List<String>, start: Int): TableResult {
        var i = start
        val rows = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith("|")) { rows.add(lines[i].trim()); i++ }
        val cells = rows.map { r ->
            var s = r; if (s.startsWith("|")) s = s.drop(1)
            if (s.endsWith("|")) s = s.dropLast(1)
            s.split("|").map { it.trim() }
        }
        val header: List<List<MdInline>> = cells.getOrNull(0)?.map { InlineMdTokenizer.parse(it) } ?: emptyList()
        val dataRows = mutableListOf<List<List<MdInline>>>()
        for (r in 2 until cells.size) {
            val aligned = (0 until header.size).map { c ->
                cells[r].getOrElse(c) { "" }.let { InlineMdTokenizer.parse(it) }
            }
            dataRows.add(aligned)
        }
        return TableResult(MdBlock.Table(header, dataRows), i)
    }
}

/** 行内分词：粗体/斜体/行内代码/链接/图片。 */
object InlineMdTokenizer {
    private val TOKEN = Regex(
        "!\\[[^\\]]*]\\([^)\\s]+\\)" +           // ![alt](url)
            "|\\[[^\\]]+]\\([^)\\s]+\\)" +            // [text](url)
            "|`[^`\\n]+`" +                            // `code`
            "|\\*\\*\\*[^*\\n]+\\*\\*\\*" +           // ***bold italic***
            "|\\*\\*[^*\\n]+\\*\\*" +                  // **bold**
            "|(?<![*\\w])\\*[^*\\n]+\\*(?!\\*)"        // *italic*
    )

    fun parse(text: String): List<MdInline> {
        if (text.isBlank()) return emptyList()
        val res = mutableListOf<MdInline>()
        var last = 0
        var m = TOKEN.find(text)
        while (m != null) {
            if (m.range.first > last) res.add(MdInline.Text(text.substring(last, m.range.first)))
            res.add(token(m.value)); last = m.range.last + 1
            m = TOKEN.find(text, last)
        }
        if (last < text.length) res.add(MdInline.Text(text.substring(last)))
        return res
    }

    private fun token(v: String): MdInline = when {
        v.startsWith("![") -> {
            val m = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)").find(v)!!
            MdInline.Img(m.groupValues[1], m.groupValues[2])
        }
        v.startsWith("[") -> {
            val m = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)").find(v)!!
            MdInline.Link(m.groupValues[2], m.groupValues[1])
        }
        v.startsWith("`") && v.endsWith("`") -> MdInline.Code(v.substring(1, v.length - 1))
        v.startsWith("***") -> MdInline.Bold(v.substring(3, v.length - 3))
        v.startsWith("**") -> MdInline.Bold(v.substring(2, v.length - 2))
        v.startsWith("*") -> MdInline.Italic(v.substring(1, v.length - 1))
        else -> MdInline.Text(v)
    }
}

// ==================== LRU 渲染缓存（按内容 hash，最近 50 条） ====================

/**
 * Markdown 解析结果 LRU 缓存：key 为内容串（O(1) 哈希），最多保留 [maxEntries] 条，
 * 超出后淘汰最久未使用的条目。
 */
@Immutable
class MiniMeMarkdownCache(
    private val maxEntries: Int = 50,
    private val map: LinkedHashMap<String, List<MdBlock>> = object : LinkedHashMap<String, List<MdBlock>>(
        50, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MdBlock>>?): Boolean =
            size > maxEntries
    }
) {
    fun get(text: String): List<MdBlock>? = map[text]
    fun put(text: String, blocks: List<MdBlock>) { map[text] = blocks }
}

// ==================== 主渲染入口 ====================

/**
 * MiniMe Markdown 渲染主入口（F2.1）。
 *
 * - 解析结果按内容 hash 缓存（[MiniMeMarkdownCache]，LRU 50 条），后台线程解析；
 * - 链接点击通过 [onOpenUrl] 回调（宿主接内置浏览器）；未提供时走系统 Intent 兜底；
 * - 图片缩略图渲染，点击放大（由 [onImageClick] 回调宿主全屏查看器）。
 */
@Composable
fun MiniMeMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cache: MiniMeMarkdownCache? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onImageClick: ((url: String, alt: String) -> Unit)? = null,
) {
    val cached = cache?.get(text)
    var blocks by remember(text, cache) { mutableStateOf(cached ?: emptyList()) }
    if (cached == null) {
        LaunchedEffect(text) {
            val result = withContext(Dispatchers.Default) { MiniMeMarkdownParser.parse(text) }
            blocks = result
            cache?.put(text, result)
        }
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Spacer -> Spacer(Modifier.height(4.dp))
                    is MdBlock.Heading -> MiniMeHeading(block.level, block.inlines, contentColor, onOpenUrl)
                    is MdBlock.Code -> MiniMeCodeBlock(block.language, block.code)
                    is MdBlock.Quote -> MiniMeQuote(block.inlines, onOpenUrl)
                    is MdBlock.Bullet -> MiniMeBulletList(block.items, contentColor, onOpenUrl)
                    is MdBlock.Ordered -> MiniMeOrderedList(block.items, block.start, contentColor, onOpenUrl)
                    is MdBlock.Task -> MiniMeTaskList(block.items, contentColor, onOpenUrl)
                    is MdBlock.Table -> MiniMeTable(block.header, block.rows, onOpenUrl)
                    is MdBlock.Image -> MiniMeImageThumb(block.url, block.alt, onImageClick)
                    is MdBlock.Paragraph -> MiniMeParagraph(block.inlines, contentColor, onOpenUrl)
                }
            }
        }
    }
}

// ==================== 段落 / 标题（行内渲染） ====================

@Composable
internal fun MiniMeParagraph(
    inlines: List<MdInline>,
    color: Color,
    onOpenUrl: ((String) -> Unit)?,
) {
    val base = MaterialTheme.typography.bodyMedium.copy(color = color, fontSize = 14.sp, lineHeight = 20.sp)
    LinkableInlines(inlines = inlines, baseStyle = base, onOpenUrl = onOpenUrl)
}

@Composable
internal fun MiniMeHeading(level: Int, inlines: List<MdInline>, color: Color, onOpenUrl: ((String) -> Unit)?) {
    val t = MaterialTheme.typography
    val style = when (level) {
        1 -> t.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color)
        2 -> t.titleLarge.copy(fontWeight = FontWeight.Bold, color = color)
        3 -> t.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = color)
        4 -> t.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = color)
        else -> t.bodyLarge.copy(fontWeight = FontWeight.Medium, color = color)
    }
    LinkableInlines(inlines = inlines, baseStyle = style, onOpenUrl = onOpenUrl)
}

/** 行内元素渲染为可点击文本：链接 primary 下划线，行内代码等宽背景，粗/斜体。 */
@Composable
internal fun LinkableInlines(
    inlines: List<MdInline>,
    baseStyle: TextStyle,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppTheme.current.colors
    val linkColor = appColors.brandPrimary
    val codeBg = appColors.surfaceSunken
    val annotated = remember(inlines, baseStyle, linkColor, codeBg) {
        buildAnnotatedString {
            for (inline in inlines) {
                when (inline) {
                    is MdInline.Text -> append(inline.s)
                    is MdInline.Bold -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(inline.s); pop()
                    }
                    is MdInline.Italic -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(inline.s); pop()
                    }
                    is MdInline.Code -> {
                        pushStyle(
                            SpanStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                background = codeBg,
                            )
                        )
                        append(" "); append(inline.s); append(" "); pop()
                    }
                    is MdInline.Link -> {
                        val start = length
                        pushStringAnnotation("URL", inline.url)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        )
                        append(inline.text)
                        pop(); pop()
                        addStringAnnotation("URL", inline.url, start, length)
                    }
                    is MdInline.Img -> append(inline.alt.ifBlank { "[图片]" })
                }
            }
        }
    }
    ClickableText(text = annotated, style = baseStyle, modifier = modifier) { offset ->
        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
            onOpenUrl?.invoke(ann.item)
        }
    }
}

// ==================== 图片缩略图 ====================

/**
 * 图片缩略图。无第三方图片加载库时，渲染为占位卡片（图片图标 + alt 文本），
 * 点击通过 [onImageClick] 回调宿主全屏查看器放大。
 */
@Composable
internal fun MiniMeImageThumb(
    url: String,
    alt: String,
    onImageClick: ((String, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onImageClick?.invoke(url, alt) },
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 96.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Image,
                contentDescription = alt,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.height(32.dp),
            )
            if (alt.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = alt,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
