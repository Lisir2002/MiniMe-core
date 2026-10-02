package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_STRUCTURED_EXTRACT = """
(function(){
  var result = { page_type: 'unknown', data: {} };
  var article = document.querySelector('article');
  var ogType = (document.querySelector('meta[property="og:type"]') || {}).content;
  if (article || ogType === 'article' || /blog|article|post/.test(location.href)) {
    result.page_type = 'article';
    result.data = {
      title: (document.querySelector('h1') || {}).textContent || document.title,
      author: (document.querySelector('meta[name="author"]') || {}).content || '',
      date: (document.querySelector('meta[property="article:published_time"]') || {}).content || (document.querySelector('time') || {}).datetime || '',
      content: ((article || document.querySelector('main') || document.body) || {}).innerText ? (article || document.querySelector('main') || document.body).innerText.slice(0, 8000) : '',
      images: Array.prototype.slice.call(document.querySelectorAll('article img, main img')).slice(0,10).map(function(img){return img.src;})
    };
  }
  else if (/product|item|goods|sku/.test(location.href) || document.querySelector('[itemprop="price"]')) {
    result.page_type = 'product';
    result.data = {
      title: (document.querySelector('h1') || {}).textContent || document.title,
      price: (document.querySelector('[itemprop="price"]') || {}).content || (document.querySelector('.price') || {}).textContent || '',
      description: ((document.querySelector('[itemprop="description"]') || {}).content || (document.querySelector('.description') || {}).textContent || '').slice(0,2000),
      images: Array.prototype.slice.call(document.querySelectorAll('img')).slice(0,10).map(function(img){return img.src;}).filter(function(src){return src;})
    };
  }
  else if (/search|query|s=|q=/.test(location.href) || document.querySelector('.search-results, #search-results, .results')) {
    result.page_type = 'search_results';
    var links = Array.prototype.slice.call(document.querySelectorAll('a')).filter(function(a){return a.href && a.textContent.trim().length > 5;}).slice(0,20);
    result.data = { results: links.map(function(a){ return { title: a.textContent.trim().slice(0,200), url: a.href }; }) };
  }
  else if (/profile|user|account|member/.test(location.pathname)) {
    result.page_type = 'profile';
    result.data = {
      username: (document.querySelector('h1') || {}).textContent || document.title,
      bio: ((document.querySelector('.bio, .description, [class*="bio"]') || {}).textContent || '').slice(0,1000),
      links: Array.prototype.slice.call(document.querySelectorAll('a')).slice(0,10).map(function(a){ return {text: a.textContent.trim().slice(0,100), url: a.href}; }).filter(function(l){return l.text;})
    };
  }
  return JSON.stringify(result);
})()
"""


internal const val JS_DEOBFUSCATE_TEXT = """
(function() {
  var rawText = arguments[0];
  if (!rawText) rawText = document.body ? document.body.innerText : '';
  var issues = [];

  // 1. 零宽字符检测与清理
  var zeroWidthCount = (rawText.match(/[\\u200B-\\u200D\\uFEFF\\u2060]/g) || []).length;
  if (zeroWidthCount > 0) {
    issues.push('zero_width_chars:' + zeroWidthCount);
    rawText = rawText.replace(/[\\u200B-\\u200D\\uFEFF\\u2060]/g, '');
  }

  // 2. CSS 混淆检测：检查是否有大量 display:none / visibility:hidden 的文本节点
  try {
    var hiddenCount = 0;
    var allEls = document.querySelectorAll('*');
    for (var i = 0; i < allEls.length && hiddenCount < 50; i++) {
      var cs = window.getComputedStyle(allEls[i]);
      if ((cs.display === 'none' || cs.visibility === 'hidden') && allEls[i].textContent.trim().length > 0) {
        hiddenCount++;
      }
    }
    if (hiddenCount > 5) issues.push('css_obfuscation:hidden_elements:' + hiddenCount);
  } catch(e) {}

  // 3. 字体反爬识别：检测是否使用了自定义字体（@font-face）且影响了正文显示
  try {
    var fontFaces = document.styleSheets;
    var customFontCount = 0;
    for (var j = 0; j < fontFaces.length; j++) {
      try {
        var rules = fontFaces[j].cssRules;
        for (var k = 0; k < rules.length; k++) {
          if (rules[k].type === 5) customFontCount++; // CSSFontFaceRule
        }
      } catch(e) {}
    }
    if (customFontCount > 3) issues.push('font_obfuscation:custom_fonts:' + customFontCount);
  } catch(e) {}

  // 4. 重复字符压缩
  var beforeLen = rawText.length;
  rawText = rawText.replace(/\\n{3,}/g, '\\n\\n').trim();
  var compressed = rawText.length < beforeLen;

  return JSON.stringify({
    clean_text: rawText.slice(0, 20000),
    issues: issues,
    original_length: beforeLen,
    cleaned_length: rawText.length,
    had_obfuscation: issues.length > 0
  });
})();
"""


