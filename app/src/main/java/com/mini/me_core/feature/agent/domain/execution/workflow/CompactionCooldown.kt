package com.mini.me_core.feature.agent.domain.execution.workflow

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D2 压缩冷却窗口（批次 D，方案见 docs/tool-orchestration-optimization.md）：
 * 每次压缩都会改写历史消息 → 击穿 prompt 缓存前缀 + 产生一次 LLM 摘要成本。
 * 压缩后短窗口内抑制**自动**压缩触发，避免「压缩后几分钟内再次压缩」的连续击穿
 * （手动 force 压缩与显式用户操作不受抑制）。
 *
 * 与 D1 masking 配合：masking 先于摘要触发且零 LLM 成本，窗口期内轮次 token 增长
 * 已被 masking 压低，90% 阈值内多撑几分钟无溢出风险；触发阈值本身留有 10% 余量。
 */
@Singleton
class CompactionCooldown @Inject constructor() {

    private val lastCompactionAt = ConcurrentHashMap<String, Long>()

    /** 会话刚发生过压缩且仍在窗口内 → 抑制自动压缩（force 调用方应绕过本检查）。 */
    fun shouldSuppress(sessionId: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (sessionId == null) return false
        val last = lastCompactionAt[sessionId] ?: return false
        return now - last < WINDOW_MS
    }

    /** 记录一次压缩完成时间（会话维度）。 */
    fun markCompacted(sessionId: String?, now: Long = System.currentTimeMillis()) {
        if (sessionId == null) return
        lastCompactionAt[sessionId] = now
    }

    /** 会话结束时清理，避免 Map 无限增长（会话删除/清空上下文路径调用）。 */
    fun forget(sessionId: String) {
        lastCompactionAt.remove(sessionId)
    }

    companion object {
        /** 冷却窗口 5 分钟。 */
        const val WINDOW_MS = 5 * 60 * 1000L
    }
}
