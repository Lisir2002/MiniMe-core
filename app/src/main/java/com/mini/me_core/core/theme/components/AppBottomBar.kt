package com.mini.me_core.core.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val LEFT_ITEM = NavItem("chat", "对话", Icons.Rounded.Chat)
private val RIGHT_ITEM = NavItem("settings", "设置", Icons.Rounded.Settings)

/**
 * 底部托举式导航栏 + 中央水晶球FAB。
 *
 * 设计要点：
 * - 仅三个入口：对话（左）、水晶球更多（中）、设置（右）
 * - 底栏高度56dp，顶部中央半圆形缺口深度30dp，双手托举水晶球
 * - 水晶球FAB沉底，底部与底栏底部对齐，不占用内容区域额外高度
 * - 水晶球效果：径向渐变球体、内部旋转光泽、顶部高光、8秒缓慢自转
 * - 点击水晶球展开底部工具面板（Git/浏览器/终端）
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

    // 尺寸
    val barHeight = 56.dp
    val fabSize = 56.dp
    val fabRadiusPx = with(density) { fabSize.toPx() / 2f }
    val notchRadiusPx = fabRadiusPx + 2f // 缺口半径比FAB大2dp

    // 托举缺口底栏背景形状（顶部中央半圆形缺口）
    val notchedShape: Shape = remember(notchRadiusPx) {
        GenericShape { size, _ ->
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val nr = notchRadiusPx

            moveTo(0f, 0f)
            lineTo(centerX - nr, 0f)
            // 半圆形缺口（从180度顺时针扫180度，向下凹陷）
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = centerX - nr,
                    top = -nr,
                    right = centerX + nr,
                    bottom = nr
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(width, 0f)
            lineTo(width, height)
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
                    .padding(bottom = 80.dp)
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

        // 底栏整体容器
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(barHeight),
        ) {
            // 托举缺口底栏背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(notchedShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer {
                        shadowElevation = 6f
                        shape = notchedShape
                        clip = true
                    },
            )

            // 左侧对话按钮
            NavButton(
                item = LEFT_ITEM,
                isSelected = currentRoute == LEFT_ITEM.key,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate(LEFT_ITEM.key)
                },
            )

            // 右侧设置按钮
            NavButton(
                item = RIGHT_ITEM,
                isSelected = currentRoute == RIGHT_ITEM.key,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate(RIGHT_ITEM.key)
                },
            )

            // 中央水晶球FAB（沉底，底部与底栏底部对齐）
            CrystalBallFab(
                expanded = panelExpanded,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    panelExpanded = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(fabSize),
            )
        }
    }
}

/**
 * 水晶球FAB。
 *
 * 视觉效果：
 * - 径向渐变球体（左上亮、右下暗，模拟3D光照）
 * - 内部两层弧形光泽，8秒缓慢自转（模拟水晶内部折射）
 * - 顶部白色高光点（模拟光源反射）
 * - 底部暗部渐变（模拟球体投影）
 * - 点击时缩放反馈
 */
@Composable
private fun CrystalBallFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "crystal_ball")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "crystal_rotation",
    )

    val scale by animateFloatAsState(
        targetValue = if (expanded) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "crystal_scale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7B9FFF),
                        Color(0xFF4A6FE8),
                        Color(0xFF2545C0),
                        Color(0xFF152D8A),
                    ),
                    center = Offset(0.35f, 0.28f),
                    radius = 0.75f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 内部旋转光泽层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h / 2
                val r = w / 2

                // 光泽弧1：大弧形，左上到右上
                drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = 210f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(
                        width = r * 0.14f,
                        cap = StrokeCap.Round,
                    ),
                )

                // 光泽弧2：小弧形，右下
                drawArc(
                    color = Color(0xFFA0C0FF).copy(alpha = 0.22f),
                    startAngle = 30f,
                    sweepAngle = 70f,
                    useCenter = false,
                    style = Stroke(
                        width = r * 0.09f,
                        cap = StrokeCap.Round,
                    ),
                )

                // 内部光点1
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = r * 0.12f,
                    center = Offset(cx - r * 0.3f, cy + r * 0.2f),
                )

                // 内部光点2
                drawCircle(
                    color = Color(0xFFB0D0FF).copy(alpha = 0.15f),
                    radius = r * 0.08f,
                    center = Offset(cx + r * 0.25f, cy - r * 0.15f),
                )
            }
        }

        // 顶部高光（不随旋转，模拟固定光源反射）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = 7.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f))
        )
        // 次高光
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = 14.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )

        // 底部暗部渐变（模拟球体投影感）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                        ),
                        center = Offset(0.5f, 0.85f),
                        radius = 0.6f,
                    )
                )
        )

        // 中央图标
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = "更多",
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 单个导航按钮。 */
@Composable
private fun NavButton(
    item: NavItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
