package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MiniMe 引用块（F2.1）。
 * - 左侧 4dp primary 竖条；
 * - 背景 surfaceVariant(50% 透明度)；
 * - 内边距 12dp；文字 onSurfaceVariant 斜体。
 */
@Composable
fun MiniMeQuote(
    inlines: List<MdInline>,
    onOpenUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.primary)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LinkableInlines(
                inlines = inlines,
                baseStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                onOpenUrl = onOpenUrl,
            )
        }
    }
}
