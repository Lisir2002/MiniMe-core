package com.mini.me_core.feature.browser.domain.fingerprint

/**
 * 指纹一致性校验引擎。
 *
 * 检查指纹配置内部各参数是否匹配。不一致的指纹反而会成为更强的特征
 * （如 UA 声明 Chrome on Windows 但 platform 是 Linux arm64），因此
 * 一致性校验是指纹伪装的生命线。
 *
 * 校验规则分为三个级别：
 * - error：严重不一致，必须修正（如 UA 与 platform 不匹配）
 * - warning：中度不一致，建议修正（如时区与语言不匹配）
 * - info：轻微不一致，可接受（如屏幕比例非主流）
 */
object FingerprintValidator {

    /**
     * 校验结果。
     */
    data class ValidationResult(
        /** 是否通过（无 error 级问题） */
        val valid: Boolean,
        /** 综合评分（0-100），扣分制，每发现一个 error 扣 20 分，warning 扣 10 分，info 扣 3 分 */
        val score: Float,
        /** 发现的问题列表 */
        val issues: List<Issue>,
    ) {
        /** 是否有可自动修正的问题 */
        val hasAutoFixable: Boolean
            get() = issues.any { it.autoFixable }

        /** 问题摘要（用于日志） */
        val summary: String
            get() = if (issues.isEmpty()) "全部通过"
            else "${issues.size} 个问题（${issues.count { it.level == "error" }} error / ${issues.count { it.level == "warning" }} warning / ${issues.count { it.level == "info" }} info）"
    }

    /**
     * 单个校验问题。
     */
    data class Issue(
        /** 级别：error / warning / info */
        val level: String,
        /** 涉及的字段（如 "userAgent/platform"） */
        val field: String,
        /** 问题描述 */
        val message: String,
        /** 修正建议 */
        val suggestion: String,
        /** 是否可自动修正 */
        val autoFixable: Boolean = false,
    )

    /**
     * 校验指纹配置的内部一致性。
     *
     * @param profile 待校验的指纹配置
     * @return 校验结果
     */
    fun validate(profile: FingerprintProfile): ValidationResult {
        val issues = mutableListOf<Issue>()

        // ===== 1. UA 与浏览器类型匹配 =====
        checkUaBrowserMatch(profile, issues)

        // ===== 2. OS 与 platform 匹配 =====
        checkOsPlatformMatch(profile, issues)

        // ===== 3. UA 与 OS 匹配 =====
        checkUaOsMatch(profile, issues)

        // ===== 4. 地区与时区/语言匹配 =====
        checkRegionTimezoneLanguageMatch(profile, issues)

        // ===== 5. WebGL 与硬件配置匹配 =====
        checkWebglHardwareMatch(profile, issues)

        // ===== 6. 设备类型与触摸/屏幕匹配 =====
        checkDeviceTypeMatch(profile, issues)

        // ===== 7. 浏览器版本合理性 =====
        checkBrowserVersionReasonable(profile, issues)

        // ===== 8. 像素比与设备类型匹配 =====
        checkPixelRatioMatch(profile, issues)

        // 计算评分
        val errorCount = issues.count { it.level == "error" }
        val warningCount = issues.count { it.level == "warning" }
        val infoCount = issues.count { it.level == "info" }
        val score = (100f - errorCount * 20f - warningCount * 10f - infoCount * 3f).coerceIn(0f, 100f)

        return ValidationResult(
            valid = errorCount == 0,
            score = score,
            issues = issues.toList(),
        )
    }

    // ===== 具体校验规则 =====

