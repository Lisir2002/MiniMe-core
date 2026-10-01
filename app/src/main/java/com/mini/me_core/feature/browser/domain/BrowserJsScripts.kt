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

    // ═══════════════════════════════════════════════════════════════
    // 第一批：反爬虫核心 JS 脚本
    // ═══════════════════════════════════════════════════════════════

    /**
     * Shadow DOM 穿透快照：递归遍历 open Shadow Root，将 shadow 内元素纳入统一编号。
     * 在 JS_SNAPSHOT 基础上增强 collect 函数，使其同时穿透 shadow tree。
     * 元素 id 保持全局唯一（__rcb_seq 递增），shadow 内元素标记 shadowRoot=true。
     */
    const val JS_SNAPSHOT_SHADOW = """
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

    /**
     * 列出所有 iframe（最多 5 层），报告同源可访问性。
     * 返回 [{index, id, name, src, accessible, depth}]
     */
    const val JS_LIST_IFRAMES = """
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

    /**
     * iframe 链式定位：解析 `iframe=id1 >> iframe=id2 >> selector` 格式的定位符。
     * 沿 iframe 链穿透到目标 frame，在其中查找元素并标记 data-rcb-id。
     * 返回 {ok, id, method, matchCount} 或 {ok:false, reason}
     */
    const val JS_IFRAME_CHAIN_LOCATE = """
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

    /**
     * 在指定 iframe 链内执行操作（click/type 等）。
     * 用法：JS_IFRAME_ACTION(frameChain, action, actionArgs)
     * 目前支持 click / type / hover
     */
    const val JS_IFRAME_ACTION = """
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

    /**
     * 增强版网络拦截：全量捕获 XHR/fetch 请求体 + 响应体（单条最大 5MB）。
     * 存储在 window.__rcb_api_calls 数组中，每条含完整 request/response。
     * 同时保留原 __rcb_net 兼容字段。
     */
    const val JS_NET_HOOK_V2 = """
    (function() {
      if (window.__rcb_net_v2) return;
      window.__rcb_net_v2 = true;
      var MAX_CALLS = 200;
      var MAX_BODY = 5 * 1024 * 1024; // 5MB 单条上限
      var SNIPPET_MAX = 2000;
      var URL_KEY_RE = /([?&](?:token|access_token|id_token|refresh_token|apikey|api_key|secret|password|credential|session|sig|signature|key|auth)=)[^&#]*/gi;
      var BODY_KEY_RE = /"((?:access_token|id_token|refresh_token|password|secret|apikey|api_key))"(\s*:\s*)"[^"]*"/gi;

      window.__rcb_api_calls = [];
      window.__rcb_api_seq = 0;

      function redactUrl(u) { return String(u || '').replace(URL_KEY_RE, function(m, p) { return p + '***'; }); }
      function redactBody(s) {
        s = String(s || '');
        return s.replace(BODY_KEY_RE, function(m, key, sp) { return '"' + key + '"' + sp + '"***"'; });
      }
      function truncateBody(s) {
        s = String(s || '');
        if (s.length > MAX_BODY) return s.slice(0, MAX_BODY) + '...[truncated ' + (s.length - MAX_BODY) + ' bytes]';
        return s;
      }
      function snippet(s) { return redactBody(s).slice(0, SNIPPET_MAX); }
      function pushCall(call) {
        if (window.__rcb_api_calls.length >= MAX_CALLS) window.__rcb_api_calls.shift();
        window.__rcb_api_calls.push(call);
      }

      // 增强 fetch
      if (window.fetch) {
        var origFetch = window.fetch;
        window.fetch = function(input, init) {
          var url = '', method = 'GET', reqBody = '';
          try {
            if (typeof input === 'string') url = input;
            else if (input && input.url) url = input.url;
            else if (input && input.href) url = input.href;
            method = (init && init.method) || (input && input.method) || 'GET';
            if (init && init.body) {
              if (typeof init.body === 'string') reqBody = init.body;
              else if (init.body instanceof FormData) {
                reqBody = '[FormData] ' + Array.from(init.body.entries()).map(function(e){return e[0]+'='+(typeof e[1]==='string'?e[1]:'[file]');}).join('&');
              } else reqBody = '[non-string body]';
            }
          } catch(e) {}
          var call = {
            id: ++window.__rcb_api_seq,
            type: 'fetch',
            method: method,
            url: redactUrl(url),
            request_headers: (init && init.headers) ? JSON.stringify(init.headers) : '',
            request_body: truncateBody(redactBody(reqBody)),
            status: 0, response_body: '', response_snippet: '',
            start_ts: Date.now(), end_ts: 0, duration_ms: 0, error: ''
          };
          var p;
          try { p = origFetch.apply(this, arguments); }
          catch(e) { call.error = String(e && e.message || e); call.end_ts = Date.now(); call.duration_ms = call.end_ts - call.start_ts; pushCall(call); throw e; }
          return p.then(function(resp) {
            call.status = resp.status;
            try {
              var c = resp.clone();
              c.text().then(function(t) {
                call.response_body = truncateBody(redactBody(t));
                call.response_snippet = snippet(t);
              }).catch(function(){});
            } catch(e) {}
            call.end_ts = Date.now(); call.duration_ms = call.end_ts - call.start_ts;
            pushCall(call);
            return resp;
          }).catch(function(e) {
            call.error = String((e && e.message) || e);
            call.end_ts = Date.now(); call.duration_ms = call.end_ts - call.start_ts;
            pushCall(call);
            throw e;
          });
        };
      }

      // 增强 XHR
      if (window.XMLHttpRequest) {
        var XHR = window.XMLHttpRequest;
        var origOpen = XHR.prototype.open;
        var origSend = XHR.prototype.send;
        var origSetHeader = XHR.prototype.setRequestHeader;
        XHR.prototype.open = function(method, url) {
          try { this.__rcb_v2 = { method: method, url: url, headers: {} }; } catch(e) {}
          return origOpen.apply(this, arguments);
        };
        XHR.prototype.setRequestHeader = function(k, v) {
          try { if (this.__rcb_v2) this.__rcb_v2.headers[k] = v; } catch(e) {}
          return origSetHeader.apply(this, arguments);
        };
        XHR.prototype.send = function(body) {
          var self = this;
          if (!self.__rcb_v2) return origSend.apply(this, arguments);
          var call = {
            id: ++window.__rcb_api_seq,
            type: 'xhr',
            method: self.__rcb_v2.method || 'GET',
            url: redactUrl(self.__rcb_v2.url || ''),
            request_headers: JSON.stringify(self.__rcb_v2.headers || {}),
            request_body: truncateBody(redactBody(typeof body === 'string' ? body : (body ? '[non-string body]' : ''))),
            status: 0, response_body: '', response_snippet: '',
            start_ts: Date.now(), end_ts: 0, duration_ms: 0, error: ''
          };
          self.addEventListener('loadend', function() {
            try {
              if (self.responseType === '' || self.responseType === 'text') {
                call.response_body = truncateBody(redactBody(self.responseText || ''));
                call.response_snippet = snippet(self.responseText || '');
              }
            } catch(e) {}
            call.status = self.status || 0;
            call.end_ts = Date.now(); call.duration_ms = call.end_ts - call.start_ts;
            if (!call.status) call.error = 'network-error-or-aborted';
            pushCall(call);
          }, { once: true });
          return origSend.apply(this, arguments);
        };
      }
    })();
"""

    /**
     * 重放已捕获的 API 请求：按 id 从 __rcb_api_calls 中取出，用 fetch 重新发送。
     * 返回 {ok, status, response_snippet, response_body}
     */
    const val JS_REPLAY_API = """
    (function() {
      var callId = parseInt(arguments[0]);
      var calls = window.__rcb_api_calls || [];
      var call = null;
      for (var i = calls.length - 1; i >= 0; i--) {
        if (calls[i].id === callId) { call = calls[i]; break; }
      }
      if (!call) return JSON.stringify({ok:false, reason:'CALL_NOT_FOUND: ' + callId});
      return fetch(call.url, {
        method: call.method || 'GET',
        headers: call.request_headers ? JSON.parse(call.request_headers) : {},
        body: (call.method === 'GET' || call.method === 'HEAD') ? undefined : call.request_body
      }).then(function(resp) {
        return resp.text().then(function(t) {
          return JSON.stringify({
            ok: true,
            id: callId,
            status: resp.status,
            response_snippet: t.slice(0, 2000),
            response_body: t.slice(0, 50000)
          });
        });
      }).catch(function(e) {
        return JSON.stringify({ok:false, reason: 'REPLAY_ERROR: ' + (e.message || e)});
      });
    })();
"""

    /**
     * 渲染完成等待：三重检测（DOM Mutation 稳定 + 网络空闲 + CSS 动画完成）。
     * 轮询检测：1) MutationObserver 版本号连续稳定 2) 无在途请求 3) 无运行中的 CSS 动画
     * 返回 {ready: bool, reason: string, checks: {dom_stable, network_idle, css_idle}}
     */
    const val JS_RENDER_WAIT_CHECK = """
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

    /**
     * 渲染类型检测：自动识别 SSR / CSR / SSG。
     * 检测信号：__NEXT_DATA__ / __NUXT__ / window.__INITIAL_STATE__ / 空 body+script / 有服务端渲染的完整 HTML
     */
    const val JS_DETECT_RENDERING_TYPE = """
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

    /**
     * Aggressive 模式指纹伪装：全量随机化 Canvas/WebGL/AudioContext/字体列表/时区/语言。
     * 每次页面加载时注入，生成随机但一致的指纹。
     * 渲染异常时由 Kotlin 层检测并回退到 basic 模式（仅覆盖 webdriver/plugins）。
     */
    const val JS_STEALTH_AGGRESSIVE = """
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

    /**
     * 渲染异常检测：检查 aggressive 模式是否导致页面渲染异常。
     * 返回 {anomaly: bool, reason: string, checks: {canvas_ok, webgl_ok, body_ok}}
     */
    const val JS_STEALTH_HEALTH_CHECK = """
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

    /**
     * 内容清洗：检测 CSS 混淆、清理零宽字符、识别字体反爬。
     * 返回清洗后的纯文本 + 检测到的混淆类型列表。
     */
    const val JS_DEOBFUSCATE_TEXT = """
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

    /**
     * 提取清洗后的页面文本（deobfuscate 版本）。
     * 自动清理零宽字符、移除隐藏元素文本、检测字体反爬。
     */
    const val JS_EXTRACT_CLEAN_TEXT = """
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

    /**
     * 分页提取：自动检测"下一页"按钮并翻页提取数据。
     * 策略：1) 找分页链接（a[rel=next] / .pagination a / .next 2) 翻页→提取→合并
     * 参数：max_pages（最大页数）, extract_selector（数据容器选择器）
     */
    const val JS_PAGINATE_EXTRACT = """
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

    /**
     * 无限滚动提取：自动滚动到底部，收集所有加载的内容。
     * 参数：max_scrolls（最大滚动次数，默认15）, scroll_selector（可选，滚动容器）
     */
    const val JS_INFINITE_SCROLL_EXTRACT = """
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

    /**
     * 快照增强标注：在快照 JSON 中标注 API 数据来源（哪些元素的数据来自哪个 API 请求）。
     * 通过 __rcb_api_calls 中的 URL 模式与页面 DOM 元素关联。
     */
    const val JS_SNAPSHOT_API_SOURCE = """
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

    // ═══════════════════════════════════════════════════════════════
    // 第二批：自动化与健壮性 JS 脚本
    // ═══════════════════════════════════════════════════════════════

    /**
     * 增强人类打字：逐字符输入，带随机间隔，偶尔模拟打错字+退格修正。
     * 参数：elementId, text, mistakeRate(0-1, 默认0.05)
     * 注意：此 JS 只做单字符追加，节奏控制在 Kotlin 层 delay 完成。
     */
    const val JS_HUMAN_TYPE_CHAR = """
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

    /** 模拟退格（删除最后一个字符），用于人类打字纠错。 */
    const val JS_HUMAN_BACKSPACE = """
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

    /** 触发 input/change 事件（人类打字结束后统一触发）。 */
    const val JS_HUMAN_FIRE_INPUT = """
    (function() {
      var id = arguments[0];
      var el = document.querySelector('[data-rcb-id="' + id + '"]');
      if (!el) return JSON.stringify({ok:false, reason:'NOT_FOUND'});
      el.dispatchEvent(new Event('input', {bubbles:true}));
      el.dispatchEvent(new Event('change', {bubbles:true}));
      return JSON.stringify({ok:true});
    })();
