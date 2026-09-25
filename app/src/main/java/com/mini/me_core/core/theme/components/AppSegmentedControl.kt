package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一顶栏 Segmented Control（胶囊式分段控件）。
 *
 * 视觉规格与 MCP 中心页面保持一致：
 * - 外层容器：surfaceVariant(alpha=0.6f) 背景 + xl 圆角 + xs 内边距；
 *   外边距 horizontal=Spacing.lg / vertical=Spacing.sm。
 * - 每个 tab：选中时 surface 背景 + lg 圆角，未选中透明；
 *   文字 bodyMedium，选中 Bold + primary，未选中 Normal + onSurfaceVariant。
 *
 * 布局模式：
 * - [tabs.size] <= 4 且 [scrollable] == false：Row 等分（weight(1f)）。
 * - [tabs.size] > 4 或 [scrollable] == true：LazyRow 横向滚动，每个 tab 最小宽度 80dp。
 *
 * @param tabs tab 标题列表（建议使用 stringResource 解析后的字符串）
 * @param selectedIndex 当前选中下标
 * @param onSelect 选中回调
 * @param modifier 外部修饰符
 * @param scrollable 强制使用可滚动模式
 */
@Composable
fun AppSegmentedControl(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val useScrollable = scrollable || tabs.size > 4

    val containerModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        .background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            RoundedCornerShape(LocalCornerRadius.current.xl)
        )
        .padding(PrimitiveSpacing.Xs)

    if (useScrollable) {
        LazyRow(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tabs) { index, title ->
                SegmentItem(
                    title = title,
                    isSelected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    modifier = Modifier.widthIn(min = 80.dp)
                )
            }
        }
    } else {
        Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                SegmentItem(
                    title = title,
                    isSelected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 单个分段：选中 surface 背景 + lg 圆角，未选中透明；文字样式与 MCP 一致。 */
@Composable
private fun SegmentItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
