package com.mini.me_core.feature.agent.domain.core.provider

import kotlinx.coroutines.runBlocking
import okhttp3.Request
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
import kotlin.random.Random

/**
 * 网络重试策略纯逻辑测试：指数退避序列、瞬时错误分类、Retry-After 降级。
 *
 * 不真正发请求，只钉住「哪些错该重试、等多久」的决策规则。
 */
class RetryPolicyTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, ResponseBody.create(null, "error body")))

    private fun httpErrorWithRetryAfter(code: Int, retryAfter: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("err")
            .header("Retry-After", retryAfter)
            .build()
        return HttpException(Response.error<Any>(ResponseBody.create(null, "x"), raw))
    }

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

    // ──────────────────────── watchdog fired 标记 → FirstByteTimeoutException ────────────────────────

    @Test
    fun wrapWatchdogFailure_fired_wrapsIntoFirstByteTimeout() {
        val raw = IOException("Software caused connection abort")
        val wrapped = wrapWatchdogFailure(raw, fired = true)
        assertTrue("watchdog 关流必须包装为 FirstByteTimeoutException", wrapped is FirstByteTimeoutException)
        assertTrue("原异常应保留在 cause 链", wrapped.cause === raw)
    }

    @Test
    fun wrapWatchdogFailure_notFired_passesThrough() {
        val raw = IOException("Software caused connection abort")
        val same = wrapWatchdogFailure(raw, fired = false)
        assertTrue("非 watchdog 失败必须原样返回", same === raw)
    }

    @Test
    fun wrapWatchdogFailure_alreadyWrapped_isIdempotent() {
        val first = FirstByteTimeoutException(IOException("closed"))
        val again = wrapWatchdogFailure(first, fired = true)
        assertTrue("已包装过不得重复包装", again === first)
    }

    @Test
    fun wrapWatchdogFailure_cancellation_isNeverWrapped() {
        // 用户点停止导致的取消不得被包装成超时错误
        val cancel = kotlinx.coroutines.CancellationException("stopped")
        assertTrue(wrapWatchdogFailure(cancel, fired = true) === cancel)
    }

    @Test
    fun minFirstByteTimeout_is60Seconds() {
        // 钉住下限常量：watchdog 不得短于 60s（长上下文模型首字节可能超过 30s）
        assertEquals(60_000L, MIN_FIRST_BYTE_TIMEOUT_MS)
    }

    // ──────────────────────── full jitter 退避（2026-10 重写） ────────────────────────

    /** 恒返回 0 的 Random：把 jitter 钉在下界。 */
    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(until: Long): Long = 0
    }

    /** 恒返回 until-1 的 Random：把 jitter 钉在上界（= ceiling）。 */
    private class CeilingRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(until: Long): Long = until - 1
    }

    @Test
    fun backoff_fullJitter_lowerBoundIsZero() {
        assertEquals(0L, backoffDelayMillis(0, random = ZeroRandom()))
        assertEquals(0L, backoffDelayMillis(7, random = ZeroRandom()))
    }

    @Test
    fun backoff_fullJitter_upperBoundIsExponentialCeilingCapped() {
        // 上界 = min(base * 2^n, cap)：500 / 1000 / 2000 / 4000 / 8000 / 封顶 10000
        assertEquals(500L, backoffDelayMillis(0, random = CeilingRandom()))
        assertEquals(1_000L, backoffDelayMillis(1, random = CeilingRandom()))
        assertEquals(2_000L, backoffDelayMillis(2, random = CeilingRandom()))
        assertEquals(4_000L, backoffDelayMillis(3, random = CeilingRandom()))
        assertEquals(8_000L, backoffDelayMillis(4, random = CeilingRandom()))
        assertEquals(10_000L, backoffDelayMillis(5, random = CeilingRandom()))
        assertEquals(10_000L, backoffDelayMillis(10, random = CeilingRandom()))
    }

    @Test
    fun backoff_fullJitter_spreadsAcrossWindow() {
        // jitter 的意义：同一次失败，不同客户端/会话散落在整个窗口内，防惊群。
        val random = Random(42) // 循环外单实例：连续采样才有分布
        val samples = (0 until 200).map {
            backoffDelayMillis(4, baseMs = 500, maxDelayMs = 10_000, random = random)
        }
        assertTrue("jitter 必须产生多个不同值", samples.distinct().size > 1)
        assertTrue("全部落在 [0, 8000] 窗口内", samples.all { it in 0..8_000 })
        assertTrue("窗口内应出现较小值（期望延迟低于无 jitter）", samples.any { it < 4_000 })
    }

    @Test
    fun backoff_negativeRetryIndex_treatedAsZero() {
        assertEquals(500L, backoffDelayMillis(-3, random = CeilingRandom()))
    }

    // ──────────────────────── RetryBudget 三道闸 ────────────────────────

    @Test
    fun budget_attemptCountCap() {
        val budget = RetryBudget(maxAttempts = 2, maxTotalDelayMs = 60_000)
        assertTrue(budget.allowsRetry(0, IOException("a")))
        assertTrue(budget.allowsRetry(1, IOException("b")))
        assertFalse("超过次数上限必须停止", budget.allowsRetry(2, IOException("c")))
    }

    @Test
    fun budget_nonRetriableError_stopsImmediately() {
        val budget = RetryBudget(maxAttempts = 6)
        assertFalse(budget.allowsRetry(0, IllegalArgumentException("client bug")))
    }

    @Test
    fun budget_totalDelayCap_stopsAfterCumulativeWait() {
        // CeilingRandom → 每次拿满窗口；累计超过 15s 预算后必须放弃（交给上层故障转移/报错）。
        val budget = RetryBudget(maxAttempts = 10, maxTotalDelayMs = 15_000, random = CeilingRandom())
        var attempt = 0
        while (budget.allowsRetry(attempt, IOException("flaky"))) {
            budget.nextDelayMs(attempt, IOException("flaky"))
            attempt++
        }
        assertTrue("累计退避达到预算上限", budget.totalDelayMs >= 15_000)
    }

    @Test
    fun budget_retryAfter_isHonoredAndCountsTowardTotal() {
        val budget = RetryBudget(maxAttempts = 6, maxTotalDelayMs = 60_000, random = ZeroRandom())
        // Retry-After: 30 → 服务端指令优先于 jitter
        assertEquals(30_000L, budget.nextDelayMs(0, httpErrorWithRetryAfter(429, "30")))
        assertEquals(30_000L, budget.totalDelayMs)
    }

    @Test
    fun budget_retryAfter_isCappedAt60Seconds() {
        val budget = RetryBudget(maxAttempts = 6, maxTotalDelayMs = 120_000, random = ZeroRandom())
        assertEquals(MAX_RETRY_AFTER_MILLIS, budget.nextDelayMs(0, httpErrorWithRetryAfter(429, "120")))
    }

    @Test
    fun budget_invalidRetryAfter_fallsBackToJitter() {
        val budget = RetryBudget(maxAttempts = 6, random = ZeroRandom())
        // 解析失败 → 降级 full jitter（ZeroRandom → 0）
        assertEquals(0L, budget.nextDelayMs(0, httpErrorWithRetryAfter(429, "soon")))
    }

    // ──────────────────────── 渐进式首字节耐心 ────────────────────────

    @Test
    fun progressivePatience_escalatesOnRetries() {
        // 首次该死得快；重试加码让「慢但活着」的服务端第二次成功。
        assertEquals(60_000L, progressiveFirstByteTimeoutMs(0, requestTimeoutSec = 60))
        assertEquals(120_000L, progressiveFirstByteTimeoutMs(1, requestTimeoutSec = 60))
        assertEquals(180_000L, progressiveFirstByteTimeoutMs(2, requestTimeoutSec = 60))
        assertEquals(180_000L, progressiveFirstByteTimeoutMs(5, requestTimeoutSec = 60))
    }

    @Test
    fun progressivePatience_neverBelow60SecondsFloor() {
        assertEquals(60_000L, progressiveFirstByteTimeoutMs(0, requestTimeoutSec = 10))
        assertEquals(60_000L, progressiveFirstByteTimeoutMs(0, requestTimeoutSec = 0))
        assertEquals(120_000L, progressiveFirstByteTimeoutMs(1, requestTimeoutSec = 10))
    }

    @Test
    fun progressivePatience_userConfigWinsWhenLarger() {
        // 用户显式配了 300s：以用户为准，不被 180s 封顶压低。
        assertEquals(300_000L, progressiveFirstByteTimeoutMs(0, requestTimeoutSec = 300))
        assertEquals(300_000L, progressiveFirstByteTimeoutMs(3, requestTimeoutSec = 300))
    }

    // ──────────────────────── RetryReason 分类 ────────────────────────

    @Test
    fun classifyReason_firstByteTimeout_isSlowServer() {
        // 「好网络仍报波动」的核心误判项：watchdog 砍的是慢服务端，不是网络。
        assertTrue(classifyRetryReason(FirstByteTimeoutException(IOException("abort"))) == RetryReason.SLOW_SERVER)
    }

    @Test
    fun classifyReason_httpCodes() {
        assertEquals(RetryReason.RATE_LIMIT, classifyRetryReason(httpError(429)))
        assertEquals(RetryReason.SLOW_SERVER, classifyRetryReason(httpError(408)))
        assertEquals(RetryReason.SERVER_BUSY, classifyRetryReason(httpError(500)))
        assertEquals(RetryReason.SERVER_BUSY, classifyRetryReason(httpError(503)))
        assertEquals(RetryReason.UNKNOWN, classifyRetryReason(httpError(400)))
        assertEquals(RetryReason.UNKNOWN, classifyRetryReason(httpError(401)))
    }

    @Test
    fun classifyReason_transportErrors_areConnection() {
        assertEquals(RetryReason.CONNECTION, classifyRetryReason(SocketTimeoutException("timed out")))
        assertEquals(RetryReason.CONNECTION, classifyRetryReason(IOException("connection reset")))
        assertEquals(RetryReason.CONNECTION, classifyRetryReason(UnknownHostException("dns")))
    }

    @Test
    fun classifyReason_streamApiAndCancellationAndUnknown() {
        assertEquals(RetryReason.STREAM_API, classifyRetryReason(StreamApiException("server_error", "boom")))
        assertEquals(RetryReason.UNKNOWN, classifyRetryReason(kotlinx.coroutines.CancellationException("c")))
        assertEquals(RetryReason.UNKNOWN, classifyRetryReason(IllegalStateException("bad state")))
    }

    // ──────────────────────── 重试封装行为（吐字后不重试 / 预算上抛） ────────────────────────

    @Test
    fun stream_afterProducingOutput_isNeverRetried() = runBlocking {
        // 修复历史 bug：流式响应无法续传，重来必然把已输出文字重复一遍（内容翻倍+双倍计费）。
        var calls = 0
        val failure = IOException("reset mid-stream")
        val thrown = runCatching {
            streamWithStaircaseRetry(
                attemptOnce = { _, onProduced ->
                    calls++
                    onProduced()
                    throw failure
                },
                budget = RetryBudget(maxAttempts = 6, random = ZeroRandom())
            )
        }.exceptionOrNull()
        assertTrue("必须原样上抛底层失败", thrown === failure)
        assertEquals("吐字后不得重试", 1, calls)
    }

    @Test
    fun stream_failureBeforeFirstByte_retriesAndReportsReason() = runBlocking {
        var calls = 0
        val reasons = mutableListOf<RetryReason>()
        streamWithStaircaseRetry(
            attemptOnce = { attempt, _ ->
                calls++
                if (attempt == 0) throw SocketTimeoutException("connect timeout")
            },
            onRetry = { _, _, reason -> reasons.add(reason) },
            budget = RetryBudget(maxAttempts = 6, random = ZeroRandom())
        )
        assertEquals("首字节前失败应重试一次后成功", 2, calls)
        assertEquals(listOf(RetryReason.CONNECTION), reasons)
    }

    @Test
    fun retryStaircase_succeedsAfterTransientFailure() = runBlocking {
        var calls = 0
        val result = retryStaircase(
            onRetry = { _, _, _ -> },
            budget = RetryBudget(maxAttempts = 6, random = ZeroRandom())
        ) {
            calls++
            if (calls == 1) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun retryStaircase_budgetExhausted_rethrowsLastError() = runBlocking {
        val boom = IOException("always down")
        val thrown = runCatching {
            retryStaircase(budget = RetryBudget(maxAttempts = 2, maxTotalDelayMs = 60_000, random = ZeroRandom())) {
                throw boom
            }
        }.exceptionOrNull()
        assertTrue("预算耗尽必须上抛最后一次失败", thrown === boom)
    }
}
