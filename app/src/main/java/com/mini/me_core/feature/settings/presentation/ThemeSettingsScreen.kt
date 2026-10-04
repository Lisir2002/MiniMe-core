package com.mini.me_core.feature.settings.presentation
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.ui.rememberPersistentScrollState

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppDialogType
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
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
 * 主题设置页（优化版）。
 *
 * 优化内容：
 * - 移除直角预设，只保留圆角模式
 * - 重新排列布局：预览→外观模式→主题预设→显示偏好→颜色自定义→恢复出厂
 * - 动效演示返回页面时自动播放一次
 * - 颜色选择器采用 HSV 调色盘 + 预设色板组合
 * - 实时预览卡片滚动时置顶，置顶期间微折叠节省空间
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

    var showResetConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 预览卡片是否已滚出可视区域（用于触发置顶折叠态）
    val previewCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100
        }
    }

    // 动效返回播放触发计数
    var animationTrigger by remember { mutableStateOf(0) }

    // 页面可见时触发一次动效播放
    LaunchedEffect(Unit) {
        animationTrigger++
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── 置顶折叠态预览（仅在滚动后显示）──
        item(key = "sticky_preview") {
            androidx.compose.animation.AnimatedVisibility(
                visible = previewCollapsed,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                CollapsedPreviewBar(
                    preset = currentPreset,
                    isDark = isDark,
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                )
            }
        }

        // ── Section 1: 实时预览（完整态）──
        item(key = "full_preview") {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                AppSectionHeader(
                    title = stringResource(R.string.theme_section_preview),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ThemePreviewCard(
                    preset = currentPreset,
                    isDark = isDark,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Section 2: 外观模式 ──
        item(key = "appearance_mode") {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                AppSectionHeader(
                    title = stringResource(R.string.theme_section_appearance),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                AppearanceModeSelector(
                    selectedMode = ThemeMode.fromPersisted(settings.mode),
                    onModeSelected = { viewModel.setMode(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Section 3: 主题预设 ──
        item(key = "presets") {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                AppSectionHeader(
                    title = stringResource(R.string.theme_section_presets),
                    subtitle = stringResource(R.string.theme_section_presets_sub),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                PresetCarousel(
                    presets = ThemePresets.All,
                    selectedPresetId = settings.presetId,
                    onPresetSelected = { viewModel.setPreset(it) },
                )
            }
        }

        // ── Section 4: 显示偏好（圆角/字体/动效）──
        item(key = "display_prefs") {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                AppSectionHeader(
                    title = stringResource(R.string.theme_section_display),
                    subtitle = stringResource(R.string.theme_section_display_sub),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                DisplayPreferencesSection(
                    cornerRadius = settings.cornerRadius,
                    fontScale = settings.fontScale,
                    animationScale = settings.animationScale,
                    animationTrigger = animationTrigger,
                    onCornerRadiusChange = { viewModel.setCornerRadius(it) },
                    onFontScaleChange = { viewModel.setFontScale(it) },
                    onAnimationScaleChange = { viewModel.setAnimationScale(it) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Section 5: 颜色自定义 ──
        item(key = "custom_colors") {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                AppSectionHeader(
                    title = stringResource(R.string.theme_section_custom_colors),
                    subtitle = stringResource(R.string.theme_section_custom_colors_sub),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ColorCustomizationSection(
                    settings = settings,
                    currentColors = colors,
                    onColorPick = { field, color -> viewModel.updateCustomColor(field, color) },
                    onResetColor = { field -> viewModel.updateCustomColor(field, null) },
                    calculateContrast = { c1, c2 -> viewModel.calculateContrastRatio(c1, c2) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Section 6: 恢复出厂主题 ──
        item(key = "factory_reset") {
            Column(modifier = Modifier.padding(top = 32.dp, bottom = 48.dp)) {
                FactoryResetButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    // 恢复出厂确认 Dialog
    if (showResetConfirm) {
        AppDialog(
            title = stringResource(R.string.theme_reset_confirm_title),
            message = stringResource(R.string.theme_reset_confirm_msg),
            type = AppDialogType.Destructive,
            confirmText = stringResource(R.string.theme_reset_confirm_button),
            onDismiss = { showResetConfirm = false },
            onConfirm = { viewModel.resetToDefaults() },
        )
    }
}

// ──────────────────────────────────────────────
// 折叠态预览栏（置顶时显示）
// ──────────────────────────────────────────────

@Composable
private fun CollapsedPreviewBar(
    preset: ThemePreset,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val previewColors = if (isDark) preset.darkColors else preset.lightColors

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        variant = AppCardVariant.Default,
    ) {
        Row(
            modifier = Modifier
                .background(previewColors.surfaceCard)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 迷你品牌标识
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                    .background(previewColors.brandPrimary),
            )
            Text(
                text = "MiniMe",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = previewColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // 迷你用户气泡
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                    .background(previewColors.brandPrimary),
            )
            // 迷你助手气泡
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                    .background(previewColors.surfaceSunken),
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.theme_preview_expand),
                tint = previewColors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ──────────────────────────────────────────────
// 实时预览卡片（完整态）
// ──────────────────────────────────────────────

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
                        text = stringResource(R.string.theme_preview_msg_user),
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
                    text = stringResource(R.string.theme_preview_msg_assistant),
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
                        text = stringResource(R.string.theme_preview_tool_call),
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
                Text(
                    text = stringResource(R.string.theme_preview_input_hint),
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                    color = previewColors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
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
// 外观模式选择器
// ──────────────────────────────────────────────

@Composable
private fun AppearanceModeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors
    val modes = listOf(
        Triple(ThemeMode.AUTO, stringResource(R.string.theme_mode_auto), Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.LIGHT, stringResource(R.string.theme_mode_light), Icons.Rounded.LightMode),
        Triple(ThemeMode.DARK, stringResource(R.string.theme_mode_dark), Icons.Rounded.DarkMode),
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
// 预设卡片横向滚动
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
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxWidth(0.65f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                .background(preset.previewSurface),
                        )
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
                            text = stringResource(R.string.theme_preset_in_use),
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
// 颜色自定义区域
// ──────────────────────────────────────────────

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
                        Text(stringResource(R.string.theme_color_reset), fontSize = LocalComponentTokens.current.text.labelSmallFontSize, color = colors.textSecondary)
                    }
                }
            }
        }

        if (lowContrast) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(start = 38.dp, top = 2.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                    .background(colors.warning.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.warning),
                )
                Text(
                    text = stringResource(R.string.theme_color_low_contrast, contrastRatio),
                    fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
                    color = colors.warning,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// 颜色选择器 Dialog：HSV 调色盘 + 预设色板
// ──────────────────────────────────────────────

@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    var selectedColor by remember { mutableStateOf(initialColor) }

    // 预设色板
    val presetSwatches = listOf(
        Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFFF97316), Color(0xFFEAB308),
        Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6),
        Color(0xFFEC4899), Color(0xFF64748B), Color(0xFF1E293B), Color(0xFFFFFFFF),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_color_picker_title, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 当前选中色预览 + Hex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                            .background(selectedColor)
                            .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.md)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HEX",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = selectedColor.toHexString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                        )
                    }
                }

                // HSV 调色盘
                HsvColorPicker(
                    initialColor = selectedColor,
                    onColorChanged = { selectedColor = it },
                )

                // 预设色板标题
                Text(
                    text = stringResource(R.string.theme_color_presets),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )

                // 预设色板网格（4列3行）
                presetSwatches.chunked(4).forEach { rowColors ->
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
                Text(stringResource(R.string.common_confirm), color = colors.brandPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

// ──────────────────────────────────────────────
// HSV 调色盘组件
// ──────────────────────────────────────────────

private fun Color.toAndroidArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)

@Composable
private fun HsvColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toAndroidArgb(), hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }

    LaunchedEffect(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toAndroidArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    LaunchedEffect(hue, saturation, value) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        onColorChanged(Color(argb))
    }

    // 动态尺寸，避免硬编码导致光标偏移
    var panelWidth by remember { mutableStateOf(0) }
    var panelHeight by remember { mutableStateOf(0) }
    var hueBarWidth by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 饱和度/亮度面板：按下即响应，拖动跟手
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .onSizeChanged { panelWidth = it.width; panelHeight = it.height }
                .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.md))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        // 按下立即更新
                        saturation = (down.position.x / panelWidth).coerceIn(0f, 1f)
                        value = (1f - down.position.y / panelHeight).coerceIn(0f, 1f)
                        // 拖动持续更新
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                saturation = (change.position.x / panelWidth).coerceIn(0f, 1f)
                                value = (1f - change.position.y / panelHeight).coerceIn(0f, 1f)
                            }
                            if (event.changes.all { !it.pressed }) break
                        }
                    }
                },
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val hueColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, Color(hueColor)),
                        startX = 0f, endX = width,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f, endY = height,
                    ),
                    size = size,
                )
            }
            // 指示器：用动态尺寸像素计算，光标精确跟手
            val indicatorSizePx = with(LocalDensity.current) { 20.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (saturation * panelWidth - indicatorSizePx / 2).toInt(),
                            y = ((1f - value) * panelHeight - indicatorSizePx / 2).toInt(),
                        )
                    }
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
            )
        }

        // 色相条：按下即响应，拖动跟手
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { hueBarWidth = it.width }
                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.sm))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        hue = (down.position.x / hueBarWidth * 360f).coerceIn(0f, 360f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                hue = (change.position.x / hueBarWidth * 360f).coerceIn(0f, 360f)
                            }
                            if (event.changes.all { !it.pressed }) break
                        }
                    }
                },
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val hueColors = (0..360 step 30).map { h ->
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f)))
                }
                drawRect(
                    brush = Brush.horizontalGradient(colors = hueColors),
                    size = size,
                )
            }
            // 色相指示器：用动态尺寸像素计算，光标精确跟手
            val thumbWidthPx = with(LocalDensity.current) { 16.dp.toPx() }
            val thumbHeightPx = with(LocalDensity.current) { 28.dp.toPx() }
            val barHeightPx = with(LocalDensity.current) { 24.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (hue / 360f * hueBarWidth - thumbWidthPx / 2).toInt(),
                            y = (-(thumbHeightPx - barHeightPx) / 2).toInt(),
                        )
                    }
                    .width(16.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                    .background(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    ),
            )
        }
    }
}

