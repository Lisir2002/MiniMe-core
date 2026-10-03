package com.mini.me_core.feature.editor.themes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.R
import com.mini.me_core.feature.editor.themes.ThemePreviewColors
import kotlinx.coroutines.launch

/**
 * 可视化主题编辑器页面。
 *
 * 选择基础主题 → 调整 15 项颜色 → 实时预览 → 保存/导出/重置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    baseThemeName: String?,
    onNavigateBack: () -> Unit,
    viewModel: ThemeEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(baseThemeName) { viewModel.loadBase(baseThemeName) }

    val snapshot by viewModel.snapshot.collectAsState()
    val overrides by viewModel.overrides.collectAsState()
    val baseName by viewModel.baseThemeName.collectAsState()
    val scope = rememberCoroutineScope()

    var showBasePicker by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingColorField by remember { mutableStateOf<EditorColorField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 基础主题选择
        Text(stringResource(R.string.editor_theme_pick_base), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showBasePicker = true }) {
            Text(baseName ?: "")
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = showBasePicker, onDismissRequest = { showBasePicker = false }) {
            viewModel.baseThemes().forEach { meta ->
                DropdownMenuItem(
                    text = { Text(meta.displayName) },
                    onClick = {
                        viewModel.loadBase(meta.name)
                        showBasePicker = false
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 实时预览
        Text(stringResource(R.string.editor_theme_preview), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        val preview = remember(snapshot, overrides) { viewModel.previewColors() }
        ThemeEditPreview(preview)

        Spacer(Modifier.height(16.dp))

        // 编辑器颜色
        Text(stringResource(R.string.editor_theme_group_editor), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        EditorColorField.EDITOR_FIELDS.forEach { field ->
            ColorEditRow(
                field = field,
                currentHex = snapshot?.values?.get(field.key) ?: "#000000",
                overrideHex = overrides[field.key],
                onPick = { pendingColorField = field },
                onReset = { viewModel.resetField(field.key) },
            )
        }

        Spacer(Modifier.height(16.dp))

        // 语法颜色
        Text(stringResource(R.string.editor_theme_group_syntax), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        EditorColorField.SYNTAX_FIELDS.forEach { field ->
            ColorEditRow(
                field = field,
                currentHex = snapshot?.values?.get(field.key) ?: "#000000",
                overrideHex = overrides[field.key],
                onPick = { pendingColorField = field },
                onReset = { viewModel.resetField(field.key) },
            )
        }

        Spacer(Modifier.height(24.dp))

        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.resetAll() }) {
                Text(stringResource(R.string.editor_theme_reset))
            }
            OutlinedButton(onClick = {
                scope.launch { runCatching { viewModel.export("my-theme") } }
            }) {
                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.editor_theme_export))
            }
            OutlinedButton(onClick = { showNameDialog = true }) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.editor_theme_save))
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    // 颜色选择 Dialog
    pendingColorField?.let { field ->
        ColorPickerDialog(
            title = stringResource(field.labelRes()),
            initialHex = overrides[field.key] ?: snapshot?.values?.get(field.key) ?: "#000000",
            onDismiss = { pendingColorField = null },
            onConfirm = { hex ->
                viewModel.setColor(field.key, hex)
                pendingColorField = null
            },
        )
    }

    // 命名保存 Dialog
    if (showNameDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.editor_theme_save)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_theme_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.save(name.ifBlank { "custom-theme" }, name.ifBlank { "Custom Theme" })
                        onNavigateBack()
                    }
                }) { Text(stringResource(R.string.editor_theme_save)) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** 颜色字段 → string resource 映射。 */
private fun EditorColorField.labelRes(): Int = when (key) {
    "editor_background" -> R.string.editor_color_editor_background
    "editor_foreground" -> R.string.editor_color_editor_foreground
    "line_number" -> R.string.editor_color_line_number
    "line_highlight" -> R.string.editor_color_line_highlight
    "selection" -> R.string.editor_color_selection
    "cursor" -> R.string.editor_color_cursor
    "keyword" -> R.string.editor_color_keyword
    "string" -> R.string.editor_color_string
    "comment" -> R.string.editor_color_comment
    "number" -> R.string.editor_color_number
    "function" -> R.string.editor_color_function
    "type" -> R.string.editor_color_type
    "variable" -> R.string.editor_color_variable
    "operator" -> R.string.editor_color_operator
    "constant" -> R.string.editor_color_constant
    else -> R.string.editor_theme_group_syntax
}

@Composable
private fun ColorEditRow(
    field: EditorColorField,
    currentHex: String,
    overrideHex: String?,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(parseColor(currentHex))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { onPick() },
        )
        Text(
            stringResource(field.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (overrideHex != null) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.editor_theme_reset), fontSize = 12.sp) }
        }
    }
}

/** 实时预览：用当前颜色绘制模拟代码。 */
@Composable
private fun ThemeEditPreview(preview: ThemePreviewColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(parseColor(preview.background))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("def greet(name):", color = parseColor(preview.keyword), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("    \"Hello, \" + name", color = parseColor(preview.string), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("    # returns greeting", color = parseColor(preview.comment), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("    return greet(name)", color = parseColor(preview.function), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("greet(42)", color = parseColor(preview.number), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** 颜色选择 Dialog：预设色板。 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val swatches = listOf(
        "#000000", "#1E1E1E", "#2D2D2D", "#424242", "#616161", "#9E9E9E", "#E0E0E0", "#FFFFFF",
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
    )
    var selected by remember { mutableStateOf(initialHex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseColor(selected)),
                )
                Spacer(Modifier.height(12.dp))
                swatches.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseColor(hex))
                                    .clickable { selected = hex },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun parseColor(hex: String): Color = runCatching {
    val v = hex.removePrefix("#")
    when (v.length) {
        6 -> Color(("FF$v".toLong(16)))
        8 -> Color(v.toLong(16))
        else -> Color.Gray
    }
}.getOrDefault(Color.Gray)
