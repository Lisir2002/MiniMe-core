package com.mini.me_core.feature.agent.presentation.component.richsegment
import com.mini.me_core.core.theme.tokens.IconSizeTokens
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.ScaledAnimation
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.feature.git.presentation.component.highlightCode
import com.mini.me_core.feature.git.presentation.component.inferSyntaxLanguage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Terminal
import dev.snipme.highlights.model.SyntaxLanguage

/** 对外暴露的「跳转行为」参数：渲染某个富文本点击时的联动动作。 */
data class SegmentationNavigationActions(
    /** 点击 URL 链接：打开内置服务浏览器。 */
    val onOpenUrl: (String) -> Unit,
    /** 点击文件路径：打开文件预览（若宿主未提供实现则走复制路径兜底）。 */
    val onOpenFilePath: (String) -> Unit,
)

// 静态值（非 Composable 上下文无法使用 LocalCornerRadius）
// Composable 使用处可按需用 LocalCornerRadius.current.lg 覆盖
private val codeBlockCorner = RoundedCornerShape(10.dp)
private val codeBlockHeaderHeight = 34.dp

@Composable
internal fun SegmentRenderer(
    segments: List<RichSegment>,
    color: Color,
    compact: Boolean,
    nav: SegmentationNavigationActions,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bodySize = if (compact) 13.sp else 14.sp
    val bodyLineHeight = if (compact) 18.sp else 20.sp
    val lineBreak = LineBreak.Simple
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(
        color = color,
        fontSize = bodySize,
        lineHeight = bodyLineHeight,
        lineBreak = lineBreak
    )

    // 问题22：渲染前清洗分段——
    // 1) 移除开头/末尾的所有 Blank（模型输出常带数量不等的尾部空行，否则每个 Blank
    //    渲染为 4dp Spacer 再加 spacedBy 6dp，尾部空白大小随机）；
    // 2) 合并中间连续 Blank 为一个（Markdown 中连续空行只等价于一次段落分隔）。
    val cleanedSegments = remember(segments) {
        segments
            .dropWhile { it is RichSegment.Blank }
            .dropLastWhile { it is RichSegment.Blank }
            .fold(mutableListOf<RichSegment>()) { acc, seg ->
                if (seg is RichSegment.Blank && acc.lastOrNull() is RichSegment.Blank) {
                    acc
                } else {
                    acc.add(seg)
                }
                acc
            }
    }

    CompositionLocalProvider(LocalContentColor provides color) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
        ) {
            for (seg in cleanedSegments) {
                when (seg) {
                    RichSegment.Blank -> Spacer(Modifier.height(if (compact) 2.dp else 4.dp))

                    is RichSegment.Paragraph -> ParagraphCard(seg.inlines, baseStyle, nav)

                    is RichSegment.Heading -> HeadingCard(seg.level, seg.inlines, color)

                    is RichSegment.Quote -> QuoteCard(seg.lines, color)

                    is RichSegment.BulletList -> BulletListCard(seg.items, baseStyle, nav)

                    is RichSegment.OrderedList -> OrderedListCard(seg.items, seg.start, baseStyle, nav)

                    is RichSegment.CodeBlock -> CodeBlockCard(seg, isDark)

                    is RichSegment.Table -> TableCard(seg, baseStyle, nav, isDark)

                    is RichSegment.Command -> CommandCard(seg.command, isDark)
                }
            }
        }
    }
}

// ============ 段落 / 行内 ============

@Composable
private fun ParagraphCard(
    inlines: List<Inline>,
    baseStyle: TextStyle,
    nav: SegmentationNavigationActions,
    modifier: Modifier = Modifier
) {
    LinkAwareText(inlines = inlines, baseStyle = baseStyle, nav = nav, modifier = modifier)
}

