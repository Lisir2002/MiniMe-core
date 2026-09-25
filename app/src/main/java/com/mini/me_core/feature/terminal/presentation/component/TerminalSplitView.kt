package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * F3.2 分屏视图：上下两个终端各占一定高度，分隔线可拖动调整比例（20%-80%）。
 * 焦点终端有 2dp primary 边框。两个 pane 由调用方提供内容（各自独立会话）。
 */
@Composable
fun TerminalSplitView(
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    topFocused: Boolean,
    bottomFocused: Boolean,
    modifier: Modifier = Modifier,
    initialRatio: Float = 0.5f,
) {
    var ratio by remember { mutableFloatStateOf(initialRatio.coerceIn(0.2f, 0.8f)) }
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalH = maxHeight
        val topH = totalH * ratio
        val dividerH = 4.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topH)
                .then(if (topFocused) Modifier.border(BorderStroke(2.dp, primary)) else Modifier)
        ) { topContent() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dividerH)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        ratio = (ratio + dragAmount / totalH.toPx()).coerceIn(0.2f, 0.8f)
                    }
                }
                .align(Alignment.BottomCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((totalH - topH - dividerH).coerceAtLeast(0.dp))
                .then(if (bottomFocused) Modifier.border(BorderStroke(2.dp, primary)) else Modifier)
        ) { bottomContent() }
    }
}
