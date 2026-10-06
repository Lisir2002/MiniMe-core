package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * 媒体 Bridge 模块
 * 提供相机、相册、录音、播放等能力（复杂功能后续逐步实现）
 */
class MediaBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "pickImage" -> BridgeResult.failure("相册选图功能开发中", "NOT_IMPLEMENTED")
            "takePhoto" -> BridgeResult.failure("相机拍照功能开发中", "NOT_IMPLEMENTED")
            "recordAudio" -> BridgeResult.failure("录音功能开发中", "NOT_IMPLEMENTED")
            "scanQR" -> BridgeResult.failure("扫码功能开发中", "NOT_IMPLEMENTED")
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "pickImage", "takePhoto", "recordAudio", "scanQR"
    )
}
