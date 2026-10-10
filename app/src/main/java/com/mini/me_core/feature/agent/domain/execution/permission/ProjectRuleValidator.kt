package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 项目级（工作区 `.minime/permissions.json`）规则校验器（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 威胁模型：项目规则文件落在可被 AI 读写的工作区内。若允许 `Bash` 整工具 ALLOW（裸 `*`），
 * 模型即可借写入该文件给自己永久提权（`Bash(*)` = 任意 shell 命令自动放行）。因此：
 *
 *  - shell/terminal 类工具的 ALLOW 规则**必须带具体命令前缀**（如 `Bash(git pull)`）；
 *  - 裸 `*`、空 pattern、或 `Bash(*)` 这种整工具 ALLOW 一律视为非法，加载时忽略并记审计；
 *  - DENY 规则不做此限制（收紧方向，无提权风险）；
 *  - 非 shell 工具（writeFile/readFile 等）的整工具 ALLOW 维持原语义（它们的授权粒度本来就是整工具）。
 *
 * 全局规则由用户在设置界面维护、落 app 私有目录，不受本校验器约束（trusted）。
 */
object ProjectRuleValidator {

    /** 按命令前缀做指令级匹配的 shell 工具名（与 ToolPermissionPolicyEngine.SHELL_TOOLS 对齐）。 */
    private val SHELL_TOOL_NAMES = setOf("Bash", "terminal")

    /** 校验结果：非法规则 + 原因（用于脱敏审计，不打印工作区路径以外的敏感内容）。 */
    data class InvalidRule(
        val rule: PermissionRule,
        val reason: String
    )

    /**
     * 判断一条项目级 ALLOW 规则是否合法。DENY 规则一律合法。
     */
    fun isValid(rule: PermissionRule): Boolean = validate(rule) == null

    /**
     * 返回非法原因；合法返回 null。
     */
    fun validate(rule: PermissionRule): String? {
        if (rule.decision == PermissionDecision.DENY) return null
        if (rule.toolName !in SHELL_TOOL_NAMES) return null
        val pattern = rule.pattern.trim()
        return when {
            isWholeToolWildcard(pattern) ->
                "shell/terminal 工具的项目 ALLOW 规则必须带具体命令前缀（如 Bash(git pull)），禁止整工具放行"
            firstTokenUnusable(pattern) ->
                "shell/terminal 工具的项目 ALLOW 规则首段必须为具体程序名，禁止通配整工具放行"
            else -> null
        }
    }

    private fun isWholeToolWildcard(pattern: String): Boolean =
        pattern.isEmpty() || pattern == PermissionRule.WHOLE_TOOL || pattern == "*"

    private fun firstTokenUnusable(pattern: String): Boolean {
        val firstToken = pattern.split(Regex("\\s+")).firstOrNull().orEmpty()
        return firstToken.isEmpty() || firstToken.all { it == '*' || it == '?' }
    }

    /**
     * 过滤项目规则列表，返回 (合法规则, 被忽略的非法规则)。
     */
    fun filterProjectRules(rules: List<PermissionRule>): Pair<List<PermissionRule>, List<InvalidRule>> {
        val valid = mutableListOf<PermissionRule>()
        val invalid = mutableListOf<InvalidRule>()
        for (r in rules) {
            val reason = validate(r)
            if (reason == null) valid += r else invalid += InvalidRule(r, reason)
        }
        return valid to invalid
    }
}
