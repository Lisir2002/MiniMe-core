package com.mini.me_core.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppDialogType
import com.mini.me_core.datalayer.engine.EncryptionStatus
import com.mini.me_core.feature.settings.data.repository.BiometricScope
import com.mini.me_core.feature.settings.data.repository.BiometricTimeoutMinutes
import com.mini.me_core.feature.settings.data.repository.SecureScreenScope
import com.mini.me_core.feature.settings.domain.security.SecurityAuditEntry
import com.mini.me_core.feature.settings.domain.security.SecurityScoreCalculator
import com.mini.me_core.feature.settings.presentation.LibEncryptionState
import com.mini.me_core.feature.settings.presentation.SecuritySettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 安全设置页：安全概览、数据库加密、凭据轮换、生物识别、防截图录屏、紧急通道、审计日志。
 */
@Composable
fun SecuritySettingsScreen(
    viewModel: SecuritySettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 确认弹窗状态
    var showBiometricConfirm by remember { mutableStateOf(false) }
    var pendingBiometricValue by remember { mutableStateOf(false) }
    var showDbEnableConfirm by remember { mutableStateOf(false) }
    var showDbDisableConfirm by remember { mutableStateOf(false) }
    var showRotateConfirm by remember { mutableStateOf(false) }
    var showClearAuditConfirm by remember { mutableStateOf(false) }

    // 成功/错误 → Snackbar
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SecurityOverviewCard(
                score = uiState.securityScore,
                risks = uiState.securityRisks,
            )

            DatabaseEncryptionCard(
                enabled = uiState.dbEncryptionEnabled,
                migrating = uiState.dbEncryptionMigrating,
                progress = uiState.dbEncryptionProgress,
                currentLib = uiState.dbEncryptionCurrentLib,
                error = uiState.dbEncryptionError,
                libStates = uiState.libStates,
                verificationResult = uiState.dbVerificationResult,
                onRetry = { viewModel.retryMigration() },
                onVerify = { viewModel.verifyDbEncryption() },
                onRequestEnable = { showDbEnableConfirm = true },
                onRequestDisable = { showDbDisableConfirm = true },
            )

            RotationCard(
                rotationCounter = uiState.rotationCounter,
                lastRotatedAt = uiState.lastRotatedAt,
                rotating = uiState.rotating,
                keyStale = uiState.keyStale,
                history = uiState.rotationHistory,
                onRotate = { showRotateConfirm = true },
            )

            BiometricCard(
                required = uiState.biometricRequired,
                supported = uiState.biometricSupported,
                scopes = uiState.biometricScopes,
                timeout = uiState.biometricTimeout,
                onToggle = { newValue ->
                    pendingBiometricValue = newValue
                    if (newValue) showBiometricConfirm = true else viewModel.toggleBiometric(false)
                },
                onScopeChange = { scope, enabled -> viewModel.setBiometricScopeEnabled(scope, enabled) },
                onTimeoutChange = { viewModel.setBiometricTimeout(it) },
            )

            SecureScreenCard(
                enabled = uiState.secureScreenEnabled,
                scope = uiState.secureScreenScope,
                onToggle = { viewModel.toggleSecureScreen(it) },
                onScopeChange = { viewModel.setSecureScreenScope(it) },
            )

            SecurityEmergencyChannelSection(
                onReset = { host, port, username, password ->
                    viewModel.emergencyReset(host, port, username, password)
                },
                isResetting = uiState.resetting,
                errorMessage = null,
                history = uiState.auditEntries.filter { it.action == SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK },
                isLocked = uiState.isEmergencyLocked(),
                lockoutRemainingMs = (uiState.emergencyLockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L),
            )

            AuditLogCard(
                entries = uiState.auditEntries,
                onClear = { showClearAuditConfirm = true },
            )
        }
    }

    // ── 弹窗 ──
    if (showBiometricConfirm) {
        AppDialog(
            title = stringResource(R.string.security_biometric_confirm_title),
            message = stringResource(R.string.security_biometric_confirm_message),
            confirmText = stringResource(R.string.security_biometric_confirm_action),
            type = AppDialogType.Default,
            onDismiss = { showBiometricConfirm = false },
            onConfirm = { viewModel.toggleBiometric(pendingBiometricValue) },
        )
    }
    if (showDbEnableConfirm) {
        AppDialog(
            title = stringResource(R.string.security_db_enable_title),
            message = stringResource(R.string.security_db_enable_message),
            confirmText = stringResource(R.string.security_db_enable_action),
            type = AppDialogType.Default,
            onDismiss = { showDbEnableConfirm = false },
            onConfirm = {
                showDbEnableConfirm = false
                viewModel.enableDbEncryption()
            },
        )
    }
    if (showDbDisableConfirm) {
        AppDialog(
            title = stringResource(R.string.security_db_disable_title),
            message = stringResource(R.string.security_db_disable_message),
            confirmText = stringResource(R.string.security_db_disable_action),
            type = AppDialogType.Destructive,
            onDismiss = { showDbDisableConfirm = false },
            onConfirm = {
                showDbDisableConfirm = false
                viewModel.disableDbEncryption()
            },
        )
    }
    if (showRotateConfirm) {
        AppDialog(
            title = stringResource(R.string.security_rotate_confirm_title),
            message = stringResource(R.string.security_rotate_confirm_message),
            confirmText = stringResource(R.string.security_rotate_confirm_action),
            type = AppDialogType.Default,
            onDismiss = { showRotateConfirm = false },
            onConfirm = {
                showRotateConfirm = false
                viewModel.rotateDek()
            },
        )
    }
    if (showClearAuditConfirm) {
        AppDialog(
            title = stringResource(R.string.security_audit_clear_title),
            message = stringResource(R.string.security_audit_clear_message),
            confirmText = stringResource(R.string.security_audit_clear_action),
            type = AppDialogType.Destructive,
            onDismiss = { showClearAuditConfirm = false },
            onConfirm = {
                showClearAuditConfirm = false
                viewModel.clearAuditLog()
            },
        )
    }
}

