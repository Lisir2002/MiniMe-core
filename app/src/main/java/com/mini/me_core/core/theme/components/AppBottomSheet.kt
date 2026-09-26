package com.mini.me_core.core.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
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

// ══════════════════════════════════════════════
// 底部抽屉统一结构（新增）
// ══════════════════════════════════════════════

/**
 * 统一底部抽屉标题栏。
 *
 * 标题左对齐（titleMedium / SemiBold / onSurface），右侧可选关闭按钮。
 * 内容区应在本组件之下自行用 `Modifier.padding(horizontal = PrimitiveSpacing.Lg)` 对齐。
 *
 * @param title 标题文字
 * @param onClose 关闭回调；null 表示不显示关闭按钮
 */
@Composable
fun AppSheetHeader(
    title: String,
    onClose: (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = LocalComponentTokens.current.text.titleMediumFontSize,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 操作菜单项（图标 + 文字）。
 *
 * 24dp 图标，操作项；危险操作走 error 色。点击区域为整行。
 *
 * @param icon 图标
 * @param label 文字
 * @param isDestructive 是否危险操作（图标与文字 error 色）
 * @param onClick 点击回调
 */
@Composable
fun AppSheetActionItem(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val tint = if (isDestructive) colors.error else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .padding(
                horizontal = PrimitiveSpacing.Lg,
                vertical = PrimitiveSpacing.Md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Lg))
        Text(
            text = label,
            fontSize = LocalComponentTokens.current.text.bodyLargeFontSize,
            color = tint,
        )
    }
}

/**
 * 选择列表项（单选）。
 *
 * 左侧 RadioButton，选中时右侧显示对勾或高亮文字。点击整行触发选中。
 *
 * @param label 文字
 * @param selected 是否选中
 * @param onClick 选中回调
 */
@Composable
fun AppSheetSelectionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .padding(
                horizontal = PrimitiveSpacing.Lg,
                vertical = PrimitiveSpacing.Md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(PrimitiveSpacing.Md))
        Text(
            text = label,
            fontSize = LocalComponentTokens.current.text.bodyLargeFontSize,
            color = if (selected) colors.brandPrimary else colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.brandPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 操作菜单项数据模型，供 [AppActionSheet] 使用。 */
data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 操作菜单底部抽屉（快捷封装）。
 *
 * 结构：dragHandle + AppSheetHeader(title, onClose) + 垂直排列的 [AppSheetActionItem]。
 * 每个 action 点击后自动 dismiss（onClick 先执行）。
 *
 * @param title 标题
 * @param actions 操作项列表
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionSheet(
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = title, onClose = onDismiss)
        Spacer(Modifier.height(PrimitiveSpacing.Sm))
        actions.forEach { action ->
            AppSheetActionItem(
                icon = action.icon,
                label = action.label,
                isDestructive = action.isDestructive,
                onClick = {
                    action.onClick()
                    onDismiss()
                },
            )
        }
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
