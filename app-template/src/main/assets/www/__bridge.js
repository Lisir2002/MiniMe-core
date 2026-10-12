/**
 * AppBridge JS 侧封装
 *
 * 提供 Promise 化的 Bridge 调用接口和事件订阅机制
 *
 * 用法：
 *   const result = await AppBridge.ui.toast({ message: "Hello" });
 *   AppBridge.on("app:pause", () => { console.log("app paused"); });
 */
(function () {
    'use strict';

    // 仅允许顶层页面使用 Bridge（iframe 禁用）
    if (window.top !== window.self) {
        console.warn('[AppBridge] Bridge 仅允许在顶层页面使用，iframe 中已禁用');
        window.AppBridge = {
            call: function () { return Promise.reject(new Error('Bridge 仅允许在顶层页面使用')); },
            on: function () {},
            off: function () {}
        };
        return;
    }

    var callbackMap = {};
    var callbackIdCounter = 0;
    var eventListeners = {};
    var nativeBridge = window.AppBridgeNative;

    /**
     * 生成唯一回调 ID
     */
    function generateCallbackId() {
        callbackIdCounter++;
        return 'cb_' + Date.now() + '_' + callbackIdCounter;
    }

    /**
     * 调用原生 Bridge 方法
     * @param {string} module - 模块名（ui/device/file/media/...）
     * @param {string} method - 方法名
     * @param {object} args - 参数对象
     * @returns {Promise} 调用结果
     */
    function call(module, method, args) {
        return new Promise(function (resolve, reject) {
            if (!nativeBridge) {
                reject(new Error('原生 Bridge 不可用'));
                return;
            }

            var callbackId = generateCallbackId();
            var entry = { resolve: resolve, reject: reject, timer: null };
            callbackMap[callbackId] = entry;

            var request = JSON.stringify({
                module: module,
                method: method,
                args: args || {},
                callbackId: callbackId
            });

            try {
                nativeBridge.call(request);
            } catch (e) {
                delete callbackMap[callbackId];
                reject(e);
                return;
            }

            // 超时保护：30 秒未响应则 reject；响应回来后会在回调入口 clearTimeout，避免悬挂定时器
            entry.timer = setTimeout(function () {
                if (callbackMap[callbackId]) {
                    delete callbackMap[callbackId];
                    reject(new Error('Bridge 调用超时: ' + module + '.' + method));
                }
            }, 30000);
        });
    }

    /**
     * 原生回调入口（由原生侧调用）
     */
    window.__bridge_callback__ = function (callbackId, result) {
        var callback = callbackMap[callbackId];
        if (!callback) return;
        if (callback.timer) clearTimeout(callback.timer);
        delete callbackMap[callbackId];

        if (result.success) {
            callback.resolve(result.data || {});
        } else {
            var error = new Error(result.error || '未知错误');
            error.code = result.errorCode || 'UNKNOWN_ERROR';
            callback.reject(error);
        }
    };

    /**
     * 原生事件入口（由原生侧调用）
     */
    window.__bridge_event__ = function (eventName, data) {
        var listeners = eventListeners[eventName];
        if (!listeners) return;
        listeners.forEach(function (listener) {
            try {
                listener(data || {});
            } catch (e) {
                console.error('[AppBridge] 事件监听器异常:', e);
            }
        });
    };

    /**
     * 订阅事件
     * @param {string} eventName - 事件名
     * @param {function} listener - 监听器函数
     */
    function on(eventName, listener) {
        if (!eventListeners[eventName]) {
            eventListeners[eventName] = [];
        }
        eventListeners[eventName].push(listener);
    }

    /**
     * 取消订阅事件
     * @param {string} eventName - 事件名
     * @param {function} listener - 要移除的监听器函数
     */
    function off(eventName, listener) {
        var listeners = eventListeners[eventName];
        if (!listeners) return;
        var index = listeners.indexOf(listener);
        if (index > -1) {
            listeners.splice(index, 1);
        }
    }

    // 构建模块代理对象，支持 AppBridge.ui.toast(...) 语法
    var modules = ['ui', 'device', 'file', 'media', 'location', 'sensor', 'connect', 'data', 'event'];
    var AppBridge = {
        call: call,
        on: on,
        off: off,
        version: '1.0.0'
    };

    modules.forEach(function (moduleName) {
        AppBridge[moduleName] = new Proxy({}, {
            get: function (target, method) {
                if (typeof method === 'string') {
                    return function (args) {
                        return call(moduleName, method, args);
                    };
                }
                return undefined;
            }
        });
    });

    window.AppBridge = AppBridge;
    console.log('[AppBridge] Bridge 已初始化，版本 ' + AppBridge.version);

    // 全局 JS 异常上报：把未捕获错误与 Promise rejection 送原生记录，便于定位线上白屏
    function reportToNative(report) {
        try {
            if (nativeBridge && typeof nativeBridge.reportJsError === 'function') {
                nativeBridge.reportJsError(
                    report.message, report.source, report.line || 0,
                    report.column || 0, report.stack || null
                );
            }
        } catch (_) { /* 上报本身不可再抛错 */ }
    }

    window.addEventListener('error', function (event) {
        var msg = event.message || 'Error';
        if (event.error && event.error.stack) msg = event.error.message || msg;
        reportToNative({
            message: msg,
            source: event.filename || '',
            line: event.lineno || 0,
            column: event.colno || 0,
            stack: event.error ? event.error.stack : null
        });
    });

    window.addEventListener('unhandledrejection', function (event) {
        var reason = event.reason;
        reportToNative({
            message: 'Unhandled Promise rejection: ' +
                (reason && reason.message ? reason.message : String(reason)),
            source: '', line: 0, column: 0,
            stack: reason && reason.stack ? reason.stack : null
        });
    });
})();
