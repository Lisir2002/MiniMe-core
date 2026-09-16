package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * Git 状态 chip（分子组 · AppGitStatusChip）：一行小字提示当前工作分支与脏文件数，
 * 让用户随时知道 Agent 在哪个工作树上操作。放在消息 meta / 卡片头部旁。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppGitStatusChip(
    branch: String,
    modifier: Modifier = Modifier,
    dirtyCount: Int = 0,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.Pill))
            .background(appPalette().surface)
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.CallMerge,
            contentDescription = null,
            tint = appPalette().labelSecondary,
            modifier = Modifier.size(AppSizing.IconXs),
        )
        Spacer(Modifier.width(AppSpacing.Xs))
        Text(
            text = branch,
            style = MaterialTheme.typography.labelSmall,
            color = appPalette().labelSecondary,
        )
        if (dirtyCount > 0) {
            Spacer(Modifier.width(AppSpacing.Xs))
            Text(
                text = "$dirtyCount 改",
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelTertiary,
            )
        }
    }
}
