package com.mini.me_core.core.migration

import android.content.Context
import com.mini.me_core.core.util.FileLogger

/**
 * 启动期数据迁移编排器。
 *
 * 按固定顺序执行所有已注册的 [MigrationTask]：
 *  - **快速路径**：任务完成标记已存在 → 直接跳过，只打一条 Verbose（后续启动零开销）；
 *  - **实际迁移**：才打一条 Info 汇总，任务内部细节自行降级 Verbose/Debug；
 *  - 单个任务失败不影响其余任务，也不阻塞启动（任务内部已 runCatching 兜底）。
 *
 * 完成标记存储：本编排器在数据库打开**之前**运行（任务需先于 DB 访问改名文件），
 * 此时 KVStore（依赖 InfraDb）尚不可用，故沿用本进程既有的预 DB 持久化通道——
 * 私有 [SharedPreferences]（与 CrashRecovery 的 MigrationStateStore 同一思路）。
 * 标记一旦写入，后续启动恒为快速路径。
 *
 * 调用点：[com.mini.me_core.MiniMeCore.onCreate] 数据层阶段最开头，
 * 早于 crashRecovery / connectionPool.driver(...)。
 */
object MigrationRunner {

    private const val TAG = "Migration"
    private const val PREFS = "minime_startup_migration"

    /**
     * 启动时按序执行全部迁移任务。
     *
     * @param context Application Context
     * @param tasks   按期望执行顺序排列的任务列表（历史任务在前）
     */
    fun runAll(context: Context, tasks: List<MigrationTask>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var ranCount = 0
        var skippedCount = 0
        var failedCount = 0

        for (task in tasks) {
            val doneKey = "done_${task.id}"
            if (prefs.getBoolean(doneKey, false)) {
                // 快速路径：已完成，零开销跳过，只留一条 Verbose 痕迹。
                FileLogger.v(TAG, "迁移[${task.id}]已完成，跳过")
                skippedCount++
                continue
            }

            FileLogger.i(TAG, "开始迁移: ${task.title} [${task.id}]")
            // execute 内部应自行幂等 + runCatching；这里再兜一层，确保单任务异常不炸启动。
            val outcome = runCatching { task.execute(context) }
            val actuallyRan = outcome.getOrDefault(false)

            if (outcome.isFailure) {
                // 任务向外抛了异常（理论上任务内部已吞掉）：记失败，不写完成标记，下次启动重试。
                FileLogger.w(TAG, "迁移[${task.id}]异常（不阻塞启动，下次启动重试）",
                    outcome.exceptionOrNull())
                failedCount++
            } else {
                // 正常返回：无论实际搬迁与否，都标记完成。任务幂等，检测廉价，
                // 避免每次启动都重跑文件探测。
                prefs.edit().putBoolean(doneKey, true).apply()
                if (actuallyRan) {
                    FileLogger.i(TAG, "迁移完成: ${task.title} [${task.id}]")
                    ranCount++
                } else {
                    FileLogger.v(TAG, "迁移[${task.id}]无需执行（标记完成）")
                    skippedCount++
                }
            }
        }

        // 汇总：全部命中快速路径/无需执行时，只打一条 Verbose；有实际迁移才打 Info。
        if (ranCount == 0) {
            FileLogger.v(TAG, "迁移检查完成：$skippedCount 个任务均已完成/无需执行，零开销跳过")
        } else {
            FileLogger.i(TAG, "迁移检查完成：实际执行 $ranCount 个，跳过 $skippedCount 个，失败 $failedCount 个")
        }
    }
}
