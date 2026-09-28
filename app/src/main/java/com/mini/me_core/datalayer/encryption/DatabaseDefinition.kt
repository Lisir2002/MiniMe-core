package com.mini.me_core.datalayer.encryption

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema

/**
 * 数据库定义接口。
 *
 * 每个数据库实现此接口，注册到 [DatabaseRegistry]。
 * 新增数据库只需实现此接口并注册，无需修改核心代码。
 */
interface DatabaseDefinition {
    /** 数据库唯一标识（用于DEK存储、日志、状态追踪） */
    val id: String

    /** 物理文件名（数据契约，不可随意更改） */
    val fileName: String

    /** SQLDelight Schema */
    val schema: SqlSchema<QueryResult.Value<Unit>>

    /** 数据库版本（用于升级检测，通常与 schema.version 一致） */
    val version: Int

    /** 是否需要加密（默认全部加密） */
    val encryptionRequired: Boolean
        get() = true

    /** 数据库描述（用于日志和调试） */
    val description: String
        get() = id
}
