package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 编译 / lint / 运行错误卡（对话流）：文件:行号 + 错误摘要 + 操作。
 */
@Composable
fun AppErrorCard(
    title: String,
    location: String?,
    message: String,
    modifier: Modifier = Modifier,
    onFix: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, AppColor.StatusDanger.copy(alpha = 0.45f), shape),
    ) {
        Row(
            Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = AppColor.StatusDanger,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
        }
        if (location != null) {
            Text(
                location,
                style = MaterialTheme.typography.bodySmall,
                color = AppColor.StatusDanger,
                modifier = Modifier.padding(start = AppSpacing.Md, end = AppSpacing.Md),
            )
        }
        Spacer(Modifier.size(AppSpacing.Xs))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = appPalette().labelSecondary,
            modifier = Modifier.padding(horizontal = AppSpacing.Md),
        )
        if (onFix != null) {
            Row(
                Modifier.padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Sm),
            ) {
                Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = AppColor.StatusDanger)) {
                    Text("让我修")
                }
            }
        }
    }
}
