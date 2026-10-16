package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * browser 工具调用的参数摘要（从 [AgentUIMessage.toolArgs] 的 JSON 解析）。
 */
internal data class BrowserToolCallArgs(
    val action: String = "",
    val url: String = "",
    val elementId: String = "",
    val text: String = "",
    val message: String = ""
) {
    /** 头部行展示的目标摘要：URL 优先，其次元素 id / 输入文本。 */
    val target: String
        get() = when {
            url.isNotBlank() -> url
            elementId.isNotBlank() -> "#$elementId"
            text.isNotBlank() -> text
            else -> ""
        }
}

/**
 * browser 工具调用结果（从落库 content 的 transport JSON 中解析 envelope 关键字段）。
 *
 * 传输形态：`{"status":"success","data":{ok,action,changed,summary,error?,snapshot?}}`；
 * 失败时也可能是 `{"status":"error","message":...}`，此时 [summary] 为空、由 UI 回落原始文案。
 */
internal data class BrowserToolCallResult(
    val ok: Boolean,
    val action: String = "",
    val summary: String = "",
    val error: String = "",
    val pageTitle: String = "",
    val pageUrl: String = ""
)

/** 解析 browser 工具参数；脏数据/空值一律兜底为空对象，不抛异常。 */
internal fun parseBrowserToolArgs(argsJson: String?): BrowserToolCallArgs {
    if (argsJson.isNullOrBlank()) return BrowserToolCallArgs()
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        BrowserToolCallArgs(
            action = obj["action"].stringOrEmpty(),
            url = obj["url"].stringOrEmpty(),
            elementId = obj["element_id"].stringOrEmpty(),
            text = obj["text"].stringOrEmpty(),
            message = obj["message"].stringOrEmpty()
        )
    }.getOrDefault(BrowserToolCallArgs())
}

/**
 * 只接受字符串类型的 JSON primitive；数字/布尔等非字符串值（含 JsonNull）一律视为未提供。
 * 注意 kotlinx 的 `contentOrNull` 只过滤 JsonNull，`{"action":123}` 会读出 "123"，
 * 对动作名这类枚举语义字段是噪音，故显式收窄。
 */
