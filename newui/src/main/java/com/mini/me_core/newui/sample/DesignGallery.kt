package com.mini.me_core.newui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.layout.pageContentPadding
import com.mini.me_core.newui.designsystem.layout.pageMaxWidth
import com.mini.me_core.newui.designsystem.slot.AppShell
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 样板页（§7 S0）：在一个页面内陈列令牌 / 原子组件 / 布局 / 三态 / 槽位。
 * 展示 Section 已拆分到同包下：
 *  - GalleryComponents.kt：令牌 / 原子 / 按钮 / 分组行 / 三态（含 Section 等辅助组件）
 *  - FormSamples.kt：输入 / 筛选 / 输入框 / 表单 & 检索 / 表单增强 / 分级导航 / 标签分页
 *  - FeedbackSamples.kt：列表菜单 / 弹窗 / 反馈 / 高动效 / 可视化 / 时序 / 文件
 *  - ChatSamples.kt：AI 对话流（状态机 + 流式演示）
 *  - SampleData.kt：对话流演示数据模型
 */
@Composable
fun DesignGallery(onNavigateBack: (() -> Unit)? = null) {
    // 对话流完整演示页：样板页内部本地切换，不进 app 主导航；返回回到样板页。
    var showChatFlow by remember { mutableStateOf(false) }
    AppTheme {
        if (showChatFlow) {
            ChatFlowGallery(onNavigateBack = { showChatFlow = false })
        } else {
            AppShell(
                title = "Design Gallery",
                onNavigateBack = onNavigateBack,
            ) {
                GalleryBody(onOpenChatFlow = { showChatFlow = true })
            }
        }
    }
}

@Composable
private fun GalleryBody(onOpenChatFlow: () -> Unit = {}) {
    val scroll = rememberScrollState()
    // 滑扫协调：对话流消息行与滑扫示例同批只开一项
    var swipeExpanded by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .verticalScroll(scroll)
            .pageMaxWidth()
            .pageContentPadding()
            .padding(top = AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
    ) {
        PrimitiveGallerySection()
        FormGallerySection()
        FeedbackGallerySection(
            swipeExpanded = swipeExpanded,
            onSwipeExpanded = { swipeExpanded = it },
        )
        ChatGallerySection(
            onOpenChatFlow = onOpenChatFlow,
            swipeExpanded = swipeExpanded,
            onSwipeExpanded = { swipeExpanded = it },
        )
    }
}
