@file:Suppress("MatchingDeclarationName")

package com.mini.me_core.feature.packager.domain.model

/**
 * 启动页配置映射（纯函数，不依赖 Android / org.json，可在 JVM 单测中直接断言）。
 *
 * 打包器在写 assets/config.json 前，先把领域模型 [SplashSettings] 规整成与模板侧
 * 契约一致的 [SplashConfigPayload]，再由 ApkBuilder 用 org.json 写入。这样字段钳制、
 * 过渡效果白名单、有无自定义图等逻辑全部可在纯 JVM 下测试。
 *
 * 模板侧契约字段（org.json 写入，模板用 kotlinx 解析、ignoreUnknownKeys）：
 * - enabled: Boolean
 * - duration: Int（毫秒，500..5000）
 * - autoHide: true
 * - backgroundColor: String（如 "#FFFFFF"）
 * - image: String?（有自定义图时为 "splash.png"，否则 null）
 * - transition: String（fade/zoom/slide/dissolve）
 * - skipOnTap: Boolean
 */
data class SplashConfigPayload(
    val enabled: Boolean,
    val mode: String,
    val durationMs: Int,
    val autoHide: Boolean,
    val backgroundColor: String,
    val image: String?,
    val transition: String,
    val skipOnTap: Boolean,
    val showLoading: Boolean,
    val htmlTimeoutMs: Int
)

/**
 * 把领域启动页设置规整为模板契约载荷。
 *
 * @param hasCustomImage 是否存在可用的自定义启动图（由调用方判断文件是否存在）
 */
fun SplashSettings.toSplashConfigPayload(hasCustomImage: Boolean): SplashConfigPayload {
    val clampedDuration = delayMs.coerceIn(SplashSettings.MIN_DURATION_MS, SplashSettings.MAX_DURATION_MS)
    val normalizedTransition = transition.lowercase().let { raw ->
        SplashSettings.SUPPORTED_TRANSITIONS.find { it == raw } ?: "fade"
    }
    val normalizedBg = backgroundColor.normalizedColorOrNull() ?: "#FFFFFF"
    val clampedHtmlTimeout = htmlTimeoutMs.coerceIn(
        SplashSettings.MIN_HTML_TIMEOUT_MS,
        SplashSettings.MAX_HTML_TIMEOUT_MS
    )
    return SplashConfigPayload(
        enabled = enabled,
        mode = mode.name.lowercase(),
        durationMs = clampedDuration,
        autoHide = true,
        backgroundColor = normalizedBg,
        // 仅当确有自定义图时才指向 splash.png；无图时为 null，模板回退纯底色
        image = if (hasCustomImage) "splash.png" else null,
        transition = normalizedTransition,
        skipOnTap = skipOnTap,
        showLoading = showLoadingDots,
        htmlTimeoutMs = clampedHtmlTimeout
    )
}

/**
 * 把 "#RRGGBB" 规整为大写形式；非法输入返回 null 由调用方回退默认色。
 * 兼容 "#RGB" 简写与无 # 前缀。
 */
fun String.normalizedColorOrNull(): String? {
    val raw = this.trim().removePrefix("#")
    val hex = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        else -> return null
    }
    if (!hex.all { it.isHexDigit() }) return null
    return "#${hex.uppercase()}"
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
