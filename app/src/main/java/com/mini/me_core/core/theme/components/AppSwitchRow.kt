package com.mini.me_core.core.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 设置项开关行（无图标块轻量版，区别于带 38dp 图标块的 [AppListItem]）。
 *
 * 整行可点击切换，尾随 Switch。实证：全项目 75 处手搓 Switch 行（Row + Text + Switch）
 * 视觉与交互不一，本组件统一为一种形态。
 *
 * @param title 标题
 * @param checked 开关状态
 * @param onCheckedChange 状态变化回调（整行点击同样触发）
 * @param modifier 修饰符
 * @param subtitle 副标题（可选）
 * @param icon 左侧图标（可选；20dp 行内图标，非 AppListItem 的图标块）
 * @param enabled 是否启用（false 时文字走禁用色、Switch 禁用态）
 * @param showDivider 是否显示底部分割线（有图标时分割线缩进与文字对齐）
 */
@Composable
fun AppSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors
    val titleColor = if (enabled) colors.textPrimary else colors.textDisabled
    val subtitleColor = if (enabled) colors.textSecondary else colors.textDisabled

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = subtitleColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.borderMuted,
                modifier = Modifier.padding(
                    start = if (icon != null) {
                        PrimitiveSpacing.Lg + 20.dp + PrimitiveSpacing.Sm
                    } else {
                        PrimitiveSpacing.Lg
                    },
                ),
            )
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：开关行各种配置。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppSwitchRowPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column {
            AppSwitchRow(
                title = "自动重连",
                subtitle = "断线后自动恢复 SSH 会话",
                checked = true,
                onCheckedChange = {},
            )
            AppSwitchRow(
                title = "退出确认",
                checked = false,
                onCheckedChange = {},
                showDivider = false,
            )
        }
    }
}
