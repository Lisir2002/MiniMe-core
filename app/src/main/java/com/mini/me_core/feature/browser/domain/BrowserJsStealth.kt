package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_ANTI_DETECT = """
(function() {
  try {
    Object.defineProperty(Navigator.prototype, 'webdriver', {
      get: function() { return undefined; },
      configurable: true
    });
  } catch(e) {}
  try {
    Object.defineProperty(navigator, 'webdriver', {
      get: function() { return undefined; },
      configurable: true
    });
  } catch(e) {}

  function makeMimeType(type, suffixes, description, enabledPlugin) {
    var mt = Object.create(MimeType.prototype);
    Object.defineProperty(mt, 'type', { get: function() { return type; }, configurable: true });
    Object.defineProperty(mt, 'suffixes', { get: function() { return suffixes; }, configurable: true });
    Object.defineProperty(mt, 'description', { get: function() { return description; }, configurable: true });
    Object.defineProperty(mt, 'enabledPlugin', { get: function() { return enabledPlugin; }, configurable: true });
    return mt;
  }
  function makePlugin(name, filename, description, mimes) {
    var p = Object.create(Plugin.prototype);
    Object.defineProperty(p, 'name', { get: function() { return name; }, configurable: true });
    Object.defineProperty(p, 'filename', { get: function() { return filename; }, configurable: true });
    Object.defineProperty(p, 'description', { get: function() { return description; }, configurable: true });
    Object.defineProperty(p, 'length', { get: function() { return mimes.length; }, configurable: true });
    for (var i = 0; i < mimes.length; i++) {
      (function(idx, m) {
        Object.defineProperty(p, idx, { get: function() { return m; }, configurable: true });
      })(i, mimes[i]);
    }
    return p;
  }
  var pdfMime1 = makeMimeType('application/pdf', 'pdf', 'Portable Document Format', null);
  var pdfMime2 = makeMimeType('application/pdf', 'pdf', 'Portable Document Format', null);
  var naclMime1 = makeMimeType('application/x-nacl', 'nexe', 'Native Client Executable', null);
  var naclMime2 = makeMimeType('application/x-pnacl', 'nexe', 'Portable Native Client Executable', null);
  var pdfPlugin1 = makePlugin('Chrome PDF Plugin', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime1]);
  var pdfPlugin2 = makePlugin('Chrome PDF Viewer', 'mhjfbmdgcfjbbpaeojofohoefgiehjai', '', [pdfMime2]);
  var naclPlugin = makePlugin('Native Client', 'internal-nacl-plugin', '', [naclMime1, naclMime2]);
  pdfMime1 = makeMimeType('application/pdf', 'pdf', 'Portable Document Format', pdfPlugin1);
  pdfMime2 = makeMimeType('application/pdf', 'pdf', 'Portable Document Format', pdfPlugin2);
  naclMime1 = makeMimeType('application/x-nacl', 'nexe', 'Native Client Executable', naclPlugin);
  naclMime2 = makeMimeType('application/x-pnacl', 'nexe', 'Portable Native Client Executable', naclPlugin);
  pdfPlugin1 = makePlugin('Chrome PDF Plugin', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime1]);
  pdfPlugin2 = makePlugin('Chrome PDF Viewer', 'mhjfbmdgcfjbbpaeojofohoefgiehjai', '', [pdfMime2]);
  naclPlugin = makePlugin('Native Client', 'internal-nacl-plugin', '', [naclMime1, naclMime2]);
  var pluginArray = [pdfPlugin1, pdfPlugin2, naclPlugin];
  var mimeArray = [pdfMime1, pdfMime2, naclMime1, naclMime2];
  try {
    Object.defineProperty(navigator, 'plugins', {
      get: function() { return pluginArray; },
      configurable: true
    });
    Object.defineProperty(navigator, 'mimeTypes', {
      get: function() { return mimeArray; },
      configurable: true
    });
  } catch(e) {}

  try {
    Object.defineProperty(navigator, 'languages', {
      get: function() { return ['zh-CN', 'zh', 'en']; },
      configurable: true
    });
  } catch(e) {}

  try {
    var originalQuery = window.navigator.permissions.query;
    window.navigator.permissions.query = function(parameters) {
      if (parameters.name === 'notifications') {
        return Promise.resolve({ state: Notification.permission });
      }
      return originalQuery.call(window.navigator.permissions, parameters);
    };
  } catch(e) {}

  try {
    Object.defineProperty(navigator, 'hardwareConcurrency', {
      get: function() { return 8; },
      configurable: true
    });
  } catch(e) {}

  try {
    Object.defineProperty(navigator, 'deviceMemory', {
      get: function() { return 8; },
      configurable: true
    });
  } catch(e) {}

  window.chrome = window.chrome || {};
  window.chrome.runtime = window.chrome.runtime || {
    OnInstalledReason: { CHROME_UPDATE: 'chrome_update', INSTALL: 'install', SHARED_MODULE_UPDATE: 'shared_module_update', UPDATE: 'update' },
    OnRestartRequiredReason: { APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic' },
    PlatformArch: { ARM: 'arm', ARM64: 'arm64', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' },
    PlatformNaclArch: { ARM: 'arm', MIPS: 'mips', X86_32: 'x86-32', X86_64: 'x86-64' },
    PlatformOs: { ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac', OPENBSD: 'openbsd', WIN: 'win' },
    RequestUpdateCheckStatus: { NO_UPDATE: 'no_update', THROTTLED: 'throttled', UPDATE_AVAILABLE: 'update_available' },
    connect: function() { return { onDisconnect: { addListener: function() {} }, onMessage: { addListener: function() {} }, postMessage: function() {} }; },
    sendMessage: function() {},
    getManifest: function() { return {}; },
    getURL: function(path) { return 'chrome-extension://' + path; },
    id: ''
  };

  try {
    var getParameter = WebGLRenderingContext.prototype.getParameter;
    WebGLRenderingContext.prototype.getParameter = function(parameter) {
      if (parameter === 37445) return 'Intel Inc.';
      if (parameter === 37446) return 'Intel Iris OpenGL Engine';
      return getParameter.apply(this, arguments);
    };
  } catch(e) {}
  try {
    var getParameter2 = WebGL2RenderingContext.prototype.getParameter;
    WebGL2RenderingContext.prototype.getParameter = function(parameter) {
      if (parameter === 37445) return 'Intel Inc.';
      if (parameter === 37446) return 'Intel Iris OpenGL Engine';
      return getParameter2.apply(this, arguments);
    };
  } catch(e) {}
})();
"""


