package com.mini.me_core.core.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveElevation
import com.mini.me_core.core.theme.tokens.PrimitiveRadius

/**
 * 统一卡片组件。
 *
 * 基于现有 CyberCard 设计（14dp 圆角、0.8dp 边框、0.5dp 阴影），
 * 使用 Semantic Token 颜色，支持三种变体。
 *
 * 替换目标：188 处直接使用 Material Card + 8 处 CyberCard。
 *
 * @param variant 卡片变体：Default（纯色卡片）/ Outlined（描边卡片）/ Elevated（阴影卡片）
 * @param modifier 修饰符
 * @param content 卡片内容
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Default,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val shape = RoundedCornerShape(PrimitiveRadius.Xl) // 12dp，与现有 CyberCard 14dp 接近

    val containerColor: Color
    val border: BorderStroke?
    val shadowElevation: androidx.compose.ui.unit.Dp

    when (variant) {
        AppCardVariant.Default -> {
            containerColor = colors.surfaceCard
            border = BorderStroke(0.8.dp, colors.borderDefault)
            shadowElevation = 0.5.dp
        }
        AppCardVariant.Outlined -> {
            containerColor = Color.Transparent
            border = BorderStroke(1.dp, colors.borderDefault)
            shadowElevation = 0.dp
        }
        AppCardVariant.Elevated -> {
            containerColor = colors.surfaceCard
            border = null
            shadowElevation = PrimitiveElevation.Z2
        }
        AppCardVariant.Sunken -> {
            containerColor = colors.surfaceSunken
            border = BorderStroke(0.8.dp, colors.borderMuted)
            shadowElevation = 0.dp
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = shape,
        shadowElevation = shadowElevation,
        tonalElevation = 0.dp,
        border = border,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * 卡片变体枚举。
 */
enum class AppCardVariant {
    /** 默认：纯色背景 + 细边框 + 微阴影（兼容现有 CyberCard） */
    Default,
    /** 描边：透明背景 + 边框（用于次要内容） */
    Outlined,
    /** 阴影：纯色背景 + 阴影（用于突出内容） */
    Elevated,
    /** 内嵌：深色背景 + 弱边框（用于代码块/工具输出） */
    Sunken,
}
