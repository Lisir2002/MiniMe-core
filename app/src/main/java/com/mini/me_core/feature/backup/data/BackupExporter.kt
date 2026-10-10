package com.mini.me_core.feature.backup.data

import android.content.Context
import com.mini.me_core.core.data.DataRegistry
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.autoConnect
import com.mini.me_core.datalayer.isActive
import com.mini.me_core.datalayer.isDefault
import com.mini.me_core.datalayer.isEnabled
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.me_core.datalayer.repository.CredentialsRepository
import com.mini.me_core.datalayer.repository.SettingsRepository
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.datalayer.useFullUrl
import com.mini.me_core.datalayer.useResponseApi
import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.backup.domain.AgentMessageDto
import com.mini.me_core.feature.backup.domain.BackupCrypto
import com.mini.me_core.feature.backup.domain.BackupMetadata
import com.mini.me_core.feature.backup.domain.BackupOptions
import com.mini.me_core.feature.backup.domain.ChatSessionDto
import com.mini.me_core.feature.backup.domain.GitCredentialDto
import com.mini.me_core.feature.backup.domain.ProviderDto
import com.mini.me_core.feature.backup.domain.RemoteConnectionDto
import com.mini.me_core.feature.backup.domain.RemoteMountDto
import com.mini.me_core.feature.backup.domain.TodoItemDto
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.KeepaliveSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import com.mini.me_core.feature.settings.data.repository.SyncSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ThemeSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message as V2AgentMessage
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.mecore.datalayer.sqldelight.agent.Todo_items as V2TodoItem
import com.mini.mecore.datalayer.sqldelight.credentials.Git_credentials
import com.mini.mecore.datalayer.sqldelight.settings.Ai_providers
import com.mini.mecore.datalayer.sqldelight.workspace.Remote_connections
import com.mini.mecore.datalayer.sqldelight.workspace.Remote_mounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 备份导出方：按 [BackupOptions] 采集七域快照 → 流式序列化为 tar.gz → 口令非空则 AES-GCM 流式加密输出。
 *
 * 行为与原 BackupManagerImpl 的导出路径逐行一致：公共导出即整包 FULL、七域默认全开由
 * [BackupOptions] 自身默认值保证；大表（sessions/messages/todos）走 jsonl 分页流式，其余表与
 * DataStore 目录走 [DataRegistry] 全量段。所有依赖由 [BackupManagerImpl] 装配时传入。
 */
