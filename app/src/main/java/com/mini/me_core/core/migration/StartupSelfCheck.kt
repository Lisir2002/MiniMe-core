package com.mini.me_core.core.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.mecore.datalayer.sqldelight.AgentDb

/**
 * 启动自检 / 冒烟测试系统。
 *
 * 把历史上散落在启动入口里的「rc 诊断代码」升级为正式的结构化自检：
 * 在数据层预热完成后、任何业务 UI 查询之前，就地跑一遍与线上崩溃路径同构的检查，
 * 让「SQL 编译失败 / 缺列 / 库打不开」在启动阶段就暴露，而不是等用户进会话页才炸。
 *
 * 日志约定（智能分层）：
 *  - 单项通过 → [Debug] `[自检/数据层] xxx通过`（细节，正常启动不刷屏）；
 *  - 单项失败 → [Error] `[自检/数据层] xxx失败` + 堆栈（出问题才显眼）；
 *  - 汇总     → 全过 [Debug] `[自检] 全部通过（N项）`；有失败 [Error] `[自检] N项失败：xxx`。
 *
 * 所有检查 runCatching 包裹，绝不向上抛——自检失败只记录，不阻断启动
 * （真崩溃会在随后的业务查询里以明确栈暴露，这里只是提前留痕）。
 */
object StartupSelfCheck {

    private const val TAG = "自检"

    /**
     * 跑数据层全部自检项并直接输出结构化日志。
     *
     * @param driver AGENT 库已预热的 driver
     */
    fun runDataLayerChecks(driver: SqlDriver) {
        var passed = 0
        val failures = mutableListOf<String>()

        // ── 项 1：表结构校验（agent_message 关键列存在）──
        runCatching {
            val cols = mutableListOf<String>()
            driver.executeQuery(null, "PRAGMA table_info(agent_message)", { cursor ->
                while (cursor.next().value) {
                    cursor.getString(1)?.let { cols.add(it) }
                }
                QueryResult.Unit
            }, 0, null).value
            if (!cols.contains("id")) {
                throw IllegalStateException("agent_message 表缺少 id 列（实际列: ${cols.joinToString()}）")
            }
            FileLogger.d(TAG, "[自检/数据层] 表结构校验通过（agent_message 列: ${cols.size}）")
            passed++
        }.onFailure {
            FileLogger.e(TAG, "[自检/数据层] 表结构校验失败", it)
            failures.add("表结构")
        }

        // ── 项 2：SQL 冒烟测试（与线上崩溃路径 100% 同构的生成查询）──
        runCatching {
            AgentDb(driver).agentQueries
                .selectMessagesBySessionPaged("__startup_smoke__", 1L)
                .executeAsList()
            FileLogger.d(TAG, "[自检/数据层] SQL冒烟测试通过")
            passed++
        }.onFailure {
            FileLogger.e(TAG, "[自检/数据层] SQL冒烟测试失败（生成 SQL/列绑定有问题）", it)
            failures.add("SQL冒烟")
        }

        // ── 项 3：连接可用性（一条最轻量的 PRAGMA 往返，确认 driver 可查询）──
        runCatching {
            driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                cursor.next().value
                QueryResult.Unit
            }, 0, null).value
            FileLogger.d(TAG, "[自检/数据层] 连接可用性校验通过")
            passed++
        }.onFailure {
            FileLogger.e(TAG, "[自检/数据层] 连接可用性校验失败", it)
            failures.add("连接可用性")
        }

        // ── 汇总 ──
        if (failures.isEmpty()) {
            FileLogger.d(TAG, "[自检] 全部通过（$passed 项）")
        } else {
            FileLogger.e(TAG, "[自检] ${failures.size}项失败：${failures.joinToString("、")}")
        }
    }
}
