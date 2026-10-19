package com.mini.me_core.feature.agent.domain.core.provider

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 参考 opencode 的网络请求重试策略：指数退避，仅针对瞬时故障。
 */
private const val TAG = "RetryPolicy"

// 对应 opencode 的 attempts = 3（最多 2 次重试）→ 提升至 6 次重试以应对不稳定网络
const val MAX_NETWORK_RETRIES = 6

/**
 * 流式请求首字节等待超时：超过此时间未收到首个内容块即关闭流，触发可重试的 IOException。
 *
 * OkHttp 的 readTimeout（120s）是「相邻数据块之间」的等待上限，对慢启动/长思考留足空间；
 * 但首字节前若卡死，等待 120s 才超时体验过差，故用此应用层 watchdog 缩短到 60s。
 */
const val FIRST_BYTE_TIMEOUT_MS = 60_000L

/**
 * 首字节 watchdog 的时间下限：requestTimeout 配得再小，流式首字节等待也不低于此值。
 *
 * 几十万 token 的大上下文请求，供应商排队/慢启动经常超过 30s；watchdog 提前关流会把
 * 「服务端还在思考」误杀成连接中断（底层表现为 Software caused connection abort）。
 * 相邻数据块间隔仍由 OkHttp readTimeout（120s）兜底。
 */
const val MIN_FIRST_BYTE_TIMEOUT_MS = 60_000L

/**
 * 重试原因：UI 文案与故障转移策略都按原因分化，不再一律报「网络波动」。
 *
 * 历史教训：好网络下用户仍频繁看到「网络波动，正在重试」——其中一大类是 watchdog 把
 * 「服务端慢思考」误杀成连接中断。文案必须诚实区分：连接真的不稳 / 服务端响应慢 /
 * 触发限速，用户的行动完全不同。
 */
enum class RetryReason {
    /** 传输层故障：连接重置、DNS、TLS、stale 连接复用失败等。 */
    CONNECTION,

    /** 服务端响应慢：首字节超时（连接已建立，服务端在排队/思考）。 */
    SLOW_SERVER,

    /** 触发限流（429 / Retry-After）。 */
    RATE_LIMIT,

    /** 服务端过载/错误（5xx / 529 overloaded）。 */
    SERVER_BUSY,

    /** SSE 流内错误事件（可重试的那类）。 */
    STREAM_API,

    /** 未归类（保守按连接类文案展示）。 */
    UNKNOWN
}

/**
 * 把失败原因归类为 [RetryReason]（纯函数，可单测）。
 *
 * 与 [isRetriableNetworkError] 的分工：后者回答「该不该重试」，本函数回答「为什么重试」。
 */
fun classifyRetryReason(t: Throwable): RetryReason = when {
    t is CancellationException -> RetryReason.UNKNOWN
    t is FirstByteTimeoutException -> RetryReason.SLOW_SERVER
    t is StreamApiException -> RetryReason.STREAM_API
    t is HttpException -> when (t.code()) {
        429 -> RetryReason.RATE_LIMIT
        408 -> RetryReason.SLOW_SERVER
        in 500..599 -> RetryReason.SERVER_BUSY
        else -> RetryReason.UNKNOWN
    }
    t is SocketTimeoutException -> RetryReason.CONNECTION
    t is IOException -> RetryReason.CONNECTION
    else -> RetryReason.UNKNOWN
}

/**
 * 重试预算：给整条重试序列上三道闸，防止「重试风暴」把一次故障放大成分钟级卡顿。
 *
 * - [maxAttempts]：尝试次数上限（不含首次）；
 * - [maxDelayPerAttemptMs]：单次退避上限（jitter 后的封顶）；
 * - [maxTotalDelayMs]：整条序列累计退避上限——超过即放弃，把失败尽快交还给上层
 *   （UI 报错 + provider/endpoint 级故障转移），而不是让用户对着重试气泡干等。
 *
 *  Retry-After 是服务端指令，单次最多等 [MAX_RETRY_AFTER_MILLIS]，同样计入总预算。
 */
