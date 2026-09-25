package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一错误状态组件。
 *
 * 图标 + 标题 + 描述 + 可选重试按钮，垂直居中。
 *
 * @param title 错误标题
 * @param message 错误描述
 * @param retryText 重试按钮文字（可选）
 * @param onRetry 重试回调（可选）
 * @param modifier 修饰符
 * @param icon 错误图标（默认 ErrorOutline）
 */
@Composable
fun AppErrorState(
    title: String,
    message: String? = null,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PrimitiveSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.error.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(PrimitiveSpacing.Lg))
        Text(
            text = title,
            fontSize = LocalComponentTokens.current.text.bodyLargeFontSize,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        if (message != null) {
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            Text(
                text = message,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                color = colors.textSecondary,
            )
        }
        if (retryText != null && onRetry != null) {
            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            TextButton(onClick = onRetry) {
                Text(retryText)
            }
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：错误状态示例。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppErrorStatePreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppErrorState(
                title = "加载失败",
                message = "网络连接异常，请检查后重试",
                retryText = "重试",
                onRetry = {},
            )
        }
    }
}
