package com.mini.me_core.feature.agent.domain.execution.mcp

import kotlinx.coroutines.flow.Flow

/**
 * MCP 服务器配置端口。实现（文件持久化）位于 data 层。
 */
interface McpConfigRepository {

    val rawJsonFlow: Flow<String>

    val serversFlow: Flow<List<McpServerConfig>>

    suspend fun getServers(): List<McpServerConfig>

    suspend fun setRawJson(json: String)

    suspend fun setServers(servers: List<McpServerConfig>)

    fun serialize(servers: List<McpServerConfig>): String

    fun parse(raw: String): List<McpServerConfig>
}
