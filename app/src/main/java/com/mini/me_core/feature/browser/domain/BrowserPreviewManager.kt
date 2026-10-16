package com.mini.me_core.feature.browser.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.webkit.WebView
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 浏览器预览帧数据：包含压缩后的 JPEG 字节、页面元信息和时间戳。
 *
 * 使用 ByteArray 而非 Bitmap 以降低内存占用，UI 层解码时按需缩放显示。
 */
data class PreviewFrame(
    /** 压缩后的 JPEG 字节数组 */
    val jpegBytes: ByteArray,
    /** 当前页面 URL */
    val url: String,
    /** 当前页面标题 */
    val title: String,
    /** 帧捕获时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewFrame) return false
        return jpegBytes.contentEquals(other.jpegBytes) &&
            url == other.url &&
            title == other.title &&
            timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * 浏览器预览截图管理器（单例）。
 *
 * 职责：定期截取当前 WebView 画面并压缩为 JPEG，通过 [previewState] StateFlow
 * 分发最新帧供 UI 层展示实时预览面板。
 *
 * 设计要点：
 *  - 使用 [WebView.draw] 截取画面（无需 PixelCopy 权限，兼容性更好）；
 *  - 压缩为 JPEG（质量 60，最大宽度 800px）降低内存与传输开销；
 *  - 仅在模型操作浏览器时（[BrowserController.agentStatus].active=true）捕获，空闲时停止；
 *  - UI 层通过 collect [previewState] 拿到最新帧并渲染。
 */
@Singleton
class BrowserPreviewManager @Inject constructor(
    private val browserController: BrowserController
) {
    private companion object {
        const val TAG = "BrowserPreviewManager"
        /** 截图 JPEG 压缩质量 */
        const val JPEG_QUALITY = 60
        /** 预览帧最大宽度（px），高度按比例缩放 */
        const val MAX_WIDTH_PX = 800
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _previewState = MutableStateFlow<PreviewFrame?>(null)
    /** 最新预览帧 StateFlow，UI 收集后显示实时截图 */
    val previewState: StateFlow<PreviewFrame?> = _previewState.asStateFlow()

    /** 定时截图协程 Job，null 表示未在捕获 */
    private var captureJob: Job? = null

    /**
     * 开始定期捕获预览截图。
     *
     * @param webView 要截取的 WebView 实例
     * @param intervalMs 截图间隔（毫秒），默认 1000ms
     */
    fun startCapturing(webView: WebView, intervalMs: Long = 1000) {
        if (captureJob?.isActive == true) return
        captureJob = scope.launch {
            while (true) {
                // 仅在模型操作浏览器时捕获，空闲时跳过节省资源
                if (browserController.agentStatus.value.active) {
                    captureNow(webView)
                }
                delay(intervalMs)
            }
        }
        FileLogger.d(TAG, "开始捕获预览截图，间隔 ${intervalMs}ms")
    }

    /** 停止定期捕获，释放截图协程。 */
    fun stopCapturing() {
        captureJob?.cancel()
        captureJob = null
        _previewState.value = null
        FileLogger.d(TAG, "停止捕获预览截图")
    }

    /**
     * 手动触发一次截图。
     *
     * 在后台线程截取 WebView 画面，压缩为 JPEG 后更新 [previewState]。
     * 如果 WebView 不可用或宽高为 0，静默跳过。
     *
     * **线程纪律**：[WebView.draw] 及 width/height 读取必须在其创建线程（主线程）执行，
     * 与 [BrowserController] 的全部 WebView 访问一致。历史上本方法直接在 IO 协程里 draw，
     * 跨线程调用被 catch 静默吞掉 → [previewState] 永远为 null → 预览面板只剩空底色，
     * 表现为「AI 接管浏览器后悬浮预览每次空白」。
     */
    suspend fun captureNow(webView: WebView) {
        try {
            val frame = withContext(Dispatchers.Main.immediate) {
                val width = webView.width
                val height = webView.height
                if (width <= 0 || height <= 0) return@withContext null

                // 按最大宽度等比缩放，避免大图占用过多内存
                val scale = MAX_WIDTH_PX.toFloat() / width
                val bitmap = Bitmap.createScaledBitmap(
                    captureWebViewBitmap(webView, width, height),
                    MAX_WIDTH_PX,
                    (height * scale).toInt(),
                    true
                )

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
                bitmap.recycle()

                PreviewFrame(
                    jpegBytes = baos.toByteArray(),
                    url = webView.url ?: "",
                    title = webView.title ?: ""
                )
            }
            if (frame != null) {
                _previewState.value = frame
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "预览截图失败: ${e.message}")
        }
    }

    /**
     * 使用 WebView.draw(Canvas) 截取原始尺寸画面。
     *
     * 在主线程执行 draw，确保 WebView 渲染状态一致。
     */
    private fun captureWebViewBitmap(webView: WebView, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val background = webView.background
        if (background != null) {
            background.draw(canvas)
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
        }
        webView.draw(canvas)
        return bitmap
    }
}
