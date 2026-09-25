package com.mini.me_core.feature.settings.presentation
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape as RCS
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.ThemeMode
import com.mini.me_core.core.theme.tokens.ThemePreset
import com.mini.me_core.core.theme.tokens.ThemePresets
import com.mini.me_core.core.theme.tokens.CustomColorFields
import com.mini.me_core.core.theme.tokens.CornerStyle
import com.mini.me_core.core.theme.tokens.SemanticColors

/**
 * 主题设置页（Phase 4 + Phase 5）。
 *
 * Phase 4：
 * - 外观模式选择（跟随系统 / 浅色 / 深色）
 * - 6 套主题预设横向卡片选择
 * - 实时预览区
 *
 * Phase 5 新增：
 * - 颜色自定义（9 项可自定义颜色 + 对比度警告）
 * - 显示偏好（圆角风格 + 字体大小 + 动效强度）
 * - 恢复出厂主题（底部红色按钮 + 确认弹窗）
 *
 * 注意：背景图功能（backgroundImage / backgroundMask / cardOpacity）数据层接口已预留，
 * UI 暂不暴露，后续单独调试后再开放。
 */
@Composable
fun ThemeSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemeSettingsViewModel = hiltViewModel(),
) {
    val colors = LocalAppTheme.current.colors
    val isDark = LocalAppTheme.current.isDark
    val settings by viewModel.settings.collectAsState()
    val currentPreset = ThemePresets.byId(settings.presetId)

    // 恢复出厂确认 Dialog 状态
    var showResetConfirm by remember { mutableStateOf(false) }

    // 问题4修复：去掉自带 Scaffold + AppTopAppBar，复用外层 SettingsScreen 的顶栏
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

            // ── Section 1: 实时预览 ──
            AppSectionHeader(title = "实时预览")
            ThemePreviewCard(
                preset = currentPreset,
                isDark = isDark,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Section 2: 外观模式 ──
            AppSectionHeader(title = "外观模式")
            AppearanceModeSelector(
                selectedMode = ThemeMode.fromPersisted(settings.mode),
                onModeSelected = { viewModel.setMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Section 3: 主题预设 ──
            AppSectionHeader(title = "主题预设", subtitle = "选择喜欢的配色方案")
            PresetCarousel(
                presets = ThemePresets.All,
                selectedPresetId = settings.presetId,
                onPresetSelected = { viewModel.setPreset(it) },
            )

            Spacer(Modifier.height(24.dp))

            // ── Section 4: 颜色自定义（Phase 5）──
            AppSectionHeader(title = "颜色自定义", subtitle = "覆盖预设颜色，点击圆点选择")
            ColorCustomizationSection(
                settings = settings,
                currentColors = colors,
                onColorPick = { field, color -> viewModel.updateCustomColor(field, color) },
                onResetColor = { field -> viewModel.updateCustomColor(field, null) },
                calculateContrast = { c1, c2 -> viewModel.calculateContrastRatio(c1, c2) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Section 5: 显示偏好（Phase 5）──
            AppSectionHeader(title = "显示偏好", subtitle = "圆角、字体大小和动效")
            DisplayPreferencesSection(
                cornerStyle = settings.cornerStyleEnum(),
                fontScale = settings.fontScale,
                animationScale = settings.animationScale,
                onCornerStyleChange = { viewModel.setCornerStyle(it) },
                onFontScaleChange = { viewModel.setFontScale(it) },
                onAnimationScaleChange = { viewModel.setAnimationScale(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(32.dp))

            // ── Section 7: 恢复出厂主题（Phase 5）──
            FactoryResetButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(48.dp))
    }

    // 恢复出厂确认 Dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复出厂主题？") },
            text = { Text("将清除所有自定义颜色和显示偏好，恢复到默认预设和外观模式。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefaults()
                    showResetConfirm = false
                }) {
                    Text("确认恢复", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ──────────────────────────────────────────────
// 实时预览卡片（Phase 4 保留）
// ──────────────────────────────────────────────

/**
 * 实时预览区：模拟聊天界面缩略图。
 */
@Composable
private fun ThemePreviewCard(
    preset: ThemePreset,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewColors = if (isDark) preset.darkColors else preset.lightColors

    AppCard(
        modifier = modifier,
        variant = AppCardVariant.Default,
    ) {
        Column(
            modifier = Modifier
                .background(previewColors.surfaceCard)
                .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "MiniMe",
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    fontWeight = FontWeight.Bold,
                    color = previewColors.textPrimary,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.xl))
                        .background(previewColors.brandPrimary),
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.xl))
                        .background(previewColors.brandPrimary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "帮我分析一下这个项目",
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = previewColors.onBrandPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.xl))
                    .background(previewColors.surfaceSunken)
                    .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "好的，我来分析项目结构。",
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    color = previewColors.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.xs))
                            .background(previewColors.success),
                    )
                    Text(
                        text = "读取文件列表",
                        fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                        color = previewColors.textSecondary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.map(20.dp)))
                    .background(previewColors.surfaceSunken)
                    .border(1.dp, previewColors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.map(20.dp)))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 左侧附件按钮占位
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                        .background(previewColors.surfaceCard)
                        .border(1.dp, previewColors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = previewColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 占位文字
                Text(
                    text = "输入消息...",
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    color = previewColors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                // 发送按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(previewColors.brandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↑",
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        color = previewColors.onBrandPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// 外观模式选择器（Phase 4 保留）
// ──────────────────────────────────────────────

@Composable
private fun AppearanceModeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors
    val modes = listOf(
        Triple(ThemeMode.AUTO, "跟随系统", Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.LIGHT, "浅色", Icons.Rounded.LightMode),
        Triple(ThemeMode.DARK, "深色", Icons.Rounded.DarkMode),
    )

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEach { (mode, label, icon) ->
                val isSelected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
                        .background(
                            if (isSelected) colors.brandContainer
                            else Color.Transparent
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) colors.onBrandContainer
                            else colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = label,
                            fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onBrandContainer
                            else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// 预设卡片横向滚动（Phase 4 保留）
// ──────────────────────────────────────────────

@Composable
private fun PresetCarousel(
    presets: List<ThemePreset>,
    selectedPresetId: String,
    onPresetSelected: (String) -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(presets) { preset ->
            val isSelected = preset.id == selectedPresetId
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.xl))
                    .background(colors.surfaceCard)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) colors.brandPrimary else colors.borderDefault,
                        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
                    )
                    .clickable { onPresetSelected(preset.id) },
            ) {
                Column(
                    modifier = Modifier
                        .padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                            .background(preset.previewBackground),
                    ) {
                        // 模拟卡片色块
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxWidth(0.65f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                .background(preset.previewSurface),
                        )
                        // 模拟主色气泡
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp)
                                .size(width = 28.dp, height = 12.dp)
                                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                .background(preset.previewPrimary),
                        )
                    }

                    Text(
                        text = preset.displayName,
                        fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = preset.description,
                        fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                }

                // "使用中" 角标：右上角叠加，不改变卡片高度
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                            .background(colors.brandPrimary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "使用中",
                            fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBrandPrimary,
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Phase 5: 颜色自定义区域
// ──────────────────────────────────────────────

/**
 * 颜色自定义区域：9 项可自定义颜色列表。
 * 每项显示颜色名 + 预览圆点 + 自定义/恢复按钮 + 对比度警告。
 */
@Composable
private fun ColorCustomizationSection(
    settings: com.mini.me_core.core.theme.tokens.ThemeSettings,
    currentColors: SemanticColors,
    onColorPick: (String, Color) -> Unit,
    onResetColor: (String) -> Unit,
    calculateContrast: (Color, Color) -> Double,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors
    var pendingColorField by remember { mutableStateOf<String?>(null) }

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CustomColorFields.ALL_KEYS.forEach { field ->
                val displayName = CustomColorFields.DISPLAY_NAMES[field] ?: field
                val isCustom = settings.customOverrides.containsKey(field)
                val currentColor = CustomColorFields.getColorForField(field, currentColors)
                val contrastBg = CustomColorFields.getContrastBackground(field, currentColors)
                val ratio = calculateContrast(currentColor, contrastBg)
                val lowContrast = ratio < 4.5

                ColorRow(
                    name = displayName,
                    color = currentColor,
                    isCustom = isCustom,
                    lowContrast = lowContrast,
                    contrastRatio = ratio,
                    onPick = { pendingColorField = field },
                    onReset = { onResetColor(field) },
                )
            }
        }
    }

    // 颜色选择器 Dialog
    pendingColorField?.let { field ->
        ColorPickerDialog(
            title = CustomColorFields.DISPLAY_NAMES[field] ?: field,
            initialColor = CustomColorFields.getColorForField(field, currentColors),
            onDismiss = { pendingColorField = null },
            onConfirm = { color ->
                onColorPick(field, color)
                pendingColorField = null
            },
        )
    }
}

