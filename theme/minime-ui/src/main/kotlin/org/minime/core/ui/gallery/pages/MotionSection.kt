package org.minime.core.ui.gallery.pages

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.minime.core.motion.MotionCategory
import org.minime.core.motion.MotionPattern
import org.minime.core.motion.MotionScale
import org.minime.core.tokens.SpacingTokens
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.motion.LocalMotionScale
import org.minime.core.ui.motion.MotionSpecs
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor

/**
 * 动效检验区（§6.4 目录 → 真机点播）。
 *
 * R5 把 Gallery MOTION 占位页替换为真实页：逐模式点播 + 播放 / 慢放 /
 * reducedMotion 对照 + 槽位-覆写令牌**双标注**。
 *
 * ## 形态（维护者 2026-10-23 指示）
 *
 * 不用卡片分块（影响操作）：**类别 tab + 可滚动模式清单**——
 * 顶部五个类别 tab，下方 LazyColumn 列出该类全部模式（含二期登记项，置灰标注），
 * 每行即一个可点播条目；演示区固定于清单上方，播放目标始终可见。
 *
 * ## 消费纪律
 *
 * 全部规格经 [MotionSpecs] 工厂取（组件禁止自带硬编码动画参数，铁律 9）；
 * reducedMotion 对照通过**本地覆写** `LocalMotionScale` 实现（不改系统设置），
 * 系统级原始值仍从 `LocalMotionScale.current` 读取。
 */
@Composable
fun MotionSection(modifier: Modifier = Modifier) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val size = MiniMeTokens.LocalSize.current

    // 系统级原始缩放（调试面板 / 模拟开关的基准，不可写）
    val systemScale = LocalMotionScale.current
    var simulated by remember { mutableStateOf<MotionScale?>(null) }
    val scale = simulated ?: systemScale

    val motionTokens = MiniMeTokens.LocalMotion.current
    val interactionTokens = MiniMeTokens.LocalInteraction.current
    // 模拟开关生效时本地重建工厂（MotionTokens 仍是唯一真源）
    val specs = remember(motionTokens, interactionTokens, scale) {
        MotionSpecs(motionTokens, interactionTokens, scale)
    }

    var category by remember { mutableStateOf(MotionCategory.TRANSITION) }
    val patterns = remember(category) {
        org.minime.core.motion.MotionCatalog.ALL.filter { it.category == category }
    }

    // 演示方块（播放目标，清单上方固定）
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var slowmo by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalMotionScale provides scale) {
        Column(modifier = modifier.fillMaxSize()) {
            // ---- 类别 tab ----
            MotionCategoryTabs(
                current = category,
                onSelect = { category = it },
                spacing = spacing,
            )

            // ---- 演示区 ----
            MotionStage(
                animValue = anim.value,
                patternId = playingId,
                degraded = playingId != null && specs.of(playingId ?: "") == null,
                color = color,
                typography = typography,
                spacing = spacing,
                shape = shape,
            )

            // ---- 对照开关（reducedMotion 模拟 / 慢放） ----
            MotionControls(
                systemScale = systemScale,
                simulated = simulated,
                onSimulate = { simulated = it },
                slowmo = slowmo,
                onSlowmo = { slowmo = it },
                spacing = spacing,
                typography = typography,
                color = color,
            )

            // ---- 可滚动模式清单（占剩余高度，勿用 fillMaxSize——会把上方 tab/演示区挤出屏幕） ----
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(patterns, key = { it.id }) { pattern ->
                    MotionPatternRow(
                        pattern = pattern,
                        specs = specs,
                        typography = typography,
                        color = color,
                        spacing = spacing,
                        shape = shape,
                        size = size,
                        onPlay = {
                            scope.launch {
                                playingId = pattern.id
                                anim.snapTo(0f)
                                val spec = specs.of(pattern.id)
                                if (spec == null) {
                                    // 直切 / 静态：立即到位，不播动画（铁律 7 契约）
                                    anim.snapTo(1f)
                                    kotlinx.coroutines.delay(320)
                                    anim.snapTo(0f)
                                    playingId = null
                                    return@launch
                                }
                                playSpec(anim, spec.spec, slowmo)
                                kotlinx.coroutines.delay(240)
                                playSpec(anim, spec.spec, slowmo, reverse = true)
                                playingId = null
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(spacing.s24.value.dp)) }
            }
        }
    }
}

