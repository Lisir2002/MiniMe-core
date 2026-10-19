@file:Suppress("FunctionNaming", "LongMethod") // Compose 命名/长组件约定

package com.mini.me_core.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.ScaledAnimation
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppNavIcon
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import kotlin.math.roundToInt

/** 面板入场 / 列表项错峰的单段时长（毫秒，会再乘全局动效缩放）。 */
private const val REVEAL_DURATION_MS = 220

/** 相邻列表项的入场错峰间隔（毫秒，会再乘全局动效缩放）。 */
private const val REVEAL_STAGGER_MS = 38

/** 拖拽关闭阈值：向左位移超过面板宽度的该比例即判定为「关闭手势」。 */
private const val DISMISS_DRAG_RATIO = 0.32f

/** 菜单项按压时的缩放目标值（轻微下沉，营造实体感）。 */
private const val ITEM_PRESSED_SCALE = 0.96f

/** 菜单项图标点击时的弹跳峰值。 */
private const val ITEM_BOUNCE_SCALE = 1.18f

/**
 * 右侧贴边工具抽屉。
 *
 * 从主界面顶栏「工具」按钮或从屏幕右缘向内滑动呼出，把终端 / 浏览器 / 打包器 /
 * Git 管理 / 设置等入口集中收纳；只复用现有路由 navigate，不新建、不删除任何功能页面。
 *
 * 设计要点：
 * - 面板宽度与左侧抽屉一致：手机竖屏 300dp；宽屏（≥600dp）按屏宽 0.32 折半并夹在 260–300dp。
 * - 形状、底色、elevation 复用左侧 ModalDrawerSheet 的令牌：RectangleShape、surface、0dp。
 * - 间距统一走 [Spacing]（md/lg/sm），动效时长统一走 [ScaledAnimation]，支持全局关闭/调慢。
 *
 * 交互增强：
 * - 手势：面板整体可横向拖拽关闭（向左跟手位移，超过面板宽度 32% 即收起，否则回弹归位）；
 *   收起状态下屏幕右缘提供向内滑动呼出的热区。
 * - 动画：列表项按序错峰入场（淡入 + 自右轻微滑入），面板入场走 spring 曲线，
 *   拖拽期间遮罩透明度随位移联动淡出，形成跟手的层次感。
 * - 动作：菜单项按压有轻微下沉与背景染色，点击时图标块弹跳一次；
 *   平板/外接鼠标场景下有悬停态。所有动效尊重 [LocalAnimationScale]（设为 0 时自动瞬时）。
 *
 * @param open 当前是否展开
 * @param onDismissRequest 点击遮罩 / 返回键 / 拖拽越界时请求收起
 * @param onNavigate 跳转已有路由并由宿主负责收起
 * @param modifier 修饰符
 * @param onOpenRequest 屏幕右缘向内滑动到阈值时请求展开
 */
