package com.mini.me_core.feature.qqbot.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level OneBot 11 API client.
 *
 * Wraps [OneBot11Server.sendApiRequest] to provide typed methods for sending
 * private / group messages and querying group / member info.
 *
 * Messages are encoded in OneBot 11 array segment format:
 * ```json
 * [{"type":"text","data":{"text":"..."}}]
 * ```
 * Long messages exceeding [QBotConstants.MESSAGE_SPLIT_LENGTH] characters are
 * automatically split into chunks and sent individually.
 */
@Singleton
class OneBotApiClient @Inject constructor(
    private val server: OneBot11Server,
    private val logManager: QBotLogManager
) {

    companion object {
        private const val TAG = "OneBotApiClient"
    }

    /**
     * Send a private message to [userId].
     *
     * If [message] is longer than [QBotConstants.MESSAGE_SPLIT_LENGTH], it is split
     * into multiple chunks and each chunk is sent as a separate API call.
     */
    suspend fun sendPrivateMessage(userId: Long, message: String) {
        val chunks = splitMessage(message)
        logManager.debug(TAG, "Sending private message to user=$userId chunks=${chunks.size}")
        chunks.forEach { chunk ->
            val params = buildJsonObject {
                put("user_id", userId)
                put("message", buildTextSegmentArray(chunk))
            }
            server.sendApiRequest("send_private_msg", params)
        }
    }

    /**
     * Send a group message to [groupId].
     *
     * If [message] is longer than [QBotConstants.MESSAGE_SPLIT_LENGTH], it is split
     * into multiple chunks and each chunk is sent as a separate API call.
     */
    suspend fun sendGroupMessage(groupId: Long, message: String) {
        val chunks = splitMessage(message)
        logManager.debug(TAG, "Sending group message to group=$groupId chunks=${chunks.size}")
        chunks.forEach { chunk ->
            val params = buildJsonObject {
                put("group_id", groupId)
                put("message", buildTextSegmentArray(chunk))
            }
            server.sendApiRequest("send_group_msg", params)
        }
    }

    /**
     * 查询机器人自身登录信息（OneBot 11 `get_login_info`）。
     *
     * @return (QQ号, 昵称)；未连接 / 超时 / 失败时返回 null。
     */
    suspend fun getLoginInfo(): Pair<Long, String>? {
        val resp = server.sendApiRequest("get_login_info", buildJsonObject {})
        val data = resp?.data ?: return null
        val userId = data["user_id"]?.jsonPrimitive?.longOrNull ?: return null
        val nickname = data["nickname"]?.jsonPrimitive?.contentOrNull ?: ""
        return userId to nickname
    }

    /**
     * Query detailed information about a group member.
     *
     * @return [OneBotApiResponse] from the LLBot, or null if no connection / timeout.
     */
    suspend fun getGroupMemberInfo(groupId: Long, userId: Long): OneBotApiResponse? {
        val params = buildJsonObject {
            put("group_id", groupId)
            put("user_id", userId)
        }
        return server.sendApiRequest("get_group_member_info", params)
    }

    /**
     * Query information about a group.
     *
     * @return [OneBotApiResponse] from the LLBot, or null if no connection / timeout.
     */
    suspend fun getGroupInfo(groupId: Long): OneBotApiResponse? {
        val params = buildJsonObject {
            put("group_id", groupId)
        }
        return server.sendApiRequest("get_group_info", params)
    }

    /**
     * Build a OneBot 11 message segment array containing a single text segment.
     */
    private fun buildTextSegmentArray(text: String): JsonArray {
        return buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("data", buildJsonObject {
                        put("text", text)
                    })
                }
            )
        }
    }

    /**
     * Split [message] into chunks no longer than [QBotConstants.MESSAGE_SPLIT_LENGTH].
     * Short messages are returned as a single-element list.
     */
    private fun splitMessage(message: String): List<String> {
        if (message.length <= QBotConstants.MESSAGE_SPLIT_LENGTH) {
            return listOf(message)
        }
        val chunks = mutableListOf<String>()
        var remaining = message
        while (remaining.length > QBotConstants.MESSAGE_SPLIT_LENGTH) {
            chunks.add(remaining.substring(0, QBotConstants.MESSAGE_SPLIT_LENGTH))
            remaining = remaining.substring(QBotConstants.MESSAGE_SPLIT_LENGTH)
        }
        chunks.add(remaining)
        return chunks
    }
}
