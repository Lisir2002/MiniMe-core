package com.mini.me_core.feature.browser.domain

/**
 * 浏览器控制器使用的所有 JavaScript 脚本常量。
 * 从 BrowserController.kt 提取，便于维护和复用。
 */
internal object BrowserJsScripts {

    const val JS_SNAPSHOT = """
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

    const val JS_PAGE_TEXT = """
    (function() {
      var t = document.body ? document.body.innerText : '';
      return t.slice(0, 12000);
    })();
"""

    const val JS_SCROLL = """
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

    const val JS_GET_ELEMENT_CENTER = """
    (function() {
      var id = arguments[0];
      var el = document.querySelector('[data-rcb-id="' + id + '"]');
      if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
      el.scrollIntoView({block:'center', behavior:'instant'});
      var r = el.getBoundingClientRect();
      return JSON.stringify({ok:true, x: r.left + r.width / 2, y: r.top + r.height / 2});
    })();
"""

    const val JS_MOUSE_MOVE = """
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

    const val JS_CLICK_AT = """
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

    const val JS_TYPE = """
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

    const val JS_SELECT = """
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

    const val JS_LOCATE = """
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

    const val JS_ACTIONABILITY = """
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

    const val JS_CHANGE_OBSERVER = """
    (function() {
      if (window.__rcb_mut) return;
      window.__rcb_mut = [];
      window.__rcb_mut_version = 0;
      var MAX_MUT = 50;
      function summarize(records) {
        for (var i = 0; i < records.length && window.__rcb_mut.length < MAX_MUT; i++) {
          var r = records[i];
          if (r.type === 'childList') {
            var t = r.target;
            if (t && t.nodeType === 1 && t.closest && t.closest('[data-rcb-skip]')) continue;
            if (r.addedNodes.length) window.__rcb_mut.push({type:'added', tag: r.addedNodes[0].nodeName || '', target: t && t.tagName ? t.tagName.toLowerCase() : ''});
            if (r.removedNodes.length) window.__rcb_mut.push({type:'removed', tag: r.removedNodes[0].nodeName || '', target: t && t.tagName ? t.tagName.toLowerCase() : ''});
          } else if (r.type === 'attributes') {
            if (r.target && r.target.nodeType === 1 && r.target.closest && r.target.closest('[data-rcb-skip]')) continue;
            window.__rcb_mut.push({type:'attr', name: r.attributeName || '', target: r.target && r.target.tagName ? r.target.tagName.toLowerCase() : ''});
          }
        }
        window.__rcb_mut_version++;
      }
      var obs = new MutationObserver(function(records) { summarize(records); });
      if (document.documentElement) {
        obs.observe(document.documentElement, {childList: true, subtree: true, attributes: true});
      }
    })();
"""

    const val JS_SUBMIT = """
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

    const val JS_ATTRIBUTE = """
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

    const val JS_HOVER = """
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

    const val JS_PRESS_KEY = """
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

    const val JS_DRAG = """
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

    const val JS_UPLOAD_CLICK = """
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

    const val JS_ELEMENT_RECT = """
    (function() {
      var id = arguments[0];
      var el = document.querySelector('[data-rcb-id="' + id + '"]');
      if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
      el.scrollIntoView({block:'center'});
      var r = el.getBoundingClientRect();
      return JSON.stringify({ok:true, x:r.left, y:r.top, width:r.width, height:r.height});
    })();
"""