// ── 安全概览卡片 ────────────────────────────────────────────────────

@Composable
private fun SecurityOverviewCard(score: Int, risks: List<SecurityScoreCalculator.SecurityRisk>) {
    val result = SecurityScoreCalculator.Result(score = score, risks = risks)
    val level = result.level
    val accent = when (level) {
        SecurityScoreCalculator.Result.Level.GOOD -> MaterialTheme.colorScheme.primary
        SecurityScoreCalculator.Result.Level.WARNING -> MaterialTheme.colorScheme.error
        SecurityScoreCalculator.Result.Level.CRITICAL -> MaterialTheme.colorScheme.error
    }
    val icon = when (level) {
        SecurityScoreCalculator.Result.Level.GOOD -> Icons.Rounded.CheckCircle
        SecurityScoreCalculator.Result.Level.WARNING -> Icons.Rounded.Warning
        SecurityScoreCalculator.Result.Level.CRITICAL -> Icons.Rounded.ErrorOutline
    }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.security_overview_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.security_overview_score, score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
            )
            if (risks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.security_overview_risks),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                risks.forEach { risk ->
                    Row(
                        Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.width(16.dp).height(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = riskLabel(risk.target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun riskLabel(target: SecurityScoreCalculator.RiskTarget): String = when (target) {
    SecurityScoreCalculator.RiskTarget.DB_ENCRYPTION -> stringResource(R.string.security_risk_db_not_encrypted)
    SecurityScoreCalculator.RiskTarget.BIOMETRIC -> stringResource(R.string.security_risk_biometric_off)
    SecurityScoreCalculator.RiskTarget.SECURE_SCREEN -> stringResource(R.string.security_risk_secure_screen_off)
    SecurityScoreCalculator.RiskTarget.KEY_ROTATION -> stringResource(R.string.security_risk_key_stale)
    SecurityScoreCalculator.RiskTarget.EMERGENCY -> stringResource(R.string.security_risk_emergency_history)
}

// ── 数据库加密卡片 ─────────────────────────────────────────────────

@Composable
private fun DatabaseEncryptionCard(
    enabled: Boolean,
    migrating: Boolean,
    progress: Int,
    currentLib: String?,
    error: String?,
    libStates: List<LibEncryptionState>,
    verificationResult: String?,
    onRetry: () -> Unit,
    onVerify: () -> Unit,
    onRequestEnable: () -> Unit,
    onRequestDisable: () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.security_db_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (enabled) stringResource(R.string.security_db_status_encrypted)
                    else stringResource(R.string.security_db_status_plain),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.security_db_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 逐库状态
            if (libStates.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                libStates.forEach { lib ->
                    val (label, color) = when (lib.display) {
                        LibEncryptionState.Display.ENCRYPTED ->
                            stringResource(R.string.security_db_lib_encrypted) to MaterialTheme.colorScheme.primary
                        LibEncryptionState.Display.PLAIN ->
                            stringResource(R.string.security_db_lib_plain) to MaterialTheme.colorScheme.onSurfaceVariant
                        LibEncryptionState.Display.MIGRATING ->
                            stringResource(R.string.security_db_lib_migrating) to MaterialTheme.colorScheme.tertiary
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lib.libName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(110.dp),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%.1f KB".format(lib.fileSizeBytes / 1024.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 迁移进度
            if (migrating) {
                Spacer(Modifier.height(Spacing.sm))
                val currentIndex = libStates.indexOfFirst { it.libName == currentLib } + 1
                Text(
                    text = stringResource(R.string.security_db_migrating_progress, currentIndex, libStates.size, currentLib ?: "", progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(16.dp).height(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.weight(1f))
                    AppButton(
                        text = stringResource(R.string.security_db_retry),
                        onClick = onRetry,
                        variant = AppButtonVariant.Outlined,
                        buttonColor = AppButtonColor.Error,
                        size = AppButtonSize.Small,
                    )
                }
            }

            verificationResult?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppButton(
                    text = if (enabled) stringResource(R.string.security_db_turn_off)
                    else stringResource(R.string.security_db_turn_on),
                    onClick = { if (enabled) onRequestDisable() else onRequestEnable() },
                    buttonColor = if (enabled) AppButtonColor.Error else AppButtonColor.Primary,
                    enabled = !migrating,
                )
                AppButton(
                    text = stringResource(R.string.security_db_verify),
                    onClick = onVerify,
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Medium,
                )
            }
        }
    }
}

// ── 凭据密钥轮换卡片 ──────────────────────────────────────────────

@Composable
private fun RotationCard(
    rotationCounter: Int,
    lastRotatedAt: Long,
    rotating: Boolean,
    keyStale: Boolean,
    history: List<com.mini.me_core.feature.settings.presentation.RotationHistoryEntry>,
    onRotate: () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.ui________3b563f02),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.security_rotate_desc, rotationCounter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastRotatedAt > 0L) {
                Spacer(Modifier.height(Spacing.xs))
                val time = remember(lastRotatedAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastRotatedAt))
                }
                Text(
                    text = stringResource(R.string.security_rotate_last_time, time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (keyStale) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(16.dp).height(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.security_rotate_stale_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // 轮换历史（最近 5 次）
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = stringResource(R.string.security_rotate_history_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                history.take(5).forEach { entry ->
                    if (entry.atMs > 0L) {
                        val time = remember(entry.atMs) {
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.atMs))
                        }
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = stringResource(R.string.security_rotate_history_version, entry.version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            AppButton(
                text = if (rotating) stringResource(R.string.ui_____6b53d706)
                else stringResource(R.string.security_rotate_action),
                onClick = onRotate,
                loading = rotating,
            )
        }
    }
}

// ── 生物识别卡片 ──────────────────────────────────────────────────

@Composable
private fun BiometricCard(
    required: Boolean,
    supported: Boolean,
    scopes: Set<BiometricScope>,
    timeout: BiometricTimeoutMinutes,
    onToggle: (Boolean) -> Unit,
    onScopeChange: (BiometricScope, Boolean) -> Unit,
    onTimeoutChange: (BiometricTimeoutMinutes) -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ui__________5d343f60),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (required) stringResource(R.string.security_state_on)
                    else stringResource(R.string.security_state_off),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (required) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = required,
                    onCheckedChange = onToggle,
                    enabled = supported,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.security_biometric_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!supported) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.security_biometric_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (supported && required) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = stringResource(R.string.security_biometric_scope_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                BiometricScope.entries.forEach { scope ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = scopes.contains(scope),
                            onCheckedChange = { onScopeChange(scope, it) },
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = scopeLabel(scope),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.security_biometric_timeout_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                BiometricTimeoutMinutes.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = timeout == t,
                            onClick = { onTimeoutChange(t) },
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.security_biometric_timeout_minutes, t.minutes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun scopeLabel(scope: BiometricScope): String = when (scope) {
    BiometricScope.SSH_CREDENTIALS -> stringResource(R.string.security_biometric_scope_ssh)
    BiometricScope.API_KEY -> stringResource(R.string.security_biometric_scope_api_key)
    BiometricScope.DB_ENCRYPTION_OP -> stringResource(R.string.security_biometric_scope_db_op)
    BiometricScope.KEY_ROTATION -> stringResource(R.string.security_biometric_scope_rotation)
}

// ── 防截图录屏卡片 ─────────────────────────────────────────────────

@Composable
private fun SecureScreenCard(
    enabled: Boolean,
    scope: SecureScreenScope,
    onToggle: (Boolean) -> Unit,
    onScopeChange: (SecureScreenScope) -> Unit,
) {
    var testMessage by remember { mutableStateOf<String?>(null) }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.security_secure_screen_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (enabled) stringResource(R.string.security_state_on)
                    else stringResource(R.string.security_state_off),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.security_secure_screen_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = stringResource(R.string.security_secure_screen_scope_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                SecureScreenScope.entries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = scope == s,
                            onClick = { onScopeChange(s) },
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = secureScopeLabel(s),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                AppButton(
                    text = stringResource(R.string.security_secure_screen_test),
                    onClick = { testMessage = "现在尝试截图，系统应拦截" },
                    variant = AppButtonVariant.Tonal,
                    size = AppButtonSize.Medium,
                )
                testMessage?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun secureScopeLabel(scope: SecureScreenScope): String = when (scope) {
    SecureScreenScope.PROVIDER_EDITOR_ONLY -> stringResource(R.string.security_secure_screen_scope_provider)
    SecureScreenScope.ALL_SENSITIVE_PAGES -> stringResource(R.string.security_secure_screen_scope_all)
    SecureScreenScope.GLOBAL -> stringResource(R.string.security_secure_screen_scope_global)
}

// ── 审计日志卡片 ──────────────────────────────────────────────────

@Composable
private fun AuditLogCard(
    entries: List<SecurityAuditEntry>,
    onClear: () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.security_audit_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                AppButton(
                    text = stringResource(R.string.security_audit_clear),
                    onClick = onClear,
                    variant = AppButtonVariant.Text,
                    buttonColor = AppButtonColor.Error,
                    size = AppButtonSize.Small,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.security_audit_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.take(20).forEach { entry ->
                    val time = remember(entry.atMs) {
                        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.atMs))
                    }
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = actionLabel(entry.action),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = if (entry.success) stringResource(R.string.security_audit_result_ok)
                            else stringResource(R.string.security_audit_result_fail),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    entry.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 80.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabel(action: String): String = when (action) {
    SecurityAuditEntry.ACTION_DB_ENCRYPTION -> stringResource(R.string.security_audit_action_db)
    SecurityAuditEntry.ACTION_KEY_ROTATE -> stringResource(R.string.security_audit_action_rotate)
    SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK -> stringResource(R.string.security_audit_action_emergency)
    SecurityAuditEntry.ACTION_BIOMETRIC -> stringResource(R.string.security_audit_action_biometric)
    SecurityAuditEntry.ACTION_SECURE_SCREEN -> stringResource(R.string.security_audit_action_secure)
    else -> action
}
