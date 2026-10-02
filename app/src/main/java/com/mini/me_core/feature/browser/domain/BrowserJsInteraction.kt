package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_SCROLL = """
(function() {
  var d = arguments[0];
  var h = window.innerHeight || 600;
  var startY = window.scrollY;
  var endY = startY, duration = 400;
  if (d === 'top') { endY = 0; duration = 500; }
  else if (d === 'bottom') { endY = document.body.scrollHeight; duration = 600; }
  else if (d === 'up') { endY = startY - Math.floor(h * 0.8); duration = 400; }
  else { endY = startY + Math.floor(h * 0.8); duration = 400; }
  endY = Math.max(0, Math.min(endY, document.body.scrollHeight - h));
  function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }
  var startTime = null;
  function step(ts) {
    if (!startTime) startTime = ts;
    var elapsed = ts - startTime;
    var t = Math.min(elapsed / duration, 1);
    window.scrollTo(0, startY + (endY - startY) * easeOutCubic(t));
    if (t < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
  return window.scrollY;
})();
"""


internal const val JS_GET_ELEMENT_CENTER = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center', behavior:'instant'});
  var r = el.getBoundingClientRect();
  return JSON.stringify({ok:true, x: r.left + r.width / 2, y: r.top + r.height / 2});
})();
"""


internal const val JS_MOUSE_MOVE = """
(function() {
  var x = arguments[0], y = arguments[1];
  if (!window.__rcb_mouse_pos) window.__rcb_mouse_pos = {x: window.innerWidth / 2, y: window.innerHeight / 2};
  var from = window.__rcb_mouse_pos;
  var over = document.elementFromPoint(x, y);
  var target = over || document.body;
  target.dispatchEvent(new MouseEvent('mouseover', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  target.dispatchEvent(new MouseEvent('mouseenter', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  document.dispatchEvent(new MouseEvent('mousemove', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  window.__rcb_mouse_pos = {x: x, y: y};
  return 'ok';
})();
"""


internal const val JS_CLICK_AT = """
(function() {
  var id = arguments[0], x = arguments[1], y = arguments[2];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y, pointerType:'mouse'}));
  el.dispatchEvent(new MouseEvent('mousedown', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  el.dispatchEvent(new PointerEvent('pointerup', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y, pointerType:'mouse'}));
  el.dispatchEvent(new MouseEvent('mouseup', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  window.__rcb_mouse_pos = {x: x, y: y};
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_TYPE = """
(function() {
  var id = arguments[0], text = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center', behavior:'smooth'});
  el.focus();
  var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  setter.call(el, text);
  el.dispatchEvent(new Event('input', {bubbles:true}));
  el.dispatchEvent(new Event('change', {bubbles:true}));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_SELECT = """
(function() {
  var id = arguments[0], value = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center', behavior:'smooth'});
  el.value = value;
  el.dispatchEvent(new Event('change', {bubbles:true}));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_LOCATE = """
(function() {
  var loc = arguments[0];
  if (!loc) return JSON.stringify({ok:false, reason:'EMPTY'});
  function nextId() { if (!window.__rcb_seq) window.__rcb_seq = 0; return String(++window.__rcb_seq); }
  function markId(el) {
    var sid = el.getAttribute('data-rcb-id');
    if (!sid) { sid = nextId(); el.setAttribute('data-rcb-id', sid); }
    return sid;
  }
  function tagToRole(tag, type) {
    if (tag === 'a') return 'link';
    if (tag === 'button') return 'button';
    if (tag === 'input') {
      if (type === 'checkbox') return 'checkbox';
      if (type === 'radio') return 'radio';
      if (type === 'submit' || type === 'button') return 'button';
      if (type === 'password' || type === 'email' || type === 'tel' || type === 'url' || type === 'search' || type === 'number') return 'textbox';
      return 'input';
    }
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'img') return 'img';
    return tag;
  }
  function elRole(el) {
    var tag = el.tagName.toLowerCase();
    var type = (el.getAttribute('type') || '').toLowerCase();
    return (el.getAttribute('role') || tagToRole(tag, type)).toLowerCase();
  }
  function elName(el) {
    var al = (el.getAttribute('aria-label') || '').trim();
    if (al) return al;
    var lb = el.getAttribute('aria-labelledby');
    if (lb) {
      var parts = [], ns = lb.split(/\s+/);
      for (var i = 0; i < ns.length; i++) {
        var ref = document.getElementById(ns[i]);
        if (ref) { var t = (ref.innerText || ref.textContent || '').replace(/\\s+/g, ' ').trim(); if (t) parts.push(t); }
      }
      if (parts.length) return parts.join(' ');
    }
    var fid = el.id || el.name;
    if (fid) {
      try {
        var lab = document.querySelector('label[for="' + fid + '"]');
        if (lab) { var t = (lab.innerText || lab.textContent || '').replace(/\\s+/g, ' ').trim(); if (t) return t; }
      } catch (e) {}
    }
    if (el.closest) {
      var parent = el.closest('label');
      if (parent) { var t2 = (parent.innerText || parent.textContent || '').replace(/\\s+/g, ' ').trim(); if (t2) return t2; }
    }
    var tag = el.tagName.toLowerCase();
    if (tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'li') {
      var t3 = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim();
      if (t3) return t3;
    }
    if (tag === 'input' || tag === 'textarea') {
      var ph = (el.getAttribute('placeholder') || '').trim();
      if (ph) return ph;
    }
    var nm = (el.getAttribute('name') || '').trim();
    if (nm) return nm;
    return '';
  }

  // Parse :nth(N) suffix
  var nthMatch = loc.match(/^(.*?):nth\\((\\d+)\\)$/);
  var baseLoc = loc, explicitIndex = -1;
  if (nthMatch) { baseLoc = nthMatch[1]; explicitIndex = parseInt(nthMatch[2], 10); }

  // 1) role+name semantic locator (highest priority)
  var rnMatch = baseLoc.match(/^role=([\\w-]+)\\s*,\\s*name=([^,]+?)(?:\\s*,\\s*index=(\\d+))?$/i);
  if (rnMatch) {
    var role = rnMatch[1].toLowerCase();
    var name = rnMatch[2].trim();
    if (explicitIndex < 0 && rnMatch[3]) explicitIndex = parseInt(rnMatch[3], 10);
    var nodes = document.querySelectorAll('a,button,input,select,textarea,[role],[contenteditable="true"],summary,li,h1,h2,h3,h4,h5,h6,img');
    var exactMatches = [], containsMatches = [];
    for (var i = 0; i < nodes.length; i++) {
      var e = nodes[i];
      if (e.closest && e.closest('[data-rcb-skip]')) continue;
      var tag = e.tagName.toLowerCase();
      if (tag === 'input' && e.type === 'hidden') continue;
      var r = elRole(e);
      if (r !== role) continue;
      var en = elName(e);
      if (en === name) exactMatches.push(e);
      else if (en.toLowerCase().indexOf(name.toLowerCase()) >= 0) containsMatches.push(e);
    }
    var matches = exactMatches.length > 0 ? exactMatches : containsMatches;
    var method = exactMatches.length > 0 ? 'role_exact' : 'role_contains';
    if (matches.length === 0) {
      return JSON.stringify({ok:false, reason:'NOT_FOUND', message:'No element with role=' + role + ' name=' + name});
    }
    var target;
    if (explicitIndex >= 0) {
      if (explicitIndex >= matches.length) {
        return JSON.stringify({ok:false, reason:'INDEX_OUT_OF_RANGE', matchCount:matches.length, message:'Index ' + explicitIndex + ' out of range, total ' + matches.length});
      }
      target = matches[explicitIndex];
    } else if (matches.length === 1) {
      target = matches[0];
    } else {
      return JSON.stringify({ok:false, reason:'STRICT_MODE', matchCount:matches.length, message:'Locator not unique, matched ' + matches.length + ' elements, use :nth(N) or index=N'});
    }
    var sid = markId(target);
    return JSON.stringify({ok:true, id:sid, method:method, matchCount:matches.length});
  }

  // 2) data-rcb-id direct lookup
  try {
    var byId = document.querySelector('[data-rcb-id="' + loc + '"]');
    if (byId) return JSON.stringify({ok:true, id: byId.getAttribute('data-rcb-id'), method:'id', matchCount:1});
  } catch (e) {}

  // 3) CSS absolute path / selector
  try {
    var cssResults = document.querySelectorAll(loc);
    if (cssResults.length > 0) {
      var cssTarget;
      if (cssResults.length === 1) {
        cssTarget = cssResults[0];
      } else if (explicitIndex >= 0 && explicitIndex < cssResults.length) {
        cssTarget = cssResults[explicitIndex];
      } else {
        return JSON.stringify({ok:false, reason:'STRICT_MODE', matchCount:cssResults.length, message:'CSS matched ' + cssResults.length + ' elements, use :nth(N)'});
      }
      if (cssTarget.getAttribute('data-rcb-skip') === null) {
        var cid = markId(cssTarget);
        return JSON.stringify({ok:true, id:cid, method:'css', matchCount:cssResults.length});
      }
    }
  } catch (e) {}

  // 4) Legacy semantic descriptor: role=... name="..." index=N
  var m = loc.match(/^role=([\\w-]+)\\s+name="?([^"]*?)"?\\s+index=(\\d+)$/i);
  if (m) {
    var role2 = m[1].toLowerCase(), name2 = m[2].trim(), index2 = parseInt(m[3], 10);
    var nodes2 = document.querySelectorAll('a,button,input,select,textarea,[role]');
    var n = 0;
    for (var j = 0; j < nodes2.length; j++) {
      var e2 = nodes2[j];
      if (e2.closest && e2.closest('[data-rcb-skip]')) continue;
      var tag2 = e2.tagName.toLowerCase();
      if (tag2 === 'input' && e2.type === 'hidden') continue;
      var r2 = (e2.getAttribute('role') || (tag2 === 'a' ? 'link' : (tag2 === 'input' ? 'input' : tag2))).toLowerCase();
      if (r2 !== role2) continue;
      var nm2 = (e2.getAttribute('aria-label') || (tag2 === 'a' || tag2 === 'button' ? (e2.innerText || '') : (e2.getAttribute('name') || ''))).trim();
      if (nm2 !== name2) continue;
      n++;
      if (n === index2) {
        var sid2 = markId(e2);
        return JSON.stringify({ok:true, id: sid2, method:'semantic', matchCount:1});
      }
    }
    return JSON.stringify({ok:false, reason:'SEMANTIC_NOT_FOUND'});
  }
  return JSON.stringify({ok:false, reason:'NOT_FOUND'});
})();
"""


internal const val JS_ACTIONABILITY = """
(function() {
  var id = arguments[0];
  var skipOverlap = arguments[1] === true;
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var rect = el.getBoundingClientRect();
  if (rect.width < 1 || rect.height < 1) return JSON.stringify({ok:false, reason:'NOT_VISIBLE', detail:'zero size'});
  var cs = window.getComputedStyle(el);
  if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0)
    return JSON.stringify({ok:false, reason:'NOT_VISIBLE', detail:'hidden style'});
  if (el.disabled || el.getAttribute('aria-disabled') === 'true')
    return JSON.stringify({ok:false, reason:'DISABLED'});
  if (!skipOverlap) {
    var x = rect.left + rect.width / 2, y = rect.top + rect.height / 2;
    var top;
    try { top = document.elementFromPoint(x, y); } catch(e) { top = null; }
    if (top && top !== el && !el.contains(top))
      return JSON.stringify({ok:false, reason:'OVERLAPPED', detail: top.tagName});
  }
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_SUBMIT = """
(function() {
  var id = arguments[0];
  var el = id ? document.querySelector('[data-rcb-id="' + id + '"]') : null;
  var form = el ? (el.closest('form') || null) : (document.querySelector('form') || null);
  if (el && !form) {
    el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view:window}));
    return JSON.stringify({ok:true, note:'clicked-element'});
  }
  if (!form) return JSON.stringify({ok:false, reason:'NO_FORM'});
  if (typeof form.requestSubmit === 'function') {
    try { form.requestSubmit(); return JSON.stringify({ok:true, note:'requestSubmit'}); } catch(e){}
  }
  form.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true}));
  return JSON.stringify({ok:true, note:'submit-event'});
})();
"""


internal const val JS_ATTRIBUTE = """
(function() {
  var id = arguments[0], attr = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var sensitive = el.tagName === 'INPUT' && el.type === 'password';
  var val;
  if (sensitive && (attr === 'value' || attr === 'textContent')) {
    val = '[redacted]';
  } else {
    val = el[attr] != null ? String(el[attr]) : (el.getAttribute(attr) || '');
  }
  return JSON.stringify({ok:true, value: val});
})();
"""


internal const val JS_HOVER = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center'});
  var r = el.getBoundingClientRect();
  var x = r.left + r.width / 2, y = r.top + r.height / 2;
  ['pointerover','mouseover','mouseenter','mousemove'].forEach(function(t) {
    el.dispatchEvent(new MouseEvent(t, {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
  });
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_PRESS_KEY = """
(function() {
  var id = arguments[0], key = arguments[1];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.focus();
  var code = key;
  if (key.length === 1) code = 'Key' + key.toUpperCase();
  else if (key === ' ') code = 'Space';
  var init = {bubbles:true, cancelable:true, key:key, code:code};
  el.dispatchEvent(new KeyboardEvent('keydown', init));
  el.dispatchEvent(new KeyboardEvent('keyup', init));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_DRAG = """
(function() {
  var srcId = arguments[0], dstId = arguments[1];
  var src = document.querySelector('[data-rcb-id="' + srcId + '"]');
  var dst = dstId ? document.querySelector('[data-rcb-id="' + dstId + '"]') : null;
  if (!src) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  var sr = src.getBoundingClientRect();
  var dr = dst ? dst.getBoundingClientRect() : sr;
  var sx = sr.left + sr.width / 2, sy = sr.top + sr.height / 2;
  var dx = dr.left + dr.width / 2, dy = dr.top + dr.height / 2;
  function me(t, x, y) { return new MouseEvent(t, {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y, button:0}); }
  src.dispatchEvent(me('pointerdown', sx, sy));
  src.dispatchEvent(me('mousedown', sx, sy));
  src.dispatchEvent(me('mousemove', dx, dy));
  (dst || src).dispatchEvent(me('mousemove', dx, dy));
  (dst || src).dispatchEvent(me('mouseup', dx, dy));
  src.dispatchEvent(me('pointerup', dx, dy));
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_UPLOAD_CLICK = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el || el.tagName !== 'INPUT' || el.type !== 'file') {
    return JSON.stringify({ok:false, reason: el ? 'NOT_FILE_INPUT' : 'NOT_FOUND'});
  }
  el.click();
  return JSON.stringify({ok:true});
})();
"""


internal const val JS_ELEMENT_RECT = """
(function() {
  var id = arguments[0];
  var el = document.querySelector('[data-rcb-id="' + id + '"]');
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  el.scrollIntoView({block:'center'});
  var r = el.getBoundingClientRect();
  return JSON.stringify({ok:true, x:r.left, y:r.top, width:r.width, height:r.height});
})();
"""


internal const val JS_GET_SCROLL_POS = """
(function() {
  return JSON.stringify({
    scrollY: window.scrollY,
    scrollHeight: document.body ? document.body.scrollHeight : 0,
    clientHeight: window.innerHeight
  });
})();
"""


