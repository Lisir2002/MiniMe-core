package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 统一进度指示器组件。
 *
 * 封装 CircularProgressIndicator / LinearProgressIndicator，使用 Semantic Token 颜色。
 *
 * @param indicatorType 指示器类型：Circular / Linear
 * @param modifier 修饰符
 * @param color 指示器颜色（默认 brandPrimary）
 * @param strokeWidth 线宽（circular 类型）
 * @param progress 进度值（0-1，null 表示不确定进度）
 */
@Composable
fun AppLoadingIndicator(
    indicatorType: AppIndicatorType = AppIndicatorType.Circular,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    strokeWidth: Dp = 3.dp,
    progress: Float? = null,
) {
    val colors = LocalAppTheme.current.colors
    val effectiveColor = color ?: colors.brandPrimary

    when (indicatorType) {
        AppIndicatorType.Circular -> {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = modifier,
                    color = effectiveColor,
                    strokeWidth = strokeWidth,
                )
            } else {
                CircularProgressIndicator(
                    modifier = modifier,
                    color = effectiveColor,
                    strokeWidth = strokeWidth,
                )
            }
        }
        AppIndicatorType.Linear -> {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = modifier.fillMaxWidth(),
                    color = effectiveColor,
                )
            } else {
                LinearProgressIndicator(
                    modifier = modifier.fillMaxWidth(),
                    color = effectiveColor,
                )
            }
        }
    }
}

/**
 * 指示器类型枚举。
 */
enum class AppIndicatorType {
    /** 圆形进度指示器 */
    Circular,
    /** 线性进度指示器 */
    Linear,
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：圆形指示器。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 200, heightDp = 100)
@Composable
private fun AppLoadingIndicatorCircularPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            AppLoadingIndicator(indicatorType = AppIndicatorType.Circular)
            AppLoadingIndicator(indicatorType = AppIndicatorType.Circular, progress = 0.6f)
        }
    }
}

/** Preview：线性指示器。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 100)
@Composable
private fun AppLoadingIndicatorLinearPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            AppLoadingIndicator(indicatorType = AppIndicatorType.Linear)
            AppLoadingIndicator(indicatorType = AppIndicatorType.Linear, progress = 0.6f)
        }
    }
}
