package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.container.CommandResult
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.git.domain.model.GitStatus
import com.mini.me_core.feature.git.domain.model.GitTag
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import io.mockk.coEvery
import io.mockk.every
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
 * 命令拼装（GIT_TERMINAL_PROMPT=0 / force_with_lease / refs/tags）、失败语义。
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

    @Test
    fun push_compliantHeadCommit_executes() = runBlocking {
        coEvery { engine.runCommandSync(any(), any(), any()) } returns "feat(agent): 新增 git_remote 工具"
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
        coEvery { engine.runCommandSync(any(), any(), any()) } returns "fix: 修复"
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
        coEvery { engine.runCommandSync(any(), any(), any()) } returns "feat: x"
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
}