internal const val JS_RENDER_WAIT_CHECK = """
(function() {
  var domStable = (window.__rcb_mut_version || 0);
  var netPending = (window.__rcb_net_pending || 0);
  // CSS 动画检测：检查是否有正在运行的 CSS 动画/过渡
  var cssIdle = true;
  try {
    var animated = document.getAnimations ? document.getAnimations({subtree: true}) : [];
    cssIdle = animated.length === 0;
  } catch(e) { cssIdle = true; }
  return JSON.stringify({
    dom_version: domStable,
    net_pending: netPending,
    css_idle: cssIdle,
    css_animations: (function() { try { return document.getAnimations ? document.getAnimations({subtree: true}).length : 0; } catch(e) { return -1; } })(),
    body_ready: !!(document.body && document.body.innerText.length > 0)
  });
})();
"""


internal const val JS_DETECT_RENDERING_TYPE = """
(function() {
  var type = 'unknown';
  var signals = [];
  // Next.js
  if (window.__NEXT_DATA__) { type = 'nextjs_ssr'; signals.push('__NEXT_DATA__'); }
  // Nuxt
  if (window.__NUXT__) { type = 'nuxt_ssr'; signals.push('__NUXT__'); }
  // Vue SSR
  if (window.__VUE_HYDRATION__ || (document.querySelector('#__nuxt') && document.querySelector('#__nuxt').children.length > 0)) {
    if (type === 'unknown') type = 'vue_ssr';
    signals.push('vue_hydration');
  }
  // React SSR（有 data-reactroot 或 data-react-helmet）
  if (document.querySelector('[data-reactroot]') || document.querySelector('#__next')) {
    if (type === 'unknown') type = 'react_ssr';
    signals.push('react_root');
  }
  // 通用 SSR 信号：body 有大量直接文本子节点
  var directText = 0;
  if (document.body) {
    for (var i = 0; i < document.body.childNodes.length; i++) {
      if (document.body.childNodes[i].nodeType === 3 && document.body.childNodes[i].textContent.trim().length > 10) directText++;
    }
  }
  // CSR 信号：body 基本为空，只有 script 标签
  var bodyChildren = document.body ? document.body.children.length : 0;
  var scriptCount = document.querySelectorAll('script').length;
  if (type === 'unknown') {
    if (bodyChildren <= 2 && scriptCount > 5 && document.body.innerText.trim().length < 50) {
      type = 'csr';
      signals.push('empty_body_many_scripts');
    } else if (directText > 0 || bodyChildren > 3) {
      type = 'ssr';
      signals.push('server_rendered_content');
    }
  }
  return JSON.stringify({ type: type, signals: signals, body_children: bodyChildren, script_count: scriptCount, direct_text_nodes: directText });
})();
"""


