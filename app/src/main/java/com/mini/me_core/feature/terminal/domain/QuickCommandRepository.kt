package com.mini.me_core.feature.terminal.domain

import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 一条快捷命令。 */
@Serializable
data class QuickCommand(
    val name: String,
    val command: String,
    val category: String,
    val builtin: Boolean = false,
)

private const val NS = "quick_commands"
private const val KEY_CUSTOM = "custom_list"
private const val KEY_RECENT = "recent_list"

/**
 * 快捷命令仓库：内置 6 分类命令 + 用户自定义命令持久化（KVStore JSON）。
 */
@Singleton
class QuickCommandRepository @Inject constructor(
    private val kv: KVStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val builtin: List<QuickCommand> = listOf(
        // 文件操作
        QuickCommand("列出文件", "ls -la", "file"),
        QuickCommand("回家目录", "cd ~", "file"),
        QuickCommand("当前路径", "pwd", "file"),
        QuickCommand("新建目录", "mkdir ", "file"),
        // 系统信息
        QuickCommand("磁盘占用", "df -h", "system"),
        QuickCommand("内存", "free -m", "system"),
        QuickCommand("系统信息", "uname -a", "system"),
        QuickCommand("进程", "ps aux", "system"),
        // 包管理
        QuickCommand("更新源", "apk update", "package"),
        QuickCommand("安装包", "apk add ", "package"),
        QuickCommand("搜索包", "apk search ", "package"),
        // 网络
        QuickCommand("下载", "curl -O ", "network"),
        QuickCommand("Ping", "ping -c 4 ", "network"),
        // Git
        QuickCommand("状态", "git status", "git"),
        QuickCommand("拉取", "git pull", "git"),
        QuickCommand("日志", "git log --oneline -10", "git"),
        QuickCommand("提交", "git commit -m \"\"", "git"),
        // 开发
        QuickCommand("Python3", "python3", "dev"),
        QuickCommand("Node", "node", "dev"),
        QuickCommand("npm 安装", "npm install", "dev"),
    )

    val customCommands: Flow<List<QuickCommand>> =
        kv.observeString(NS, KEY_CUSTOM).map { raw ->
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<QuickCommand>>(raw) }.getOrDefault(emptyList())
        }

    val recentCommands: Flow<List<QuickCommand>> =
        kv.observeString(NS, KEY_RECENT).map { raw ->
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<QuickCommand>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun saveCustom(list: List<QuickCommand>) {
        kv.putString(NS, KEY_CUSTOM, json.encodeToString(list))
    }

    suspend fun pushRecent(cmd: QuickCommand) {
        val current = runCatching {
            kv.getString(NS, KEY_RECENT)?.let { json.decodeFromString<List<QuickCommand>>(it) }
        }.getOrNull() ?: emptyList()
        val next = (listOf(cmd) + current.filter { it.command != cmd.command }).take(5)
        kv.putString(NS, KEY_RECENT, json.encodeToString(next))
    }

    val categories = listOf("file", "system", "package", "network", "git", "dev")
}
