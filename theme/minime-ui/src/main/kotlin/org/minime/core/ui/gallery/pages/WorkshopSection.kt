package org.minime.core.ui.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.runtime.ThemeStatus
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.textSecondaryColor
import org.minime.core.ui.gallery.textTertiaryColor

/**
 * 分区五：主题工坊（白皮书 §10.7 / charter §4）——R4 替换 WORKSHOP 占位页。
 *
 * 三区布局：**顶栏**（标题 + 覆写计数 + 重置）+ **分类 tab**（色板 / 字阶 / 圆角 /
 * 间距 / 动效 / 玻璃）+ **工作区**（宽屏左实时预览右令牌控件；窄屏上下堆叠）。
 *
 * 数据面（全部外置，页面即单元——API 五原则）：
 * - 当前生效值：[LocalThemeSnapshot]（MiniMeTheme 随 manager.snapshot 重组）；
 * - 覆写表 / 原值面貌 / 状态：`manager.overlays` / `manager.baseSnapshot` / `manager.status`；
 * - 编辑动作：`manager.applyOverrides` / `manager.resetOverrides`（R4.1 运行时，后台编译
 *   + last-wins 节流 + 失败回滚，本页不含任何编译/合并逻辑）。
 *
 * 自举纪律（R4-c）：全部控件自绘 + 令牌消费（滑杆 / 开关 / 步进 / hex 输入 / 分段），
 * 正式组件族随 R6 组件目录回收。
 */
@Composable
fun WorkshopSection(manager: ThemeManager, modifier: Modifier = Modifier) {
    val snapshot = LocalThemeSnapshot.current
    val overlays by manager.overlays.collectAsState()
    val status by manager.status.collectAsState()
    var category by remember { mutableStateOf(WorkshopCategory.PALETTE) }
    var selectedPath by remember { mutableStateOf<String?>(null) }

    // fallback 态（无主题包）：覆写无处施加——编辑器禁用并明示
    val overridable = manager.currentPackId != ThemeManager.FALLBACK_ID
    // 分组折叠态（页面级记忆，跨分类保持）
    val groupExpanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize()) {
        WorkshopTopBar(
            overlayCount = overlays.size,
            category = category,
            onResetCategory = { manager.resetOverrides(WorkshopSpec.pathsOf(category)) },
            onResetAll = { manager.resetOverrides() },
        )
        WorkshopCategoryTabs(
            current = category,
            onSelect = {
                category = it
                selectedPath = null // 换类即清选择（选中路径可能不属新类）
            },
        )
        (status as? ThemeStatus.Failed)?.let { failed ->
            WorkshopErrorBanner(code = failed.code.name, message = failed.message)
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 640dp = Material medium 断点：工坊工作区自有响应式（宿主 840dp 管侧栏）
            val wide = maxWidth >= 640.dp
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WorkshopPreview(modifier = Modifier.weight(1f).fillMaxSize())
                    WorkshopConsole(
                        manager = manager,
                        category = category,
                        selectedPath = selectedPath,
                        overridable = overridable,
                        groupExpanded = groupExpanded,
                        onSelect = { selectedPath = it },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    WorkshopPreview(modifier = Modifier.weight(0.42f).fillMaxWidth())
                    WorkshopConsole(
                        manager = manager,
                        category = category,
                        selectedPath = selectedPath,
                        overridable = overridable,
                        groupExpanded = groupExpanded,
                        onSelect = { selectedPath = it },
                        modifier = Modifier.weight(0.58f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------- 顶栏（操作栏） ----------

@Composable
private fun WorkshopTopBar(
    overlayCount: Int,
    category: WorkshopCategory,
    onResetCategory: () -> Unit,
    onResetAll: () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主题工坊",
                style = typography.title3.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Text(
                text = if (overlayCount == 0) "无覆写 · 当前为包原初面貌" else "$overlayCount 条覆写生效中（内存态，导出才落盘）",
                style = typography.caption2.toTextStyle(),
                color = if (overlayCount == 0) color.textSecondaryColor else color.accentColor,
            )
        }
        WorkshopTextButton(
            text = "重置本类",
            enabled = overlayCount > 0,
            onClick = onResetCategory,
        )
        Spacer(Modifier.width(spacing.s8.value.dp))
        WorkshopTextButton(
            text = "全部还原",
            enabled = overlayCount > 0,
            onClick = onResetAll,
        )
    }
}

/** 自绘文字按钮（accent 文字；禁用转 textTertiary——组件族随 R6 回收） */
@Composable
private fun WorkshopTextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Box(
        modifier = Modifier
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            .background(if (enabled) color.fillPrimaryColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = text,
            style = typography.callout.toTextStyle(),
            color = if (enabled) color.accentColor else color.textSecondaryColor,
        )
    }
}

// ---------- 分类 tab ----------

@Composable
private fun WorkshopCategoryTabs(current: WorkshopCategory, onSelect: (WorkshopCategory) -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
    ) {
        WorkshopCategory.entries.forEach { category ->
            val selected = category == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable { onSelect(category) }
                    .padding(vertical = spacing.s8.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.title,
                    style = typography.caption1.toTextStyle(),
                    color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(spacing.s8.value.dp))
}

// ---------- 错误条（§5.2 错误码面） ----------

@Composable
private fun WorkshopErrorBanner(code: String, message: String) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val error = color.stateErrorColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s4.value.dp)
            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
            // banner 惯例透明度（非设计令牌；iOS 横幅风）
            .background(error.copy(alpha = 0.12f))
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "覆写被拒绝 · $code",
            style = typography.callout.toTextStyle(),
            color = error,
        )
        Spacer(Modifier.width(spacing.s8.value.dp))
        Text(
            text = message,
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------- 右控制台（编辑器 + 令牌树） ----------

@Composable
private fun WorkshopConsole(
    manager: ThemeManager,
    category: WorkshopCategory,
    selectedPath: String?,
    overridable: Boolean,
    groupExpanded: SnapshotStateMap<String, Boolean>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays by manager.overlays.collectAsState()
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
        ) {
            if (selectedPath != null) {
                WorkshopEditorCard(
                    path = selectedPath,
                    manager = manager,
                    overridable = overridable,
                )
            } else {
                WorkshopEditorPlaceholder(overridable = overridable)
            }
        }
        WorkshopTree(
            category = category,
            selectedPath = selectedPath,
            overlays = overlays,
            groupExpanded = groupExpanded,
            onSelect = onSelect,
            modifier = Modifier.weight(0.4f).fillMaxWidth(),
        )
    }
}

/** 未选中态提示（或 fallback 态禁用说明） */
@Composable
private fun WorkshopEditorPlaceholder(overridable: Boolean) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
            .background(color.surfaceColor)
            .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s24.value.dp),
    ) {
        Text(
            text = if (overridable) "从下方令牌树选择一条令牌" else "当前为 fallback 基座（无主题包），覆写不可用",
            style = typography.callout.toTextStyle(),
            color = color.textSecondaryColor,
        )
        if (overridable) {
            Spacer(Modifier.height(spacing.s4.value.dp))
            Text(
                text = "编辑即时生效于整个画廊（全局实时）；内存态，导出 diff 主题包才落盘（R4.3）。",
                style = typography.caption2.toTextStyle(),
                color = color.textSecondaryColor,
            )
        }
    }
}
