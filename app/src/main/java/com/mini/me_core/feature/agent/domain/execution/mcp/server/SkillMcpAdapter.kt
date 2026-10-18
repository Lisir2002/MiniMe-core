package com.mini.me_core.feature.agent.domain.execution.mcp.server

import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * F4：MCP prompts / resources 的服务端端口。
 *
 * **为什么 prompts 与 resources 都归 Skill**（D3）：
 * - MCP 规范明确 prompts 是 **user-controlled**（"designed to be user-controlled, meaning
 *   they are exposed from servers to clients with the intention of the user being able to
 *   explicitly select them for use"），典型形态是 UI 里的斜杠命令——这与 Skill 的
 *   `PROMPT` 型语义（用户显式选择后把指令注入上下文）**完全对位**。
 * - MCP 规范明确 resources 是 **application-driven**（由宿主应用决定如何纳入上下文），
 *   URI 寻址、只读、可订阅——与 `SCRIPT` 型 Skill（URI 寻址的脚本资源）天然契合。
 * - 另有 `SkillType.MCP`：调用映射到某已连接的 MCP 工具，属**消费方**而非被暴露方，
 *   故不出现在 prompts/resources 列表里。
 *
 * 实现（`DefaultSkillMcpAdapter`）与 [AgentToolMcpAdapter] 平行，
 * 共同挂在 [McpServerSession] 上。
 */
interface SkillMcpAdapter {

    /**
     * 是否接入任何可暴露技能。
     *
     * 供 `initialize` 决定要不要声明 `prompts` / `resources` 能力。
     *
     * 注意语义是「**当前**是否有可暴露技能」而非「是否接了技能源」：技能被全部禁用时
     * 返回 false，能力声明随之消失。若声明了能力却恒返回空列表，等于对外承诺了一个
     * 永远空实现的接口，客户端会反复重试。
     */
    fun hasSkills(): Boolean

    /** `prompts/list`：`PROMPT` 型且已启用的技能。 */
    fun listPrompts(): List<Skill>

    /** `prompts/get`：按 id 取技能正文；不存在返回 null。 */
    fun getPrompt(id: String): Skill?

    /** `resources/list`：`SCRIPT` 型且已启用的技能。 */
    fun listResources(): List<Skill>

    /** `resources/read`：按 URI 取技能；URI 不属于本 server 返回 null。 */
    fun getResource(uri: String): Skill?
}

/**
 * 无技能时使用的空实现。
 *
 * 不给 `SkillMcpAdapter` 设 `null` 默认，而是显式传入——内置三分组 server 与
 * HostServer 都**刻意不接**技能（内置分组是工具维度，技能是用户维度），由装配处
 * 显式传 [EmptySkillMcpAdapter] 表明意图。
 */
object EmptySkillMcpAdapter : SkillMcpAdapter {
    override fun hasSkills(): Boolean = false
    override fun listPrompts(): List<Skill> = emptyList()
    override fun getPrompt(id: String): Skill? = null
    override fun listResources(): List<Skill> = emptyList()
    override fun getResource(uri: String): Skill? = null
}

// ──────────────────────────────────────────────
// MCP 协议形状
// ──────────────────────────────────────────────

/**
 * `prompts/list` 的 `result`。
 *
 * 依规范，prompt 定义含 `name` / `title` / `description` / `arguments` / `icons`，
 * 其中 `name` 必填。技能的 `name` 可能含空格与非 ASCII（用户自定义），而规范未对
 * prompt 名施加字符集限制，故这里原样透传，不做命名空间化——与 `McpTool` 不同，
 * prompt 不进模型 function-calling 的 `tools` 数组，不受该限制影响。
 */
fun Skill.toPromptDescriptor(): JsonObject = buildJsonObject {
    put("name", id)
    put("title", name)
    put("description", description)
    // PROMPT 型技能不接受运行时参数：它的正文就是完整的指令模板，
    // 需要变量化的能力应走 SCRIPT 型或 MCP 型。故 arguments 恒省略而非空数组——
    // 空数组会让部分客户端渲染出一个无意义的空参数表。
}

/**
 * `resources/list` 的元素。
 *
 * 依规范，resource 定义含 `uri` / `name` / `title` / `description` / `mimeType`。
 */
