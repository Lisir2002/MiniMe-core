package com.mini.me_core.feature.backup.data

import com.mini.me_core.core.data.DataRegistry
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.me_core.datalayer.repository.CredentialsRepository
import com.mini.me_core.datalayer.repository.SettingsRepository
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.backup.domain.AgentMessageDto
import com.mini.me_core.feature.backup.domain.BackupDecryptionException
import com.mini.me_core.feature.backup.domain.BackupIntegrity
import com.mini.me_core.feature.backup.domain.BackupMetadata
import com.mini.me_core.feature.backup.domain.BackupPreview
import com.mini.me_core.feature.backup.domain.BackupSnapshot
import com.mini.me_core.feature.backup.domain.ChatSessionDto
import com.mini.me_core.feature.backup.domain.RestoreMode
import com.mini.me_core.feature.backup.domain.RestoreStats
import com.mini.me_core.feature.backup.domain.TodoItemDto
import com.mini.me_core.feature.backup.domain.toMetadata
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.KeepaliveSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import com.mini.me_core.feature.settings.data.repository.SyncSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ThemeSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message as V2AgentMessage
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.mecore.datalayer.sqldelight.agent.Todo_items as V2TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 备份恢复方：流式解包 → 校验 schemaVersion → 分批合并/覆盖写回各数据域；
 * 另提供恢复预览（不写库）与可恢复性自检。
 *
 * 行为与原 BackupManagerImpl 的导入/预览/校验路径逐行一致：OVERWRITE 仅清空备份实际携带的域、
 * 元数据小表 upsert 主键替换、口令错误与损坏文件的异常分类文案均保持原样。
 * 所有依赖由 [BackupManagerImpl] 装配时传入；解密到临时文件/打开 tar 流复用 [BackupSnapshotStore]。
 */
