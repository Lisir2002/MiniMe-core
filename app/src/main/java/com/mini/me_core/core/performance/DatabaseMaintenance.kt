package com.mini.me_core.core.performance

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F6.5 数据库/存储定期维护：清理过期数据与临时文件。
 *
 * - 崩溃报告：CrashReporter 已自动保留最多 10 条，这里兜底再清理一次。
 * - 临时文件：清理 cacheDir 下超过 [TEMP_MAX_AGE_MS] 的文件（图片/网络缓存等）。
 * - 搜索历史 >30 天：KVStore 的 settings namespace 里按 updated_at 过期清理由各仓库自行负责，
 *   这里不做跨域删除，避免误删用户配置。
 *
 * 在 Application.onCreate 后台触发一次（幂等），不阻塞首帧。
 */
@Singleton
class DatabaseMaintenance @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val TEMP_MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)
        private const val TAG = "DatabaseMaintenance"
    }

    /** 执行一次维护。应在 IO 线程调用。 */
    fun run() {
        runCatching {
            cleanCrashReports()
            cleanTempCache()
        }.onFailure { FileLogger.w(TAG, "数据库维护任务失败（忽略）", it) }
    }

    private fun cleanCrashReports() {
        // CrashReporter 写时即裁剪到 10 条，这里兜底按数量再 trim 一次。
        val reports = CrashReporter.listReports()
        reports.drop(10).forEach { runCatching { it.delete() } }
        if (reports.size > 10) {
            FileLogger.d(TAG, "清理崩溃报告：删除 ${reports.size - 10} 条最旧记录")
        }
    }

    private fun cleanTempCache() {
        val now = System.currentTimeMillis()
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && now - f.lastModified() > TEMP_MAX_AGE_MS) {
                freed += f.length()
                runCatching { f.delete() }
            }
        }
        if (freed > 0) {
            FileLogger.d(TAG, "清理临时缓存：释放 ${freed / 1024} KB")
        }
    }
}