/**
 * 单个颜色行：名称 + 圆点 + 对比度警告 + 自定义/恢复按钮。
 */
@Composable
private fun ColorRow(
    name: String,
    color: Color,
    isCustom: Boolean,
    lowContrast: Boolean,
    contrastRatio: Double,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                // 颜色预览圆点（点击弹出选择器）
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, colors.borderDefault, CircleShape)
                        .clickable { onPick() },
                )
                Text(
                    text = name,
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isCustom) {
                    TextButton(onClick = onReset) {
                        Text("恢复", fontSize = LocalComponentTokens.current.text.labelSmallFontSize, color = colors.textSecondary)
                    }
                }
            }
        }

        // 对比度警告（WCAG AA: 4.5:1）
        if (lowContrast) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 38.dp, top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.warning),
                )
                Text(
                    text = "对比度不足（%.1f:1），可能影响可读性".format(contrastRatio),
                    fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                    color = colors.warning,
                )
            }
        }
    }
}

/**
 * 颜色选择器 Dialog：预设色板 + 常用色快速选择。
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    var selectedColor by remember { mutableStateOf(initialColor) }

    // 预设色板（每行 5 个，共 4 行 = 20 色）
    val presetSwatches = listOf(
        // 红色系
        Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFFB91C1C), Color(0xFFF87171), Color(0xFFFECACA),
        // 橙色系
        Color(0xFFF97316), Color(0xFFEA580C), Color(0xFFD97706), Color(0xFFFB923C), Color(0xFFFED7AA),
        // 黄色系
        Color(0xFFEAB308), Color(0xFFCA8A04), Color(0xFFFACC15), Color(0xFFFEF08A), Color(0xFFFDE68A),
        // 绿色系
        Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF15803D), Color(0xFF4ADE80), Color(0xFFBBF7D0),
        // 青色系
        Color(0xFF06B6D4), Color(0xFF0891B2), Color(0xFF0E7490), Color(0xFF22D3EE), Color(0xFFA5F3FC),
        // 蓝色系
        Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFFBFDBFE),
        // 紫色系
        Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFF6D28D9), Color(0xFFA78BFA), Color(0xFFDDD6FE),
        // 粉色系
        Color(0xFFEC4899), Color(0xFFDB2777), Color(0xFFBE185D), Color(0xFFF472B6), Color(0xFFFBCFE8),
        // 灰阶
        Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF0F172A),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择$title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 当前选中色预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                        .background(selectedColor)
                        .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.md)),
                )

                // 色板网格（5列）
                presetSwatches.chunked(5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowColors.forEach { swatch ->
                            val isSelected = selectedColor.toHexShort() == swatch.toHexShort()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                    .background(swatch)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) colors.brandPrimary else colors.borderDefault,
                                        shape = RoundedCornerShape(LocalCornerRadius.current.sm),
                                    )
                                    .clickable { selectedColor = swatch },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) {
                Text("确定", color = colors.brandPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 颜色转短 hex（用于比较是否选中），忽略 alpha 差异。 */
