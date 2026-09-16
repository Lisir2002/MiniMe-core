package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.launch

/**
 * 消息滚动容器（分子组 · AppMessageScroller）：反向 [LazyColumn]（index 0 = 最新消息，视觉底部），
 * 面向 AI 对话流优化：
 *
 * - 自动跟随：[newMessageKey] 变化且用户停留在底部时平滑滚到最新消息；用户上滑浏览历史时暂停跟随。
 * - 跳到底部：离开底部后右下角浮现品牌圆钮，点击回到底部。
 * - 加载历史：传入 [onLoadHistory] 后在视觉顶部渲染「加载更早消息」入口（reverseLayout 下追加到列表末尾）。
 *
 * 调用方按时间正序在 [content] 中书写消息即可，渲染顺序由 reverseLayout 保证。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppMessageScroller(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = AppSpacing.Md),
    newMessageKey: Any? = null,
    onLoadHistory: (() -> Unit)? = null,
    loadingHistory: Boolean = false,
    showScrollToBottomButton: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // reverseLayout：滚到最新（视觉底部）时 firstVisibleItemIndex == 0 且无偏移
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(newMessageKey) {
        if (newMessageKey != null && isAtBottom) {
            listState.animateScrollToItem(0)
        }
    }
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            content()
            if (onLoadHistory != null) {
                item(key = "loadHistory") {
                    LoadHistoryRow(loading = loadingHistory, onLoad = onLoadHistory)
                }
            }
        }
        if (showScrollToBottomButton) {
            AnimatedVisibility(
                visible = !isAtBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AppSpacing.Lg, bottom = AppSpacing.Lg),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appPalette().primary)
                        .clickable {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "回到底部",
                        tint = appPalette().onPrimary,
                        modifier = Modifier.size(AppSizing.IconM),
                    )
                }
            }
        }
    }
}

/** 视觉顶部的「加载更早消息」入口（reverseLayout 下位于列表末尾）。 */
@Composable
private fun LoadHistoryRow(loading: Boolean, onLoad: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "正在加载更早消息…" else "加载更早消息",
            style = MaterialTheme.typography.labelMedium,
            color = appPalette().primary,
            modifier = Modifier
                .clip(RoundedCornerShape(AppRadius.Pill))
                .clickable(enabled = !loading, onClick = onLoad)
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        )
    }
}