internal const val JS_STEALTH_AGGRESSIVE = """
(function() {
  if (window.__rcb_stealth) return;
  window.__rcb_stealth = 'aggressive';
  // 随机种子（每次会话固定，避免同一页面内指纹不一致）
  var seed = Math.floor(Math.random() * 1000000);
  function seededRandom(s) { return function() { s = (s * 9301 + 49297) % 233280; return s / 233280; }; }
  var rand = seededRandom(seed);

  // 1. Canvas 指纹随机化：在 toDataURL/getImageData 中加入微小噪声
  try {
    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function() {
      var ctx = this.getContext('2d');
      if (ctx) {
        try {
          var imgData = ctx.getImageData(0, 0, Math.min(this.width, 100), Math.min(this.height, 100));
          for (var i = 0; i < imgData.data.length; i += 4) {
            imgData.data[i] = Math.max(0, Math.min(255, imgData.data[i] + Math.floor(rand() * 2) - 1));
          }
          ctx.putImageData(imgData, 0, 0);
        } catch(e) {}
      }
      return origToDataURL.apply(this, arguments);
    };
  } catch(e) {}

  // 2. WebGL 指纹随机化：getParameter 返回随机化的 GPU 信息
  try {
    var vendors = ['Intel Inc.', 'ATI Technologies Inc.', 'NVIDIA Corporation', 'Google Inc.'];
    var renderers = ['Intel Iris OpenGL Engine', 'AMD Radeon Pro 5500M OpenGL Engine', 'NVIDIA GeForce GTX 1650 OpenGL Engine', 'ANGLE (Google, Vulkan 1.0.0)'];
    var vIdx = Math.floor(rand() * vendors.length);
    var origGetParam = WebGLRenderingContext.prototype.getParameter;
    WebGLRenderingContext.prototype.getParameter = function(p) {
      if (p === 37445) return vendors[vIdx];
      if (p === 37446) return renderers[vIdx];
      return origGetParam.apply(this, arguments);
    };
    if (window.WebGL2RenderingContext) {
      var origGetParam2 = WebGL2RenderingContext.prototype.getParameter;
      WebGL2RenderingContext.prototype.getParameter = function(p) {
        if (p === 37445) return vendors[vIdx];
        if (p === 37446) return renderers[vIdx];
        return origGetParam2.apply(this, arguments);
      };
    }
  } catch(e) {}

  // 3. AudioContext 指纹随机化：OscillatorNode 频率加微扰
  try {
    var origGetFreq = Object.getOwnPropertyDescriptor(AudioNode.prototype, 'context');
    // AudioBuffer 指纹：getChannelData 加微噪声
    var origGetChannel = AudioBuffer.prototype.getChannelData;
    AudioBuffer.prototype.getChannelData = function(channel) {
      var data = origGetChannel.call(this, channel);
      try {
        for (var i = 0; i < Math.min(data.length, 100); i++) {
          data[i] = data[i] + (rand() - 0.5) * 0.0000001;
        }
      } catch(e) {}
      return data;
    };
  } catch(e) {}

  // 4. 字体列表随机化：navigator.fonts 报告常见字体
  try {
    var fakeFonts = ['Arial', 'Helvetica', 'Times New Roman', 'Courier New', 'Georgia', 'Verdana', 'Monaco', 'Menlo'];
    Object.defineProperty(navigator, 'fonts', {
      get: function() {
        return {
          ready: Promise.resolve(),
          check: function() { return true; },
          values: function() { return fakeFonts.map(function(f) { return new FontFace(f, ''); })[Symbol.iterator](); }
        };
      },
      configurable: true
    });
  } catch(e) {}

  // 5. 时区随机化
  try {
    var timezones = ['Asia/Shanghai', 'America/New_York', 'Europe/London', 'Asia/Tokyo'];
    var tzIdx = Math.floor(rand() * timezones.length);
    DateTimeFormat = Intl.DateTimeFormat;
    Intl.DateTimeFormat = function() {
      var orig = new DateTimeFormat.apply(null, arguments);
      try { orig.resolvedOptions().timeZone = timezones[tzIdx]; } catch(e) {}
      return orig;
    };
  } catch(e) {}

  // 6. 语言随机化
  try {
    var langs = ['zh-CN', 'en-US', 'ja-JP', 'zh-TW'];
    var lIdx = Math.floor(rand() * langs.length);
    Object.defineProperty(navigator, 'language', { get: function() { return langs[lIdx]; }, configurable: true });
    Object.defineProperty(navigator, 'languages', { get: function() { return [langs[lIdx], 'en', 'en-US']; }, configurable: true });
  } catch(e) {}

  // 7. 覆盖 webdriver（基础项）
  try {
    Object.defineProperty(Navigator.prototype, 'webdriver', { get: function() { return undefined; }, configurable: true });
  } catch(e) {}
})();
"""


