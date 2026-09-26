package com.mini.me_core.feature.qqbot.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.me_core.datalayer.engine.DatabaseDriverFactory
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.feature.qqbot.domain.QBotGroupMember
import com.mini.me_core.feature.qqbot.domain.QBotMessage
import com.mini.me_core.feature.qqbot.domain.QBotMessageType
import com.mini.me_core.feature.qqbot.domain.QBotSenderRole
import com.mini.me_core.feature.qqbot.domain.QBotSession
import com.mini.me_core.feature.qqbot.domain.QBotSessionType
import com.mini.mecore.datalayer.sqldelight.QBotDb
import com.mini.mecore.datalayer.sqldelight.qqbot.Qqbot_group_member
import com.mini.mecore.datalayer.sqldelight.qqbot.Qqbot_message
import com.mini.mecore.datalayer.sqldelight.qqbot.Qqbot_session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 机器人数据仓库（data 层门面）。
 *
 * 封装 SQLDelight [QBotDb] 的全部查询操作，对上（domain / presentation）只暴露
 * [QBotModels] 中的领域数据类，不泄露 SQLDelight 生成的下划线命名实体。
 *
 * 驱动通过注入的 [DatabaseDriverFactory] 按 [LibName.QQBOT] 创建，库文件独立于
 * 程序内 AI 对话的 agent 库，聊天记录物理隔离。所有写操作在 [Dispatchers.IO] 上执行。
 *
 * @constructor 由 Hilt 注入 [DatabaseDriverFactory]，内部据此构建 [QBotDb]。
 */
