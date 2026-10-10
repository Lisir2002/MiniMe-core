package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 灾难性 rm 目标判定（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 从 [ToolPermissionPolicyEngine] 抽出的可测核心：
 *  - 路径先经 [PathNormalizer] 归一（$HOME/${HOME}/~ → /root）；
 *  - 对每一段（含一层派生执行内层）做 rm 目标黑名单比对；
 *  - 内层还会同步跑 [DangerousCommandGuard.blockReason]，挡住 mkfs/dd 写设备等。
 */
object CatastrophicRmGuard {

    private const val MSG_ROOT = "安全防护：禁止执行高危删除操作（根目录删除）"
    private const val MSG_GLOB = "安全防护：禁止执行高危删除操作（全局或相对路径通配删除）"
    private const val MSG_HOME = "安全防护：禁止执行高危删除操作（用户家目录删除）"
    private const val MSG_WORKSPACE = "安全防护：禁止执行高危删除操作（工作区根目录删除）"
    private const val MSG_TMP = "安全防护：禁止执行高危删除操作（系统临时目录整体删除）"
    private const val MSG_SYS = "安全防护：禁止执行高危删除操作（系统关键目录删除）"

    /**
     * 对一组顶层段做灾难性 rm 判定；命中返回拒绝原因，否则 null。
     */
    fun reason(segments: List<List<String>>): String? {
        for (seg in segments) {
            directReason(seg)?.let { return it }
            // 一层派生穿透：eval/bash -c/sh -c/sudo/xargs/env 内层命令再跑一次同款判定。
            for (inner in DerivedCommandAnalyzer.extractInnerCommands(seg)) {
                val innerAnalysis = ShellCommandParser.analyze(inner)
                for (innerSeg in innerAnalysis.segments) {
                    directReason(innerSeg)?.let { return it }
                }
                DangerousCommandGuard.blockReason(inner)?.let { return it }
            }
        }
        return null
    }

    /** 对单个命令段做灾难性 rm 目标判定，按目标类别收敛为单一谓词链。 */
    private fun directReason(seg: List<String>): String? {
        val rmInfo = ShellCommandParser.parseRmInfo(seg)
        if (!rmInfo.isRm) return null
        return rmInfo.targetPaths
            .map { PathNormalizer.normalize(it.trim()) }
            .map { trimTrailingSlash(it) }
            .firstNotNullOfOrNull { reasonForTarget(it) }
    }

    /** 单条归一后路径对应的拒绝原因；不命中返回 null。 */
    private fun reasonForTarget(normalized: String): String? = when {
        isRootTarget(normalized) -> MSG_ROOT
        isGlobTarget(normalized) -> MSG_GLOB
        isHomeTarget(normalized) -> MSG_HOME
        isWorkspaceTarget(normalized) -> MSG_WORKSPACE
        isTmpTarget(normalized) -> MSG_TMP
        isSysDirTarget(normalized) -> MSG_SYS
        else -> null
    }

    private fun trimTrailingSlash(p: String): String =
        if (p.length > 1) p.trimEnd('/') else p

    private fun isRootTarget(p: String): Boolean =
        p == "/" || p == "/*" || p == "//*"

    private fun isGlobTarget(p: String): Boolean =
        p == "*" || p == ".*" || p == "." || p == "./*" || p == ".." || p == "../.*"

    /** 家目录整体删除：归一后命中 /root 或 /root 下通配（含 ~、~/通配、$HOME、${HOME}）。 */
    private fun isHomeTarget(p: String): Boolean {
        val home = ContainerPaths.CONTAINER_HOME
        return p == home || p == "$home/*" || p == "/home" || p == "/home/*"
    }

    private fun isTmpTarget(p: String): Boolean =
        p == "/tmp" || p == "/tmp/*"

    private fun isSysDirTarget(p: String): Boolean {
        val sysDirs = setOf(
            "/bin", "/boot", "/dev", "/etc", "/home", "/lib", "/lib64", "/opt", "/proc",
            "/root", "/run", "/sbin", "/sys", "/usr", "/var"
        )
        return sysDirs.any { p == it || p.startsWith("$it/") || p.startsWith("$it/*") }
    }

    /** 工作区路径：/root/workspace 或 /workspace 及其子树（含通配、穿越）。 */
    private fun isWorkspaceTarget(p: String): Boolean {
        val bases = listOf(ContainerPaths.CONTAINER_WORKSPACE, "/workspace")
        if (p == "workspace" || p == "workspace/*") return true
        return bases.any { base ->
            p == base ||
                p == "$base/*" || p == "$base/.*" || p == "$base/." || p == "$base/.." ||
                p.startsWith("$base/../") || p.startsWith("$base/")
        }
    }
}
