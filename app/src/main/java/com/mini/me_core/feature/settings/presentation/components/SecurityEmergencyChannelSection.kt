package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppDialogType
import com.mini.me_core.core.theme.components.AppTextField
import com.mini.me_core.feature.settings.domain.security.SecurityAuditEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 安全紧急通道：验证 SSH 密码通过后，允许重置 MasterKey。
 *
 * 流程：点击按钮 → 内联展开表单（host/port/username/password）→ 提交 →
 * [AppDialog] 危险确认（红色，明确「解锁后所有凭据需重新录入，不可撤销」）→ 执行重置。
 *
 * @param history 最近紧急解锁记录（时间 + 验证主机）。
 * @param isLocked 是否因连续失败被临时锁定。
 * @param lockoutRemainingMs 锁定剩余毫秒数（用于文案）。
 */
@Composable
fun SecurityEmergencyChannelSection(
    onReset: (host: String, port: Int, username: String, password: String) -> Unit,
    isResetting: Boolean = false,
    errorMessage: String? = null,
    history: List<SecurityAuditEntry> = emptyList(),
    isLocked: Boolean = false,
    lockoutRemainingMs: Long = 0L,
) {
    var showForm by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.ui_________e229ee7f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(Spacing.xs))
            // 修复：原先拼接 ui___6435a24f + ui________11361eb5 两个完全相同的字符串，改为只用一个。
            Text(
                text = stringResource(R.string.ui___6435a24f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))

            if (isLocked) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.security_emergency_locked, lockoutRemainingMs / 1000 / 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            AppButton(
                text = if (isResetting) stringResource(R.string.ui_____9c56ac70)
                else stringResource(R.string.security_emergency_open_form),
                onClick = { showForm = true },
                buttonColor = AppButtonColor.Error,
                enabled = !isResetting && !isLocked,
            )

            // 内联表单
            if (showForm) {
                Spacer(Modifier.height(Spacing.sm))
                AppTextField(
                    value = host,
                    onValueChange = { host = it; localError = null },
                    label = { Text(stringResource(R.string.ui____65227369)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.xs))
                Row {
                    AppTextField(
                        value = portText,
                        onValueChange = { portText = it; localError = null },
                        label = { Text(stringResource(R.string.ui____c76cfefe)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    AppTextField(
                        value = username,
                        onValueChange = { username = it; localError = null },
                        label = { Text(stringResource(R.string.ui_____819767ad)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                AppTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = { Text(stringResource(R.string.ui____a8105204)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (localError != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = localError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                val fillAllText = stringResource(R.string.security_emergency_fill_all)
                val badPortText = stringResource(R.string.security_emergency_bad_port)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppButton(
                        text = stringResource(R.string.security_emergency_submit),
                        onClick = {
                            val port = portText.toIntOrNull()
                            if (host.isBlank() || username.isBlank() || password.isBlank()) {
                                localError = fillAllText
                            } else if (port == null || port < 1 || port > 65535) {
                                localError = badPortText
                            } else {
                                localError = null
                                showConfirm = true
                            }
                        },
                        buttonColor = AppButtonColor.Error,
                        size = com.mini.me_core.core.theme.components.AppButtonSize.Small,
                    )
                    AppButton(
                        text = stringResource(R.string.ui____625fb26b_3),
                        onClick = { showForm = false },
                        variant = com.mini.me_core.core.theme.components.AppButtonVariant.Text,
                        size = com.mini.me_core.core.theme.components.AppButtonSize.Small,
                    )
                }
            }

            // 解锁历史（最近 5 条）
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = stringResource(R.string.security_emergency_history_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(Spacing.xs))
                history.take(5).forEach { entry ->
                    val time = remember(entry.atMs) {
                        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.atMs))
                    }
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = entry.detail ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 危险确认弹窗（红色，AppDialog）
    if (showConfirm) {
        AppDialog(
            title = stringResource(R.string.security_emergency_confirm_title),
            message = stringResource(R.string.security_emergency_confirm_message),
            confirmText = stringResource(R.string.security_emergency_confirm_action),
            cancelText = stringResource(R.string.ui____625fb26b_3),
            type = AppDialogType.Destructive,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                showForm = false
                val port = portText.toIntOrNull() ?: 22
                onReset(host.trim(), port, username.trim(), password)
            },
        )
    }
}
