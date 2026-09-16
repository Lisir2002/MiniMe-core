package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 建议追问 chips（对话流）：AI 回复完给出的下一步快捷选项。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppFollowUpChips(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit = {},
) {
    FlowRow(
        modifier = modifier.padding(horizontal = AppSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        suggestions.forEach { s ->
            Text(
                text = s,
                style = MaterialTheme.typography.labelMedium,
                color = appPalette().primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Pill))
                    .background(appPalette().primary.copy(alpha = 0.08f))
                    .border(AppStroke.Thin, appPalette().primary.copy(alpha = 0.30f), RoundedCornerShape(AppRadius.Pill))
                    .clickable { onPick(s) }
                    .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
            )
        }
    }
}
