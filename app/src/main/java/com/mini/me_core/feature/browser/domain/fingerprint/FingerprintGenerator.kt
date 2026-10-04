package com.mini.me_core.feature.browser.domain.fingerprint

import kotlin.random.Random

/**
 * 指纹随机生成器。
 *
 * 生成内部一致的完整指纹配置。所有生成的配置都会通过 [FingerprintValidator] 校验，
 * 确保不出现"UA 声明 Windows 但 platform 是 Linux"这类低级错误。
 *
 * 生成模板：
 * - random：完全随机（浏览器/OS/地区/档次均随机）
 * - region_us / region_cn / region_jp：指定地区
 * - high_end / low_end：指定硬件档次
 * - mobile / desktop：指定设备类型
 */
object FingerprintGenerator {

    /** 生成模板枚举 */
    enum class Template(val id: String) {
        RANDOM("random"),
        REGION_US("region_us"),
        REGION_CN("region_cn"),
        REGION_JP("region_jp"),
        REGION_EU("region_eu"),
        HIGH_END("high_end"),
        LOW_END("low_end"),
        MOBILE("mobile"),
        DESKTOP("desktop"),
    }

    // ===== WebGL 显卡信息库 =====

    /** 高端显卡 */
    private val HIGH_END_GPUS = listOf(
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 4090/PCIe/SSE2",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 4080/PCIe/SSE2",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 4070 Ti/PCIe/SSE2",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 3090/PCIe/SSE2",
        "Google Inc. (AMD)" to "AMD Radeon RX 7900 XTX/PCIe/SSE2",
        "Google Inc. (AMD)" to "AMD Radeon RX 7900 XT/PCIe/SSE2",
    )

    /** 中端显卡 */
    private val MID_RANGE_GPUS = listOf(
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 4060/PCIe/SSE2",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 3070/PCIe/SSE2",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce RTX 3060/PCIe/SSE2",
        "Google Inc. (AMD)" to "AMD Radeon RX 6700 XT/PCIe/SSE2",
        "Google Inc. (AMD)" to "AMD Radeon RX 6600/PCIe/SSE2",
    )

    /** 低端/集成显卡 */
    private val LOW_END_GPUS = listOf(
        "Google Inc. (Intel)" to "Intel(R) UHD Graphics 770",
        "Google Inc. (Intel)" to "Intel(R) UHD Graphics 630",
        "Google Inc. (Intel)" to "Intel(R) Iris(R) Xe Graphics",
        "Google Inc. (AMD)" to "AMD Radeon(TM) Graphics",
        "Google Inc. (NVIDIA)" to "NVIDIA GeForce GTX 1650/PCIe/SSE2",
    )

    /** Apple Silicon 显卡 */
    private val APPLE_GPUS = listOf(
        "Apple" to "Apple M1",
        "Apple" to "Apple M1 Pro",
        "Apple" to "Apple M1 Max",
        "Apple" to "Apple M2",
        "Apple" to "Apple M2 Pro",
        "Apple" to "Apple M3",
        "Apple" to "Apple M3 Pro",
    )

    // ===== 地区配置 =====

    private data class RegionConfig(
        val region: String,
        val timezone: String,
        val language: String,
        val languages: String,
    )

    private val REGIONS = listOf(
        RegionConfig("US", "America/New_York", "en-US", "en-US,en"),
        RegionConfig("US", "America/Chicago", "en-US", "en-US,en"),
        RegionConfig("US", "America/Los_Angeles", "en-US", "en-US,en"),
        RegionConfig("GB", "Europe/London", "en-GB", "en-GB,en"),
        RegionConfig("DE", "Europe/Berlin", "de-DE", "de-DE,de"),
        RegionConfig("FR", "Europe/Paris", "fr-FR", "fr-FR,fr"),
        RegionConfig("JP", "Asia/Tokyo", "ja-JP", "ja-JP,ja"),
        RegionConfig("KR", "Asia/Seoul", "ko-KR", "ko-KR,ko"),
        RegionConfig("SG", "Asia/Singapore", "en-SG", "en-SG,en"),
        RegionConfig("AU", "Australia/Sydney", "en-AU", "en-AU,en"),
        RegionConfig("CA", "America/Toronto", "en-CA", "en-CA,en"),
    )

