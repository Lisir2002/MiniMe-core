package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 统一输入框组件。
 *
 * 封装 Material3 OutlinedTextField，使用 Semantic Token 颜色。
 * 统一聚焦边框色、未聚焦边框色、圆角。
 *
 * @param value 输入值
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
 * @param label 标签文字
 * @param placeholder 占位文字
 * @param leadingIcon 前置图标
 * @param trailingIcon 后置图标
 * @param isPassword 是否密码输入框（默认 false）
 * @param errorMessage 错误信息（非空时显示错误态）
 * @param singleLine 是否单行（默认 true）
 * @param enabled 是否可用（默认 true）
 * @param keyboardOptions 键盘选项
 * @param keyboardActions 键盘动作
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    shape: Shape? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supportingText: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppTheme.current.colors
    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveVisualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }

    val effectiveTrailing: (@Composable () -> Unit)? = when {
        isPassword -> {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        tint = colors.textSecondary,
                    )
                }
            }
        }
        trailingIcon != null -> trailingIcon
        else -> null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = effectiveTrailing,
        isError = isError || errorMessage != null,
        visualTransformation = effectiveVisualTransformation,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape ?: androidx.compose.foundation.shape.RoundedCornerShape(LocalCornerRadius.current.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.brandPrimary,
            unfocusedBorderColor = colors.borderDefault,
            focusedLabelColor = colors.brandPrimary,
            unfocusedLabelColor = colors.textSecondary,
            focusedContainerColor = colors.surfaceCard,
            unfocusedContainerColor = colors.surfaceCard,
            errorBorderColor = colors.error,
        ),
        supportingText = supportingText ?: errorMessage?.let { { Text(it, color = colors.error) } },
    )
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：默认输入框。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun AppTextFieldPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = "",
                onValueChange = {},
                label = { Text("API Key") },
                placeholder = { Text("请输入 API Key") },
            )
            AppTextField(
                value = "secret123",
                onValueChange = {},
                label = { Text("密码") },
                isPassword = true,
            )
        }
    }
}