internal const val JS_STEALTH_HEALTH_CHECK = """
(function() {
  var checks = { canvas_ok: false, webgl_ok: false, body_ok: false, stealth_mode: window.__rcb_stealth || 'none' };
  // Canvas 检测
  try {
    var c = document.createElement('canvas');
    c.width = 100; c.height = 100;
    var ctx = c.getContext('2d');
    ctx.fillStyle = 'red'; ctx.fillRect(0, 0, 50, 50);
    var data = ctx.getImageData(0, 0, 10, 10);
    checks.canvas_ok = data.data.length === 400 && data.data[0] > 200;
  } catch(e) { checks.canvas_ok = false; }
  // WebGL 检测
  try {
    var gl = document.createElement('canvas').getContext('webgl');
    checks.webgl_ok = !!gl;
  } catch(e) { checks.webgl_ok = false; }
  // Body 渲染检测
  checks.body_ok = !!(document.body && document.body.children.length > 0);
  var anomaly = !checks.canvas_ok || !checks.body_ok;
  return JSON.stringify({ anomaly: anomaly, reason: anomaly ? 'Render anomaly detected' : 'Healthy', checks: checks });
})();
"""


internal const val JS_CAPTCHA_DETECT = """
(function() {
  var results = [];
  // reCAPTCHA
  var recaptcha = document.querySelector('.g-recaptcha, iframe[src*="recaptcha"], #recaptcha-widget');
  if (recaptcha) {
    var r = recaptcha.getBoundingClientRect();
    results.push({ type: 'recaptcha', x: r.left, y: r.top, w: r.width, h: r.height, text: (recaptcha.innerText||'').slice(0,100) });
  }
  // hCaptcha
  var hcaptcha = document.querySelector('.h-captcha, iframe[src*="hcaptcha"]');
  if (hcaptcha) {
    var r2 = hcaptcha.getBoundingClientRect();
    results.push({ type: 'hcaptcha', x: r2.left, y: r2.top, w: r2.width, h: r2.height, text: (hcaptcha.innerText||'').slice(0,100) });
  }
  // GeeTest 极验
  var geetest = document.querySelector('.geetest_holder, .geetest_radar, [class*="geetest"]');
  if (geetest) {
    var r3 = geetest.getBoundingClientRect();
    results.push({ type: 'geetest', x: r3.left, y: r3.top, w: r3.width, h: r3.height, text: (geetest.innerText||'').slice(0,100) });
  }
  // Cloudflare Turnstile
  var turnstile = document.querySelector('.cf-turnstile, iframe[src*="turnstile"], [class*="cloudflare"]');
  if (turnstile) {
    var r4 = turnstile.getBoundingClientRect();
    results.push({ type: 'cloudflare_turnstile', x: r4.left, y: r4.top, w: r4.width, h: r4.height, text: (turnstile.innerText||'').slice(0,100) });
  }
  // 滑块通用（slider / slide-to-verify）
  var slider = document.querySelector('.slider, .slide-verify, [class*="slider"], [class*="nc_"], [id*="slide"]');
  if (slider) {
    var r5 = slider.getBoundingClientRect();
    if (r5.width > 10 && r5.height > 10) {
      results.push({ type: 'slider', x: r5.left, y: r5.top, w: r5.width, h: r5.height, text: (slider.innerText||'').slice(0,100) });
    }
  }
  // 点选验证码（文字/图片点选）
  var clickVerify = document.querySelector('[class*="click-verify"], [class*="icon-click"], [class*="word-verify"]');
  if (clickVerify) {
    var r6 = clickVerify.getBoundingClientRect();
    results.push({ type: 'click_select', x: r6.left, y: r6.top, w: r6.width, h: r6.height, text: (clickVerify.innerText||'').slice(0,100) });
  }
  return JSON.stringify({ detected: results.length > 0, types: results.map(function(r){return r.type;}), elements: results });
})();
"""


