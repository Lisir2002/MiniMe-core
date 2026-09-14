package com.mini.me_core.newui.designsystem.component.molecule

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
 * 归一化：卡片底 [AppColor.BrandCard]、标题 [AppColor.BrandInk]、正文 [AppColor.LabelSecondary]、
 * 确认键缺省 iOS 蓝 [AppColor.BrandPrimary]，与其余弹窗分子保持同一主风格色阶。
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
                color = AppColor.BrandInk,
            )
        },
        text = if (text != null) {
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.LabelSecondary,
                )
            }
        } else null,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText, color = if (confirmButtonColor != Color.Unspecified) confirmButtonColor else AppColor.BrandPrimary)
            }
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissText, color = AppColor.LabelSecondary)
                }
            }
        } else null,
        containerColor = AppColor.BrandCard,
    )
}