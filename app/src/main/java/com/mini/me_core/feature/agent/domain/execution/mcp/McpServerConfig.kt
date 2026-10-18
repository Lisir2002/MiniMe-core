package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import kotlinx.serialization.Serializable

// 远程 HTTP（含 url）或本地 stdio（含 command）两种形态，由 isStdio 推断。
@Serializable
data class McpServerConfig(
    val name: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    /** 黑名单：这些工具不注册、不进模型可见面。 */
    val disabledTools: Set<String> = emptySet(),
    /**
     * F3：白名单（收窄到最小可见面）。非空时**只有**这些工具可用，且优先于 [disabledTools]。
     *
     * 为空表示「未启用白名单」，而非「白名单为空」——二者语义不同，故不能用空集区分
     * 「没配」与「配了但一个都不给」，后者应通过 `disabledTools` 表达。
     */
    val enabledTools: Set<String> = emptySet(),
    /** 灰名单：这些工具可见，但每次执行前需用户确认。 */
    val requireApprovalTools: Set<String> = emptySet(),
    /**
     * F1：内置能力分组。非空即表示这台 server 是**内置 server**（进程内 MCP server，
     * 由 [BuiltinGroup] + [BuiltinTransport] 承载），`url`/`command` 均不适用。
     *
     * 内置 server 由代码提供、不落盘，因此该字段**不会**出现在 `mcp.json` 里
     * （解析时缺失即 null），存量配置文件行为完全不变。
     */
    val builtinGroup: BuiltinGroup? = null,
    /**
     * 是否允许外部 MCP 客户端访问（仅内置 server 有意义；远端 server 本身就是对外服务）。
     *
     * 默认取 [BuiltinGroup.remotelyExposedByDefault]：`MiniMe-Core` 允许，
     * 含 UI 交互 / 本机登录态工具的 `MiniMe-Chat` / `MiniMe-Data` 默认拒绝。
     */
    val remotelyExposed: Boolean = builtinGroup?.remotelyExposedByDefault ?: true
) {
    val isStdio: Boolean get() = !command.isNullOrBlank()

    /** 是否内置 server（进程内 MCP server，非 HTTP/stdio 形态）。 */
    val isBuiltin: Boolean get() = builtinGroup != null
}
