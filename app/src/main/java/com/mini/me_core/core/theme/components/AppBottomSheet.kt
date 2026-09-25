package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一底部弹窗组件。
 *
 * 封装 Material3 ModalBottomSheet，使用 Semantic Token 颜色。
 * 统一 dragHandle（4dp高、32dp宽、弱化色）、顶部 xl 圆角、内容 padding。
 *
 * @param onDismiss 关闭回调
 * @param content 内容区域
 * @param skipPartiallyExpanded 是否跳过部分展开状态（默认 true）
 * @param containerColor 自定义容器色（默认使用 surfaceOverlay）
 * @param sheetState 自定义 SheetState（可选）
 * @param dragHandle 自定义 dragHandle（默认统一样式；传 null 隐藏）
 * @param sheetGesturesEnabled 是否启用手势（默认 true）
 * @param tonalElevation 色调高度（默认 0.dp）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    containerColor: Color? = null,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    ),
    dragHandle: @Composable (() -> Unit)? = { DefaultDragHandle() },
    sheetGesturesEnabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val shape = RoundedCornerShape(
        topStart = LocalCornerRadius.current.xl,
        topEnd = LocalCornerRadius.current.xl,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor ?: colors.surfaceOverlay,
        dragHandle = dragHandle,
        sheetGesturesEnabled = sheetGesturesEnabled,
        tonalElevation = tonalElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = PrimitiveSpacing.Xxl),
        ) {
            content()
        }
    }
}

/** 默认统一 dragHandle。 */
@Composable
private fun DefaultDragHandle() {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PrimitiveSpacing.Sm),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.textTertiary),
        )
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：底部弹表示例。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 500)
@Composable
private fun AppBottomSheetPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = PrimitiveSpacing.Sm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LocalAppTheme.current.colors.textTertiary),
                )
            }
            Text(
                text = "底部弹窗标题",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
                color = LocalAppTheme.current.colors.textPrimary,
            )
            Spacer(Modifier.height(PrimitiveSpacing.Md))
            Text(
                text = "这里是底部弹窗的内容区域。统一使用 AppBottomSheet 组件，保证 dragHandle、圆角和 padding 一致。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
                color = LocalAppTheme.current.colors.textSecondary,
            )
        }
    }
}
