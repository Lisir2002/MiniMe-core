package com.mini.me_core.feature.agent.domain.core.provider

import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * LLM 失败归类测试（纯 JUnit4，无 Android 依赖）。
 *
 * 钉住三类规则：
 * 1. 异常类型直判（UnknownHost/ConnectException/SocketTimeout/SSL/Http/Stream/首字节超时）；
 * 2. enrich 包装形态（IllegalStateException 包 HttpException）必须靠 cause 链取到状态码；
 * 3. 文本兜底：OS 原文 "Software caused connection abort" 等必须命中对应类别。
 */
class LlmErrorClassifierTest {

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<Any>(code, ResponseBody.create(null, "error body")))

    // ──────────────────────── 1. 异常类型直判 ────────────────────────

    @Test
    fun unknownHost_isDns() {
        assertEquals(LlmErrorKind.DNS, LlmErrorClassifier.classify(UnknownHostException("api.example.com")))
    }

    @Test
    fun connectException_isConnectRefused() {
        assertEquals(
            LlmErrorKind.CONNECT_REFUSED,
            LlmErrorClassifier.classify(java.net.ConnectException("Connection refused"))
        )
    }

    @Test
    fun socketTimeout_isTimeout() {
        assertEquals(LlmErrorKind.TIMEOUT, LlmErrorClassifier.classify(SocketTimeoutException("read timed out")))
    }

    @Test
    fun sslException_isSsl() {
        assertEquals(
            LlmErrorKind.SSL,
            LlmErrorClassifier.classify(javax.net.ssl.SSLException("handshake failed"))
        )
    }

    @Test
    fun firstByteTimeoutException_isFirstByteTimeout() {
        assertEquals(
            LlmErrorKind.FIRST_BYTE_TIMEOUT,
            LlmErrorClassifier.classify(FirstByteTimeoutException(IOException("closed")))
        )
    }

    @Test
    fun streamApiException_isStreamError() {
        assertEquals(
            LlmErrorKind.STREAM_ERROR,
            LlmErrorClassifier.classify(StreamApiException("server_error", "boom"))
        )
    }

    @Test
    fun httpCodes_mapToKinds() {
        assertEquals(LlmErrorKind.HTTP_AUTH, LlmErrorClassifier.classify(httpError(401)))
        assertEquals(LlmErrorKind.HTTP_AUTH, LlmErrorClassifier.classify(httpError(403)))
        assertEquals(LlmErrorKind.HTTP_429, LlmErrorClassifier.classify(httpError(429)))
        assertEquals(LlmErrorKind.TIMEOUT, LlmErrorClassifier.classify(httpError(408)))
        assertEquals(LlmErrorKind.HTTP_5XX, LlmErrorClassifier.classify(httpError(500)))
        assertEquals(LlmErrorKind.HTTP_5XX, LlmErrorClassifier.classify(httpError(503)))
        // 其他 4xx 无对应类别 → UNKNOWN（UI 展示原文）
        assertEquals(LlmErrorKind.UNKNOWN, LlmErrorClassifier.classify(httpError(400)))
        assertEquals(LlmErrorKind.UNKNOWN, LlmErrorClassifier.classify(httpError(404)))
    }

    // ──────────────────────── 2. cause 链遍历（enrich 包装形态） ────────────────────────

    @Test
    fun enrichedHttpException_isResolvedThroughCauseChain() {
        // HttpErrorEnricher.enrichWithHttpErrorBody 的真实包装形态：
        // IllegalStateException("HTTP 401: …", cause = HttpException)
        val enriched = IllegalStateException("HTTP 401: Unauthorized body", httpError(401))
        assertEquals(LlmErrorKind.HTTP_AUTH, LlmErrorClassifier.classify(enriched))
    }

    @Test
    fun deepCauseChain_isResolved() {
        // 多层包装：RuntimeException → IllegalStateException → UnknownHostException
        val deep = RuntimeException("wrapper", IllegalStateException("mid", UnknownHostException("no dns")))
        assertEquals(LlmErrorKind.DNS, LlmErrorClassifier.classify(deep))
    }

    @Test
    fun causeChainCycle_terminatesWithoutHanging() {
        // 自引用环：a.cause = b, b.cause = a（用 public initCause 构造，不用反射）
        val a = IllegalStateException("a")
        val b = IllegalStateException("b")
        a.initCause(b)
        b.initCause(a)
        // 不应死循环；环上无 HttpException/类型命中，最终走文本兜底
        val kind = LlmErrorClassifier.classify(a)
        assertTrue(kind == LlmErrorKind.UNKNOWN || kind == LlmErrorKind.STREAM_ERROR)
    }

    // ──────────────────────── 3. 文本兜底 ────────────────────────

    @Test
    fun messageFallback_softwareCausedConnectionAbort() {
        // 用户实际遇到的报错原文（OkHttp 透传 OS ECONNABORTED）
        assertEquals(
            LlmErrorKind.NETWORK_ABORT,
            LlmErrorClassifier.classify(IOException("Software caused connection abort"))
        )
    }

    @Test
    fun messageFallback_commonOsTexts() {
        assertEquals(
            LlmErrorKind.NETWORK_RESET,
            LlmErrorClassifier.classify(IOException("Connection reset by peer"))
        )
        assertEquals(
            LlmErrorKind.NETWORK_ABORT,
            LlmErrorClassifier.classify(IOException("connection was aborted"))
        )
        assertEquals(
            LlmErrorKind.DNS,
            LlmErrorClassifier.classify(IOException("Unable to resolve host \"api.example.com\""))
        )
        assertEquals(
            LlmErrorKind.TIMEOUT,
            LlmErrorClassifier.classify(IOException("failed to connect after 30000ms (timed out)"))
        )
        assertEquals(
            LlmErrorKind.SSL,
            LlmErrorClassifier.classify(IOException("SSL handshake aborted: ssl=0x…"))
        )
        assertEquals(
            LlmErrorKind.CONNECT_REFUSED,
            LlmErrorClassifier.classify(IOException("failed to connect to /10.0.0.1 (port 443) after 5000ms: ECONNREFUSED"))
        )
    }

    @Test
    fun nullThrowable_isUnknown() {
        assertEquals(LlmErrorKind.UNKNOWN, LlmErrorClassifier.classify(null))
    }

    @Test
    fun unrecognizedException_isUnknown() {
        assertEquals(LlmErrorKind.UNKNOWN, LlmErrorClassifier.classify(IllegalStateException("bad state")))
    }

    @Test
    fun cancellationException_isUnknown_notRetriedAsNetwork() {
        // CancellationException 不是网络错误，归类 UNKNOWN（重试决策由 isRetriableNetworkError 负责）
        assertEquals(
            LlmErrorKind.UNKNOWN,
            LlmErrorClassifier.classify(kotlinx.coroutines.CancellationException("stopped"))
        )
    }
}
