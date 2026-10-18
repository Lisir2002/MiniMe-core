package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.mcp.server.DefaultSkillMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.mcp.server.SkillResourceUri
import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillResolution
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillScope
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillStateRepository
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType
import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state as V2ConvState
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4：技能 → MCP prompts / resources 的分派与寻址验证。
 *
 * 覆盖 D3 的两条分派规则与三个「刻意不做」：
 * 1. `PROMPT` 型进 prompts，`SCRIPT` 型进 resources；
 * 2. `MCP` 型**不暴露**（它是消费方，不是供给方）；
 * 3. 已禁用技能不暴露（对外可见性 = 用户在 App 内已启用的技能）；
 * 4. `list` 与 `get` 口径一致（无悬空条目）；
 * 5. URI 为不可信输入：畸形/异 scheme 一律 null，不抛异常；
 * 6. 技能仓库异常时降级为「不暴露」，绝不把异常抛进 MCP 请求路径。
 */
@Suppress("TooManyFunctions")
class SkillMcpAdapterTest {

    /** 技能仓库桩：只提供 `listSkillsSync` 的数据，其余方法一律不支持（本测试不触达）。 */
    @Suppress("TooManyFunctions")
    private class FakeSkillRepo(
        private val skills: List<Skill>,
        private val error: Throwable? = null
    ) : SkillStateRepository {
        override val skillsFlow: Flow<List<Skill>> = flowOf(skills)

        override suspend fun listSkills(): List<Skill> =
            error?.let { throw it } ?: skills

        override fun skillsRoot(): File = File("/tmp/skills")

        override fun listSkillsSync(): List<Skill> = error?.let { throw it } ?: skills

        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit

        override suspend fun install(sourceDir: File): Skill? = null

        override suspend fun uninstall(id: String): Boolean = false

        override suspend fun update(id: String, sourceDir: File): Skill? = null

        override suspend fun setScopeOverride(id: String, scope: SkillScope?, agentType: String?) = Unit

        override suspend fun setConversationEnabled(skillId: String, sessionId: String, enabled: Boolean) = Unit

        override suspend fun removeConversationBinding(skillId: String, sessionId: String) = Unit

        override suspend fun listConversationStates(sessionId: String): List<V2ConvState> = emptyList()

        override suspend fun getConversationState(skillId: String, sessionId: String): V2ConvState? = null

        override suspend fun filterVisibleSkills(
            skills: List<Skill>,
            sessionId: String?,
            agentType: String
        ): List<Skill> = skills

        override fun filterVisibleSkillsSync(
            skills: List<Skill>,
            sessionId: String?,
            agentType: String
        ): List<Skill> = skills

        override suspend fun resolveSkillWithDependencies(id: String): SkillResolution? = null
    }

    private fun promptSkill(id: String = "p1", enabled: Boolean = true) = Skill(
        id = id,
        name = "写作助手",
        description = "帮你写文档",
        type = SkillType.PROMPT,
        enabled = enabled,
        instructions = "请按下列风格写作：简洁、要点式。"
    )

    private fun scriptSkill(id: String = "s1", enabled: Boolean = true) = Skill(
        id = id,
        name = "构建脚本",
        description = "执行构建",
        type = SkillType.SCRIPT,
        enabled = enabled,
        entry = "build.sh",
        instructions = ""
    )

    private fun mcpSkill(id: String = "m1") = Skill(
        id = id,
        name = "远端技能",
        description = "包装 MCP 工具",
        type = SkillType.MCP,
        mcpTool = "some__remote_tool",
        instructions = ""
    )

    private fun adapterOf(vararg skills: Skill, error: Throwable? = null) =
        DefaultSkillMcpAdapter(FakeSkillRepo(skills.toList(), error))

    // ── 分派规则 ──────────────────────────────────────────────

    @Test
    fun `PROMPT 型进 prompts 不进 resources`() {
        val a = adapterOf(promptSkill())
        assertEquals(listOf("p1"), a.listPrompts().map { it.id })
        assertTrue(a.listResources().isEmpty())
    }

    @Test
    fun `SCRIPT 型进 resources 不进 prompts`() {
        val a = adapterOf(scriptSkill())
        assertTrue(a.listPrompts().isEmpty())
        assertEquals(listOf("s1"), a.listResources().map { it.id })
    }

