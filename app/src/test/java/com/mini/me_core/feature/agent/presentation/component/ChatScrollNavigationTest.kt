package com.mini.me_core.feature.agent.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「跳到上一条用户消息」纯计算内核回归。
 *
 * 覆盖：首次点击取视口之上最近用户组、位于用户消息组时再点击上跳一条、
 * 触顶（上方无用户消息）返回 null（按钮隐藏）、无用户消息的列表恒 null。
 */
class ChatScrollNavigationTest {

    // 组 0/2/4 含用户消息（如：历史组 → 任务1 → 任务2）
    private val userGroups = listOf(0, 2, 4)

    @Test
    fun firstClick_jumpsToNearestUserGroupAboveViewport() {
        // 视口顶在组 3（无用户消息）：目标是组 2
        assertEquals(2, previousUserMessageGroupIndex(userGroups, topGroupIndex = 3))
        // 视口顶在组 5（底部，组 4 是最近一条用户消息）：目标是组 4
        assertEquals(4, previousUserMessageGroupIndex(userGroups, topGroupIndex = 5))
    }

    @Test
    fun standingOnUserGroup_clickAdvancesToPreviousOne() {
        // 视口顶正落在用户消息组 2 上：目标是上一条用户消息组 0（不重复跳回 2）
        assertEquals(0, previousUserMessageGroupIndex(userGroups, topGroupIndex = 2))
        // 视口顶落在用户消息组 4 上：目标是 2
        assertEquals(2, previousUserMessageGroupIndex(userGroups, topGroupIndex = 4))
    }

    @Test
    fun atTopmostUserGroup_returnsNull_buttonHides() {
        // 视口顶已在最顶的用户消息组 0：上方无用户消息 → null（按钮隐藏消失）
        assertNull(previousUserMessageGroupIndex(userGroups, topGroupIndex = 0))
        // 全列表无用户消息（纯工具/助手组）：恒 null
        assertNull(previousUserMessageGroupIndex(emptyList(), topGroupIndex = 5))
    }

    @Test
    fun noUserGroupBetween_targetsNextOneAbove() {
        // 组 3 是用户消息组、组 5 也是：视口顶在组 6 → 目标 5
        assertEquals(5, previousUserMessageGroupIndex(listOf(0, 3, 5), topGroupIndex = 6))
        // 视口顶在组 4（3 与 5 之间）→ 目标 3
        assertEquals(3, previousUserMessageGroupIndex(listOf(0, 3, 5), topGroupIndex = 4))
    }
}
