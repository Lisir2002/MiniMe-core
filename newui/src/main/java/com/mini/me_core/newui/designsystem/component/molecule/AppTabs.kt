package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppSizing

/**
 * 滑动指示器标签栏（分子组 · AppTabs）。
 * 不用 BoxWithConstraints，改用 onSizeChanged 记录总宽度后算 tabWidth。
 */
@Composable
fun AppTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = appPalette().primary,
) {
    if (tabs.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, tabs.size - 1)
    var totalWidthPx by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tabWidth = if (totalWidthPx > 0) {
        with(density) { (totalWidthPx / tabs.size).toDp() }
    } else {
        0.dp
    }
    val indicatorOffset by animateDpAsState(
        targetValue = tabWidth * safeIndex.toFloat(),
        animationSpec = tween(durationMillis = AppMotion.Med.toInt()),
        label = "tabIndicator",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { totalWidthPx = it.width },
    ) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val selected = index == safeIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(AppSizing.IconButton)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(3.dp)
                    .background(indicatorColor),
            )
        }
    }
}
