package com.minime.template.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

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

    // 权限申请异步等待
    private var permissionDeferred: CompletableDeferred<Map<String, Boolean>>? = null

    /**
     * 由 MainActivity 的 onRequestPermissionsResult 调用，通知权限申请结果
     */
    fun onPermissionResult(permissions: Array<out String>, grantResults: IntArray) {
        val result = permissions.mapIndexed { index, permission ->
            permission to (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED)
        }.toMap()
        permissionDeferred?.complete(result)
        permissionDeferred = null
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
            "checkPermission" -> checkPermission(args)
            "checkPermissions" -> checkPermissions(args)
            "requestPermission" -> requestPermission(args)
            "requestPermissions" -> requestPermissions(args)
            "openAppSettings" -> openAppSettings()
            "getAllPermissions" -> getAllPermissions()
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "toast", "notification", "setStatusBarColor", "setNavigationBarColor",
        "setImmersiveMode", "setOrientation", "vibrate", "getDeviceInfo",
        "checkPermission", "checkPermissions", "requestPermission", "requestPermissions",
        "openAppSettings", "getAllPermissions"
    )

    private fun toast(args: JsonObject): BridgeResult {
        val message = args["message"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 message 参数", "MISSING_PARAM")
        val duration = if (args["long"]?.toString()?.toBoolean() == true) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        // Toast 必须在主线程显示，Bridge 方法在 IO 线程执行，需切换
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
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

    // ========== 权限相关方法 ==========

    private fun checkPermission(args: JsonObject): BridgeResult {
        val permission = args["permission"]?.toString()?.trim('"')
            ?: return BridgeResult.failure("缺少 permission 参数", "MISSING_PARAM")
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val data = buildJsonObject {
            put("permission", permission)
            put("granted", granted)
        }
        return BridgeResult.success(data)
    }

    private fun checkPermissions(args: JsonObject): BridgeResult {
        val permissionsArray = args["permissions"]?.jsonArray
            ?: return BridgeResult.failure("缺少 permissions 数组", "MISSING_PARAM")
        val permissions = permissionsArray.map { it.toString().trim('"') }
        val result = permissions.associateWith { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        val data = buildJsonObject {
            result.forEach { (permission, granted) ->
                put(permission, granted)
            }
        }
        return BridgeResult.success(data)
    }

    private suspend fun requestPermission(args: JsonObject): BridgeResult {
        val permission = args["permission"]?.toString()?.trim('"')
            ?: return BridgeResult.failure("缺少 permission 参数", "MISSING_PARAM")
        return requestPermissionsInternal(listOf(permission))
    }

    private suspend fun requestPermissions(args: JsonObject): BridgeResult {
        val permissionsArray = args["permissions"]?.jsonArray
            ?: return BridgeResult.failure("缺少 permissions 数组", "MISSING_PARAM")
        val permissions = permissionsArray.map { it.toString().trim('"') }
        return requestPermissionsInternal(permissions)
    }

    private suspend fun requestPermissionsInternal(permissions: List<String>): BridgeResult {
        val activity = webView?.context as? android.app.Activity
            ?: return BridgeResult.failure("无法获取 Activity", "ACTIVITY_NOT_FOUND")

        // 检查是否所有权限都已授予
        val allGranted = permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val data = buildJsonObject {
                permissions.forEach { put(it, true) }
            }
            return BridgeResult.success(data)
        }

        // 创建 deferred 等待结果
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        permissionDeferred = deferred

        // 在主线程申请权限
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            androidx.core.app.ActivityCompat.requestPermissions(
                activity, permissions.toTypedArray(), PERMISSION_REQUEST_CODE
            )
        }

        // 等待权限申请结果（最多等待 30 秒）
        val result = try {
            withTimeoutOrNull(30_000) { deferred.await() }
                ?: permissions.associateWith { false }
        } catch (e: Exception) {
            permissions.associateWith { false }
        }

        val data = buildJsonObject {
            result.forEach { (permission, granted) ->
                put(permission, granted)
            }
        }
        return BridgeResult.success(data)
    }

    private fun openAppSettings(): BridgeResult {
        val activity = webView?.context as? android.app.Activity
            ?: return BridgeResult.failure("无法获取 Activity", "ACTIVITY_NOT_FOUND")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return BridgeResult.success()
    }

    private fun getAllPermissions(): BridgeResult {
        // 从 PackageInfo 获取所有已声明的权限
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val grantResults = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        val normalPermissions = mutableListOf<String>()
        val grantedPermissions = mutableListOf<String>()
        val deniedPermissions = mutableListOf<String>()

        requestedPermissions.forEachIndexed { index, permission ->
            val flag = grantResults.getOrNull(index) ?: 0
            val granted = (flag and PackageManager.PERMISSION_GRANTED) != 0
            if (granted) {
                grantedPermissions.add(permission)
            } else {
                // 判断是否是危险权限（需要运行时申请）
                val isDangerous = try {
                    context.packageManager.getPermissionInfo(permission, 0).protectionLevel ==
                        android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                } catch (_: Exception) {
                    false
                }
                if (isDangerous) {
                    deniedPermissions.add(permission)
                } else {
                    normalPermissions.add(permission)
                }
            }
        }

        val data = buildJsonObject {
            put("total", requestedPermissions.size)
            put("granted", grantedPermissions.size)
            put("denied", deniedPermissions.size)
            put("normal", normalPermissions.size)
            put("grantedList", JsonArray(grantedPermissions.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            put("deniedList", JsonArray(deniedPermissions.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            put("normalList", JsonArray(normalPermissions.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        return BridgeResult.success(data)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
