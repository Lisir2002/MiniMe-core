package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.model.MiniMeLog
import com.mini.me_core.datalayer.engine.LibName

/**
 * 数据库启动健康守卫（ARC-07 闭环）。
 *
 * 每个库打开后：
 *  1. `PRAGMA quick_check` 自检；
 *  2. 返回 "ok" → 重置失败计数；
 *  3. 非 "ok" → 计数 +1，触发 SchemaSelfHealer 泛化自愈；
 *  4. 连续失败达 [MAX_FAILURES] 次 → 自动 [MigrationEngine.restoreSnapshot] 回退
 *     （替换旧实现里 runCatching 静默忽略的隐患）。
 *
 * 覆盖全部 4 个物理库（AgentDb / CredentialsDb / WorkspaceDb / AuxDb）。
 * 每次 healing 打日志，不静默。
 */
object DatabaseHealthGuard {
    private const val TAG = "DatabaseHealthGuard"
    private const val MAX_FAILURES = 3

    private val failures = HashMap<LibName, Int>()

    /** 返回 true 表示健康（quick_check=ok）。 */
    fun onOpened(lib: LibName, driver: SqlDriver, engine: MigrationEngine): Boolean {
        val result = SchemaSelfHealer.quickCheck(driver)
        if (result == "ok") {
            failures.remove(lib)
            MiniMeLog.i(TAG, "$lib quick_check 通过")
            return true
        }
        val count = (failures[lib] ?: 0) + 1
        failures[lib] = count
        MiniMeLog.e(TAG, "$lib quick_check 失败($count/$MAX_FAILURES): $result，触发自愈")
        runCatching {
            // 泛化自愈：枚举本库全部表，PRAGMA table_info 比对/缺列补齐。
            SchemaSelfHealer.healAllExistingTables(driver)
        }.onFailure { MiniMeLog.e(TAG, "$lib 自愈失败", it) }

        if (count >= MAX_FAILURES) {
            MiniMeLog.e(TAG, "$lib 连续 $MAX_FAILURES 次 quick_check 失败，自动 restoreSnapshot")
            val restored = runCatching { engine.restoreSnapshot(lib) }.getOrDefault(false)
            MiniMeLog.e(TAG, "$lib restoreSnapshot 结果=$restored")
            failures.remove(lib)
        }
        return false
    }
}
