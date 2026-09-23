package com.mini.logs.ui.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import java.io.File

/**
 * 文件选择 BottomSheet：快捷范围 + 文件列表勾选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectorSheet(
    logFiles: List<File>,
    selectedFiles: Set<String>,
    onSelectQuickRange: (Int) -> Unit,
    onToggleFile: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

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
                    text = "选择日志文件",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary,
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // 快捷范围
            AppSectionHeader(title = "快捷范围")
            Row(horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
                listOf("今天" to 1, "近3天" to 3, "近7天" to 7, "全部" to 0).forEach { (label, days) ->
                    AppChip(
                        text = label,
                        variant = AppChipVariant.Default,
                        chipColor = AppChipColor.Primary,
                        modifier = Modifier.clickable { onSelectQuickRange(days) },
                    )
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            HorizontalDivider(color = colors.borderMuted)
            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // 文件列表
            AppSectionHeader(title = "文件列表 (${logFiles.size})")
            logFiles.forEach { file ->
                val isSelected = file.name in selectedFiles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = PrimitiveSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleFile(file.name, it) },
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = FormatUtils.formatFileSize(file.length()),
                            color = colors.textTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
            ) {
                AppButton(
                    text = "全选",
                    onClick = onSelectAll,
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "重置",
                    onClick = onReset,
                    variant = AppButtonVariant.Outlined,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "完成",
                    onClick = onDismiss,
                    size = AppButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
