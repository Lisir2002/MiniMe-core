package com.mini.me_core.feature.backup.data

import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.backup.domain.AutoBackupConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动备份配置的 KVStore 持久化（重启不丢）。
 *
 * 命名空间 [NS] 下保存：总开关、保留份数、周期间隔天数、升级前备份开关、上次周期备份时间。
 * 读取侧宽容：未配置时回落到 [AutoBackupConfig] 默认值。
 */
@Singleton
class BackupConfigStore @Inject constructor(
    private val kv: KVStore,
) {
    private companion object {
        const val NS = "auto_backup_config"
        const val KEY_ENABLED = "enabled"
        const val KEY_KEEP_MAX = "keep_max"
        const val KEY_INTERVAL_DAYS = "interval_days"
        const val KEY_BACKUP_ON_UPGRADE = "backup_on_upgrade"
        const val KEY_LAST_PERIODIC_MS = "last_periodic_ms"
    }

    val config: Flow<AutoBackupConfig> = kv.observeBool(NS, KEY_ENABLED).map { _ -> snapshot() }

    suspend fun snapshot(): AutoBackupConfig = AutoBackupConfig(
        enabled = kv.getBool(NS, KEY_ENABLED) ?: true,
        keepMax = (kv.getInt(NS, KEY_KEEP_MAX) ?: 7L).toInt(),
        intervalDays = (kv.getInt(NS, KEY_INTERVAL_DAYS) ?: 0L).toInt(),
        backupOnUpgrade = kv.getBool(NS, KEY_BACKUP_ON_UPGRADE) ?: true,
        lastPeriodicBackupMs = kv.getInt(NS, KEY_LAST_PERIODIC_MS) ?: 0L,
    )

    suspend fun setEnabled(enabled: Boolean) = kv.putBool(NS, KEY_ENABLED, enabled)

    suspend fun setKeepMax(keepMax: Int) = kv.putInt(NS, KEY_KEEP_MAX, keepMax.toLong())

    suspend fun setIntervalDays(days: Int) = kv.putInt(NS, KEY_INTERVAL_DAYS, days.toLong())

    suspend fun setBackupOnUpgrade(enabled: Boolean) = kv.putBool(NS, KEY_BACKUP_ON_UPGRADE, enabled)

    suspend fun setLastPeriodicBackup(nowMs: Long) = kv.putInt(NS, KEY_LAST_PERIODIC_MS, nowMs)
}
