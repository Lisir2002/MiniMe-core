package com.mini.logs.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.logs.data.DefaultFileMode
import com.mini.logs.data.FontSize
import com.mini.logs.data.LogDirResolver
import com.mini.logs.data.SettingsStore
import com.mini.logs.data.ThemeMode
import com.mini.logs.data.ViewMode
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppDivider
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

private const val LOG_DIR_PATH = "/storage/emulated/0/Documents/MiniMe-core/logs/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val clipboard = LocalClipboardManager.current

    // 外观状态
    var themeMode by remember { mutableStateOf(store.themeMode) }
    var viewMode by remember { mutableStateOf(store.viewMode) }
    var fontSize by remember { mutableStateOf(store.fontSize) }
    var useMonospace by remember { mutableStateOf(store.useMonospace) }
    var showMilliseconds by remember { mutableStateOf(store.showMilliseconds) }

    // 行为状态
    var defaultFileMode by remember { mutableStateOf(store.defaultFileMode) }
    var autoTail by remember { mutableStateOf(store.autoTail) }
    var logSource by remember { mutableStateOf(store.logSource) }
    var contextLines by remember { mutableIntStateOf(store.contextLines) }
    var maxLoadLines by remember { mutableIntStateOf(store.maxLoadLines) }

    // 弹出层控制
    var sheetTitle by remember { mutableStateOf("") }
    var sheetOptions by remember { mutableStateOf<List<OptionItem>>(emptyList()) }
    var sheetVisible by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    fun openSheet(title: String, options: List<OptionItem>) {
        sheetTitle = title
        sheetOptions = options
        sheetVisible = true
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PrimitiveSpacing.Xl),
    ) {
        // ── 外观 ──
        AppSectionHeader(title = "外观")
        AppCard(modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg)) {
            ValueSettingRow(
                icon = Icons.Rounded.Palette,
                title = "主题",
                valueText = themeMode.label(),
                onClick = {
                    openSheet("主题", ThemeMode.entries.map { mode ->
                        OptionItem(mode.label(), mode == themeMode) {
                            themeMode = mode; store.themeMode = mode
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.ViewAgenda,
                title = "视图模式",
                valueText = viewMode.label(),
                onClick = {
                    openSheet("视图模式", ViewMode.entries.map { mode ->
                        OptionItem(mode.label(), mode == viewMode) {
                            viewMode = mode; store.viewMode = mode
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.TextSnippet,
                title = "字体大小",
                valueText = fontSize.label(),
                onClick = {
                    openSheet("字体大小", FontSize.entries.map { size ->
                        OptionItem(size.label(), size == fontSize) {
                            fontSize = size; store.fontSize = size
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.FontDownload,
                title = "字体",
                valueText = if (useMonospace) "Monospace" else "系统默认",
                onClick = {
                    openSheet("字体", listOf(
                        OptionItem("系统默认", !useMonospace) {
                            useMonospace = false; store.useMonospace = false
                        },
                        OptionItem("Monospace", useMonospace) {
                            useMonospace = true; store.useMonospace = true
                        },
                    ))
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Schedule,
                title = "时间格式",
                valueText = if (showMilliseconds) "显示毫秒" else "24小时制",
                onClick = {
                    openSheet("时间格式", listOf(
                        OptionItem("24小时制", !showMilliseconds && true) {
                            showMilliseconds = false; store.showMilliseconds = false
                        },
                        OptionItem("12小时制", false) {
                            // 12小时制暂无独立持久化位，先按 24h 无毫秒处理
                            showMilliseconds = false; store.showMilliseconds = false
                        },
                        OptionItem("显示毫秒", showMilliseconds) {
                            showMilliseconds = true; store.showMilliseconds = true
                        },
                    ))
                },
            )
        }

        // ── 行为 ──
        Spacer(Modifier.height(PrimitiveSpacing.Lg))
        AppSectionHeader(title = "行为")
        AppCard(modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg)) {
            ValueSettingRow(
                icon = Icons.Rounded.FileOpen,
                title = "默认文件",
                valueText = defaultFileMode.label(),
                onClick = {
                    openSheet("默认文件", DefaultFileMode.entries.map { mode ->
                        OptionItem(mode.label(), mode == defaultFileMode) {
                            defaultFileMode = mode; store.defaultFileMode = mode
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            SwitchSettingRow(
                icon = Icons.Rounded.Archive,
                title = "自动尾随",
                checked = autoTail,
                onCheckedChange = { autoTail = it; store.autoTail = it },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Description,
                title = "日志来源",
                valueText = logSource.label(),
                onClick = {
                    openSheet("日志来源", LogDirResolver.LogSource.entries.map { src ->
                        OptionItem(src.label(), src == logSource) {
                            logSource = src
                            store.logSource = src
                            toast("已切换到${src.label()}，返回日志页自动刷新")
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Tag,
                title = "上下文行数",
                valueText = "$contextLines",
                onClick = {
                    openSheet("上下文行数", listOf(5, 10, 20, 50).map { n ->
                        OptionItem("$n 行", n == contextLines) {
                            contextLines = n; store.contextLines = n
                        }
                    })
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Language,
                title = "日志加载上限",
                valueText = "$maxLoadLines 行",
                onClick = {
                    openSheet("日志加载上限", listOf(5000, 10000, 50000).map { n ->
                        OptionItem("${n / 1000}K 行", n == maxLoadLines) {
                            maxLoadLines = n; store.maxLoadLines = n
                        }
                    })
                },
            )
        }

        // ── 数据管理 ──
        Spacer(Modifier.height(PrimitiveSpacing.Lg))
        AppSectionHeader(title = "数据管理")
        AppCard(modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg)) {
            DangerRow(
                icon = Icons.Rounded.CleaningServices,
                title = "清理 7 天前日志",
                onClick = { showClearLogsDialog = true },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Description,
                title = "导出所有日志备份",
                valueText = null,
                onClick = { toast("功能开发中") },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.SettingsBackupRestore,
                title = "导出/导入设置",
                valueText = null,
                onClick = { toast("功能开发中") },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Brush,
                title = "清理缓存",
                valueText = null,
                onClick = { showClearCacheDialog = true },
            )
        }

        // ── 关于 ──
        Spacer(Modifier.height(PrimitiveSpacing.Lg))
        AppSectionHeader(title = "关于")
        AppCard(modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg)) {
            ValueSettingRow(
                icon = Icons.Rounded.Info,
                title = "版本",
                valueText = "1.0.0",
                onClick = null,
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.FolderOpen,
                title = "日志目录路径",
                valueText = null,
                onClick = {
                    clipboard.setText(AnnotatedString(LOG_DIR_PATH))
                    toast("已复制到剪贴板")
                },
            )
            AppDivider(horizontalPadding = 68.dp)
            ValueSettingRow(
                icon = Icons.Rounded.Info,
                title = "开源声明",
                valueText = null,
                onClick = { toast("功能开发中") },
            )
        }
    }

    // ── 选项 BottomSheet ──
    if (sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = PrimitiveSpacing.Xl)) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = PrimitiveSpacing.Lg,
                        vertical = PrimitiveSpacing.Sm,
                    ),
                )
                sheetOptions.forEach { option ->
                    OptionRow(
                        label = option.label,
                        selected = option.selected,
                        onClick = {
                            sheetVisible = false
                            option.onSelect()
                        },
                    )
                }
            }
        }
    }

    // ── 清理日志确认 ──
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("清理 7 天前日志？") },
            text = { Text("附属应用为只读模式，不会删除主应用日志文件。此操作将在后续版本接入清理能力。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearLogsDialog = false
                    toast("功能开发中")
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text("取消") }
            },
        )
    }

    // ── 清理缓存确认 ──
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存？") },
            text = { Text("将清除所有书签与高亮标记，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    store.bookmarks = emptyMap()
                    store.highlights = emptyMap()
                    toast("缓存已清理")
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") }
            },
        )
    }
}

// ── 显示当前值的设置行 ──
@Composable
private fun ValueSettingRow(
    icon: ImageVector,
    title: String,
    valueText: String?,
    onClick: (() -> Unit)?,
) {
    val colors = LocalAppTheme.current.colors
    AppListItem(
        icon = icon,
        title = title,
        onClick = onClick,
        showDivider = false,
        trailing = {
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

// ── 红色危险操作行（自定义标题颜色） ──
@Composable
private fun DangerRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.error)
        }
        Spacer(Modifier.size(PrimitiveSpacing.Md))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.error,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Switch 行 ──
@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppListItem(
        icon = icon,
        title = title,
        onClick = { onCheckedChange(!checked) },
        showDivider = false,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

// ── BottomSheet 选项行 ──
@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.brandPrimary else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = colors.brandPrimary,
            )
        }
    }
}

private data class OptionItem(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

// ── 枚举中文标签 ──
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.AMOLED -> "纯黑"
}

private fun ViewMode.label(): String = when (this) {
    ViewMode.COMPACT -> "紧凑"
    ViewMode.COMFORTABLE -> "舒适"
}

private fun FontSize.label(): String = when (this) {
    FontSize.SMALL -> "小"
    FontSize.MEDIUM -> "中"
    FontSize.LARGE -> "大"
    FontSize.XLARGE -> "特大"
}

private fun DefaultFileMode.label(): String = when (this) {
    DefaultFileMode.TODAY -> "今天"
    DefaultFileMode.LAST -> "记住上次"
    DefaultFileMode.ASK -> "每次询问"
}

private fun LogDirResolver.LogSource.label(): String = when (this) {
    LogDirResolver.LogSource.AUTO -> "自动检测"
    LogDirResolver.LogSource.EXTERNAL_PUBLIC -> "外部存储"
    LogDirResolver.LogSource.APP_PRIVATE -> "应用私有"
}