private fun JsonElement?.stringOrEmpty(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()

/** 解析 browser 工具结果；解析失败返回 null（调用方回落默认工具气泡渲染）。 */
internal fun parseBrowserToolResult(content: String?): BrowserToolCallResult? {
    if (content.isNullOrBlank()) return null
    val cleaned = content.withoutToolStatusPrefix()
    return runCatching {
        val obj = Json.parseToJsonElement(cleaned).jsonObject
        val data = obj["data"]?.jsonObject ?: return@runCatching null
        val snapshot = data["snapshot"]?.jsonObject
        BrowserToolCallResult(
            ok = data["ok"]?.jsonPrimitive?.booleanOrNull ?: true,
            action = data["action"].stringOrEmpty(),
            summary = data["summary"].stringOrEmpty(),
            error = data["error"].stringOrEmpty(),
            pageTitle = snapshot?.get("title").stringOrEmpty(),
            pageUrl = snapshot?.get("url").stringOrEmpty()
        )
    }.getOrNull()
}

/** 动作名 → 中文标签资源（常用动作本地化；未列入的动作回落动作原名）。 */
private val BROWSER_ACTION_LABEL_RES: Map<String, Int> = mapOf(
    "navigate" to R.string.browser_card_action_navigate,
    "view" to R.string.browser_card_action_view,
    "snapshot" to R.string.browser_card_action_snapshot,
    "click" to R.string.browser_card_action_click,
    "type" to R.string.browser_card_action_type,
    "fill_form" to R.string.browser_card_action_fill_form,
    "submit" to R.string.browser_card_action_submit,
    "select_option" to R.string.browser_card_action_select_option,
    "scroll" to R.string.browser_card_action_scroll,
    "hover" to R.string.browser_card_action_hover,
    "drag" to R.string.browser_card_action_drag,
    "press_key" to R.string.browser_card_action_press_key,
    "upload_file" to R.string.browser_card_action_upload_file,
    "back" to R.string.browser_card_action_back,
    "forward" to R.string.browser_card_action_forward,
    "reload" to R.string.browser_card_action_reload,
    "screenshot" to R.string.browser_card_action_screenshot,
    "extract" to R.string.browser_card_action_extract,
    "evaluate" to R.string.browser_card_action_evaluate,
    "wait_for" to R.string.browser_card_action_wait_for,
    "wait" to R.string.browser_card_action_wait,
    "login" to R.string.browser_card_action_login,
    "takeover" to R.string.browser_card_action_takeover,
    "new_tab" to R.string.browser_card_action_new_tab,
    "switch_tab" to R.string.browser_card_action_switch_tab,
    "close_tab" to R.string.browser_card_action_close_tab,
    "list_tabs" to R.string.browser_card_action_list_tabs,
    "downloads" to R.string.browser_card_action_downloads,
    "history" to R.string.browser_card_action_history,
    "get_attribute" to R.string.browser_card_action_get_attribute,
    "handle_dialog" to R.string.browser_card_action_handle_dialog,
    "network" to R.string.browser_card_action_network
)

/** 动作名 → 中文标签（map 查表，新增动作只加一行映射，不涨分支复杂度）。 */
@Composable
internal fun browserActionLabel(action: String): String {
    val resId = BROWSER_ACTION_LABEL_RES[action]
    return resId?.let { stringResource(it) }
        ?: action.ifBlank { stringResource(R.string.browser_card_default_action) }
}

/** 动作名 → 图标（与浏览器操作面板时间线的映射保持一致，未知名回落 SmartToy）。 */
internal fun browserActionIcon(action: String): ImageVector = when (action) {
    "navigate", "new_tab" -> Icons.Rounded.Public
    "click" -> Icons.Rounded.TouchApp
    "type", "fill_form", "press_key" -> Icons.Rounded.Keyboard
    "select_option", "submit" -> Icons.Rounded.CheckBox
    "scroll" -> Icons.Rounded.ArrowDownward
    "screenshot" -> Icons.Rounded.PhotoCamera
    "extract", "get_attribute" -> Icons.Rounded.Description
    "wait_for", "wait" -> Icons.Rounded.HourglassEmpty
    "back" -> Icons.AutoMirrored.Rounded.ArrowBack
    "forward" -> Icons.AutoMirrored.Rounded.ArrowForward
    "reload" -> Icons.Rounded.Refresh
    "takeover" -> Icons.Rounded.PanTool
    else -> Icons.Rounded.SmartToy
}

/**
 * AI 浏览器操作工具调用卡片。
 *
 * 替代 browser 工具的通用工具气泡：一行「状态点 + 动作图标 + 动作标签 + 目标」，
 * 下方按状态展开动作说明；takeover（请求用户接管）动作渲染为专门的交互卡片，
 * 把「AI 需要你亲自完成什么、当前是否在等你、你是否已完成」直接讲清楚，
 * 不必再点开原始 JSON 结果。
 */
@Composable
internal fun BrowserToolCallCard(
    message: AgentUIMessage,
    running: Boolean
) {
    val args = remember(message.toolArgs) { parseBrowserToolArgs(message.toolArgs) }
    val result = if (!running) {
        remember(message.id, message.content) { parseBrowserToolResult(message.content) }
    } else null
    val takeover = args.action == "takeover"

    Column(modifier = Modifier.padding(Spacing.md)) {
        BrowserToolCallHeader(
            action = args.action,
            target = args.target,
            takeover = takeover,
            running = running,
            isError = message.isError
        )
        Spacer(Modifier.height(Spacing.xs))

        when {
            takeover -> BrowserTakeoverCardBody(
                args = args,
                result = result,
                running = running,
                isError = message.isError
            )
            running -> Text(
                text = stringResource(R.string.browser_card_browsing),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                val detail = result?.let { r ->
                    when {
                        r.error.isNotBlank() -> r.error
                        r.summary.isNotBlank() -> r.summary
                        r.pageTitle.isNotBlank() -> r.pageTitle
                        else -> null
                    }
                }
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        color = if (result?.ok == false || message.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** 卡片头部一行：状态点 + 动作图标 + 动作标签 + 目标（省略）/ 运行中动效。 */
@Composable
private fun BrowserToolCallHeader(
    action: String,
    target: String,
    takeover: Boolean,
    running: Boolean,
    isError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolStatusDot(running = running, isError = isError)
        Spacer(Modifier.width(Spacing.sm))
        Icon(
            imageVector = browserActionIcon(action),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = browserActionLabel(action),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize
            )
        )
        if (!takeover && target.isNotBlank()) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = target,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (running) {
            Spacer(Modifier.width(Spacing.sm))
            TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant, dotSize = 5.dp)
        }
    }
}

/**
 * takeover 动作的卡片正文：说明 AI 需要用户亲自完成什么 + 当前等待/完成状态。
 */
@Composable
private fun BrowserTakeoverCardBody(
    args: BrowserToolCallArgs,
    result: BrowserToolCallResult?,
    running: Boolean,
    isError: Boolean
) {
    val statusText: String
    val statusColor = when {
        isError -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        result?.ok == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    when {
        isError -> statusText = stringResource(R.string.browser_card_takeover_failed)
        running -> statusText = stringResource(R.string.browser_card_takeover_waiting)
        result?.ok == true -> statusText = stringResource(R.string.browser_card_takeover_done)
        else -> statusText = stringResource(R.string.browser_card_takeover_cancelled)
    }

    Column {
        Text(
            text = stringResource(R.string.browser_card_takeover_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        if (args.message.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = args.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
