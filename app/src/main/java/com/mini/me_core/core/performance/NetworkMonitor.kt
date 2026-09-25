package com.mini.me_core.core.performance

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * F6.6 网络请求监控（仅 debug 构建采集）。
 *
 * - [NetworkMonitor] 单例保存最近 [MAX_RECORDS] 条请求记录（时间倒序）与聚合统计。
 * - [NetworkMonitorInterceptor] 是 OkHttp Interceptor，在 debug 构建被加入主 OkHttpClient；
 *   release 构建不加入（或加 No-op），零开销、零隐私泄漏。
 * - 请求/响应头中的 Authorization 等敏感字段在记录时脱敏。
 */
object NetworkMonitor {

    private const val MAX_RECORDS = 100

    data class RequestRecord(
        val id: Long,
        val method: String,
        val url: String,
        val statusCode: Int,
        val durationMs: Long,
        val requestSizeBytes: Long,
        val responseSizeBytes: Long,
        val error: String? = null,
        val requestHeaders: Map<String, String> = emptyMap(),
        val responseHeaders: Map<String, String> = emptyMap(),
    )

    private val records: MutableList<RequestRecord> = Collections.synchronizedList(ArrayList())

    @Synchronized
    fun add(record: RequestRecord) {
        records.add(0, record)
        if (records.size > MAX_RECORDS) {
            // 保留前 MAX_RECORDS（最新的）。
            while (records.size > MAX_RECORDS) records.removeAt(records.size - 1)
        }
    }

    @Synchronized
    fun snapshot(): List<RequestRecord> = records.toList()

    @Synchronized
    fun clear() = records.clear()

    data class Stats(
        val total: Int,
        val successRate: Int,
        val avgDurationMs: Long,
        val slowestTop5: List<RequestRecord>,
    )

    @Synchronized
    fun stats(): Stats {
        if (records.isEmpty()) return Stats(0, 100, 0L, emptyList())
        val ok = records.count { it.error == null && it.statusCode in 200..399 }
        val avg = records.map { it.durationMs }.average().toLong()
        val slowest = records.sortedByDescending { it.durationMs }.take(5)
        return Stats(
            total = records.size,
            successRate = (ok * 100 / records.size),
            avgDurationMs = avg,
            slowestTop5 = slowest,
        )
    }
}

/** 敏感请求头脱敏：Authorization / Cookie / Set-Cookie 等只保留是否存在。 */
private fun redactHeaders(headers: okhttp3.Headers): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (name in headers.names()) {
        val lower = name.lowercase()
        out[name] = if (lower == "authorization" || lower == "cookie" || lower == "set-cookie" || lower.contains("token")) {
            "***"
        } else {
            headers.get(name).orEmpty()
        }
    }
    return out
}

/** OkHttp 拦截器：记录每个请求的耗时/状态/大小。仅 debug 加入客户端。 */
class NetworkMonitorInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val reqSize = (request.body?.contentLength() ?: 0L).coerceAtLeast(0L)
        var error: String? = null
        var response: Response? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (e: IOException) {
            error = e.javaClass.simpleName + ": " + (e.message ?: "")
            throw e
        } finally {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            val respHeaders = response?.headers ?: okhttp3.Headers.Builder().build()
            val respSize = response?.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
            NetworkMonitor.add(
                NetworkMonitor.RequestRecord(
                    id = startNs,
                    method = request.method,
                    url = request.url.toString(),
                    statusCode = response?.code ?: -1,
                    durationMs = durationMs,
                    requestSizeBytes = reqSize,
                    responseSizeBytes = respSize,
                    error = error,
                    requestHeaders = redactHeaders(request.headers),
                    responseHeaders = redactHeaders(respHeaders),
                )
            )
        }
    }
}
