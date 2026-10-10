package com.mini.me_core.feature.backup.data

import android.content.Context
import com.mini.me_core.core.data.DataRegistry
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.backup.domain.BackupIntegrity
import com.mini.me_core.feature.backup.domain.BackupManager
import com.mini.me_core.feature.backup.domain.BackupOptions
import com.mini.me_core.feature.backup.domain.BackupPreview
import com.mini.me_core.feature.backup.domain.RestoreMode
import com.mini.me_core.feature.backup.domain.RestoreStats
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.KeepaliveSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import com.mini.me_core.feature.settings.data.repository.SyncSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ThemeSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份编排门面。类名、包名、构造函数签名、Hilt 注解与所实现接口均与原实现保持一致，
 * 调用方（[com.mini.me_core.feature.backup.presentation.BackupViewModel]、
 * [AutoBackupManager] 等）与 Hilt 绑定零改动。
 *
 * 原约 928 行实现被按职责拆为三个同包 internal 协作者，本类只负责装配与委托：
 * - [store]：快照读写/临时文件/低层 tar 流管道（[BackupSnapshotStore]）；
 * - [exporter]：导出、七域快照采集、打包与口令流式加密输出（[BackupExporter]）；
 * - [restorer]：恢复、解包、版本/完整性校验与回写（[BackupRestorer]）。
 *
 * 所有外部可观察行为（加密范围、七域默认、流式加解密调用方式、OVERWRITE 域隔离、
 * 临时文件崩溃清理等）均原样搬入协作者，本类不做任何判定或值的改动。
 */
@Singleton
class BackupManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val logSettingsRepository: LogSettingsRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val settingsRepo: com.mini.me_core.datalayer.repository.SettingsRepository,
    private val credentialsRepo: com.mini.me_core.datalayer.repository.CredentialsRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val v2WorkspaceRepository: V2WorkspaceRepository,
    private val encryptor: CredentialEncryptor,
    private val dataRegistry: DataRegistry,
    private val v2Agent: V2AgentRepository,
) : BackupManager {

    private val store = BackupSnapshotStore(context)

    private val exporter = BackupExporter(
        context = context,
        store = store,
        v2Agent = v2Agent,
        dataRegistry = dataRegistry,
        settingsRepo = settingsRepo,
        credentialsRepo = credentialsRepo,
        v2WorkspaceRepository = v2WorkspaceRepository,
        mcpConfigRepository = mcpConfigRepository,
        permissionRulesRepository = permissionRulesRepository,
        themeSettingsRepository = themeSettingsRepository,
        keepaliveSettingsRepository = keepaliveSettingsRepository,
        logSettingsRepository = logSettingsRepository,
        visionModelSettingsRepository = visionModelSettingsRepository,
        compactionModelSettingsRepository = compactionModelSettingsRepository,
        syncSettingsRepository = syncSettingsRepository,
        encryptor = encryptor,
    )

    private val restorer = BackupRestorer(
        store = store,
        v2Agent = v2Agent,
        dataRegistry = dataRegistry,
        settingsRepo = settingsRepo,
        credentialsRepo = credentialsRepo,
        v2WorkspaceRepository = v2WorkspaceRepository,
        mcpConfigRepository = mcpConfigRepository,
        mcpManager = mcpManager,
        permissionRulesRepository = permissionRulesRepository,
        workspaceRepository = workspaceRepository,
        themeSettingsRepository = themeSettingsRepository,
        keepaliveSettingsRepository = keepaliveSettingsRepository,
        logSettingsRepository = logSettingsRepository,
        visionModelSettingsRepository = visionModelSettingsRepository,
        compactionModelSettingsRepository = compactionModelSettingsRepository,
        syncSettingsRepository = syncSettingsRepository,
        encryptor = encryptor,
    )

    override suspend fun export(password: CharArray?, options: BackupOptions, output: OutputStream) {
        exporter.export(password, options, output)
    }

    override suspend fun exportSession(sessionId: String, output: OutputStream) {
        exporter.exportSession(sessionId, output)
    }

    override suspend fun import(input: InputStream, password: CharArray?): Result<RestoreStats> =
        restorer.import(input, password)

    override suspend fun import(
        input: InputStream,
        password: CharArray?,
        mode: RestoreMode,
    ): Result<RestoreStats> = restorer.import(input, password, mode)

    override suspend fun preview(input: InputStream, password: CharArray?): Result<BackupPreview> =
        restorer.preview(input, password)

    override suspend fun verifyBackup(input: InputStream, password: CharArray?): Result<BackupIntegrity> =
        restorer.verifyBackup(input, password)
}
