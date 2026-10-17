package com.mini.me_core.feature.agent.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDisplayNamesTest {

    @Test
    fun knownToolGetsHumanLabel() {
        assertEquals("编辑文件", ToolDisplayNames.humanLabel("editFile"))
        assertEquals("联网搜索", ToolDisplayNames.humanLabel("websearch"))
        assertEquals("执行命令", ToolDisplayNames.humanLabel("Bash"))
    }

    @Test
    fun unknownToolReturnsNullSoCallerFallsBackToRawName() {
        assertNull(ToolDisplayNames.humanLabel("someFutureTool"))
    }

    @Test
    fun blankNameReturnsNull() {
        assertNull(ToolDisplayNames.humanLabel(null))
        assertNull(ToolDisplayNames.humanLabel(""))
        assertNull(ToolDisplayNames.humanLabel("   "))
    }

    @Test
    fun rawNameShownOnlyWhenDifferentFromHumanLabel() {
        assertTrue(ToolDisplayNames.showRawName("editFile", "编辑文件"))
        assertFalse(ToolDisplayNames.showRawName("editFile", null))
        assertFalse(ToolDisplayNames.showRawName(null, "编辑文件"))
    }

    /** 注册表里出现过的工具名必须都有人话摘要，避免新工具裸显示 camelCase。 */
    @Test
    fun everyRegisteredToolHasHumanLabel() {
        val registered = listOf(
            "readFile", "sendFile", "viewImage", "writeFile", "editFile", "Bash", "run_code",
            "check_environment", "ensure_android_env", "switch_container_arch", "terminal",
            "list", "search", "loadSkill", "runSkillScript", "load_rule", "loadSop",
            "askUserQuestion", "manageMcp", "websearch", "webfetch", "switchMode", "todo",
            "memory", "generateImage", "browser", "device_storage", "network_proxy", "goal",
            "job_start", "job_status", "job_kill", "job_log", "schedule"
        )
        val missing = registered.filter { ToolDisplayNames.humanLabel(it) == null }
        assertTrue("以下工具缺少人话摘要：$missing", missing.isEmpty())
    }
}
