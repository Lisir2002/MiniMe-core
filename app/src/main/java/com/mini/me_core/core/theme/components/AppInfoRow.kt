package com.mini.me_core.core.theme.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 信息展示行：标签 + 值（标签在上、值在下，值加粗）。
 *
 * 原型为 AboutSection 的私有 DeviceRow（设备信息/版本信息 8 行）。
 * 支持点击与长按（长按复制是既有的成熟交互，配合 AppCopyAction 或自定义回调）。
 *
 * @param label 标签（次要色）
 * @param value 值（主色加粗）
 * @param modifier 修饰符
 * @param onClick 点击回调（可选）
 * @param onLongClick 长按回调（可选，典型用途为复制 value）
 * @param showDivider 是否显示底部分割线
 */
@Composable
fun AppInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors
    val hasInteraction = onClick != null || onLongClick != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasInteraction) {
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { onLongClick?.invoke() },
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.borderMuted,
                modifier = Modifier.padding(start = PrimitiveSpacing.Lg),
            )
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：信息行。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppInfoRowPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column {
            AppInfoRow(label = "版本", value = "v1.4.2")
            AppInfoRow(
                label = "包名",
                value = "com.mini.me_core",
                onLongClick = {},
                showDivider = false,
            )
        }
    }
}
