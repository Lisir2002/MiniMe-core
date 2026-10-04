package com.mini.me_core.core.theme.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import kotlinx.coroutines.delay

/**
 * 工具页面枚举，用于「更多」按钮的点击切换和扇形菜单。
 */
enum class ToolPage(val route: String, val label: String) {
    GIT("git", "Git"),
    BROWSER("browser", "浏览器"),
    TERMINAL("terminal", "终端"),
    ;

    fun next(): ToolPage = entries[(ordinal + 1) % entries.size]
}

/**
 * 应用底部导航栏。
 *
 * 三个按钮：对话 / 更多 / 设置。
 * 「更多」按钮支持两种交互：
 * - 点击：在 Git、浏览器、终端之间循环切换
 * - 长按：从按钮位置向上扇形展开菜单，选择后跳转并收起
 *
 * @param currentRoute 当前路由，用于高亮选中态
 * @param onNavigate 导航回调
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    var fanMenuExpanded by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf(ToolPage.GIT) }

    val syncedTool = when (currentRoute) {
        "git" -> ToolPage.GIT
        "browser" -> ToolPage.BROWSER
        "terminal" -> ToolPage.TERMINAL
        else -> currentTool
    }

    val isToolSelected = currentRoute in listOf("git", "browser", "terminal")
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            // 对话
            NavigationBarItem(
                selected = currentRoute == "chat",
                onClick = { onNavigate("chat") },
                icon = { Icon(Icons.Rounded.Chat, contentDescription = null) },
                label = { Text("对话", style = MaterialTheme.typography.labelSmall) },
            )

            // 更多（点击循环切换，长按扇形菜单）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    currentTool = syncedTool.next()
                                    onNavigate(currentTool.route)
                                },
                                onLongPress = {
                                    fanMenuExpanded = true
                                },
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = if (isToolSelected) activeColor else inactiveColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = syncedTool.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToolSelected) activeColor else inactiveColor,
                    )
                }
            }

            // 设置
            NavigationBarItem(
                selected = currentRoute == "settings",
                onClick = { onNavigate("settings") },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text("设置", style = MaterialTheme.typography.labelSmall) },
            )
        }

        // 扇形菜单覆盖层
        if (fanMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .offset(y = (-220).dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // 半透明背景，点击收起
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { fanMenuExpanded = false },
                        ),
                )

                // 扇形菜单项容器
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .height(180.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    FanMenuItem(
                        label = "Git",
                        expanded = fanMenuExpanded,
                        angleDeg = 150f,
                        radius = 105f,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = {
                            currentTool = ToolPage.GIT
                            onNavigate("git")
                            fanMenuExpanded = false
                        },
                    )
                    FanMenuItem(
                        label = "浏览器",
                        expanded = fanMenuExpanded,
                        angleDeg = 90f,
                        radius = 115f,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = {
                            currentTool = ToolPage.BROWSER
                            onNavigate("browser")
                            fanMenuExpanded = false
                        },
                    )
                    FanMenuItem(
                        label = "终端",
                        expanded = fanMenuExpanded,
                        angleDeg = 30f,
                        radius = 105f,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = {
                            currentTool = ToolPage.TERMINAL
                            onNavigate("terminal")
                            fanMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * 扇形菜单项，从底部中心按指定角度展开。
 */
@Composable
private fun FanMenuItem(
    label: String,
    expanded: Boolean,
    angleDeg: Float,
    radius: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val targetX = (Math.cos(angleRad) * radius).toFloat()
    val targetY = -(Math.sin(angleRad) * radius).toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "fan_$label",
    )

    Box(
        modifier = modifier
            .offset(
                x = (targetX * animatedProgress).dp,
                y = (targetY * animatedProgress).dp,
            )
            .graphicsLayer {
                alpha = animatedProgress
                scaleX = 0.7f + 0.3f * animatedProgress
                scaleY = 0.7f + 0.3f * animatedProgress
            }
            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