@Suppress("LongParameterList") // 构造函数签名保持与原注入依赖一致，逐参搬运
internal class BackupExporter(
    private val context: Context,
    private val store: BackupSnapshotStore,
    private val v2Agent: V2AgentRepository,
    private val dataRegistry: DataRegistry,
    private val settingsRepo: SettingsRepository,
    private val credentialsRepo: CredentialsRepository,
    private val v2WorkspaceRepository: V2WorkspaceRepository,
    private val mcpConfigRepository: McpConfigRepository,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val logSettingsRepository: LogSettingsRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val encryptor: CredentialEncryptor,
) {

    /** safeDao 的 suspend 版本。DAO 本身是 suspend fun，必须走 withContext(Dispatchers.IO) 的调用方已处理。 */
    private suspend inline fun <T> safeDaoSuspend(tag: String, failValue: T, crossinline block: suspend () -> T): T {
        return runCatching { block() }.onFailure {
            FileLogger.e("BackupMgr", "safeDaoSuspend[$tag] 失败，返回兜底值 failValue=$failValue", it)
        }.getOrDefault(failValue)
    }

    private fun currentSchemaVersion(): Int = AGENT_SCHEMA_VERSION

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    suspend fun export(password: CharArray?, options: BackupOptions, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val temp = store.createTempFile()
            try {
                writeTarGz(temp, options)
                val pw = password?.takeIf { it.isNotEmpty() }
                FileInputStream(temp).use { input ->
                    if (pw != null) {
                        BackupCrypto.encryptStream(input, output, pw)
                    } else {
                        input.copyTo(output)
                    }
                }
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun exportSession(sessionId: String, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val session =
                safeDaoSuspend("getSessionById", null) { v2Agent.getSessionById(sessionId) }
                    ?: error("Session not found: $sessionId")
            val temp = store.createTempFile()
            try {
                FileOutputStream(temp).use { fos ->
                    GzipCompressorOutputStream(fos).use { gz ->
                        TarArchiveOutputStream(gz).use { tar ->
                            writeMetadataEntry(tar, BackupMetadata(
                                schemaVersion = currentSchemaVersion(),
                                appVersion = appVersionName(),
                                createdAt = System.currentTimeMillis()
                            ))
                            store.writeJsonlFileEntry(tar, FILE_SESSIONS) { writer ->
                                writer.writeLine(backupJson.encodeToString(ChatSessionDto.serializer(), session.toDto()))
                            }
                            store.writeJsonlFileEntry(tar, FILE_MESSAGES) { writer ->
                                var lastTs = 0L
                                var lastId = ""
                                while (true) {
                                    val batch = safeDaoSuspend(
                                        "getMsgPageAfter_$sessionId",
                                        emptyList()
                                    ) { v2Agent.getPageBySessionAfter(sessionId, lastTs, lastId, PAGE_SIZE.toLong()) }
                                    if (batch.isEmpty()) break
                                    batch.forEach { writer.writeLine(backupJson.encodeToString(AgentMessageDto.serializer(), it.toDto())) }
                                    lastTs = batch.last().created_at
                                    lastId = batch.last().id
                                }
                            }
                            store.writeJsonlFileEntry(tar, FILE_TODOS) { writer ->
                                var lastCreatedAtMs = 0L
                                var lastId = ""
                                while (true) {
                                    val batch = safeDaoSuspend(
                                        "getTodoPageAfter_$sessionId",
                                        emptyList()
                                    ) { v2Agent.getTodoPageBySessionAfter(sessionId, lastCreatedAtMs, lastId, PAGE_SIZE.toLong()) }
                                    if (batch.isEmpty()) break
                                    batch.forEach { writer.writeLine(backupJson.encodeToString(TodoItemDto.serializer(), it.toDto())) }
                                    lastCreatedAtMs = batch.last().created_at_ms
                                    lastId = batch.last().id
                                }
                            }
                        }
                    }
                }
                FileInputStream(temp).use { it.copyTo(output) }
            } finally {
                temp.delete()
            }
        }
    }

    // ── 导出辅助 ──────────────────────────────────────────────

    private suspend fun writeTarGz(file: File, options: BackupOptions) {
        FileOutputStream(file).use { fos ->
            GzipCompressorOutputStream(fos).use { gz ->
                TarArchiveOutputStream(gz).use { tar ->
                    writeMetadataEntry(tar, buildMetadata(options))
                    if (options.chatHistory) {
                        writeSessionsJsonl(tar)
                        writeMessagesJsonl(tar)
                        writeTodosJsonl(tar)
                    }
                    // 注册表全量段：除已由 jsonl 流式导出的大表（chatSessions/messages/todos）外，
                    // 其余全部 Room 表 + DataStore 目录都走数据注册表导出（R3 全量覆盖，不再手工白名单）。
                    writeRegistryEntry(tar)
                }
            }
        }
    }

    private suspend fun writeSessionsJsonl(tar: TarArchiveOutputStream) {
        store.writeJsonlFileEntry(tar, FILE_SESSIONS) { writer ->
            var lastUpdatedAtMs = 0L
            var lastId = ""
            while (true) {
                val batch = safeDaoSuspend(
                        "getSessionPageAfter",
                        emptyList()
                    ) { v2Agent.getSessionPageAfter(lastUpdatedAtMs, lastId, PAGE_SIZE.toLong()) }
                if (batch.isEmpty()) break
                batch.forEach { writer.writeLine(backupJson.encodeToString(ChatSessionDto.serializer(), it.toDto())) }
                lastUpdatedAtMs = batch.last().updated_at
                lastId = batch.last().id
            }
        }
    }

    private suspend fun writeMessagesJsonl(tar: TarArchiveOutputStream) {
        store.writeJsonlFileEntry(tar, FILE_MESSAGES) { writer ->
            var lastTs = 0L
            var lastId = ""
            while (true) {
                val batch = safeDaoSuspend(
                        "getMsgPageAfter",
                        emptyList()
                    ) { v2Agent.getMessagePageAfter(lastTs, lastId, PAGE_SIZE.toLong()) }
                if (batch.isEmpty()) break
                batch.forEach { writer.writeLine(backupJson.encodeToString(AgentMessageDto.serializer(), it.toDto())) }
                lastTs = batch.last().created_at
                lastId = batch.last().id
            }
        }
    }

    private suspend fun writeTodosJsonl(tar: TarArchiveOutputStream) {
        store.writeJsonlFileEntry(tar, FILE_TODOS) { writer ->
            var lastCreatedAtMs = 0L
            var lastId = ""
            while (true) {
                val batch = safeDaoSuspend(
                        "getTodoPageAfter",
                        emptyList()
                    ) { v2Agent.getTodoPageAfter(lastCreatedAtMs, lastId, PAGE_SIZE.toLong()) }
                if (batch.isEmpty()) break
                batch.forEach { writer.writeLine(backupJson.encodeToString(TodoItemDto.serializer(), it.toDto())) }
                lastCreatedAtMs = batch.last().created_at_ms
                lastId = batch.last().id
            }
        }
    }

    /**
     * 写入注册表全量段（`registry.tar`）：对 [DataRegistry.snapshotAll] 结果做一次打包。
     * 大表（chat_sessions / agent_messages / todo_items）已由 jsonl 流式导出，这里排除避免体积翻倍；
     * 其余表（checkpoint/skill/wake/telemetry/t2i/credentials…）与 DataStore 目录在此全量覆盖。
     */
    private suspend fun writeRegistryEntry(tar: TarArchiveOutputStream) {
        val blobs = dataRegistry.snapshotAll().filterNot { it.key in JSONL_STREAMED_TABLES }
        if (blobs.isEmpty()) return
        val packed = dataRegistry.pack(blobs)
        store.writeTarEntry(tar, FILE_REGISTRY, packed)
        FileLogger.i(TAG, "注册表段写入 ${blobs.size} 个数据域（${packed.size} bytes）")
    }

    private suspend fun buildMetadata(options: BackupOptions): BackupMetadata = BackupMetadata(
        schemaVersion = currentSchemaVersion(),
        appVersion = appVersionName(),
        createdAt = System.currentTimeMillis(),
        providers = if (options.providers) {
            safeDaoSuspend("getAllProviders", emptyList()) { settingsRepo.listProviders().map { it.toV2Dto() } }
        } else emptyList(),
        gitCredentials = if (options.gitCredentials) {
            safeDaoSuspend("getAllGitCred", emptyList()) { credentialsRepo.listGitCredentials().map { it.toV2Dto() } }
        } else emptyList(),
        remoteConnections = if (options.remoteConnections) {
            safeDaoSuspend("getAllRemoteConn", emptyList()) { v2WorkspaceRepository.listRemoteConnections().map { it.toV2Dto() } }
        } else emptyList(),
        remoteMounts = if (options.remoteConnections) {
            safeDaoSuspend("getAllRemoteMounts", emptyList()) { v2WorkspaceRepository.listAllRemoteMounts().map { it.toV2Dto() } }
        } else emptyList(),
        mcpServers = if (options.mcpServers) mcpConfigRepository.getServers() else emptyList(),
        globalPermissionRules = if (options.permissionRules) permissionRulesRepository.getGlobalRulesOnce() else emptyList(),
        themeMode = if (options.appSettings) themeSettingsRepository.snapshot() else null,
        keepaliveEnabled = if (options.appSettings) keepaliveSettingsRepository.snapshot() else false,
        logLevel = if (options.appSettings) logSettingsRepository.snapshot() else null,
        visionProviderId = if (options.appSettings) visionModelSettingsRepository.getVisionProviderId() else "",
        visionModel = if (options.appSettings) visionModelSettingsRepository.getVisionModel() else "",
        compactionProviderId = if (options.appSettings) compactionModelSettingsRepository.getCompactionProviderId() else "",
        compactionModel = if (options.appSettings) compactionModelSettingsRepository.getCompactionModel() else "",
        syncSettings = if (options.appSettings) syncSettingsRepository.snapshot() else null
    )

    private fun writeMetadataEntry(tar: TarArchiveOutputStream, metadata: BackupMetadata) {
        val content = backupJson.encodeToString(BackupMetadata.serializer(), metadata).toByteArray(Charsets.UTF_8)
        store.writeTarEntry(tar, FILE_METADATA, content)
    }

    // ── Entity ↔ DTO 转换（导出方向：SQLDelight 行 → DTO）─────────────────────────

    private suspend fun Ai_providers.toV2Dto(): ProviderDto {
        val resolvedKey = if (encrypted_api_key.isNotEmpty()) {
            runCatching { encryptor.decrypt(encrypted_api_key) }
                .onFailure { FileLogger.w(TAG, "toDto 解密 encrypted_api_key 失败，导出空串: ${it.message}") }
                .getOrDefault("")
        } else ""
        return ProviderDto(
            id = id,
            name = name,
            type = type,
            apiKey = resolvedKey,
            baseUrl = base_url,
            defaultModel = default_model,
            isActive = isActive,
            models = models,
            selectedModel = default_model,
            isEnabled = isEnabled,
            useFullUrl = useFullUrl,
            useResponseApi = useResponseApi
        )
    }

    private suspend fun Git_credentials.toV2Dto(): GitCredentialDto {
        val resolvedToken = if (encrypted_token.isNotEmpty()) {
            runCatching { encryptor.decrypt(encrypted_token) }
                .onFailure { FileLogger.w(TAG, "toDto GitCredential 解密 encrypted_token 失败：${it.message}") }
                .getOrDefault("")
        } else ""
        return GitCredentialDto(
            id = id,
            host = host,
            username = username,
            token = resolvedToken,
            label = label,
            isDefault = isDefault,
            createdAt = created_at_ms,
            updatedAt = updated_at_ms
        )
    }

    /** 解密失败时回退为密文本身（逐字段等价于原 try/catch 内联写法；encryptor.decrypt 为 suspend）。 */
    private suspend fun String.decryptOrFallback(): String =
        try { encryptor.decrypt(this) } catch (_: Throwable) { this }

    private suspend fun Remote_connections.toV2Dto(): RemoteConnectionDto {
        val isPwd = auth_type.equals("PASSWORD", ignoreCase = true)
        val resolvedAuthData: String = if (isPwd) auth_data.decryptOrFallback() else auth_data
        val resolvedPassphrase: String? = if (!passphrase.isNullOrBlank()) passphrase.decryptOrFallback() else null
        return RemoteConnectionDto(
            id, name, protocol, host, port.toInt(), username, auth_type, resolvedAuthData, resolvedPassphrase
        )
    }

    private fun Remote_mounts.toV2Dto() =
        RemoteMountDto(id, connection_id, remote_path, local_mount_path, isActive, autoConnect)

    private fun V2AgentSession.toDto() = ChatSessionDto(
        id = id, title = title ?: "",
        createdAt = created_at, updatedAt = updated_at,
        workspacePath = workspace_path, mode = mode, reasoningEffort = reasoning_effort,
        providerId = provider_id, model = model
    )

    private fun V2AgentMessage.toDto() = AgentMessageDto(
        id, session_id, task_id, role, content, created_at, tool_calls_json, tool_call_id, tool_name, tool_args,
        is_error != 0L, reasoning, signature, attachments_json, is_compacted != 0L, is_context_summary != 0L, is_compaction_marker != 0L,
        chunk_group_id, chunk_index.toInt()
    )

    // ── V2（SQLDelight）↔ DTO 映射（导出方向）──────────────────────────

    private fun V2TodoItem.toDto() = TodoItemDto(
        id = id, sessionId = session_id, subject = subject, description = description,
        status = status, priority = priority.toInt(), order = sort_order.toInt(),
        createdAt = created_at_ms, updatedAt = updated_at_ms
    )
}
