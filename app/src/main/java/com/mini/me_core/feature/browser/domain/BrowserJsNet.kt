package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_CHANGE_OBSERVER = """
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


internal const val JS_NET_HOOK = """
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


internal const val JS_NET_HOOK_V2 = """
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


internal const val JS_REPLAY_API = """
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


internal const val JS_BLOCK_RESOURCE = """
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


internal const val JS_FULL_PAGE_INFO = """
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


internal const val JS_API_PAGINATE = """
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


