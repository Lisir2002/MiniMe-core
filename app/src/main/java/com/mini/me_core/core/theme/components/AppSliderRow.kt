package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens

/**
 * 滑块行：标签 + 当前值 + 通栏 Slider。
 *
 * 原型为 ThemeSettingsScreen 的私有 SliderRow（圆角/透明度/字号三处调用）。
 * 滑块配色统一走 Semantic Token（brandPrimary 轨道、borderDefault 未激活轨道）。
 *
 * @param label 标签
 * @param value 当前值
 * @param valueRange 取值范围
 * @param onValueChange 值变化回调
 * @param valueLabel 当前值的展示文字（由调用方格式化，如 "75%"）
 * @param modifier 修饰符
 * @param enabled 是否启用
 * @param steps 步进数（0 = 连续）
 */
@Composable
fun AppSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                color = if (enabled) colors.textPrimary else colors.textDisabled,
            )
            Text(
                text = valueLabel,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = if (enabled) colors.textSecondary else colors.textDisabled,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.brandPrimary,
                activeTrackColor = colors.brandPrimary,
                inactiveTrackColor = colors.borderDefault.copy(alpha = 0.3f),
                disabledThumbColor = colors.textDisabled,
                disabledActiveTrackColor = colors.textDisabled.copy(alpha = 0.4f),
                disabledInactiveTrackColor = colors.borderDefault.copy(alpha = 0.15f),
            ),
        )
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：滑块行。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun AppSliderRowPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column {
            AppSliderRow(
                label = "透明度",
                value = 0.75f,
                valueRange = 0f..1f,
                onValueChange = {},
                valueLabel = "75%",
            )
            AppSliderRow(
                label = "字号",
                value = 14f,
                valueRange = 10f..24f,
                onValueChange = {},
                valueLabel = "14 sp",
                enabled = false,
            )
        }
    }
}
