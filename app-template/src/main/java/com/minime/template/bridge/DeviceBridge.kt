package com.minime.template.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * 设备 Bridge 模块
 * 提供设备信息、电池、亮度、音量、已安装应用等能力
 */
class DeviceBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "getInfo" -> getInfo()
            "getBatteryLevel" -> getBatteryLevel()
            "setBrightness" -> setBrightness(args)
            "getBrightness" -> getBrightness()
            "openSettings" -> openSettings(args)
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "getInfo", "getBatteryLevel", "setBrightness", "getBrightness", "openSettings"
    )

    private fun getInfo(): BridgeResult {
        val data = buildJsonObject {
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("board", Build.BOARD)
            put("hardware", Build.HARDWARE)
            put("fingerprint", Build.FINGERPRINT)
        }
        return BridgeResult.success(data)
    }

    private fun getBatteryLevel(): BridgeResult {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val data = buildJsonObject {
            put("level", level)
            put("charging", batteryManager.isCharging)
        }
        return BridgeResult.success(data)
    }

    private fun setBrightness(args: JsonObject): BridgeResult {
        val brightness = args["brightness"]?.toString()?.toIntOrNull() ?: return BridgeResult.failure("缺少 brightness 参数", "MISSING_PARAM")
        if (brightness < 0 || brightness > 255) {
            return BridgeResult.failure("brightness 范围 0-255", "INVALID_PARAM")
        }
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            return BridgeResult.success()
        } catch (e: Exception) {
            return BridgeResult.failure("设置亮度失败: ${e.message}", "SETTINGS_ERROR")
        }
    }

    private fun getBrightness(): BridgeResult {
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val data = buildJsonObject { put("brightness", brightness) }
        return BridgeResult.success(data)
    }

    private fun openSettings(args: JsonObject): BridgeResult {
        val section = args["section"]?.toString()?.trim('"') ?: "main"
        val intent = when (section) {
            "main" -> Intent(Settings.ACTION_SETTINGS)
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "app" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            "notification" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return BridgeResult.success()
    }
}
