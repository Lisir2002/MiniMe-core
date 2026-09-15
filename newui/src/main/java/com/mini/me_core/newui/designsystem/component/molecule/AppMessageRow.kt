package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 消息行（分子组 · AppMessageRow）：头像 + 角色头部（姓名/时间）+ [AppChatBubble] + 操作区。
 *
 * - 双侧头像：AI 左、用户右，首字母品牌渐变圆（[AppAvatar]）。
 * - 分组合并：同一角色连续消息传 [grouped] = true 时隐藏头像/姓名，仅保留气泡，紧凑成组。
 * - 长按（或点按）气泡展开操作区：复制 / 重试（仅失败态）/ 删除，操作后自动收起。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMessageRow(
    text: String,
    modifier: Modifier = Modifier,
    state: AppChatMessageState = AppChatMessageState.Complete,
    isUser: Boolean = false,
    avatarLabel: String? = null,
    showAvatar: Boolean = true,
    name: String? = null,
    timestamp: String? = null,
    grouped: Boolean = false,
    accent: Color = appPalette().primary,
    onRetry: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    swipeEnabled: Boolean = false,
    swipeEdge: AppSwipeEdge = AppSwipeEdge.End,
    swipeIndex: Int? = null,
    swipeExpandedIndex: Int? = null,
    onSwipeExpanded: ((Int?) -> Unit)? = null,
) {
    val label = name ?: if (isUser) "你" else "AI"
    val avatarText = (avatarLabel ?: label).take(1)
    var actionsVisible by remember { mutableStateOf(false) }
    val hasActions = onCopy != null || onRetry != null || onDelete != null

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!isUser) {
                AvatarSlot(avatarText, showAvatar = showAvatar && !grouped)
                Spacer(Modifier.width(AppSpacing.Sm))
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                if (!grouped) {
                    HeaderRow(label = label, timestamp = timestamp, isUser = isUser)
                    Spacer(Modifier.height(2.dp))
                }
                Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                    AppChatBubble(
                        text = text,
                        state = state,
                        isUser = isUser,
                        accent = accent,
                        onRetry = onRetry,
                        modifier = if (hasActions) {
                            Modifier.combinedClickable(
                                onClick = { actionsVisible = !actionsVisible },
                                onLongClick = { actionsVisible = true },
                            )
                        } else {
                            Modifier
                        },
                    )
                    AnimatedVisibility(
                        visible = actionsVisible,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        MessageActionsBar(
                            state = state,
                            onCopy = onCopy,
                            onRetry = onRetry,
                            onDelete = onDelete,
                            onDismiss = { actionsVisible = false },
                        )
                    }
                }
            }
            if (isUser) {
                Spacer(Modifier.width(AppSpacing.Sm))
                AvatarSlot(avatarText, showAvatar = showAvatar && !grouped)
            }
        }
    }

    if (swipeEnabled) {
        AppSwipeAction(
            modifier = modifier,
            edge = swipeEdge,
            index = swipeIndex,
            expandedIndex = swipeExpandedIndex,
            onExpanded = onSwipeExpanded,
            actions = {
                if (onCopy != null) {
                    AppSwipeButton(
                        icon = Icons.Rounded.ContentCopy,
                        label = "复制",
                        background = appPalette().primary,
                        onClick = onCopy,
                    )
                }
                if (state == AppChatMessageState.Error && onRetry != null) {
                    AppSwipeButton(
                        icon = Icons.Rounded.Refresh,
                        label = "重试",
                        background = AppColor.StatusInfo,
                        onClick = onRetry,
                    )
                }
                if (onDelete != null) {
                    AppSwipeButton(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "删除",
                        background = AppColor.StatusDanger,
                        onClick = onDelete,
                    )
                }
            },
        ) {
            rowContent()
        }
    } else {
        Box(modifier = modifier) {
            rowContent()
        }
    }
}

/** 消息头部：角色名（SemiBold 次级文字）+ 时间（三级文字）。 */
@Composable
private fun HeaderRow(label: String, timestamp: String?, isUser: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = appPalette().labelSecondary,
        )
        if (timestamp != null) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelTertiary,
                modifier = Modifier.padding(start = AppSpacing.Xs),
            )
        }
    }
}

/** 头像槽位：显示首字母头像；分组合并时以等宽占位保持对齐。 */
@Composable
private fun AvatarSlot(avatarText: String, showAvatar: Boolean) {
    if (showAvatar) {
        AppAvatar(text = avatarText, size = 28.dp)
    } else {
        Spacer(Modifier.size(28.dp))
    }
}

/** 长按操作区：深色胶囊内复制 / 重试 / 删除。 */
@Composable
private fun MessageActionsBar(
    state: AppChatMessageState,
    onCopy: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = AppSpacing.Xs)
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(AppColor.OnDarkSurfaceRaised)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionChip(Icons.Rounded.ContentCopy, "复制", AppColor.OnDarkInk) {
            onCopy?.invoke()
            onDismiss()
        }
        if (state == AppChatMessageState.Error && onRetry != null) {
            ActionChip(Icons.Rounded.Refresh, "重试", AppColor.OnDarkInk) {
                onRetry()
                onDismiss()
            }
        }
        if (onDelete != null) {
            ActionChip(Icons.Rounded.DeleteOutline, "删除", AppColor.OnDarkInk) {
                onDelete()
                onDismiss()
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
