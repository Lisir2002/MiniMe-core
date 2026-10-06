package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * Bridge 模块基类
 */
abstract class BridgeModule(
    protected val context: Context,
    val moduleName: String
) {
    /**
     * 执行 Bridge 方法
     * @param method 方法名
     * @param args 参数 JSON 对象
     * @return 执行结果
     */
    abstract suspend fun execute(method: String, args: JsonObject): BridgeResult

    /**
     * 检查方法是否存在
     */
    abstract fun hasMethod(method: String): Boolean

    /**
     * 获取所有支持的方法名列表
     */
    abstract fun getMethods(): List<String>
}
