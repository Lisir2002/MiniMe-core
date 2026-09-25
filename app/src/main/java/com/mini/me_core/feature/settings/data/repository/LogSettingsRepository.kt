package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持久化「日志最低记录等级」与「诊断模式」。等级以枚举名（字符串）存取。
 *
 * 智能分层日志：
 *  - 正常启动只输出阶段汇总（Info），细节降级 Verbose/Debug；
 *  - [isDiagnosticMode] 打开后，各模块可据此输出上下文详情（预留接口，暂未接 UI）。
 */
@Singleton
class LogSettingsRepository @Inject constructor(
    private val kv: KVStore
) {
    private companion object {
        const val NS = "settings"
        const val LEVEL_KEY = "log_min_level"
        const val DIAGNOSTIC_KEY = "log_diagnostic_mode"
        val DEFAULT_LEVEL = LogLevel.VERBOSE
    }

    val levelFlow: Flow<LogLevel> = kv.observeString(NS, LEVEL_KEY).map { stored ->
        stored?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: DEFAULT_LEVEL
    }

    suspend fun setLevel(level: LogLevel) {
        kv.putString(NS, LEVEL_KEY, level.name)
    }

    suspend fun snapshot(): String = levelFlow.first().name

    suspend fun restore(value: String?) {
        val level = value?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: return
        setLevel(level)
    }

    // ── 诊断模式（预留接口，暂未接设置页 UI）──────────────────────────────
    // 开启后：各模块在出现 W/E 时输出上下文详情日志，正常启动也可临时降到 VERBOSE 排查。
    // 当前默认关闭——正常启动保持「只输出阶段汇总」的安静行为。

    /** 诊断模式是否开启（响应式）。默认 false。 */
    val diagnosticModeFlow: Flow<Boolean> =
        kv.observeBool(NS, DIAGNOSTIC_KEY).map { it ?: false }

    /** 一次性读取诊断模式开关。默认 false。 */
    suspend fun isDiagnosticMode(): Boolean = kv.getBool(NS, DIAGNOSTIC_KEY) ?: false

    /** 设置诊断模式开关（预留：未来设置页 UI 接入时调用）。 */
    suspend fun setDiagnosticMode(enabled: Boolean) {
        kv.putBool(NS, DIAGNOSTIC_KEY, enabled)
    }
}