    // ===== 屏幕分辨率 =====

    private val DESKTOP_RESOLUTIONS = listOf(
        1920 to 1080,
        2560 to 1440,
        1366 to 768,
        1536 to 864,
        1440 to 900,
        1680 to 1050,
        1920 to 1200,
        3840 to 2160,
    )

    private val MOBILE_RESOLUTIONS = listOf(
        390 to 844,   // iPhone 12/13/14
        414 to 896,   // iPhone 11/XR
        375 to 812,   // iPhone X/XS/11 Pro
        430 to 932,   // iPhone 14 Pro Max
        360 to 800,   // Android 常见
        412 to 915,   // Android 大屏
        384 to 854,   // Android
    )

    /**
     * 生成一个随机指纹配置。
     *
     * @param template 生成模板（null 则完全随机）
     * @param region 指定地区（覆盖 template）
     * @param browser 指定浏览器（覆盖 template）
     * @param deviceType 指定设备类型（覆盖 template）
     * @param name 配置名称（null 则自动生成）
     * @return 生成的指纹配置（已通过一致性校验）
     */
    fun generate(
        template: Template? = null,
        region: String? = null,
        browser: String? = null,
        deviceType: String? = null,
        name: String? = null,
    ): FingerprintProfile {
        // 1. 确定设备类型
        val finalDeviceType = deviceType ?: when (template) {
            Template.MOBILE -> "mobile"
            Template.DESKTOP -> "desktop"
            else -> if (Random.nextFloat() < 0.6f) "desktop" else "mobile"
        }

        // 2. 确定地区
        val finalRegion = region ?: when (template) {
            Template.REGION_US -> "US"
            Template.REGION_CN -> "CN"
            Template.REGION_JP -> "JP"
            Template.REGION_EU -> listOf("GB", "DE", "FR").random()
            else -> REGIONS.random().region
        }
        val regionConfig = REGIONS.firstOrNull { it.region == finalRegion }
            ?: REGIONS.first()

        // 3. 确定浏览器和 UA
        val finalBrowser = browser ?: when (template) {
            else -> if (finalDeviceType == "mobile") {
                // 移动端 Chrome 和 Safari 为主
                if (Random.nextFloat() < 0.5f) "chrome" else "safari"
            } else {
                // 桌面端 Chrome 为主，其次 Safari/Edge/Firefox
                val r = Random.nextFloat()
                when {
                    r < 0.55f -> "chrome"
                    r < 0.75f -> "safari"
                    r < 0.9f -> "edge"
                    else -> "firefox"
                }
            }
        }

        // Safari 只能在 macOS/iOS 上
        val finalOs = when {
            finalBrowser == "safari" && finalDeviceType == "desktop" -> "macos"
            finalBrowser == "safari" && finalDeviceType == "mobile" -> "ios"
            finalDeviceType == "mobile" -> if (Random.nextFloat() < 0.6f) "android" else "ios"
            else -> {
                val r = Random.nextFloat()
                when {
                    r < 0.7f -> "windows"
                    r < 0.9f -> "macos"
                    else -> "linux"
                }
            }
        }

        // 从 UA 池选择匹配的 UA
        val uaEntry = UaPool.weightedRandom(
            browser = finalBrowser,
            os = finalOs,
            deviceType = finalDeviceType,
        ) ?: UaPool.random() ?: UaPool.all.first()

        // 4. 确定硬件档次
        val isHighEnd = template == Template.HIGH_END ||
            (template == null && Random.nextFloat() < 0.2f)
        val isLowEnd = template == Template.LOW_END ||
            (template == null && Random.nextFloat() < 0.3f)

        // 5. 确定硬件配置
        val hardwareConcurrency = when {
            isHighEnd -> Random.nextInt(12, 25)
            isLowEnd -> Random.nextInt(2, 7)
            finalDeviceType == "mobile" -> Random.nextInt(6, 13)
            else -> Random.nextInt(4, 17)
        }

        val deviceMemory = when {
            isHighEnd -> listOf(16, 32).random()
            isLowEnd -> listOf(2, 4).random()
            finalDeviceType == "mobile" -> listOf(4, 6, 8).random()
            else -> listOf(8, 16).random()
        }

        // 6. 确定屏幕
        val (screenWidth, screenHeight) = if (finalDeviceType == "mobile") {
            MOBILE_RESOLUTIONS.random()
        } else {
            DESKTOP_RESOLUTIONS.random()
        }

        val pixelRatio = when {
            finalDeviceType == "mobile" -> listOf(2f, 3f).random()
            screenWidth >= 2560 -> 2f
            else -> listOf(1f, 1.25f, 1.5f, 2f).random()
        }

        val maxTouchPoints = if (finalDeviceType == "mobile") Random.nextInt(5, 11) else 0

        // 7. 确定 WebGL
        val (webglVendor, webglRenderer) = when {
            finalOs == "macos" || finalOs == "ios" -> APPLE_GPUS.random()
            isHighEnd -> HIGH_END_GPUS.random()
            isLowEnd -> LOW_END_GPUS.random()
            else -> listOf(MID_RANGE_GPUS, LOW_END_GPUS).flatten().random()
        }

        // 8. 噪声种子
        val canvasNoiseSeed = Random.nextInt()
        val audioNoiseSeed = Random.nextInt()

        // 9. platform
        val platform = UaPool.inferPlatform(uaEntry.ua)

        // 10. 生成名称
        val profileName = name ?: run {
            val tier = if (isHighEnd) "高端" else if (isLowEnd) "入门" else "主流"
            val browserName = finalBrowser.replaceFirstChar { it.uppercase() }
            "$regionConfig.region $browserName $tier"
        }

        // 11. 构建配置
        val profile = FingerprintProfile(
            id = "fp_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            name = profileName,
            browser = finalBrowser,
            browserVersion = uaEntry.majorVersion.toString() + ".0.0.0",
            userAgent = uaEntry.ua,
            os = finalOs,
            osVersion = when (finalOs) {
                "windows" -> "10"
                "macos" -> "14.0"
                "linux" -> "22.04"
                "android" -> "14"
                "ios" -> "17.0"
                else -> "10"
            },
            platform = platform,
            region = regionConfig.region,
            timezone = regionConfig.timezone,
            language = regionConfig.language,
            languages = regionConfig.languages,
            hardwareConcurrency = hardwareConcurrency,
            deviceMemory = deviceMemory,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            colorDepth = 24,
            pixelRatio = pixelRatio,
            maxTouchPoints = maxTouchPoints,
            webglVendor = webglVendor,
            webglRenderer = webglRenderer,
            canvasNoiseSeed = canvasNoiseSeed,
            audioNoiseSeed = audioNoiseSeed,
            normalizePlugins = true,
            webrtcProtectionLevel = 1,
            canvasNoiseEnabled = true,
            audioNoiseEnabled = true,
            webglSpoofEnabled = true,
            fontLimitEnabled = true,
        )

        // 12. 自动修正（确保一致性）
        return FingerprintValidator.autoFix(profile)
    }