private fun Color.toHexShort(): String =
    "#%02X%02X%02X".format(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

// ──────────────────────────────────────────────
// Phase 5: 显示偏好区域
// ──────────────────────────────────────────────

/**
 * 显示偏好区域：圆角风格选择 + 字体大小滑块 + 动效强度滑块。
 */
@Composable
private fun DisplayPreferencesSection(
    cornerStyle: CornerStyle,
    fontScale: Float,
    animationScale: Float,
    onCornerStyleChange: (CornerStyle) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onAnimationScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 圆角风格三选一
            Text(
                text = "圆角风格",
                fontSize = LocalComponentTokens.current.text.titleSmallFontSize,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val styles = listOf(
                    Triple(CornerStyle.ROUNDED, "圆角", RoundedCornerShape(LocalCornerRadius.current.xl)),
                    Triple(CornerStyle.Sharp, "直角", RoundedCornerShape(0.dp)),
                    Triple(CornerStyle.Pill, "胶囊", RoundedCornerShape(999.dp)),
                )
                styles.forEach { (style, label, shape) ->
                    val isSelected = style == cornerStyle
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
                            .background(if (isSelected) colors.brandContainer else Color.Transparent)
                            .clickable { onCornerStyleChange(style) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 预览形状
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 24.dp)
                                .clip(shape)
                                .background(if (isSelected) colors.onBrandContainer else colors.surfaceSunken)
                                .border(1.dp, colors.borderDefault, shape),
                        )
                        Text(
                            text = label,
                            fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onBrandContainer else colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 字体大小滑块
            SliderRow(
                label = "字体大小",
                value = fontScale,
                valueRange = 0.8f..1.4f,
                onValueChange = onFontScaleChange,
                valueLabel = "%.1fx".format(fontScale),
            )

            // 动效强度滑块
            SliderRow(
                label = "动效强度",
                value = animationScale,
                valueRange = 0f..1f,
                onValueChange = onAnimationScaleChange,
                valueLabel = if (animationScale == 0f) "关闭" else "%.0f%%".format(animationScale * 100),
            )

            // 动效预览：点击播放动画，时长跟随 animationScale
            AnimationPreviewBox()
        }
    }
}

// ──────────────────────────────────────────────
// Phase 5: 动效强度预览
// ──────────────────────────────────────────────

/**
 * 动效强度预览：点击后播放一段横向滑动+渐隐动画，动画时长跟随 LocalAnimationScale。
 * 0% 时直接跳变（无动画），100% 时最慢最丝滑。
 */
@Composable
private fun AnimationPreviewBox() {
    val colors = LocalAppTheme.current.colors
    val animScale = com.mini.me_core.core.theme.LocalAnimationScale.current

    var playTrigger by remember { mutableStateOf(false) }
    val animDuration = (600L * animScale).toInt().coerceAtLeast(0)

    val offsetX by animateFloatAsState(
        targetValue = if (playTrigger) 180f else 0f,
        animationSpec = tween(durationMillis = animDuration),
        label = "previewOffset",
    )
    val alpha by animateFloatAsState(
        targetValue = if (playTrigger) 0.3f else 1f,
        animationSpec = tween(durationMillis = animDuration),
        label = "previewAlpha",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "点击预览动效",
            fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
            color = colors.textTertiary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.sm))
                .clickable { playTrigger = !playTrigger },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp + offsetX.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.brandPrimary.copy(alpha = alpha)),
            )
        }
    }
}

// ──────────────────────────────────────────────
// Phase 5: 恢复出厂主题按钮
// ──────────────────────────────────────────────

/**
 * 页面底部红色"恢复出厂主题"按钮。
 */
@Composable
private fun FactoryResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.error.copy(alpha = 0.1f),
            contentColor = colors.error,
        ),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
    ) {
        Text(
            text = "恢复出厂主题",
            fontWeight = FontWeight.Medium,
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
        )
    }
}

// ──────────────────────────────────────────────
// 通用：滑块行
// ──────────────────────────────────────────────

/**
 * 通用滑块行：标签 + 当前值 + Slider。
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
) {
    val colors = LocalAppTheme.current.colors

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, fontSize = LocalComponentTokens.current.text.bodyMediumFontSize, color = colors.textPrimary)
            Text(text = valueLabel, fontSize = LocalComponentTokens.current.text.bodySmallFontSize, color = colors.textSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.brandPrimary,
                activeTrackColor = colors.brandPrimary,
            ),
        )
    }
}
