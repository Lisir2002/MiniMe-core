package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 提交 / 可回滚标记 chip（对话流）。
 */
@Composable
fun AppCommitChip(
    hash: String,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已提交 $hash",
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
        )
        if (onUndo != null) {
            Spacer(Modifier.width(AppSpacing.Xs))
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = "撤销",
                tint = appPalette().primary,
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .clip(RoundedCornerShape(AppRadius.Pill))
                    .clickable { onUndo() },
            )
        }
    }
}
