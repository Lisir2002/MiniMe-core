package com.mini.me_core.core.util

import java.io.File

/**
 * 诊断产物统一清理入口（审计 F2 / M）。
 *
 * 背景：应用日志、AI 会话日志、崩溃报告分别由三处独立逻辑清理，且**日志类清理只在
 * [FileLogger.init] / 权限切换那一刻执行一次**——长驻后台的应用永远不再执行后续清理，
 * "保留 7 天"的天数规则实际上形同虚设。这里把三类产物的清理收敛到一处，由冷启动统一调用一次。
 *
 * 保留策略：
 *  - 应用日志 / AI 会话日志：早于 [LogConfig.maxAgeDays] 天的文件删除；
 *  - 崩溃报告：最多保留 [LogConfig.maxCrashReports] 条（最新的）。
 */
object DiagnosticCleanup {

    /**
     * 清理 [dir] 下超过 [LogConfig.maxAgeDays] 天的日志文件（`log-*` 与 `session-*`，含滚动件）。
     *
     * @param nameFilter 只清理满足该谓词的文件名；默认两类都清。
     */
    fun cleanupOldLogs(dir: File?, nameFilter: (String) -> Boolean = { true }) {
        if (dir == null || !dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - LogConfig.maxAgeDays * 24L * 60 * 60 * 1000
        dir.listFiles { f: File -> f.isFile && nameFilter(f.name) }?.forEach { file ->
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    /** 应用日志目录（`log-*.txt`）的过期清理。 */
    fun cleanupAppLogs(dir: File?) = cleanupOldLogs(dir) { it.startsWith(LogFiles.APP_LOG_PREFIX) && it.endsWith(".txt") }

    /** AI 会话日志目录（`session-*.log`）的过期清理。 */
    fun cleanupAiLogs(dir: File?) = cleanupOldLogs(dir) { it.startsWith(LogFiles.AI_LOG_PREFIX) && it.endsWith(".log") }

    /** 崩溃报告目录：只保留最新的 [LogConfig.maxCrashReports] 条。 */
    fun trimCrashReports(dir: File?) {
        if (dir == null || !dir.isDirectory) return
        val reports = dir.listFiles { f: File -> f.isFile && f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?: return
        reports.drop(LogConfig.maxCrashReports).forEach { runCatching { it.delete() } }
    }
}
