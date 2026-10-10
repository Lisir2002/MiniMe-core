package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 派生执行（wrapper）穿透分析：识别一行命令是否为「外层解释器/提权/包裹器」，
 * 并把其内层要执行的命令字符串抽出来，再交给危险分析跑**一层**同款判定。
 *
 * 覆盖的包裹形态（按要求递归一层即可，不再向内递归）：
 *  - `eval "<cmd>"` / `eval '<cmd>'`
 *  - `bash -c "<cmd>"` / `sh -c` / `zsh -c` / `dash -c` / `ash -c`
 *  - `sudo ... <cmd>`（含 `-u user` 等选项前缀）
 *  - `xargs ... <cmd>`
 *  - `env VAR=v ... <cmd>`（命令包裹）
 *
 * 设计取舍：
 * - 只抽「内层命令字符串」，不做二次派生（避免无限递归）；
 * - 引号/分词由调用方把抽出的字符串再丢回 [ShellCommandParser.analyze] 处理；
 * - 本对象为纯 Kotlin 无 Android 依赖，可直接 JVM 单测。
 */
object DerivedCommandAnalyzer {

    private val SHELL_WRAPPERS = setOf("sh", "bash", "zsh", "dash", "ash")
    private val ENV_ASSIGN_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /** sudo 带值选项（选项 + 取值占两个 token）。 */
    private val SUDO_VALUE_FLAGS = setOf("-u", "--user", "-g", "--group")

    /** xargs 带值选项（选项 + 取值占两个 token）。 */
    private val XARGS_VALUE_FLAGS = setOf(
        "-I", "-P", "-n", "-d", "-s", "--replace", "--max-procs", "--max-args", "--delimiter"
    )

    /** env 带值选项（选项 + 取值占两个 token）。 */
    private val ENV_VALUE_FLAGS = setOf("-u", "-C", "--unset", "--chdir")

    /** 段首环境赋值跳过（与 [ShellCommandParser.effectiveTokens] 语义一致）。 */
    private fun dropEnvAssigns(tokens: List<String>): List<String> {
        var i = 0
        while (i < tokens.size && ENV_ASSIGN_RE.matches(tokens[i])) i++
        return if (i == 0) tokens else tokens.subList(i, tokens.size)
    }

    /**
     * 若该段是派生包裹器，返回其内层命令字符串列表（通常 0 或 1 个）；否则返回空。
     * 返回的字符串可能含未展开引号/分词，调用方应再走 [ShellCommandParser.analyze]。
     */
    fun extractInnerCommands(tokens: List<String>): List<String> {
        val eff = dropEnvAssigns(tokens)
        if (eff.isEmpty()) return emptyList()
        val program = eff.first()
        val rest = eff.drop(1)
        return when {
            program == "eval" -> joinIfNotBlank(rest)
            program in SHELL_WRAPPERS -> extractAfterCFlag(eff)
            program == "sudo" -> joinIfNotBlank(skipValueFlags(rest, SUDO_VALUE_FLAGS, inlineAssign = false))
            program == "xargs" -> joinIfNotBlank(skipValueFlags(rest, XARGS_VALUE_FLAGS, inlineAssign = false))
            program == "env" -> joinIfNotBlank(skipValueFlags(rest, ENV_VALUE_FLAGS, inlineAssign = true))
            else -> emptyList()
        }
    }

    private fun joinIfNotBlank(rest: List<String>): List<String> {
        val joined = rest.joinToString(" ")
        return if (joined.isBlank()) emptyList() else listOf(joined)
    }

    /** 从 `sh -c "cmd" args...` 中取出 `-c` 后的命令串。 */
    private fun extractAfterCFlag(tokens: List<String>): List<String> {
        for (i in 1 until tokens.size) {
            val t = tokens[i]
            val cmd = when {
                t == "-c" -> tokens.getOrNull(i + 1)?.takeIf { it.isNotBlank() }
                t.startsWith("-c") && t.length > 2 -> t.substring(2)
                t.startsWith("-") && t != "-" -> null // 其它 flag 跳过
                else -> return emptyList() // 非 flag 且不是 -c：不是我们要的形态
            }
            if (cmd != null) return listOf(cmd)
        }
        return emptyList()
    }

    /**
     * 跳过包裹器选项与环境赋值，返回其后真正要执行的命令 token。
     *
     * 该循环必须顺序消费 token：带值 flag 占两个 token、inline 赋值占一个 token、
     * `--` 终止符吃掉自身后停止。用统一的「每个 token 前进 1 或 2」状态机实现，
     * 避免对 sudo/xargs/env 各写一份重复循环。
     */
    @Suppress("LoopWithTooManyJumpStatements") // 顺序分词状态机需按 token 提前终止（-- 终止 / 非选项停下）
    private fun skipValueFlags(
        args: List<String>,
        valueFlags: Set<String>,
        inlineAssign: Boolean
    ): List<String> {
        var i = 0
        while (i < args.size) {
            val t = args[i]
            if (t == "--") { i++; break }
            val advance = advanceFor(t, valueFlags, inlineAssign)
            if (advance == 0) break
            i += advance
        }
        return args.subList(i, args.size)
    }

    /** 返回该 token 应前进的步数（0 = 命中非选项，停止；1 = 吞掉自身；2 = 吞掉自身+取值）。 */
    private fun advanceFor(t: String, valueFlags: Set<String>, inlineAssign: Boolean): Int = when {
        inlineAssign && ENV_ASSIGN_RE.matches(t) -> 1
        t in valueFlags -> 2
        isInlineValueFlag(t) -> 1
        t.startsWith("-") && t != "-" -> 1
        else -> 0
    }

    private fun isInlineValueFlag(t: String): Boolean =
        t.startsWith("--user=") || t.startsWith("--group=") ||
            t.startsWith("-I") || t.startsWith("--replace=")
}
