package org.minime.core.ui.gallery.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.draw.drawHairline
import org.minime.core.ui.draw.HairlineEdge
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.tokens.ThemeMeta
import org.minime.core.ui.gallery.GalleryIcon
import org.minime.core.ui.gallery.ThemeModifiers
import org.minime.core.ui.gallery.canvasColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.groupedColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor

/**
 * Gallery 顶栏（§10.2：顶栏含**常驻主题入口**——当前包 + 修饰符 + 一键切换，
 * 借鉴 Compose Material Catalog 的常驻 ThemePicker）。
 *
 * 自举：底色 = bg.canvas，底部发丝线 = elevation.hairline 令牌，
 * 高度 = size.navBar.height 令牌（顶部安全区由调用方垫入）。
 */

/**
 * @param onToggleDark 亮 / 暗一键切换（走 `ThemeManager.select`，§8.1 换肤路径）
 * @param onOpenDrawer 窄屏打开侧边栏抽屉
 * @param compact 窄屏形态（菜单按钮替代副标题）
 */
@Composable
fun GalleryTopBar(
    title: String,
    subtitle: String,
    meta: ThemeMeta,
    compact: Boolean,
    onToggleDark: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val size = MiniMeTokens.LocalSize.current
    val shape = MiniMeTokens.LocalShape.current
    val elevation = MiniMeTokens.LocalElevation.current

    val dark = meta.activeModifiers.contains(ThemeModifiers.DARK)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color.canvasColor)
            .drawHairline(HairlineEdge.Bottom, color.separatorColor, elevation.hairline.px)
            .padding(horizontal = spacing.gutter.value.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(size.navBarHeight.value.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                // 窄屏：菜单按钮（44dp 触达，视觉 24dp 图标，§3.2）
                Box(
                    modifier = Modifier
                        .size(size.touchTarget.value.dp)
                        .clip(ContinuousCornerShape(10.dp))
                        .clickable(onClick = onOpenDrawer),
                    contentAlignment = Alignment.Center,
                ) {
                    GalleryIcon(
                        icon = GalleryIcon.Menu,
                        color = color.textPrimaryColor,
                        iconSize = size.icon.value.dp,
                        contentDescription = "打开分区导航",
                    )
                }
                Spacer(Modifier.width(spacing.s8.value.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (compact) typography.headline.toTextStyle() else typography.title3.toTextStyle(),
                    color = color.textPrimaryColor,
                    maxLines = 1,
                )
                if (!compact) {
                    Text(
                        text = subtitle,
                        style = typography.caption1.toTextStyle(),
                        color = color.textSecondaryColor,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(spacing.s8.value.dp))

            // 常驻主题入口：当前外观 + 一键切换（§10.2）
            Row(
                modifier = Modifier
                    .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                    .background(color.fillPrimaryColor)
                    .clickable(onClick = onToggleDark)
                    .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
            ) {
                GalleryIcon(
                    icon = if (dark) GalleryIcon.Moon else GalleryIcon.Sun,
                    color = color.textPrimaryColor,
                    iconSize = 18.dp,
                    contentDescription = "切换亮暗色",
                )
                Text(
                    text = if (dark) "暗色" else "亮色",
                    style = typography.footnote.toTextStyle(),
                    color = color.textPrimaryColor,
                )
            }
        }
    }
}
