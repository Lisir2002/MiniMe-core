package com.mini.me_core.feature.agent.domain.execution.mcp

/**
 * F3：MCP server 的工具治理策略。
 *
 * 把 [McpServerConfig] 上三组裸集合的**语义**收敛到单一判定入口，避免「谁在哪个
 * 集合里」的规则散落在 `McpManager`、`AgentToolMcpAdapter`、UI 徽章三处各判一次——
 * 三处判定一旦漂移，就会出现「界面显示需审批但实际直接放行」这类最坏情况
 * （**权限提示失真是安全缺陷，不只是 UI bug**）。
 *
 * 三组集合的语义互不重叠，必须严格区分：
 *
 * | 集合 | 语义 | 命中后果 |
 * |---|---|---|
 * | `disabledTools` | 黑名单：这些工具**不注册**、不可被模型看到 | 工具不存在于注册表 |
 * | `enabledTools` | 白名单：只有这些工具可用（生产安全默认） | 其余工具一律不可见 |
 * | `requireApprovalTools` | 灰名单：可见、但每次执行前要用户确认 | `permissionPolicy = ASK` |
 *
 * **黑名单与白名单同时命中时，黑名单优先（拒绝优先）**。
 *
 * 这不是随意选择，而是联网核实后的结论（2026-10）：MCP 生态里两种做法都有，但
 * **安全优先的一侧一致**——
 * - `mcp-cli`：`Step 1 匹配 disabledTools → BLOCKED（absolute precedence）`，
 *   其 README 称之为 security-first precedence model；
 * - `mcp-compress-router`：`The denylist wins on conflict: a tool matching both
 *   lists is blocked`；
 * - `synthorg` 更严格：两表重叠直接**在加载期报配置错误**。
 *
 * 少数实现（如某些客户端文档）主张白名单优先。**不采纳**的理由：白名单的语义是
 * 「收窄到最小可见面」，属粗粒度的日常配置；黑名单的语义是「这个工具绝对不许用」，
 * 通常是踩过坑后的针对性封禁。当一个工具同时出现在两者里，几乎总是说明用户**曾经
 * 想封禁它**——让黑名单赢，才不会因为白名单里残留一个名字而把危险工具重新放开。
 * 这也正是本仓「按假定最坏处理」的一贯取向。
 *
 * G2 起另带两个**服务器级**字段（[kind] / [enabled]）。它们不参与工具级判定规则的
 * 细化，只负责把「这台 server 是什么形态、是否启用」这两件事也收进同一个入口——
 * 此前这两个维度只存在于 `McpServerConfig` 与 `McpManager` 里，本类无从回答
 * 「这台被禁用的 server 属于什么形态」，调用方只能自己去猜。
 *
 * 联网核实（2026-10）后确认这一收拢与生态一致：
 * - VS Code：`When an MCP server is disabled, it does not start and its tools,
 *   prompts, resources, and MCP apps are excluded from chat`；
 * - Codex：`Use enabled = false when the goal is to avoid connecting to that
 *   server at all`；
 * - Gemini CLI：禁用即不连接、不提供工具。
 *
 * 即「禁用 = 不连接 + 工具不暴露」是客户端的通行约定，故 [enabled] 为 false 时
 * [isToolEnabled] **一律返回 false**，不再看三组名单。
 */
