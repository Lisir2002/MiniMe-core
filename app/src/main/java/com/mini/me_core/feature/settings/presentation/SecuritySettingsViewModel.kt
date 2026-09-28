package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.security.OperationResult
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.feature.settings.data.repository.BiometricConfigRepository
import com.mini.me_core.feature.settings.data.repository.BiometricScope
import com.mini.me_core.feature.settings.data.repository.BiometricTimeoutMinutes
import com.mini.me_core.feature.settings.data.repository.SecureScreenRepository
import com.mini.me_core.feature.settings.data.repository.SecureScreenScope
import com.mini.me_core.feature.settings.data.repository.SecurityAuditLogRepository
import com.mini.me_core.feature.settings.domain.security.SecurityAuditEntry
import com.mini.me_core.feature.settings.domain.security.SecurityScoreCalculator
import com.mini.me_core.feature.workspace.domain.RemoteAuditAction
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 单个库的加密状态（新架构全部默认加密，仅展示文件信息）。 */
data class LibEncryptionState(
    val libName: String,
    val fileSizeBytes: Long = 0L,
)

/** 密钥轮换历史条目。 */
data class RotationHistoryEntry(
    val atMs: Long,
    val version: Int,
)

data class SecurityUiState(
    val biometricRequired: Boolean = false,
    val biometricSupported: Boolean = true,
    val rotationCounter: Int = 0,
    val lastRotatedAt: Long = 0L,
    val loading: Boolean = false,
    val rotating: Boolean = false,
    val resetting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // 数据库加密：新架构全部默认加密，仅展示状态
    val libStates: List<LibEncryptionState> = emptyList(),
    // 防截图录屏
    val secureScreenEnabled: Boolean = true,
    val secureScreenScope: SecureScreenScope = SecureScreenScope.DEFAULT,
    // 生物识别配置
    val biometricScopes: Set<BiometricScope> = emptySet(),
    val biometricTimeout: BiometricTimeoutMinutes = BiometricTimeoutMinutes.DEFAULT,
    // 安全概览
    val securityScore: Int = 0,
    val securityRisks: List<SecurityScoreCalculator.SecurityRisk> = emptyList(),
    // 密钥轮换历史
    val rotationHistory: List<RotationHistoryEntry> = emptyList(),
    val keyStale: Boolean = false,
    // 紧急解锁
    val emergencyUnlockCount: Int = 0,
    val emergencyFailedAttempts: Int = 0,
    val emergencyLockoutUntil: Long = 0L,
    // 审计日志
    val auditEntries: List<SecurityAuditEntry> = emptyList(),
) {
    /** 新架构：所有数据库默认加密。 */
    val dbEncryptionEnabled: Boolean get() = true

    /** 是否处于紧急解锁锁定中。 */
    fun isEmergencyLocked(nowMs: Long = System.currentTimeMillis()): Boolean =
        emergencyLockoutUntil > nowMs
}

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val encryptor: CredentialEncryptor,
    private val auditLogRepo: RemoteAuditLogRepository,
    private val secureScreenRepository: SecureScreenRepository,
    private val pathProvider: DatabasePathProvider,
    private val biometricConfigRepository: BiometricConfigRepository,
    private val securityAuditLogRepository: SecurityAuditLogRepository,
) : ViewModel() {

    private val _baseState = MutableStateFlow(SecurityUiState())
    val baseState: StateFlow<SecurityUiState> = _baseState.asStateFlow()

    private data class ExternalConfig(
        val secureEnabled: Boolean,
        val secureScope: SecureScreenScope,
        val biometricScopes: Set<BiometricScope>,
        val biometricTimeout: BiometricTimeoutMinutes,
    )

    private val externalConfigFlow = combine(
        secureScreenRepository.enabledFlow,
        secureScreenRepository.scopeFlow,
        biometricConfigRepository.scopesFlow,
        biometricConfigRepository.timeoutFlow,
    ) { secure, scope, scopes, timeout ->
        ExternalConfig(secure, scope, scopes, timeout)
    }

    val uiState: StateFlow<SecurityUiState> =
        combine(
            _baseState,
            externalConfigFlow,
            securityAuditLogRepository.observeEntries(),
        ) { base, config, audit ->
            base.copy(
                secureScreenEnabled = config.secureEnabled,
                secureScreenScope = config.secureScope,
                biometricScopes = config.biometricScopes,
                biometricTimeout = config.biometricTimeout,
                auditEntries = audit,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SecurityUiState(),
        )

    init {
        loadState()
        refreshDbEncryptionStatus()
        refreshSecurityScore()
    }

    // ── 基础状态加载 ───────────────────────────────────────────────────

    private fun loadState() {
        viewModelScope.launch {
            try {
                val state = encryptor.encryptionState()
                if (state != null) {
                    val now = System.currentTimeMillis()
                    val fresh = SecurityScoreCalculator.Inputs.isKeyFresh(state.lastRotatedAt, now)
                    _baseState.value = _baseState.value.copy(
                        biometricRequired = state.biometricRequired,
                        rotationCounter = state.rotationCounter,
                        lastRotatedAt = state.lastRotatedAt,
                        keyStale = !fresh,
                        rotationHistory = listOf(
                            RotationHistoryEntry(
                                atMs = state.lastRotatedAt,
                                version = state.rotationCounter,
                            )
                        ).filter { it.atMs > 0L },
                    )
                }
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "加载加密状态失败", e)
            }
        }
    }

    // ── 安全概览评分 ────────────────────────────────────────────────────

    fun refreshSecurityScore() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val audit = securityAuditLogRepository.list()
                val hasEmergencyHistory = audit.any { it.action == SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK }
                val inputs = SecurityScoreCalculator.Inputs(
                    dbEncrypted = true, // 新架构全部默认加密
                    biometricEnabled = _baseState.value.biometricRequired,
                    secureScreenEnabled = runCatching { secureScreenRepository.enabledFlow.first() }.getOrDefault(true),
                    keyRotatedWithin90Days = SecurityScoreCalculator.Inputs.isKeyFresh(_baseState.value.lastRotatedAt, now),
                    noEmergencyUnlockHistory = !hasEmergencyHistory,
                )
                val result = SecurityScoreCalculator.calculate(inputs)
                _baseState.value = _baseState.value.copy(
                    securityScore = result.score,
                    securityRisks = result.risks,
                )
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "计算安全评分失败", e)
            }
        }
    }

    // ── 生物识别 ──────────────────────────────────────────────────────

    fun toggleBiometric(required: Boolean) {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(loading = true, error = null, successMessage = null)
            try {
                val result = encryptor.setBiometricRequired(required)
                when (result) {
                    is OperationResult.Success -> {
                        _baseState.value = _baseState.value.copy(
                            biometricRequired = required,
                            loading = false,
                            successMessage = "生物识别保护已${if (required) "开启" else "关闭"}"
                        )
                        securityAuditLogRepository.append(
                            action = SecurityAuditEntry.ACTION_BIOMETRIC,
                            success = true,
                            detail = "生物识别保护已${if (required) "开启" else "关闭"}",
                        )
                        loadState()
                        refreshSecurityScore()
                    }
                    is OperationResult.Failure -> {
                        _baseState.value = _baseState.value.copy(
                            loading = false,
                            error = "切换失败: ${result.error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(loading = false, error = "切换失败: ${e.message}")
            }
        }
    }

    fun setBiometricScopeEnabled(scope: BiometricScope, enabled: Boolean) {
        viewModelScope.launch {
            try {
                biometricConfigRepository.setScopeEnabled(scope, enabled)
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(error = "保存生物识别范围失败: ${e.message}")
            }
        }
    }

    fun setBiometricTimeout(timeout: BiometricTimeoutMinutes) {
        viewModelScope.launch {
            try {
                biometricConfigRepository.setTimeout(timeout)
                _baseState.value = _baseState.value.copy(successMessage = "生物识别超时已设为 ${timeout.minutes} 分钟")
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(error = "保存生物识别超时失败: ${e.message}")
            }
        }
    }

    // ── 凭据密钥轮换 ───────────────────────────────────────────────────

    fun rotateDek() {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(rotating = true, error = null, successMessage = null)
            try {
                val result = encryptor.scheduleRotateDek()
                when (result) {
                    is OperationResult.Success -> {
                        _baseState.value = _baseState.value.copy(
                            rotating = false,
                            successMessage = "凭据密钥轮换完成（版本 ${result.data.rotationCounter}）"
                        )
                        securityAuditLogRepository.append(
                            action = SecurityAuditEntry.ACTION_KEY_ROTATE,
                            success = true,
                            detail = "轮换完成，版本 ${result.data.rotationCounter}",
                        )
                        loadState()
                        refreshSecurityScore()
                    }
                    is OperationResult.Failure -> {
                        _baseState.value = _baseState.value.copy(
                            rotating = false,
                            error = "轮换失败: ${result.error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(
                    rotating = false,
                    error = "轮换失败: ${e.message}"
                )
                FileLogger.e("SecurityVM", "rotateDek 异常", e)
            }
        }
    }

    // ── 数据库加密状态展示（新架构：全部默认加密）──────────────────────

    fun refreshDbEncryptionStatus() {
        viewModelScope.launch {
            try {
                val states = LibName.entries.map { lib ->
                    val size = runCatching { pathProvider.mainDb(lib).length() }.getOrDefault(0L)
                    LibEncryptionState(
                        libName = lib.name.lowercase(),
                        fileSizeBytes = size,
                    )
                }
                _baseState.value = _baseState.value.copy(libStates = states)
                refreshSecurityScore()
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "刷新数据库加密状态失败", e)
            }
        }
    }

    // ── 防截图录屏 ─────────────────────────────────────────────────────

    fun toggleSecureScreen(enabled: Boolean) {
        viewModelScope.launch {
            try {
                secureScreenRepository.setEnabled(enabled)
                _baseState.value = _baseState.value.copy(
                    successMessage = "防截图录屏已${if (enabled) "开启" else "关闭"}"
                )
                refreshSecurityScore()
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(error = "切换防截图失败: ${e.message}")
            }
        }
    }

    fun setSecureScreenScope(scope: SecureScreenScope) {
        viewModelScope.launch {
            try {
                secureScreenRepository.setScope(scope)
                _baseState.value = _baseState.value.copy(successMessage = "防截图保护范围已更新")
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(error = "保存防截图范围失败: ${e.message}")
            }
        }
    }

    // ── 紧急解锁 ───────────────────────────────────────────────────────

    fun emergencyReset(host: String, port: Int, username: String, password: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (_baseState.value.isEmergencyLocked(now)) {
                _baseState.value = _baseState.value.copy(
                    error = "紧急解锁已临时锁定，请稍后再试（连续失败过多）"
                )
                return@launch
            }
            _baseState.value = _baseState.value.copy(resetting = true, error = null, successMessage = null)
            try {
                encryptor.emergencyResetMasterKey()
                auditLogRepo.append(
                    category = RemoteAuditCategory.SECURITY,
                    action = RemoteAuditAction.EMERGENCY_RESET_MASTERKEY,
                    success = true,
                    message = "通过 SSH 验证（$host:$port）执行紧急重置"
                )
                securityAuditLogRepository.append(
                    action = SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK,
                    success = true,
                    detail = "验证主机 $host:$port",
                )
                _baseState.value = _baseState.value.copy(
                    resetting = false,
                    emergencyFailedAttempts = 0,
                    emergencyLockoutUntil = 0L,
                    emergencyUnlockCount = _baseState.value.emergencyUnlockCount + 1,
                    successMessage = "主密钥已重置，请重新录入各远程连接的密码"
                )
                loadState()
                refreshSecurityScore()
            } catch (e: Exception) {
                val failed = _baseState.value.emergencyFailedAttempts + 1
                val lockoutUntil = if (failed >= 5) now + 5 * 60 * 1000L else 0L
                _baseState.value = _baseState.value.copy(
                    resetting = false,
                    emergencyFailedAttempts = failed,
                    emergencyLockoutUntil = lockoutUntil,
                    error = "重置失败: ${e.message}",
                )
            }
        }
    }

    // ── 审计日志 ───────────────────────────────────────────────────────

    fun clearAuditLog() {
        viewModelScope.launch {
            try {
                securityAuditLogRepository.clear()
                _baseState.value = _baseState.value.copy(successMessage = "审计日志已清空")
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(error = "清空审计日志失败: ${e.message}")
            }
        }
    }

    fun clearMessages() {
        _baseState.value = _baseState.value.copy(error = null, successMessage = null)
    }
}
