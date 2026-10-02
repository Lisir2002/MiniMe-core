package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_SNAPSHOT = """
(function() {
  function txt(n) { return (n && (n.innerText || n.textContent || '') || '').replace(/\s+/g, ' ').trim(); }
  function resolveLabel(el) {
    var al = (el.getAttribute('aria-label') || '').trim();
    if (al) return al;
    var lb = el.getAttribute('aria-labelledby');
    if (lb) {
      var parts = [], ns = lb.split(/\s+/);
      for (var i = 0; i < ns.length; i++) {
        var ref = document.getElementById(ns[i]);
        if (ref) { var t = txt(ref); if (t) parts.push(t); }
      }
      if (parts.length) return parts.join(' ');
    }
    var fid = el.id || el.name;
    if (fid) {
      try {
        var lab = document.querySelector('label[for="' + fid + '"]');
        if (lab) { var t = txt(lab); if (t) return t; }
      } catch (e) {}
    }
    if (el.closest) {
      var parent = el.closest('label');
      if (parent) { var t2 = txt(parent); if (t2) return t2; }
    }
    return '';
  }
  function computeAccessibleName(el) {
    var label = resolveLabel(el);
    if (label) return label;
    var tag = el.tagName.toLowerCase();
    if (tag === 'button' || tag === 'a' || tag === 'summary') {
      var t = txt(el);
      if (t) return t;
    }
    if (tag === 'input' || tag === 'textarea') {
      var ph = (el.getAttribute('placeholder') || '').trim();
      if (ph) return ph;
    }
    var nm = (el.getAttribute('name') || '').trim();
    if (nm) return nm;
    return '';
  }
  // CSS 绝对路径：html > body > div#main > form > div:nth-child(2) > input
  function cssPath(el) {
    if (!el || el.nodeType !== 1) return '';
    var parts = [], node = el;
    while (node && node.nodeType === 1 && node !== document.documentElement) {
      var part = node.tagName.toLowerCase();
      if (node.id) { part += '#' + node.id; parts.unshift(part); break; }
      if (node.classList && node.classList.length) part += '.' + node.classList[0];
      var parent = node.parentElement;
      if (parent && parent.children.length > 1) {
        var idx = 1, sib = parent.firstElementChild;
        while (sib && sib !== node) { if (sib.tagName === node.tagName) idx++; sib = sib.nextElementSibling; }
        part += ':nth-child(' + idx + ')';
      }
      parts.unshift(part);
      node = parent;
    }
    parts.unshift('html');
    return parts.join(' > ');
  }
  function isVisible(el) {
    var rect = el.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) return false;
    var cs = window.getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    var o = parseFloat(cs.opacity);
    if (!isNaN(o) && o === 0) return false;
    return true;
  }
  function isInViewport(el) {
    var r = el.getBoundingClientRect();
    var vh = window.innerHeight || document.documentElement.clientHeight;
    var vw = window.innerWidth || document.documentElement.clientWidth;
    return r.top >= 0 && r.left >= 0 && r.bottom <= vh && r.right <= vw;
  }
  function isOverlapped(el) {
    var r = el.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var top;
    try { top = document.elementFromPoint(x, y); } catch (e) { return false; }
    if (!top) return false;
    return !(top === el || el.contains(top));
  }
  if (!window.__rcb_seq) window.__rcb_seq = 0;
  var els = [];
  var seen = new WeakSet();
  var sel = 'a,button,input,select,textarea,[role="button"],[role="link"],[role="menuitem"],[contenteditable="true"],summary';
  var semIndex = {};
  function semKey(el, kind) {
    var role = (el.getAttribute('role') || (kind === 'link' ? 'link' : (kind.indexOf('input') === 0 ? 'input' : el.tagName.toLowerCase()))).toLowerCase();
    var type = (el.getAttribute('type') || '').toLowerCase();
    return role + '|' + type;
  }
  function collect(root) {
    if (!root || seen.has(root)) return;
    seen.add(root);
    var nodes = root.querySelectorAll ? root.querySelectorAll(sel) : [];
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.closest('[data-rcb-skip]')) continue;
      if (el.tagName === 'INPUT' && el.type === 'hidden') continue;
      var rect = el.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) continue;
      var id = el.getAttribute('data-rcb-id');
      if (!id) { id = String(++window.__rcb_seq); el.setAttribute('data-rcb-id', id); }
      var tag = el.tagName.toLowerCase();
      var type = (el.getAttribute('type') || '').toLowerCase();
      var role = el.getAttribute('role') || '';
      var sensitive = tag === 'input' && type === 'password';
      var kind = tag;
      if (tag === 'a' && el.getAttribute('href')) kind = 'link';
      else if (tag === 'button' || role === 'button') kind = 'button';
      else if (tag === 'input') kind = 'input:' + (type || 'text');
      else if (tag === 'select') kind = 'select';
      else if (tag === 'textarea') kind = 'textarea';
      var label = resolveLabel(el);
      var accessibleName = computeAccessibleName(el);
      var visibleText = (tag === 'input' || tag === 'textarea') ? '' : txt(el);
      var value = (tag === 'input' || tag === 'textarea') ? (sensitive ? '' : (el.value || '')) : '';
      var options = [];
      if (tag === 'select') {
        var so = el.options && el.options[el.selectedIndex];
        if (so) value = so.text || so.value || '';
        for (var o = 0; o < el.options.length && o < 30; o++) {
          options.push({ value: el.options[o].value || '', text: txt(el.options[o]) || el.options[o].value || '' });
        }
      }
      var checked = (tag === 'input' && (type === 'checkbox' || type === 'radio')) ? !!el.checked : false;
      var loc = cssPath(el);
      var skey = semKey(el, kind);
      semIndex[skey] = (semIndex[skey] || 0) + 1;
      var semRole = role || (kind === 'link' ? 'link' : (kind.indexOf('input') === 0 ? 'input' : tag));
      var semantic = 'role=' + semRole + ' name="' + (label || visibleText).slice(0, 80) + '" index=' + semIndex[skey];
      var visible = isVisible(el);
      var inViewport = visible && isInViewport(el);
      els.push({
        id: id,
        tag: tag,
        kind: kind,
        role: role,
        type: type,
        name: el.getAttribute('name') || '',
        label: label,
        accessibleName: accessibleName,
        text: visibleText.slice(0, 200),
        value: value.slice(0, 200),
        href: el.getAttribute('href') || '',
        ariaLabel: el.getAttribute('aria-label') || '',
        ariaExpanded: el.getAttribute('aria-expanded') || '',
        heading: /^H[1-6]$/.test(el.tagName) ? tag : '',
        contenteditable: el.getAttribute('contenteditable') === 'true',
        disabled: !!el.disabled || el.getAttribute('aria-disabled') === 'true',
        required: !!el.required,
        readonly: !!el.readOnly,
        checked: checked,
        options: options,
        placeholder: el.getAttribute('placeholder') || '',
        sensitive: sensitive,
        locator: loc,
        semantic: semantic,
        inViewport: inViewport,
        visible: visible,
        needsScroll: visible && !inViewport,
        overlapped: inViewport && isOverlapped(el)
      });
      // 元素上限保护：超大页面（长列表/表格/导航）只取前 MAX_SNAPSHOT_ELEMENTS 个，
      // 防止一次快照生成超大 JSON（内存/耗时峰值，低内存时易触发 LMKD 静默杀进程）。
      if (els.length >= MAX_SNAPSHOT_ELEMENTS) break;
    }
  }
  collect(document);
  var headings = [];
  var hnodes = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
  for (var i = 0; i < hnodes.length && i < 100; i++) {
    headings.push({ level: parseInt(hnodes[i].tagName.charAt(1)), text: txt(hnodes[i]).slice(0, 200) });
  }
  return JSON.stringify({ title: document.title || '', url: location.href, headings: headings, elements: els });
})();
"""


