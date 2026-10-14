package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.mini.me_core.core.theme.components.AppTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mini.me_core.core.theme.Spacing

/**
 * 备份加密选项：开关 + 密码输入。
 * 用户勾选「使用密码加密备份」时展开。
 *
 * 安全口径：加密即整包加密（FULL）；历史上「仅加密凭据」单选从未接入导出管线，
 * 已从 UI 删除，杜绝看似可选实则无效的选项。
 */
@Suppress("FunctionNaming") // @Composable 函数按项目惯例使用 PascalCase，detekt 已知误报
@Composable
fun BackupEncryptOptionsSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_encrypt_with_password),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.backup_encrypt_cross_device_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (enabled) {
            Spacer(Modifier.height(Spacing.sm))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(LocalCornerRadius.current.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    AppTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(Spacing.sm))

                    Text(
                        text = stringResource(R.string.backup_encrypt_scope_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}