@Composable
fun RightSidebar(
    open: Boolean,
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRequest: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 面板宽度与左侧抽屉一致：手机竖屏 300dp；宽屏按屏宽 0.32 折半并夹在 260–300dp。
        val panelWidth = if (maxWidth >= 600.dp) {
            (maxWidth * 0.32f).coerceIn(260.dp, 300.dp)
        } else {
            300.dp
        }

        val animationScale = LocalAnimationScale.current
        val panelDuration = (REVEAL_DURATION_MS * animationScale).toInt().coerceAtLeast(0)
        val density = LocalDensity.current
        val panelWidthPx = remember(panelWidth, density) { with(density) { panelWidth.toPx() } }
        val edgeSwipeThresholdPx = remember(density) {
            with(density) { RightSidebarGestures.EdgeSwipeOpenDistance.toPx() }
        }

        // ── 拖拽位移：向左为负、上限为面板宽度；拖拽中跟手（snap），松手后回弹（spring）──
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        // 每次重新展开都归零，避免上一次手势的残留偏移污染本次入场位置。
        LaunchedEffect(open) {
            if (open) {
                dragOffsetPx = 0f
                dragging = false
            }
        }
        val settleOffset by animateFloatAsState(
            targetValue = dragOffsetPx,
            animationSpec = if (dragging) {
                snap()
            } else {
                ScaledAnimation.spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            },
            label = "sidebarPanelSettle",
        )

        // 半透明遮罩：点击收起；拖拽时透明度跟手减弱，形成「拖走即淡出」的层次。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(animationSpec = ScaledAnimation.tween(panelDuration)),
            exit = fadeOut(animationSpec = ScaledAnimation.tween(panelDuration)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .graphicsLayer {
                        val dragged = (-settleOffset / panelWidthPx).coerceIn(0f, 1f)
                        alpha = 1f - dragged
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
            )
        }

        // 右侧面板：从右滑入/滑出；形状、底色、elevation 与左侧 ModalDrawerSheet 保持一致。
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = ScaledAnimation.finiteSpring<IntOffset>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetX = { fullWidth -> fullWidth },
            ) + fadeIn(animationSpec = ScaledAnimation.tween(panelDuration)),
            exit = slideOutHorizontally(
                animationSpec = ScaledAnimation.tween(panelDuration),
                targetOffsetX = { fullWidth -> fullWidth },
            ) + fadeOut(animationSpec = ScaledAnimation.tween(panelDuration)),
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(settleOffset.roundToInt(), 0) }
                    .pointerInput(panelWidthPx, onDismissRequest) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragCancel = {
                                dragging = false
                                dragOffsetPx = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                val passedThreshold =
                                    dragOffsetPx <= -panelWidthPx * DISMISS_DRAG_RATIO
                                dragOffsetPx = 0f
                                if (passedThreshold) onDismissRequest()
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffsetPx =
                                    (dragOffsetPx + dragAmount).coerceIn(-panelWidthPx, 0f)
                            },
                        )
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RectangleShape,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(vertical = Spacing.md),
                ) {
                    val groups = groups()
                    // 逐项累加的全局序号：用于列表错峰入场的延迟递增。
                    var revealIndex = 0
                    groups.forEach { group ->
                        revealIndex += 1
                        SidebarReveal(index = revealIndex, visible = open) {
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                text = stringResource(group.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.lg,
                                    vertical = Spacing.sm,
                                ),
                            )
                        }
                        group.entries.forEach { entry ->
                            revealIndex += 1
                            SidebarReveal(index = revealIndex, visible = open) {
                                SidebarItem(
                                    icon = entry.icon,
                                    iconBgLight = entry.iconBgLight,
                                    iconBgDark = entry.iconBgDark,
                                    label = stringResource(entry.labelRes),
                                    onClick = { onNavigate(entry.route) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 收起状态下才接管右缘热区：向内（左）滑动累计超过阈值即呼出抽屉。
        // 展开时不挂载，避免拦截面板自身的拖拽关闭手势与内容区交互。
        if (!open) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(RightSidebarGestures.EdgeSwipeZoneWidth)
                    .edgeSwipeToOpen(
                        onOpenRequest = onOpenRequest,
                        thresholdPx = edgeSwipeThresholdPx,
                    )
            )
        }
    }
}

/** 侧边栏手势相关的尺度常量。 */
private object RightSidebarGestures {
    /** 右缘热区宽度：仅在抽屉收起时生效的滑动呼出感应区。 */
    val EdgeSwipeZoneWidth = 22.dp

    /** 呼出所需的最小向内滑动距离：阈值偏大，避免误触。 */
    val EdgeSwipeOpenDistance = 44.dp
}

/**
 * 屏幕右缘向内滑动呼出手势。
 *
 * 仅在水平为主的拖动累计位移超过 [thresholdPx] 时触发一次，触发后立即清零累计值，
 * 避免一次长按滑动连续重复呼出。
 */
private fun Modifier.edgeSwipeToOpen(
    onOpenRequest: () -> Unit,
    thresholdPx: Float,
): Modifier = pointerInput(onOpenRequest, thresholdPx) {
    var accumulated = 0f
    detectHorizontalDragGestures(
        onDragStart = { accumulated = 0f },
        onDragCancel = { accumulated = 0f },
        onHorizontalDrag = { _, dragAmount ->
            accumulated += dragAmount
            if (accumulated <= -thresholdPx) {
                accumulated = 0f
                onOpenRequest()
            }
        },
    )
}

/**
 * 列表错峰入场的包裹层：按 [index] 递增延迟，形成自上而下的级联显现。
 *
 * 同时作用于分组标题与菜单项，让整列内容呈现统一的波浪式进入；退出统一瞬时淡出，
 * 避免与面板自身的滑出动画叠加出现拖影。
 */
@Composable
private fun SidebarReveal(
    index: Int,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val stagger = (REVEAL_STAGGER_MS * LocalAnimationScale.current).toInt().coerceAtLeast(0)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = ScaledAnimation.tween(
                millis = REVEAL_DURATION_MS,
                delayMillis = index * stagger,
            )
        ) + slideInHorizontally(
            animationSpec = ScaledAnimation.tween(
                millis = REVEAL_DURATION_MS,
                delayMillis = index * stagger,
            ),
            initialOffsetX = { fullWidth -> fullWidth / 3 },
        ),
        exit = fadeOut(animationSpec = ScaledAnimation.tween(0)),
    ) {
        content()
    }
}

