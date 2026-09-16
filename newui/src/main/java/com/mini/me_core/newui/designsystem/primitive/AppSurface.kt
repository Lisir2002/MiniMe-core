package com.mini.me_core.newui.designsystem.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppRadius

/**
 * 统一表面容器（§3.4 表面容器规范 · AppSurface）：基于 M3 [Surface] 的薄封装。
 *
 * 设计来源：§3.4 表面容器规范——
 * - 圆角默认 [AppRadius.Md]（12dp），与 [AppCard] 保持一致；
 * - 默认扁平：[tonalElevation] / [shadowElevation] 均为 [AppElevation.Z0]，分层靠底色而非阴影，
 *   需要悬浮/抬升语义时由调用方显式传入 Z2/Z3；
 * - [border] 默认 null；需要描边容器（如 AI 气泡外框）再传入 [BorderStroke]。
 *
 * 与 [AppCard] 的分工：[AppCard] 是固定规格（Z1 阴影 + Md 圆角）的业务卡片；
 * 本组件是参数化表面容器，供分子/原子层自由组合。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(AppRadius.Md),
    tonalElevation: Dp = AppElevation.Z0,
    shadowElevation: Dp = AppElevation.Z0,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = shape,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}