"""

    /**
     * safe_click 前置检查：返回元素的完整可交互状态。
     * 返回 {ok, reason, detail, visible, inViewport, clickable, overlapped, rect}
     */
    const val JS_SAFE_CLICK_CHECK = """
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

    /**
     * safe_click 后置验证：点击后检查页面是否发生了可感知的变化。
     * 对比点击前后的 DOM 指纹（正文长度、元素数、URL hash）。
     */
    const val JS_SAFE_CLICK_VERIFY = """
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

    /** 捕获点击前的页面指纹（用于 safe_click 后置验证）。 */
    const val JS_CAPTURE_BEFORE_CLICK = """
    (function() {
      return JSON.stringify({
        textLen: document.body ? document.body.innerText.length : 0,
        elCount: document.querySelectorAll('*').length,
        url: location.href,
        hash: location.hash
      });
    })();
"""

    /**
     * 滚动位置获取：返回当前滚动 Y 坐标和页面总高度。
     */
    const val JS_GET_SCROLL_POS = """
    (function() {
      return JSON.stringify({
        scrollY: window.scrollY,
        scrollHeight: document.body ? document.body.scrollHeight : 0,
        clientHeight: window.innerHeight
      });
    })();
"""

    // ═══════════════════════════════════════════════════════════════
    // 第三批：会话与闭环 JS 脚本
    // ═══════════════════════════════════════════════════════════════

    /**
     * 导出 localStorage + sessionStorage（用于会话保存/恢复）。
     * 返回 JSON: {localStorage: {k:v}, sessionStorage: {k:v}}
     */
    const val JS_STORAGE_DUMP = """
    (function() {
      var ls = {}, ss = {};
      try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); ls[k] = localStorage.getItem(k); } } catch(e) {}
      try { for (var j = 0; j < sessionStorage.length; j++) { var k2 = sessionStorage.key(j); ss[k2] = sessionStorage.getItem(k2); } } catch(e) {}
      return JSON.stringify({ localStorage: ls, sessionStorage: ss, url: location.href });
    })();
