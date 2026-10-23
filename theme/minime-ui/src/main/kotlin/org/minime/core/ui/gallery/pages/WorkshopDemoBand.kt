package org.minime.core.ui.gallery.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.minime.core.tokens.SpacingTokens
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toComposeColor
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.gallery.accentColor
import org.minime.core.ui.gallery.canvasColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.stateSuccessColor
import org.minime.core.ui.gallery.stateWarningColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor

/**
 * 工坊演示带（R5.5 布局重构，charter §3 / D1–D4）。
 *
 * 维护者指令（2026-10-23 21:41）：「固定顶部演示区域占内容区域的 1/5」——本组件即
 * 那 1/5：**固定不随参数流滚动**，覆写编译成功即即时换肤（「改完立刻看见」的常驻
 * 对照）。结构 = 组切换 chip 行（[DemoGroup] 六组）+ 横滑样本带（一行一样本，
 * 无卡片无分区——「样板页面不要有那种分块的」纪律的延续）。
 *
 * 高度预算（小屏 360×640，内容区 ≈486dp）：演示带 1/5 ≈ 97dp = chip 行 32dp +
 * 样本带 65dp；每组精选代表样本（不求全求代表，charter §7 风险对策）。
 *
 * 联动：分类 tab 切换 → [defaultDemoGroup] 给默认组（调用侧），用户手动 chip
 * 切换优先，直到下次切类。
 */
@Composable
fun WorkshopDemoBand(
    demoGroup: DemoGroup,
    onDemoGroup: (DemoGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiniMeTokens.LocalSpacing.current
    // 令牌集在 composable 上下文中一次取齐；LazyRow content 非 composable，
    // 只能捕获已取好的值（勿在 LazyListScope lambda 内读 local / remember）。
    val t = rememberDemoTokens()

    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = WorkshopLayoutSpec.DEMO_BAND_TAG },
    ) {
        DemoGroupChips(
            current = demoGroup,
            onSelect = onDemoGroup,
            spacing = spacing,
        )
        LazyRow(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s12.value.dp),
            contentPadding = PaddingValues(horizontal = spacing.s12.value.dp),
        ) {
            when (demoGroup) {
                DemoGroup.TEXT -> demoTextUnits(t)
                DemoGroup.BUTTONS -> demoButtonUnits(t)
                DemoGroup.CARDS -> demoCardUnits(t)
                DemoGroup.SWATCH -> demoSwatchUnits(t)
                DemoGroup.GLASS -> demoGlassUnit(t)
                DemoGroup.MOTION -> demoMotionUnit(t)
            }
        }
    }
}

