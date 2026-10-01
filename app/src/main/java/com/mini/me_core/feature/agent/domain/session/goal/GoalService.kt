package com.mini.me_core.feature.agent.domain.session.goal

import com.mini.me_core.core.util.EnumSafe
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.exception.DataLayerErrorCode
import com.mini.me_core.datalayer.exception.DataLayerException
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_goals as V2AgentGoal
import java.util.UUID
import javax.inject.Inject

/**
 * 会话级「任务目标」状态机服务（对齐 DSH GoalService 契约）。
 */
class GoalService @Inject constructor(
    private val v2Agent: V2AgentRepository,
) {

    private companion object {
        const val TAG = "GoalService"
        const val MAX_ACTIVATE_ATTEMPTS = 2
    }

    /** 读取会话当前 ACTIVE 目标；无则返回 null。 */
    suspend fun getActive(sessionId: String): V2AgentGoal? =
        v2Agent.getActiveGoalBySession(sessionId)

    /**
     * 激活新目标（会话内幂等替换）：事务内把旧 ACTIVE 目标置 ABANDONED，再插入新 ACTIVE 目标。
     */
    suspend fun activate(sessionId: String, text: String, roundSeq: Int = 0): V2AgentGoal {
        val now = System.currentTimeMillis()
        val goalId = UUID.randomUUID().toString()
        var lastError: DataLayerException? = null
        repeat(MAX_ACTIVATE_ATTEMPTS) { attempt ->
            try {
                v2Agent.runInTx { tx ->
                    tx.activateGoal(
                        sessionId = sessionId,
                        old = v2Agent.getActiveGoalBySessionBlocking(sessionId),
                        goalId = goalId,
                        text = text,
                        status = GoalStatus.ACTIVE.name,
                        revision = 0L,
                        parentGoalId = "",
                        roundSeq = roundSeq.toLong(),
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                }
                return V2AgentGoal(
                    goal_id = goalId, session_id = sessionId, text = text,
                    status = GoalStatus.ACTIVE.name, revision = 0L,
                    parent_goal_id = "", round_seq = roundSeq.toLong(),
                    created_at_ms = now, updated_at_ms = now
                )
            } catch (e: DataLayerException) {
                if (e.errorCode != DataLayerErrorCode.CONCURRENT_ACCESS) throw e
                lastError = e
                FileLogger.w(TAG, "activate($sessionId) CAS 冲突（并发改了 revision），重试 ${attempt + 1}/$MAX_ACTIVATE_ATTEMPTS")
            }
        }
        throw lastError ?: DataLayerException(
            "activate($sessionId) 失败（无异常信息）",
            DataLayerErrorCode.CONCURRENT_ACCESS,
        )
    }

    /** 按 id 读取目标。 */
    suspend fun getById(goalId: String): V2AgentGoal? =
        v2Agent.getGoalById(goalId)

    /**
     * CAS 更新目标文本（修订号冲突时返回 null，不覆盖并发写入）。
     */
    suspend fun updateText(goalId: String, newText: String): V2AgentGoal? {
        val existing = v2Agent.getGoalById(goalId) ?: return null
        if (existing.statusEnum() == GoalStatus.DONE || existing.statusEnum() == GoalStatus.ABANDONED) {
            return null
        }
        return casSet(goalId, existing.statusEnum(), newText)
    }

    /** CAS 置状态。修订号冲突或目标不存在时返回 null。 */
    suspend fun setStatus(goalId: String, status: GoalStatus): V2AgentGoal? {
        val existing = v2Agent.getGoalById(goalId) ?: return null
        if (existing.statusEnum() == status) return existing
        return casSet(goalId, status, existing.text)
    }

    /** 完成目标（置 DONE）。 */
    suspend fun complete(goalId: String): V2AgentGoal? = setStatus(goalId, GoalStatus.DONE)

    /** 放弃目标（置 ABANDONED）。 */
    suspend fun abandon(goalId: String): V2AgentGoal? = setStatus(goalId, GoalStatus.ABANDONED)

    private suspend fun casSet(goalId: String, status: GoalStatus, text: String): V2AgentGoal? {
        val existing = v2Agent.getGoalById(goalId) ?: return null
        val updated = v2Agent.casUpdateGoalStatusAndText(
            goalId = goalId,
            status = status.name,
            text = text,
            newRevision = existing.revision + 1,
            expectedRevision = existing.revision,
            updatedAtMs = System.currentTimeMillis()
        )
        return if (updated > 0) getById(goalId) else null
    }
}

/** 目标生命周期状态（对齐 DSH GoalStatus）。 */
enum class GoalStatus {
    PROPOSED, ACTIVE, DONE, ABANDONED
}

internal fun V2AgentGoal.statusEnum(): GoalStatus =
    EnumSafe.valueOf(status, GoalStatus.ACTIVE, tag = "Agent_goals.status")
