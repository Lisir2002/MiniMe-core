package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一对话框组件。
 *
 * 封装 Material3 AlertDialog，使用 Semantic Token 颜色。
 * 支持三种类型：default（确认按钮 primary）、destructive（确认按钮 error）、singleChoice（单选列表）。
 *
 * @param title 对话框标题
 * @param onDismiss 关闭对话框回调（点击取消或外部 dismiss）
 * @param type 对话框类型：Default / Destructive / SingleChoice
 * @param message 对话框内容文字（singleChoice 类型时忽略）
 * @param confirmText 确认按钮文字
 * @param cancelText 取消按钮文字
 * @param onConfirm 确认按钮回调
 * @param options 单选项列表（singleChoice 类型必填）
 * @param selectedIndex 当前选中索引（singleChoice 类型）
 * @param onOptionSelected 单选项选中回调（singleChoice 类型）
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    type: AppDialogType = AppDialogType.Default,
    message: String? = null,
    confirmText: String = "确定",
    cancelText: String = "取消",
    onConfirm: (() -> Unit)? = null,
    options: List<String>? = null,
    selectedIndex: Int? = null,
    onOptionSelected: ((Int) -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors

    val confirmButtonColor = when (type) {
        AppDialogType.Default -> colors.brandPrimary
        AppDialogType.Destructive -> colors.error
        AppDialogType.SingleChoice -> colors.brandPrimary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            when (type) {
                AppDialogType.SingleChoice -> {
                    val opts = options ?: emptyList()
                    Column {
                        opts.forEachIndexed { index, option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedIndex == index,
                                        onClick = { onOptionSelected?.invoke(index) }
                                    )
                                    .padding(vertical = PrimitiveSpacing.Sm),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedIndex == index,
                                    onClick = { onOptionSelected?.invoke(index) },
                                )
                                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                                Text(
                                    text = option,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (message != null) {
                        Text(
                            text = message,
                            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = cancelText,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(
                    onClick = {
                        onConfirm?.invoke()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = confirmText,
                        color = confirmButtonColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = colors.surfaceOverlay,
    )
}

/**
 * 对话框类型枚举。
 */
enum class AppDialogType {
    /** 默认：确认按钮 primary 色 */
    Default,
    /** 危险操作：确认按钮 error 色 */
    Destructive,
    /** 单选列表：内容区显示单选选项 */
    SingleChoice,
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：默认确认对话框。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppDialogDefaultPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppDialog(
            title = "删除确认",
            message = "确定要删除这个项目吗？此操作不可撤销。",
            confirmText = "删除",
            onDismiss = {},
        )
    }
}

/** Preview：危险操作对话框。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppDialogDestructivePreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppDialog(
            title = "清除所有数据",
            message = "此操作将删除所有本地数据，且无法恢复。",
            type = AppDialogType.Destructive,
            confirmText = "清除",
            onDismiss = {},
        )
    }
}

/** Preview：单选列表对话框。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 400)
@Composable
private fun AppDialogSingleChoicePreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppDialog(
            title = "选择主题",
            type = AppDialogType.SingleChoice,
            options = listOf("浅色模式", "深色模式", "跟随系统"),
            selectedIndex = 0,
            confirmText = "确定",
            onDismiss = {},
        )
    }
}
