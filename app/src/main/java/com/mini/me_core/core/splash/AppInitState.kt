package com.mini.me_core.core.splash

import android.os.Handler
import android.os.Looper
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

    private val timeoutRunnable = Runnable {
        if (marked.compareAndSet(false, true)) {
            isReady = true
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
        }
    }
}