class RetryBudget(
    val maxAttempts: Int = MAX_NETWORK_RETRIES,
    val maxDelayPerAttemptMs: Long = DEFAULT_MAX_DELAY_PER_ATTEMPT_MS,
    val maxTotalDelayMs: Long = DEFAULT_MAX_TOTAL_DELAY_MS,
    private val random: Random = Random.Default
) {
    /** 已累计的退避等待（毫秒）。 */
    var totalDelayMs: Long = 0L
        private set

    /**
     * 本次失败是否允许继续重试。
     *
     * @param attempt 已失败次数（0 表示首次失败）
     * @param error 本次失败
     */
    fun allowsRetry(attempt: Int, error: Throwable): Boolean {
        if (attempt >= maxAttempts) return false
        if (!isRetriableNetworkError(error)) return false
        return totalDelayMs < maxTotalDelayMs
    }

    /**
     * 计算并「消费」本次退避时长：优先服务端 Retry-After（封顶后计入预算），
     * 否则 full jitter 指数退避（见 [backoffDelayMillis]）。
     */
    fun nextDelayMs(attempt: Int, error: Throwable): Long {
        val serverDelay = extractRetryAfterMillis(error)
        val wait = if (serverDelay != null && serverDelay > 0) {
            min(serverDelay, MAX_RETRY_AFTER_MILLIS)
        } else {
            backoffDelayMillis(attempt, maxDelayMs = maxDelayPerAttemptMs, random = random)
        }
        totalDelayMs += wait
        return wait
    }

    companion object {
        /** 单次退避封顶：10s（对齐历史 exponentialDelayMillis 的上限）。 */
        const val DEFAULT_MAX_DELAY_PER_ATTEMPT_MS = 10_000L

        /**
         * 累计退避预算：15s。6 次 full-jitter 退避的期望和约 7.5s、最坏 15s，
         * 与「指数退避总和」同量级；超限即放弃，交给上层故障转移/报错。
         */
        const val DEFAULT_MAX_TOTAL_DELAY_MS = 15_000L
    }
}

/**
 * Full jitter 指数退避（AWS 架构博客推荐的标准做法）：
 * `delay = random(0, min(cap, base * 2^attempt))`。
 *
 * 为什么必须有抖动：无抖动时所有客户端（或多个会话）在相同毫秒数后一起重试，
 * 形成「惊群」把刚恢复的服务再次打垮；full jitter 把重试均匀摊到整个窗口，
 * 同时期望延迟反而更低（约降 50%）。
 *
 * [random] 可注入以便测试钉住边界；生产传 [Random.Default]。
 */
fun backoffDelayMillis(
    retryIndex: Int,
    baseMs: Long = 500L,
    maxDelayMs: Long = 10_000L,
    random: Random = Random.Default
): Long {
    val ceiling = min(baseMs * 2.0.pow(retryIndex.coerceAtLeast(0)).toLong(), maxDelayMs)
    if (ceiling <= 0L) return 0L
    return random.nextLong(ceiling + 1) // [0, ceiling] 闭区间
}

/**
 * 启动首字节超时 watchdog（作为当前协程的子协程）：在超时后
 * 若 [isFirstByteReceived] 仍为 false，则调用 [close]（通常是关闭 ResponseBody），
 * 强制读取抛出 IOException 以被重试机制捕获。
 *
 * 调用方应在收到首个内容块后取消返回的 [Job]。
 */
suspend fun launchFirstByteWatchdog(
    close: () -> Unit,
    timeoutMs: Long = FIRST_BYTE_TIMEOUT_MS,
    isFirstByteReceived: () -> Boolean
): Job = CoroutineScope(coroutineContext[Job] ?: error("No Job in coroutineContext")).launch {
    delay(timeoutMs)
    if (!isFirstByteReceived()) {
        runCatching { close() }
    }
}

/**
 * 首字节 watchdog 主动关流导致的失败（区别于真实网络中断）。
 *
 * 继承 [IOException]：保持可重试语义（[streamWithStaircaseRetry] 视其为瞬时故障），
 * 同时让上层分类器（[LlmErrorClassifier]）能识别出「服务端慢」而非「网络断」。
 */
class FirstByteTimeoutException(cause: Throwable) : IOException("首字节等待超时（watchdog 主动关流）", cause)

/**
 * 渐进式首字节耐心：第 N 次尝试允许等多久首字节。
 *
 * 设计动机（「好网络仍报网络波动」的直接根因之一）：固定 60s 耐心会把
 * 「服务端还在排队/思考」误杀成连接中断——大上下文 + 思考模型首字节经常 1-3 分钟。
 * 误杀后重试还是 60s，于是一次次被砍，用户看到重试风暴后失败。
 *
 * 渐进策略：首次保持基线（该死得快，别让用户干等 60s+）；重试逐级加码
 * （120s → 180s 封顶），让「慢但活着」的服务端在第二次尝试成功，而不是烧完预算。
 * 真正死亡的连接通常在 TCP 层快速失败（RST / connect 超时），根本走不到 watchdog，
 * 因此加码不会显著拖长真故障的耗时。
 *
 * @param attempt 第几次尝试（0 起）
 * @param requestTimeoutSec 用户配置的流式首字节超时（秒）
 */
