package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.container.CommandResult
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.git.domain.model.GitStatus
import com.mini.me_core.feature.git.domain.model.GitTag
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 远端仓库操作工具回归：纪律拦截（提交合规/tag 治理/URL 治理/目录治理）、
 * 命令拼装（GIT_TERMINAL_PROMPT=0 / -C 作用域 / force_with_lease / refs/tags）、
 * 凭据链预检与诊断、失败语义。
 */
class GitRemoteToolTest {

    private lateinit var engine: CommandEngine
    private lateinit var gitRepository: GitRepository
    private lateinit var workspace: WorkspaceRepository
    private lateinit var tool: GitRemoteTool

    private val cleanStatus = GitStatus(
        branch = "main", ahead = 0, behind = 0,
        staged = emptyList(), unstaged = emptyList(), untracked = emptyList()
    )

    @Before
    fun setUp() {
        engine = mockk(relaxed = true)
        gitRepository = mockk(relaxed = true)
        workspace = mockk(relaxed = true)
        coEvery { gitRepository.isRepo() } returns true
        coEvery { gitRepository.status() } returns cleanStatus
        coEvery { workspace.currentPath() } returns "/workspace"
        // happy-path 默认：读命令返回空；以下精确桩覆盖新加的预检链路
        coEvery { engine.runCommandSync(any(), any(), any()) } returns ""
        coEvery { engine.runCommandSync(match { it.contains("log -1") }, any(), any()) } returns "feat(agent): 新增 git_remote 工具"
        coEvery { engine.runCommandSync(match { it.contains("config --get remote.origin.url") }, any(), any()) } returns "https://github.com/org/repo.git"
        coEvery { engine.runCommandSync(match { it.contains("rev-parse --abbrev-ref") }, any(), any()) } returns "origin/main"
        coEvery { engine.runCommandSync(match { it.contains("is-inside-work-tree") }, any(), any()) } returns "true"
        coEvery { engine.runCommandSync(match { it.contains("config --get-all credential.helper") }, any(), any()) } returns "store --file=/root/.git-credentials"
        // 凭据文件读取：默认返回 __CRED_FOUND__ + 合法 URL 行（credential 链健康）
        coEvery { engine.runCommandSync(match { it.contains("__CRED_MISSING__") }, any(), any()) } returns "__CRED_FOUND__\nhttps://user:token@github.com"
        tool = GitRemoteTool(engine, gitRepository, workspace)
    }

    private fun args(vararg pairs: Pair<String, Any?>): Map<String, kotlinx.serialization.json.JsonElement> =
        buildJsonObject {
            pairs.forEach { (k, v) ->
                when (v) {
                    is String? -> put(k, JsonPrimitive(v ?: ""))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is Int -> put(k, JsonPrimitive(v))
                }
            }
        }

    private fun ToolResult.asErrorCode(): String? =
        (this as? ToolResult.Error)?.code

    private fun ToolResult.asSuccessData() =
        (this as? ToolResult.Success)?.data

    private fun ToolResult.asErrorData() =
        (this as? ToolResult.Error)?.message

    // ───────────── 既有 happy-path 用例（随预检链路迁移） ─────────────

    @Test
    fun push_compliantHeadCommit_executes() = runBlocking {
        coEvery { engine.runCommandSyncWithExit(match { it.contains("git push") }, any(), any()) } returns
            CommandResult("To github.com:org/repo.git", 0)

        val result = tool.execute(args("action" to "push"))

        assertTrue("合规提交应放行", result is ToolResult.Success)
        coVerify {
            engine.runCommandSyncWithExit(
                match { it.startsWith("GIT_TERMINAL_PROMPT=0 git push") },
                any(), any()
            )
        }
    }

