package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("ClassOrdering") // 行为保持搬迁：companion/属性顺序沿用原 domain 类，不重排
@Singleton
class McpConfigRepositoryImpl @Inject constructor(
    private val containerInstaller: ContainerInstaller
) : McpConfigRepository {
    private companion object {
        const val TAG = "McpConfigRepository"
        const val CONFIG_FILE = "mcp.json"
        const val DEFAULT_JSON = """{"mcpServers":{}}"""
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        val PRETTY_JSON = Json { prettyPrint = true }
    }

    private val configFile: File
        get() = File(containerInstaller.minimeDir, CONFIG_FILE)

    private val rawState = MutableStateFlow<String?>(null)
    private val mutex = Mutex()

    private suspend fun ensureLoaded() {
        if (rawState.value != null) return
        mutex.withLock {
            if (rawState.value != null) return
            rawState.value = load()
        }
    }

    private suspend fun load(): String = withContext(Dispatchers.IO) {
        val file = configFile
        if (file.isFile) {
            return@withContext runCatching { file.readText() }.getOrElse {
                FileLogger.w(TAG, "读取 $CONFIG_FILE 失败，回退默认配置: ${it.message}")
                DEFAULT_JSON
            }
        }
        writeFile(DEFAULT_JSON)
        DEFAULT_JSON
    }

    private fun writeFile(json: String) {
        val file = configFile
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    override val rawJsonFlow: Flow<String> = flow {
        ensureLoaded()
        emitAll(rawState.filterNotNull())
    }

    override val serversFlow: Flow<List<McpServerConfig>> = rawJsonFlow.map { parse(it) }

    override suspend fun getServers(): List<McpServerConfig> {
        ensureLoaded()
        return parse(rawState.value ?: DEFAULT_JSON)
    }

    override suspend fun setRawJson(json: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeFile(json) }
            rawState.value = json
        }
    }

    override suspend fun setServers(servers: List<McpServerConfig>) {
        setRawJson(serialize(servers))
    }

    override fun serialize(servers: List<McpServerConfig>): String {
        val serversObj = buildJsonObject {
            servers.forEach { server ->
                putJsonObject(server.name) {
                    if (server.isStdio) {
                        put("command", server.command)
                        if (server.args.isNotEmpty()) {
                            putJsonArray("args") { server.args.forEach { add(it) } }
                        }
                        if (server.env.isNotEmpty()) {
                            putJsonObject("env") {
                                server.env.forEach { (key, value) -> put(key, value) }
                            }
                        }
                    } else {
                        put("url", server.url ?: "")
                        if (server.headers.isNotEmpty()) {
                            putJsonObject("headers") {
                                server.headers.forEach { (key, value) -> put(key, value) }
                            }
                        }
                    }
                    put("enabled", server.enabled)
                    putToolGovernanceSets(server)
                }
            }
        }
        val root = buildJsonObject { put("mcpServers", serversObj) }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), root)
    }

    /** 从配置对象读取字符串集合；键缺失或类型不符一律回退空集（fail-soft，坏字段不拖垮整个配置）。 */
    private fun stringSet(obj: kotlinx.serialization.json.JsonObject, key: String): Set<String> =
        (obj[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }?.toSet() ?: emptySet()

    /**
     * 序列化工具治理三组集合（黑名单 / 白名单 / 灰名单）。
     *
     * 三组集合语义见 `ServerPolicy`。F3 起新增 `enabledTools`，为控制方法长度把三者
     * 收拢到一处——新增第四组时只改这里，不会再撑爆 [serialize] 的复杂度阈值。
     */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putToolGovernanceSets(
        server: McpServerConfig
    ) {
        if (server.disabledTools.isNotEmpty()) {
            putJsonArray("disabledTools") { server.disabledTools.forEach { add(it) } }
        }
        if (server.enabledTools.isNotEmpty()) {
            putJsonArray("enabledTools") { server.enabledTools.forEach { add(it) } }
        }
        if (server.requireApprovalTools.isNotEmpty()) {
            putJsonArray("requireApprovalTools") { server.requireApprovalTools.forEach { add(it) } }
        }
    }

    override fun parse(raw: String): List<McpServerConfig> {
        val root = runCatching { JSON.parseToJsonElement(raw).jsonObject }.getOrElse {
            FileLogger.w(TAG, "MCP 配置 JSON 解析失败: ${it.message}")
            return emptyList()
        }
        val servers = (root["mcpServers"] as? JsonObject) ?: return emptyList()

        return servers.mapNotNull { (name, element) ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true

            val command = (obj["command"] as? JsonPrimitive)?.contentOrNull
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull

            val disabledTools = stringSet(obj, "disabledTools")
            // F3：白名单。存量配置缺失该键即为空集 = 未启用白名单，行为不变。
            val enabledTools = stringSet(obj, "enabledTools")
            val requireApprovalTools = stringSet(obj, "requireApprovalTools")

            when {
                !command.isNullOrBlank() -> {
                    val args = (obj["args"] as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: emptyList()
                    val env = (obj["env"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(
                        name = name,
                        command = command,
                        args = args,
                        env = env,
                        enabled = enabled,
                        disabledTools = disabledTools,
                        enabledTools = enabledTools,
                        requireApprovalTools = requireApprovalTools
                    )
                }
                !url.isNullOrBlank() -> {
                    val headers = (obj["headers"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(
                        name = name,
                        url = url,
                        headers = headers,
                        enabled = enabled,
                        disabledTools = disabledTools,
                        enabledTools = enabledTools,
                        requireApprovalTools = requireApprovalTools
                    )
                }
                else -> {
                    // 既无 url 也无 command，无法识别，跳过。
                    FileLogger.i(TAG, "跳过无法识别的 MCP server（缺 url/command）: $name")
                    null
                }
            }
        }
    }
}
