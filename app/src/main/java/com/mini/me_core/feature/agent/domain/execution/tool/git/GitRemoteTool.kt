package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ParameterType
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCapability
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.git.domain.GitCommandFailureException
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 远端仓库操作工具：remote 管理、fetch/pull/push/push_tag/clone，内建推送纪律。
 *
 * 设计原则（2025 业界 AI coding agent 安全实践）：
 *  - **凭据不落工具层**：容器内 `credential.helper=store` 经 GitCredentialsFileSync
 *    自动注入，本工具不接收、不传递任何 token/密码；remote URL 禁止内联 user:pass@。
 *  - **写操作显式审批**：permissionPolicy=ASK，一切触及远端的状态变更都经用户确认，
 *    只读查询（remote_list/remote_show/fetch）也在同一工具内由用户一次性授权。
 *  - **纪律先于执行**：push 前校验 HEAD 提交符合 Conventional Commits（与 .githooks
 *    同源规则）；push_tag 仅允许合法语义化版本且远端不存在同名 tag；拒绝裸 --force，
 *    仅允许 --force-with-lease。releases/tags 一律等用户显式拍板。
 *  - **容器健壮性**：网络命令统一 GIT_TERMINAL_PROMPT=0（缺凭据时立即失败而非挂起
 *    等输入拖到超时）+ 300s 超时（默认 120s 不够大仓库）；代理 env 由容器侧统一注入。
 *
 * 与 [GitOpsTool] 的分工：gitops 管本地纪律与发版体检（check_commit/suggest_commit/
 * release_check/release_tag/changelog），本工具管远端状态变更与同步。
 */
