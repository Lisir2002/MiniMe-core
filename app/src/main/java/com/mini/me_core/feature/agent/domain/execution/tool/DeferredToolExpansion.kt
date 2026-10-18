package com.mini.me_core.feature.agent.domain.execution.tool

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B2 延迟加载展开集（按会话的 LRU）。
 *
 * 两类入口刷新同一集合：
 * - [recordHit]：模型调 `tool_catalog` 检索命中（下一步就要用）；
 * - [recordCall]：模型实际调用了某 DEFERRED 工具（正在用，不能下轮就丢）。
 *
 * 语义要点：
 * - access-order LRU，每会话上限 [MAX_EXPANDED_PER_SESSION] 条——超限淘汰最久未用，
 *   保证「刚用过的工具不会丢、很久不用的自动退出上下文」；
 * - 只存工具名，装配时由 [ToolLoadingPolicy] 与 registry 现状求交——MCP 注销后的
 *   残留名字自然失效，无需监听注销事件；
 * - 会话删除时由 [com.mini.me_core.feature.agent.domain.session.SessionUseCase] 调
 *   [forget] 清理，防 Map 随会话无限增长。
 */
@Singleton
class DeferredToolExpansion @Inject constructor() {

    /** sessionId → (工具名 → 最近访问时间戳) 的 access-order LRU。 */
    private val expandedBySession = ConcurrentHashMap<String, LinkedHashMap<String, Long>>()

    /** 目录检索命中：把命中工具名刷新进展开集（已展开的顺延为新近使用）。 */
    fun recordHit(sessionId: String, toolNames: Collection<String>) {
        if (toolNames.isEmpty()) return
        val map = mapFor(sessionId)
        synchronized(map) {
            val now = System.currentTimeMillis()
            toolNames.forEach { map[it] = now }
        }
    }

    /** 实际调用命中：把被调用的 DEFERRED 工具刷新进展开集。 */
    fun recordCall(sessionId: String, toolName: String) {
        val map = mapFor(sessionId)
        synchronized(map) {
            map[toolName] = System.currentTimeMillis()
        }
    }

    /** 取本会话当前展开集（只读快照；空集表示无展开）。 */
    fun getExpanded(sessionId: String): Set<String> {
        val map = expandedBySession[sessionId] ?: return emptySet()
        synchronized(map) {
            return map.keys.toSet()
        }
    }

    /** 会话删除：清理该会话的展开记录。 */
    fun forget(sessionId: String) {
        expandedBySession.remove(sessionId)
    }

    /** 测试/重置用：清空全部会话的展开记录。 */
    fun clear() {
        expandedBySession.clear()
    }

    private fun mapFor(sessionId: String): LinkedHashMap<String, Long> {
        // computeIfAbsent 原子：并发目录命中/调用刷新不会出现「双 map 二选一丢写」。
        return expandedBySession.computeIfAbsent(sessionId) { AccessOrderLruMap() }
    }

    /**
     * access-order LRU：读/写都算访问；超出 [MAX_EXPANDED_PER_SESSION] 时
     * [LinkedHashMap.removeEldestEntry] 自动淘汰最久未用条目。
     */
    private class AccessOrderLruMap :
        LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_EXPANDED_PER_SESSION
        }
    }

    companion object {
        /**
         * 每会话展开上限：12 条 deferred 工具的描述体量约与 12 个 CORE 工具相当，
         * 再大就失去「收窄上下文」的意义（目录检索本身也是收窄手段）。
         */
        const val MAX_EXPANDED_PER_SESSION = 12
    }
}