    @Test
    fun push_nonCompliantHeadCommit_blockedByDiscipline() = runBlocking {
        coEvery { engine.runCommandSync(any(), any(), any()) } returns "随便写的提交信息"

        val result = tool.execute(args("action" to "push"))

        assertEquals("COMMIT_DISCIPLINE_VIOLATION", result.asErrorCode())
        // 纪律拦截时不得触达任何写命令
        coVerify(exactly = 0) { engine.runCommandSyncWithExit(any(), any(), any()) }
    }

    @Test
    fun push_forceWithLease_flagForwarded() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("log -1") }, any(), any()) } returns "fix: 修复"
        coEvery { engine.runCommandSyncWithExit(any(), any(), any()) } returns CommandResult("forced update", 0)

        tool.execute(args("action" to "push", "force_with_lease" to true))

        coVerify {
            engine.runCommandSyncWithExit(
                match { it.contains("--force-with-lease") },
                any(), any()
            )
        }
    }

    @Test
    fun push_gitFailure_mapsToError() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("log -1") }, any(), any()) } returns "feat: x"
        coEvery { engine.runCommandSyncWithExit(any(), any(), any()) } returns
            CommandResult("fatal: Authentication failed", 128)

        val result = tool.execute(args("action" to "push"))

        assertEquals("GIT_COMMAND_FAILED", result.asErrorCode())
    }

    @Test
    fun pushTag_validLocalTagNotOnRemote_pushes() = runBlocking {
        coEvery { gitRepository.listTags() } returns listOf(GitTag("v1.2.3", "abc1234"))
        // ls-remote 查询（runCommandSync）返回空 = 远端无同名 tag
        coEvery { engine.runCommandSync(match { it.contains("ls-remote") }, any(), any()) } returns ""
        coEvery { engine.runCommandSyncWithExit(match { it.contains("push") }, any(), any()) } returns
            CommandResult("new tag pushed", 0)

        val result = tool.execute(args("action" to "push_tag", "tag" to "v1.2.3"))

        assertTrue(result is ToolResult.Success)
        coVerify {
            engine.runCommandSyncWithExit(
                match { it.contains("refs/tags/v1.2.3") },
                any(), any()
            )
        }
    }

    @Test
    fun pushTag_remoteAlreadyHasTag_rejected() = runBlocking {
        coEvery { gitRepository.listTags() } returns listOf(GitTag("v1.2.3", "abc1234"))
        coEvery { engine.runCommandSync(match { it.contains("ls-remote") }, any(), any()) } returns
            "abc1234\trefs/tags/v1.2.3"

        val result = tool.execute(args("action" to "push_tag", "tag" to "v1.2.3"))

        assertEquals("TAG_ALREADY_EXISTS_REMOTE", result.asErrorCode())
    }

    @Test
    fun pushTag_invalidTagName_rejected() = runBlocking {
        val result = tool.execute(args("action" to "push_tag", "tag" to "release-1"))

        assertEquals("INVALID_TAG_NAME", result.asErrorCode())
    }

    @Test
    fun pushTag_localTagMissing_rejected() = runBlocking {
        coEvery { gitRepository.listTags() } returns emptyList()

        val result = tool.execute(args("action" to "push_tag", "tag" to "v9.9.9"))

        assertEquals("TAG_NOT_FOUND_LOCAL", result.asErrorCode())
    }

    @Test
    fun remoteAdd_inlineCredentials_rejected() = runBlocking {
        val result = tool.execute(
            args("action" to "remote_add", "remote" to "origin", "url" to "https://user:pass@github.com/org/repo.git")
        )

        assertEquals("INVALID_REMOTE_URL", result.asErrorCode())
        coVerify(exactly = 0) { engine.runCommandSyncWithExit(any(), any(), any()) }
    }

    @Test
    fun remoteAdd_validUrl_executes() = runBlocking {
        coEvery { engine.runCommandSyncWithExit(any(), any(), any()) } returns CommandResult("", 0)

        val result = tool.execute(
            args("action" to "remote_add", "remote" to "origin", "url" to "https://github.com/org/repo.git")
        )

        assertTrue(result is ToolResult.Success)
        coVerify {
            engine.runCommandSyncWithExit(
                match { it.contains("git remote add origin https://github.com/org/repo.git") },
                any(), any()
            )
        }
    }

    @Test
    fun clone_targetDirExists_rejected() = runBlocking {
        coEvery { engine.runCommandSync(match { it == "ls -A" }, any(), any()) } returns "repo\nREADME.md"

        val result = tool.execute(
            args("action" to "clone", "url" to "https://github.com/org/repo.git", "dir" to "repo")
        )

        assertEquals("CLONE_DIR_EXISTS", result.asErrorCode())
    }

    @Test
    fun clone_valid_shallowWithDepth() = runBlocking {
        coEvery { engine.runCommandSync(match { it == "ls -A" }, any(), any()) } returns "other"
        coEvery { engine.runCommandSyncWithExit(any(), any(), any()) } returns CommandResult("Cloning into...", 0)

        val result = tool.execute(
            args("action" to "clone", "url" to "https://github.com/org/repo.git", "dir" to "repo", "depth" to 1)
        )

        assertTrue(result is ToolResult.Success)
        coVerify {
            engine.runCommandSyncWithExit(
                match { it.contains("git clone --depth 1 https://github.com/org/repo.git repo") },
                any(), any()
            )
        }
    }

    @Test
    fun clone_invalidDepth_rejected() = runBlocking {
        val result = tool.execute(
            args("action" to "clone", "url" to "https://github.com/org/repo.git", "depth" to 0)
        )

        assertEquals("INVALID_DEPTH", result.asErrorCode())
    }

    @Test
    fun pull_dirtyWorktree_rejected() = runBlocking {
        coEvery { gitRepository.status() } returns cleanStatus.copy(untracked = listOf("new.txt"))

        val result = tool.execute(args("action" to "pull"))

        assertEquals("DIRTY_WORKTREE", result.asErrorCode())
    }

    @Test
    fun pull_cleanWorktree_executes() = runBlocking {
        coEvery { engine.runCommandSyncWithExit(match { it.contains("git pull") }, any(), any()) } returns
            CommandResult("Already up to date.", 0)

        val result = tool.execute(args("action" to "pull"))

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun notAGitRepo_allActionsRejected() = runBlocking {
        coEvery { gitRepository.isRepo() } returns false

        val result = tool.execute(args("action" to "fetch"))

        assertEquals("NOT_A_GIT_REPO", result.asErrorCode())
    }

    @Test
    fun unknownAction_rejected() = runBlocking {
        val result = tool.execute(args("action" to "rebase"))

        assertEquals("UNSUPPORTED_ACTION", result.asErrorCode())
    }

    @Test
    fun remoteList_parsesVerboseOutput() = runBlocking {
        coEvery { engine.runCommandSync(any(), any(), any()) } returns
            "origin\thttps://github.com/org/repo.git (fetch)\norigin\thttps://github.com/org/repo.git (push)\n"

        val result = tool.execute(args("action" to "remote_list"))

        assertTrue(result is ToolResult.Success)
        val data = result.asSuccessData()
        assertTrue(data.toString().contains("origin"))
        assertTrue(data.toString().contains("https://github.com/org/repo.git"))
    }

    // ───────────── repo_dir 作用域（新增） ─────────────

    @Test
    fun push_repoDirScoped_usesDashCAndKeepsCwd() = runBlocking {
        coEvery { engine.runCommandSyncWithExit(match { it.contains("git -C sub push") }, any(), any()) } returns
            CommandResult("pushed", 0)

        val result = tool.execute(args("action" to "push", "repo_dir" to "sub"))

        assertTrue(result is ToolResult.Success)
        // 命令注入 `git -C sub`，cwd 仍为工作区根（不传给 git 命令体）
        coVerify {
            engine.runCommandSyncWithExit(
                match { it.startsWith("GIT_TERMINAL_PROMPT=0 git -C sub push") },
                any(), any()
            )
        }
    }

    @Test
    fun push_repoDirAbsolute_rejected() = runBlocking {
        val result = tool.execute(args("action" to "push", "repo_dir" to "/abs/path"))

        assertEquals("INVALID_REPO_DIR", result.asErrorCode())
        coVerify(exactly = 0) { engine.runCommandSync(any(), any(), any()) }
    }

    @Test
    fun push_repoDirDotDot_escapeRejected() = runBlocking {
        val result = tool.execute(args("action" to "push", "repo_dir" to "a/../b"))

        assertEquals("INVALID_REPO_DIR", result.asErrorCode())
    }

    @Test
    fun clone_rejectsRepoDir() = runBlocking {
        val result = tool.execute(
            args("action" to "clone", "url" to "https://github.com/org/repo.git", "repo_dir" to "sub")
        )

        assertEquals("INVALID_REPO_DIR", result.asErrorCode())
    }

    @Test
    fun push_repoDirNotARepo_rejected() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("is-inside-work-tree") }, any(), any()) } returns
            "fatal: not a git repository"

        val result = tool.execute(args("action" to "push", "repo_dir" to "notrepo"))

        assertEquals("NOT_A_GIT_REPO", result.asErrorCode())
    }

    @Test
    fun rootNotRepo_discoversSubRepo_inMessage() = runBlocking {
        coEvery { gitRepository.isRepo() } returns false
        coEvery { engine.runCommandSync(match { it.contains("for d in */") }, any(), any()) } returns "repo2\n"

        val result = tool.execute(args("action" to "push"))

        assertEquals("NOT_A_GIT_REPO", result.asErrorCode())
        assertTrue(result.asErrorData().toString().contains("repo2"))
    }

    @Test
    fun rootNotRepo_noDiscovery_generic() = runBlocking {
        coEvery { gitRepository.isRepo() } returns false
        coEvery { engine.runCommandSync(match { it.contains("for d in */") }, any(), any()) } returns ""

        val result = tool.execute(args("action" to "push"))

        assertEquals("NOT_A_GIT_REPO", result.asErrorCode())
        assertTrue(result.asErrorData().toString().contains("不是 git 仓库"))
    }

    // ───────────── push 前置条件预检（新增） ─────────────

    @Test
    fun push_noUpstream_singleProblem() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("rev-parse --abbrev-ref") }, any(), any()) } returns
            "fatal: no upstream configured for the current branch"

        val result = tool.execute(args("action" to "push"))

        assertEquals("NO_UPSTREAM", result.asErrorCode())
        assertTrue(result.asErrorData().toString().contains("No configured push destination"))
    }

    @Test
    fun push_credentialFileMissing_singleProblem() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("__CRED_MISSING__") }, any(), any()) } returns "__CRED_MISSING__"

        val result = tool.execute(args("action" to "push"))

        assertEquals("CREDENTIAL_FILE_MISSING", result.asErrorCode())
    }

    @Test
    fun push_multipleProblems_aggregated() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("rev-parse --abbrev-ref") }, any(), any()) } returns
            "fatal: no upstream configured"
        coEvery { engine.runCommandSync(match { it.contains("__CRED_MISSING__") }, any(), any()) } returns "__CRED_MISSING__"

        val result = tool.execute(args("action" to "push"))

        assertEquals("PUSH_PRECONDITION_BLOCKED", result.asErrorCode())
        val msg = result.asErrorData().toString()
        assertTrue(msg.contains("No configured push destination"))
        assertTrue(msg.contains("凭据文件不存在"))
    }

    @Test
    fun pushTag_helperMissing_blocksBeforeLsRemote() = runBlocking {
        coEvery { gitRepository.listTags() } returns listOf(GitTag("v1.2.3", "abc1234"))
        // credential.helper 链中查不到 store 条目
        coEvery { engine.runCommandSync(match { it.contains("config --get-all credential.helper") }, any(), any()) } returns ""

        val result = tool.execute(args("action" to "push_tag", "tag" to "v1.2.3"))

        assertEquals("CREDENTIAL_HELPER_MISSING", result.asErrorCode())
        // 凭据预检早于 ls-remote：凭据坏了就不浪费网络往返
        coVerify(exactly = 0) { engine.runCommandSync(match { it.contains("ls-remote") }, any(), any()) }
    }

    @Test
    fun pushTag_sshRemote_skipsHelperCheck() = runBlocking {
        coEvery { gitRepository.listTags() } returns listOf(GitTag("v1.2.3", "abc1234"))
        // ssh 远端：helper 文件检查应被跳过
        coEvery { engine.runCommandSync(match { it.contains("config --get remote.origin.url") }, any(), any()) } returns "git@github.com:org/repo.git"
        coEvery { engine.runCommandSync(match { it.contains("ls-remote") }, any(), any()) } returns ""
        coEvery { engine.runCommandSyncWithExit(match { it.contains("push") }, any(), any()) } returns CommandResult("pushed", 0)

        val result = tool.execute(args("action" to "push_tag", "tag" to "v1.2.3"))

        assertTrue(result is ToolResult.Success)
        coVerify(exactly = 0) { engine.runCommandSync(match { it.contains("config --get-all credential.helper") }, any(), any()) }
    }

    @Test
    fun push_authFailure_attachesDiagnosisWithChmod() = runBlocking {
        coEvery { engine.runCommandSyncWithExit(match { it.contains("git push") }, any(), any()) } returns
            CommandResult("fatal: Authentication failed", 128)

        val result = tool.execute(args("action" to "push"))

        assertEquals("GIT_COMMAND_FAILED", result.asErrorCode())
        val msg = result.asErrorData().toString()
        assertTrue(msg.contains("【凭据诊断】"))
        assertTrue(msg.contains("chmod 600"))
    }

    @Test
    fun fetch_credentialWarning_notBlocking() = runBlocking {
        // 凭据文件缺失：仅作警告，不阻断读操作
        coEvery { engine.runCommandSync(match { it.contains("__CRED_MISSING__") }, any(), any()) } returns "__CRED_MISSING__"
        coEvery { engine.runCommandSyncWithExit(match { it.contains("git fetch") }, any(), any()) } returns CommandResult("Fetch done", 0)

        val result = tool.execute(args("action" to "fetch", "remote" to "origin"))

        assertTrue(result is ToolResult.Success)
        assertTrue(result.asSuccessData().toString().contains("credential_warning"))
    }

    @Test
    fun pushTag_repoDir_scopedTagList() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("tag --list") }, any(), any()) } returns "v1.2.3"
        coEvery { engine.runCommandSync(match { it.contains("ls-remote") }, any(), any()) } returns ""
        coEvery { engine.runCommandSyncWithExit(match { it.contains("push") }, any(), any()) } returns CommandResult("pushed", 0)

        val result = tool.execute(args("action" to "push_tag", "tag" to "v1.2.3", "repo_dir" to "sub"))

        assertTrue(result is ToolResult.Success)
        coVerify {
            engine.runCommandSync(match { it.contains("git -C sub tag --list") }, any(), any())
        }
    }

    @Test
    fun pull_repoDir_dirtyPorcelain_rejected() = runBlocking {
        coEvery { engine.runCommandSync(match { it.contains("status --porcelain") }, any(), any()) } returns
            "## main...origin/main\n M file.txt"

        val result = tool.execute(args("action" to "pull", "repo_dir" to "sub"))

        assertEquals("DIRTY_WORKTREE", result.asErrorCode())
        coVerify(exactly = 0) { engine.runCommandSyncWithExit(any(), any(), any()) }
    }
}
