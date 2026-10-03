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
 * 4. Canvas 指纹噪声区（基于种子的确定性像素扰动）
 * 5. WebGL 伪装区（vendor/renderer 与扩展列表标准化）
 * 6. Audio 指纹噪声区（OfflineAudioContext / AnalyserNode 扰动）
 * 7. 字体指纹限制区（白名单或常见字体标准化）
 * 8. 屏幕尺寸伪装区（screen.width/height/avail*）
 * 9. navigator.webdriver 隐藏区
 * 10. 反检测加固区（toString 欺骗，防止被检测到重写）
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

            // 4. Canvas 指纹噪声
            if (profile.canvasNoiseEnabled) {
                appendCanvasSection()
            }

            // 5. WebGL 伪装
            if (profile.webglSpoofEnabled && profile.webglVendor != null) {
                appendWebGlSection()
            }

            // 6. Audio 指纹噪声
            if (profile.audioNoiseEnabled) {
                appendAudioSection()
            }

            // 7. 字体指纹限制
            if (profile.fontLimitEnabled) {
                appendFontSection()
            }

            // 8. 屏幕尺寸伪装
            appendScreenSizeSection()

            // 9. navigator.webdriver 隐藏
            appendWebDriverSection()

            // 10. 反检测加固（toString 欺骗）
            appendAntiDetectionSection()

            appendLine("})();")
        }
    }

    /** 把字符串安全地转成 JS 字面量（null 输出为 JS null）。 */
    private fun jsLiteral(value: String?): String {
        if (value == null) return "null"
        val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\n", "\\n").replace("\r", "\\r")
        return "'$escaped'"
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
        appendLine("    webglVendor: ${jsLiteral(profile.webglVendor)},")
        appendLine("    webglRenderer: ${jsLiteral(profile.webglRenderer)},")
        appendLine("    fontsWhitelist: ${jsLiteral(profile.fontsWhitelist)},")
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

    // ===== Canvas 指纹噪声区 =====

    private fun StringBuilder.appendCanvasSection() {
        appendLine("  // ===== Canvas 指纹噪声（确定性像素扰动） =====")
        appendLine("  try {")
        // 基于种子的确定性 PRNG（mulberry32），同一种子产生相同噪声序列
        appendLine("    function __fpRng(seed) {")
        appendLine("      var a = (seed ^ 0x9e3779b9) >>> 0;")
        appendLine("      return function() {")
        appendLine("        a |= 0; a = (a + 0x6D2B79F5) | 0;")
        appendLine("        var t = Math.imul(a ^ (a >>> 15), 1 | a);")
        appendLine("        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;")
        appendLine("        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;")
        appendLine("      };")
        appendLine("    }")
        // 对像素缓冲区施加 ±1~2 的 RGB 微扰动（人眼不可见但改变哈希）
        appendLine("    function __fpNoisePixels(data, seed) {")
        appendLine("      var rng = __fpRng(seed + data.length);")
        appendLine("      for (var i = 0; i < data.length; i += 4) {")
        appendLine("        var d = (rng() > 0.5 ? 1 : -1) * (1 + Math.floor(rng() * 2));")
        appendLine("        data[i]   = Math.max(0, Math.min(255, data[i] + d));")
        appendLine("        data[i+1] = Math.max(0, Math.min(255, data[i+1] + d));")
        appendLine("        data[i+2] = Math.max(0, Math.min(255, data[i+2] + d));")
        appendLine("      }")
        appendLine("    }")
        // getImageData 返回带噪声的像素数据
        appendLine("    var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;")
        appendLine("    CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {")
        appendLine("      var img = origGetImageData.apply(this, arguments);")
        appendLine("      __fpNoisePixels(img.data, __FP_CONFIG__.canvasNoiseSeed);")
        appendLine("      return img;")
        appendLine("    };")
        // toDataURL：复制到离屏 canvas，经 getImageData 扰动后导出
        appendLine("    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;")
        appendLine("    HTMLCanvasElement.prototype.toDataURL = function() {")
        appendLine("      try {")
        appendLine("        var w = this.width, h = this.height;")
        appendLine("        if (!w || !h) return origToDataURL.apply(this, arguments);")
        appendLine("        var tmp = document.createElement('canvas');")
        appendLine("        tmp.width = w; tmp.height = h;")
        appendLine("        var tctx = tmp.getContext('2d');")
        appendLine("        tctx.drawImage(this, 0, 0);")
        appendLine("        var img = tctx.getImageData(0, 0, w, h);")
        appendLine("        tctx.putImageData(img, 0, 0);")
        appendLine("        return origToDataURL.apply(tmp, arguments);")
        appendLine("      } catch(e) {")
        appendLine("        return origToDataURL.apply(this, arguments);")
        appendLine("      }")
        appendLine("    };")
        // toBlob：与 toDataURL 相同的离屏扰动流程
        appendLine("    var origToBlob = HTMLCanvasElement.prototype.toBlob;")
        appendLine("    if (origToBlob) {")
        appendLine("      HTMLCanvasElement.prototype.toBlob = function(cb, type, quality) {")
        appendLine("        try {")
        appendLine("          var w = this.width, h = this.height;")
        appendLine("          if (!w || !h) { origToBlob.call(this, cb, type, quality); return; }")
        appendLine("          var tmp = document.createElement('canvas');")
        appendLine("          tmp.width = w; tmp.height = h;")
        appendLine("          var tctx = tmp.getContext('2d');")
        appendLine("          tctx.drawImage(this, 0, 0);")
        appendLine("          var img = tctx.getImageData(0, 0, w, h);")
        appendLine("          tctx.putImageData(img, 0, 0);")
        appendLine("          origToBlob.call(tmp, cb, type, quality);")
        appendLine("        } catch(e) {")
        appendLine("          try { origToBlob.call(this, cb, type, quality); } catch(e2) {}")
        appendLine("        }")
        appendLine("      };")
        appendLine("    }")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== WebGL 指纹伪装区 =====

    private fun StringBuilder.appendWebGlSection() {
        appendLine("  // ===== WebGL 指纹伪装（vendor/renderer 与扩展标准化） =====")
        appendLine("  try {")
        appendLine("    var UNMASKED_VENDOR = 0x9245, UNMASKED_RENDERER = 0x9246;")
        // 常见、与具体硬件无关的扩展白名单（隐藏系统/特有 GPU 扩展）
        appendLine("    var EXT_ALLOWLIST = [")
        appendLine("      'WEBGL_lose_context','OES_texture_float','OES_texture_half_float',")
        appendLine("      'OES_standard_derivatives','WEBGL_depth_texture','ANGLE_instanced_arrays',")
        appendLine("      'OES_element_index_uint','EXT_blend_minmax','EXT_shader_texture_lod',")
        appendLine("      'OES_fbo_render_mipmap','WEBGL_draw_buffers'")
        appendLine("    ];")
        appendLine("    function __fpPatchGl(proto) {")
        appendLine("      if (!proto) return;")
        appendLine("      var origGetParam = proto.getParameter;")
        appendLine("      proto.getParameter = function(pname) {")
        appendLine("        if (pname === UNMASKED_VENDOR) return __FP_CONFIG__.webglVendor;")
        appendLine("        if (pname === UNMASKED_RENDERER) return __FP_CONFIG__.webglRenderer;")
        appendLine("        return origGetParam.call(this, pname);")
        appendLine("      };")
        appendLine("      var origExts = proto.getSupportedExtensions;")
        appendLine("      if (origExts) {")
        appendLine("        proto.getSupportedExtensions = function() {")
        appendLine("          var exts = origExts.call(this) || [];")
        appendLine("          return exts.filter(function(e){ return EXT_ALLOWLIST.indexOf(e) !== -1; });")
        appendLine("        };")
        appendLine("      }")
        appendLine("    }")
        appendLine("    if (typeof WebGLRenderingContext !== 'undefined') __fpPatchGl(WebGLRenderingContext.prototype);")
        appendLine("    if (typeof WebGL2RenderingContext !== 'undefined') __fpPatchGl(WebGL2RenderingContext.prototype);")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== Audio 指纹噪声区 =====

    private fun StringBuilder.appendAudioSection() {
        appendLine("  // ===== Audio 指纹噪声（OfflineAudioContext / AnalyserNode 微扰动） =====")
        appendLine("  try {")
        appendLine("    function __fpAudioRng(seed) {")
        appendLine("      var a = (seed ^ 0x9e3779b9) >>> 0;")
        appendLine("      return function() {")
        appendLine("        a |= 0; a = (a + 0x6D2B79F5) | 0;")
        appendLine("        var t = Math.imul(a ^ (a >>> 15), 1 | a);")
        appendLine("        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;")
        appendLine("        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;")
        appendLine("      };")
        appendLine("    }")
        // startRendering 渲染完成后对通道数据叠加 ±0.0001 级别噪声
        appendLine("    if (typeof OfflineAudioContext !== 'undefined') {")
        appendLine("      var origStartRendering = OfflineAudioContext.prototype.startRendering;")
        appendLine("      OfflineAudioContext.prototype.startRendering = function() {")
        appendLine("        var self = this;")
        appendLine("        return origStartRendering.call(self).then(function(buffer) {")
        appendLine("          var rng = __fpAudioRng(__FP_CONFIG__.audioNoiseSeed);")
        appendLine("          for (var ch = 0; ch < buffer.numberOfChannels; ch++) {")
        appendLine("            var data = buffer.getChannelData(ch);")
        appendLine("            for (var i = 0; i < data.length; i++) {")
        appendLine("              data[i] = data[i] + (rng() - 0.5) * 0.0002;")
        appendLine("            }")
        appendLine("          }")
        appendLine("          return buffer;")
        appendLine("        });")
        appendLine("      };")
        appendLine("    }")
        // AnalyserNode 频域/时域数据轻微扰动，避免被音频指纹采样
        appendLine("    if (typeof AnalyserNode !== 'undefined') {")
        appendLine("      var origGetFreq = AnalyserNode.prototype.getByteFrequencyData;")
        appendLine("      AnalyserNode.prototype.getByteFrequencyData = function(arr) {")
        appendLine("        origGetFreq.call(this, arr);")
        appendLine("        var rng = __fpAudioRng(__FP_CONFIG__.audioNoiseSeed + 1);")
        appendLine("        for (var i = 0; i < arr.length; i++) {")
        appendLine("          arr[i] = Math.max(0, Math.min(255, arr[i] + Math.floor((rng() - 0.5) * 2)));")
        appendLine("        }")
        appendLine("      };")
        appendLine("      var origGetTime = AnalyserNode.prototype.getByteTimeDomainData;")
        appendLine("      AnalyserNode.prototype.getByteTimeDomainData = function(arr) {")
        appendLine("        origGetTime.call(this, arr);")
        appendLine("        var rng = __fpAudioRng(__FP_CONFIG__.audioNoiseSeed + 2);")
        appendLine("        for (var i = 0; i < arr.length; i++) {")
        appendLine("          arr[i] = Math.max(0, Math.min(255, arr[i] + Math.floor((rng() - 0.5) * 2)));")
        appendLine("        }")
        appendLine("      };")
        appendLine("    }")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== 字体指纹限制区 =====

    private fun StringBuilder.appendFontSection() {
        appendLine("  // ===== 字体指纹限制（白名单 / 常见字体标准化） =====")
        appendLine("  try {")
        appendLine("    if (typeof FontFaceSet === 'undefined' || !FontFaceSet.prototype.check) return;")
        // 从 CSS font spec（如 "12px Arial"）中提取字体名
        appendLine("    function __fpFontName(fontSpec) {")
        appendLine("      var m = /([a-zA-Z0-9_\\- ,]+)\\s*$/.exec(String(fontSpec || ''));")
        appendLine("      return m ? m[1].trim().toLowerCase() : '';")
        appendLine("    }")
        appendLine("    var origCheck = FontFaceSet.prototype.check;")
        appendLine("    var whitelist = __FP_CONFIG__.fontsWhitelist;")
        appendLine("    if (whitelist) {")
        // 白名单模式：仅白名单内字体判定为可用
        appendLine("      var list = whitelist.split(',').map(function(s){ return s.trim().toLowerCase(); });")
        appendLine("      FontFaceSet.prototype.check = function(fontSpec, text) {")
        appendLine("        var name = __fpFontName(fontSpec);")
        appendLine("        if (name) {")
        appendLine("          var hit = list.some(function(w){ return w && (name.indexOf(w) !== -1 || w.indexOf(name) !== -1); });")
        appendLine("          if (!hit) return false;")
        appendLine("        }")
        appendLine("        return origCheck.call(this, fontSpec, text);")
        appendLine("      };")
        appendLine("    } else {")
        // 标准化模式：非常见系统字体统一判定为不可用，隐藏特有字体
        appendLine("      var common = ['arial','helvetica','times','courier','verdana','georgia','palatino',")
        appendLine("        'garamond','bookman','comic','trebuchet','impact','segoe','roboto','sans-serif',")
        appendLine("        'serif','monospace','monaco','consolas','menlo','system-ui','inter','arial black'];")
        appendLine("      FontFaceSet.prototype.check = function(fontSpec, text) {")
        appendLine("        var name = __fpFontName(fontSpec);")
        appendLine("        if (name) {")
        appendLine("          var known = common.some(function(c){ return name.indexOf(c) !== -1; });")
        appendLine("          if (!known) return false;")
        appendLine("        }")
        appendLine("        return origCheck.call(this, fontSpec, text);")
        appendLine("      };")
        appendLine("    }")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== 屏幕尺寸伪装区 =====

    private fun StringBuilder.appendScreenSizeSection() {
        appendLine("  // ===== 屏幕尺寸伪装 =====")
        appendLine("  try {")
        appendLine("    Object.defineProperty(Screen.prototype, 'width', {")
        appendLine("      get: function() { return __FP_CONFIG__.screenWidth; }, configurable: true });")
        appendLine("    Object.defineProperty(Screen.prototype, 'height', {")
        appendLine("      get: function() { return __FP_CONFIG__.screenHeight; }, configurable: true });")
        appendLine("    Object.defineProperty(Screen.prototype, 'availWidth', {")
        appendLine("      get: function() { return __FP_CONFIG__.screenWidth; }, configurable: true });")
        appendLine("    Object.defineProperty(Screen.prototype, 'availHeight', {")
        appendLine("      get: function() { return __FP_CONFIG__.screenHeight - 40; }, configurable: true });")
        appendLine("    Object.defineProperty(Screen.prototype, 'availTop', {")
        appendLine("      get: function() { return 0; }, configurable: true });")
        appendLine("    Object.defineProperty(Screen.prototype, 'availLeft', {")
        appendLine("      get: function() { return 0; }, configurable: true });")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== navigator.webdriver 隐藏区 =====

    private fun StringBuilder.appendWebDriverSection() {
        appendLine("  // ===== navigator.webdriver 隐藏 =====")
        appendLine("  try {")
        appendLine("    Object.defineProperty(Navigator.prototype, 'webdriver', {")
        appendLine("      get: function() { return false; },")
        appendLine("      configurable: true")
        appendLine("    });")
        appendLine("    if (window.navigator) {")
        appendLine("      try { delete window.navigator.webdriver; } catch(e) {}")
        appendLine("    }")
        appendLine("  } catch(e) {}")
        appendLine()
    }

    // ===== 反检测加固区 =====

    private fun StringBuilder.appendAntiDetectionSection() {
        appendLine("  // ===== 反检测加固（toString 欺骗） =====")
        appendLine("  try {")
        appendLine("    var nativeCodeFn = function() { return '[native code]'; };")
        // 为指定对象/方法恢复 [native code] 标识（同时覆盖 getter 与函数本身）
        appendLine("    function __fpPatchToString(target, prop) {")
        appendLine("      try {")
        appendLine("        if (!target) return;")
        appendLine("        var v = target[prop];")
        appendLine("        if (typeof v === 'function') v.toString = nativeCodeFn;")
        appendLine("        var d = Object.getOwnPropertyDescriptor(target, prop);")
        appendLine("        if (d && d.get) d.get.toString = nativeCodeFn;")
        appendLine("      } catch(e) {}")
        appendLine("    }")
        appendLine("    var pairs = [")
        // 基础属性 getter
        appendLine("      [Navigator.prototype, 'platform'],")
        appendLine("      [Navigator.prototype, 'hardwareConcurrency'],")
        appendLine("      [Navigator.prototype, 'deviceMemory'],")
        appendLine("      [Navigator.prototype, 'maxTouchPoints'],")
        appendLine("      [Navigator.prototype, 'language'],")
        appendLine("      [Navigator.prototype, 'languages'],")
        appendLine("      [Navigator.prototype, 'webdriver'],")
        appendLine("      [Screen.prototype, 'colorDepth'],")
        appendLine("      [Screen.prototype, 'pixelDepth'],")
        appendLine("      [Screen.prototype, 'width'],")
        appendLine("      [Screen.prototype, 'height'],")
        appendLine("      [Screen.prototype, 'availWidth'],")
        appendLine("      [Screen.prototype, 'availHeight'],")
        appendLine("      [Screen.prototype, 'availTop'],")
        appendLine("      [Screen.prototype, 'availLeft'],")
        // Canvas
        appendLine("      [typeof CanvasRenderingContext2D !== 'undefined' ? CanvasRenderingContext2D.prototype : null, 'getImageData'],")
        appendLine("      [typeof HTMLCanvasElement !== 'undefined' ? HTMLCanvasElement.prototype : null, 'toDataURL'],")
        appendLine("      [typeof HTMLCanvasElement !== 'undefined' ? HTMLCanvasElement.prototype : null, 'toBlob'],")
        // WebGL
        appendLine("      [typeof WebGLRenderingContext !== 'undefined' ? WebGLRenderingContext.prototype : null, 'getParameter'],")
        appendLine("      [typeof WebGLRenderingContext !== 'undefined' ? WebGLRenderingContext.prototype : null, 'getSupportedExtensions'],")
        appendLine("      [typeof WebGL2RenderingContext !== 'undefined' ? WebGL2RenderingContext.prototype : null, 'getParameter'],")
        appendLine("      [typeof WebGL2RenderingContext !== 'undefined' ? WebGL2RenderingContext.prototype : null, 'getSupportedExtensions'],")
        // Audio
        appendLine("      [typeof OfflineAudioContext !== 'undefined' ? OfflineAudioContext.prototype : null, 'startRendering'],")
        appendLine("      [typeof AnalyserNode !== 'undefined' ? AnalyserNode.prototype : null, 'getByteFrequencyData'],")
        appendLine("      [typeof AnalyserNode !== 'undefined' ? AnalyserNode.prototype : null, 'getByteTimeDomainData'],")
        // Font
        appendLine("      [typeof FontFaceSet !== 'undefined' ? FontFaceSet.prototype : null, 'check']")
        appendLine("    ];")
        appendLine("    pairs.forEach(function(pair){ __fpPatchToString(pair[0], pair[1]); });")
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
        "screen_size_spoofed" to script.contains("Screen.prototype, 'width'"),
        "pixelRatio_spoofed" to script.contains("devicePixelRatio"),
        "webrtc_protected" to script.contains("RTCPeerConnection"),
        "canvas_noise" to (script.contains("Canvas 指纹噪声") && script.contains("getImageData")),
        "webgl_spoofed" to (script.contains("WebGL 指纹伪装") && script.contains("UNMASKED_VENDOR")),
        "audio_noise" to (script.contains("Audio 指纹噪声") && script.contains("startRendering")),
        "font_limited" to (script.contains("字体指纹限制") && script.contains("FontFaceSet")),
        "webdriver_hidden" to (script.contains("navigator.webdriver 隐藏") && script.contains("webdriver")),
        "toString_patched" to script.contains("native code"),
    )
}
