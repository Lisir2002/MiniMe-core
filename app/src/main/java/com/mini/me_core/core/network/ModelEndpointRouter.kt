package com.mini.me_core.core.network

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType

/**
 * 模型端点路由器：「内置优化网络线路」的选路核心。
 *
 * 解决的体验问题：用户网络很好，但某个模型端点（中转站/镜像/官方域名）临时抽风，
 * 请求全部打向同一个不可达端点，用户看到「网络波动，正在重试」刷屏。
 * 本路由器为每次模型调用生成候选端点序列，并按 [EndpointHealthRegistry] 的
 * 健康状态选路：
 * 1. **用户配置的 baseUrl 永远首选**（除熔断中）——用户的显式选择优先级最高；
 * 2. **同类型官方端点**作为第二候选（用户配置的不是官方域名时）——同一把 API Key
 *    直接打官方域名，不引入任何未验证的第三方线路；
 * 3. `useFullUrl=true` 时用户填的是完整请求 URL（明确指定了某条线路），不换端点，
 *    原样返回用户配置——渐进式增强，绝不抢夺用户的显式配置。
 *
 * 健康数据由 [EndpointHealthRegistry] 维护：本路由器只读排序，不自己记账。
 */
class ModelEndpointRouter(
    private val healthRegistry: EndpointHealthRegistry
) {

    /**
     * 生成候选端点序列（有序）：[用户配置 URL, 同类型官方 URL（若不同且未配满）]。
     * 返回空列表表示无法路由（用户未配置 baseUrl）。
     */
    fun candidateEndpoints(config: AIProviderConfig): List<String> {
        val userUrl = config.baseUrl.trim()
        if (userUrl.isEmpty()) return emptyList()
        // 完整 URL 模式：用户明确指定了完整请求地址（含路径），不猜、不换。
        if (config.useFullUrl) return listOf(userUrl)
        val officialUrl = officialBaseUrl(config.type)
        return buildList {
            add(userUrl)
            // 官方端点仅在同类型且与用户配置不同（规范化比较）时作为备用候选。
            if (officialUrl != null && normalize(officialUrl) != normalize(userUrl)) {
                add(officialUrl)
            }
        }
    }

    /**
     * 选出本次请求应使用的端点。
     *
     * 默认返回用户配置 URL（健康分最高时原序不变——渐进式，存量行为零变化）；
     * 仅当用户配置端点熔断中且官方端点健康时才改选官方端点。
     */
    fun selectEndpoint(config: AIProviderConfig): String {
        val candidates = candidateEndpoints(config)
        if (candidates.isEmpty()) return config.baseUrl.trim()
        val ranked = healthRegistry.rankEndpoints(candidates)
        val chosen = ranked.firstOrNull() ?: return config.baseUrl.trim()
        if (normalize(chosen) != normalize(config.baseUrl)) {
            FileLogger.i(TAG, "首选端点不健康，改选备用线路: ${config.baseUrl} → $chosen")
        }
        return chosen
    }

    /**
     * 端点级故障转移：给定刚刚失败的端点，返回序列中的下一个健康端点；没有则 null。
     * 调用方（工作流传失败转移环）在首字节前失败时调用，实现「换一条线路再试」。
     */
    fun nextEndpoint(config: AIProviderConfig, failedUrl: String): String? {
        val candidates = candidateEndpoints(config)
        val failedIndex = candidates.indexOfFirst { normalize(it) == normalize(failedUrl) }
        if (failedIndex < 0) return null
        // 从失败点之后按健康分找第一个未熔断的端点。
        val remaining = candidates.drop(failedIndex + 1)
        if (remaining.isEmpty()) return null
        return healthRegistry.rankEndpoints(remaining).firstOrNull { !healthRegistry.isCircuitOpen(it) }
    }

    /** 同类型官方端点（仅厂商官方域名，不引入任何第三方中转）。 */
    private fun officialBaseUrl(type: ProviderType): String? = when (type) {
        ProviderType.OPENAI -> "https://api.openai.com/"
        ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
        ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/"
    }

    /** 规范化 URL 用于去重/比较：去空白、去尾部斜杠、小写。 */
    private fun normalize(url: String): String = url.trim().trimEnd('/').lowercase()

    private companion object {
        const val TAG = "EndpointRouter"
    }
}
