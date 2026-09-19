package com.mini.me_core.feature.agent.domain.goal

import com.mini.me_core.feature.agent.data.local.entity.GoalEntity

/**
 * 目标（Goal）数据端口：domain 定义，data 实现。
 * domain 不感知 SQLDelight 生成类型；事务原语（激活/CAS）由适配器内部完成。
 */
interface GoalPort {
    suspend fun getActive(sessionId: String): GoalEntity?
    suspend fun getById(goalId: String): GoalEntity?
    /** 事务内：旧 ACTIVE 置 ABANDONED + 插入新目标。 */
    suspend fun activate(
        sessionId: String, goalId: String, text: String, status: String,
        revision: Long, parentGoalId: String, roundSeq: Long,
        createdAtMs: Long, updatedAtMs: Long,
    )
    /** CAS 更新状态与文本，返回受影响行数。 */
    suspend fun casUpdateStatusAndText(
        goalId: String, status: String, text: String,
        newRevision: Long, expectedRevision: Long, updatedAtMs: Long,
    ): Long
}
