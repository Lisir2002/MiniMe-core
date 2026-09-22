package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一按钮组件。
 *
 * 基于现有 PrimaryButton/SecondaryButton/DangerOutlinedButton/DangerTextButton/
 * AgentActionButton 等归纳。
 *
 * 支持四种变体、五种颜色、三种尺寸，可选加载态和左侧图标。
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param variant 变体：Filled/Outlined/Text/Tonal
 * @param buttonColor 颜色：Primary/Success/Warning/Error/Neutral
 * @param size 尺寸：Small/Medium/Large
 * @param enabled 是否可用
 * @param loading 是否加载中（显示进度圈，禁用点击）
 * @param icon 可选左侧图标
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    buttonColor: AppButtonColor = AppButtonColor.Primary,
    size: AppButtonSize = AppButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = LocalAppTheme.current.colors
    val isEnabled = enabled && !loading

    // Resolve content/container colors based on variant + buttonColor
    val containerColor: Color = when (buttonColor) {
        AppButtonColor.Primary -> colors.brandPrimary
        AppButtonColor.Success -> colors.success
        AppButtonColor.Warning -> colors.warning
        AppButtonColor.Error -> colors.error
        AppButtonColor.Neutral -> colors.surfaceCard
    }
    val contentColor: Color = when (buttonColor) {
        AppButtonColor.Primary -> colors.onBrandPrimary
        AppButtonColor.Success -> colors.onSuccess
        AppButtonColor.Warning -> colors.onWarning
        AppButtonColor.Error -> colors.onError
        AppButtonColor.Neutral -> colors.textPrimary
    }
    val disabledContainer: Color = colors.surfacePressed
    val disabledContent: Color = colors.textDisabled

    val height = when (size) {
        AppButtonSize.Small -> 32.dp
        AppButtonSize.Medium -> 40.dp
        AppButtonSize.Large -> 48.dp
    }
    val iconSize = when (size) {
        AppButtonSize.Small -> 14.dp
        AppButtonSize.Medium -> 18.dp
        AppButtonSize.Large -> 20.dp
    }
    val fontSize = when (size) {
        AppButtonSize.Small -> 12.sp
        AppButtonSize.Medium -> 14.sp
        AppButtonSize.Large -> 16.sp
    }

    val shape = RoundedCornerShape(PrimitiveRadius.Xl) // 12dp

    val buttonColors: ButtonColors = when (variant) {
        AppButtonVariant.Filled -> ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        )
        AppButtonVariant.Tonal -> ButtonDefaults.filledTonalButtonColors(
            containerColor = when (buttonColor) {
                AppButtonColor.Primary -> colors.brandContainer
                AppButtonColor.Success -> colors.successContainer
                AppButtonColor.Warning -> colors.warningContainer
                AppButtonColor.Error -> colors.errorContainer
                AppButtonColor.Neutral -> colors.surfaceSunken
            },
            contentColor = when (buttonColor) {
                AppButtonColor.Primary -> colors.onBrandContainer
                AppButtonColor.Success -> colors.onSuccessContainer
                AppButtonColor.Warning -> colors.onWarningContainer
                AppButtonColor.Error -> colors.onErrorContainer
                AppButtonColor.Neutral -> colors.textPrimary
            },
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        )
        AppButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = disabledContent,
        )
        AppButtonVariant.Text -> ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = disabledContent,
        )
    }

    val modifierWithHeight = modifier.heightIn(min = height)

    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
            }
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    when (variant) {
        AppButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = modifierWithHeight,
            enabled = isEnabled,
            shape = shape,
            colors = buttonColors,
        ) { content() }

        AppButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifierWithHeight,
            enabled = isEnabled,
            shape = shape,
            colors = buttonColors,
        ) { content() }

        AppButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifierWithHeight,
            enabled = isEnabled,
            shape = shape,
            colors = buttonColors,
        ) { content() }

        AppButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = modifierWithHeight,
            enabled = isEnabled,
            shape = shape,
            colors = buttonColors,
        ) { content() }
    }
}

/** 按钮变体枚举。 */
enum class AppButtonVariant {
    Filled, Outlined, Text, Tonal
}

/** 按钮颜色枚举。 */
enum class AppButtonColor {
    Primary, Success, Warning, Error, Neutral
}

/** 按钮尺寸枚举。 */
enum class AppButtonSize {
    Small, Medium, Large
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：展示所有变体和颜色。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 420, heightDp = 600)
@Composable
private fun AppButtonPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Filled:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "Primary", onClick = {})
                AppButton(text = "Success", onClick = {}, buttonColor = AppButtonColor.Success)
                AppButton(text = "Error", onClick = {}, buttonColor = AppButtonColor.Error)
            }
            Text("Outlined:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "Primary", onClick = {}, variant = AppButtonVariant.Outlined)
                AppButton(text = "Error", onClick = {}, variant = AppButtonVariant.Outlined, buttonColor = AppButtonColor.Error)
            }
            Text("Tonal:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "Primary", onClick = {}, variant = AppButtonVariant.Tonal)
                AppButton(text = "Warning", onClick = {}, variant = AppButtonVariant.Tonal, buttonColor = AppButtonColor.Warning)
            }
            Text("Text:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "Cancel", onClick = {}, variant = AppButtonVariant.Text)
                AppButton(text = "Delete", onClick = {}, variant = AppButtonVariant.Text, buttonColor = AppButtonColor.Error)
            }
            Text("States:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "Loading", onClick = {}, loading = true)
                AppButton(text = "Disabled", onClick = {}, enabled = false)
                AppButton(text = "Small", onClick = {}, size = AppButtonSize.Small)
            }
        }
    }
}
