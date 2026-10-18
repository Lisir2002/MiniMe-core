package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D1 Observation Masking（批次 D，方案见 docs/tool-orchestration-optimization.md）：
 * 在历史消息组装时（发送前视图变换，**不落库**）把「最近 N 个工具回合之外」的
 * 工具输出替换为占位符，动作（tool_use）与推理骨架原样保留。
 *
 * 三级级联位置：L1 截断（ToolOutputStore，已有）→ **L2 masking（本类）**→ L3 LLM 摘要
 * （ContextCompactor，已有）。masking 零额外 LLM 调用，先于摘要触发，
 * 用「占位符 + 按需取回」替代整段旧输出（JetBrains 实测历史段 token -52%）。
 *
 * 压缩安全层级（红线，对应方案文档「永不 mask」条款）：
 * - 用户指令：本类只处理 ToolResultMessage，UserMessage 永不触碰；
 * - 活跃错误：status=error 的工具结果永不 mask（错误上下文常是排障关键）；
 * - todo 状态：todo 工具结果永不 mask（任务清单状态须完整可见）；
 * - 当前文件路径：占位符保留 output_path 取回路径；进行中的文件在最近 N 回合窗口内。
 *
 * 附带阈值 [MIN_MASK_CHARS]：过短的旧输出（如 {"ok":true}）mask 收益为负
 * （占位符本身也有长度），仅 mask 超阈值的大输出——40K 内联上限下的旧预览正文是主要浪费源。
 */
@Singleton
class ObservationMasker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 返回 masking 后的请求视图；无需 masking 时返回原列表（同一实例，调用方可按引用判等）。
     *
     * 工具回合定义：一条 AssistantMessage（toolCalls 非空）连同其后连续的
     * ToolResultMessage 构成一个回合；从尾部数保留最近 [keepRecentToolRounds] 个回合，
     * 更早的 ToolResultMessage 按规则占位符化。
     */
    fun mask(
        messages: List<AgentMessage>,
        keepRecentToolRounds: Int = DEFAULT_KEEP_RECENT_TOOL_ROUNDS
    ): List<AgentMessage> {
        if (messages.isEmpty()) return messages

        // 工具回合起点 = 含 toolCalls 的 AssistantMessage 下标（尾部对齐统计）。
        val roundStartIndices = messages.indices.filter { index ->
            val msg = messages[index]
            msg is AgentMessage.AssistantMessage && msg.toolCalls.isNotEmpty()
        }
        if (roundStartIndices.size <= keepRecentToolRounds) return messages

        // 第一个「保留」回合的起点：此前的工具输出进入 masking 区。
        val maskBeforeIndex = roundStartIndices[roundStartIndices.size - keepRecentToolRounds]

        var masked = false
        val result = ArrayList<AgentMessage>(messages.size)
        for (index in messages.indices) {
            val msg = messages[index]
            if (index < maskBeforeIndex && msg is AgentMessage.ToolResultMessage) {
                val placeholder = buildPlaceholder(msg)
                if (placeholder != null) {
                    result.add(msg.copy(result = placeholder))
                    masked = true
                    continue
                }
            }
            result.add(msg)
        }
        // 无任何消息被 mask 时返回原列表，避免无意义拷贝。
        return if (masked) result else messages
    }

    /**
     * 生成占位符；返回 null 表示该消息不应 mask（错误 / todo / 过短）。
     * 占位符保留 output_path（若有），模型可用 readFile 按需取回完整内容。
     */
    private fun buildPlaceholder(msg: AgentMessage.ToolResultMessage): String? {
        if (msg.result.length <= MIN_MASK_CHARS) return null
        if (msg.toolName == TODO_TOOL_NAME) return null

        val parsed = runCatching { json.parseToJsonElement(msg.result).jsonObject }.getOrNull()
        // 红线：错误结果永不 mask（排障关键上下文）。
        if (parsed?.get("status")?.jsonPrimitive?.contentOrNull == "error") return null

        val data = parsed?.get("data") as? JsonObject
        val outputPath = data?.get("output_path")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val size = msg.result.length
        return if (outputPath != null) {
            "[输出已归档：路径 $outputPath，大小 $size 字符，可用 readFile(path=\"$outputPath\", start_line=...) 按需取回]"
        } else {
            "[历史输出已归档：${msg.toolName} 返回约 $size 字符，未随上下文保留；需要时重新执行该工具]"
        }
    }

    companion object {
        /** 默认保留最近 10 个工具回合的完整输出（JetBrains observation masking 同款窗口）。 */
        const val DEFAULT_KEEP_RECENT_TOOL_ROUNDS = 10

        /** 短于此长度的旧输出不 mask（mask 收益为负）。 */
        const val MIN_MASK_CHARS = 400

        private const val TODO_TOOL_NAME = "todo"
    }
}
