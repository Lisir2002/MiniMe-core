package com.mini.me_core.feature.agent.domain.plan

import com.mini.me_core.feature.agent.data.local.entity.PlanEntity

/** 计划（Plan）数据端口：domain 定义，data 实现，不感知 SQLDelight 类型。 */
interface PlanPort {
    suspend fun getLatest(sessionId: String): PlanEntity?
    suspend fun getById(planId: String): PlanEntity?
    suspend fun upsert(plan: PlanEntity)
    suspend fun propose(
        sessionId: String, planId: String, title: String, steps: String,
        status: String, pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    )
    suspend fun updateContent(
        planId: String, status: String, steps: String, pendingSelection: String, updatedAtMs: Long,
    )
}
