package com.mini.me_core.feature.git.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 危险操作的严重级别。
 */
enum class DangerLevel {
    /** 普通提示级，无破坏性，仅做信息告知。 */
    INFO,

    /** 警告级：有副作用但可恢复，使用主题 tertiary 色强调。 */
    WARNING,

    /** 危险级：操作不可逆，使用 error 红色主题。 */
    DANGER,

    /** 极危级：不可逆且影响范围大，使用 errorContainer 醒目背景 + 大图标。 */
    EXTREME,
}

/**
 * 通用危险操作确认对话框。
 *
 * 根据 [dangerLevel] 自动调整图标、标题与确认按钮颜色；[DangerLevel.EXTREME] 会使用
 * errorContainer 作为对话框背景并展示更醒目的警告图标。当 [requireInput] 不为 null 时，
 * 对话框内会出现输入框，只有输入内容与 [requireInput] 完全一致时确认按钮才可用，
 * 用于强制用户复述分支名 / "FORCE" / "RESET" 等关键指令。
 *
 * @param title 对话框标题
 * @param message 正文说明
 * @param confirmText 确认按钮文字（如「删除」「强制推送」）
 * @param dangerLevel 危险级别，决定图标与配色
 * @param requireInput 需要用户精确输入的确认文本；null 表示无需输入校验
 * @param inputHint 输入框提示文案
 * @param details 额外详情（如将被删除的文件列表），以等宽字体区块展示
 * @param onConfirm 确认回调（此时按钮已通过输入校验）
 * @param onDismiss 关闭回调（取消或外部 dismiss）
 */
@Composable
internal fun DangerousActionDialog(
    title: String,
    message: String,
    confirmText: String,
    dangerLevel: DangerLevel = DangerLevel.WARNING,
    requireInput: String? = null,
    inputHint: String? = null,
    details: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember(requireInput) { mutableStateOf("") }
    val needsInput = requireInput != null
    val canConfirm = !needsInput || input.trim() == requireInput

    val accentColor = when (dangerLevel) {
        DangerLevel.INFO -> MaterialTheme.colorScheme.primary
        DangerLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        DangerLevel.DANGER -> MaterialTheme.colorScheme.error
        DangerLevel.EXTREME -> MaterialTheme.colorScheme.error
    }
    val icon: ImageVector = when (dangerLevel) {
        DangerLevel.INFO -> Icons.Rounded.Info
        DangerLevel.WARNING -> Icons.Rounded.Warning
        DangerLevel.DANGER -> Icons.Rounded.Error
        DangerLevel.EXTREME -> Icons.Rounded.Dangerous
    }
    val extreme = dangerLevel == DangerLevel.EXTREME
    val dialogContainerColor = if (extreme) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val titleColor = if (extreme) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val messageColor = if (extreme) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(if (extreme) 56.dp else 40.dp),
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageColor,
                )

                if (details != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(LocalCornerRadius.current.sm),
                    ) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }

                if (needsInput) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = inputHint?.let { { Text(it) } },
                        placeholder = requireInput?.let { { Text(it) } },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                TextButton(
                    onClick = {
                        if (canConfirm) {
                            onConfirm()
                            onDismiss()
                        }
                    },
                    enabled = canConfirm,
                ) {
                    Text(
                        text = confirmText,
                        color = if (canConfirm) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = dialogContainerColor,
    )
}
