package com.mini.me_core.feature.agent.data.remote.anthropic

data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    /**
     * system prompt：String（单段）或 List<AnthropicSystemBlock>（分段，可带 cache_control）。
     * Gson 按运行时类型序列化，两种形态均符合 Anthropic Messages API 的 system 字段契约。
     */
    val system: Any? = null,
    val max_tokens: Int = 16384,
    // 开启 extended thinking 时不能携带 temperature（官方要求），置 null 由 Gson 跳过该字段。
    val temperature: Float? = null,
    val top_p: Float? = null,
    val thinking: AnthropicThinkingConfig? = null,
    val tools: List<AnthropicToolDefinition>? = null,
    val stream: Boolean = false
)

/**
 * system prompt 分段块。cache_control 非空时在该块末尾设置 prompt cache 断点，
 * 其后内容（messages 等）可复用缓存，命中部分的输入 token 按缓存价计费。
 */
data class AnthropicSystemBlock(
    val type: String = "text",
    val text: String,
    val cache_control: Map<String, String>? = null
)

/** Anthropic extended thinking 配置：type="enabled" + budget_tokens（思考 token 预算）。 */
data class AnthropicThinkingConfig(
    val type: String = "enabled",
    val budget_tokens: Int
)

data class AnthropicMessage(
    val role: String, // "user" or "assistant"
    val content: Any // Can be String or List<AnthropicContentBlock>
)

data class AnthropicContentBlock(
    val type: String, // "text", "tool_use", "tool_result", "thinking"
    val text: String? = null,
    val source: Map<String, Any>? = null,
    val id: String? = null, // for tool_use
    val name: String? = null, // for tool_use
    val input: Map<String, Any>? = null, // for tool_use
    val tool_use_id: String? = null, // for tool_result
    val content: String? = null, // for tool_result
    val is_error: Boolean? = null, // for tool_result
    val thinking: String? = null, // for thinking block：思考摘要文本
    val signature: String? = null // for thinking block：加密签名，多轮/工具循环须原样回传
)

data class AnthropicToolDefinition(
    val name: String,
    val description: String,
    val input_schema: Map<String, Any>,
    /** prompt cache 断点标记：置于 tools 数组末尾元素上，使 system+tools 前缀可被缓存复用。 */
    val cache_control: Map<String, String>? = null
)

data class AnthropicMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)

data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int,
    /** 本轮新写入缓存的 token 数（cache_creation，按高于原价的费率计费）。 */
    val cache_creation_input_tokens: Int = 0,
    /** 本轮命中缓存的 token 数（cache_read，按缓存价计费）。 */
    val cache_read_input_tokens: Int = 0
)
