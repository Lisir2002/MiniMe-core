package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.exponentialDecay
import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滑扫锚点：Closed（收起）/ Open（展开露出动作栏）/ Trigger（全滑确认点）。
 * 对齐 iOS `swipeActions` 的三种归位落点：
 *  - **Closed** ——不区分拖向，最终都弹回原位；
 *  - **Open** ——拖过阈值稳定展开，停留直到点动作 / 点内容 / 他行展开；
 *  - **Trigger** ——仅 [AppSwipePattern.Dismiss]（`allowsFullSwipe`）模式携带，全滑到底即触发。
 */
enum class SwipeValue { Closed, Open, Trigger }

/**
 * 滑扫行为模式（对齐 SwiftUI `swipeActions(edge:allowsFullSwipe:)` 语义）：
 *
 *  - **Reveal**（默认 · iOS 默认）——左滑越过阈值**稳定展开**露出动作栏，
 *    直到点按钮 / 点内容区 / 打开另一行才收起。对应 `allowsFullSwipe = false` 的「只开放、不自动执行」；
 *  - **Dismiss**（`allowsFullSwipe = true` 的全滑）——全滑到底越过 Trigger 阈值
 *    立即执行**第一条动作**（[AppSwipeAction.onTrigger]）并自动收起，
 *    用于「滑一下直接执行」的单动作列表（如邮件列表的「归档」）。
 */
enum class AppSwipePattern { Reveal, Dismiss }

/**
 * 动作按钮的揭示进度（0..1）：由 [AppSwipeAction] 注入，随拖拽进度实时更新，
 * 按钮据此做**透明度渐入**（iOS 随内容平移自然露出的做法，无花哨动画）。默认 1f 便于脱离容器单独使用。
 */
internal val LocalSwipeReveal = staticCompositionLocalOf { 1f }

/**
 * 动作栏所在边：默认靠右（End），可切到靠左（Start）。
 *
 * 遵循 iOS HIG 语义：**End（左滑）承载破坏性/高频操作**（删除·归档·更多）。
 * **Start（右滑）承载正向/可逆操作**（置顶·标记已读·收藏）。破坏性动作请配红色令牌。
 */
enum class AppSwipeEdge { Start, End }

/**
 * 揭示「呈现阶段」（对齐 iOS 27 `swipeActions(…){ onPresentationChanged }` 的回调语义）。
 *
 * 事件在「**闭合** ↔ **呈现（展开/触发）**」折返时各回调一次，供外部同步 UI 状态
 * （如收起时的工具条重排、点击空白处收起后的高亮复位）。
 */
enum class AppSwipePhase { Closed, Revealed, Triggered }

/**
 * 滑扫操作的提升状态。
 *
 * 拖动跟手 / fling / settle / clamp / 全滑确认全由 `AnchoredDraggableState` 托管。
 * 暴露业务关心的收口 API：位移、揭示进度（0..1）、呈现阶段、是否稳定展开、开合、越界阻尼比例。
 *
 * **关键设计**：所有属性都**直接读取 `anchored.offset`**（`MutableFloatState`），
 * 避免 `requireOffset()` 封装导致 State 追踪失效。
 *
 * **设计来源**：本文档将「一行能滑出什么动作」与「多个行之间如何互斥协调」分离，
 * 对齐 iOS 27 引入的 `swipeActionsContainer()` 容器模型：
 *  - 行只声明**动作本体**（见 [AppSwipeButton]）；
 *  - **协调（互斥 / 收起）**由外部容器（样板页的列表）通过 `index + expandedIndex + onExpanded`
 *    提升状态完成（见 [AppSwipeAction] 内部互斥 LaunchedEffect）。
 */
