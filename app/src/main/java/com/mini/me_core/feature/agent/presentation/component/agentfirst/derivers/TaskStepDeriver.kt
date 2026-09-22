package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType

/**
 * 任务步骤状态。
 */
enum class TaskStepStatus {
    /** 等待执行。 */
    PENDING,

    /** 执行中。 */
    RUNNING,

    /** 已完成（成功）。 */
    COMPLETED,

    /** 失败。 */
    FAILED,

    /** 已取消。 */
    CANCELLED
}

/**
 * 任务步骤数据类。
 *
 * 从 TOOL 子分组推导，每个 TOOL 消息对应一个步骤。
 *
 * @property title 步骤标题（从工具命令关键词推导）
 * @property status 步骤状态
 * @property durationMs 执行耗时（毫秒），0 表示未知
 * @property toolMessageId 对应的工具消息 id
 * @property toolName 工具名
 * @property command 命令摘要（前 30 字）
 */
data class TaskStep(
    val title: String,
    val status: TaskStepStatus,
    val durationMs: Long = 0,
    val toolMessageId: String,
    val toolName: String?,
    val command: String
)

/**
 * 任务步骤推导器。
 *
 * 从 TaskGroup 的 TOOL 子分组推导步骤列表。纯函数，可单元测试。
 *
 * 推导逻辑：
 * 1. 从 group.subGroups 筛选 type==TOOL 的子分组
 * 2. 每个 TOOL 子分组内的每条消息推导为一个步骤
 * 3. 步骤标题：从 toolArgs 的 command 字段提取关键词匹配
 * 4. 步骤状态：检查 isError 和 runningTool 匹配
 *
 * 步骤标题关键词匹配规则：
 * - apk add / apt install / pip install / npm install → "安装依赖"
 * - git clone → "初始化仓库"
 * - git pull / git fetch → "同步代码"
 * - git commit / git push → "提交代码"
 * - gradle build / make / npm run build / cargo build → "构建项目"
 * - gradle test / npm test / pytest → "运行测试"
 * - writeFile → "创建文件: {filename}"
 * - editFile → "修改文件: {filename}"
 * - readFile / cat / head / tail → "读取文件"
 * - rm / delete → "删除文件"
 * - cd / mkdir / mv / cp → "文件操作"
 * - curl / wget → "下载文件"
 * - chmod / chown → "修改权限"
 * - export / source → "环境配置"
 * - 其他 → "执行命令: {前30字}"
 */
object TaskStepDeriver {

    /**
     * 从任务组推导步骤列表。
     *
     * @param group 任务分组
     * @param runningTools 当前会话所有运行中工具
     * @return 步骤列表（按时间顺序）
     */
    fun derive(group: TaskGroup, runningTools: List<RunningToolOutput>): List<TaskStep> {
        val toolMessages = TaskStateDeriver.collectToolMessages(group)
        if (toolMessages.isEmpty()) return emptyList()

        return toolMessages.map { message ->
            deriveStep(message, runningTools)
        }
    }

    /**
     * 推导单个步骤。
     */
    private fun deriveStep(
        message: AgentUIMessage,
        runningTools: List<RunningToolOutput>
    ): TaskStep {
        val isRunning = runningTools.any { it.messageId == message.id }
        val command = ToolTypeDeriver.extractCommand(message.toolArgs ?: "")
        val title = deriveTitle(message.toolName, command, message.toolArgs)

        val status = when {
            isRunning -> TaskStepStatus.RUNNING
            ToolStateDeriver.containsCancelledMarker(message.content) -> TaskStepStatus.CANCELLED
            message.isError -> TaskStepStatus.FAILED
            else -> TaskStepStatus.COMPLETED
        }

        return TaskStep(
            title = title,
            status = status,
            toolMessageId = message.id,
            toolName = message.toolName,
            command = command.take(30)
        )
    }

    /**
     * 推导步骤标题。
     *
     * 优先从 toolName 判断（writeFile/editFile 等非 Bash 工具），
     * 再从 command 关键词匹配（Bash 工具）。
     */
    fun deriveTitle(toolName: String?, command: String, toolArgs: String?): String {
        // 非 Bash 工具：从 toolName 判断
        when (toolName) {
            "writeFile" -> {
                val filename = extractJsonField(toolArgs, "path") ?: extractJsonField(toolArgs, "file")
                return if (filename != null) "创建文件: $filename" else "创建文件"
            }
            "editFile" -> {
                val filename = extractJsonField(toolArgs, "path") ?: extractJsonField(toolArgs, "file")
                return if (filename != null) "修改文件: $filename" else "修改文件"
            }
            "readFile" -> {
                val filename = extractJsonField(toolArgs, "path") ?: extractJsonField(toolArgs, "file")
                return if (filename != null) "读取文件: $filename" else "读取文件"
            }
            "viewImage" -> return "查看图片"
            "list" -> return "列出文件"
            "search" -> return "搜索代码"
            "websearch" -> return "网页搜索"
            "webfetch" -> return "网页抓取"
            "todo" -> return "管理待办"
            "goal" -> return "管理目标"
            "plan" -> return "制定计划"
            "manageMcp" -> return "管理 MCP 工具"
            "check_environment" -> return "环境探测"
            "usage" -> return "用量统计"
            "run_code" -> return "运行代码"
            "terminal" -> return "终端命令"
        }

        // Bash 工具：从 command 关键词匹配
        if (command.isNotBlank()) {
            return deriveBashTitle(command)
        }

        // 兜底
        return "执行工具: ${toolName ?: "未知"}"
    }

