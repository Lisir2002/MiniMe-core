package com.mini.me_core.feature.backup.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.backup.data.AutoBackupManager
import com.mini.me_core.feature.backup.data.ExternalBackupStore
import com.mini.me_core.feature.backup.data.guard.DataSentinel
import com.mini.me_core.feature.backup.data.guard.SentinelVerdict
import com.mini.me_core.feature.backup.domain.AutoBackupConfig
import com.mini.me_core.feature.backup.domain.BackupDecryptionException
import com.mini.me_core.feature.backup.domain.BackupHistoryItem
import com.mini.me_core.feature.backup.domain.BackupManager
import com.mini.me_core.feature.backup.domain.BackupOptions
import com.mini.me_core.feature.backup.domain.BackupPreview
import com.mini.me_core.feature.backup.domain.BackupSource
import com.mini.me_core.feature.backup.domain.RestoreMode
import com.mini.me_core.feature.backup.domain.RestoreStats
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mini.me_core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import javax.inject.Inject

sealed class BackupState {
    data object Idle : BackupState()
    /** fraction < 0 表示不确定进度；>=0 为 0..1。 */
    data class Working(val fraction: Float = -1f, val label: String = "") : BackupState()
    data object ExportDone : BackupState()
    data class ImportSuccess(val stats: RestoreStats, val canUndo: Boolean) : BackupState()
    data class Error(val message: String) : BackupState()
    /** 恢复预览就绪：展示差异后由用户选择合并/覆盖。 */
    data class PreviewReady(
        val preview: BackupPreview,
        val source: RestoreSource,
        val password: String,
    ) : BackupState()
}

/** 预览来源：SAF 选的 Uri / 本机历史文件 / 外部历史条目。 */
sealed class RestoreSource {
    data class SafUri(val uri: Uri) : RestoreSource()
    data class LocalFile(val file: File) : RestoreSource()
    data class External(val item: ExternalBackupStore.Item) : RestoreSource()
}