class AppSwipeActionState internal constructor(
    internal val anchored: AnchoredDraggableState<SwipeValue>,
    internal val openAnchorAbs: Float,
    internal val triggerAnchorAbs: Float,
    internal val startSwipeAbs: Float,
) {
    /** 当前内容层横向位移（px），Closed 为 0，Open 为 ±actionWidth。 */
    val offset: Float
        get() = anchored.offset

    /**
     * 揭示进度 0..1：在**起始滑动阈值**之后才线性攀升，驱动动作按钮的淡入。
     * 对齐 Gmail 滑动揭示的「先滑一段才露按钮」手感——越过约 startSwipeThreshold 才开始露。
     */
    val progress: Float
        get() {
            val travelled = abs(offset) - startSwipeAbs
            val span = (openAnchorAbs - startSwipeAbs).coerceAtLeast(1f)
            return (travelled / span).coerceIn(0f, 1f)
        }

    /** 当前呈现阶段（用于 [AppSwipeAction.onPresentationChanged] 上报）。 */
    val phase: AppSwipePhase
        get() {
            if (abs(offset) < startSwipeAbs * 0.8f) return AppSwipePhase.Closed
            return if (anchored.settledValue == SwipeValue.Trigger) {
                AppSwipePhase.Triggered
            } else {
                AppSwipePhase.Revealed
            }
        }

    /** 是否已稳定展开（settle 完成后才翻转）。 */
    val isOpen: Boolean
        get() = anchored.settledValue == SwipeValue.Open

    /** 是否"非关闭态"——无论 settle 与否，只要越过起始阈值就算正在/已展开。 */
    val isEngaged: Boolean
        get() = isEngagedAt(offset)

    /** 给定 offset 判定"非关闭态"（阈值判断唯一收口处）。 */
    internal fun isEngagedAt(offset: Float): Boolean = abs(offset) > startSwipeAbs * 0.8f

    /** 是否正处于"全滑确认"阈值（拖超过动作栏），用于放大 + 阻尼反馈。 */
    val isBeyondReveal: Boolean
        get() = abs(offset) > openAnchorAbs * 1.02f

    /** 全滑超额比例 0..1：在动作栏宽度与全滑确认点之间插值（触发即等于 1）。 */
    val overshoot: Float
        get() = (((abs(offset) - openAnchorAbs) / (triggerAnchorAbs - openAnchorAbs).coerceAtLeast(1f)))
            .coerceIn(0f, 1f)

    /**
     * **越界阻尼**（rubber-band / friction）：超过动作栏后把超额位移按抛物线衰减，
     * 制造"橡皮筋跟手"手感——对齐 iOS `UIScrollView` bounces 阻尼。
     */
    val resistedOffset: Float
        get() {
            val raw = abs(offset)
            if (raw <= openAnchorAbs) return offset
            val excess = raw - openAnchorAbs
            val maxExcess = (triggerAnchorAbs - openAnchorAbs).coerceAtLeast(1f)
            val t = (excess / maxExcess).coerceIn(0f, 1f)
            val damped = openAnchorAbs + maxExcess * (0.5f + 0.5f * t) * t
            return if (offset < 0) -damped else damped
        }

    /** 展开露出动作栏（Reveal 模式收起后再次展开用），保留轻微弹性手感。 */
    suspend fun open() = anchored.animateTo(
        SwipeValue.Open,
        AppMotion.emphasizedSpring(),
    )

    /**
     * 收起（弹回原位）。用**无过冲弹簧**：MediumBouncy 收起时会从 -actionWidth 过冲越过 0
     * 冲到正值，反向穿越 `isEngagedAt` 阈值 → 触发申报 effect 误判「重新展开」→ 互斥雪崩。
     * 临界阻尼彻底消除过冲。
     */
    suspend fun close() = anchored.animateTo(
        SwipeValue.Closed,
        AppMotion.noBounceSpring(),
    )
}

/**
 * 创建可提升的滑扫状态；`actionWidth` 必须与 [AppSwipeAction] 保持一致。
 *
 * @param triggerWidth 从 [actionWidth] 到全滑确认点的额外宽度；拖超过即触发 [AppSwipeAction.onTrigger]。
 * @param pattern 滑扫模式（参与锚点表构造）：
 *   **Reveal** 时锚点表**不含 Trigger**——高速 fling 最多锚定到 Open，杜绝内容层冲过动作栏
 *   露出空白缝；**Dismiss** 时保留 Trigger 锚点支撑全滑确认。
 *
 * @since 0.1.0-experimental
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun rememberAppSwipeActionState(
    edge: AppSwipeEdge = AppSwipeEdge.End,
    actionWidth: Dp = 120.dp,
    triggerWidth: Dp = 56.dp,
    startSwipeThreshold: Dp = 12.dp,
    pattern: AppSwipePattern = AppSwipePattern.Reveal,
): AppSwipeActionState {
    val density = LocalDensity.current
    val (openAbs, triggerAbs, startAbs) = remember(edge, actionWidth, triggerWidth, startSwipeThreshold) {
        Triple(
            with(density) { actionWidth.toPx() },
            with(density) { (actionWidth + triggerWidth).toPx() },
            with(density) { startSwipeThreshold.toPx() },
        )
    }
    val velocityThreshold = remember(openAbs) { openAbs.coerceAtLeast(48f) }

    val anchors = remember(edge, openAbs, triggerAbs, pattern) {
        val sign = if (edge == AppSwipeEdge.End) -1f else 1f
        DraggableAnchors {
            SwipeValue.Closed at 0f
            SwipeValue.Open at sign * openAbs
            // 仅 Dismiss 模式提供 Trigger 锚点；Reveal 模式 fling 最多到 Open（见 KDoc）。
            if (pattern == AppSwipePattern.Dismiss) {
                SwipeValue.Trigger at sign * triggerAbs
            }
        }
    }

    val anchored = remember(anchors, velocityThreshold) {
        AnchoredDraggableState(
            initialValue = SwipeValue.Closed,
            anchors = anchors,
            positionalThreshold = { distance -> abs(distance) * 0.30f },
            velocityThreshold = { velocityThreshold },
            snapAnimationSpec = AppMotion.emphasizedSpring(),
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { it in SwipeValue.values() },
        )
    }
    return remember(anchored, openAbs, triggerAbs, startAbs) {
        AppSwipeActionState(anchored, openAbs, triggerAbs, startAbs)
    }
}

/** 颜色向另一颜色插值（用于按钮按压压暗反馈）。 */
internal fun Color.blend(target: Color, t: Float): Color = Color(
    red = red + (target.red - red) * t,
    green = green + (target.green - green) * t,
    blue = blue + (target.blue - blue) * t,
    alpha = alpha + (target.alpha - alpha) * t,
)

