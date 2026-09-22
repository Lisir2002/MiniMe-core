package com.mini.me_core.core.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.tokens.PrimitiveRadius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一 Chip 标签组件。
 *
 * 基于现有 InfoChip（SkillsScreen/SkillDetailScreen）、TypeChip（ProxyNodesScreen）、
 * StatusChip（GitScreen）、CyberChip、VariantPill、VersionPill、ModePill 等归纳。
 *
 * 支持三种变体和六种状态色，可选左侧图标和右侧关闭按钮。
 *
 * @param text 标签文字
 * @param modifier 修饰符
 * @param variant 变体：Default（浅底彩色字）/ Outlined（描边）/ Filled（实心底色）
 * @param chipColor 颜色：Primary/Success/Warning/Error/Info/Neutral
 * @param icon 可选左侧图标
 * @param onClose 可选关闭按钮回调；非空时右侧显示 ×
 */
@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: AppChipVariant = AppChipVariant.Default,
    chipColor: AppChipColor = AppChipColor.Neutral,
    icon: ImageVector? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors

    val containerColor: Color
    val contentColor: Color
    val borderStroke: BorderStroke?

    when (variant) {
        AppChipVariant.Default -> {
            containerColor = when (chipColor) {
                AppChipColor.Primary -> colors.brandContainer
                AppChipColor.Success -> colors.successContainer
                AppChipColor.Warning -> colors.warningContainer
                AppChipColor.Error -> colors.errorContainer
                AppChipColor.Info -> colors.infoContainer
                AppChipColor.Neutral -> colors.surfaceSunken
            }
            contentColor = when (chipColor) {
                AppChipColor.Primary -> colors.onBrandContainer
                AppChipColor.Success -> colors.onSuccessContainer
                AppChipColor.Warning -> colors.onWarningContainer
                AppChipColor.Error -> colors.onErrorContainer
                AppChipColor.Info -> colors.onInfoContainer
                AppChipColor.Neutral -> colors.textSecondary
            }
            borderStroke = null
        }
        AppChipVariant.Outlined -> {
            containerColor = Color.Transparent
            contentColor = when (chipColor) {
                AppChipColor.Primary -> colors.brandPrimary
                AppChipColor.Success -> colors.success
                AppChipColor.Warning -> colors.warning
                AppChipColor.Error -> colors.error
                AppChipColor.Info -> colors.info
                AppChipColor.Neutral -> colors.textSecondary
            }
            borderStroke = BorderStroke(
                width = PrimitiveSpacing.Hairline,
                color = when (chipColor) {
                    AppChipColor.Primary -> colors.brandPrimary
                    AppChipColor.Success -> colors.success
                    AppChipColor.Warning -> colors.warning
                    AppChipColor.Error -> colors.error
                    AppChipColor.Info -> colors.info
                    AppChipColor.Neutral -> colors.borderDefault
                },
            )
        }
        AppChipVariant.Filled -> {
            containerColor = when (chipColor) {
                AppChipColor.Primary -> colors.brandPrimary
                AppChipColor.Success -> colors.success
                AppChipColor.Warning -> colors.warning
                AppChipColor.Error -> colors.error
                AppChipColor.Info -> colors.info
                AppChipColor.Neutral -> colors.surfacePressed
            }
            contentColor = when (chipColor) {
                AppChipColor.Primary -> colors.onBrandPrimary
                AppChipColor.Success -> colors.onSuccess
                AppChipColor.Warning -> colors.onWarning
                AppChipColor.Error -> colors.onError
                AppChipColor.Info -> colors.onInfo
                AppChipColor.Neutral -> colors.textPrimary
            }
            borderStroke = null
        }
    }

    Surface(
        shape = RoundedCornerShape(PrimitiveRadius.Md),
        color = containerColor,
        border = borderStroke,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PrimitiveSpacing.MdPlus,
                vertical = PrimitiveSpacing.Xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(PrimitiveSpacing.Xxs))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (onClose != null) {
                Spacer(Modifier.width(PrimitiveSpacing.Xxs))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = contentColor,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** Chip 变体枚举。 */
enum class AppChipVariant {
    /** 默认：浅色容器底 + 深色文字（tonal） */
    Default,

    /** 描边：透明底 + 彩色边框 + 彩色文字 */
    Outlined,

    /** 填充：实心彩色底 + 反色文字 */
    Filled,
}

/** Chip 颜色枚举。 */
enum class AppChipColor {
    Primary, Success, Warning, Error, Info, Neutral
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：展示所有变体 × 颜色组合。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 500)
@Composable
private fun AppChipPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Text("Default (tonal) variant:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChip(text = "Primary", chipColor = AppChipColor.Primary)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Success", chipColor = AppChipColor.Success)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Warning", chipColor = AppChipColor.Warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChip(text = "Error", chipColor = AppChipColor.Error)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Info", chipColor = AppChipColor.Info)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Neutral", chipColor = AppChipColor.Neutral)
            }

            Spacer(Modifier.size(8.dp))
            Text("Outlined variant:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChip(text = "Primary", variant = AppChipVariant.Outlined, chipColor = AppChipColor.Primary)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Success", variant = AppChipVariant.Outlined, chipColor = AppChipColor.Success)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Error", variant = AppChipVariant.Outlined, chipColor = AppChipColor.Error)
            }

            Spacer(Modifier.size(8.dp))
            Text("Filled variant:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChip(text = "Primary", variant = AppChipVariant.Filled, chipColor = AppChipColor.Primary)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Success", variant = AppChipVariant.Filled, chipColor = AppChipColor.Success)
                Spacer(Modifier.width(Spacing.sm))
                AppChip(text = "Error", variant = AppChipVariant.Filled, chipColor = AppChipColor.Error)
            }

            Spacer(Modifier.size(8.dp))
            Text("With icon and close button:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppChip(
                    text = "Build",
                    chipColor = AppChipColor.Primary,
                    icon = Icons.Rounded.Build,
                )
                Spacer(Modifier.width(Spacing.sm))
                AppChip(
                    text = "Filter",
                    variant = AppChipVariant.Filled,
                    chipColor = AppChipColor.Info,
                    onClose = {},
                )
            }
        }
    }
}