/**
 * 按规格驱动一次 0→1 动画；[slowmo] 为真时以 1/4 时间比例推进。
 *
 * 慢放实现：Compose 的 `spring` **没有时长参数**，把 spec 时长乘 4 会失真——
 * 正解是保持 spec 不变、把喂给它的**播放时间**压缩为 1/4：
 * `vectorize(...).getValueFromNanos(playTimeNanos / 4, ...)`，再 `snapTo` 落值。
 * 这样 spring 曲线形状完全保真，只是「时间被拉长 4 倍」。
 */
private suspend fun playSpec(
    anim: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    spec: FiniteAnimationSpec<Float>,
    slowmo: Boolean,
    reverse: Boolean = false,
) {
    if (!slowmo) {
        if (reverse) {
            anim.animateTo(0f, spec)
        } else {
            anim.animateTo(1f, spec, initialVelocity = 0f)
        }
        return
    }

    val vectorized: VectorizedFiniteAnimationSpec<androidx.compose.animation.core.AnimationVector1D> =
        spec.vectorize(Float.VectorConverter)
    val start = withFrameNanosOrZero { it }
    val target = if (reverse) 0f else 1f
    while (true) {
        val now = withFrameNanosOrZero { it }
        val scaled = ((now - start) / 4f).toLong() // 1/4 速率
        val value = vectorized.getValueFromNanos(
            scaled,
            androidx.compose.animation.core.AnimationVector(if (reverse) 1f else 0f),
            androidx.compose.animation.core.AnimationVector(target),
            androidx.compose.animation.core.AnimationVector(0f),
        ).value
        anim.snapTo(value)
        if (kotlin.math.abs(value - target) < 0.0005f) break
        // 安全阀：折算后超过 30s 视为发散，退出
        if ((now - start) > 30_000_000_000L) break
    }
    anim.snapTo(target)
}

/** 帧时钟封装（便于单测替换；生产直接取帧时间） */
private suspend fun withFrameNanosOrZero(onFrame: (Long) -> Long): Long =
    androidx.compose.runtime.withFrameNanos(onFrame)

// ---------- 类别 tab ----------

@Composable
private fun MotionCategoryTabs(
    current: MotionCategory,
    onSelect: (MotionCategory) -> Unit,
    spacing: SpacingTokens,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter.value.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
    ) {
        MotionCategory.entries.forEach { category ->
            val selected = category == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable { onSelect(category) }
                    .padding(vertical = spacing.s8.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.title,
                    style = typography.caption1.toTextStyle(),
                    color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(spacing.s8.value.dp))
}

// ---------- 演示区 ----------

@Composable
private fun MotionStage(
    animValue: Float,
    patternId: String?,
    degraded: Boolean,
    color: org.minime.core.tokens.ColorTokens,
    typography: org.minime.core.tokens.TypographyTokens,
    spacing: SpacingTokens,
    shape: org.minime.core.tokens.ShapeTokens,
) {
    Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(percent = 12))
                .background(color.fillPrimaryColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (animValue * 240).dp)
                    .padding(start = spacing.s8.value.dp)
                    .size(36.dp)
                    .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                    .background(if (degraded) color.separatorColor else color.accentColor),
            )
            if (patternId != null) {
                Text(
                    text = patternId,
                    style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = color.textSecondaryColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(spacing.s4.value.dp)
                        .testTag("motion-stage-playing"),
                )
            }
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
    }
}

// ---------- 对照控件 ----------

