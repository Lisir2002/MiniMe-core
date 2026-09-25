package com.mini.me_core.feature.settings.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.feature.settings.data.repository.SettingsExportManager
import kotlinx.coroutines.launch

/**
 * F5.2 设置导出/导入屏幕。
 *
 * 两个 Tab：导出（分类勾选 + 加密 + 导出方式）/ 导入（选文件 + 预览 + 冲突处理）。
 */
@Composable
fun SettingsExportImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsExportImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tabIndex by remember { mutableIntStateOf(0) }
    var importPassword by remember { mutableStateOf("") }

    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // SAF：保存文件（导出）
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val content = exportState.exportContent ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_export_save))
            }.onFailure {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_export_save_failed))
            }
        }
    }

    // SAF：选择文件（导入）
    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: ""
            }.onSuccess { raw ->
                viewModel.parseImport(raw, importPassword)
            }.onFailure {
                viewModel.let {
                    // trigger error state
                }
            }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.settings_export_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text(stringResource(R.string.settings_export_title)) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text(stringResource(R.string.settings_import_title)) }
                )
            }

            when (tabIndex) {
                0 -> ExportTab(
                    exportState = exportState,
                    onToggleCategory = viewModel::toggleCategory,
                    onSelectAll = viewModel::selectAllCategories,
                    onSelectNone = viewModel::selectNoCategories,
                    onSetEncrypt = viewModel::setEncrypt,
                    onSetPassword = viewModel::setPassword,
                    onExport = { method ->
                        viewModel.generateExport { content ->
                            when (method) {
                                ExportMethod.SHARE -> {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, content)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_export_share)))
                                }
                                ExportMethod.SAVE -> {
                                    val name = if (exportState.isEncrypted) "minime_settings.minimebak" else "minime_settings.json"
                                    saveFileLauncher.launch(name)
                                }
                                ExportMethod.CLIPBOARD -> {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("miniMe_settings", content))
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_export_clipboard)) }
                                }
                            }
                        }
                    },
                )
                1 -> ImportTab(
                    importState = importState,
                    password = importPassword,
                    onPasswordChange = { importPassword = it },
                    onPickFile = { pickFileLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                    onToggleConflictKeep = viewModel::toggleConflictKeep,
                    onKeepAll = viewModel::keepAll,
                    onOverwriteAll = viewModel::overwriteAll,
                    onApply = viewModel::applyImport,
                    onReset = viewModel::resetImport,
                    onParseWithPassword = { viewModel.parseImport(it, importPassword) },
                )
            }
        }
    }
}

enum class ExportMethod { SHARE, SAVE, CLIPBOARD }

@Composable
private fun ExportTab(
    exportState: SettingsExportImportViewModel.ExportUiState,
    onToggleCategory: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSetEncrypt: (Boolean) -> Unit,
    onSetPassword: (String) -> Unit,
    onExport: (ExportMethod) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 分类选择
        Text(
            text = stringResource(R.string.settings_export_select_categories),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.settings_export_select_all)) }
            TextButton(onClick = onSelectNone) { Text(stringResource(R.string.settings_export_select_none)) }
        }
        SettingsExportManager.CATEGORIES.forEach { cat ->
            val titleRes = when (cat.id) {
                "appearance" -> R.string.settings_category_appearance
                "ai_provider" -> R.string.settings_category_ai_provider
                "terminal" -> R.string.settings_category_terminal
                "norm_flow" -> R.string.settings_category_norm_flow
                "system" -> R.string.settings_category_system
                else -> R.string.settings_category_system
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = cat.id in exportState.selectedCategories,
                    onCheckedChange = { onToggleCategory(cat.id) },
                )
                Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider()

        // 加密选项
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_export_encrypt), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = exportState.encrypt, onCheckedChange = onSetEncrypt)
        }
        if (exportState.encrypt) {
            OutlinedTextField(
                value = exportState.password,
                onValueChange = onSetPassword,
                label = { Text(stringResource(R.string.settings_export_password_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        HorizontalDivider()

        // 导出方式
        Text(
            text = stringResource(R.string.settings_export_method),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onExport(ExportMethod.SHARE) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_export_share)) }
            OutlinedButton(
                onClick = { onExport(ExportMethod.SAVE) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_export_save)) }
            OutlinedButton(
                onClick = { onExport(ExportMethod.CLIPBOARD) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_export_clipboard)) }
        }
    }
}

@Composable
private fun ImportTab(
    importState: SettingsExportImportViewModel.ImportUiState,
    password: String,
    onPasswordChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onToggleConflictKeep: (String, Boolean) -> Unit,
    onKeepAll: () -> Unit,
    onOverwriteAll: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onParseWithPassword: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (importState) {
            is SettingsExportImportViewModel.ImportUiState.Idle -> {
                Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_import_pick_file))
                }
            }

            is SettingsExportImportViewModel.ImportUiState.Parsed -> {
                if (importState.needsPassword) {
                    Text(
                        stringResource(R.string.settings_import_password_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.settings_export_password_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    // Re-pick with password
                    Button(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_import_pick_file)) }
                    return@Column
                }

                // 预览
                Text(
                    stringResource(R.string.settings_import_items_count, importState.itemCount, importState.categoryCount),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.settings_import_preview), style = MaterialTheme.typography.bodyMedium)

                if (importState.conflicts.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.settings_import_conflict_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.settings_import_conflict_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOverwriteAll) {
                            Text(stringResource(R.string.settings_import_overwrite_all))
                        }
                        TextButton(onClick = onKeepAll) {
                            Text(stringResource(R.string.settings_import_keep_all))
                        }
                    }
                    importState.conflicts.forEach { conflict ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conflict.key, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${conflict.current?.display() ?: "--"} → ${conflict.incoming.display()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                onToggleConflictKeep(conflict.key, conflict.key !in importState.keepKeys)
                            }) {
                                Text(
                                    if (conflict.key in importState.keepKeys)
                                        stringResource(R.string.settings_import_keep)
                                    else
                                        stringResource(R.string.settings_import_overwrite)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Button(
                    onClick = onApply,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_import_apply)) }
            }

            is SettingsExportImportViewModel.ImportUiState.Result -> {
                Text(
                    stringResource(R.string.settings_import_result_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        R.string.settings_import_result_detail,
                        importState.applied,
                        importState.skipped,
                        importState.failed,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_import_pick_file))
                }
            }

            is SettingsExportImportViewModel.ImportUiState.Error -> {
                Text(
                    stringResource(R.string.settings_import_invalid_file),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_back))
                }
            }
        }
    }
}