/** 渲染行内元素文本：对 URL / FILE 注解做出点击响应，支持复制 / 打开。 */
@Composable
private fun LinkAwareText(
    inlines: List<Inline>,
    baseStyle: TextStyle,
    nav: SegmentationNavigationActions,
    modifier: Modifier = Modifier
) {
    // 审计修复：行内代码背景、链接色、文件路径色全部跟随主题语义色
    val colors = LocalAppTheme.current.colors
    val codeBackground = colors.surfaceSunken
    val linkColor = colors.brandPrimary
    val filePathColor = colors.sky
    val annotated = androidx.compose.runtime.remember(inlines, baseStyle, codeBackground, linkColor, filePathColor) {
        renderInlines(inlines, baseStyle, codeBackground, linkColor, filePathColor)
    }

    ClickableText(
        text = annotated,
        style = baseStyle,
        modifier = modifier,
    ) { offset ->
        // 先尝试 URL 注解
        val urlHit = annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
            .firstOrNull()
        if (urlHit != null) {
            nav.onOpenUrl(urlHit.item)
            return@ClickableText
        }
        val fileHit = annotated.getStringAnnotations(tag = "FILE", start = offset, end = offset)
            .firstOrNull()
        if (fileHit != null) {
            nav.onOpenFilePath(fileHit.item)
            return@ClickableText
        }
    }
}

private fun renderInlines(
    inlines: List<Inline>,
    baseStyle: TextStyle,
    codeBackground: Color,
    linkColor: Color,
    filePathColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        for (inline in inlines) {
            when (inline) {
                is Inline.Plain -> append(inline.text)
                is Inline.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(inline.text)
                    pop()
                }
                is Inline.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(inline.text)
                    pop()
                }
                is Inline.BoldItalic -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    append(inline.text)
                    pop()
                }
                is Inline.Code -> {
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        // 混合模式：行内代码 12sp 等宽（非 Composable 上下文，使用静态值）
                        fontSize = 12.sp,
                        background = codeBackground
                    ))
                    append(" ")
                    append(inline.code)
                    append(" ")
                    pop()
                }
                is Inline.Url -> {
                    val label = inline.label ?: inline.url
                    val start = length
                    pushStringAnnotation(tag = "URL", annotation = inline.url)
                    pushStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    ))
                    append(label)
                    pop()
                    pop()
                    val end = length
                    addStringAnnotation(tag = "URL", annotation = inline.url, start = start, end = end)
                }
                is Inline.FilePath -> {
                    val start = length
                    pushStringAnnotation(tag = "FILE", annotation = inline.path)
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (baseStyle.fontSize.value - 0.5f).sp,
                        color = filePathColor
                    ))
                    append(inline.path)
                    pop()
                    pop()
                    val end = length
                    addStringAnnotation(tag = "FILE", annotation = inline.path, start = start, end = end)
                }
            }
        }
    }.takeUnless { it.isEmpty() } ?: AnnotatedString("")
}

// ============ 标题 ============

@Composable
private fun HeadingCard(level: Int, inlines: List<Inline>, color: Color) {
    val typography = MaterialTheme.typography
    val (style, spacingTop) = when (level) {
        1 -> typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = color) to 0.dp
        2 -> typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color) to 0.dp
        3 -> typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = color) to 0.dp
        4 -> typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = color) to 0.dp
        5 -> typography.bodyLarge.copy(fontWeight = FontWeight.Medium, color = color) to 0.dp
        else -> typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = color) to 0.dp
    }
    if (spacingTop > 0.dp) Spacer(Modifier.height(spacingTop))
    val nav = LocalSegmentNavActions.current
    LinkAwareText(inlines = inlines, baseStyle = style, nav = nav)
}

// ============ 引用 ============

@Composable
private fun QuoteCard(lines: List<String>, color: Color) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = LocalAppTheme.current.colors
    Row(Modifier.fillMaxWidth()) {
        // 混合模式：引用块左侧竖线 2dp，颜色跟随主题 brandPrimary
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(LocalCornerRadius.current.map(1.dp)))
                .background(colors.brandPrimary)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // 混合模式：引用文字弱化色
            val quoteColor = colors.textSecondary
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = quoteColor
                    )
                )
            }
        }
    }
}

// ============ 列表 ============

