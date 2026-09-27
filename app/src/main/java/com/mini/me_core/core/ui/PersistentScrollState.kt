package com.mini.me_core.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 带持久化的 LazyListState：页面离开时自动保存滚动位置，重新进入时恢复。
 * @param key 页面唯一标识（建议用路由名或页面类名）
 */
@Composable
fun rememberPersistentLazyListState(key: String): LazyListState {
    val saved = ScrollPositionManager.get(key)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = saved?.first ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.second ?: 0
    )
    DisposableEffect(key) {
        onDispose {
            ScrollPositionManager.save(
                key,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset
            )
        }
    }
    return state
}

/**
 * 带持久化的 ScrollState（用于 Column(verticalScroll)）。
 */
@Composable
fun rememberPersistentScrollState(key: String): ScrollState {
    val saved = ScrollPositionManager.get(key)
    val state = rememberScrollState(initial = saved?.second ?: 0)
    DisposableEffect(key) {
        onDispose {
            ScrollPositionManager.save(key, 0, state.value)
        }
    }
    return state
}
