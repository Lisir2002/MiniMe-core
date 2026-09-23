package com.mini.me_core.feature.agent.presentation.component.richsegment
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
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

    CompositionLocalProvider(LocalContentColor provides color) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
        ) {
            for (seg in segments) {
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
                .clip(RoundedCornerShape(1.dp))
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
                            .clip(RoundedCornerShape(2.dp))
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

@Composable
private fun CodeBlockCard(seg: RichSegment.CodeBlock, isDark: Boolean) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(seg) { mutableStateOf(true) }
    val syntaxLang = seg.language?.let { inferByLabel(it) }
    val lineCount = seg.code.count { it == '\n' } + 1
    val shouldCollapse = lineCount > 30

    // 混合模式：代码块背景跟随 surfaceSunken，文字跟随 textPrimary
    val colors = LocalAppTheme.current.colors
    val bg = colors.surfaceSunken
    val fg = colors.textPrimary
    val label = seg.language?.uppercase() ?: "CODE"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(codeBlockCorner)
            .background(bg)
            .border(BorderStroke(1.dp, colors.borderDefault), codeBlockCorner)
            .animateContentSize(animationSpec = tween(160))
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
                modifier = Modifier.size(14.dp)
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
                modifier = Modifier.size(30.dp),
                onClick = { clipboard.setText(AnnotatedString(plainText)) }
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.ui______224996c0),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(15.dp)
                )
            }
            if (shouldCollapse) {
                androidx.compose.material3.IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.ui____def9e98b) else stringResource(R.string.ui____e2edde5a),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(15.dp)
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
                    color = fg
                )
                val highlighted = syntaxLang?.let { highlightCode(seg.code, it) }
                if (highlighted != null) {
                    // 水平滚动：避免窄屏上长行被截断
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        // 行号
                        val gutterW = remember(lineCount) { (lineCount.toString().length * 8 + 10).dp }
                        Column(
                            modifier = Modifier
                                .width(gutterW)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.End
                        ) {
                            (1..lineCount).forEach { n ->
                                Text(
                                    text = "$n",
                                    style = rawStyle.copy(
                                        color = fg.copy(alpha = 0.42f),
                                        fontSize = 11.5.sp
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        Text(
                            text = highlighted,
                            style = rawStyle,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    Text(
                        text = seg.code,
                        style = rawStyle
                    )
                }
            }
        }
    }
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
                contentDescription = stringResource(R.string.ui______ee92cd5e),
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
