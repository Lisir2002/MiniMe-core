package com.mini.me_core.feature.git.data.repository

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.git.domain.GitCommandFailureException
import com.mini.me_core.feature.git.domain.GitGraphBuilder
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.git.domain.GitRepository.AllRefs
import com.mini.me_core.feature.git.domain.model.GitBranch
import com.mini.me_core.feature.git.domain.model.GitCommit
import com.mini.me_core.feature.git.domain.model.GitFileChange
import com.mini.me_core.feature.git.domain.model.GitGraph
import com.mini.me_core.feature.git.domain.model.GitGraphRef
import com.mini.me_core.feature.git.domain.model.GraphCommit
import com.mini.me_core.feature.git.domain.model.GitStatus
import com.mini.me_core.feature.git.domain.model.GitTag
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在容器内执行 git 命令并解析输出。
 *
 * 直接复用 LinuxContainerEngine.runCommandSync（cwd = 当前工作区，绑定挂载到 ~/workspace），
 * 不经 agent 工具链 / 权限引擎——Git 页是用户主动操作。所有输出解析为纯领域模型。
 *
 * 命令经 shellQuote 逐参数转义后拼成单条 `git ...` 字符串交给 `/bin/sh -c`，故格式串里的
 * `|`、`%(...)`、含空格的路径都能安全传递。
 */