"""

    /**
     * 恢复 storage（从 JSON 字符串恢复 localStorage/sessionStorage）。
     * 参数：storageJson（JS_STORAGE_DUMP 输出的 JSON）
     */
    const val JS_STORAGE_RESTORE = """
    (function() {
      var data = JSON.parse(arguments[0]);
      var restored = { ls: 0, ss: 0 };
      try {
        if (data.localStorage) {
          for (var k in data.localStorage) { localStorage.setItem(k, data.localStorage[k]); restored.ls++; }
        }
        if (data.sessionStorage) {
          for (var k2 in data.sessionStorage) { sessionStorage.setItem(k2, data.sessionStorage[k2]); restored.ss++; }
        }
      } catch(e) { return JSON.stringify({ok:false, error: e.message}); }
      return JSON.stringify({ok:true, restored: restored});
    })();
"""

    /**
     * 验证码检测：识别常见验证码类型并返回位置信息。
     * 检测：reCAPTCHA / hCaptcha / 极验 GeeTest / 滑块滑块 / 点选验证 / Cloudflare Turnstile
     * 返回 {detected: bool, types: [string], elements: [{type, x, y, w, h, text}]}
     */
    const val JS_CAPTCHA_DETECT = """
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

    /**
     * 权限审计：列出页面已请求的权限状态。
     * 检测：通知权限、地理位置、麦克风/摄像头（通过 permissions API）。
     */
    const val JS_PERMISSION_AUDIT = """
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

    /**
     * 资源拦截：通过注入 CSS 隐藏/移除指定类型资源，减少加载和渲染。
     * 参数：blockTypes（逗号分隔：image/css/font/media）
     * 注意：只能拦截已加载后的渲染显示，不能阻止网络请求（WebView 限制）。
     */
    const val JS_BLOCK_RESOURCE = """
    (function() {
      var types = arguments[0].split(',');
      var style = document.createElement('style');
      var rules = [];
      if (types.indexOf('image') >= 0) {
        rules.push('img { display: none !important; }');
        rules.push('*[style*="background-image"] { background-image: none !important; }');
      }
      if (types.indexOf('css') >= 0) {
        // CSS 拦截效果有限，只能隐藏部分装饰元素
      }
      if (types.indexOf('font') >= 0) {
        rules.push('* { font-family: monospace !important; }');
      }
      if (types.indexOf('media') >= 0) {
        rules.push('video, audio { display: none !important; }');
        rules.push('iframe[src*="youtube"], iframe[src*="video"] { display: none !important; }');
      }
      style.textContent = rules.join('\\n');
      document.head.appendChild(style);
      return JSON.stringify({ ok: true, blocked: types, rules_count: rules.length });
    })();