@Composable
private fun BulletListCard(
    items: List<List<Inline>>,
    baseStyle: TextStyle,
    nav: SegmentationNavigationActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { inlines ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp, start = 2.dp, end = 10.dp)) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.map(2.dp)))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                LinkAwareText(
                    inlines = inlines,
                    baseStyle = baseStyle,
                    nav = nav,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OrderedListCard(
    items: List<List<Inline>>,
    start: Int,
    baseStyle: TextStyle,
    nav: SegmentationNavigationActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { idx, inlines ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${idx + start}. ",
                    style = baseStyle.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
                LinkAwareText(
                    inlines = inlines,
                    baseStyle = baseStyle,
                    nav = nav,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============ 代码块（含语言标签 + 复制 + 展开/收起） ============

/** 折叠态最多渲染的行数：折叠容器高 260dp 且可垂直滚动，超出部分用户不会看到，展开时才全量渲染。 */
private const val FOLD_VISIBLE_LINES = 80

/**
 * 生成代码块行号文本（每行一个数字，换行分隔）。
 *
 * 旧实现为每一行创建一个 `Text` Composable，数千行的代码块会产生数千个节点，
 * 是长输出场景下 slot table 膨胀与布局耗时的主要来源；合并为单个 Text 后节点数恒为 1，
 * 视觉表现（等宽右对齐、行高）保持不变。
 */
internal fun buildLineNumbers(lineCount: Int): String = buildString {
    for (n in 1..lineCount) {
        if (n > 1) append('\n')
        append(n)
    }
}

/**
 * 取文本的前 [maxLines] 行（按换行符切分）。
 *
 * 用于折叠态裁剪渲染：只让可见部分进入组合与布局，避免数千行全部展开渲染。
 * 实现上只做一次 substring，不产生中间集合。
 */
internal fun headLines(text: String, maxLines: Int): String {
    if (maxLines <= 0) return ""
    var seenLines = 1
    var idx = 0
    while (idx < text.length) {
        if (text[idx] == '\n') {
            if (seenLines == maxLines) return text.substring(0, idx)
            seenLines++
        }
        idx++
    }
    return text
}

@Composable
private fun CodeBlockCard(seg: RichSegment.CodeBlock, isDark: Boolean) {
    val clipboard = LocalClipboardManager.current
    // 注意：不能用 remember(seg) 做 key——seg 是 data class，流式期间每个 delta 都变化，
    // 会导致用户手动收起的代码块在下一个 delta 被强制重新展开。
    var expanded by remember { mutableStateOf(true) }
    val syntaxLang = seg.language?.let { inferByLabel(it) }
    val lineCount = seg.code.count { it == '\n' } + 1
    val shouldCollapse = lineCount > 30

    // 问题21：代码块背景改用 surfacePage（比 surfaceSunken 更深/更浅），
    // 解决在思考气泡（surfaceVariant=surfaceSunken）中代码块背景融为一体的问题。
    // 深色模式 surfacePage=#0F172A 在 #334155 气泡上形成凹陷层级；浅色模式 #F8FAFC 在 #F1F5F9 上有对比。
    val colors = LocalAppTheme.current.colors
    val bg = colors.surfacePage
    val fg = colors.textPrimary
    val label = seg.language?.uppercase() ?: "CODE"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.map(10.dp)))
            .background(bg)
            // 问题21：边框加强为 borderStrong，轮廓更清晰
            .border(BorderStroke(1.dp, colors.borderStrong), RoundedCornerShape(LocalCornerRadius.current.map(10.dp)))
            .animateContentSize(animationSpec = ScaledAnimation.tweenGlobal(160))
    ) {
        // Header：语言角标 + 复制 + 展开/收起（渐变跟随品牌色）
        Row(
            Modifier
                .fillMaxWidth()
                .height(codeBlockHeaderHeight)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.brandPrimary, colors.brandSecondary)
                    )
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(IconSizeTokens.small)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(Modifier.weight(1f))
            val plainText = seg.code
            androidx.compose.material3.IconButton(
                modifier = Modifier.size(IconSizeTokens.large),
                onClick = { clipboard.setText(AnnotatedString(plainText)) }
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.chat_copy_code),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(IconSizeTokens.small)
                )
            }
            if (shouldCollapse) {
                androidx.compose.material3.IconButton(
                    modifier = Modifier.size(IconSizeTokens.large),
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.common_collapse_action) else stringResource(R.string.common_expand),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(IconSizeTokens.small)
                    )
                }
            }
        }

        SelectionContainer {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (!expanded) Modifier.height(260.dp).verticalScroll(rememberScrollState())
                        else Modifier
                    )
                    // 混合模式：代码块内边距 12dp
                    .padding(horizontal = Spacing.md, vertical = Spacing.md)
            ) {
                val rawStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    // 混合模式：代码块 12sp 等宽，行高 18sp
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    lineHeight = 18.sp,
                    color = fg,
                    // 代码是逐字内容：RTL 语言（阿拉伯语/希伯来语）环境下若跟随布局方向
                    // 做 Bidi 解析，代码里的括号、运算符、数字串会被重排，肉眼可见错位。
                    // 强制 LTR，让代码在任何系统语言下都按源码顺序渲染。
                    textDirection = TextDirection.Ltr
                )
                // 折叠态只渲染前若干行：折叠容器是 260dp 固定高 + 垂直滚动，后面的行
                // 用户根本滚不到（展开后才是完整内容），全量渲染数千行纯属浪费。
                val folded = !expanded && shouldCollapse
                // Tab 展开必须在进入语法高亮之前统一做：高亮 span 按字符索引定位，
                // 事后对 AnnotatedString 做字符串替换会让所有 span 整体错位。
                // 复制按钮仍复制未展开的 seg.code 原文。
                val renderedCode = remember(seg.code, folded) {
                    val base = if (folded) headLines(seg.code, FOLD_VISIBLE_LINES) else seg.code
                    base.expandTabsForRender()
                }
                val renderedLineCount = if (folded) minOf(lineCount, FOLD_VISIBLE_LINES) else lineCount
                // 流式增量高亮：未闭合块只高亮「已稳定的完整行」，末行（仍在写入）按纯文本追加。
                // 每个 delta 只重排一行，而不是全量重跑语法高亮（原实现每字符一次全量高亮，O(n²)）。
                // 闭合到达时 stable 区 === 最终内容，视觉上无缝过渡。
                val stableCode = if (seg.closed) renderedCode else stablePrefixOf(renderedCode)
                val tailCode = if (seg.closed) "" else renderedCode.removePrefix(stableCode)
                // 语法高亮放后台线程：长代码块在主线程同步高亮会直接掉帧。
                // produceState 在 key 变化期间保留上一次结果，不会出现「空一帧」。
                val highlighted by produceState<AnnotatedString?>(
                    initialValue = null,
                    key1 = stableCode,
                    key2 = syntaxLang
                ) {
                    value = if (syntaxLang == null || stableCode.isBlank()) {
                        null
                    } else {
                        withContext(Dispatchers.Default) { highlightCode(stableCode, syntaxLang) }
                    }
                }
                // 高亮未就绪（首帧/切换语言）时先按纯文本渲染，绝不留空。
                val display: AnnotatedString = highlighted
                    ?.let { if (tailCode.isEmpty()) it else it + AnnotatedString(tailCode) }
                    ?: AnnotatedString(renderedCode)
                if (highlighted != null || syntaxLang == null) {
                    // 整块强制 LTR：水平滚动容器同样按 LTR 排（RTL 语言下默认从右起滚，
                    // 行号会被甩到代码右侧），保证行号列始终在左、代码始终按源码方向铺开。
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        // 水平滚动：避免窄屏上长行被截断
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            // 行号：合并为单个 Text。旧实现每行一个 Text，数千行的代码块会产生
                            // 数千个 Composable 节点，是长输出时 slot table 膨胀与布局耗时的主因。
                            val gutterW = remember(renderedLineCount) {
                                (renderedLineCount.toString().length * 8 + 10).dp
                            }
                            Text(
                                text = remember(renderedLineCount) { buildLineNumbers(renderedLineCount) },
                                style = rawStyle.copy(
                                    color = fg.copy(alpha = 0.42f),
                                    fontSize = 11.5.sp
                                ),
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .width(gutterW)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = display,
                                style = rawStyle,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = renderedCode,
                        style = rawStyle
                    )
                }
            }
        }
    }
}

