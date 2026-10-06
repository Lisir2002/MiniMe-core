package com.minime.template.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.minime.template.ConfigManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * UI Bridge 模块
 * 提供 toast、对话框、通知、状态栏/导航栏控制、横竖屏、沉浸模式等能力
 */
class UiBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    private val configManager = ConfigManager.getInstance(context)
    private var webView: WebView? = null
    private var window: Window? = null

    fun attach(webView: WebView, window: Window) {
        this.webView = webView
        this.window = window
    }

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "toast" -> toast(args)
            "notification" -> notification(args)
            "setStatusBarColor" -> setStatusBarColor(args)
            "setNavigationBarColor" -> setNavigationBarColor(args)
            "setImmersiveMode" -> setImmersiveMode(args)
            "setOrientation" -> setOrientation(args)
            "vibrate" -> vibrate(args)
            "getDeviceInfo" -> getDeviceInfo()
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "toast", "notification", "setStatusBarColor", "setNavigationBarColor",
        "setImmersiveMode", "setOrientation", "vibrate", "getDeviceInfo"
    )

    private fun toast(args: JsonObject): BridgeResult {
        val message = args["message"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 message 参数", "MISSING_PARAM")
        val duration = if (args["long"]?.toString()?.toBoolean() == true) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, message, duration).show()
        return BridgeResult.success()
    }

    private fun notification(args: JsonObject): BridgeResult {
        if (!configManager.isPermissionDeclared("notification")) {
            return BridgeResult.permissionDenied("notification")
        }
        val title = args["title"]?.toString()?.trim('"') ?: "通知"
        val body = args["body"]?.toString()?.trim('"') ?: ""
        val channelId = "app_template_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "应用通知", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return BridgeResult.success()
    }

    private fun setStatusBarColor(args: JsonObject): BridgeResult {
        val color = args["color"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 color 参数", "MISSING_PARAM")
        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.statusBarColor = Color.parseColor(color)
        }
        return BridgeResult.success()
    }

    private fun setNavigationBarColor(args: JsonObject): BridgeResult {
        val color = args["color"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 color 参数", "MISSING_PARAM")
        window?.navigationBarColor = Color.parseColor(color)
        return BridgeResult.success()
    }

    private fun setImmersiveMode(args: JsonObject): BridgeResult {
        val immersive = args["immersive"]?.toString()?.toBoolean() ?: return BridgeResult.failure("缺少 immersive 参数", "MISSING_PARAM")
        window?.decorView?.let { decorView ->
            if (immersive) {
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
            } else {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        return BridgeResult.success()
    }

    private fun setOrientation(args: JsonObject): BridgeResult {
        val orientation = args["orientation"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 orientation 参数", "MISSING_PARAM")
        val activity = webView?.context as? android.app.Activity ?: return BridgeResult.failure("无法获取 Activity", "ACTIVITY_NOT_FOUND")
        activity.requestedOrientation = when (orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> return BridgeResult.failure("无效的 orientation 值: $orientation", "INVALID_PARAM")
        }
        return BridgeResult.success()
    }

    private fun vibrate(args: JsonObject): BridgeResult {
        val milliseconds = args["milliseconds"]?.toString()?.toLongOrNull() ?: 200
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(milliseconds, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
        return BridgeResult.success()
    }

    private fun getDeviceInfo(): BridgeResult {
        val data = buildJsonObject {
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("board", Build.BOARD)
            put("hardware", Build.HARDWARE)
        }
        return BridgeResult.success(data)
    }
}
