package com.mini.me_core.feature.agent.domain.guard

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DangerousCommandGuard @Inject constructor(
    private val settings: NormFlowSettingsRepository
) : ToolGuard {

    override val id = "dangerous-command"

    override suspend fun guard(ctx: ToolGuardContext): ToolGuardResult {
        if (ctx.toolName != "Bash") return ToolGuardResult.Pass
        if (!settings.isGuardDangerousCommandActive()) return ToolGuardResult.Pass
        val command = ctx.argString("command") ?: return ToolGuardResult.Pass
        if (command.isBlank()) return ToolGuardResult.Pass
        if (DANGEROUS_PATTERNS.any { it.containsMatchIn(command) }) {
            return ToolGuardResult.Block(
                code = "DANGEROUS_COMMAND",
                message = "Dangerous command blocked: ${command.take(120)}"
            )
        }
        return ToolGuardResult.Pass
    }

    private companion object {
        val DANGEROUS_PATTERNS = listOf(
            Regex("rm\\s+-[rf]+\\s+(/|/\\*|~)", RegexOption.IGNORE_CASE),
            Regex("\\bmkfs\\b", RegexOption.IGNORE_CASE),
            Regex("\\bdd\\s+if=/dev/zero\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(shutdown|reboot|halt|poweroff)\\b", RegexOption.IGNORE_CASE),
            Regex("chmod\\s+-R\\s+777\\s+/", RegexOption.IGNORE_CASE)
        )
    }
}