class ServerPolicy(
    val disabledTools: Set<String> = emptySet(),
    val enabledTools: Set<String> = emptySet(),
    val requireApprovalTools: Set<String> = emptySet(),
    /** G2：服务器形态，从配置派生（唯一实现见 [ServerKind.derive]）。 */
    val kind: ServerKind = ServerKind.BUILTIN,
    /**
     * G2：服务器是否启用。为 false 时该 server 的工具**一律不可见**。
     *
     * 与 [McpManager] 的连接过滤是双保险而非重复：那一层保证「不连接」，这一层保证
     * 「即便连接已建立、配置随后被禁用，已构造的工具也不会继续可用」。
     */
    val enabled: Boolean = true
) {

    /** 该策略是否显式配置了白名单。空集表示「未启用白名单」，而非「白名单为空」。 */
    val hasWhitelist: Boolean get() = enabledTools.isNotEmpty()

    /** 内置 server 恒启用（由代码装配、不落盘，没有「禁用」这个用户操作）。 */
    val isBuiltin: Boolean get() = kind == ServerKind.BUILTIN

    override fun equals(other: Any?): Boolean =
        other is ServerPolicy &&
            disabledTools == other.disabledTools &&
            enabledTools == other.enabledTools &&
            requireApprovalTools == other.requireApprovalTools &&
            kind == other.kind &&
            enabled == other.enabled

    override fun hashCode(): Int {
        var result = disabledTools.hashCode()
        result = 31 * result + enabledTools.hashCode()
        result = 31 * result + requireApprovalTools.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }

    override fun toString(): String =
        "ServerPolicy(kind=$kind, enabled=$enabled, disabledTools=$disabledTools, " +
            "enabledTools=$enabledTools, requireApprovalTools=$requireApprovalTools)"

    /**
     * 工具是否应被注册（进模型可见面）。
     *
     * **server 级先行**：[enabled] 为 false 直接全部不可见，连黑名单都不必查。
     * 其后**拒绝优先**：再查黑名单，命中即隐藏，**不再看白名单**。理由见类注释
     * （与 mcp-cli / mcp-compress-router 的 security-first 约定一致）。
     * 未配置白名单时退化为「不在黑名单即可见」。
     */
    fun isToolEnabled(remoteName: String): Boolean {
        if (!enabled) return false
        if (remoteName in disabledTools) return false
        if (hasWhitelist) return remoteName in enabledTools
        return true
    }

    /**
     * 工具是否需要每次执行前经用户审批。
     *
     * 只认用户显式配置，**不推断、不兜底**：未配置即`AUTO_APPROVE`，交由本地权限
     * 引擎按 `capabilities` / 权限规则 / 沙箱等既有维度判定。理由见 [MCP 注解不可信]
     * 说明——工具自己声明的 `annotations` 是不可信提示，不能据此把写操作降级为放行。
     */
    fun requiresApproval(remoteName: String): Boolean =
        isToolEnabled(remoteName) && remoteName in requireApprovalTools

    companion object {
        /**
         * 无限制策略：内置 server 用。
         *
         * 内置工具的审批判定**完全走本地** `permissionPolicy`（如 `terminal` 为 `ASK`），
         * 不经此处的用户配置——内置工具不落盘，没有配置可读。
         */
        val Unrestricted: ServerPolicy = ServerPolicy(kind = ServerKind.BUILTIN, enabled = true)

        /**
         * 由配置派生策略。
         *
         * `kind` / `enabled` 一并映射——否则调用方拿到策略后无法回答「这台 server 是什么
         * 形态、是否已禁用」，得回头去翻配置，判定入口就又漏了一处。
         */
        fun from(cfg: McpServerConfig): ServerPolicy = ServerPolicy(
            disabledTools = cfg.disabledTools,
            enabledTools = cfg.enabledTools,
            requireApprovalTools = cfg.requireApprovalTools,
            kind = ServerKind.derive(cfg),
            enabled = cfg.enabled
        )
    }
}

/*
 * MCP 注解不可信（规范约束，非本仓库独创）：
 *
 * MCP `tools/call` 的 `annotations`（`readOnlyHint` / `destructiveHint` /
 * `idempotentHint` / `openWorldHint`）按规范是**不可信提示**，其默认值分别为
 * `readOnlyHint=false` / `destructiveHint=true` / `idempotentHint=false` /
 * `openWorldHint=true`。
 *
 * 这意味着一个声明 `readOnlyHint=true` 的外部工具**没有任何保证**真的只读。
 * 故本仓库的审批判定只信两类输入：本地 `permissionPolicy`，以及用户在
 * `requireApprovalTools` 里的显式勾选。**不得**把 `annotations` 参与判定。
 */