internal const val JS_EXTRACT_CLEAN_TEXT = """
(function() {
  // 收集所有可见文本节点
  var temp = document.createElement('div');
  function cloneVisible(root) {
    var clone = root.cloneNode(true);
    // 移除隐藏元素
    var toRemove = [];
    clone.querySelectorAll('*').forEach(function(el) {
      var cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') {
        toRemove.push(el);
      }
    });
    toRemove.forEach(function(el) { if (el.parentNode) el.parentNode.removeChild(el); });
    return clone;
  }
  try {
    var bodyClone = cloneVisible(document.body);
    var text = bodyClone.innerText || bodyClone.textContent || '';
    // 清理零宽字符
    text = text.replace(/[\\u200B-\\u200D\\uFEFF\\u2060]/g, '');
    text = text.replace(/\\n{3,}/g, '\\n\\n').trim();
    return JSON.stringify({ ok: true, text: text.slice(0, 20000), length: text.length });
  } catch(e) {
    return JSON.stringify({ ok: false, reason: e.message });
  }
})();
"""


internal const val JS_PAGINATE_EXTRACT = """
(function() {
  var maxPages = Math.min(parseInt(arguments[0]) || 5, 10);
  var extractSel = arguments[1] || null;
  var results = [];
  var issues = [];

  function extractCurrent() {
    var container = extractSel ? document.querySelector(extractSel) : document;
    if (!container) return [];
    // 提取容器内的卡片/列表项
    var items = container.querySelectorAll('article, .card, .item, li.search-result, .result-item, tr');
    if (items.length === 0) {
      // 退化为提取整个容器的文本
      return [{ text: (container.innerText || '').slice(0, 2000) }];
    }
    return Array.from(items).slice(0, 50).map(function(item) {
      return {
        text: (item.innerText || '').slice(0, 1000),
        html: item.innerHTML.slice(0, 3000),
        links: Array.from(item.querySelectorAll('a')).map(function(a) { return { text: (a.innerText || '').trim().slice(0,100), href: a.href }; }).filter(function(l) { return l.text; }).slice(0, 5)
      };
    });
  }

  function findNextPage() {
    // 优先 a[rel=next]
    var next = document.querySelector('a[rel="next"]');
    if (next) return { type: 'link', el: next };
    // 常见分页选择器
    var selectors = ['.pagination a.next', '.pager .next a', '.next-page a', 'a.next', 'a[aria-label*="Next" i]', 'button[aria-label*="Next" i]'];
    for (var i = 0; i < selectors.length; i++) {
      var el = document.querySelector(selectors[i]);
      if (el && el.offsetParent !== null) return { type: 'click', el: el };
    }
    return null;
  }

  // 第 1 页
  results.push({ page: 1, items: extractCurrent(), url: location.href });
  // 翻页
  for (var p = 2; p <= maxPages; p++) {
    var next = findNextPage();
    if (!next) { issues.push('no_next_page_at_page_' + p); break; }
    try {
      if (next.type === 'link') {
        // 直接导航
        results.push({ page: p, items: extractCurrent(), url: location.href, note: 'navigation required' });
        issues.push('link_next_detected_page_' + p + ': manual navigation required');
        break; // 链接型下一页需要重新加载页面，无法在单页内完成
      } else {
        // 点击翻页
        next.el.click();
        // 等待内容加载
        var waitStart = Date.now();
        var oldCount = results[results.length - 1].items.length;
        while (Date.now() - waitStart < 3000) {
          var newItems = extractCurrent();
          if (newItems.length !== oldCount || document.readyState === 'complete') break;
        }
        results.push({ page: p, items: extractCurrent(), url: location.href });
      }
    } catch(e) {
      issues.push('page_' + p + '_error: ' + e.message);
      break;
    }
  }

  return JSON.stringify({ ok: true, total_pages: results.length, results: results, issues: issues });
})();
"""


