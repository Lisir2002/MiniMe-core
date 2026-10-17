@file:Suppress("FunctionNaming") // Compose @Composable PascalCase 命名约定（与右侧抽屉/抽屉 chrome 一致）

package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.MiniMeTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 侧边栏统一导航图标块（应用主题统一图标设计）。
 *
 * 左侧抽屉底部按钮与右侧工具抽屉的菜单项共用本组件——同一套尺寸、圆角、
 * 日夜双色背景与白色前景，保证两个侧边栏的图标视觉完全一致。
 *
 * 规格（与既有左侧抽屉 DrawerNavIcon 逐像素一致，抽取时行为保持）：
 * - 图标块 36dp，圆角 [LocalCornerRadius.current.lg]
 * - 图标 20dp，前景恒白（彩色块上保证对比度）
 * - 背景色日夜双取值：亮色模式用 [iconBgLight]，暗色模式用 [iconBgDark]
 *
 * @param icon 图标
 * @param iconBgLight 亮色模式图标块背景色
 * @param iconBgDark 暗色模式图标块背景色
 * @param contentDescription 无障碍描述（null 时由外层 Row 的 semantics 提供）
 * @param blockSize 图标块尺寸（默认 36dp）
 * @param iconSize 图标尺寸（默认 20dp）
 */
@Composable
fun AppNavIcon(
    icon: ImageVector,
    iconBgLight: Color,
    iconBgDark: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    blockSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(blockSize)
            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
            .background(if (LocalAppDarkMode.current) iconBgDark else iconBgLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Preview：统一图标块在主题下的效果（internal 避免 UnusedPrivateMember 新增 baseline 债务）。 */
@Preview(showBackground = true, widthDp = 200, heightDp = 100)
@Composable
internal fun AppNavIconPreview() {
    MiniMeTheme(darkTheme = false) {
        Row {
            AppNavIcon(
                icon = Icons.Rounded.Settings,
                iconBgLight = Color(0xFF0EA5E9),
                iconBgDark = Color(0xFF0369A1),
                contentDescription = stringResource(R.string.chat_settings),
            )
        }
    }
}
