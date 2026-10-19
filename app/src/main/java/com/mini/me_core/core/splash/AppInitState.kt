package com.mini.me_core.core.splash

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用初始化状态门闩：控制启动画面（Splash Screen）的保持与退出。
 *
 * 阻塞启动画面的初始化项：数据层（崩溃恢复、AGENT库预热）、主题加载、核心服务（凭据桥接、MCP）。
 * 不阻塞：模型元数据刷新、连接预热、文档提取等异步预热。
 *
 * 超时保护：最长 1.5 秒，超时自动 markReady，避免初始化异常时启动画面卡死。
 */
object AppInitState {

    private const val TIMEOUT_MS = 1500L

    @Volatile
    var isReady: Boolean = false
        private set

    private val marked = AtomicBoolean(false)

    private val timeoutHandler = Handler(Looper.getMainLooper())

    // 就绪信号量：markReady / 超时兜底任一触发即 complete。
    // 晚于两者调用的 awaitReady 立即返回（CompletableDeferred 已完成）。
    private val readyDeferred = CompletableDeferred<Unit>()

    private val timeoutRunnable = Runnable {
        if (marked.compareAndSet(false, true)) {
            isReady = true
            readyDeferred.complete(Unit)
        }
    }

    init {
        // 类加载即启动超时计时：Application 构造时本对象被首次引用，
        // 此时距冷启动已极近，1.5s 从这里算合理。
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    /**
     * 标记初始化完成，启动画面将在下一帧退出。
     * 幂等：多次调用只生效一次。
     */
    fun markReady() {
        if (marked.compareAndSet(false, true)) {
            isReady = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            readyDeferred.complete(Unit)
        }
    }

    /**
     * 挂起等待初始化就绪。
     *
     * 正常由 [markReady] 唤醒；初始化异常时由类加载即启动的 1.5s 超时兜底唤醒，
     * 因此本函数必会在 TIMEOUT_MS 内返回，调用方无需再套超时
     * （防御性 withTimeoutOrNull 仅防未来改动破坏该不变量）。
     */
    suspend fun awaitReady() {
        withTimeoutOrNull(TIMEOUT_MS * 2) {
            readyDeferred.await()
        }
    }
}

/**
 * A1 共享退场门闩（挂起）：等初始化就绪 → 补足最短展示 → 退场；
 * [maxTotalMs] 为硬上限，就绪异常不达时也能收尾。
 *
 * 由 MINIMAL / CLASSIC 两类「非逐帧」启动风格复用。
 * 调用方需已在 LaunchedEffect 中播放完淡入动画。
 */
internal suspend fun awaitReadyThenDismiss(
    onDismiss: () -> Unit,
    maxTotalMs: Long,
) {
    val startMs = android.os.SystemClock.elapsedRealtime()
    withTimeoutOrNull(maxTotalMs) {
        AppInitState.awaitReady()
        val waited = android.os.SystemClock.elapsedRealtime() - startMs
        if (waited < MIN_DISPLAY_MS) delay(MIN_DISPLAY_MS - waited)
    }
    onDismiss()
}
