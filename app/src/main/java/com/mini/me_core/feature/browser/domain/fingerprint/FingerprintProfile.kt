package com.mini.me_core.feature.browser.domain.fingerprint

import kotlinx.serialization.Serializable

/**
 * 浏览器指纹配置。
 *
 * 一组内部一致的浏览器参数集合，可整体切换应用到 WebView。
 * 所有字段均为可选项，未设置的字段使用浏览器真实值（不伪装）。
 *
 * 一致性约束（由 [FingerprintValidator] 校验）：
 * - [browser] 与 [userAgent] 必须匹配
 * - [os] 与 [platform] 必须匹配
 * - [region] 与 [timezone]/[language] 应匹配
 * - [webglRenderer] 与 [hardwareConcurrency]/[deviceMemory] 应匹配（高端显卡配高配置）
 */
@Serializable
data class FingerprintProfile(
    /** 唯一标识 */
    val id: String,

    /** 配置名称（用户可读） */
    val name: String,

    // ===== 浏览器标识 =====

    /** 浏览器类型：chrome / safari / firefox / edge */
    val browser: String = "chrome",

    /** 浏览器版本，如 "120.0.0.0" */
    val browserVersion: String = "120.0.0.0",

    /** 完整 User-Agent 字符串（如设置则优先使用，否则由 browser+browserVersion+os 生成） */
    val userAgent: String? = null,

    // ===== 操作系统 =====

    /** 操作系统：windows / macos / linux / android / ios */
    val os: String = "windows",

    /** 操作系统版本，如 "10" / "14.0" */
    val osVersion: String = "10",

    /** navigator.platform 值，如 "Win32" / "MacIntel" / "Linux x86_64" / "iPhone" */
    val platform: String = "Win32",

    // ===== 地区与语言 =====

    /** 地区代码：US / CN / JP / GB / DE / FR 等 */
    val region: String = "US",

    /** 时区，如 "America/New_York" / "Asia/Shanghai" */
    val timezone: String = "America/New_York",

    /** 主语言，如 "en-US" / "zh-CN" / "ja-JP" */
    val language: String = "en-US",

    /** 接受语言列表（navigator.languages），逗号分隔 */
    val languages: String = "en-US,en",

    // ===== 硬件属性 =====

    /** CPU 核心数（navigator.hardwareConcurrency），范围 2-32 */
    val hardwareConcurrency: Int = 8,

    /** 设备内存 GB（navigator.deviceMemory），范围 2-32 */
    val deviceMemory: Int = 8,

    /** 屏幕宽度 px */
    val screenWidth: Int = 1920,

    /** 屏幕高度 px */
    val screenHeight: Int = 1080,

    /** 色深（screen.colorDepth），通常 24 */
    val colorDepth: Int = 24,

    /** 像素比（window.devicePixelRatio），范围 1-3 */
    val pixelRatio: Float = 1f,

    /** 最大触摸点数（navigator.maxTouchPoints），桌面通常 0，移动通常 5+ */
    val maxTouchPoints: Int = 0,

    // ===== WebGL =====

    /** WebGL 供应商（UNMASKED_VENDOR_WEBGL），如 "Google Inc. (NVIDIA)" */
    val webglVendor: String? = null,

    /** WebGL 渲染器（UNMASKED_RENDERER_WEBGL），如 "NVIDIA GeForce RTX 3060/PCIe/SSE2" */
    val webglRenderer: String? = null,

    // ===== 指纹噪声种子 =====

    /** Canvas 噪声种子（决定噪声模式，同一种子产生一致的噪声） */
    val canvasNoiseSeed: Int = 0,

    /** Audio 噪声种子 */
    val audioNoiseSeed: Int = 0,

    // ===== 字体与插件 =====

    /** 字体白名单（逗号分隔），为空则不限制字体检测 */
    val fontsWhitelist: String? = null,

    /** 是否标准化插件列表（隐藏系统特有插件） */
    val normalizePlugins: Boolean = true,

    // ===== 防护开关 =====

    /** WebRTC 防护级别：0=关闭 1=标准（阻止ICE） 2=严格（完全禁用） */
    val webrtcProtectionLevel: Int = 1,

    /** 是否启用 Canvas 指纹噪声 */
    val canvasNoiseEnabled: Boolean = true,

    /** 是否启用 Audio 指纹噪声 */
    val audioNoiseEnabled: Boolean = true,

    /** 是否启用 WebGL 指纹伪装 */
    val webglSpoofEnabled: Boolean = true,

    /** 是否启用字体指纹限制 */
    val fontLimitEnabled: Boolean = true,

    // ===== 运行时状态（不参与序列化持久化的瞬时状态） =====

    /** 质量评分（0-100），由检测结果更新 */
    val score: Float = 0f,

    /** 状态：active / cooling / retired */
    val status: String = "active",

    /** 累计使用次数 */
    val totalUses: Int = 0,

    /** 最后使用时间戳（ms） */
    val lastUsedAt: Long = 0,

    /** 冷却到期时间戳（ms），0 表示不在冷却期 */
    val coolingUntil: Long = 0,

    /** 创建时间戳（ms） */
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** 是否在冷却期 */
    val isCooling: Boolean
        get() = coolingUntil > System.currentTimeMillis()

    /** 是否可用（active 且不在冷却期） */
    val isAvailable: Boolean
        get() = status == "active" && !isCooling

    /** 简短描述（用于日志和 UI 摘要） */
    val shortDescription: String
        get() = "$name · ${browser.replaceFirstChar { it.uppercase() }} $browserVersion · $region · 评分${score.toInt()}"

    companion object {
        /** 默认配置 ID */
        const val DEFAULT_ID = "default_real"

        /** 创建一个"真实指纹"配置（所有伪装关闭，使用浏览器真实值） */
        fun realFingerprint(name: String = "真实指纹（无伪装）"): FingerprintProfile =
            FingerprintProfile(
                id = DEFAULT_ID,
                name = name,
                webrtcProtectionLevel = 0,
                canvasNoiseEnabled = false,
                audioNoiseEnabled = false,
                webglSpoofEnabled = false,
                fontLimitEnabled = false,
                normalizePlugins = false,
            )
    }
}
