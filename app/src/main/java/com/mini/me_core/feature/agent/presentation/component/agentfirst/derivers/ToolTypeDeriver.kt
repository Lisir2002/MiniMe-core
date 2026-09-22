package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

/**
 * 工具类型分类。用于 ToolCallCard 的图标和颜色区分。
 *
 * 通过关键词匹配从 toolName 和 toolArgs 推断，不引入新的枚举字段。
 * 特殊类型（ENV/TODO/USAGE）保留现有特殊渲染分支，不走通用 ToolCallCard。
 */
enum class ToolType {
    /** Shell 执行（Bash、terminal、run_code）。 */
    SHELL,

    /** 文件读取（readFile、viewImage、list、search）。 */
    FILE_READ,

    /** 文件写入（writeFile、editFile、create）。 */
    FILE_WRITE,

    /** 构建/运行（build、gradle、make、npm run、run_code）。 */
    BUILD_RUN,

    /** 搜索（search、find、grep、websearch、webfetch、browser）。 */
    SEARCH,

    /** MCP 工具（manageMcp 或来自 MCP 服务器注册的工具）。 */
    MCP,

    /** 环境探测（check_environment）— 保留 EnvironmentStatusStrip 分支。 */
    ENV,

    /** 待办事项（todo）— 保留 TodoCard 分支。 */
    TODO,

    /** 用量统计（usage）— 紧凑文本。 */
    USAGE,

    /** 其他工具。 */
    OTHER
}

/**
 * 工具类型识别器。
 *
 * 从 toolName 和 toolArgs 推断工具类型。纯函数，可单元测试。
 *
 * 识别规则（按优先级）：
 * 1. toolName == "check_environment" → ENV
 * 2. toolName == "todo" → TODO
 * 3. toolName == "usage" → USAGE
 * 4. toolName 含 "mcp" → MCP
 * 5. toolName == "Bash" || "terminal" → SHELL（需进一步检查 toolArgs 中的命令是否为构建命令）
 * 6. toolName 含 "write"/"edit"/"create" → FILE_WRITE
 * 7. toolName 含 "read"/"view"/"list" → FILE_READ
 * 8. toolName 含 "run"/"build"/"gradle"/"make" → BUILD_RUN
 * 9. toolName 含 "search"/"find"/"grep"/"web"/"browser" → SEARCH
 * 10. 其他 → OTHER
 *
 * 对于 Bash 工具，额外检查 toolArgs 中的 command 字段：
 * - 含 "gradle"/"make"/"npm run"/"cargo build"/"pip install"/"apk add" → BUILD_RUN
 * - 含 "cat"/"head"/"tail"/"ls"/"find"/"grep" → FILE_READ（只读命令）
 */
object ToolTypeDeriver {

    /**
     * 从工具名和参数推断工具类型。
     *
     * @param toolName 工具名（如 "Bash"、"readFile"、"writeFile"）
     * @param toolArgs 工具参数（JSON 文本，Bash 工具含 command 字段）
     * @return 推断的工具类型
     */
    fun derive(toolName: String?, toolArgs: String? = null): ToolType {
        if (toolName.isNullOrBlank()) return ToolType.OTHER
        val name = toolName.lowercase()

        // 特殊类型优先（保留现有特殊渲染分支）
        return when {
            name == "check_environment" -> ToolType.ENV
            name == "todo" -> ToolType.TODO
            name == "usage" -> ToolType.USAGE
            name.contains("mcp") -> ToolType.MCP

            // Shell 类：需进一步检查命令内容
            name == "bash" || name == "terminal" -> deriveBashType(toolArgs)

            // 文件写入
            name.contains("write") || name.contains("edit") || name.contains("create") -> ToolType.FILE_WRITE

            // 文件读取
            name.contains("read") || name.contains("view") || name == "list" -> ToolType.FILE_READ

            // 构建/运行
            name.contains("build") || name.contains("gradle") || name.contains("make") ||
                name.contains("run_code") -> ToolType.BUILD_RUN

            // 搜索
            name.contains("search") || name.contains("find") || name.contains("grep") ||
                name.contains("web") || name.contains("browser") -> ToolType.SEARCH

            else -> ToolType.OTHER
        }
    }

    /**
     * 从 Bash 工具的 command 字段推断具体类型。
     *
     * 构建命令（gradle/make/npm run/cargo build/pip install/apk add）→ BUILD_RUN
     * 只读命令（cat/head/tail/ls/find/grep）→ FILE_READ
     * 其他 → SHELL
     */
    private fun deriveBashType(toolArgs: String?): ToolType {
        if (toolArgs.isNullOrBlank()) return ToolType.SHELL
        val command = extractCommand(toolArgs).lowercase()
        if (command.isBlank()) return ToolType.SHELL

        return when {
            // 构建/安装命令
            command.contains("gradle") || command.contains("make ") ||
                command.contains("npm run") || command.contains("cargo build") ||
                command.contains("pip install") || command.contains("apk add") ||
                command.contains("apt install") || command.contains("npm install") ||
                command.contains("yarn install") || command.contains("go build") ||
                command.contains("cmake") || command.contains("mvn ") ||
                command.contains("dotnet build") -> ToolType.BUILD_RUN

            // 只读命令
            command.startsWith("cat ") || command.startsWith("head ") ||
                command.startsWith("tail ") || command.startsWith("ls ") ||
                command.startsWith("find ") || command.startsWith("grep ") ||
                command == "cat" || command == "ls" || command == "pwd" -> ToolType.FILE_READ

            else -> ToolType.SHELL
        }
    }

    /**
     * 从 toolArgs JSON 中提取 command 字段值。
     *
     * 纯函数，可单元测试。Bash 工具的 toolArgs 格式通常为 {"command": "..."}。
     * 如果解析失败，返回空字符串。
     */
    fun extractCommand(toolArgs: String): String {
        // 简单 JSON 解析：查找 "command" 字段的值
        val commandPattern = Regex(""""command"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val match = commandPattern.find(toolArgs)
        return match?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?: ""
    }
}
