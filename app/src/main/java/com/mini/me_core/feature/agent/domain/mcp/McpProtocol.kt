package com.mini.me_core.feature.agent.domain.mcp

import com.mini.me_core.BuildConfig

/**
 * MCP 协议共享常量：协议版本、客户端/服务端身份信息。
 *
 * 客户端（[McpClient]）和服务端（[com.mini.me_core.feature.agent.domain.mcp.server.McpServerSession]）
 * 共用同一套协议版本号与 BuildConfig 版本号，避免硬编码不一致。
 */
object McpProtocol {
    const val PROTOCOL_VERSION: String = "2025-06-18"

    const val CLIENT_NAME: String = "ai-code-editor"
    val CLIENT_VERSION: String = BuildConfig.VERSION_NAME

    const val SERVER_NAME: String = "minime-mcp"
    val SERVER_VERSION: String = BuildConfig.VERSION_NAME
}
