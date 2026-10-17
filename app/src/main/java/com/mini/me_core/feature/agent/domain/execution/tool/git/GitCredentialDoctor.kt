package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.feature.agent.domain.container.CommandEngine

/**
 * 凭据链医生：把「could not read Username ... No such device or address」这类
 * 与根因完全不搭的报错，翻译成可操作的诊断（实战经验问题C/P0-3/P3-10）。
 *
 * 根因链：`~/.git-credentials` 被写成 helper 协议格式（kv 多行）而非 store 认的
 * URL 行格式 → store helper 静默返回空 → git 回退交互询问 → 无 TTY 直接失败。
 *
 * 职责（全部经 [CommandEngine] 执行，可 mockk 单测）：
 *  - [precheck]：网络操作前的本地预检（helper 链 → store 文件存在/空/格式 → host 匹配）；
 *  - [diagnose]：网络操作失败后的根因诊断（仅凭据类失败输出触发）。
 *
 * 安全边界：凭据文件内容只在内存中做格式/host 布尔判定，**禁入 FileLogger 与
 * 错误消息**（消息仅回形态结论与修复命令）；工具本身不写任何凭据文件——
 * 凭据不落工具层，修复命令交由用户在容器终端/App 凭据管理执行。
 */
internal class GitCredentialDoctor(
    private val commandEngine: CommandEngine
) {

    /**
     * https 远端凭据预检。ssh/git@ 形态返回 null（走密钥链，store 文件不适用）。
     * @param remoteUrl 目标远端 URL（http(s) 形态）
     * @param repoDir 仓库作用域目录（-C 传导，读取实际生效的 helper 链）
     * @return 非 null = 凭据链问题（含修复命令）；null = 放行
     */
    suspend fun precheck(remoteUrl: String, repoDir: String?): GitRemoteRejection? {
        if (isSshRemote(remoteUrl)) return null
        val host = GitDiscipline.remoteUrlHost(remoteUrl)
        val storeEntry = findStoreHelperEntry(repoDir)
            ?: return GitRemoteRejection(
                credentialHelperMissingMessage(),
                "CREDENTIAL_HELPER_MISSING"
            )
        val filePath = GitDiscipline.storeFilePath(storeEntry) ?: DEFAULT_CREDENTIAL_FILE
        val fileOut = readCredentialFile(filePath)
        return when {
            fileOut == null -> GitRemoteRejection(
                credentialFileMissingMessage(filePath),
                "CREDENTIAL_FILE_MISSING"
            )
            fileOut.isBlank() -> GitRemoteRejection(
                "凭据文件存在但为空：${GitDiscipline.shellQuote(filePath)}。" +
                    credentialFixHint(filePath, host),
                "CREDENTIAL_FILE_EMPTY"
            )
            else -> {
                val formatIssue = GitDiscipline.credentialFileFormatIssue(fileOut)
                if (formatIssue != null) {
                    GitRemoteRejection(
                        "凭据文件格式错误：$formatIssue。" + credentialFixHint(filePath, host),
                        "CREDENTIAL_FILE_FORMAT_INVALID"
                    )
                } else {
                    hostMatchRejection(fileOut, host, filePath)
                }
            }
        }
    }

    /**
     * 失败诊断：仅当输出疑似凭据类失败时返回诊断文案（否则 null，零额外命令）。
     * 内容不含凭据原文，仅形态结论 + 修复命令。
     */
    suspend fun diagnose(gitOutput: String, repoDir: String?): String? {
        if (!GitDiscipline.looksLikeCredentialFailure(gitOutput)) return null
        if (gitOutput.lowercase().let { it.contains("publickey") || it.contains("permission denied") && it.contains("ssh") }) {
            return buildString {
                append("【凭据诊断】失败输出疑似 SSH 密钥问题。请检查远端是否为 SSH 形态、")
                append("容器内是否已配置 SSH 私钥（App「凭据管理」→ SSH 密钥），")
                append("或改用 https 远端 + credential.helper=store 凭据链。")
            }
        }
        val storeEntry = findStoreHelperEntry(repoDir)
        val filePath = storeEntry?.let { GitDiscipline.storeFilePath(it) } ?: DEFAULT_CREDENTIAL_FILE
        val fileOut = readCredentialFile(filePath)
        return buildString {
            append("【凭据诊断】git 未能读取账号密码。实际排查结论：")
            if (storeEntry == null) {
                append("credential.helper 链中未配置 store；")
            } else {
                append("credential.helper=store 已配置（file=${GitDiscipline.shellQuote(filePath)}）；")
            }
            when {
                fileOut == null -> append("凭据文件不存在（容器重建/升级会丢失凭据文件，需重新配置）；")
                fileOut.isBlank() -> append("凭据文件为空；")
                else -> append("凭据文件格式问题：${GitDiscipline.credentialFileFormatIssue(fileOut) ?: "内容合法但可能无该 host 条目"}；")
            }
            append("修复步骤：")
            GitDiscipline.credentialStoreFixAdvice(null, filePath).forEach { append("\n  - $it") }
        }
    }

    // ─────────────────────── 内部实现 ───────────────────────

    /** ssh / git@ / git:// 形态走密钥链，store 文件预检不适用。 */
    private fun isSshRemote(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("git@") || u.startsWith("ssh://") || u.startsWith("git://")
    }

    /** 读取实际生效的 credential.helper 链，返回第一个 store 条目（无则 null）。 */
    private suspend fun findStoreHelperEntry(repoDir: String?): String? {
        val cmd = buildString {
            append("GIT_TERMINAL_PROMPT=0 git")
            if (repoDir != null) {
                append(" -C ")
                append(GitDiscipline.shellQuote(repoDir))
            }
            append(" config --get-all credential.helper")
        }
        val out = commandEngine.runCommandSync(cmd, null, QUERY_TIMEOUT_MS)
        return out.lines()
            .map { it.removeSuffix("\r").trim() }
            .firstOrNull { it.startsWith("store") }
    }

    /**
     * 读取 store 凭据文件内容。返回 null = 文件不存在。
     * 用固定标记区分「不存在」与「空文件」，避免把空输出误判为缺失。
     * 凭据文件路径由 helper 的 --file 决定（多为绝对路径），读取无需 repoDir 作用域。
     */
    private suspend fun readCredentialFile(filePath: String): String? {
        val quoted = GitDiscipline.shellQuote(filePath)
        val cmd = "if [ -f $quoted ]; then echo __CRED_FOUND__; cat $quoted; else echo __CRED_MISSING__; fi"
        val out = commandEngine.runCommandSync(cmd, null, QUERY_TIMEOUT_MS)
        if (out.contains("__CRED_MISSING__")) return null
        return out.substringAfter("__CRED_FOUND__", "").removePrefix("\n")
    }

    /** host 匹配：凭据文件须含 @<host> 条目；host 未知（多 remote 聚合场景）时跳过。 */
    private fun hostMatchRejection(
        content: String,
        host: String?,
        filePath: String
    ): GitRemoteRejection? {
        if (host == null) return null
        val hasHost = content.lines().any { it.contains("@$host") }
        return if (hasHost) {
            null
        } else {
            GitRemoteRejection(
                "凭据文件中没有 host '$host' 的条目（当前文件只覆盖其他 host）。" +
                    credentialFixHint(filePath, host),
                "CREDENTIAL_HOST_NOT_FOUND"
            )
        }
    }

    private fun credentialHelperMissingMessage(): String = buildString {
        append("credential.helper 未配置 store——缺凭据时 git 会回退到交互式询问用户名，")
        append("无 TTY 环境下直接失败并报 'could not read Username ... No such device or address'，")
        append("报错与真实根因（未配置凭据链）完全不搭。修复步骤：")
        GitDiscipline.credentialStoreFixAdvice(null, DEFAULT_CREDENTIAL_FILE).forEach { append("\n  - $it") }
    }

    private fun credentialFileMissingMessage(filePath: String): String = buildString {
        append("凭据文件不存在：${GitDiscipline.shellQuote(filePath)}")
        append("（容器重建/升级会静默丢失该文件，属于已知场景）。修复步骤：")
        GitDiscipline.credentialStoreFixAdvice(null, filePath).forEach { append("\n  - $it") }
    }

    private fun credentialFixHint(filePath: String, host: String?): String = buildString {
        append(" 修复步骤：")
        GitDiscipline.credentialStoreFixAdvice(host, filePath).forEach { append("\n  - $it") }
    }

    private companion object {
        /** 轻量本地查询超时。 */
        const val QUERY_TIMEOUT_MS = 60_000L

        /** store helper 未显式 --file 时的默认路径（容器 HOME=/root，见 ProotInvocationBuilder env 注入）。 */
        const val DEFAULT_CREDENTIAL_FILE = "/root/.git-credentials"
    }
}
