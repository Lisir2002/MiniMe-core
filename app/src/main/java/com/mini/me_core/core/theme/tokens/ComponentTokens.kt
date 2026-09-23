package com.mini.me_core.core.theme.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Component Tokens（第三层）：组件级样式令牌。
 *
 * 在 Primitive Tokens（原子值）和 Semantic Tokens（用途色）之上，
 * 按"组件类型"组织样式参数，使圆角/字号/阴影/字体粗细/内边距全部跟随主题和用户偏好。
 *
 * 不可变，主题切换或用户调整偏好时整体替换。
 * 通过 [LocalComponentTokens] 提供给组件树。
 */
@Immutable
data class ComponentTokens(
    // ── 卡片 ──
    val card: CardTokens,
    // ── 文字 ──
    val text: TextTokens,
    // ── 按钮 ──
    val button: ButtonTokens,
    // ── 输入框 ──
    val input: InputTokens,
    // ── 列表项 ──
    val listItem: ListItemTokens,
    // ── 标签 Chip ──
    val chip: ChipTokens,
    // ── 顶栏 ──
    val topAppBar: TopAppBarTokens,
    // ── 分割线 ──
    val divider: DividerTokens,
    // ── 徽章/状态点 ──
    val badge: BadgeTokens,
    // ── 空状态 ──
    val emptyState: EmptyStateTokens,
    // ── 对话框/Sheet ──
    val dialog: DialogTokens,
    // ── 工具卡片（对话页特有）──
    val toolCard: ToolCardTokens,
    // ── 气泡（对话页特有）──
    val bubble: BubbleTokens,
) {

    // ── 动态映射方法 ──

    /** 应用圆角风格：所有 cornerRadius 字段按 CornerStyle 映射 */
    fun withCornerScale(scale: CornerScale): ComponentTokens = copy(
        card = card.copy(cornerRadius = scale.xl),
        button = button.copy(cornerRadius = scale.xl),
        input = input.copy(cornerRadius = scale.lg),
        listItem = listItem.copy(iconBlockCornerRadius = scale.md),
        chip = chip.copy(cornerRadius = scale.md),
        dialog = dialog.copy(cornerRadius = scale.xl),
        toolCard = toolCard.copy(cornerRadius = scale.lg),
        bubble = bubble.copy(
            userBubbleCornerTopStart = scale.xxl,
            userBubbleCornerTopEnd = scale.xxl,
            // 用户气泡右下小圆角：Rounded 模式 4dp，Sharp/Pill 跟随整体
            userBubbleCornerBottomEnd = if (scale == CornerScale.Rounded) PrimitiveRadius.Xs else scale.xxl,
            userBubbleCornerBottomStart = scale.xxl,
        ),
    )

    /** 应用字体缩放：所有 fontSize/lineHeight 字段乘以 scale */
    fun withFontScale(scale: Float): ComponentTokens {
        if (scale == 1.0f) return this
        return copy(
            text = text.scaleFontSize(scale),
            button = button.copy(
                fontSizeSmall = button.fontSizeSmall * scale,
                fontSizeMedium = button.fontSizeMedium * scale,
                fontSizeLarge = button.fontSizeLarge * scale,
            ),
            input = input.copy(fontSize = input.fontSize * scale),
            listItem = listItem.copy(
                titleFontSize = listItem.titleFontSize * scale,
                subtitleFontSize = listItem.subtitleFontSize * scale,
            ),
            chip = chip.copy(fontSize = chip.fontSize * scale),
            topAppBar = topAppBar.copy(titleFontSize = topAppBar.titleFontSize * scale),
            badge = badge.copy(badgeFontSize = badge.badgeFontSize * scale),
            emptyState = emptyState.copy(
                titleFontSize = emptyState.titleFontSize * scale,
                subtitleFontSize = emptyState.subtitleFontSize * scale,
            ),
            dialog = dialog.copy(titleFontSize = dialog.titleFontSize * scale),
            toolCard = toolCard.copy(
                titleFontSize = toolCard.titleFontSize * scale,
                outputFontSize = toolCard.outputFontSize * scale,
            ),
            bubble = bubble.copy(
                userBubbleFontSize = bubble.userBubbleFontSize * scale,
                userBubbleLineHeight = bubble.userBubbleLineHeight * scale,
            ),
        )
    }

    /**
     * 应用字体粗细缩放：按 scale 映射到最近的 FontWeight 档位。
     *
     * scale ≤ 0.85 → Normal (W400)
     * 0.85 < scale ≤ 1.0 → Medium (W500) 或保持原值
     * 1.0 < scale ≤ 1.15 → SemiBold (W600)
     * scale > 1.15 → Bold (W700)
     */
    fun withFontWeightScale(scale: Float): ComponentTokens {
        if (scale == 1.0f) return this
        val mapped = when {
            scale <= 0.85f -> FontWeight.Normal
            scale <= 1.0f -> FontWeight.Medium
            scale <= 1.15f -> FontWeight.SemiBold
            else -> FontWeight.Bold
        }
        return copy(
            text = text.copy(
                titleLargeFontWeight = mapped,
                titleMediumFontWeight = mapped,
                titleSmallFontWeight = mapped,
                bodyLargeFontWeight = mapped,
                bodyMediumFontWeight = mapped,
                bodySmallFontWeight = mapped,
                labelLargeFontWeight = mapped,
                labelSmallFontWeight = mapped,
            ),
            button = button.copy(fontWeight = mapped),
            listItem = listItem.copy(titleFontWeight = mapped),
            chip = chip.copy(fontWeight = mapped),
            topAppBar = topAppBar.copy(titleFontWeight = mapped),
            badge = badge.copy(badgeFontWeight = mapped),
            emptyState = emptyState.copy(titleFontWeight = mapped),
            dialog = dialog.copy(titleFontWeight = mapped),
            toolCard = toolCard.copy(titleFontWeight = mapped),
        )
    }

    /**
     * 应用卡片透明度：背景色字段注入 alpha。
     * 注意：实际使用时由组件读取 LocalAppTheme.current.cardAlpha，
     * 此处仅作为参考值记录，不直接修改颜色。
     */
    fun withCardAlpha(alpha: Float): ComponentTokens = this // 颜色 alpha 由组件在使用时注入

    companion object {
        /**
         * 亮色默认值。
         * 从现有代码逐字段提取，不凭空创造。
         */
        val Light = ComponentTokens(
            card = CardTokens(
                containerColor = SemanticColors.Light.surfaceCard,
                borderColor = SemanticColors.Light.borderDefault,
                borderWidth = 0.8.dp,
                cornerRadius = PrimitiveRadius.Xl, // 12dp
                shadowElevation = 0.5.dp,
                paddingHorizontal = PrimitiveSpacing.Lg,  // 16dp
                paddingVertical = PrimitiveSpacing.Md,    // 12dp
            ),
            text = TextTokens(
                titleLargeFontSize = 20.sp,
                titleLargeFontWeight = FontWeight.SemiBold,
                titleLargeColor = SemanticColors.Light.textPrimary,
                titleMediumFontSize = 16.sp,
                titleMediumFontWeight = FontWeight.SemiBold,
                titleMediumColor = SemanticColors.Light.textPrimary,
                titleSmallFontSize = 14.sp,
                titleSmallFontWeight = FontWeight.SemiBold,
                titleSmallColor = SemanticColors.Light.textPrimary,
                bodyLargeFontSize = 16.sp,
                bodyLargeFontWeight = FontWeight.Normal,
                bodyLargeLineHeight = 24.sp,
                bodyLargeColor = SemanticColors.Light.textPrimary,
                bodyMediumFontSize = 14.sp,
                bodyMediumFontWeight = FontWeight.Normal,
                bodyMediumLineHeight = 21.sp,
                bodyMediumColor = SemanticColors.Light.textPrimary,
                bodySmallFontSize = 12.sp,
                bodySmallFontWeight = FontWeight.Normal,
                bodySmallLineHeight = 16.sp,
                bodySmallColor = SemanticColors.Light.textSecondary,
                labelLargeFontSize = 14.sp,
                labelLargeFontWeight = FontWeight.Medium,
                labelLargeColor = SemanticColors.Light.textPrimary,
                labelSmallFontSize = 11.sp,
                labelSmallFontWeight = FontWeight.Medium,
                labelSmallColor = SemanticColors.Light.textTertiary,
            ),
            button = ButtonTokens(
                primaryContainerColor = SemanticColors.Light.brandPrimary,
                primaryContentColor = SemanticColors.Light.onBrandPrimary,
                secondaryContainerColor = SemanticColors.Light.brandContainer,
                secondaryContentColor = SemanticColors.Light.onBrandContainer,
                textButtonContentColor = SemanticColors.Light.brandPrimary,
                cornerRadius = PrimitiveRadius.Xl, // 12dp
                heightSmall = 32.dp,
                heightMedium = 40.dp,
                heightLarge = 48.dp,
                paddingHorizontal = PrimitiveSpacing.Lg, // 16dp
                fontSizeSmall = 12.sp,
                fontSizeMedium = 14.sp,
                fontSizeLarge = 16.sp,
                fontWeight = FontWeight.Bold,
                shadowElevation = PrimitiveElevation.Z1,
            ),
            input = InputTokens(
                backgroundColor = SemanticColors.Light.surfaceCard,
                borderColor = SemanticColors.Light.borderDefault,
                focusedBorderColor = SemanticColors.Light.borderFocus,
                cornerRadius = PrimitiveRadius.Lg, // 10dp
                height = PrimitiveSpacing.InputBarHeight, // 60dp
                paddingHorizontal = PrimitiveSpacing.Lg, // 16dp
                paddingVertical = PrimitiveSpacing.Md,   // 12dp
                fontSize = 16.sp,
                cursorColor = SemanticColors.Light.brandPrimary,
            ),
            listItem = ListItemTokens(
                minHeight = 56.dp,
                paddingHorizontal = PrimitiveSpacing.Lg,  // 16dp
                paddingVertical = PrimitiveSpacing.Md,      // 12dp
                iconBlockSize = 38.dp,
                iconBlockCornerRadius = PrimitiveRadius.Md, // 8dp
                iconSize = 20.dp,
                titleFontSize = 16.sp,
                titleFontWeight = FontWeight.SemiBold,
                subtitleFontSize = 14.sp,
                dividerThickness = 0.5.dp,
                dividerColor = SemanticColors.Light.borderMuted,
            ),
            chip = ChipTokens(
                defaultContainerColor = SemanticColors.Light.brandContainer,
                defaultContentColor = SemanticColors.Light.onBrandContainer,
                outlinedBorderColor = SemanticColors.Light.brandPrimary,
                outlinedBorderWidth = PrimitiveSpacing.Hairline, // 1dp
                cornerRadius = PrimitiveRadius.Md, // 8dp
                height = 28.dp,
                paddingHorizontal = PrimitiveSpacing.MdPlus, // 10dp
                paddingVertical = PrimitiveSpacing.Xs,      // 4dp
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                iconSize = 14.dp,
            ),
            topAppBar = TopAppBarTokens(
                height = 44.dp,
                backgroundColor = SemanticColors.Light.surfaceCard,
                titleFontSize = 16.sp,
                titleFontWeight = FontWeight.SemiBold,
                titleColor = SemanticColors.Light.textPrimary,
                iconButtonSize = 40.dp,
                iconSize = 20.dp,
            ),
            divider = DividerTokens(
                color = SemanticColors.Light.borderMuted,
                thickness = PrimitiveSpacing.Hairline, // 1dp
            ),
            badge = BadgeTokens(
                badgeCornerRadius = PrimitiveRadius.Pill, // 999dp
                badgeFontSize = 10.sp,
                badgeFontWeight = FontWeight.Bold,
                badgePaddingHorizontal = PrimitiveSpacing.MdPlus, // 10dp
                badgePaddingVertical = PrimitiveSpacing.Xs,      // 4dp
                statusDotSize = 8.dp,
            ),
            emptyState = EmptyStateTokens(
                iconSize = 48.dp,
                iconTintColor = SemanticColors.Light.textTertiary,
                titleFontSize = 16.sp,
                titleFontWeight = FontWeight.Bold,
                titleColor = SemanticColors.Light.textPrimary,
                subtitleFontSize = 14.sp,
                subtitleColor = SemanticColors.Light.textSecondary,
                spacingAfterIcon = PrimitiveSpacing.Lg, // 16dp
                spacingAfterTitle = PrimitiveSpacing.Sm, // 8dp
            ),
            dialog = DialogTokens(
                backgroundColor = SemanticColors.Light.surfaceOverlay,
                cornerRadius = PrimitiveRadius.Xl, // 12dp
                paddingHorizontal = PrimitiveSpacing.Xl, // 24dp
                paddingVertical = PrimitiveSpacing.Lg,   // 16dp
                titleFontSize = 18.sp,
                titleFontWeight = FontWeight.SemiBold,
                titleColor = SemanticColors.Light.textPrimary,
            ),
            toolCard = ToolCardTokens(
                backgroundColor = SemanticColors.Light.surfaceSunken,
                borderColor = SemanticColors.Light.borderDefault,
                borderWidth = 1.dp,
                cornerRadius = PrimitiveRadius.Lg, // 10dp
                paddingHorizontal = PrimitiveSpacing.Md,   // 12dp
                paddingVertical = PrimitiveSpacing.Sm,    // 8dp
                titleFontSize = 14.sp,
                titleFontWeight = FontWeight.Medium,
                titleColor = SemanticColors.Light.textPrimary,
                outputFontSize = 13.sp,
                outputColor = SemanticColors.Light.textSecondary,
                outputBackground = SemanticColors.Light.surfaceCard,
            ),
            bubble = BubbleTokens(
                userBubbleBackgroundColor = SemanticColors.Light.brandPrimary,
                userBubbleTextColor = SemanticColors.Light.onBrandPrimary,
                userBubbleCornerTopStart = PrimitiveRadius.Xxl, // 16dp
                userBubbleCornerTopEnd = PrimitiveRadius.Xxl,   // 16dp
                userBubbleCornerBottomEnd = PrimitiveRadius.Xs,  // 4dp（指向用户）
                userBubbleCornerBottomStart = PrimitiveRadius.Xxl, // 16dp
                userBubblePaddingHorizontal = PrimitiveSpacing.Md, // 12dp
                userBubblePaddingVertical = PrimitiveSpacing.Sm,   // 8dp
                userBubbleFontSize = 14.sp,
                userBubbleLineHeight = 20.sp,
                aiBubbleBackgroundColor = Color.Transparent,
                aiBubbleTextColor = SemanticColors.Light.textPrimary,
                aiBubblePaddingStart = PrimitiveSpacing.Md,  // 12dp
                aiBubblePaddingEnd = PrimitiveSpacing.Lg,    // 16dp
                aiBubblePaddingVertical = PrimitiveSpacing.Xs, // 4dp
                aiBubbleLeftBarWidth = 2.dp,
                aiBubbleLeftBarColor = SemanticColors.Light.brandPrimary,
            ),
        )

        /**
         * 暗色默认值。
         * 尺寸/圆角/字号与亮色一致，仅颜色从 SemanticColors.Dark 提取。
         */
        val Dark = Light.copy(
            card = Light.card.copy(
                containerColor = SemanticColors.Dark.surfaceCard,
                borderColor = SemanticColors.Dark.borderDefault,
                shadowElevation = PrimitiveElevation.Z2, // 暗色阴影更深
            ),
            text = Light.text.copy(
                titleLargeColor = SemanticColors.Dark.textPrimary,
                titleMediumColor = SemanticColors.Dark.textPrimary,
                titleSmallColor = SemanticColors.Dark.textPrimary,
                bodyLargeColor = SemanticColors.Dark.textPrimary,
                bodyMediumColor = SemanticColors.Dark.textPrimary,
                bodySmallColor = SemanticColors.Dark.textSecondary,
                labelLargeColor = SemanticColors.Dark.textPrimary,
                labelSmallColor = SemanticColors.Dark.textTertiary,
            ),
            button = Light.button.copy(
                primaryContainerColor = SemanticColors.Dark.brandPrimary,
                primaryContentColor = SemanticColors.Dark.onBrandPrimary,
                secondaryContainerColor = SemanticColors.Dark.brandContainer,
                secondaryContentColor = SemanticColors.Dark.onBrandContainer,
                textButtonContentColor = SemanticColors.Dark.brandPrimary,
            ),
            input = Light.input.copy(
                backgroundColor = SemanticColors.Dark.surfaceCard,
                borderColor = SemanticColors.Dark.borderDefault,
                focusedBorderColor = SemanticColors.Dark.borderFocus,
                cursorColor = SemanticColors.Dark.brandPrimary,
            ),
            listItem = Light.listItem.copy(
                dividerColor = SemanticColors.Dark.borderMuted,
            ),
            chip = Light.chip.copy(
                defaultContainerColor = SemanticColors.Dark.brandContainer,
                defaultContentColor = SemanticColors.Dark.onBrandContainer,
                outlinedBorderColor = SemanticColors.Dark.brandPrimary,
            ),
            topAppBar = Light.topAppBar.copy(
                backgroundColor = SemanticColors.Dark.surfaceCard,
                titleColor = SemanticColors.Dark.textPrimary,
            ),
            divider = Light.divider.copy(
                color = SemanticColors.Dark.borderMuted,
            ),
            emptyState = Light.emptyState.copy(
                iconTintColor = SemanticColors.Dark.textTertiary,
                titleColor = SemanticColors.Dark.textPrimary,
                subtitleColor = SemanticColors.Dark.textSecondary,
            ),
            dialog = Light.dialog.copy(
                backgroundColor = SemanticColors.Dark.surfaceOverlay,
                titleColor = SemanticColors.Dark.textPrimary,
            ),
            toolCard = Light.toolCard.copy(
                backgroundColor = SemanticColors.Dark.surfaceSunken,
                borderColor = SemanticColors.Dark.borderDefault,
                titleColor = SemanticColors.Dark.textPrimary,
                outputColor = SemanticColors.Dark.textSecondary,
                outputBackground = SemanticColors.Dark.surfaceCard,
            ),
            bubble = Light.bubble.copy(
                userBubbleBackgroundColor = SemanticColors.Dark.brandPrimary,
                userBubbleTextColor = SemanticColors.Dark.onBrandPrimary,
                aiBubbleTextColor = SemanticColors.Dark.textPrimary,
                aiBubbleLeftBarColor = SemanticColors.Dark.brandPrimary,
            ),
        )
    }
}