internal const val JS_INFINITE_SCROLL_EXTRACT = """
(function() {
  var maxScrolls = Math.min(parseInt(arguments[0]) || 15, 30);
  var extractSel = arguments[1] || null;
  var issues = [];
  var collectedItems = [];
  var seenTexts = new Set();

  function extractItems() {
    var container = extractSel ? document.querySelector(extractSel) : document;
    if (!container) return [];
    var items = container.querySelectorAll('article, .card, .item, .post, .comment, .feed-item, li');
    if (items.length === 0) {
      // 退化为提取 body
      return [{ text: (container.innerText || '').slice(0, 500), _fallback: true }];
    }
    return Array.from(items).map(function(item) {
      var t = (item.innerText || '').slice(0, 500);
      return { text: t, top: item.getBoundingClientRect().top + window.scrollY };
    }).filter(function(it) { return it.text.trim().length > 0; });
  }

  function scrollDown() {
    var h = document.body.scrollHeight;
    window.scrollTo(0, h);
    return h;
  }

  var lastHeight = 0;
  var stableCount = 0;
  for (var s = 0; s < maxScrolls; s++) {
    var items = extractItems();
    // 去重合并
    for (var i = 0; i < items.length; i++) {
      var key = items[i].text.slice(0, 100);
      if (!seenTexts.has(key)) {
        seenTexts.add(key);
        collectedItems.push(items[i]);
      }
    }
    var newHeight = scrollDown();
    // 等待加载
    var waitStart = Date.now();
    while (Date.now() - waitStart < 1500) {
      if (document.body.scrollHeight > newHeight) break;
    }
    if (document.body.scrollHeight === lastHeight) {
      stableCount++;
      if (stableCount >= 2) { issues.push('height_stable_at_scroll_' + s); break; }
    } else {
      stableCount = 0;
    }
    lastHeight = document.body.scrollHeight;
  }
  // 最终提取
  var finalItems = extractItems();
  for (var j = 0; j < finalItems.length; j++) {
    var key2 = finalItems[j].text.slice(0, 100);
    if (!seenTexts.has(key2)) {
      seenTexts.add(key2);
      collectedItems.push(finalItems[j]);
    }
  }

  return JSON.stringify({
    ok: true,
    total_items: collectedItems.length,
    items: collectedItems.slice(0, 200),
    scrolls_performed: s,
    page_height: document.body.scrollHeight,
    issues: issues
  });
})();
"""


internal const val JS_EXTRACT_SSR_DATA = """
(function() {
  var MAX_SIZE = 1024 * 1024; // 1MB
  var sources = [];
  if (window.__NEXT_DATA__) sources.push({ name: '__NEXT_DATA__', data: window.__NEXT_DATA__ });
  if (window.__NUXT__) sources.push({ name: '__NUXT__', data: window.__NUXT__ });
  if (window.__INITIAL_STATE__) sources.push({ name: '__INITIAL_STATE__', data: window.__INITIAL_STATE__ });
  if (window.__APOLLO_STATE__) sources.push({ name: '__APOLLO_STATE__', data: window.__APOLLO_STATE__ });
  if (window.__PRELOADED_STATE__) sources.push({ name: '__PRELOADED_STATE__', data: window.__PRELOADED_STATE__ });
  // 从 script tag 提取（Next.js 通常内联在 script#__NEXT_DATA__）
  if (sources.length === 0) {
    var scripts = document.querySelectorAll('script[type="application/json"]');
    for (var i = 0; i < scripts.length; i++) {
      var id = scripts[i].id || '';
      if (id.indexOf('__NEXT_DATA__') >= 0 || id.indexOf('__NUXT__') >= 0 || id.indexOf('state') >= 0) {
        try { sources.push({ name: id, data: JSON.parse(scripts[i].textContent) }); } catch(e) {}
      }
    }
  }
  var results = [];
  for (var j = 0; j < sources.length; j++) {
    var s = sources[j];
    var str = JSON.stringify(s.data);
    results.push({
      name: s.name,
      size: str.length,
      truncated: str.length > MAX_SIZE,
      data: str.length > MAX_SIZE ? str.slice(0, MAX_SIZE) : str
    });
  }
  return JSON.stringify({ sources: results, count: results.length });
})();
"""


