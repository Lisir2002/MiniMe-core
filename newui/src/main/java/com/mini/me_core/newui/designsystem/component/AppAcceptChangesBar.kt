package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 批量接受 / 拒绝改动条（对话流结尾）。
 */
@Composable
fun AppAcceptChangesBar(
    modifier: Modifier = Modifier,
    changedCount: Int = 0,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = AppColor.StatusSuccess)) {
            Text("全部接受 ($changedCount)")
        }
        OutlinedButton(onClick = onReject) {
            Text("全部拒绝")
        }
        Spacer(Modifier.width(0))
    }
}
