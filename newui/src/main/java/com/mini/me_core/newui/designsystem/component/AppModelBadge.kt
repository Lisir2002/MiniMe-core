package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 模型标记小字徽章（对话流）：标识本条回复所用模型 / 档位。
 */
@Composable
fun AppModelBadge(
    model: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = model,
        style = MaterialTheme.typography.labelSmall,
        color = appPalette().labelTertiary,
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
    )
}
