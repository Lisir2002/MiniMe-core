package com.mini.me_core.newui.designsystem.component.atom

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mini.me_core.newui.designsystem.token.generated.AppSizing

/**
 * 图标按钮（§3.6 图标规范 · AppIconButton）：基于 M3 [IconButton] 的薄封装。
 *
 * 设计来源：§3.6 图标规范——
 * - 触摸目标固定 [AppSizing.IconButton]（40dp），满足触控最小热区要求；
 * - 图标视觉尺寸固定 [AppSizing.IconM]（20dp），与 [AppIcon] 默认尺寸一致；
 * - tint 默认取 M3 onSurfaceVariant（二级图标色），强调场景由调用方显式传入。
 *
 * 无障碍语义：[contentDescription] 为 null 时图标视为装饰性（同 [AppIcon] 约定，§3.6.7）；
 * 可操作图标按钮务必传入有意义的 contentDescription。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(AppSizing.IconButton),
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(AppSizing.IconM),
        )
    }
}
