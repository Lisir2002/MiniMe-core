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
 *  - **作用域传导**：仓库在子目录时用 `repo_dir` 参数，`git -C <repo_dir>` 传导
 *    （本地 PRoot 与远程 SSH 两模式语义一致）；凭据链预检在 push/push_tag 前阻断，
 *    fetch/pull 等读操作仅成功附 credential_warning 不拦截（公开仓库无需鉴权）。
 *
 * 与 [GitOpsTool] 的分工：gitops 管本地纪律与发版体检（check_commit/suggest_commit/
 * release_check/release_tag/changelog），本工具管远端状态变更与同步。
 */
class GitRemoteTool @Inject constructor(
    private val commandEngine: CommandEngine,
    private val gitRepository: GitRepository,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool() {

    /** 仓库作用域定位器：解决「工具在工作区根执行、仓库在子目录」的作用域错配。 */
    private val locator = GitRepoLocator(commandEngine, gitRepository, workspaceRepository)

    /** 凭据链医生：把「could not read Username ... No such device or address」翻译成可操作诊断。 */
    private val doctor = GitCredentialDoctor(commandEngine)

    override val name = "git_remote"
    override val description = buildString {
        append("远端仓库操作：remote 管理（list/show/add/remove/set_url）、fetch/pull/push/push_tag/clone。")
        append("纪律：push 前自动校验提交信息合规；push_tag 仅合法语义化版本且远端不存在；")
        append("拒绝裸 --force（仅支持 force_with_lease）；凭据由容器 credential.helper 自动注入，URL 禁止内联账号密码。")
        append("repo_dir 可指定仓库子目录（相对工作区根，如 sub/repo），避免误报「当前工作区不是 git 仓库」；clone 不接受 repo_dir。")
        append("示例：{\"action\":\"push\",\"remote\":\"origin\",\"branch\":\"main\"}；")
        append("{\"action\":\"push\",\"repo_dir\":\"my/subrepo\",\"remote\":\"origin\"}；")
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
        "repo_dir" to ToolParameter(
            "repo_dir", ParameterType.STRING,
            "仓库作用域目录（相对工作区根，如 sub/repo），支持子目录里的 git 仓库，避免误报「当前工作区不是 git 仓库」。" +
                "仅相对路径、禁绝对/~/.. 段、≤4 层；clone 不接受本参数。",
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
        val repoDir = args["repo_dir"]?.jsonPrimitive?.contentOrNull
        // 作用域预检：clone 拒携 repo_dir；repo_dir 校验+域内验证；根非仓库时一层发现
        locator.resolve(action, repoDir)?.let { rejection ->
            return errorResult(rejection.message, rejection.code)
        }
        return try {
            when (action) {
                "remote_list" -> doRemoteList(repoDir)
                "remote_show" -> doRemoteShow(args, repoDir)
                "remote_add" -> doRemoteAdd(args, repoDir)
                "remote_remove" -> doRemoteRemove(args, repoDir)
                "remote_set_url" -> doRemoteSetUrl(args, repoDir)
                "fetch" -> doFetch(args, repoDir)
                "pull" -> doPull(repoDir)
                "push" -> doPush(args, repoDir)
                "push_tag" -> doPushTag(args, repoDir)
                "clone" -> doClone(args)
                else -> errorResult("未知 action：$action", "UNSUPPORTED_ACTION")
            }
        } catch (e: GitCommandFailureException) {
            // 凭据类失败用 doctor 追加可操作诊断（仅形态结论+修复命令，不含凭据原文）
            val raw = e.message ?: ""
            val diagnose = runCatching { doctor.diagnose(raw, repoDir) }.getOrNull()
            FileLogger.w(TAG, "git_remote[$action] 失败", e)
            val message = if (diagnose != null) "git 命令失败：$raw\n$diagnose" else "git 命令失败：$raw"
            errorResult(message, "GIT_COMMAND_FAILED")
        }
        // 其余异常一律上抛：ToolBatchExecutor 已有统一边界（CancellationException 重抛 +
        // Exception 兜底为 TOOL_EXECUTION_FAILED）。工具内不再泛型 catch——那会连
        // CancellationException 一并吞掉，破坏协程取消语义。
    }

    // ─────────────────────── remote 管理（本地 config） ───────────────────────

    private suspend fun doRemoteList(repoDir: String?): ToolResult {
        val raw = git("remote", "-v", repoDir = repoDir)
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

    private suspend fun doRemoteShow(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        val raw = gitChecked("remote", "show", remote, timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir)
        val branches = raw.lines()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.startsWith("*") || it.contains(" tracks ") }
            .take(50)
        // 读操作：凭据链异常仅作提示，不拦截（公开仓库无需鉴权）
        val remoteUrl = git("config", "--get", "remote.$remote.url", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim()
        val warning = readCredentialWarning(repoDir, remoteUrl)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("detail", JsonPrimitive(raw.trim().take(2000)))
                put("branch_lines", JsonArray(branches.map { JsonPrimitive(it) }))
                if (warning != null) put("credential_warning", JsonPrimitive(warning))
            }
        )
    }

    private suspend fun doRemoteAdd(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_add 缺少 url", "MISSING_URL")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote（仅字母数字与 . _ -）", "INVALID_REMOTE_NAME")
        }
        GitDiscipline.remoteUrlRejectionReason(url)?.let { reason ->
            return errorResult(reason, "INVALID_REMOTE_URL")
        }
        gitChecked("remote", "add", remote, url, repoDir = repoDir)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("url", JsonPrimitive(url))
                put("next_step", JsonPrimitive("凭据由 credential.helper 自动注入；可直接 fetch/pull"))
            }
        )
    }

    private suspend fun doRemoteRemove(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_remove 缺少 remote", "MISSING_REMOTE")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        gitChecked("remote", "remove", remote, repoDir = repoDir)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("removed", JsonPrimitive(remote))
            }
        )
    }

    private suspend fun doRemoteSetUrl(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("remote_set_url 缺少 url", "MISSING_URL")
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return errorResult("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        GitDiscipline.remoteUrlRejectionReason(url)?.let { reason ->
            return errorResult(reason, "INVALID_REMOTE_URL")
        }
        gitChecked("remote", "set-url", remote, url, repoDir = repoDir)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("remote", JsonPrimitive(remote))
                put("url", JsonPrimitive(url))
            }
        )
    }

    // ─────────────────────── 网络同步 ───────────────────────

    private suspend fun doFetch(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
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
        val out = gitChecked(argv, timeoutMs = NETWORK_TIMEOUT_MS, repoDir = repoDir)
        // 读操作：成功附凭据提示（指定远端时可解析 url，--all 则无法定位单一远端）
        val remoteUrl = remote?.let { git("config", "--get", "remote.$it.url", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim() }
        val warning = readCredentialWarning(repoDir, remoteUrl)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("output", JsonPrimitive(out.trim().take(1000)))
                put("next_step", JsonPrimitive("fetch 只更新远端跟踪引用，不改工作区；需要同步工作区用 pull"))
                if (warning != null) put("credential_warning", JsonPrimitive(warning))
            }
        )
    }

    private suspend fun doPull(repoDir: String?): ToolResult {
        val (branch, dirty) = if (repoDir != null) {
            val s = scopedStatus(repoDir)
            s.branch to s.dirty
        } else {
            val s = gitRepository.status()
            s.branch to s.hasChanges
        }
        if (dirty) {
            return errorResult(
                "工作区有未提交改动（pull 可能产生冲突）。先提交或 stash 再拉取。",
                "DIRTY_WORKTREE"
            )
        }
        val out = gitChecked("pull", timeoutMs = NETWORK_TIMEOUT_MS, repoDir = repoDir)
        // 读操作：凭据链异常仅作提示，不拦截
        val remoteUrl = git("config", "--get", "branch.$branch.remote", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim()
        val warning = readCredentialWarning(repoDir, remoteUrl)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("branch", JsonPrimitive(branch))
                put("output", JsonPrimitive(out.trim().take(1000)))
                if (warning != null) put("credential_warning", JsonPrimitive(warning))
            }
        )
    }

    /**
     * 推送。纪律：
     *  - HEAD 提交信息必须符合 Conventional Commits（自动生成提交除外）；
     *  - 拒绝裸 --force，仅 force_with_lease=true 时附加 --force-with-lease；
     *  - 无上游时首推建关联（--set-upstream）；
     *  - 推送前置条件（远端存在/上游追踪/凭据链）任一不满足即阻断，多问题聚合报告。
     */
    private suspend fun doPush(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull
        val branch = args["branch"]?.jsonPrimitive?.contentOrNull
        val forceWithLease = args["force_with_lease"]?.jsonPrimitive?.booleanOrNull ?: false

        // 纪律 1：HEAD 提交信息合规（与 .githooks/commit-msg 同源）
        val headSubject = git("log", "-1", "--pretty=format:%s", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim()
        GitDiscipline.commitSubjectViolation(headSubject)?.let { violation ->
            return errorResult(
                "推送被纪律拦截：HEAD 提交信息不合规——$violation。" +
                    "请先修正提交信息（amend）或用 gitops check_commit 校验后再推。",
                "COMMIT_DISCIPLINE_VIOLATION"
            )
        }

        // 纪律 2：推送前置条件（远端/上游/凭据）预检，阻断式；多问题聚合为 PUSH_PRECONDITION_BLOCKED
        pushPreflightRejection(args, repoDir)?.let { rejection ->
            return errorResult(rejection.message, rejection.code)
        }

        // 纪律 3：永不裸 force
        if (forceWithLease) {
            FileLogger.i(TAG, "push force_with_lease（远端有新提交时会拒绝覆盖）")
        }

        val statusBranch = branch ?: if (repoDir != null) scopedStatus(repoDir).branch else gitRepository.status().branch
        val argv = mutableListOf("push")
        if (forceWithLease) argv.add("--force-with-lease")
        if (remote != null && branch != null) {
            argv.addAll(listOf(remote, branch))
        } else if (remote != null) {
            argv.add(remote)
        }
        val out = gitChecked(argv, timeoutMs = NETWORK_TIMEOUT_MS, repoDir = repoDir)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("branch", JsonPrimitive(statusBranch))
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
     *  - 凭据预检早于 ls-remote（凭据坏了就不浪费网络往返）；
     *  - releases/tags 一律等用户显式拍板——工具级 ASK 已覆盖，此处再加双重确认字段。
     */
    private suspend fun doPushTag(args: Map<String, JsonElement>, repoDir: String?): ToolResult {
        val tag = args["tag"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("push_tag 缺少 tag", "MISSING_TAG")
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        pushTagRejection(tag, remote, repoDir)?.let { rejection ->
            return errorResult(rejection.message, rejection.code)
        }
        val out = gitChecked("push", remote, "refs/tags/$tag", timeoutMs = NETWORK_TIMEOUT_MS, repoDir = repoDir)
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
     * push 前置条件预检：远端存在 / 上游追踪 / 凭据链。任一不满足即拦截；
     * 多问题收集后聚合为 PUSH_PRECONDITION_BLOCKED 编号呈现，便于一次性修复。
     * 工作区是否脏（dirty）不纳入阻断——push 允许脏树是有意决策。
     * @return null = 放行；非 null = 首个拦截（聚合场景为汇总条目）。
     */
    private suspend fun pushPreflightRejection(args: Map<String, JsonElement>, repoDir: String?): GitRemoteRejection? {
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        if (!GitDiscipline.isValidRemoteName(remote)) {
            return GitRemoteRejection("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        val rejections = mutableListOf<GitRemoteRejection>()
        // 1. 远端是否存在（不存在是「No configured push destination」的最常见根因）
        val remoteUrl = git("config", "--get", "remote.$remote.url", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim()
        if (remoteUrl.isBlank()) {
            rejections += GitRemoteRejection(
                "远端 '$remote' 不存在（git remote -v 查不到）；这是报错「No configured push destination」的常见根因，" +
                    "请先 remote_add / remote_set_url 配置远端。",
                "NO_SUCH_REMOTE"
            )
        } else {
            // 2. 当前分支是否配置了上游追踪
            val upstreamRaw = git("rev-parse", "--abbrev-ref", "@{upstream}", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).lowercase()
            val upstreamOk = upstreamRaw.isNotBlank() && !upstreamRaw.contains("fatal") && !upstreamRaw.contains("no upstream")
            if (!upstreamOk) {
                rejections += GitRemoteRejection(
                    "当前分支没有配置上游追踪（@{upstream} 解析失败），git 会报「No configured push destination」。" +
                        "请显式指定 remote 与 branch（如 {\"remote\":\"origin\",\"branch\":\"main\"}），" +
                        "或首次推送加 --set-upstream（在工具中指定 remote+branch 即等效）。",
                    "NO_UPSTREAM"
                )
            }
            // 3. 凭据链预检（仅 https 形态；ssh 走密钥链由 doctor 内部跳过）
            doctor.precheck(remoteUrl, repoDir)?.let { rejections += it }
        }
        return if (rejections.size <= 1) {
            rejections.firstOrNull()
        } else {
            GitRemoteRejection(
                rejections.mapIndexed { i, r -> "${i + 1}. [${r.code}] ${r.message}" }
                    .joinToString("\n", prefix = "推送前置条件未满足（${rejections.size} 项），需一并修复：\n"),
                "PUSH_PRECONDITION_BLOCKED"
            )
        }
    }

    /**
     * push_tag 预检：tag 名合法、远端名合法、本地 tag 已存在、凭据链、远端无同名 tag。
     * 凭据预检早于 ls-remote——凭据坏了就不浪费网络往返。
     * @return 非 null = 拦截原因；null = 放行。
     */
    private suspend fun pushTagRejection(tag: String, remote: String, repoDir: String?): GitRemoteRejection? {
        val rejections = mutableListOf<GitRemoteRejection>()
        if (!GitDiscipline.TAG_REGEX.matches(tag)) {
            rejections += GitRemoteRejection(
                "tag 名不合法（需 vX.Y.Z / vX.Y.Z.N 或后缀 -rcN/-betaN/-alphaN/-devN 形态）：$tag",
                "INVALID_TAG_NAME"
            )
        }
        if (!GitDiscipline.isValidRemoteName(remote)) {
            rejections += GitRemoteRejection("非法远端名：$remote", "INVALID_REMOTE_NAME")
        }
        if (rejections.isEmpty()) {
            val localTags = if (repoDir != null) scopedTagNames(repoDir) else gitRepository.listTags().map { it.name }
            if (!localTags.contains(tag)) {
                rejections += GitRemoteRejection(
                    "本地不存在 tag '$tag'（先用 gitops release_tag 或 git tag 创建）",
                    "TAG_NOT_FOUND_LOCAL"
                )
            }
        }
        // 凭据预检早于 ls-remote：凭据坏了就不浪费网络往返
        if (rejections.isEmpty()) {
            val remoteUrl = git("config", "--get", "remote.$remote.url", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir).trim()
            doctor.precheck(remoteUrl, repoDir)?.let { rejections += it }
        }
        if (rejections.isEmpty() &&
            git("ls-remote", "--tags", remote, tag, timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir)
                .lines().any { it.contains("refs/tags/$tag") }
        ) {
            rejections += GitRemoteRejection("远端已存在 tag '$tag'，拒绝覆盖", "TAG_ALREADY_EXISTS_REMOTE")
        }
        return rejections.firstOrNull()
    }

    /**
     * clone 到工作区根下的新目录。治理：
     *  - URL 协议白名单 + 禁内联凭据；
     *  - 目录名仅字母数字与 . _ -（防路径逃逸），且不得已存在；
     *  - depth 可选浅克隆（1..1000）。
     * repo_dir 已由 locator 在 execute 阶段拦截（clone 不接受该参数）。
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
        // 读操作：凭据链异常仅作提示，不拦截（公开仓库无需鉴权）
        val warning = readCredentialWarning(null, url)
        return success(
            buildJsonObject {
                put("ok", JsonPrimitive(true))
                put("dir", JsonPrimitive(dir))
                put("url", JsonPrimitive(url))
                put("depth", JsonPrimitive(depth ?: 0))
                put("output", JsonPrimitive(out.trim().take(1000)))
                if (warning != null) put("credential_warning", JsonPrimitive(warning))
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

    // ─────────────────────── repo_dir 作用域读取（既有 17 个 root 用例零回归） ───────────────────────

    /** 域内工作区状态：branch（porcelain -b 首行）+ dirty（后续任一非空行）。 */
    private suspend fun scopedStatus(repoDir: String?): ScopedStatus {
        val porcelain = git("status", "--porcelain", "-b", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir)
        val first = porcelain.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val branch = if (first.startsWith("## ")) {
            first.removePrefix("## ").substringBefore("...").substringBefore(" [").trim()
        } else {
            "(unknown)"
        }
        val dirty = porcelain.lineSequence().drop(1).any { it.isNotBlank() }
        return ScopedStatus(branch, dirty)
    }

    /** 域内 tag 名列表（git tag --list），替代 gitRepository.listTags() 的 root 作用域。 */
    private suspend fun scopedTagNames(repoDir: String?): List<String> {
        return git("tag", "--list", timeoutMs = QUERY_TIMEOUT_MS, repoDir = repoDir)
            .lines().map { it.removeSuffix("\r").trim() }.filter { it.isNotBlank() }
    }

    /** 读操作成功后的凭据提示（不拦截）：凭据链异常时给出修复提示，公开仓库可忽略。 */
    private suspend fun readCredentialWarning(repoDir: String?, remoteUrl: String?): String? {
        if (remoteUrl.isNullOrBlank()) return null
        return doctor.precheck(remoteUrl, repoDir)?.message
    }

    // ─────────────────────── 工具方法 ───────────────────────

    private suspend fun gitChecked(
        vararg args: String,
        timeoutMs: Long = NETWORK_TIMEOUT_MS,
        repoDir: String? = null
    ): String = gitChecked(args.toList(), timeoutMs, repoDir)

    /**
     * 执行 git 命令（网络类统一 GIT_TERMINAL_PROMPT=0）。
     * 该环境变量让 git 在缺凭据/需交互时立即失败退出，而非挂起等待输入拖到超时——
     * 容器内无人值守场景的关键防护。
     * repoDir 非空时注入 `git -C <repoDir>`：本地 PRoot 与远程 SSH 两模式语义一致，
     * 且 git 从该目录做仓库发现，正确拾取子仓库本地 config（含其 credential.helper 段）。
     * List 重载供命令段动态拼装（fetch/clone 等）使用，免 spread 数组拷贝。
     */
    private suspend fun gitChecked(
        args: List<String>,
        timeoutMs: Long = NETWORK_TIMEOUT_MS,
        repoDir: String? = null
    ): String {
        val cmd = buildString {
            append("GIT_TERMINAL_PROMPT=0 git")
            if (repoDir != null) {
                append(" -C ")
                append(GitDiscipline.shellQuote(repoDir))
            }
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

    /** 只读执行（不判退出码，输出解析容错）。同样带 GIT_TERMINAL_PROMPT=0 与 -C 传导。 */
    private suspend fun git(
        vararg args: String,
        timeoutMs: Long = QUERY_TIMEOUT_MS,
        repoDir: String? = null
    ): String {
        val cmd = buildString {
            append("GIT_TERMINAL_PROMPT=0 git")
            if (repoDir != null) {
                append(" -C ")
                append(GitDiscipline.shellQuote(repoDir))
            }
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

/** 工具内预检拦截原因：message 直接给模型，code 供 UI/日志分类（internal 供 locator/doctor 复用）。 */
internal data class GitRemoteRejection(val message: String, val code: String)

/** repo_dir 作用域内读取的工作区状态（branch + dirty）。 */
private data class ScopedStatus(val branch: String, val dirty: Boolean)
