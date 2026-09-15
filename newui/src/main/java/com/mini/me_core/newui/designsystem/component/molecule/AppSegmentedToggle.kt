package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
 * 选中即 surface 浮起 + 加粗，比原生 TabRow 指示条更立体（Linear/Vercel 同款胶囊切换）。
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
    BoxWithConstraints(modifier.fillMaxWidth().height(itemHeight)) {
        val itemWidth = maxWidth / options.size
        val sliderWidth = itemWidth - inset * 2
        val sliderHeight = itemHeight - inset * 2
        // 高亮滑块：宽=itemWidth-inset*2、高=itemHeight-inset*2、起点 x=inset+itemWidth*index
        val indicatorOffset by animateDpAsState(
            targetValue = inset + itemWidth * safeIndex.toFloat(),
            animationSpec = tween(durationMillis = AppMotion.Med.toInt()),
            label = "indicator",
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(AppRadius.Pill))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // 高亮滑块：宽度严格等于一个选项格，不再 fillMaxSize 撑满整行
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(sliderWidth)
                    .height(sliderHeight)
                    .clip(RoundedCornerShape(AppRadius.Pill))
                    .background(MaterialTheme.colorScheme.surface),
            )
            // 选项
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(itemHeight)
                            .clickable(enabled = true, onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (index == safeIndex) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (index == safeIndex) {
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
}
