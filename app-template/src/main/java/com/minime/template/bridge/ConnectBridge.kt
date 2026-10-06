package com.minime.template.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * 连接性 Bridge 模块
 * 提供网络状态、蓝牙、NFC、WiFi 等能力（蓝牙/NFC/WiFi 复杂功能后续逐步实现）
 */
class ConnectBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "getNetworkStatus" -> getNetworkStatus()
            "bluetoothScan" -> BridgeResult.failure("蓝牙功能开发中", "NOT_IMPLEMENTED")
            "nfcRead" -> BridgeResult.failure("NFC功能开发中", "NOT_IMPLEMENTED")
            "wifiGetList" -> BridgeResult.failure("WiFi功能开发中", "NOT_IMPLEMENTED")
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "getNetworkStatus", "bluetoothScan", "nfcRead", "wifiGetList"
    )

    private fun getNetworkStatus(): BridgeResult {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val type = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
        val data = buildJsonObject {
            put("type", type)
            put("connected", capabilities != null)
        }
        return BridgeResult.success(data)
    }
}
