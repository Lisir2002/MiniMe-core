package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ParameterType
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCapability
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserController
import com.mini.me_core.feature.browser.domain.BrowserCredential
import com.mini.me_core.feature.browser.domain.BrowserCredentialStore
import com.mini.me_core.feature.browser.domain.BrowserDownloadInfo
import com.mini.me_core.feature.browser.domain.BrowserElement
import com.mini.me_core.feature.browser.domain.BrowserLoginPromptManager
import com.mini.me_core.feature.browser.domain.BrowserNetworkRecord
import com.mini.me_core.feature.browser.domain.BrowserOperationController
import com.mini.me_core.feature.browser.domain.BrowserPageSnapshot
import com.mini.me_core.feature.browser.domain.BrowserSnapshotDelta
import com.mini.me_core.feature.browser.domain.BrowserTabInfo
import com.mini.me_core.feature.browser.domain.BrowserTakeoverManager
import com.mini.me_core.feature.browser.domain.SnapshotLevel
import com.mini.me_core.feature.browser.domain.antidetect.AntidetectController
import com.mini.me_core.feature.browser.domain.antidetect.SignalDetector
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintGenerator
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintProfile
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintValidator
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import javax.inject.Inject

/**
 * 内置服务浏览器工具：让模型在共享 WebView 会话中浏览网页并操作页面。
 *
 * 与用户共享受同一浏览会话（同一份 Cookie/登录态）：模型能浏览、点击、输入、提交、
 * 截图、执行 JS；用户手动登录后模型自动复用登录态，模型操作在浏览器页实时可见。
 *
 * 支持访问外网 https/http，也支持容器内开发服务（http://localhost:PORT，PRoot 与宿主机
 * 共享网络栈，容器内服务直接绑在 Android 网络栈上）。
 *
 * 动作清单：
 *  - navigate(url)          打开网页
 *  - snapshot()             提取页面（快照分级：summary/standard/full，见 snapshot_level）
 *  - page_text()            单独取页面正文（按需取文，省 token）
 *  - click(element_id)      点击元素
 *  - type(element_id,text)  输入文本
 *  - select_option(id,val)  下拉选择
 *  - submit(element_id?)    提交表单（id 可空→提交页面首个表单）
 *  - scroll(direction)      滚动（top/bottom/up/down）
 *  - screenshot()           页面截图（返回 data URL，多模态模型查看）
 *  - evaluate(js)           执行任意 JS
 *  - wait_for(selector)     等待元素出现
 *  - wait_for_change()      事件驱动等待页面变化（替代轮询 snapshot）
 *  - history()              查询最近动作历史
 *  - get_attribute(id,attr) 读元素属性
 *  - handle_dialog(accept)  处理页面 alert/confirm
 *  - login()                登录当前站点（从加密凭据库取账号密码自动代填；无则请用户输入）
 *
 * 统一返回 envelope（R2.3 干净替换）：所有动作返回
 * `{ok, action, changed, summary, note|error, recoverable, snapshot?, delta?}`，
 * 写操作自动返回增量 delta 让模型无需自行轮询。
 * `element_id` 接受 data-rcb-id / CSS 绝对路径 / 语义描述符三者任一（三级定位）。
 */