/**
 * 取代码的「稳定前缀」：到最后一个换行为止的全部完整行。
 *
 * 流式期间末行仍可能被继续写入，对它做语法高亮是纯浪费（下一字符就整体失效）；
 * 只高亮稳定区，末行按纯文本渲染，可将每个 delta 的高亮开销从 O(全量) 降到 O(稳定区)。
 */
private fun stablePrefixOf(code: String): String {
    val lastNewline = code.lastIndexOf('\n')
    return if (lastNewline < 0) "" else code.substring(0, lastNewline + 1)
}

private fun inferByLabel(label: String): SyntaxLanguage? {
    // 常见别名映射
    return when (label.lowercase()) {
        "sh", "bash", "shell", "zsh" -> SyntaxLanguage.SHELL
        "yml", "yaml" -> null
        "js", "javascript" -> SyntaxLanguage.JAVASCRIPT
        "ts", "typescript" -> SyntaxLanguage.TYPESCRIPT
        "kt", "kotlin" -> SyntaxLanguage.KOTLIN
        "py" -> SyntaxLanguage.PYTHON
        else -> inferSyntaxLanguage("dummy.$label")
    }
}

// ============ 命令卡片（终端黑） ============

@Composable
private fun CommandCard(command: String, isDark: Boolean) {
    val clipboard = LocalClipboardManager.current
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
            .background(if (isDark) Color(0xFF0B1018) else Color(0xFF0F172A))
            .border(
                BorderStroke(
                    1.dp,
                    colors.success.copy(alpha = 0.6f)
                ),
                RoundedCornerShape(LocalCornerRadius.current.lg)
            )
            .clickable { clipboard.setText(AnnotatedString(command)) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧绿色提示色条（跟随主题 success 色）
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.success)
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Rounded.Terminal,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer {
            Box(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        text = "$ ",
                        color = colors.success,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = command,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
        androidx.compose.material3.IconButton(onClick = { clipboard.setText(AnnotatedString(command)) }) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy_command),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============ 表格 ============

@Composable
private fun TableCard(
    seg: RichSegment.Table,
    baseStyle: TextStyle,
    nav: SegmentationNavigationActions,
    isDark: Boolean
) {
    val colCount = seg.header.size
    if (colCount <= 0) return
    val colors = LocalAppTheme.current.colors
    val headerBg = colors.surfaceAccent
    val rowBgAlt = colors.surfaceSunken
    val borderColor = colors.borderDefault

    Surface(
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, borderColor),
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 外层横向滚动：表格宽度超屏宽时水平滚
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(Modifier.fillMaxWidth()) {
                // 表头
                Row(Modifier.fillMaxWidth().background(headerBg)) {
                    for (cell in seg.header) {
                        Box(
                            Modifier
                                .widthIn(min = 110.dp)
                                .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)
                                .border(
                                    BorderStroke(0.5.dp, borderColor.copy(alpha = 0.5f))
                                )
                        ) {
                            LinkAwareText(
                                inlines = cell,
                                baseStyle = baseStyle.copy(fontWeight = FontWeight.SemiBold),
                                nav = nav
                            )
                        }
                    }
                }
                // 数据行（交替底色）
                seg.rows.forEachIndexed { rIdx, row ->
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (rIdx % 2 == 1) Modifier.background(rowBgAlt) else Modifier)
                    ) {
                        for (cell in row) {
                            Box(
                                Modifier
                                    .widthIn(min = 110.dp)
                                    .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)
                                    .border(
                                        BorderStroke(
                                            0.5.dp,
                                            borderColor.copy(alpha = 0.5f)
                                        )
                                    )
                            ) {
                                LinkAwareText(inlines = cell, baseStyle = baseStyle, nav = nav)
                            }
                        }
                    }
                }
            }
        }
    }
}
