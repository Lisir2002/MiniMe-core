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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val size = MiniMeTokens.LocalSize.current
    val motion = MiniMeTokens.LocalMotion.current

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

/** 样本预览（原 LazyColumn 内容，抽为独立 composable 供左右分栏复用） */
@Composable
private fun PreviewSamples() {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current
    val size = MiniMeTokens.LocalSize.current
    val motion = MiniMeTokens.LocalMotion.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(spacing.s12.value.dp)) }

        // ---- 文本组（字阶预览） ----
        item {
            PreviewCard(title = "文本", spacing) {
                Text(
                    text = "主题引擎 MiniMe",
                    style = typography.largeTitle.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Text(
                    text = "Title3 层级样张",
                    style = typography.title3.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Text(
                    text = "Body 正文样张——实时预览随覆写即时换肤（§10.7）。",
                    style = typography.body.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Text(
                    text = "Caption 辅助说明样张",
                    style = typography.caption1.toTextStyle(),
                    color = color.textSecondaryColor,
                )
            }
        }

        // ---- 按钮组 ----
        item {
            PreviewCard(title = "按钮", spacing) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s12.value.dp)) {
                    Box(
                        modifier = Modifier
                            .height(size.buttonHeight.value.dp)
                            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                            .background(color.accentColor)
                            .padding(horizontal = spacing.s24.value.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "主按钮",
                            style = typography.callout.toTextStyle(),
                            color = color.surfaceColor,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(size.buttonHeight.value.dp)
                            .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                            .background(color.fillPrimaryColor)
                            .padding(horizontal = spacing.s24.value.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "次按钮",
                            style = typography.callout.toTextStyle(),
                            color = color.textPrimaryColor,
                        )
                    }
                }
            }
        }

        // ---- 卡片 + 列表行 + 开关 ----
        item {
            PreviewCard(title = "卡片与列表", spacing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                        .background(color.groupedColor)
                        .padding(spacing.s16.value.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(size.icon.value.dp)
                                .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                                .background(color.accentColor),
                        )
                        Spacer(Modifier.width(spacing.s12.value.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "列表行标题",
                                style = typography.callout.toTextStyle(),
                                color = color.textPrimaryColor,
                            )
                            Text(
                                text = "副标题说明",
                                style = typography.caption2.toTextStyle(),
                                color = color.textSecondaryColor,
                            )
                        }
                        Text(
                            text = "›",
                            style = typography.title3.toTextStyle(),
                            color = color.textSecondaryColor,
                        )
                    }
                    Spacer(Modifier.height(spacing.s12.value.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "开关",
                            style = typography.callout.toTextStyle(),
                            color = color.textPrimaryColor,
                            modifier = Modifier.weight(1f),
                        )
                        val w = size.switchWidth.value.dp
                        val h = size.switchHeight.value.dp
                        Box(
                            modifier = Modifier
                                .width(w)
                                .height(h)
                                .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                                .background(color.accentColor),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(h - 4.dp)
                                    .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                                    .background(Color.White)
                                    .padding(start = w - h),
                            )
                        }
                    }
                }
            }
        }

        // ---- 色板条 ----
        item {
            PreviewCard(title = "色板", spacing) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s8.value.dp)) {
                    listOf(
                        "accent" to color.accentColor,
                        "error" to color.stateErrorColor,
                        "success" to color.stateSuccessColor,
                        "warning" to color.stateWarningColor,
                        "canvas" to color.canvasColor,
                    ).forEach { (name, c) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                                    .background(c),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = name,
                                style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                                color = color.textSecondaryColor,
                            )
                        }
                    }
                }
            }
        }

        // ---- 玻璃样本（thin 配方拟态近似） ----
        item {
            val glass = MiniMeTokens.LocalGlass.current
            val elevation = MiniMeTokens.LocalElevation.current
            PreviewCard(title = "玻璃（glass.thin 拟态近似）", spacing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                        .background(color.groupedColor)
                        .drawBehind {
                            // §6.8.3：tint + 饱和度 + 发丝线，零 GPU 成本；真 blur 随 R9
                            drawRoundRect(
                                color = glass.thin.tint.toComposeColor().copy(alpha = glass.thin.tintAlpha),
                                cornerRadius = CornerRadius(shape.radiusCard.value.dp.toPx()),
                            )
                            if (glass.thin.hairline) {
                                drawRoundRect(
                                    color = color.separatorColor,
                                    cornerRadius = CornerRadius(shape.radiusCard.value.dp.toPx()),
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
                        style = typography.caption1.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                        color = color.textPrimaryColor,
                    )
                }
            }
        }

        // ---- 动效点播（spatial.default spring） ----
        item {
            val spatial = motion.spatialDefault
            val anim = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            PreviewCard(title = "动效（motion.spatial.default 点播）", spacing) {
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
                                .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                                .background(color.accentColor),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "k ${spatial.stiffness.wsFmt()} · c ${spatial.damping.wsFmt()} · v0 ${spatial.initialVelocity.wsFmt()}",
                            style = typography.caption2.toTextStyle().copy(fontFamily = "monospace".toFontFamily()),
                            color = color.textSecondaryColor,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(ContinuousCornerShape(shape.radiusButton.value.dp))
                                .background(color.fillPrimaryColor)
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
                                .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s8.value.dp),
                        ) {
                            Text(
                                text = "播放",
                                style = typography.callout.toTextStyle(),
                                color = color.accentColor,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(spacing.s24.value.dp)) }
    }
}

/** 预览分组卡（surface + radiusCard + 发丝线） */
@Composable
private fun PreviewCard(title: String, spacing: org.minime.core.tokens.SpacingTokens, content: @Composable () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val shape = MiniMeTokens.LocalShape.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.s8.value.dp),
    ) {
        Text(
            text = title,
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.padding(bottom = spacing.s8.value.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                .background(color.surfaceColor)
                .padding(spacing.s16.value.dp),
        ) {
            content()
        }
    }
}
