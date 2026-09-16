package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mini.me_core.newui.designsystem.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppDialog（统一弹窗封装）Robolectric UI 测试。
 *
 * 被测签名（见 AppDialog.kt）：
 * ```
 * fun AppDialog(
 *     title: String,
 *     onDismiss: () -> Unit,
 *     confirmText: String,
 *     onConfirm: () -> Unit,
 *     modifier: Modifier = Modifier,
 *     text: String? = null,
 *     dismissText: String? = null,
 *     confirmButtonColor: Color = Color.Unspecified,
 * )
 * ```
 * 底层是 Material3 [androidx.compose.material3.AlertDialog]：onDismissRequest 绑定到 onDismiss。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDialogTest {

    @get:Rule
    val compose = createComposeRule()

    /** 验证标题与正文文本均被渲染到语义树。 */
    @Test
    fun dialog_displaysTitleAndText() {
        compose.setContent {
            AppTheme {
                AppDialog(
                    title = "删除确认",
                    onDismiss = {},
                    confirmText = "确认",
                    onConfirm = {},
                    text = "确定要删除该会话吗？",
                )
            }
        }

        compose.onNodeWithText("删除确认").assertIsDisplayed()
        compose.onNodeWithText("确定要删除该会话吗？").assertIsDisplayed()
    }

    /** 点击确认按钮应触发 onConfirm 回调。 */
    @Test
    fun dialog_confirmButtonCallsOnConfirm() {
        var confirmed = false
        compose.setContent {
            AppTheme {
                AppDialog(
                    title = "删除确认",
                    onDismiss = {},
                    confirmText = "确认",
                    onConfirm = { confirmed = true },
                    text = "确定要删除该会话吗？",
                )
            }
        }

        assertFalse(confirmed)
        compose.onNodeWithText("确认").performClick()
        assertTrue("点击确认按钮应触发 onConfirm", confirmed)
    }

    /** 点击取消按钮应触发 onDismiss 回调。 */
    @Test
    fun dialog_dismissButtonCallsOnDismiss() {
        var dismissed = false
        compose.setContent {
            AppTheme {
                AppDialog(
                    title = "删除确认",
                    onDismiss = { dismissed = true },
                    confirmText = "确认",
                    onConfirm = {},
                    text = "确定要删除该会话吗？",
                    dismissText = "取消",
                )
            }
        }

        assertFalse(dismissed)
        compose.onNodeWithText("取消").performClick()
        assertTrue("点击取消按钮应触发 onDismiss", dismissed)
    }

    /** 按系统返回键应触发 onDismiss（AlertDialog.onDismissRequest 行为）。 */
    @Test
    fun dialog_dismissOnBackPress() {
        var dismissed = false
        compose.setContent {
            AppTheme {
                AppDialog(
                    title = "删除确认",
                    onDismiss = { dismissed = true },
                    confirmText = "确认",
                    onConfirm = {},
                )
            }
        }
        compose.waitForIdle()

        assertFalse(dismissed)
        // Activity 返回键路由到弹窗 window，AlertDialog 会回调 onDismissRequest = onDismiss
        compose.activity.onBackPressed()
        compose.waitForIdle()
        assertTrue("按返回键应触发 onDismiss", dismissed)
    }
}
