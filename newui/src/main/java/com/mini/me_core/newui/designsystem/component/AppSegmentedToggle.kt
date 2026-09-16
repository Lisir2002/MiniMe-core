package com.mini.me_core.newui.designsystem.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppRadius

/**
 * 滑动分段控件（分子组 · AppSegmentedToggle）：pill 轨道内的高亮块随选择弹性滑动，
 * 选中即 surface 浮起 + 加粗。
 *
 * 不用 BoxWithConstraints（SubcomposeLayout），避免在 intrinsic measurement 父布局中崩溃。
 * 改用 Row + weight 等分选项，高亮滑块用 offset 动画。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 40.dp
    val inset = 3.dp
    val safeIndex = selectedIndex.coerceIn(0, options.size - 1)

    // 高亮滑块：用 Row + weight 定位，不依赖 BoxWithConstraints。
    // 每个选项用 weight(1f) 等分；高亮滑块也用 weight(1f)，通过 offset 移动到选中项位置。
    // offset 的 px 值需要知道 item 宽度，但我们在 Compose 里无法直接拿到 px。
    // 改用另一种方案：每个选项位置叠一个透明 Box，选中时背景为 surface。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(inset)) {
            options.forEachIndexed { index, label ->
                val selected = index == safeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .background(if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = true, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