@Suppress("TooManyFunctions", "ClassOrdering") // 行为保持搬迁：端口方法面大、companion/属性顺序沿用原类，不重排不拆分
@Singleton
class GitRepositoryImpl @Inject constructor(
    private val engine: CommandEngine,
    private val workspaceRepository: WorkspaceRepository
) : GitRepository {
    private companion object {
        /** 提交拓扑图每页加载条数。首批与每次「加载更多」都取这么多条，超过的需滚到底再拉。 */
        const val GRAPH_PAGE_SIZE = 100
    }
    /**
     * 执行一条 `git` 子命令，返回合并后的 stdout+stderr 文本。仅用于只读命令（status/branches/log/remote 等）：
     * 这些靠输出解析、容错，git 非零退出码不会让 UI 误判（解析得空罢了）。凭据由 `credential.helper=store`
     * 经落盘文件自动注入，不在此按命令塞 `http.extraHeader`。
     * 每条参数经 shellQuote 单引号转义，含空格/特殊字符的值（提交消息、含空格路径等）安全传递。
     */
    private suspend fun git(
        vararg args: String
    ): String = gitRaw(args)

    /**
     * 执行一条 `git` **写**子命令，据退出码判成败：非零（真实失败）抛 [GitCommandFailureException]
     * 携带 git 输出文本，上层据此如实显示「失败 + 原因」而非误报成功。
     * 空退出码（超时/异常）同样按失败抛，避免静默成功。
     */
    private suspend fun gitChecked(
        vararg args: String
    ): String {
        val cmd = buildString {
            append("git")
            args.forEach { append(' '); append(shellQuote(it)) }
        }
        val result = engine.runCommandSyncWithExit(cmd, workspaceRepository.currentPath())
        if (result.exitCode == 0) return result.output
        throw GitCommandFailureException(result.output.ifBlank { "git 退出码 ${result.exitCode}" })
    }

    /** 拼命令并跑（不判退出码），git 与 gitChecked 复用。 */
    private suspend fun gitRaw(args: Array<out String>): String {
        val cmd = buildString {
            append("git")
            args.forEach { append(' '); append(shellQuote(it)) }
        }
        return engine.runCommandSync(cmd, workspaceRepository.currentPath())
    }

    /** 当前工作区是否处于一个 git 工作树内。SSH 未连接等异常时返回 false 而非抛出，避免 UI 崩溃。 */
    override suspend fun isRepo(): Boolean {
        return runCatching { git("rev-parse", "--is-inside-work-tree").trim() == "true" }
            .getOrElse { false }
    }

    /** 容器是否就绪可执行 git 命令；未就绪时返回引导文案（供 Git 页在刷新前提示用户去终端页初始化）。 */
    override fun notReadyHint(): String? = engine.notReadyHint()

    /** 在当前工作区初始化 git 仓库（`git init`）。据退出码判成败，失败抛 [GitCommandFailureException]。 */
    override suspend fun initRepo(): String = gitChecked("init")

    /** 是否已配置至少一个远程仓库（`git remote` 输出非空）。拉取/推送前据此门控。 */
    override suspend fun hasRemote(): Boolean = git("remote").trim().isNotEmpty()

    /** `git status` 聚合视图。 */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements") // 逐字搬迁，porcelain 判定分支与顺序扫描保持，不拆函数
    override suspend fun status(): GitStatus {
        val raw = git("status", "--porcelain=v1", "-b")
        val lines = raw.split('\n').map { it.removeSuffix("\r") }

        var branch = "(unknown)"
        var ahead = 0
        var behind = 0
        val staged = mutableListOf<GitFileChange>()
        val unstaged = mutableListOf<GitFileChange>()
        val untracked = mutableListOf<String>()

        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("## ")) {
                val header = line.removePrefix("## ")
                // 形如 "main" / "main...origin/main" / "main...origin/main [ahead 2, behind 1]"
                val tracking = header.substringBefore(" [")
                branch = tracking.substringBefore("...").ifBlank { tracking }
                val bracket = header.substringAfter(" [", "")
                if (bracket.isNotBlank()) {
                    bracket.removeSuffix("]").split(",").forEach { tok ->
                        val t = tok.trim()
                        val n = t.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (t.startsWith("ahead")) ahead = n
                        else if (t.startsWith("behind")) behind = n
                    }
                }
                continue
            }
            if (line.length < 3) continue
            val x = line[0]
            val y = line[1]
            val rawPath = line.substring(3)
            // 重命名形如 "old -> new"，展示新路径。
            val path = unquotePorcelainPath(rawPath.substringAfter(" -> ").trim())

            if (x == '?' && y == '?') {
                untracked.add(path)
                continue
            }
            if (x != ' ' && x != '?') {
                staged.add(GitFileChange(path, x.toString(), staged = true))
            }
            if (y != ' ' && y != '?') {
                unstaged.add(GitFileChange(path, y.toString(), staged = false))
            }
        }
        return GitStatus(branch, ahead, behind, staged, unstaged, untracked)
    }

    /** 本地 + 远程分支列表，当前分支高亮。 */
    override suspend fun branches(): List<GitBranch> = loadAllRefs().branches

    /**
     * 仅拉本地分支的 refs-by-commit 映射（`for-each-ref refs/heads`，大仓库亚秒级），
     * 供首屏轻量快照的 graph 标注当前分支位置。远程分支与标签标注延迟到 BRANCHES tab 全量加载。
     */
    @Suppress("LoopWithTooManyJumpStatements") // 顺序扫描需提前终止，行为保持
    override suspend fun localRefsOnly(): Map<String, List<GitGraphRef>> = withContext(Dispatchers.Default) {
        val raw = runCatching {
            git("for-each-ref", "--format=%(refname:short)|%(objectname)|%(HEAD)|%(refname)", "refs/heads")
        }.getOrDefault("")
        if (raw.isBlank() || raw.startsWith("fatal:")) return@withContext emptyMap()
        val result = mutableMapOf<String, MutableList<GitGraphRef>>()
        for (line in raw.split('\n')) {
            val l = line.removeSuffix("\r").trim()
            if (l.isBlank()) continue
            val parts = l.split('|', limit = 4)
            if (parts.size < 4) continue
            val isHead = parts[2].trim() == "*"
            result.getOrPut(parts[1]) { mutableListOf() }
                .add(GitGraphRef(parts[0], isBranch = true, isCurrent = isHead, isRemote = false))
        }
        result
    }

    /** 最近 [limit] 条提交。 */
    override suspend fun log(limit: Int): List<GitCommit> {
        val raw = git("log", "--pretty=format:%H|%h|%an|%ar|%s", "-n", limit.toString())
        if (raw.isBlank() || raw.startsWith("fatal:")) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.removeSuffix("\r").split('|', limit = 5)
            if (parts.size < 5) null
            else GitCommit(parts[0], parts[1], parts[2], parts[3], parts[4])
        }
    }

    override suspend fun graph(
        limit: Int,
        refs: Map<String, List<GitGraphRef>>
    ): GitGraph =
        graphAppend(emptyList(), refs, limit)

    override suspend fun graphAppend(
        existingCommits: List<GraphCommit>,
        refs: Map<String, List<GitGraphRef>>,
        limit: Int
    ): GitGraph = withContext(Dispatchers.Default) {
        val skip = existingCommits.size
        val logRaw = runCatching { git("log", "--pretty=format:%H|%h|%an|%ar|%s|%P", "--skip", skip.toString(), "-n", limit.toString()) }
            .getOrDefault("")
        if (logRaw.isBlank() || logRaw.startsWith("fatal:")) {
            return@withContext if (existingCommits.isEmpty()) GitGraph.EMPTY
            else GitGraphBuilder.buildGraph(existingCommits, refs, hasMore = false)
        }

        val newCommits = GitGraphBuilder.parseGraphCommits(logRaw)
        if (newCommits.isEmpty() && existingCommits.isEmpty()) return@withContext GitGraph.EMPTY

        // 合并去重：新批次理论上不会与已加载重复（--skip 跳过已加载数量），但防御性按 hash 去重保顺序。
        val existingHashes = existingCommits.mapTo(HashSet()) { it.hash }
        val merged = existingCommits + newCommits.filter { it.hash !in existingHashes }
        GitGraphBuilder.buildGraph(merged, refs, hasMore = newCommits.size >= limit)
    }

    @Suppress("LoopWithTooManyJumpStatements") // 顺序扫描需提前终止，行为保持
    override suspend fun loadAllRefs(): AllRefs = withContext(Dispatchers.Default) {
        val raw = runCatching {
            git(
                "for-each-ref",
                "--format=%(refname:short)|%(objectname)|%(HEAD)|%(refname)",
                "refs/heads", "refs/remotes", "refs/tags"
            )
        }.getOrDefault("")
        if (raw.isBlank() || raw.startsWith("fatal:")) return@withContext AllRefs(emptyList(), emptyList(), emptyMap())
        val branches = mutableListOf<GitBranch>()
        val tags = mutableListOf<GitTag>()
        val refsByCommit = mutableMapOf<String, MutableList<GitGraphRef>>()
        for (line in raw.split('\n')) {
            val l = line.removeSuffix("\r").trim()
            if (l.isBlank()) continue
            val parts = l.split('|', limit = 4)
            if (parts.size < 4) continue
            val name = parts[0]
            val hash = parts[1]
            val isHead = parts[2].trim() == "*"
            val refname = parts[3]
            val isRemote = refname.startsWith("refs/remotes")
            val isBranch = refname.startsWith("refs/heads") || isRemote
            refsByCommit.getOrPut(hash) { mutableListOf() }
                .add(GitGraphRef(name, isBranch, isHead && !isRemote, isRemote))
            if (isBranch) {
                branches.add(GitBranch(name, current = isHead && !isRemote, remote = isRemote))
            } else {
                // 标签：objectname 取短哈希（前 7 位）与原 listTags 行为一致。
                tags.add(GitTag(name, hash.take(7)))
            }
        }
        // for-each-ref 默认按 refname 排序（本地分支、远程分支、标签各自有序），标签再倒序与原行为对齐。
        tags.reverse()
        AllRefs(branches, tags, refsByCommit)
    }

    /**
     * 某次提交改动的文件清单。用 `diff-tree --root` 以兼容无父的根提交；`--no-renames` 让重命名
     * 退化为「删除旧 + 新增新」，状态码取首字符即可复用 GitFileChange。
     * 返回空列表表示该提交无文件改动（如空提交）。
     */
    override suspend fun commitFiles(hash: String): List<GitFileChange> {
        val raw = git("diff-tree", "--no-commit-id", "-r", "--root", "--name-status", "--no-renames", hash)
        return withContext(Dispatchers.Default) {
            raw.lineSequence().mapNotNull { line ->
                val l = line.removeSuffix("\r").trim()
                val tab = l.indexOf('\t')
                if (l.isBlank() || tab < 0) null
                else {
                    val status = l.substring(0, tab).trim()
                    val path = l.substring(tab + 1).trim()
                    GitFileChange(path, status, staged = false)
                }
            }.toList()
        }
    }

    override suspend fun stage(path: String) = gitChecked("add", "--", path)
    override suspend fun unstage(path: String) = gitChecked("reset", "HEAD", "--", path)
    override suspend fun stageAll() = gitChecked("add", "-A")
    override suspend fun unstageAll() = gitChecked("reset")
    override suspend fun commit(message: String) = gitChecked("commit", "-m", message)

    /**
     * 拉取：直接 `git pull`，凭据由容器 `credential.helper` 链自动注入。
     * remote 不存在或真实失败由 gitChecked 据退出码抛 [GitCommandFailureException]，上层 toast。
     */
    override suspend fun pull(): String = gitChecked("pull")

    /**
     * 推送：有上游则 `git push`；当前分支无上游时自动 `git push --set-upstream <remote> <branch>` 首推建关联。
     * remote 取 `git remote` 首个；分支取 `git rev-parse --abbrev-ref HEAD`。凭据仍由容器 credential.helper 链兜底注入。
     */
    override suspend fun push(): String {
        val hasUpstream = runCatching { git("rev-parse", "--abbrev-ref", "@{upstream}").trim() }
            .getOrDefault("")
            .takeIf { it.isNotBlank() && it != "HEAD" && !it.startsWith("fatal") } != null
        if (hasUpstream) return gitChecked("push")
        val remote = git("remote").split('\n').firstOrNull { it.removeSuffix("\r").isNotBlank() }?.removeSuffix("\r")?.trim()
            ?: throw GitCommandFailureException("未配置远程仓库")
        val branch = git("rev-parse", "--abbrev-ref", "HEAD").removeSuffix("\r").trim()
            .takeIf { it.isNotBlank() && it != "HEAD" }
            ?: throw GitCommandFailureException("无法确定当前分支（处于 detached HEAD）")
        return gitChecked("push", "--set-upstream", remote, branch)
    }

    /** 本地标签列表，按创建时间倒序（最新在前）。 */
    override suspend fun listTags(): List<GitTag> = loadAllRefs().tags

    /**
     * 创建新分支。name 为新分支名；startPoint 为基准分支名（null/空 → 从当前 HEAD）；
     * checkout=true 则创建并切换（`git checkout -b`），否则仅创建不切换（`git branch`）。
     */
    override suspend fun createBranch(name: String, startPoint: String?, checkout: Boolean): String {
        return if (checkout) {
            if (startPoint.isNullOrBlank()) gitChecked("checkout", "-b", name)
            else gitChecked("checkout", "-b", name, startPoint)
        } else {
            if (startPoint.isNullOrBlank()) gitChecked("branch", name)
            else gitChecked("branch", name, startPoint)
        }
    }

    /** 安全删除本地分支（`git branch -d`）：仅删除已合并到上游的分支，未合并时 git 报错抛 [GitCommandFailureException]。 */
    override suspend fun deleteBranch(name: String): String = gitChecked("branch", "-d", name)

    /** 重命名本地分支。当前分支也可重命名：传单参数 `git branch -m <new>`。 */
    override suspend fun renameBranch(oldName: String, newName: String): String =
        gitChecked("branch", "-m", oldName, newName)

    /** 创建轻量标签（`git tag <name>`），指向当前 HEAD。附注标签需消息且交互复杂，暂只做轻量标签。 */
    override suspend fun createTag(name: String): String = gitChecked("tag", name)

    /** 删除本地标签（`git tag -d <name>`）。 */
    override suspend fun deleteTag(name: String): String = gitChecked("tag", "-d", name)

    /** 删除远程分支（`git push <remote> --delete <branch>`）。ref 形如 `origin/feature`，拆出 remote 与分支名。 */
    override suspend fun deleteRemoteBranch(ref: String): String {
        val remote = ref.substringBefore('/', "origin")
        val branch = ref.substringAfter('/', ref)
        return gitChecked("push", remote, "--delete", branch)
    }

    /**
     * 切换到指定分支或标签。branch 可以是本地分支名、远程分支名或 tag 名。
     * 远程分支用 `git checkout -b <local> <remote>` 创建本地跟踪分支，去掉远程前缀（如 origin/）。
     */
    override suspend fun checkout(branch: String, isRemote: Boolean): String {
        return if (isRemote) {
            val localName = branch.substringAfter('/', branch)
            gitChecked("checkout", "-b", localName, "--track", branch)
        } else {
            gitChecked("checkout", branch)
        }
    }

    /**
     * 写入提交署名，**优先项目级**：当前工作区（~/workspace/.git/config）已有项目级署名时写 local，
     * 否则写 global（容器 `GIT_CONFIG_GLOBAL=/root/.minime/.gitconfig`，持久挂载，跨 rootfs 升级不丢）作默认。
     * 空值跳过对应项不动现有配置。由 UI 在用户保存身份时显式调用。
     */
    override suspend fun setUserIdentity(name: String, email: String) {
        if (name.isNotBlank()) gitChecked("config", if (hasLocalConfig("user.name")) "--local" else "--global", "user.name", name)
        if (email.isNotBlank()) gitChecked("config", if (hasLocalConfig("user.email")) "--local" else "--global", "user.email", email)
    }

    /** 当前工作区是否有项目级（`--local`）配置值。非 git 仓库或无值时返回 false。 */
    private suspend fun hasLocalConfig(key: String): Boolean =
        runCatching { git("config", "--local", "--get", key).trim() }.getOrDefault("").isNotBlank()

    /** 读取 git 当前实际生效的 user.name（按 git 解析顺序：local→global→system）。失败返回空串。 */
    override suspend fun getUserName(): String =
        runCatching { git("config", "--get", "user.name").trim() }.getOrDefault("").removeSuffix("\r")

    /** 读取 git 当前实际生效的 user.email（local→global→system）。失败返回空串。 */
    override suspend fun getUserEmail(): String =
        runCatching { git("config", "--get", "user.email").trim() }.getOrDefault("").removeSuffix("\r")

    /**
     * 写入仓库地址 remote.origin.url，**仅写项目级**：remote.origin.url 是单个仓库的远端地址，
     * 与 user.name/email 这种「身份默认值」不同——它绝不该写 global。
     * 故此处始终写 `--local`（当前工作区须是 git 仓库），非仓库时抛 [GitCommandFailureException] 提示用户。
     * 同时顺手清掉全局可能残留的 remote.origin.url（历史版本误写下的），一次性消除污染。
     */
    override suspend fun setRepoUrl(url: String) {
        if (url.isNotBlank()) {
            gitChecked("config", "--local", "remote.origin.url", url)
        } else {
            // 空值删除 local 的 remote.origin.url，git config --unset 对不存在的 key 返回非零但不影响其它配置
            runCatching { gitChecked("config", "--local", "--unset", "remote.origin.url") }
        }
        // 清除全局残留的 remote.origin.url（历史误写），避免污染后续 git clone 等命令。
        runCatching { git("config", "--global", "--unset", "remote.origin.url") }
    }

    /** 读取 git 当前实际生效的 remote.origin.url（local→global→system）。失败返回空串。 */
    override suspend fun getRepoUrl(): String =
        runCatching { git("config", "--get", "remote.origin.url").trim() }.getOrDefault("").removeSuffix("\r")

    /**
     * 读取指定 ref（提交/分支/标签）下某文件的完整内容（`git show <ref>:<path>`）。
     * 用于提交文件 diff：取 `<hash>^:<path>`（改动前）与 `<hash>:<path>`（改动后）对比。
     * 文件在指定 ref 不存在时（如新增文件的首个提交）git 报错输出 `fatal:`，此处检测到即返回空串，
     * 上层据空串判定为「新增/删除」，整个文件按全增或全删呈现。
     */
    override suspend fun showFileContent(ref: String, path: String): String {
        val out = git("show", "$ref:$path")
        // git show 对不存在的路径输出 fatal 到 stderr，runCommandSync 合并了 stdout+stderr。
        // 检测到 fatal 前缀视为该版本无此文件，返回空串让 diff 按全增/全删处理。
        return if (out.startsWith("fatal:") || out.startsWith("error:")) "" else out
    }

    /**
     * 读取工作区当前文件内容。用于工作区改动 diff：与 `HEAD:<path>` 对比看出未暂存的改动。
     * 文件不存在或读取失败返回空串。经容器内直接读文件而非 git show，因为工作区文件即当前内容。
     */
    override suspend fun worktreeFileContent(path: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                java.io.File(workspaceRepository.currentPath(), path).takeIf { it.isFile }?.readText() ?: ""
            }.getOrDefault("")
        }

    /**
     * porcelain v1 对含引号/反斜杠/控制字符的路径会整体加引号并做 C 风格转义
     * （如 `"a\"b.txt"`、`"a\tb.txt"`、八进制 `\NNN`），这里做反向解析还原真实路径。
     * 未加引号的普通路径（含空格）原样返回。
     */
    private fun unquotePorcelainPath(raw: String): String {
        if (!raw.startsWith("\"")) return raw
        val inner = raw.removeSurrounding("\"")
        val sb = StringBuilder(inner.length)
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length) {
                when (val n = inner[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    in '0'..'7' -> {
                        // 八进制 \NNN（最多 3 位）
                        val end = minOf(i + 4, inner.length)
                        val octal = inner.substring(i + 1, end).takeWhile { it in '0'..'7' }
                        if (octal.length == 3) {
                            sb.append(octal.toInt(8).toChar())
                            i += 1 + octal.length
                        } else {
                            sb.append(c); i += 1
                        }
                    }
                    else -> { sb.append(c); i += 1 }
                }
            } else {
                sb.append(c); i += 1
            }
        }
        return sb.toString()
    }

    /**
     * 对单个 shell 参数做单引号转义。含「安全字符」之外的字符（空格、`|`、`$`、反引号、`*` 等）时
     * 整体包单引号，内嵌单引号用 `'\''` 关闭-转义-重开。格式串（带 `|`、`%(...)`）、含空格路径、
     * 提交消息均由此安全传递。注意 `|` 是 shell 管道符，**不可**列入安全集。
     */
    private fun shellQuote(arg: String): String {
        if (arg.isEmpty()) return "''"
        if (arg.all { it.isLetterOrDigit() || it in "_.@/:=+,%-" }) return arg
        return "'" + arg.replace("'", "'\\''") + "'"
    }
}
