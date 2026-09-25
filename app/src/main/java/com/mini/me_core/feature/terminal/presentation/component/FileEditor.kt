package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import kotlinx.coroutines.launch

/**
 * 内置容器文本编辑器：加载文件内容到 [OutlinedTextField]，保存时经 base64 写回容器。
 * 大文件（>1MB）由调用方在打开前警告。
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(filePath) {
        loading = true
        access.readFileText(filePath)
            .onSuccess { content = it }
            .onFailure { content = "(无法读取文件: ${it.message})" }
        loading = false
    }

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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = filePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                IconButton(
                    onClick = {
                        saving = true
                        scope.launch {
                            access.writeFileText(filePath, content)
                                .onSuccess {
                                    dirty = false
                                    snackbar.showSnackbar("已保存")
                                }
                                .onFailure { snackbar.showSnackbar("保存失败: ${it.message}") }
                            saving = false
                        }
                    },
                    enabled = dirty && !saving
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "保存",
                        tint = if (dirty) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
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
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; dirty = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.md),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                placeholder = { Text("") }
            )
        }
    }
}
