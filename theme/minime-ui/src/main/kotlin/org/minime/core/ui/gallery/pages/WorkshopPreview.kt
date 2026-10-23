package org.minime.core.ui.gallery.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
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
import org.minime.core.ui.gallery.stateErrorColor
import org.minime.core.ui.gallery.stateSuccessColor
import org.minime.core.ui.gallery.stateWarningColor
import org.minime.core.ui.gallery.fillPrimaryColor
import org.minime.core.ui.gallery.groupedColor
import org.minime.core.ui.gallery.separatorColor
import org.minime.core.ui.gallery.surfaceColor
import org.minime.core.ui.gallery.textPrimaryColor
import org.minime.core.ui.gallery.textSecondaryColor

/**
 * 工坊实时预览（charter §4「工作区左」）。
 *
 * 全部样本读当前生效子系统令牌（[MiniMeTokens] locals）——覆写编译成功即整树换肤，
 * 「改完立刻看见」；样本自身即令牌的活文档（自举纪律的消费侧证明）。
 *
 * 含动效点播：以当前 `motion.spatial.default` spring 播放一次过冲滑移
 * （v0 = initialVelocity）——动效 tab 的即时反馈。
 */
@Composable
fun WorkshopPreview(modifier: Modifier = Modifier) {
    val spacing = MiniMeTokens.LocalSpacing.current

    // 左列 = 实时读数（WCAG 矩阵，随覆写重算）+ 样本预览（两个滚动区）
    Column(modifier = modifier.fillMaxSize()) {
        WorkshopWcagMatrix(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .padding(horizontal = spacing.s16.value.dp),
        )
        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .padding(horizontal = spacing.s16.value.dp),
        ) {
            PreviewSamples()
        }
    }
}

/**
 * 样本预览（P3：去卡片分块 → 类别 tab + 可滚动清单）。
 *
 * 维护者指示（2026-10-23）：「样板页面不要有那种分块的，影响操作，可以适当加入
 * tab 菜单设计或者滚动页面设计」。原实现每个样本组各套一张 [PreviewCard] 外框，
 * 观感零碎且浪费纵向空间；改为**一组一屏的 tab 切换**：同一时刻只见一类样本，
 * 清单直接铺在画布上，行与行之间用分隔线区分，不再嵌套卡片。
 */
@Composable
private fun PreviewSamples() {
    var group by remember { mutableStateOf(PreviewGroup.TEXT) }
    val spacing = MiniMeTokens.LocalSpacing.current
    // 令牌集在 composable 上下文中一次取齐；LazyColumn content 非 composable，
    // 只能捕获已取好的值（勿在 LazyListScope lambda 内读 local / remember）。
    val t = rememberPreviewTokens()

    Column(modifier = Modifier.fillMaxSize()) {
        PreviewGroupTabs(
            current = group,
            onSelect = { group = it },
            spacing = spacing,
        )
        // 占剩余高度（勿用 fillMaxSize——会把上方 tab 条挤出屏幕）
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { Spacer(Modifier.height(spacing.s8.value.dp)) }
            when (group) {
                PreviewGroup.TEXT -> textSamples(t)
                PreviewGroup.BUTTONS -> buttonSamples(t)
                PreviewGroup.CARDS -> cardSamples(t)
                PreviewGroup.SWATCH -> swatchSamples(t)
                PreviewGroup.GLASS -> glassSamples(t)
                PreviewGroup.MOTION -> motionSamples(t)
            }
            item { Spacer(Modifier.height(spacing.s24.value.dp)) }
        }
    }
}

/**
 * 样本类别（tab）。
 *
 * 命名避开与工坊分类 tab（色板 / 字阶 / 圆角 / 间距 / 动效 / 玻璃）重名——
 * 同屏两行 tab 若出现同名项，用户无法分辨哪行控制什么（P3 评审教训）。
 */
private enum class PreviewGroup(val title: String) {
    TEXT("文本"),
    BUTTONS("按钮"),
    CARDS("卡片"),
    SWATCH("配色"),
    GLASS("玻璃拟态"),
    MOTION("动效点播"),
}

/** 样本类别 tab 条 */
@Composable
private fun PreviewGroupTabs(
    current: PreviewGroup,
    onSelect: (PreviewGroup) -> Unit,
    spacing: SpacingTokens,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s4.value.dp),
    ) {
        PreviewGroup.entries.forEach { group ->
            val selected = group == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) color.accentColor else color.fillPrimaryColor)
                    .clickable { onSelect(group) }
                    .padding(vertical = spacing.s8.value.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = group.title,
                    style = typography.caption2.toTextStyle(),
                    color = if (selected) color.surfaceColor else color.textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 行容器（去卡片化：透明底 + 分隔线） */
@Composable
private fun PreviewRow(
    title: String,
    spacing: SpacingTokens,
    content: @Composable () -> Unit,
) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.padding(bottom = spacing.s8.value.dp),
        )
        content()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.s16.value.dp)
                .height(1.dp)
                .background(color.separatorColor),
        )
    }
}


// ---------- 样本内容（tab 切换的清单主体；令牌由调用侧在 composable 上下文中取好后传入） ----------

