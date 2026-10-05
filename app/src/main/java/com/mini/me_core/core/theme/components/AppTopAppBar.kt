package com.mini.me_core.core.theme.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一顶栏组件（深度重构版）。
 *
 * 特性：
 * - 毛玻璃半透明背景
 * - 滚动感知隐藏/显示（通过 visible 参数）
 * - 搜索展开动画（点击搜索图标，标题 Crossfade 为搜索框）
 * - 按钮分组（返回按钮一组、操作按钮一组，每组玻璃背景）
 * - 可见性平滑动画
 * - 触觉反馈
 *
 * **API 向后兼容**：原有参数签名不变，新增参数均有默认值。
 *
 * @param title 标题文字
 * @param onNavigateBack 返回回调；非空时显示导航图标按钮
 * @param navigationIcon 导航图标（默认 ArrowBack）
 * @param navigationContentDescription 导航图标内容描述
 * @param actions 右侧操作区
 * @param titleContent 可选自定义标题区；非空时替代 title 文字
 * @param visible 是否可见（用于滚动感知隐藏）
 * @param searchable 是否启用搜索展开
 * @param searchHint 搜索框占位文字
 * @param onSearchQueryChange 搜索内容变化回调
 */
@Composable
fun AppTopAppBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    titleContent: @Composable (() -> Unit)? = null,
    visible: Boolean = true,
    searchable: Boolean = false,
    searchHint: String = "搜索…",
    onSearchQueryChange: ((String) -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalAppTheme.current.colors
    val cornerRadius = LocalCornerRadius.current
    val haptic = LocalHapticFeedback.current
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 可见性动画
    val visibilityAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "topbar_visibility",
    )

    // 搜索激活时的标题缩放
    val titleScale by animateFloatAsState(
        targetValue = if (searchActive) 0.85f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "title_scale",
    )

    Surface(
        color = colors.surfacePage.copy(alpha = 0.94f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .graphicsLayerAlpha(visibilityAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回按钮组（玻璃背景）
            if (onNavigateBack != null && navigationIcon != null) {
                Box(
                    modifier = Modifier
                        .padding(start = PrimitiveSpacing.Sm)
                        .clip(RoundedCornerShape(cornerRadius.sm))
                        .background(colors.surfacePage.copy(alpha = 0.6f))
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                    onSearchQueryChange?.invoke("")
                                } else {
                                    onNavigateBack()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = searchActive,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_icon_crossfade",
                    ) { isSearch ->
                        Icon(
                            imageVector = if (isSearch) Icons.Rounded.Close else navigationIcon,
                            contentDescription = if (isSearch) "关闭搜索" else navigationContentDescription,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(PrimitiveSpacing.Md))
            }

            Spacer(Modifier.width(PrimitiveSpacing.Xs))

            // 标题区 / 搜索框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Crossfade(
                    targetState = searchActive && searchable,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "title_search_crossfade",
                ) { isSearchMode ->
                    if (isSearchMode) {
                        // 搜索输入框
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                onSearchQueryChange?.invoke(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(cornerRadius.sm))
                                .background(colors.surfacePage.copy(alpha = 0.5f))
                                .padding(horizontal = PrimitiveSpacing.Md),
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(colors.brandPrimary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(PrimitiveSpacing.Xs))
                                    Box(Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = searchHint,
                                                color = colors.textTertiary,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            },
                        )
                    } else {
                        // 标题
                        if (titleContent != null) {
                            titleContent()
                        } else {
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                modifier = Modifier.scale(titleScale),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(PrimitiveSpacing.Xs))

            // 操作按钮组（玻璃背景容器）
            if (searchable || actions != {}) {
                Row(
                    modifier = Modifier
                        .padding(end = PrimitiveSpacing.Sm)
                        .clip(RoundedCornerShape(cornerRadius.sm))
                        .background(colors.surfacePage.copy(alpha = 0.6f))
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 搜索按钮
                    if (searchable) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                searchActive = !searchActive
                                if (!searchActive) {
                                    searchQuery = ""
                                    onSearchQueryChange?.invoke("")
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Crossfade(
                                targetState = searchActive,
                                animationSpec = tween(durationMillis = 200),
                                label = "search_icon_crossfade",
                            ) { active ->
                                Icon(
                                    imageVector = if (active) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (active) "关闭搜索" else "搜索",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    // 自定义操作
                    actions()
                }
            } else {
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
            }
        }
    }
}

/**
 * 为 Modifier 添加 alpha 动画的辅助函数。
 * 避免直接使用 graphicsLayer 导致的重组范围扩大。
 */
private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier =
    this.then(Modifier.graphicsLayer { this.alpha = alpha })

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：带返回按钮和搜索的顶栏。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun AppTopAppBarPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppTopAppBar(
            title = "设置",
            onNavigateBack = {},
            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            searchable = true,
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "More",
                        tint = LocalAppTheme.current.colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
    }
}

/** Preview：无返回按钮的顶栏。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun AppTopAppBarNoBackPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        AppTopAppBar(title = "主页")
    }
}
