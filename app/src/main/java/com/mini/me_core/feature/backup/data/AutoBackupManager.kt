package com.mini.me_core.feature.backup.data

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.R
import com.mini.me_core.feature.backup.domain.AutoBackupConfig
import com.mini.me_core.feature.backup.domain.BackupHistoryItem
import com.mini.me_core.feature.backup.domain.BackupManager
import com.mini.me_core.feature.backup.domain.BackupMetadata
import com.mini.me_core.feature.backup.domain.BackupOptions
import com.mini.me_core.feature.backup.domain.BackupCrypto
import com.mini.me_core.feature.backup.domain.BackupSource
import com.mini.me_core.feature.backup.domain.isPeriodicBackupDue
import com.mini.me_core.feature.backup.domain.RestoreMode
import com.mini.me_core.feature.backup.domain.RestoreStats
import com.mini.me_core.feature.backup.domain.BackupIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动备份（数据安全网）：本机私有目录明文备份 + 外部公共目录加密备份双保险。
 *
 * 增强能力（v0.0.0.21 重构）：
 * - 配置可持久化（[BackupConfigStore]）：总开关/保留份数/周期间隔/升级前开关；
 * - 导出后完整性自检（[BackupManager.verifyBackup]），损坏自动重试一次，坏备份不进轮转；
 * - 历史列表统一本机/外部（[history]），支持任选一份恢复/删除；
 * - 任何恢复前自动写一份「安全点」备份（[createSafetyPoint]），结果可撤销；
 * - 周期性备份到期判定（[runPeriodicIfDue]，启动时调用）。
 *
 * 安全说明：外部公共目录其他应用可读，外部备份一律用签名派生密钥加密；本机私有备份保持明文。
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val externalBackupStore: ExternalBackupStore,
    private val signatureKeyStore: SignatureKeyStore,
    private val configStore: BackupConfigStore,
) {
    private companion object {
        const val TAG = "AutoBackup"
        const val PREFIX = "backup-"
        const val SUFFIX = ".tar.gz"
        const val SAFETY_PREFIX = "safety-"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun autoBackupDir(): File = File(context.filesDir, "auto-backups")

    /** 当前自动备份配置（同步快照，UI 可直接读）。 */
    suspend fun config(): AutoBackupConfig = configStore.snapshot()

    /** 配置响应式流。 */
    fun configFlow(): Flow<AutoBackupConfig> = configStore.config

    suspend fun setEnabled(enabled: Boolean) = configStore.setEnabled(enabled)
    suspend fun setKeepMax(keepMax: Int) = configStore.setKeepMax(keepMax)
    suspend fun setIntervalDays(days: Int) = configStore.setIntervalDays(days)
    suspend fun setBackupOnUpgrade(enabled: Boolean) = configStore.setBackupOnUpgrade(enabled)

    /**
     * 立即全量备份到私有目录。导出后重新打开做完整性自检，损坏自动重试一次；
     * 仍失败则不保留坏文件（不进轮转）。成功返回 true。
     */
    suspend fun backupNow(): Boolean = withContext(Dispatchers.IO) {
        if (!configStore.snapshot().enabled) {
            FileLogger.d(TAG, "自动备份已关闭，跳过")
            return@withContext false
        }
        runCatching {
            autoBackupDir().mkdirs()
            val file = File(autoBackupDir(), "$PREFIX${System.currentTimeMillis()}$SUFFIX")
            var attempt = 0
            var ok = false
            while (attempt < 2 && !ok) {
                attempt++
                FileOutputStream(file).use { backupManager.export(null, BackupOptions(), it) }
                val integrity = verifyLocalFile(file)
                if (integrity.isSuccess && integrity.getOrThrow().valid) {
                    ok = true
                } else {
                    FileLogger.w(TAG, "备份完整性自检未通过（第 $attempt 次），重写: ${integrity.exceptionOrNull()?.message}")
                    file.delete()
                }
            }
            if (!ok) {
                FileLogger.e(TAG, "备份两次均未通过完整性自检，放弃该份备份")
                return@withContext false
            }
            pruneLocked()
            FileLogger.i(TAG, "自动备份完成: ${file.name} (${file.length()} bytes)")
            true
        }.onFailure {
            FileLogger.e(TAG, "自动备份失败", it)
            false
        }.getOrDefault(false)
    }

    /** 对本机文件做完整性校验（解密=无，直接开 tar）。 */
    private suspend fun verifyLocalFile(file: File): Result<BackupIntegrity> =
        FileInputStream(file).use { backupManager.verifyBackup(it, null) }

    /** 备份全部落点：私有明文 + 外部加密（尽力而为，任一步失败不影响整体返回）。 */
    suspend fun backupAll(): Boolean {
        val local = backupNow()
        val external = backupToExternal()
        return local || external
    }

    /**
     * 把最近一份本机明文备份加密后写入外部公共目录。
     * 外部不可用/签名密钥失败时返回 false（调用方应据此引导授权，不静默）。
     */
    suspend fun backupToExternal(): Boolean = withContext(Dispatchers.IO) {
        if (!externalBackupStore.isAvailable()) {
            FileLogger.d(TAG, "外部备份跳过：公共存储不可用（未授权或未挂载）")
            return@withContext false
        }
        val password = signatureKeyStore.signaturePassword()
            ?: run { FileLogger.w(TAG, "外部备份跳过：签名密钥派生失败"); return@withContext false }
        runCatching {
            var src = latestBackup()
            if (src == null) {
                backupNow()
                src = latestBackup()
            }
            val source = src ?: return@withContext false
            val ok = externalBackupStore.write { out ->
                FileInputStream(source).use { BackupCrypto.encryptStream(it, out, password) }
            }
            if (ok) pruneExternalLocked()
            FileLogger.i(TAG, "外部加密备份完成（基于 ${source.name}）")
            ok
        }.onFailure {
            FileLogger.e(TAG, "外部加密备份失败", it)
            false
        }.getOrDefault(false)
    }

    /** 私有目录下全部自动备份（按文件名时间戳降序，最新的在前）。安全点单独排在 history 里。 */
    fun backups(): List<File> = autoBackupDir()
        .listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
        ?.sortedByDescending { epochOf(it) }
        ?.toList()
        ?: emptyList()

    /** 最近一份自动备份文件；无则 null。 */
    fun latestBackup(): File? = backups().firstOrNull()

    /** 最近一次自动备份时间（epoch ms）；无则 null。 */
    fun lastBackupTime(): Long? = latestBackup()?.let { epochOf(it) }

    /** 本机自动备份总占用字节。 */
    fun localStorageBytes(): Long = backups().sumOf { it.length() }

    private fun epochOf(file: File): Long =
        file.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: file.lastModified()

    /** 轮转：超过配置保留份数时删除最旧的。 */
    private suspend fun pruneLocked() {
        val keepMax = runCatching { configStore.snapshot().keepMax }.getOrDefault(7)
        val files = backups()
        excessBackupFiles(files, keepMax).forEach { runCatching { it.delete() } }
    }

    /** 一键清理：删除除最新一份之外的全部本机自动备份。返回释放字节数。 */
    suspend fun clearExcessLocalBackups(): Long = withContext(Dispatchers.IO) {
        val files = backups()
        if (files.size <= 1) return@withContext 0L
        var freed = 0L
        files.drop(1).forEach { freed += it.length(); runCatching { it.delete() } }
        freed
    }

    // ── 历史列表（本机 + 外部统一） ──────────────────────────────────

    /** 合并后的备份历史（最新在前）。本机项解析 metadata 得到内容概要；外部项概要为 null。 */
    suspend fun history(): List<BackupHistoryItem> = withContext(Dispatchers.IO) {
        val local = backups().map { f ->
            BackupHistoryItem(
                epochMs = epochOf(f),
                sizeBytes = f.length(),
                source = BackupSource.LOCAL,
                fileName = f.name,
                stats = parseLocalMetadata(f),
            )
        }
        val ext = externalBackups().map { item ->
            BackupHistoryItem(
                epochMs = item.epochMs,
                sizeBytes = externalSizeOf(item),
                source = BackupSource.EXTERNAL,
                fileName = item.name,
                stats = null,
            )
        }
        (local + ext).sortedByDescending { it.epochMs }
    }

    private fun externalSizeOf(item: ExternalBackupStore.Item): Long = runCatching {
        externalBackupStore.openInput(item)?.use { it.available().toLong() } ?: 0L
    }.getOrDefault(0L)

    /** 打开本机 tar.gz 只读 metadata.json（小表条数）+ 粗略统计 jsonl 行数。 */
    private fun parseLocalMetadata(file: File): RestoreStats? = runCatching {
        var meta: BackupMetadata? = null
        var sessions = 0
        var messages = 0
        var todos = 0
        FileInputStream(file).use { fis ->
            GzipCompressorInputStream(BufferedInputStream(fis)).use { gz ->
                TarArchiveInputStream(gz).use { tar ->
                    var e = tar.nextEntry
                    while (e != null) {
                        when (e.name) {
                            "metadata.json" -> {
                                meta = json.decodeFromString(BackupMetadata.serializer(), tar.readBytes().toString(Charsets.UTF_8))
                            }
                            "chatSessions.jsonl" -> sessions = countLines(tar)
                            "messages.jsonl" -> messages = countLines(tar)
                            "todoItems.jsonl" -> todos = countLines(tar)
                            else -> tar.readBytes()
                        }
                        e = tar.nextEntry
                    }
                }
            }
        }
        meta?.let { m ->
            RestoreStats(
                providers = m.providers.size,
                gitCredentials = m.gitCredentials.size,
                remoteConnections = m.remoteConnections.size,
                remoteMounts = m.remoteMounts.size,
                chatSessions = sessions,
                agentMessages = messages,
                todoItems = todos,
                mcpServers = m.mcpServers.size,
                globalPermissionRules = m.globalPermissionRules.size,
            )
        }
    }.getOrNull()

    private fun countLines(tar: TarArchiveInputStream): Int {
        val bytes = tar.readBytes()
        if (bytes.isEmpty()) return 0
        var count = 0
        bytes.forEach { if (it == '\n'.code.toByte()) count++ }
        return count
    }

    // ── 外部公共目录安全网 ──────────────────────────────────────────

    fun externalBackups(): List<ExternalBackupStore.Item> = externalBackupStore.list()

    fun latestExternalBackup(): ExternalBackupStore.Item? = externalBackups().firstOrNull()

    fun lastExternalBackupTime(): Long? = latestExternalBackup()?.epochMs

    fun externalAvailable(): Boolean = externalBackupStore.isAvailable()

    private suspend fun pruneExternalLocked() {
        val keepMax = runCatching { configStore.snapshot().keepMax }.getOrDefault(7)
        excessExternalBackups(externalBackups(), keepMax).forEach { externalBackupStore.delete(it) }
    }

    /** 从指定外部条目恢复（签名密钥解密）。 */
    suspend fun restoreExternal(item: ExternalBackupStore.Item, mode: RestoreMode): Result<RestoreStats> = withContext(Dispatchers.IO) {
        val password = signatureKeyStore.signaturePassword()
            ?: return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.backup_external_key_failed)))
        runCatching {
            val input = externalBackupStore.openInput(item)
                ?: throw IllegalArgumentException(context.getString(R.string.backup_external_read_failed))
            input.use { backupManager.import(it, password, mode) }.getOrThrow()
        }
    }

    /** 从最近一份外部加密备份恢复。 */
    suspend fun restoreFromLatestExternal(mode: RestoreMode = RestoreMode.MERGE): Result<RestoreStats> {
        val item = latestExternalBackup()
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.backup_external_none)))
        return restoreExternal(item, mode)
    }

    /** 删除一份外部备份。 */
    fun deleteExternal(item: ExternalBackupStore.Item) = externalBackupStore.delete(item)

    /** 删除一份本机备份文件。 */
    fun deleteLocal(file: File) = runCatching { file.delete() }.getOrDefault(false)

    /** 暴露签名派生口令（供 ViewModel 做预览/验证外部备份）。 */
    fun signaturePassword(): CharArray? = signatureKeyStore.signaturePassword()

    /** 打开外部备份读取流（供 ViewModel 做预览/验证）。 */
    fun openExternalInput(item: ExternalBackupStore.Item): java.io.InputStream? =
        externalBackupStore.openInput(item)

    // ── 恢复前安全点（撤销点） ──────────────────────────────────────

    /**
     * 恢复前安全点：立即把当前状态写一份本机明文备份，文件名带 safety- 前缀。
     * 该份备份不参与轮转（pruneLocked 只删 PREFIX 开头且非 safety 的文件——见 history 标记）。
     * 失败仅记日志，不阻断恢复主流程。
     */
    suspend fun createSafetyPoint(): File? = withContext(Dispatchers.IO) {
        runCatching {
            autoBackupDir().mkdirs()
            val file = File(autoBackupDir(), "$SAFETY_PREFIX${System.currentTimeMillis()}$SUFFIX")
            FileOutputStream(file).use { backupManager.export(null, BackupOptions(), it) }
            FileLogger.i(TAG, "恢复前安全点已写入: ${file.name}")
            file
        }.onFailure { FileLogger.w(TAG, "恢复前安全点写入失败（不阻断恢复）", it) }.getOrNull()
    }

    // ── 周期性备份（启动时检查是否到期） ────────────────────────────

    /**
     * 启动时检查周期性备份是否到期：开关开启且距上次周期备份超过 intervalDays 天则执行一次。
     * 到期后更新 lastPeriodicBackupMs。
     */
    suspend fun runPeriodicIfDue(nowMs: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val cfg = configStore.snapshot()
        if (!cfg.enabled) return@withContext false
        if (!isPeriodicBackupDue(cfg.lastPeriodicBackupMs, cfg.intervalDays, nowMs)) return@withContext false
        val ok = backupNow()
        if (ok) configStore.setLastPeriodicBackup(nowMs)
        ok
    }
}

/**
 * 自动备份轮转的**纯判定逻辑**（无 Android/IO 依赖，便于单元测试，见 D10）。
 * 输入按时间戳降序（最新在前）；返回超出 [keepMax] 份的最旧文件（应删除）。
 */
internal fun excessBackupFiles(sortedNewestFirst: List<File>, keepMax: Int): List<File> =
    if (sortedNewestFirst.size <= keepMax) emptyList() else sortedNewestFirst.drop(keepMax)

/** 外部备份轮转的**纯判定逻辑**。 */
internal fun excessExternalBackups(
    sortedNewestFirst: List<ExternalBackupStore.Item>,
    keepMax: Int,
): List<ExternalBackupStore.Item> =
    if (sortedNewestFirst.size <= keepMax) emptyList() else sortedNewestFirst.drop(keepMax)
