package com.mini.me_core.core.network

import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端点路由器测试：候选生成（用户配置首选/官方次选/useFullUrl 直通）、
 * 健康选路、故障转移。钉死「用户显式配置永远优先」的渐进式语义。
 */
class ModelEndpointRouterTest {

    private fun config(
        baseUrl: String,
        type: ProviderType = ProviderType.OPENAI,
        useFullUrl: Boolean = false
    ) = AIProviderConfig(
        id = "p1",
        name = "test",
        type = type,
        apiKey = "k",
        baseUrl = baseUrl,
        defaultModel = "m",
        isActive = true,
        useFullUrl = useFullUrl
    )

    private val now = 1_000_000L

    @Test
    fun candidates_userUrlFirst_officialSecond() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cands = router.candidateEndpoints(config("https://relay.example.com/v1"))
        assertEquals(listOf("https://relay.example.com/v1", "https://api.openai.com/"), cands)
    }

    @Test
    fun candidates_userUrlAlreadyOfficial_singleCandidate() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cands = router.candidateEndpoints(config("https://api.openai.com/"))
        assertEquals(listOf("https://api.openai.com/"), cands)
    }

    @Test
    fun candidates_officialMatch_ignoresTrailingSlashAndCase() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cands = router.candidateEndpoints(config("https://API.OpenAI.com"))
        assertEquals("规范化后与官方相同 → 不产生重复候选", 1, cands.size)
    }

    @Test
    fun candidates_useFullUrl_returnsUserUrlOnly() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cands = router.candidateEndpoints(
            config("https://relay.example.com/v1/chat/completions", useFullUrl = true)
        )
        assertEquals("完整 URL 模式不换线路", listOf("https://relay.example.com/v1/chat/completions"), cands)
    }

    @Test
    fun candidates_blankBaseUrl_empty() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        assertTrue(router.candidateEndpoints(config("  ")).isEmpty())
    }

    @Test
    fun candidates_anthropicAndGemini_officialUrls() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        assertEquals(
            "https://api.anthropic.com/",
            router.candidateEndpoints(config("https://relay.example.com", type = ProviderType.ANTHROPIC))[1]
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/",
            router.candidateEndpoints(config("https://relay.example.com", type = ProviderType.GEMINI))[1]
        )
    }

    @Test
    fun selectEndpoint_healthyUserUrl_keepsUserChoice() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cfg = config("https://relay.example.com/v1")
        assertEquals("无健康数据时保持用户配置（存量行为不变）", cfg.baseUrl, router.selectEndpoint(cfg))
    }

    @Test
    fun selectEndpoint_userUrlCircuitOpen_switchesToOfficial() {
        val registry = EndpointHealthRegistry()
        repeat(EndpointHealthRegistry.FAILURE_THRESHOLD) {
            registry.recordFailure("https://relay.example.com/v1", now)
        }
        val router = ModelEndpointRouter(registry)
        val chosen = router.selectEndpoint(config("https://relay.example.com/v1"))
        assertEquals("用户端点熔断 → 改选官方线路", "https://api.openai.com/", chosen)
    }

    @Test
    fun nextEndpoint_returnsOfficialAfterUserUrlFails() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cfg = config("https://relay.example.com/v1")
        val next = router.nextEndpoint(cfg, failedUrl = "https://relay.example.com/v1")
        assertEquals("https://api.openai.com/", next)
    }

    @Test
    fun nextEndpoint_noMoreCandidates_returnsNull() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cfg = config("https://api.openai.com/")
        assertNull(router.nextEndpoint(cfg, failedUrl = "https://api.openai.com/"))
    }

    @Test
    fun nextEndpoint_useFullUrl_returnsNull() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        val cfg = config("https://relay.example.com/v1/chat/completions", useFullUrl = true)
        assertNull("完整 URL 模式不做端点级转移", router.nextEndpoint(cfg, failedUrl = cfg.baseUrl))
    }

    @Test
    fun nextEndpoint_unknownFailedUrl_returnsNull() {
        val router = ModelEndpointRouter(EndpointHealthRegistry())
        assertNull(router.nextEndpoint(config("https://relay.example.com/v1"), failedUrl = "https://other.example/"))
    }
}