@Singleton
class QBotRepository @Inject constructor(
    factory: DatabaseDriverFactory,
) {

    private val db: QBotDb = QBotDb(factory.create(LibName.QQBOT))
    private val q = db.qqbotQueries

    // ── 会话 ────────────────────────────────────────────────────────────

    /** 观察全部会话，按更新时间倒序。 */
    fun getAllSessions(): Flow<List<QBotSession>> =
        q.selectAllSessions().asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map(::mapSession)
        }

    /** 观察指定类型的会话（群聊 / 私聊），按更新时间倒序。 */
    fun getSessionsByType(type: QBotSessionType): Flow<List<QBotSession>> =
        q.selectSessionsByType(type.name).asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map(::mapSession)
        }

    /** 按 id 一次性读取会话，不存在返回 null。 */
    suspend fun getSessionById(sessionId: String): QBotSession? =
        withContext(Dispatchers.IO) {
            q.selectSessionById(sessionId).executeAsOneOrNull()?.let(::mapSession)
        }

    /** 插入或更新会话（已存在时合并名称 / 最后消息 / 未读数，保留 created_at）。 */
    suspend fun upsertSession(session: QBotSession) = withContext(Dispatchers.IO) {
        q.upsertSession(
            session_id = session.sessionId,
            type = session.type.name,
            group_id = session.groupId,
            user_id = session.userId,
            group_name = session.groupName,
            user_name = session.userName,
            last_message = session.lastMessage,
            last_message_time = session.lastMessageTime,
            unread_count = session.unreadCount.toLong(),
            bot_qq = session.botQq,
            created_at = session.createdAt,
            updated_at = session.updatedAt,
        )
    }

    /** 更新会话的最后一条消息预览与时间戳。 */
    suspend fun updateLastMessage(sessionId: String, lastMessage: String?, timestamp: Long) =
        withContext(Dispatchers.IO) {
            q.updateSessionLastMessage(
                last_message = lastMessage,
                last_message_time = timestamp,
                updated_at = timestamp,
                session_id = sessionId,
            )
        }

    /** 未读消息数 +1。 */
    suspend fun incrementUnread(sessionId: String) = withContext(Dispatchers.IO) {
        q.incrementUnreadCount(sessionId)
    }

    /** 未读消息数清零。 */
    suspend fun resetUnread(sessionId: String) = withContext(Dispatchers.IO) {
        q.resetUnreadCount(sessionId)
    }

    /** 删除会话，并在同一事务内删除其全部消息。 */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        db.transaction {
            q.deleteMessagesBySession(sessionId)
            q.deleteSession(sessionId)
        }
    }

    // ── 消息 ────────────────────────────────────────────────────────────

    /**
     * 观察某会话的消息流。
     *
     * @param limit 非空时只取最近的 [limit] 条（底层按时间倒序拉取后翻转为时间正序）；
     *              为空时返回会话全部消息（时间正序）。
     */
    fun getMessagesBySession(sessionId: String, limit: Long? = null): Flow<List<QBotMessage>> {
        val rows = if (limit != null) {
            q.selectMessagesBySessionLimit(sessionId, limit)
                .asFlow().mapToList(Dispatchers.IO)
                .map { it.reversed() }
        } else {
            q.selectMessagesBySession(sessionId)
                .asFlow().mapToList(Dispatchers.IO)
        }
        return rows.map { list -> list.map(::mapMessage) }
    }

    /** 按 message_id 一次性读取消息，不存在返回 null。 */
    suspend fun getMessageById(messageId: String): QBotMessage? =
        withContext(Dispatchers.IO) {
            q.selectMessageById(messageId).executeAsOneOrNull()?.let(::mapMessage)
        }

    /** 插入或替换一条消息。 */
    suspend fun insertMessage(message: QBotMessage) = withContext(Dispatchers.IO) {
        q.insertMessage(
            message_id = message.messageId,
            session_id = message.sessionId,
            sender_id = message.senderId,
            sender_name = message.senderName,
            sender_avatar = message.senderAvatar,
            sender_role = message.senderRole.name,
            content_type = message.contentType.name,
            content = message.content,
            raw_message = message.rawMessage,
            bot_reply_id = message.botReplyId,
            timestamp = message.timestamp,
            is_bot = if (message.isBot) 1L else 0L,
        )
    }

    /** 删除某会话的全部消息。 */
    suspend fun deleteMessagesBySession(sessionId: String) = withContext(Dispatchers.IO) {
        q.deleteMessagesBySession(sessionId)
    }

    /** 按 message_id 删除单条消息。 */
    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        q.deleteMessageById(messageId)
    }

    // ── 群成员 ───────────────────────────────────────────────────────────

    /** 观察某群的全部成员（按角色排序、昵称升序）。 */
    fun getGroupMembers(groupId: Long): Flow<List<QBotGroupMember>> =
        q.selectGroupMembers(groupId).asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map(::mapMember)
        }

    /** 一次性读取单个群成员，不存在返回 null。 */
    suspend fun getGroupMember(groupId: Long, userId: Long): QBotGroupMember? =
        withContext(Dispatchers.IO) {
            q.selectGroupMember(groupId, userId).executeAsOneOrNull()?.let(::mapMember)
        }

    /** 插入或更新群成员（名片 / 昵称 / 头像 / 角色）。 */
    suspend fun upsertGroupMember(member: QBotGroupMember) = withContext(Dispatchers.IO) {
        q.upsertGroupMember(
            group_id = member.groupId,
            user_id = member.userId,
            card = member.card,
            nickname = member.nickname,
            avatar = member.avatar,
            role = member.role.name,
            last_active = member.lastActive,
            updated_at = member.updatedAt,
        )
    }

    /** 更新群成员的最后活跃时间戳。 */
    suspend fun updateMemberLastActive(groupId: Long, userId: Long, timestamp: Long) =
        withContext(Dispatchers.IO) {
            q.updateMemberLastActive(timestamp, groupId, userId)
        }

    // ── Entity ↔ Domain 映射 ────────────────────────────────────────────

    private fun mapSession(row: Qqbot_session): QBotSession = QBotSession(
        sessionId = row.session_id,
        type = QBotSessionType.fromString(row.type),
        groupId = row.group_id,
        userId = row.user_id,
        groupName = row.group_name,
        userName = row.user_name,
        lastMessage = row.last_message,
        lastMessageTime = row.last_message_time,
        unreadCount = row.unread_count.toInt(),
        botQq = row.bot_qq,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    private fun mapMessage(row: Qqbot_message): QBotMessage = QBotMessage(
        messageId = row.message_id,
        sessionId = row.session_id,
        senderId = row.sender_id,
        senderName = row.sender_name,
        senderAvatar = row.sender_avatar,
        senderRole = QBotSenderRole.fromString(row.sender_role),
        contentType = QBotMessageType.fromString(row.content_type),
        content = row.content,
        rawMessage = row.raw_message,
        botReplyId = row.bot_reply_id,
        timestamp = row.timestamp,
        isBot = row.is_bot != 0L,
    )

    private fun mapMember(row: Qqbot_group_member): QBotGroupMember = QBotGroupMember(
        groupId = row.group_id,
        userId = row.user_id,
        card = row.card,
        nickname = row.nickname,
        avatar = row.avatar,
        role = QBotSenderRole.fromString(row.role),
        lastActive = row.last_active,
        updatedAt = row.updated_at,
    )
}