/** 数据保全（哨兵 + 本机/外部自动备份 + 历史）的 UI 状态。 */
data class DataSafetyUiState(
    val verdict: SentinelVerdict? = null,
    val lastBackupTime: Long? = null,
    val backupCount: Int = 0,
    val localStorageBytes: Long = 0L,
    val lastExternalBackupTime: Long? = null,
    val externalBackupCount: Int = 0,
    val externalAvailable: Boolean = true,
    val working: Boolean = false,
    val justBackedUp: Boolean = false,
    val history: List<BackupHistoryItem> = emptyList(),
    val config: AutoBackupConfig = AutoBackupConfig(),
    /** 最近一次轻提示消息（一次性消费）。 */
    val snackbar: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val dataSentinel: DataSentinel,
    private val autoBackupManager: AutoBackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _dataSafety = MutableStateFlow(DataSafetyUiState())
    val dataSafety: StateFlow<DataSafetyUiState> = _dataSafety.asStateFlow()

    /** 当前进行中的任务，用于取消。 */
    private var workingJob: Job? = null
    /** 最近一次恢复前安全点，用于「撤销本次恢复」。 */
    private var lastSafetyPoint: File? = null

    // ── 导出 ──────────────────────────────────────────────

    fun export(password: String, options: BackupOptions, output: OutputStream) {
        _state.value = BackupState.Working(label = context.getString(R.string.backup_processing_data))
        workingJob = viewModelScope.launch {
            val pw = password.toCharArray().takeIf { it.isNotEmpty() }
            try {
                backupManager.export(pw, options, output)
                _state.value = BackupState.ExportDone
                showSnackbar(context.getString(R.string.backup_exported))
            } catch (e: Exception) {
                _state.value = BackupState.Error(e.message ?: context.getString(R.string.backup_export_failed))
            } finally {
                runCatching { output.close() }
            }
        }
    }

    /** 导出文件名：含应用版本号，规范扩展名 .tar.gz。 */
    fun defaultExportFileName(): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val ver = if (version.isNotBlank()) "-v$version" else ""
        return "minime-backup$ver-$stamp.tar.gz"
    }

    // ── 导入 / 恢复（预览 → 模式 → 安全点 → 恢复 → 可撤销） ──────────

    /** 用户从 SAF 选了文件：先打开口令框，确认口令后进入预览。 */
    fun startPreviewFromSaf(uri: Uri, password: String) {
        val pw = password.toCharArray().takeIf { it.isNotEmpty() }
        runRestorePreview(RestoreSource.SafUri(uri), password) { src ->
            require(src is RestoreSource.SafUri)
            val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(src.uri) }
                ?: return@runRestorePreview Result.failure(IllegalArgumentException(context.getString(R.string.backup_read_failed)))
            input.use { backupManager.preview(it, pw) }
        }
    }

    /** 从本机历史文件恢复：先预览。 */
    fun startRestoreLocal(file: File) {
        runRestorePreview(RestoreSource.LocalFile(file), "") { src ->
            require(src is RestoreSource.LocalFile)
            FileInputStream(src.file).use { backupManager.preview(it, null) }
        }
    }

    /** 从外部历史条目恢复：先预览。 */
    fun startRestoreExternal(item: ExternalBackupStore.Item) {
        runRestorePreview(RestoreSource.External(item), "") { src ->
            require(src is RestoreSource.External)
            val pw = autoBackupManager.signaturePassword()
                ?: return@runRestorePreview Result.failure(IllegalArgumentException(context.getString(R.string.backup_external_key_failed)))
            val input = autoBackupManager.openExternalInput(item)
                ?: return@runRestorePreview Result.failure(IllegalArgumentException(context.getString(R.string.backup_external_read_failed)))
            input.use { backupManager.preview(it, pw) }
        }
    }

    private fun runRestorePreview(
        source: RestoreSource,
        password: String,
        block: suspend (RestoreSource) -> Result<BackupPreview>,
    ) {
        _state.value = BackupState.Working(label = context.getString(R.string.backup_previewing))
        workingJob = viewModelScope.launch {
            runCatching { block(source) }
                .onSuccess { result ->
                    result
                        .onSuccess { preview -> _state.value = BackupState.PreviewReady(preview, source, password) }
                        .onFailure { e -> _state.value = BackupState.Error(describeImportError(e)) }
                }
                .onFailure { e -> _state.value = BackupState.Error(describeImportError(e)) }
        }
    }

    /** 用户在预览对话框选定模式后：先写安全点，再真正恢复。 */
    fun confirmRestore(mode: RestoreMode) {
        val s = _state.value as? BackupState.PreviewReady ?: return
        _state.value = BackupState.Working(label = context.getString(R.string.backup_restoring))
        workingJob = viewModelScope.launch {
            // P0：恢复前安全点。
            lastSafetyPoint = autoBackupManager.createSafetyPoint()
            val result = when (val src = s.source) {
                is RestoreSource.SafUri -> {
                    val pw = s.password.toCharArray().takeIf { it.isNotEmpty() }
                    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(src.uri) }
                        ?.use { backupManager.import(it, pw, mode) }
                        ?: Result.failure(IllegalArgumentException(context.getString(R.string.backup_read_failed)))
                }
                is RestoreSource.LocalFile ->
                    FileInputStream(src.file).use { backupManager.import(it, null, mode) }
                is RestoreSource.External ->
                    autoBackupManager.restoreExternal(src.item, mode)
            }
            onRestoreResult(result)
        }
    }

    /** 撤销本次恢复：用恢复前自动写的安全点，以 OVERWRITE 模式整包回滚。 */
    fun undoRestore() {
        val safety = lastSafetyPoint ?: run {
            _state.value = BackupState.Error(context.getString(R.string.backup_undo_unavailable))
            return
        }
        _state.value = BackupState.Working(label = context.getString(R.string.backup_undoing))
        workingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileInputStream(safety).use { backupManager.import(it, null, RestoreMode.OVERWRITE) }
            }
            result.onSuccess {
                lastSafetyPoint = null
                showSnackbar(context.getString(R.string.backup_undone))
            }.onFailure { e ->
                _state.value = BackupState.Error(e.message ?: context.getString(R.string.backup_undo_failed))
            }
            refreshDataSafety()
        }
    }

    private fun onRestoreResult(result: Result<RestoreStats>) {
        result.onSuccess { stats ->
            _state.value = BackupState.ImportSuccess(stats, canUndo = lastSafetyPoint != null)
            refreshDataSafety()
        }.onFailure { e ->
            _state.value = BackupState.Error(describeImportError(e))
        }
    }

    private fun describeImportError(e: Throwable): String = when (e) {
        is BackupDecryptionException -> e.message ?: context.getString(R.string.backup_wrong_password)
        else -> e.message ?: context.getString(R.string.backup_import_failed)
    }

    fun cancelWorking() {
        workingJob?.cancel()
        workingJob = null
        _state.value = BackupState.Idle
    }

    fun reset() {
        _state.value = BackupState.Idle
    }

    fun snackbarConsumed() {
        _dataSafety.update { it.copy(snackbar = null) }
    }

    private fun showSnackbar(msg: String) {
        _dataSafety.update { it.copy(snackbar = msg) }
    }

    /** 从历史列表项恢复：按来源找到对应 File/外部条目后进入预览。 */
    fun restoreFromHistory(item: BackupHistoryItem) {
        when (item.source) {
            BackupSource.LOCAL -> {
                val file = autoBackupManager.backups().firstOrNull { it.name == item.fileName } ?: return
                startRestoreLocal(file)
            }
            BackupSource.EXTERNAL -> {
                val ext = autoBackupManager.externalBackups().firstOrNull { it.name == item.fileName } ?: return
                startRestoreExternal(ext)
            }
        }
    }

    /** 从最近一份本机备份恢复（数据丢失告警入口）。 */
    fun restoreLatestLocal() {
        val file = autoBackupManager.latestBackup() ?: run {
            _state.value = BackupState.Error(context.getString(R.string.backup_auto_none))
            return
        }
        startRestoreLocal(file)
    }

    /** 从外部历史条目恢复（供 UI 回调）。 */
    fun startRestoreExternalItem(item: BackupHistoryItem) {
        val ext = autoBackupManager.externalBackups().firstOrNull { it.name == item.fileName } ?: return
        startRestoreExternal(ext)
    }

    // ── 历史：删除 / 清理 / 验证 ──────────────────────────────

    fun deleteHistory(item: BackupHistoryItem) {
        viewModelScope.launch {
            when (item.source) {
                BackupSource.LOCAL -> {
                    autoBackupManager.backups().firstOrNull { it.name == item.fileName }?.let {
                        autoBackupManager.deleteLocal(it)
                    }
                }
                BackupSource.EXTERNAL -> {
                    autoBackupManager.externalBackups().firstOrNull { it.name == item.fileName }?.let {
                        autoBackupManager.deleteExternal(it)
                    }
                }
            }
            refreshDataSafety()
        }
    }

    fun clearExcessLocal() {
        viewModelScope.launch {
            val freed = autoBackupManager.clearExcessLocalBackups()
            showSnackbar(context.getString(R.string.backup_cleared_bytes, formatBytes(freed)))
            refreshDataSafety()
        }
    }

    /** 分享一份本机备份文件（通过 FileProvider 授权 Uri 发送）。外部条目由系统 Downloads 直接可见。 */
    fun shareHistory(item: BackupHistoryItem) {
        if (item.source != BackupSource.LOCAL) return
        val file = autoBackupManager.backups().firstOrNull { it.name == item.fileName } ?: return
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gzip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, item.fileName)
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onFailure { showSnackbar(context.getString(R.string.backup_export_failed)) }
    }

    fun verifyHistory(item: BackupHistoryItem) {
        viewModelScope.launch {
            val ok = when (item.source) {
                BackupSource.LOCAL -> autoBackupManager.backups().firstOrNull { it.name == item.fileName }
                    ?.let { f -> runCatching { FileInputStream(f).use { backupManager.verifyBackup(it, null) }.getOrThrow().valid }.getOrDefault(false) }
                    ?: false
                BackupSource.EXTERNAL -> {
                    autoBackupManager.externalBackups().firstOrNull { it.name == item.fileName }?.let { ext ->
                        val pw = autoBackupManager.signaturePassword()
                        if (pw == null) false
                        else runCatching {
                            autoBackupManager.openExternalInput(ext)?.use { backupManager.verifyBackup(it, pw) }?.getOrThrow()?.valid
                        }.getOrDefault(false) ?: false
                    } ?: false
                }
            }
            showSnackbar(
                if (ok) context.getString(R.string.backup_verify_ok, item.fileName)
                else context.getString(R.string.backup_verify_bad, item.fileName)
            )
        }
    }

    // ── 自动备份配置 ──────────────────────────────────────────

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { autoBackupManager.setEnabled(enabled); refreshDataSafety() }
    }

    fun setKeepMax(keepMax: Int) {
        viewModelScope.launch { autoBackupManager.setKeepMax(keepMax); refreshDataSafety() }
    }

    fun setIntervalDays(days: Int) {
        viewModelScope.launch { autoBackupManager.setIntervalDays(days); refreshDataSafety() }
    }

    fun setBackupOnUpgrade(enabled: Boolean) {
        viewModelScope.launch { autoBackupManager.setBackupOnUpgrade(enabled); refreshDataSafety() }
    }

    // ── 立即备份 / 恢复本机最近一份 ───────────────────────────

    fun refreshDataSafety() {
        viewModelScope.launch {
            val verdict = dataSentinel.check()
            val history = autoBackupManager.history()
            val cfg = autoBackupManager.config()
            _dataSafety.value = DataSafetyUiState(
                verdict = verdict,
                lastBackupTime = autoBackupManager.lastBackupTime(),
                backupCount = autoBackupManager.backups().size,
                localStorageBytes = autoBackupManager.localStorageBytes(),
                lastExternalBackupTime = autoBackupManager.lastExternalBackupTime(),
                externalBackupCount = autoBackupManager.externalBackups().size,
                externalAvailable = autoBackupManager.externalAvailable(),
                history = history,
                config = cfg,
                snackbar = _dataSafety.value.snackbar,
            )
        }
    }

    fun backupNow() {
        _dataSafety.update { it.copy(working = true) }
        viewModelScope.launch {
            val ok = autoBackupManager.backupNow()
            _dataSafety.update { it.copy(working = false, justBackedUp = ok) }
            refreshDataSafety()
            if (ok) showSnackbar(context.getString(R.string.backup_auto_backed_up))
            else _state.value = BackupState.Error(context.getString(R.string.backup_auto_failed))
        }
    }

    fun backupToExternal() {
        if (!autoBackupManager.externalAvailable()) {
            _state.value = BackupState.Error(context.getString(R.string.backup_external_unavailable))
            return
        }
        _dataSafety.update { it.copy(working = true) }
        viewModelScope.launch {
            val ok = autoBackupManager.backupToExternal()
            _dataSafety.update { it.copy(working = false) }
            refreshDataSafety()
            if (ok) showSnackbar(context.getString(R.string.backup_external_backed_up))
            else _state.value = BackupState.Error(context.getString(R.string.backup_external_failed))
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unit = 0
            while (size >= 1024 && unit < units.size - 1) { size /= 1024.0; unit++ }
            return String.format(java.util.Locale.getDefault(), "%.1f %s", size, units[unit])
        }
    }
}
