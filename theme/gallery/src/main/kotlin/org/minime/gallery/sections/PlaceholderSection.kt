package org.minime.gallery.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toTextStyle
import org.minime.gallery.GalleryIcon
import org.minime.gallery.icon
import org.minime.gallery.nav.GallerySection
import org.minime.gallery.accentColor
import org.minime.gallery.fillPrimaryColor
import org.minime.gallery.groupedColor
import org.minime.gallery.textPrimaryColor
import org.minime.gallery.textSecondaryColor

/**
 * 未交付分区占位页（§10.2 六分区中尚未建成的四个）。
 *
 * 存在意义：「画廊与批次同速生长」——每批 RC 的 APK 里都能看到全部六个分区入口，
 * 未建成的明示批次与验收项，避免「看不到 = 不存在」的信息丢失。
 * 对应批次交付时，本页替换为真实分区实现。
 */
@Composable
fun PlaceholderSection(section: GallerySection, modifier: Modifier = Modifier) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val size = MiniMeTokens.LocalSize.current

    val plan = when (section) {
        GallerySection.COMPONENTS ->
            "八大类 60 组件（MVP 25）：弹窗族 R6 → 菜单 / Sheet R7 → 横幅 / 页面骨架 R8 → " +
                "基础控件 R9。七态矩阵 + 检查工具条 + 深链逐项验收。"
        GallerySection.PROTOTYPES ->
            "P1–P8 页面原型建档（§6.5.1）+ 导航矩阵六类可重放 + 加载序列重放，随 R8 交付。"
        GallerySection.MOTION ->
            "26 个动效模式逐条点播 + 慢放 2×/5×/10× + 逐帧 scrub + reducedMotion 降级对照，" +
                "随 R5 动效层交付。"
        GallerySection.WORKSHOP ->
            "令牌覆写层（fallback < 编译快照 < 运行时覆写）+ WCAG 矩阵 + 导入导出 diff " +
                "主题包（round-trip），随 R4 交付——本计划的第一个高价值用例。"
        else -> ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.gutter.value.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(shape.radiusCardLarge.value.dp))
                .background(color.groupedColor)
                .padding(spacing.s24.value.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                    .background(color.fillPrimaryColor),
                contentAlignment = Alignment.Center,
            ) {
                org.minime.gallery.GalleryIcon(
                    icon = section.icon(),
                    color = color.textSecondaryColor,
                    iconSize = size.icon.value.dp,
                )
            }
            Spacer(Modifier.height(spacing.s16.value.dp))
            Text(
                text = section.title,
                style = typography.title3.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.height(spacing.s4.value.dp))
            Text(
                text = "随 ${section.plannedBatch} 交付",
                style = typography.footnote.toTextStyle(),
                color = color.accentColor,
            )
            Spacer(Modifier.height(spacing.s12.value.dp))
            Text(
                text = plan,
                style = typography.callout.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
    }
}
