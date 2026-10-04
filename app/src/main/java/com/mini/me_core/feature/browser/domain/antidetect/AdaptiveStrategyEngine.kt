package com.mini.me_core.feature.browser.domain.antidetect

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自适应反爬策略引擎。
 *
 * 基于上下文老虎机（Contextual Bandit）算法，根据当前反爬模式、站点特征、
 * 指纹状态等上下文信息，自动选择最优应对策略，并通过策略执行效果反馈持续学习优化。
 *
 * 核心概念：
 * - 反爬模式（AntiBotPattern）：识别当前遇到的具体反爬类型（Cloudflare/验证码/IP封禁等）
 * - 策略（Strategy）：预定义的应对动作（切换指纹/降低频率/冷却/清理Cookie等）
 * - 上下文（Context）：决策时的环境信息（反爬模式、站点、连续失败数等）
 * - 奖励（Reward）：策略执行后的效果评分（成功=正，失败=负）
 *
 * 算法：ε-贪婪（ε-Greedy）+ 上下文加权
 * - 以 1-ε 概率选择当前估计价值最高的策略（利用）
 * - 以 ε 概率随机选择策略（探索），避免陷入局部最优
 * - 策略价值估计 = 历史平均奖励 × 上下文匹配度权重
 */
@Singleton
class AdaptiveStrategyEngine @Inject constructor() {

    // ===== 反爬模式定义 =====

    /**
     * 反爬模式。
     */
    enum class AntiBotPattern(
        val id: String,
        val displayName: String,
        val description: String,
    ) {
        CLOUDFLARE("cloudflare", "Cloudflare 防护", "检测到 Cloudflare 5秒盾/JS挑战/拦截页"),
        AKAMAI("akamai", "Akamai 防护", "检测到 Akamai Bot Manager 拦截"),
        CAPTCHA("captcha", "验证码挑战", "页面出现 reCAPTCHA/hCaptcha/极验等验证码"),
        IP_BANNED("ip_banned", "IP 被封禁", "返回 403/访问被拒，提示 IP 被封禁"),
        RATE_LIMITED("rate_limited", "频率限制", "返回 429 Too Many Requests"),
        BLOCK_PAGE("block_page", "拦截页", "页面显示访问被拦截/需要验证"),
        BEHAVIORAL("behavioral", "行为检测", "检测到异常操作行为（鼠标/键盘/时序）"),
        FINGERPRINT_BLOCKED("fingerprint_blocked", "指纹被标记", "当前指纹被目标站点标记或拉黑"),
        UNKNOWN("unknown", "未知反爬", "检测到反爬信号但无法确定具体类型"),
        NONE("none", "无反爬", "未检测到明显反爬信号"),
    }

    // ===== 策略定义 =====

    /**
     * 可用策略列表。
     */
    val availableStrategies = listOf(
        "switch_fingerprint",
        "slow_down",
        "cool_down",
        "clear_cookies",
        "switch_and_slow",
        "humanize_behavior",
        "keep_current",
    )

    // ===== 策略效果统计 =====

    /**
     * 策略效果记录。
     */
    @Serializable
    data class StrategyStats(
        val strategy: String,
        var totalUses: Int = 0,
        var successes: Int = 0,
        var failures: Int = 0,
        var totalReward: Float = 0f,
    ) {
        /** 平均奖励（价值估计） */
        val avgReward: Float
            get() = if (totalUses > 0) totalReward / totalUses else 0f

        /** 成功率 */
        val successRate: Float
            get() = if (totalUses > 0) successes.toFloat() / totalUses else 0f
    }

    /** 全局策略统计（不区分上下文） */
    private val globalStats = mutableMapOf<String, StrategyStats>()

    /** 按反爬模式分组的策略统计 */
    private val patternStats = mutableMapOf<String, MutableMap<String, StrategyStats>>()

    // ===== 算法参数 =====

    companion object {
        /** 探索概率 ε：以 ε 概率随机选择策略（探索），1-ε 选择最优（利用） */
        private const val EPSILON = 0.15f

        /** 最小使用次数：低于此次数的策略优先被探索 */
        private const val MIN_EXPLORE_COUNT = 3

        /** 上下文匹配权重：模式匹配的策略价值乘以这个权重 */
        private const val PATTERN_MATCH_WEIGHT = 1.3f

        /** 奖励值：成功 */
        private const val REWARD_SUCCESS = 1.0f
        /** 奖励值：部分成功（有改善但未完全解决） */
        private const val REWARD_PARTIAL = 0.3f
        /** 奖励值：失败 */
        private const val REWARD_FAILURE = -1.0f
    }

