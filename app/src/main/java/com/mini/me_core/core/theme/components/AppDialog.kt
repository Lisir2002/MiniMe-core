package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

// ══════════════════════════════════════════════
// 通用弹窗基座（新增）
// ══════════════════════════════════════════════

/**
 * 确认弹窗（带顶部 48dp 图标）。
 *
 * 统一替代裸 AlertDialog 的确认场景。图标位于标题上方居中，危险操作时确认按钮走 error 色。
 *
 * @param title 标题
 * @param message 正文说明
 * @param confirmText 确认按钮文字
 * @param cancelText 取消按钮文字
 * @param icon 顶部图标（48dp）；null 表示不显示图标
 * @param iconTint 图标着色；null 时默认跟随 brandPrimary
 * @param isDestructive 是否危险操作（确认按钮 error 色）
 * @param onConfirm 确认回调
 * @param onDismiss 关闭回调（取消/外部 dismiss）
 */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String? = null,
    confirmText: String = "确定",
    cancelText: String = "取消",
    icon: ImageVector? = null,
    iconTint: Color? = null,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val confirmColor = if (isDestructive) colors.error else colors.brandPrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint ?: colors.brandPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
        },
        title = {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            if (message != null) {
                Text(
                    text = message,
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = cancelText, color = colors.textSecondary)
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(onClick = { onConfirm(); onDismiss() }) {
                    Text(
                        text = confirmText,
                        color = confirmColor,
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
 * 输入弹窗。
 *
 * 单个文本输入框，默认聚焦并弹出软键盘。可通过 [validator] 控制确认按钮是否可用。
 *
 * @param title 标题
 * @param initialValue 初始值
 * @param placeholder 占位提示
 * @param label 输入框标签
 * @param confirmText 确认按钮文字
 * @param cancelText 取消按钮文字
 * @param isDestructive 是否危险操作
 * @param validator 校验函数；返回 false 时确认按钮禁用；默认非空即可用
 * @param onConfirm 确认回调（回传输入文本，已 trim）
 * @param onDismiss 关闭回调
 */
@Composable
fun AppInputDialog(
    title: String,
    initialValue: String = "",
    placeholder: String? = null,
    label: String? = null,
    confirmText: String = "确定",
    cancelText: String = "取消",
    isDestructive: Boolean = false,
    validator: (String) -> Boolean = { it.isNotBlank() },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canConfirm = validator(text)
    val confirmColor = if (isDestructive) colors.error else colors.brandPrimary

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = cancelText, color = colors.textSecondary)
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(
                    onClick = { onConfirm(text.trim()); onDismiss() },
                    enabled = canConfirm,
                ) {
                    Text(
                        text = confirmText,
                        color = if (canConfirm) confirmColor else colors.textDisabled,
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
 * 加载弹窗。
 *
 * - [progress] 为 null：不确定进度圆环（居中 CircularProgressIndicator）。
 * - [progress] 在 0f..1f：线性进度条（LinearProgressIndicator）。
 * - [onDismiss] 为 null：不可取消（点击外部/返回键不关闭，且不显示取消按钮）。
 *
 * @param title 标题
 * @param message 正文说明
 * @param progress 进度；null=不确定圆环，0-1=线性进度
 * @param onDismiss 关闭回调；null 表示不可取消
 */
@Composable
fun AppLoadingDialog(
    title: String,
    message: String? = null,
    progress: Float? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
            ) {
                if (progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = colors.brandPrimary,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.brandPrimary,
                        trackColor = colors.surfaceSunken,
                    )
                }
                if (message != null) {
                    Text(
                        text = message,
                        fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            if (onDismiss != null) {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", color = colors.textSecondary)
                    }
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = colors.surfaceOverlay,
    )
}

/**
 * 结果弹窗（成功/失败大图标提示）。
 *
 * 单按钮结果反馈，点击确认即关闭。
 *
 * @param title 标题
 * @param message 正文说明
 * @param isSuccess true=成功大图标（success 色），false=失败大图标（error 色）
 * @param confirmText 确认按钮文字
 * @param onConfirm 确认回调
 */
@Composable
fun AppResultDialog(
    title: String,
    message: String? = null,
    isSuccess: Boolean = true,
    confirmText: String = "确定",
    onConfirm: () -> Unit = {},
) {
    val colors = LocalAppTheme.current.colors
    val icon = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error
    val tint = if (isSuccess) colors.success else colors.error
    AlertDialog(
        onDismissRequest = onConfirm,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            if (message != null) {
                Text(
                    text = message,
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = confirmText,
                        color = colors.brandPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        containerColor = colors.surfaceOverlay,
    )
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
