package org.minime.gallery.sections

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.minime.core.api.MiniMeEngine
import org.minime.core.ui.LocalThemeSnapshot
import org.minime.core.ui.MiniMeTokens
import org.minime.core.ui.draw.ContinuousCornerShape
import org.minime.core.ui.m3.toFontFamily
import org.minime.core.ui.m3.toTextStyle
import org.minime.core.ui.runtime.ThemeManager
import org.minime.core.ui.runtime.ThemeStatus
import org.minime.gallery.ThemeModifiers
import org.minime.gallery.accentColor
import org.minime.gallery.fillPrimaryColor
import org.minime.gallery.surfaceColor
import org.minime.gallery.textPrimaryColor
import org.minime.gallery.textSecondaryColor

/**
 * 分区六：调试面板（白皮书 §10.8）。
 *
 * R3 基础版：主题状态（当前包 + 版本 + 引擎版本 + 激活修饰符）+ 降级链状态。
 * §10.8 完整开关表（fontScale / RTL / 慢放 / 性能档 / 视觉过滤 / 焦点 TalkBack）
 * 随 R5 / R9 / R10 逐项落地——本文件是它们的落点。
 *
 * 修饰符开关走 `ThemeManager.select`（§8.1 换肤路径），与顶栏主题入口同源。
 */
@Composable
fun DebugPanelSection(manager: ThemeManager, modifier: Modifier = Modifier) {
    val snapshot = LocalThemeSnapshot.current
    val status by manager.status.collectAsState()
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s16.value.dp)) {
                Text(
                    text = "调试面板",
                    style = typography.largeTitle.toTextStyle(),
                    color = color.textPrimaryColor,
                )
                Spacer(Modifier.height(spacing.s4.value.dp))
                Text(
                    text = "主题状态与降级链实测（§10.8）；性能与无障碍开关随批次补齐。",
                    style = typography.footnote.toTextStyle(),
                    color = color.textSecondaryColor,
                )
            }
        }

        // ---- 主题 ----
        item {
            DebugGroup(title = "主题") {
                DebugRow("当前包", snapshot.meta.packName)
                DebugRow("包版本", snapshot.meta.packVersion)
                DebugRow("引擎版本", MiniMeEngine.VERSION)
                DebugRow("激活修饰符", snapshot.meta.activeModifiers.ifEmpty { listOf("（无）") }.joinToString(" · "))
                DebugRow(
                    "降级链状态",
                    when (val s = status) {
                        is ThemeStatus.Ready ->
                            if (s.degraded) "Ready（降级可用，fellBack=${s.packId}）" else "Ready"
                        is ThemeStatus.Loading -> "Loading（${s.packId}）"
                        is ThemeStatus.Failed -> "Failed：${s.code}（fellBack=${s.fellBackTo ?: "无"}）"
                        ThemeStatus.Idle -> "Idle"
                    },
                )
                DebugRow("编译警告", "${(status as? ThemeStatus.Ready)?.warnings ?: 0} 条")
            }
        }

        // ---- 修饰符开关 ----
        item {
            DebugGroup(title = "修饰符（resolutionOrder）") {
                val active = snapshot.meta.activeModifiers
                ThemeModifiers.ALL.forEach { modifier ->
                    ModifierToggleRow(
                        label = modifier,
                        checked = modifier in active,
                        onToggle = { manager.toggleModifier(modifier, active) },
                    )
                }
            }
        }

        // ---- 待落地项 ----
        item {
            DebugGroup(title = "随批次落地") {
                DebugRow("fontScale 1.0→2.0", "R9（G5）")
                DebugRow("RTL 镜像", "R8 页面矩阵")
                DebugRow("慢放倍率", "R5 动效检验")
                DebugRow("性能档位模拟", "R9 降级链三档")
                DebugRow("视觉过滤（色盲）", "R10 收口")
                DebugRow("焦点 / TalkBack", "R6 起逐组件")
            }
        }
    }
}

@Composable
private fun DebugGroup(title: String, content: @Composable () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val shape = MiniMeTokens.LocalShape.current

    Column(modifier = Modifier.padding(horizontal = spacing.gutter.value.dp, vertical = spacing.s8.value.dp)) {
        Text(
            text = title,
            style = typography.headline.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.padding(vertical = spacing.s8.value.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(shape.radiusCard.value.dp))
                .background(color.surfaceColor)
                .padding(horizontal = spacing.s16.value.dp, vertical = spacing.s4.value.dp),
        ) { content() }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val mono = typography.monospace.toTextStyle().copy(fontFamily = "monospace".toFontFamily())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.body.toTextStyle(),
            color = color.textPrimaryColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = mono,
            color = color.textSecondaryColor,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun ModifierToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val color = MiniMeTokens.LocalColor.current
    val typography = MiniMeTokens.LocalTypography.current
    val spacing = MiniMeTokens.LocalSpacing.current
    val size = MiniMeTokens.LocalSize.current
    val shape = MiniMeTokens.LocalShape.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = spacing.s8.value.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.body.toTextStyle(),
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
                .background(if (checked) color.accentColor else color.fillPrimaryColor),
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(h - 4.dp)
                    .clip(ContinuousCornerShape(shape.radiusPill.value.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .then(if (checked) Modifier.padding(start = w - h) else Modifier),
            )
        }
    }
}

/** 修饰符开关切换（保留其余修饰符，§10.8 调试面板的 theme 行） */
private fun ThemeManager.toggleModifier(target: String, active: List<String>) {
    val next = if (target in active) active - target else active + target
    select(packId = ThemeManager.BUILT_IN, modifiers = next)
}