    /**
     * 从 Bash 命令推导步骤标题。
     */
    private fun deriveBashTitle(command: String): String {
        val cmd = command.lowercase().trim()

        return when {
            // 安装依赖
            cmd.contains("apk add") || cmd.contains("apt install") ||
                cmd.contains("apt-get install") || cmd.contains("pip install") ||
                cmd.contains("npm install") || cmd.contains("yarn install") ||
                cmd.contains("pnpm install") || cmd.contains("cargo install") ||
                cmd.contains("go install") -> "安装依赖"

            // 初始化仓库
            cmd.startsWith("git clone") -> "初始化仓库"

            // 同步代码
            cmd.startsWith("git pull") || cmd.startsWith("git fetch") -> "同步代码"

            // 提交代码
            cmd.startsWith("git commit") || cmd.startsWith("git push") -> "提交代码"

            // 构建项目
            (cmd.contains("gradle") || cmd.contains("gradlew")) &&
                (cmd.contains("build") || cmd.contains("assemble") || cmd.contains("bundle")) ||
                cmd.contains("make ") || cmd == "make" ||
                cmd.contains("npm run build") || cmd.contains("yarn build") ||
                cmd.contains("cargo build") || cmd.contains("go build") ||
                cmd.contains("cmake") || cmd.contains("mvn ") ||
                cmd.contains("mvnw ") || cmd.contains("dotnet build") -> "构建项目"

            // 运行测试
            (cmd.contains("gradle") || cmd.contains("gradlew")) && cmd.contains("test") ||
                cmd.contains("npm test") ||
                cmd.contains("yarn test") || cmd.contains("pytest") ||
                cmd.contains("go test") || cmd.contains("cargo test") ||
                cmd.contains("phpunit") -> "运行测试"

            // 读取文件
            cmd.startsWith("cat ") || cmd.startsWith("head ") ||
                cmd.startsWith("tail ") || cmd.startsWith("less ") ||
                cmd == "cat" || cmd == "ls" || cmd == "pwd" -> "读取文件"

            // 删除文件
            cmd.startsWith("rm ") || cmd.startsWith("rmdir ") ||
                cmd.contains("unlink") -> "删除文件"

            // 文件操作
            cmd.startsWith("cd ") || cmd.startsWith("mkdir ") ||
                cmd.startsWith("mv ") || cmd.startsWith("cp ") ||
                cmd.startsWith("touch ") || cmd.startsWith("ln ") -> "文件操作"

            // 下载文件
            cmd.startsWith("curl ") || cmd.startsWith("wget ") -> "下载文件"

            // 修改权限
            cmd.startsWith("chmod ") || cmd.startsWith("chown ") -> "修改权限"

            // 环境配置
            cmd.startsWith("export ") || cmd.startsWith("source ") ||
                cmd.startsWith("alias ") || cmd.contains("echo ") -> "环境配置"

            // 运行服务
            cmd.contains("npm start") || cmd.contains("npm run dev") ||
                cmd.contains("yarn dev") || cmd.contains("python -m http.server") ||
                cmd.contains("python3 -m http.server") -> "启动服务"

            // 兜底：执行命令 + 前30字
            else -> "执行命令: ${command.take(30)}"
        }
    }

    /**
     * 从 JSON 文本中提取指定字段的值。
     *
     * 纯函数，可单元测试。用于提取 writeFile/editFile 的 path/filename 字段。
     */
    fun extractJsonField(json: String?, field: String): String? {
        if (json.isNullOrBlank()) return null
        val pattern = Regex(""""$field"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
    }

    /**
     * 计算已完成步骤数。
     */
    fun completedCount(steps: List<TaskStep>): Int {
        return steps.count { it.status == TaskStepStatus.COMPLETED }
    }

    /**
     * 计算总步骤数。
     */
    fun totalCount(steps: List<TaskStep>): Int {
        return steps.size
    }
}