internal const val JS_PAGE_TEXT = """
(function() {
  var t = document.body ? document.body.innerText : '';
  return t.slice(0, 12000);
})();
"""


internal const val JS_EXTRACT = """
(function() {
  var selector = arguments[0], mode = arguments[1] || 'text';
  var el = selector ? document.querySelector(selector) : document;
  if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
  function txt(n) { return (n.innerText || n.textContent || '').trim(); }
  if (mode === 'links') {
    var links = el.querySelectorAll ? el.querySelectorAll('a') : [];
    var out = [];
    for (var i=0;i<links.length && i<200;i++) {
      var h = links[i].getAttribute('href') || '';
      var t = txt(links[i]);
      if (h || t) out.push({text: t.slice(0,120), href: h});
    }
    return JSON.stringify({ok:true, mode:mode, data: out});
  }
  if (mode === 'headings') {
    var heads = el.querySelectorAll ? el.querySelectorAll('h1,h2,h3,h4,h5,h6') : [];
    var hs = [];
    for (var j=0;j<heads.length && j<200;j++) {
      hs.push({level: parseInt(heads[j].tagName.charAt(1)), text: txt(heads[j]).slice(0,200)});
    }
    return JSON.stringify({ok:true, mode:mode, data: hs});
  }
  if (mode === 'table') {
    var table = el.tagName === 'TABLE' ? el : (el.querySelector ? el.querySelector('table') : null);
    if (!table) return JSON.stringify({ok:false, reason:'NO_TABLE'});
    var trs = table.querySelectorAll('tr');
    var rows = [];
    for (var k=0;k<trs.length && k<500;k++) {
      var tds = trs[k].querySelectorAll('th,td');
      var cells = [];
      for (var m=0;m<tds.length;m++) cells.push(txt(tds[m]).slice(0,200));
      rows.push(cells);
    }
    return JSON.stringify({ok:true, mode:mode, data: rows});
  }
  if (mode === 'html') {
    return JSON.stringify({ok:true, mode:mode, data: (el.innerHTML || '').slice(0, 20000)});
  }
  return JSON.stringify({ok:true, mode:mode, data: txt(el).slice(0, 20000)});
})();
"""


