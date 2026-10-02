package com.mini.me_core.feature.settings.presentation.component

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 通用文档查看器：优先从 GitHub 远程拉取最新 Markdown，失败时回退到本地 assets 缓存。
 *
 * 用于「关于」页的用户协议 / 隐私政策 / 说明文档等需要实时跟随仓库更新的长文档。
 * 开源协议（GPL-3.0）等固定文档可只传 assetPath 不走网络。
 * 顶栏由本页面唯一提供（Scaffold + AppTopAppBar），背景/文字颜色跟随主题。
 *
 * @param title 顶栏标题
 * @param assetPath assets 内的本地兜底文档路径（如 "docs/user-agreement.md"）
 * @param remoteUrl 可选的 GitHub raw 地址；非空时优先远程拉取并缓存
 * @param alternateAssetPath 可选的另一语言版本路径；非空时顶栏显示中/EN 切换按钮
 * @param alternateRemoteUrl 可选的另一语言版本远程地址
 * @param onBack 返回回调
 */
@Composable
fun DocViewerScreen(
    title: String,
    assetPath: String,
    remoteUrl: String? = null,
    alternateAssetPath: String? = null,
    alternateRemoteUrl: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 当前实际加载的路径：支持在主/备语言版本间切换。
    var currentPath by remember(assetPath, alternateAssetPath) { mutableStateOf(assetPath) }
    var currentRemote by remember(remoteUrl, alternateRemoteUrl) { mutableStateOf(remoteUrl) }
    var state by remember(currentPath, currentRemote) { mutableStateOf<DocLoadState>(DocLoadState.Loading) }

    // 拦截系统返回键，关闭文档查看器
    BackHandler { onBack() }

    LaunchedEffect(currentPath, currentRemote) {
        state = DocLoadState.Loading
        state = withContext(Dispatchers.IO) {
            // 1. 优先远程拉取（带缓存）
            if (currentRemote != null) {
                runCatching { fetchRemoteMarkdown(context, currentRemote!!, currentPath) }
                    .onSuccess { return@withContext DocLoadState.Success(it) }
            }
            // 2. 回退本地 assets
            runCatching {
                context.assets.open(currentPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.fold(
                onSuccess = { DocLoadState.Success(it) },
                onFailure = { DocLoadState.Error },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            AppTopAppBar(
                title = title,
                onNavigateBack = onBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
                actions = {
                    if (alternateAssetPath != null) {
                        // 切换按钮：显示"将要切换到的语言"。
                        val switchingToEn = currentPath.contains("-zh", ignoreCase = true)
                        TextButton(onClick = {
                            if (switchingToEn) {
                                currentPath = alternateAssetPath
                                currentRemote = alternateRemoteUrl
                            } else {
                                currentPath = assetPath
                                currentRemote = remoteUrl
                            }
                        }) {
                            Text(
                                text = stringResource(
                                    if (switchingToEn) R.string.doc_viewer_switch_to_en
                                    else R.string.doc_viewer_switch_to_zh
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                DocLoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                DocLoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.doc_viewer_error),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.common_back))
                        }
                    }
                }
                is DocLoadState.Success -> {
                    val bg = MaterialTheme.colorScheme.background
                    val fg = MaterialTheme.colorScheme.onBackground
                    val link = MaterialTheme.colorScheme.primary
                    val html = remember(current.markdown, bg, fg, link) {
                        markdownToHtml(current.markdown, bg, fg, link)
                    }
                    DocWebView(html = html, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private sealed interface DocLoadState {
    data object Loading : DocLoadState
    data object Error : DocLoadState
    data class Success(val markdown: String) : DocLoadState
}

/** 远程文档缓存目录名。 */
private const val DOC_CACHE_DIR = "doc_cache"

/** 缓存有效期：12 小时（毫秒）。 */
private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L

private val httpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/**
 * 从 GitHub raw 拉取 Markdown 文档，带本地文件缓存。
 * 缓存命中且未过期时直接返回缓存；否则网络拉取并写入缓存。
 * 网络失败时若有缓存则返回缓存，否则抛出异常。
 */
private fun fetchRemoteMarkdown(context: android.content.Context, url: String, assetPath: String): String {
    val cacheDir = File(context.cacheDir, DOC_CACHE_DIR).apply { mkdirs() }
    val cacheFile = File(cacheDir, assetPath.replace('/', '_'))

    // 1. 缓存命中且未过期
    if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL_MS) {
        return cacheFile.readText(Charsets.UTF_8)
    }

    // 2. 网络拉取
    return try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MiniMe-core-DocViewer")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            // 写入缓存
            cacheFile.writeText(body, Charsets.UTF_8)
            body
        }
    } catch (e: Exception) {
        // 3. 网络失败，有缓存则返回缓存
        if (cacheFile.exists()) {
            cacheFile.readText(Charsets.UTF_8)
        } else {
            throw e
        }
    }
}

@Composable
private fun DocWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // 纯静态文档，无需 JS
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // 安全加固：静态文档无需任何文件 / 内容访问能力
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                webViewClient = WebViewClient()
                setBackgroundColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
    )
}

// ============================================================
// 轻量 Markdown -> HTML（标题 / 加粗 / 行内代码 / 链接 / 列表 / 分隔线）
// ============================================================

private fun markdownToHtml(
    markdown: String,
    bg: Color,
    fg: Color,
    link: Color,
): String {
    val body = StringBuilder()
    var inUl = false
    var inOl = false

    fun closeLists() {
        if (inUl) { body.append("</ul>"); inUl = false }
        if (inOl) { body.append("</ol>"); inOl = false }
    }

    for (raw in markdown.lines()) {
        val line = raw.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                closeLists(); body.append("<h1>").append(inline(trimmed.removePrefix("# "))).append("</h1>")
            }
            trimmed.startsWith("## ") -> {
                closeLists(); body.append("<h2>").append(inline(trimmed.removePrefix("## "))).append("</h2>")
            }
            trimmed.startsWith("### ") -> {
                closeLists(); body.append("<h3>").append(inline(trimmed.removePrefix("### "))).append("</h3>")
            }
            trimmed.startsWith("#### ") -> {
                closeLists(); body.append("<h4>").append(inline(trimmed.removePrefix("#### "))).append("</h4>")
            }
            trimmed.isEmpty() -> {
                closeLists(); body.append("\n")
            }
            trimmed == "---" || trimmed == "***" -> {
                closeLists(); body.append("<hr/>")
            }
            UNORDERED_LIST.matches(trimmed) -> {
                if (!inUl) { closeLists(); body.append("<ul>"); inUl = true }
                body.append("<li>").append(inline(trimmed.replace(UNORDERED_LIST_REPL, ""))).append("</li>")
            }
            ORDERED_LIST.matches(trimmed) -> {
                if (!inOl) { closeLists(); body.append("<ol>"); inOl = true }
                body.append("<li>").append(inline(trimmed.replace(ORDERED_LIST_REPL, ""))).append("</li>")
            }
            else -> {
                closeLists(); body.append("<p>").append(inline(trimmed)).append("</p>")
            }
        }
    }
    closeLists()

    val css = """
        body { font-family: sans-serif; margin: 0; padding: 16px;
               background: ${bg.cssRgba()}; color: ${fg.cssRgba()};
               line-height: 1.6; font-size: 15px; }
        h1 { font-size: 22px; } h2 { font-size: 19px; }
        h3 { font-size: 17px; } h4 { font-size: 15px; }
        h1,h2,h3,h4 { margin: 20px 0 8px; line-height: 1.3; }
        p { margin: 8px 0; }
        ul, ol { margin: 8px 0; padding-left: 26px; }
        li { margin: 4px 0; }
        code { font-family: monospace; background: rgba(128,128,128,0.25);
               padding: 1px 4px; border-radius: 4px; }
        a { color: ${link.cssRgba()}; text-decoration: none; }
        hr { border: none; border-top: 1px solid rgba(128,128,128,0.35); margin: 18px 0; }
        strong { font-weight: bold; }
    """.trimIndent()

    return "<html><head><meta charset=\"utf-8\"><style>$css</style></head><body>$body</body></html>"
}

private val UNORDERED_LIST = Regex("^[-*]\\s+.*")
private val UNORDERED_LIST_REPL = Regex("^[-*]\\s+")
private val ORDERED_LIST = Regex("^\\d+\\.\\s+.*")
private val ORDERED_LIST_REPL = Regex("^\\d+\\.\\s+")

/** 行内转换：先转义 HTML，再处理链接 / 加粗 / 行内代码。 */
private fun inline(text: String): String {
    var s = escapeHtml(text)
    // [text](url)
    s = LINK_REGEX.replace(s) { m ->
        "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
    }
    // `code`
    s = s.replace(BACKTICK_REGEX) { "<code>${it.groupValues[1]}</code>" }
    // **bold**
    s = s.replace(BOLD_REGEX) { "<strong>${it.groupValues[1]}</strong>" }
    return s
}

private val LINK_REGEX = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
private val BACKTICK_REGEX = Regex("`([^`]+)`")
private val BOLD_REGEX = Regex("\\*\\*([^*]+)\\*\\*")

private fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun Color.cssRgba(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = "%.2f".format(alpha.coerceIn(0f, 1f))
    return "rgba($r,$g,$b,$a)"
}