class GitRemoteTool @Inject constructor(
    private val commandEngine: CommandEngine,
    private val gitRepository: GitRepository,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool() {

    override val name = "git_remote"
    override val description = buildString {
        append("远端仓库操作：remote 管理（list/show/add/remove/set_url）、fetch/pull/push/push_tag/clone。")
        append("纪律：push 前自动校验提交信息合规；push_tag 仅合法语义化版本且远端不存在；")
        append("拒绝裸 --force（仅支持 force_with_lease）；凭据由容器 credential.helper 自动注入，URL 禁止内联账号密码。")
        append("示例：{\"action\":\"push\",\"remote\":\"origin\",\"branch\":\"main\"}；")
        append("{\"action\":\"clone\",\"url\":\"https://github.com/org/repo.git\",\"depth\":1}。")
    }
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(
        ToolCapability.NETWORK_READ,
        ToolCapability.NETWORK_WRITE,
        ToolCapability.WRITE_WORKSPACE
    )

    override val parameters = mapOf(
        "action" to ToolParameter(
            "action", ParameterType.STRING,
            "要执行的操作：remote_list / remote_show / remote_add / remote_remove / remote_set_url / fetch / pull / push / push_tag / clone",
            enum = listOf(
                "remote_list", "remote_show", "remote_add", "remote_remove", "remote_set_url",
                "fetch", "pull", "push", "push_tag", "clone"
            )
        ),
        "remote" to ToolParameter(
            "remote", ParameterType.STRING,
            "远端名（默认 origin）；remote_remove/remote_set_url/remote_show/push_tag 时使用",
            required = false
        ),
        "url" to ToolParameter(
            "url", ParameterType.STRING,
            "remote_add / remote_set_url / clone 时的远端地址（https/http/ssh/git@ 形态，禁止内联账号密码）",
            required = false
        ),
        "branch" to ToolParameter(
            "branch", ParameterType.STRING,
            "push 时要推送的分支（缺省推送当前分支上游）",
            required = false
        ),
        "tag" to ToolParameter(
            "tag", ParameterType.STRING,
            "push_tag 时要推送的本地 tag 名（需 vX.Y.Z / vX.Y.Z.N 或 -rcN 等后缀形态且远端不存在同名 tag）",
            required = false
        ),
        "dir" to ToolParameter(
            "dir", ParameterType.STRING,
            "clone 的目标目录名（仅字母数字与 . _ -，缺省从 URL 推断；只能落在工作区根下）",
            required = false
        ),
        "depth" to ToolParameter(
            "depth", ParameterType.INTEGER,
            "clone 的浅克隆深度（可选，大仓库省流量；缺省全量）",
            required = false
        ),
        "force_with_lease" to ToolParameter(
            "force_with_lease", ParameterType.BOOLEAN,
            "push 时是否使用 --force-with-lease（远端有他人新提交时拒绝覆盖）；裸 --force 一律禁止",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("缺少 action", "MISSING_ACTION")
        if (!gitRepository.isRepo()) {
            return errorResult("当前工作区不是 git 仓库", "NOT_A_GIT_REPO")
        }
        return try {
            when (action) {
                "remote_list" -> doRemoteList()
                "remote_show" -> doRemoteShow(args)
                "remote_add" -> doRemoteAdd(args)
                "remote_remove" -> doRemoteRemove(args)
                "remote_set_url" -> doRemoteSetUrl(args)
                "fetch" -> doFetch(args)
                "pull" -> doPull()
                "push" -> doPush(args)
                "push_tag" -> doPushTag(args)
                "clone" -> doClone(args)
                else -> errorResult("未知 action：$action", "UNSUPPORTED_ACTION")
            }
        } catch (e: GitCommandFailureException) {
            FileLogger.w(TAG, "git_remote[$action] 失败", e)
            errorResult("git 命令失败：${e.message}", "GIT_COMMAND_FAILED")
        }
        // 其余异常一律上抛：ToolBatchExecutor 已有统一边界（CancellationException 重抛 +
        // Exception 兜底为 TOOL_EXECUTION_FAILED）。工具内不再泛型 catch——那会连
        // CancellationException 一并吞掉，破坏协程取消语义。
    }

    // ─────────────────────── remote 管理（本地 config） ───────────────────────

    private suspend fun doRemoteList(): ToolResult {
        val raw = git("remote", "-v")
        val remotes = raw.lines()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                buildJsonObject {
                    put("name", JsonPrimitive(parts.getOrElse(0) { "" }))
                    put("url", JsonPrimitive(parts.getOrElse(1) { "" }))
                }
            }
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("count", JsonPrimitive(remotes.size))
                put("remotes", JsonArray(remotes))
            }
        )
    }

    private suspend fun doRemoteShow(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        val raw = gitChecked("remote", "show", remote, timeoutMs = QUERY_TIMEOUT_MS)
        val branches = raw.lines()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.startsWith("*") || it.contains(" tracks ") }
            .take(50)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("detail", JsonPrimitive(raw.trim().take(2000)))
                put("branch_lines", JsonArray(branches.map { JsonPrimitive(it) }))
            }
        )
    }

    private suspend fun doRemoteAdd(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_add 缺少 url", "MISSING_URL")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote（仅字母数字与 . _ -）", "INVALID_REMOTE_NAME")
        }
        GitDiscipline.remoteUrlRejectionReason(url)?.let { reason ->
            return errorResult(reason, "INVALID_REMOTE_URL")
        }
        gitChecked("remote", "add", remote, url)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("url", JsonPrimitive(url))
                put("next_step", JsonPrimitive("凭据由 credential.helper 自动注入；可直接 fetch/pull"))
            }
        )
    }

    private suspend fun doRemoteRemove(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_remove 缺少 remote", "MISSING_REMOTE")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        gitChecked("remote", "remove", remote)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("removed", JsonPrimitive(remote))
            }
        )
    }

    private suspend fun doRemoteSetUrl(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_set_url 缺少 url", "MISSING_URL")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        GitDiscipline.remoteUrlRejectionReason(url)?.let { reason ->
            return errorResult(reason, "INVALID_REMOTE_URL")
        }
        gitChecked("remote", "set-url", remote, url)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("url", JsonPrimitive(url))
            }
        )
    }

    // ─────────────────────── 网络同步 ───────────────────────

    private suspend fun doFetch(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull
        val argv = mutableListOf("fetch")
        if (remote != null) {
            if (!GitDiscipline.isValidRemoteName(remote)) {
                return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
            }
            argv.add(remote)
        } else {
            argv.add("--all")
        }
        argv.add("--prune")
        val out = gitChecked(argv, timeoutMs = NETWORK_TIMEOUT_MS)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("output", JsonPrimitive(out.trim().take(1000)))
                put("next_step", JsonPrimitive("fetch 只更新远端跟踪引用，不改工作区；需要同步工作区用 pull"))
            }
        )
    }

    private suspend fun doPull(): ToolResult {
        val status = gitRepository.status()
        if (status.hasChanges) {
            return errorResult(
                "工作区有未提交改动（${status.staged.size} staged / ${status.unstaged.size} unstaged / ${status.untracked.size} untracked），" +
                    "pull 可能产生冲突。先提交或 stash 再拉取。",
                "DIRTY_WORKTREE"
            )
        }
        val out = gitChecked("pull", timeoutMs = NETWORK_TIMEOUT_MS)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("branch", JsonPrimitive(status.branch))
                put("output", JsonPrimitive(out.trim().take(1000)))
            }
        )
    }

    /**
     * 推送。纪律：
     *  - HEAD 提交信息必须符合 Conventional Commits（自动生成提交除外）；
     *  - 拒绝裸 --force，仅 force_with_lease=true 时附加 --force-with-lease；
     *  - 无上游时首推建关联（--set-upstream）。
     */
    private suspend fun doPush(args: Map<String, JsonElement>): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull
        val branch = args["branch"]?.jsonPrimitive?.contentOrNull
        val forceWithLease = args["force_with_lease"]?.jsonPrimitive?.booleanOrNull ?: false

        // 纪律 1：HEAD 提交信息合规（与 .githooks/commit-msg 同源）
        val headSubject = git("log", "-1", "--pretty=format:%s").trim()
        GitDiscipline.commitSubjectViolation(headSubject)?.let { violation ->
            return errorResult(
                "推送被纪律拦截：HEAD 提交信息不合规——$violation。" +
                    "请先修正提交信息（amend）或用 gitops check_commit 校验后再推。",
                "COMMIT_DISCIPLINE_VIOLATION"
            )
        }

        // 纪律 2：永不裸 force
        if (forceWithLease) {
            FileLogger.i(TAG, "push force_with_lease（远端有新提交时会拒绝覆盖）")
        }

        val status = gitRepository.status()
        val argv = mutableListOf("push")
        if (forceWithLease) argv.add("--force-with-lease")
        if (remote != null && branch != null) {
            argv.addAll(listOf(remote, branch))
        } else if (remote != null) {
            argv.add(remote)
        }
        val out = gitChecked(argv, timeoutMs = NETWORK_TIMEOUT_MS)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("branch", JsonPrimitive(branch ?: status.branch))
                put("remote", JsonPrimitive(remote ?: "(当前上游)"))
                put("force_with_lease", JsonPrimitive(forceWithLease))
                put("output", JsonPrimitive(out.trim().take(1000)))
            }
        )
    }

    /**
     * 推送 tag。纪律：
     *  - tag 名必须合法语义化版本（vX.Y.Z / vX.Y.Z.N 及 -rcN 等后缀）；
     *  - 本地必须已存在该 tag；
     *  - 远端不得已有同名 tag（ls-remote 查询，拒绝覆盖）；
     *  - releases/tags 一律等用户显式拍板——工具级 ASK 已覆盖，此处再加双重确认字段。
     */
    private suspend fun doPushTag(args: Map<String, JsonElement>): ToolResult {
        val tag = args["tag"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("push_tag 缺少 tag", "MISSING_TAG")
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        pushTagRejection(tag, remote)?.let { rejection ->
            return errorResult(rejection.message, rejection.code)
        }
        val out = gitChecked("push", remote, "refs/tags/$tag", timeoutMs = NETWORK_TIMEOUT_MS)
        FileLogger.i(TAG, "push_tag $tag -> $remote（用户已审批）")
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("tag", JsonPrimitive(tag))
                put("remote", JsonPrimitive(remote))
                put("output", JsonPrimitive(out.trim().take(1000)))
                put("note", JsonPrimitive("tag 已推送，CI 发版流水线（如有）将被触发"))
            }
        )
    }

    /**
     * push_tag 预检：tag 名合法、远端名合法、本地 tag 已存在、远端无同名 tag。
     * @return 非 null = 拦截原因；null = 放行。when 单出口，避免多 return 分散纪律顺序。
     */
    private suspend fun pushTagRejection(tag: String, remote: String): GitRemoteRejection? = when {
        !GitDiscipline.TAG_REGEX.matches(tag) -> GitRemoteRejection(
            "tag 名不合法（需 vX.Y.Z / vX.Y.Z.N 或后缀 -rcN/-betaN/-alphaN/-devN 形态）：$tag",
            "INVALID_TAG_NAME"
        )
        !GitDiscipline.isValidRemoteName(remote) -> GitRemoteRejection(
            "非法远端名：$remote",
            "INVALID_REMOTE_NAME"
        )
        !gitRepository.listTags().any { it.name == tag } -> GitRemoteRejection(
            "本地不存在 tag '$tag'（先用 gitops release_tag 或 git tag 创建）",
            "TAG_NOT_FOUND_LOCAL"
        )
        git("ls-remote", "--tags", remote, tag, timeoutMs = QUERY_TIMEOUT_MS)
            .lines().any { it.contains("refs/tags/$tag") } -> GitRemoteRejection(
            "远端已存在 tag '$tag'，拒绝覆盖",
            "TAG_ALREADY_EXISTS_REMOTE"
        )
        else -> null
    }

    /**
     * clone 到工作区根下的新目录。治理：
     *  - URL 协议白名单 + 禁内联凭据；
     *  - 目录名仅字母数字与 . _ -（防路径逃逸），且不得已存在；
     *  - depth 可选浅克隆（1..1000）。
     */
    private suspend fun doClone(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("clone 缺少 url", "MISSING_URL")
        val requestedDir = args["dir"]?.jsonPrimitive?.contentOrNull
        val dir = requestedDir ?: url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git")
        val depth = args["depth"]?.jsonPrimitive?.intOrNull
        cloneRejection(url, dir, depth)?.let { rejection ->
            return errorResult(rejection.message, rejection.code)
        }
        val argv = mutableListOf("clone")
        if (depth != null) argv.addAll(listOf("--depth", depth.toString()))
        argv.addAll(listOf(url, dir))
        val out = gitChecked(argv, timeoutMs = NETWORK_TIMEOUT_MS)
        FileLogger.i(TAG, "clone $url -> $dir (depth=$depth)")
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("dir", JsonPrimitive(dir))
                put("url", JsonPrimitive(url))
                put("depth", JsonPrimitive(depth ?: 0))
                put("output", JsonPrimitive(out.trim().take(1000)))
            }
        )
    }

    /**
     * clone 预检：URL 合法、目标目录名合法且未占用、depth 在 1..1000。
     * @return 非 null = 拦截原因；null = 放行。when 单出口，与 [pushTagRejection] 同构。
     */
    private suspend fun cloneRejection(url: String, dir: String, depth: Int?): GitRemoteRejection? {
        val urlReason = GitDiscipline.remoteUrlRejectionReason(url)
        return when {
            urlReason != null -> GitRemoteRejection(urlReason, "INVALID_REMOTE_URL")
            !GitDiscipline.isValidCloneDir(dir) -> GitRemoteRejection(
                "非法目标目录名：'$dir'（仅字母数字与 . _ -，且不能已存在）",
                "INVALID_CLONE_DIR"
            )
            worktreeFileExists(dir) -> GitRemoteRejection(
                "目标目录 '$dir' 已存在于工作区，拒绝覆盖",
                "CLONE_DIR_EXISTS"
            )
            depth != null && (depth < 1 || depth > 1000) -> GitRemoteRejection(
                "depth 需在 1..1000",
                "INVALID_DEPTH"
            )
            else -> null
        }
    }

    // ─────────────────────── 工具方法 ───────────────────────

    private suspend fun gitChecked(
        vararg args: String,
        timeoutMs: Long = NETWORK_TIMEOUT_MS
    ): String = gitChecked(args.toList(), timeoutMs)

    /**
     * 执行 git 命令（网络类统一 GIT_TERMINAL_PROMPT=0）。
     * 该环境变量让 git 在缺凭据/需交互时立即失败退出，而非挂起等待输入拖到超时——
     * 容器内无人值守场景的关键防护。
     * List 重载供命令段动态拼装（fetch/clone 等）使用，免 spread 数组拷贝。
     */
    private suspend fun gitChecked(
        args: List<String>,
        timeoutMs: Long = NETWORK_TIMEOUT_MS
    ): String {
        val cmd = buildString {
            append("GIT_TERMINAL_PROMPT=0 git")
            args.forEach { append(' '); append(GitDiscipline.shellQuote(it)) }
        }
        val result = commandEngine.runCommandSyncWithExit(cmd, workspaceRepository.currentPath(), timeoutMs)
        if (result.exitCode == 0) return result.output
        throw GitCommandFailureException(
            result.output.ifBlank {
                if (result.timedOut) "git 命令超时（${timeoutMs / 1000}s）" else "git 退出码 ${result.exitCode}"
            }
        )
    }

    /** 只读执行（不判退出码，输出解析容错）。同样带 GIT_TERMINAL_PROMPT=0。 */
    private suspend fun git(
        vararg args: String,
        timeoutMs: Long = QUERY_TIMEOUT_MS
    ): String {
        val cmd = buildString {
            append("GIT_TERMINAL_PROMPT=0 git")
            args.forEach { append(' '); append(GitDiscipline.shellQuote(it)) }
        }
        return commandEngine.runCommandSync(cmd, workspaceRepository.currentPath(), timeoutMs)
    }

    /** 工作区根下是否已存在同名文件/目录（防 clone 覆盖）。 */
    private suspend fun worktreeFileExists(name: String): Boolean {
        val listing = commandEngine.runCommandSync(
            "ls -A",
            workspaceRepository.currentPath(),
            10_000L
        )
        return listing.lines().map { it.removeSuffix("\r").trim() }.any { it == name }
    }

    private fun success(obj: JsonObject): ToolResult = ToolResult.Success(obj)
    private fun errorResult(message: String, code: String): ToolResult =
        ToolResult.Error(message, code)

    private companion object {
        const val TAG = "GitRemoteTool"

        /** 网络类 git 操作超时：大仓库 clone/push 远超默认 120s。 */
        const val NETWORK_TIMEOUT_MS = 300_000L

        /** 轻量远端查询（remote show / ls-remote）超时。 */
        const val QUERY_TIMEOUT_MS = 60_000L
    }
}

/** 工具内预检拦截原因：message 直接给模型，code 供 UI/日志分类。 */
private data class GitRemoteRejection(val message: String, val code: String)