class BrowserAgentTool @Inject constructor(
    private val browserController: BrowserController,
    private val credentialStore: BrowserCredentialStore,
    private val loginPromptManager: BrowserLoginPromptManager,
    private val takeoverManager: BrowserTakeoverManager,
    private val pathMapper: WorkspacePathMapper,
    private val fingerprintManager: FingerprintManager,
    private val antidetectController: AntidetectController,
    private val operationController: BrowserOperationController
) : AgentTool() {

    private companion object {
        const val TAG = "BrowserAgentTool"
        const val MAX_ELEMENTS = 120
        const val MAX_TEXT = 8000
        // 全局操作超时：防止 WebView 无响应时协程永久挂起（如本地服务器断开后 click 卡死）
        const val GLOBAL_TIMEOUT_MS = 30_000L
        // 连续失败熔断阈值：达到后拒绝继续操作，提示模型检查页面状态，避免死循环
        const val FAILURE_CIRCUIT_THRESHOLD = 3
        // 浏览器交互动作集合：反爬暂停时这些动作被拒绝，管理类动作不受限
        val BROWSER_INTERACT_ACTIONS = setOf(
            "navigate", "view", "snapshot", "page_text", "extract",
            "click", "type", "fill_form", "select_option", "submit",
            "scroll", "hover", "drag", "press_key", "upload_file",
            "back", "forward", "reload", "screenshot", "evaluate",
            "wait_for", "wait_for_change", "wait_for_network_idle",
            "get_attribute", "new_tab", "switch_tab", "close_tab",
            "snapshot_shadow", "list_iframes", "iframe_action",
            "safe_click", "human_type", "action_chain",
            "spa_navigate", "wait_for_request"
        )
    }

    // 连续失败计数：成功时重置，失败时递增，达到阈值后触发熔断
    @Volatile private var consecutiveFailures = 0
    // 熔断状态标记：熔断后所有操作直接返回错误，直到页面恢复（navigate/view 成功自动重置）
    @Volatile private var circuitBroken = false

    private val json = Json { ignoreUnknownKeys = true }

    override val name = "browser"
    override val description = buildString {
        append("操作内置服务浏览器，与用户共享同一会话和登录态。支持外网 https/http 与容器内 http://localhost:PORT。\n")
        append("\n")
        append("## 核心原则\n")
        append("1. 观察优先：用 view 而非反复 snapshot；写操作后看 changed+delta 验证，无需重新 snapshot。\n")
        append("2. 失败换策略：同一动作连续失败 2 次必须换方法（换定位方式/换动作/先 view 确认页面状态），禁止同样参数重复重试。\n")
        append("3. 等待用事件：用 wait_for_change 等待页面变化，而非轮询 snapshot；用 wait_for_network_idle 等待网络空闲。\n")
        append("4. 读返回 envelope：所有动作返回 {ok, action, changed, summary, note|error, error_code, recoverable, snapshot?, delta?}，必须检查 ok 和 error_code。\n")
        append("5. 省 token 优先：默认 summary 级快照（控件摘要），仅需完整元素时用 standard，需正文时用 full。\n")
        append("6. 批量操作：fill_form 一次性填写多个字段，action_chain 串联多个动作，减少往返次数。\n")
        append("7. 危险操作先确认：涉及支付、删除、提交不可撤销表单时，先 takeover 请求用户确认。\n")
        append("\n")
        append("## 标准流程\n")
        append("view/navigate(url) → 读 summary 识别页面类型 → click/type/fill_form/submit 操作 → 看 changed+delta 验证结果 → wait_for_change 等后续变化 → 必要时 screenshot 确认视觉效果。\n")
        append("\n")
        append("## 错误处理决策表（必须按 error_code 对应处理）\n")
        append("- CONNECTION_REFUSED：目标服务已停止（常见于 localhost 本地开发服务断开）。先 navigate 确认页面状态，检查服务是否启动，不要继续 click/type。\n")
        append("- TIMEOUT：操作 30 秒无响应，页面可能卡死。调用 reload 或 navigate 重新加载，不要重复同样操作。\n")
        append("- PAGE_UNRESPONSIVE：WebView 无响应。调用 reload 页面，严重时 navigate 到目标 URL 重新加载。\n")
        append("- ELEMENT_NOT_FOUND：页面中不存在指定元素。先 view(snapshot_level=standard) 看当前页面结构，调整 element_id（换 data-rcb-id/CSS/语义描述符），不要重复同样的定位。\n")
        append("- DNS_FAILURE：无法解析主机名。检查 URL 是否正确，网络/代理是否正常。\n")
        append("- CONNECTION_TIMEOUT：服务器响应过慢或不可达。稍后重试，或检查网络/代理设置。\n")
        append("- CIRCUIT_BROKEN：连续 3 次失败触发熔断。必须先调用 navigate 或 view 重置熔断状态，再继续其他操作。\n")
        append("- 其他 error：阅读 error 字段中的具体建议，按建议处理。\n")
        append("\n")
        append("## 效率技巧\n")
        append("- view 自动等待页面稳定、识别页面类型（article/search_results/product/login/unknown）并返回对应级别快照，比 navigate+snapshot 更高效。\n")
        append("- 写操作（click/type/fill_form/submit/select_option）返回 delta 增量对比，直接看 changed 和 delta 即可验证操作效果。\n")
        append("- page_text 单独取页面正文，比 full 级快照更省 token。\n")
        append("- extract 支持按 selector 或模式结构化抽取，比 snapshot 后手动解析更高效。\n")
        append("- wait_for_change 是事件驱动的，比轮询 snapshot 更省 token 且响应更快。\n")
        append("- safe_click 内置等待元素可点击+重试，比手动 click+wait_for 更可靠。\n")
        append("- human_type 模拟人类输入节奏，降低被反爬检测的概率。\n")
        append("- network/list_api_calls 可查看页面发起的 API 请求，SPA 页面直接调 API 比模拟点击更高效。\n")
        append("- detect_framework 识别前端框架（React/Vue/Angular），针对性选择操作策略。\n")
        append("- macro_record/macro_playback 可录制和回放重复操作序列。\n")
        append("\n")
        append("## 场景化引导\n")
        append("- 搜索场景：view(搜索URL) → 读 summary 识别结果列表 → click 第一个结果链接 → wait_for_change → view 读文章。\n")
        append("- 表单填写：view 确认表单存在 → fill_form 批量填写 → submit 提交 → 看 changed+delta 验证提交结果。\n")
        append("- 数据抓取：view 识别页面类型 → extract 结构化抽取 → paginate_extract/infinite_scroll_extract 处理分页/无限滚动。\n")
        append("- SPA 单页应用：detect_framework 识别框架 → network 监听 API → 直接 replay_api 或用 spa_navigate，避免页面刷新。\n")
        append("- 本地开发服务：访问 http://localhost:PORT 前确认服务正在运行；遇到 CONNECTION_REFUSED 说明服务已断开，需重启服务后再操作。\n")
        append("- 反爬页面：apply_stealth 启用隐身模式 → human_type 模拟输入 → safe_click 安全点击 → detect_captcha 检测验证码，遇到验证码立即 takeover。\n")
        append("\n")
        append("## 元素定位策略（element_id 三种方式任选）\n")
        append("- data-rcb-id：快照中元素自带的稳定 ID，最可靠，优先使用。\n")
        append("- CSS 绝对路径：如 /html/body/div[2]/form/input[1]，页面结构变化时易失效。\n")
        append("- 语义描述符：role=button name=提交 index=0，最灵活但可能匹配多个元素，用 index 精确指定。\n")
        append("- 定位失败时：先 view(standard) 查看当前元素列表，换一种定位方式，不要重复同样的定位。\n")
        append("\n")
        append("## 页面状态判断\n")
        append("- 页面加载完成：view 自动等待页面稳定；或 wait_for_network_idle 等待网络空闲。\n")
        append("- 操作成功：写操作返回 changed=true 且 delta 中有变化；或 wait_for_change 检测到页面变化。\n")
        append("- 需要等待：操作后页面未立即变化时，用 wait_for_change（事件驱动）而非轮询 snapshot。\n")
        append("- 页面跳转：navigate 后用 view 确认新页面加载完成，不要立即操作。\n")
        append("\n")
        append("## 禁忌清单\n")
        append("- 不要在同一元素上连续 click 超过 2 次，失败后先 view 确认元素状态。\n")
        append("- 不要在 wait_for 超时后立即同样参数重试，应调整 selector 或 timeout。\n")
        append("- 不要忽略 error_code，每种错误码对应不同的处理策略。\n")
        append("- 不要在 CIRCUIT_BROKEN 状态下继续非 navigate/view 操作。\n")
        append("- 不要反复 snapshot，写操作后看 delta 即可，观察用 view。\n")
        append("- 不要在页面未加载完成时立即操作，先 view 或 wait_for_network_idle。\n")
        append("- 不要用 full 级快照做常规观察，默认 summary 即可。\n")
        append("- 不要在遇到验证码/支付/二次认证时强行尝试，立即 takeover 请求用户接管。\n")
        append("- 不要在 localhost 服务断开后继续操作，先确认服务运行状态。\n")
        append("- 不要删除/提交不可逆操作前不确认，先 takeover 让用户确认。\n")
        append("\n")
        append("## 指纹与反爬系统\n")
        append("浏览器内置指纹伪装和反爬检测系统，支持多套浏览器指纹配置切换、自动检测反爬信号、自适应调整策略。\n")
        append("\n")
        append("### 什么时候该切换指纹\n")
        append("- 访问新地区/国家的网站时（如从美国站点切换到日本站点），切换为对应地区的指纹以降低被检测概率。\n")
        append("- 被反爬系统检测后（返回验证码/403/拦截页），切换到新指纹继续操作，旧指纹进入冷却期。\n")
        append("- 长时间使用同一指纹操作同一站点后，切换指纹降低关联追踪风险。\n")
        append("- 操作：先 fingerprint_list 查看可用配置 → fingerprint_set 切换（apply_now=true 自动重载页面）。\n")
        append("\n")
        append("### 什么时候该调用 antidetect_adjust\n")
        append("- 遇到验证码（captcha）、403 Forbidden、Cloudflare/Akamai 拦截页时。\n")
        append("- 连续操作失败、页面返回 429 限流时。\n")
        append("- 工具返回 note 中提示「检测到高风险反爬信号」时。\n")
        append("- 策略选择：auto（自动推荐）/ switch_fingerprint（切换指纹）/ slow_down（降低频率）/ cool_down（暂停冷却）。\n")
        append("\n")
        append("### 指纹动作清单\n")
        append("- fingerprint_list：列出所有配置（支持 status/region/browser 筛选）。\n")
        append("- fingerprint_get：查看当前或指定配置详情（参数 profile_id，不传则返回当前）。\n")
        append("- fingerprint_set：切换到指定配置（参数 profile_id, apply_now=true 自动重载页面）。\n")
        append("- fingerprint_create：创建随机配置（参数 template=region_us/region_cn/region_jp/region_eu/high_end/mobile，name）。\n")
        append("- fingerprint_delete：删除配置（参数 profile_id, force=true 可删除当前使用的）。\n")
        append("- fingerprint_validate：校验配置内部一致性（UA/OS/地区/硬件是否匹配）。\n")
        append("- fingerprint_test：测试当前指纹（返回一致性评分和脚本大小）。\n")
        append("\n")
        append("### 反爬动作清单\n")
        append("- antidetect_status：查看当前反爬状态（detected/riskLevel/healthScore/consecutiveFailures/paused）。\n")
        append("- antidetect_signals：查看活跃信号和历史记录。\n")
        append("- antidetect_adjust：执行自适应调整（参数 strategy, reason）。\n")
        append("- antidetect_pause：暂停浏览器操作（参数 duration_seconds，默认 300 秒）。\n")
        append("- antidetect_resume：恢复浏览器操作。\n")
        append("- browser_status：综合状态一览（当前指纹+反爬状态+当前URL）。\n")
        append("\n")
        append("### 指纹/反爬禁忌\n")
        append("- 不要在同一指纹下频繁切换IP，指纹与IP地理不一致是反爬检测的重要信号。\n")
        append("- 不要被检测到后继续同样操作，应先调用 antidetect_adjust 或切换指纹。\n")
        append("- 不要在 antidetect_pause 暂停期间强行执行浏览器操作（会返回 ANTIDETECT_PAUSED 错误）。\n")
        append("- 不要频繁创建和删除指纹配置，预置配置已足够覆盖常见场景。\n")
        append("- 真实指纹配置（default_real）不可删除，无伪装时适合访问不敏感站点。\n")
        append("\n")
        append("## 核心动作速查\n")
        append("navigate(打开)/view(智能查看)/snapshot(快照)/click(点击)/type(输入)/fill_form(批量填写)/submit(提交)/scroll(滚动)/wait_for(等元素)/wait_for_change(等变化)/screenshot(截图)/evaluate(执行JS)/back/forward/reload/login(自动登录)/takeover(请求用户接管)。\n")
        append("高级动作：extract(结构化抽取)/select_option/hover/drag/press_key/upload_file/wait_for_network_idle/history/get_attribute/handle_dialog/标签页管理/网络请求分析/反爬增强(snapshot_shadow/apply_stealth/deobfuscate)/自动化增强(safe_click/human_type/macro)/SPA专项(detect_framework/extract_ssr_data/spa_navigate/api_paginate)。\n")
        append("指纹反爬：fingerprint_list/get/set/create/delete/validate/test / antidetect_status/signals/adjust/pause/resume / browser_status。")
    }
    override val capabilities = setOf(ToolCapability.NETWORK_READ, ToolCapability.NETWORK_WRITE, ToolCapability.USER_INTERACTION)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "要执行的浏览器动作：核心动作 navigate/view/snapshot/click/type/fill_form/submit/scroll/wait_for/screenshot；高级动作 extract/select_option/hover/drag/press_key/upload_file/back/forward/reload/evaluate/wait_for_change/wait_for_network_idle/history/get_attribute/handle_dialog/login/takeover/new_tab/switch_tab/close_tab/list_tabs/downloads/network/network_get/wait_for_request；反爬增强 snapshot_shadow/list_iframes/iframe_action/intercept_api/list_api_calls/replay_api/wait_for_render_complete/detect_rendering_type/apply_stealth/deobfuscate/extract_clean_text/paginate_extract/infinite_scroll_extract；自动化增强 safe_click/human_type/macro_record/macro_playback/macro_list/macro_clear/set_request_interval/set_user_agent/list_user_agents/action_chain；会话闭环 save_session/restore_session/list_sessions/clear_data/set_incognito/wait_for_download/download_to_workspace/upload_from_url/detect_captcha/permission_audit/block_resource/operation_log/screenshot_full_page；SPA专项 detect_framework/extract_ssr_data/extract_framework_state/detect_virtual_list/spa_navigate/api_paginate；指纹反爬 fingerprint_list/fingerprint_get/fingerprint_set/fingerprint_create/fingerprint_delete/fingerprint_validate/fingerprint_test/antidetect_status/antidetect_signals/antidetect_adjust/antidetect_pause/antidetect_resume/browser_status",
            required = true,
            enum = listOf(
                "navigate", "view", "snapshot", "page_text", "extract", "click", "type", "fill_form", "select_option", "submit",
                "scroll", "hover", "drag", "press_key", "upload_file", "back", "forward", "reload",
                "screenshot", "evaluate", "wait_for", "wait_for_change", "wait_for_network_idle", "history", "get_attribute",
                "handle_dialog", "login", "takeover",
                "new_tab", "switch_tab", "close_tab", "list_tabs", "downloads",
                "network", "network_get", "wait_for_request",
                "snapshot_shadow", "list_iframes", "iframe_action",
                "intercept_api", "list_api_calls", "replay_api",
                "wait_for_render_complete", "detect_rendering_type",
                "apply_stealth", "deobfuscate", "extract_clean_text",
                "paginate_extract", "infinite_scroll_extract",
                // 第二批：自动化与健壮性
                "safe_click", "human_type",
                "macro_record", "macro_playback", "macro_list", "macro_clear",
                "set_request_interval", "set_user_agent", "list_user_agents",
                "action_chain",
                // 第三批：会话与闭环
                "save_session", "restore_session", "list_sessions", "clear_data", "set_incognito",
                "wait_for_download", "download_to_workspace", "upload_from_url",
                "detect_captcha", "permission_audit", "block_resource",
                "operation_log", "screenshot_full_page",
                // 第四批：SPA 专项
                "detect_framework", "extract_ssr_data", "extract_framework_state",
                "detect_virtual_list", "spa_navigate", "api_paginate",
                // 第五批：指纹与反爬
                "fingerprint_list", "fingerprint_get", "fingerprint_set",
                "fingerprint_create", "fingerprint_delete", "fingerprint_validate", "fingerprint_test",
                "antidetect_status", "antidetect_signals", "antidetect_adjust",
                "antidetect_pause", "antidetect_resume",
                "browser_status"
            )
        ),
        "url" to ToolParameter(
            name = "url",
            type = ParameterType.STRING,
            description = "navigate / wait_for_request 时必填；new_tab / network_get 时可选：要打开的 URL 或要匹配的请求 URL 子串（可省略协议，自动补 https://；容器服务用 http://localhost:端口）",
            required = false
        ),
        "element_id" to ToolParameter(
            name = "element_id",
            type = ParameterType.STRING,
            description = "click/type/select_option/submit/get_attribute 时必填：snapshot 返回的元素 data-rcb-id，也接受 CSS 绝对路径或语义描述符（role=… name=… index=…）三者任一；screenshot 时可选：仅截取该元素区域",
            required = false
        ),
        "snapshot_level" to ToolParameter(
            name = "snapshot_level",
            type = ParameterType.STRING,
            description = "snapshot / 各写操作返回时可选：快照粒度 summary（默认，仅控件摘要，最省 token）/ standard（含完整元素 JSON）/ full（含页面正文）。同一值也作为结果分级：summary 只回一行结论+摘要",
            required = false,
            enum = listOf("summary", "standard", "full")
        ),
        "text" to ToolParameter(
            name = "text",
            type = ParameterType.STRING,
            description = "type 时必填：要输入到输入框的文本",
            required = false
        ),
        "value" to ToolParameter(
            name = "value",
            type = ParameterType.STRING,
            description = "select_option 时必填：要选中的 option value",
            required = false
        ),
        "direction" to ToolParameter(
            name = "direction",
            type = ParameterType.STRING,
            description = "scroll 时可选：top/bottom/up/down，默认 down",
            required = false,
            enum = listOf("top", "bottom", "up", "down")
        ),
        "attribute" to ToolParameter(
            name = "attribute",
            type = ParameterType.STRING,
            description = "get_attribute 时必填：要读取的元素属性名（如 value/textContent/href/src）",
            required = false
        ),
        "selector" to ToolParameter(
            name = "selector",
            type = ParameterType.STRING,
            description = "wait_for 时必填：CSS 选择器，等待其出现在页面上",
            required = false
        ),
        "accept" to ToolParameter(
            name = "accept",
            type = ParameterType.BOOLEAN,
            description = "handle_dialog 时必填：true=确认，false=取消",
            required = false
        ),
        "js" to ToolParameter(
            name = "js",
            type = ParameterType.STRING,
            description = "evaluate 时必填：要执行的 JavaScript 代码（最后一条表达式的值会作为字符串返回）",
            required = false
        ),
        "timeout_ms" to ToolParameter(
            name = "timeout_ms",
            type = ParameterType.INTEGER,
            description = "wait_for / wait_for_change / wait_for_request / wait_for_network_idle 时可选：等待超时毫秒（wait_for 默认 10000，wait_for_change 默认 15000，wait_for_request 默认 15000，wait_for_network_idle 默认 10000）",
            required = false
        ),
        "limit" to ToolParameter(
            name = "limit",
            type = ParameterType.INTEGER,
            description = "network 时可选：返回最近多少条网络请求记录，默认 20（1-100）",
            required = false
        ),
        "network_id" to ToolParameter(
            name = "network_id",
            type = ParameterType.INTEGER,
            description = "network_get 时可选：按记录的 id 精确查询（与 url 二选一，都空则返回最新一条）",
            required = false
        ),
        "message" to ToolParameter(
            name = "message",
            type = ParameterType.STRING,
            description = "takeover 时必填：告诉用户需要亲自完成什么（如验证码、支付、二次认证、人工决策）",
            required = false
        ),
        "key" to ToolParameter(
            name = "key",
            type = ParameterType.STRING,
            description = "press_key 时必填：要按下的键（如 Enter、Escape、Tab、Backspace 或单字符）",
            required = false
        ),
        "target_element_id" to ToolParameter(
            name = "target_element_id",
            type = ParameterType.STRING,
            description = "drag 时必填：拖拽目标元素的 id（把 element_id 拖到该元素上）",
            required = false
        ),
        "file_path" to ToolParameter(
            name = "file_path",
            type = ParameterType.STRING,
            description = "upload_file 时必填：要上传的本地文件路径（容器内路径，如 ~/workspace/data/input.csv）",
            required = false
        ),
        "mode" to ToolParameter(
            name = "mode",
            type = ParameterType.STRING,
            description = "extract 时可选：text（默认，纯文本）/ links / headings / table / html",
            required = false,
            enum = listOf("text", "links", "headings", "table", "html")
        ),
        "tab_id" to ToolParameter(
            name = "tab_id",
            type = ParameterType.STRING,
            description = "switch_tab / close_tab 时必填：要切换/关闭的标签 id（从 list_tabs 获取）",
            required = false
        ),
        "fields" to ToolParameter(
            name = "fields",
            type = ParameterType.OBJECT,
            description = "fill_form 时必填：要填写的字段映射，key 为元素 id（data-rcb-id / CSS路径 / 语义描述符），value 为要输入的文本。如 {\"username\": \"admin\", \"password\": \"123\"}",
            required = false
        ),
        "auto_scroll" to ToolParameter(
            name = "auto_scroll",
            type = ParameterType.BOOLEAN,
            description = "snapshot / extract 时可选：true=自动滚动到底部加载更多内容（最多10次，间隔800ms），默认false",
            required = false
        ),
        "structured" to ToolParameter(
            name = "structured",
            type = ParameterType.BOOLEAN,
            description = "extract 时可选：true=自动识别页面类型并输出结构化JSON（article/product/search_results/profile），默认false。为true时忽略selector和mode",
            required = false
        ),
        "idle_ms" to ToolParameter(
            name = "idle_ms",
            type = ParameterType.INTEGER,
            description = "wait_for_network_idle 时可选：判定网络空闲所需的连续无在途请求毫秒数，默认500",
            required = false
        ),
        // ── 第一批反爬增强参数 ──
        "frame_chain" to ToolParameter(
            name = "frame_chain",
            type = ParameterType.STRING,
            description = "list_iframes / iframe_action 时必填：iframe 链式定位符，格式如 \"iframe=main >> iframe=content >> button.submit\"，最多5层",
            required = false
        ),
        "iframe_action" to ToolParameter(
            name = "iframe_action",
            type = ParameterType.STRING,
            description = "iframe_action 时必填：要在 iframe 内执行的动作（click/type/hover）",
            required = false,
            enum = listOf("click", "type", "hover")
        ),
        "call_id" to ToolParameter(
            name = "call_id",
            type = ParameterType.INTEGER,
            description = "replay_api 时必填：要重放的 API 调用 id（从 list_api_calls 获取）",
            required = false
        ),
        "stealth_mode" to ToolParameter(
            name = "stealth_mode",
            type = ParameterType.STRING,
            description = "apply_stealth 时可选：指纹伪装模式 aggressive（全量随机化，默认）/ basic（仅基础覆盖）",
            required = false,
            enum = listOf("aggressive", "basic")
        ),
        "max_pages" to ToolParameter(
            name = "max_pages",
            type = ParameterType.INTEGER,
            description = "paginate_extract 时可选：最大翻页数，默认5，最大10",
            required = false
        ),
        "max_scrolls" to ToolParameter(
            name = "max_scrolls",
            type = ParameterType.INTEGER,
            description = "infinite_scroll_extract 时可选：最大滚动次数，默认15，最大30",
            required = false
        ),
        // ── 第二批自动化增强参数 ──
        "humanize" to ToolParameter(
            name = "humanize",
            type = ParameterType.BOOLEAN,
            description = "click/type/scroll 时可选：是否模拟人类行为（随机延迟、贝塞尔鼠标轨迹），默认 true",
            required = false
        ),
        "mistake_rate" to ToolParameter(
            name = "mistake_rate",
            type = ParameterType.INTEGER,
            description = "human_type 时可选：打错字概率百分比（0-20），默认5",
            required = false
        ),
        "macro_action" to ToolParameter(
            name = "macro_action",
            type = ParameterType.STRING,
            description = "macro_record 时必填：start（开始录制）/ stop（停止录制）",
            required = false,
            enum = listOf("start", "stop")
        ),
        "macro_params" to ToolParameter(
            name = "macro_params",
            type = ParameterType.OBJECT,
            description = "macro_playback 时可选：参数化替换映射（占位符 -> 实际值），如 {\"{{username}}\": \"admin\"}",
            required = false
        ),
        "interval_ms" to ToolParameter(
            name = "interval_ms",
            type = ParameterType.INTEGER,
            description = "set_request_interval 时必填：请求间隔毫秒数（0-10000）",
            required = false
        ),
        "ua_name" to ToolParameter(
            name = "ua_name",
            type = ParameterType.STRING,
            description = "set_user_agent 时必填：预设名称（chrome_desktop/firefox_desktop/safari_macos/chrome_android/safari_ios）或自定义 UA 字符串",
            required = false
        ),
        "steps" to ToolParameter(
            name = "steps",
            type = ParameterType.OBJECT,
            description = "action_chain 时必填：操作链数组，每步为 {\"action\": \"click\", \"element_id\": \"5\"} 格式的对象",
            required = false
        ),
        // ── 第三批会话闭环参数 ──
        "session_id" to ToolParameter(
            name = "session_id",
            type = ParameterType.STRING,
            description = "restore_session 时必填：要恢复的会话 ID（从 list_sessions 获取）",
            required = false
        ),
        "clear_types" to ToolParameter(
            name = "clear_types",
            type = ParameterType.STRING,
            description = "clear_data 时可选：清除范围 all/cookie/cache/storage，默认 all",
            required = false,
            enum = listOf("all", "cookie", "cache", "storage")
        ),
        "incognito" to ToolParameter(
            name = "incognito",
            type = ParameterType.BOOLEAN,
            description = "set_incognito 时必填：true=开启隐身，false=关闭隐身",
            required = false
        ),
        "download_id" to ToolParameter(
            name = "download_id",
            type = ParameterType.STRING,
            description = "download_to_workspace 时必填：要获取文件的下载任务 ID",
            required = false
        ),
        "upload_url" to ToolParameter(
            name = "upload_url",
            type = ParameterType.STRING,
            description = "upload_from_url 时必填：要下载并上传的文件 URL",
            required = false
        ),
        "block_types" to ToolParameter(
            name = "block_types",
            type = ParameterType.STRING,
            description = "block_resource 时必填：要拦截的资源类型（逗号分隔）：image,font,media",
            required = false
        ),
        "log_limit" to ToolParameter(
            name = "log_limit",
            type = ParameterType.INTEGER,
            description = "operation_log 时可选：返回最近 N 条操作日志，默认30",
            required = false
        ),
        // ── 第四批 SPA 专项参数 ──
        "spa_url" to ToolParameter(
            name = "spa_url",
            type = ParameterType.STRING,
            description = "spa_navigate 时必填：SPA 路由目标 URL 或路径",
            required = false
        ),
        "api_url_pattern" to ToolParameter(
            name = "api_url_pattern",
            type = ParameterType.STRING,
            description = "api_paginate 时可选：要分析的 API URL 子串（不填则分析最近的 API 调用）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少 action 参数", "MISSING_ACTION")

        // 中断检查：如果用户已请求中断，立即返回，不执行任何操作
        if (operationController.isInterruptRequested()) {
            browserController.stopLoading()
            operationController.resetInterrupt()
            return ToolResult.Error("用户已中断操作", "INTERRUPTED")
        }

        // 标记操作开始，UI 显示中断按钮
        operationController.operationStarted()

        // 熔断检查：连续失败达到阈值后，除 navigate/view 外的操作直接拒绝，
        // 防止模型在页面无响应时无限重试形成死循环。
        // navigate/view 允许通过，因为它们可能恢复页面状态。
        if (circuitBroken && action !in setOf("navigate", "view", "snapshot", "reload", "back", "forward")) {
            operationController.operationFinished()
            return ToolResult.Error(
                "浏览器操作已熔断：连续 $FAILURE_CIRCUIT_THRESHOLD 次操作失败，页面可能无响应或服务器已断开。" +
                    "请先调用 navigate 或 view 检查页面状态，确认页面恢复后再继续操作。",
                "CIRCUIT_BROKEN"
            )
        }

        // 反爬暂停检查：浏览器交互类动作在反爬暂停期间被拒绝，
        // 指纹/反爬管理类动作不受限制（便于模型查看状态或执行恢复操作）。
        if (action in BROWSER_INTERACT_ACTIONS && antidetectController.isPaused()) {
            val remain = antidetectController.state.value.pauseRemainingSeconds
            operationController.operationFinished()
            return ToolResult.Error(
                "反爬保护已暂停：浏览器操作被暂停 $remain 秒。" +
                    "请等待暂停结束，或调用 antidetect_resume 提前恢复；" +
                    "如需调整策略可调用 antidetect_adjust。",
                "ANTIDETECT_PAUSED"
            )
        }

        return try {
            // 耗时操作前再次检查中断信号
            if (action in setOf("navigate", "click", "type", "fill_form", "submit",
                    "wait_for", "wait_for_change", "wait_for_network_idle", "wait_for_request",
                    "wait_for_render_complete", "paginate_extract", "infinite_scroll_extract",
                    "macro_playback", "action_chain")
                && operationController.isInterruptRequested()) {
                browserController.stopLoading()
                operationController.resetInterrupt()
                return ToolResult.Error("用户已中断操作", "INTERRUPTED")
            }
            // 全局超时保护：防止 WebView 无响应时协程永久挂起
            // （如本地服务器断开后，click/type 等操作在 WebView 层无限等待）
            withTimeout(GLOBAL_TIMEOUT_MS) {
                var result = when (action) {
                    "navigate" -> doNavigate(args)
                    "view" -> doView(args)
                    "snapshot" -> doSnapshot(args)
                    "page_text" -> doPageText()
                    "extract" -> doExtract(args)
                    "click" -> doClick(args)
                    "type" -> doType(args)
                    "fill_form" -> doFillForm(args)
                    "select_option" -> doSelect(args)
                    "submit" -> doSubmit(args)
                    "scroll" -> doScroll(args)
                    "hover" -> doHover(args)
                    "drag" -> doDrag(args)
                    "press_key" -> doPressKey(args)
                    "upload_file" -> doUploadFile(args)
                    "back" -> doNavigation("back", browserController.back())
                    "forward" -> doNavigation("forward", browserController.forward())
                    "reload" -> doNavigation("reload", browserController.reloadPage())
                    "screenshot" -> doScreenshot(args)
                    "evaluate" -> doEvaluate(args)
                    "wait_for" -> doWaitFor(args)
                    "wait_for_change" -> doWaitForChange(args)
                    "wait_for_network_idle" -> doWaitForNetworkIdle(args)
                    "history" -> doHistory()
                    "get_attribute" -> doGetAttribute(args)
                    "handle_dialog" -> doHandleDialog(args)
                    "login" -> doLogin()
                    "takeover" -> doTakeover(args)
                    "new_tab" -> doNewTab(args)
                    "switch_tab" -> doSwitchTab(args)
                    "close_tab" -> doCloseTab(args)
                    "list_tabs" -> doListTabs()
                    "downloads" -> doListDownloads()
                    "network" -> doNetwork(args)
                    "network_get" -> doNetworkGet(args)
                    "wait_for_request" -> doWaitForRequest(args)
                    "snapshot_shadow" -> doSnapshotShadow(args)
                    "list_iframes" -> doListIframes()
                    "iframe_action" -> doIframeAction(args)
                    "intercept_api" -> doInterceptApi()
                    "list_api_calls" -> doListApiCalls(args)
                    "replay_api" -> doReplayApi(args)
                    "wait_for_render_complete" -> doWaitForRenderComplete(args)
                    "detect_rendering_type" -> doDetectRenderingType()
                    "apply_stealth" -> doApplyStealth(args)
                    "deobfuscate" -> doDeobfuscate(args)
                    "extract_clean_text" -> doExtractCleanText()
                    "paginate_extract" -> doPaginateExtract(args)
                    "infinite_scroll_extract" -> doInfiniteScrollExtract(args)
                    "safe_click" -> doSafeClick(args)
                    "human_type" -> doHumanType(args)
                    "macro_record" -> doMacroRecord(args)
                    "macro_playback" -> doMacroPlayback(args)
                    "macro_list" -> doMacroList()
                    "macro_clear" -> doMacroClear()
                    "set_request_interval" -> doSetRequestInterval(args)
                    "set_user_agent" -> doSetUserAgent(args)
                    "list_user_agents" -> doListUserAgents()
                    "action_chain" -> doActionChain(args)
                    "save_session" -> doSaveSession()
                    "restore_session" -> doRestoreSession(args)
                    "list_sessions" -> doListSessions()
                    "clear_data" -> doClearData(args)
                    "set_incognito" -> doSetIncognito(args)
                    "wait_for_download" -> doWaitForDownload(args)
                    "download_to_workspace" -> doDownloadToWorkspace(args)
                    "upload_from_url" -> doUploadFromUrl(args)
                    "detect_captcha" -> doDetectCaptcha()
                    "permission_audit" -> doPermissionAudit()
                    "block_resource" -> doBlockResource(args)
                    "operation_log" -> doOperationLog(args)
                    "screenshot_full_page" -> doScreenshotFullPage()
                    "detect_framework" -> doDetectFramework()
                    "extract_ssr_data" -> doExtractSsrData()
                    "extract_framework_state" -> doExtractFrameworkState()
                    "detect_virtual_list" -> doDetectVirtualList()
                    "spa_navigate" -> doSpaNavigate(args)
                    "api_paginate" -> doApiPaginate(args)
                    // 指纹管理
                    "fingerprint_list" -> doFingerprintList(args)
                    "fingerprint_get" -> doFingerprintGet(args)
                    "fingerprint_set" -> doFingerprintSet(args)
                    "fingerprint_create" -> doFingerprintCreate(args)
                    "fingerprint_delete" -> doFingerprintDelete(args)
                    "fingerprint_validate" -> doFingerprintValidate(args)
                    "fingerprint_test" -> doFingerprintTest()
                    // 反爬控制
                    "antidetect_status" -> doAntidetectStatus()
                    "antidetect_signals" -> doAntidetectSignals(args)
                    "antidetect_adjust" -> doAntidetectAdjust(args)
                    "antidetect_pause" -> doAntidetectPause(args)
                    "antidetect_resume" -> doAntidetectResume()
                    // 综合状态
                    "browser_status" -> doBrowserStatus()
                    else -> ToolResult.Error("未知动作: $action", "UNKNOWN_ACTION")
                }
                // 操作成功：重置连续失败计数和熔断状态
                // （navigate/view/snapshot 等读操作成功也视为页面恢复）
                if (result is ToolResult.Success || (result is ToolResult.Error && result.code == "UNKNOWN_ACTION")) {
                    consecutiveFailures = 0
                    circuitBroken = false
                } else {
                    // 操作返回业务错误（如元素找不到），计入连续失败
                    recordFailure(action)
                }
                // 反爬信号分析：对导航类动作执行后检测反爬信号
                // （仅对加载页面的动作做检测，管理类动作不触发）
                if (action in setOf("navigate", "view", "reload", "back", "forward", "spa_navigate")) {
                    val success = result is ToolResult.Success
                    val currentUrl = browserController.uiState.value.currentUrl
                    val signals = antidetectController.analyzeResult(
                        url = currentUrl,
                        success = success
                    )
                    // 检测到高风险信号时，在返回结果中提示模型考虑调整策略
                    if (signals.any { it.severity in listOf("high", "critical") }) {
                        val highSignals = signals.filter { it.severity in listOf("high", "critical") }
                        val noteSuffix = "⚠ 检测到高风险反爬信号：${highSignals.joinToString("、") { it.description }}。建议调用 antidetect_adjust 执行自适应调整。"
                        result = appendNoteToResult(result, noteSuffix)
                    }
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            FileLogger.e(TAG, "browser.$action 超时（${GLOBAL_TIMEOUT_MS}ms）", e)
            recordFailure(action)
            ToolResult.Error(
                "浏览器操作超时（${GLOBAL_TIMEOUT_MS / 1000}秒无响应）：页面可能已卡死或服务器已断开。" +
                    "建议调用 navigate 重新加载页面，或检查目标服务是否仍在运行。",
                "TIMEOUT"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "browser.$action 失败", e)
            recordFailure(action)
            // 错误分类：根据异常消息判断具体原因，帮助模型做出正确决策而非盲目重试
            val classified = classifyBrowserError(e, action)
            ToolResult.Error(classified.first, classified.second)
        } finally {
            operationController.operationFinished()
        }
    }

    /**
     * 记录一次操作失败，达到阈值后触发熔断。
     */
    private fun recordFailure(action: String) {
        consecutiveFailures++
        if (consecutiveFailures >= FAILURE_CIRCUIT_THRESHOLD) {
            circuitBroken = true
            FileLogger.w(TAG, "浏览器操作熔断触发：连续 $consecutiveFailures 次失败（最近动作: $action）")
        }
    }

    /**
     * 浏览器错误分类：根据异常类型和消息判断具体原因，
     * 返回 (用户可读消息, 错误码)。帮助模型区分"服务器断开"和"元素找不到"，
     * 避免盲目重试形成死循环。
     */
    private fun classifyBrowserError(e: Exception, action: String): Pair<String, String> {
        val msg = e.message ?: ""
        val lowerMsg = msg.lowercase()

        return when {
            // 连接被拒绝：本地服务器或目标服务已停止
            lowerMsg.contains("connection refused") || lowerMsg.contains("econnrefused") ->
                "连接被拒绝：目标服务器可能已停止运行（如本地开发服务已断开）。" +
                    "请确认服务是否仍在运行，或调用 navigate 检查页面状态。" to "CONNECTION_REFUSED"

            // DNS 解析失败
            lowerMsg.contains("unable to resolve host") || lowerMsg.contains("nodename nor servname") ->
                "DNS 解析失败：无法解析目标主机名。请检查 URL 是否正确，或网络连接是否正常。" to "DNS_FAILURE"

            // 连接超时
            lowerMsg.contains("connection timed out") || lowerMsg.contains("etimedout") ->
                "连接超时：目标服务器响应过慢或不可达。建议稍后重试，或检查网络/代理设置。" to "CONNECTION_TIMEOUT"

            // 页面无响应 / WebView 相关错误
            lowerMsg.contains("webview") || lowerMsg.contains("page not responding") ||
                lowerMsg.contains("javascript interface") || lowerMsg.contains("evaluatejavascript") ->
                "页面无响应：WebView 可能已卡死。建议调用 navigate 或 reload 重新加载页面。" to "PAGE_UNRESPONSIVE"

            // 元素找不到（业务层面的错误，通常可恢复）
            lowerMsg.contains("not found") || lowerMsg.contains("no such element") ||
                lowerMsg.contains("unable to locate") ->
                "元素未找到：页面中不存在指定元素。可能是页面尚未加载完成、元素已变化，或定位描述不准确。" +
                    "建议先调用 snapshot 查看当前页面结构，再调整元素定位。" to "ELEMENT_NOT_FOUND"

            // 通用错误
            else ->
                "浏览器操作失败: $msg" to "UNKNOWN"
        }
    }

    // ─────────────────────────── 动作实现 ───────────────────────────

    private suspend fun doNavigate(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("navigate 需要 url 参数", "MISSING_URL")
        browserController.validateUrl(url)?.let {
            return envelope("navigate", ok = false, error = it, recoverable = true, summary = "导航被拦截：$it")
        }
        val snap = browserController.navigate(url)
        return envelope(
            action = "navigate",
            ok = true,
            changed = true,
            summary = "已打开 ${snap.url.ifBlank { url }}（${snap.title.ifBlank { "(无标题)" }}）",
            snapshot = snapshotToJson(snap, navLevelOf(args))
        )
    }

    private suspend fun doSnapshot(args: Map<String, JsonElement>): ToolResult {
        val level = snapshotLevelOf(args)
        val autoScroll = args["auto_scroll"]?.jsonPrimitive?.booleanOrNull ?: false
        val snap = browserController.snapshot(level, autoScroll)
        return envelope("snapshot", ok = true, summary = snapshotSummary(snap), snapshot = snapshotToJson(snap, level))
    }

    /** 按需取正文（R2.1）：模型需要页面文本时单独取，不随快照默认返回。 */
    private suspend fun doPageText(): ToolResult {
        val text = browserController.pageText().take(MAX_TEXT)
        return envelope("page_text", ok = true, summary = "页面正文共 ${text.length} 字（超过 ${MAX_TEXT} 字已截断）", note = text)
    }

    /**
     * 智能页面查看：可选导航到 url，等待页面稳定，识别页面类型并按需返回对应级别快照。
     * article -> FULL（含正文）；search_results/product -> STANDARD；其余 -> STANDARD。
     */
    private suspend fun doView(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        if (url != null) {
            browserController.validateUrl(url)?.let {
                return envelope("view", ok = false, error = it, recoverable = true, summary = "导航被拦截：$it")
            }
            browserController.navigate(url)
        }
        runCatching { browserController.waitForPageReady(3000) }
        val structured = browserController.extract(null, "text", autoScroll = false, structured = true)
        val pageType = runCatching {
            json.parseToJsonElement(structured).jsonObject["page_type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        }.getOrNull() ?: "unknown"
        val level = when (pageType) {
            "article" -> SnapshotLevel.FULL
            "search_results", "product" -> SnapshotLevel.STANDARD
            else -> SnapshotLevel.STANDARD
        }
        val snap = browserController.snapshot(level)
        return envelope(
            action = "view",
            ok = true,
            changed = url != null,
            summary = "页面类型: $pageType，已获取${level.name.lowercase()}级快照（${snap.elements.size}个元素）",
            snapshot = snapshotToJson(snap, level),
            note = structured.take(4000)
        )
    }

    private suspend fun doExtract(args: Map<String, JsonElement>): ToolResult {
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull ?: "text"
        val autoScroll = args["auto_scroll"]?.jsonPrimitive?.booleanOrNull ?: false
        val structured = args["structured"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = browserController.extract(selector, mode, autoScroll, structured)
        val note = result.take(MAX_TEXT)
        val summary = if (structured) {
            val pageType = runCatching {
                json.parseToJsonElement(result).jsonObject["page_type"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            "结构化提取完成（页面类型: ${pageType ?: "unknown"}）"
        } else {
            "已按 mode=$mode 抽取数据"
        }
        return envelope("extract", ok = true, summary = summary, note = note)
    }

    // ─────────────────── 写操作（统一 envelope + 增量 delta） ───────────────────

    /**
     * 写操作统一入口（R2.3 写操作自动验证）：执行动作 → 检测"元素未找到"类失败 →
     * 取 controller 计算好的增量 delta 一并返回，模型无需自行轮询。
     */
    private suspend fun writeEnvelope(action: String, args: Map<String, JsonElement>, run: suspend () -> BrowserPageSnapshot): ToolResult {
        val snap = run()
        val notFound = snap.pageText.takeIf { it.startsWith("元素 ") && it.contains("未找到") }
        if (notFound != null) {
            return envelope(
                action = action,
                ok = false,
                error = notFound,
                errorCode = "ELEMENT_NOT_FOUND",
                recoverable = true,
                summary = "$action 失败：$notFound",
                note = "元素可能因页面刷新/重渲染失效。建议：1) 重新 view(snapshot_level=standard) 获取最新元素标识；2) 换用 CSS 绝对路径或语义描述符（role=… name=… index=…）替代 data-rcb-id；3) 不要重复同样的定位方式"
            )
        }
        val delta = browserController.lastDelta()
        return envelope(
            action = action,
            ok = true,
            changed = delta != null &&
                (delta.added.isNotEmpty() || delta.changed.isNotEmpty() || delta.removed.isNotEmpty() || delta.textNote.isNotBlank()),
            summary = delta?.let { writeDeltaSummary(action, it) } ?: "$action 完成",
            snapshot = snapshotToJson(snap, snapshotLevelOf(args)),
            delta = delta
        )
    }

    private suspend fun doClick(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("click 需要 element_id", "MISSING_ELEMENT_ID")
        return writeEnvelope("click", args) { browserController.click(id) }
    }

    private suspend fun doType(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("type 需要 element_id", "MISSING_ELEMENT_ID")
        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("type 需要 text", "MISSING_TEXT")
        return writeEnvelope("type", args) { browserController.type(id, text) }
    }

    /** 批量表单填写：fields 为 元素标识 -> 文本 的对象映射，一次性填写多个字段。 */
    private suspend fun doFillForm(args: Map<String, JsonElement>): ToolResult {
        val fieldsObj = args["fields"]?.jsonObject
            ?: return ToolResult.Error("fill_form 需要 fields 参数（对象映射：元素标识->文本）", "MISSING_FIELDS")
        val fields: Map<String, String> = fieldsObj.mapValues { (_, v) ->
            v.jsonPrimitive.contentOrNull ?: ""
        }
        if (fields.isEmpty()) {
            return envelope("fill_form", ok = false, recoverable = true, summary = "fill_form 失败：fields 为空", error = "fields 不能为空")
        }
        return writeEnvelope("fill_form", args) { browserController.fillForm(fields) }
    }

    private suspend fun doSelect(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("select_option 需要 element_id", "MISSING_ELEMENT_ID")
        val value = args["value"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("select_option 需要 value", "MISSING_VALUE")
        return writeEnvelope("select_option", args) { browserController.selectOption(id, value) }
    }

    private suspend fun doSubmit(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull
        return writeEnvelope("submit", args) { browserController.submit(id) }
    }

    private suspend fun doScroll(args: Map<String, JsonElement>): ToolResult {
        val direction = args["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
        return writeEnvelope("scroll", args) { browserController.scroll(direction) }
    }

    private suspend fun doHover(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("hover 需要 element_id", "MISSING_ELEMENT_ID")
        return writeEnvelope("hover", args) { browserController.hover(id) }
    }

    private suspend fun doDrag(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("drag 需要 element_id", "MISSING_ELEMENT_ID")
        val targetId = args["target_element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("drag 需要 target_element_id", "MISSING_TARGET_ELEMENT_ID")
        return writeEnvelope("drag", args) { browserController.drag(id, targetId) }
    }

    private suspend fun doPressKey(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("press_key 需要 element_id", "MISSING_ELEMENT_ID")
        val key = args["key"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("press_key 需要 key", "MISSING_KEY")
        return writeEnvelope("press_key", args) { browserController.pressKey(id, key) }
    }

    private suspend fun doUploadFile(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("upload_file 需要 element_id", "MISSING_ELEMENT_ID")
        val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("upload_file 需要 file_path", "MISSING_FILE_PATH")
        val hostFile = runCatching { pathMapper.toHostFile(filePath) }.getOrNull()
            ?: java.io.File(filePath)
        val error = browserController.uploadFile(id, hostFile)
        return if (error == null) {
            val after = browserController.snapshot(snapshotLevelOf(args))
            envelope(
                action = "upload_file",
                ok = true,
                summary = "已上传文件 ${pathMapper.toContainerPath(hostFile.absolutePath)} 到元素 #$id",
                snapshot = snapshotToJson(after, snapshotLevelOf(args))
            )
        } else {
            envelope("upload_file", ok = false, error = error, recoverable = true, summary = "上传失败：$error")
        }
    }

    /** 导航类动作统一处理（back/forward/reload）：页面完全变化，默认给 standard 快照。 */
    private suspend fun doNavigation(action: String, snap: BrowserPageSnapshot): ToolResult {
        val verb = when (action) {
            "back" -> "已后退"
            "forward" -> "已前进"
            else -> "已刷新"
        }
        return envelope(
            action = action,
            ok = true,
            changed = true,
            summary = "${verb}到 ${snap.title.ifBlank { "(无标题)" }}（${snap.url}）",
            snapshot = snapshotToJson(snap, navLevelOf(emptyMap()))
        )
    }

    private suspend fun doScreenshot(args: Map<String, JsonElement>): ToolResult {
        val elementId = args["element_id"]?.jsonPrimitive?.contentOrNull
        val dataUrl = browserController.screenshot(elementId)
            ?: return envelope(
                "screenshot", ok = false, recoverable = true,
                summary = "截图失败", error = "截图失败：浏览器页面尚未渲染（请先在浏览器页打开页面）"
            )
        return envelope(
            action = "screenshot",
            ok = true,
            summary = "已截取${if (elementId != null) "元素 #$elementId" else "页面"}截图",
            note = "data:image/png;base64，多模态模型可直接查看",
            extra = JsonObject(mapOf("image_data_url" to JsonPrimitive(dataUrl)))
        )
    }

    private suspend fun doEvaluate(args: Map<String, JsonElement>): ToolResult {
        val js = args["js"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("evaluate 需要 js 参数", "MISSING_JS")
        val result = browserController.evaluate(js)
        return envelope("evaluate", ok = true, summary = "JS 执行完成", note = result.take(MAX_TEXT))
    }

    private suspend fun doWaitFor(args: Map<String, JsonElement>): ToolResult {
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 10_000L
        val snap = browserController.waitFor(selector, timeout)
        return envelope(
            action = "wait_for",
            ok = true,
            changed = true,
            summary = "等待完成：${if (selector.isNullOrBlank()) "页面已就绪" else "元素 $selector 已出现"}",
            snapshot = snapshotToJson(snap, snapshotLevelOf(args))
        )
    }

    /** 事件驱动等待页面变化（R2.4）：替代轮询 snapshot，页面 DOM/网络变化即返回摘要。 */
    private suspend fun doWaitForChange(args: Map<String, JsonElement>): ToolResult {
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 15_000L
        val result = browserController.waitForChange(timeout)
        return if (result.changed) {
            envelope(
                action = "wait_for_change",
                ok = true,
                changed = true,
                summary = "页面发生变化：${result.summary.take(200)}"
            )
        } else {
            envelope(
                action = "wait_for_change",
                ok = true,
                changed = false,
                summary = "等待 ${timeout}ms 超时，页面无变化",
                note = "可调用 snapshot 查看当前状态，或继续其他操作"
            )
        }
    }

    /** 等待网络空闲：连续 idleMs 无在途请求即返回，超时则报告。 */
    private suspend fun doWaitForNetworkIdle(args: Map<String, JsonElement>): ToolResult {
        val timeoutMs = args["timeout_ms"]?.jsonPrimitive?.intOrNull?.toLong() ?: 10000L
        val idleMs = args["idle_ms"]?.jsonPrimitive?.intOrNull?.toLong() ?: 500L
        val ok = browserController.waitForNetworkIdle(timeoutMs, idleMs)
        return envelope(
            action = "wait_for_network_idle",
            ok = ok,
            summary = if (ok) "网络已空闲（连续${idleMs}ms无在途请求）" else "等待网络空闲超时（${timeoutMs}ms）",
            recoverable = true
        )
    }

    /** 动作历史查询（R2.3）：返回最近 30 条操作 + 结果摘要，避免重复操作。 */
    private suspend fun doHistory(): ToolResult {
        val history = browserController.actionHistory()
        return envelope(
            action = "history",
            ok = true,
            summary = "最近 ${history.size} 条动作",
            note = history.joinToString("\n")
        )
    }

    private suspend fun doGetAttribute(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("get_attribute 需要 element_id", "MISSING_ELEMENT_ID")
        val attr = args["attribute"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("get_attribute 需要 attribute", "MISSING_ATTRIBUTE")
        val value = browserController.getAttribute(id, attr)
        return envelope(
            action = "get_attribute",
            ok = true,
            summary = "属性 $attr 读取完成",
            note = "$id.$attr = ${value ?: "(空)"}"
        )
    }

    private suspend fun doHandleDialog(args: Map<String, JsonElement>): ToolResult {
        val accept = runCatching { args["accept"]?.jsonPrimitive?.contentOrNull?.toBoolean() }.getOrNull() ?: true
        val handled = browserController.handleDialog(accept)
        return envelope(
            action = "handle_dialog",
            ok = handled,
            summary = if (handled) "对话框已${if (accept) "确认" else "取消"}" else "当前没有待处理的对话框"
        )
    }

    /** 请求用户接管当前页面（验证码/支付/二次认证等），用户完成后返回最新快照。 */
    private suspend fun doTakeover(args: Map<String, JsonElement>): ToolResult {
        val message = args["message"]?.jsonPrimitive?.contentOrNull
            ?: "请用户亲自完成当前页面上的操作（如验证码、支付、二次认证等）。"
        val snap = browserController.snapshot(SnapshotLevel.SUMMARY)
        val host = runCatching { URI(snap.url).host }.getOrNull().orEmpty()
        val title = if (host.isNotBlank()) "需要你接管 $host" else "需要你接管浏览器"
        val answer = takeoverManager.awaitTakeover(title, message)
        return if (answer.confirmed) {
            val after = browserController.snapshot(SnapshotLevel.SUMMARY)
            envelope(
                action = "takeover",
                ok = true,
                changed = true,
                summary = "用户已完成接管，页面状态已更新",
                snapshot = snapshotToJson(after, navLevelOf(emptyMap()))
            )
        } else {
            envelope("takeover", ok = false, summary = "用户未接管/取消了接管请求。")
        }
    }

    /** 登录当前站点：从凭据库取账号密码自动代填；无则请求用户输入。 */
    private suspend fun doLogin(): ToolResult {
        val snap = browserController.snapshot(SnapshotLevel.SUMMARY)
        if (!snap.hasLoginForm) {
            return envelope("login", ok = false, summary = "无需登录", error = "当前页面未检测到登录表单（无密码输入框）")
        }
        val host = extractHost(snap.url) ?: "unknown"
        var cred = credentialStore.find(host)
        if (cred == null) {
            FileLogger.i(TAG, "凭据库无 $host 凭据，请求用户输入")
            val answer = loginPromptManager.awaitCredentials(host)
            if (answer.cancelled || answer.username.isNullOrBlank() || answer.password.isNullOrBlank()) {
                return envelope(
                    "login", ok = false, summary = "未获得凭据",
                    note = "用户未提供 $host 的登录凭据。请提示用户在浏览器页手动登录，或重新调用 login 让用户输入。"
                )
            }
            credentialStore.save(host, answer.username, answer.password)
            cred = BrowserCredential(host = host, username = answer.username, password = answer.password)
        }

        // 定位用户名/密码框并代填 + 提交
        var usernameId: String? = null
        var passwordId: String? = null
        for (e in snap.elements) {
            if (e.tag == "input" && e.type == "password" && passwordId == null) passwordId = e.id
            else if (e.tag == "input" && (e.type == "text" || e.type == "email" || e.type == "tel" || (e.type.isBlank() && e.name.isNotBlank())) && usernameId == null) usernameId = e.id
        }
        if (passwordId == null) {
            return envelope("login", ok = false, recoverable = true, summary = "登录失败", error = "未找到密码输入框，无法自动代填")
        }
        if (!usernameId.isNullOrBlank()) browserController.type(usernameId, cred.username)
        browserController.type(passwordId, cred.password)
        // 尝试提交（优先提交登录表单）
        val after = browserController.submit(usernameId)
        return envelope(
            action = "login",
            ok = true,
            summary = "已用 ${cred.username} 自动代填并提交登录表单",
            note = "若登录成功，后续请求自动携带登录态。",
            snapshot = snapshotToJson(after, navLevelOf(emptyMap()))
        )
    }

    // ─────────────────────────── 多标签页 ───────────────────────────

    private suspend fun doNewTab(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        url?.let { browserController.validateUrl(it)?.let { e -> return envelope("new_tab", ok = false, error = e, recoverable = true, summary = "已拦截：$e") } }
        val info = browserController.newTab(url)
        return envelope(
            action = "new_tab",
            ok = true,
            changed = true,
            summary = "已新建标签${if (url != null) "并打开 $url" else ""}",
            extra = JsonObject(
                mapOf(
                    "tab_id" to JsonPrimitive(info.id),
                    "title" to JsonPrimitive(info.title),
                    "url" to JsonPrimitive(info.url),
                    "tabs" to tabsToJson()
                )
            )
        )
    }

    private suspend fun doSwitchTab(args: Map<String, JsonElement>): ToolResult {
        val id = args["tab_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("switch_tab 需要 tab_id", "MISSING_TAB_ID")
        val err = browserController.switchTab(id)
        return if (err != null) {
            envelope("switch_tab", ok = false, error = err, recoverable = true, summary = "切换失败：$err")
        } else {
            val snap = browserController.snapshot(navLevelOf(args))
            envelope(
                action = "switch_tab",
                ok = true,
                changed = true,
                summary = "已切换到标签 ${snap.title.ifBlank { id }}",
                snapshot = snapshotToJson(snap, navLevelOf(args))
            )
        }
    }

    private suspend fun doCloseTab(args: Map<String, JsonElement>): ToolResult {
        val id = args["tab_id"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("close_tab 需要 tab_id", "MISSING_TAB_ID")
        val err = browserController.closeTab(id)
        return if (err != null) {
            envelope("close_tab", ok = false, error = err, recoverable = true, summary = "关闭失败：$err")
        } else {
            val snap = browserController.snapshot(navLevelOf(args))
            envelope(
                action = "close_tab",
                ok = true,
                changed = true,
                summary = "已关闭标签 $id",
                snapshot = snapshotToJson(snap, navLevelOf(args)),
                extra = JsonObject(mapOf("tabs" to tabsToJson()))
            )
        }
    }

    private fun doListTabs(): ToolResult = envelope(
        action = "list_tabs",
        ok = true,
        summary = "当前共 ${browserController.listTabs().size} 个标签",
        extra = JsonObject(mapOf("tabs" to tabsToJson()))
    )

    private fun doListDownloads(): ToolResult {
        val downloads = browserController.listDownloads()
        return envelope(
            action = "downloads",
            ok = true,
            summary = "最近 ${downloads.size} 个下载任务",
            extra = JsonObject(
                mapOf(
                    "count" to JsonPrimitive(downloads.size),
                    "downloads" to JsonArray(downloads.map { downloadToJson(it) })
                )
            )
        )
    }

    // ─────────────────────────── 动态数据捕获：网络请求查询 ───────────────────────────

    private suspend fun doNetwork(args: Map<String, JsonElement>): ToolResult {
        val limit = runCatching { args["limit"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 20
        val records = browserController.listNetwork(limit)
        val total = browserController.networkTotalCount()
        val pending = browserController.networkPendingCount()
        return envelope(
            action = "network",
            ok = true,
            summary = "已按时间倒序返回最近 ${records.size} 条异步数据请求",
            note = "URL/响应均脱敏，敏感参数置 ***。",
            extra = JsonObject(
                mapOf(
                    "count" to JsonPrimitive(records.size),
                    "total" to JsonPrimitive(total),
                    "pending" to JsonPrimitive(pending),
                    "requests" to JsonArray(records.map { networkToJson(it) })
                )
            )
        )
    }

    private suspend fun doNetworkGet(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        val id = runCatching { args["network_id"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull()
        val rec = browserController.getNetwork(url, id)
        return if (rec != null) {
            envelope(
                action = "network_get",
                ok = true,
                summary = "已找到请求记录 #${rec.id}",
                extra = JsonObject(mapOf("request" to networkToJson(rec)))
            )
        } else {
            envelope("network_get", ok = false, summary = "未找到匹配的网络请求记录")
        }
    }

    private suspend fun doWaitForRequest(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("wait_for_request 需要 url 参数（要等待的请求 URL 子串）", "MISSING_URL")
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 15_000L
        val rec = browserController.waitForRequest(url, timeout)
        return if (rec != null) {
            envelope(
                action = "wait_for_request",
                ok = true,
                changed = true,
                summary = "已捕获到匹配 $url 的请求",
                extra = JsonObject(mapOf("request" to networkToJson(rec)))
            )
        } else {
            val pending = browserController.networkPendingCount()
            envelope(
                action = "wait_for_request",
                ok = false,
                summary = "等待超时，未捕获到匹配 $url 的请求",
                note = "当前在途业务请求 $pending 个。"
            )
        }
    }

    // ─────────────────────────── 第一批：反爬虫核心动作 ───────────────────────────

    /** Shadow DOM 穿透快照：递归遍历 open Shadow Root，元素纳入统一编号。 */
    private suspend fun doSnapshotShadow(args: Map<String, JsonElement>): ToolResult {
        val level = snapshotLevelOf(args)
        val snap = browserController.snapshotShadow(level)
        return envelope(
            action = "snapshot_shadow",
            ok = true,
            summary = "Shadow DOM 穿透快照完成：${snap.elements.size}个元素（含shadow内元素）",
            snapshot = snapshotToJson(snap, level)
        )
    }

    /** 列出所有 iframe（最多5层），报告同源可访问性。 */
    private suspend fun doListIframes(): ToolResult {
        val raw = browserController.listIframes()
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val count = parsed?.get("count")?.let { if (it is JsonPrimitive) it.content.toIntOrNull() ?: 0 } ?: 0
        return envelope(
            action = "list_iframes",
            ok = true,
            summary = "检测到 $count 个 iframe（最多5层递归）",
            extra = parsed ?: JsonObject(mapOf("raw" to JsonPrimitive(raw)))
        )
    }

    /** 在 iframe 链内执行操作（click/type/hover）。 */
    private suspend fun doIframeAction(args: Map<String, JsonElement>): ToolResult {
        val chain = args["frame_chain"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("iframe_action 需要 frame_chain 参数", "MISSING_FRAME_CHAIN")
        val action = args["iframe_action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("iframe_action 需要 iframe_action 参数（click/type/hover）", "MISSING_IFRAME_ACTION")
        val elementId = args["element_id"]?.jsonPrimitive?.contentOrNull ?: ""
        val text = args["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val result = browserController.actionInIframe(chain, action, elementId, text)
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val ok: Boolean = runCatching { (parsed?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        return envelope(
            action = "iframe_action",
            ok = ok,
            changed = ok,
            summary = if (ok) "iframe 内 $action 操作成功" else "iframe 内操作失败",
            note = result.take(MAX_TEXT),
            recoverable = !ok
        )
    }

    /** 启用增强版 API 拦截（全量请求/响应体捕获）。 */
    private suspend fun doInterceptApi(): ToolResult {
        browserController.enableApiInterception()
        return envelope(
            action = "intercept_api",
            ok = true,
            summary = "已启用增强版 API 拦截：全量捕获 XHR/fetch 请求体+响应体（单条最大5MB，仅存内存）",
            note = "后续网络请求将被完整记录，可用 list_api_calls 查看、replay_api 重放。"
        )
    }

    /** 列出已捕获的 API 调用（含完整请求/响应体）。 */
    private suspend fun doListApiCalls(args: Map<String, JsonElement>): ToolResult {
        val limit = runCatching { args["limit"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 20
        val raw = browserController.listApiCalls(limit)
        return envelope(
            action = "list_api_calls",
            ok = true,
            summary = "已返回最近 $limit 条 API 调用记录",
            note = raw.take(MAX_TEXT)
        )
    }

    /** 重放指定 id 的 API 请求。 */
    private suspend fun doReplayApi(args: Map<String, JsonElement>): ToolResult {
        val callId = runCatching { args["call_id"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull()
            ?: return ToolResult.Error("replay_api 需要 call_id 参数", "MISSING_CALL_ID")
        val result = browserController.replayApi(callId)
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val ok: Boolean = runCatching { (parsed?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        return envelope(
            action = "replay_api",
            ok = ok,
            summary = if (ok) "API #$callId 重放成功" else "API #$callId 重放失败",
            note = result.take(MAX_TEXT),
            recoverable = !ok
        )
    }

    /** 等待渲染完成：DOM Mutation + 网络空闲 + CSS 动画三重检测。 */
    private suspend fun doWaitForRenderComplete(args: Map<String, JsonElement>): ToolResult {
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 10_000L
        val ok = browserController.waitForRenderComplete(timeout)
        return envelope(
            action = "wait_for_render_complete",
            ok = true,
            summary = if (ok) "渲染已完成（DOM稳定+网络空闲+CSS动画空闲）" else "等待渲染完成超时（${timeout}ms）",
            recoverable = !ok
        )
    }

    /** 自动识别页面渲染类型（SSR/CSR/Next.js/Nuxt 等）。 */
    private suspend fun doDetectRenderingType(): ToolResult {
        val result = browserController.detectRenderingType()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val type = parsed?.get("type")?.let { if (it is JsonPrimitive) it.content } ?: "unknown"
        return envelope(
            action = "detect_rendering_type",
            ok = true,
            summary = "页面渲染类型: $type",
            note = result.take(MAX_TEXT)
        )
    }

    /** 指纹伪装：aggressive 全量随机化 / basic 基础覆盖。 */
    private suspend fun doApplyStealth(args: Map<String, JsonElement>): ToolResult {
        val mode = args["stealth_mode"]?.jsonPrimitive?.contentOrNull ?: "aggressive"
        val result = browserController.applyStealth(mode)
        return envelope(
            action = "apply_stealth",
            ok = true,
            summary = "已应用 $mode 模式指纹伪装",
            note = result.take(MAX_TEXT)
        )
    }

    /** 内容清洗：检测 CSS 混淆、零宽字符、字体反爬并清理。 */
    private suspend fun doDeobfuscate(args: Map<String, JsonElement>): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull
        val result = browserController.deobfuscate(text)
        return envelope(
            action = "deobfuscate",
            ok = true,
            summary = "内容清洗完成",
            note = result.take(MAX_TEXT)
        )
    }

    /** 提取清洗后的页面文本（自动移除隐藏元素、清理零宽字符）。 */
    private suspend fun doExtractCleanText(): ToolResult {
        val result = browserController.extractCleanText()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val ok: Boolean = runCatching { (parsed?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        val text: String = runCatching { (parsed?.get("text") as? JsonPrimitive)?.content }.getOrNull() ?: ""
        return envelope(
            action = "extract_clean_text",
            ok = ok,
            summary = "已提取清洗后页面文本（${text.length}字）",
            note = text.take(MAX_TEXT)
        )
    }

    /** 分页提取：自动翻页收集数据。 */
    private suspend fun doPaginateExtract(args: Map<String, JsonElement>): ToolResult {
        val maxPages = runCatching { args["max_pages"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 5
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val result = browserController.paginateExtract(maxPages, selector)
        return envelope(
            action = "paginate_extract",
            ok = true,
            summary = "分页提取完成（最多${maxPages}页）",
            note = result.take(MAX_TEXT)
        )
    }

    /** 无限滚动提取：自动滚动收集所有加载内容。 */
    private suspend fun doInfiniteScrollExtract(args: Map<String, JsonElement>): ToolResult {
        val maxScrolls = runCatching { args["max_scrolls"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 15
        val selector = args["selector"]?.jsonPrimitive?.contentOrNull
        val result = browserController.infiniteScrollExtract(maxScrolls, selector)
        return envelope(
            action = "infinite_scroll_extract",
            ok = true,
            summary = "无限滚动提取完成（最多${maxScrolls}次滚动）",
            note = result.take(MAX_TEXT)
        )
    }

    // ─────────────────────────── 第二批：自动化与健壮性动作 ───────────────────────────

    /** safe_click：前置检查 + 执行 + 后置验证。 */
    private suspend fun doSafeClick(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("safe_click 需要 element_id", "MISSING_ELEMENT_ID")
        val result = browserController.safeClick(id)
        return if (result.ok) {
            envelope(
                action = "safe_click",
                ok = true,
                changed = result.changed,
                summary = "safe_click 完成${if (result.changed) "（页面已发生变化）" else "（页面无明显变化，可能为静态元素）"}",
                note = "前置检查通过，后置验证${if (result.changed) "检测到页面变化" else "未检测到明显变化"}"
            )
        } else {
            envelope(
                action = "safe_click",
                ok = false,
                recoverable = true,
                summary = "safe_click 失败：${result.reason}",
                error = result.detail,
                note = "建议：1) 重新 snapshot 获取最新元素标识 2) 调用 screenshot 查看页面状态 3) 尝试使用 CSS 路径或语义描述符"
            )
        }
    }

    /** human_type：人类增强打字（逐字符、随机间隔、偶尔打错字修正）。 */
    private suspend fun doHumanType(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("human_type 需要 element_id", "MISSING_ELEMENT_ID")
        val text = args["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("human_type 需要 text", "MISSING_TEXT")
        val mistakePct = runCatching { args["mistake_rate"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 5
        val mistakeRate = (mistakePct.coerceIn(0, 20)).toDouble() / 100.0
        val snap = browserController.humanType(id, text, mistakeRate)
        return writeEnvelope("human_type", args) { snap }
    }

    /** macro_record：开始/停止宏录制。 */
    private suspend fun doMacroRecord(args: Map<String, JsonElement>): ToolResult {
        val macroAction = args["macro_action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("macro_record 需要 macro_action（start/stop）", "MISSING_MACRO_ACTION")
        return if (macroAction == "start") {
            val msg = browserController.macroStartRecord()
            envelope("macro_record", ok = true, changed = true, summary = msg)
        } else {
            val count = browserController.macroStopRecord()
            envelope(
                action = "macro_record",
                ok = true,
                changed = true,
                summary = "宏录制已停止，共录制 $count 步操作",
                note = "使用 macro_playback 回放，macro_list 查看步骤，macro_clear 清空"
            )
        }
    }

    /** macro_playback：回放录制的宏，支持参数化替换。 */
    private suspend fun doMacroPlayback(args: Map<String, JsonElement>): ToolResult {
        val steps = browserController.getMacro()
        if (steps.isEmpty()) {
            return envelope("macro_playback", ok = false, summary = "没有可回放的宏（请先 macro_record start 录制）", recoverable = true)
        }
        val params = runCatching {
            (args["macro_params"] as? JsonObject)?.mapValues { (_, v) ->
                (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            }
        }.getOrNull() ?: emptyMap()

        val results = mutableListOf<String>()
        var success = 0
        var failed = 0
        for ((idx, step) in steps.withIndex()) {
            // 参数化替换
            var stepAction = step.action
            val stepArgs = step.args.mapValues { (_, v) ->
                var replaced = v
                for ((k, v2) in params) replaced = replaced.replace(k, v2)
                replaced
            }
            try {
                // 简单的操作回放（调用现有方法）
                when (stepAction) {
                    "click" -> { browserController.click(stepArgs["element_id"] ?: ""); success++ }
                    "type" -> { browserController.type(stepArgs["element_id"] ?: "", stepArgs["text"] ?: ""); success++ }
                    "scroll" -> { browserController.scroll(stepArgs["direction"] ?: "down"); success++ }
                    "navigate" -> { browserController.navigate(stepArgs["url"] ?: ""); success++ }
                    else -> { results.add("[${idx+1}] $stepAction: 跳过（不支持回放）"); continue }
                }
                results.add("[${idx+1}] $stepAction: 成功")
                // 步骤间随机延迟
                kotlinx.coroutines.delay((200..500).random().toLong())
            } catch (e: Exception) {
                failed++
                results.add("[${idx+1}] $stepAction: 失败 - ${e.message}")
            }
        }
        return envelope(
            action = "macro_playback",
            ok = failed == 0,
            changed = success > 0,
            summary = "宏回放完成：$success 成功，$failed 失败（共 ${steps.size} 步）",
            note = results.joinToString("\n").take(MAX_TEXT),
            recoverable = failed > 0
        )
    }

    /** macro_list：列出录制的宏步骤。 */
    private suspend fun doMacroList(): ToolResult {
        val steps = browserController.getMacro()
        val recording = browserController.isRecording()
        return envelope(
            action = "macro_list",
            ok = true,
            summary = "宏状态：${if (recording) "录制中" else "未录制"}，已录制 ${steps.size} 步",
            note = steps.mapIndexed { i, s -> "[${i+1}] ${s.action} ${s.args}" }.joinToString("\n").take(MAX_TEXT)
        )
    }

    /** macro_clear：清空录制的宏。 */
    private suspend fun doMacroClear(): ToolResult {
        browserController.macroClear()
        return envelope("macro_clear", ok = true, changed = true, summary = "宏已清空")
    }

    /** set_request_interval：设置请求间隔（避免被限流）。 */
    private suspend fun doSetRequestInterval(args: Map<String, JsonElement>): ToolResult {
        val ms = runCatching { args["interval_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull()
            ?: return ToolResult.Error("set_request_interval 需要 interval_ms", "MISSING_INTERVAL")
        browserController.setRequestInterval(ms)
        return envelope(
            action = "set_request_interval",
            ok = true,
            summary = "请求间隔已设置为 ${ms}ms",
            note = "后续操作间将自动添加该延迟以避免触发限流"
        )
    }

    /** set_user_agent：切换 UA。 */
    private suspend fun doSetUserAgent(args: Map<String, JsonElement>): ToolResult {
        val name = args["ua_name"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("set_user_agent 需要 ua_name", "MISSING_UA_NAME")
        val msg = browserController.setUserAgent(name)
        return envelope(
            action = "set_user_agent",
            ok = true,
            changed = true,
            summary = msg,
            note = "切换 UA 后建议刷新页面使新 UA 生效"
        )
    }

    /** list_user_agents：列出内置 UA 选项。 */
    private suspend fun doListUserAgents(): ToolResult {
        val uas = browserController.listUserAgents()
        return envelope(
            action = "list_user_agents",
            ok = true,
            summary = "内置 ${uas.size} 个 UA 预设",
            note = uas.map { (k, v) -> "$k: ${v.take(80)}" }.joinToString("\n").take(MAX_TEXT),
            extra = JsonObject(uas.mapValues { JsonPrimitive(it.value) })
        )
    }

    /**
     * action_chain 原子操作链：按顺序执行多步操作，某步失败时回滚到初始状态。
     * steps 参数为数组，每步 {action, element_id?, text?, direction?, url?}。
     */
    private suspend fun doActionChain(args: Map<String, JsonElement>): ToolResult {
        val stepsArr = runCatching { (args["steps"] as? JsonArray) }.getOrNull()
            ?: return ToolResult.Error("action_chain 需要 steps 数组参数", "MISSING_STEPS")

        // 记录初始状态用于回滚
        val initialUrl = browserController.currentSnapshot().url
        val results = mutableListOf<String>()
        var successCount = 0

        for ((idx, stepEl) in stepsArr.withIndex()) {
            val stepObj = stepEl as? JsonObject
                ?: continue
            val stepAction = (stepObj["action"] as? JsonPrimitive)?.content ?: ""
            val stepArgs = stepObj.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
            try {
                when (stepAction) {
                    "click" -> {
                        val id = stepArgs["element_id"] ?: ""
                        browserController.click(id); successCount++
                        results.add("[${idx+1}] click($id): 成功")
                    }
                    "type" -> {
                        val id = stepArgs["element_id"] ?: ""
                        val text = stepArgs["text"] ?: ""
                        browserController.type(id, text); successCount++
                        results.add("[${idx+1}] type($id, ...): 成功")
                    }
                    "scroll" -> {
                        val dir = stepArgs["direction"] ?: "down"
                        browserController.scroll(dir); successCount++
                        results.add("[${idx+1}] scroll($dir): 成功")
                    }
                    "navigate" -> {
                        val url = stepArgs["url"] ?: ""
                        browserController.navigate(url); successCount++
                        results.add("[${idx+1}] navigate($url): 成功")
                    }
                    "wait" -> {
                        val ms = stepArgs["ms"]?.toLongOrNull() ?: 1000L
                        kotlinx.coroutines.delay(ms); successCount++
                        results.add("[${idx+1}] wait(${ms}ms): 成功")
                    }
                    else -> results.add("[${idx+1}] $stepAction: 跳过（不支持的链操作）")
                }
            } catch (e: Exception) {
                // 回滚：导航回初始页面
                results.add("[${idx+1}] $stepAction: 失败 - ${e.message}")
                results.add("回滚中：导航回初始页面 $initialUrl ...")
                runCatching { browserController.navigate(initialUrl) }
                return envelope(
                    action = "action_chain",
                    ok = false,
                    changed = successCount > 0,
                    summary = "操作链在第 ${idx+1} 步失败，已回滚到初始状态（成功 $successCount 步）",
                    note = results.joinToString("\n").take(MAX_TEXT),
                    recoverable = true
                )
            }
        }
        return envelope(
            action = "action_chain",
            ok = true,
            changed = successCount > 0,
            summary = "操作链全部执行成功（$successCount 步）",
            note = results.joinToString("\n").take(MAX_TEXT)
        )
    }

    // ─────────────────────────── 第三批：会话与闭环动作 ───────────────────────────

    /** save_session：保存当前会话（cookies + storage + URL）。 */
    private suspend fun doSaveSession(): ToolResult {
        val sessionId = browserController.saveSession()
        return envelope(
            action = "save_session",
            ok = true,
            changed = true,
            summary = "会话已保存: $sessionId",
            note = "使用 restore_session(session_id=$sessionId) 恢复",
            extra = JsonObject(mapOf("session_id" to JsonPrimitive(sessionId)))
        )
    }

    /** restore_session：按 session_id 恢复会话。 */
    private suspend fun doRestoreSession(args: Map<String, JsonElement>): ToolResult {
        val sessionId = args["session_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("restore_session 需要 session_id", "MISSING_SESSION_ID")
        val msg = browserController.restoreSession(sessionId)
        return envelope(
            action = "restore_session",
            ok = !msg.startsWith("未找到"),
            changed = true,
            summary = msg,
            recoverable = msg.startsWith("未找到")
        )
    }

    /** list_sessions：列出已保存的会话。 */
    private suspend fun doListSessions(): ToolResult {
        val sessions = browserController.listSessions()
        return envelope(
            action = "list_sessions",
            ok = true,
            summary = "已保存 ${sessions.size} 个会话",
            note = sessions.map { "${it.sessionId}: ${it.url} (${it.timestamp})" }.joinToString("\n").take(MAX_TEXT)
        )
    }

    /** clear_data：清除浏览数据。 */
    private suspend fun doClearData(args: Map<String, JsonElement>): ToolResult {
        val types = args["clear_types"]?.jsonPrimitive?.contentOrNull ?: "all"
        val msg = browserController.clearData(types)
        return envelope(
            action = "clear_data",
            ok = true,
            changed = true,
            summary = msg
        )
    }

    /** set_incognito：切换隐身模式。 */
    private suspend fun doSetIncognito(args: Map<String, JsonElement>): ToolResult {
        val on = runCatching { args["incognito"]?.jsonPrimitive?.contentOrNull?.toBoolean() }.getOrNull()
            ?: return ToolResult.Error("set_incognito 需要 incognito 参数", "MISSING_INCOGNITO")
        browserController.setIncognitoMode(on)
        return envelope(
            action = "set_incognito",
            ok = true,
            changed = true,
            summary = if (on) "已开启隐身模式（不保留历史/cookie）" else "已关闭隐身模式"
        )
    }

    /** wait_for_download：等待下载完成。 */
    private suspend fun doWaitForDownload(args: Map<String, JsonElement>): ToolResult {
        val timeout = runCatching { args["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull() ?: 30_000L
        val info = browserController.waitForDownload(timeout)
        return if (info != null) {
            envelope(
                action = "wait_for_download",
                ok = info.status == "done",
                summary = "下载${if (info.status == "done") "完成" else "失败"}: ${info.fileName}",
                extra = JsonObject(mapOf("download" to downloadToJson(info)))
            )
        } else {
            envelope(
                action = "wait_for_download",
                ok = false,
                summary = "等待下载超时（${timeout}ms）",
                recoverable = true
            )
        }
    }

    /** download_to_workspace：获取下载文件的本地路径。 */
    private suspend fun doDownloadToWorkspace(args: Map<String, JsonElement>): ToolResult {
        val downloadId = args["download_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("download_to_workspace 需要 download_id", "MISSING_DOWNLOAD_ID")
        val file = browserController.downloadToWorkspace(downloadId)
        return if (file != null && file.exists()) {
            envelope(
                action = "download_to_workspace",
                ok = true,
                summary = "文件已就绪: ${file.name} (${file.length()} bytes)",
                note = "本地路径: ${file.absolutePath}",
                extra = JsonObject(mapOf("path" to JsonPrimitive(file.absolutePath), "size" to JsonPrimitive(file.length())))
            )
        } else {
            envelope(
                action = "download_to_workspace",
                ok = false,
                summary = "未找到下载任务 $downloadId 或文件不存在",
                recoverable = true
            )
        }
    }

    /** upload_from_url：从 URL 下载文件并上传到 input[type=file] 元素。 */
    private suspend fun doUploadFromUrl(args: Map<String, JsonElement>): ToolResult {
        val url = args["upload_url"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("upload_from_url 需要 upload_url", "MISSING_UPLOAD_URL")
        val elementId = args["element_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("upload_from_url 需要 element_id（目标 file input）", "MISSING_ELEMENT_ID")
        val file = browserController.downloadFileFromUrl(url)
            ?: return envelope("upload_from_url", ok = false, summary = "从 URL 下载文件失败: $url", recoverable = true)
        val error = browserController.uploadFile(elementId, file)
        return if (error == null) {
            envelope("upload_from_url", ok = true, summary = "已下载并上传文件到元素 #$elementId")
        } else {
            envelope("upload_from_url", ok = false, summary = "上传失败: $error", recoverable = true)
        }
    }

    /** detect_captcha：检测页面验证码并返回位置信息。 */
    private suspend fun doDetectCaptcha(): ToolResult {
        val result = browserController.detectCaptcha()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val detected: Boolean = runCatching { (parsed?.get("detected") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        // 自动截图返回，模型自行判断是否多模态可识图
        val screenshot = browserController.screenshot(null)
        return envelope(
            action = "detect_captcha",
            ok = true,
            summary = if (detected) "检测到验证码！建议调用 screenshot 查看并使用 takeover 请求用户协助" else "未检测到常见验证码",
            note = result.take(MAX_TEXT),
            extra = JsonObject(mapOf(
                "detected" to JsonPrimitive(detected),
                "screenshot_available" to JsonPrimitive(screenshot != null)
            ))
        )
    }

    /** permission_audit：审计页面权限状态。 */
    private suspend fun doPermissionAudit(): ToolResult {
        val result = browserController.permissionAudit()
        return envelope(
            action = "permission_audit",
            ok = true,
            summary = "权限审计完成",
            note = result.take(MAX_TEXT)
        )
    }

    /** block_resource：拦截指定类型资源渲染。 */
    private suspend fun doBlockResource(args: Map<String, JsonElement>): ToolResult {
        val types = args["block_types"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("block_resource 需要 block_types（image,font,media）", "MISSING_BLOCK_TYPES")
        val result = browserController.blockResource(types)
        return envelope(
            action = "block_resource",
            ok = true,
            summary = "已拦截资源类型: $types",
            note = result.take(MAX_TEXT)
        )
    }

    /** operation_log：查看操作日志。 */
    private suspend fun doOperationLog(args: Map<String, JsonElement>): ToolResult {
        val limit = runCatching { args["log_limit"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 30
        val log = browserController.operationLog(limit)
        return envelope(
            action = "operation_log",
            ok = true,
            summary = "最近 ${log.size} 条操作日志",
            note = log.joinToString("\n").take(MAX_TEXT)
        )
    }

    /** screenshot_full_page：全页截图（当前实现为视口截图 + 页面高度信息）。 */
    private suspend fun doScreenshotFullPage(): ToolResult {
        val dataUrl = browserController.screenshotFullPage()
            ?: return envelope("screenshot_full_page", ok = false, summary = "全页截图失败", recoverable = true)
        return envelope(
            action = "screenshot_full_page",
            ok = true,
            summary = "已截取页面（视口截图，页面完整高度信息已返回）",
            note = "Android WebView 限制：当前截取可视区域。可配合 scroll 逐步截取完整页面。",
            extra = JsonObject(mapOf("image_data_url" to JsonPrimitive(dataUrl)))
        )
    }

    // ─────────────────────────── 第四批：SPA 专项动作 ───────────────────────────

    /** detect_framework：检测前端框架及版本。 */
    private suspend fun doDetectFramework(): ToolResult {
        val result = browserController.detectFramework()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val framework: String = runCatching { (parsed?.get("framework") as? JsonPrimitive)?.content }.getOrNull() ?: "unknown"
        val version: String = runCatching { (parsed?.get("version") as? JsonPrimitive)?.content }.getOrNull() ?: ""
        return envelope(
            action = "detect_framework",
            ok = true,
            summary = "前端框架: $framework${if (version.isNotEmpty()) " v$version" else ""}",
            note = result.take(MAX_TEXT)
        )
    }

    /** extract_ssr_data：提取 SSR 注入数据。 */
    private suspend fun doExtractSsrData(): ToolResult {
        val result = browserController.extractSsrData()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val count = parsed?.get("count")?.let { if (it is JsonPrimitive) it.content.toIntOrNull() ?: 0 } ?: 0
        return envelope(
            action = "extract_ssr_data",
            ok = true,
            summary = "提取到 $count 个 SSR 数据源（单条截断到 1MB）",
            note = result.take(MAX_TEXT)
        )
    }

    /** extract_framework_state：提取框架内部状态（React Fiber/Vue/Pinia/Zustand）。 */
    private suspend fun doExtractFrameworkState(): ToolResult {
        val result = browserController.extractFrameworkState()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val type = parsed?.get("type")?.let { if (it is JsonPrimitive) it.content } ?: "unknown"
        val supported: Boolean = runCatching { (parsed?.get("supported") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        val note = parsed?.get("note")?.let { if (it is JsonPrimitive) it.content } ?: ""
        return envelope(
            action = "extract_framework_state",
            ok = true,
            summary = if (supported) "已提取 $type 框架内部状态" else "框架状态访问: $type（$note）",
            note = result.take(MAX_TEXT)
        )
    }

    /** detect_virtual_list：检测虚拟列表。 */
    private suspend fun doDetectVirtualList(): ToolResult {
        val result = browserController.detectVirtualList()
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val isVirtual: Boolean = runCatching { (parsed?.get("virtual") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        val estimated = parsed?.get("estimated_total")?.let { if (it is JsonPrimitive) it.content.toIntOrNull() ?: 0 } ?: 0
        return envelope(
            action = "detect_virtual_list",
            ok = true,
            summary = if (isVirtual) "检测到虚拟列表（估算约 $estimated 行数据，当前 DOM 仅渲染可视区域）" else "未检测到虚拟列表特征",
            note = result.take(MAX_TEXT)
        )
    }

    /** spa_navigate：SPA 路由导航。 */
    private suspend fun doSpaNavigate(args: Map<String, JsonElement>): ToolResult {
        val url = args["spa_url"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("spa_navigate 需要 spa_url", "MISSING_SPA_URL")
        val result = browserController.spaNavigate(url)
        val parsed = runCatching { json.parseToJsonElement(result).jsonObject }.getOrNull()
        val ok: Boolean = runCatching { (parsed?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        val method = parsed?.get("method")?.let { if (it is JsonPrimitive) it.content } ?: ""
        return envelope(
            action = "spa_navigate",
            ok = ok,
            changed = ok,
            summary = if (ok) "SPA 导航成功（$method）→ $url" else "SPA 导航失败: $method",
            recoverable = !ok
        )
    }

    /** api_paginate：API 分页参数识别与遍历建议。 */
    private suspend fun doApiPaginate(args: Map<String, JsonElement>): ToolResult {
        val maxPages = runCatching { args["max_pages"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 3
        val urlPattern = args["api_url_pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        val result = browserController.apiPaginate(maxPages, urlPattern)
        return envelope(
            action = "api_paginate",
            ok = true,
            summary = "API 分页分析完成",
            note = result.take(MAX_TEXT)
        )
    }

    // ─────────────────────── 指纹管理动作 ───────────────────────

    /** 序列化单个指纹配置为 JSON 对象。 */
    private fun profileToJson(p: FingerprintProfile): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(p.id),
            "name" to JsonPrimitive(p.name),
            "browser" to JsonPrimitive(p.browser),
            "browser_version" to JsonPrimitive(p.browserVersion),
            "os" to JsonPrimitive(p.os),
            "os_version" to JsonPrimitive(p.osVersion),
            "platform" to JsonPrimitive(p.platform),
            "region" to JsonPrimitive(p.region),
            "timezone" to JsonPrimitive(p.timezone),
            "language" to JsonPrimitive(p.language),
            "hardware_concurrency" to JsonPrimitive(p.hardwareConcurrency),
            "device_memory" to JsonPrimitive(p.deviceMemory),
            "screen" to JsonPrimitive("${p.screenWidth}x${p.screenHeight}"),
            "score" to JsonPrimitive(p.score),
            "status" to JsonPrimitive(p.status),
            "is_available" to JsonPrimitive(p.isAvailable),
            "is_cooling" to JsonPrimitive(p.isCooling),
            "total_uses" to JsonPrimitive(p.totalUses),
            "webrtc_protection" to JsonPrimitive(p.webrtcProtectionLevel),
            "webgl_spoof" to JsonPrimitive(p.webglSpoofEnabled),
            "canvas_noise" to JsonPrimitive(p.canvasNoiseEnabled)
        )
    )

    /** fingerprint_list：列出所有指纹配置，支持 status/region/browser 筛选。 */
    private fun doFingerprintList(args: Map<String, JsonElement>): ToolResult {
        val statusFilter = args["status"]?.jsonPrimitive?.contentOrNull
        val regionFilter = args["region"]?.jsonPrimitive?.contentOrNull
        val browserFilter = args["browser"]?.jsonPrimitive?.contentOrNull

        var list = fingerprintManager.getAll()
        statusFilter?.let { f -> list = list.filter { it.status.equals(f, ignoreCase = true) } }
        regionFilter?.let { f -> list = list.filter { it.region.equals(f, ignoreCase = true) } }
        browserFilter?.let { f -> list = list.filter { it.browser.equals(f, ignoreCase = true) } }

        val currentId = fingerprintManager.currentProfileId.value
        val profilesJson = JsonArray(list.map { p ->
            JsonObject(profileToJson(p) + ("is_current" to JsonPrimitive(p.id == currentId)))
        })
        val stats = fingerprintManager.getStats()

        return envelope(
            action = "fingerprint_list",
            ok = true,
            summary = "共 ${list.size} 个指纹配置（可用 ${stats["available"]}，冷却中 ${stats["cooling"]}）",
            extra = JsonObject(mapOf(
                "profiles" to profilesJson,
                "stats" to JsonObject(stats.mapValues { JsonPrimitive(it.value.toString()) }),
                "current_id" to JsonPrimitive(currentId ?: "none")
            ))
        )
    }

    /** fingerprint_get：获取当前或指定配置详情。 */
    private fun doFingerprintGet(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
        val profile = if (profileId.isNullOrBlank()) {
            fingerprintManager.getCurrent()
        } else {
            fingerprintManager.getById(profileId)
        } ?: return envelope(
            action = "fingerprint_get",
            ok = false,
            error = "指纹配置不存在：${profileId ?: "(当前无激活配置)"}",
            errorCode = "PROFILE_NOT_FOUND",
            recoverable = true
        )

        return envelope(
            action = "fingerprint_get",
            ok = true,
            summary = profile.shortDescription,
            extra = JsonObject(mapOf("profile" to profileToJson(profile)))
        )
    }

    /** fingerprint_set：切换到指定配置，可选自动重载页面。 */
    private suspend fun doFingerprintSet(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
            ?: return envelope(
                action = "fingerprint_set",
                ok = false,
                error = "缺少 profile_id 参数",
                errorCode = "MISSING_PROFILE_ID"
            )
        val applyNow = args["apply_now"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true

        val target = browserController.switchFingerprint(profileId, reload = applyNow)
            ?: return envelope(
                action = "fingerprint_set",
                ok = false,
                error = "无法切换到指纹 $profileId：配置不存在、不可用或正在冷却期",
                errorCode = "SWITCH_FAILED",
                recoverable = true
            )

        return envelope(
            action = "fingerprint_set",
            ok = true,
            changed = true,
            summary = "已切换到指纹：${target.name}（${target.region} · ${target.browser} ${target.browserVersion}）",
            note = if (applyNow) "页面已重新加载，新指纹已生效" else "已切换配置，下次页面加载时生效"
        )
    }

    /** fingerprint_create：创建随机配置。 */
    private fun doFingerprintCreate(args: Map<String, JsonElement>): ToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull
        val templateName = args["template"]?.jsonPrimitive?.contentOrNull
        val template = when (templateName?.lowercase()) {
            "region_us", "us" -> FingerprintGenerator.Template.REGION_US
            "region_cn", "cn" -> FingerprintGenerator.Template.REGION_CN
            "region_jp", "jp" -> FingerprintGenerator.Template.REGION_JP
            "region_eu", "eu" -> FingerprintGenerator.Template.REGION_EU
            "high_end", "gaming" -> FingerprintGenerator.Template.HIGH_END
            "mobile", "android" -> FingerprintGenerator.Template.MOBILE
            else -> null
        }

        val profile = fingerprintManager.createRandom(template = template, name = name)
        return envelope(
            action = "fingerprint_create",
            ok = true,
            changed = true,
            summary = "已创建指纹：${profile.name}（ID: ${profile.id}）",
            note = "地区=${profile.region}，浏览器=${profile.browser} ${profile.browserVersion}，评分=${profile.score.toInt()}",
            extra = JsonObject(mapOf("profile" to profileToJson(profile)))
        )
    }

    /** fingerprint_delete：删除配置。 */
    private fun doFingerprintDelete(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
            ?: return envelope(
                action = "fingerprint_delete",
                ok = false,
                error = "缺少 profile_id 参数",
                errorCode = "MISSING_PROFILE_ID"
            )
        val force = args["force"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

        val ok = fingerprintManager.delete(profileId, force = force)
        return if (ok) {
            envelope(
                action = "fingerprint_delete",
                ok = true,
                changed = true,
                summary = "已删除指纹配置：$profileId"
            )
        } else {
            envelope(
                action = "fingerprint_delete",
                ok = false,
                error = "删除失败：配置不存在、是当前激活配置（需 force=true）、或为内置真实指纹配置",
                errorCode = "DELETE_FAILED",
                recoverable = true
            )
        }
    }

    /** fingerprint_validate：校验配置一致性。 */
    private fun doFingerprintValidate(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
        val result = fingerprintManager.validate(profileId)
            ?: return envelope(
                action = "fingerprint_validate",
                ok = false,
                error = "配置不存在或无当前激活配置",
                errorCode = "PROFILE_NOT_FOUND"
            )

        val issues = result.issues.joinToString("；") { "${it.field}: ${it.message}" }
        return envelope(
            action = "fingerprint_validate",
            ok = result.valid,
            summary = if (result.valid) "配置一致性校验通过（评分 ${result.score.toInt()}）"
                       else "配置存在 ${result.issues.size} 个一致性问题（评分 ${result.score.toInt()}）",
            note = if (result.valid) "所有指纹字段内部一致" else issues,
            extra = JsonObject(mapOf(
                "valid" to JsonPrimitive(result.valid),
                "score" to JsonPrimitive(result.score),
                "issue_count" to JsonPrimitive(result.issues.size)
            ))
        )
    }

    /** fingerprint_test：运行指纹检测（简化版：返回当前配置评分和一致性结果）。 */
    private fun doFingerprintTest(): ToolResult {
        val current = fingerprintManager.getCurrent()
            ?: return envelope(
                action = "fingerprint_test",
                ok = false,
                error = "无当前激活指纹配置",
                errorCode = "NO_PROFILE"
            )
        val validation = fingerprintManager.validate()
        val scriptSize = com.mini.me_core.feature.browser.domain.fingerprint.FingerprintInjector
            .estimateScriptSize(current)

        return envelope(
            action = "fingerprint_test",
            ok = true,
            summary = "当前指纹：${current.shortDescription}",
            note = "一致性评分=${validation?.score?.toInt() ?: 0}/100，" +
                "注入脚本大小=${scriptSize}字节，" +
                "WebRTC防护级别=${current.webrtcProtectionLevel}",
            extra = JsonObject(mapOf(
                "profile" to profileToJson(current),
                "consistency_score" to JsonPrimitive(validation?.score ?: 0f),
                "script_size_bytes" to JsonPrimitive(scriptSize)
            ))
        )
    }

    // ─────────────────────── 反爬控制动作 ───────────────────────

    /** antidetect_status：获取当前反爬状态。 */
    private fun doAntidetectStatus(): ToolResult {
        val s = antidetectController.state.value
        return envelope(
            action = "antidetect_status",
            ok = true,
            summary = "反爬状态：detected=${s.detected}，风险=${s.riskLevel}，健康度=${s.healthScore.toInt()}/100",
            extra = JsonObject(mapOf(
                "detected" to JsonPrimitive(s.detected),
                "risk_level" to JsonPrimitive(s.riskLevel),
                "health_score" to JsonPrimitive(s.healthScore),
                "consecutive_failures" to JsonPrimitive(s.consecutiveFailures),
                "paused" to JsonPrimitive(s.paused),
                "pause_remaining_seconds" to JsonPrimitive(s.pauseRemainingSeconds),
                "last_detection_at" to JsonPrimitive(s.lastDetectionAt),
                "last_strategy" to JsonPrimitive(s.lastStrategy ?: "")
            ))
        )
    }

    /** antidetect_signals：获取活跃信号和历史。 */
    private fun doAntidetectSignals(args: Map<String, JsonElement>): ToolResult {
        val limit = runCatching { args["limit"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 10
        val active = antidetectController.getActiveSignals()
        val history = antidetectController.getSignalHistory(limit = limit)

        fun signalToJson(sig: SignalDetector.Signal) = JsonObject(mapOf(
            "type" to JsonPrimitive(sig.type),
            "severity" to JsonPrimitive(sig.severity),
            "url" to JsonPrimitive(sig.url),
            "description" to JsonPrimitive(sig.description),
            "timestamp" to JsonPrimitive(sig.timestamp)
        ))

        return envelope(
            action = "antidetect_signals",
            ok = true,
            summary = "活跃信号 ${active.size} 个，历史记录 ${history.size} 条",
            note = if (active.isEmpty()) "当前无活跃反爬信号"
                   else active.joinToString("；") { it.description },
            extra = JsonObject(mapOf(
                "active_signals" to JsonArray(active.map { signalToJson(it) }),
                "history" to JsonArray(history.map { signalToJson(it) })
            ))
        )
    }

    /** antidetect_adjust：执行自适应调整。 */
    private fun doAntidetectAdjust(args: Map<String, JsonElement>): ToolResult {
        val strategy = args["strategy"]?.jsonPrimitive?.contentOrNull ?: "auto"
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "手动触发"
        val result = antidetectController.adjust(strategy = strategy, reason = reason)
        return envelope(
            action = "antidetect_adjust",
            ok = true,
            changed = true,
            summary = "自适应调整完成（策略：$strategy）",
            note = result
        )
    }

    /** antidetect_pause：暂停浏览器操作。 */
    private fun doAntidetectPause(args: Map<String, JsonElement>): ToolResult {
        val duration = runCatching { args["duration_seconds"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 300
        antidetectController.pause(durationSeconds = duration)
        return envelope(
            action = "antidetect_pause",
            ok = true,
            changed = true,
            summary = "已暂停浏览器操作 ${duration} 秒",
            note = "暂停期间所有浏览器导航和交互操作将被拒绝，调用 antidetect_resume 可提前恢复"
        )
    }

    /** antidetect_resume：恢复浏览器操作。 */
    private fun doAntidetectResume(): ToolResult {
        antidetectController.resume()
        return envelope(
            action = "antidetect_resume",
            ok = true,
            changed = true,
            summary = "已恢复浏览器操作",
            note = "连续失败计数已重置，活跃信号已清除"
        )
    }

    /** browser_status：综合状态（当前指纹+反爬状态+当前URL）。 */
    private fun doBrowserStatus(): ToolResult {
        val fp = fingerprintManager.getCurrent()
        val ad = antidetectController.state.value
        val currentUrl = browserController.uiState.value.currentUrl

        return envelope(
            action = "browser_status",
            ok = true,
            summary = "当前页面：${currentUrl.ifBlank { "(无)" }}",
            note = "指纹：${fp?.shortDescription ?: "无"} | " +
                "反爬：${if (ad.detected) "已检测(${ad.riskLevel})" else "正常"} | " +
                "健康度：${ad.healthScore.toInt()}/100 | " +
                "${if (ad.paused) "暂停中(${ad.pauseRemainingSeconds}s)" else "运行中"}",
            extra = JsonObject(mapOf(
                "current_url" to JsonPrimitive(currentUrl),
                "fingerprint" to (fp?.let { profileToJson(it) } ?: JsonObject(emptyMap())),
                "antidetect" to JsonObject(mapOf(
                    "detected" to JsonPrimitive(ad.detected),
                    "risk_level" to JsonPrimitive(ad.riskLevel),
                    "health_score" to JsonPrimitive(ad.healthScore),
                    "paused" to JsonPrimitive(ad.paused)
                ))
            ))
        )
    }

    private fun downloadToJson(d: BrowserDownloadInfo): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(d.id),
                "url" to JsonPrimitive(d.url),
                "file_name" to JsonPrimitive(d.fileName),
                "path" to JsonPrimitive(d.path),
                "status" to JsonPrimitive(d.status),
                "error" to JsonPrimitive(d.error)
            )
        )

    private fun networkToJson(rec: BrowserNetworkRecord): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(rec.id),
                "op" to JsonPrimitive(rec.op),
                "method" to JsonPrimitive(rec.method),
                "url" to JsonPrimitive(rec.url),
                "status" to JsonPrimitive(rec.status),
                "duration_ms" to JsonPrimitive(rec.durationMs),
                "size" to JsonPrimitive(rec.size),
                "response_snippet" to JsonPrimitive(rec.responseSnippet),
                "error" to JsonPrimitive(rec.error)
            )
        )

    private fun tabsToJson(): JsonArray {
        val activeId = browserController.uiState.value.activeTabId
        return JsonArray(
            browserController.listTabs().map { t ->
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(t.id),
                        "title" to JsonPrimitive(t.title),
                        "url" to JsonPrimitive(t.url),
                        "active" to JsonPrimitive(t.id == activeId)
                    )
                )
            }
        )
    }

    // ─────────────────────────── 统一 envelope ───────────────────────────

    /**
     * 统一动作 envelope（R2.3 干净替换）：所有动作返回 `{ok, action, changed, summary, note|error,
     * recoverable, snapshot?, delta?}`，[extra] 用于追加结构化的动作专属数据（tabs/requests 等）。
     */
    /**
     * 在已有 ToolResult.Success 的 envelope 中追加 note 提示（用于反爬信号提醒等场景）。
     * 对 Error 类型直接返回原样不修改。
     */
    private fun appendNoteToResult(result: ToolResult, note: String): ToolResult {
        if (result !is ToolResult.Success) return result
        val original = result.data as? JsonObject ?: return result
        val existingNote = (original["note"] as? JsonPrimitive)?.content ?: ""
        val merged = if (existingNote.isBlank()) note else "$existingNote\n$note"
        val newFields = original.toMutableMap()
        newFields["note"] = JsonPrimitive(merged)
        return ToolResult.Success(JsonObject(newFields))
    }

    private fun envelope(
        action: String,
        ok: Boolean = true,
        changed: Boolean = false,
        summary: String = "",
        note: String = "",
        error: String = "",
        errorCode: String = "",
        recoverable: Boolean = false,
        snapshot: JsonObject? = null,
        delta: BrowserSnapshotDelta? = null,
        extra: JsonObject? = null
    ): ToolResult {
        val fields = linkedMapOf<String, JsonElement>(
            "ok" to JsonPrimitive(ok),
            "action" to JsonPrimitive(action),
            "changed" to JsonPrimitive(changed),
            "summary" to JsonPrimitive(summary)
        )
        if (note.isNotBlank()) fields["note"] = JsonPrimitive(note)
        if (error.isNotBlank()) fields["error"] = JsonPrimitive(error)
        if (errorCode.isNotBlank()) fields["error_code"] = JsonPrimitive(errorCode)
        if (recoverable) fields["recoverable"] = JsonPrimitive(true)
        snapshot?.let { fields["snapshot"] = it }
        delta?.let { fields["delta"] = deltaToJson(it) }
        extra?.let { fields.putAll(it) }
        return ToolResult.Success(JsonObject(fields))
    }

    /** 增量 delta 序列化（R2.1）：新增/变化元素以紧凑摘要返回，消失元素只给 id。 */
    private fun deltaToJson(delta: BrowserSnapshotDelta): JsonObject = JsonObject(
        mapOf(
            "added" to JsonArray(delta.added.map { elementBriefJson(it) }),
            "removed" to JsonArray(delta.removed.map { JsonPrimitive(it) }),
            "changed" to JsonArray(delta.changed.map { elementBriefJson(it) }),
            "text_note" to JsonPrimitive(delta.textNote)
        )
    )

    private fun writeDeltaSummary(action: String, delta: BrowserSnapshotDelta): String = buildString {
        append(action).append("完成")
        val parts = mutableListOf<String>()
        if (delta.added.isNotEmpty()) parts.add("新增 ${delta.added.size}")
        if (delta.changed.isNotEmpty()) parts.add("变化 ${delta.changed.size}")
        if (delta.removed.isNotEmpty()) parts.add("消失 ${delta.removed.size}")
        if (parts.isNotEmpty()) append("：").append(parts.joinToString("，"))
        if (delta.textNote.isNotBlank()) append("；").append(delta.textNote)
    }

    private fun snapshotSummary(snap: BrowserPageSnapshot): String = buildString {
        append("页面「").append(snap.title.ifBlank { "(无标题)" }).append("」")
        if (snap.url.isNotBlank()) append(" URL=").append(snap.url)
        val visible = snap.elements.count { it.visible && it.inViewport }
        append(" 控件 ").append(snap.elements.size).append(" 个（视口内 ").append(visible).append("）")
        if (snap.hasLoginForm) append("，检测到登录表单")
        if (snap.pendingRequests > 0) append("，在途请求 ").append(snap.pendingRequests)
    }

    private fun snapshotLevelOf(args: Map<String, JsonElement>): SnapshotLevel =
        SnapshotLevel.fromName(args["snapshot_level"]?.jsonPrimitive?.contentOrNull)

    /** 导航/标签切换类动作默认 standard（页面完全变化，给完整元素便于继续操作），可用 snapshot_level 覆盖。 */
    private fun navLevelOf(args: Map<String, JsonElement>): SnapshotLevel {
        val explicit = args["snapshot_level"]?.jsonPrimitive?.contentOrNull
        return if (explicit != null) SnapshotLevel.fromName(explicit) else SnapshotLevel.STANDARD
    }

    // ─────────────────────────── 序列化辅助 ───────────────────────────

    /**
     * 快照分级序列化（R2.1）：
     *  - summary：标题/URL/登录信号 + 每控件一行的紧凑摘要（含可操作性标注），不含完整元素与 page_text；
     *  - standard：summary + 完整元素 JSON；
     *  - full：standard + page_text。
     */
    private fun snapshotToJson(snap: BrowserPageSnapshot, level: SnapshotLevel = SnapshotLevel.SUMMARY): JsonObject {
        val elements = snap.elements.take(MAX_ELEMENTS)
        // 每控件一行的紧凑清单 + 可操作性标注（R2.2）：模型可快速扫出「编号 + 控件类型 + 名称 + 状态 + 可操作性」
        val sb = StringBuilder()
        for (el in elements) {
            sb.append('[').append(el.id).append("] ").append(el.kind)
            val name = el.label.ifBlank { el.text }.ifBlank { el.value }
            if (name.isNotBlank()) sb.append(" \"").append(name).append('"')
            when {
                el.kind.endsWith(":checkbox") || el.kind.endsWith(":radio") ->
                    sb.append(if (el.checked) " [已勾选]" else " [未勾选]")
                el.kind == "select" && el.value.isNotBlank() ->
                    sb.append(" [当前=").append(el.value).append(']')
            }
            if (el.kind == "select" && el.options.isNotEmpty()) {
                sb.append(" 选项=").append(el.options.joinToString("/") { o -> o.text.ifBlank { o.value } })
            }
            if (el.required) sb.append(" [必填]")
            if (el.disabled) sb.append(" [禁用]")
            // 可操作性标注（R2.2）：不可见/视口外/被遮挡
            if (!el.visible) sb.append(" [不可见]")
            else if (el.needsScroll) sb.append(" [视口外]")
            if (el.overlapped) sb.append(" [被遮挡]")
            if (el.kind == "link" && el.href.isNotBlank()) sb.append(" -> ").append(el.href)
            if (el.placeholder.isNotBlank() && el.label.isBlank()) sb.append(" (placeholder=").append(el.placeholder).append(')')
            sb.append('\n')
        }
        // 标题大纲（缩进展示层级，帮助模型快速理解页面结构）
        val headingSb = StringBuilder()
        for (h in snap.headings) {
            headingSb.append("  ".repeat((h.level - 1).coerceAtLeast(0)))
                .append('H').append(h.level).append(' ').append(h.text).append('\n')
        }
        val fields = linkedMapOf<String, JsonElement>(
            "title" to JsonPrimitive(snap.title),
            "url" to JsonPrimitive(snap.url),
            "login_page" to JsonPrimitive(snap.hasLoginForm),
            "login_hint" to JsonPrimitive(snap.loginHint),
            "pending_requests" to JsonPrimitive(snap.pendingRequests),
            "controls_total" to JsonPrimitive(snap.elements.size),
            "controls_shown" to JsonPrimitive(elements.size),
            "controls_summary" to JsonPrimitive(sb.toString()),
            "headings_summary" to JsonPrimitive(headingSb.toString())
        )
        if (level != SnapshotLevel.SUMMARY) {
            fields["headings"] = JsonArray(
                snap.headings.map { h ->
                    JsonObject(mapOf("level" to JsonPrimitive(h.level), "text" to JsonPrimitive(h.text)))
                }
            )
            fields["elements"] = JsonArray(elements.map { elementJson(it) })
        }
        if (level == SnapshotLevel.FULL) {
            fields["page_text"] = JsonPrimitive(snap.pageText.take(MAX_TEXT))
        }
        return JsonObject(fields)
    }

    /** 完整元素 JSON（standard/full 级）：含三级定位字段与可操作性标注（R2.2）。 */
    private fun elementJson(el: BrowserElement): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(el.id),
            "kind" to JsonPrimitive(el.kind),
            "tag" to JsonPrimitive(el.tag),
            "type" to JsonPrimitive(el.type),
            "label" to JsonPrimitive(el.label),
            "text" to JsonPrimitive(if (el.sensitive) "" else el.text),
            "value" to JsonPrimitive(if (el.sensitive) "" else el.value),
            "href" to JsonPrimitive(el.href),
            "placeholder" to JsonPrimitive(el.placeholder),
            "checked" to JsonPrimitive(el.checked),
            "disabled" to JsonPrimitive(el.disabled),
            "required" to JsonPrimitive(el.required),
            "readonly" to JsonPrimitive(el.readonly),
            "options" to JsonArray(
                el.options.map { o ->
                    JsonObject(mapOf("value" to JsonPrimitive(o.value), "text" to JsonPrimitive(o.text)))
                }
            ),
            "sensitive" to JsonPrimitive(el.sensitive),
            // R2.2 三级定位 + 可操作性标注
            "locator" to JsonPrimitive(el.locator),
            "semantic" to JsonPrimitive(el.semantic),
            "in_viewport" to JsonPrimitive(el.inViewport),
            "visible" to JsonPrimitive(el.visible),
            "needs_scroll" to JsonPrimitive(el.needsScroll),
            "overlapped" to JsonPrimitive(el.overlapped)
        )
    )

    /** 增量/变化元素紧凑摘要（delta 用）。 */
    private fun elementBriefJson(el: BrowserElement): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(el.id),
            "kind" to JsonPrimitive(el.kind),
            "label" to JsonPrimitive(el.label),
            "text" to JsonPrimitive(if (el.sensitive) "" else el.text),
            "in_viewport" to JsonPrimitive(el.inViewport),
            "visible" to JsonPrimitive(el.visible),
            "needs_scroll" to JsonPrimitive(el.needsScroll),
            "overlapped" to JsonPrimitive(el.overlapped)
        )
    )

    private fun extractHost(url: String): String? {
        return runCatching { URI(url).host } .getOrNull()?.lowercase()
    }
}
