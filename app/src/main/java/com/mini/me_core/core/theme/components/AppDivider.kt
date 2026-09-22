package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一分割线组件。
 *
 * 基于现有代码中各处使用的 HorizontalDivider 归纳（CyberMenuRow 内部分割线、
 * CompactionDivider 等），使用 Semantic Token 颜色。
 *
 * @param modifier 修饰符
 * @param horizontalPadding 左右缩进（默认 0，用于从列表项图标后开始分割线）
 * @param thickness 分割线厚度（默认 1dp）
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    thickness: Dp = PrimitiveSpacing.Hairline,
) {
    val colors = LocalAppTheme.current.colors
    HorizontalDivider(
        modifier = modifier.then(
            if (horizontalPadding > 0.dp) Modifier.padding(start = horizontalPadding) else Modifier
        ),
        thickness = thickness,
        color = colors.borderMuted,
    )
}

/** Preview：展示默认分割线和带缩进的分割线。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 120)
@Composable
private fun AppDividerPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Default divider")
            Spacer(Modifier.height(Spacing.sm))
            AppDivider()
            Spacer(Modifier.height(Spacing.lg))
            Text("Indented divider (68dp, like CyberMenuRow)")
            Spacer(Modifier.height(Spacing.sm))
            AppDivider(horizontalPadding = 68.dp)
        }
    }
}
