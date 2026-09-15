package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 键盘键帽（分子组 · AppKeyCap / AppKeyCombo）：iOS 简约风格的拟态键帽，
 * 用于展示快捷键组合（如 ⌘K / Ctrl+⇧P）或短命令标签。
 *
 * 归一化要点（对齐 iOS 简约规范）：
 *  - **克制拟态**：以 `surface → surfaceVariant` 纵向渐变模拟键帽由亮到暗的下沉面，
 *    替代原 2dp 大块强调色"底部暗边"（免过于游戏化）；边缘发丝线用 [appPalette().separator]。
 *  - **微光底色**：键帽底部一条强调色 hairline（`accentColor @28%`）作为"光缝"点缀，
 *    与主色 #0A84FF 呼应，弱化到纹理级不影响可读性。
 *  - **度量令牌**：内边距/圆角/间距全走 [AppSpacing]/[AppRadius]，消除硬编码 px。
 *  - **文字**：等宽字体（快捷键惯例）+ 半粗 + [appPalette().ink] 一级文字。
 *  - **组合**：[AppKeyCombo] 以「+」串联，分隔间距 `AppSpacing.Xs`、色 [appPalette().labelTertiary]。
 */
@Composable
fun AppKeyCap(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = appPalette().primary,
) {
    Box(
        modifier = modifier
            .shadow(AppElevation.Z1, RoundedCornerShape(AppRadius.Sm))
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .border(1.dp, appPalette().separator, RoundedCornerShape(AppRadius.Sm))
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        // 底部"光缝" hairline：拟态键帽下沉边的微光，accent 弱化为纹理级点缀
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(alpha = 0.28f)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = appPalette().ink,
        )
    }
}

/**
 * 快捷键组合（分子组 · AppKeyCombo）：多个键帽以「+」串联。
 */
@Composable
fun AppKeyCombo(
    keys: List<String>,
    modifier: Modifier = Modifier,
    accentColor: Color = appPalette().primary,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { index, key ->
            if (index > 0) {
                Spacer(Modifier.width(AppSpacing.Xs))
                Text(
                    text = "+",
                    style = MaterialTheme.typography.labelSmall,
                    color = appPalette().labelTertiary,
                )
                Spacer(Modifier.width(AppSpacing.Xs))
            }
            AppKeyCap(label = key, accentColor = accentColor)
        }
    }
}