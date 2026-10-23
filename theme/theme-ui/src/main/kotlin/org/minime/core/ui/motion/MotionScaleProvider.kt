package org.minime.core.ui.motion

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.minime.core.motion.MotionScale

/**
 * 系统级无障碍动画缩放的读取与提供（白皮书 §6.4.6 铁律 7 的「系统级」一路）。
 *
 * ## 两级分离（RC 决策）
 *
 * | 级别 | 载体 | 生效时机 | 语义 |
 * |---|---|---|---|
 * | **主题包级** | `reducedMotion` 修饰符 | 编译期 | 六槽 spring 高阻尼化（引擎侧改写令牌） |
 * | **系统级**（本文件） | `Settings.Global.ANIMATOR_DURATION_SCALE` | 运行时 | 无障碍开关，0 = 关闭全部动画 |
 *
 * **系统级优先于主题包级**：用户显式关闭动画时，主题包再怎么配也应当尊重——
 * 反之不成立（用户没关，就不该替用户降级）。
 *
 * **PARTIAL（0<v<1）不叠加降级**：尊重用户的中间档，系统值 0.5 交给动画系统自身缩放，
 * 本层不做额外减半（否则 0.5 档会被二次削弱成 0.25 档）。
 */
object MotionScaleReader {

    /**
     * 读系统动画缩放。
     *
     * 读不到（权限/设备差异/非 Android 运行时）时返回 [MotionScale.NORMAL]——
     * 读不到 ≠ 用户关闭动画，不得误降级。
     */
    fun read(contentResolver: ContentResolver): MotionScale {
        val raw = try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) {
            return MotionScale.NORMAL
        }
        return MotionScale.fromSystemValue(raw)
    }
}

/**
 * 当前生效的动画缩放档（系统级）。
 *
 * 默认 [MotionScale.NORMAL]——未包裹 [MotionScaleProvider] 的组合（如预览）按全规格渲染。
 */
val LocalMotionScale: ProvidableCompositionLocal<MotionScale> =
    staticCompositionLocalOf { MotionScale.NORMAL }

/**
 * 提供系统级动画缩放（读一次，值不变则不重组）。
 *
 * 用法：`MotionScaleProvider { content() }` 包在应用根部。
 */
@Composable
fun MotionScaleProvider(content: @Composable () -> Unit) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val scale = androidx.compose.runtime.remember(resolver) { MotionScaleReader.read(resolver) }
    androidx.compose.runtime.CompositionLocalProvider(LocalMotionScale provides scale, content = content)
}
