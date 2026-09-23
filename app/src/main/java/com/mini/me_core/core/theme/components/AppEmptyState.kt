package com.mini.me_core.core.theme.components
import com.mini.me_core.core.theme.tokens.LocalComponentTokens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.FilledTonalButton
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
 * 统一空状态组件。
 *
 * 基于现有 AppEmptyState（AppComponents.kt，13 处使用）归纳。
 * 图标 + 标题 + 可选副标题 + 可选操作按钮，垂直居中。
 *
 * @param title 标题文字（16sp 粗体）
 * @param modifier 修饰符
 * @param icon 可选图标（48dp，弱化色）
 * @param subtitle 可选副标题（14sp 弱化色）
 * @param actionLabel 可选操作按钮文字
 * @param onAction 可选操作按钮回调
 */
@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PrimitiveSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(PrimitiveSpacing.Lg))
        }
        Text(
            text = title,
            fontSize = LocalComponentTokens.current.text.bodyLargeFontSize,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            Text(
                text = subtitle,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                color = colors.textSecondary,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：展示各种空状态配置。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
private fun AppEmptyStatePreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppEmptyState(
                title = "暂无会话",
                subtitle = "开始一个新的对话吧",
                icon = Icons.Rounded.Inbox,
            )
            AppEmptyState(
                title = "没有文件",
                actionLabel = "导入文件",
                onAction = {},
            )
        }
    }
}
