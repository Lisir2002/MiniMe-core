package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 回合总结条（对话流）：一轮结束的浓缩指标。
 */
@Composable
fun AppTurnSummaryBar(
    filesChanged: Int,
    toolsRun: Int,
    duration: String,
    tokens: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape)
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("文件", "$filesChanged")
        Stat("工具", "$toolsRun")
        Stat("耗时", duration)
        Stat("tokens", tokens)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = appPalette().ink)
        Spacer(Modifier.width(AppSpacing.Tiny))
        Text(label, style = MaterialTheme.typography.labelSmall, color = appPalette().labelTertiary)
    }
}
