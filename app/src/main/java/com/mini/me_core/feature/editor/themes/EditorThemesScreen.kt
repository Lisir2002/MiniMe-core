package com.mini.me_core.feature.editor.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.R
import com.mini.me_core.feature.editor.textmate.TextMateManager

/**
 * 编辑器主题管理器页面。
 *
 * 功能：主题网格（缩略图+名称）、分类筛选、搜索、跟随系统开关、导入、
 * 点击全屏预览并应用、自定义主题编辑/删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorThemesScreen(
    onNavigateBack: () -> Unit,
    onOpenEditor: (baseThemeName: String?) -> Unit,
    onPickImportFile: () -> Unit = {},
    viewModel: EditorThemesViewModel = hiltViewModel(),
) {
    val themes by viewModel.themes.collectAsState()
    val category by viewModel.category.collectAsState()
    val query by viewModel.query.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val current by viewModel.currentTheme.collectAsState()
    val toast by viewModel.toast.collectAsState()

    var previewTheme by remember { mutableStateOf<ThemeMetadata?>(null) }
    var confirmDelete by remember { mutableStateOf<ThemeMetadata?>(null) }

    toast?.let { t ->
        LaunchedEffect(t) {
            // 简单 Toast 提示（通过 Dialog 样式展示一次）
            viewModel.toastShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        // 搜索框
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.editor_theme_search_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        // 分类筛选
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(null, stringResource(R.string.editor_theme_category_all), category) { viewModel.setCategory(it) }
            CategoryChip(ThemeCategory.DARK, stringResource(R.string.editor_theme_category_dark), category) { viewModel.setCategory(it) }
            CategoryChip(ThemeCategory.LIGHT, stringResource(R.string.editor_theme_category_light), category) { viewModel.setCategory(it) }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip(ThemeCategory.HIGH_CONTRAST, stringResource(R.string.editor_theme_category_high_contrast), category) { viewModel.setCategory(it) }
            CategoryChip(ThemeCategory.COLORBLIND, stringResource(R.string.editor_theme_category_colorblind), category) { viewModel.setCategory(it) }
            CategoryChip(ThemeCategory.CUSTOM, stringResource(R.string.editor_theme_category_custom), category) { viewModel.setCategory(it) }
        }

        Spacer(Modifier.height(12.dp))

        // 跟随系统开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_theme_follow_system), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.editor_theme_follow_system_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = prefs.followSystem, onCheckedChange = { viewModel.setFollowSystem(it) })
        }

        Spacer(Modifier.height(12.dp))

        // 导入按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPickImportFile) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.editor_theme_import))
            }
            TextButton(onClick = { onOpenEditor(null) }) {
                Text(stringResource(R.string.editor_theme_new))
            }
        }

        Spacer(Modifier.height(8.dp))

        // 主题网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(themes, key = { it.name }) { theme ->
                ThemeCard(
                    theme = theme,
                    isActive = theme.name == current,
                    onClick = { previewTheme = theme },
                    onEdit = if (theme.isCustom) ({ onOpenEditor(theme.name) }) else null,
                    onDelete = if (theme.isCustom) ({ confirmDelete = theme }) else null,
                )
            }
        }
    }

    // 全屏预览 Dialog
    previewTheme?.let { meta ->
        ThemePreviewDialog(
            meta = meta,
            onDismiss = { previewTheme = null },
            onApply = {
                viewModel.applyTheme(meta.name)
                previewTheme = null
            },
        )
    }

    // 删除确认
    confirmDelete?.let { meta ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.editor_theme_delete_confirm_title)) },
            text = { Text(stringResource(R.string.editor_theme_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTheme(meta.name)
                    confirmDelete = null
                }) { Text(stringResource(R.string.editor_theme_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CategoryChip(
    value: ThemeCategory?,
    label: String,
    selected: ThemeCategory?,
    onSelect: (ThemeCategory?) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label, fontSize = 12.sp) },
    )
}

/** 单个主题卡片：缩略图 + 名称 + 使用中角标 + 编辑/删除。 */
@Composable
private fun ThemeCard(
    theme: ThemeMetadata,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val preview = remember(theme.name) { TextMateManager.getPreviewColors(theme.name) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            ThemeThumbnail(preview, modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(theme.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (isActive) {
                        Text(
                            stringResource(R.string.editor_theme_applied),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.editor_theme_edit), modifier = Modifier.size(16.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.editor_theme_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** 用主题色板绘制的迷你代码缩略图。 */
@Composable
fun ThemeThumbnail(preview: ThemePreviewColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(parseColor(preview.background))
            .padding(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ThumbLine(parseColor(preview.keyword), 0.55f)
            ThumbLine(parseColor(preview.string), 0.8f)
            ThumbLine(parseColor(preview.function), 0.65f)
            ThumbLine(parseColor(preview.comment), 0.45f)
            ThumbLine(parseColor(preview.number), 0.3f)
        }
    }
}

@Composable
private fun ThumbLine(color: Color, widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(6.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
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

/** 全屏预览 Dialog：展示多种语言示例 + 应用按钮。 */
@Composable
private fun ThemePreviewDialog(
    meta: ThemeMetadata,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val preview = remember(meta.name) { TextMateManager.getPreviewColors(meta.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meta.displayName) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(parseColor(preview.background))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("def hello():", color = parseColor(preview.keyword), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("    \"return\" + 42", color = parseColor(preview.string), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("    # comment", color = parseColor(preview.comment), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("    return foo(42)", color = parseColor(preview.function), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(meta.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.editor_theme_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
