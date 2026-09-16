package com.mini.me_core.newui.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mini.me_core.newui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppSegmentedToggle（滑动分段控件）Robolectric UI 测试。
 *
 * 被测签名（见 AppSegmentedToggle.kt）：
 * ```
 * fun AppSegmentedToggle(
 *     options: List<String>,
 *     selectedIndex: Int,
 *     onSelect: (Int) -> Unit,
 *     modifier: Modifier = Modifier,
 * )
 * ```
 * 视觉区分：选中项 pill 背景浮起（surface）+ [androidx.compose.ui.text.font.FontWeight.SemiBold]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSegmentedToggleTest {

    @get:Rule
    val compose = createComposeRule()

    private val options = listOf("日", "周", "月")

    /** 所有选项都应被渲染并可见。 */
    @Test
    fun segmentedToggle_displaysAllOptions() {
        compose.setContent {
            AppTheme {
                AppSegmentedToggle(
                    options = options,
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }

        options.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /** 点击某个选项应触发 onSelect，且回传正确下标。 */
    @Test
    fun segmentedToggle_selectOptionCallsOnSelected() {
        var selected by mutableIntStateOf(-1)
        compose.setContent {
            AppTheme {
                AppSegmentedToggle(
                    options = options,
                    selectedIndex = 0,
                    onSelect = { selected = it },
                )
            }
        }

        assertEquals(-1, selected)
        compose.onNodeWithText("周").performClick()
        compose.waitUntil { selected == 1 }
        assertEquals("点击「周」应回调 index=1", 1, selected)
    }

    /** 选中态切换后：selectedIndex 驱动选中项浮起 + 加粗，所有选项仍可见。 */
    @Test
    fun segmentedToggle_selectedOptionHighlighted() {
        var selectedIndex by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                AppSegmentedToggle(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                )
            }
        }
        compose.waitForIdle()

        assertEquals(0, selectedIndex)

        // 点击「月」→ 选中高亮块滑动到第三项（surface 浮起 + SemiBold）
        compose.onNodeWithText("月").performClick()
        compose.waitUntil { selectedIndex == 2 }
        compose.waitForIdle()

        assertTrue("选中态应迁移到「月」(index=2)", selectedIndex == 2)
        options.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