/** 颜色转 hex 字符串（带 #） */
private fun Color.toHexString(): String =
    "#%02X%02X%02X".format(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

/** 颜色转短 hex（用于比较是否选中），忽略 alpha 差异。 */
private fun Color.toHexShort(): String =
    "#%02X%02X%02X".format(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

// ──────────────────────────────────────────────
// 显示偏好区域（移除直角预设，只保留圆角）
// ──────────────────────────────────────────────

@Composable
private fun DisplayPreferencesSection(
    cornerRadius: Float,
    fontScale: Float,
    animationScale: Float,
    animationTrigger: Int,
    onCornerRadiusChange: (Float) -> Unit,
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
            // 圆角预览 + 半径滑块
            Text(
                text = stringResource(R.string.theme_corner_radius),
                fontSize = LocalComponentTokens.current.text.titleSmallFontSize,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )

            // 圆角预览（跟随实际半径）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 小预览
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 32.dp)
                        .clip(RoundedCornerShape(cornerRadius.dp))
                        .background(colors.brandPrimary.copy(alpha = 0.2f))
                        .border(1.dp, colors.brandPrimary, RoundedCornerShape(cornerRadius.dp)),
                )
                // 中预览
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 32.dp)
                        .clip(RoundedCornerShape(cornerRadius.dp))
                        .background(colors.surfaceSunken)
                        .border(1.dp, colors.borderDefault, RoundedCornerShape(cornerRadius.dp)),
                )
                // 当前半径数值
                Text(
                    text = "%.0fdp".format(cornerRadius),
                    fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                    fontWeight = FontWeight.Bold,
                    color = colors.brandPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            // 圆角半径滑块
            SliderRow(
                label = stringResource(R.string.theme_corner_radius),
                value = cornerRadius,
                valueRange = 0f..24f,
                onValueChange = onCornerRadiusChange,
                valueLabel = "%.0fdp".format(cornerRadius),
            )

            Spacer(Modifier.height(4.dp))

            // 字体大小滑块
            SliderRow(
                label = stringResource(R.string.theme_font_size),
                value = fontScale,
                valueRange = 0.8f..1.4f,
                onValueChange = onFontScaleChange,
                valueLabel = "%.1fx".format(fontScale),
            )

            // 动效强度滑块
            SliderRow(
                label = stringResource(R.string.theme_animation_scale),
                value = animationScale,
                valueRange = 0f..1f,
                onValueChange = onAnimationScaleChange,
                valueLabel = if (animationScale == 0f) stringResource(R.string.common_close) else "%.0f%%".format(animationScale * 100),
            )

            // 动效预览（返回页面时自动播放）
            AnimationPreviewBox(trigger = animationTrigger)
        }
    }
}

