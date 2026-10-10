package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 「下载即执行 / 内联解释器联网后动态执行」判定（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 从 [DangerousCommandGuard] 下沉的独立判定，覆盖三类供应链/RCE 形态：
 *  - 同命令行内跨段「下载到本地文件 → 立即解释器执行该文件」：`curl -o x.sh URL && bash x.sh`；
 *  - 进程替换 `<(curl ...)`：`bash <(curl URL)` / `cat <(curl URL) | bash`；
 *  - 内联解释器代码里联网抓取后直接 exec/eval：`python3 -c "...urllib...exec..."`。
 *
 * 命中即 Block（返回拒绝原因），未命中返回 null。
 */
internal object DownloadExecGuard {

    private val CURL_PROGS = setOf("curl")
    private val WGET_PROGS = setOf("wget", "wget2")
    private val DOWNLOAD_PROGS = CURL_PROGS + WGET_PROGS

    /** 以文件为参数的解释器（执行本地脚本文件，而非内联字符串）。 */
    private val FILE_INTERPRETER_PROGS = setOf("sh", "bash", "zsh", "dash", "ash", ".", "source")

    /** shell 内建关键字：出现这些词不视为执行下载产物。 */
    private val SHELL_KEYWORDS = setOf(
        "break", "continue", "echo", "printf", "cd", "export", "exit", "return",
        "set", "shift", "wait", "true", "false", ":"
    )

    private val ENV_ASSIGN_RE = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /** 进程替换：`<(curl ...)` / `<(wget ...)`。 */
    private val PROCESS_SUB_RE = Regex("""(?i)<\s*\(\s*(?:curl|wget|wget2)\b""")

    /** 内联解释器 -c/-e 形态。 */
    private val INLINE_INTERPRETER_RE = Regex("""(?i)\b(?:python[0-9.]*|perl|node)\s+-(?:c|e)\b""")

    /** 内联代码里的联网抓取特征。 */
    private val NET_FEATURE_RE = Regex(
        """(?i)\b(?:urllib|requests|httpx|aiohttp|fetch|http\.request|https?://|XMLHttpRequest)\b"""
    )

    /** 内联代码里的动态执行特征。 */
    private val DYNAMIC_EXEC_RE = Regex("""\b(?:exec|eval)\s*\(""")

    private const val MSG_PROCESS_SUB =
        "安全防护：禁止下载后直接执行远程脚本（供应链/代码执行风险）。请先 `curl -O` 下载检查，而非 `bash <(curl URL)` 直接执行。"

    private const val MSG_DOWNLOAD_THEN_EXEC =
        "安全防护：禁止下载后同会话内直接执行远程脚本（供应链/代码执行风险）。请先检查下载内容，再手动执行，而非 `curl -o x && bash x` 直接串联。"

    private const val MSG_INLINE_NET_EXEC =
        "安全防护：禁止在内联解释器代码中联网抓取后直接 exec/eval 执行（远程代码执行风险）。请先把内容写入文件检查，或使用包管理器安装可信依赖。"

    /** 主入口：对一条命令行（已分段）做下载即执行判定。joiners[i] 为 segments[i] 与 segments[i+1] 之间的连接符。 */
    fun blockReason(command: String, segments: List<List<String>>, joiners: List<Char> = emptyList()): String? {
        if (PROCESS_SUB_RE.containsMatchIn(command)) return MSG_PROCESS_SUB
        downloadThenExecReason(segments, joiners)?.let { return it }
        inlineNetworkExecReason(command)?.let { return it }
        return null
    }

    private fun dropEnvAssigns(tokens: List<String>): List<String> {
        var i = 0
        while (i < tokens.size && ENV_ASSIGN_RE.matches(tokens[i])) i++
        return if (i == 0) tokens else tokens.subList(i, tokens.size)
    }

