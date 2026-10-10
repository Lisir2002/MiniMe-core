package com.mini.me_core.feature.workspace.domain.repository

import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.security.HostKeyManager
import com.mini.me_core.core.security.HostKeyTrustCallback
import com.mini.me_core.core.security.HostKeyTrustDecision
import com.mini.me_core.datalayer.autoConnect
import com.mini.me_core.datalayer.isActive
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteMount
import com.mini.me_core.feature.workspace.domain.model.RemoteProtocol
import com.mini.me_core.feature.workspace.domain.remote.RemoteAuth
import com.mini.me_core.feature.workspace.domain.remote.SyncEngine
import com.mini.me_core.feature.workspace.domain.remote.ftp.CleartextFtpNotConfirmedException
import com.mini.me_core.feature.workspace.domain.remote.ftp.FtpSyncClient
import com.mini.me_core.feature.workspace.domain.remote.local.LocalSyncClient
import com.mini.me_core.feature.workspace.domain.remote.sftp.SftpSyncClient
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 待用户决策的首次连接主机密钥请求（UI 据此弹出指纹确认框）。 */
data class PendingHostKeyRequest(
    val hostKey: String,
    val fingerprintSha256: String,
)

@Singleton
class RemoteRepository @Inject constructor(
    private val v2Workspace: V2WorkspaceRepository,
    private val syncSettings: com.mini.me_core.feature.settings.data.repository.SyncSettingsRepository,
    private val hostKeyManager: HostKeyManager,
    private val encryptor: CredentialEncryptor,
    private val kv: KVStore,
) {
    private val activeEngines = ConcurrentHashMap<String, SyncEngine>()
    private val activeEngineIds = MutableStateFlow<Set<String>>(emptySet())

    // ── SSH 首连指纹确认（fail-closed：未弹 UI/用户拒绝即拒连） ──────────────────────
    private val _pendingHostKey = MutableStateFlow<PendingHostKeyRequest?>(null)
    /** 待用户确认的主机密钥请求（非空时 UI 弹确认框）。后台自动重连路径也走这里，但无 UI 时由超时拒绝。 */
    val pendingHostKey: StateFlow<PendingHostKeyRequest?> = _pendingHostKey.asStateFlow()
    private var pendingHostKeyDeferred: CompletableDeferred<HostKeyTrustDecision>? = null

    // ── 明文 FTP 风险确认（per-host 持久化，默认拒绝静默明文登录） ───────────────────
    private val _pendingCleartextFtp = MutableStateFlow<String?>(null)
    /** 待用户确认的明文 FTP 主机（非空时 UI 弹风险确认框）。 */
    val pendingCleartextFtp: StateFlow<String?> = _pendingCleartextFtp.asStateFlow()
    private var pendingCleartextDeferred: CompletableDeferred<Boolean>? = null

    init {
        // 把 HostKeyManager 的首连回调接到 UI 状态：ssh 验证线程在此同步等待用户决策。
        // 该回调由 sshj 在 transport 线程上同步调用（非挂起），故用 runBlocking 等待 UI 决策。
        hostKeyManager.trustCallback = HostKeyTrustCallback { hostKey, fingerprintSha256 ->
            // 已有未决请求时，新请求直接 REJECT（fail-closed，避免弹窗排队错乱）。
            if (pendingHostKeyDeferred != null) {
                return@HostKeyTrustCallback HostKeyTrustDecision.REJECT
            }
            val deferred = CompletableDeferred<HostKeyTrustDecision>()
            pendingHostKeyDeferred = deferred
            _pendingHostKey.value = PendingHostKeyRequest(hostKey, fingerprintSha256)
            try {
                kotlinx.coroutines.runBlocking { deferred.await() }
            } catch (e: Exception) {
                HostKeyTrustDecision.REJECT
            } finally {
                _pendingHostKey.value = null
                pendingHostKeyDeferred = null
            }
        }
    }

    /** UI：用户对首连指纹做出决策（本次信任 / 永久信任 / 拒绝）。 */
    fun resolvePendingHostKey(decision: HostKeyTrustDecision) {
        pendingHostKeyDeferred?.complete(decision)
    }

    /** UI：取消主机密钥确认弹窗（等价于拒绝）。 */
    fun cancelPendingHostKey() {
        pendingHostKeyDeferred?.complete(HostKeyTrustDecision.REJECT)
    }

    private suspend fun awaitCleartextFtpConfirmation(host: String): Boolean {
        if (kv.getBool(FTP_CLEARTEXT_NS, cleartextKey(host)) == true) return true
        // 已有未决请求 → 拒绝
        if (pendingCleartextDeferred != null) return false
        val deferred = CompletableDeferred<Boolean>()
        pendingCleartextDeferred = deferred
        _pendingCleartextFtp.value = host
        val ok = try {
            deferred.await()
        } catch (e: Exception) {
            false
        } finally {
            _pendingCleartextFtp.value = null
            pendingCleartextDeferred = null
        }
        if (ok) {
            // 用户显式确认过该主机的明文风险 → 持久化，后续不再打扰。
            kv.putBool(FTP_CLEARTEXT_NS, cleartextKey(host), true)
        }
        return ok
    }

    /** UI：用户确认/拒绝某主机的明文 FTP 风险。 */
    fun resolveCleartextFtp(confirmed: Boolean) {
        pendingCleartextDeferred?.complete(confirmed)
    }

    /**
     * 构造明文 FTP 客户端：先等待用户对该主机的明文风险确认（per-host 已确认则直接放行），
     * 未确认即抛 [CleartextFtpNotConfirmedException]（fail-closed，引导走 SFTP/FTPS 或显式确认）。
     */
    private suspend fun buildFtpClient(host: String): FtpSyncClient {
        val confirmed = awaitCleartextFtpConfirmation(host)
        if (!confirmed) {
            throw CleartextFtpNotConfirmedException(
                "明文 FTP 未获风险确认：账号口令与文件均明文传输。请确认承担明文风险，或改用 SFTP。"
            )
        }
        return FtpSyncClient(cleartextConfirmedByUser = true)
    }

    private suspend fun getConnectionRow(id: String): com.mini.mecore.datalayer.sqldelight.workspace.Remote_connections? =
        v2Workspace.getRemoteConnection(id)

    private suspend fun getMountRow(id: String): com.mini.mecore.datalayer.sqldelight.workspace.Remote_mounts? =
        v2Workspace.getRemoteMount(id)

    fun getConnections(): Flow<List<RemoteConnection>> =
        v2Workspace.observeAllRemoteConnections().map { list ->
            list.map { it.toDomainModel() }
        }

    /** 按 id 一次性读（用于冷启动 SSH 连接组装、Profile 激活时查主机配置）。 */
    suspend fun getConnectionById(id: String): RemoteConnection? {
        return getConnectionRow(id)?.toDomainModel()
    }

    /** 按 id 一次性读出带原始 RemoteAuth（含密码或私钥+passphrase）的完整配置。
     * 用于 MiniMeCore 冷启动、SettingsViewModel 切远程时组装 SSH 连接——
     * RemoteConnection.domain 只暴露 password（PASSWORD 类型），但 PRIVATE_KEY
     * 类型时还需 passphrase，这个方法直接返回 auth 密封类。
     *
     * authType 兼容：rc60 之前存小写 "password"/"key"，rc60+ 统一写大写
     * "PASSWORD"/"PRIVATE_KEY"；读取时两种都认，老用户升级不丢认证。 */
    suspend fun getAuthById(id: String): RemoteAuth? {
        val row = getConnectionRow(id) ?: return null
        return resolveAuth(row.auth_type, row.auth_data, row.passphrase)
    }

    fun getMounts(): Flow<List<RemoteMount>> = combine(
            v2Workspace.observeAllRemoteMounts(),
            activeEngineIds
        ) { list, activeIds ->
            buildList {
                for (row in list) {
                    val connRow = v2Workspace.getRemoteConnection(row.connection_id)
                    val conn = connRow?.toDomainModel()
                    add(row.toDomainModel(conn).copy(
                        isActive = activeIds.contains(row.id)
                    ))
                }
            }
        }

    /**
     * 解密凭据：优先当作密文用 CredentialEncryptor 解密，解密失败则回退为"旧版本明文"
     * 原样返回。这样老用户升级到加密版本后，所有已保存的旧明文密码/私钥 passphrase
     * 都不会失效，仍可正常登录，只是下次 save/update 时会被重新加密写入。
     */
    private suspend fun decryptCredential(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            encryptor.decrypt(raw)
        } catch (e: Exception) {
            raw // 兼容旧数据：解密失败当作明文
        }
    }

    /** 密码和私钥 passphrase 存库前一律用 Android Keystore AES-256-GCM 加密。 */
    private suspend fun encryptCredential(plain: String?): String {
        if (plain.isNullOrBlank()) return ""
        return encryptor.encrypt(plain)
    }

    suspend fun addConnection(conn: RemoteConnection, auth: RemoteAuth) {
        val authType = if (auth is RemoteAuth.Password) "PASSWORD" else "PRIVATE_KEY"
        // PASSWORD 类型：authData = 密码（加密后）；PRIVATE_KEY 类型：authData = 私钥文件路径（路径本身不需要加密，明文即可）
        val authData = when (auth) {
            is RemoteAuth.Password -> encryptCredential(auth.password)
            is RemoteAuth.PrivateKey -> auth.privateKeyPath
        }
        // passphrase 仅 PRIVATE_KEY 场景使用，同样加密（它也是凭据的一部分）。
        val passphrase = (auth as? RemoteAuth.PrivateKey)?.passphrase
        val passphraseEnc = if (passphrase.isNullOrBlank()) null else encryptCredential(passphrase)

        v2Workspace.upsertRemoteConnection(
            id = conn.id,
            name = conn.name,
            protocol = conn.protocol.name,
            host = conn.host,
            port = conn.port.toLong(),
            username = conn.username,
            authType = authType,
            authData = authData,
            passphrase = passphraseEnc,
        )
    }

    suspend fun updateConnection(conn: RemoteConnection, auth: RemoteAuth) {
        // Will overwrite existing connection ID
        addConnection(conn, auth)
    }

    suspend fun deleteConnection(id: String) {
        val row = getConnectionRow(id)
        if (row != null) {
            // Associated mounts will cascade delete in DB, but we should disconnect them
            activeEngines.keys.forEach { mountId -> disconnectMount(mountId) }
            v2Workspace.deleteRemoteConnection(id)
        }
    }

    suspend fun addMount(mount: RemoteMount) {
        v2Workspace.upsertRemoteMount(
                id = mount.id,
                connectionId = mount.connectionId,
                remotePath = mount.remotePath,
                localMountPath = mount.localMountPath,
                isActive = 0L,
                autoConnect = if (mount.autoConnect) 1L else 0L,
            )
    }

    suspend fun updateMount(mount: RemoteMount) {
        v2Workspace.updateRemoteMount(
                id = mount.id,
                connectionId = mount.connectionId,
                remotePath = mount.remotePath,
                localMountPath = mount.localMountPath,
                isActive = 0L,
                autoConnect = if (mount.autoConnect) 1L else 0L,
            )
    }

    suspend fun deleteMount(mountId: String) {
        disconnectMount(mountId)
        val row = getMountRow(mountId)
        if (row != null) {
            v2Workspace.deleteRemoteMount(mountId)
        }
    }

    suspend fun connectMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mountRow = getMountRow(mountId) ?: return@withContext Result.failure(Exception("Mount not found"))
            val connRow = getConnectionRow(mountRow.connection_id) ?: return@withContext Result.failure(Exception("Connection not found"))

            val conn = connRow.toDomainModel()
            val mount = mountRow.toDomainModel(conn)

            val client = when (conn.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient(hostKeyManager.createVerifier())
                RemoteProtocol.FTP -> buildFtpClient(conn.host)
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }

            val auth = resolveAuth(connRow.auth_type, connRow.auth_data, connRow.passphrase)

            client.connect(conn.host, conn.port, conn.username, auth)

            val engine = SyncEngine(
                mount = mount,
                connection = conn,
                syncClient = client,
                ignoredPatternsStr = syncSettings.ignoredPatterns.value,
                useGitIgnore = syncSettings.useGitIgnore.value,
                maxSyncBatchSize = syncSettings.maxSyncBatchSize.value
            )
            // 移除默认的全量下载以免覆盖本地修改，交由用户手动点击同步
            engine.startWatching()     // 增量监听
            if (conn.protocol == RemoteProtocol.LOCAL) {
                engine.uploadWorkspace()
            }

            activeEngines[mountId] = engine
            activeEngineIds.update { it + mountId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnectMount(mountId: String) {
        activeEngines[mountId]?.shutdown()
        activeEngines.remove(mountId)
        activeEngineIds.update { it - mountId }
    }

    suspend fun forceUploadMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = activeEngines[mountId] ?: return@withContext Result.failure(Exception("请先连接该挂载点"))
            engine.uploadWorkspace()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forceDownloadMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = activeEngines[mountId] ?: return@withContext Result.failure(Exception("请先连接该挂载点"))
            engine.downloadWorkspace()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        auth: RemoteAuth,
        protocol: RemoteProtocol
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = when (protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient(hostKeyManager.createVerifier())
                RemoteProtocol.FTP -> buildFtpClient(host)
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }
            client.connect(host, port, username, auth)
            client.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 按已保存连接的 id 测试连通性（加载凭据后短连短断）。 */
    suspend fun testConnectionById(connectionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connRow = getConnectionRow(connectionId) ?: return@withContext Result.failure(Exception("Connection not found"))
            val conn = connRow.toDomainModel()

            val client = when (conn.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient(hostKeyManager.createVerifier())
                RemoteProtocol.FTP -> buildFtpClient(conn.host)
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }
            val auth = resolveAuth(connRow.auth_type, connRow.auth_data, connRow.passphrase)

            client.connect(conn.host, conn.port, conn.username, auth)
            client.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listRemoteDirectories(connectionId: String, path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val connRow = getConnectionRow(connectionId) ?: return@withContext Result.failure(Exception("Connection not found"))
            val conn = connRow.toDomainModel()

            val client = when (conn.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient(hostKeyManager.createVerifier())
                RemoteProtocol.FTP -> buildFtpClient(conn.host)
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }
            val auth = resolveAuth(connRow.auth_type, connRow.auth_data, connRow.passphrase)

            client.connect(conn.host, conn.port, conn.username, auth)
            val files = client.listFiles(path).filter { it.isDirectory }.map { it.name }
            client.disconnect()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从 V2 行构造 Domain Model：密码字段已解密（只填到 RemoteConnection.password，
     * PASSWORD 类型为密码，PRIVATE_KEY 类型为空字符串，避免泄漏私钥 passphrase）。
     * 私钥 passphrase 只在真正构造 RemoteAuth 用于连接的地方 decrypt，不在 Domain 层
     * 暴露，减少内存明文驻留点。
     *
     * authType 兼容：新大写 PASSWORD/PRIVATE_KEY 与 旧小写 password/key 都认。
     */
    private suspend fun com.mini.mecore.datalayer.sqldelight.workspace.Remote_connections.toDomainModel() = RemoteConnection(
        id = id,
        name = name,
        protocol = runCatching { RemoteProtocol.valueOf(protocol) }.getOrDefault(RemoteProtocol.SFTP),
        host = host,
        port = port.toInt(),
        username = username,
        password = if (isPasswordAuth(auth_type)) decryptCredential(auth_data) else ""
    )

    private fun com.mini.mecore.datalayer.sqldelight.workspace.Remote_mounts.toDomainModel(conn: RemoteConnection?) = RemoteMount(
        id = id,
        connectionId = connection_id,
        remotePath = remote_path,
        localMountPath = local_mount_path,
        isActive = isActive,
        autoConnect = autoConnect,
        connection = conn
    )

    // ── authType 新旧值兼容辅助 ──────────────────────────────────────────

    /**
     * 密码类型判断：rc60 之前写 "password"，rc60+ 统一写 "PASSWORD"。
     * 忽略大小写，两种都认。
     */
    private fun isPasswordAuth(authType: String): Boolean =
        authType.equals("PASSWORD", ignoreCase = true)
            || authType.equals("password", ignoreCase = true)

    /**
     * 私钥类型判断：rc60 之前写 "key"，rc60+ 统一写 "PRIVATE_KEY"。
     * 忽略大小写，两种都认。
     */
    private fun isPrivateKeyAuth(authType: String): Boolean =
        authType.equals("PRIVATE_KEY", ignoreCase = true)
            || authType.equals("key", ignoreCase = true)

    /**
     * 从 Entity 的 authType/authData/passphrase 三字段构造 RemoteAuth。
     * 密码 & passphrase 走 CredentialEncryptor 解密，decrypt 失败回退旧明文。
     * authType 未知时默认当 Password 处理（兜底：至少让用户能看到密码字段再决定）。
     */
    private suspend fun resolveAuth(
        authType: String,
        authData: String,
        passphrase: String?
    ): RemoteAuth = when {
        isPrivateKeyAuth(authType) ->
            RemoteAuth.PrivateKey(authData, decryptCredential(passphrase))
        // PASSWORD 或未知类型：统一当作密码登录，避免老数据 authType 被脏写就完全登不上。
        else -> RemoteAuth.Password(decryptCredential(authData))
    }

    private companion object {
        const val FTP_CLEARTEXT_NS = "remote_ftp_cleartext"
        fun cleartextKey(host: String) = "ok_$host"
    }
}
