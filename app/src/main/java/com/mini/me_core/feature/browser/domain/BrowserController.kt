package com.mini.me_core.feature.browser.domain

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置服务浏览器核心控制器（单例）。
 *
 * 职责：持有唯一的 [WebView] 实例，同时服务于
 *  - 用户侧：[ServiceBrowserScreen] 通过 [bind]/[unbind] 挂载/卸载同一 WebView；
 *  - 模型侧：[BrowserAgentTool] 通过 suspend 操作（navigate/snapshot/click/type/...）驱动页面。
 *
 * 线程模型：WebView 所有操作必须在主线程执行。本类把每个操作封成 suspend 函数，
 * 内部统一调度到主线程（[withContext](Dispatchers.Main) 或主 Handler post），
 * 工具侧在 IO 协程调用即可。
 *
 * 关键设计：模型与用户共享同一个浏览会话 —— 同一份 Cookie、同一个页面，
 * 用户登录后模型自动复用登录态。
 */
@Singleton
class BrowserController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pathMapper: WorkspacePathMapper,
    private val proxyManager: ClashProxyManager,
    private val okHttp: OkHttpClient,
    private val historyStore: BrowserHistoryStore,
    private val bookmarkStore: BrowserBookmarkStore,
    private val downloadManager: BrowserDownloadManager,
    private val adBlocker: AdBlocker,
    private val webTranslator: WebTranslator
) {
    private companion object {
        const val TAG = "BrowserController"

        /** 页面纯文本截断上限。 */
        const val MAX_PAGE_TEXT = 12000

        /** 快照元素数量上限：超大页面只取前N个可交互控件。 */
        const val MAX_SNAPSHOT_ELEMENTS = 300

        /** 导航协议白名单：拦截危险scheme。 */
        val ALLOWED_SCHEMES = setOf("http", "https")

        /** 桌面版网页UA。 */
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /** 内部标签：id + WebView + 标题/URL 缓存。 */
    private class BrowserTab(
        val id: String,
        val webView: WebView,
        var title: String = "",
        var url: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 原始移动 UA（R1.3 桌面版切换）：首个 WebView 创建时记录，桌面版关闭后恢复。 */
    @Volatile
    private var originalUserAgent: String? = null

    // 代理开关变化时，让已存在的 WebView 也跟上 mihomo 代理（新标签/导航天然生效）。
    init {
        scope.launch {
            proxyManager.state.collect { applyWebViewProxy(null) }
        }
    }

    // ─────────────────────── WebView 代理接管 ───────────────────────

    /**
     * 把 WebView 的网络流量接入 mihomo 代理。WebView 不认 Java ProxySelector，只能靠
     * [ProxyController.setProxyOverride] 做进程级代理覆盖。
     * 代理开启时指向 mihomo mixed-port 并放行 loopback/内网（容器开发服务直连），
     * 关闭时清除覆盖回退系统默认网络。内部统一 post 到主线程执行。
     */
    private fun applyWebViewProxy(webView: WebView?) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            FileLogger.w(TAG, "当前 WebView 不支持 PROXY_OVERRIDE，外网访问将走系统默认网络")
            return
        }
        // ProxyController 内部与 WebView 提供方通信，统一调度到主线程避免线程问题。
        val syncExecutor = Executor { it.run() }
        mainHandler.post {
            runCatching {
                if (proxyManager.isEnabled()) {
                    val config = ProxyConfig.Builder()
                        .addProxyRule("127.0.0.1:${ClashProxyManager.MIXED_PORT}")
                        .addBypassRule("localhost")
                        .addBypassRule("127.0.0.1")
                        .addBypassRule("10.*")
                        .addBypassRule("192.168.*")
                        .addBypassRule("172.16.*")
                        .build()
                    ProxyController.getInstance().setProxyOverride(config, syncExecutor, Runnable {})
                } else {
                    ProxyController.getInstance().clearProxyOverride(syncExecutor, Runnable {})
                }
            }.onFailure {
                FileLogger.w(TAG, "设置 WebView 代理失败: ${it.message}")
            }
        }
    }

    /** 全部标签页（每个标签独占一个 WebView，保留各自历史栈/会话/Cookie）。 */
    private val tabs = mutableListOf<BrowserTab>()

    /** 当前激活标签 id。 */
    private var activeTabId: String? = null

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /** F4.4 当前页是否为可阅读文章。 */
    private val _readerAvailable = MutableStateFlow(false)
    val readerAvailable: StateFlow<Boolean> = _readerAvailable.asStateFlow()

    /** F4.5 当前页已拦截的广告/跟踪器数量。 */
    val blockedCount: StateFlow<Int> = adBlocker.blockedCount

    /** F4.6 当前页面语言（document.lang）。 */
    private val _pageLang = MutableStateFlow("")
    val pageLang: StateFlow<String> = _pageLang.asStateFlow()

    /** F4.7 开发者工具：Console 日志（最近 200 条）。 */
    private val _consoleLogs = MutableStateFlow<List<String>>(emptyList())
    val consoleLogs: StateFlow<List<String>> = _consoleLogs.asStateFlow()

    /** F4.8 手势操作设置。 */
    data class GestureSettings(
        val edgeSwipe: Boolean = true,
        val pullToRefresh: Boolean = true,
        val doubleTapZoom: Boolean = true,
        /** 0=低 1=中 2=高 */
        val sensitivity: Int = 1
    )
    private val _gestureSettings = MutableStateFlow(GestureSettings())
    val gestureSettings: StateFlow<GestureSettings> = _gestureSettings.asStateFlow()

    fun setGestureSettings(s: GestureSettings) { _gestureSettings.value = s }

    /** F4.8 当前可见 WebView 的纵向滚动偏移（用于下拉刷新判断是否在顶部）。 */
    fun activeWebViewScrollY(): Int = activeWebView()?.scrollY ?: 0

    /** F4.5 广告拦截与隐私设置（UI 读取/开关用）。 */
    fun privacy(): AdBlocker = adBlocker

    private val _tabsState = MutableStateFlow<List<BrowserTabInfo>>(emptyList())
    /** 标签列表（UI 标签栏与 list_tabs 工具共用）。 */
    val tabsState: StateFlow<List<BrowserTabInfo>> = _tabsState.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentBrowserStatus())
    val agentStatus: StateFlow<AgentBrowserStatus> = _agentStatus.asStateFlow()

    /** AI 操作历史（R3 操作时间线）：最多保留 100 条，供 UI AI 助手面板展示。 */
    private val _agentActionHistory = MutableStateFlow<List<AgentActionRecord>>(emptyList())
    val agentActionHistory: StateFlow<List<AgentActionRecord>> = _agentActionHistory.asStateFlow()

    /** 追加一条 AI 操作记录（自动截断到 100 条）。敏感信息需在调用方脱敏后传入。 */
    private fun recordAction(action: String, description: String, success: Boolean = true, error: String = "") {
        val record = AgentActionRecord(
            action = action,
            description = description,
            timestamp = System.currentTimeMillis(),
            success = success,
            error = error
        )
        val current = _agentActionHistory.value
        val updated = if (current.size >= 100) current.drop(1) + record else current + record
        _agentActionHistory.value = updated
    }

    /** 清空 AI 操作历史。 */
    fun clearAgentActionHistory() {
        _agentActionHistory.value = emptyList()
    }

    /** 下载任务列表（F4.2 委托给 [BrowserDownloadManager]）。 */
    val downloads: StateFlow<List<BrowserDownloadInfo>> = downloadManager.downloads

    /** 未完成下载数（F4.2 底栏角标）。 */
    val activeDownloadCount: StateFlow<Int> = downloadManager.activeCount

    /** 待确认下载（WebView 触发后弹确认框）。 */
    val pendingDownload: StateFlow<com.mini.me_core.feature.browser.domain.PendingBrowserDownload?> = downloadManager.pendingDownload

    /** 进度弹窗中展示的下载任务 ID。 */
    val dialogDownloadId: StateFlow<String?> = downloadManager.dialogDownloadId

    private val _pendingDialog = MutableStateFlow<PendingBrowserDialog?>(null)
    val pendingDialog: StateFlow<PendingBrowserDialog?> = _pendingDialog.asStateFlow()

    private var dialogResult: JsResult? = null
    private var dialogDeferred: CompletableDeferred<Boolean>? = null

    /** upload_file：模型请求上传时暂存目标文件；onShowFileChooser 触发时用 FileProvider Uri 回填。 */
    @Volatile
    private var pendingUploadFile: File? = null

    @Volatile
    private var pendingUploadDone: CompletableDeferred<Boolean>? = null

    /** 最近一次快照，供 get_attribute 等操作参考。 */
    @Volatile
    private var lastSnapshot: BrowserPageSnapshot = BrowserPageSnapshot()

    /**
     * 增量快照基线（R2.1）：最近一次写操作后的快照，供 computeDelta 计算前后差异。
     * 导航后重置（新页面与旧页面不可比）。
     */
    @Volatile
    private var deltaBaseline: BrowserPageSnapshot? = null

    /**
     * 最近一次写操作后的增量结果（R2.1/R2.3 写操作自动验证）：
     * 写操作内部计算「基线 → 操作后」差异后填充，工具侧读取并随 envelope 返回给模型。
     */
    @Volatile
    private var lastDelta: BrowserSnapshotDelta? = null

    /** 最近动作日志（R2.3 动作历史）：操作 + 结果摘要，最多保留 30 条。 */
    private val actionLog = ArrayDeque<String>()

    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────── UI 绑定 ───────────────────────────

    /**
     * 浏览器页挂载 WebView。创建（首次）或复用已有实例，配置好后返回给 Compose AndroidView。
     * 必须在主线程调用（Compose AndroidView factory 即主线程）。
     */
    /** 确保存在一个激活标签（浏览器页组合前调用；避免 composition 期间创建标签引发 key 跳变崩溃）。 */
    fun ensureActiveTab() {
        if (tabs.isEmpty()) {
            val tab = createTab(null)
            activeTabId = tab.id
            applyActiveTab(tab)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun bind(): WebView {
        ensureActiveTab()
        val active = activeTab() ?: createTab(null).also {
            activeTabId = it.id
            applyActiveTab(it)
        }
        _uiState.value = _uiState.value.copy(screenVisible = true)
        // 共享 WebView 复用前必须先摘除旧父容器，否则 Compose AndroidView 二次 addView 会抛
        // IllegalStateException("The specified child already has a parent ...")。
        detachFromParent(active.webView)
        return active.webView
    }

    /** 浏览器页卸载时调用（不销毁 WebView，保留会话/Cookie）。 */
    fun unbind() {
        // 摘除激活 WebView，避免离开页面后仍挂在旧 AndroidViewHolder 上，下次返回时重复挂载崩溃。
        activeWebView()?.let(::detachFromParent)
        _uiState.value = _uiState.value.copy(screenVisible = false)
    }

    /** 后退（用户侧 UI）。 */
    fun goBack() {
        mainHandler.post { activeWebView()?.let { if (it.canGoBack()) it.goBack() } }
    }

    /** 前进（用户侧 UI）。 */
    fun goForward() {
        mainHandler.post { activeWebView()?.let { if (it.canGoForward()) it.goForward() } }
    }

    /** 刷新当前页（用户侧 UI）。 */
    fun reload() {
        mainHandler.post { activeWebView()?.reload() }
    }

    /** 停止加载（用户侧 UI）。 */
    fun stopLoading() {
        mainHandler.post { activeWebView()?.stopLoading() }
    }

    /** 应用退出/不再需要时销毁所有 WebView。 */
    fun destroy() {
        mainHandler.post {
            tabs.forEach { detachAndDestroy(it.webView) }
            tabs.clear()
            activeTabId = null
            _tabsState.value = emptyList()
        }
    }

    // ─────────────────────── 用户侧 API（R1 更像浏览器） ───────────────────────

    /** 访问历史（R1.1 历史记录）：时间倒序。 */
    fun history(): List<BrowserHistoryEntry> = historyStore.entries()

    /** 清空访问历史（R1.1）。 */
    fun clearHistory() = historyStore.clear()

    /** 收藏夹（R1.1 收藏夹）：时间倒序。 */
    fun bookmarks(): List<BrowserBookmark> = bookmarkStore.bookmarks()

    /** 当前 URL 是否已收藏（R1.1）。 */
    fun isBookmarked(url: String): Boolean = bookmarkStore.contains(url)

    /** 收藏当前页面（R1.1）；返回是否新增成功（同 URL 已收藏返回 false）。 */
    fun addBookmark(): Boolean = bookmarkStore.add(_uiState.value.title, _uiState.value.currentUrl)

    /** 取消收藏指定 URL（R1.1）。 */
    fun removeBookmark(url: String) = bookmarkStore.remove(url)

    /** 清除下载列表（R1.2 / F4.2 下载管理 UI）。 */
    fun clearDownloads() = downloadManager.clear()

    /** 下载任务的宿主文件（供 UI「打开」下载文件用）；未完成或路径无效返回 null。 */
    fun downloadHostFile(info: BrowserDownloadInfo): File? = downloadManager.hostFile(info)

    /** 暂停下载（F4.2）。 */
    fun pauseDownload(id: String) = downloadManager.pause(id)

    /** 继续下载（F4.2）。 */
    fun resumeDownload(id: String) = downloadManager.resume(id)

    /** 取消下载（F4.2）。 */
    fun cancelDownload(id: String) = downloadManager.cancel(id)

    /** 删除下载记录与文件（F4.2）。 */
    fun deleteDownload(id: String) = downloadManager.delete(id)

    /** 重试下载（R1.2 / F4.2）：按原 URL 重新发起下载任务。 */
    fun retryDownload(info: BrowserDownloadInfo) = downloadManager.retry(info)

    /** 用户确认下载（WebView 触发的下载确认弹窗）。 */
    fun confirmPendingDownload() = downloadManager.confirmPendingDownload()

    /** 用户取消下载确认弹窗。 */
    fun cancelPendingDownload() = downloadManager.cancelPendingDownload()

    /** 关闭下载进度弹窗。 */
    fun dismissDialogDownload() = downloadManager.dismissDialogDownload()

    /**
     * 无痕模式（R1.3 无痕模式）：会话级开关。
     * 开启/关闭时都清空 Cookie 与缓存（无痕会话不落痕；关闭后不残留本次会话的 cookie），
     * 无痕期间不记录访问历史。
     */
    fun setIncognito(on: Boolean) {
        mainHandler.post {
            val cm = CookieManager.getInstance()
            runCatching { cm.removeAllCookies(null) }
            runCatching { activeWebView()?.clearCache(true) }
            _uiState.value = _uiState.value.copy(incognito = on)
        }
    }

    /**
     * 桌面版网页切换（R1.3 桌面版切换）：切到桌面 UA 后重载当前页。
     * 存储桌面 UA（[DESKTOP_UA]），非桌面态使用 WebView 默认移动 UA。
     */
    fun toggleDesktopMode() {
        val next = !_uiState.value.desktopMode
        mainHandler.post {
            activeWebView()?.let { wv ->
                if (next) {
                    wv.settings.userAgentString = DESKTOP_UA
                } else {
                    // 恢复移动 UA：默认 UA（去 wv 标记）或保存的原始 UA
                    wv.settings.userAgentString = originalUserAgent ?: wv.settings.userAgentString.replace(DESKTOP_UA, "")
                }
                _uiState.value = _uiState.value.copy(desktopMode = next)
                wv.reload()
            }
        }
    }

    /** 页面缩放（R1.3 缩放控制）：textZoom 百分比（100 默认，范围 50–200）。 */
    fun setTextZoom(percent: Int) {
        val clamped = percent.coerceIn(50, 200)
        mainHandler.post {
            activeWebView()?.settings?.textZoom = clamped
            _uiState.value = _uiState.value.copy(textZoom = clamped)
        }
    }

    /** 页内查找（R1.1 页内查找）：findAllAsync 高亮全部匹配。 */
    fun findOnPage(text: String) {
        if (text.isBlank()) {
            clearFindOnPage()
            return
        }
        mainHandler.post { activeWebView()?.findAllAsync(text) }
    }

    /** 页内查找（R1.1）：在匹配结果中上/下切换高亮。 */
    fun findNextOnPage(forward: Boolean) {
        mainHandler.post { activeWebView()?.findNext(forward) }
    }

    /** 页内查找（R1.1）：清除高亮。 */
    fun clearFindOnPage() {
        mainHandler.post { activeWebView()?.clearMatches() }
    }

    /** F4.4 阅读模式：提取当前页正文，回调主线程。 */
    fun extractReaderContent(onResult: (ReaderArticle) -> Unit) {
        mainHandler.post {
            val wv = activeWebView() ?: run { onResult(ReaderArticle()); return@post }
            wv.evaluateJavascript(ReaderMode.JS_EXTRACT) { raw ->
                onResult(ReaderMode.parse(raw))
            }
        }
    }

    /** F4.4 阅读模式：重新检测当前页是否可阅读。 */
    fun detectReaderAvailable() {
        mainHandler.post {
            val wv = activeWebView() ?: run { _readerAvailable.value = false; return@post }
            wv.evaluateJavascript(ReaderMode.JS_DETECT) { raw ->
                _readerAvailable.value = raw?.trim() == "true"
            }
        }
    }

    /** F4.6 翻译整页正文：提取正文后逐段翻译，回调译文（纯文本拼接）。 */
    fun translateCurrentPage(targetLang: String, onResult: (String) -> Unit) {
        mainHandler.post {
            val wv = activeWebView() ?: run { onResult(""); return@post }
            wv.evaluateJavascript(ReaderMode.JS_EXTRACT) { raw ->
                val article = ReaderMode.parse(raw)
                scope.launch {
                    val sb = StringBuilder()
                    article.blocks.filter { it.type == "p" }.forEach { block ->
                        val t = webTranslator.translate(block.text, targetLang)
                        sb.append(t).append("\n\n")
                    }
                    onResult(sb.toString().trim())
                }
            }
        }
    }

    /** F4.6 划词翻译：读取当前选中文字并翻译，回调 (原文, 译文)。 */
    fun translateSelection(targetLang: String, onResult: (String, String) -> Unit) {
        mainHandler.post {
            val wv = activeWebView() ?: run { onResult("", ""); return@post }
            wv.evaluateJavascript("(function(){return window.getSelection().toString();})();") { raw ->
                val sel = raw?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"").orEmpty()
                if (sel.isBlank()) { onResult("", ""); return@evaluateJavascript }
                scope.launch {
                    val translated = webTranslator.translate(sel, targetLang)
                    onResult(sel, translated)
                }
            }
        }
    }

    // ─────────────────────── F4.7 开发者工具 ───────────────────────

    /** 清空 Console 日志。 */
    fun clearConsole() { _consoleLogs.value = emptyList() }

    /** Console 执行 JS（eval），回传结果。 */
    fun evalJs(code: String, onResult: (String) -> Unit) {
        mainHandler.post {
            val wv = activeWebView() ?: run { onResult(""); return@post }
            // 回显执行的命令到 Console
            _consoleLogs.update { (it + "> $code").takeLast(200) }
            wv.evaluateJavascript("(function(){try{return eval(${JsonPrimitive(code)});}catch(e){return 'Error: '+e.message;}})();") { raw ->
                val out = raw ?: "undefined"
                _consoleLogs.update { (it + out).takeLast(200) }
                onResult(out)
            }
        }
    }

    /** Network 记录：从页面 window.__rcb_net 读取最近请求，回调列表。 */
    fun networkRecords(onResult: (List<BrowserNetworkRecord>) -> Unit) {
        val js = "JSON.stringify((window.__rcb_net || []).slice(-50).reverse())"
        mainHandler.post {
            activeWebView()?.evaluateJavascript(js) { raw ->
                onResult(decodeNetworkList(raw ?: "[]"))
            } ?: onResult(emptyList())
        }
    }

    /** DOM 树摘要（标签统计 + 顶层结构概览）。 */
    fun domSummary(onResult: (String) -> Unit) {
        val js = """
            (function() {
              var counts = {};
              document.querySelectorAll('*').forEach(function(el){ counts[el.tagName]=(counts[el.tagName]||0)+1; });
              var arr = Object.keys(counts).map(function(k){return k+':'+counts[k];}).sort();
              return JSON.stringify({title: document.title, total: document.querySelectorAll('*').length, tags: arr});
            })();
        """.trimIndent()
        mainHandler.post {
            activeWebView()?.evaluateJavascript(js) { onResult(it?.trim().orEmpty()) } ?: onResult("")
        }
    }

    /** Cookie + LocalStorage 概览。 */
    fun storageDump(onResult: (String) -> Unit) {
        val js = """
            (function() {
              var ls = [];
              for (var i=0;i<localStorage.length;i++){var k=localStorage.key(i);ls.push(k+'='+localStorage.getItem(k));}
              return JSON.stringify({cookie: document.cookie || '(none)', localStorage: ls});
            })();
        """.trimIndent()
        mainHandler.post {
            activeWebView()?.evaluateJavascript(js) { onResult(it?.trim().orEmpty()) } ?: onResult("")
        }
    }

    /**
     * F4.3 自动填充：在当前页面注入 JS，把用户名/密码填入匹配的输入框。
     * 按常见选择器找用户名框（email/tel/user/account）与密码框（type=password）。
     */
    fun autoFillCredentials(username: String, password: String) {
        mainHandler.post {
            val wv = activeWebView() ?: return@post
            val js = """
                (function() {
                  function setVal(el, v) {
                    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    setter.call(el, v);
                    el.dispatchEvent(new Event('input', {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                  }
                  var pass = document.querySelector('input[type=password]');
                  var user = document.querySelector('input[type=email], input[type=tel], input[name*="user" i], input[name*="email" i], input[name*="account" i], input[id*="user" i], input[id*="email" i]');
                  if (user) setVal(user, ${JsonPrimitive(username)});
                  if (pass) setVal(pass, ${JsonPrimitive(password)});
                  return JSON.stringify({user: !!user, pass: !!pass});
                })();
            """.trimIndent()
            wv.evaluateJavascript(js, null)
        }
    }

    // ─────────────────────── 模型/工具操作（suspend） ───────────────────────

    /** 规范化 URL：无协议时自动补 https://。 */
    fun normalizeUrl(url: String): String =
        if (url.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) url else "https://$url"

    /** 校验导航目标：返回 null 表示安全，否则返回拦截原因（供工具侧返回错误）。 */
    fun validateUrl(url: String): String? {
        val normalized = normalizeUrl(url)
        val scheme = runCatching { URI(normalized).scheme }.getOrNull()?.lowercase()
            ?: return "无法解析 URL：$url"
        if (scheme !in ALLOWED_SCHEMES) {
            return "已拦截导航：不允许的协议 '$scheme'（仅允许 http/https）。"
        }
        return null
    }

    /** 打开 URL 并返回页面快照。危险协议会被拒绝加载。 */
    suspend fun navigate(url: String): BrowserPageSnapshot = mutex.withLock {
        val normalized = normalizeUrl(url)
        val blocked = validateUrl(normalized)
        if (blocked != null) {
            FileLogger.w(TAG, blocked)
            recordAction("navigate", "导航到 $url", success = false, error = blocked)
            return@withLock BrowserPageSnapshot(url = "", pageText = blocked)
        }
        _agentStatus.value = AgentBrowserStatus("正在导航到 $url", true)
        try {
            withContext(Dispatchers.Main) { ensureWebView().loadUrl(normalized) }
            waitForPageSettled(30_000)
            // 智能等待页面真正就绪（网络空闲 + DOM 稳定 + 关键元素出现），超时降级不阻塞导航主流程
            runCatching { waitForPageReady(3000) }
            // 导航到新页面：重置增量基线与上次增量（新旧页面不可比），下次写操作后重新建立。
            deltaBaseline = null
            lastDelta = null
            val snap = snapshotInternal(SnapshotLevel.SUMMARY)
            recordAction("navigate", "导航到 $url")
            snap
        } catch (e: Exception) {
            recordAction("navigate", "导航到 $url", success = false, error = e.message ?: "未知错误")
            throw e
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 获取当前页面快照（元素树 + 文本）。
     * @param level 快照分级
     * @param autoScroll 是否自动滚动到底部加载更多内容（懒加载页面），滚动完成后重新获取快照
     */
    suspend fun snapshot(level: SnapshotLevel = SnapshotLevel.FULL, autoScroll: Boolean = false): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在提取页面结构", true)
        try {
            if (autoScroll) autoScrollLoad()
            val snap = snapshotInternal(level)
            recordAction("snapshot", "获取页面快照（${level.name.lowercase()}级${if (autoScroll) ", 自动滚动加载" else ""}），${snap.elements.size}个可交互元素")
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 单独取页面纯文本（R2.1 按需取文）：模型需要正文时再取，不再随快照默认返回。 */
    suspend fun pageText(): String = mutex.withLock {
        evalJs(BrowserJsScripts.JS_PAGE_TEXT).take(MAX_PAGE_TEXT)
    }

    /**
     * 四级定位解析：把模型传入的 element_id（role+name / data-rcb-id / CSS / 旧版语义）
     * 解析成元素的 data-rcb-id。
     * @return null 表示全部解析失败；否则返回（id, 命中方式, 匹配数）。
     */
    suspend fun resolveElementId(locator: String): ResolvedElement? = mutex.withLock {
        val raw = evalJs("($BrowserJsScripts.JS_LOCATE)(${quote(locator)})")
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return@withLock null
        val ok = runCatching { (obj["ok"] as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
        if (!ok) return@withLock null
        val id = (obj["id"] as? JsonPrimitive)?.content ?: return@withLock null
        val method = (obj["method"] as? JsonPrimitive)?.content ?: "id"
        val matchCount = runCatching { (obj["matchCount"] as? JsonPrimitive)?.content?.toInt() }.getOrNull() ?: 1
        ResolvedElement(id, method, matchCount)
    }

    /**
     * 元素可交互性等待：最多等待 timeoutMs，每 intervalMs 重试 BrowserJsScripts.JS_ACTIONABILITY。
     * 元素不在视口内时先 scrollIntoView。skipOverlap=true 时跳过遮挡检查（输入框可能被键盘遮挡）。
     * @return null 表示超时仍不可交互；否则返回失败原因字符串（成功时返回 null）。
     */
    private suspend fun waitForActionable(id: String, skipOverlap: Boolean = false, timeoutMs: Long = 5000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastReason: String? = null
        while (System.currentTimeMillis() < deadline) {
            val raw = evalJs("($BrowserJsScripts.JS_ACTIONABILITY)(${quote(id)}, ${if (skipOverlap) "true" else "false"})")
            val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            val ok = runCatching { (obj?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
            if (ok) return null
            val reason = (obj?.get("reason") as? JsonPrimitive)?.content ?: "UNKNOWN"
            val detail = (obj?.get("detail") as? JsonPrimitive)?.content ?: ""
            lastReason = "$reason${if (detail.isNotBlank()) " ($detail)" else ""}"
            // NOT_VISIBLE 时尝试 scrollIntoView
            if (reason == "NOT_VISIBLE") {
                evalJs("(function(){var el=document.querySelector('[data-rcb-id=" + quote(id) + "']'); if(el) el.scrollIntoView({block:'center'});})()")
            }
            delay(100)
        }
        return lastReason ?: "timeout waiting for element to become actionable"
    }

    /**
     * 增量快照（R2.1）：相对 [deltaBaseline] 计算元素级差异。
     * 基线为空（首次/导航后）返回 null，调用方应回退到全量快照。
     */
    fun computeDelta(after: BrowserPageSnapshot): BrowserSnapshotDelta? {
        val before = deltaBaseline ?: return null
        val beforeMap = before.elements.associateBy { it.id }
        val afterMap = after.elements.associateBy { it.id }
        val added = after.elements.filter { it.id !in beforeMap }
        val removed = before.elements.map { it.id }.filter { it !in afterMap }
        val changed = after.elements.filter { e ->
            val b = beforeMap[e.id] ?: return@filter false
            elementSignature(b) != elementSignature(e)
        }
        val textNote = when {
            after.pageText.isBlank() || before.pageText.isBlank() -> ""
            after.pageText.length > before.pageText.length * 2 -> "页面正文明显变长（${before.pageText.length} → ${after.pageText.length} 字）"
            after.pageText.length * 2 < before.pageText.length -> "页面正文明显变短（${before.pageText.length} → ${after.pageText.length} 字）"
            else -> ""
        }
        return BrowserSnapshotDelta(added = added, removed = removed, changed = changed, textNote = textNote)
    }

    /** 元素可感知签名（R2.1 diff 依据）：文本/取值/状态/可操作性/选项集合任一变化即视为变化。 */
    private fun elementSignature(e: BrowserElement): String =
        listOf(
            e.kind, e.label, e.text, e.value, e.href, e.placeholder,
            e.checked, e.disabled, e.required, e.readonly, e.sensitive,
            e.inViewport, e.visible, e.needsScroll, e.overlapped,
            e.options.joinToString("|") { "${it.value}=${it.text}" }
        ).joinToString("∷")

    /** 记录一次动作到 action_log（R2.3 动作历史）。 */
    fun recordAction(action: String, summary: String) {
        synchronized(actionLog) {
            actionLog.addLast("[$action] $summary")
            while (actionLog.size > 30) actionLog.removeFirst()
        }
    }

    /** 动作历史（R2.3）：最近 30 条操作 + 结果摘要，供 history 动作查询。 */
    fun actionHistory(): List<String> = synchronized(actionLog) { actionLog.toList() }

    /** 最近一次写操作后的增量（R2.1/R2.3 写操作自动验证），供工具侧随 envelope 返回。 */
    fun lastDelta(): BrowserSnapshotDelta? = lastDelta

    /**
     * 系统内存压力回调（由 MiniMeCore.onTrimMemory 在 RUNNING_CRITICAL/lowMemory 时转发）：
     * 释放本进程持有的页面快照大对象缓存（全量元素树 / 增量基线 / 增量结果），
     * 降低被 LMKD 静默杀进程的概率——LMKD 杀进程绕过 Java CrashHandler，
     * 正是「模型输出时闪退却无日志」的高概率根因之一（见 MiniMeCore.onTrimMemory）。
     * 释放后模型下一次 snapshot 会重新生成快照，属可接受的降级。
     */
    fun onMemoryPressure() {
        lastSnapshot = BrowserPageSnapshot()
        deltaBaseline = null
        lastDelta = null
    }

    /**
     * 写操作后统一处理（R2.1/R2.3 写操作自动验证）：计算「基线 → 操作后」增量，
     * 把基线推进到操作后快照（下次写操作以此为对照），并记录动作日志。
     */
    private fun afterWrite(action: String, after: BrowserPageSnapshot): BrowserPageSnapshot {
        val delta = computeDelta(after)
        lastDelta = delta
        deltaBaseline = after
        val summary = buildString {
            append(action)
            if (delta != null) {
                append(": 新增 ${delta.added.size}，变化 ${delta.changed.size}，消失 ${delta.removed.size}")
                if (delta.textNote.isNotBlank()) append("，${delta.textNote}")
            }
        }
        recordAction(action, summary)
        return after
    }

    /** 点击元素，返回点击后的快照。 */
    suspend fun click(elementId: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在点击元素 $elementId", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("click", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / role+name / CSS 均未命中")
            }
            // 可交互性等待：最多 5 秒，每 100ms 重试
            val notActionable = waitForActionable(resolved.id, skipOverlap = false)
            if (notActionable != null) {
                recordAction("click", "失败：元素不可交互（$notActionable）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 不可交互：$notActionable")
            }
            // 反检测：点击前随机延迟 100-300ms，模拟人类操作
            delay((100..300).random().toLong())
            // 鼠标移动轨迹：获取元素中心坐标，从当前鼠标位置贝塞尔曲线移动过去
            val centerRaw = evalJs("($BrowserJsScripts.JS_GET_ELEMENT_CENTER)(${quote(resolved.id)})")
            val centerObj = runCatching { json.parseToJsonElement(centerRaw).jsonObject }.getOrNull()
            val targetX = runCatching { (centerObj?.get("x") as? JsonPrimitive)?.content?.toDouble() }.getOrNull() ?: 0.0
            val targetY = runCatching { (centerObj?.get("y") as? JsonPrimitive)?.content?.toDouble() }.getOrNull() ?: 0.0
            // 获取当前鼠标位置
            val mouseRaw = evalJs("JSON.stringify(window.__rcb_mouse_pos || {x:window.innerWidth/2, y:window.innerHeight/2})")
            val mouseObj = runCatching { json.parseToJsonElement(mouseRaw).jsonObject }.getOrNull()
            val startX = runCatching { (mouseObj?.get("x") as? JsonPrimitive)?.content?.toDouble() }.getOrNull() ?: (targetX / 2)
            val startY = runCatching { (mouseObj?.get("y") as? JsonPrimitive)?.content?.toDouble() }.getOrNull() ?: (targetY / 2)
            // 生成二次贝塞尔曲线点（控制点随机偏移，模拟人类鼠标弧线）
            val steps = (12..18).random()
            val midX = (startX + targetX) / 2 + ((-50..50).random().toDouble())
            val midY = (startY + targetY) / 2 + ((-50..50).random().toDouble())
            for (i in 1..steps) {
                val t = i.toDouble() / steps
                val invT = 1.0 - t
                val px = invT * invT * startX + 2 * invT * t * midX + t * t * targetX
                val py = invT * invT * startY + 2 * invT * t * midY + t * t * targetY
                evalJs("($BrowserJsScripts.JS_MOUSE_MOVE)(${px}, ${py})")
                delay((10..30).random().toLong())
            }
            // hover 停留 50-150ms
            delay((50..150).random().toLong())
            // 触发点击事件序列
            evalJs("($BrowserJsScripts.JS_CLICK_AT)(${quote(resolved.id)}, ${targetX}, ${targetY})")
            waitForPageSettled(10_000)
            afterWrite("click", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 在元素中输入文本。
     * 反检测：多字符文本逐字符输入，每个字符间随机延迟 50-150ms，模拟人类打字节奏。
     */
    suspend fun type(elementId: String, text: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在向 $elementId 输入内容", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("type", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / role+name / CSS 均未命中")
            }
            // 可交互性等待：最多 5 秒，跳过遮挡检查（输入框可能被键盘遮挡）
            val notActionable = waitForActionable(resolved.id, skipOverlap = true)
            if (notActionable != null) {
                recordAction("type", "失败：输入框不可交互（$notActionable）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 不可交互：$notActionable")
            }
            if (text.isEmpty()) {
                // 空文本：直接触发清空事件
                evalJs("($BrowserJsScripts.JS_TYPE)(${quote(resolved.id)}, ${quote("")})")
            } else if (text.length == 1) {
                // 单字符：直接用原 BrowserJsScripts.JS_TYPE
                evalJs("($BrowserJsScripts.JS_TYPE)(${quote(resolved.id)}, ${quote(text)})")
            } else {
                // 多字符：逐字符输入，每字符间隔 50-150ms 随机延迟，最后统一触发 input/change
                for (ch in text) {
                    evalJs("($BrowserJsScripts.JS_APPEND_CHAR)(${quote(resolved.id)}, ${quote(ch.toString())})")
                    delay((50..150).random().toLong())
                }
                evalJs("($BrowserJsScripts.JS_FIRE_INPUT)(${quote(resolved.id)})")
            }
            afterWrite("type", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 批量表单填写：对多个 (elementId, text) 字段依次定位并输入文本。
     *
     * 每个字段：resolveElementId -> 逐字符输入（复用 type 的人类打字节奏）-> delay(100)。
     * 某个字段定位失败时跳过该字段，继续填写后续字段。
     * 全部完成后返回 SUMMARY 级快照。
     *
     * @param fields 元素定位符 -> 待填文本 的映射
     * @return 填写完成后的页面快照
     */
    suspend fun fillForm(fields: Map<String, String>): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在批量填写表单（${fields.size}个字段）", true)
        try {
            var filled = 0
            var failed = 0
            for ((elementId, text) in fields) {
                val resolved = resolveElementId(elementId)
                if (resolved == null) {
                    failed++
                    continue
                }
                if (text.isEmpty()) {
                    evalJs("($BrowserJsScripts.JS_TYPE)(${quote(resolved.id)}, ${quote("")})")
                } else if (text.length == 1) {
                    evalJs("($BrowserJsScripts.JS_TYPE)(${quote(resolved.id)}, ${quote(text)})")
                } else {
                    for (ch in text) {
                        evalJs("($BrowserJsScripts.JS_APPEND_CHAR)(${quote(resolved.id)}, ${quote(ch.toString())})")
                        delay((50..150).random().toLong())
                    }
                    evalJs("($BrowserJsScripts.JS_FIRE_INPUT)(${quote(resolved.id)})")
                }
                delay(100)
                filled++
            }
            val snap = snapshotInternal(SnapshotLevel.SUMMARY)
            recordAction("fill_form", "批量填写 ${fields.size} 个字段（成功$filled, 失败$failed）")
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 下拉选择。 */
    suspend fun selectOption(elementId: String, value: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在选择 $elementId", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("select_option", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / CSS 路径 / 语义均未命中")
            }
            evalJs("($BrowserJsScripts.JS_SELECT)(${quote(resolved.id)}, ${quote(value)})")
            afterWrite("select_option", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 提交表单（elementId 可为空 → 提交页面首个表单）。 */
    suspend fun submit(elementId: String?): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在提交表单", true)
        try {
            val id = elementId?.let { resolveElementId(it)?.id }
            evalJs("($BrowserJsScripts.JS_SUBMIT)(${if (id == null) "null" else quote(id)})")
            waitForPageSettled(15_000)
            afterWrite("submit", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 滚动页面。 */
    suspend fun scroll(direction: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在滚动页面", true)
        try {
            evalJs("($BrowserJsScripts.JS_SCROLL)(${quote(direction)})")
            delay(550)
            afterWrite("scroll", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 读取元素属性。 */
    suspend fun getAttribute(elementId: String, attribute: String): String? {
        val out = evalJs("($BrowserJsScripts.JS_ATTRIBUTE)(${quote(elementId)}, ${quote(attribute)})")
        val parsed = runCatching { json.parseToJsonElement(out).jsonObject }.getOrNull()
        return parsed?.get("value")?.let { if (it is JsonPrimitive) it.content else null }
    }

    /** 执行任意 JS，返回结果字符串。 */
    suspend fun evaluate(js: String): String {
        _agentStatus.value = AgentBrowserStatus("正在执行页面脚本", true)
        return try {
            evalJs(js)
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 结构化取数：按 CSS selector + mode 抽取文本/链接/标题/表格/HTML，返回原始 JSON 字符串。
     *
     * @param selector CSS 选择器，null 表示整页
     * @param mode 抽取模式：text / links / headings / table / html
     * @param autoScroll 是否自动滚动到底部加载更多内容后再提取
     * @param structured 为 true 时自动识别页面类型并输出结构化 JSON（article/product/search_results/profile），忽略 selector 和 mode
     */
    suspend fun extract(selector: String?, mode: String, autoScroll: Boolean = false, structured: Boolean = false): String = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在抽取结构化数据", true)
        return@withLock try {
            if (structured) {
                if (autoScroll) autoScrollLoad()
                val result = evalJs(BrowserJsScripts.JS_STRUCTURED_EXTRACT)
                recordAction("extract", "结构化提取页面内容（自动识别页面类型）")
                result
            } else {
                if (autoScroll) autoScrollLoad()
                val result = evalJs("($BrowserJsScripts.JS_EXTRACT)(${if (selector == null) "null" else quote(selector)}, ${quote(mode)})")
                recordAction("extract", "抽取结构化数据（mode=$mode${if (selector != null) ", selector=$selector" else ""}${if (autoScroll) ", 自动滚动" else ""}）")
                result
            }
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 页面截图（未指定元素时截视口），返回 base64 PNG data URL（供多模态模型查看）。 */
    suspend fun screenshot(elementId: String? = null): String? = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus(if (elementId != null) "正在截取元素 #$elementId" else "正在截取页面", true)
        try {
            // 元素截图：先滚动元素到视口中央并取相对视口矩形
            var crop: Rect? = null
            if (elementId != null) {
                val raw = evalJs("($BrowserJsScripts.JS_ELEMENT_RECT)(${quote(elementId)})")
                val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                val ok = runCatching { (obj?.get("ok") as? JsonPrimitive)?.content?.toBoolean() }.getOrNull() ?: false
                if (!ok) return@withLock null
                fun num(key: String): Float? = (obj?.get(key) as? JsonPrimitive)?.content?.toFloatOrNull()
                val x = num("x") ?: return@withLock null
                val y = num("y") ?: return@withLock null
                val w = num("width") ?: return@withLock null
                val h = num("height") ?: return@withLock null
                crop = Rect(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
            }
            val bmp = withContext(Dispatchers.Main) {
                val wv = ensureWebView()
                if (wv.width <= 0 || wv.height <= 0) return@withContext null
                val full = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                wv.draw(Canvas(full))
                val rect = crop
                if (rect != null) {
                    val left = rect.left.coerceIn(0, full.width - 1)
                    val top = rect.top.coerceIn(0, full.height - 1)
                    val right = rect.right.coerceIn(left + 1, full.width)
                    val bottom = rect.bottom.coerceIn(top + 1, full.height)
                    val cropped = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
                    if (cropped !== full) full.recycle()
                    cropped
                } else full
            } ?: return@withLock null
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos)
            bmp.recycle()
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            recordAction("screenshot", if (elementId != null) "截取元素 #$elementId" else "截取页面")
            "data:image/png;base64,$b64"
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 处理浏览器对话框（alert/confirm）。返回是否成功处理。 */
    suspend fun handleDialog(accept: Boolean): Boolean = mutex.withLock {
        val deferred = dialogDeferred ?: return@withLock false
        dialogDeferred = null
        val resolved = deferred.complete(accept)
        // 结果确认动作由监听 dialogDeferred 完成的协程在主线程执行
        withContext(Dispatchers.Main) { delay(50) }
        _pendingDialog.value = null
        resolved
    }

    /** 等待元素出现或加载完成。selector 为 CSS 选择器（匹配则视为满足）。 */
    suspend fun waitFor(selector: String?, timeoutMs: Long): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("等待页面元素", true)
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val snap = snapshotInternal(SnapshotLevel.STANDARD)
                if (selector.isNullOrBlank()) {
                    if (snap.url.isNotBlank()) {
                        recordAction("wait", "等待页面加载完成")
                        return@withLock snap
                    }
                } else {
                    val found = evalJs("(function(){ return !!document.querySelector(${quote(selector)}); })()")
                    if (found.trim() == "true") {
                        recordAction("wait", "等待元素出现：$selector")
                        return@withLock snap
                    }
                }
                delay(500)
            }
            val snap = snapshotInternal(SnapshotLevel.STANDARD)
            recordAction("wait", "等待超时（${timeoutMs}ms）${if (selector != null) "：$selector" else ""}", success = false)
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 事件驱动等待（R2.4）：挂起直到页面 DOM 发生变化（MutationObserver 版本号前进）或超时。
     * @param timeoutMs    最长等待毫秒
     * @param baselineVersion 变化基线版本号；不传则取当前版本号作为起点
     * @return 等待结果：是否发生变化 + 变化摘要文本（从页面环形缓冲读取，取最近变化）
     */
    suspend fun waitForChange(timeoutMs: Long, baselineVersion: Long? = null): WaitChangeResult = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("等待页面变化", true)
        try {
            val startVersion = baselineVersion ?: readMutVersion()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val version = readMutVersion()
                if (version > startVersion) {
                    val summary = decodeJsString(evalJs("JSON.stringify((window.__rcb_mut || []).slice(-10))"))
                    recordAction("wait", "检测到页面变化（DOM mutation v$version）")
                    return@withLock WaitChangeResult(changed = true, version = version, summary = summary)
                }
                // 网络事件也唤醒（wait_for_change 同时监听网络动静）
                if (networkPendingCount() > 0) {
                    val recs = listNetwork(1)
                    if (recs.isNotEmpty() && recs.first().startTs > System.currentTimeMillis() - 2000) {
                        recordAction("wait", "检测到新的网络请求：${recs.first().url.take(80)}")
                        return@withLock WaitChangeResult(changed = true, version = version, summary = "检测到新的网络请求：${recs.first().url.take(120)}")
                    }
                }
                delay(300)
            }
            recordAction("wait", "等待页面变化超时（${timeoutMs}ms）", success = false)
            WaitChangeResult(changed = false, version = readMutVersion(), summary = "")
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 渲染完成智能等待：三重判断页面是否真正就绪。
     *
     * 条件（同时满足）：
     *  1. 网络空闲：在途请求数为 0 且持续 500ms 无新请求
     *  2. DOM 稳定：MutationObserver 版本号连续 500ms 无变化
     *  3. 关键元素：body 非空且页面存在可交互元素（a/button/input/select/textarea）
     *
     * 轮询间隔 100ms，超时后降级返回 false（不阻塞主流程）。
     * 注意：本方法不自行加锁，应在持有 mutex 的上下文中调用（如 navigate 内部）。
     *
     * @param timeoutMs 最长等待毫秒，默认 3000
     * @return true 表示三重条件均满足；false 表示超时降级
     */
    suspend fun waitForPageReady(timeoutMs: Long = 3000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var netIdleSince = 0L
        var domStableSince = 0L
        var lastMutVersion = -1L
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            // 条件1：网络空闲（在途请求为0且持续500ms）
            val pending = readPendingCount()
            if (pending == 0) {
                if (netIdleSince == 0L) netIdleSince = now
            } else {
                netIdleSince = 0L
            }
            // 条件2：DOM稳定（版本号连续500ms不变）
            val mutVer = readMutVersion()
            if (mutVer == lastMutVersion) {
                if (domStableSince == 0L) domStableSince = now
            } else {
                domStableSince = 0L
                lastMutVersion = mutVer
            }
            // 条件3：body非空且有可交互元素
            val bodyReady = evalJs(
                "(function(){ return document.body && document.body.innerText.length > 0 && " +
                "document.querySelectorAll('a,button,input,select,textarea').length > 0; })()"
            ).trim() == "true"

            val netIdle = netIdleSince > 0 && (now - netIdleSince) >= 500
            val domStable = domStableSince > 0 && (now - domStableSince) >= 500
            if (netIdle && domStable && bodyReady) return true
            delay(100)
        }
        return false
    }

    /**
     * 等待网络空闲：轮询在途业务请求数（fetch/XHR/WS/SSE），
     * 当连续 idleMs 毫秒内 pendingCount == 0 时返回 true。
     *
     * @param timeoutMs 最长等待毫秒，默认 10000
     * @param idleMs 判定空闲所需的持续无请求时长，默认 500ms
     * @return true 表示网络已空闲；false 表示超时
     */
    suspend fun waitForNetworkIdle(timeoutMs: Long = 10000, idleMs: Long = 500): Boolean = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("等待网络空闲", true)
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            var idleSince = 0L
            while (System.currentTimeMillis() < deadline) {
                val pending = readPendingCount()
                val now = System.currentTimeMillis()
                if (pending == 0) {
                    if (idleSince == 0L) idleSince = now
                    if (now - idleSince >= idleMs) {
                        recordAction("wait", "网络已空闲（持续${idleMs}ms无在途请求）")
                        return@withLock true
                    }
                } else {
                    idleSince = 0L
                }
                delay(100)
            }
            recordAction("wait", "等待网络空闲超时（${timeoutMs}ms）", success = false)
            false
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    // ─────────────────── 补充动作（hover/drag/press_key/upload/导航） ───────────────────

    /** 读取页面 DOM 变化版本号（R2.4，MutationObserver 维护）；注入失效时为 0。 */
    private suspend fun readMutVersion(): Long {
        val raw = evalJs("(window.__rcb_mut_version || 0)")
        return raw.trim().toLongOrNull() ?: 0L
    }

    /** 悬停元素，返回悬停后快照。 */
    suspend fun hover(elementId: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在悬停元素 $elementId", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("hover", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / CSS 路径 / 语义均未命中")
            }
            evalJs("($BrowserJsScripts.JS_HOVER)(${quote(resolved.id)})")
            delay(200)
            afterWrite("hover", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 按键（向元素派发 keydown/keyup）。 */
    suspend fun pressKey(elementId: String, key: String): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在按键 $elementId", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("press_key", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / CSS 路径 / 语义均未命中")
            }
            evalJs("($BrowserJsScripts.JS_PRESS_KEY)(${quote(resolved.id)}, ${quote(key)})")
            afterWrite("press_key", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 拖拽：从源元素拖到目标元素（targetElementId 可空，表示原地拖拽）。 */
    suspend fun drag(elementId: String, targetElementId: String?): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在拖拽 $elementId", true)
        try {
            val resolved = resolveElementId(elementId)
            if (resolved == null) {
                recordAction("drag", "失败：元素未找到（$elementId）")
                return@withLock BrowserPageSnapshot(url = lastSnapshot.url, pageText = "元素 $elementId 未找到：data-rcb-id / CSS 路径 / 语义均未命中")
            }
            val targetResolved = targetElementId?.let { resolveElementId(it)?.id }
            evalJs("($BrowserJsScripts.JS_DRAG)(${quote(resolved.id)}, ${if (targetResolved == null) "null" else quote(targetResolved)})")
            afterWrite("drag", snapshotInternal(SnapshotLevel.SUMMARY))
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /**
     * 向页面中 file 输入框自动回填本地文件。
     *
     * @return null 表示成功；否则返回错误说明（供工具侧透传给模型）。
     */
    suspend fun uploadFile(elementId: String, hostFile: File): String? = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在上传文件", true)
        try {
            if (!hostFile.exists() || !hostFile.isFile) {
                return@withLock "上传失败：文件不存在 ${hostFile.absolutePath}"
            }
            val done = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                pendingUploadFile = hostFile
                pendingUploadDone = done
            }
            try {
                evalJs("($BrowserJsScripts.JS_UPLOAD_CLICK)(${quote(elementId)})")
                val ok = withTimeoutOrNull(3_000) { done.await() } ?: false
                if (ok) null
                else "未能自动上传：文件选择器未被触发（可能被页面脚本拦截）。可改用 takeover 让用户手动选择文件。"
            } finally {
                withContext(Dispatchers.Main) {
                    pendingUploadFile = null
                    pendingUploadDone = null
                }
            }
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 后退一页，返回快照。 */
    suspend fun back(): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在后退", true)
        try {
            withContext(Dispatchers.Main) { activeWebView()?.takeIf { it.canGoBack() }?.goBack() }
            waitForPageSettled(15_000)
            val snap = snapshotInternal()
            recordAction("back", "后退一页")
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 前进一页，返回快照。 */
    suspend fun forward(): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在前进", true)
        try {
            withContext(Dispatchers.Main) { activeWebView()?.takeIf { it.canGoForward() }?.goForward() }
            waitForPageSettled(15_000)
            val snap = snapshotInternal()
            recordAction("forward", "前进一页")
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 刷新当前页，返回快照。 */
    suspend fun reloadPage(): BrowserPageSnapshot = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在刷新页面", true)
        try {
            withContext(Dispatchers.Main) { activeWebView()?.reload() }
            waitForPageSettled(30_000)
            val snap = snapshotInternal()
            recordAction("reload", "刷新页面")
            snap
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    // ─────────────────── 多标签页（new/switch/close/list） ───────────────────

    /** 新建标签页（可带初始 URL），并切换过去。返回新标签信息。 */
    suspend fun newTab(url: String?): BrowserTabInfo = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("正在新建标签页", true)
        try {
            val info = withContext(Dispatchers.Main) {
                val tab = createTab(url)
                switchToTab(tab.id)
                BrowserTabInfo(tab.id, tab.title, tab.url)
            }
            recordAction("new_tab", "新建标签页${if (url != null) "：$url" else ""}")
            info
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 切换到指定标签。返回 null 表示成功，否则返回错误说明。 */
    suspend fun switchTab(tabId: String): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            if (findTab(tabId) == null) return@withContext "标签不存在：$tabId"
            switchToTab(tabId)
            null
        }
    }

    /** 关闭指定标签。返回 null 表示成功，否则返回错误说明。 */
    suspend fun closeTab(tabId: String): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            val tab = findTab(tabId) ?: return@withContext "标签不存在：$tabId"
            if (tabs.size <= 1) return@withContext "至少保留一个标签页，无法关闭"
            val idx = tabs.indexOf(tab)
            tabs.removeAt(idx)
            detachAndDestroy(tab.webView)
            if (activeTabId == tabId) {
                val next = tabs.getOrNull(idx) ?: tabs.lastOrNull()
                activeTabId = next?.id
                next?.let(::applyActiveTab)
            }
            publishTabs()
            null
        }
    }

    /** 列出所有标签页（供 list_tabs 工具与 UI 使用）。 */
    fun listTabs(): List<BrowserTabInfo> = tabsState.value

    /** 关闭除 [keepId] 外的全部标签（F4.1 标签管理）。返回 null 成功，否则错误说明。 */
    suspend fun closeOtherTabs(keepId: String): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            val keep = findTab(keepId) ?: return@withContext "标签不存在：$keepId"
            val toClose = tabs.filter { it.id != keepId }
            toClose.forEach { t ->
                tabs.remove(t)
                detachAndDestroy(t.webView)
            }
            activeTabId = keep.id
            applyActiveTab(keep)
            publishTabs()
            null
        }
    }

    /** 关闭 [fromId] 右侧（不含自身）的所有标签（F4.1）。 */
    suspend fun closeRightTabs(fromId: String): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            val idx = tabs.indexOfFirst { it.id == fromId }
            if (idx < 0) return@withContext "标签不存在：$fromId"
            val toClose = tabs.drop(idx + 1)
            val activeDropped = toClose.any { it.id == activeTabId }
            toClose.forEach { t ->
                tabs.remove(t)
                detachAndDestroy(t.webView)
            }
            if (activeDropped) {
                val target = findTab(fromId)
                activeTabId = target?.id
                target?.let(::applyActiveTab)
            }
            publishTabs()
            null
        }
    }

    /** 关闭全部标签（F4.1）：至少保留当前激活标签，避免 WebView 为空。 */
    suspend fun closeAllTabs(): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            val keep = activeTab() ?: tabs.firstOrNull() ?: return@withContext null
            val toClose = tabs.filter { it.id != keep.id }
            toClose.forEach { t ->
                tabs.remove(t)
                detachAndDestroy(t.webView)
            }
            activeTabId = keep.id
            applyActiveTab(keep)
            publishTabs()
            null
        }
    }

    // ─────────────────────────── 下载 ───────────────────────────

    /** 列出最近下载任务（供 downloads 工具查询）。 */
    fun listDownloads(): List<BrowserDownloadInfo> = downloadManager.downloads.value

    // ─────────────────────────── 内部实现 ───────────────────────────

    /** 当前页面是否存在登录表单（密码框等）。 */
    fun detectLoginForm(snapshot: BrowserPageSnapshot): Pair<Boolean, String> {
        var hasPassword = false
        var usernameId: String? = null
        var passwordId: String? = null
        for (e in snapshot.elements) {
            if (e.tag == "input" && e.type == "password") {
                hasPassword = true
                passwordId = e.id
            } else if (e.tag == "input" && (e.type == "text" || e.type == "email" || e.type == "tel" || (e.type.isBlank() && e.name.isNotBlank()))) {
                if (usernameId == null) usernameId = e.id
            }
        }
        return if (hasPassword && passwordId != null) {
            true to "检测到登录表单（用户名框=${usernameId ?: "未知"}，密码框=$passwordId）。请调用 login 工具自动代填，或提示用户手动填写。"
        } else if (snapshot.pageText.contains(Regex("(?i)sign in|log in|login|登录|登陆|登入"))) {
            true to "页面可能要求登录（检测到登录相关文案）。请调用 login 工具处理。"
        } else {
            false to ""
        }
    }

    private fun activeTab(): BrowserTab? = tabs.firstOrNull { it.id == activeTabId }

    private fun activeWebView(): WebView? = activeTab()?.webView

    private fun findTab(id: String): BrowserTab? = tabs.firstOrNull { it.id == id }

    /** 切换到指定标签（主线程）。 */
    private fun switchToTab(tabId: String) {
        val tab = findTab(tabId) ?: return
        activeTabId = tabId
        applyActiveTab(tab)
        publishTabs()
    }

    /** 用指定标签刷新 UI 状态（主线程）。 */
    private fun applyActiveTab(tab: BrowserTab) {
        _uiState.value = _uiState.value.copy(
            currentUrl = tab.url,
            title = tab.title,
            canGoBack = tab.webView.canGoBack(),
            canGoForward = tab.webView.canGoForward(),
            isLoading = false,
            progress = 0,
            activeTabId = tab.id
        )
    }

    /** 将标签列表同步到 [tabsState]。 */
    private fun publishTabs() {
        _tabsState.value = tabs.map { BrowserTabInfo(it.id, it.title, it.url) }
    }

    /** 新建一个标签（主线程），可选加载初始 URL；不自动切换。 */
    private fun createTab(url: String?): BrowserTab {
        val id = UUID.randomUUID().toString()
        val wv = createWebView(id)
        val tab = BrowserTab(id = id, webView = wv)
        tabs.add(tab)
        if (url != null) {
            val normalized = normalizeUrl(url)
            tab.url = normalized
            wv.loadUrl(normalized)
        }
        publishTabs()
        FileLogger.i(TAG, "新建标签 $id${if (url != null) " -> $url" else ""}")
        return tab
    }

    /** 配置一个全新的 WebView（主线程），回调按标签 id 分派。 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tabId: String): WebView {
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // F4.8 双指捏合缩放 + 双击缩放（系统默认手势）
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // 首次创建时记录原始移动 UA（去 wv 标记），供桌面版切换关闭后恢复
            if (originalUserAgent == null) originalUserAgent = userAgentString.replaceFirst("; wv", "")
            userAgentString = if (_uiState.value.desktopMode) DESKTOP_UA else (originalUserAgent ?: userAgentString.replaceFirst("; wv", ""))
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                return if (adBlocker.shouldBlock(url)) {
                    // 返回空响应以阻断广告/跟踪器请求
                    android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                } else null
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                adBlocker.reset()
                onTabLoading(tabId, true)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                onTabFinished(tabId, view, url)
            }
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                FileLogger.w(TAG, "页面加载错误 code=$errorCode url=$failingUrl: $description")
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                FileLogger.w(TAG, "页面加载错误: ${error?.errorCode} ${error?.description} ${request?.url}")
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onTabProgress(tabId, newProgress)
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                onTabTitle(tabId, title)
            }
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                val line = "[${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                _consoleLogs.update { (it + line).takeLast(200) }
                return true
            }
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                handleDialog("alert", message ?: "", result)
                return true
            }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                handleDialog("confirm", message ?: "", result)
                return true
            }
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                val file = pendingUploadFile
                if (file != null && filePathCallback != null && file.exists() && file.isFile) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    filePathCallback.onReceiveValue(arrayOf(uri))
                    pendingUploadFile = null
                    pendingUploadDone?.complete(true)
                    pendingUploadDone = null
                    FileLogger.i(TAG, "已自动回填上传文件: ${file.absolutePath}")
                    return true
                }
                // 无自动上传目标：走系统默认文件选择器（配合 takeover，用户手动选择）
                return false
            }
        }
        // 下载监听：F4.2 委托给 BrowserDownloadManager（进度/暂停/续传/通知）
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            downloadManager.enqueue(url, userAgent, contentDisposition, mimetype)
        }
        // 动态数据捕获插桩：在 document-start 注入（比任何页面脚本早），抓取 fetch/XHR/WS/SSE 请求。
        try {
            WebViewCompat.addDocumentStartJavaScript(wv, BrowserJsScripts.JS_NET_HOOK, setOf("*"))
        } catch (e: Exception) {
            FileLogger.w(TAG, "addDocumentStartJavaScript 注入失败（旧 WebView 降级：不采集网络数据）", e)
        }
        // DOM 变化订阅（R2.4 事件驱动感知）：document-start 注入 MutationObserver，
        // 供 wait_for_change 挂起等待页面变化，替代轮询 snapshot。
        try {
            WebViewCompat.addDocumentStartJavaScript(wv, BrowserJsScripts.JS_CHANGE_OBSERVER, setOf("*"))
        } catch (e: Exception) {
            FileLogger.w(TAG, "addDocumentStartJavaScript 注入失败（旧 WebView 降级：wait_for_change 不可用）", e)
        }
        // 反检测：document-start 注入，确保在任何页面脚本之前覆盖 webdriver/plugins 等特征
        try {
            WebViewCompat.addDocumentStartJavaScript(wv, BrowserJsScripts.JS_ANTI_DETECT, setOf("*"))
        } catch (e: Exception) {
            FileLogger.w(TAG, "addDocumentStartJavaScript 反检测注入失败（降级 onPageStarted 注入）", e)
        }
        // 新 WebView 创建即按当前代理开关接管网络出口（WebView 不认 Java ProxySelector）。
        applyWebViewProxy(wv)
        return wv
    }

    private fun onTabLoading(tabId: String, isLoading: Boolean) {
        if (activeTabId != tabId) return
        _uiState.value = _uiState.value.copy(isLoading = isLoading, progress = if (isLoading) 10 else _uiState.value.progress)
    }

    private fun onTabFinished(tabId: String, view: WebView?, url: String?) {
        val tab = findTab(tabId)
        if (tab != null) {
            tab.url = url ?: tab.url
            view?.title?.let { tab.title = it }
        }
        publishTabs()
        // R1.1 历史记录：页面加载完成且非无痕模式时写入访问历史（含 http/https 页面）
        if (!_uiState.value.incognito && !url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            historyStore.record(tab?.title ?: "", url)
        }
        if (activeTabId == tabId) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                progress = 100,
                currentUrl = url ?: _uiState.value.currentUrl,
                canGoBack = view?.canGoBack() ?: false,
                canGoForward = view?.canGoForward() ?: false,
                title = tab?.title ?: _uiState.value.title
            )
            detectReaderAvailable()
            // F4.6 检测页面语言
            activeWebView()?.evaluateJavascript("(function(){return (document.documentElement.lang||'').toLowerCase();})();") { l ->
                _pageLang.value = l?.trim()?.removePrefix("\"")?.removeSuffix("\"").orEmpty()
            }
        }
    }

    private fun onTabProgress(tabId: String, progress: Int) {
        if (activeTabId != tabId) return
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    private fun onTabTitle(tabId: String, title: String?) {
        val tab = findTab(tabId)
        if (tab != null && title != null) tab.title = title
        publishTabs()
        if (activeTabId == tabId && title != null) {
            _uiState.value = _uiState.value.copy(title = title)
        }
    }

    /** 将 WebView 从当前父容器摘除（不销毁），供复用前的重新挂载与页面卸载时调用。 */
    private fun detachFromParent(wv: WebView) {
        (wv.parent as? android.view.ViewGroup)?.removeView(wv)
    }

    private fun detachAndDestroy(wv: WebView) {
        detachFromParent(wv)
        wv.destroy()
    }

    /**
     * 自动滚动加载：反复滚动到底部触发懒加载，直到 scrollHeight 不再增长或达到最大滚动次数（10次）。
     * 每次滚动间隔 800ms，模拟人类浏览节奏。调用方需已持有 mutex。
     */
    private suspend fun autoScrollLoad() {
        var lastHeight = 0
        repeat(10) {
            evalJs("window.scrollTo(0, document.body.scrollHeight)")
            delay(800)
            val height = evalJs("document.body.scrollHeight").trim().toIntOrNull() ?: 0
            if (height == lastHeight) return
            lastHeight = height
        }
    }

    /**
     * 快照分级提取（R2.1）：总是收集元素树与标题大纲（供 delta 计算与元素定位），
     * 仅 [SnapshotLevel.FULL] 时才额外取 page_text（最贵的部分），
     * summary/standard 不取正文，由工具侧按分级裁剪元素 JSON 后返回。
     */
    private suspend fun snapshotInternal(level: SnapshotLevel = SnapshotLevel.FULL): BrowserPageSnapshot {
        val elJson = evalJs(BrowserJsScripts.JS_SNAPSHOT)
        val parsed = runCatching { json.parseToJsonElement(elJson).jsonObject }.getOrNull()
        val elements = runCatching {
            parsed?.get("elements")?.let { el ->
                Json { ignoreUnknownKeys = true }.decodeFromString<List<BrowserElement>>(el.toString())
            }
        }.getOrNull() ?: emptyList()
        val headings = runCatching {
            parsed?.get("headings")?.let { h ->
                Json { ignoreUnknownKeys = true }.decodeFromString<List<BrowserHeading>>(h.toString())
            }
        }.getOrNull() ?: emptyList()
        val title = parsed?.get("title")?.let { if (it is JsonPrimitive) it.content else "" } ?: ""
        val url = parsed?.get("url")?.let { if (it is JsonPrimitive) it.content else "" } ?: ""
        // 分级：page_text 只在 FULL 级提取；登录表单信号基于元素（密码框）始终检测
        val pageText = if (level == SnapshotLevel.FULL) evalJs(BrowserJsScripts.JS_PAGE_TEXT) else ""
        val (hasLogin, hint) = detectLoginForm(
            BrowserPageSnapshot(title = title, url = url, headings = headings, elements = elements, pageText = pageText.take(12000))
        )
        val snap = BrowserPageSnapshot(
            title = title,
            url = url,
            headings = headings,
            elements = elements,
            pageText = pageText.take(12000),
            hasLoginForm = hasLogin,
            loginHint = hint,
            pendingRequests = readPendingCount()
        )
        lastSnapshot = snap
        return snap
    }

    /** 主线程执行 JS（evaluateJavascript 回调转 suspend）。 */
    private suspend fun evalJs(js: String): String {
        return withContext(Dispatchers.Main) {
            val wv = ensureWebView()
            val deferred = CompletableDeferred<String>()
            wv.evaluateJavascript(js) { result -> deferred.complete(result ?: "null") }
            try {
                withTimeoutOrNull(15_000) { deferred.await() } ?: "null"
            } catch (e: Exception) {
                FileLogger.e(TAG, "evaluateJavascript 失败", e)
                "null"
            }
        }
    }

    /** 在主线程获取当前激活标签的 WebView（懒创建首个标签）。 */
    private suspend fun ensureWebView(): WebView = withContext(Dispatchers.Main) {
        activeWebView() ?: run {
            val tab = createTab(null)
            activeTabId = tab.id
            applyActiveTab(tab)
            tab.webView
        }
    }

    /** WebChromeClient 对话框回调：登记待处理对话框，等待工具侧处理。 */
    private fun handleDialog(type: String, message: String, result: JsResult?) {
        dialogResult?.cancel()
        val id = UUID.randomUUID().toString()
        _pendingDialog.value = PendingBrowserDialog(id, type, message)
        dialogResult = result
        val deferred = CompletableDeferred<Boolean>()
        dialogDeferred = deferred
        scope.launch {
            val accept = try { deferred.await() } catch (e: Exception) { false }
            mainHandler.post {
                val r = dialogResult
                if (r != null) {
                    if (accept) r.confirm() else r.cancel()
                }
                if (_pendingDialog.value?.id == id) _pendingDialog.value = null
                if (dialogResult === r) dialogResult = null
            }
        }
    }

    private suspend fun waitForPageSettled(timeoutMs: Long) {
        val debounceMs = 400L
        val deadline = System.currentTimeMillis() + timeoutMs
        var stableCount = 0
        var lastDom: String? = null
        var lastRoute: Long = -1L
        var firstPass = true
        while (System.currentTimeMillis() < deadline) {
            val loading = withContext(Dispatchers.Main) { _uiState.value.isLoading }
            val pending = readPendingCount()
            val dom = readDomMeta()
            val route = readRouteSeq()
            if (loading || pending > 0) {
                // 主文档还在加载、或有在途业务请求（fetch/XHR/WS/SSE），重置稳定计数
                stableCount = 0
                lastDom = null
            } else if (!firstPass && dom == lastDom && route == lastRoute) {
                // 业务请求已停 + DOM 稳定 + 路由稳定，累计到 debounce 即视为就绪
                stableCount++
                if (stableCount * 100L >= debounceMs) break
            } else {
                stableCount = 0
            }
            lastDom = dom
            lastRoute = route
            firstPass = false
            delay(100)
        }
        withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(isLoading = false, progress = 100) }
    }

    /** 读取页面内在途业务请求数（fetch/XHR/WS/SSE）；注入失效时为 0，不影响判定。 */
    private suspend fun readPendingCount(): Int {
        val raw = evalJs("(window.__rcb_net_pending || 0)")
        return raw.trim().toIntOrNull() ?: 0
    }

    /** 读取页面 DOM 稳定性指纹（正文长度 : 元素总数）。 */
    private suspend fun readDomMeta(): String = evalJs(
        "(function(){ var n = document.body ? document.body.innerText.length : -1; var c = document.querySelectorAll('*').length; return n + ':' + c; })()"
    )

    /** 读取 SPA 路由变更计数（pushState/replaceState/popstate 触发）。 */
    private suspend fun readRouteSeq(): Long {
        val raw = evalJs("(window.__rcb_route_seq || 0)")
        return raw.trim().toLongOrNull() ?: 0L
    }

    // ─────────────────── 动态数据捕获：网络请求日志查询 ───────────────────

    /** 页面内在途业务请求数（fetch/XHR/WS/SSE），供 wait_for_request 超时、network 汇总报告。 */
    suspend fun networkPendingCount(): Int = mutex.withLock { readPendingCount() }

    /** 页面内网络缓冲总记录数（含已完成与在途）。 */
    suspend fun networkTotalCount(): Int = mutex.withLock {
        evalJs("(window.__rcb_net || []).length").trim().toIntOrNull() ?: 0
    }

    /** 列出页面内已记录的异步数据请求（fetch/XHR/WS/SSE），按时间倒序返回最近 limit 条。 */
    suspend fun listNetwork(limit: Int = 20): List<BrowserNetworkRecord> = mutex.withLock {
        val n = limit.coerceIn(1, 100)
        decodeNetworkList(evalJs("JSON.stringify((window.__rcb_net || []).slice(-$n).reverse())"))
    }

    /** 按记录 id 或 URL 子串查询单条网络请求记录（两者都空则返回最新一条）。 */
    suspend fun getNetwork(url: String? = null, id: Int? = null): BrowserNetworkRecord? = mutex.withLock {
        val all = decodeNetworkList(evalJs("JSON.stringify(window.__rcb_net || [])"))
        when {
            id != null -> all.firstOrNull { it.id == id }
            !url.isNullOrBlank() -> all.lastOrNull { it.url.contains(url) }
            else -> all.lastOrNull()
        }
    }

    /** 轮询等待匹配 URL 子串的请求完成并返回该记录（超时返回 null）。 */
    suspend fun waitForRequest(url: String, timeoutMs: Long = 15_000): BrowserNetworkRecord? = mutex.withLock {
        _agentStatus.value = AgentBrowserStatus("等待数据请求：$url", true)
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val hit = decodeNetworkList(evalJs("JSON.stringify(window.__rcb_net || [])"))
                    .lastOrNull { it.url.contains(url) }
                if (hit != null) return@withLock hit
                delay(300)
            }
            null
        } finally {
            _agentStatus.value = AgentBrowserStatus()
        }
    }

    /** 解析网络缓冲 JSON（兼容 WebView evaluateJavascript 对字符串结果再包裹一层的形态）。 */
    private fun decodeNetworkList(raw: String): List<BrowserNetworkRecord> {
        val text = raw.trim()
        if (text.isEmpty() || text == "null") return emptyList()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val arr = when (element) {
            is JsonArray -> element
            is JsonPrimitive -> if (element.isString) {
                runCatching { json.parseToJsonElement(element.content) }.getOrNull() as? JsonArray ?: return emptyList()
            } else return emptyList()
            else -> return emptyList()
        }
        return runCatching { json.decodeFromString<List<BrowserNetworkRecord>>(arr.toString()) }.getOrNull() ?: emptyList()
    }

    private fun quote(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        return "\"$escaped\""
    }

    /**
     * 解码 WebView evaluateJavascript 返回的 JS 字符串结果：JS 表达式结果为字符串时，
     * WebView 会再包一层引号并转义（如 `"[\"a\"]"`），此处还原为原始 JSON 文本。
     */
    private fun decodeJsString(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("\"") && t.endsWith("\"")) {
            return runCatching { json.parseToJsonElement(t).jsonPrimitive.content }.getOrNull() ?: t
        }
        return t
    }

    /** 当前快照（模型读属性等用途）。 */
    fun currentSnapshot(): BrowserPageSnapshot = lastSnapshot
}