fun progressiveFirstByteTimeoutMs(attempt: Int, requestTimeoutSec: Int): Long {
    val base = (requestTimeoutSec.coerceAtLeast(0) * 1000L).coerceAtLeast(MIN_FIRST_BYTE_TIMEOUT_MS)
    return when {
        attempt <= 0 -> base
        attempt == 1 -> maxOf(base, 120_000L)
        else -> maxOf(base, 180_000L)
    }
}

/**
 * 把 watchdog 关流导致的失败包装为 [FirstByteTimeoutException]；非 watchdog 失败原样返回。
 *
 * 幂等：已是 [FirstByteTimeoutException] 或取消类异常时不重复包装。
 * 调用方在适配器最外层 catch 的入口调用，传入本次尝试是否被 watchdog 关流的标记。
 */
fun wrapWatchdogFailure(e: Throwable, fired: Boolean): Throwable =
    if (fired && e !is FirstByteTimeoutException && e !is CancellationException) {
        FirstByteTimeoutException(e)
    } else {
        e
    }

private val TRANSIENT_MESSAGES = listOf(
    "load failed",
    "network connection was lost",
    "network request failed",
    "failed to fetch",
    "econnreset",
    "econnrefused",
    "etimedout",
    "socket hang up"
)

/**
 * 对应 opencode 的 delay=500, factor=2, maxDelay=10000
 */
fun exponentialDelayMillis(retryIndex: Int): Long {
    val delay = 500L
    val factor = 2.0
    val maxDelay = 10000L
    val wait = (delay * factor.pow(retryIndex)).toLong()
    return min(wait, maxDelay)
}

/**
 * SSE 流中收到的 error 事件，携带错误码用于重试判定。
 *
 * 对齐 Codex CLI 的错误分类：cyber_policy / invalid_request / context_window_exceeded /
 * quota_exceeded / usage_not_included 等为不可重试；server_error / server_is_overloaded 等默认可重试。
 */
class StreamApiException(
    val code: String?,
    message: String,
    val retryAfterMillis: Long? = null
) : Exception(message.ifBlank { code ?: "stream error" })

// 对齐 Codex CLI is_retryable()：这些错误码明确不可重试
private val NON_RETRYABLE_STREAM_CODES = setOf(
    "cyber_policy",
    "invalid_request_error",
    "invalid_request",
    "context_window_exceeded",
    "quota_exceeded",
    "usage_not_included",
    "usage_limit_reached",
    "invalid_image_request"
)

/**
 * 对应 opencode 的 isTransientError，并兼容 Android 的网络异常类型。
 *
 * 除传统的 IOException 判定外，还支持 HTTP 状态码感知：
 * - 429（速率限制）、408（请求超时）→ 可重试
 * - 5xx（500/502/503/504 等）→ 可重试（服务端瞬时故障）
 * - 其他 4xx → 不重试（客户端错误，重试无意义）
 */
fun isRetriableNetworkError(t: Throwable): Boolean {
    if (t is CancellationException) return false

    if (t is StreamApiException) {
        return !NON_RETRYABLE_STREAM_CODES.contains(t.code)
    }

    // 兼容原生网络异常
    if (t is SocketTimeoutException || t is InterruptedIOException ||
        t is java.net.UnknownHostException || t is java.net.ConnectException ||
        t is javax.net.ssl.SSLException || t is IOException) {
        return true
    }

    // HTTP 状态码感知：429/408/5xx 视为瞬时故障可重试
    if (t is HttpException) {
        val code = t.code()
        return code == 429 || code == 408 || code >= 500
    }

    val message = t.message?.lowercase() ?: t.toString().lowercase()
    return TRANSIENT_MESSAGES.any { message.contains(it) }
}

/**
 * 从 [HttpException] 的响应头中解析 `Retry-After`，返回等待毫秒数。
 *
 * `Retry-After` 有两种格式：
 * 1. 秒数（如 `"30"`）→ 直接转为毫秒
 * 2. HTTP 日期（如 `"Fri, 29 Jun 2026 10:00:00 GMT"`）→ 计算距当前时间的差值
 *
 * 非 HttpException 或无 `Retry-After` 头部 → 返回 null。
 * 解析失败也返回 null（降级到指数退避）。
 */
