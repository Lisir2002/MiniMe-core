package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_LIST_IFRAMES = """
(function() {
  var MAX_DEPTH = 5;
  var results = [];
  function walk(doc, depth, prefix) {
    if (depth > MAX_DEPTH) return;
    var frames = doc.querySelectorAll('iframe, frame');
    for (var i = 0; i < frames.length; i++) {
      var f = frames[i];
      var accessible = false;
      var childDoc = null;
      try {
        childDoc = f.contentDocument || f.contentWindow && f.contentWindow.document;
        accessible = !!childDoc;
      } catch(e) { accessible = false; }
      var path = prefix ? prefix + ' >> iframe=' + (f.id || f.name || i) : 'iframe=' + (f.id || f.name || i);
      results.push({
        index: results.length,
        id: f.id || '',
        name: f.name || '',
        src: (f.src || '').slice(0, 200),
        accessible: accessible,
        depth: depth,
        path: path
      });
      if (accessible && childDoc) walk(childDoc, depth + 1, path);
    }
  }
  walk(document, 1, '');
  return JSON.stringify({ count: results.length, iframes: results });
})();
"""


internal const val JS_IFRAME_CHAIN_LOCATE = """
(function() {
  var chain = arguments[0];
  if (!chain) return JSON.stringify({ok:false, reason:'EMPTY_CHAIN'});
  var parts = chain.split(/\s*>>\s*/);
  var doc = document;
  var framePath = [];
  for (var i = 0; i < parts.length - 1; i++) {
    var part = parts[i].trim();
    if (part.indexOf('iframe=') === 0) {
      var sel = part.slice(7);
      var frame = null;
      try {
        if (sel.charAt(0) === '#') {
          frame = doc.querySelector('iframe' + sel);
        } else {
          frame = doc.querySelector('iframe#' + sel + ', iframe[name="' + sel + '"]');
          if (!frame) frame = doc.querySelectorAll('iframe')[parseInt(sel) || 0];
        }
      } catch(e) {}
      if (!frame) return JSON.stringify({ok:false, reason:'IFRAME_NOT_FOUND: ' + sel});
      try {
        doc = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
      } catch(e) {
        return JSON.stringify({ok:false, reason:'IFRAME_CROSS_ORIGIN: ' + sel});
      }
      if (!doc) return JSON.stringify({ok:false, reason:'IFRAME_NO_DOC: ' + sel});
      framePath.push(sel);
    } else {
      return JSON.stringify({ok:false, reason:'INVALID_CHAIN_PART: ' + part});
    }
  }
  var finalSel = parts[parts.length - 1].trim();
  // 最终选择器：可以是 CSS 选择器或 data-rcb-id
  var el = null;
  try {
    if (finalSel.indexOf('data-rcb-id=') >= 0 || finalSel.charAt(0) === '[') {
      el = doc.querySelector(finalSel);
    } else if (/^\d+$/.test(finalSel)) {
      el = doc.querySelector('[data-rcb-id="' + finalSel + '"]');
    } else {
      el = doc.querySelector(finalSel);
    }
  } catch(e) {}
  if (!el) return JSON.stringify({ok:false, reason:'ELEMENT_NOT_FOUND in iframe chain'});
  // 标记 data-rcb-id
  if (!window.__rcb_seq) window.__rcb_seq = 0;
  var id = el.getAttribute('data-rcb-id');
  if (!id) { id = String(++window.__rcb_seq); el.setAttribute('data-rcb-id', id); }
  return JSON.stringify({ok:true, id:id, method:'iframe_chain', matchCount:1, frame_path: framePath.join(' >> ')});
})();
"""


internal const val JS_IFRAME_ACTION = """
(function() {
  var chain = arguments[0];
  var action = arguments[1];
  var arg1 = arguments[2];
  var arg2 = arguments[3];
  var parts = chain.split(/\s*>>\s*/);
  var doc = document;
  for (var i = 0; i < parts.length; i++) {
    var part = parts[i].trim();
    if (part.indexOf('iframe=') === 0) {
      var sel = part.slice(7);
      var frame = null;
      try {
        if (sel.charAt(0) === '#') frame = doc.querySelector('iframe' + sel);
        else {
          frame = doc.querySelector('iframe#' + sel + ', iframe[name="' + sel + '"]');
          if (!frame) frame = doc.querySelectorAll('iframe')[parseInt(sel) || 0];
        }
      } catch(e) {}
      if (!frame) return JSON.stringify({ok:false, reason:'IFRAME_NOT_FOUND: ' + sel});
      try { doc = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document); }
      catch(e) { return JSON.stringify({ok:false, reason:'IFRAME_CROSS_ORIGIN: ' + sel}); }
      if (!doc) return JSON.stringify({ok:false, reason:'IFRAME_NO_DOC: ' + sel});
    }
  }
  // 在最终 doc 中查找元素
  var el = null;
  try {
    el = doc.querySelector('[data-rcb-id="' + arg1 + '"]') || doc.querySelector(arg1);
  } catch(e) {}
  if (!el) return JSON.stringify({ok:false, reason:'ELEMENT_NOT_FOUND'});
  if (action === 'click') {
    el.scrollIntoView({block:'center'});
    el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view: window}));
    return JSON.stringify({ok:true, action:'click'});
  }
  if (action === 'type') {
    el.scrollIntoView({block:'center'});
    el.focus();
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(el, arg2 || '');
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    return JSON.stringify({ok:true, action:'type'});
  }
  if (action === 'hover') {
    el.scrollIntoView({block:'center'});
    el.dispatchEvent(new MouseEvent('mouseover', {bubbles:true, cancelable:true, view: window}));
    return JSON.stringify({ok:true, action:'hover'});
  }
  return JSON.stringify({ok:false, reason:'UNSUPPORTED_ACTION: ' + action});
})();
"""