// ── 各组件令牌数据类 ──

@Immutable
data class CardTokens(
    val containerColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val cornerRadius: Dp,
    val shadowElevation: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
)

@Immutable
data class TextTokens(
    val titleLargeFontSize: TextUnit,
    val titleLargeFontWeight: FontWeight,
    val titleLargeColor: Color,
    val titleMediumFontSize: TextUnit,
    val titleMediumFontWeight: FontWeight,
    val titleMediumColor: Color,
    val titleSmallFontSize: TextUnit,
    val titleSmallFontWeight: FontWeight,
    val titleSmallColor: Color,
    val bodyLargeFontSize: TextUnit,
    val bodyLargeFontWeight: FontWeight,
    val bodyLargeLineHeight: TextUnit,
    val bodyLargeColor: Color,
    val bodyMediumFontSize: TextUnit,
    val bodyMediumFontWeight: FontWeight,
    val bodyMediumLineHeight: TextUnit,
    val bodyMediumColor: Color,
    val bodySmallFontSize: TextUnit,
    val bodySmallFontWeight: FontWeight,
    val bodySmallLineHeight: TextUnit,
    val bodySmallColor: Color,
    val labelLargeFontSize: TextUnit,
    val labelLargeFontWeight: FontWeight,
    val labelLargeColor: Color,
    val labelSmallFontSize: TextUnit,
    val labelSmallFontWeight: FontWeight,
    val labelSmallColor: Color,
) {
    /** 字体缩放：所有 fontSize/lineHeight 乘以 scale */
    fun scaleFontSize(scale: Float): TextTokens = copy(
        titleLargeFontSize = titleLargeFontSize * scale,
        titleMediumFontSize = titleMediumFontSize * scale,
        titleSmallFontSize = titleSmallFontSize * scale,
        bodyLargeFontSize = bodyLargeFontSize * scale,
        bodyLargeLineHeight = bodyLargeLineHeight * scale,
        bodyMediumFontSize = bodyMediumFontSize * scale,
        bodyMediumLineHeight = bodyMediumLineHeight * scale,
        bodySmallFontSize = bodySmallFontSize * scale,
        bodySmallLineHeight = bodySmallLineHeight * scale,
        labelLargeFontSize = labelLargeFontSize * scale,
        labelSmallFontSize = labelSmallFontSize * scale,
    )
}

