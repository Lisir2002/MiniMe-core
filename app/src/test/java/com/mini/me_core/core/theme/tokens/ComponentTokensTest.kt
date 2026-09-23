package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Component Tokens 动态映射规则单元测试。
 *
 * 验证：
 * 1. CornerStyle → CornerScale 三档映射
 * 2. fontScale → 字号缩放
 * 3. fontWeightScale → FontWeight 档位映射
 * 4. Light/Dark 默认值差异（仅颜色，尺寸一致）
 * 5. withCornerScale 作用于所有组件的 cornerRadius 字段
 */
class ComponentTokensTest {

    // ── 1. CornerStyle → CornerScale 映射 ──

    @Test
    fun `CornerStyle ROUNDED 映射到 Rounded 档位`() {
        val scale = CornerScale.from(CornerStyle.ROUNDED)
        assertEquals(PrimitiveRadius.Xs, scale.xs)   // 4dp
        assertEquals(PrimitiveRadius.Sm, scale.sm)   // 6dp
        assertEquals(PrimitiveRadius.Md, scale.md)   // 8dp
        assertEquals(PrimitiveRadius.Lg, scale.lg)   // 10dp
        assertEquals(PrimitiveRadius.Xl, scale.xl)  // 12dp
        assertEquals(999.dp, scale.pill)             // pill 始终 999
    }

    @Test
    fun `CornerStyle Sharp 映射到全 0dp 档位`() {
        val scale = CornerScale.from(CornerStyle.Sharp)
        assertEquals(0.dp, scale.xs)
        assertEquals(0.dp, scale.sm)
        assertEquals(0.dp, scale.md)
        assertEquals(0.dp, scale.lg)
        assertEquals(0.dp, scale.xl)
        // pill 不随风格变
        assertEquals(999.dp, scale.pill)
    }

    @Test
    fun `CornerStyle Pill 映射到全 999dp 档位`() {
        val scale = CornerScale.from(CornerStyle.Pill)
        assertEquals(999.dp, scale.xs)
        assertEquals(999.dp, scale.sm)
        assertEquals(999.dp, scale.md)
        assertEquals(999.dp, scale.lg)
        assertEquals(999.dp, scale.xl)
        assertEquals(999.dp, scale.pill)
    }

    // ── 2. fontScale → 字号缩放 ──