internal const val JS_PERMISSION_AUDIT = """
(function() {
  var results = [];
  // 通知权限
  try {
    if (window.Notification) {
      results.push({ name: 'notifications', state: Notification.permission });
    }
  } catch(e) {}
  // Permissions API
  try {
    if (navigator.permissions) {
      ['geolocation', 'microphone', 'camera', 'clipboard-read', 'clipboard-write'].forEach(function(p) {
        navigator.permissions.query({ name: p }).then(function(status) {
          results.push({ name: p, state: status.state });
        }).catch(function(){});
      });
    }
  } catch(e) {}
  // 延迟返回（Permissions API 是异步的）
  return new Promise(function(resolve) {
    setTimeout(function() {
      resolve(JSON.stringify({ permissions: results, origin: location.origin }));
    }, 500);
  });
})();
"""


internal const val JS_DETECT_FRAMEWORK = """
(function() {
  var result = { framework: 'unknown', version: '', signals: [], mount_point: '' };
  // Next.js
  if (window.__NEXT_DATA__) {
    result.framework = 'nextjs';
    result.version = window.__NEXT_DATA__.version || '';
    result.signals.push('__NEXT_DATA__');
    result.mount_point = '#__next';
    return JSON.stringify(result);
  }
  // Nuxt
  if (window.__NUXT__) {
    result.framework = 'nuxt';
    result.signals.push('__NUXT__');
    result.mount_point = '#__nuxt';
    return JSON.stringify(result);
  }
  // Vue 3
  if (window.__VUE__ || document.querySelector('#__vue_app__') || (window.Vue && Vue.createApp)) {
    result.framework = 'vue3';
    result.version = (window.Vue && Vue.version) || '3.x';
    result.signals.push('__VUE__');
    result.mount_point = '#app';
    return JSON.stringify(result);
  }
  // Vue 2
  if (window.Vue && !Vue.createApp) {
    result.framework = 'vue2';
    result.version = Vue.version || '2.x';
    result.signals.push('window.Vue');
    return JSON.stringify(result);
  }
  // React
  var reactRoot = document.querySelector('#__next, #root, [data-reactroot]');
  if (reactRoot) {
    var fiberKey = Object.keys(reactRoot).find(function(k) { return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'); });
    if (fiberKey) {
      result.framework = 'react';
      result.signals.push(fiberKey);
      result.mount_point = reactRoot.id || reactRoot.tagName;
      // 尝试获取版本
      var fiber = reactRoot[fiberKey];
      var current = fiber;
      while (current) {
        if (current.type && current.type.render && current.type.render.name) break;
        if (current.child) current = current.child; else break;
      }
      return JSON.stringify(result);
    }
  }
  // Angular
  if (window.ng || document.querySelector('[ng-version]')) {
    result.framework = 'angular';
    var ngVer = document.querySelector('[ng-version]');
    result.version = ngVer ? ngVer.getAttribute('ng-version') : '';
    result.signals.push('ng-version');
    return JSON.stringify(result);
  }
  // Svelte
  if (document.querySelector('[class*="svelte-"]') || window.__svelte) {
    result.framework = 'svelte';
    result.signals.push('svelte-classes');
    return JSON.stringify(result);
  }
  // Remix
  if (window.__remixContext || document.querySelector('#__remix')) {
    result.framework = 'remix';
    result.signals.push('__remixContext');
    return JSON.stringify(result);
  }
  return JSON.stringify(result);
})();
"""


