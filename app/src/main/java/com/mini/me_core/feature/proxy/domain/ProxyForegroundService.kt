package com.mini.me_core.feature.proxy.domain

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mini.me_core.MainActivity
import com.mini.me_core.R
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.AndroidEntryPoint

/**
 * P2-14：代理前台保活服务。
 *
 * 代理启用时 startForeground 常驻通知「代理运行中 · 127.0.0.1:7890」，降低 mihomo 子进程被系统
 * 回收概率；点击通知回 MainActivity（用户可再进代理设置页）。关闭代理时 stopForeground + stopSelf。
 * 与 TerminalKeepaliveService 思路对齐但独立实现，不复用代码。
 */
@AndroidEntryPoint
class ProxyForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.i(TAG, "ProxyForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                ACTION_STOP -> {
                    FileLogger.i(TAG, "收到停止动作，退出前台")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                else -> {
                    // ACTION_START / START_STICKY 重建：确保前台通知就位（避免 DID_NOT_START_IN_TIME）。
                    val port = intent?.getIntExtra(EXTRA_PORT, 7890) ?: 7890
                    showForeground(port)
                }
            }
        }.onFailure {
            FileLogger.e(TAG, "onStartCommand 异常，前台兜底", it)
            runCatching { showForeground(7890) }
        }
        return START_STICKY
    }

    private fun showForeground(port: Int) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("代理运行中 · 127.0.0.1:$port")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure { FileLogger.e(TAG, "startForeground failed", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ProxyFgService"
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "proxy_service"
        private const val ACTION_START = "com.mini.me_core.proxy.START"
        private const val ACTION_STOP = "com.mini.me_core.proxy.STOP"
        private const val EXTRA_PORT = "port"

        /** 代理启用后调用：进入前台常驻。 */
        fun start(context: Context, port: Int) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            runCatching { context.startForegroundService(intent) }
                .onFailure { FileLogger.e(TAG, "start foreground failed", it) }
        }

        /** 代理关闭后调用：退出前台并自停。 */
        fun stop(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
                .onFailure { FileLogger.e(TAG, "stop service failed", it) }
        }
    }
}