// ──────────────────────────────────────────────
// 动效强度预览（支持触发计数）
// ──────────────────────────────────────────────

@Composable
private fun AnimationPreviewBox(
    trigger: Int = 0,
) {
    val colors = LocalAppTheme.current.colors
    val animScale = com.mini.me_core.core.theme.LocalAnimationScale.current

    var playCount by remember { mutableStateOf(0) }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val animDuration = (900L * animScale).toInt().coerceAtLeast(0)

    // 外部触发时播放
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            playCount++
        }
    }

    LaunchedEffect(playCount) {
        if (playCount == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = animDuration, easing = androidx.compose.animation.core.LinearEasing),
        )
        progress.snapTo(0f)
    }

    val p = progress.value
    val offsetX = 8f + p * 200f
    val wave = kotlin.math.sin((p * Math.PI).toDouble()).toFloat()
    val dotScale = 1f + 0.5f * wave
    val dotAlpha = 1f - 0.4f * wave

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.theme_animation_preview_hint),
            fontSize = LocalComponentTokens.current.text.labelSmallFontSize,
            color = colors.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderDefault, RoundedCornerShape(LocalCornerRadius.current.sm))
                .clickable { playCount++ },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = offsetX.dp)
                    .scale(dotScale)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.brandPrimary.copy(alpha = dotAlpha)),
            )
        }
    }
}

// ──────────────────────────────────────────────
// 恢复出厂主题按钮
// ──────────────────────────────────────────────

@Composable
private fun FactoryResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.error,
        ),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.error),
    ) {
        Text(
            text = stringResource(R.string.theme_factory_reset),
            fontWeight = FontWeight.Medium,
            fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
        )
    }
}

// ──────────────────────────────────────────────
// 通用：滑块行
// ──────────────────────────────────────────────

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    enabled: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = LocalComponentTokens.current.text.bodyMediumFontSize,
                color = if (enabled) colors.textPrimary else colors.textDisabled,
            )
            Text(
                text = valueLabel,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                color = if (enabled) colors.textSecondary else colors.textDisabled,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.brandPrimary,
                activeTrackColor = colors.brandPrimary,
                inactiveTrackColor = colors.borderDefault.copy(alpha = 0.3f),
                disabledThumbColor = colors.textDisabled,
                disabledActiveTrackColor = colors.textDisabled.copy(alpha = 0.4f),
                disabledInactiveTrackColor = colors.borderDefault.copy(alpha = 0.15f),
            ),
        )
    }
}
