package com.mini.me_core.feature.editor.snippets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.editor.snippets.CustomSnippetStore
import com.mini.me_core.feature.editor.snippets.SnippetRepository
import com.mini.me_core.feature.editor.snippets.SnippetSettings
import com.mini.me_core.feature.editor.snippets.model.Snippet

/**
 * 代码片段管理设置页。
 *
 *  - 顶部：片段补全开关
 *  - 自定义片段列表（增删改、启用/禁用）+ 导入/导出
 *  - 内置片段按语言分组（只读展示）
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SnippetSettingsScreen(onNavigateBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val exportCopiedText = stringResource(R.string.snippet_export_copied)

    var completionEnabled by remember { mutableStateOf(SnippetSettings.completionEnabled) }
    var customList by remember { mutableStateOf(CustomSnippetStore.all()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        customList = CustomSnippetStore.all()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_snippets_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.snippet_add))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 补全开关
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.snippet_completion_enabled), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.snippet_completion_enabled_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = completionEnabled,
                        onCheckedChange = {
                            completionEnabled = it
                            SnippetSettings.completionEnabled = it
                        },
                    )
                }
            }

            // 导入/导出
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            val exported = CustomSnippetStore.exportToJson()
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(exported))
                            snackbar = exportCopiedText
                        }
                    }) {
                        Text(stringResource(R.string.snippet_export))
                    }
                    OutlinedButton(onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        runCatching {
                            val n = CustomSnippetStore.importFromJson(text)
                            refresh()
                            snackbar = LocalContext.current.getString(R.string.snippet_import_success, n)
                        }.onFailure {
                            snackbar = LocalContext.current.getString(R.string.snippet_import_error, it.message ?: "")
                        }
                    }) {
                        Text(stringResource(R.string.snippet_import))
                    }
                }
            }

            // 自定义片段
            item {
                Text(
                    stringResource(R.string.snippet_section_custom) +
                        "（" + stringResource(R.string.snippet_count_prefix, customList.size) + "）",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (customList.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.snippet_empty_custom),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(customList) { snippet ->
                SnippetCard(
                    snippet = snippet,
                    onToggle = {
                        CustomSnippetStore.setEnabled(snippet.id, !snippet.enabled); refresh()
                    },
                    onEdit = { editing = snippet; showEditor = true },
                    onDelete = {
                        CustomSnippetStore.delete(snippet.id); refresh()
                    },
                )
            }

            // 内置片段
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.snippet_section_builtin),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            SnippetRepository.builtinByLanguage().forEach { (lang, list) ->
                item(key = "lang-$lang") {
                    Text(
                        "$lang · ${list.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.id }) { snippet ->
                    Text(
                        "${snippet.prefix} — ${snippet.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
        }
    }

    // 编辑对话框
    if (showEditor) {
        SnippetEditDialog(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { name, prefix, language, description, body ->
                runCatching {
                    val cur = editing
                    if (cur == null) {
                        CustomSnippetStore.add(name, prefix, language, description, body)
                    } else {
                        CustomSnippetStore.update(cur.id, name, prefix, language, description, body)
                    }
                    refresh()
                    showEditor = false
                }.onFailure {
                    snackbar = it.message
                }
            },
        )
    }

    // 轻提示
    snackbar?.let { msg ->
        AlertDialog(
            onDismissRequest = { snackbar = null },
            confirmButton = { TextButton(onClick = { snackbar = null }) { Text(stringResource(R.string.snippet_cancel)) } },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun SnippetCard(
    snippet: Snippet,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(snippet.name, fontWeight = FontWeight.Medium)
                Text(
                    "${snippet.language} · ${snippet.prefix}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = snippet.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.snippet_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.snippet_delete))
            }
        }
    }
}
