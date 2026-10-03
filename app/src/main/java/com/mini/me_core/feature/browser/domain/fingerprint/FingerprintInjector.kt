package com.mini.me_core.feature.browser.domain.fingerprint

/**
 * 指纹注入脚本生成器。
 *
 * 根据 [FingerprintProfile] 生成合并后的 JavaScript 注入脚本，
 * 在页面脚本执行之前注入到 WebView，实现浏览器指纹伪装。
 *
 * 脚本结构：
 * 1. 配置注入区（从指纹配置动态填充的常量）
 * 2. 基础属性伪装区（navigator/screen/Intl 等属性重写）
 * 3. WebRTC 防护区（阻止 ICE candidate 泄露真实 IP）
 * 4. 反检测加固区（toString 欺骗，防止被检测到重写）
 *
 * 后续阶段将扩展：Canvas 噪声、WebGL 伪装、Audio 噪声、字体限制。
 */
object FingerprintInjector {

    /**
     * 根据指纹配置生成完整的注入脚本。
     *
     * @param profile 指纹配置
     * @return 合并后的 JavaScript 代码
     */
    fun generateScript(profile: FingerprintProfile): String {
        return buildString {
            appendLine("(function() {")
            appendLine("  'use strict';")
            appendLine()

            // 1. 配置注入区
            appendConfigSection(profile)

            // 2. 基础属性伪装
            appendBasePropertySection(profile)

            // 3. WebRTC 防护
            if (profile.webrtcProtectionLevel > 0) {
                appendWebRtcSection(profile)
            }

            // 4. 反检测加固（toString 欺骗）
            appendAntiDetectionSection()

            appendLine("})();")
        }
    }

    // ===== 配置注入区 =====

    private fun StringBuilder.appendConfigSection(profile: FingerprintProfile) {
        appendLine("  // ===== 指纹配置（由 FingerprintInjector 注入） =====")
        appendLine("  var __FP_CONFIG__ = {")
        appendLine("    browser: '${profile.browser}',")
        appendLine("    browserVersion: '${profile.browserVersion}',")
        appendLine("    os: '${profile.os}',")
        appendLine("    platform: '${profile.platform}',")
        appendLine("    region: '${profile.region}',")
        appendLine("    timezone: '${profile.timezone}',")
        appendLine("    language: '${profile.language}',")
        appendLine("    languages: '${profile.languages}',")
        appendLine("    hardwareConcurrency: ${profile.hardwareConcurrency},")
        appendLine("    deviceMemory: ${profile.deviceMemory},")
        appendLine("    screenWidth: ${profile.screenWidth},")
        appendLine("    screenHeight: ${profile.screenHeight},")
        appendLine("    colorDepth: ${profile.colorDepth},")
        appendLine("    pixelRatio: ${profile.pixelRatio},")
        appendLine("    maxTouchPoints: ${profile.maxTouchPoints},")
        appendLine("    canvasNoiseSeed: ${profile.canvasNoiseSeed},")
        appendLine("    audioNoiseSeed: ${profile.audioNoiseSeed},")
        appendLine("    webrtcProtection: ${profile.webrtcProtectionLevel},")
        appendLine("    canvasNoiseEnabled: ${profile.canvasNoiseEnabled},")
        appendLine("    audioNoiseEnabled: ${profile.audioNoiseEnabled},")
        appendLine("    webglSpoofEnabled: ${profile.webglSpoofEnabled},")
        appendLine("    fontLimitEnabled: ${profile.fontLimitEnabled}")
        appendLine("  };")
        appendLine()
    }

    // ===== 基础属性伪装区 =====