/**
 * 滑扫动作按钮（AppSwipeButton）：iOS 简约风格——**扁平纯色块**，图标 + 文字垂直排布，等宽平分动作栏。
 *
 * **对齐 SwiftUI `Button(role: .destructive)` / `tint(_:)` 语义**：
 * 破坏性动作（删除）传红色令牌，普通动作传主色/语义色令牌；颜色由外部经 [background] 传入。
 *
 * 揭示方式是**位置驱动而非 alpha 淡入**（对齐 iOS 内容平移自然露出）：
 * 按钮绘制在底层子画布（[AppSwipeAction] 的 `matchParentSize` 层）上，顶层不透明内容层
 * 向左平移多少、右侧按钮就被揭开多少——"顺缝露出"，无整体渐显。本地 [LocalSwipeReveal]
 * 此刻仅用作**可点击门控**（`enabled = reveal > 0.05f`），不再修改透明度。
 *
 * 按压时把底色压暗一档（[Color.blend]），给出手感反馈。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun RowScope.AppSwipeButton(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color = appPalette().onPrimary,
    onClick: () -> Unit,
) {
    val reveal = LocalSwipeReveal.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) background.blend(appPalette().ink, 0.12f) else background,
        label = "swipeButtonPressedBg",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(bg, RoundedCornerShape(AppRadius.None))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = reveal > 0.05f,
                onClick = AppHaptics.click(onClick),
            )
            .padding(horizontal = AppSpacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AppSpacing.Xs),
            )
        }
    }
}

/**
 * 滑扫操作（分子组 · AppSwipeAction · iOS 简约风格重绘，基于全网检索重构）。
 *
 * ## 设计来源（2026 全网检索）
 * 参考 SwiftUI `swipeActions` /`ButtonRole` /`tint()`，以及 iOS 27 的
 * `swipeActionsContainer()` + `onPresentationChanged` 容器协调模型；Compose 侧参考
 * Material3 `SwipeToDismissBox` 与 `RevealSwipe`（底层同为 `AnchoredDraggableState`）。
 *
 * ## 三层心智模型
 * 1. **行声明动作**（`actions` 槽：放 [AppSwipeButton] 序列）；
 * 2. **容器协调互斥**（`index + expandedIndex + onExpanded`：同批只开一项，收取行为与 iOS 27
 *    `swipeActionsContainer()` 的"单一活动行"等价）；
 * 3. **随态上报**（[AppSwipePhase] 经 [onPresentationChanged]，对齐 `onPresentationChanged`）。
 *
 * ## 交互正确性（已修问题保留，不得回退）
 *  - **收起无过冲**：`close()` 用临界阻尼弹簧，杜绝过冲触发互斥雪崩；
 *  - **Reveal 无 Trigger 锚点**：高速 fling 最多锚定 Open，杜绝冲过动作栏露空白缝；
 *  - **互斥**：同批只开一项，`expandedIndex` 移出 effect key，`close()` 不被 cancel；
 *  - **申报防线**：仅在 `settledValue != Closed` 且正朝展开方向时才抢占展开位。
 *
 * ## 视觉
 * 按 iOS `swipeActions` 重绘：动作栏为**底层子画布**（[AppRadius.Md] 整条圆角容器，无阴影），
 * 内部按钮**扁平纯色拼接、无分隔线**，绘制在该子画布上；顶层不透明白色内容层
 * **向左平移多少、按钮就从右侧揭开多少**（位置驱动顺缝露出，无整体渐显）。
 *
 * @since 0.1.0-experimental
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AppSwipeAction(
    modifier: Modifier = Modifier,
    edge: AppSwipeEdge = AppSwipeEdge.End,
    actionWidth: Dp = 120.dp,
    pattern: AppSwipePattern = AppSwipePattern.Reveal,
    state: AppSwipeActionState? = null,
    index: Int? = null,
    expandedIndex: Int? = null,
    onExpanded: ((Int?) -> Unit)? = null,
    onSwipeProgress: ((Float) -> Unit)? = null,
    onPresentationChanged: ((AppSwipePhase) -> Unit)? = null,
    onTrigger: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedState = state ?: rememberAppSwipeActionState(edge, actionWidth, pattern = pattern)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnExpanded by rememberUpdatedState(onExpanded)
    val currentOnProgress by rememberUpdatedState(onSwipeProgress)
    val currentOnPresentation by rememberUpdatedState(onPresentationChanged)
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    val currentPattern by rememberUpdatedState(pattern)
    val currentExpandedIndex by rememberUpdatedState(expandedIndex)
    val shape = RoundedCornerShape(AppRadius.Md)

    // —— 直接读 MutableFloatState，Compose 必然追踪变化。
    val rawOffset = resolvedState.anchored.offset

    // progress：起始滑动阈值之后线性攀升至 1。揭示本身由「顶层内容层平移顺缝揭开」完成
    // （非 alpha 渐入）；progress 经 LocalSwipeReveal 下发，仅作按钮可点击门控。
    val travelled = abs(rawOffset) - resolvedState.startSwipeAbs
    val span = (resolvedState.openAnchorAbs - resolvedState.startSwipeAbs).coerceAtLeast(1f)
    val progress = (travelled / span).coerceIn(0f, 1f)

    // 越界阻尼化 offset：rawOffset 不超过 openAnchorAbs 时原样；超过后按抛物线衰减。
    val openAbs = resolvedState.openAnchorAbs
    val rawAbs = abs(rawOffset)
    val contentOffset: Float = if (rawAbs <= openAbs) {
        rawOffset
    } else {
        val excess = rawAbs - openAbs
        val maxExcess = (resolvedState.triggerAnchorAbs - openAbs).coerceAtLeast(1f)
        val t = (excess / maxExcess).coerceIn(0f, 1f)
        val damped = openAbs + maxExcess * (0.5f + 0.5f * t) * t
        if (rawOffset < 0) -damped else damped
    }

    val isEngaged = resolvedState.isEngagedAt(rawOffset)

    // —— 互斥收起（同批只开一项；对齐 `swipeActionsContainer()` 的单一活动行）——
    // ① 本项展开/收起 → 向外部申报展开位。key 含 state+index、不含 expandedIndex；
    //    distinctUntilChanged 保证只在「真正跨越 engaged 阈值」时回调一次。
    //    发射 (engaged, settlingOpen) 二元组——settlingOpen 表示正朝展开方向 settle，
    //    收起路径即便过冲也绝不抢报，杜绝互斥错乱链。
    LaunchedEffect(resolvedState, index) {
        if (index == null) return@LaunchedEffect
        snapshotFlow {
            resolvedState.isEngagedAt(resolvedState.anchored.offset) to
                (resolvedState.anchored.settledValue != SwipeValue.Closed)
        }
            .distinctUntilChanged()
            .collect { (engaged, settlingOpen) ->
                val cb = currentOnExpanded ?: return@collect
                val current = currentExpandedIndex
                when {
                    engaged && settlingOpen && current != index -> cb(index)
                    !engaged && current == index -> cb(null)
                }
            }
    }

    // ② 别的项被展开 → 收起自己。不把 expandedIndex 放进 key，close() 不被 cancel。
    LaunchedEffect(resolvedState, index) {
        if (index == null) return@LaunchedEffect
        snapshotFlow { currentExpandedIndex }
            .distinctUntilChanged()
            .collect { current ->
                if (current != null && current != index && resolvedState.isEngagedAt(resolvedState.anchored.offset)) {
                    resolvedState.close()
                }
            }
    }

    // 呈现阶段上报（对齐 iOS 27 `onPresentationChanged`）：折返时各回调一次。
    LaunchedEffect(resolvedState) {
        var lastPhase: AppSwipePhase? = null
        snapshotFlow { resolvedState.phase }
            .distinctUntilChanged()
            .collect { phase ->
                if (lastPhase != phase) {
                    currentOnPresentation?.invoke(phase)
                    lastPhase = phase
                }
            }
    }

    // 全滑确认：settle 到 Trigger 锚点即触发 onTrigger（Reveal 无 Trigger 锚点，实际仅 Dismiss 触发）。
    LaunchedEffect(resolvedState, pattern) {
        snapshotFlow { resolvedState.anchored.settledValue }
            .collect { v ->
                if (v == SwipeValue.Trigger) {
                    if (currentPattern == AppSwipePattern.Dismiss) {
                        currentOnTrigger?.invoke()
                        scope.launch { resolvedState.close() }
                    } else if (onTrigger != null) {
                        currentOnTrigger?.invoke()
                        scope.launch { resolvedState.open() }
                    } else {
                        scope.launch { resolvedState.open() }
                    }
                }
            }
    }

    // 拖拽过程中实时上报揭示进度（0..1），供外部做态处理。
    LaunchedEffect(resolvedState) {
        snapshotFlow { resolvedState.anchored.offset }
            .collect { off ->
                val t = (abs(off) - resolvedState.startSwipeAbs) /
                    (resolvedState.openAnchorAbs - resolvedState.startSwipeAbs).coerceAtLeast(1f)
                currentOnProgress?.invoke(t.coerceIn(0f, 1f))
            }
    }

    // 阈值触感分级：揭示跨越 ~25% 轻震；越过全滑确认区再震。
    LaunchedEffect(resolvedState) {
        var armed = false
        var armedBeyond = false
        snapshotFlow { resolvedState.anchored.offset }
            .collect { off ->
                val p = (abs(off) - resolvedState.startSwipeAbs) /
                    (resolvedState.openAnchorAbs - resolvedState.startSwipeAbs).coerceAtLeast(1f)
                val clamped = p.coerceIn(0f, 1f)
                if (clamped >= 0.25f && !armed) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    armed = true
                } else if (clamped < 0.25f) {
                    armed = false
                }
                val beyond = abs(off) > resolvedState.openAnchorAbs * 1.02f
                if (beyond && !armedBeyond) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    armedBeyond = true
                } else if (!beyond) {
                    armedBeyond = false
                }
            }
    }

    // 外层 Box：fillMaxWidth 保证整行撑满；clip(shape) 防内容溢出覆盖相邻行。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        contentAlignment = if (edge == AppSwipeEdge.End) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // 底层动作栏：用 matchParentSize() 与内容层最终尺寸一致——该修饰符不参与外层 Box 的
        // 尺寸测量（Box 在兄弟测完后再用最终尺寸 tight 测量本子项），因此在 verticalScroll 等
        // 「高度无界」父级下也不会像 fillMaxSize() 那样被压成 0 高（真机实测：按钮因此整条不可见）。
        Box(modifier = Modifier.matchParentSize()) {
            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .align(if (edge == AppSwipeEdge.End) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(
                        // 兜底色：按钮 fillMaxHeight 无缝拼接，理论上不会露出；留 surfaceVariant 以防缝隙透视。
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalSwipeReveal provides progress) {
                    actions()
                }
            }
        }

        // 顶层内容滑层：宽度撑满、高度 wrap（由内容决定行高）；平移用 offset{} 在 layout 阶段完成。
        // ⚠️ offset 必须在 background 之前：offset 只平移链中位于其**内侧**的节点。若 background
        // 写在 offset 前面，背景矩形会留在初始位置绘制、不随拖拽移动，把底层动作栏永久盖住
        // （真机表现为：内容文字滑开了，但按钮整条不可见，右侧只见一条背景空白）。
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(contentOffset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    enabled = isEngaged,
                    onClick = { scope.launch { resolvedState.close() } },
                )
                .anchoredDraggable(
                    state = resolvedState.anchored,
                    orientation = Orientation.Horizontal,
                    reverseDirection = false,
                ),
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, name = "AppSwipeAction - Reveal")
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PreviewAppSwipeAction() {
    AppTheme {
        val state = rememberAppSwipeActionState()
        AppSwipeAction(
            state = state,
            actions = {
                AppSwipeButton(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    background = MaterialTheme.colorScheme.error,
                    onClick = {},
                )
            },
        ) {
            Text(
                text = "向左滑出操作按钮",
                modifier = Modifier.padding(AppSpacing.Lg),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}