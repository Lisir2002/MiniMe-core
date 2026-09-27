package com.mini.me_core.core.viewer.code

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppErrorState
import com.mini.me_core.core.viewer.CodeThemeMapper
import com.mini.me_core.core.viewer.native.dto.HighlightCategory
import com.mini.me_core.core.viewer.native.dto.HighlightSpan
import com.mini.me_core.core.viewer.native.dto.SymbolNode
import kotlinx.coroutines.flow.collectLatest

/**
 * Native 代码查看器内容区（无 Scaffold / 无顶栏）。
 *
 * 顶栏（返回 / 文件名 / 搜索 / 大纲）由宿主（关于页）统一提供，遵循 UI 规范「顶栏唯一」。
 * 大纲抽屉在此处渲染（覆盖在代码区上方），可直接访问 LazyListState 跳转行号。
 */
@Composable
fun CodeViewerScreen(
    path: String,
    viewModel: CodeViewerViewModel,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(path) { viewModel.open(path) }

    // 监听滚动事件（来自大纲跳转 / 搜索匹配跳转）
    LaunchedEffect(Unit) {
        viewModel.scrollToLine.collectLatest { line ->
            if (line in 0 until ui.lines.size) {
                listState.animateScrollToItem(line)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            ui.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ui.error != null -> {
                AppErrorState(
                    title = "加载失败",
                    message = "代码查看器加载失败，请重试。",
                    retryText = "重试",
                    onRetry = { viewModel.open(path) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ui.lines.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "文件为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> CodeLines(
                ui = ui,
                listState = listState,
                currentMatchLine = ui.searchResults.getOrNull(ui.currentMatchIndex),
            )
        }

        // 大纲抽屉
        if (ui.showOutline) {
            OutlineDrawer(
                outline = ui.outline,
                onDismiss = { viewModel.closeOutline() },
                onItemClick = { node -> viewModel.onOutlineItemClick(node.startLine - 1) },
            )
        }
    }
}

@Composable
private fun CodeLines(
    ui: CodeViewerUiState,
    listState: LazyListState,
    currentMatchLine: Int?,
) {
    // 行起始字节偏移（UTF-8），用于把全局 span 映射到行内偏移。
    val lineStarts = ArrayList<Int>(ui.lines.size + 1)
    var acc = 0
    lineStarts.add(0)
    for (line in ui.lines) {
        acc += line.toByteArray(Charsets.UTF_8).size + 1 // +1 for '\n'
        lineStarts.add(acc)
    }

    val matchSet = remember(ui.searchResults) { ui.searchResults.toSet() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(ui.lines, key = { i, _ -> i }) { index, line ->
            val startByte = lineStarts[index]
            val colorMap: Map<HighlightCategory, Color> = HighlightCategory.entries.associateWith {
                CodeThemeMapper.colorFor(it)
            }
            val annotated = annotateLine(line, startByte, ui.spans) { colorMap[it] ?: Color.Unspecified }
            val isMatch = index in matchSet
            val isCurrentMatch = index == currentMatchLine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            isCurrentMatch -> MaterialTheme.colorScheme.tertiaryContainer
                            isMatch -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                    )
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = (index + 1).toString().padStart(5, ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentMatch)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = annotated,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentMatch)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        Color.Unspecified,
                )
            }
        }
    }
}

/** 右侧滑出大纲抽屉：半透明遮罩 + 70% 宽度面板。 */
@Composable
private fun OutlineDrawer(
    outline: List<SymbolNode>,
    onDismiss: () -> Unit,
    onItemClick: (SymbolNode) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )
        // 抽屉面板
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.72f)
                .align(Alignment.CenterEnd),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "大纲",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(outline, key = { it.hashCode() }) { node ->
                        OutlineItem(node = node, onClick = { onItemClick(node) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineItem(node: SymbolNode, onClick: () -> Unit) {
    // kind: 0=unknown, 1=class, 2=function, 3=method, 4=interface, 5=struct, 6=section/heading
    val indentDp = when (node.kind) {
        1, 4, 5 -> 8.dp   // class/interface/struct
        2, 3 -> 24.dp     // function/method
        else -> 8.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = indentDp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = node.startLine.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun annotateLine(
    line: String,
    lineStartByte: Int,
    spans: List<HighlightSpan>,
    resolveColor: (HighlightCategory) -> Color,
): AnnotatedString = buildAnnotatedString {
    append(line)
    val lineBytes = line.toByteArray(Charsets.UTF_8).size
    for (span in spans) {
        if (span.endByte <= lineStartByte || span.startByte >= lineStartByte + lineBytes + 1) continue
        val s = (span.startByte - lineStartByte).coerceIn(0, line.length)
        val e = (span.endByte - lineStartByte).coerceIn(s, line.length)
        if (s < e) {
            addStyle(SpanStyle(color = resolveColor(span.category)), s, e)
        }
    }
}
