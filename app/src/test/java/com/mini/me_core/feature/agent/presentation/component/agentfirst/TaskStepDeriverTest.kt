package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStepDeriverTest {

    private fun toolMsg(
        id: String = "tool_1",
        content: String = "success",
        isError: Boolean = false,
        toolName: String = "Bash",
        toolArgs: String = """{"command":"./gradlew build"}"""
    ) = AgentUIMessage(
        id = id, role = MessageRole.TOOL, content = content,
        toolName = toolName, toolArgs = toolArgs, isError = isError
    )

    private fun taskGroup(tools: List<AgentUIMessage>) = TaskGroup(
        taskId = "task_1", title = "test", timestamp = 0,
        subGroups = listOf(TaskSubGroup(id = "sg_1", type = TaskSubGroupType.TOOL, messages = tools))
    )

    // ===== 步骤数量 =====

    @Test
    fun noToolMessages_returnsEmptyList() {
        val group = TaskGroup(
            taskId = "t1", title = "t", timestamp = 0,
            subGroups = listOf(TaskSubGroup(id = "sg", type = TaskSubGroupType.USER, messages = emptyList()))
        )
        assertTrue(TaskStepDeriver.derive(group, emptyList()).isEmpty())
    }

    @Test
    fun threeToolMessages_returnsThreeSteps() {
        val group = taskGroup(listOf(
            toolMsg(id = "t1"), toolMsg(id = "t2"), toolMsg(id = "t3")
        ))
        assertEquals(3, TaskStepDeriver.derive(group, emptyList()).size)
    }

    // ===== 步骤状态 =====

    @Test
    fun runningTool_returnsRUNNING() {
        val group = taskGroup(listOf(toolMsg(id = "t1")))
        val running = listOf(RunningToolOutput(messageId = "t1", text = "building", toolName = "Bash"))
        val steps = TaskStepDeriver.derive(group, running)
        assertEquals(TaskStepStatus.RUNNING, steps[0].status)
    }

    @Test
    fun errorTool_returnsFAILED() {
        val group = taskGroup(listOf(toolMsg(id = "t1", content = "failed", isError = true)))
        val steps = TaskStepDeriver.derive(group, emptyList())
        assertEquals(TaskStepStatus.FAILED, steps[0].status)
    }

    @Test
    fun cancelledTool_returnsCANCELLED() {
        val group = taskGroup(listOf(toolMsg(id = "t1", content = "已停止", isError = true)))
        val steps = TaskStepDeriver.derive(group, emptyList())
        assertEquals(TaskStepStatus.CANCELLED, steps[0].status)
    }

    @Test
    fun successTool_returnsCOMPLETED() {
        val group = taskGroup(listOf(toolMsg(id = "t1", content = "success")))
        val steps = TaskStepDeriver.derive(group, emptyList())
        assertEquals(TaskStepStatus.COMPLETED, steps[0].status)
    }

    // ===== 步骤标题推导（非 Bash 工具） =====

    @Test
    fun writeFile_titleContainsFilename() {
        val args = """{"path":"/root/app/build.gradle.kts","content":"..."}"""
        val title = TaskStepDeriver.deriveTitle("writeFile", "", args)
        assertTrue(title.contains("创建文件"))
        assertTrue(title.contains("build.gradle.kts"))
    }

    @Test
    fun editFile_titleContainsFilename() {
        val args = """{"path":"/root/app/src/Main.kt","oldText":"a","newText":"b"}"""
        val title = TaskStepDeriver.deriveTitle("editFile", "", args)
        assertTrue(title.contains("修改文件"))
        assertTrue(title.contains("Main.kt"))
    }

    @Test
    fun readFile_titleContainsFilename() {
        val args = """{"path":"/root/app/README.md"}"""
        val title = TaskStepDeriver.deriveTitle("readFile", "", args)
        assertTrue(title.contains("读取文件"))
        assertTrue(title.contains("README.md"))
    }

    @Test
    fun viewImage_title() {
        assertEquals("查看图片", TaskStepDeriver.deriveTitle("viewImage", "", null))
    }

    @Test
    fun list_title() {
        assertEquals("列出文件", TaskStepDeriver.deriveTitle("list", "", null))
    }

    @Test
    fun search_title() {
        assertEquals("搜索代码", TaskStepDeriver.deriveTitle("search", "", null))
    }

    @Test
    fun websearch_title() {
        assertEquals("网页搜索", TaskStepDeriver.deriveTitle("websearch", "", null))
    }

    @Test
    fun webfetch_title() {
        assertEquals("网页抓取", TaskStepDeriver.deriveTitle("webfetch", "", null))
    }

    @Test
    fun todo_title() {
        assertEquals("管理待办", TaskStepDeriver.deriveTitle("todo", "", null))
    }

    @Test
    fun manageMcp_title() {
        assertEquals("管理 MCP 工具", TaskStepDeriver.deriveTitle("manageMcp", "", null))
    }

    @Test
    fun checkEnvironment_title() {
        assertEquals("环境探测", TaskStepDeriver.deriveTitle("check_environment", "", null))
    }

    @Test
    fun runCode_title() {
        assertEquals("运行代码", TaskStepDeriver.deriveTitle("run_code", "", null))
    }

    // ===== 步骤标题推导（Bash 命令） =====

    @Test
    fun bashApkAdd_title() {
        assertEquals("安装依赖", TaskStepDeriver.deriveTitle("Bash", "apk add git curl", null))
    }

    @Test
    fun bashAptInstall_title() {
        assertEquals("安装依赖", TaskStepDeriver.deriveTitle("Bash", "apt install -y vim", null))
    }

    @Test
    fun bashPipInstall_title() {
        assertEquals("安装依赖", TaskStepDeriver.deriveTitle("Bash", "pip install requests", null))
    }

    @Test
    fun bashNpmInstall_title() {
        assertEquals("安装依赖", TaskStepDeriver.deriveTitle("Bash", "npm install", null))
    }

    @Test
    fun bashGitClone_title() {
        assertEquals("初始化仓库", TaskStepDeriver.deriveTitle("Bash", "git clone https://github.com/user/repo.git", null))
    }

    @Test
    fun bashGitPull_title() {
        assertEquals("同步代码", TaskStepDeriver.deriveTitle("Bash", "git pull origin main", null))
    }

    @Test
    fun bashGitCommit_title() {
        assertEquals("提交代码", TaskStepDeriver.deriveTitle("Bash", "git commit -m 'fix'", null))
    }

    @Test
    fun bashGradleBuild_title() {
        assertEquals("构建项目", TaskStepDeriver.deriveTitle("Bash", "./gradlew assembleDebug", null))
    }

    @Test
    fun bashMake_title() {
        assertEquals("构建项目", TaskStepDeriver.deriveTitle("Bash", "make -j4", null))
    }

    @Test
    fun bashNpmRunBuild_title() {
        assertEquals("构建项目", TaskStepDeriver.deriveTitle("Bash", "npm run build", null))
    }

    @Test
    fun bashGradleTest_title() {
        assertEquals("运行测试", TaskStepDeriver.deriveTitle("Bash", "./gradlew test", null))
    }

    @Test
    fun bashCat_title() {
        assertEquals("读取文件", TaskStepDeriver.deriveTitle("Bash", "cat /etc/hosts", null))
    }

    @Test
    fun bashRm_title() {
        assertEquals("删除文件", TaskStepDeriver.deriveTitle("Bash", "rm -rf /tmp/build", null))
    }

    @Test
    fun bashMkdir_title() {
        assertEquals("文件操作", TaskStepDeriver.deriveTitle("Bash", "mkdir -p /root/app/src", null))
    }

    @Test
    fun bashCurl_title() {
        assertEquals("下载文件", TaskStepDeriver.deriveTitle("Bash", "curl -O https://example.com/file.zip", null))
    }

    @Test
    fun bashChmod_title() {
        assertEquals("修改权限", TaskStepDeriver.deriveTitle("Bash", "chmod +x script.sh", null))
    }

    @Test
    fun bashExport_title() {
        assertEquals("环境配置", TaskStepDeriver.deriveTitle("Bash", "export PATH=/usr/local/bin:\${'$'}PATH", null))
    }

    @Test
    fun bashUnknownCommand_titleContainsCommand() {
        val title = TaskStepDeriver.deriveTitle("Bash", "some-custom-command --flag value", null)
        assertTrue(title.contains("执行命令"))
        assertTrue(title.contains("some-custom-command"))
    }

    // ===== extractJsonField =====

    @Test
    fun extractJsonField_valid_returnsValue() {
        val json = """{"path":"/root/app/build.gradle.kts","content":"..."}"""
        assertEquals("/root/app/build.gradle.kts", TaskStepDeriver.extractJsonField(json, "path"))
    }

    @Test
    fun extractJsonField_missing_returnsNull() {
        val json = """{"content":"..."}"""
        assertEquals(null, TaskStepDeriver.extractJsonField(json, "path"))
    }

    @Test
    fun extractJsonField_nullJson_returnsNull() {
        assertEquals(null, TaskStepDeriver.extractJsonField(null, "path"))
    }

    // ===== completedCount / totalCount =====

    @Test
    fun completedCount_countsOnlyCompleted() {
        val steps = listOf(
            TaskStep("s1", TaskStepStatus.COMPLETED, toolMessageId = "t1", toolName = "Bash", command = ""),
            TaskStep("s2", TaskStepStatus.FAILED, toolMessageId = "t2", toolName = "Bash", command = ""),
            TaskStep("s3", TaskStepStatus.COMPLETED, toolMessageId = "t3", toolName = "Bash", command = "")
        )
        assertEquals(2, TaskStepDeriver.completedCount(steps))
        assertEquals(3, TaskStepDeriver.totalCount(steps))
    }
}
