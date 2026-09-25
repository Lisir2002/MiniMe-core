package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import kotlinx.coroutines.delay

/** 超过该行数时折叠，懒渲染（只渲染可见行），避免超长代码块一次性布局卡顿。 */
private const val LAZY_RENDER_LINE_THRESHOLD = 100

/**
 * MiniMe 代码块组件（F2.1）。
 *
 * 规范（设计文档 2.3 / 2.4）：
 *  - 背景 surfaceVariant，圆角 12dp，内边距 12dp；
 *  - 顶部栏：语言标签（左，12sp onSurfaceVariant）+ 复制按钮（右，IconButton）；
 *  - 字体等宽 13sp，行高 1.5（约 19.5sp）；
 *  - 行号可选，默认关闭；超宽代码横向滚动；
 *  - 超长代码块（> [LAZY_RENDER_LINE_THRESHOLD] 行）折叠懒渲染，点击展开。
 */
@Composable
fun MiniMeCodeBlock(
    language: String?,
    code: String,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    var expanded by remember(code) { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val appColors = LocalAppTheme.current.colors
    val cornerShape = RoundedCornerShape(12.dp)

    val rawLines = remember(code) { code.split("\n") }
    val totalLines = rawLines.size
    val shouldCollapse = totalLines > LAZY_RENDER_LINE_THRESHOLD
    val visibleLines = remember(rawLines, shouldCollapse, expanded) {
        if (shouldCollapse && !expanded) rawLines.take(LAZY_RENDER_LINE_THRESHOLD) else rawLines
    }
    val visibleCode = remember(visibleLines) { visibleLines.joinToString("\n") }

    // 语法高亮（自实现，颜色全部来自主题语义色）
    val highlighted = remember(visibleCode, language) {
        MiniMeSyntaxHighlighter.build(
            visibleCode,
            MiniMeSyntaxHighlighter.tokenize(visibleCode, language),
        ) { type ->
            when (type) {
                MiniMeSyntaxHighlighter.TokenType.KEYWORD -> appColors.error
                MiniMeSyntaxHighlighter.TokenType.STRING -> appColors.sky
                MiniMeSyntaxHighlighter.TokenType.COMMENT -> appColors.textTertiary
                MiniMeSyntaxHighlighter.TokenType.NUMBER -> appColors.info
                MiniMeSyntaxHighlighter.TokenType.FUNCTION -> appColors.accentReasoning
                MiniMeSyntaxHighlighter.TokenType.TYPE -> appColors.success
            }
        }
    }

    Surface(
        shape = cornerShape,
        color = colors.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // ── 顶部栏：语言标签 + 复制 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.replaceFirstChar { it.uppercase() } ?: "TEXT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.chat_copied else R.string.chat_copy
                        ),
                        tint = if (copied) appColors.success else colors.onSurfaceVariant,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }

            // ── 代码主体：横向滚动 + 行号(可选) ──
            SelectionContainer {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (shouldCollapse && !expanded) Modifier.heightIn(max = 320.dp)
                            else Modifier
                        )
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if (showLineNumbers) {
                            val gutterWidth = remember(totalLines) {
                                (totalLines.toString().length * 8 + 12).dp
                            }
                            Column(
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .padding(start = 12.dp, end = 8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                visibleLines.indices.forEach { idx ->
                                    Text(
                                        text = "${idx + 1}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 19.5.sp,
                                            color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                                        ),
                                    )
                                }
                            }
                        }
                        Text(
                            text = highlighted,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 19.5.sp, // 行高 1.5
                                color = colors.onSurface,
                            ),
                            modifier = Modifier.padding(
                                start = if (showLineNumbers) 0.dp else 12.dp,
                                end = 12.dp,
                                bottom = 12.dp,
                            ),
                        )
                    }
                }
            }

            // ── 折叠态底部展开按钮 ──
            if (shouldCollapse) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cornerShape)
                        .background(colors.surfaceVariant)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.md_code_collapse else R.string.md_code_expand
                            ),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.md_code_lines, totalLines),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
}
