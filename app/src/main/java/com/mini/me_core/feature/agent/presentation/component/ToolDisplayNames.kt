package com.mini.me_core.feature.agent.presentation.component

/**
 * 工具名的人话摘要映射。
 *
 * 解决的问题（审计 P2-16）：
 *  - 模型侧工具名是给程序看的（camelCase / snake_case），直接显示给用户既不好读，
 *    也与代码块语言角标（等宽 + 大写）的呈现规范不一致；
 *  - 对话流里同一工具可能以不同大小写出现（"Bash" vs "bash"），需归一。
 *
 * 设计约束：
 *  - 纯函数、无 Android 依赖，可独立单测；
 *  - 未登记的新工具不猜译，原样返回（由 [ToolDisplayNames.showRawName] 决定是否附带原始名），
 *    保证新增工具无需改这里也有合理降级显示；
 *  - 只做「展示层文案」，不影响工具派发、权限规则等任何逻辑（那些一律用原始名）。
 */
object ToolDisplayNames {

    /** 已知工具的人话摘要。key 为注册时的原始工具名（见 AgentModule.registerTool）。 */
    private val LABELS: Map<String, String> = mapOf(
        "readFile" to "读取文件",
        "sendFile" to "发送文件",
        "viewImage" to "查看图片",
        "writeFile" to "写入文件",
        "editFile" to "编辑文件",
        "Bash" to "执行命令",
        "run_code" to "运行代码",
        "check_environment" to "环境探测",
        "ensure_android_env" to "准备 Android 环境",
        "switch_container_arch" to "切换容器架构",
        "terminal" to "终端会话",
        "list" to "列出目录",
        "search" to "搜索代码",
        "loadSkill" to "加载技能",
        "runSkillScript" to "运行技能脚本",
        "load_rule" to "加载规则",
        "loadSop" to "加载 SOP",
        "askUserQuestion" to "向你提问",
        "manageMcp" to "管理 MCP",
        "websearch" to "联网搜索",
        "webfetch" to "抓取网页",
        "switchMode" to "切换模式",
        "todo" to "待办清单",
        "memory" to "记忆管理",
        "generateImage" to "生成图片",
        "browser" to "浏览器",
        "device_storage" to "设备存储",
        "network_proxy" to "网络代理",
        "goal" to "任务目标",
        "job_start" to "启动后台任务",
        "job_status" to "查询后台任务",
        "job_kill" to "结束后台任务",
        "job_log" to "查看任务日志",
        "schedule" to "定时提醒",
        "plan" to "计划协作"
    )

    /**
     * 取工具的人话摘要；未登记的返回 null（调用方应回退到原始名）。
     *
     * @param rawName 注册用的原始工具名，大小写敏感（与注册表一致）。
     */
    fun humanLabel(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        return LABELS[rawName]
    }

    /**
     * 是否需要在人话摘要旁附带原始工具名。
     *
     * 仅在「有摘要且原始名不等于摘要」时附带，避免出现「编辑文件 editFile」之外的冗余
     * （例如未登记工具只显示原始名，不会拼出「readFile readFile」）。
     */
    fun showRawName(rawName: String?, humanLabel: String?): Boolean {
        if (rawName.isNullOrBlank() || humanLabel == null) return false
        return rawName != humanLabel
    }
}
