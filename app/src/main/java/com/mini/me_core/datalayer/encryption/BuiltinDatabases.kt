package com.mini.me_core.datalayer.encryption

import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.InfraDb
import com.mini.mecore.datalayer.sqldelight.SettingsDb
import com.mini.mecore.datalayer.sqldelight.T2iDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb

/**
 * 6个内置数据库定义。
 * 文件名是数据契约，绝对不可更改。
 */

object AgentDatabase : DatabaseDefinition {
    override val id = "agent"
    override val fileName = "minime_agent_v3.db"
    override val schema = AgentDb.Schema
    override val version: Int = AgentDb.Schema.version.toInt()
    override val description = "会话与Agent工作流数据库"
}

object CredentialsDatabase : DatabaseDefinition {
    override val id = "credentials"
    override val fileName = "minime_credentials_v2.db"
    override val schema = CredentialsDb.Schema
    override val version: Int = CredentialsDb.Schema.version.toInt()
    override val description = "凭据与密钥数据库"
}

object SettingsDatabase : DatabaseDefinition {
    override val id = "settings"
    override val fileName = "minime_settings_v2.db"
    override val schema = SettingsDb.Schema
    override val version: Int = SettingsDb.Schema.version.toInt()
    override val description = "应用设置数据库"
}

object WorkspaceDatabase : DatabaseDefinition {
    override val id = "workspace"
    override val fileName = "minime_workspace_v2.db"
    override val schema = WorkspaceDb.Schema
    override val version: Int = WorkspaceDb.Schema.version.toInt()
    override val description = "工作区与主机配置数据库"
}

object T2iDatabase : DatabaseDefinition {
    override val id = "t2i"
    override val fileName = "minime_t2i_v2.db"
    override val schema = T2iDb.Schema
    override val version: Int = T2iDb.Schema.version.toInt()
    override val description = "T2I生成任务数据库"
}

object InfraDatabase : DatabaseDefinition {
    override val id = "infra"
    override val fileName = "minime_infra_v2.db"
    override val schema = InfraDb.Schema
    override val version: Int = InfraDb.Schema.version.toInt()
    override val description = "基础设施KV/文档/队列数据库"
}

/**
 * 注册所有内置数据库到注册表。
 * 在应用启动时（通过Hilt）调用。
 */
fun registerBuiltinDatabases(registry: DatabaseRegistry) {
    registry.register(AgentDatabase)
    registry.register(CredentialsDatabase)
    registry.register(SettingsDatabase)
    registry.register(WorkspaceDatabase)
    registry.register(T2iDatabase)
    registry.register(InfraDatabase)
}
