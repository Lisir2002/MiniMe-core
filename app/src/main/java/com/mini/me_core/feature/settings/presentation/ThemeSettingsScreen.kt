package com.mini.me_core.feature.settings.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.core.theme.AppTopAppBar
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonVariant
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

/**
 * 主题设置页（Phase 4 第一期）。
 *
 * 包含：
 * - 外观模式选择（跟随系统 / 浅色 / 深色）
 * - 6 套主题预设横向卡片选择
 * - 实时预览区（模拟聊天界面）
 * - 恢复默认按钮
 *
 * 所有颜色通过 [LocalAppTheme] 获取，预设切换时全局主题自动重组。
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

    Scaffold(
        containerColor = colors.surfacePage,
        contentColor = colors.textPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = "主题与外观",
                onNavigateBack = onNavigateBack,
            ) {
                TextButton(onClick = { viewModel.resetToDefaults() }) {
                    Text(
                        text = "恢复默认",
                        color = colors.brandPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfacePage)
                .padding(padding)
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

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ──────────────────────────────────────────────
// 实时预览卡片
// ──────────────────────────────────────────────

/**
 * 实时预览区：模拟聊天界面缩略图。
 *
 * 直接使用所选预设的颜色构建迷你聊天气泡 + 工具块，
 * 让用户在调整时立即看到效果。
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部模拟 AppBar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "MiniMe",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = previewColors.textPrimary,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColors.brandPrimary),
                )
            }

            Spacer(Modifier.height(4.dp))

            // 用户消息气泡（右对齐，品牌色）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColors.brandPrimary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "帮我分析一下这个项目",
                        fontSize = 12.sp,
                        color = previewColors.onBrandPrimary,
                    )
                }
            }

            // AI 回复气泡（左对齐，卡片背景）
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColors.surfaceSunken)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "好的，我来分析项目结构。",
                    fontSize = 12.sp,
                    color = previewColors.textPrimary,
                )
                // 工具块
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(previewColors.success),
                    )
                    Text(
                        text = "读取文件列表",
                        fontSize = 10.sp,
                        color = previewColors.textSecondary,
                    )
                }
            }

            // 输入栏模拟
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(previewColors.surfaceSunken)
                    .border(1.dp, previewColors.borderDefault, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "  输入消息...",
                    fontSize = 12.sp,
                    color = previewColors.textTertiary,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(previewColors.brandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↑",
                        fontSize = 12.sp,
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

/**
 * 外观模式三选一（跟随系统 / 浅色 / 深色）。
 */
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
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEach { (mode, label, icon) ->
                val isSelected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
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
                            fontSize = 12.sp,
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

/**
 * 6 套主题预设横向滚动卡片。
 * 每个卡片显示预设名称 + 3 个迷你色块预览。
 */
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
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceCard)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) colors.brandPrimary else colors.borderDefault,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onPresetSelected(preset.id) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 迷你配色预览（3 个色块）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(preset.previewBackground),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(preset.previewSurface),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(preset.previewPrimary),
                    )
                }

                Text(
                    text = preset.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = preset.description,
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                )

                if (isSelected) {
                    AppChip(
                        text = "使用中",
                        variant = AppChipVariant.Filled,
                        chipColor = AppChipColor.Primary,
                    )
                }
            }
        }
    }
}