internal const val JS_SNAPSHOT_SHADOW = """
(function() {
  function txt(n) { return (n && (n.innerText || n.textContent || '') || '').replace(/\s+/g, ' ').trim(); }
  function resolveLabel(el) {
    var al = (el.getAttribute('aria-label') || '').trim();
    if (al) return al;
    var lb = el.getAttribute('aria-labelledby');
    if (lb) {
      var parts = [], ns = lb.split(/\s+/);
      for (var i = 0; i < ns.length; i++) {
        var ref = document.getElementById(ns[i]);
        if (ref) { var t = txt(ref); if (t) parts.push(t); }
      }
      if (parts.length) return parts.join(' ');
    }
    var fid = el.id || el.name;
    if (fid) {
      try {
        var lab = el.getRootNode().querySelector('label[for="' + fid + '"]');
        if (lab) { var t = txt(lab); return t; }
      } catch (e) {}
    }
    if (el.closest) {
      var parent = el.closest('label');
      if (parent) { var t2 = txt(parent); if (t2) return t2; }
    }
    return '';
  }
  function cssPath(el) {
    if (!el || el.nodeType !== 1) return '';
    var parts = [], node = el;
    while (node && node.nodeType === 1 && node !== document.documentElement) {
      var part = node.tagName.toLowerCase();
      if (node.id) { part += '#' + node.id; parts.unshift(part); break; }
      if (node.classList && node.classList.length) part += '.' + node.classList[0];
      var parent = node.parentElement;
      if (parent && parent.children.length > 1) {
        var idx = 1, sib = parent.firstElementChild;
        while (sib && sib !== node) { if (sib.tagName === node.tagName) idx++; sib = sib.nextElementSibling; }
        part += ':nth-child(' + idx + ')';
      }
      parts.unshift(part);
      node = parent;
    }
    parts.unshift('html');
    return parts.join(' > ');
  }
  function isVisible(el) {
    var rect = el.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) return false;
    var cs = window.getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    var o = parseFloat(cs.opacity);
    if (!isNaN(o) && o === 0) return false;
    return true;
  }
  function isInViewport(el) {
    var r = el.getBoundingClientRect();
    var vh = window.innerHeight || document.documentElement.clientHeight;
    var vw = window.innerWidth || document.documentElement.clientWidth;
    return r.top >= 0 && r.left >= 0 && r.bottom <= vh && r.right <= vw;
  }
  function isOverlapped(el) {
    var r = el.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var top;
    try { top = document.elementFromPoint(x, y); } catch (e) { return false; }
    if (!top) return false;
    return !(top === el || el.contains(top));
  }
  if (!window.__rcb_seq) window.__rcb_seq = 0;
  var els = [];
  var seen = new WeakSet();
  var sel = 'a,button,input,select,textarea,[role="button"],[role="link"],[role="menuitem"],[contenteditable="true"],summary';
  var semIndex = {};
  function semKey(el, kind) {
    var role = (el.getAttribute('role') || (kind === 'link' ? 'link' : (kind.indexOf('input') === 0 ? 'input' : el.tagName.toLowerCase()))).toLowerCase();
    var type = (el.getAttribute('type') || '').toLowerCase();
    return role + '|' + type;
  }
  function collect(root, shadowPath) {
    if (!root || seen.has(root)) return;
    seen.add(root);
    var nodes = root.querySelectorAll ? root.querySelectorAll(sel) : [];
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.closest('[data-rcb-skip]')) continue;
      if (el.tagName === 'INPUT' && el.type === 'hidden') continue;
      var rect = el.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) continue;
      var id = el.getAttribute('data-rcb-id');
      if (!id) { id = String(++window.__rcb_seq); el.setAttribute('data-rcb-id', id); }
      var tag = el.tagName.toLowerCase();
      var type = (el.getAttribute('type') || '').toLowerCase();
      var role = el.getAttribute('role') || '';
      var sensitive = tag === 'input' && type === 'password';
      var kind = tag;
      if (tag === 'a' && el.getAttribute('href')) kind = 'link';
      else if (tag === 'button' || role === 'button') kind = 'button';
      else if (tag === 'input') kind = 'input:' + (type || 'text');
      else if (tag === 'select') kind = 'select';
      else if (tag === 'textarea') kind = 'textarea';
      var label = resolveLabel(el);
      var visibleText = (tag === 'input' || tag === 'textarea') ? '' : txt(el);
      var value = (tag === 'input' || tag === 'textarea') ? (sensitive ? '' : (el.value || '')) : '';
      var options = [];
      if (tag === 'select') {
        var so = el.options && el.options[el.selectedIndex];
        if (so) value = so.text || so.value || '';
        for (var o = 0; o < el.options.length && o < 30; o++) {
          options.push({ value: el.options[o].value || '', text: txt(el.options[o]) || el.options[o].value || '' });
        }
      }
      var checked = (tag === 'input' && (type === 'checkbox' || type === 'radio')) ? !!el.checked : false;
      var loc = cssPath(el);
      if (shadowPath) loc = shadowPath + ' >> ' + loc;
      var skey = semKey(el, kind);
      semIndex[skey] = (semIndex[skey] || 0) + 1;
      var semRole = role || (kind === 'link' ? 'link' : (kind.indexOf('input') === 0 ? 'input' : tag));
      var semantic = 'role=' + semRole + ' name="' + (label || visibleText).slice(0, 80) + '" index=' + semIndex[skey];
      if (shadowPath) semantic = '[shadow] ' + semantic;
      var visible = isVisible(el);
      var inViewport = visible && isInViewport(el);
      els.push({
        id: id, tag: tag, kind: kind, role: role, type: type,
        name: el.getAttribute('name') || '', label: label,
        text: visibleText.slice(0, 200), value: value.slice(0, 200),
        href: el.getAttribute('href') || '',
        ariaLabel: el.getAttribute('aria-label') || '',
        ariaExpanded: el.getAttribute('aria-expanded') || '',
        heading: /^H[1-6]$/.test(el.tagName) ? tag : '',
        contenteditable: el.getAttribute('contenteditable') === 'true',
        disabled: !!el.disabled || el.getAttribute('aria-disabled') === 'true',
        required: !!el.required, readonly: !!el.readOnly, checked: checked,
        options: options, placeholder: el.getAttribute('placeholder') || '',
        sensitive: sensitive, locator: loc, semantic: semantic,
        inViewport: inViewport, visible: visible,
        needsScroll: visible && !inViewport,
        overlapped: inViewport && isOverlapped(el),
        shadow: !!shadowPath
      });
      if (els.length >= 300) return;
    }
    // 递归穿透 open shadow root
    if (root.querySelectorAll) {
      var allEls = root.querySelectorAll('*');
      for (var j = 0; j < allEls.length; j++) {
        var host = allEls[j];
        if (host.shadowRoot && host.shadowRoot.mode === 'open') {
          var hostTag = host.tagName.toLowerCase();
          var hostId = host.id ? '#' + host.id : '';
          var shadowPath2 = (shadowPath ? shadowPath + ' >> ' : '') + 'shadow(' + hostTag + hostId + ')';
          collect(host.shadowRoot, shadowPath2);
          if (els.length >= 300) return;
        }
      }
    }
  }
  collect(document, null);
  var headings = [];
  var hnodes = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
  for (var i = 0; i < hnodes.length && i < 100; i++) {
    headings.push({ level: parseInt(hnodes[i].tagName.charAt(1)), text: txt(hnodes[i]).slice(0, 200) });
  }
  // 统计 shadow root 数量
  var shadowCount = 0;
  try {
    document.querySelectorAll('*').forEach(function(el) {
      if (el.shadowRoot && el.shadowRoot.mode === 'open') shadowCount++;
    });
  } catch(e) {}
  return JSON.stringify({ title: document.title || '', url: location.href, headings: headings, elements: els, shadow_roots: shadowCount });
})();
"""


internal const val JS_SNAPSHOT_API_SOURCE = """
(function() {
  var calls = window.__rcb_api_calls || [];
  var summary = [];
  for (var i = Math.max(0, calls.length - 10); i < calls.length; i++) {
    summary.push({
      id: calls[i].id,
      type: calls[i].type,
      method: calls[i].method,
      url: (calls[i].url || '').slice(0, 200),
      status: calls[i].status,
      duration_ms: calls[i].duration_ms,
      response_size: (calls[i].response_body || '').length
    });
  }
  return JSON.stringify({ api_call_count: calls.length, recent_calls: summary });
})();
"""


