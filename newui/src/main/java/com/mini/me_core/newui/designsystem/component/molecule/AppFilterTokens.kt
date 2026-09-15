package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 单个激活筛选 token（分子组 · AppFilterToken）：
 * 类似 iOS 26 `.searchable(tokens:)` 的样式——已生效的筛选条件以「分组名 · 值」的形式内联展示，
 * 高亮加粗 + 品牌小圆点，可一键反向移除（✕）。
 */
@Composable
fun AppFilterToken(
    group: String,
    value: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppRadius.Pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(appPalette().primary.copy(alpha = 0.12f))
            .padding(start = AppSpacing.Sm, end = AppSpacing.Xs, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(AppRadius.Pill))
                .background(appPalette().primary),
        )
        Spacer(Modifier.size(AppSpacing.Xs))
        Text(
            text = "$group · $value",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = appPalette().ink,
            maxLines = 1,
        )
        Spacer(Modifier.size(AppSpacing.Sm))
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "移除",
            tint = appPalette().primary,
            modifier = Modifier
                .size(AppSizing.IconXs)
                .clip(RoundedCornerShape(AppRadius.Pill))
                .clickable(onClick = onRemove)
                .padding(AppSpacing.Tiny),
        )
    }
}

/** 激活筛选 token 行（分子组 · AppFilterTokens）：
 *  横向可滚动展示 [active] 组 token，右侧跟随「清除全部」入口；空则自动隐藏。
 *  借鉴 iOS 搜索 token 交互，让用户一眼看懂当前生效的筛选条件并可逐个/批量撤销。 */
@Composable
fun AppFilterTokens(
    active: List<Pair<String, String>>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onClearAll: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = active.isNotEmpty(),
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            active.forEachIndexed { index, pair ->
                AppFilterToken(
                    group = pair.first,
                    value = pair.second,
                    onRemove = { onRemove(index) },
                )
            }
            if (onClearAll != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = AppSpacing.Sm, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "清除全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColor.StatusDanger,
                    )
                }
            }
        }
    }
}