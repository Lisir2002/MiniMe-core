package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/** 迷你复选方块（分子组 · AppCheckbox）：勾选时品牌色填充 + 弹簧弹出的白勾。  *
 * @since 0.1.0-experimental
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = appPalette().primary,
) {
    val boxColor by animateColorAsState(
        targetValue = if (checked) color else MaterialTheme.colorScheme.surfaceVariant,
        label = "checkboxBox",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = AppMotion.emphasizedSpring(),
        label = "checkboxCheck",
    )
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(boxColor)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        // 装饰图标：旁侧已有文字/语义，跳过无障碍
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = appPalette().onPrimary,
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                    alpha = checkScale
                },
        )
    }
}

/** 复选设置行（分子组 · AppCheckRow）：左侧标题+副标题，右侧 [AppCheckbox]，整行可点。  *
 * @since 0.1.0-experimental
 */
@Composable
fun AppCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.Tiny),
                )
            }
        }
        Spacer(Modifier.padding(start = AppSpacing.Sm))
        AppCheckbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}