private fun LazyListScope.textSamples(t: PreviewTokens) {
    item {
        PreviewRow("文本", t.spacing) {
            Column {
                Text("主题引擎 MiniMe", style = t.typography.largeTitle.toTextStyle(), color = t.color.textPrimaryColor)
                Text("Title3 层级样张", style = t.typography.title3.toTextStyle(), color = t.color.textPrimaryColor)
                Text(
                    "Body 正文样张——实时预览随覆写即时换肤（§10.7）。",
                    style = t.typography.body.toTextStyle(),
                    color = t.color.textPrimaryColor,
                )
                Text("Caption 辅助说明样张", style = t.typography.caption1.toTextStyle(), color = t.color.textSecondaryColor)
            }
        }
    }
}

private fun LazyListScope.buttonSamples(t: PreviewTokens) {
    item {
        PreviewRow("按钮", t.spacing) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.s12.value.dp)) {
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
}

private fun LazyListScope.cardSamples(t: PreviewTokens) {
    item {
        PreviewRow("卡片与列表", t.spacing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousCornerShape(t.shape.radiusCard.value.dp))
                    .background(t.color.groupedColor)
                    .padding(t.spacing.s16.value.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(t.size.icon.value.dp)
                            .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                            .background(t.color.accentColor),
                    )
                    Spacer(Modifier.width(t.spacing.s12.value.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("列表行标题", style = t.typography.callout.toTextStyle(), color = t.color.textPrimaryColor)
                        Text("副标题说明", style = t.typography.caption2.toTextStyle(), color = t.color.textSecondaryColor)
                    }
                    Text("›", style = t.typography.title3.toTextStyle(), color = t.color.textSecondaryColor)
                }
                Spacer(Modifier.height(t.spacing.s12.value.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "开关",
                        style = t.typography.callout.toTextStyle(),
                        color = t.color.textPrimaryColor,
                        modifier = Modifier.weight(1f),
                    )
                    val w = t.size.switchWidth.value.dp
                    val h = t.size.switchHeight.value.dp
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(h)
                            .clip(ContinuousCornerShape(t.shape.radiusPill.value.dp))
                            .background(t.color.accentColor),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(h - 4.dp)
                                .clip(ContinuousCornerShape(t.shape.radiusPill.value.dp))
                                .background(Color.White)
                                .padding(start = w - h),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.swatchSamples(t: PreviewTokens) {
    item {
        PreviewRow("色板", t.spacing) {
            Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.s8.value.dp)) {
                listOf(
                    "accent" to t.color.accentColor,
                    "error" to t.color.stateErrorColor,
                    "success" to t.color.stateSuccessColor,
                    "warning" to t.color.stateWarningColor,
                    "canvas" to t.color.canvasColor,
                ).forEach { (name, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                                .background(c),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = t.typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                            color = t.color.textSecondaryColor,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.glassSamples(t: PreviewTokens) {
    item {
        val glass = MiniMeTokens.LocalGlass.current
        val elevation = MiniMeTokens.LocalElevation.current
        PreviewRow("玻璃（glass.thin 拟态近似）", t.spacing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(ContinuousCornerShape(t.shape.radiusCard.value.dp))
                    .background(t.color.groupedColor)
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
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = elevation.hairline.px,
                                ),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "tint ${glass.thin.tintAlpha.wsFmt()} · blur ${glass.thin.blur.value.wsFmt()}${glass.thin.blur.unit}",
                    style = t.typography.caption1.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                    color = t.color.textPrimaryColor,
                )
            }
        }
    }
}

private fun LazyListScope.motionSamples(t: PreviewTokens) {
    item {
        val spatial = t.motion.spatialDefault
        val anim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        PreviewRow("动效（motion.spatial.default 点播）", t.spacing) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (anim.value * 160).dp)
                            .size(32.dp)
                            .clip(ContinuousCornerShape(t.shape.radiusButton.value.dp))
                            .background(t.color.accentColor),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "k ${spatial.stiffness.wsFmt()} · c ${spatial.damping.wsFmt()} · v0 ${spatial.initialVelocity.wsFmt()}",
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
                            .padding(horizontal = t.spacing.s16.value.dp, vertical = t.spacing.s8.value.dp),
                    ) {
                        Text("播放", style = t.typography.callout.toTextStyle(), color = t.color.accentColor)
                    }
                }
            }
        }
    }
}

/** 样本函数所需的令牌集（一次取齐，避免重复读 local） */
private class PreviewTokens(
    val color: org.minime.core.tokens.ColorTokens,
    val typography: org.minime.core.tokens.TypographyTokens,
    val shape: org.minime.core.tokens.ShapeTokens,
    val size: org.minime.core.tokens.SizeTokens,
    val spacing: SpacingTokens,
    val motion: org.minime.core.tokens.MotionTokens,
)

@Composable
private fun rememberPreviewTokens(): PreviewTokens = PreviewTokens(
    color = MiniMeTokens.LocalColor.current,
    typography = MiniMeTokens.LocalTypography.current,
    shape = MiniMeTokens.LocalShape.current,
    size = MiniMeTokens.LocalSize.current,
    spacing = MiniMeTokens.LocalSpacing.current,
    motion = MiniMeTokens.LocalMotion.current,
)
