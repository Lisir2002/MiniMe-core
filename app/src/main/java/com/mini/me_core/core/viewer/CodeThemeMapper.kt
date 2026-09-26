package com.mini.me_core.core.viewer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mini.me_core.core.viewer.native.dto.HighlightCategory

/**
 * 高亮语义类别 → 颜色映射。所有颜色从 [ColorScheme] 派生，自动适配明暗主题，不硬编码。
 */
object CodeThemeMapper {
    @Composable
    fun colorFor(category: HighlightCategory, scheme: ColorScheme = MaterialTheme.colorScheme): Color =
        when (category) {
            HighlightCategory.KEYWORD -> scheme.primary
            HighlightCategory.TYPE -> scheme.secondary
            HighlightCategory.FUNCTION -> scheme.tertiary
            // 字符串：用 tertiary 的弱化变体，明暗主题下都可读
            HighlightCategory.STRING -> scheme.tertiary.copy(alpha = 0.85f)
            HighlightCategory.NUMBER -> scheme.error
            HighlightCategory.COMMENT -> scheme.onSurfaceVariant
            HighlightCategory.OPERATOR -> scheme.onSurface
            HighlightCategory.PROPERTY -> scheme.secondary
            HighlightCategory.VARIABLE -> scheme.onSurface
            HighlightCategory.CONSTANT -> scheme.error
            HighlightCategory.TAG -> scheme.primary
            HighlightCategory.ATTRIBUTE -> scheme.tertiary
            HighlightCategory.PARAMETER -> scheme.onSurfaceVariant
            HighlightCategory.ANNOTATION -> scheme.tertiary
            HighlightCategory.NAMESPACE -> scheme.secondary
            HighlightCategory.PUNCTUATION -> scheme.onSurfaceVariant
            HighlightCategory.TEXT -> scheme.onSurface
            HighlightCategory.NONE -> scheme.onSurface
        }
}