    init {
        // 初始化所有策略的统计
        availableStrategies.forEach { strategy ->
            globalStats[strategy] = StrategyStats(strategy = strategy)
        }
    }

    // ===== 反爬模式识别 =====

    /**
     * 识别当前反爬模式。
     *
     * 根据活跃信号组合、HTTP 状态码、页面内容特征等，判断具体的反爬类型。
     *
     * @param signals 活跃信号列表
     * @param statusCode HTTP 状态码
     * @param pageTitle 页面标题
     * @param pageUrl 页面 URL
     * @return 识别到的反爬模式（含置信度）
     */
    fun detectPattern(
        signals: List<SignalDetector.Signal>,
        statusCode: Int? = null,
        pageTitle: String? = null,
        pageUrl: String = "",
    ): PatternDetectionResult {
        val signalTypes = signals.map { it.type }.toSet()
        val lowerTitle = pageTitle?.lowercase() ?: ""
        val lowerUrl = pageUrl.lowercase()

        // Cloudflare 检测
        if (signalTypes.contains("cloudflare_challenge") ||
            lowerTitle.contains("cloudflare") ||
            lowerTitle.contains("just a moment") ||
            signalTypes.contains("block_page") && statusCode == 503
        ) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.CLOUDFLARE,
                confidence = 0.85f,
                matchedSignals = signals.filter { it.type in listOf("cloudflare_challenge", "block_page") },
            )
        }

        // Akamai 检测
        if (signalTypes.contains("akamai_block") || lowerTitle.contains("akamai")) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.AKAMAI,
                confidence = 0.8f,
                matchedSignals = signals.filter { it.type == "akamai_block" },
            )
        }

        // IP 被封禁
        if (signalTypes.contains("ip_banned") ||
            (statusCode == 403 && signalTypes.contains("block_page"))
        ) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.IP_BANNED,
                confidence = 0.8f,
                matchedSignals = signals.filter { it.type in listOf("ip_banned", "block_page") },
            )
        }

        // 频率限制
        if (signalTypes.contains("http_429") || statusCode == 429) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.RATE_LIMITED,
                confidence = 0.9f,
                matchedSignals = signals.filter { it.type == "http_429" },
            )
        }

        // 验证码
        if (signalTypes.contains("captcha_detected") ||
            lowerTitle.contains("captcha") ||
            lowerTitle.contains("验证")
        ) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.CAPTCHA,
                confidence = 0.85f,
                matchedSignals = signals.filter { it.type == "captcha_detected" },
            )
        }

        // 行为检测
        if (signalTypes.contains("behavioral_detected") ||
            signalTypes.contains("unusual_behavior")
        ) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.BEHAVIORAL,
                confidence = 0.7f,
                matchedSignals = signals.filter { it.type in listOf("behavioral_detected", "unusual_behavior") },
            )
        }

        // 指纹被标记
        if (signalTypes.contains("fingerprint_flagged")) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.FINGERPRINT_BLOCKED,
                confidence = 0.75f,
                matchedSignals = signals.filter { it.type == "fingerprint_flagged" },
            )
        }

        // 通用拦截页
        if (signalTypes.contains("block_page")) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.BLOCK_PAGE,
                confidence = 0.6f,
                matchedSignals = signals.filter { it.type == "block_page" },
            )
        }

        // 有信号但无法确定类型
        if (signals.isNotEmpty()) {
            return PatternDetectionResult(
                pattern = AntiBotPattern.UNKNOWN,
                confidence = 0.4f,
                matchedSignals = signals,
            )
        }

        // 无反爬
        return PatternDetectionResult(
            pattern = AntiBotPattern.NONE,
            confidence = 0.95f,
            matchedSignals = emptyList(),
        )
    }

    /**
     * 反爬模式识别结果。
     */
    data class PatternDetectionResult(
        val pattern: AntiBotPattern,
        val confidence: Float,
        val matchedSignals: List<SignalDetector.Signal>,
    )

    // ===== 策略选择（上下文老虎机） =====

    /**
     * 选择最优策略。
     *
     * 基于 ε-贪婪算法：
     * 1. 计算每个策略在当前上下文下的估计价值
     * 2. 以 1-ε 概率选择价值最高的策略（利用）
     * 3. 以 ε 概率随机选择（探索），优先选择使用次数少的策略
     *
     * @param pattern 当前反爬模式
     * @param consecutiveFailures 连续失败次数
     * @param currentFingerprintId 当前指纹 ID
     * @return 选中的策略名称
     */
    fun selectStrategy(
        pattern: AntiBotPattern,
        consecutiveFailures: Int = 0,
        currentFingerprintId: String? = null,
    ): String {
        // 探索：以 ε 概率随机选择，优先选择使用次数少的策略
        if (Math.random() < EPSILON) {
            val underExplored = availableStrategies.filter {
                (globalStats[it]?.totalUses ?: 0) < MIN_EXPLORE_COUNT
            }
            if (underExplored.isNotEmpty()) {
                return underExplored.random()
            }
            // 所有策略都有足够样本，完全随机
            return availableStrategies.random()
        }

        // 利用：选择估计价值最高的策略
        val scoredStrategies = availableStrategies.map { strategy ->
            val value = estimateValue(strategy, pattern, consecutiveFailures, currentFingerprintId)
            strategy to value
        }.sortedByDescending { it.second }

        return scoredStrategies.first().first
    }

    /**
     * 估计策略在当前上下文下的价值。
     *
     * 价值 = 全局平均奖励 × 0.3 + 模式匹配平均奖励 × 0.7 × 匹配权重
     * - 全局平均奖励：策略在所有场景下的平均表现
     * - 模式匹配平均奖励：策略在同类反爬模式下的平均表现（更相关）
     * - 匹配权重：如果策略与模式天然匹配，额外加权
     *
     * @param strategy 策略名称
     * @param pattern 当前反爬模式
     * @param consecutiveFailures 连续失败次数
     * @param currentFingerprintId 当前指纹 ID
     * @return 估计价值（越高越好）
     */
    private fun estimateValue(
        strategy: String,
        pattern: AntiBotPattern,
        consecutiveFailures: Int,
        currentFingerprintId: String?,
    ): Float {
        val globalAvg = globalStats[strategy]?.avgReward ?: 0f
        val patternAvg = patternStats[pattern.id]?.get(strategy)?.avgReward ?: 0f

        // 基础价值：全局 30% + 模式 70%
        var value = globalAvg * 0.3f + patternAvg * 0.7f

        // 模式-策略天然匹配加权
        val naturalMatch = isNaturalMatch(strategy, pattern)
        if (naturalMatch) {
            value *= PATTERN_MATCH_WEIGHT
        }

        // 连续失败多的时候，更倾向于激进策略（切换指纹/冷却）
        if (consecutiveFailures >= 3 && strategy in listOf("switch_fingerprint", "cool_down", "switch_and_slow")) {
            value += 0.2f
        }

        // 如果当前指纹已经被标记，切换指纹的价值更高
        if (pattern == AntiBotPattern.FINGERPRINT_BLOCKED && strategy == "switch_fingerprint") {
            value += 0.3f
        }

        return value
    }

    /**
     * 判断策略与反爬模式是否天然匹配。
     *
     * 基于领域知识的先验匹配：
     * - IP 封禁 → 冷却（等 IP 解封）或切换指纹
     * - 频率限制 → 降低频率
     * - 验证码 → 切换指纹（换身份）
     * - 行为检测 → 人类化行为
     * - 指纹被标记 → 切换指纹
     * - Cloudflare → 切换指纹 + 降低频率
     */
    private fun isNaturalMatch(strategy: String, pattern: AntiBotPattern): Boolean = when (pattern) {
        AntiBotPattern.IP_BANNED -> strategy in listOf("cool_down", "switch_fingerprint")
        AntiBotPattern.RATE_LIMITED -> strategy in listOf("slow_down", "cool_down")
        AntiBotPattern.CAPTCHA -> strategy in listOf("switch_fingerprint", "switch_and_slow")
        AntiBotPattern.BEHAVIORAL -> strategy in listOf("humanize_behavior", "slow_down")
        AntiBotPattern.FINGERPRINT_BLOCKED -> strategy == "switch_fingerprint"
        AntiBotPattern.CLOUDFLARE -> strategy in listOf("switch_and_slow", "switch_fingerprint")
        AntiBotPattern.AKAMAI -> strategy in listOf("switch_fingerprint", "humanize_behavior")
        AntiBotPattern.BLOCK_PAGE -> strategy in listOf("switch_fingerprint", "clear_cookies")
        AntiBotPattern.UNKNOWN -> strategy in listOf("switch_fingerprint", "slow_down")
        AntiBotPattern.NONE -> strategy == "keep_current"
    }

    // ===== 策略效果反馈 =====

    /**
     * 记录策略执行结果，更新统计。
     *
     * @param strategy 执行的策略
     * @param pattern 执行时的反爬模式
     * @param success 是否成功解决反爬
     * @param partial 是否部分成功（有改善但未完全解决）
     */
    fun recordResult(
        strategy: String,
        pattern: AntiBotPattern,
        success: Boolean,
        partial: Boolean = false,
    ) {
        val reward = when {
            success -> REWARD_SUCCESS
            partial -> REWARD_PARTIAL
            else -> REWARD_FAILURE
        }

        // 更新全局统计
        val global = globalStats.getOrPut(strategy) { StrategyStats(strategy = strategy) }
        global.totalUses++
        if (success) global.successes++ else global.failures++
        global.totalReward += reward

        // 更新模式分组统计
        val patternGroup = patternStats.getOrPut(pattern.id) { mutableMapOf() }
        val patternStat = patternGroup.getOrPut(strategy) { StrategyStats(strategy = strategy) }
        patternStat.totalUses++
        if (success) patternStat.successes++ else patternStat.failures++
        patternStat.totalReward += reward
    }

    // ===== 查询与分析 =====

    /**
     * 获取策略推荐解释。
     *
     * 返回当前推荐策略的原因解释，帮助模型理解为什么选择这个策略。
     *
     * @param pattern 当前反爬模式
     * @param consecutiveFailures 连续失败次数
     * @return 推荐策略及解释
     */
    fun getRecommendation(
        pattern: AntiBotPattern,
        consecutiveFailures: Int = 0,
    ): StrategyRecommendation {
        val strategy = selectStrategy(pattern, consecutiveFailures)
        val globalStat = globalStats[strategy]
        val patternStat = patternStats[pattern.id]?.get(strategy)

        val reason = buildString {
            append("检测到反爬模式：${pattern.displayName}（${pattern.description}）。")
            append("推荐策略：${strategyDisplayName(strategy)}。")
            if (isNaturalMatch(strategy, pattern)) {
                append("该策略与当前反爬模式天然匹配。")
            }
            if (consecutiveFailures >= 3) {
                append("连续失败 $consecutiveFailures 次，需要更激进的应对。")
            }
            if (globalStat != null && globalStat.totalUses > 0) {
                append("历史成功率：${(globalStat.successRate * 100).toInt()}%（${globalStat.totalUses}次）。")
            }
            if (patternStat != null && patternStat.totalUses > 0) {
                append("同类模式成功率：${(patternStat.successRate * 100).toInt()}%（${patternStat.totalUses}次）。")
            }
        }

        return StrategyRecommendation(
            strategy = strategy,
            pattern = pattern,
            reason = reason,
            globalSuccessRate = globalStat?.successRate ?: 0f,
            patternSuccessRate = patternStat?.successRate ?: 0f,
        )
    }

    /**
     * 策略推荐结果。
     */
    data class StrategyRecommendation(
        val strategy: String,
        val pattern: AntiBotPattern,
        val reason: String,
        val globalSuccessRate: Float,
        val patternSuccessRate: Float,
    )

    /**
     * 获取所有策略的统计摘要。
     */
    fun getStatsSummary(): Map<String, StrategyStats> = globalStats.toMap()

    /**
     * 重置所有统计（用于调试或新会话）。
     */
    fun reset() {
        globalStats.clear()
        patternStats.clear()
        availableStrategies.forEach { strategy ->
            globalStats[strategy] = StrategyStats(strategy = strategy)
        }
    }

    // ===== 辅助函数 =====

    private fun strategyDisplayName(strategy: String): String = when (strategy) {
        "switch_fingerprint" -> "切换指纹"
        "slow_down" -> "降低操作频率"
        "cool_down" -> "进入冷却期"
        "clear_cookies" -> "清理 Cookie"
        "switch_and_slow" -> "切换指纹并降低频率"
        "humanize_behavior" -> "人类化操作行为"
        "keep_current" -> "保持当前配置"
        else -> strategy
    }
}