@Immutable
data class ButtonTokens(
    val primaryContainerColor: Color,
    val primaryContentColor: Color,
    val secondaryContainerColor: Color,
    val secondaryContentColor: Color,
    val textButtonContentColor: Color,
    val cornerRadius: Dp,
    val heightSmall: Dp,
    val heightMedium: Dp,
    val heightLarge: Dp,
    val paddingHorizontal: Dp,
    val fontSizeSmall: TextUnit,
    val fontSizeMedium: TextUnit,
    val fontSizeLarge: TextUnit,
    val fontWeight: FontWeight,
    val shadowElevation: Dp,
)

@Immutable
data class InputTokens(
    val backgroundColor: Color,
    val borderColor: Color,
    val focusedBorderColor: Color,
    val cornerRadius: Dp,
    val height: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val fontSize: TextUnit,
    val cursorColor: Color,
)

@Immutable
data class ListItemTokens(
    val minHeight: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val iconBlockSize: Dp,
    val iconBlockCornerRadius: Dp,
    val iconSize: Dp,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val subtitleFontSize: TextUnit,
    val dividerThickness: Dp,
    val dividerColor: Color,
)

@Immutable
data class ChipTokens(
    val defaultContainerColor: Color,
    val defaultContentColor: Color,
    val outlinedBorderColor: Color,
    val outlinedBorderWidth: Dp,
    val cornerRadius: Dp,
    val height: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val iconSize: Dp,
)

