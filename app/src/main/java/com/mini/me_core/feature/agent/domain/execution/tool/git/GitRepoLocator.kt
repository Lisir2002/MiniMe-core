package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository

/**
 * 仓库作用域定位器：解决「工具在工作区根执行、仓库在子目录」的作用域错配
 * （实战经验问题B：报「No configured push destination」把排查引向错误方向）。
 *
 * 职责（全部经 [CommandEngine] 执行，可 mockk 单测）：
 *  - clone 不接受 repo_dir（clone 目标由 dir 参数决定，只能落在工作区根）；
 *  - repo_dir 非空：纯校验（相对路径/禁逃逸/字符/层数）+ 域内 `git -C <dir> rev-parse` 验证；
 *  - repo_dir 为空且工作区根不是仓库：向下探测一层 .git，把发现的仓库路径写进报错，
 *    并给出 repo_dir 用法——报错即指引，而非干瘪的「不是 git 仓库」。
 *
 * 传导方式为 `git -C <相对路径>`（本地 PRoot 与远程 SSH 两模式语义一致；
 * 远程 SSH 模式 buildCdCommand 固定 cd ~/workspace，改 cwd 会静默失效）。
 */
internal class GitRepoLocator(
    private val commandEngine: CommandEngine,
    private val gitRepository: GitRepository,
    private val workspaceRepository: WorkspaceRepository
) {

    /**
     * 作用域预检。
     * @return 非 null = 拦截（message 给模型，code 供 UI/日志分类）；null = 放行。
     */
    suspend fun resolve(action: String, repoDir: String?): GitRemoteRejection? {
        val rejection = when {
            // clone 的目标目录由 dir 参数决定，与仓库作用域参数互斥
            action == "clone" && repoDir != null -> GitRemoteRejection(
                "clone 只能落在工作区根（目标目录用 dir 参数），不接受 repo_dir",
                "INVALID_REPO_DIR"
            )
            repoDir != null -> GitDiscipline.repoDirRejectionReason(repoDir)
                ?.let { GitRemoteRejection(it, "INVALID_REPO_DIR") }
                ?: verifyRepoDir(repoDir)
            !gitRepository.isRepo() -> GitRemoteRejection(
                GitDiscipline.notARepoMessage(workspaceRepository.currentPath(), discoverSubRepos()),
                "NOT_A_GIT_REPO"
            )
            else -> null
        }
        return rejection
    }

    /** 域内验证 repo_dir 指向的目录确实是 git 仓库（经同一 -C 传导）。 */
    private suspend fun verifyRepoDir(repoDir: String): GitRemoteRejection? {
        val cmd = "GIT_TERMINAL_PROMPT=0 git -C ${GitDiscipline.shellQuote(repoDir)} " +
            "rev-parse --is-inside-work-tree"
        val out = commandEngine.runCommandSync(
            cmd, workspaceRepository.currentPath(), QUERY_TIMEOUT_MS
        ).trim()
        return when {
            out.contains("cannot change to") || out.contains("Not a directory") ->
                GitRemoteRejection(
                    "repo_dir 指定的目录不存在：$repoDir（相对工作区根 ${workspaceRepository.currentPath()}）",
                    "NOT_A_GIT_REPO"
                )
            out != "true" -> GitRemoteRejection(
                GitDiscipline.notARepoMessage(
                    "${workspaceRepository.currentPath()}/$repoDir", emptyList()
                ),
                "NOT_A_GIT_REPO"
            )
            else -> null
        }
    }

    /**
     * 工作区根下一层 .git 探测（单条 shell，覆盖 .git 目录与 worktree/submodule 的 .git 文件）。
     * 探测失败按「未发现」降级，不阻断主流程。
     */
    private suspend fun discoverSubRepos(): List<String> {
        val out = runCatching {
            commandEngine.runCommandSync(
                "for d in */ ; do [ -e \"\$d/.git\" ] && echo \"\${d%/}\" ; done",
                workspaceRepository.currentPath(),
                QUERY_TIMEOUT_MS
            )
        }.getOrDefault("")
        return out.lines()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.isNotBlank() }
            .take(MAX_DISCOVERED)
    }

    private companion object {
        /** 轻量本地查询超时。 */
        const val QUERY_TIMEOUT_MS = 60_000L

        /** 探测结果截断，避免报错文案过长。 */
        const val MAX_DISCOVERED = 20
    }
}
