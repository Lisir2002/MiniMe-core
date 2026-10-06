package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * 事件 Bridge 模块
 * 提供事件订阅能力，原生侧通过 AppBridge.emitEvent 向 JS 侧发送事件
 * 本模块本身不提供调用方法，仅作为事件系统的占位模块
 */
class EventBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return BridgeResult.failure("事件模块仅支持订阅，不支持直接调用", "EVENT_ONLY")
    }

    override fun hasMethod(method: String): Boolean = false

    override fun getMethods(): List<String> = emptyList()

    companion object {
        // 预定义事件名
        const val EVENT_ON_PAUSE = "app:pause"
        const val EVENT_ON_RESUME = "app:resume"
        const val EVENT_ON_DESTROY = "app:destroy"
        const val EVENT_NETWORK_CHANGE = "network:change"
        const val EVENT_BACK_PRESSED = "app:backPressed"
    }
}
