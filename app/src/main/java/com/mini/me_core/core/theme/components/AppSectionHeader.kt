package com.mini.me_core.core.theme.components
import com.mini.me_core.core.theme.tokens.LocalComponentTokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一分区标题组件。
 *
 * 基于现有 AppSectionHeader（AppComponents.kt，带左侧装饰条）和
 * CyberSectionHeader（CyberComponents.kt，无装饰条纯文字）归纳。
 *
 * 支持标题、可选副标题、可选尾随操作组件、可选左侧图标。
 *
 * @param title 标题文字
 * @param modifier 修饰符
 * @param subtitle 可选副标题（12sp 弱化色）
 * @param icon 可选左侧图标（替代原 AppSectionHeader 的装饰条）
 * @param trailing 可选尾随组件（如"查看全部"按钮）
 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors

    Row(
        modifier = modifier
            .padding(
                horizontal = PrimitiveSpacing.Lg,
                vertical = PrimitiveSpacing.Sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.width(20.dp),
            )
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(PrimitiveSpacing.Xxs))
                Text(
                    text = subtitle,
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            trailing()
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：展示各种配置。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppSectionHeaderPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            AppSectionHeader(title = "基础设置")
            AppSectionHeader(
                title = "高级选项",
                subtitle = "需要重启生效",
            )
            AppSectionHeader(
                title = "已安装技能",
                subtitle = "共 12 个",
                trailing = {
                    Text(
                        text = "查看全部",
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = LocalAppTheme.current.colors.brandPrimary,
                    )
                },
            )
            AppSectionHeader(
                title = "环境变量",
                icon = Icons.Rounded.Settings,
                trailing = {
                    Text(
                        text = "编辑",
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = LocalAppTheme.current.colors.brandPrimary,
                    )
                },
            )
        }
    }
}
