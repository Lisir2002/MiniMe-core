package com.mini.me_core.feature.agent.domain.execution.mcp

/**
 * G4：一次**可选能力**枚举的完整结果。
 *
 * 存在的理由是把 G3「list 失败一律返回空列表」这条降级规则造成的**语义塌缩**补回来。
 * 那条规则对核心路径是对的——一个可选的 prompts 列表不该拖垮 `tools/list`，早退即可；
 * 但同一个「空列表」在 UI 上会丢失决策信息：
 *
 * - `Unsupported`：该 server 明确不具备该能力（未声明 / 回 `-32601`）→ 不必重试
 * - `Failed`：声明了但取不到（超时、断连、解析失败）→ 值得重试，也可能稍后就好
 * - `Listed(emptyList())`：能力正常、确实一条都没有 →坦然空着
 *
 * 三者压成「列表为空」，用户看到的是同一句「暂无 prompts」，无法判断要不要重试、
 * 也不知道该去骂 server 还是等网络。
 *
 * 刻意**不用** `Result<T>`：`Result` 只有成功/失败两态，装不下「不支持」——
 * 而「不支持」恰恰是最常见、且最不该被当成错误呈现给用户的一种结果。
 *
 * @param T 条目类型（如 [McpPromptDescriptor] / [McpResourceDescriptor]）。
 */
public sealed interface OptionalCapability<out T> {

    /** 枚举成功；[items] 可能为空，那就是真的没有。 */
    public data class Listed<out T>(val items: List<T>) : OptionalCapability<T>

    /** 该 server 不具备此能力。这不是错误，UI 应平静地说明「未暴露」。 */
    public data object Unsupported : OptionalCapability<Nothing>

    /** 枚举失败（区别于不支持）。[reason] 已是可展示文本，不保证对用户友好。 */
    public data class Failed(val reason: String) : OptionalCapability<Nothing>
}

/**
 * 取回条目列表，把 [OptionalCapability.Unsupported] 与 [OptionalCapability.Failed]
 * 一并压平为空列表。
 *
 * 这是给「不需要向用户解释原因」的调用方用的收窄出口（如 agent 工具装配路径），
 * 语义与 G3 的原始行为完全一致。
 */
public fun <T> OptionalCapability<T>.itemsOrEmpty(): List<T> = when (this) {
    is OptionalCapability.Listed -> items
    is OptionalCapability.Unsupported -> emptyList()
    is OptionalCapability.Failed -> emptyList()
}