@Immutable
data class TopAppBarTokens(
    val height: Dp,
    val backgroundColor: Color,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val titleColor: Color,
    val iconButtonSize: Dp,
    val iconSize: Dp,
)

@Immutable
data class DividerTokens(
    val color: Color,
    val thickness: Dp,
)

@Immutable
data class BadgeTokens(
    val badgeCornerRadius: Dp,
    val badgeFontSize: TextUnit,
    val badgeFontWeight: FontWeight,
    val badgePaddingHorizontal: Dp,
    val badgePaddingVertical: Dp,
    val statusDotSize: Dp,
)

@Immutable
data class EmptyStateTokens(
    val iconSize: Dp,
    val iconTintColor: Color,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val titleColor: Color,
    val subtitleFontSize: TextUnit,
    val subtitleColor: Color,
    val spacingAfterIcon: Dp,
    val spacingAfterTitle: Dp,
)

@Immutable
data class DialogTokens(
    val backgroundColor: Color,
    val cornerRadius: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val titleColor: Color,
)

@Immutable
data class ToolCardTokens(
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
    val cornerRadius: Dp,
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val titleColor: Color,
    val outputFontSize: TextUnit,
    val outputColor: Color,
    val outputBackground: Color,
)

@Immutable
data class BubbleTokens(
    val userBubbleBackgroundColor: Color,
    val userBubbleTextColor: Color,
    val userBubbleCornerTopStart: Dp,
    val userBubbleCornerTopEnd: Dp,
    val userBubbleCornerBottomEnd: Dp,
    val userBubbleCornerBottomStart: Dp,
    val userBubblePaddingHorizontal: Dp,
    val userBubblePaddingVertical: Dp,
    val userBubbleFontSize: TextUnit,
    val userBubbleLineHeight: TextUnit,
    val aiBubbleBackgroundColor: Color,
    val aiBubbleTextColor: Color,
    val aiBubblePaddingStart: Dp,
    val aiBubblePaddingEnd: Dp,
    val aiBubblePaddingVertical: Dp,
    val aiBubbleLeftBarWidth: Dp,
    val aiBubbleLeftBarColor: Color,
)

/**
 * CompositionLocal：当前 ComponentTokens。
 *
 * 用法：`val tokens = LocalComponentTokens.current`
 *       `tokens.card.cornerRadius`
 *
 * 由 AIEditorTheme 根据用户设置 + 暗色模式提供。
 */
val LocalComponentTokens = staticCompositionLocalOf { ComponentTokens.Light }

/**
 * CompositionLocal：字体粗细缩放比例。
 *
 * 由 AIEditorTheme 根据用户设置提供（1.0=标准，<1.0=更细，>1.0=更粗）。
 * 组件读取后映射到 FontWeight 档位。
 *
 * 注意：Markdown 渲染器的加粗（**bold**）是语义解析，不跟随此值。
 */
val LocalFontWeightScale = staticCompositionLocalOf { 1.0f }
