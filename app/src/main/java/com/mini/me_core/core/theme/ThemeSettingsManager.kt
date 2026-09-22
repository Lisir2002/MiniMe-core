package com.mini.me_core.core.theme

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.core.theme.tokens.AppThemeState
import com.mini.me_core.core.theme.tokens.CustomColorFields
import com.mini.me_core.core.theme.tokens.SemanticColors
import com.mini.me_core.core.theme.tokens.ThemeMode
import com.mini.me_core.core.theme.tokens.ThemePreset
import com.mini.me_core.core.theme.tokens.ThemePresets
import com.mini.me_core.core.theme.tokens.ThemeSettings
import com.mini.me_core.core.theme.tokens.CornerStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题设置管理器（Phase 4 + Phase 5）。
 *
 * 负责：
 * 1. 从 KVStore 加载/持久化用户主题设置（外观模式 + 预设选择 + 自定义颜色 + 背景图 + 显示偏好）
 * 2. 对外暴露 [settings] StateFlow，UI 层订阅后实时重组
 * 3. 根据当前设置 + 暗色标志，计算最终的 [SemanticColors]（含自定义覆盖）
 * 4. 提供 WCAG 对比度计算工具
 * 5. 同步写入旧版 theme_mode 键，保持与现有 ThemeSettingsRepository 兼容
 */