fun extractRetryAfterMillis(t: Throwable): Long? {
    if (t !is HttpException) return null
    val header = t.response()?.headers()?.get("Retry-After") ?: return null

    // 格式 1：纯秒数
    header.toLongOrNull()?.let { seconds ->
        return seconds * 1000L
    }

    // 格式 2：HTTP 日期（RFC 1123）
    return runCatching {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val retryTime = sdf.parse(header)?.time ?: return null
        val delay = retryTime - System.currentTimeMillis()
        if (delay > 0) delay else null
    }.getOrNull()
}

/** Retry-After 头部值的上限，防止服务端返回过大的值导致无限等待。 */
const val MAX_RETRY_AFTER_MILLIS = 60_000L

/**
 * 在指数退避下重试 [block]（保持原方法名），用于非流式请求。
 *
 * 重试策略（2026-10 重写）：
 * - full jitter 指数退避（[backoffDelayMillis]）+ 429 尊重 Retry-After；
 * - [RetryBudget] 三道闸：次数 / 单次封顶 / 累计退避上限，超限即上抛，不拖分钟级卡顿；
 * - [onRetry] 增加 [RetryReason] 参数，UI 按真实原因出文案（不再一律「网络波动」）。
 *
 * @param onRetry 重试前回调 (当前重试次数, 最大重试次数, 原因)；用于通知上层"正在重试"。
 *                置于 [block] 之前以保证 `retryStaircase { ... }` 的 trailing lambda 仍绑定到 [block]。
 */
suspend fun <T> retryStaircase(
    onRetry: (suspend (attempt: Int, maxRetries: Int, reason: RetryReason) -> Unit)? = null,
    maxRetries: Int = MAX_NETWORK_RETRIES,
    budget: RetryBudget = RetryBudget(maxAttempts = maxRetries),
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            if (!budget.allowsRetry(attempt, e)) throw e
            val wait = budget.nextDelayMs(attempt, e)
            val reason = classifyRetryReason(e)
            FileLogger.w(TAG, "网络请求失败，第 ${attempt + 1}/$maxRetries 次重试（等待 ${wait}ms, $reason）: ${e.javaClass.simpleName} ${e.message}")
            onRetry?.invoke(attempt + 1, maxRetries, reason)
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}

/**
 * 流式请求的重试封装（保持原方法名）。
 *
 * 重试策略（2026-10 重写）：同 [retryStaircase]——full jitter + [RetryBudget] + reason。
 *
 * **吐字后不再重试**（修复历史 bug）：`attemptOnce` 内一旦调用过 [onProduced]
 * （已有 TextDelta/工具增量流出），本次失败直接上抛。LLM 流式响应无法断点续传，
 * 重来必然把已输出的文字重复一遍（用户看到内容翻倍、token 双倍计费）。
 * 首字节前失败才可安全重试——那时 UI 尚未展示任何正文。
 *
 * @param attemptOnce 单次尝试。参数 (attempt, onProduced)：attempt 为第几次尝试（0 起，
 *                    供渐进式首字节耐心 [progressiveFirstByteTimeoutMs] 使用）；
 *                    onProduced 必须在本轮流出任何内容后调用（标记「已吐字」）。
 * @param onRetry 重试前回调 (当前重试次数, 最大重试次数, 原因)。回调在 delay 之前调用，
 *                确保 UI 能立即展示重试状态。声明为 suspend 以便调用方在其中通过 Flow 的 emit() 推送重试事件。
 */
suspend fun streamWithStaircaseRetry(
    attemptOnce: suspend (attempt: Int, onProduced: () -> Unit) -> Unit,
    onRetry: (suspend (attempt: Int, maxRetries: Int, reason: RetryReason) -> Unit)? = null,
    maxRetries: Int = MAX_NETWORK_RETRIES,
    budget: RetryBudget = RetryBudget(maxAttempts = maxRetries)
) {
    var attempt = 0
    while (true) {
        val produced = AtomicBoolean(false)
        try {
            attemptOnce(attempt) { produced.set(true) }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            // 已吐字再失败 → 不重试：流式响应无法续传，重来必然重复输出。
            if (produced.get()) {
                FileLogger.w(TAG, "流式请求在已输出内容后失败，不重试（避免重复输出）: ${e.javaClass.simpleName} ${e.message}")
                throw e
            }
            if (!budget.allowsRetry(attempt, e)) throw e
            val wait = budget.nextDelayMs(attempt, e)
            val reason = classifyRetryReason(e)
            FileLogger.w(TAG, "流式请求失败，第 ${attempt + 1}/$maxRetries 次重试（等待 ${wait}ms, $reason）: ${e.javaClass.simpleName} ${e.message}")
            onRetry?.invoke(attempt + 1, maxRetries, reason)
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}