@Composable
private fun MotionControls(
    systemScale: MotionScale,
    simulated: MotionScale?,
    onSimulate: (MotionScale?) -> Unit,
    slowmo: Boolean,
    onSlowmo: (Boolean) -> Unit,
    spacing: SpacingTokens,
    typography: org.minime.core.tokens.TypographyTokens,
    color: org.minime.core.tokens.ColorTokens,
) {
    val shape = MiniMeTokens.LocalShape.current

    Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s8.value.dp),
        ) {
            Text(
                text = "reducedMotion",
                style = typography.caption1.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.weight(1f))
            // 模拟开关：仅本地覆写 LocalMotionScale，不改系统设置
            listOf(
                null to "跟随系统",
                MotionScale.NORMAL to "全规格",
                MotionScale.OFF to "关闭",
            ).forEach { (value, label) ->
                val selected = simulated == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) color.accentColor else color.fillPrimaryColor)
                        .clickable { onSimulate(value) }
                        .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
                ) {
                    Text(
                        text = label,
                        style = typography.caption2.toTextStyle(),
                        color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "播放速度",
                style = typography.caption1.toTextStyle(),
                color = color.textPrimaryColor,
            )
            Spacer(Modifier.weight(1f))
            listOf(false to "1×", true to "0.25× 慢放").forEach { (value, label) ->
                val selected = slowmo == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) color.accentColor else color.fillPrimaryColor)
                        .clickable { onSlowmo(value) }
                        .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
                ) {
                    Text(
                        text = label,
                        style = typography.caption2.toTextStyle(),
                        color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.s8.value.dp))
        Text(
            text = "系统级缩放：${systemScale.name}",
            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
        )
        Spacer(Modifier.height(spacing.s8.value.dp))
    }
}

// ---------- 模式行 ----------

@Composable
private fun MotionPatternRow(
    pattern: MotionPattern,
    specs: MotionSpecs,
    typography: org.minime.core.tokens.TypographyTokens,
    color: org.minime.core.tokens.ColorTokens,
    spacing: SpacingTokens,
    shape: org.minime.core.tokens.ShapeTokens,
    size: org.minime.core.tokens.SizeTokens,
    onPlay: () -> Unit,
) {
    val resolved = specs.of(pattern.id)
    val enabled = pattern.mvp

    // 外层 Column 挂 testTag：语义树会剪掉无语义节点，行级断言（chip/播放钮二选一）
    // 需要一个行内语义锚点才能在未合并树上做父子导航
    Column(
        modifier = Modifier
            .padding(horizontal = spacing.gutter.value.dp)
            .testTag("motion-pattern-row"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pattern.id,
                    style = typography.callout.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = if (enabled) color.textPrimaryColor else color.textSecondaryColor,
                )
                Text(
                    text = pattern.purpose,
                    style = typography.caption2.toTextStyle(),
                    color = color.textSecondaryColor,
                )
            }
            if (!enabled) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(color.fillPrimaryColor)
                        .padding(horizontal = spacing.s8.value.dp, vertical = spacing.s4.value.dp)
                        .testTag("motion-pattern-phase2"),
                ) {
                    Text(
                        text = "二期",
                        style = typography.caption2.toTextStyle(),
                        color = color.textSecondaryColor,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                        .background(if (resolved != null) color.accentColor else color.fillPrimaryColor)
                        .clickable(enabled = resolved != null || pattern.id.isNotEmpty()) { onPlay() }
                        .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp)
                        .testTag("motion-pattern-play"),
                ) {
                    Text(
                        text = if (resolved != null) "播放" else "降级态",
                        style = typography.callout.toTextStyle(),
                        color = if (resolved != null) color.surfaceColor else color.textSecondaryColor,
                    )
                }
            }
        }
        // 双标注（铁律 9）：槽位 + 时长覆写令牌
        Text(
            text = buildString {
                append("槽位 ")
                append(if (pattern.slot.isAnimated) pattern.slot.path else "—")
                append(" · 时长 ")
                append(pattern.durationToken.path)
                append("（${pattern.specDurationMs}ms）")
                pattern.dampingOverrideToken?.let { append(" · 阻尼覆写 $it") }
                if (pattern.reducedMotion.name != "SHORT_TWEEN_HALF") {
                    append(" · 降级 ${pattern.reducedMotion.name}")
                }
            },
            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
            color = color.textSecondaryColor,
            modifier = Modifier.padding(top = spacing.s4.value.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.s12.value.dp)
                .height(1.dp)
                .background(color.separatorColor),
        )
    }
}