@Singleton
class ThemeSettingsManager @Inject constructor(
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "ThemeSettingsManager"
        private const val NS = "theme_settings"
        private const val KEY_SETTINGS = "current"

        // 兼容旧版 ThemeSettingsRepository 的键
        private const val LEGACY_NS = "settings"
        private const val LEGACY_THEME_MODE_KEY = "theme_mode"

        /** WCAG AA 正文文本对比度最低要求 */
        const val WCAG_AA_NORMAL_TEXT_RATIO = 4.5
    }

    private val _settings = MutableStateFlow(loadFromKv())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    /**
     * 根据暗色标志和当前设置，计算应使用的 SemanticColors。
     * Phase 5：先从预设获取基础颜色，再用 customOverrides 覆盖指定字段。
     * 由 Composable 层在重组时调用。
     */
    fun resolveColors(isDark: Boolean): SemanticColors {
        val preset = ThemePresets.byId(_settings.value.presetId)
        val base = if (isDark) preset.darkColors else preset.lightColors
        // Phase 5：应用自定义颜色覆盖
        return CustomColorFields.applyOverrides(base, _settings.value.customOverrides)
    }

    /**
     * 根据暗色标志和当前设置，计算完整的 AppThemeState。
     * Phase 5：包含背景图 URI、遮罩浓度、卡片透明度。
     */
    fun resolveThemeState(isDark: Boolean): AppThemeState {
        val colors = resolveColors(isDark)
        val s = _settings.value
        return AppThemeState(
            colors = colors,
            isDark = isDark,
            backgroundImageUri = s.backgroundImage,
            backgroundScrim = s.backgroundMask,
            cardAlpha = s.cardOpacity,
        )
    }

    // ── Phase 4 已有方法（保持签名不变）──

    /** 切换外观模式（AUTO / LIGHT / DARK）。 */
    suspend fun setMode(mode: ThemeMode) {
        val updated = _settings.value.copy(mode = mode.name)
        persist(updated)
    }

    /** 切换主题预设。 */
    suspend fun setPreset(presetId: String) {
        val updated = _settings.value.copy(presetId = presetId)
        persist(updated)
    }

    /** 恢复默认设置（默认预设 + 跟随系统 + 清除所有自定义）。 */
    suspend fun resetToDefaults() {
        persist(ThemeSettings.DEFAULT)
    }

    // ── Phase 5 新增方法 ──

    /**
     * 更新或清除单个自定义颜色。
     *
     * @param field 颜色字段名（见 [CustomColorFields] 的常量）
     * @param color 要设置的颜色；传 null 表示清除该字段的自定义（恢复预设值）
     */
    suspend fun updateCustomColor(field: String, color: Color?) {
        val current = _settings.value
        val newOverrides = current.customOverrides.toMutableMap()
        if (color == null) {
            newOverrides.remove(field)
        } else {
            newOverrides[field] = color.toArgbHex()
        }
        persist(current.copy(customOverrides = newOverrides))
    }

    /**
     * 设置背景图 URI。传 null 清除背景图。
     */
    suspend fun setBackgroundImage(uri: String?) {
        persist(_settings.value.copy(backgroundImage = uri))
    }

    /**
     * 设置背景图遮罩浓度（0.0-1.0）。
     */
    suspend fun setBackgroundMask(mask: Float) {
        val clamped = mask.coerceIn(0f, 1f)
        persist(_settings.value.copy(backgroundMask = clamped))
    }

    /**
     * 设置卡片透明度（0.0-1.0）。
     */
    suspend fun setCardOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        persist(_settings.value.copy(cardOpacity = clamped))
    }

    /**
     * 设置圆角风格。
     */
    suspend fun setCornerStyle(style: CornerStyle) {
        persist(_settings.value.copy(cornerStyle = style.name))
    }

    /**
     * 设置字体大小缩放（0.8-1.4）。
     */
    suspend fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.4f)
        persist(_settings.value.copy(fontScale = clamped))
    }

    /**
     * 设置动效强度（0.0-1.0，0.0=关闭动效）。
     */
    suspend fun setAnimationScale(scale: Float) {
        val clamped = scale.coerceIn(0f, 1f)
        persist(_settings.value.copy(animationScale = clamped))
    }

    // ── 对比度计算（WCAG 公式）──

    /**
     * 计算两个颜色之间的 WCAG 对比度比值。
     *
     * 公式：(L1 + 0.05) / (L2 + 0.05)，其中 L1 为较亮颜色的相对亮度，L2 为较暗颜色的相对亮度。
     * 返回值范围 1.0（同色）到 21.0（黑白）。
     * WCAG AA 标准：正文文本 ≥ 4.5:1，大文本 ≥ 3.0:1。
     *
     * @param color1 第一个颜色
     * @param color2 第二个颜色
     * @return 对比度比值（Double，如 4.5、7.0）
     */
    fun calculateContrastRatio(color1: Color, color2: Color): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // ── 内部 ──

    /**
     * 计算颜色的相对亮度（WCAG 定义）。
     * 先将 sRGB 分量线性化，再按 Y = 0.2126*R + 0.7152*G + 0.0722*B 加权。
     */
    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** sRGB 分量线性化（WCAG 公式）。 */
    private fun linearize(component: Float): Double {
        val c = component.toDouble()
        return if (c <= 0.03928) {
            c / 12.92
        } else {
            Math.pow((c + 0.055) / 1.055, 2.4)
        }
    }

    /** Compose Color 转 ARGB hex 字符串（#AARRGGBB）。 */
    private fun Color.toArgbHex(): String {
        val a = (alpha * 255).toInt() and 0xFF
        val r = (red * 255).toInt() and 0xFF
        val g = (green * 255).toInt() and 0xFF
        val b = (blue * 255).toInt() and 0xFF
        return "#%02X%02X%02X%02X".format(a, r, g, b)
    }

    private fun loadFromKv(): ThemeSettings {
        val raw = kv.getString(NS, KEY_SETTINGS)
        val parsed = ThemeSettings.fromJson(raw)
        Log.d(TAG, "Loaded theme settings: mode=${parsed.mode}, preset=${parsed.presetId}, " +
            "overrides=${parsed.customOverrides.size}, bgImage=${parsed.backgroundImage != null}")
        return parsed
    }

    private suspend fun persist(newSettings: ThemeSettings) {
        // 写入新格式（完整 JSON）
        kv.putJson(NS, KEY_SETTINGS, ThemeSettings.toJson(newSettings))
        // 同步写入旧版 theme_mode 键，保持与现有 ThemeSettingsRepository 兼容
        kv.putString(LEGACY_NS, LEGACY_THEME_MODE_KEY, newSettings.mode)
        _settings.update { newSettings }
        Log.d(TAG, "Saved theme settings: mode=${newSettings.mode}, preset=${newSettings.presetId}, " +
            "overrides=${newSettings.customOverrides.size}")
    }
}
