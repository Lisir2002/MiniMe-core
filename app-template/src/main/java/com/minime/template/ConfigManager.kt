package com.minime.template

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 配置管理器：读取 assets/config.json 并提供全局配置访问
 */
class ConfigManager private constructor(context: Context) {

    val config: AppConfig

    init {
        config = loadConfig(context)
    }

    private fun loadConfig(context: Context): AppConfig {
        return try {
            val inputStream = context.assets.open("config.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            inputStream.close()
            AppConfig.fromJson(jsonString)
        } catch (e: Exception) {
            AppConfig()
        }
    }

    /**
     * 检查指定 Bridge 模块是否启用
     */
    fun isBridgeModuleEnabled(module: String): Boolean {
        return config.bridge.enabled && config.bridge.modules.contains(module)
    }

    /**
     * 检查指定权限是否在配置中声明
     */
    fun isPermissionDeclared(permission: String): Boolean {
        return when (permission) {
            "internet" -> config.permissions.internet
            "storage" -> config.permissions.storage
            "camera" -> config.permissions.camera
            "microphone" -> config.permissions.microphone
            "location" -> config.permissions.location
            "bluetooth" -> config.permissions.bluetooth
            "nfc" -> config.permissions.nfc
            "contacts" -> config.permissions.contacts
            "calendar" -> config.permissions.calendar
            "notification" -> config.permissions.notification
            else -> false
        }
    }

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
