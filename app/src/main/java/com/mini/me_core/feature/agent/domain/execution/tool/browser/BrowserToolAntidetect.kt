package com.mini.me_core.feature.agent.domain.execution.tool.browser

import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.browser.domain.BrowserController
import com.mini.me_core.feature.browser.domain.antidetect.AntidetectController
import com.mini.me_core.feature.browser.domain.antidetect.SignalDetector
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintGenerator
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 反检测/指纹管理工具域（行为保持自 [BrowserAgentTool] 原样搬迁）。
 *
 * 承载 fingerprint_*、antidetect_*、browser_status 等管理类动作。
 * 这些动作多数直接读写 [fingerprintManager]/[antidetectController]，不触发浏览器交互锁；
 * 统一返回 envelope 通过门面 [tools] 回调（[BrowserAgentTool.envelope]），结构逐字不变。
 * 反检测开关默认值、判定文案与错误码均未改动。
 */
@Suppress("ClassOrdering") // 行为保持搬迁：成员按指纹/反爬/状态职责自然分组
internal class BrowserToolAntidetect(
    private val tools: BrowserAgentTool,
    private val browserController: BrowserController,
    private val fingerprintManager: FingerprintManager,
    private val antidetectController: AntidetectController
) {

    private companion object {
        const val MAX_TEXT = 8000
    }

    /** 序列化单个指纹配置为 JSON 对象。 */
    private fun profileToJson(p: FingerprintProfile): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(p.id),
            "name" to JsonPrimitive(p.name),
            "browser" to JsonPrimitive(p.browser),
            "browser_version" to JsonPrimitive(p.browserVersion),
            "os" to JsonPrimitive(p.os),
            "os_version" to JsonPrimitive(p.osVersion),
            "platform" to JsonPrimitive(p.platform),
            "region" to JsonPrimitive(p.region),
            "timezone" to JsonPrimitive(p.timezone),
            "language" to JsonPrimitive(p.language),
            "hardware_concurrency" to JsonPrimitive(p.hardwareConcurrency),
            "device_memory" to JsonPrimitive(p.deviceMemory),
            "screen" to JsonPrimitive("${p.screenWidth}x${p.screenHeight}"),
            "score" to JsonPrimitive(p.score),
            "status" to JsonPrimitive(p.status),
            "is_available" to JsonPrimitive(p.isAvailable),
            "is_cooling" to JsonPrimitive(p.isCooling),
            "total_uses" to JsonPrimitive(p.totalUses),
            "webrtc_protection" to JsonPrimitive(p.webrtcProtectionLevel),
            "webgl_spoof" to JsonPrimitive(p.webglSpoofEnabled),
            "canvas_noise" to JsonPrimitive(p.canvasNoiseEnabled)
        )
    )

    /** fingerprint_list：列出所有指纹配置，支持 status/region/browser 筛选。 */
    fun doFingerprintList(args: Map<String, JsonElement>): ToolResult {
        val statusFilter = args["status"]?.jsonPrimitive?.contentOrNull
        val regionFilter = args["region"]?.jsonPrimitive?.contentOrNull
        val browserFilter = args["browser"]?.jsonPrimitive?.contentOrNull

        var list = fingerprintManager.getAll()
        statusFilter?.let { f -> list = list.filter { it.status.equals(f, ignoreCase = true) } }
        regionFilter?.let { f -> list = list.filter { it.region.equals(f, ignoreCase = true) } }
        browserFilter?.let { f -> list = list.filter { it.browser.equals(f, ignoreCase = true) } }

        val currentId = fingerprintManager.currentProfileId.value
        val profilesJson = JsonArray(list.map { p ->
            JsonObject(profileToJson(p) + ("is_current" to JsonPrimitive(p.id == currentId)))
        })
        val stats = fingerprintManager.getStats()

        return tools.envelope(
            action = "fingerprint_list",
            ok = true,
            summary = "共 ${list.size} 个指纹配置（可用 ${stats["available"]}，冷却中 ${stats["cooling"]}）",
            extra = JsonObject(mapOf(
                "profiles" to profilesJson,
                "stats" to JsonObject(stats.mapValues { JsonPrimitive(it.value.toString()) }),
                "current_id" to JsonPrimitive(currentId ?: "none")
            ))
        )
    }

    /** fingerprint_get：获取当前或指定配置详情。 */
    fun doFingerprintGet(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
        val profile = if (profileId.isNullOrBlank()) {
            fingerprintManager.getCurrent()
        } else {
            fingerprintManager.getById(profileId)
        } ?: return tools.envelope(
            action = "fingerprint_get",
            ok = false,
            error = "指纹配置不存在：${profileId ?: "(当前无激活配置)"}",
            errorCode = "PROFILE_NOT_FOUND",
            recoverable = true
        )

        return tools.envelope(
            action = "fingerprint_get",
            ok = true,
            summary = profile.shortDescription,
            extra = JsonObject(mapOf("profile" to profileToJson(profile)))
        )
    }

    /** fingerprint_set：切换到指定配置，可选自动重载页面。 */
    suspend fun doFingerprintSet(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
            ?: return tools.envelope(
                action = "fingerprint_set",
                ok = false,
                error = "缺少 profile_id 参数",
                errorCode = "MISSING_PROFILE_ID"
            )
        val applyNow = args["apply_now"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true

        val target = browserController.switchFingerprint(profileId, reload = applyNow)
            ?: return tools.envelope(
                action = "fingerprint_set",
                ok = false,
                error = "无法切换到指纹 $profileId：配置不存在、不可用或正在冷却期",
                errorCode = "SWITCH_FAILED",
                recoverable = true
            )

        return tools.envelope(
            action = "fingerprint_set",
            ok = true,
            changed = true,
            summary = "已切换到指纹：${target.name}（${target.region} · ${target.browser} ${target.browserVersion}）",
            note = if (applyNow) "页面已重新加载，新指纹已生效" else "已切换配置，下次页面加载时生效"
        )
    }

    /** fingerprint_create：创建随机配置。 */
    fun doFingerprintCreate(args: Map<String, JsonElement>): ToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull
        val templateName = args["template"]?.jsonPrimitive?.contentOrNull
        val template = when (templateName?.lowercase()) {
            "region_us", "us" -> FingerprintGenerator.Template.REGION_US
            "region_cn", "cn" -> FingerprintGenerator.Template.REGION_CN
            "region_jp", "jp" -> FingerprintGenerator.Template.REGION_JP
            "region_eu", "eu" -> FingerprintGenerator.Template.REGION_EU
            "high_end", "gaming" -> FingerprintGenerator.Template.HIGH_END
            "mobile", "android" -> FingerprintGenerator.Template.MOBILE
            else -> null
        }

        val profile = fingerprintManager.createRandom(template = template, name = name)
        return tools.envelope(
            action = "fingerprint_create",
            ok = true,
            changed = true,
            summary = "已创建指纹：${profile.name}（ID: ${profile.id}）",
            note = "地区=${profile.region}，浏览器=${profile.browser} ${profile.browserVersion}，评分=${profile.score.toInt()}",
            extra = JsonObject(mapOf("profile" to profileToJson(profile)))
        )
    }

    /** fingerprint_delete：删除配置。 */
    fun doFingerprintDelete(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
            ?: return tools.envelope(
                action = "fingerprint_delete",
                ok = false,
                error = "缺少 profile_id 参数",
                errorCode = "MISSING_PROFILE_ID"
            )
        val force = args["force"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

        val ok = fingerprintManager.delete(profileId, force = force)
        return if (ok) {
            tools.envelope(
                action = "fingerprint_delete",
                ok = true,
                changed = true,
                summary = "已删除指纹配置：$profileId"
            )
        } else {
            tools.envelope(
                action = "fingerprint_delete",
                ok = false,
                error = "删除失败：配置不存在、是当前激活配置（需 force=true）、或为内置真实指纹配置",
                errorCode = "DELETE_FAILED",
                recoverable = true
            )
        }
    }

    /** fingerprint_validate：校验配置一致性。 */
    fun doFingerprintValidate(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
        val result = fingerprintManager.validate(profileId)
            ?: return tools.envelope(
                action = "fingerprint_validate",
                ok = false,
                error = "配置不存在或无当前激活配置",
                errorCode = "PROFILE_NOT_FOUND"
            )

        val issues = result.issues.joinToString("；") { "${it.field}: ${it.message}" }
        return tools.envelope(
            action = "fingerprint_validate",
            ok = result.valid,
            summary = if (result.valid) "配置一致性校验通过（评分 ${result.score.toInt()}）"
                       else "配置存在 ${result.issues.size} 个一致性问题（评分 ${result.score.toInt()}）",
            note = if (result.valid) "所有指纹字段内部一致" else issues,
            extra = JsonObject(mapOf(
                "valid" to JsonPrimitive(result.valid),
                "score" to JsonPrimitive(result.score),
                "issue_count" to JsonPrimitive(result.issues.size)
            ))
        )
    }

    /** fingerprint_test：运行指纹检测（简化版：返回当前配置评分和一致性结果）。 */
    fun doFingerprintTest(): ToolResult {
        val current = fingerprintManager.getCurrent()
            ?: return tools.envelope(
                action = "fingerprint_test",
                ok = false,
                error = "无当前激活指纹配置",
                errorCode = "NO_PROFILE"
            )
        val validation = fingerprintManager.validate()
        val scriptSize = com.mini.me_core.feature.browser.domain.fingerprint.FingerprintInjector
            .estimateScriptSize(current)

        return tools.envelope(
            action = "fingerprint_test",
            ok = true,
            summary = "当前指纹：${current.shortDescription}",
            note = "一致性评分=${validation?.score?.toInt() ?: 0}/100，" +
                "注入脚本大小=${scriptSize}字节，" +
                "WebRTC防护级别=${current.webrtcProtectionLevel}",
            extra = JsonObject(mapOf(
                "profile" to profileToJson(current),
                "consistency_score" to JsonPrimitive(validation?.score ?: 0f),
                "script_size_bytes" to JsonPrimitive(scriptSize)
            ))
        )
    }

    // ─────────────────────── 反爬控制动作 ───────────────────────

    /** antidetect_status：获取当前反爬状态。 */
    fun doAntidetectStatus(): ToolResult {
        val s = antidetectController.state.value
        return tools.envelope(
            action = "antidetect_status",
            ok = true,
            summary = "反爬状态：detected=${s.detected}，风险=${s.riskLevel}，健康度=${s.healthScore.toInt()}/100",
            extra = JsonObject(mapOf(
                "detected" to JsonPrimitive(s.detected),
                "risk_level" to JsonPrimitive(s.riskLevel),
                "health_score" to JsonPrimitive(s.healthScore),
                "consecutive_failures" to JsonPrimitive(s.consecutiveFailures),
                "paused" to JsonPrimitive(s.paused),
                "pause_remaining_seconds" to JsonPrimitive(s.pauseRemainingSeconds),
                "last_detection_at" to JsonPrimitive(s.lastDetectionAt),
                "last_strategy" to JsonPrimitive(s.lastStrategy ?: "")
            ))
        )
    }

    /** antidetect_signals：获取活跃信号和历史。 */
    fun doAntidetectSignals(args: Map<String, JsonElement>): ToolResult {
        val limit = runCatching { args["limit"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 10
        val active = antidetectController.getActiveSignals()
        val history = antidetectController.getSignalHistory(limit = limit)

        fun signalToJson(sig: SignalDetector.Signal) = JsonObject(mapOf(
            "type" to JsonPrimitive(sig.type),
            "severity" to JsonPrimitive(sig.severity),
            "url" to JsonPrimitive(sig.url),
            "description" to JsonPrimitive(sig.description),
            "timestamp" to JsonPrimitive(sig.timestamp)
        ))

        return tools.envelope(
            action = "antidetect_signals",
            ok = true,
            summary = "活跃信号 ${active.size} 个，历史记录 ${history.size} 条",
            note = if (active.isEmpty()) "当前无活跃反爬信号"
                   else active.joinToString("；") { it.description },
            extra = JsonObject(mapOf(
                "active_signals" to JsonArray(active.map { signalToJson(it) }),
                "history" to JsonArray(history.map { signalToJson(it) })
            ))
        )
    }

    /** antidetect_adjust：执行自适应调整。 */
    fun doAntidetectAdjust(args: Map<String, JsonElement>): ToolResult {
        val strategy = args["strategy"]?.jsonPrimitive?.contentOrNull ?: "auto"
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "手动触发"
        val result = antidetectController.adjust(strategy = strategy, reason = reason)
        return tools.envelope(
            action = "antidetect_adjust",
            ok = true,
            changed = true,
            summary = "自适应调整完成（策略：$strategy）",
            note = result
        )
    }

    /** antidetect_pause：暂停浏览器操作。 */
    fun doAntidetectPause(args: Map<String, JsonElement>): ToolResult {
        val duration = runCatching { args["duration_seconds"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 300
        antidetectController.pause(durationSeconds = duration)
        return tools.envelope(
            action = "antidetect_pause",
            ok = true,
            changed = true,
            summary = "已暂停浏览器操作 ${duration} 秒",
            note = "暂停期间所有浏览器导航和交互操作将被拒绝，调用 antidetect_resume 可提前恢复"
        )
    }

    /** antidetect_resume：恢复浏览器操作。 */
    fun doAntidetectResume(): ToolResult {
        antidetectController.resume()
        return tools.envelope(
            action = "antidetect_resume",
            ok = true,
            changed = true,
            summary = "已恢复浏览器操作",
            note = "连续失败计数已重置，活跃信号已清除"
        )
    }

    /** browser_status：综合状态（当前指纹+反爬状态+当前URL）。 */
    fun doBrowserStatus(): ToolResult {
        val fp = fingerprintManager.getCurrent()
        val ad = antidetectController.state.value
        val currentUrl = browserController.uiState.value.currentUrl

        return tools.envelope(
            action = "browser_status",
            ok = true,
            summary = "当前页面：${currentUrl.ifBlank { "(无)" }}",
            note = "指纹：${fp?.shortDescription ?: "无"} | " +
                "反爬：${if (ad.detected) "已检测(${ad.riskLevel})" else "正常"} | " +
                "健康度：${ad.healthScore.toInt()}/100 | " +
                "${if (ad.paused) "暂停中(${ad.pauseRemainingSeconds}s)" else "运行中"}",
            extra = JsonObject(mapOf(
                "current_url" to JsonPrimitive(currentUrl),
                "fingerprint" to (fp?.let { profileToJson(it) } ?: JsonObject(emptyMap())),
                "antidetect" to JsonObject(mapOf(
                    "detected" to JsonPrimitive(ad.detected),
                    "risk_level" to JsonPrimitive(ad.riskLevel),
                    "health_score" to JsonPrimitive(ad.healthScore),
                    "paused" to JsonPrimitive(ad.paused)
                ))
            ))
        )
    }
}