internal const val JS_DETECT_VIRTUAL_LIST = """
(function() {
  var result = { virtual: false, signals: [], scroll_height: 0, dom_items: 0, estimated_total: 0 };
  var containers = document.querySelectorAll('[class*="virtual"], [class*="viewport"], [class*="ReactVirtualized"], [class*="react-window"], [class*="vue-virtual"]');
  var container = containers[0];
  if (!container) {
    // 信号检测：找一个可滚动的长列表容器
    var allDivs = document.querySelectorAll('div');
    for (var i = 0; i < allDivs.length; i++) {
      var d = allDivs[i];
      if (d.scrollHeight > d.clientHeight * 3 && d.children.length > 5 && d.children.length < 50) {
        container = d;
        result.signals.push('height_mismatch');
        break;
      }
    }
  } else {
    result.signals.push('library_class');
  }
  if (!container) return JSON.stringify(result);
  result.scroll_height = container.scrollHeight;
  result.dom_items = container.children.length;
  // 估算总行数：通过行高
  if (container.children.length > 0) {
    var firstChild = container.children[0];
    var rowHeight = firstChild.getBoundingClientRect().height || 50;
    result.estimated_total = Math.round(container.scrollHeight / rowHeight);
    if (result.estimated_total > container.children.length * 3) {
      result.signals.push('row_height_consistent');
      result.virtual = true;
    }
  }
  // 检测常见虚拟列表库
  if (document.querySelector('.ReactVirtualized__List')) result.signals.push('react-virtualized');
  if (document.querySelector('.ReactWindow__VariableSizeList, [class*="react-window"]')) result.signals.push('react-window');
  if (document.querySelector('[class*="vue-virtual-scroller"]')) result.signals.push('vue-virtual-scroller');
  return JSON.stringify(result);
})();
"""


internal const val JS_SPA_NAVIGATE = """
(function() {
  var targetUrl = arguments[0];
  var result = { ok: false, method: '', url: targetUrl };

  // 尝试解析为路径（去掉 origin）
  var path = targetUrl;
  try {
    var u = new URL(targetUrl, location.origin);
    path = u.pathname + u.search + u.hash;
  } catch(e) {}

  // 1. React Router v6（通过 navigate 函数）
  try {
    // 检查是否有全局 navigate（部分应用暴露）
    if (window.navigate && typeof window.navigate === 'function') {
      window.navigate(path);
      result.ok = true; result.method = 'react-router-v6-navigate';
      return JSON.stringify(result);
    }
  } catch(e) {}

  // 2. Vue Router
  try {
    var appEl = document.querySelector('#__vue_app__') || document.querySelector('#app');
    if (appEl && appEl.__vue_app__) {
      var router = appEl.__vue_app__.config.globalProperties.${'$'}router;
      if (router && router.push) {
        router.push(path);
        result.ok = true; result.method = 'vue-router';
        return JSON.stringify(result);
      }
    }
  } catch(e) {}

  // 3. 通用降级：history.pushState + popstate 事件
  try {
    history.pushState(null, '', path);
    window.dispatchEvent(new PopStateEvent('popstate'));
    result.ok = true; result.method = 'generic-pushstate';
    return JSON.stringify(result);
  } catch(e) {
    result.method = 'failed: ' + e.message;
  }
  return JSON.stringify(result);
})();
"""


