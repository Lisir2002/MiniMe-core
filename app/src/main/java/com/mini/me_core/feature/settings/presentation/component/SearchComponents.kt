package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search

/**
 * 设置页搜索顶栏：替代普通标题栏。
 *
 * 视觉：
 * - 背景与正常顶栏一致（surfaceCard），高度一致（topAppBar.height）
 * - 搜索框聚焦时边框变品牌色、光标品牌色
 * - 输入框内一键清空按钮（仅在有输入时显示）
 * - IME Search action
 *
 * 交互：
 * - [onSearch] 在 IME Search 键或点击结果时回调，用于记录历史
 */
@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
) {
    val tokens = LocalComponentTokens.current
    val appColors = LocalAppTheme.current.colors
    var focused by remember { mutableStateOf(false) }
    val animScale = LocalAnimationScale.current
    val animDuration = (200L * animScale).toInt().coerceAtLeast(0)

    Surface(
        color = tokens.topAppBar.backgroundColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(tokens.topAppBar.height),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(tokens.topAppBar.iconButtonSize),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_search_close_content_desc),
                    tint = tokens.text.titleSmallColor,
                    modifier = Modifier.size(tokens.topAppBar.iconSize),
                )
            }

            // 搜索输入框（自定义 BasicTextField，完全走令牌）
            val inputShape = RoundedCornerShape(LocalCornerRadius.current.map(tokens.input.cornerRadius))
            val borderColor = if (focused) {
                appColors.borderFocus
            } else {
                appColors.borderDefault
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(tokens.topAppBar.iconButtonSize)
                    .clip(inputShape)
                    .background(tokens.input.backgroundColor)
                    .border(
                        width = if (focused) PrimitiveSpacing.Hairline + PrimitiveSpacing.Xs else PrimitiveSpacing.Hairline,
                        color = borderColor,
                        shape = inputShape,
                    )
                    .padding(horizontal = PrimitiveSpacing.Md),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = tokens.text.labelSmallColor,
                        modifier = Modifier.size(tokens.chip.iconSize),
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.SmPlus))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = tokens.input.fontSize,
                                    color = appColors.textTertiary,
                                ),
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = tokens.input.fontSize,
                                color = tokens.text.titleMediumColor,
                            ),
                            cursorBrush = SolidColor(tokens.input.cursorColor),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearch(query) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focused = it.isFocused },
                        )
                    }
                    // 清空按钮（仅在有输入时显示，带淡入淡出动画）
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(animDuration)),
                        exit = fadeOut(animationSpec = tween(animDuration)),
                    ) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(tokens.topAppBar.iconButtonSize),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.settings_search_clear_content_desc),
                                tint = tokens.text.labelSmallColor,
                                modifier = Modifier.size(tokens.chip.iconSize),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
        }
    }
}

/**
 * 搜索结果计数行：「找到 N 项」。
 * 走令牌：颜色 textSecondary，字号 labelSmall。
 */
@Composable
internal fun SearchResultCountRow(count: Int) {
    val tokens = LocalComponentTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PrimitiveSpacing.Lg,
                end = PrimitiveSpacing.Lg,
                top = PrimitiveSpacing.SmPlus,
                bottom = PrimitiveSpacing.Xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_search_result_count, count),
            style = TextStyle(
                fontSize = tokens.text.labelSmallFontSize,
                fontWeight = tokens.text.labelLargeFontWeight,
                color = tokens.text.labelSmallColor,
            ),
        )
    }
}

/**
 * 搜索历史区：空输入时展示。
 * 标题「最近搜索」+ 清空按钮 + 横向 Chip 列表。
 */
@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val tokens = LocalComponentTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_search_history_title),
                style = TextStyle(
                    fontSize = tokens.text.titleSmallFontSize,
                    fontWeight = tokens.text.titleSmallFontWeight,
                    color = tokens.text.titleSmallColor,
                ),
            )
            if (history.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_search_history_clear),
                    style = TextStyle(
                        fontSize = tokens.text.labelLargeFontSize,
                        fontWeight = tokens.text.labelLargeFontWeight,
                        color = tokens.text.labelLargeColor,
                    ),
                    modifier = Modifier.clickable { onClearHistory() },
                )
            }
        }
        Spacer(Modifier.height(PrimitiveSpacing.MdPlus))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_search_history_empty),
                style = TextStyle(
                    fontSize = tokens.text.bodySmallFontSize,
                    color = tokens.text.bodySmallColor,
                ),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
            ) {
                items(history) { item ->
                    HistoryChip(
                        label = item,
                        onClick = { onHistoryClick(item) },
                    )
                }
            }
        }
    }
}

/** 单个历史搜索词 Chip。 */
@Composable
private fun HistoryChip(
    label: String,
    onClick: () -> Unit,
) {
    val tokens = LocalComponentTokens.current
    val chipShape = RoundedCornerShape(LocalCornerRadius.current.map(tokens.chip.cornerRadius))
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(tokens.chip.defaultContainerColor)
            .clickable { onClick() }
            .padding(
                horizontal = tokens.chip.paddingHorizontal,
                vertical = tokens.chip.paddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = tokens.chip.defaultContentColor,
            modifier = Modifier.size(tokens.chip.iconSize),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Xs))
        Text(
            text = label,
            style = TextStyle(
                fontSize = tokens.chip.fontSize,
                fontWeight = tokens.chip.fontWeight,
                color = tokens.chip.defaultContentColor,
            ),
        )
    }
}
