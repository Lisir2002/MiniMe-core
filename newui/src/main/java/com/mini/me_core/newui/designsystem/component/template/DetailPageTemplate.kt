package com.mini.me_core.newui.designsystem.component.template

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.slot.AppShell
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 详情页面模板骨架（template 层 · P3-15）。
 *
 * 组合 [AppShell] + 可滚动内容区，为业务侧提供统一的详情页布局骨架。
 * 内容区用 [Column] + [Modifier.verticalScroll] 包裹，调用方只需传入 [content] 即可。
 *
 * ## 槽位
 * - **顶栏**：标题 + 可选返回键 + 右侧操作槽位 [topBarActions]。
 * - **内容区**：纵向滚动 [Column]，由 [content] 定义具体详情 UI。
 * - **底栏**：可选固定底栏 [bottomBar]（如操作按钮组），不随内容滚动。
 *
 * ## 用法示例
 * ```
 * DetailPageTemplate(
 *     title = "项目详情",
 *     onNavigateBack = { navController.popBackStack() },
 *     bottomBar = { AppButton("保存", onClick = { ... }) },
 * ) {
 *     ProjectHeader(project)
 *     SectionTitle("描述")
 *     ProjectDescription(project.description)
 * }
 * ```
 *
 * @param title 顶栏标题。
 * @param modifier 外层修饰符。
 * @param onNavigateBack 导航回调；非空时顶栏左侧显示返回箭头。
 * @param topBarActions 顶栏右侧操作槽位（[RowScope] 内可放 IconButton 等）。
 * @param bottomBar 可选固定底栏；不传则不渲染。
 * @param content 详情内容区，自动包裹纵向滚动。
 */
@Composable
fun DetailPageTemplate(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppShell(
        title = title,
        onNavigateBack = onNavigateBack,
        topBarActions = topBarActions,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(ScrollState(0)),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AppSpacing.Lg,
                    vertical = AppSpacing.Md,
                ),
            ) {
                content()
            }
        }
    }
}
