package com.minime.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 应用配置模型，对应 assets/config.json
 * 构建引擎在打包时根据用户配置生成此文件
 */
@Serializable
data class AppConfig(
    val app: AppInfo = AppInfo(),
    val webview: WebViewConfig = WebViewConfig(),
    val permissions: PermissionConfig = PermissionConfig(),
    val bridge: BridgeConfig = BridgeConfig(),
    val splash: SplashConfig = SplashConfig()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(jsonString: String): AppConfig {
            return try {
                json.decodeFromString(serializer(), jsonString)
            } catch (e: Exception) {
                AppConfig()
            }
        }
    }
}

@Serializable
data class AppInfo(
    val name: String = "应用模版",
    val packageName: String = "com.minime.template",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val icon: String = "ic_launcher"
)

@Serializable
data class WebViewConfig(
    val entry: String = "index.html",
    val themeColor: String = "#6750A4",
    val orientation: String = "portrait",
    val immersive: Boolean = false,
    val statusBarColor: String = "#4F378B",
    val navigationBarColor: String = "#000000",
    val hardwareAccelerated: Boolean = true,
    val cacheMode: String = "default",
    val debuggable: Boolean = false,
    val spaFallback: Boolean = true
)

@Serializable
data class PermissionConfig(
    val internet: Boolean = true,
    val storage: Boolean = false,
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val bluetooth: Boolean = false,
    val nfc: Boolean = false,
    val contacts: Boolean = false,
    val calendar: Boolean = false,
    val notification: Boolean = false
)

@Serializable
data class BridgeConfig(
    val enabled: Boolean = true,
    val modules: List<String> = listOf("ui", "device", "file", "media", "location", "sensor", "connect", "data", "event")
)

@Serializable
data class SplashConfig(
    val enabled: Boolean = true,
    val duration: Int = 1500,
    val autoHide: Boolean = true,
    val backgroundColor: String = "#6750A4"
)
