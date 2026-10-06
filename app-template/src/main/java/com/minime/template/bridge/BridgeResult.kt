package com.minime.template.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Bridge 统一返回结果
 */
@Serializable
data class BridgeResult(
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val errorCode: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun success(data: JsonObject? = null): BridgeResult {
            return BridgeResult(success = true, data = data)
        }

        fun failure(error: String, errorCode: String = "UNKNOWN_ERROR"): BridgeResult {
            return BridgeResult(success = false, error = error, errorCode = errorCode)
        }

        fun permissionDenied(permission: String): BridgeResult {
            return BridgeResult(
                success = false,
                error = "权限未授予: $permission",
                errorCode = "PERMISSION_DENIED"
            )
        }

        fun notSupported(): BridgeResult {
            return BridgeResult(
                success = false,
                error = "当前设备不支持此功能",
                errorCode = "NOT_SUPPORTED"
            )
        }

        fun moduleDisabled(module: String): BridgeResult {
            return BridgeResult(
                success = false,
                error = "Bridge 模块未启用: $module",
                errorCode = "MODULE_DISABLED"
            )
        }

        fun sourceNotAllowed(): BridgeResult {
            return BridgeResult(
                success = false,
                error = "调用来源不受信任，仅允许本地页面调用 Bridge",
                errorCode = "SOURCE_NOT_ALLOWED"
            )
        }
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}
