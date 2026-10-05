package com.mini.me_core.core.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/** 工具页面定义。 */
@Stable
private data class ToolDef(
    val route: String,
    val label: String,
    val desc: String,
    val icon: ImageVector,
)

private val TOOLS = listOf(
    ToolDef("git", "Git", "版本控制与代码管理", Icons.Rounded.Code),
    ToolDef("browser", "浏览器", "内置浏览器与指纹管理", Icons.Rounded.Public),
    ToolDef("terminal", "终端", "命令行与包管理", Icons.Rounded.Terminal),
)

/** 主导航项定义。 */
@Stable
private data class NavDef(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavDef("chat", "对话", Icons.Rounded.Chat),
    NavDef("more", "更多", Icons.Rounded.Apps),
    NavDef("settings", "设置", Icons.Rounded.Settings),
)

/**
 * 浮动毛玻璃药丸底栏（全新设计）。
 *
 * 设计要点：
 * - 浮动药丸容器，不贴满宽度，悬浮于内容上方
 * - 毛玻璃半透明背景，柔和阴影
 * - 水滴形选中指示器，弹簧平滑滑动
 * - 中央「更多」按钮点击展开底部面板，展示工具卡片
 * - 底部面板毛玻璃风格，工具卡片带图标+名称+描述
 * - 所有操作带触觉反馈
 *
 * @param currentRoute 当前路由
 * @param onNavigate 导航回调
 * @param visible 是否可见（滚动感知隐藏预留）
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    visible: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val cornerRadius = LocalCornerRadius.current
    var panelExpanded by remember { mutableStateOf(false) }

    val isToolPage = currentRoute in TOOLS.map { it.route }
    val currentTool = TOOLS.firstOrNull { it.route == currentRoute }

    val selectedIndex = when {
        currentRoute == "chat" -> 0
        isToolPage -> 1
        currentRoute == "settings" -> 2
        else -> 0
    }

    val indicatorX by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "pill_indicator_x",
    )

    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bar_alpha",
    )

    val pillWidth = 232.dp
    val pillHeight = 60.dp
    val itemWidth = pillWidth / 3
    val indicatorWidth = 68.dp
    val indicatorHeight = 44.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = barAlpha },
    ) {
        // 背景遮罩
        AnimatedVisibility(
            visible = panelExpanded,
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { panelExpanded = false },
                    ),
            )
        }

        // 底部工具面板
        AnimatedVisibility(
            visible = panelExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(durationMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(durationMillis = 180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 84.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .padding(vertical = 20.dp, horizontal = 16.dp),
            ) {
                Text(
                    text = "工具面板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                )

                TOOLS.forEachIndexed { index, tool ->
                    val isActive = currentRoute == tool.route
                    val cardScale by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.98f,
                        animationSpec = spring(dampingRatio = 0.6f),
                        label = "tool_card_${tool.route}",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .scale(cardScale)
                            .clip(RoundedCornerShape(cornerRadius.md))
                            .background(
                                if (isActive)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(tool.route)
                                    panelExpanded = false
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(cornerRadius.sm))
                                .background(
                                    if (isActive)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.label,
                                tint = if (isActive) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tool.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        // 浮动药丸底栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .graphicsLayer {
                        shadowElevation = 12f
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                    },
            ) {
                // 水滴形选中指示器
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .offset(
                            x = (indicatorX * itemWidth.value + (itemWidth.value - indicatorWidth.value) / 2f).dp,
                            y = ((pillHeight.value - indicatorHeight.value) / 2f).dp,
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NAV_ITEMS.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.55f),
                            label = "nav_icon_scale_${item.key}",
                        )
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.6f,
                            animationSpec = tween(durationMillis = 200),
                            label = "nav_label_alpha_${item.key}",
                        )

                        val displayIcon = if (item.key == "more" && currentTool != null) {
                            currentTool.icon
                        } else {
                            item.icon
                        }
                        val displayLabel = if (item.key == "more" && currentTool != null) {
                            currentTool.label
                        } else {
                            item.label
                        }

                        Column(
                            modifier = Modifier
                                .width(itemWidth)
                                .height(pillHeight)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (item.key == "more") {
                                            panelExpanded = true
                                        } else {
                                            onNavigate(item.key)
                                        }
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = displayIcon,
                                contentDescription = displayLabel,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(iconScale),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
