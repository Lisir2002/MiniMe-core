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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
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

/** 底栏导航项定义。 */
@Stable
private data class NavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

// 左右各两个导航项，中间是凸起FAB
private val LEFT_ITEMS = listOf(
    NavItem("chat", "对话", Icons.Rounded.Chat),
    NavItem("files", "文件", Icons.Rounded.Folder),
)
private val RIGHT_ITEMS = listOf(
    NavItem("terminal", "终端", Icons.Rounded.Terminal),
    NavItem("settings", "设置", Icons.Rounded.Settings),
)

/**
 * 底部凹口导航栏 + 中央凸起圆形FAB。
 *
 * 设计要点：
 * - 白色底栏背景，顶部中央有平滑凹口（notch）
 * - 中央圆形FAB凸起于底栏上方，主题色填充，带阴影
 * - 左右各两个导航按钮（图标+文字），选中态主题色
 * - 点击中央FAB展开底部工具面板（Git/浏览器/终端）
 * - 面板毛玻璃风格，工具卡片带图标+名称+描述
 * - 所有操作带触觉反馈
 *
 * @param currentRoute 当前路由
 * @param onNavigate 导航回调
 * @param visible 是否可见
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    visible: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val cornerRadius = LocalCornerRadius.current
    val density = LocalDensity.current
    var panelExpanded by remember { mutableStateOf(false) }

    val isToolPage = currentRoute in TOOLS.map { it.route }
    val currentTool = TOOLS.firstOrNull { it.route == currentRoute }

    // 尺寸
    val barHeight = 64.dp
    val fabSize = 56.dp
    val fabRadiusPx = with(density) { fabSize.toPx() / 2f }
    val notchRadiusPx = fabRadiusPx + 4f // 凹口半径比FAB大4dp间距

    // 凹口底栏背景形状
    val notchedShape: Shape = remember(notchRadiusPx) {
        GenericShape { size, _ ->
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val nr = notchRadiusPx
            val cornerR = 8f

            moveTo(0f, 0f)
            // 到凹口左侧起点
            lineTo(centerX - nr - cornerR, 0f)
            // 左圆角过渡 + 凹口左半（控制点2在nr高度，确保最低点切线水平）
            cubicTo(
                centerX - nr + cornerR * 0.5f, 0f,
                centerX - nr * 0.5f, nr,
                centerX, nr
            )
            // 凹口右半 + 右圆角过渡（控制点1在nr高度，与左半切线共线）
            cubicTo(
                centerX + nr * 0.5f, nr,
                centerX + nr - cornerR * 0.5f, 0f,
                centerX + nr + cornerR, 0f
            )
            // 到右上角
            lineTo(width, 0f)
            // 右边
            lineTo(width, height)
            // 底边
            lineTo(0f, height)
            close()
        }
    }

    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bar_alpha",
    )

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
                    .padding(bottom = 100.dp)
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

                TOOLS.forEach { tool ->
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

        // 底栏整体容器（含FAB凸起空间）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(barHeight + fabSize / 2),
        ) {
            // 凹口底栏背景
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(notchedShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer {
                        shadowElevation = 8f
                        shape = notchedShape
                        clip = true
                    },
            ) {
                // 左右导航按钮
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧两个按钮
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LEFT_ITEMS.forEach { item ->
                            NavButton(
                                item = item,
                                isSelected = currentRoute == item.key,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(item.key)
                                },
                            )
                        }
                    }
                    // 中间占位（FAB凹口区域）
                    Spacer(modifier = Modifier.width(fabSize + 24.dp))
                    // 右侧两个按钮
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RIGHT_ITEMS.forEach { item ->
                            NavButton(
                                item = item,
                                isSelected = currentRoute == item.key,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(item.key)
                                },
                            )
                        }
                    }
                }
            }

            // 中央凸起FAB
            val fabScale by animateFloatAsState(
                targetValue = if (panelExpanded) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.55f),
                label = "fab_scale",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
                    .size(fabSize)
                    .scale(fabScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .graphicsLayer {
                        shadowElevation = 12f
                        shape = CircleShape
                        clip = true
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            panelExpanded = true
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = "更多",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/** 单个导航按钮。 */
@Composable
private fun NavButton(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "nav_icon_${item.key}",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(durationMillis = 200),
        label = "nav_label_${item.key}",
    )

    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            maxLines = 1,
        )
    }
}
