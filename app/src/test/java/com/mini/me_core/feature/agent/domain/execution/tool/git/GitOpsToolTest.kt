package com.mini.me_core.feature.agent.domain.execution.tool.git

import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.git.domain.GitRepository
import com.mini.me_core.feature.git.domain.model.GitFileChange
import com.mini.me_core.feature.git.domain.model.GitStatus
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GitOpsTool 回归：suggest_commit 的 push 前测试建议需按工作区技术栈守卫，
 * 不再把 gradlew 写成唯一命令；无改动时早退、不触发 ls -A。
 */
class GitOpsToolTest {

    private lateinit var engine: CommandEngine
    private lateinit var gitRepository: GitRepository
    private lateinit var workspace: WorkspaceRepository
    private lateinit var tool: GitOpsTool

    private val cleanStatus = GitStatus(
        branch = "main", ahead = 0, behind = 0,
        staged = emptyList(), unstaged = emptyList(), untracked = emptyList()
    )
    private val changedStatus = GitStatus(
        branch = "main", ahead = 0, behind = 0,
        staged = listOf(GitFileChange("a.txt", "A", true)),
        unstaged = emptyList(), untracked = emptyList()
    )

    @Before
    fun setUp() {
        engine = mockk(relaxed = true)
        gitRepository = mockk(relaxed = true)
        workspace = mockk(relaxed = true)
        coEvery { gitRepository.isRepo() } returns true
        coEvery { workspace.currentPath() } returns "/workspace"
        tool = GitOpsTool(gitRepository, engine, workspace)
    }

    private fun args(vararg pairs: Pair<String, Any?>): Map<String, kotlinx.serialization.json.JsonElement> =
        buildJsonObject {
            pairs.forEach { (k, v) ->
                when (v) {
                    is String? -> put(k, JsonPrimitive(v ?: ""))
                    is Boolean -> put(k, JsonPrimitive(v))
                }
            }
        }

    @Test
    fun suggestCommit_gradlewPresent_advisesGradleTest() = runBlocking {
        coEvery { gitRepository.status() } returns changedStatus
        coEvery { engine.runCommandSync(match { it == "ls -A" }, any(), any()) } returns "gradlew\nsrc\nbuild.gradle.kts"

        val result = tool.execute(args("action" to "suggest_commit"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data.toString()
        assertTrue("应建议 gradlew 单测", data.contains("./gradlew :app:testReleaseUnitTest"))
        assertTrue("建议应带 AGENTS.md 出处", data.contains("AGENTS.md"))
    }

    @Test
    fun suggestCommit_packageJson_advisesNpmTest() = runBlocking {
        coEvery { gitRepository.status() } returns changedStatus
        coEvery { engine.runCommandSync(match { it == "ls -A" }, any(), any()) } returns "package.json\nsrc"

        val result = tool.execute(args("action" to "suggest_commit"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data.toString()
        assertTrue("应降级建议 npm test", data.contains("npm test"))
        assertTrue("降级说明应提示非 Gradle 工程", data.contains("Gradle 工程") || data.contains("实际技术栈"))
    }

    @Test
    fun suggestCommit_noChanges_returnsEarly_withoutLsA() = runBlocking {
        coEvery { gitRepository.status() } returns cleanStatus

        val result = tool.execute(args("action" to "suggest_commit"))

        assertTrue(result is ToolResult.Success)
        // 无改动早退，不应触发 ls -A 工作区探测
        coVerify(exactly = 0) { engine.runCommandSync(match { it == "ls -A" }, any(), any()) }
    }
}
