package org.minime.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.minime.core.tokens.ThemeSnapshot
import org.minime.core.ui.m3.toColorScheme
import org.minime.core.ui.m3.toShapes
import org.minime.core.ui.m3.toTypography
import org.minime.core.ui.motion.LocalMotionScale
import org.minime.core.ui.motion.LocalMotionSpecs
import org.minime.core.ui.motion.LocalMiniMeMotionScheme
import org.minime.core.ui.motion.MotionScaleProvider
import org.minime.core.ui.motion.MotionSpecs
import org.minime.core.ui.motion.toMiniMeMotionScheme
import org.minime.core.ui.runtime.ThemeManager

/**
 * 主题入口（§8.1 换肤时序的落地）。
 *
 * 一次提供两件事：
 * 1. **十个 `Local*Tokens`**——引擎自绘组件的消费面（§6.2）；
 * 2. **M3 `MaterialTheme`**——存量 M3 页面经映射层获得新配色（§9.1），
 *    映射只覆盖 color / typography / shape。
 *
 * 换肤行为对齐 §8.1 三条约定：
 * - **直切无渐变**：值替换即完成，不做 crossfade（与 iOS 系统外观切换一致）；
 * - **进行中动效不取消**：spring 参数对象不可变，在飞动画按旧规格跑完，天然承接；
 * - **子系统实例复用**：相等子系统在上游 `ThemeManager` 已复用旧实例（§8.2），
 *   因此这些子树**零重组**（R2 验收项的依据）。
 *
 * R5 追加：整树由 [MotionScaleProvider] 包裹——系统级无障碍缩放
 * （`Settings.Global.ANIMATOR_DURATION_SCALE`）在引擎内自足读取，
 * 宿主无需额外接线；读不到按 NORMAL（不误降级）。
 */
@Composable
fun MiniMeTheme(
    snapshot: ThemeSnapshot,
    content: @Composable () -> Unit,
) {
    // R5：系统级缩放在此挂载（引擎自足）。读取是一次性的——设置变更需重组才刷新，
    // 与动效层其余部分同语义（主题包覆写同样需要重编译/重选才生效）。
    MotionScaleProvider {
        MiniMeThemeContent(snapshot = snapshot, content = content)
    }
}

@Composable
private fun MiniMeThemeContent(
    snapshot: ThemeSnapshot,
    content: @Composable () -> Unit,
) {
    // 映射只在对应子系统变化时重算：remember 的 key 用子系统实例本身
    val motionScale = LocalMotionScale.current
    val colorScheme = remember(snapshot.color) { snapshot.color.toColorScheme() }
    val typography = remember(snapshot.typography) { snapshot.typography.toTypography() }
    val shapes = remember(snapshot.shape) { snapshot.shape.toShapes() }
    // R5：六槽 spring 只读回喂动效方案（单一真源仍是 MotionTokens）
    val motionScheme = remember(snapshot.motion) { snapshot.motion.toMiniMeMotionScheme() }
    // R5：动效规格工厂（消费方唯一入口，避免各处自行拼 AnimationSpec）
    val motionSpecs = remember(snapshot.motion, snapshot.interaction, motionScale) {
        MotionSpecs(
            motion = snapshot.motion,
            interaction = snapshot.interaction,
            scale = motionScale,
        )
    }

    CompositionLocalProvider(
        MiniMeTokens.LocalColor provides snapshot.color,
        MiniMeTokens.LocalTypography provides snapshot.typography,
        MiniMeTokens.LocalShape provides snapshot.shape,
        MiniMeTokens.LocalSize provides snapshot.size,
        MiniMeTokens.LocalSpacing provides snapshot.spacing,
        MiniMeTokens.LocalMotion provides snapshot.motion,
        MiniMeTokens.LocalInteraction provides snapshot.interaction,
        MiniMeTokens.LocalElevation provides snapshot.elevation,
        MiniMeTokens.LocalOpacity provides snapshot.opacity,
        MiniMeTokens.LocalGlass provides snapshot.glass,
        LocalThemeSnapshot provides snapshot,
        LocalMotionSpecs provides motionSpecs,
        LocalMiniMeMotionScheme provides motionScheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * 从 [ThemeManager] 订阅当前快照（应用唯一入口）。
 *
 * 冷启动前必须先调用 `ThemeManager.initialize()`——它同步恢复上次选择，
 * 保证**首帧即目标主题**（§8.2）。
 */
@Composable
fun MiniMeTheme(
    manager: ThemeManager,
    content: @Composable () -> Unit,
) {
    val snapshot by manager.snapshot.collectAsState()
    MiniMeTheme(snapshot = snapshot, content = content)
}
