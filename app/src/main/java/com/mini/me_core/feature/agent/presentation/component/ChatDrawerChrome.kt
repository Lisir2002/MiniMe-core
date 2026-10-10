@file:Suppress("FunctionNaming", "LongMethod") // Compose @Composable PascalCase 命名约定；长 Composable 逐字搬迁，行为保持
package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import androidx.compose.ui.res.stringResource

/**
 * 侧边栏导航图标块：彩色圆角背景 + 白色图标，日夜间背景色随主题切换。
 */
@Composable
internal fun DrawerNavIcon(
    icon: ImageVector,
    iconBgLight: Color,
    iconBgDark: Color
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
            .background(if (LocalAppDarkMode.current) iconBgDark else iconBgLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
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

/**
 * 侧边栏底部「设置」按钮：图标 + 文字 的整块可点击区域（位于底部左侧）。
 */
@Composable
internal fun DrawerSettingsButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerNavIcon(
            icon = Icons.Rounded.Settings,
            iconBgLight = Color(0xFF0EA5E9),
            iconBgDark = Color(0xFF0369A1)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = stringResource(R.string.chat_settings),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 侧边栏底部「应用打包器」按钮：图标 + 文字 的整块可点击区域。
 */
@Composable
internal fun DrawerPackagerButton(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerNavIcon(
            icon = Icons.Rounded.Code,
            iconBgLight = Color(0xFF8B5CF6),
            iconBgDark = Color(0xFF6D28D9)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = "应用打包器",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
