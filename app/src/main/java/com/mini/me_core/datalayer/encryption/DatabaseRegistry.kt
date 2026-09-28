package com.mini.me_core.datalayer.encryption

/**
 * 数据库注册表。
 *
 * 应用启动时注册所有数据库，运行时通过id查询。
 * 支持插件动态注册（预留扩展口）。
 */
class DatabaseRegistry {
    private val definitions = mutableMapOf<String, DatabaseDefinition>()
    private val lock = Any()

    /** 注册数据库定义（启动时调用，或插件加载时调用） */
    fun register(definition: DatabaseDefinition) {
        synchronized(lock) {
            require(!definitions.containsKey(definition.id)) {
                "数据库重复注册: ${definition.id}"
            }
            definitions[definition.id] = definition
        }
    }

    /** 根据id获取数据库定义 */
    fun get(id: String): DatabaseDefinition? =
        synchronized(lock) { definitions[id] }

    /** 获取所有已注册数据库 */
    fun getAll(): List<DatabaseDefinition> =
        synchronized(lock) { definitions.values.toList() }

    /** 检查数据库是否已注册 */
    fun isRegistered(id: String): Boolean =
        synchronized(lock) { definitions.containsKey(id) }
}
