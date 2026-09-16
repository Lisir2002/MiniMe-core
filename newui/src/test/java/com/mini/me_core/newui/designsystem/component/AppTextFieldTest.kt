package com.mini.me_core.newui.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mini.me_core.newui.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppTextField（统一文本输入封装）Robolectric UI 测试。
 *
 * 被测签名（见 AppTextField.kt）：
 * ```
 * fun AppTextField(
 *     value: String,
 *     onValueChange: (String) -> Unit,
 *     label: String,
 *     modifier: Modifier = Modifier,
 *     placeholder: String? = null,
 *     singleLine: Boolean = true,
 *     isError: Boolean = false,
 *     supportingText: String? = null,
 * )
 * ```
 * 底层是 Material3 [androidx.compose.material3.OutlinedTextField]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTextFieldTest {

    @get:Rule
    val compose = createComposeRule()

    /** 外部传入的 value 应正确显示在输入框中。 */
    @Test
    fun textField_displaysValue() {
        compose.setContent {
            AppTheme {
                AppTextField(
                    value = "张三",
                    onValueChange = {},
                    label = "用户名",
                )
            }
        }

        compose.onNodeWithText("用户名").assertIsDisplayed()
        compose.onNodeWithText("张三").assertIsDisplayed()
    }

    /** 用户输入文本应触发 onValueChange，并把新值回写到 state。 */
    @Test
    fun textField_onValueChangeCalled() {
        var current by mutableStateOf("")
        compose.setContent {
            AppTheme {
                AppTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = "用户名",
                )
            }
        }

        // 点击获取焦点后输入新文本（performTextInput 通过 IME input connection setText）
        compose.onNodeWithText("用户名").performClick()
        compose.onNodeWithText("用户名").performTextInput("李四")
        compose.waitUntil { current == "李四" }
        assertEquals("输入后 state 应更新为输入文本", "李四", current)
    }

    /** isError = true 时应渲染 supportingText 错误提示。 */
    @Test
    fun textField_errorStateDisplaysError() {
        compose.setContent {
            AppTheme {
                AppTextField(
                    value = "abc",
                    onValueChange = {},
                    label = "邮箱",
                    isError = true,
                    supportingText = "邮箱格式不正确",
                )
            }
        }

        compose.onNodeWithText("邮箱格式不正确").assertIsDisplayed()
    }

    /** value 为空时应展示 placeholder 提示文本。 */
    @Test
    fun textField_placeholderDisplaysWhenEmpty() {
        compose.setContent {
            AppTheme {
                AppTextField(
                    value = "",
                    onValueChange = {},
                    label = "搜索",
                    placeholder = "输入关键词…",
                )
            }
        }

        compose.onNodeWithText("输入关键词…").assertIsDisplayed()
    }
}
