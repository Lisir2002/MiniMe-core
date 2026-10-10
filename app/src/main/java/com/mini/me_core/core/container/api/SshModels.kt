package com.mini.me_core.core.container.api

import com.mini.me_core.feature.workspace.domain.remote.RemoteAuth

/** 远程 SSH 连接状态，供 UI 指示器与工作区初始化时序判断。 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/** 把 SSH 连接异常翻译成友好提示，供工具层回显给 AI/用户。 */
fun friendlySshError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        "ECONNREFUSED" in msg || "Connection refused" in msg ->
            "无法连接到 SSH 服务器（连接被拒绝），请确认远程服务器已启动且端口正确"
        "UnknownHost" in msg || "unknown host" in msg ->
            "无法连接到 SSH 服务器（未知主机），请检查主机地址"
        "Auth fail" in msg || "authentication" in msg ->
            "SSH 认证失败，请检查用户名和密码"
        "Network is unreachable" in msg ->
            "网络不可达，请检查网络连接"
        "SocketTimeout" in msg || "timed out" in msg ->
            "连接 SSH 服务器超时，请检查网络或服务器状态"
        "SSH 未连接" in msg ->
            "SSH 未连接，请等待连接恢复或在设置中检查配置"
        else -> "SSH 连接失败: ${e.message ?: "未知错误"}"
    }
}

/** 远程 SSH 连接配置。 */
data class RemoteConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: RemoteAuth,
    /** 远程服务器上的工作区根路径（AI 的 ~/workspace 映射到此路径）。 */
    val remoteWorkspacePath: String
)
