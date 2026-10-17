package com.mini.me_core.datalayer.store

/**
 * 流式生成活跃标记（任务 B：应用被系统回收导致对话中断的恢复）。
 *
 * 语义：每个正在流式生成的会话记录一条 `sessionId → 写入进程 pid` 标记；
 * 生成正常结束即清除。冷启动时若发现某会话的标记 pid 与当前进程不一致，
 * 说明写入标记的进程已死（LMKD/厂商回收），该会话的助手消息停在中途，
 * 需要补记中断提示——见 `SessionUseCase.recoverInterruptedGeneration`。
 *
 * 为什么用 pid 而不是布尔「活跃」位：Activity/ViewModel 重建时进程不变，
 * 布尔位无法区分「同进程取消后重建」与「进程死亡后冷启动」；pid 不匹配
 * 才是进程死亡的可靠证据（同进程重建时 pid 必然相同，不会误伤）。
 */
interface GenerationMarkerStore {

    /** 标记会话正在流式生成，[pid] 为写入标记的进程 id（android.os.Process.myPid()）。 */
    fun markActive(sessionId: String, pid: Int)

    /** 生成结束（正常完成/失败/用户停止）时清除标记。 */
    fun clearActive(sessionId: String)

    /** 全部活跃标记：`sessionId → 标记 pid`（已软删除的条目由 KV 查询过滤，不返回）。 */
    fun activeMarkers(): Map<String, Int>
}

/**
 * [GenerationMarkerStore] 的 KV 实现：namespace `agent_generation`，
 * 键为 sessionId，值为 pid 字符串。读取侧容错跳过无法解析的脏值。
 */
class KvGenerationMarkerStore(private val kv: KVStore) : GenerationMarkerStore {

    override fun markActive(sessionId: String, pid: Int) {
        kv.putString(NAMESPACE, sessionId, pid.toString())
    }

    override fun clearActive(sessionId: String) {
        kv.delete(NAMESPACE, sessionId)
    }

    override fun activeMarkers(): Map<String, Int> =
        kv.getAll(NAMESPACE)
            .mapNotNull { entry -> entry.stringVal?.toIntOrNull()?.let { entry.key to it } }
            .toMap()

    private companion object {
        const val NAMESPACE = "agent_generation"
    }
}