    private fun StringBuilder.appendBasePropertySection(profile: FingerprintProfile) {
        appendLine("  // ===== 基础属性伪装 =====")

        // navigator.platform
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'platform', {")
        appendLine("      get: function() { return __FP_CONFIG__.platform; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // navigator.hardwareConcurrency
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'hardwareConcurrency', {")
        appendLine("      get: function() { return __FP_CONFIG__.hardwareConcurrency; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // navigator.deviceMemory
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'deviceMemory', {")
        appendLine("      get: function() { return __FP_CONFIG__.deviceMemory; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // navigator.maxTouchPoints
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {")
        appendLine("      get: function() { return __FP_CONFIG__.maxTouchPoints; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // navigator.language / languages
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'language', {")
        appendLine("      get: function() { return __FP_CONFIG__.language; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'languages', {")
        appendLine("      get: function() { return __FP_CONFIG__.languages.split(','); },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // screen.colorDepth / pixelDepth
        appendLine("  try {")
        appendLine("    Object.defineProperty(Screen.prototype, 'colorDepth', {")
        appendLine("      get: function() { return __FP_CONFIG__.colorDepth; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("    Object.defineProperty(Screen.prototype, 'pixelDepth', {")
        appendLine("      get: function() { return __FP_CONFIG__.colorDepth; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // window.devicePixelRatio
        appendLine("  try {")
        appendLine("    Object.defineProperty(window, 'devicePixelRatio', {")
        appendLine("      get: function() { return __FP_CONFIG__.pixelRatio; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("  } catch(e) {}")

        // 时区伪装（Intl.DateTimeFormat）
        appendLine("  try {")
        appendLine("    var originalDateTimeFormat = Intl.DateTimeFormat;")
        appendLine("    Intl.DateTimeFormat = function(locales, options) {")
        appendLine("      options = options || {};")
        appendLine("      options.timeZone = __FP_CONFIG__.timezone;")
        appendLine("      return new originalDateTimeFormat(locales, options);")
        appendLine("    };")
        appendLine("    Intl.DateTimeFormat.prototype = originalDateTimeFormat.prototype;")
        appendLine("    Intl.DateTimeFormat.supportedLocalesOf = originalDateTimeFormat.supportedLocalesOf;")
        appendLine("  } catch(e) {}")

        appendLine()
    }

    // ===== WebRTC 防护区 =====

    private fun StringBuilder.appendWebRtcSection(profile: FingerprintProfile) {
        appendLine("  // ===== WebRTC 防护（阻止真实 IP 泄露） =====")
        appendLine("  try {")
        appendLine("    var originalRTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection;")
        appendLine("    if (originalRTCPeerConnection) {")
        appendLine("      var protectionLevel = __FP_CONFIG__.webrtcProtection;")

        // 级别 1：标准防护（阻止 ICE candidate 传递）
        appendLine("      if (protectionLevel >= 1) {")
        appendLine("        var PatchedPeerConnection = function(configuration) {")
        appendLine("          var pc = new originalRTCPeerConnection(configuration);")
        appendLine("          var originalOnIceCandidate = null;")
        appendLine("          Object.defineProperty(pc, 'onicecandidate', {")
        appendLine("            set: function(handler) {")
        appendLine("              originalOnIceCandidate = handler;")
        appendLine("              this._onicecandidate = function(event) {")
        appendLine("                if (event.candidate && protectionLevel >= 2) {")
        appendLine("                  return; // 严格模式：丢弃所有 candidate")
        appendLine("                }")
        appendLine("                if (event.candidate && event.candidate.candidate) {")
        appendLine("                  var cand = event.candidate.candidate;")
        appendLine("                  // 过滤 srflx（服务器反射地址，含公网IP）和 host（本地IP）candidate")
        appendLine("                  if (cand.indexOf('srflx') !== -1 || cand.indexOf('host') !== -1) {")
        appendLine("                    return;")
        appendLine("                  }")
        appendLine("                }")
        appendLine("                if (originalOnIceCandidate) {")
        appendLine("                  originalOnIceCandidate.call(this, event);")
        appendLine("                }")
        appendLine("              };")
        appendLine("            },")
        appendLine("            get: function() {")
        appendLine("              return this._onicecandidate;")
        appendLine("            }")
        appendLine("          });")
        appendLine("          return pc;")
        appendLine("        };")
        appendLine("        PatchedPeerConnection.prototype = originalRTCPeerConnection.prototype;")
        appendLine("        window.RTCPeerConnection = PatchedPeerConnection;")
        appendLine("        if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = PatchedPeerConnection;")
        appendLine("        if (window.mozRTCPeerConnection) window.mozRTCPeerConnection = PatchedPeerConnection;")
        appendLine("      }")

        // 级别 2：严格防护（完全禁用 WebRTC）
        appendLine("      if (protectionLevel >= 2) {")
        appendLine("        // 完全禁用 createDataChannel 和 addStream")
        appendLine("        var originalCreateDataChannel = originalRTCPeerConnection.prototype.createDataChannel;")
        appendLine("        if (originalCreateDataChannel) {")
        appendLine("          originalRTCPeerConnection.prototype.createDataChannel = function() {")
        appendLine("            throw new Error('WebRTC DataChannel is disabled');")
        appendLine("          };")
        appendLine("        }")
        appendLine("      }")

        appendLine("    }")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== 反检测加固区 =====

    private fun StringBuilder.appendAntiDetectionSection() {
        appendLine("  // ===== 反检测加固（toString 欺骗） =====")
        appendLine("  try {")
        appendLine("    // 为所有被重写的原生方法恢复 [native code] 标识")
        appendLine("    var nativeCodeFn = function() { return '[native code]'; };")
        appendLine("    var methodsToPatch = [")
        appendLine("      [Navigator.prototype, 'platform'],")
        appendLine("      [Navigator.prototype, 'hardwareConcurrency'],")
        appendLine("      [Navigator.prototype, 'deviceMemory'],")
        appendLine("      [Navigator.prototype, 'maxTouchPoints'],")
        appendLine("      [Navigator.prototype, 'language'],")
        appendLine("      [Navigator.prototype, 'languages'],")
        appendLine("      [Screen.prototype, 'colorDepth'],")
        appendLine("      [Screen.prototype, 'pixelDepth']")
        appendLine("    ];")
        appendLine("    methodsToPatch.forEach(function(pair) {")
        appendLine("      try {")
        appendLine("        var desc = Object.getOwnPropertyDescriptor(pair[0], pair[1]);")
        appendLine("        if (desc && desc.get) {")
        appendLine("          desc.get.toString = nativeCodeFn;")
        appendLine("        }")
        appendLine("      } catch(e) {}")
        appendLine("    });")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    /**
     * 生成脚本的体积（字节数），用于性能监控。
     */
    fun estimateScriptSize(profile: FingerprintProfile): Int =
        generateScript(profile).toByteArray().size

    /**
     * 检查脚本是否包含所有必要的伪装模块。
     * 用于自检和调试。
     */
    fun checkScriptCompleteness(script: String): Map<String, Boolean> = mapOf(
        "config_injected" to script.contains("__FP_CONFIG__"),
        "platform_spoofed" to script.contains("Navigator.prototype', 'platform'"),
        "hardwareConcurrency_spoofed" to script.contains("hardwareConcurrency"),
        "deviceMemory_spoofed" to script.contains("deviceMemory"),
        "language_spoofed" to script.contains("Navigator.prototype', 'language'"),
        "timezone_spoofed" to script.contains("Intl.DateTimeFormat"),
        "screen_spoofed" to script.contains("Screen.prototype"),
        "pixelRatio_spoofed" to script.contains("devicePixelRatio"),
        "webrtc_protected" to script.contains("RTCPeerConnection"),
        "toString_patched" to script.contains("native code"),
    )
}
