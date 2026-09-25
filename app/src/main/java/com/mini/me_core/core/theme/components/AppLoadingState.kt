package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一加载状态组件。
 *
 * 基于现有 AppLoadingState（AppComponents.kt）归纳，使用 Semantic Token 颜色。
 * 居中圆形进度条 + 可选加载文字。
 *
 * @param loadingText 可选加载文字说明
 */
@Composable
fun AppLoadingState(
    loadingText: String? = null,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PrimitiveSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = colors.brandPrimary.copy(alpha = 0.6f),
        )
        if (loadingText != null) {
            Spacer(Modifier.height(PrimitiveSpacing.Md))
            Text(
                text = loadingText,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = colors.textTertiary,
            )
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：带文字的加载状态。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 300)
@Composable
private fun AppLoadingStatePreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLoadingState(loadingText = "加载中...")
        }
    }
}

/** Preview：纯加载指示器。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun AppLoadingStateNoTextPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLoadingState()
        }
    }
}