internal const val JS_EXTRACT_FRAMEWORK_STATE = """
(function() {
  var result = { type: 'unknown', supported: false, data: null, note: '' };

  // ── React Fiber 树遍历 ──
  var rootEl = document.querySelector('#__next, #root, [data-reactroot]');
  if (rootEl) {
    var fiberKey = Object.keys(rootEl).find(function(k) { return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'); });
    if (fiberKey) {
      result.type = 'react';
      result.supported = true;
      try {
        var fiber = rootEl[fiberKey];
        // 向上找到根 fiber，然后向下遍历收集组件 state
        var components = [];
        var queue = [fiber];
        var visited = 0;
        while (queue.length > 0 && visited < 50) {
          var f = queue.shift();
          visited++;
          if (!f) continue;
          // 函数组件 hooks
          if (f.memoizedState && typeof f.memoizedState === 'object') {
            var stateStr = '';
            try { stateStr = JSON.stringify(f.memoizedState, function(key, val) {
              if (typeof val === 'function') return '[fn]';
              if (val && val.${'$'}${'$'}typeof) return '[symbol]';
              return val;
            }).slice(0, 500); } catch(e) {}
            if (stateStr && stateStr.length > 5 && stateStr !== '{}') {
              var compName = (f.type && (f.type.name || f.type.displayName)) || (f.elementType && (f.elementType.name || f.elementType.displayName)) || 'Anonymous';
              components.push({ name: compName, state: stateStr.slice(0, 300) });
            }
          }
          // 类组件 state
          if (f.stateNode && f.stateNode.state && f.stateNode.constructor) {
            try {
              var clsState = JSON.stringify(f.stateNode.state, function(key, val) {
                if (typeof val === 'function') return '[fn]';
                return val;
              }).slice(0, 300);
              components.push({ name: f.stateNode.constructor.name, state: clsState });
            } catch(e) {}
          }
          if (f.child) queue.push(f.child);
          if (f.sibling) queue.push(f.sibling);
        }
        result.data = { components: components.slice(0, 20), total_visited: visited };
        result.note = 'React Fiber 遍历完成，提取了 ' + components.length + ' 个组件状态';
      } catch(e) {
        result.note = 'React Fiber 遍历失败: ' + e.message;
      }
      // Redux store 检测
      try {
        if (window.__REDUX_DEVTOOLS_EXTENSION__ || (rootEl._store && rootEl._store.getState)) {
          result.redux = true;
        }
      } catch(e) {}
      return JSON.stringify(result);
    }
  }

  // ── Vue 3 实例 ──
  if (window.__VUE__ || document.querySelector('#__vue_app__')) {
    result.type = 'vue3';
    result.supported = true;
    try {
      var appEl = document.querySelector('#__vue_app__') || document.querySelector('#app');
      var vueApp = appEl && (appEl.__vue_app__ || (appEl.__vueParentComponent));
      if (vueApp) {
        // Pinia store 检测
        var stores = [];
        try {
          if (vueApp.config && vueApp.config.globalProperties && vueApp.config.globalProperties.${'$'}pinia) {
            var pinia = vueApp.config.globalProperties.${'$'}pinia;
            if (pinia._s) {
              pinia._s.forEach(function(store, id) {
                var stateStr = '';
                try { stateStr = JSON.stringify(store.${'$'}state).slice(0, 500); } catch(e) {}
                stores.push({ id: id, state: stateStr.slice(0, 300) });
              });
            }
          }
        } catch(e) {}
        result.data = { pinia_stores: stores, note: stores.length > 0 ? '提取了 ' + stores.length + ' 个 Pinia store' : '未检测到 Pinia store' };
      }
    } catch(e) {
      result.note = 'Vue 实例访问失败: ' + e.message;
    }
    return JSON.stringify(result);
  }

  // ── Zustand 检测 ──
  if (window.__ZUSTAND__) {
    result.type = 'zustand';
    result.supported = true;
    try {
      result.data = { store: JSON.stringify(window.__ZUSTAND__).slice(0, 500) };
    } catch(e) {}
    return JSON.stringify(result);
  }

  result.note = '未识别到可访问的框架内部状态（支持 React/Vue3/Pinia/Zustand）';
  return JSON.stringify(result);
})();
"""


