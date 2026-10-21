package org.minime.core.ui.gallery.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.tokens.ThemeMeta
import org.minime.core.ui.gallery.nav.GallerySection
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
 * 右侧边栏导航（白皮书 §10.2：右侧边栏 `ThemeSidebar`；手机降级为抽屉）。
 *
 * R3 边界说明：`ThemeSidebar` 组件本体属二期目录（§7.2.5），此处为**壳级导航**
 * （摆放 + 选择器——§10.1 硬纪律 1 禁止的是「组件被重写」，壳级导航不属组件目录），
 * 组件交付后由 gallery 切换为 `ThemeSidebar` 真组件，避免双真源。
 * 视觉全部取自当前主题令牌（自举 §10.9）。
 */

/** 分区 → 侧边栏图标 */
internal fun GallerySection.icon(): GalleryIcon = when (this) {
    GallerySection.TOKENS -> GalleryIcon.Tokens
    GallerySection.COMPONENTS -> GalleryIcon.Components
    GallerySection.PROTOTYPES -> GalleryIcon.Prototypes
    GallerySection.MOTION -> GalleryIcon.Motion
    GallerySection.WORKSHOP -> GalleryIcon.Workshop
    GallerySection.DEBUG -> GalleryIcon.Debug
}

/**
 * 侧边栏主体（宽屏常驻 / 窄屏抽屉共用同一 composable）。
 */
@Composable
fun GallerySidebar(
    current: GallerySection,
    meta: ThemeMeta,
    engineVersion: String,
    onSelect: (GallerySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(color.groupedColor)
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s16.value.dp),
    ) {
        // 头部：产品名 + 引擎版本
        Column(modifier = Modifier.padding(start = spacing.s8.value.dp, top = spacing.s8.value.dp)) {
            Text(
                text = "Design Gallery",
                style = typography.title3.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.height(spacing.s4.value.dp))
            Text(
                text = "验证页面 · 引擎 $engineVersion",
                style = typography.caption1.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
        Spacer(Modifier.height(spacing.s16.value.dp))

        // 分区导航（顺序即验收顺序，§10.2）
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s4.value.dp)) {
            GallerySection.entries.forEach { section ->
                SidebarItem(
                    section = section,
                    selected = section == current,
                    onClick = { onSelect(section) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部：当前主题摘要
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                .background(color.surfaceColor)
                .padding(spacing.s12.value.dp),
        ) {
            Text(
                text = meta.packName,
                style = typography.footnote.toTextStyle(),
                color = color.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.s4.value.dp))
            Text(
                text = buildString {
                    append("包版本 ${meta.packVersion}")
                    if (meta.activeModifiers.isNotEmpty()) {
                        append(" · ${meta.activeModifiers.joinToString(" / ")}")
                    }
                },
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    section: GallerySection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val size = MiniMeTokens.LocalSize.current

    val bg = if (selected) color.fillPrimaryColor else color.groupedColor
    val contentColor = if (selected) color.textPrimaryColor else color.textSecondaryColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryIcon(icon = section.icon(), color = contentColor, iconSize = size.icon.value.dp)
        Spacer(Modifier.width(spacing.s12.value.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = typography.body.toTextStyle(),
                color = contentColor,
            )
            Text(
                text = section.subtitle,
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 未交付分区显示批次徽标（R6–R9 等），让「同速生长」可见
        if (section.plannedBatch != "R3") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color.fillPrimaryColor)
                    .padding(horizontal = spacing.s8.value.dp, vertical = 2.dp),
            ) {
                Text(
                    text = section.plannedBatch,
                    style = typography.caption2.toTextStyle(),
                    color = color.textSecondaryColor,
                )
            }
        }
    }
}

/**
 * 窄屏抽屉（右侧面板 + 暗化 scrim）。
 *
 * R3 无动画（立即显隐）：动效规格统一由 R5 动效层令牌化后替换——
 * 壳级不自行发明动画参数（§10.9 纪律 + 目录外零新增）。
 */
@Composable
fun GalleryDrawer(
    current: GallerySection,
    meta: ThemeMeta,
    engineVersion: String,
    onSelect: (GallerySection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opacity = MiniMeTokens.LocalOpacity.current
    val shape = MiniMeTokens.LocalShape.current

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        // scrim：黑 × opacity.scrim 令牌（iOS 模态暗化 idiom）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = opacity.scrim.value))
                .clickable(onClick = onDismiss)
        )
        GallerySidebar(
            current = current,
            meta = meta,
            engineVersion = engineVersion,
            onSelect = onSelect,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(288.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = shape.radiusSheetTop.value.dp,
                        bottomStart = shape.radiusSheetTop.value.dp,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp,
                    )
                ),
        )
    }
}
