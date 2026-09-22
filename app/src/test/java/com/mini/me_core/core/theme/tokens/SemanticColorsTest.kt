package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Semantic 语义色单元测试。
 * 验证亮色/暗色默认值与现有代码一致。
 */
class SemanticColorsTest {

    @Test
    fun `亮色品牌色与现有 LightColorScheme 一致`() {
        val colors = SemanticColors.Light
        // LightColorScheme.primary = #2563EB
        assertEquals(Color(0xFF2563EB), colors.brandPrimary)
        // LightColorScheme.onPrimary = #FFFFFF
        assertEquals(Color(0xFFFFFFFF), colors.onBrandPrimary)
        // LightColorScheme.primaryContainer = #DBEAFE
        assertEquals(Color(0xFFDBEAFE), colors.brandContainer)
    }

    @Test
    fun `暗色品牌色与现有 DarkColorScheme 一致`() {
        val colors = SemanticColors.Dark
        // DarkColorScheme.primary = #60A5FA
        assertEquals(Color(0xFF60A5FA), colors.brandPrimary)
        // DarkColorScheme.primaryContainer = #0F3A63
        assertEquals(Color(0xFF0F3A63), colors.brandContainer)
    }

    @Test
    fun `亮色状态色与现有代码一致`() {
        val colors = SemanticColors.Light
        // LightColorScheme.error = #DC2626
        assertEquals(Color(0xFFDC2626), colors.error)
        // LightColorScheme.errorContainer = #FEE2E2
        assertEquals(Color(0xFFFEE2E2), colors.errorContainer)
        // Brand.StatusGreen.Light = #16A34A
        assertEquals(Color(0xFF16A34A), colors.success)
    }

    @Test
    fun `暗色状态色与现有代码一致`() {
        val colors = SemanticColors.Dark
        // DarkColorScheme.error = #F87171
        assertEquals(Color(0xFFF87171), colors.error)
        // DarkColorScheme.errorContainer = #7F1D1D
        assertEquals(Color(0xFF7F1D1D), colors.errorContainer)
        // Brand.StatusGreen.Dark = #4ADE80
        assertEquals(Color(0xFF4ADE80), colors.success)
    }

    @Test
    fun `亮色 Surface 分层与现有背景一致`() {
        val colors = SemanticColors.Light
        // LightColorScheme.background = #F8FAFC
        assertEquals(Color(0xFFF8FAFC), colors.surfacePage)
        // LightColorScheme.surface = #FFFFFF
        assertEquals(Color(0xFFFFFFFF), colors.surfaceCard)
        // LightColorScheme.surfaceVariant = #F1F5F9
        assertEquals(Color(0xFFF1F5F9), colors.surfaceSunken)
    }

    @Test
    fun `暗色 Surface 多层级设计`() {
        val colors = SemanticColors.Dark
        // 页面背景最暗
        assertEquals(Color(0xFF0F172A), colors.surfacePage)
        // 卡片比页面亮一阶
        assertEquals(Color(0xFF1E293B), colors.surfaceCard)
        // 内嵌比卡片亮一阶
        assertEquals(Color(0xFF334155), colors.surfaceSunken)
        // 验证层级递增（页面 < 卡片 < 内嵌）
        assertNotEquals(colors.surfacePage, colors.surfaceCard)
        assertNotEquals(colors.surfaceCard, colors.surfaceSunken)
    }

    @Test
    fun `亮色文字色与现有代码一致`() {
        val colors = SemanticColors.Light
        // LightColorScheme.onBackground = #0F172A
        assertEquals(Color(0xFF0F172A), colors.textPrimary)
        // LightColorScheme.onSurfaceVariant = #475569
        assertEquals(Color(0xFF475569), colors.textSecondary)
    }

    @Test
    fun `暗色文字色与现有代码一致`() {
        val colors = SemanticColors.Dark
        // DarkColorScheme.onBackground = #E2E8F0
        assertEquals(Color(0xFFE2E8F0), colors.textPrimary)
        // DarkColorScheme.onSurfaceVariant = #94A3B8
        assertEquals(Color(0xFF94A3B8), colors.textSecondary)
    }

    @Test
    fun `亮色边框色与现有代码一致`() {
        val colors = SemanticColors.Light
        // LightColorScheme.outlineVariant = #E2E8F0
        assertEquals(Color(0xFFE2E8F0), colors.borderDefault)
    }

    @Test
    fun `暗色边框色与现有代码一致`() {
        val colors = SemanticColors.Dark
        // DarkColorScheme.outlineVariant = #334155
        assertEquals(Color(0xFF334155), colors.borderDefault)
    }