"""

    /**
     * 全页滚动截图辅助：逐段滚动页面并返回总高度信息。
     * Kotlin 层通过多次截图拼接实现全页截图。
     */
    const val JS_FULL_PAGE_INFO = """
    (function() {
      var body = document.body;
      var html = document.documentElement;
      return JSON.stringify({
        scrollHeight: Math.max(body.scrollHeight, html.scrollHeight),
        scrollWidth: Math.max(body.scrollWidth, html.scrollWidth),
        clientHeight: window.innerHeight,
        clientWidth: window.innerWidth,
        currentScrollY: window.scrollY
      });
    })();
"""

    // ═══════════════════════════════════════════════════════════════
    // 第四批：SPA 专项 JS 脚本
    // ═══════════════════════════════════════════════════════════════

    /**
     * 检测前端框架及版本。
     * 检测：React / Vue 2 / Vue 3 / Angular / Svelte / Next.js / Nuxt / Remix
     * 返回 {framework, version, signals, mount_point}
     */
    const val JS_DETECT_FRAMEWORK = """
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

    /**
     * SSR 数据提取：自动检测 __NEXT_DATA__ / __NUXT__ / __INITIAL_STATE__ / window.__APOLLO_STATE__
     * 默认截断到 1MB。
     */
    const val JS_EXTRACT_SSR_DATA = """
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

    /**
     * 框架内部状态访问：提取 React Fiber / Vue 实例 / Redux / Pinia / Zustand 状态。
     * 无法识别时优雅降级返回 NOT_SUPPORTED。
     */
    const val JS_EXTRACT_FRAMEWORK_STATE = """
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

    /**
     * 虚拟列表检测：多信号判断是否为虚拟列表。
     * 信号：DOM节点数 vs 滚动高度不匹配 / 行高一致 / 虚拟列表库 class
     */
    const val JS_DETECT_VIRTUAL_LIST = """
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

    /**
     * SPA 路由导航：自动检测前端路由并调用对应 API。
     * 参数：url（目标 URL 路径）
     * 策略：React Router (history.push) / Vue Router (router.push) / 通用 (history.pushState + popstate)
     */
    const val JS_SPA_NAVIGATE = """
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

    /**
     * API 分页参数识别与自动遍历。
     * 分析最近捕获的 API 调用，识别分页参数（page/offset/limit/cursor），
     * 自动重放后续分页并合并结果。
     * 参数：maxPages（最大页数），urlPattern（要匹配的 URL 子串）
     */
    const val JS_API_PAGINATE = """
    (function() {
      var maxPages = Math.min(parseInt(arguments[0]) || 3, 10);
      var urlPattern = arguments[1] || '';
      var calls = window.__rcb_api_calls || [];

      // 找到基础请求
      var baseCall = null;
      for (var i = calls.length - 1; i >= 0; i--) {
        if (urlPattern === '' || calls[i].url.indexOf(urlPattern) >= 0) {
          baseCall = calls[i];
          break;
        }
      }
      if (!baseCall) return JSON.stringify({ ok: false, reason: 'NO_MATCHING_API', message: '未找到匹配的 API 请求' });

      // 识别分页参数
      var url = baseCall.url;
      var pageParam = null;
      var pageValue = null;
      var paramNames = ['page', 'offset', 'cursor', 'skip', 'after', 'start'];
      for (var p = 0; p < paramNames.length; p++) {
        var regex = new RegExp('[?&]' + paramNames[p] + '=([^&]*)');
        var match = url.match(regex);
        if (match) { pageParam = paramNames[p]; pageValue = match[1]; break; }
      }
      // 也检查请求体中的分页参数
      if (!pageParam && baseCall.request_body) {
        for (var q = 0; q < paramNames.length; q++) {
          var bodyRegex = new RegExp('"' + paramNames[q] + '"\\s*:\\s*"?([^",}]*)');
          var bodyMatch = baseCall.request_body.match(bodyRegex);
          if (bodyMatch) { pageParam = paramNames[q]; pageValue = bodyMatch[1]; break; }
        }
      }
      var paginationInfo = {
        base_url: url.slice(0, 200),
        page_param: pageParam || 'none',
        current_value: pageValue || '',
        method: baseCall.method
      };

      // 自动遍历分页（仅返回信息，实际重放由 Kotlin 层控制）
      var pages = [];
      if (pageParam) {
        var startVal = parseInt(pageValue) || 0;
        for (var pg = 1; pg <= maxPages; pg++) {
          pages.push({ page: pg, value: startVal + pg, note: '可通过 replay_api 修改参数后重放' });
        }
      }
      return JSON.stringify({
        ok: true,
        pagination: paginationInfo,
        suggested_pages: pages,
        base_call: { id: baseCall.id, url: baseCall.url.slice(0, 200), method: baseCall.method }
      });
    })();
"""

}
