package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolTypeDeriverTest {

    // ===== 特殊类型 =====

    @Test
    fun checkEnvironment_returnsENV() {
        assertEquals(ToolType.ENV, ToolTypeDeriver.derive("check_environment"))
    }

    @Test
    fun todo_returnsTODO() {
        assertEquals(ToolType.TODO, ToolTypeDeriver.derive("todo"))
    }

    @Test
    fun usage_returnsUSAGE() {
        assertEquals(ToolType.USAGE, ToolTypeDeriver.derive("usage"))
    }

    // ===== MCP =====

    @Test
    fun manageMcp_returnsMCP() {
        assertEquals(ToolType.MCP, ToolTypeDeriver.derive("manageMcp"))
    }

    @Test
    fun mcpTool_returnsMCP() {
        assertEquals(ToolType.MCP, ToolTypeDeriver.derive("mcp_server_tool"))
    }

    // ===== Shell =====

    @Test
    fun bash_returnsSHELL() {
        assertEquals(ToolType.SHELL, ToolTypeDeriver.derive("Bash"))
    }

    @Test
    fun terminal_returnsSHELL() {
        assertEquals(ToolType.SHELL, ToolTypeDeriver.derive("terminal"))
    }

    @Test
    fun bashWithBuildCommand_returnsBUILD_RUN() {
        val args = """{"command":"./gradlew assembleDebug"}"""
        assertEquals(ToolType.BUILD_RUN, ToolTypeDeriver.derive("Bash", args))
    }

    @Test
    fun bashWithApkAdd_returnsBUILD_RUN() {
        val args = """{"command":"apk add git curl"}"""
        assertEquals(ToolType.BUILD_RUN, ToolTypeDeriver.derive("Bash", args))
    }

    @Test
    fun bashWithReadCommand_returnsFILE_READ() {
        val args = """{"command":"cat /etc/hosts"}"""
        assertEquals(ToolType.FILE_READ, ToolTypeDeriver.derive("Bash", args))
    }

    @Test
    fun bashWithLsCommand_returnsFILE_READ() {
        val args = """{"command":"ls -la"}"""
        assertEquals(ToolType.FILE_READ, ToolTypeDeriver.derive("Bash", args))
    }

    @Test
    fun bashWithEchoCommand_returnsSHELL() {
        val args = """{"command":"echo hello"}"""
        assertEquals(ToolType.SHELL, ToolTypeDeriver.derive("Bash", args))
    }

    // ===== 文件写入 =====

    @Test
    fun writeFile_returnsFILE_WRITE() {
        assertEquals(ToolType.FILE_WRITE, ToolTypeDeriver.derive("writeFile"))
    }

    @Test
    fun editFile_returnsFILE_WRITE() {
        assertEquals(ToolType.FILE_WRITE, ToolTypeDeriver.derive("editFile"))
    }

    // ===== 文件读取 =====

    @Test
    fun readFile_returnsFILE_READ() {
        assertEquals(ToolType.FILE_READ, ToolTypeDeriver.derive("readFile"))
    }

    @Test
    fun viewImage_returnsFILE_READ() {
        assertEquals(ToolType.FILE_READ, ToolTypeDeriver.derive("viewImage"))
    }

    @Test
    fun list_returnsFILE_READ() {
        assertEquals(ToolType.FILE_READ, ToolTypeDeriver.derive("list"))
    }

    // ===== 构建/运行 =====

    @Test
    fun runCode_returnsBUILD_RUN() {
        assertEquals(ToolType.BUILD_RUN, ToolTypeDeriver.derive("run_code"))
    }

    // ===== 搜索 =====

    @Test
    fun search_returnsSEARCH() {
        assertEquals(ToolType.SEARCH, ToolTypeDeriver.derive("search"))
    }

    @Test
    fun websearch_returnsSEARCH() {
        assertEquals(ToolType.SEARCH, ToolTypeDeriver.derive("websearch"))
    }

    @Test
    fun webfetch_returnsSEARCH() {
        assertEquals(ToolType.SEARCH, ToolTypeDeriver.derive("webfetch"))
    }

    @Test
    fun browser_returnsSEARCH() {
        assertEquals(ToolType.SEARCH, ToolTypeDeriver.derive("browser"))
    }

    // ===== 边界情况 =====

    @Test
    fun nullToolName_returnsOTHER() {
        assertEquals(ToolType.OTHER, ToolTypeDeriver.derive(null))
    }

    @Test
    fun blankToolName_returnsOTHER() {
        assertEquals(ToolType.OTHER, ToolTypeDeriver.derive(""))
    }

    @Test
    fun unknownTool_returnsOTHER() {
        assertEquals(ToolType.OTHER, ToolTypeDeriver.derive("someUnknownTool"))
    }

    @Test
    fun goal_returnsOTHER() {
        assertEquals(ToolType.OTHER, ToolTypeDeriver.derive("goal"))
    }

    @Test
    fun plan_returnsOTHER() {
        assertEquals(ToolType.OTHER, ToolTypeDeriver.derive("plan"))
    }

    // ===== extractCommand =====

    @Test
    fun extractCommand_validJson_returnsCommand() {
        val json = """{"command":"ls -la","timeout":30}"""
        assertEquals("ls -la", ToolTypeDeriver.extractCommand(json))
    }

    @Test
    fun extractCommand_noCommandField_returnsEmpty() {
        val json = """{"timeout":30}"""
        assertEquals("", ToolTypeDeriver.extractCommand(json))
    }

    @Test
    fun extractCommand_invalidJson_returnsEmpty() {
        assertEquals("", ToolTypeDeriver.extractCommand("not json"))
    }

    @Test
    fun extractCommand_blankInput_returnsEmpty() {
        assertEquals("", ToolTypeDeriver.extractCommand(""))
    }

    @Test
    fun extractCommand_withEscapedQuotes_returnsUnescaped() {
        val json = """{"command":"echo \"hello world\""}"""
        assertEquals("echo \"hello world\"", ToolTypeDeriver.extractCommand(json))
    }
}
