package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一列表项组件。
 *
 * 基于现有 CyberMenuRow 设计，支持图标块、标题、副标题、搜索高亮、尾随组件、分割线。
 *
 * 替换目标：CyberMenuRow（4处）+ 各模块自定义列表项。
 *
 * @param icon 图标
 * @param title 标题
 * @param subtitle 副标题（可选）
 * @param onClick 点击事件（null 表示不可点击）
 * @param modifier 修饰符
 * @param showDivider 是否显示底部分割线
 * @param highlightQuery 搜索高亮关键词
 * @param trailing 尾随组件（null 时默认显示箭头）
 * @param iconBg 图标块背景色（null 时使用默认灰色，图标为灰色；非 null 时图标为白色）
 */
@Composable
fun AppListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    highlightQuery: String = "",
    trailing: (@Composable () -> Unit)? = null,
    iconBg: Color? = null,
) {
    val colors = LocalAppTheme.current.colors

    val effectiveIconBg = iconBg ?: colors.surfaceSunken
    val effectiveIconTint = if (iconBg != null) Color.White else colors.textSecondary
    val titleColor = colors.textPrimary
    val subtitleColor = colors.textSecondary
    val dividerColor = colors.borderMuted

    val effectiveTrailing: @Composable () -> Unit = trailing ?: {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
        )
    }

    val highlightedTitle = remember(title, highlightQuery) {
        if (highlightQuery.isBlank()) {
            AnnotatedString(title)
        } else {
            buildAnnotatedString {
                append(title)
                val lower = title.lowercase()
                val tokens = highlightQuery.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                for (token in tokens) {
                    var start = lower.indexOf(token.lowercase())
                    while (start >= 0) {
                        val end = start + token.length
                        addStyle(
                            style = SpanStyle(background = colors.brandPrimary.copy(alpha = 0.2f)),
                            start = start,
                            end = end,
                        )
                        start = lower.indexOf(token.lowercase(), end)
                    }
                }
                toAnnotatedString()
            }
        }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
        ) {
            // 图标块
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(PrimitiveRadius.Md))
                    .background(effectiveIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveIconTint,
                    modifier = Modifier.size(20.dp),
                )
            }

            // 标题 + 副标题
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = highlightedTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                    )
                }
            }

            // 尾随组件
            effectiveTrailing()
        }

        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = dividerColor,
                modifier = Modifier.padding(start = PrimitiveSpacing.Lg + 38.dp + PrimitiveSpacing.Md),
            )
        }
    }
}
