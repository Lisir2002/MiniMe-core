package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 联网搜索命中卡（对话流）：与文档引用互补的搜索结果列表。
 */
data class AppWebHit(val title: String, val domain: String, val snippet: String)

@Composable
fun AppWebSearchCard(
    hits: List<AppWebHit>,
    modifier: Modifier = Modifier,
    onOpen: (AppWebHit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(appPalette().card),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = appPalette().labelSecondary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                "联网搜索 · ${hits.size} 条结果",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelTertiary,
            )
        }
        if (expanded) {
            hits.forEach { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(h) }
                        .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            h.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = appPalette().ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            h.domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = appPalette().primary,
                        )
                        Text(
                            h.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = appPalette().labelSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
