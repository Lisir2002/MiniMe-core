package com.mini.me_core.newui.designsystem.component.atom

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 基础卡片（§3.12 AppCard）：surface 底 + z1 阴影 + md 圆角。
 *
 * 统一底色：有 / 无 onClick 均使用 [MaterialTheme.colorScheme.surface]，
 * 不再以 surfaceVariant 区分可点击性——可点击性由 onClick 自带的涟漪（ripple）表达，
 * 避免业务方靠底色误判交互态。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    val elevation = AppElevation.Z1
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            onClick = onClick,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            content = content,
        )
    }
}

@Preview(showBackground = true, name = "AppCard - Default")
@Composable
private fun PreviewAppCard() {
    AppTheme {
        AppCard(modifier = Modifier.padding(AppSpacing.Lg)) {
            Text(
                text = "这是一张基础卡片\nsurface 底 + z1 阴影 + md 圆角",
                modifier = Modifier.padding(AppSpacing.Lg),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
