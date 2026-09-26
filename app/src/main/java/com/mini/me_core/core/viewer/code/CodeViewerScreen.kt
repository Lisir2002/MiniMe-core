package com.mini.me_core.core.viewer.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

/**
 * Native 代码查看器内容区（无 Scaffold / 无顶栏）。
 *
 * 顶栏（返回 / 文件名 / 搜索 / 大纲）由宿主（关于页）统一提供，遵循 UI 规范「顶栏唯一」。
 * 详细异常堆栈仅写入 FileLogger，UI 仅展示友好错误提示 + 重试按钮。
 */
@Composable
fun CodeViewerScreen(
    path: String,
    viewModel: CodeViewerViewModel,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(path) { viewModel.open(path) }

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
                        text = stringResource(R.string.viewer_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ui.error != null -> {
                AppErrorState(
                    title = stringResource(R.string.viewer_error_title),
                    message = stringResource(R.string.viewer_error_message),
                    retryText = stringResource(R.string.update_retry),
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
                        text = stringResource(R.string.viewer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> CodeLines(ui = ui)
        }
    }
}

@Composable
private fun CodeLines(ui: CodeViewerUiState) {
    // 行起始字节偏移（UTF-8），用于把全局 span 映射到行内偏移。
    val lineStarts = ArrayList<Int>(ui.lines.size + 1)
    var acc = 0
    lineStarts.add(0)
    for (line in ui.lines) {
        acc += line.toByteArray(Charsets.UTF_8).size + 1 // +1 for '\n'
        lineStarts.add(acc)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(ui.lines, key = { i, _ -> i }) { index, line ->
            val startByte = lineStarts[index]
            // 在 composable 上下文预计算类别颜色表
            val colorMap: Map<HighlightCategory, Color> = HighlightCategory.entries.associateWith {
                CodeThemeMapper.colorFor(it)
            }
            val annotated = annotateLine(line, startByte, ui.spans) { colorMap[it] ?: Color.Unspecified }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = (index + 1).toString().padStart(5, ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = annotated,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
