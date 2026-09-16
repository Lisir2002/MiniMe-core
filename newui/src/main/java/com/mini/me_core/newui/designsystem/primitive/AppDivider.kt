package com.mini.me_core.newui.designsystem.primitive

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppLayout

/**
 * 水平分割线（§3.9 分隔线规范 · AppDivider）：基于 M3 [HorizontalDivider] 的薄封装。
 *
 * 设计来源：§3.9 分隔线规范——
 * - 厚度统一 [AppLayout.DividerThickness]（1dp），禁止业务方散落 `0.5.dp` / `1.dp` 硬编码；
 * - 颜色默认取 [appPalette] 的 separator 令牌（深浅主题自适应）；
 * - 仅用于同一容器内分组的弱分隔，跨区块的强分隔应改用留白（[AppSpacing]）而非分割线。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = AppLayout.DividerThickness,
    color: Color = appPalette().separator,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
    )
}
