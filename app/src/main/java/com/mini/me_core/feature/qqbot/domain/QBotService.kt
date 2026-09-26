package com.mini.me_core.feature.qqbot.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mini.me_core.MainActivity
import com.mini.me_core.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * QQ 机器人前台常驻服务。
 *
 * 职责：
 *  - 以 [START_STICKY] 常驻通知栏，承载 LLBot 进程、OneBot 11 反向 WS 服务端与消息处理链；
 *  - 持有 [PowerManager.PARTIAL_WAKE_LOCK] 与 [WifiManager.WIFI_MODE_FULL_HIGH_PERF]，
 *    防止 CPU / WiFi 在熄屏后休眠导致机器人离线；
 *  - 启动一个 Watchdog 协程，按 [QBotConstants.WATCHDOG_INTERVAL_MS] 周期探测 LLBot 进程，
 *    死亡时委托 [LLBotProcessManager] 自动重启（退避策略由其内部维护）；
 *  - 通过收集各单例暴露的 StateFlow 刷新通知栏，向 UI 暴露运行状态。
 *
 * 服务与 UI 之间不做跨进程回调，UI 直接观察 [LLBotProcessManager.state] 与
 * [OneBot11Server.wsState] 等单例 StateFlow。
 */
@AndroidEntryPoint
class QBotService : Service() {

    @Inject
    lateinit var llBotProcessManager: LLBotProcessManager

    @Inject
    lateinit var oneBot11Server: OneBot11Server

    @Inject
    lateinit var messageProcessor: MessageProcessor

    @Inject
    lateinit var logManager: QBotLogManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var running = false

    @Volatile
    private var watchdogJob: Job? = null

    @Volatile
    private var stateCollectorJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val messageCount = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        logManager.info(TAG, "QBotService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            QBotConstants.ACTION_STOP -> {
                logManager.info(TAG, "received ACTION_STOP")
                stopQBot()
                return START_STICKY
            }
            else -> startQBot()
        }
        return START_STICKY
    }

    /**
     * 启动机器人：建通知渠道 → 前台通知 → 启动 WS 服务端 → 接消息链 → 启动 Watchdog
     * → 持有 WakeLock / WifiLock → 拉起 LLBot 进程。
     */
    private fun startQBot() {
        if (running) {
            updateNotification()
            return
        }
        running = true
        messageCount.set(0)

        createNotificationChannel()
        startForeground(QBotConstants.NOTIFICATION_ID, buildNotification())

        // OneBot 11 WS 服务端先于 LLBot 启动，LLBot 作为客户端反向连接进来。
        oneBot11Server.start()
        oneBot11Server.onMessage = { event ->
            messageCount.incrementAndGet()
            messageProcessor.process(event)
            updateNotification()
        }

        acquireLocks()
        startWatchdog()
        observeState()

        serviceScope.launch {
            runCatching { llBotProcessManager.start() }
                .onFailure { logManager.error(TAG, "failed to start LLBot", it) }
        }
        logManager.info(TAG, "QBotService started")
    }

    /** 停止机器人：停 LLBot → 停 WS → 停 Watchdog → 释放锁 → 退出前台。 */
    private fun stopQBot() {
        running = false
        watchdogJob?.cancel()
        watchdogJob = null
        stateCollectorJob?.cancel()
        stateCollectorJob = null

        serviceScope.launch {
            runCatching { llBotProcessManager.stop() }
                .onFailure { logManager.error(TAG, "failed to stop LLBot", it) }
            oneBot11Server.onMessage = null
            oneBot11Server.stop()
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            logManager.info(TAG, "QBotService stopped")
            stopSelf()
        }
    }

    // ── 保活锁 ────────────────────────────────────────────────────────────

    /** 获取 PARTIAL_WAKE_LOCK（防止 CPU 休眠）与 FULL_HIGH_PERF WifiLock（防止 WiFi 休眠）。 */
    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "$TAG::WifiLock",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { logManager.error(TAG, "failed to acquire keep-alive locks", it) }
    }

    /** 释放 WakeLock / WifiLock。 */
    private fun releaseLocks() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
            wifiLock?.takeIf { it.isHeld }?.release()
            wifiLock = null
        }
    }

    // ── Watchdog ─────────────────────────────────────────────────────────

    /** 周期探测 LLBot 进程存活，死亡时委托 [LLBotProcessManager.start] 自动重启。 */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(QBotConstants.WATCHDOG_INTERVAL_MS)
                if (!running) break
                if (!llBotProcessManager.isAlive()) {
                    logManager.warn(TAG, "watchdog: LLBot not alive, triggering restart")
                    runCatching { llBotProcessManager.start() }
                        .onFailure { logManager.error(TAG, "watchdog restart failed", it) }
                }
            }
        }
    }

    // ── 状态观察 → 通知刷新 ──────────────────────────────────────────────

    /** 收集运行状态 / WS 连接状态，变化时刷新常驻通知。 */
    private fun observeState() {
        stateCollectorJob?.cancel()
        stateCollectorJob = serviceScope.launch {
            launch {
                llBotProcessManager.state.collect { updateNotification() }
            }
            launch {
                oneBot11Server.wsState.collect { updateNotification() }
            }
        }
    }

    // ── 通知 ──────────────────────────────────────────────────────────────

    /** 创建常驻通知渠道（低优先级，不发声）。 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                QBotConstants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.qqbot_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    /** 构建常驻通知：运行状态 + 机器人 QQ + 连接状态 + 今日消息数，带停止 / 管理按钮。 */
    private fun buildNotification(): Notification {
        val botQq = messageProcessor.botQq
        val connected = oneBot11Server.wsState.value == QBotWsState.CONNECTED
        val contentText = if (connected) {
            getString(R.string.qqbot_notification_connected, botQq)
        } else {
            getString(R.string.qqbot_notification_disconnected)
        }
        val statusText = when (llBotProcessManager.getState()) {
            QBotState.STOPPED -> getString(R.string.qqbot_status_stopped)
            QBotState.STARTING -> getString(R.string.qqbot_status_starting)
            QBotState.RUNNING -> getString(R.string.qqbot_status_running)
            QBotState.ERROR -> getString(R.string.qqbot_status_error)
            QBotState.STOPPING -> getString(R.string.qqbot_status_stopping)
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, QBotService::class.java).setAction(QBotConstants.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val manageIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, QBotConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.qqbot_notification_running))
            .setContentText("$statusText · $contentText")
            .setSubText(getString(R.string.qqbot_notification_today_messages, messageCount.get()))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(manageIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.qqbot_notification_stop),
                    stopIntent,
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.qqbot_notification_manage),
                    manageIntent,
                ).build(),
            )
            .build()
    }

    /** 用最新状态重建并刷新常驻通知。 */
    private fun updateNotification() {
        if (!running) return
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(QBotConstants.NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseLocks()
        logManager.info(TAG, "QBotService destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QBotService"

        /** 启动 QBot 前台服务（幂等）。 */
        fun start(context: Context) {
            val intent = Intent(context, QBotService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 停止 QBot 前台服务（向服务发送 ACTION_STOP）。 */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, QBotService::class.java)
                        .setAction(QBotConstants.ACTION_STOP),
                )
            }
        }
    }
}
