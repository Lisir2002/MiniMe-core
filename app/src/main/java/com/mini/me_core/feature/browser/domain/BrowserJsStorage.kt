package com.mini.me_core.feature.browser.domain

/**
 * 浏览器 JavaScript 脚本常量，按职责拆分。
 */

internal const val JS_STORAGE_DUMP = """
(function() {
  var ls = {}, ss = {};
  try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); ls[k] = localStorage.getItem(k); } } catch(e) {}
  try { for (var j = 0; j < sessionStorage.length; j++) { var k2 = sessionStorage.key(j); ss[k2] = sessionStorage.getItem(k2); } } catch(e) {}
  return JSON.stringify({ localStorage: ls, sessionStorage: ss, url: location.href });
})();
"""


internal const val JS_STORAGE_RESTORE = """
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


