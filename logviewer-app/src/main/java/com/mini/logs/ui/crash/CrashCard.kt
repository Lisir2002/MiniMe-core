package com.mini.logs.ui.crash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.CrashGroup
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 崩溃聚合卡片。已修复的卡片降低透明度。
 */
@Composable
fun CrashCard(
    crash: CrashGroup,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onToggleFixed: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (crash.isFixed) 0.45f else 1f)
            .clickable { onClick() },
        variant = AppCardVariant.Default,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (crash.isFixed) "🟢" else "🔴",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = crash.exceptionType,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (crash.isFixed) colors.textSecondary else colors.error,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "发生 ${crash.occurrences} 次 · 最近: ${FormatUtils.formatRelativeTime(crash.lastOccurrence)}" +
                    " · Tag: ${crash.tags.joinToString(", ")}",
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = crash.message.ifBlank { crash.stackTraceFirstLine },
                fontSize = 13.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppButton(
                    text = "详情",
                    onClick = onClick,
                    variant = AppButtonVariant.Text,
                    size = AppButtonSize.Small,
                )
                AppButton(
                    text = "复制",
                    onClick = onCopy,
                    variant = AppButtonVariant.Text,
                    size = AppButtonSize.Small,
                )
                AppButton(
                    text = if (crash.isFixed) "取消修复" else "标记已修复",
                    onClick = onToggleFixed,
                    variant = AppButtonVariant.Text,
                    buttonColor = if (crash.isFixed) AppButtonColor.Neutral else AppButtonColor.Success,
                    size = AppButtonSize.Small,
                )
            }
        }
    }
}