    @Test
    fun `MCP 型不被暴露在任何一个列表`() {
        val a = adapterOf(mcpSkill())
        assertTrue(a.listPrompts().isEmpty())
        assertTrue(a.listResources().isEmpty())
        assertFalse(a.hasSkills())
    }

    @Test
    fun `已禁用技能不暴露`() {
        val a = adapterOf(promptSkill("p1", enabled = false), scriptSkill("s1", enabled = false))
        assertTrue(a.listPrompts().isEmpty())
        assertTrue(a.listResources().isEmpty())
        assertFalse(a.hasSkills())
    }

    @Test
    fun `三种型态混合时分派互不串味`() {
        val a = adapterOf(promptSkill("p1"), scriptSkill("s1"), mcpSkill("m1"))
        assertEquals(listOf("p1"), a.listPrompts().map { it.id })
        assertEquals(listOf("s1"), a.listResources().map { it.id })
        assertTrue(a.hasSkills())
    }

    // ── list 与 get 的口径一致性 ──────────────────────────────

    @Test
    fun `getPrompt 能取到 listPrompts 里的每一条`() {
        val a = adapterOf(promptSkill("p1"), promptSkill("p2"))
        a.listPrompts().forEach { listed ->
            assertNotNull("list 中出现但 get 取不到：${listed.id}", a.getPrompt(listed.id))
        }
    }

    @Test
    fun `getResource 能取到 listResources 里的每一条`() {
        val a = adapterOf(scriptSkill("s1"), scriptSkill("s2"))
        a.listResources().forEach { listed ->
            val uri = SkillResourceUri.of(listed)
            assertNotNull("list 中出现但 read 取不到：$uri", a.getResource(uri))
        }
    }

    @Test
    fun `getPrompt 取不到 SCRIPT 型——不得跨型取`() {
        val a = adapterOf(scriptSkill("s1"))
        assertNull(a.getPrompt("s1"))
    }

    @Test
    fun `getResource 取不到 PROMPT 型——不得跨型取`() {
        val a = adapterOf(promptSkill("p1"))
        assertNull(a.getResource(SkillResourceUri.of(promptSkill("p1"))))
    }

    @Test
    fun `未知 id 返回 null`() {
        val a = adapterOf(promptSkill("p1"))
        assertNull(a.getPrompt("nope"))
        assertNull(a.getResource(SkillResourceUri.of(promptSkill("p1"))))
    }

    // ── URI 映射（不可信输入） ────────────────────────────────

    @Test
    fun `URI 形如 minime skill id`() {
        assertEquals("minime://skill/s1", SkillResourceUri.of(scriptSkill("s1")))
    }

    @Test
    fun `parseId 能反解自造URI`() {
        assertEquals("s1", SkillResourceUri.parseId("minime://skill/s1"))
    }

    @Test
    fun `parseId 对异 scheme 返回 null`() {
        assertNull(SkillResourceUri.parseId("file:///etc/passwd"))
        assertNull(SkillResourceUri.parseId("minime://other/s1"))
    }

    @Test
    fun `parseId 对空 id 与多层路径返回 null`() {
        assertNull(SkillResourceUri.parseId("minime://skill/"))
        assertNull(SkillResourceUri.parseId("minime://skill/a/b"))
        assertNull(SkillResourceUri.parseId(""))
    }

    @Test
    fun `畸形 URI 传入 getResource 只返回 null 不抛异常`() {
        val a = adapterOf(scriptSkill("s1"))
        listOf("", "not a uri", "minime://skill/", "minime://skill/a/b", "minime://").forEach {
            assertNull("不应抛异常：$it", a.getResource(it))
        }
    }

    @Test
    fun `未知 scheme 的 URI 取不到本server 的技能`() {
        val a = adapterOf(scriptSkill("s1"))
        assertNull(a.getResource("minime://skill/../s1"))
        assertNull(a.getResource("MINIME://skill/s1"))
    }

    // ── 降级 ────────────────────────────────────────────────

    @Test
    fun `仓库异常时降级为不暴露而非抛出`() {
        val a = adapterOf(error = IllegalStateException("目录权限异常"))
        assertFalse(a.hasSkills())
        assertTrue(a.listPrompts().isEmpty())
        assertTrue(a.listResources().isEmpty())
        assertNull(a.getPrompt("p1"))
        assertNull(a.getResource("minime://skill/s1"))
    }
}
