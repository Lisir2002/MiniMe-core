@file:Suppress("FunctionNaming", "LongMethod") // Compose @Composable PascalCase 命名约定；长 Composable 逐字搬迁，行为保持
package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppNavIcon
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 侧边栏导航图标块：彩色圆角背景 + 白色图标，日夜间背景色随主题切换。
 *
 * 实现已抽取为设计系统组件 [AppNavIcon]（core/theme/components），
 * 左侧抽屉与右侧工具抽屉共用同一组件，保证两个侧边栏图标视觉一致。
 */
@Composable
internal fun DrawerNavIcon(
    icon: ImageVector,
    iconBgLight: Color,
    iconBgDark: Color
) {
    AppNavIcon(
        icon = icon,
        iconBgLight = iconBgLight,
        iconBgDark = iconBgDark,
    )
}

/**
 * 侧边栏底部导航图标按钮：点击整块触发，内部复用 [DrawerNavIcon] 的彩色圆角图标块。
 * 图标之间按 [Arrangement.End] 贴右排列，按钮本身不扩展占宽。
 */
@Composable
internal fun DrawerBottomIconButton(
    icon: ImageVector,
    contentDescription: String,
    iconBgLight: Color,
    iconBgDark: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        DrawerNavIcon(
            icon = icon,
            iconBgLight = iconBgLight,
            iconBgDark = iconBgDark
        )
    }
}

