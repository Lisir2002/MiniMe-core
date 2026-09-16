package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 按钮统一封装（§3.12 AppButton）：四种变体，新增页面默认首选，禁止裸 `Box.clickable` 当按钮。
 */
enum class AppButtonVariant { Primary, FilledTonal, Outlined, Text }

/**
 * 统一按钮组件，支持四种变体（Primary / FilledTonal / Outlined / Text）。
 *
 * @param text 按钮文案。
 * @param onClick 点击回调。
 * @param modifier 外层修饰符。
 * @param variant 按钮变体，默认 Primary。
 * @param enabled 是否可用。
 * @since 0.1.0-experimental
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    enabled: Boolean = true,
) {
    when (variant) {
        AppButtonVariant.Primary -> Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text)
        }
        AppButtonVariant.FilledTonal -> FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text)
        }
        AppButtonVariant.Outlined -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text)
        }
        AppButtonVariant.Text -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text)
        }
    }
}

/** 按钮（带前导图标，用于"新建/导入"等带图入口）。  *
 * @since 0.1.0-experimental
 */
@Composable
fun AppIconButton(
    text: String,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    enabled: Boolean = true,
) {
    when (variant) {
        AppButtonVariant.Primary -> Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            IconButtonContent(text, leadingIcon)
        }
        AppButtonVariant.FilledTonal -> FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            IconButtonContent(text, leadingIcon)
        }
        AppButtonVariant.Outlined -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            IconButtonContent(text, leadingIcon)
        }
        AppButtonVariant.Text -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            IconButtonContent(text, leadingIcon)
        }
    }
}

/** 带图标按钮通用内容：前导图标 + 间距 + 文字。 */
@Composable
private fun RowScope.IconButtonContent(
    text: String,
    leadingIcon: @Composable () -> Unit,
) {
    leadingIcon()
    Spacer(Modifier.padding(start = AppSpacing.Sm))
    Text(text)
}

@Preview(showBackground = true, name = "AppButton - All Variants")
@Composable
private fun PreviewAppButton() {
    AppTheme {
        Column(
            modifier = Modifier.padding(AppSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
        ) {
            AppButton(text = "Primary", onClick = {}, variant = AppButtonVariant.Primary)
            AppButton(text = "FilledTonal", onClick = {}, variant = AppButtonVariant.FilledTonal)
            AppButton(text = "Outlined", onClick = {}, variant = AppButtonVariant.Outlined)
            AppButton(text = "Text", onClick = {}, variant = AppButtonVariant.Text)
            Spacer(Modifier.height(AppSpacing.Sm))
            AppIconButton(
                text = "带图标按钮",
                onClick = {},
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null)
                },
            )
        }
    }
}