    const val JS_EXTRACT = """
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

    const val JS_NET_HOOK = """
    (function() {
      if (window.__rcb_net) return;
      var MAX_NET = 100;
      var SNIPPET_MAX = 2000;
      var URL_KEY_RE = /([?&](?:token|access_token|id_token|refresh_token|apikey|api_key|secret|password|credential|session|sig|signature|key|auth)=)[^&#]*/gi;
      var BODY_KEY_RE = /"((?:access_token|id_token|refresh_token|password|secret|apikey|api_key))"(\s*:\s*)"[^"]*"/gi;

      window.__rcb_net = [];
      window.__rcb_net_pending = 0;
      window.__rcb_net_seq = 0;
      window.__rcb_route_seq = 0;

      function redactUrl(u) {
        return String(u || '').replace(URL_KEY_RE, function(m, p) { return p + '***'; });
      }
      function redactBody(s) {
        s = String(s || '');
        return s.replace(BODY_KEY_RE, function(m, key, sp) { return '"' + key + '"' + sp + '"***"'; });
      }
      function snippet(s) { return redactBody(s).slice(0, SNIPPET_MAX); }
      function pushRec(rec) {
        if (window.__rcb_net.length >= MAX_NET) window.__rcb_net.shift();
        window.__rcb_net.push(rec);
      }
      function newRec(op, method, url) {
        return { id: ++window.__rcb_net_seq, op: op, method: method, url: redactUrl(url), status: 0, duration_ms: 0, size: 0, start_ts: Date.now(), response_snippet: '', error: '' };
      }
      function settleRec(rec, status, bodyText, error) {
        rec.end_ts = Date.now();
        rec.duration_ms = rec.end_ts - rec.start_ts;
        if (typeof bodyText === 'string') { rec.size = bodyText.length; rec.response_snippet = snippet(bodyText); }
        if (status !== undefined && status !== null) rec.status = status;
        if (error) rec.error = String(error).slice(0, 200);
        window.__rcb_net_pending = Math.max(0, window.__rcb_net_pending - 1);
        pushRec(rec);
      }

      if (window.fetch) {
        var origFetch = window.fetch;
        window.fetch = function(input, init) {
          var url = '';
          try {
            if (typeof input === 'string') url = input;
            else if (input && input.url) url = input.url;
            else if (input && input.href) url = input.href;
          } catch (e) {}
          var method = (init && init.method) || 'GET';
          var rec = newRec('fetch', method, url);
          window.__rcb_net_pending++;
          var p;
          try { p = origFetch.apply(this, arguments); }
          catch (e) { settleRec(rec, 0, null, e && e.message); throw e; }
          return p.then(function(resp) {
            try {
              rec.status = resp.status;
              var c = resp.clone();
              if (c && c.text) {
                c.text().then(function(t) { rec.size = t.length; rec.response_snippet = snippet(t); }).catch(function() { rec.size = -1; });
              }
            } catch (e) { rec.size = -1; }
            rec.end_ts = Date.now(); rec.duration_ms = rec.end_ts - rec.start_ts;
            window.__rcb_net_pending = Math.max(0, window.__rcb_net_pending - 1);
            pushRec(rec);
            return resp;
          }).catch(function(e) {
            rec.error = String((e && e.message) || e).slice(0, 200);
            rec.end_ts = Date.now(); rec.duration_ms = rec.end_ts - rec.start_ts;
            window.__rcb_net_pending = Math.max(0, window.__rcb_net_pending - 1);
            pushRec(rec);
            throw e;
          });
        };
      }

      if (window.XMLHttpRequest) {
        var XHR = window.XMLHttpRequest;
        var origOpen = XHR.prototype.open;
        var origSend = XHR.prototype.send;
        XHR.prototype.open = function(method, url) {
          try { this.__rcb = { method: method, url: url }; } catch (e) {}
          return origOpen.apply(this, arguments);
        };
        XHR.prototype.send = function() {
          var self = this;
          var rec = null;
          if (self.__rcb) {
            rec = newRec('xhr', self.__rcb.method || 'GET', self.__rcb.url || '');
            window.__rcb_net_pending++;
          }
          if (rec && self.addEventListener) {
            self.addEventListener('loadend', function() {
              var bodyText = null;
              try {
                if (self.responseType === '' || self.responseType === 'text') bodyText = self.responseText;
                else {
                  if (self.response && (self.response.size != null)) rec.size = self.response.size;
                  else if (self.response && (self.response.byteLength != null)) rec.size = self.response.byteLength;
                  else rec.size = -1;
                }
              } catch (e) { rec.size = -1; }
              settleRec(rec, self.status, bodyText, (self.status === 0) ? 'network-error-or-aborted' : '');
            }, { once: true });
          }
          return origSend.apply(this, arguments);
        };
      }

      if (window.WebSocket) {
        var WS = window.WebSocket;
        function wsPatch(ws) {
          try {
            var url = '';
            try { url = ws.url || ''; } catch (e) {}
            var connRec = newRec('websocket', 'connect', url);
            window.__rcb_net_pending++;
            ws.addEventListener('open', function() {
              connRec.status = 101; connRec.duration_ms = Date.now() - connRec.start_ts;
              window.__rcb_net_pending = Math.max(0, window.__rcb_net_pending - 1); pushRec(connRec);
            });
            ws.addEventListener('close', function(e) {
              connRec.status = (e && e.code) ? e.code : 0; connRec.duration_ms = Date.now() - connRec.start_ts; pushRec(connRec);
            });
            ws.addEventListener('error', function() { connRec.error = 'websocket-error'; });
            ws.addEventListener('message', function(e) {
              var m = newRec('websocket', 'message', url);
              var data = e && e.data;
              if (typeof data === 'string') { m.size = data.length; m.response_snippet = snippet(data); }
              else if (data && (data.size != null)) m.size = data.size;
              else if (data && (data.byteLength != null)) m.size = data.byteLength;
              else if (data instanceof ArrayBuffer) m.size = data.byteLength;
              else if (data) m.size = -1;
              m.status = 101; m.duration_ms = 0; pushRec(m);
            });
            try {
              var origSend = ws.send;
              ws.send = function(data) {
                var m = newRec('websocket', 'send', url);
                try {
                  if (typeof data === 'string') { m.size = data.length; m.response_snippet = snippet(data); }
                  else if (data instanceof ArrayBuffer) m.size = data.byteLength;
                  else if (data && (data.byteLength != null)) m.size = data.byteLength;
                  else if (data && (data.size != null)) m.size = data.size;
                  else if (data) m.size = -1;
                } catch (e2) { m.size = -1; }
                pushRec(m);
                return origSend.apply(this, arguments);
              };
            } catch (e) {}
          } catch (e) {}
        }
        window.WebSocket = function(url, protocols) {
          var ws;
          try { ws = (arguments.length > 1) ? new WS(url, protocols) : new WS(url); } catch (e) { throw e; }
          wsPatch(ws);
          return ws;
        };
        try { window.WebSocket.prototype = WS.prototype; } catch (e) {}
      }

      if (window.EventSource) {
        var ES = window.EventSource;
        function esPatch(es) {
          try {
            var url = '';
            try { url = es.url || ''; } catch (e) {}
            var connRec = newRec('eventsource', 'connect', url);
            window.__rcb_net_pending++;
            es.addEventListener('open', function() {
              connRec.status = 200; connRec.duration_ms = Date.now() - connRec.start_ts;
              window.__rcb_net_pending = Math.max(0, window.__rcb_net_pending - 1); pushRec(connRec);
            });
            es.addEventListener('error', function() { connRec.error = 'eventsource-error'; });
            es.addEventListener('message', function(e) {
              var m = newRec('eventsource', 'event', url);
              var data = e && e.data;
              if (typeof data === 'string') { m.size = data.length; m.response_snippet = snippet(data); }
              m.status = 200; m.duration_ms = 0; pushRec(m);
            });
          } catch (e) {}
        }
        window.EventSource = function(url, config) {
          var es;
          try { es = (arguments.length > 1) ? new ES(url, config) : new ES(url); } catch (e) { throw e; }
          esPatch(es);
          return es;
        };
        try { window.EventSource.prototype = ES.prototype; } catch (e) {}
      }

      try {
        function routeChanged() {
          window.__rcb_route_seq++;
          try { window.dispatchEvent(new Event('rcb-routechange')); } catch (e2) {}
        }
        ['pushState', 'replaceState'].forEach(function(m) {
          var orig = history[m];
          history[m] = function() {
            var r = orig.apply(this, arguments);
            routeChanged();
            return r;
          };
        });
        window.addEventListener('popstate', routeChanged);
      } catch (e) {}
    })();
"""

    const val JS_ANTI_DETECT = """
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

    const val JS_STRUCTURED_EXTRACT = """
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

    const val JS_APPEND_CHAR = """
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

    const val JS_FIRE_INPUT = """
    (function() {
      var id = arguments[0];
      var el = document.querySelector('[data-rcb-id="' + id + '"]');
      if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
      el.dispatchEvent(new Event('input', {bubbles:true}));
      el.dispatchEvent(new Event('change', {bubbles:true}));
      return JSON.stringify({ok:true});
    })();
"""

}
