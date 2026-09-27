package com.mini.me_core.feature.qqbot.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * QQ 机器人运行状态
 */
enum class QBotState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
    STOPPING;

    val isActive: Boolean get() = this == STARTING || this == RUNNING
}

/**
 * QQ 登录状态（与 [QBotState] 进程生命周期正交，单独描述账号登录进展）。
 */
enum class QBotLoginState {
    /** 未登录 / 尚未尝试登录。 */
    LOGGED_OUT,

    /** 二维码已生成并展示，等待用户用 QQ 扫码。 */
    QR_SHOWN,

    /** 已扫码，等待用户在手机 QQ 上确认。 */
    SCANNING,

    /** 正在登录（提交票据 / 鉴权中）。 */
    LOGGING_IN,

    /** 需要设备锁短信验证码。 */
    SMS_CODE_REQUIRED,

    /** 已登录成功，持有 QQ 号与昵称。 */
    LOGGED_IN,

    /** 登录失败（密码错误 / 风控 / 二维码过期等）。 */
    LOGIN_FAILED,

    /** 运行中掉线（被挤下线 / 网络断开导致会话失效）。 */
    OFFLINE;
}

/**
 * 会话类型
 */
enum class QBotSessionType {
    GROUP,
    PRIVATE;

    companion object {
        fun fromString(value: String): QBotSessionType =
            entries.firstOrNull { it.name == value } ?: PRIVATE
    }
}

/**
 * 消息内容类型
 */
enum class QBotMessageType {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    SYSTEM,
    RECALL;

    companion object {
        fun fromString(value: String): QBotMessageType =
            entries.firstOrNull { it.name == value } ?: TEXT
    }
}

/**
 * 发送者角色
 */
enum class QBotSenderRole {
    OWNER,
    ADMIN,
    MEMBER,
    BOT;

    companion object {
        fun fromString(value: String): QBotSenderRole =
            entries.firstOrNull { it.name == value } ?: MEMBER
    }
}

/**
 * WebSocket 连接状态
 */
enum class QBotWsState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED;
}

/**
 * QQ 机器人配置
 */
data class QBotConfig(
    val enabled: Boolean = false,
    val wsPort: Int = 3001,
    val wsToken: String = "",
    val botQq: Long = 0,
    /** 登录方式：qrcode（扫码）/ password（密码）。 */
    val loginType: String = "qrcode",
    /** 轻量编码（Base64）存储的密码，第一阶段可选；不做强加密。 */
    val passwordEncrypted: String = "",
    val privateTriggerEnabled: Boolean = true,
    val groupAtTriggerEnabled: Boolean = true,
    val defaultModel: String = "",
    val contextLength: Int = 20,
    val llBotPath: String = ""
)

/**
 * 一个可选模型条目（聚合模型 id、所属供应商与上下文长度），供设置页下拉选择。
 */
data class QBotModelOption(
    /** 模型 id，如 "gpt-4o-mini"。 */
    val modelId: String,
    /** 供应商 id。 */
    val providerId: String,
    /** 供应商显示名。 */
    val providerName: String,
    /** 模型上下文窗口（token），未知为 0。 */
    val contextTokens: Int = 0,
) {
    /** 列表展示用标签：模型名（供应商名）。 */
    val displayLabel: String
        get() = if (providerName.isBlank()) modelId else "$modelId（$providerName）"
}

/**
 * QQ 机器人运行时状态
 */
data class QBotStatus(
    val state: QBotState = QBotState.STOPPED,
    val wsState: QBotWsState = QBotWsState.DISCONNECTED,
    val botQq: Long = 0,
    val botNickname: String = "",
    val loginState: QBotLoginState = QBotLoginState.LOGGED_OUT,
    val loginErrorMessage: String = "",
    val llBotPid: Int = -1,
    val uptimeMs: Long = 0,
    val todayMessageCount: Int = 0,
    val errorMessage: String = ""
)

/**
 * 会话数据模型
 */
data class QBotSession(
    val sessionId: String,
    val type: QBotSessionType,
    val groupId: Long? = null,
    val userId: Long? = null,
    val groupName: String? = null,
    val userName: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val unreadCount: Int = 0,
    val botQq: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val displayName: String
        get() = when (type) {
            QBotSessionType.GROUP -> groupName ?: "群${groupId ?: ""}"
            QBotSessionType.PRIVATE -> userName ?: "用户${userId ?: ""}"
        }

    val avatarUrl: String
        get() = when (type) {
            QBotSessionType.GROUP -> "https://p.qlogo.cn/gh/${groupId ?: 0}/${groupId ?: 0}/100"
            QBotSessionType.PRIVATE -> "https://q1.qlogo.cn/g?b=qq&nk=${userId ?: 0}&s=100"
        }

    companion object {
        fun groupSessionId(groupId: Long): String = "group_$groupId"
        fun privateSessionId(userId: Long): String = "private_$userId"
    }
}

/**
 * 消息数据模型
 */
data class QBotMessage(
    val messageId: String,
    val sessionId: String,
    val senderId: Long,
    val senderName: String,
    val senderAvatar: String? = null,
    val senderRole: QBotSenderRole = QBotSenderRole.MEMBER,
    val contentType: QBotMessageType = QBotMessageType.TEXT,
    val content: String = "",
    val rawMessage: String? = null,
    val botReplyId: String? = null,
    val timestamp: Long = 0,
    val isBot: Boolean = false
)

/**
 * 群成员数据模型
 */
data class QBotGroupMember(
    val groupId: Long,
    val userId: Long,
    val card: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val role: QBotSenderRole = QBotSenderRole.MEMBER,
    val lastActive: Long = 0,
    val updatedAt: Long = 0
) {
    val displayName: String
        get() = card.ifBlank { nickname.ifBlank { userId.toString() } }

    val avatarUrl: String
        get() = avatar ?: "https://q1.qlogo.cn/g?b=qq&nk=$userId&s=100"
}

/**
 * 日志条目
 */
data class QBotLogEntry(
    val timestamp: Long,
    val level: QBotLogLevel,
    val tag: String,
    val message: String
)

enum class QBotLogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * OneBot 11 消息事件
 */
@Serializable
data class OneBotMessageEvent(
    @SerialName("post_type") val postType: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("sub_type") val subType: String,
    @SerialName("message_id") val messageId: Int,
    @SerialName("user_id") val userId: Long,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("raw_message") val rawMessage: String,
    val message: List<OneBotMessageSegment> = emptyList(),
    val sender: OneBotSenderInfo = OneBotSenderInfo(0, ""),
    val time: Long = 0L
)

@Serializable
data class OneBotSenderInfo(
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val card: String = "",
    val role: String = "member"
)

@Serializable
data class OneBotMessageSegment(
    val type: String,
    val data: Map<String, String> = emptyMap()
)

/**
 * OneBot 11 API 响应
 */
@Serializable
data class OneBotApiResponse(
    val status: String,
    @SerialName("retcode") val retCode: Int,
    val data: JsonObject? = null,
    val echo: String? = null
)