@Suppress("LongParameterList", "TooManyFunctions") // 构造签名逐参保持；恢复各域内聚为私有方法，行为保持提取
internal class BackupRestorer(
    private val store: BackupSnapshotStore,
    private val v2Agent: V2AgentRepository,
    private val dataRegistry: DataRegistry,
    private val settingsRepo: SettingsRepository,
    private val credentialsRepo: CredentialsRepository,
    private val v2WorkspaceRepository: V2WorkspaceRepository,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val workspaceRepository: WorkspaceRepository,
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

    suspend fun import(input: InputStream, password: CharArray?): Result<RestoreStats> =
        import(input, password, RestoreMode.MERGE)

    suspend fun import(
        input: InputStream,
        password: CharArray?,
        mode: RestoreMode,
    ): Result<RestoreStats> {
        val pw = password?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            runCatching {
                store.openTarStream(input, pw).use { tar -> restoreFromTar(tar, mode, write = true).first }
            }.recoverCatching { e ->
                when (e) {
                    is BackupDecryptionException -> throw e
                    is IllegalStateException -> throw e
                    else -> throw IllegalArgumentException(
                        if (pw != null) {
                            "备份文件已损坏，或口令与备份文件不匹配"
                        } else {
                            "不是有效的 MiniMe-core 备份文件；如果这是加密备份，请输入导出口令"
                        },
                        e
                    )
                }
            }
        }
    }

    suspend fun preview(input: InputStream, password: CharArray?): Result<BackupPreview> {
        val pw = password?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            runCatching {
                val (stats, meta) = store.openTarStream(input, pw).use { tar -> restoreFromTar(tar, RestoreMode.MERGE, write = false) }
                BackupPreview(
                    backup = stats,
                    current = currentStatsSnapshot(),
                    appVersion = meta?.appVersion ?: "",
                    createdAt = meta?.createdAt ?: 0L,
                    encrypted = pw != null,
                )
            }.recoverCatching { e ->
                if (e is BackupDecryptionException) {
                    throw e
                } else {
                    throw IllegalArgumentException(
                        if (pw != null) "备份文件已损坏，或口令与备份文件不匹配"
                        else "不是有效的 MiniMe-core 备份文件；如果这是加密备份，请输入导出口令",
                        e
                    )
                }
            }
        }
    }

    suspend fun verifyBackup(input: InputStream, password: CharArray?): Result<BackupIntegrity> {
        val pw = password?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            runCatching {
                store.openTarStream(input, pw).use { tar ->
                    var metadataFound = false
                    var entryCount = 0
                    var entry = tar.nextEntry
                    while (entry != null) {
                        entryCount++
                        when (entry.name) {
                            FILE_METADATA -> {
                                val plain = tar.readBytes()
                                backupJson.decodeFromString(BackupMetadata.serializer(), String(plain, Charsets.UTF_8))
                                metadataFound = true
                            }
                            FILE_LEGACY_SNAPSHOT -> {
                                val plain = tar.readBytes()
                                backupJson.decodeFromString(BackupSnapshot.serializer(), String(plain, Charsets.UTF_8))
                                metadataFound = true
                            }
                            FILE_SESSIONS, FILE_MESSAGES, FILE_TODOS -> {
                                // 逐行可解析性校验：读全部字节并按行 decode 一次，损坏行抛异常即失败。
                                val serializer = if (entry.name == FILE_SESSIONS) {
                                    ChatSessionDto.serializer()
                                } else if (entry.name == FILE_MESSAGES) {
                                    AgentMessageDto.serializer()
                                } else {
                                    TodoItemDto.serializer()
                                }
                                tar.readBytes().toString(Charsets.UTF_8).lineSequence().forEach { line ->
                                    if (line.isNotBlank()) backupJson.decodeFromString(serializer, line)
                                }
                            }
                            FILE_REGISTRY -> {
                                // registry.tar 解包成功即视为可解析。
                                dataRegistry.unpack(tar.readBytes())
                            }
                            else -> tar.readBytes()
                        }
                        entry = tar.nextEntry
                    }
                    if (!metadataFound) error("缺少 metadata.json")
                    BackupIntegrity(valid = true, entryCount = entryCount)
                }
            }.recoverCatching { e ->
                BackupIntegrity(valid = false, errorReason = e.message ?: "备份校验失败")
            }
        }
    }

    /** 当前库各数据域条数快照（供恢复预览对比）。 */
    private suspend fun currentStatsSnapshot(): RestoreStats = RestoreStats(
        chatSessions = safeDaoSuspend("countSessions", 0) { v2Agent.countSessions() }.toInt(),
        providers = safeDaoSuspend("countProviders", 0) { settingsRepo.listProviders().size },
        gitCredentials = safeDaoSuspend("countGitCred", 0) { credentialsRepo.listGitCredentials().size },
        remoteConnections = safeDaoSuspend("countRemoteConn", 0) { v2WorkspaceRepository.listRemoteConnections().size },
        remoteMounts = safeDaoSuspend("countRemoteMounts", 0) { v2WorkspaceRepository.listAllRemoteMounts().size },
        mcpServers = runCatching { mcpConfigRepository.getServers().size }.getOrDefault(0),
        globalPermissionRules = runCatching { permissionRulesRepository.getGlobalRulesOnce().size }.getOrDefault(0),
    )

    // ── 导入辅助 ──────────────────────────────────────────────

    /**
     * 解包 tar：根据 [write] 决定是否写库，根据 [mode] 决定是否先清空对应数据域。
     *
     * - [write]=false（恢复预览）：只解析 metadata、逐行计数各 jsonl，**不写库**。
     * - [write]=true 且 [mode]=[RestoreMode.OVERWRITE]：仅对备份实际携带的域先清空再插入，
     *   绝不触碰备份中不存在的域（数据隔离）。
     *
     * @return (统计, 解析到的元数据；legacy 格式也会从 snapshot 还原出元数据)
     */
    private suspend fun restoreFromTar(
        tar: TarArchiveInputStream,
        mode: RestoreMode,
        write: Boolean,
    ): Pair<RestoreStats, BackupMetadata?> {
        var metadata: BackupMetadata? = null
        var stats = RestoreStats()
        var entry = tar.nextEntry
        while (entry != null) {
            when (entry.name) {
                FILE_LEGACY_SNAPSHOT -> {
                    val plain = tar.readBytes()
                    val snapshot = backupJson.decodeFromString(BackupSnapshot.serializer(), String(plain, Charsets.UTF_8))
                    checkVersion(snapshot.schemaVersion)
                    if (!write) {
                        // 预览模式：仅计数，不写库。
                        stats += RestoreStats(
                            chatSessions = snapshot.chatSessions.size,
                            agentMessages = snapshot.agentMessages.size,
                            todoItems = snapshot.todoItems.size,
                        )
                    } else {
                        stats += restoreLegacy(snapshot, mode)
                    }
                    return stats to snapshot.toMetadata()
                }
                FILE_METADATA -> {
                    val plain = tar.readBytes()
                    metadata = backupJson.decodeFromString(BackupMetadata.serializer(), String(plain, Charsets.UTF_8))
                    checkVersion(metadata.schemaVersion)
                }
                FILE_SESSIONS -> {
                    stats += RestoreStats(chatSessions = restoreSessionsDomain(tar, mode, write))
                }
                FILE_MESSAGES -> {
                    stats += RestoreStats(agentMessages = restoreMessagesDomain(tar, mode, write))
                }
                FILE_TODOS -> {
                    stats += RestoreStats(todoItems = restoreTodosDomain(tar, mode, write))
                }
                FILE_REGISTRY -> {
                    // 注册表段：预览模式只解包计数字节，不 restoreAll。
                    val blobs = dataRegistry.unpack(tar.readBytes())
                    if (write) {
                        dataRegistry.restoreAll(blobs)
                        FileLogger.i(TAG, "注册表段还原 ${blobs.size} 个数据域")
                    }
                }
            }
            entry = tar.nextEntry
        }
        val meta = metadata ?: error("不是有效的 MiniMe-core 备份文件：缺少 metadata.json")
        val metaStats = if (write) restoreMeta(meta, mode) else previewMetaStats(meta)
        return (stats + metaStats) to meta
    }

    /** 预览模式下按 metadata 各域列表长度计数（不写库），字段与原内联 else 分支逐字一致。 */
    private fun previewMetaStats(meta: BackupMetadata): RestoreStats = RestoreStats(
        providers = meta.providers.size,
        gitCredentials = meta.gitCredentials.size,
        remoteConnections = meta.remoteConnections.size,
        remoteMounts = meta.remoteMounts.size,
        mcpServers = meta.mcpServers.size,
        globalPermissionRules = meta.globalPermissionRules.size,
    )

    /** 会话域恢复：OVERWRITE 先清空，DTO 的空 workspacePath 回退为当前库路径。逻辑逐字等价原内联块。 */
    private suspend fun restoreSessionsDomain(tar: TarArchiveInputStream, mode: RestoreMode, write: Boolean): Int {
        val currentWorkspacePath = runCatching { workspaceRepository.currentPath() }
            .onFailure { FileLogger.w("BackupMgr", "读取当前 workspacePath 失败", it) }
            .getOrDefault("")
        return restoreJsonl(
            tar, ChatSessionDto.serializer(), "sessions", write,
            onClear = { if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("clearSessions", Unit) { v2Agent.deleteAllSessions() } },
            insert = { dtos ->
                safeDaoSuspend("upsertSessions", 0) {
                    val mapped = dtos.map { it.copy(workspacePath = it.workspacePath.ifBlank { currentWorkspacePath }).toV2() }
                    v2Agent.upsertAllSessions(mapped)
                    dtos.size
                }
            }
        )
    }

    /** 消息域恢复：OVERWRITE 先清空。逻辑逐字等价原内联块。 */
    private suspend fun restoreMessagesDomain(tar: TarArchiveInputStream, mode: RestoreMode, write: Boolean): Int =
        restoreJsonl(
            tar, AgentMessageDto.serializer(), "messages", write,
            onClear = { if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("clearMessages", Unit) { v2Agent.deleteAllMessages() } },
            insert = { dtos ->
                safeDaoSuspend("insertMessages", 0) {
                    val mapped = dtos.map { it.toV2() }
                    v2Agent.insertAllMessages(mapped)
                    dtos.size
                }
            }
        )

    /** 待办域恢复：OVERWRITE 先清空。逻辑逐字等价原内联块。 */
    private suspend fun restoreTodosDomain(tar: TarArchiveInputStream, mode: RestoreMode, write: Boolean): Int =
        restoreJsonl(
            tar, TodoItemDto.serializer(), "todos", write,
            onClear = { if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("clearTodos", Unit) { v2Agent.deleteAllTodos() } },
            insert = { dtos ->
                safeDaoSuspend("upsertTodos", 0) {
                    val mapped = dtos.map { it.toV2() }
                    v2Agent.upsertAllTodos(mapped)
                    dtos.size
                }
            }
        )

    /**
     * 逐行解析 jsonl 条目。[write]=false 时只计数不插入；[onClear] 在 OVERWRITE 模式插入前清空对应域。
     * 返回该文件解析到的总行数（预览）或实际写入行数（恢复）。
     */
    private suspend fun <T> restoreJsonl(
        tar: TarArchiveInputStream,
        serializer: KSerializer<T>,
        tag: String,
        write: Boolean,
        onClear: (suspend () -> Unit)? = null,
        insert: suspend (List<T>) -> Int,
    ): Int {
        val buffer = ByteArray(64 * 1024)
        val line = ByteArrayOutputStream(16 * 1024)
        val batch = ArrayList<T>(PAGE_SIZE)
        var count = 0
        if (write) onClear?.invoke()
        while (true) {
            val n = tar.read(buffer)
            if (n < 0) break
            for (i in 0 until n) {
                val b = buffer[i]
                if (b == '\n'.code.toByte()) {
                    count += endLine(line, batch, write, insert, serializer, tag)
                } else {
                    line.write(b.toInt())
                }
            }
        }
        if (line.size() > 0) {
            runCatching { backupJson.decodeFromString(serializer, line.toByteArray().toString(Charsets.UTF_8)) }
                .onFailure { FileLogger.w("BackupMgr", "restoreJsonl[$tag] 尾行解析失败，跳过", it) }
                .getOrNull()?.let { batch.add(it) }
        }
        count += tailFlush(batch, write, insert)
        return count
    }

    /**
     * 遇到换行符时结算当前累积行：空行仅复位缓冲；非空行解析一条记录并累入批次，返回本次新增条数。
     * 与原内联「if(line>0) parse/reset → if(parsed!=null) accumulate」逐字等价。
     */
    private suspend fun <T> endLine(
        line: ByteArrayOutputStream,
        batch: ArrayList<T>,
        write: Boolean,
        insert: suspend (List<T>) -> Int,
        serializer: KSerializer<T>,
        tag: String,
    ): Int {
        if (line.size() == 0) {
            line.reset()
            return 0
        }
        val parsed = runCatching {
            backupJson.decodeFromString(serializer, line.toByteArray().toString(Charsets.UTF_8))
        }.onFailure { FileLogger.w("BackupMgr", "restoreJsonl[$tag] 单行解析失败，跳过", it) }
            .getOrNull()
        line.reset()
        return if (parsed != null) accumulateParsed(parsed, batch, write, insert) else 0
    }

    /**
     * 累计一条已解析记录：批次满 [PAGE_SIZE] 时按 [write] 落库/计数并清空批次，返回本次新增条数。
     * 与原内联「batch.add → 满批 insert/计数 → clear」逻辑逐字等价。
     */
    private suspend fun <T> accumulateParsed(
        parsed: T,
        batch: ArrayList<T>,
        write: Boolean,
        insert: suspend (List<T>) -> Int,
    ): Int {
        batch.add(parsed)
        if (batch.size >= PAGE_SIZE) {
            val written = if (write) {
                runCatching { insert(batch.toList()) }.getOrDefault(0)
            } else {
                batch.size
            }
            batch.clear()
            return written
        }
        return 0
    }

    /** 收尾批次：不写库则仅计数，写库则一次性 insert；空批次返回 0。与原尾段逐字等价（不清空）。 */
    private suspend fun <T> tailFlush(
        batch: ArrayList<T>,
        write: Boolean,
        insert: suspend (List<T>) -> Int,
    ): Int {
        if (batch.isEmpty()) return 0
        return if (write) {
            runCatching { insert(batch.toList()) }.getOrDefault(0)
        } else {
            batch.size
        }
    }

    /**
     * 校验备份 schemaVersion 上界：仅拒绝「未来未知版本」。
     *
     * 历史备份可能携带旧版 schemaVersion（Room 时代 v49 / V2 六库版本）。上界取
     * 「已知最大版本」（[AGENT_SCHEMA_VERSION] 与 [LEGACY_AGENT_SCHEMA_VERSION] 的较大者），
     * 超过才拒绝，避免把可向下迁移的旧备份误拒。
     */
    private fun checkVersion(schemaVersion: Int) {
        val maxKnown = maxOf(AGENT_SCHEMA_VERSION, LEGACY_AGENT_SCHEMA_VERSION)
        if (schemaVersion > maxKnown) {
            error("备份的数据库版本 v$schemaVersion 高于本应用已知最大版本 v$maxKnown，请升级应用")
        }
    }

    /** 旧格式（单文件 snapshot.json 完整快照）还原。OVERWRITE 模式对大表先清空再插入。 */
    private suspend fun restoreLegacy(snapshot: BackupSnapshot, mode: RestoreMode): RestoreStats {
        var stats = restoreMeta(snapshot.toMetadata(), mode)
        val currentWorkspacePath = runCatching { workspaceRepository.currentPath() }
            .onFailure { FileLogger.w("BackupMgr", "读取 workspacePath 失败(legacy)", it) }
            .getOrDefault("")
        if (snapshot.chatSessions.isNotEmpty()) {
            if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("legacyClearSessions", Unit) { v2Agent.deleteAllSessions() }
            safeDaoSuspend("legacyUpsertSessions", Unit) {
                val mapped = snapshot.chatSessions.map { it.copy(workspacePath = it.workspacePath.ifBlank { currentWorkspacePath }).toV2() }
                v2Agent.upsertAllSessions(mapped)
            }
        }
        if (snapshot.agentMessages.isNotEmpty()) {
            if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("legacyClearMessages", Unit) { v2Agent.deleteAllMessages() }
            safeDaoSuspend("legacyInsertMessages", Unit) {
                val mapped = snapshot.agentMessages.map { it.toV2() }
                v2Agent.insertAllMessages(mapped)
            }
        }
        if (snapshot.todoItems.isNotEmpty()) {
            if (mode == RestoreMode.OVERWRITE) safeDaoSuspend("legacyClearTodos", Unit) { v2Agent.deleteAllTodos() }
            safeDaoSuspend("legacyUpsertTodos", Unit) {
                val mapped = snapshot.todoItems.map { it.toV2() }
                v2Agent.upsertAllTodos(mapped)
            }
        }
        return stats + RestoreStats(
            chatSessions = snapshot.chatSessions.size,
            agentMessages = snapshot.agentMessages.size,
            todoItems = snapshot.todoItems.size
        )
    }

    /** 元数据段还原（小表 + 应用设置），新旧格式共用。OVERWRITE 对小表仍走 upsert（主键替换）。 */
    @Suppress("UnusedParameter") // 恢复状态机行为保持搬迁：小表一律 upsert 主键替换，mode 形参仅为与调用方签名对齐，不分支
    private suspend fun restoreMeta(meta: BackupMetadata, mode: RestoreMode = RestoreMode.MERGE): RestoreStats {
        restoreProviders(meta)
        restoreGitCredentials(meta)
        restoreRemoteConnections(meta)
        restoreRemoteMounts(meta)
        restoreMcpServers(meta)
        restorePermissionRules(meta)
        restoreAppSettings(meta)
        return RestoreStats(
            providers = meta.providers.size,
            gitCredentials = meta.gitCredentials.size,
            remoteConnections = meta.remoteConnections.size,
            remoteMounts = meta.remoteMounts.size,
            mcpServers = meta.mcpServers.size,
            globalPermissionRules = meta.globalPermissionRules.size
        )
    }

    private suspend fun restoreProviders(meta: BackupMetadata) {
        if (meta.providers.isEmpty()) return
        safeDaoSuspend("insertProviders", Unit) {
            meta.providers.forEach { p ->
                val encrypted = if (p.apiKey.isNotEmpty()) {
                    runCatching { encryptor.encrypt(p.apiKey) }
                        .onFailure { FileLogger.w(TAG, "restoreMeta 加密 apiKey 失败，encryptedApiKey 落库为空串") }
                        .getOrDefault("")
                } else ""
                settingsRepo.saveProvider(
                    id = p.id, name = p.name, type = p.type,
                    encryptedApiKey = encrypted, baseUrl = p.baseUrl,
                    defaultModel = p.selectedModel.ifBlank { p.defaultModel },
                    isActive = p.isActive, models = p.models,
                    isEnabled = p.isEnabled, useFullUrl = p.useFullUrl, useResponseApi = p.useResponseApi,
                    temperature = 1.0, topP = 1.0, maxTokens = null,
                    apiPath = "/v1/chat/completions",
                    requestTimeout = 30, retryCount = 2,
                    fallbackProviderId = null,
                    favoriteModels = "", modelOrder = "",
                )
            }
        }
    }

    private suspend fun restoreGitCredentials(meta: BackupMetadata) {
        if (meta.gitCredentials.isEmpty()) return
        safeDaoSuspend("upsertGitCreds", Unit) {
            meta.gitCredentials.forEach { c ->
                val encrypted = if (c.token.isNotEmpty()) {
                    runCatching { encryptor.encrypt(c.token) }
                        .onFailure { FileLogger.w(TAG, "restoreMeta 加密 git token 失败") }
                        .getOrDefault("")
                } else ""
                credentialsRepo.upsertGitCredential(
                    id = c.id, host = c.host, username = c.username,
                    encryptedToken = encrypted, label = c.label,
                    isDefault = c.isDefault,
                    createdAtMs = c.createdAt, updatedAtMs = c.updatedAt,
                )
            }
        }
    }

    private suspend fun restoreRemoteConnections(meta: BackupMetadata) {
        if (meta.remoteConnections.isEmpty()) return
        safeDaoSuspend("insertRemoteConns", Unit) {
            meta.remoteConnections.forEach { c ->
                val isPwd = c.authType.equals("PASSWORD", ignoreCase = true)
                val authData = if (isPwd && c.authData.isNotEmpty()) encryptor.encrypt(c.authData) else c.authData
                val pass = c.passphrase?.takeIf { it.isNotBlank() }?.let { encryptor.encrypt(it) }
                v2WorkspaceRepository.upsertRemoteConnection(
                    id = c.id, name = c.name, protocol = c.protocol, host = c.host,
                    port = c.port.toLong(), username = c.username, authType = c.authType,
                    authData = authData, passphrase = pass,
                )
            }
        }
    }

    private suspend fun restoreRemoteMounts(meta: BackupMetadata) {
        if (meta.remoteMounts.isEmpty()) return
        safeDaoSuspend("insertRemoteMounts", Unit) {
            meta.remoteMounts.forEach { m ->
                v2WorkspaceRepository.upsertRemoteMount(
                    id = m.id, connectionId = m.connectionId, remotePath = m.remotePath,
                    localMountPath = m.localMountPath,
                    isActive = if (m.isActive) 1L else 0L,
                    autoConnect = if (m.autoConnect) 1L else 0L,
                )
            }
        }
    }

    private suspend fun restoreMcpServers(meta: BackupMetadata) {
        if (meta.mcpServers.isEmpty()) return
        runCatching {
            mcpConfigRepository.setServers(meta.mcpServers)
            mcpManager.reload()
        }.onFailure { FileLogger.w("BackupMgr", "restoreMeta: MCP 恢复失败", it) }
    }

    private suspend fun restorePermissionRules(meta: BackupMetadata) {
        if (meta.globalPermissionRules.isEmpty()) return
        runCatching { permissionRulesRepository.setGlobalRules(meta.globalPermissionRules) }
            .onFailure { FileLogger.w("BackupMgr", "restoreMeta: permissionRules 恢复失败", it) }
    }

    private suspend fun restoreAppSettings(meta: BackupMetadata) {
        runCatching { meta.themeMode?.let { themeSettingsRepository.restore(it) } }
            .onFailure { FileLogger.w("BackupMgr", "restoreMeta: theme 恢复失败", it) }
        runCatching { keepaliveSettingsRepository.restore(meta.keepaliveEnabled) }
            .onFailure { FileLogger.w("BackupMgr", "restoreMeta: keepalive 恢复失败", it) }
        runCatching { logSettingsRepository.restore(meta.logLevel) }
            .onFailure { FileLogger.w("BackupMgr", "restoreMeta: logLevel 恢复失败", it) }
        if (meta.visionProviderId.isNotBlank() || meta.visionModel.isNotBlank()) {
            runCatching { visionModelSettingsRepository.setVisionModel(meta.visionProviderId, meta.visionModel) }
                .onFailure { FileLogger.w("BackupMgr", "restoreMeta: visionModel 恢复失败", it) }
        }
        if (meta.compactionProviderId.isNotBlank() || meta.compactionModel.isNotBlank()) {
            runCatching { compactionModelSettingsRepository.setCompactionModel(meta.compactionProviderId, meta.compactionModel) }
                .onFailure { FileLogger.w("BackupMgr", "restoreMeta: compactionModel 恢复失败", it) }
        }
        runCatching { meta.syncSettings?.let { syncSettingsRepository.restore(it) } }
            .onFailure { FileLogger.w("BackupMgr", "restoreMeta: syncSettings 恢复失败", it) }
    }

    // ── V2（SQLDelight）↔ DTO 映射（恢复方向：DTO → 行）──────────────────────────

    private fun ChatSessionDto.toV2() = V2AgentSession(
        id = id, title = title, mode = mode, model = model, status = "active",
        created_at = createdAt, updated_at = updatedAt,
        workspace_path = workspacePath, reasoning_effort = reasoningEffort,
        provider_id = providerId,
        total_input_tokens = 0L, total_output_tokens = 0L, last_input_tokens = 0L
    )

    private fun AgentMessageDto.toV2() = V2AgentMessage(
        id = id, session_id = sessionId, role = role, seq = timestamp, created_at = timestamp,
        task_id = taskId, content = content, tool_calls_json = toolCallsJson,
        tool_call_id = toolCallId, tool_name = toolName, tool_args = toolArgs,
        is_error = if (isError) 1L else 0L, reasoning = reasoning, signature = signature,
        attachments_json = attachmentsJson, is_compacted = if (isCompacted) 1L else 0L,
        is_context_summary = if (isContextSummary) 1L else 0L,
        is_compaction_marker = if (isCompactionMarker) 1L else 0L,
        input_tokens = 0L, output_tokens = 0L,
        chunk_group_id = chunkGroupId, chunk_index = chunkIndex.toLong(),
        provider_id = providerId
    )

    private fun TodoItemDto.toV2() = V2TodoItem(
        id = id, session_id = sessionId, subject = subject, description = description,
        status = status, priority = priority.toLong(), sort_order = order.toLong(),
        created_at_ms = createdAt, updated_at_ms = updatedAt
    )
}
