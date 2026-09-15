package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius

/**
 * 弹窗统一封装（§3.12 AppDialog / AppTokens 圆角边距）。
 * 破坏性确认：confirmText 用 StatusDanger，调用方在 onClick 外套 [AppHaptics.click]。
 *
 * 归一化：卡片底 [appPalette().card]、标题 [appPalette().ink]、正文 [appPalette().labelSecondary]、
 * 确认键缺省 iOS 蓝 [appPalette().primary]，与其余弹窗分子保持同一主风格色阶。
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
) {
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
        containerColor = appPalette().card,
    )
}