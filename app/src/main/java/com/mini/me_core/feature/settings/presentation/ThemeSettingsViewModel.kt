package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.mini.me_core.core.theme.ThemeSettingsManager
import com.mini.me_core.core.theme.tokens.CornerStyle
import com.mini.me_core.core.theme.tokens.ThemeMode
import com.mini.me_core.core.theme.tokens.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主题设置页 ViewModel（Phase 4 + Phase 5）。
 *
 * 桥接 UI 层和 [ThemeSettingsManager]，提供 StateFlow 供 Composable 订阅。
 * 不修改任何现有 ViewModel，仅作为新增文件。
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val themeManager: ThemeSettingsManager,
) : ViewModel() {

    /** 当前主题设置（模式 + 预设 ID + 自定义颜色 + 背景图 + 显示偏好）。 */
    val settings: StateFlow<ThemeSettings> = themeManager.settings

    // ── Phase 4 已有方法 ──

    /** 切换外观模式。 */
    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { themeManager.setMode(mode) }
    }

    /** 切换主题预设。 */
    fun setPreset(presetId: String) {
        viewModelScope.launch { themeManager.setPreset(presetId) }
    }

    /** 恢复默认（Phase 5：清除所有自定义）。 */
    fun resetToDefaults() {
        viewModelScope.launch { themeManager.resetToDefaults() }
    }

    // ── Phase 5 新增方法 ──

    /**
     * 更新或清除单个自定义颜色。
     * @param field 颜色字段名（见 CustomColorFields 常量）
     * @param color 新颜色；null 表示清除自定义
     */
    fun updateCustomColor(field: String, color: Color?) {
        viewModelScope.launch { themeManager.updateCustomColor(field, color) }
    }

    /** 设置背景图 URI（null 清除）。 */
    fun setBackgroundImage(uri: String?) {
        viewModelScope.launch { themeManager.setBackgroundImage(uri) }
    }

    /** 设置背景图遮罩浓度（0.0-1.0）。 */
    fun setBackgroundMask(mask: Float) {
        viewModelScope.launch { themeManager.setBackgroundMask(mask) }
    }

    /** 设置卡片透明度（0.0-1.0）。 */
    fun setCardOpacity(opacity: Float) {
        viewModelScope.launch { themeManager.setCardOpacity(opacity) }
    }

    /** 设置圆角风格。 */
    fun setCornerStyle(style: CornerStyle) {
        viewModelScope.launch { themeManager.setCornerStyle(style) }
    }

    /** 设置字体大小缩放（0.8-1.4）。 */
    fun setFontScale(scale: Float) {
        viewModelScope.launch { themeManager.setFontScale(scale) }
    }

    /** 设置动效强度（0.0-1.0）。 */
    fun setAnimationScale(scale: Float) {
        viewModelScope.launch { themeManager.setAnimationScale(scale) }
    }

    /**
     * 计算两个颜色的 WCAG 对比度比值。
     * 委托给 [ThemeSettingsManager.calculateContrastRatio]。
     */
    fun calculateContrastRatio(color1: Color, color2: Color): Double =
        themeManager.calculateContrastRatio(color1, color2)
}
