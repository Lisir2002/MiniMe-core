package com.mini.me_core.newui.designsystem.component.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.component.molecule.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.molecule.AppIconButton
import com.mini.me_core.newui.designsystem.component.molecule.AppSkeletonList
import com.mini.me_core.newui.designsystem.slot.AppShell
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 列表页四态状态密封接口。
 *
 * 用于 [ListPageTemplate] 的状态机，将加载 / 空 / 错误 / 内容四种 UI 状态归一为单一类型，
 * 调用方只需把当前业务状态映射为对应子类，模板负责渲染匹配的界面。
 *
 * @param T 列表项数据类型。
 */
sealed interface ListPageState<out T> {

    /** 加载中态：展示骨架屏或进度指示器。 */
    data object Loading : ListPageState<Nothing>

    /** 空态：列表成功加载但无数据。 */
    data object Empty : ListPageState<Nothing>

    /**
     * 错误态：加载失败。
     *
     * @property message 错误提示文案，用于界面展示。
     */
    data class Error(val message: String) : ListPageState<Nothing>

    /**
     * 内容态：列表数据就绪。
     *
     * @property data 列表数据集合，由 [itemContent] 逐项渲染。
     */
    data class Content<T>(val data: List<T>) : ListPageState<T>
}

/**
 * 列表页面模板骨架（template 层 · P3-15）。
 *
 * 组合 [AppShell] + [LazyColumn] + 四态切换（加载 / 空 / 错误 / 内容），
 * 为业务侧提供统一的列表页布局骨架，避免每页重复编写状态切换逻辑。
 *
 * ## 四态行为
 * - **加载态** [ListPageState.Loading]：居中展示 [CircularProgressIndicator]，
 *   同时在下方渲染 [AppSkeletonList] 骨架行，给出"正在加载列表"的预期。
 * - **空态** [ListPageState.Empty]：居中展示收件箱图标 + [emptyMessage] 文案。
 * - **错误态** [ListPageState.Error]：居中展示错误文案 + 重试按钮（[onRetry] 非空时渲染）。
 * - **内容态** [ListPageState.Content]：用 [LazyColumn] 渲染 [data]，逐项调用 [itemContent]。
 *
 * ## 用法示例
 * ```
 * ListPageTemplate(
 *     title = "消息列表",
 *     state = messageState, // ListPageState<Message>
 *     onNavigateBack = { navController.popBackStack() },
 *     itemContent = { MessageRow(it) },
 *     onRetry = { viewModel.loadMessages() },
 * )
 * ```
 *
 * @param T 列表项数据类型。
 * @param title 顶栏标题。
 * @param state 当前页面状态（四态密封接口）。
 * @param modifier 外层修饰符。
 * @param onNavigateBack 导航回调；非空时顶栏左侧显示返回箭头。
 * @param topBarActions 顶栏右侧操作槽位（[RowScope] 内可放 IconButton 等）。
 * @param itemContent 列表项渲染回调，由调用方定义每项 UI。
 * @param onRetry 重试回调；非空且状态为错误态时展示重试按钮。
 * @param emptyMessage 空态文案，默认"暂无数据"。
 * @param errorMessage 错误态兜底文案（[ListPageState.Error.message] 为空时使用）。
 */
@Composable
fun <T> ListPageTemplate(
    title: String,
    state: ListPageState<T>,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit,
    onRetry: (() -> Unit)? = null,
    emptyMessage: String = "暂无数据",
    errorMessage: String = "加载失败",
) {
    AppShell(
        title = title,
        onNavigateBack = onNavigateBack,
        topBarActions = topBarActions,
        modifier = modifier,
    ) { innerPadding ->
        when (state) {
            is ListPageState.Loading -> LoadingContent(modifier = Modifier.padding(innerPadding))
            is ListPageState.Empty -> EmptyContent(
                message = emptyMessage,
                modifier = Modifier.padding(innerPadding),
            )
            is ListPageState.Error -> ErrorContent(
                message = state.message.ifBlank { errorMessage },
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding),
            )
            is ListPageState.Content -> ContentList(
                data = state.data,
                itemContent = itemContent,
                contentPadding = innerPadding,
            )
        }
    }
}

/** 加载态：居中进度指示器 + 骨架列表行。 */
@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AppSpacing.Xl))
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(AppSpacing.Xl))
        AppSkeletonList(rows = 5)
    }
}

/** 空态：收件箱图标 + 文案居中。 */
@Composable
private fun EmptyContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(AppSpacing.Md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 错误态：错误文案 + 可选重试按钮。 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppSpacing.Xl),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(AppSpacing.Md))
            AppIconButton(
                text = "重试",
                onClick = onRetry,
                variant = AppButtonVariant.Outlined,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** 内容态：LazyColumn 渲染数据列表。 */
@Composable
private fun <T> ContentList(
    data: List<T>,
    itemContent: @Composable (T) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.Lg,
            end = AppSpacing.Lg,
            top = AppSpacing.Md,
            bottom = AppSpacing.Lg,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
    ) {
        items(data) { item ->
            itemContent(item)
        }
    }
}
