package com.mini.me_core.core.viewer.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.me_core.R
import com.mini.me_core.core.viewer.CodeThemeMapper
import com.mini.me_core.core.viewer.native.dto.HighlightCategory
import com.mini.me_core.core.viewer.native.dto.HighlightSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(
    path: String,
    onBack: () -> Unit,
    viewModel: CodeViewerViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(path) { viewModel.open(path) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.fileName.ifEmpty { stringResource(R.string.viewer_code_title) }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onSearchQuery(ui.searchQuery) }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.viewer_search))
                    }
                    IconButton(onClick = { viewModel.toggleOutline() }) {
                        Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.viewer_outline))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (ui.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ui.error?.let {
                Text(stringResource(R.string.viewer_error, it), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            CodeLines(ui = ui)
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
