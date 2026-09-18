package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.theme.AppUiMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 通用组件视觉回归金标示例（UI-4b）。
 *
 * 约定：
 *  - 只给通用 component 拍金标；composite/ 下的业务复合组件还在高频迭代，不拍。
 *  - 新金标：CI 跑 record 任务生成；日常跑 verify 任务对比，不一致即红。
 *  - 浅色拍一张即可，暗色/大号字体另补测试时再照此模板加。
 *
 * 运行：./gradlew :newui:verifyRoborazziDebug（CI 门禁）
 * 记录：./gradlew :newui:recordRoborazziDebug（更新金标，需人工 review diff）
 */
@RunWith(RobolectricTestRunner::class)
class AppButtonsScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun buttonVariants_light() {
        rule.setContent {
            AppTheme(uiMode = AppUiMode.Light) {
                Surface {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppButton("Primary", onClick = {}, variant = AppButtonVariant.Primary)
                        AppButton("FilledTonal", onClick = {}, variant = AppButtonVariant.FilledTonal)
                        AppButton("Outlined", onClick = {}, variant = AppButtonVariant.Outlined)
                        AppButton("Text", onClick = {}, variant = AppButtonVariant.Text)
                        AppButton("Disabled", onClick = {}, enabled = false)
                    }
                }
            }
        }
        captureRoboImage("src/test/screenshots/AppButtons_light.png")
    }
}