/** 样本组 chip 行（六组；与分类 tab 形态同词汇，重名已由命名错开避免） */
@Composable
private fun DemoGroupChips(
    current: DemoGroup,
    onSelect: (DemoGroup) -> Unit,
    spacing: SpacingTokens,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s12.value.dp, vertical = spacing.s4.value.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DemoGroup.entries.forEach { group ->
            val selected = group == current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable { onSelect(group) }
                    .padding(horizontal = spacing.s8.value.dp, vertical = spacing.s4.value.dp),
            ) {
                Text(
                    text = group.title,
                    style = typography.caption2.toTextStyle(),
                    color = if (selected) color.textPrimaryColor else color.textSecondaryColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 横滑样本单元（小 label + 样本本体；宽度自适应内容，透明底无卡片——去分块纪律）。
 */
@Composable
private fun DemoUnit(
    label: String,
    content: @Composable () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current

    Column(modifier = Modifier.padding(vertical = spacing.s4.value.dp)) {
        Text(
            text = label,
            style = typography.caption2.toTextStyle(),
            color = color.textSecondaryColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(spacing.s4.value.dp))
        content()
    }
}

// ---------- 各组样本（横滑单元；精选代表，令牌由调用侧一次取齐） ----------

private fun LazyListScope.demoTextUnits(t: DemoTokens) {
    item {
        DemoUnit("largeTitle") {
            Text("主题引擎", style = t.typography.largeTitle.toTextStyle(), color = t.color.textPrimaryColor, maxLines = 1)
        }
    }
    item {
        DemoUnit("title3") {
            Text("Title3 层级", style = t.typography.title3.toTextStyle(), color = t.color.textPrimaryColor, maxLines = 1)
        }
    }
    item {
        DemoUnit("body") {
            Text("Body 正文样张", style = t.typography.body.toTextStyle(), color = t.color.textPrimaryColor, maxLines = 1)
        }
    }
    item {
        DemoUnit("caption") {
            Text("Caption 辅助", style = t.typography.caption1.toTextStyle(), color = t.color.textSecondaryColor, maxLines = 1)
        }
    }
}

private fun LazyListScope.demoButtonUnits(t: DemoTokens) {
    item {
        DemoUnit("主按钮") {
            Box(
                modifier = Modifier
                    .height(t.size.buttonHeight.value.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                    .background(t.color.accentColor)
                    .padding(horizontal = t.spacing.s24.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("主按钮", style = t.typography.callout.toTextStyle(), color = t.color.surfaceColor)
            }
        }
    }
    item {
        DemoUnit("次按钮") {
            Box(
                modifier = Modifier
                    .height(t.size.buttonHeight.value.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                    .background(t.color.fillPrimaryColor)
                    .padding(horizontal = t.spacing.s24.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("次按钮", style = t.typography.callout.toTextStyle(), color = t.color.textPrimaryColor)
            }
        }
    }
}

private fun LazyListScope.demoCardUnits(t: DemoTokens) {
    item {
        DemoUnit("radiusButton") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                    .background(t.color.accentColor),
            )
        }
    }
    item {
        DemoUnit("radiusCard") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusCard.value.dp))
                    .background(t.color.canvasColor)
                    .drawBehind {
                        drawRoundRect(
                            color = t.color.accentColor,
                            cornerRadius = CornerRadius(t.shape.radiusCard.value.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    },
            )
        }
    }
    item {
        DemoUnit("间距阶梯") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(t.spacing.s4.value.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(t.spacing.s4, t.spacing.s8, t.spacing.s12, t.spacing.s16).forEach { s ->
                    Box(
                        modifier = Modifier
                            .width(s.value.dp)
                            .height(32.dp)
                            .clip(ContinuousCornerShape(t.shape.radiusPill.value.dp))
                            .background(t.color.accentColor),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.demoSwatchUnits(t: DemoTokens) {
    listOf(
        "accent" to t.color.accentColor,
        "error" to t.color.stateErrorColor,
        "success" to t.color.stateSuccessColor,
        "warning" to t.color.stateWarningColor,
        "canvas" to t.color.canvasColor,
    ).forEach { (name, c) ->
        item {
            DemoUnit(name) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                        .background(c),
                )
            }
        }
    }
}

private fun LazyListScope.demoGlassUnit(t: DemoTokens) {
    item {
        val glass = MiniMeTokens.LocalGlass.current
        val elevation = MiniMeTokens.LocalElevation.current
        DemoUnit("glass.thin") {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(48.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusCard.value.dp))
                    .background(t.color.canvasColor)
                    .drawBehind {
                        // §6.8.3：tint + 饱和度 + 发丝线，零 GPU 成本；真 blur 随 R9
                        drawRoundRect(
                            color = glass.thin.tint.toComposeColor().copy(alpha = glass.thin.tintAlpha),
                            cornerRadius = CornerRadius(t.shape.radiusCard.value.dp.toPx()),
                        )
                        if (glass.thin.hairline) {
                            drawRoundRect(
                                color = t.color.separatorColor,
                                cornerRadius = CornerRadius(t.shape.radiusCard.value.dp.toPx()),
                                style = Stroke(width = elevation.hairline.px),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "α${glass.thin.tintAlpha.wsFmt()}",
                    style = t.typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = t.color.textPrimaryColor,
                )
            }
        }
    }
}

private fun LazyListScope.demoMotionUnit(t: DemoTokens) {
    item {
        val spatial = t.motion.spatialDefault
        val anim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        DemoUnit("motion.spatial.default 点播") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "k ${spatial.stiffness.wsFmt()} · c ${spatial.damping.wsFmt()}",
                        style = t.typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                        color = t.color.textSecondaryColor,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                            .background(t.color.fillPrimaryColor)
                            .clickable {
                                scope.launch {
                                    anim.snapTo(0f)
                                    anim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = spatial.damping,
                                            stiffness = spatial.stiffness,
                                        ),
                                        initialVelocity = spatial.initialVelocity,
                                    )
                                    anim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = spatial.damping,
                                            stiffness = spatial.stiffness,
                                        ),
                                    )
                                }
                            }
                            .padding(horizontal = t.spacing.s12.value.dp, vertical = t.spacing.s4.value.dp),
                    ) {
                        Text("播放", style = t.typography.caption1.toTextStyle(), color = t.color.accentColor)
                    }
                }
                Spacer(Modifier.height(t.spacing.s4.value.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(32.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (anim.value * 104).dp)
                            .size(24.dp)
                            .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                            .background(t.color.accentColor),
                    )
                }
            }
        }
    }
}

// ---------- 令牌集 ----------

private class DemoTokens(
    val color: org.minime.core.tokens.ColorTokens,
    val typography: org.minime.core.tokens.TypographyTokens,
    val shape: org.minime.core.tokens.ShapeTokens,
    val size: org.minime.core.tokens.SizeTokens,
    val spacing: SpacingTokens,
    val motion: org.minime.core.tokens.MotionTokens,
)

@Composable
private fun rememberDemoTokens(): DemoTokens = DemoTokens(
    color = MiniMeTokens.LocalColor.current,
    typography = MiniMeTokens.LocalTypography.current,
    shape = MiniMeTokens.LocalShape.current,
    size = MiniMeTokens.LocalSize.current,
    spacing = MiniMeTokens.LocalSpacing.current,
    motion = MiniMeTokens.LocalMotion.current,
)
