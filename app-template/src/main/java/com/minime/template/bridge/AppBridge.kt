package com.minime.template.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.minime.template.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * AppBridge 总入口
 *
 * 安全设计：
 * 1. 来源校验：仅允许本地虚拟域名（https://appassets.androidplatform.net/）的顶层页面调用
 * 2. 能力分级：敏感能力需在 config.json 中显式声明权限
 * 3. 模块开关：Bridge 模块可在 config.json 中单独启用/禁用
 * 4. 异步回调：所有调用通过 callbackId 异步返回结果，避免阻塞主线程
 */
class AppBridge(
    private val context: Context,
    private val webView: WebView
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val configManager = ConfigManager.getInstance(context)

    private val modules: MutableMap<String, BridgeModule> by lazy {
        mutableMapOf<String, BridgeModule>().apply {
            registerModule(UiBridge(context, "ui"))
            registerModule(DeviceBridge(context, "device"))
            registerModule(FileBridge(context, "file"))
            registerModule(MediaBridge(context, "media"))
            registerModule(LocationBridge(context, "location"))
            registerModule(SensorBridge(context, "sensor"))
            registerModule(ConnectBridge(context, "connect"))
            registerModule(DataBridge(context, "data"))
            registerModule(EventBridge(context, "event"))
        }
    }

    private fun registerModule(module: BridgeModule) {
        modules[module.moduleName] = module
    }

    /**
     * JS 侧统一调用入口
     * @param requestJson JSON 格式：{"module":"ui","method":"toast","args":{...},"callbackId":"xxx"}
     */
    @JavascriptInterface
    fun call(requestJson: String) {
        // 来源校验：仅允许本地虚拟域名
        if (!isSourceAllowed()) {
            val result = BridgeResult.sourceNotAllowed()
            // 来源不允许时不回调（防止泄露信息给不可信来源）
            return
        }

        val request = try {
            json.parseToJsonElement(requestJson).jsonObject
        } catch (e: Exception) {
            return
        }

        val moduleName = request["module"]?.toString()?.trim('"') ?: return
        val method = request["method"]?.toString()?.trim('"') ?: return
        val callbackId = request["callbackId"]?.toString()?.trim('"') ?: return
        val args = (request["args"] as? JsonObject) ?: JsonObject(emptyMap())

        // 模块开关检查
        if (!configManager.isBridgeModuleEnabled(moduleName)) {
            invokeCallback(callbackId, BridgeResult.moduleDisabled(moduleName))
            return
        }

        val module = modules[moduleName]
        if (module == null) {
            invokeCallback(callbackId, BridgeResult.failure("模块不存在: $moduleName", "MODULE_NOT_FOUND"))
            return
        }

        if (!module.hasMethod(method)) {
            invokeCallback(callbackId, BridgeResult.failure("方法不存在: $moduleName.$method", "METHOD_NOT_FOUND"))
            return
        }

        // 异步执行，避免阻塞 JS 线程
        scope.launch(Dispatchers.IO) {
            val result = try {
                module.execute(method, args)
            } catch (e: Exception) {
                BridgeResult.failure("执行异常: ${e.message}", "EXECUTION_ERROR")
            }
            invokeCallback(callbackId, result)
        }
    }

    /**
     * 来源校验：检查当前 WebView 加载的是否为本地虚拟域名
     */
    private fun isSourceAllowed(): Boolean {
        val url = webView.url ?: return false
        return url.startsWith(LOCAL_VIRTUAL_DOMAIN)
    }

    /**
     * 调用 JS 侧回调
     */
    private fun invokeCallback(callbackId: String, result: BridgeResult) {
        val script = "if(window.__bridge_callback__){window.__bridge_callback__('$callbackId',${result.toJson()});}"
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * 向 JS 侧发送事件
     */
    fun emitEvent(eventName: String, data: JsonObject? = null) {
        val dataJson = data?.toString() ?: "{}"
        val script = "if(window.__bridge_event__){window.__bridge_event__('$eventName',$dataJson);}"
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    companion object {
        const val LOCAL_VIRTUAL_DOMAIN = "https://appassets.androidplatform.net/"
        const val BRIDGE_INTERFACE_NAME = "AppBridgeNative"
    }
}
