package com.mini.me_core.feature.settings.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    private val _ignoredPatterns = MutableStateFlow(
        prefs.getString("ignored_patterns", ".git,node_modules,build,dist,.gradle,.idea,.cxx,.vscode,tmp")!!
    )
    val ignoredPatterns: StateFlow<String> = _ignoredPatterns.asStateFlow()

    private val _useGitIgnore = MutableStateFlow(
        prefs.getBoolean("use_gitignore", true)
    )
    val useGitIgnore: StateFlow<Boolean> = _useGitIgnore.asStateFlow()

    private val _maxSyncBatchSize = MutableStateFlow(
        prefs.getInt("max_sync_batch_size", 5)
    )
    val maxSyncBatchSize: StateFlow<Int> = _maxSyncBatchSize.asStateFlow()

    /** 全局冲突处理策略：remote_overwrite / local_overwrite / skip / rename。 */
    private val _conflictStrategy = MutableStateFlow(
        prefs.getString("conflict_strategy", "remote_overwrite")!!
    )
    val conflictStrategy: StateFlow<String> = _conflictStrategy.asStateFlow()

    /** 是否启用自动同步。 */
    private val _autoSyncEnabled = MutableStateFlow(
        prefs.getBoolean("auto_sync_enabled", false)
    )
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    /** 自动同步间隔（分钟）：5 / 15 / 30 / 60；0 表示仅手动。 */
    private val _autoSyncIntervalMinutes = MutableStateFlow(
        prefs.getInt("auto_sync_interval_minutes", 15)
    )
    val autoSyncIntervalMinutes: StateFlow<Int> = _autoSyncIntervalMinutes.asStateFlow()

    fun setIgnoredPatterns(patterns: String) {
        prefs.edit { putString("ignored_patterns", patterns) }
        _ignoredPatterns.value = patterns
    }

    fun setUseGitIgnore(use: Boolean) {
        prefs.edit { putBoolean("use_gitignore", use) }
        _useGitIgnore.value = use
    }

    fun setMaxSyncBatchSize(size: Int) {
        prefs.edit { putInt("max_sync_batch_size", size) }
        _maxSyncBatchSize.value = size
    }

    fun setConflictStrategy(strategy: String) {
        prefs.edit { putString("conflict_strategy", strategy) }
        _conflictStrategy.value = strategy
    }

    fun setAutoSync(enabled: Boolean, intervalMinutes: Int) {
        prefs.edit {
            putBoolean("auto_sync_enabled", enabled)
            putInt("auto_sync_interval_minutes", intervalMinutes)
        }
        _autoSyncEnabled.value = enabled
        _autoSyncIntervalMinutes.value = intervalMinutes
    }

    /** 备份快照：同步偏好全部键。 */
    fun snapshot(): SyncSettingsSnapshot = SyncSettingsSnapshot(
        ignoredPatterns = _ignoredPatterns.value,
        useGitIgnore = _useGitIgnore.value,
        maxSyncBatchSize = _maxSyncBatchSize.value,
        conflictStrategy = _conflictStrategy.value,
        autoSyncEnabled = _autoSyncEnabled.value,
        autoSyncIntervalMinutes = _autoSyncIntervalMinutes.value,
    )

    /** 从备份还原同步偏好。 */
    fun restore(snapshot: SyncSettingsSnapshot) {
        setIgnoredPatterns(snapshot.ignoredPatterns)
        setUseGitIgnore(snapshot.useGitIgnore)
        setMaxSyncBatchSize(snapshot.maxSyncBatchSize)
        setConflictStrategy(snapshot.conflictStrategy)
        setAutoSync(snapshot.autoSyncEnabled, snapshot.autoSyncIntervalMinutes)
    }
}

/** 同步偏好的可序列化快照。 */
@kotlinx.serialization.Serializable
data class SyncSettingsSnapshot(
    val ignoredPatterns: String,
    val useGitIgnore: Boolean,
    val maxSyncBatchSize: Int,
    val conflictStrategy: String = "remote_overwrite",
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalMinutes: Int = 15,
)
