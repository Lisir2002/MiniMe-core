package com.mini.me_core.feature.workspace.domain.model

enum class RemoteProtocol {
    SFTP,
    FTP,
    LOCAL
}

data class RemoteConnection(
    val id: String,
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String = "",
    /** 最近一次连接成功的时间戳（毫秒），null 表示从未连接。仅用于 UI 展示。 */
    val lastConnectedAt: Long? = null,
)

/**
 * 远程挂载点（工作区）。
 *
 * @property syncDirection 同步方向："bidirectional"（双向）/ "upload_only"（仅上传）/ "download_only"（仅下载）。
 *   持久化权威位置是 KVStore `remote_mount_prefs`（按 mountId，见 RemoteRepository.mountDirectionKey
 *   与 RemoteServerViewModel.setSyncDirection）；DB 无对应列，本字段为默认值/兼容保留，
 *   新建引擎时以 KVStore 值优先（RemoteRepository.connectMount）。
 * @property conflictStrategy 冲突处理策略："remote_overwrite"（远程覆盖本地）/ "local_overwrite"（本地覆盖远程）/
 *   "skip"（跳过冲突）/ "rename"（重命名保留双方）。全局设置，持久化于 SyncSettingsRepository，
 *   由 SyncEngine 经 provider lambda 热读执行。
 */
data class RemoteMount(
    val id: String,
    val connectionId: String,
    val remotePath: String,
    val localMountPath: String,
    val isActive: Boolean = false,
    val autoConnect: Boolean = true,
    val syncDirection: String = "bidirectional",
    val conflictStrategy: String = "remote_overwrite",
    // Provide a convenient reference to the underlying connection when used in UI
    val connection: RemoteConnection? = null
)
