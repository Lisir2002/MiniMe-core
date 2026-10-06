package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * 传感器 Bridge 模块
 * 提供加速度、陀螺仪、磁力计等传感器能力（复杂功能后续逐步实现）
 */
class SensorBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "getSensorData" -> BridgeResult.failure("传感器功能开发中", "NOT_IMPLEMENTED")
            "watchSensor" -> BridgeResult.failure("传感器功能开发中", "NOT_IMPLEMENTED")
            "clearWatch" -> BridgeResult.failure("传感器功能开发中", "NOT_IMPLEMENTED")
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "getSensorData", "watchSensor", "clearWatch"
    )
}
