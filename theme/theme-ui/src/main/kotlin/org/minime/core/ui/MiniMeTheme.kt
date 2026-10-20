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
 */
@Composable
fun MiniMeTheme(
    snapshot: ThemeSnapshot,
    content: @Composable () -> Unit,
) {
    // 映射只在对应子系统变化时重算：remember 的 key 用子系统实例本身
    val colorScheme = remember(snapshot.color) { snapshot.color.toColorScheme() }
    val typography = remember(snapshot.typography) { snapshot.typography.toTypography() }
    val shapes = remember(snapshot.shape) { snapshot.shape.toShapes() }

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
