package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

/**
 * 错误诊断结果。
 *
 * @property title 诊断标题（简短描述）
 * @property suggestion 修复建议（用户可读的建议文本）
 * @property fixCommand 可选的修复命令（如果有），用户可复制后手动执行
 */
data class ErrorDiagnosis(
    val title: String,
    val suggestion: String,
    val fixCommand: String? = null
)

/**
 * 错误诊断器。
 *
 * 内置常见错误模式识别（纯前端，不调用 Agent）。诊断提示是建议性的，
 * 显示"复制修复命令"按钮，不自动执行。
 *
 * 识别规则（按优先级）：
 * 1. apk 数据库锁错误 → 建议清理锁文件
 * 2. 命令未找到 → 建议安装
 * 3. 权限不足 → 提示权限不足
 * 4. 磁盘空间不足 → 提示清理磁盘
 * 5. 命令执行超时 → 建议增大 timeout 或拆分命令
 * 6. 网络连接失败 → 建议检查网络
 * 7. 语法错误 → 建议检查命令语法
 *
 * 注意：
 * - 诊断提示是建议性的，不自动执行修复命令
 * - 修复命令基于常见错误模式推断，可能不准确（如 Alpine 特定路径）
 * - 复制命令前应显示警告："以下修复命令基于常见错误模式推断，请确认后执行"
 */
object ErrorDiagnoser {

    /**
     * 诊断工具输出中的错误。
     *
     * @param content 工具输出文本
     * @param toolName 工具名（可选，用于特定工具的错误模式）
     * @return 诊断结果，如果无法识别则返回 null
     */
    fun diagnose(content: String, toolName: String? = null): ErrorDiagnosis? {
        if (content.isBlank()) return null
        val lower = content.lowercase()

        // 1. apk 数据库锁错误
        if (lower.contains("unable to lock database") ||
            lower.contains("failed to open apk database") ||
            lower.contains("database is locked") && lower.contains("apk")) {
            return ErrorDiagnosis(
                title = "APK 数据库被锁定",
                suggestion = "另一个 apk 进程可能正在运行或异常退出。请先终止残留进程，再清理锁文件后重试。",
                fixCommand = "rm -f /lib/apk/db/lock"
            )
        }

        // 2. 命令未找到
        if (lower.contains("command not found") ||
            lower.contains("not found") && lower.contains("sh: ")) {
            val missingCommand = extractMissingCommand(content)
            return ErrorDiagnosis(
                title = "命令未找到${if (missingCommand != null) ": $missingCommand" else ""}",
                suggestion = "该命令可能未安装。请先安装对应的软件包，或检查命令拼写是否正确。",
                fixCommand = if (missingCommand != null) "apk add $missingCommand" else null
            )
        }

        // 3. 权限不足
        if (lower.contains("permission denied") ||
            lower.contains("operation not permitted") ||
            lower.contains("access denied")) {
            return ErrorDiagnosis(
                title = "权限不足",
                suggestion = "当前用户没有足够的权限执行此操作。请检查文件权限或使用更高权限执行。",
                fixCommand = null
            )
        }

        // 4. 磁盘空间不足
        if (lower.contains("no space left") ||
            lower.contains("disk full") ||
            lower.contains("no space left on device")) {
            return ErrorDiagnosis(
                title = "磁盘空间不足",
                suggestion = "容器存储空间已满。请清理不必要的文件、缓存或日志后重试。",
                fixCommand = "df -h && du -sh /root/* 2>/dev/null | sort -rh | head -10"
            )
        }

        // 5. 命令执行超时
        if (ToolStateDeriver.containsTimeoutMarker(content)) {
            return ErrorDiagnosis(
                title = "命令执行超时",
                suggestion = "命令执行时间超过限制。建议增大 timeout 参数，或将复杂命令拆分为多个小步骤执行。",
                fixCommand = null
            )
        }

        // 6. 网络连接失败
        if (lower.contains("connection refused") ||
            lower.contains("connection timed out") ||
            lower.contains("could not resolve host") ||
            lower.contains("network is unreachable") ||
            lower.contains("failed to connect")) {
            return ErrorDiagnosis(
                title = "网络连接失败",
                suggestion = "无法连接到目标服务器。请检查网络连接、代理设置或目标服务器状态。",
                fixCommand = null
            )
        }

        // 7. 语法错误（Shell）
        if (lower.contains("syntax error") ||
            lower.contains("unexpected token") ||
            lower.contains("unexpected end of file")) {
            return ErrorDiagnosis(
                title = "命令语法错误",
                suggestion = "Shell 命令存在语法错误。请检查引号、括号是否匹配，命令是否完整。",
                fixCommand = null
            )
        }

        // 8. 文件不存在
        if (lower.contains("no such file or directory") ||
            lower.contains("not a directory")) {
            return ErrorDiagnosis(
                title = "文件或目录不存在",
                suggestion = "指定的文件或目录不存在。请检查路径是否正确，或先创建所需的目录。",
                fixCommand = null
            )
        }

        // 无法识别
        return null
    }

    /**
     * 从错误文本中提取未找到的命令名。
     *
     * 纯函数，可单元测试。匹配 "sh: xxx: command not found" 或 "xxx: command not found" 格式。
     */
    fun extractMissingCommand(content: String): String? {
        // 匹配 "sh: xxx: command not found"
        val pattern1 = Regex("""sh:\s+(\S+):\s+command not found""", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(content)
        if (match1 != null) return match1.groupValues[1]

        // 匹配 "xxx: command not found"
        val pattern2 = Regex("""(\S+):\s+command not found""", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(content)
        if (match2 != null) return match2.groupValues[1]

        return null
    }
}
