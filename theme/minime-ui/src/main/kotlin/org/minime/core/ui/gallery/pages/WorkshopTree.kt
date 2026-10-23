package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TypographyValue
import org.minime.core.schema.TokenValue
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.tokens.ThemeSnapshot
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor

/**
 * 工坊令牌树的流内 items（R5.5：树并入参数流，charter §3）。
 *
 * 结构：分类（[WorkshopCategory]）→ 二级分组（[WorkshopGroup]，可折叠）→ 叶（可覆写路径）。
 * 叶行三要素：语义名 + 完整路径（工程标识）+ 当前值摘要；被覆写的叶带 accent 圆点
 * （diff 高亮的导航侧），选中叶整行高亮。
 *
 * 值全部读自当前生效快照（[LocalThemeSnapshot].raw）——覆写成功后本树即时反映；
 * 「当前值 vs 原值」的完整对照在编辑器内（读 baseSnapshot）。
 *
 * **手风琴（R5.5）**：选中叶的行下方内嵌 [WorkshopEditorContent]（同刻单开由单一
 * `selectedPath` 天然保证；再点选中行 = 收起，由调用侧 `onSelect` 的取逻辑决定）。
 * 展开区 = accent 竖线 + surfaceColor 底 + 左缩进，替代 R5.5 前的常驻编辑器卡
 * （去「未选中也空占 60%」的比例切割，charter §1.1 病灶 1）。
 *
 * 折叠态读取纪律：`groupExpanded` 的读必须发生在 item 的 composable lambda 内
 * （本扩展函数自身非 composable，直接读不触发重组——折叠点击将无响应）。
 */
fun LazyListScope.workshopTreeItems(
    category: WorkshopCategory,
    selectedPath: String?,
    overlays: Map<String, TokenValue>,
    overridable: Boolean,
    groupExpanded: SnapshotStateMap<String, Boolean>,
    manager: ThemeManager,
    onSelect: (String) -> Unit,
) {
    val groups = WorkshopSpec.groupsOf(category)

    groups.forEach { group ->
        val key = "${category.subsystem}/${group.title}"
        item(key = "header-$key") {
            val expanded = groupExpanded[key] ?: true
            WorkshopGroupHeader(
                title = group.title,
                count = group.paths.size,
                expanded = expanded,
                onToggle = { groupExpanded[key] = !expanded },
            )
        }
        items(
            items = group.paths,
            key = { it },
        ) { path ->
            // expanded 在 composable lambda 内读：折叠切换时这些 item 重组显隐
            val expanded = groupExpanded[key] ?: true
            if (expanded) {
                val snapshot = LocalThemeSnapshot.current
                Column(modifier = Modifier.fillMaxWidth()) {
                    WorkshopTreeRow(
                        path = path,
                        snapshot = snapshot,
                        overridden = path in overlays,
                        selected = path == selectedPath,
                        onClick = { onSelect(path) },
                    )
                    // 手风琴：选中行下方内嵌编辑器（无独立 LazyColumn，滚动流天然承载）
                    if (path == selectedPath) {
                        WorkshopEditorInline(
                            path = path,
                            manager = manager,
                            overridable = overridable,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手风琴展开区（R5.5）：accent 竖线 + surfaceColor 底 + 左缩进 + 上下文行。
 *
 * 「正在编辑：路径」行明示上下文（charter §7 风险对策：展开后树的行-编辑器-行
 * 混合流中，用户需要知道正在编辑谁）。
 */
@Composable
private fun WorkshopEditorInline(
    path: String,
    manager: ThemeManager,
    overridable: Boolean,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = spacing.s4.value.dp),
    ) {
        // accent 竖线（展开态标识；pill 圆角；fillMaxHeight 跟随兄弟列高度）
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(color.accentColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "正在编辑：$path",
                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                color = color.accentColor,
                modifier = Modifier.padding(start = spacing.s12.value.dp, bottom = spacing.s4.value.dp),
            )
            WorkshopEditorContent(
                path = path,
                manager = manager,
                overridable = overridable,
                modifier = Modifier
                    .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                    .padding(start = spacing.s12.value.dp),
            )
        }
    }
}

@Composable
private fun WorkshopGroupHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = spacing.s8.value.dp, bottom = spacing.s4.value.dp)
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(color.fillPrimaryColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            style = typography.caption1.toTextStyle(),
            color = color.textSecondaryColor,
        )
        Spacer(Modifier.width(spacing.s8.value.dp))
        Text(
            text = title,
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count",
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
        )
    }
}

@Composable
private fun WorkshopTreeRow(
    path: String,
    snapshot: ThemeSnapshot,
    overridden: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val elevation = MiniMeTokens.LocalElevation.current
    val value = snapshot.raw[path]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(if (selected) color.fillPrimaryColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .drawBehind {
                // 发丝线分隔（elevation.hairline 令牌；drawBehind 内禁读 CompositionLocal，值外取）
                drawLine(
                    color = color.separatorColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = elevation.hairline.px,
                )
            }
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp)
            .semantics { testTag = "workshop-leaf-$path" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // diff 圆点：被覆写 = accent 实点；未覆写 = 空点占位（防跳动）
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                .background(if (overridden) color.accentColor else color.separatorColor),
        )
        Spacer(Modifier.width(spacing.s12.value.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = WorkshopSpec.displayName(path),
                style = typography.callout.toTextStyle(),
                color = if (selected) color.accentColor else color.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(spacing.s8.value.dp))
        Text(
            text = value?.let(::valueSummary) ?: "—",
            style = typography.caption1.toTextStyle(),
            color = if (overridden) color.accentColor else color.textSecondaryColor,
            maxLines = 1,
        )
    }
}

/** 叶行值摘要（各类型的紧凑读数；编辑器内有完整形态） */
private fun valueSummary(value: TokenValue): String = when (value) {
    is ColorValue -> value.hex.removePrefix("#")
    is DimensionValue -> "${value.value.wsFmt()}${value.unit}"
    is DurationValue -> "${value.ms.wsFmt()}ms"
    is NumberValue -> value.value.wsFmt()
    is BooleanValue -> if (value.value) "true" else "false"
    is StringValue -> value.value
    is SpringValue -> "k${value.stiffness.wsFmt()} c${value.damping.wsFmt()}"
    is GlassValue -> "${value.blur.value.wsFmt()}${value.blur.unit} · α${value.tintAlpha.wsFmt()}"
    is TypographyValue -> "${value.fontSize.value.wsFmt()}${value.fontSize.unit}"
    is ShadowValue -> "shadow"
    else -> "—"
}
