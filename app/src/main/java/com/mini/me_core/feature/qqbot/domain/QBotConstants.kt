package com.mini.me_core.feature.qqbot.domain

/**
 * QQ 机器人模块常量
 */
object QBotConstants {
    // WebSocket 服务
    const val DEFAULT_WS_PORT = 3001
    const val WS_HOST = "127.0.0.1"
    const val WS_PATH = "/onebot/v11/ws"
    const val WS_HEARTBEAT_INTERVAL_MS = 15_000L

    // 会话管理
    const val DEFAULT_CONTEXT_LENGTH = 20
    const val SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1000L // 2 小时

    // 日志
    const val LOG_BUFFER_SIZE = 500

    // 进程保活
    const val WATCHDOG_INTERVAL_MS = 5_000L
    const val MAX_RESTART_RETRIES = 5
    val RESTART_BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

    // 通知
    const val NOTIFICATION_CHANNEL_ID = "qqbot_service"
    const val NOTIFICATION_ID = 3001

    // LLBot
    const val LL_BOT_PROCESS_NAME = "llbot"
    const val LL_BOT_CONFIG_FILE = "llbot_config.json5"
    const val LL_BOT_LOG_FILE = "llbot.log"

    // 消息
    const val MAX_MESSAGE_LENGTH = 3000
    const val MESSAGE_SPLIT_LENGTH = 2800

    // QQ 头像
    const val QQ_AVATAR_BASE_URL = "https://q1.qlogo.cn/g?b=qq&nk="
    const val QQ_AVATAR_SIZE = 100

    // 数据库
    const val DB_NAME = "qqbot.db"

    // 速率限制
    const val RATE_LIMIT_PER_MINUTE = 10
    const val MAX_CONCURRENT_SESSIONS = 3

    // 通知 Action
    const val ACTION_STOP = "com.mini.me_core.qqbot.ACTION_STOP"
    const val ACTION_MANAGE = "com.mini.me_core.qqbot.ACTION_MANAGE"
}