    /**
     * 批量生成多个指纹配置。
     *
     * @param count 生成数量
     * @param template 生成模板
     * @return 生成的配置列表
     */
    fun generateBatch(count: Int, template: Template? = null): List<FingerprintProfile> =
        List(count) { generate(template = template) }

    /**
     * 基于现有配置生成一个"变体"（只修改部分字段，保持核心身份不变）。
     *
     * 用于需要轻微调整指纹但不改变整体身份的场景（如刷新噪声）。
     *
     * @param base 基础配置
     * @param refreshNoise 是否刷新噪声种子
     * @param varyScreen 是否轻微变化屏幕
     * @return 变体配置
     */
    fun generateVariant(
        base: FingerprintProfile,
        refreshNoise: Boolean = true,
        varyScreen: Boolean = false,
    ): FingerprintProfile = base.copy(
        id = "fp_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
        canvasNoiseSeed = if (refreshNoise) Random.nextInt() else base.canvasNoiseSeed,
        audioNoiseSeed = if (refreshNoise) Random.nextInt() else base.audioNoiseSeed,
        screenWidth = if (varyScreen && base.screenWidth > 1366) {
            base.screenWidth + Random.nextInt(-100, 100)
        } else base.screenWidth,
        screenHeight = if (varyScreen && base.screenHeight > 768) {
            base.screenHeight + Random.nextInt(-60, 60)
        } else base.screenHeight,
        createdAt = System.currentTimeMillis(),
    )
}
