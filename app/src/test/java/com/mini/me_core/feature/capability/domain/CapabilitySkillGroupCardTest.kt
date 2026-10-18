package com.mini.me_core.feature.capability.domain

import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4：技能分组卡的计数口径。
 *
 * 这条不变量一句话说清：**UI 上显示的 prompts / resources 数量，必须等于外部 MCP 客户端
 * 调`prompts/list` / `resources/list` 实际拿到的条数。**
 *
 * 靠两份代码各写一遍 `type == PROMPT` 是最容易出的错——两边都能编译、都能跑，
 * 只有在用户把技能禁用之后才会分叉。故实现复用服务端同一对分派谓词，本测试钉住它。
 */
class CapabilitySkillGroupCardTest {

    private fun skill(id: String, type: SkillType, enabled: Boolean = true) = Skill(
        id = id,
        name = id,
        description = "d",
        type = type,
        enabled = enabled,
        instructions = ""
    )

    @Test
    fun `空列表得到全零且未暴露`() {
        val card = buildCapabilitySkillGroupCard(emptyList())
        assertEquals(0, card.total)
        assertEquals(0, card.promptCount)
        assertEquals(0, card.scriptCount)
        assertEquals(0, card.mcpAliasCount)
        assertFalse(card.exposedViaMcp)
    }

    @Test
    fun `三种型态各自落到对应计数`() {
        val card = buildCapabilitySkillGroupCard(
            listOf(
                skill("p", SkillType.PROMPT),
                skill("p2", SkillType.PROMPT),
                skill("s", SkillType.SCRIPT),
                skill("m", SkillType.MCP)
            )
        )
        assertEquals(4, card.total)
        assertEquals(2, card.promptCount)
        assertEquals(1, card.scriptCount)
        assertEquals(1, card.mcpAliasCount)
        assertTrue(card.exposedViaMcp)
    }

    @Test
    fun `已禁用的 PROMPT 与 SCRIPT 不计入暴露数`() {
        val card = buildCapabilitySkillGroupCard(
            listOf(
                skill("p", SkillType.PROMPT, enabled = false),
                skill("s", SkillType.SCRIPT, enabled = false)
            )
        )
        assertEquals(2, card.total)
        assertEquals(0, card.promptCount)
        assertEquals(0, card.scriptCount)
        assertFalse(card.exposedViaMcp)
    }

    @Test
    fun `仅 MCP 型技能时不算对外暴露`() {
        val card = buildCapabilitySkillGroupCard(listOf(skill("m", SkillType.MCP)))
        assertEquals(1, card.total)
        assertEquals(1, card.mcpAliasCount)
        assertFalse(card.exposedViaMcp)
    }

    @Test
    fun `MCP 型计数含未启用者——它是资产而非暴露态`() {
        val card = buildCapabilitySkillGroupCard(
            listOf(skill("m", SkillType.MCP, enabled = false))
        )
        assertEquals(1, card.mcpAliasCount)
    }

    @Test
    fun `只要有一个可暴露技能即标记已暴露`() {
        assertTrue(buildCapabilitySkillGroupCard(listOf(skill("p", SkillType.PROMPT))).exposedViaMcp)
        assertTrue(buildCapabilitySkillGroupCard(listOf(skill("s", SkillType.SCRIPT))).exposedViaMcp)
    }

    @Test
    fun `默认概览的技能分组卡是全零`() {
        assertEquals(0, CapabilityOverview().skillGroup.total)
        assertFalse(CapabilityOverview().skillGroup.exposedViaMcp)
    }
}
