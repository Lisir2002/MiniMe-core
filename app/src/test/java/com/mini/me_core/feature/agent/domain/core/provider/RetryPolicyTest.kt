package com.mini.me_core.feature.agent.domain.core.provider

import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络重试策略纯逻辑测试：指数退避序列、瞬时错误分类、Retry-After 降级。
 *
 * 不真正发请求，只钉住「哪些错该重试、等多久」的决策规则。
 */
class RetryPolicyTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, ResponseBody.create(null, "error body")))

    @Test
    fun exponentialDelay_startsAt500AndDoubles() {
        assertEquals(500L, exponentialDelayMillis(0))
        assertEquals(1000L, exponentialDelayMillis(1))
        assertEquals(2000L, exponentialDelayMillis(2))
        assertEquals(4000L, exponentialDelayMillis(3))
    }

    @Test
    fun exponentialDelay_capsAt10Seconds() {
        // 第 5 次起理论值已超 10000ms，应被截断。
        assertEquals(10_000L, exponentialDelayMillis(5))
        assertEquals(10_000L, exponentialDelayMillis(10))
    }

    @Test
    fun cancellation_isNeverRetriable() {
        assertFalse(isRetriableNetworkError(kotlinx.coroutines.CancellationException("cancelled")))
    }

    @Test
    fun streamApi_nonRetryableCodes_areNotRetriable() {
        assertFalse(isRetriableNetworkError(StreamApiException("cyber_policy", "blocked")))
        assertFalse(isRetriableNetworkError(StreamApiException("invalid_request", "bad")))
        assertFalse(isRetriableNetworkError(StreamApiException("context_window_exceeded", "too big")))
        assertFalse(isRetriableNetworkError(StreamApiException("quota_exceeded", "no quota")))
    }

    @Test
    fun streamApi_unknownOrNullCode_isRetriable() {
        assertTrue(isRetriableNetworkError(StreamApiException("server_error", "boom")))
        assertTrue(isRetriableNetworkError(StreamApiException("server_is_overloaded", "busy")))
        assertTrue(isRetriableNetworkError(StreamApiException(null, "generic stream error")))
    }

    @Test
    fun nativeIoExceptions_areRetriable() {
        assertTrue(isRetriableNetworkError(SocketTimeoutException("read timed out")))
        assertTrue(isRetriableNetworkError(UnknownHostException("no dns")))
        assertTrue(isRetriableNetworkError(IOException("connection reset")))
    }

    @Test
    fun http429And5xx_areRetriable_butOther4xxAreNot() {
        assertTrue(isRetriableNetworkError(httpError(429)))
        assertTrue(isRetriableNetworkError(httpError(408)))
        assertTrue(isRetriableNetworkError(httpError(500)))
        assertTrue(isRetriableNetworkError(httpError(503)))
        assertTrue(isRetriableNetworkError(httpError(502)))

        assertFalse(isRetriableNetworkError(httpError(400)))
        assertFalse(isRetriableNetworkError(httpError(404)))
        assertFalse(isRetriableNetworkError(httpError(401)))
    }

    @Test
    fun transientMessageFallback_isRetriable() {
        assertTrue(isRetriableNetworkError(RuntimeException("java.net.SocketException: network connection was lost")))
        assertTrue(isRetriableNetworkError(RuntimeException("ECONNRESET by peer")))
    }

    @Test
    fun plainDomainException_isNotRetriable() {
        assertFalse(isRetriableNetworkError(IllegalStateException("bad state")))
        assertFalse(isRetriableNetworkError(IllegalArgumentException("bad arg")))
    }

    @Test
    fun extractRetryAfter_nonHttpException_returnsNull() {
        assertNull(extractRetryAfterMillis(IOException("io")))
        assertNull(extractRetryAfterMillis(RuntimeException("x")))
    }
}
