package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/** 对话框内容区最大高度占屏高比例：超出则内部滚动，保证标题与按钮始终可见。 */
private const val DIALOG_CONTENT_MAX_HEIGHT_FRACTION = 0.7f

/**
 * 统一对话框组件。
 *
 * 封装 Material3 AlertDialog，使用 Semantic Token 颜色。
 * 支持三种类型：default（确认按钮 primary）、destructive（确认按钮 error）、singleChoice（单选列表）。
 *
 * v2 能力（均有默认值，既有调用点零改动）：
 * - [content] 自定义内容插槽（渲染在 message / 单选列表下方）
 * - [confirmEnabled] 确认按钮可用性（表单校验场景）
 * - [isConfirming] 确认执行中：按钮转圈 + 禁用 + 阻断外部 dismiss
 * - [dismissOnClickOutside] 是否允许遮罩/返回键关闭
 * - [dismissOnConfirm] 点击确认后是否自动关闭
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
 * @param content 自定义内容插槽，渲染在 message / 单选列表下方；null 时不占位
 * @param confirmEnabled 确认按钮是否可用；默认 true
 * @param isConfirming 确认执行中：确认按钮转圈、全部按钮禁用、阻断外部 dismiss
 * @param dismissOnClickOutside 是否允许点击遮罩/返回键关闭；默认 true
 * @param dismissOnConfirm 点击确认后是否自动关闭；默认 true（异步确认应配 isConfirming 传 false）
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
    content: (@Composable () -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isConfirming: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    dismissOnConfirm: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors
    // 确认执行中是"必须等待"状态：阻断外部 dismiss，避免误触打断异步操作
    val canDismissOutside = dismissOnClickOutside && !isConfirming

    val confirmButtonColor = when (type) {
        AppDialogType.Default -> colors.brandPrimary
        AppDialogType.Destructive -> colors.error
        AppDialogType.SingleChoice -> colors.brandPrimary
    }

    AlertDialog(
        onDismissRequest = { if (canDismissOutside) onDismiss() },
        title = {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        },
        text = {
            // 长内容（单选列表 / 富文本）可滚动，上限 70% 屏高，避免按钮被挤出屏幕
            Column(
                modifier = Modifier
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * DIALOG_CONTENT_MAX_HEIGHT_FRACTION)
                    .verticalScroll(rememberScrollState())
            ) {
                when (type) {
                    AppDialogType.SingleChoice -> {
                        val opts = options ?: emptyList()
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
                val hasContentAbove = type == AppDialogType.SingleChoice || message != null
                if (content != null) {
                    if (hasContentAbove) {
                        Spacer(Modifier.height(PrimitiveSpacing.Md))
                    }
                    content?.invoke()
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isConfirming,
                ) {
                    Text(
                        text = cancelText,
                        color = if (isConfirming) colors.textDisabled else colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(
                    onClick = {
                        onConfirm?.invoke()
                        if (dismissOnConfirm) onDismiss()
                    },
                    enabled = confirmEnabled && !isConfirming,
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (confirmEnabled) confirmButtonColor else colors.textDisabled,
                        )
                        Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    }
                    Text(
                        text = confirmText,
                        color = when {
                            isConfirming -> colors.textDisabled
                            confirmEnabled -> confirmButtonColor
                            else -> colors.textDisabled
                        },
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
 * v2 能力（均有默认值，既有调用点零改动）：
 * - [content] 自定义内容插槽（渲染在 message 下方），承载输入框/列表等富内容
 * - [confirmEnabled] 确认按钮可用性（如表单校验未过）
 * - [isConfirming] 确认执行中：确认按钮转圈 + 全部按钮禁用 + 阻断外部 dismiss；
 *   调用方负责异步完成后自行关闭（配 dismissOnConfirm = false 使用）
 * - [dismissOnClickOutside] 是否允许点击遮罩/返回键关闭；false = 必须通过按钮做出选择
 * - [dismissOnConfirm] 点击确认后是否自动关闭；默认 true
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
 * @param content 自定义内容插槽，渲染在 message 下方；null 时不占位
 * @param confirmEnabled 确认按钮是否可用；默认 true
 * @param isConfirming 确认执行中状态
 * @param dismissOnClickOutside 是否允许遮罩/返回键关闭；默认 true
 * @param dismissOnConfirm 点击确认后是否自动关闭；默认 true（异步确认应配 isConfirming 传 false）
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
    content: (@Composable () -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isConfirming: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    dismissOnConfirm: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors
    val confirmColor = if (isDestructive) colors.error else colors.brandPrimary
    // 确认执行中是"必须等待"状态：阻断外部 dismiss，避免误触打断异步操作
    val canDismissOutside = dismissOnClickOutside && !isConfirming

    AlertDialog(
        onDismissRequest = { if (canDismissOutside) onDismiss() },
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
            // 长内容可滚动，上限 70% 屏高，避免按钮被挤出屏幕
            Column(
                modifier = Modifier
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * DIALOG_CONTENT_MAX_HEIGHT_FRACTION)
                    .verticalScroll(rememberScrollState())
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                        color = colors.textSecondary,
                    )
                }
                if (content != null) {
                    if (message != null) {
                        Spacer(Modifier.height(PrimitiveSpacing.Md))
                    }
                    content?.invoke()
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isConfirming,
                ) {
                    Text(
                        text = cancelText,
                        color = if (isConfirming) colors.textDisabled else colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                TextButton(
                    onClick = { onConfirm(); if (dismissOnConfirm) onDismiss() },
                    enabled = confirmEnabled && !isConfirming,
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = if (confirmEnabled) confirmColor else colors.textDisabled,
                        )
                        Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    }
                    Text(
                        text = confirmText,
                        color = when {
                            isConfirming -> colors.textDisabled
                            confirmEnabled -> confirmColor
                            else -> colors.textDisabled
                        },
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

/** Preview：确认执行中（按钮转圈 + 阻断外部 dismiss）。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppConfirmDialogConfirmingPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppConfirmDialog(
            title = "删除项目",
            message = "正在删除项目文件与构建产物…",
            confirmText = "删除",
            isDestructive = true,
            isConfirming = true,
            dismissOnConfirm = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview：自定义内容插槽（message + content 混排）。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppConfirmDialogContentPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppConfirmDialog(
            title = "恢复备份",
            message = "恢复将合并以下数据，冲突项以备份为准。",
            confirmText = "恢复",
            onConfirm = {},
            onDismiss = {},
            content = {
                Text(
                    text = "· AI 服务商配置\n· Git 凭据\n· 终端设置",
                    color = LocalAppTheme.current.colors.textSecondary,
                )
            },
        )
    }
}

/** Preview：必须做出选择（阻断遮罩/返回键关闭）。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppConfirmDialogForcedChoicePreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppConfirmDialog(
            title = "安全警告",
            message = "检测到明文 HTTP 请求，数据可能被窃听或篡改。",
            confirmText = "我知道了",
            dismissOnClickOutside = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
