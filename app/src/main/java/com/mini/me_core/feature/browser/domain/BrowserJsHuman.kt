package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_APPEND_CHAR = """
(function() {
  var id = arguments[0], ch = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center', behavior:'smooth'});
  el.focus();
  var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  setter.call(el, (el.value || '') + ch);
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_FIRE_INPUT = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.dispatchEvent(new Event('input', {bubbles:true}));
  el.dispatchEvent(new Event('change', {bubbles:true}));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_HUMAN_TYPE_CHAR = """
(function() {
  var id = arguments[0], ch = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  setter.call(el, (el.value || '') + ch);
  return JSON.stringify({ok:true, value: el.value});
})();
"""


internal const val JS_HUMAN_BACKSPACE = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  var v = el.value || '';
  setter.call(el, v.slice(0, -1));
  el.dispatchEvent(new Event('input', {bubbles:true}));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_HUMAN_FIRE_INPUT = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.dispatchEvent(new Event('input', {bubbles:true}));
  el.dispatchEvent(new Event('change', {bubbles:true}));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_SAFE_CLICK_CHECK = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var rect = el.getBoundingClientRect();
  var cs = window.getComputedStyle(el);
  var result = {
    ok: true,
    visible: true,
    inViewport: false,
    clickable: true,
    overlapped: false,
    rect: { x: rect.left, y: rect.top, w: rect.width, h: rect.height }
  };
  if (rect.width < 1 || rect.height < 1) { result.ok = false; result.reason = 'ZERO_SIZE'; result.visible = false; return JSON.stringify(result); }
  if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) {
    result.ok = false; result.reason = 'HIDDEN'; result.visible = false; return JSON.stringify(result);
  }
  var vh = window.innerHeight || document.documentElement.clientHeight;
  var vw = window.innerWidth || document.documentElement.clientWidth;
  result.inViewport = rect.top >= 0 && rect.left >= 0 && rect.bottom <= vh && rect.right <= vw;
  if (el.disabled || el.getAttribute('aria-disabled') === 'true') {
    result.ok = false; result.reason = 'DISABLED'; result.clickable = false; return JSON.stringify(result);
  }
  // 遮挡检查
  var cx = rect.left + rect.width / 2, cy = rect.top + rect.height / 2;
  var top;
  try { top = document.elementFromPoint(cx, cy); } catch(e) {}
  if (top && top !== el && !el.contains(top)) {
    result.overlapped = true;
    result.overlapTarget = top.tagName.toLowerCase() + (top.id ? '#' + top.id : '');
  }
  return JSON.stringify(result);
})();
"""


internal const val JS_SAFE_CLICK_VERIFY = """
(function() {
  var before = arguments[0];
  var now = {
    textLen: document.body ? document.body.innerText.length : 0,
    elCount: document.querySelectorAll('*').length,
    url: location.href,
    hash: location.hash
  };
  var changed = now.textLen !== before.textLen || now.elCount !== before.elCount || now.url !== before.url;
  return JSON.stringify({ changed: changed, before: before, after: now });
})();
"""


internal const val JS_CAPTURE_BEFORE_CLICK = """
(function() {
  return JSON.stringify({
    textLen: document.body ? document.body.innerText.length : 0,
    elCount: document.querySelectorAll('*').length,
    url: location.href,
    hash: location.hash
  });
})();
"""


