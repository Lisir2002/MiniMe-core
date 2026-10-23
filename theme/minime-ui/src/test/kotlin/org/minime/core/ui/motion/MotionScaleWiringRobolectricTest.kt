package org.minime.core.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.minime.core.motion.MotionScale
import org.minime.core.ui.MiniMeTheme
import org.minime.core.ui.MiniMeThemeBootstrap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 系统级动画缩放接线测试（R5-D 补缺口：`MiniMeTheme` 内挂 `MotionScaleProvider`）。
 *
 * 背景：提交 B 建了 [MotionScaleProvider] 但未在任何入口挂载，`LocalMotionScale`
 * 永远走兜底 NORMAL——系统无障碍开关（设置里的「移除动画」）在引擎内**完全失效**。
 * 本测试锁定该接线：主题树内读到的档位 = 系统设置值，且规格工厂按档位降级。
 *
 * 两级分离的另一半（主题包级 reducedMotion 修饰符）在 theme-engine 单测覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionScaleWiringRobolectricTest {

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    private fun resolver() =
        ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun setThemeTree() {
        rule.setContent {
            MiniMeTheme(
                manager = MiniMeThemeBootstrap.create(LocalContext.current)
                    .also { it.initialize() },
            ) {
                WiringProbe()
            }
        }
    }

    /** 探针：把主题树内的两个 local 提到测试可见（组合期求值） */
    @Composable
    private fun WiringProbe() {
        probedScale = LocalMotionScale.current
        probedSpecs = LocalMotionSpecs.current
        Unit
    }

    private var probedScale: MotionScale? = null
    private var probedSpecs: MotionSpecs? = null

    @Test
    fun 系统OFF档_主题树读到OFF且规格降级为短tween减半() {
        Settings.Global.putFloat(resolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        setThemeTree()

        Assert.assertEquals(MotionScale.OFF, probedScale)
        val spec = requireNotNull(probedSpecs).require("page.push/pop")
        Assert.assertTrue("OFF 档应降级为 tween，实际: ${spec.spec}", spec.spec is TweenSpec<*>)
        // 铁律 7 时长减半：SLOW 300ms → 150ms
        Assert.assertEquals(150, spec.durationMs)
    }

    @Test
    fun 系统NORMAL档_主题树读到NORMAL且规格为全量spring() {
        Settings.Global.putFloat(resolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

        setThemeTree()

        Assert.assertEquals(MotionScale.NORMAL, probedScale)
        val spec = requireNotNull(probedSpecs).require("page.push/pop")
        Assert.assertTrue("NORMAL 档应为 spring，实际: ${spec.spec}", spec.spec is SpringSpec<*>)
        Assert.assertEquals(300, spec.durationMs)
    }

    @Test
    fun 系统PARTIAL档_不叠加降级仍为全规格spring() {
        Settings.Global.putFloat(resolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)

        setThemeTree()

        Assert.assertEquals(MotionScale.PARTIAL, probedScale)
        val spec = requireNotNull(probedSpecs).require("page.push/pop")
        Assert.assertTrue("PARTIAL 档应保持全规格，实际: ${spec.spec}", spec.spec is SpringSpec<*>)
    }
}
