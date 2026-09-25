package com.mini.me_core.core.theme.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一搜索栏组件。
 *
 * 胶囊形全圆角搜索输入框，统一颜色和样式。
 *
 * @param query 搜索关键词
 * @param onQueryChange 关键词变化回调
 * @param placeholder 占位文字
 * @param modifier 修饰符
 * @param onClear 清除按钮回调
 * @param resultCount 结果数量（可选，显示在右侧）
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    resultCount: Int? = null,
) {
    val colors = LocalAppTheme.current.colors
    val hasText = query.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(LocalCornerRadius.current.pill),
        color = colors.surfaceSunken,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            Box(modifier = Modifier.weight(1f)) {
                if (!hasText) {
                    Text(
                        text = placeholder,
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.brandPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (resultCount != null && hasText) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$resultCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (hasText && onClear != null) {
                Spacer(Modifier.width(PrimitiveSpacing.Xs))
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清除",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：搜索栏示例。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 150)
@Composable
private fun AppSearchBarPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSearchBar(
                query = "",
                onQueryChange = {},
                placeholder = "搜索设置...",
            )
            AppSearchBar(
                query = "模型",
                onQueryChange = {},
                placeholder = "搜索设置...",
                resultCount = 5,
                onClear = {},
            )
        }
    }
}