/**
 * 侧边栏菜单项：图标块 + 文字 的整块可点击区域。
 *
 * 复用与抽屉导航图标同一套设计令牌（[AppNavIcon] 图标块、clip md 点击热区、
 * bodyLarge/Medium/onSurface 文字）。在此基础上叠加动效反馈：
 * - 按压：整行轻微下沉（0.96）并以 onSurface 微染色背景呼应，水波纹沿用
 *   [LocalIndication] 的默认实现；平板/鼠标场景额外有悬停态。
 * - 点击：图标块弹跳一次（1.18 冲顶后回落），形成确认感。
 * 无障碍描述挂在行上（与左侧一致）。
 */
@Composable
private fun SidebarItem(
    icon: ImageVector,
    iconBgLight: Color,
    iconBgDark: Color,
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val animationScale = LocalAnimationScale.current

    // 图标弹跳：点击瞬间抬到峰值，动画结束后由 finishedListener 拉回 1 形成回落。
    var bounceTarget by remember { mutableFloatStateOf(1f) }
    val iconScale by animateFloatAsState(
        targetValue = bounceTarget,
        animationSpec = ScaledAnimation.spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sidebarItemBounce",
        finishedListener = { bounceTarget = 1f },
    )

    val rowScale by animateFloatAsState(
        targetValue = if (pressed) ITEM_PRESSED_SCALE else 1f,
        animationSpec = ScaledAnimation.spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "sidebarItemPress",
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.12f else if (hovered) 0.07f else 0f,
        animationSpec = ScaledAnimation.tween(millis = 120),
        label = "sidebarItemTint",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = backgroundAlpha))
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (animationScale > 0f) bounceTarget = ITEM_BOUNCE_SCALE
                    onClick()
                },
            )
            .semantics { contentDescription = label }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppNavIcon(
            icon = icon,
            iconBgLight = iconBgLight,
            iconBgDark = iconBgDark,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class SidebarEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val route: String,
    /** 图标块亮色模式背景（与左侧抽屉同目的地按钮保持同色）。 */
    val iconBgLight: Color,
    /** 图标块暗色模式背景。 */
    val iconBgDark: Color,
)

private data class SidebarGroup(
    val titleRes: Int,
    val entries: List<SidebarEntry>,
)

/**
 * 分组与路由：全部复用 NavHost 中已存在的 route。
 *
 * 原「文件管理」「关于」为无独立页面的占位项、「能力中心」与设置页入口重复，三者一并删除；
 * 「版本控制」更名「Git 管理」——该页覆盖状态/暂存/提交、分支、提交记录、署名、凭据、
 * 仓库初始化与远程操作，命名与功能范围相符。
 */
@Composable
private fun groups(): List<SidebarGroup> = listOf(
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_tools,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.Terminal,
                labelRes = R.string.right_sidebar_terminal,
                route = "terminal",
                iconBgLight = Color(0xFF22C55E),
                iconBgDark = Color(0xFF15803D),
            ),
            SidebarEntry(
                icon = Icons.Rounded.Public,
                labelRes = R.string.right_sidebar_browser,
                route = "browser",
                iconBgLight = Color(0xFF06B6D4),
                iconBgDark = Color(0xFF0E7490),
            ),
            SidebarEntry(
                icon = Icons.Rounded.Android,
                labelRes = R.string.right_sidebar_packager,
                route = "packager",
                // 应用打包器入口：紫色图标块，与导航图标令牌一致。
                iconBgLight = Color(0xFF8B5CF6),
                iconBgDark = Color(0xFF6D28D9),
            ),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_dev,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.AccountTree,
                labelRes = R.string.right_sidebar_git,
                route = "git",
                iconBgLight = Color(0xFFF97316),
                iconBgDark = Color(0xFFC2410C),
            ),
        ),
    ),
    SidebarGroup(
        titleRes = R.string.right_sidebar_group_other,
        entries = listOf(
            SidebarEntry(
                icon = Icons.Rounded.Settings,
                labelRes = R.string.right_sidebar_settings,
                route = "settings",
                // 设置入口：天蓝色图标块，与导航图标令牌一致。
                iconBgLight = Color(0xFF0EA5E9),
                iconBgDark = Color(0xFF0369A1),
            ),
        ),
    ),
)