    @Test
    fun `fontScale 1_0 不改变字号`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontScale(1.0f)
        assertEquals(base.text.bodyMediumFontSize, scaled.text.bodyMediumFontSize)
        assertEquals(base.button.fontSizeMedium, scaled.button.fontSizeMedium)
    }

    @Test
    fun `fontScale 1_2 放大字号`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontScale(1.2f)
        // bodyMedium base = 14.sp → 16.8.sp
        assertEquals(14.sp * 1.2f, scaled.text.bodyMediumFontSize)
        // button medium base = 14.sp → 16.8.sp
        assertEquals(14.sp * 1.2f, scaled.button.fontSizeMedium)
        // lineHeight 也同步缩放
        assertEquals(21.sp * 1.2f, scaled.text.bodyMediumLineHeight)
    }

    @Test
    fun `fontScale 0_8 缩小字号`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontScale(0.8f)
        assertEquals(16.sp * 0.8f, scaled.text.bodyLargeFontSize)
        assertEquals(24.sp * 0.8f, scaled.text.bodyLargeLineHeight)
    }

    // ── 3. fontWeightScale → FontWeight 档位映射 ──

    @Test
    fun `fontWeightScale 1_0 保持原始设计值`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontWeightScale(1.0f)
        // scale=1.0 时早返回，保持 base 设计值不变（titleLarge=SemiBold）
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, scaled.text.titleLargeFontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, scaled.button.fontWeight)
    }

    @Test
    fun `fontWeightScale 0_85 映射到 Normal`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontWeightScale(0.8f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, scaled.text.titleLargeFontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, scaled.button.fontWeight)
    }

    @Test
    fun `fontWeightScale 1_15 映射到 SemiBold`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontWeightScale(1.1f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, scaled.text.titleLargeFontWeight)
    }

    @Test
    fun `fontWeightScale 1_2 映射到 Bold`() {
        val base = ComponentTokens.Light
        val scaled = base.withFontWeightScale(1.2f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, scaled.text.titleLargeFontWeight)
    }

    // ── 4. Light/Dark 默认值 ──

    @Test
    fun `Light 和 Dark 尺寸一致，仅颜色不同`() {
        val light = ComponentTokens.Light
        val dark = ComponentTokens.Dark
        // 圆角一致
        assertEquals(light.card.cornerRadius, dark.card.cornerRadius)
        assertEquals(light.button.cornerRadius, dark.button.cornerRadius)
        // 字号一致
        assertEquals(light.text.bodyMediumFontSize, dark.text.bodyMediumFontSize)
        assertEquals(light.button.fontSizeMedium, dark.button.fontSizeMedium)
        // 颜色不同
        assertNotEquals(light.card.containerColor, dark.card.containerColor)
        assertNotEquals(light.text.titleLargeColor, dark.text.titleLargeColor)
    }

    // ── 5. withCornerScale 作用于所有组件 ──

    @Test
    fun `withCornerScale Sharp 使所有卡片圆角归 0`() {
        val base = ComponentTokens.Light
        val sharp = base.withCornerScale(CornerScale.Sharp)
        assertEquals(0.dp, sharp.card.cornerRadius)
        assertEquals(0.dp, sharp.button.cornerRadius)
        assertEquals(0.dp, sharp.input.cornerRadius)
        assertEquals(0.dp, sharp.toolCard.cornerRadius)
        assertEquals(0.dp, sharp.dialog.cornerRadius)
    }

    @Test
    fun `withCornerScale Pill 使所有圆角变胶囊`() {
        val base = ComponentTokens.Light
        val pill = base.withCornerScale(CornerScale.Pill)
        assertEquals(999.dp, pill.card.cornerRadius)
        assertEquals(999.dp, pill.button.cornerRadius)
        assertEquals(999.dp, pill.toolCard.cornerRadius)
    }

    @Test
    fun `用户气泡非对称圆角 - Rounded 保持 16_16_4_16`() {
        val base = ComponentTokens.Light
        val rounded = base.withCornerScale(CornerScale.Rounded)
        // Rounded 模式：右下小圆角 4dp（指向用户）
        assertEquals(16.dp, rounded.bubble.userBubbleCornerTopStart)
        assertEquals(16.dp, rounded.bubble.userBubbleCornerTopEnd)
        assertEquals(4.dp, rounded.bubble.userBubbleCornerBottomEnd)
        assertEquals(16.dp, rounded.bubble.userBubbleCornerBottomStart)
    }

    @Test
    fun `用户气泡非对称圆角 - Sharp 全 0`() {
        val base = ComponentTokens.Light
        val sharp = base.withCornerScale(CornerScale.Sharp)
        assertEquals(0.dp, sharp.bubble.userBubbleCornerTopStart)
        assertEquals(0.dp, sharp.bubble.userBubbleCornerTopEnd)
        assertEquals(0.dp, sharp.bubble.userBubbleCornerBottomEnd)
        assertEquals(0.dp, sharp.bubble.userBubbleCornerBottomStart)
    }

    @Test
    fun `用户气泡非对称圆角 - Pill 全 999`() {
        val base = ComponentTokens.Light
        val pill = base.withCornerScale(CornerScale.Pill)
        assertEquals(999.dp, pill.bubble.userBubbleCornerTopStart)
        assertEquals(999.dp, pill.bubble.userBubbleCornerTopEnd)
        assertEquals(999.dp, pill.bubble.userBubbleCornerBottomEnd)
        assertEquals(999.dp, pill.bubble.userBubbleCornerBottomStart)
    }

    // ── 6. pill 字段始终 999dp ──

    @Test
    fun `CornerScale pill 字段始终 999dp`() {
        assertEquals(999.dp, CornerScale.Rounded.pill)
        assertEquals(999.dp, CornerScale.Sharp.pill)
        assertEquals(999.dp, CornerScale.Pill.pill)
    }

    // ── 7. map() 非标圆角跟随风格切换 ──

    @Test
    fun `map Sharp 模式下非标值归零`() {
        assertEquals(0.dp, CornerScale.Sharp.map(18.dp))
        assertEquals(0.dp, CornerScale.Sharp.map(24.dp))
        assertEquals(0.dp, CornerScale.Sharp.map(28.dp))
        assertEquals(0.dp, CornerScale.Sharp.map(3.dp))
        assertEquals(0.dp, CornerScale.Sharp.map(2.dp))
        assertEquals(0.dp, CornerScale.Sharp.map(20.dp))
    }

    @Test
    fun `map Pill 模式下非标值变胶囊`() {
        assertEquals(999.dp, CornerScale.Pill.map(18.dp))
        assertEquals(999.dp, CornerScale.Pill.map(24.dp))
        assertEquals(999.dp, CornerScale.Pill.map(28.dp))
        assertEquals(999.dp, CornerScale.Pill.map(3.dp))
        assertEquals(999.dp, CornerScale.Pill.map(2.dp))
        assertEquals(999.dp, CornerScale.Pill.map(20.dp))
    }

    @Test
    fun `map Rounded 模式下透传原值`() {
        assertEquals(18.dp, CornerScale.Rounded.map(18.dp))
        assertEquals(24.dp, CornerScale.Rounded.map(24.dp))
        assertEquals(28.dp, CornerScale.Rounded.map(28.dp))
        assertEquals(3.dp, CornerScale.Rounded.map(3.dp))
        assertEquals(2.dp, CornerScale.Rounded.map(2.dp))
        assertEquals(20.dp, CornerScale.Rounded.map(20.dp))
        // 标准档位值也透传
        assertEquals(12.dp, CornerScale.Rounded.map(12.dp))
        assertEquals(4.dp, CornerScale.Rounded.map(4.dp))
    }
}
