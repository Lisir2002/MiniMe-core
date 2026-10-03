package com.mini.me_core.feature.browser.domain.antidetect

import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 反爬控制器（单例）。
 *
 * 职责：
 * - 维护当前反爬状态（是否被检测、风险等级、活跃信号）
 * - 收集和管理反爬信号历史
 * - 执行自适应调整策略（规则驱动，后续升级为 AI 驱动）
 * - 管理暂停/恢复状态
 * - 维护连续失败计数和熔断状态
 *
 * 与 [FingerprintManager] 协作：调整策略可能涉及指纹切换、冷却等操作。
 */
@Singleton
class AntidetectController @Inject constructor(
    private val fingerprintManager: FingerprintManager,
) {

    // ===== 状态 =====

    /**
     * 反爬状态。
     */
    data class AntidetectState(
        /** 是否被检测到反爬 */
        val detected: Boolean = false,
        /** 风险等级：none / low / medium / high / critical */
        val riskLevel: String = "none",
        /** 健康度评分（0-100），越高越安全 */
        val healthScore: Float = 100f,
        /** 连续失败次数 */
        val consecutiveFailures: Int = 0,
        /** 是否暂停中 */
        val paused: Boolean = false,
        /** 暂停到期时间戳（ms），0 表示未暂停 */
        val pauseUntil: Long = 0,
        /** 最后一次被检测时间戳 */
        val lastDetectionAt: Long = 0,
        /** 最后一次调整时间戳 */
        val lastAdjustmentAt: Long = 0,
        /** 最后一次调整的策略 */
        val lastStrategy: String? = null,
    ) {
        /** 是否可用（未暂停且未熔断） */
        val isAvailable: Boolean
            get() = !paused && consecutiveFailures < 5

        /** 暂停剩余秒数 */
        val pauseRemainingSeconds: Int
            get() = if (paused && pauseUntil > System.currentTimeMillis()) {
                ((pauseUntil - System.currentTimeMillis()) / 1000).toInt()
            } else 0
    }

    private val _state = MutableStateFlow(AntidetectState())
    val state: StateFlow<AntidetectState> = _state.asStateFlow()

    /** 活跃信号列表（最近的信号，未解决的） */
    private val activeSignals = CopyOnWriteArrayList<SignalDetector.Signal>()

    /** 信号历史（最近 100 条） */
    private val signalHistory = CopyOnWriteArrayList<SignalDetector.Signal>()

    /** 策略执行历史（最近 50 条） */
    private val adjustmentHistory = CopyOnWriteArrayList<AdjustmentRecord>()

    // ===== 常量 =====

    companion object {
        /** 连续失败熔断阈值 */
        private const val FAILURE_THRESHOLD = 5

        /** 默认暂停时长（秒） */
        private const val DEFAULT_PAUSE_SECONDS = 300
    }

    // ===== 信号检测与收集 =====

    /**
     * 分析一次操作结果，检测反爬信号并更新状态。
     *
     * @param statusCode HTTP 状态码
     * @param pageText 页面文本
     * @param responseTimeMs 响应时间
     * @param url 页面 URL
     * @param success 操作是否成功
     * @return 检测到的信号列表
     */
    fun analyzeResult(
        statusCode: Int? = null,
        pageText: String? = null,
        responseTimeMs: Long? = null,
        url: String = "",
        success: Boolean = true,
    ): List<SignalDetector.Signal> {
        // 1. 检测信号
        val signals = SignalDetector.detect(
            statusCode = statusCode,
            pageText = pageText,
            responseTimeMs = responseTimeMs,
            url = url,
        )

        // 2. 更新连续失败计数
        if (!success || signals.any { it.severity in listOf("high", "critical") }) {
            _state.update { it.copy(consecutiveFailures = it.consecutiveFailures + 1) }
        } else {
            // 成功时缓慢恢复（不是立即清零，避免抖动）
            _state.update {
                val newCount = (it.consecutiveFailures - 1).coerceAtLeast(0)
                it.copy(consecutiveFailures = newCount)
            }
        }

        // 3. 处理检测到的信号
        if (signals.isNotEmpty()) {
            signals.forEach { signal ->
                activeSignals.add(signal)
                signalHistory.add(signal)
                // 限制历史长度
                while (signalHistory.size > 100) signalHistory.removeAt(0)
            }

            // 更新状态
            val maxSeverity = signals.maxOf { severityWeight(it.severity) }
            val riskLevel = when {
                maxSeverity >= 4 -> "critical"
                maxSeverity >= 3 -> "high"
                maxSeverity >= 2 -> "medium"
                else -> "low"
            }
            _state.update {
                it.copy(
                    detected = true,
                    riskLevel = riskLevel,
                    lastDetectionAt = System.currentTimeMillis(),
                    healthScore = calculateHealthScore(),
                )
            }
        } else if (success) {
            // 操作成功且无信号，逐渐恢复健康度
            _state.update {
                val newScore = (it.healthScore + 2f).coerceAtMost(100f)
                val newDetected = if (newScore >= 80f && activeSignals.isEmpty()) false else it.detected
                it.copy(
                    healthScore = newScore,
                    detected = newDetected,
                    riskLevel = if (newDetected) it.riskLevel else "none",
                )
            }
        }

        return signals
    }

    // ===== 自适应调整 =====

    /**
     * 调整记录。
     */
    data class AdjustmentRecord(
        val strategy: String,
        val reason: String,
        val timestamp: Long = System.currentTimeMillis(),
        val success: Boolean? = null,
    )

    /**
     * 执行自适应调整。
     *
     * @param strategy 调整策略（auto 则自动选择）
     * @param reason 调整原因
     * @return 调整结果描述
     */
    fun adjust(strategy: String = "auto", reason: String = ""): String {
        val actualStrategy = if (strategy == "auto") selectAutoStrategy() else strategy

        val result = when (actualStrategy) {
            "switch_fingerprint" -> doSwitchFingerprint()
            "slow_down" -> doSlowDown()
            "cool_down" -> doCoolDown()
            "clear_cookies" -> doClearCookies()
            "keep_current" -> "保持当前配置，继续观察"
            else -> "未知策略：$actualStrategy"
        }

        // 记录调整历史
        adjustmentHistory.add(AdjustmentRecord(actualStrategy, reason))
        while (adjustmentHistory.size > 50) adjustmentHistory.removeAt(0)

        _state.update {
            it.copy(
                lastAdjustmentAt = System.currentTimeMillis(),
                lastStrategy = actualStrategy,
            )
        }

        return result
    }

    /**
     * 自动选择最优策略（规则驱动）。
     *
     * 后续版本将升级为 AI 驱动（上下文老虎机/强化学习）。
     */
    private fun selectAutoStrategy(): String {
        val currentState = _state.value
        val hasHighSignal = activeSignals.any { it.severity in listOf("high", "critical") }
        val hasCaptcha = activeSignals.any { it.type == "captcha_detected" }
        val hasBlockPage = activeSignals.any { it.type == "block_page" }
        val hasIpBanned = activeSignals.any { it.type == "ip_banned" }
        val has429 = activeSignals.any { it.type == "http_429" }

        return when {
            // IP 被封禁：需要冷却 + 切换指纹
            hasIpBanned -> "cool_down"
            // 拦截页 + 高风险：切换指纹
            hasBlockPage && hasHighSignal -> "switch_fingerprint"
            // 验证码频繁：切换指纹
            hasCaptcha && currentState.consecutiveFailures >= 2 -> "switch_fingerprint"
            // 429 限流：降低频率
            has429 -> "slow_down"
            // 连续失败多：冷却
            currentState.consecutiveFailures >= 4 -> "cool_down"
            // 轻度问题：降低频率观察
            currentState.consecutiveFailures >= 2 -> "slow_down"
            // 无明显问题：保持
            else -> "keep_current"
        }
    }

    /** 切换指纹 */
    private fun doSwitchFingerprint(): String {
        val current = fingerprintManager.getCurrent()
        val available = fingerprintManager.getAvailable().filter { it.id != current?.id }

        if (available.isEmpty()) {
            // 没有可用配置，生成一个新的
            val newProfile = fingerprintManager.createRandom(name = "自动生成 ${System.currentTimeMillis() % 10000}")
            fingerprintManager.switchTo(newProfile.id)
            return "已生成并切换到新指纹：${newProfile.name}"
        }

        // 选择评分最高的可用配置
        val best = available.maxByOrNull { it.score } ?: available.first()
        fingerprintManager.switchTo(best.id)

        // 将旧指纹标记为冷却
        current?.let { fingerprintManager.cooldown(it.id, 300) }

        // 清除活跃信号（切换后重新开始）
        activeSignals.clear()

        return "已切换到指纹：${best.name}（评分 ${best.score.toInt()}），旧指纹进入 5 分钟冷却"
    }

    /** 降低操作频率（返回建议，实际由模型执行） */
    private fun doSlowDown(): String {
        _state.update { it.copy(healthScore = (it.healthScore + 5f).coerceAtMost(100f)) }
        return "已降低操作频率：建议每次操作间隔 5-10 秒，避免连续快速操作"
    }

    /** 冷却（暂停操作） */
    private fun doCoolDown(): String {
        pause(DEFAULT_PAUSE_SECONDS)
        activeSignals.clear()
        return "已进入冷却状态：暂停操作 $DEFAULT_PAUSE_SECONDS 秒，期间所有浏览器操作将被拒绝"
    }

    /** 清理 Cookie（返回建议，实际由 BrowserController 执行） */
    private fun doClearCookies(): String {
        return "建议清理当前站点 Cookie 和 localStorage（可通过 browser_storage 工具执行），然后重新加载页面"
    }

    // ===== 暂停/恢复 =====

    /**
     * 暂停浏览器操作。
     *
     * @param durationSeconds 暂停时长（秒）
     * @param reason 暂停原因
     */
    fun pause(durationSeconds: Int = DEFAULT_PAUSE_SECONDS, reason: String = "") {
        val until = System.currentTimeMillis() + durationSeconds * 1000L
        _state.update {
            it.copy(
                paused = true,
                pauseUntil = until,
            )
        }
    }

    /**
     * 恢复浏览器操作。
     */
    fun resume() {
        _state.update {
            it.copy(
                paused = false,
                pauseUntil = 0,
                consecutiveFailures = 0,
            )
        }
        activeSignals.clear()
    }

    /**
     * 检查是否暂停中（含自动到期恢复）。
     */
    fun isPaused(): Boolean {
        val current = _state.value
        if (current.paused && current.pauseUntil > 0 &&
            System.currentTimeMillis() >= current.pauseUntil
        ) {
            // 自动到期恢复
            resume()
            return false
        }
        return current.paused
    }

    // ===== 查询操作 =====

    /** 获取活跃信号列表 */
    fun getActiveSignals(): List<SignalDetector.Signal> = activeSignals.toList()

    /** 获取信号历史 */
    fun getSignalHistory(limit: Int = 20): List<SignalDetector.Signal> =
        signalHistory.takeLast(limit).reversed()

    /** 获取调整历史 */
    fun getAdjustmentHistory(limit: Int = 20): List<AdjustmentRecord> =
        adjustmentHistory.takeLast(limit).reversed()

    /** 标记信号为已解决 */
    fun resolveSignal(signal: SignalDetector.Signal) {
        activeSignals.remove(signal)
    }

    /** 清除所有活跃信号 */
    fun clearActiveSignals() {
        activeSignals.clear()
    }

    // ===== 健康度计算 =====

    /**
     * 计算健康度评分（0-100）。
     *
     * 扣分因素：
     * - 活跃高风险信号：每个扣 15 分
     * - 活跃中风险信号：每个扣 8 分
     * - 活跃低风险信号：每个扣 3 分
     * - 连续失败：每次扣 5 分
     * - 暂停中：扣 20 分
     */
    private fun calculateHealthScore(): Float {
        var score = 100f

        activeSignals.forEach { signal ->
            score -= when (signal.severity) {
                "critical" -> 25f
                "high" -> 15f
                "medium" -> 8f
                "low" -> 3f
                else -> 2f
            }
        }

        score -= _state.value.consecutiveFailures * 5f

        if (_state.value.paused) score -= 20f

        return score.coerceIn(0f, 100f)
    }

    // ===== 重置 =====

    /**
     * 重置所有状态（用于调试或新会话）。
     */
    fun reset() {
        _state.value = AntidetectState()
        activeSignals.clear()
        signalHistory.clear()
        adjustmentHistory.clear()
    }

    // ===== 辅助函数 =====

    private fun severityWeight(severity: String): Int = when (severity) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }
}
