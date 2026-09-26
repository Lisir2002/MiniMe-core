package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.security.OperationResult
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.DbEncryptionMigrationEngine
import com.mini.me_core.datalayer.engine.EncryptionStatus
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.engine.MigrationResult
import com.mini.me_core.datalayer.engine.MigrationStateStore
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

/** 单个库的加密状态（用于数据库加密卡片逐库展示）。 */
data class LibEncryptionState(
    val libName: String,
    val status: EncryptionStatus,
    val fileSizeBytes: Long = 0L,
) {
    /** UI 分类：已加密 / 明文 / 迁移中。 */
    val display: Display
        get() = when (status) {
            EncryptionStatus.ENCRYPTED -> Display.ENCRYPTED
            EncryptionStatus.PLAIN -> Display.PLAIN
            else -> Display.MIGRATING
        }

    enum class Display { ENCRYPTED, PLAIN, MIGRATING }
}

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
    // 数据库加密：SQLCipher 加密状态与迁移进度
    val dbEncryptionEnabled: Boolean = false,
    val dbEncryptionMigrating: Boolean = false,
    val dbEncryptionProgress: Int = 0,
    val dbEncryptionCurrentLib: String? = null,
    val dbEncryptionError: String? = null,
    val libStates: List<LibEncryptionState> = emptyList(),
    val dbVerificationResult: String? = null,
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
    /** 是否处于紧急解锁锁定中。 */
    fun isEmergencyLocked(nowMs: Long = System.currentTimeMillis()): Boolean =
        emergencyLockoutUntil > nowMs
}

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val encryptor: CredentialEncryptor,
    private val auditLogRepo: RemoteAuditLogRepository,
    private val dbMigrationEngine: DbEncryptionMigrationEngine,
    private val dbMigrationStateStore: MigrationStateStore,
    private val secureScreenRepository: SecureScreenRepository,
    private val pathProvider: DatabasePathProvider,
    private val biometricConfigRepository: BiometricConfigRepository,
    private val securityAuditLogRepository: SecurityAuditLogRepository,
) : ViewModel() {

    private val _baseState = MutableStateFlow(SecurityUiState())
    val baseState: StateFlow<SecurityUiState> = _baseState.asStateFlow()

    /** 外部配置流（防截图 + 生物识别配置），与 baseState 解耦后再合并。 */
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
                val base = _baseState.value
                val now = System.currentTimeMillis()
                val audit = securityAuditLogRepository.list()
                val hasEmergencyHistory = audit.any { it.action == SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK }
                val inputs = SecurityScoreCalculator.Inputs(
                    dbEncrypted = base.dbEncryptionEnabled,
                    biometricEnabled = base.biometricRequired,
                    secureScreenEnabled = runCatching { secureScreenRepository.enabledFlow.first() }.getOrDefault(true),
                    keyRotatedWithin90Days = SecurityScoreCalculator.Inputs.isKeyFresh(base.lastRotatedAt, now),
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
                        securityAuditLogRepository.append(
                            action = SecurityAuditEntry.ACTION_BIOMETRIC,
                            success = false,
                            detail = "切换失败: ${result.error.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(loading = false, error = "切换失败: ${e.message}")
                FileLogger.w("SecurityVM", "切换生物识别失败", e)
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
                        securityAuditLogRepository.append(
                            action = SecurityAuditEntry.ACTION_KEY_ROTATE,
                            success = false,
                            detail = "轮换失败: ${result.error.message}",
                        )
                    }
                }
            } catch (e: Exception) {
                // 修复：encryptor 抛异常时 rotating 永远卡 true —— 这里兜底复位。
                _baseState.value = _baseState.value.copy(
                    rotating = false,
                    error = "轮换失败: ${e.message}"
                )
                FileLogger.e("SecurityVM", "rotateDek 异常", e)
                securityAuditLogRepository.append(
                    action = SecurityAuditEntry.ACTION_KEY_ROTATE,
                    success = false,
                    detail = "轮换异常: ${e.message}",
                )
            }
        }
    }

    // ── 数据库加密（SQLCipher）───────────────────────────────────────

    fun refreshDbEncryptionStatus() {
        viewModelScope.launch {
            try {
                val states = LibName.entries.map { lib ->
                    val st = dbMigrationStateStore.getState(lib)
                    val size = runCatching { pathProvider.mainDb(lib).length() }.getOrDefault(0L)
                    LibEncryptionState(
                        libName = lib.name,
                        status = st.encryptionStatus,
                        fileSizeBytes = size,
                    )
                }
                val allEncrypted = states.all { it.status == EncryptionStatus.ENCRYPTED }
                _baseState.value = _baseState.value.copy(
                    dbEncryptionEnabled = allEncrypted,
                    libStates = states,
                )
                refreshSecurityScore()
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "刷新数据库加密状态失败", e)
            }
        }
    }

    fun enableDbEncryption() {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(
                dbEncryptionMigrating = true,
                dbEncryptionProgress = 0,
                dbEncryptionError = null,
                dbEncryptionCurrentLib = null,
            )

            var completed = 0
            var hasError = false
            val total = LibName.entries.size
            for (lib in LibName.entries) {
                _baseState.value = _baseState.value.copy(dbEncryptionCurrentLib = lib.name)
                try {
                    val result = dbMigrationEngine.migrateToEncrypted(lib)
                    when (result) {
                        MigrationResult.SUCCESS, MigrationResult.ALREADY_ENCRYPTED -> {
                            completed++
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionProgress = (completed.toDouble() / total * 100).toInt(),
                            )
                        }
                        MigrationResult.FAILED_RETRYABLE -> {
                            hasError = true
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionError = "库 ${lib.name} 迁移失败（可重试）",
                            )
                            break
                        }
                        MigrationResult.ALREADY_PLAIN -> Unit
                    }
                } catch (e: Exception) {
                    hasError = true
                    _baseState.value = _baseState.value.copy(
                        dbEncryptionError = "库 ${lib.name} 迁移异常: ${e.message}",
                    )
                    FileLogger.e("SecurityVM", "数据库加密迁移失败: ${lib.name}", e)
                    break
                }
            }

            _baseState.value = _baseState.value.copy(
                dbEncryptionMigrating = false,
                dbEncryptionCurrentLib = null,
                dbEncryptionEnabled = !hasError && completed == total,
                successMessage = if (!hasError) "数据库加密已开启（$total 个库）" else null,
            )
            securityAuditLogRepository.append(
                action = SecurityAuditEntry.ACTION_DB_ENCRYPTION,
                success = !hasError,
                detail = if (!hasError) "开启数据库加密（$total 个库）" else "开启数据库加密失败",
            )
            refreshDbEncryptionStatus()
        }
    }

    fun disableDbEncryption() {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(
                dbEncryptionMigrating = true,
                dbEncryptionProgress = 0,
                dbEncryptionError = null,
                dbEncryptionCurrentLib = null,
            )

            var completed = 0
            var hasError = false
            val total = LibName.entries.size
            for (lib in LibName.entries) {
                _baseState.value = _baseState.value.copy(dbEncryptionCurrentLib = lib.name)
                try {
                    val result = dbMigrationEngine.migrateToPlain(lib)
                    when (result) {
                        MigrationResult.SUCCESS, MigrationResult.ALREADY_PLAIN -> {
                            completed++
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionProgress = (completed.toDouble() / total * 100).toInt(),
                            )
                        }
                        MigrationResult.FAILED_RETRYABLE -> {
                            hasError = true
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionError = "库 ${lib.name} 反向迁移失败（可重试）",
                            )
                            break
                        }
                        MigrationResult.ALREADY_ENCRYPTED -> Unit
                    }
                } catch (e: Exception) {
                    hasError = true
                    _baseState.value = _baseState.value.copy(
                        dbEncryptionError = "库 ${lib.name} 反向迁移异常: ${e.message}",
                    )
                    FileLogger.e("SecurityVM", "数据库解密迁移失败: ${lib.name}", e)
                    break
                }
            }

            _baseState.value = _baseState.value.copy(
                dbEncryptionMigrating = false,
                dbEncryptionCurrentLib = null,
                dbEncryptionEnabled = hasError,
                successMessage = if (!hasError) "数据库加密已关闭（所有库已回退到明文）" else null,
            )
            securityAuditLogRepository.append(
                action = SecurityAuditEntry.ACTION_DB_ENCRYPTION,
                success = !hasError,
                detail = if (!hasError) "关闭数据库加密" else "关闭数据库加密失败",
            )
            refreshDbEncryptionStatus()
        }
    }

    /** 迁移失败后重试：复用当前开关方向。 */
    fun retryMigration() {
        viewModelScope.launch {
            val currentlyEnabled = _baseState.value.dbEncryptionEnabled
            if (currentlyEnabled) disableDbEncryption() else enableDbEncryption()
        }
    }

    /** 手动校验：逐库重读状态 + 文件存在性/大小，输出结果摘要。 */
    fun verifyDbEncryption() {
        viewModelScope.launch {
            try {
                val sb = StringBuilder()
                var ok = 0
                LibName.entries.forEach { lib ->
                    val st = dbMigrationStateStore.getState(lib)
                    val file = runCatching { pathProvider.mainDb(lib) }.getOrNull()
                    val exists = file?.exists() == true
                    val size = file?.length() ?: 0L
                    val statusLabel = when (st.encryptionStatus) {
                        EncryptionStatus.ENCRYPTED -> "已加密"
                        EncryptionStatus.PLAIN -> "明文"
                        else -> "迁移中"
                    }
                    val fileLabel = when {
                        !exists -> "文件缺失"
                        size <= 0L -> "空文件"
                        else -> "%.1f KB".format(size / 1024.0)
                    }
                    sb.appendLine("${lib.name}: $statusLabel · $fileLabel")
                    if (st.encryptionStatus == EncryptionStatus.ENCRYPTED && exists && size > 0L) ok++
                }
                _baseState.value = _baseState.value.copy(
                    dbVerificationResult = "校验完成：$ok/${LibName.entries.size} 个库状态正常\n$sb",
                )
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(dbVerificationResult = "校验失败: ${e.message}")
                FileLogger.w("SecurityVM", "手动校验数据库失败", e)
            }
        }
    }

    fun toggleDbEncryption(enabled: Boolean) {
        if (enabled) enableDbEncryption() else disableDbEncryption()
    }

    // ── 防截图录屏 ─────────────────────────────────────────────────────

    fun toggleSecureScreen(enabled: Boolean) {
        viewModelScope.launch {
            try {
                secureScreenRepository.setEnabled(enabled)
                _baseState.value = _baseState.value.copy(
                    successMessage = "防截图录屏已${if (enabled) "开启" else "关闭"}"
                )
                securityAuditLogRepository.append(
                    action = SecurityAuditEntry.ACTION_SECURE_SCREEN,
                    success = true,
                    detail = "防截图录屏已${if (enabled) "开启" else "关闭"}",
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
                FileLogger.i("SecurityVM", "紧急重置完成")
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
                // 验证失败计数：连续 5 次失败锁定 5 分钟。
                val failed = _baseState.value.emergencyFailedAttempts + 1
                val lockoutUntil = if (failed >= 5) now + 5 * 60 * 1000L else 0L
                _baseState.value = _baseState.value.copy(
                    resetting = false,
                    emergencyFailedAttempts = failed,
                    emergencyLockoutUntil = lockoutUntil,
                    error = "重置失败: ${e.message}",
                )
                securityAuditLogRepository.append(
                    action = SecurityAuditEntry.ACTION_EMERGENCY_UNLOCK,
                    success = false,
                    detail = "失败（$host:$port）: ${e.message}",
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
