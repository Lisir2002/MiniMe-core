package com.minime.template.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * 数据交互 Bridge 模块
 * 提供剪贴板、分享、打开应用等能力
 */
class DataBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "getClipboard" -> getClipboard()
            "setClipboard" -> setClipboard(args)
            "share" -> share(args)
            "openUrl" -> openUrl(args)
            "openApp" -> openApp(args)
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "getClipboard", "setClipboard", "share", "openUrl", "openApp"
    )

    private fun getClipboard(): BridgeResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        val data = buildJsonObject { put("text", text) }
        return BridgeResult.success(data)
    }

    private fun setClipboard(args: JsonObject): BridgeResult {
        val text = args["text"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 text 参数", "MISSING_PARAM")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        return BridgeResult.success()
    }

    private fun share(args: JsonObject): BridgeResult {
        val title = args["title"]?.toString()?.trim('"') ?: "分享"
        val text = args["text"]?.toString()?.trim('"') ?: ""
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return BridgeResult.success()
    }

    private fun openUrl(args: JsonObject): BridgeResult {
        val url = args["url"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 url 参数", "MISSING_PARAM")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            BridgeResult.success()
        } catch (e: Exception) {
            BridgeResult.failure("打开链接失败: ${e.message}", "OPEN_ERROR")
        }
    }

    private fun openApp(args: JsonObject): BridgeResult {
        val packageName = args["packageName"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 packageName 参数", "MISSING_PARAM")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            BridgeResult.success()
        } else {
            BridgeResult.failure("应用未安装: $packageName", "APP_NOT_FOUND")
        }
    }
}