fun Skill.toResourceDescriptor(): JsonObject {
    // 先在 lambda 外算好 URI：buildJsonObject 的 lambda 带接收者，内部的 this 指向
    // JsonObjectBuilder 而非 Skill，直接写 this 会拿到 builder 传给 of() 而编译失败。
    val uri = SkillResourceUri.of(this)
    return buildJsonObject {
        put("uri", uri)
        put("name", id)
        put("title", name)
        put("description", description)
        // 脚本技能以文本形式暴露（MIME 由 [SkillResourceUri] 统一给出），
        // 二进制内容走 runSkillScript 的容器执行，不经 resources/read。
        put("mimeType", SkillResourceUri.MIME_TEXT_PLAIN)
    }
}

/** `prompts/get` 的 `result`。 */
fun Skill.toPromptResult(): JsonObject = buildJsonObject {
    put("description", description)
    putJsonArray("messages") {
        add(buildJsonObject {
            // 技能指令是给模型看的用户侧输入，故 role=user（规范只允许 user/assistant）。
            put("role", "user")
            putJsonObject("content") {
                put("type", "text")
                put("text", instructions)
            }
        })
    }
}

/** `resources/read` 的 `result`。 */
fun Skill.toResourceResult(): JsonObject = buildJsonObject {
    putJsonArray("contents") {
        add(buildJsonObject {
            put("uri", SkillResourceUri.of(this@toResourceResult))
            put("mimeType", SkillResourceUri.MIME_TEXT_PLAIN)
            put("text", this@toResourceResult.scriptBody())
        })
    }
}

/**
 * SCRIPT 型技能的入口脚本文本。
 *
 * `resources/read` 读的是**脚本文本**而非执行结果——resources 的语义是「只读内容」，
 * 不是「执行动作」。真正的执行仍走既有 `runSkillScript` 工具（需 ZTH 审批）。
 * 若在这里执行，等于让外部 MCP 客户端借 resources/read 绕过审批，属安全缺陷。
 */
private fun Skill.scriptBody(): String = buildString {
    appendLine("# 技能: $name ($id)")
    if (description.isNotBlank()) appendLine("# 说明: $description")
    entry?.let { appendLine("# 入口脚本: $it") }
    appendLine("# 注：本资源暴露的是技能定义文本；执行请调用 runSkillScript 工具（需审批）。")
}

/**
 * 技能的 MCP 资源 URI。
 *
 * 形如 `minime://skill/{id}`。用自定义 scheme 而非 `file://`：
 * `file://` 会被外部客户端当作真实文件系统路径去解析甚至尝试打开，
 * 而技能并非磁盘上的普通文件（可能内置、可能来自 DB 状态覆盖）。
 */
object SkillResourceUri {
    const val SCHEME = "minime"
    const val MIME_TEXT_PLAIN = "text/plain; charset=utf-8"

    /** 由技能 id 生成 URI。 */
    fun of(skill: Skill): String = "$SCHEME://skill/${skill.id}"

    /**
     * 反解 URI → 技能 id；不属于本 scheme 或结构不符返回 null。
     *
     * 解析失败必须返回 null 而非抛出：URI 来自**外部客户端**，属不可信输入，
     * 一个畸形 URI 不该导致整个 `resources/read` 崩掉（应按「资源不存在」处理）。
     */
    fun parseId(uri: String): String? {
        val prefix = "$SCHEME://skill/"
        if (!uri.startsWith(prefix)) return null
        val id = uri.removePrefix(prefix)
        // 含 '/' 说明是多层路径或畸形 URI；id 本身不允许出现 '/'（它来自技能目录名）
        return id.ifEmpty { null }?.takeIf { '/' !in it }
    }
}

/** `PROMPT` 型技能才进 prompts；`MCP` 型是消费方，不被暴露。 */
internal fun Skill.asPromptOrNull(): Skill? = takeIf { it.type == SkillType.PROMPT && it.enabled }

/** `SCRIPT` 型技能才进 resources。 */
internal fun Skill.asResourceOrNull(): Skill? = takeIf { it.type == SkillType.SCRIPT && it.enabled }
