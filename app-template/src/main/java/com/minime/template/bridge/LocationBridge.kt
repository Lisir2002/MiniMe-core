package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * 位置 Bridge 模块
 * 提供 GPS 定位、网络定位等能力（复杂功能后续逐步实现）
 */
class LocationBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "getCurrentPosition" -> BridgeResult.failure("定位功能开发中", "NOT_IMPLEMENTED")
            "watchPosition" -> BridgeResult.failure("持续定位功能开发中", "NOT_IMPLEMENTED")
            "clearWatch" -> BridgeResult.failure("持续定位功能开发中", "NOT_IMPLEMENTED")
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "getCurrentPosition", "watchPosition", "clearWatch"
    )
}
