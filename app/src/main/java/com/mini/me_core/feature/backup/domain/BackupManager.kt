package com.mini.me_core.feature.backup.domain

import java.io.InputStream
import java.io.OutputStream

/**
 * 备份编排器：从各数据源采集快照并打包，或反向解包还原。
 *
 * 导出：按 [BackupOptions] 采集 → 流式序列化（metadata.json + 各 *.jsonl）→ tar.gz 压缩 → 口令非空则 AES-GCM 流式加密。
 * 导入：口令非空则先流式解密 → 解 tar.gz → 校验 schemaVersion → 分批合并写入各数据源。
 * 全程流式，内存峰值与数据总量解耦（只持有当前分页批次）。
 */
interface BackupManager {
    /**
     * 流式生成备份写入 [output]（调用方负责打开与关闭输出流）。
     * password 为 null 或空时不加密，输出明文 tar.gz。
     */
    suspend fun export(password: CharArray?, options: BackupOptions, output: OutputStream)

    /**
     * 导出单个会话（无密码 tar.gz）：只含该会话 + 关联消息 + todo，可由 [import] 直接还原。
     * @param output 调用方负责打开与关闭输出流
     */
    suspend fun exportSession(sessionId: String, output: OutputStream)

    /**
     * 流式解包并还原备份文件。
     * @param input 调用方负责打开与关闭输入流
     * @param password 备份未加密时传 null 或空；加密文件必须提供正确口令。
     * @return 还原统计（各数据段条目数）；口令错误/格式不符/版本过高时返回失败。
     */
    suspend fun import(input: InputStream, password: CharArray?): Result<RestoreStats> =
        import(input, password, RestoreMode.MERGE)

    /**
     * 按指定 [mode] 流式解包并还原（[RestoreMode.OVERWRITE] 先清空对应数据域再插入，仅影响备份携带的域）。
     */
    suspend fun import(input: InputStream, password: CharArray?, mode: RestoreMode): Result<RestoreStats>

    /**
     * 恢复预览：只解析 [input] 统计各域条数并与当前库对比，**不写入任何数据**。
     * 供 UI 在真正恢复前向用户展示差异/冲突。
     */
    suspend fun preview(input: InputStream, password: CharArray?): Result<BackupPreview>

    /**
     * 备份可恢复性验证：重新打开已写出的 tar.gz，校验 metadata.json 存在、各 jsonl 可逐行解析。
     * 用于导出后自检（损坏自动重试，不让坏备份进入轮转）与历史列表「验证」入口。
     */
    suspend fun verifyBackup(input: InputStream, password: CharArray?): Result<BackupIntegrity>
}

/** 导出数据范围选项；未勾选的段在快照中保持空值，导入时跳过。 */
data class BackupOptions(
    val providers: Boolean = true,
    val gitCredentials: Boolean = true,
    val remoteConnections: Boolean = true,
    val chatHistory: Boolean = true,
    val mcpServers: Boolean = true,
    val permissionRules: Boolean = true,
    val appSettings: Boolean = true
)

data class RestoreStats(
    val providers: Int = 0,
    val gitCredentials: Int = 0,
    val remoteConnections: Int = 0,
    val remoteMounts: Int = 0,
    val chatSessions: Int = 0,
    val agentMessages: Int = 0,
    val todoItems: Int = 0,
    val mcpServers: Int = 0,
    val globalPermissionRules: Int = 0
) {
    operator fun plus(other: RestoreStats) = RestoreStats(
        providers = providers + other.providers,
        gitCredentials = gitCredentials + other.gitCredentials,
        remoteConnections = remoteConnections + other.remoteConnections,
        remoteMounts = remoteMounts + other.remoteMounts,
        chatSessions = chatSessions + other.chatSessions,
        agentMessages = agentMessages + other.agentMessages,
        todoItems = todoItems + other.todoItems,
        mcpServers = mcpServers + other.mcpServers,
        globalPermissionRules = globalPermissionRules + other.globalPermissionRules
    )
}
