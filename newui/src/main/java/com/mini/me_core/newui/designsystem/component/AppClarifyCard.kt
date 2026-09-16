package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 反问 / 澄清卡（对话流）：AI 信息不足时提问并给出快速选项。
 */
@Composable
fun AppClarifyCard(
    question: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onAnswer: (String) -> Unit = {},
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape)
            .padding(AppSpacing.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.HelpOutline,
                contentDescription = null,
                tint = appPalette().primary,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
            )
        }
        Spacer(Modifier.width(AppSpacing.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
            options.forEach { o ->
                Text(
                    o,
                    style = MaterialTheme.typography.labelMedium,
                    color = appPalette().primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .background(appPalette().primary.copy(alpha = 0.08f))
                        .clickable { onAnswer(o) }
                        .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
                )
            }
        }
    }
}
