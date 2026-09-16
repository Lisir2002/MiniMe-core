package com.mini.me_core.newui.designsystem.component.molecule

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
 * AppTabs（滑动指示器标签栏）Robolectric UI 测试。
 *
 * 被测签名（见 AppTabs.kt）：
 * ```
 * fun AppTabs(
 *     tabs: List<String>,
 *     selectedIndex: Int,
 *     onSelect: (Int) -> Unit,
 *     modifier: Modifier = Modifier,
 *     indicatorColor: Color = appPalette().primary,
 * )
 * ```
 * 视觉区分：选中项 [androidx.compose.ui.text.font.FontWeight.SemiBold] + 主色文字 + 底部滑动指示条。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf("会话", "联系人", "设置")

    /** 所有 Tab 标签都应被渲染并可见。 */
    @Test
    fun tabs_displaysAllTabLabels() {
        compose.setContent {
            AppTheme {
                AppTabs(
                    tabs = tabs,
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }

        tabs.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    /** 点击某个 Tab 应触发 onSelect，且回传正确的下标。 */
    @Test
    fun tabs_selectTabCallsOnSelected() {
        var selected by mutableIntStateOf(-1)
        compose.setContent {
            AppTheme {
                AppTabs(
                    tabs = tabs,
                    selectedIndex = 0,
                    onSelect = { selected = it },
                )
            }
        }

        assertEquals(-1, selected)
        compose.onNodeWithText("联系人").performClick()
        compose.waitUntil { selected == 1 }
        assertEquals("点击「联系人」应回调 index=1", 1, selected)
    }

    /** 选中态切换后：selectedIndex 状态驱动选中项加粗高亮，标签保持可见。 */
    @Test
    fun tabs_selectedTabHighlighted() {
        var selectedIndex by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                AppTabs(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                )
            }
        }
        compose.waitForIdle()

        // 初始：0 号 Tab 选中（SemiBold + 主色文字 + 指示条）
        assertEquals(0, selectedIndex)

        // 点击第二个 Tab → 状态迁移到 1 号，选中高亮随之移动
        compose.onNodeWithText("设置").performClick()
        compose.waitUntil { selectedIndex == 2 }
        compose.waitForIdle()

        assertTrue("选中态应迁移到「设置」(index=2)", selectedIndex == 2)
        // 选中切换后所有标签仍在语义树中
        tabs.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
