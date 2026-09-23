package com.mini.logs.ui.logs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.LogEntry
import com.mini.logs.export.LogExporter
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 导出日志 BottomSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    currentEntries: List<LogEntry>,
    allEntries: List<LogEntry>,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val context = LocalContext.current

    var scope by remember { mutableStateOf(LogExporter.ExportScope.CURRENT_VIEW) }
    var format by remember { mutableStateOf(LogExporter.ExportFormat.TXT) }
    var includeDeviceInfo by remember { mutableStateOf(true) }
    var includeStats by remember { mutableStateOf(true) }
    var includeMarks by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Lg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = PrimitiveSpacing.Xxl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "导出日志",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary,
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // 导出范围
            AppSectionHeader(title = "导出范围")
            LogExporter.ExportScope.entries.forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PrimitiveSpacing.Xxs)
                        .clickable { scope = s },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = scope == s,
                        onClick = { scope = s },
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    val label = when (s) {
                        LogExporter.ExportScope.CURRENT_VIEW -> "当前显示 (${currentEntries.size} 行)"
                        LogExporter.ExportScope.CURRENT_FILE -> "当前文件全部 (${allEntries.size} 行)"
                        LogExporter.ExportScope.ALL_FILES -> "所有文件"
                    }
                    Text(text = label, color = colors.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))
            HorizontalDivider(color = colors.borderMuted)

            // 导出格式
            AppSectionHeader(title = "导出格式")
            Row(horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
                LogExporter.ExportFormat.entries.forEach { f ->
                    AppChip(
                        text = when (f) {
                            LogExporter.ExportFormat.TXT -> "TXT"
                            LogExporter.ExportFormat.MARKDOWN -> "Markdown"
                        },
                        variant = if (format == f) AppChipVariant.Filled else AppChipVariant.Outlined,
                        chipColor = AppChipColor.Primary,
                        modifier = Modifier.clickable { format = f },
                    )
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))
            HorizontalDivider(color = colors.borderMuted)

            // 包含内容
            AppSectionHeader(title = "包含内容")
            listOf(
                "设备信息" to includeDeviceInfo to { v: Boolean -> includeDeviceInfo = v },
                "统计摘要" to includeStats to { v: Boolean -> includeStats = v },
                "标记/书签" to includeMarks to { v: Boolean -> includeMarks = v },
            ).forEach { (pair, onChange) ->
                val (label, checked) = pair
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PrimitiveSpacing.Xxs)
                        .clickable { onChange(!checked) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = onChange)
                    Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    Text(text = label, color = colors.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            HorizontalDivider(color = colors.borderMuted)

            // 操作按钮
            Spacer(Modifier.height(PrimitiveSpacing.Md))
            val entriesToExport = when (scope) {
                LogExporter.ExportScope.CURRENT_VIEW -> currentEntries
                LogExporter.ExportScope.CURRENT_FILE -> allEntries
                LogExporter.ExportScope.ALL_FILES -> allEntries
            }
            val options = LogExporter.ExportOptions(
                format = format,
                includeDeviceInfo = includeDeviceInfo,
                includeStats = includeStats,
                includeMarks = includeMarks,
                scope = scope,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
            ) {
                AppButton(
                    text = "导出",
                    onClick = {
                        val file = LogExporter.export(context, entriesToExport, options)
                        Toast.makeText(
                            context,
                            if (file != null) "已导出到 Downloads/${file.name}" else "导出失败",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (file != null) onDismiss()
                    },
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "分享",
                    onClick = {
                        val file = LogExporter.export(context, entriesToExport, options)
                        if (file != null) {
                            LogExporter.share(context, file)
                            onDismiss()
                        } else {
                            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
