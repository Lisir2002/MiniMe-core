package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.security.OperationResult
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.DbEncryptionMigrationEngine
import com.mini.me_core.datalayer.engine.EncryptionStatus
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.engine.MigrationResult
import com.mini.me_core.datalayer.engine.MigrationStateStore
import com.mini.me_core.feature.agent.domain.zth.ZthPerformanceClass
import com.mini.me_core.feature.agent.domain.zth.ZthPresetTier
import com.mini.me_core.feature.settings.data.repository.ZthTierRepository
import com.mini.me_core.feature.settings.data.repository.SecureScreenRepository
import com.mini.me_core.feature.workspace.domain.RemoteAuditAction
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val biometricRequired: Boolean = false,
    val migratedFromV1: Boolean = true,
    val rotationCounter: Int = 0,
    val lastRotatedAt: Long = 0L,
    val loading: Boolean = false,
    val rotating: Boolean = false,
    val resetting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // 数据库加密（P1）：SQLCipher 加密状态与迁移进度
    val dbEncryptionEnabled: Boolean = false,
    val dbEncryptionMigrating: Boolean = false,
    val dbEncryptionProgress: Int = 0,
    val dbEncryptionCurrentLib: String? = null,
    val dbEncryptionError: String? = null,
    // ZTH 三字段（Phase 3.4）：默认值仅用于 UI 初始帧；真实值由 tierFlow 组合覆盖
    val zthTier: ZthPresetTier = ZthPresetTier.BALANCED,
    val zthPerfClass: ZthPerformanceClass = ZthPerformanceClass.HIGH_END,
    val zthSwipeEnabled: Boolean = true,
    // 防截图录屏：模型供应商编辑页是否启用 FLAG_SECURE
    val secureScreenEnabled: Boolean = true
)

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val encryptor: CredentialEncryptor,
    private val auditLogRepo: RemoteAuditLogRepository,
    private val zthTierRepository: ZthTierRepository,
    private val dbMigrationEngine: DbEncryptionMigrationEngine,
    private val dbMigrationStateStore: MigrationStateStore,
    private val secureScreenRepository: SecureScreenRepository,
) : ViewModel() {

    // BaseState（凭据/轮换部分）+ ZTH StateFlow 三字段 + 防截图开关 → 合成一个统一 SecurityUiState
    private val _baseState = MutableStateFlow(SecurityUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SecurityUiState> =
        combine(
            _baseState,
            zthTierRepository.tierFlow,
            zthTierRepository.perfClassFlow,
            zthTierRepository.swipeEnabledFlow,
            secureScreenRepository.enabledFlow
        ) { base, tier, perf, swipe, secure ->
            base.copy(zthTier = tier, zthPerfClass = perf, zthSwipeEnabled = swipe, secureScreenEnabled = secure)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SecurityUiState()
        )

    init {
        loadState()
        refreshDbEncryptionStatus()
    }

    private fun loadState() {
        viewModelScope.launch {
            try {
                val state = encryptor.encryptionState()
                if (state != null) {
                    val cur = _baseState.value
                    _baseState.value = cur.copy(
                        biometricRequired = state.biometricRequired,
                        migratedFromV1 = state.migratedFromV1,
                        rotationCounter = state.rotationCounter,
                        lastRotatedAt = state.lastRotatedAt
                    )
                }
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "加载加密状态失败", e)
            }
        }
    }

    fun toggleBiometric(required: Boolean) {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(loading = true, error = null, successMessage = null)
            val result = encryptor.setBiometricRequired(required)
            when (result) {
                is OperationResult.Success -> {
                    _baseState.value = _baseState.value.copy(
                        biometricRequired = required,
                        loading = false,
                        successMessage = "生物识别保护已${if (required) "开启" else "关闭"}"
                    )
                    loadState()
                }
                is OperationResult.Failure -> {
                    _baseState.value = _baseState.value.copy(
                        loading = false,
                        error = "切换失败: ${result.error.message}"
                    )
                }
            }
        }
    }

    fun rotateDek() {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(rotating = true, error = null, successMessage = null)
            val result = encryptor.scheduleRotateDek()
            when (result) {
                is OperationResult.Success -> {
                    _baseState.value = _baseState.value.copy(
                        rotating = false,
                        successMessage = "凭据密钥轮换完成（版本 ${result.data.rotationCounter}）"
                    )
                    loadState()
                }
                is OperationResult.Failure -> {
                    _baseState.value = _baseState.value.copy(
                        rotating = false,
                        error = "轮换失败: ${result.error.message}"
                    )
                }
            }
        }
    }

    fun emergencyReset(host: String, port: Int, username: String, password: String) {
        viewModelScope.launch {
            _baseState.value = _baseState.value.copy(resetting = true, error = null, successMessage = null)
            try {
                val report = encryptor.emergencyResetMasterKey()
                FileLogger.i("SecurityVM", "紧急重置完成")
                auditLogRepo.append(
                    category = RemoteAuditCategory.SECURITY,
                    action = RemoteAuditAction.EMERGENCY_RESET_MASTERKEY,
                    success = true,
                    message = "通过 SSH 验证（$host:$port）执行紧急重置"
                )
                _baseState.value = _baseState.value.copy(
                    resetting = false,
                    successMessage = "主密钥已重置，请重新录入各远程连接的密码"
                )
                loadState()
            } catch (e: Exception) {
                _baseState.value = _baseState.value.copy(
                    resetting = false,
                    error = "重置失败: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _baseState.value = _baseState.value.copy(error = null, successMessage = null)
    }

    // ── 数据库加密（P1 / SQLCipher）────────────────────────────────────

    /**
     * 刷新所有库的加密状态。
     * P1 阶段：所有库都为 ENCRYPTED 时 dbEncryptionEnabled=true，否则为 false。
     */
    fun refreshDbEncryptionStatus() {
        viewModelScope.launch {
            try {
                val allEncrypted = LibName.entries.all { lib ->
                    dbMigrationStateStore.getState(lib).encryptionStatus == EncryptionStatus.ENCRYPTED
                }
                _baseState.value = _baseState.value.copy(dbEncryptionEnabled = allEncrypted)
            } catch (e: Exception) {
                FileLogger.w("SecurityVM", "刷新数据库加密状态失败", e)
            }
        }
    }

    /**
     * 开启数据库加密：依次对 6 个库执行明文→加密迁移。
     * 迁移过程中更新进度（已完成库数 / 6 * 100）。
     * 任何一个库迁移失败 → 停止后续迁移，保留已加密的库，显示错误。
     */
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
            for (lib in LibName.entries) {
                _baseState.value = _baseState.value.copy(dbEncryptionCurrentLib = lib.name)
                try {
                    val result = dbMigrationEngine.migrateToEncrypted(lib)
                    when (result) {
                        MigrationResult.SUCCESS, MigrationResult.ALREADY_ENCRYPTED -> {
                            completed++
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionProgress = (completed.toDouble() / LibName.entries.size * 100).toInt(),
                            )
                        }
                        MigrationResult.FAILED_RETRYABLE -> {
                            hasError = true
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionError = "库 ${lib.name} 迁移失败（可重试）",
                            )
                            break
                        }
                        MigrationResult.ALREADY_PLAIN -> {
                            // 正向迁移不会返回此值，忽略
                        }
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
                dbEncryptionEnabled = !hasError && completed == LibName.entries.size,
                successMessage = if (!hasError) "数据库加密已开启（${LibName.entries.size} 个库）" else null,
            )
        }
    }

    /**
     * 关闭数据库加密：依次对 6 个库执行加密→明文反向迁移。
     */
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
            for (lib in LibName.entries) {
                _baseState.value = _baseState.value.copy(dbEncryptionCurrentLib = lib.name)
                try {
                    val result = dbMigrationEngine.migrateToPlain(lib)
                    when (result) {
                        MigrationResult.SUCCESS, MigrationResult.ALREADY_PLAIN -> {
                            completed++
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionProgress = (completed.toDouble() / LibName.entries.size * 100).toInt(),
                            )
                        }
                        MigrationResult.FAILED_RETRYABLE -> {
                            hasError = true
                            _baseState.value = _baseState.value.copy(
                                dbEncryptionError = "库 ${lib.name} 反向迁移失败（可重试）",
                            )
                            break
                        }
                        MigrationResult.ALREADY_ENCRYPTED -> {
                            // 反向迁移不会返回此值，忽略
                        }
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
                dbEncryptionEnabled = hasError, // 有错误时保持原状态
                successMessage = if (!hasError) "数据库加密已关闭（所有库已回退到明文）" else null,
            )
        }
    }

    /**
     * 切换数据库加密开关。
     * @param enabled true=开启加密，false=关闭加密
     */
    fun toggleDbEncryption(enabled: Boolean) {
        if (enabled) enableDbEncryption() else disableDbEncryption()
    }

    // ── ZTH 档位三 setter（Phase 3.4）──────────────────────────────────
    fun setZthTier(tier: ZthPresetTier) {
        viewModelScope.launch {
            zthTierRepository.setTier(tier)
            _baseState.value = _baseState.value.copy(successMessage = "ZTH 档位已设置为：$tier")
        }
    }
    fun setZthPerf(cls: ZthPerformanceClass) {
        viewModelScope.launch {
            zthTierRepository.setPerformanceClass(cls)
            _baseState.value = _baseState.value.copy(successMessage = "ZTH 性能等级已设置为：${cls.name}")
        }
    }
    fun setZthSwipe(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.zthTier
            zthTierRepository.setSwipeEnabled(enabled, current)
            val actual = if (current.tier >= 2 && !enabled) {
                _baseState.value = _baseState.value.copy(error = "档位 BALANCED/STRICT：必须启用滑动确认（C.4.8），已阻止关闭。")
                true
            } else {
                _baseState.value = _baseState.value.copy(successMessage = "ZTH 滑动确认：${if (enabled) "开" else "关"}（仅档位 MINIMAL 以下允许关闭）")
                enabled
            }
            _baseState.value = _baseState.value.copy(zthSwipeEnabled = actual)
        }
    }

    // ── 防截图录屏开关 ──
    fun toggleSecureScreen(enabled: Boolean) {
        viewModelScope.launch {
            secureScreenRepository.setEnabled(enabled)
            _baseState.value = _baseState.value.copy(
                successMessage = "防截图录屏已${if (enabled) "开启" else "关闭"}"
            )
        }
    }
}