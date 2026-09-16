package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppRadius

/**
 * 带数值气泡的滑杆（分子组 · AppSlider）。
 * 不用 BoxWithConstraints（SubcomposeLayout），改用 onSizeChanged 记录轨道宽度后定位气泡。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    labelFormat: (Float) -> String = { it.toInt().toString() },
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableStateOf(0) }
    val bubbleW = 42.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onSizeChanged { trackWidthPx = it.width },
        ) {
            // 把 px 转 dp 后算 offset；首帧 trackWidth=0 时气泡居中，不影响。
            val density = androidx.compose.ui.platform.LocalDensity.current
            val trackWidthDp = with(density) { trackWidthPx.toDp() }
            val bubbleOffsetX = if (trackWidthDp > bubbleW) {
                (trackWidthDp - bubbleW) * (frac - 0.5f)
            } else {
                0.dp
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = bubbleOffsetX)
                    .width(bubbleW)
                    .height(26.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.inverseSurface),
                )
                Text(
                    text = labelFormat(value),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
