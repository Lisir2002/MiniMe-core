package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 弹窗统一封装（§3.12 AppDialog / AppTokens 圆角边距）。
 * 破坏性确认：confirmText 用 StatusDanger，调用方在 onClick 外套 [AppHaptics.click]。
 *
 * 归一化：卡片底 [appPalette().card]、标题 [appPalette().ink]、正文 [appPalette().labelSecondary]、
 * 确认键缺省 iOS 蓝 [appPalette().primary]，与其余弹窗分子保持同一主风格色阶。
 *
 * 对话框体系合并：本组件是**唯一推荐**的弹窗入口；AppDialogs / AppDialogsAdvanced 已废弃。
 * 已并入原 Advanced 中有价值的自定义能力：
 *  - [scrimAlpha] 非 null 时改用 [BasicAlertDialog] + 自定义遮罩（用 [appPalette().ink] 自适应明暗）；
 *    为 null 时保持 M3 [AlertDialog] 默认遮罩行为；
 *  - [containerColor] 自定义弹窗背景色；[tonalElevation] 扁平风格默认 0。
 * 新增参数均带默认值，不破坏既有调用。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissText: String? = null,
    confirmButtonColor: Color = Color.Unspecified,
    scrimAlpha: Float? = null,
    containerColor: Color = appPalette().card,
    tonalElevation: Dp = 0.dp,
) {
    if (scrimAlpha != null) {
        // 自定义遮罩透明度：M3 AlertDialog 不支持自定义 scrim，故切换到 BasicAlertDialog，
        // 由外层 Box 用 appPalette().ink（自适应明暗）+ alpha 自行绘制 scrim。
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(),
            modifier = modifier,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appPalette().ink.copy(alpha = scrimAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(AppRadius.Lg),
                    color = containerColor,
                    tonalElevation = tonalElevation,
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.Lg)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = appPalette().ink,
                        )
                        if (text != null) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = appPalette().labelSecondary,
                                modifier = Modifier.padding(top = AppSpacing.Sm),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppSpacing.Lg),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dismissText != null) {
                                TextButton(onClick = onDismiss) {
                                    Text(text = dismissText, color = appPalette().labelSecondary)
                                }
                            }
                            TextButton(onClick = onConfirm) {
                                Text(
                                    text = confirmText,
                                    color = if (confirmButtonColor != Color.Unspecified) confirmButtonColor else appPalette().primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            shape = RoundedCornerShape(AppRadius.Lg),
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = appPalette().ink,
                )
            },
            text = if (text != null) {
                {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appPalette().labelSecondary,
                    )
                }
            } else null,
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = confirmText, color = if (confirmButtonColor != Color.Unspecified) confirmButtonColor else appPalette().primary)
                }
            },
            dismissButton = if (dismissText != null) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(text = dismissText, color = appPalette().labelSecondary)
                    }
                }
            } else null,
            containerColor = containerColor,
            tonalElevation = tonalElevation,
        )
    }
}

@Preview(showBackground = true, name = "AppDialog - Confirm")
@Composable
private fun PreviewAppDialog() {
    AppTheme {
        var showDialog by remember { mutableStateOf(true) }
        if (showDialog) {
            AppDialog(
                title = "确认操作",
                text = "此操作将不可撤销，确定要继续吗？",
                confirmText = "确认",
                dismissText = "取消",
                onConfirm = { showDialog = false },
                onDismiss = { showDialog = false },
            )
        }
    }
}