    @Test
    fun `亮色功能语义色与现有 ChatAccent 一致`() {
        val colors = SemanticColors.Light
        // ChatAccent.Build.light = #B45309
        assertEquals(Color(0xFFB45309), colors.accentBuild)
        // ChatAccent.Plan.light = #2563EB
        assertEquals(Color(0xFF2563EB), colors.accentPlan)
        // ChatAccent.Auto.light = #0D9488
        assertEquals(Color(0xFF0D9488), colors.accentAuto)
        // ChatAccent.Reasoning.light = #7C3AED
        assertEquals(Color(0xFF7C3AED), colors.accentReasoning)
        // ChatAccent.Skill.light = #DB2777
        assertEquals(Color(0xFFDB2777), colors.accentSkill)
    }

    @Test
    fun `暗色功能语义色与现有 ChatAccent 一致`() {
        val colors = SemanticColors.Dark
        // ChatAccent.Build.dark = #FBBF24
        assertEquals(Color(0xFFFBBF24), colors.accentBuild)
        // ChatAccent.Plan.dark = #60A5FA
        assertEquals(Color(0xFF60A5FA), colors.accentPlan)
        // ChatAccent.Auto.dark = #2DD4BF
        assertEquals(Color(0xFF2DD4BF), colors.accentAuto)
        // ChatAccent.Reasoning.dark = #A78BFA
        assertEquals(Color(0xFFA78BFA), colors.accentReasoning)
        // ChatAccent.Skill.dark = #F472B6
        assertEquals(Color(0xFFF472B6), colors.accentSkill)
    }

    @Test
    fun `亮色与暗色主题不同`() {
        val light = SemanticColors.Light
        val dark = SemanticColors.Dark
        assertNotEquals(light.brandPrimary, dark.brandPrimary)
        assertNotEquals(light.surfacePage, dark.surfacePage)
        assertNotEquals(light.textPrimary, dark.textPrimary)
        assertNotEquals(light.error, dark.error)
    }

    @Test
    fun `亮色 onAccent 色与现有 ChatAccent onLight 一致`() {
        val colors = SemanticColors.Light
        // ChatAccent 所有模式 onLight = White
        assertEquals(Color(0xFFFFFFFF), colors.onAccentBuild)
        assertEquals(Color(0xFFFFFFFF), colors.onAccentPlan)
        assertEquals(Color(0xFFFFFFFF), colors.onAccentAuto)
        assertEquals(Color(0xFFFFFFFF), colors.onAccentReasoning)
        assertEquals(Color(0xFFFFFFFF), colors.onAccentSkill)
    }

    @Test
    fun `暗色 onAccent 色与现有 ChatAccent onDark 一致`() {
        val colors = SemanticColors.Dark
        // ChatAccent.Build.onDark = #451A03
        assertEquals(Color(0xFF451A03), colors.onAccentBuild)
        // ChatAccent.Plan.onDark = #0B3B76
        assertEquals(Color(0xFF0B3B76), colors.onAccentPlan)
        // ChatAccent.Auto.onDark = #0B3B2E
        assertEquals(Color(0xFF0B3B2E), colors.onAccentAuto)
        // ChatAccent.Reasoning.onDark = #2E1065
        assertEquals(Color(0xFF2E1065), colors.onAccentReasoning)
        // ChatAccent.Skill.onDark = #500724
        assertEquals(Color(0xFF500724), colors.onAccentSkill)
    }

    @Test
    fun `亮色扩展状态色与现有代码一致`() {
        val colors = SemanticColors.Light
        // sky：AboutSection.skyAccent 亮色 = #0284C7
        assertEquals(Color(0xFF0284C7), colors.sky)
        // skyContainer：AboutSection.skyBg 亮色 = #F0F9FF
        assertEquals(Color(0xFFF0F9FF), colors.skyContainer)
        // orange：AboutSection.orangeText 亮色 = #B45309
        assertEquals(Color(0xFFB45309), colors.orange)
        // orangeContainer：AboutSection.orangeBg 亮色 = #FFF3E0
        assertEquals(Color(0xFFFFF3E0), colors.orangeContainer)
    }

    @Test
    fun `暗色扩展状态色与现有代码一致`() {
        val colors = SemanticColors.Dark
        // sky：AboutSection.skyAccent 暗色 = #38BDF8
        assertEquals(Color(0xFF38BDF8), colors.sky)
        // skyContainer：AboutSection.skyBg 暗色 = #0C4A6E
        assertEquals(Color(0xFF0C4A6E), colors.skyContainer)
        // orange：AboutSection.orangeText 暗色 = #FDBA74
        assertEquals(Color(0xFFFDBA74), colors.orange)
        // orangeContainer：AboutSection.orangeBg 暗色 = #7C2D12
        assertEquals(Color(0xFF7C2D12), colors.orangeContainer)
    }
}
