package com.mini.me_core.feature.browser.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.browser.domain.BrowserNetworkRecord

/**
 * F4.7 开发者工具面板：底部滑出，50% 屏高，标签页切换
 * Console（日志 + eval）/ Network（请求列表）/ Elements（DOM 摘要）/ Storage（Cookie/LS）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsPanel(
    consoleLogs: List<String>,
    network: List<BrowserNetworkRecord>,
    domSummary: String,
    storageDump: String,
    onEval: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onRefreshDom: () -> Unit,
    onRefreshStorage: () -> Unit,
    onClearConsole: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    var evalInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.heightIn(min = 300.dp, max = 520.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.browser_devtools_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.lg)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Console", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = tab == 1, onClick = { tab = 1; onRefreshNetwork() }, text = { Text("Network", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = tab == 2, onClick = { tab = 2; onRefreshDom() }, text = { Text("Elements", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = tab == 3, onClick = { tab = 3; onRefreshStorage() }, text = { Text("Storage", style = MaterialTheme.typography.labelMedium) })
            }
            when (tab) {
                0 -> ConsoleTab(consoleLogs = consoleLogs, evalInput = evalInput, onEvalInput = { evalInput = it }, onEval = { onEval(evalInput); evalInput = "" }, onClear = onClearConsole)
                1 -> NetworkTab(records = network, onRefresh = onRefreshNetwork)
                2 -> TextPanel(domSummary.ifBlank { "—" }, onRefresh = onRefreshDom)
                3 -> TextPanel(storageDump.ifBlank { "—" }, onRefresh = onRefreshStorage)
            }
        }
    }
}

@Composable
private fun ConsoleTab(
    consoleLogs: List<String>,
    evalInput: String,
    onEvalInput: (String) -> Unit,
    onEval: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier
            .weight(1f)
            .padding(horizontal = Spacing.lg)) {
            items(consoleLogs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = evalInput,
                onValueChange = onEvalInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("> JS") },
                singleLine = true
            )
            TextButton(onClick = onEval) { Text(stringResource(R.string.browser_devtools_eval)) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.browser_devtools_clear)) }
        }
    }
}

@Composable
private fun NetworkTab(records: List<BrowserNetworkRecord>, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier = Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Text(stringResource(R.string.browser_devtools_net_empty), modifier = Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                items(records) { r ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${r.method} ${r.status} ${r.durationMs}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(r.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPanel(text: String, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(modifier = Modifier
        .padding(Spacing.lg)
        .fillMaxSize()) {
        item { Text(text, style = MaterialTheme.typography.bodySmall) }
    }
}
