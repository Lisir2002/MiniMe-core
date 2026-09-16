package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 引用来源：标题 + URL + 摘要。 */
data class AppCitationSource(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * 引用来源卡（分子组 · AppCitationCard）：Agent 联网 / 查文档后列出参考来源。
 * 默认折叠为「引用 N 个来源」一行，展开后逐条列标题、URL、摘要；
 * 与"执行类"工具卡互补，专管"检索来源"这一层。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppCitationCard(
    sources: List<AppCitationSource>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = modifier
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = appPalette().primary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = "引用 ${sources.size} 个来源",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起引用" else "展开引用",
                tint = appPalette().labelTertiary,
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                sources.forEach { s -> SourceRow(s) }
            }
        }
    }
}

@Composable
private fun SourceRow(source: AppCitationSource) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(appPalette().surface)
            .padding(AppSpacing.Sm),
    ) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = appPalette().ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(AppSpacing.Tiny))
        Text(
            text = source.url,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(AppSpacing.Tiny))
        Text(
            text = source.snippet,
            style = MaterialTheme.typography.bodySmall,
            color = appPalette().labelSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