    /** 1. UA 与浏览器类型匹配 */
    private fun checkUaBrowserMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val ua = profile.userAgent ?: return
        val inferredBrowser = UaPool.inferBrowser(ua)
        if (inferredBrowser != "unknown" && inferredBrowser != profile.browser) {
            issues.add(Issue(
                level = "error",
                field = "userAgent/browser",
                message = "UA 推断为 $inferredBrowser，但配置声明为 ${profile.browser}",
                suggestion = "将 browser 改为 $inferredBrowser，或更换为 ${profile.browser} 的 UA",
                autoFixable = true,
            ))
        }
    }

    /** 2. OS 与 platform 匹配 */
    private fun checkOsPlatformMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val expectedPlatform = when (profile.os) {
            "windows" -> "Win32"
            "macos" -> "MacIntel"
            "linux" -> "Linux x86_64"
            "android" -> "Linux armv8l"
            "ios" -> "iPhone"
            else -> return
        }
        if (profile.platform != expectedPlatform) {
            issues.add(Issue(
                level = "error",
                field = "os/platform",
                message = "OS 为 ${profile.os}，期望 platform 为 $expectedPlatform，实际为 ${profile.platform}",
                suggestion = "将 platform 改为 $expectedPlatform",
                autoFixable = true,
            ))
        }
    }

    /** 3. UA 与 OS 匹配 */
    private fun checkUaOsMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val ua = profile.userAgent ?: return
        val inferredOs = UaPool.inferOs(ua)
        if (inferredOs != "unknown" && inferredOs != profile.os) {
            issues.add(Issue(
                level = "error",
                field = "userAgent/os",
                message = "UA 推断 OS 为 $inferredOs，但配置声明为 ${profile.os}",
                suggestion = "将 os 改为 $inferredOs，或更换为 ${profile.os} 的 UA",
                autoFixable = true,
            ))
        }
    }

    /** 4. 地区与时区/语言匹配 */
    private fun checkRegionTimezoneLanguageMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        // 地区与时区匹配
        val expectedTimezone = regionToTimezone(profile.region)
        if (expectedTimezone != null && profile.timezone != expectedTimezone) {
            // 同一国家可能有多个时区，只给 warning
            val sameCountry = profile.timezone.startsWith(profile.region) ||
                timezoneCountry(profile.timezone) == profile.region
            if (!sameCountry) {
                issues.add(Issue(
                    level = "warning",
                    field = "region/timezone",
                    message = "地区为 ${profile.region}，但时区为 ${profile.timezone}（期望约为 $expectedTimezone）",
                    suggestion = "将 timezone 改为 $expectedTimezone，或将 region 改为 ${timezoneCountry(profile.timezone)}",
                    autoFixable = true,
                ))
            }
        }

        // 地区与语言匹配
        val expectedLanguage = regionToLanguage(profile.region)
        if (expectedLanguage != null && !profile.language.startsWith(expectedLanguage.take(2))) {
            issues.add(Issue(
                level = "warning",
                field = "region/language",
                message = "地区为 ${profile.region}，但语言为 ${profile.language}（期望约为 $expectedLanguage）",
                suggestion = "将 language 改为 $expectedLanguage，或将 region 改为对应地区",
                autoFixable = true,
            ))
        }

        // 时区与语言一致性（美国时区配中文是强特征）
        if (profile.timezone.contains("America") && profile.language.startsWith("zh")) {
            issues.add(Issue(
                level = "warning",
                field = "timezone/language",
                message = "时区为美洲，但语言为中文（美国用户很少用中文系统）",
                suggestion = "将 language 改为 en-US，或将 timezone 改为 Asia/Shanghai",
                autoFixable = true,
            ))
        }
    }

    /** 5. WebGL 与硬件配置匹配 */
    private fun checkWebglHardwareMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val renderer = profile.webglRenderer ?: return

        // 高端显卡应配高 CPU/内存
        val isHighEndGpu = renderer.contains("RTX 40") || renderer.contains("RTX 3090") ||
            renderer.contains("RX 7900") || renderer.contains("M2 Ultra") || renderer.contains("M3 Max")
        val isMidRangeGpu = renderer.contains("RTX 3060") || renderer.contains("RTX 3070") ||
            renderer.contains("RX 6700") || renderer.contains("M2 Pro") || renderer.contains("M3 Pro")

        if (isHighEndGpu) {
            if (profile.hardwareConcurrency < 12) {
                issues.add(Issue(
                    level = "warning",
                    field = "webglRenderer/hardwareConcurrency",
                    message = "高端显卡（$renderer）配 ${profile.hardwareConcurrency} 核 CPU 不合理（高端显卡通常配 12 核以上）",
                    suggestion = "将 hardwareConcurrency 改为 16，或更换为中低端显卡",
                    autoFixable = true,
                ))
            }
            if (profile.deviceMemory < 16) {
                issues.add(Issue(
                    level = "warning",
                    field = "webglRenderer/deviceMemory",
                    message = "高端显卡配 ${profile.deviceMemory}GB 内存不合理（高端显卡通常配 16GB 以上）",
                    suggestion = "将 deviceMemory 改为 16 或 32",
                    autoFixable = true,
                ))
            }
        } else if (isMidRangeGpu) {
            if (profile.hardwareConcurrency < 6) {
                issues.add(Issue(
                    level = "info",
                    field = "webglRenderer/hardwareConcurrency",
                    message = "中端显卡配 ${profile.hardwareConcurrency} 核 CPU 偏低",
                    suggestion = "将 hardwareConcurrency 改为 8",
                    autoFixable = true,
                ))
            }
        }

        // 集成显卡不应配过高配置
        val isIntegratedGpu = renderer.contains("UHD") || renderer.contains("Iris") ||
            renderer.contains("Radeon Graphics") || renderer.contains("Vega")
        if (isIntegratedGpu && profile.hardwareConcurrency > 16) {
            issues.add(Issue(
                level = "info",
                field = "webglRenderer/hardwareConcurrency",
                message = "集成显卡配 ${profile.hardwareConcurrency} 核 CPU 偏高（集成显卡通常在中低端机型）",
                suggestion = "将 hardwareConcurrency 改为 8 或更低",
                autoFixable = true,
            ))
        }
    }

    /** 6. 设备类型与触摸/屏幕匹配 */
    private fun checkDeviceTypeMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val isMobileOs = profile.os == "android" || profile.os == "ios"

        // 移动端应有触摸支持
        if (isMobileOs && profile.maxTouchPoints == 0) {
            issues.add(Issue(
                level = "error",
                field = "os/maxTouchPoints",
                message = "移动端 OS（${profile.os}）但 maxTouchPoints=0（移动端通常支持触摸）",
                suggestion = "将 maxTouchPoints 改为 5 或更高",
                autoFixable = true,
            ))
        }

        // 桌面端通常不支持触摸（除了触摸屏笔记本，但较少见）
        if (!isMobileOs && profile.maxTouchPoints > 0) {
            issues.add(Issue(
                level = "info",
                field = "os/maxTouchPoints",
                message = "桌面端 OS（${profile.os}）但 maxTouchPoints=${profile.maxTouchPoints}（触摸屏笔记本较少见）",
                suggestion = "如非触摸屏笔记本，将 maxTouchPoints 改为 0",
                autoFixable = true,
            ))
        }

        // 移动端屏幕尺寸
        if (isMobileOs && profile.screenWidth > 800) {
            issues.add(Issue(
                level = "warning",
                field = "os/screenWidth",
                message = "移动端 OS 但屏幕宽度 ${profile.screenWidth}px（移动端通常 < 500px CSS 像素）",
                suggestion = "将 screenWidth 改为 390 或 414，screenHeight 改为 844 或 896",
                autoFixable = true,
            ))
        }
    }

    /** 7. 浏览器版本合理性 */
    private fun checkBrowserVersionReasonable(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val majorVersion = profile.browserVersion.substringBefore(".").toIntOrNull() ?: return

        // Chrome/Edge 版本应在 100-130 之间（2024-2026 年范围）
        if ((profile.browser == "chrome" || profile.browser == "edge") &&
            (majorVersion < 100 || majorVersion > 130)) {
            issues.add(Issue(
                level = "warning",
                field = "browserVersion",
                message = "${profile.browser} 版本 $majorVersion 不在合理范围（100-130）",
                suggestion = "将 browserVersion 改为 120-126 之间的版本",
                autoFixable = true,
            ))
        }

        // Safari 版本应在 15-18 之间
        if (profile.browser == "safari" && (majorVersion < 15 || majorVersion > 18)) {
            issues.add(Issue(
                level = "warning",
                field = "browserVersion",
                message = "Safari 版本 $majorVersion 不在合理范围（15-18）",
                suggestion = "将 browserVersion 改为 16 或 17",
                autoFixable = true,
            ))
        }

        // Firefox 版本应在 100-130 之间
        if (profile.browser == "firefox" && (majorVersion < 100 || majorVersion > 130)) {
            issues.add(Issue(
                level = "warning",
                field = "browserVersion",
                message = "Firefox 版本 $majorVersion 不在合理范围（100-130）",
                suggestion = "将 browserVersion 改为 115-127 之间的版本",
                autoFixable = true,
            ))
        }
    }

    /** 8. 像素比与设备类型匹配 */
    private fun checkPixelRatioMatch(profile: FingerprintProfile, issues: MutableList<Issue>) {
        val isMobileOs = profile.os == "android" || profile.os == "ios"

        // 移动端像素比通常 2-3
        if (isMobileOs && profile.pixelRatio < 2f) {
            issues.add(Issue(
                level = "warning",
                field = "os/pixelRatio",
                message = "移动端 OS 但 pixelRatio=${profile.pixelRatio}（移动端通常 2-3）",
                suggestion = "将 pixelRatio 改为 2 或 3",
                autoFixable = true,
            ))
        }

        // 桌面端像素比通常 1-2（高 DPI 屏可能 2）
        if (!isMobileOs && profile.pixelRatio > 2.5f) {
            issues.add(Issue(
                level = "info",
                field = "os/pixelRatio",
                message = "桌面端 OS 但 pixelRatio=${profile.pixelRatio}（桌面端通常 1-2）",
                suggestion = "将 pixelRatio 改为 1 或 2",
                autoFixable = true,
            ))
        }
    }

    // ===== 辅助函数 =====

    /** 地区到时区的映射（主要国家的主要时区） */
    private fun regionToTimezone(region: String): String? = when (region.uppercase()) {
        "US" -> "America/New_York"
        "CN" -> "Asia/Shanghai"
        "JP" -> "Asia/Tokyo"
        "GB" -> "Europe/London"
        "DE" -> "Europe/Berlin"
        "FR" -> "Europe/Paris"
        "KR" -> "Asia/Seoul"
        "SG" -> "Asia/Singapore"
        "AU" -> "Australia/Sydney"
        "CA" -> "America/Toronto"
        "RU" -> "Europe/Moscow"
        "IN" -> "Asia/Kolkata"
        "BR" -> "America/Sao_Paulo"
        else -> null
    }

    /** 时区到国家代码的映射（取时区前缀） */
    private fun timezoneCountry(timezone: String): String = when {
        timezone.startsWith("America/") -> "US"
        timezone.startsWith("Asia/Shanghai") || timezone.startsWith("Asia/Chongqing") -> "CN"
        timezone.startsWith("Asia/Tokyo") -> "JP"
        timezone.startsWith("Europe/London") -> "GB"
        timezone.startsWith("Europe/Berlin") -> "DE"
        timezone.startsWith("Europe/Paris") -> "FR"
        timezone.startsWith("Asia/Seoul") -> "KR"
        else -> timezone.substringBefore("/").uppercase()
    }

    /** 地区到语言的映射 */
    private fun regionToLanguage(region: String): String? = when (region.uppercase()) {
        "US" -> "en-US"
        "CN" -> "zh-CN"
        "JP" -> "ja-JP"
        "GB" -> "en-GB"
        "DE" -> "de-DE"
        "FR" -> "fr-FR"
        "KR" -> "ko-KR"
        "SG" -> "en-SG"
        "AU" -> "en-AU"
        "CA" -> "en-CA"
        "RU" -> "ru-RU"
        "IN" -> "hi-IN"
        "BR" -> "pt-BR"
        else -> null
    }

    /**
     * 尝试自动修正所有可修正的问题。
     *
     * @param profile 原始配置
     * @return 修正后的配置（如无可修正项返回原配置）
     */
    fun autoFix(profile: FingerprintProfile): FingerprintProfile {
        var result = profile
        val issues = validate(profile).issues.filter { it.autoFixable }

        for (issue in issues) {
            result = when (issue.field) {
                "os/platform" -> {
                    val expectedPlatform = when (result.os) {
                        "windows" -> "Win32"
                        "macos" -> "MacIntel"
                        "linux" -> "Linux x86_64"
                        "android" -> "Linux armv8l"
                        "ios" -> "iPhone"
                        else -> result.platform
                    }
                    result.copy(platform = expectedPlatform)
                }
                "os/maxTouchPoints" -> {
                    val isMobile = result.os == "android" || result.os == "ios"
                    result.copy(maxTouchPoints = if (isMobile) 5 else 0)
                }
                "os/pixelRatio" -> {
                    val isMobile = result.os == "android" || result.os == "ios"
                    result.copy(pixelRatio = if (isMobile) 2f else 1f)
                }
                "os/screenWidth" -> {
                    val isMobile = result.os == "android" || result.os == "ios"
                    if (isMobile) result.copy(screenWidth = 390, screenHeight = 844) else result
                }
                "region/timezone" -> {
                    val tz = regionToTimezone(result.region) ?: result.timezone
                    result.copy(timezone = tz)
                }
                "region/language", "timezone/language" -> {
                    val lang = regionToLanguage(result.region) ?: result.language
                    result.copy(language = lang, languages = "$lang,${lang.substringBefore("-")}")
                }
                "webglRenderer/hardwareConcurrency" -> {
                    val renderer = result.webglRenderer ?: ""
                    val cores = when {
                        renderer.contains("RTX 40") || renderer.contains("RTX 3090") -> 16
                        renderer.contains("RTX 3060") || renderer.contains("RTX 3070") -> 8
                        else -> result.hardwareConcurrency
                    }
                    result.copy(hardwareConcurrency = cores)
                }
                "webglRenderer/deviceMemory" -> {
                    val renderer = result.webglRenderer ?: ""
                    val mem = when {
                        renderer.contains("RTX 40") || renderer.contains("RTX 3090") -> 16
                        else -> result.deviceMemory
                    }
                    result.copy(deviceMemory = mem)
                }
                else -> result
            }
        }
        return result
    }
}
