package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/**
 * 测试结果卡（对话流）：一轮改完后展示通过 / 失败数，失败用例可展开。
 */
@Composable
fun AppTestResultCard(
    passed: Int,
    failed: List<String>,
    modifier: Modifier = Modifier,
    title: String = "测试",
) {
    var expanded by remember { mutableStateOf(false) }
    val ok = failed.isEmpty()
    val accent = if (ok) AppColor.StatusSuccess else AppColor.StatusDanger
    val shape = RoundedCornerShape(AppRadius.Md)
    Column(
        modifier = modifier
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { if (failed.isNotEmpty()) expanded = !expanded }
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(AppSizing.IconM),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "✓ $passed",
                style = MaterialTheme.typography.labelMedium,
                color = AppColor.StatusSuccess,
            )
            if (failed.isNotEmpty()) {
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    "✗ ${failed.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColor.StatusDanger,
                )
            }
        }
        if (failed.isNotEmpty() && expanded) {
            Column(
                Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                failed.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = appPalette().labelSecondary,
                    )
                }
            }
        }
    }
}