    /**
     * 跨段「下载到本地文件 → 同会话内解释器执行该文件」。
     * 仅当后续段确实对同一文件名/路径执行时才拦；后续为 shell 关键字或无关命令不拦。
     * curl 下载需由 `&&`/`|` 成功链/管道连接才视为有意串联；wget 下载任意连接符均拦。
     */
    @Suppress("LoopWithTooManyJumpStatements") // 顺序扫描各段：遇下载段记录文件名、遇解释器段判定是否执行已下载文件，需按段 continue/return 提前终止
    private fun downloadThenExecReason(segments: List<List<String>>, joiners: List<Char>): String? {
        // 已下载文件名 -> 下载工具（curl/wget）。
        val downloaded = mutableMapOf<String, String>()
        for ((idx, rawSeg) in segments.withIndex()) {
            val eff = dropEnvAssigns(rawSeg)
            val program = eff.firstOrNull() ?: continue
            if (program in DOWNLOAD_PROGS) {
                recordDownloadedFiles(program, eff, downloaded)
                continue
            }
            if (program in SHELL_KEYWORDS) continue
            if (program !in FILE_INTERPRETER_PROGS) continue
            val joiner = joiners.getOrNull(idx - 1)
            val executed = scriptArgOf(eff) ?: continue
            val matchedDownloader = downloaded.entries.firstOrNull { sameLocalFile(it.key, executed) }?.value
                ?: continue
            if (shouldBlock(matchedDownloader, joiner)) return MSG_DOWNLOAD_THEN_EXEC
        }
        return null
    }

    /** curl 仅在 &&/| 成功链下拦；wget 任意连接符均拦（`;` 无条件执行也属高风险）。 */
    private fun shouldBlock(downloader: String, joiner: Char?): Boolean {
        if (downloader in WGET_PROGS) return true
        return joiner == '&' || joiner == '|'
    }

    private fun recordDownloadedFiles(downloader: String, eff: List<String>, out: MutableMap<String, String>) {
        var i = 1
        while (i < eff.size) {
            val a = eff[i]
            when {
                downloader in CURL_PROGS && (a == "-o" || a == "--output") ->
                    eff.getOrNull(i + 1)?.let { out[it] = downloader; i++ }
                downloader in CURL_PROGS && a.startsWith("--output=") ->
                    out[a.substringAfter("=")] = downloader
                downloader in WGET_PROGS && (a == "-O" || a == "--output-document") ->
                    eff.getOrNull(i + 1)?.let { out[it] = downloader; i++ }
                downloader in WGET_PROGS && a.startsWith("--output-document=") ->
                    out[a.substringAfter("=")] = downloader
            }
            i++
        }
    }

    /** 解释器段的脚本文件参数：第一个非 flag、非内联串的位置参数。 */
    @Suppress("LoopWithTooManyJumpStatements") // 顺序分词扫描，需按 token 跳过 flag 后提前返回
    private fun scriptArgOf(eff: List<String>): String? {
        val args = eff.drop(1)
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a == "-c" || a == "-e" || a == "--") { i += if (a == "--") 1 else 2; continue }
            if (a.startsWith("-")) { i++; continue }
            return a
        }
        return null
    }

    /** 宽松比较：忽略 ./ 前缀与目录层差异。 */
    private fun sameLocalFile(a: String, b: String): Boolean {
        val na = a.removePrefix("./")
        val nb = b.removePrefix("./")
        if (na == nb) return true
        return na.substringAfterLast('/') == nb.substringAfterLast('/')
    }

    /** 内联解释器代码里联网抓取后直接 exec/eval。 */
    private fun inlineNetworkExecReason(command: String): String? {
        if (!INLINE_INTERPRETER_RE.containsMatchIn(command)) return null
        val hasNet = NET_FEATURE_RE.containsMatchIn(command)
        val hasExec = DYNAMIC_EXEC_RE.containsMatchIn(command)
        return if (hasNet && hasExec) MSG_INLINE_NET_EXEC else null
    }
}
