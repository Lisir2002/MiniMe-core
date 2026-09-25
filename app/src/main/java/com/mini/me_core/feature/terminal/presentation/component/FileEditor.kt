package com.mini.me_core.feature.terminal.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import kotlinx.coroutines.launch

/**
 * P2 模块9：内置容器文本编辑器。
 * - 行号开关 / 自动换行开关
 * - 未保存圆点 + 返回拦截确认
 * - 保存后留在编辑器并 Snackbar 提示
 */
@Composable
fun ContainerFileEditor(
    filePath: String,
    access: ContainerFileAccess,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(true) }
    var confirmExit by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(filePath) {
        loading = true
        access.readFileText(filePath)
            .onSuccess { content = it }
            .onFailure { content = "" }
        loading = false
    }

    fun attemptClose() {
        if (dirty) confirmExit = true else onDismiss()
    }

    BackHandler(enabled = true) { attemptClose() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { attemptClose() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "close",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = filePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (dirty) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showLineNumbers = !showLineNumbers }) {
                        Icon(Icons.Rounded.Numbers, contentDescription = "line numbers",
                            tint = if (showLineNumbers) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { wordWrap = !wordWrap }) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = "word wrap",
                            tint = if (wordWrap) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            saving = true
                            scope.launch {
                                access.writeFileText(filePath, content)
                                    .onSuccess {
                                        dirty = false
                                        snackbar.showSnackbar("已保存")
                                    }
                                    .onFailure { snackbar.showSnackbar("保存失败") }
                                saving = false
                            }
                        },
                        enabled = dirty && !saving
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "save",
                            tint = if (dirty) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading || saving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                if (showLineNumbers) {
                    val lineCount = content.lines().size
                    Column(
                        modifier = Modifier
                            .width(32.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = i.toString(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; dirty = true },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = if (wordWrap) Int.MAX_VALUE else 1,
                    placeholder = { Text("") }
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.fm_unsaved_changes)) },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onDismiss() }) {
                    Text(stringResource(R.string.fm_confirm_discard),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancel") } }
        )
    }
}
