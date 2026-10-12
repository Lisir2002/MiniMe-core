# 模板容器（app-template）全方位性能与稳定性优化方案

适用对象：打包器内置的本地 H5 容器 `app-template`（产物 `template-base.apk`），通过虚拟 HTTPS 域名
`https://appassets.androidplatform.net/` 经 `WebViewAssetLoader` 加载 `assets/www/`，JS Bridge 原生交互，
`assets/config.json` 驱动。兼容 minSdk 26 / targetSdk 34（Android 8~15）。

本方案先做中英文业界调研（Android 官方文档、得物/头条/百度/LOFTER 离线包实践、腾讯云 JSBridge 基准、
Cordova/Capacitor 容器做法、华为离线加载对比），再逐项对照本容器真实代码评估后给出。

---

## 0. 启动延迟约 5 秒的根因（证据链）

现象：打包出的 APK 冷启动停在纯紫屏（`#6750A4`）约 5 秒才进页面。

证据：
1. 主题 `Theme.AppTemplate` 的 `windowBackground=@color/splash_background(#6750A4)`；MainActivity 又叠加同色
   `splashView`。冷启动在 `onCreate/setContentView` 之前系统即画紫色 windowBackground。
2. `splashView` **只在 `WebViewClient.onPageFinished` 才淡出**（原代码无任何定时兜底，`SplashConfig.duration/autoHide`
   声明了却未被原生使用）。
3. 原 `onCreate` 顺序：`new WebView(this)`（L56）等重操作全部在 `setContentView`（L141）**之前**同步执行，
   即首帧绘制前主线程被 WebView 首次初始化占满。
4. 反编译实际 `template-base.apk` 的 classes.dex 核对：`configureWebViewSettings` 内确有
   `setSafeBrowsingEnabled(true)`，与源码一致；MainActivity 无 `postDelayed/Thread.sleep`（dex 内 postDelayed
   属 kotlinx.coroutines 内部）。
5. `index.html` 无任何外部 http(s)/CDN 资源（grep 为 0），只引用本地 `__bridge.js`；故延迟不是外链网络超时，
   而是 **WebView/Chromium 首次冷启动本身**。

根因结论：
- **主因（确定性、可优化）**：WebView 首次初始化是官方明确的"非常繁重"过程，默认在**主线程隐式整块执行**
  （加载系统 WebView provider、浏览器/渲染进程、网络栈、GPU/合成、Safe Browsing 数据库）。本容器把它放在
  `setContentView` 之前、且全部压在 splash 关键路径上，无并行、无兜底。
- **放大因素（中国大陆网络尤为明显）**：`setSafeBrowsingEnabled(true)` 首启需连 Google 初始化/更新安全浏览数据，
  网络不可达时出现数秒级等待，叠加在首帧之前。实测约 5 秒与该超时量级吻合。
- 已排除：config.json 仅读极小文件、`TemplateApp.onCreate` 只装崩溃处理器（crashDir 为 lazy）、Bridge 构造无 IO/网络、
  页面无外链。

参考：
- 官方《优化 WebView 启动》https://developer.android.google.cn/develop/ui/views/layout/webapps/optimize-webview-startup
- 官方《处理 WebView 终止》https://developer.android.google.cn/develop/ui/views/layout/webapps/handle-termination
- 官方《使用 JavaScript 桥接访问原生 API》https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge

---

## 1. 采用的优化项清单（按维度）

收益评级：★★★ 直接显著 / ★★ 中等 / ★ 稳健性或边际。标注成本与风险。

### 维度 1+2：启动与内核（核心）

| 编号 | 优化项 | 原理/收益 | 成本 | 风险 |
|---|---|---|---|---|
| A1 | 升级 androidx.webkit 1.11.0→1.16.0，`Application.onCreate` 调 `WebViewCompat.startUpWebView(..., shouldRunUiThreadStartUpTasks(false))` | 官方 API，把浏览器进程/profile 等繁重启动负载移到后台线程，与进程启动并行；关键路径 WebView 官方推荐做法 | 低 | 低（旧内核自动降级，try/catch 兜底）★★★ |
| A2 | `Application.onCreate` 预热 Safe Browsing（`WebView.startSafeBrowsing`，API27+ 守卫） | 把安全浏览初始化/网络从"首页加载串行阻塞"提前到进程启动并行；WebView≥122 为空操作即时回调 | 低 | 低（保留安全浏览能力）★★ |
| A3 | 重排 MainActivity 启动时序：先 `setContentView(仅 splash)` 并在首帧绘制后（post/Choreographer）再创建 WebView、loadUrl | 首帧立即上屏，避免重初始化全部挡在第一帧前；后台预热与首帧并行 | 中 | 中（需保证视图挂载后再 load）★★★ |
| A4 | splash 消失改以**首帧绘制**为准：`WebView.postVisualStateCallback`/`WebViewCompat.addVisualStateCallback`，onPageFinished 兜底；再加最长看门狗 | 比 onPageFinished 更贴近"真正可见"；看门狗保证任何异常都不会永久紫屏 | 中 | 低 ★★★ |
| A5 | 纯本地应用（未声明 INTERNET）`setBlockNetworkLoads(true)` | 本地包根本不发网络请求，直接消除网络栈/Safe Browsing 等待与外联面；离线 H5 常见形态 | 低 | 低（按权限派生，联网包不受影响）★★★ |
| A6 | 接通被忽略的 `config.webview.cacheMode`；`setJavaScriptCanOpenWindowsAutomatically(false)`、`setSaveFormData(false)` | 配置真正生效；减少非必要能力与隐私残留 | 低 | 低 ★★ |

明确**不做**：
- 不强制 `setLayerType(HARDWARE)`：Manifest 已开硬件加速，强制硬件层反而增内存，官方默认最优。
- 不引入 X5/Tencent 内核：体积与合规成本高、需下载分发、与系统 WebView 行为分叉，对纯本地容器收益不成立。
- 不另起 `android:process` 独立进程：Android 8+ 渲染器本就运行在隔离沙盒进程；把 Activity 放独立进程会让全部 Bridge
  走跨进程 IPC，复杂度与延迟反升。单 Activity 容器不值得。
- 不做离屏 WebView 实例池：本应用就是一个全屏 WebView，无多实例复用场景。

### 维度 3：渲染与交互
- 现代 WebView 在 `viewport user-scalable=no` 下已去除 300ms 点击延迟；demo 页已有 `-webkit-tap-highlight-color:transparent`。
- 真实业务页面由打包用户提供，容器层不强行改写其 CSS；仅在文档中建议 `touch-action: manipulation`、合成层/`will-change`
  节制、长列表虚拟化、图片懒加载。**不改业务 DOM**，避免副作用。★

### 维度 4：JS Bridge
| 编号 | 优化项 | 说明 | 风险 |
|---|---|---|---|
| B1 | 保留 `addJavascriptInterface` 单入口 `call`，不迁移 WebMessageListener | 现设计已具来源校验、模块开关、方法存在性校验、iframe 禁用、@JavascriptInterface 单方法、IO 异步；迁移会破坏 `__bridge.js` 协议，收益（基准约 0.45ms/次）对本低频交互场景不抵风险 | — |
| B2 | 原生→JS 字符串参数改用 `JSONObject.quote()` 转义（callbackId/eventName） | 消除引号/换行导致的脚本断裂与注入面 | 低 ★★ |
| B3 | Bridge 调用耗时/成败埋点（debuggable 开关） | 可观测 module.method 耗时与错误率 | 低 ★ |
| B4 | `__bridge.js` 响应回来后 `clearTimeout`，避免每调用挂一个 30s 定时器 | 降低高频调用下定时器/闭包滞留 | 低 ★ |
| B5 | 新增 JS 全局异常上报 `window.onerror`/`unhandledrejection` → 原生 `reportJsError` | 接入崩溃/错误日志，白屏可追溯 | 低 ★★ |

不做批量/节流合并：Bridge 调用由用户操作触发、低频；`@JavascriptInterface` 在后台线程被调用且原生已异步派发，
无串行阻塞 JS 的问题。大数据传递建议走文件/分块，属业务侧约定。

### 维度 5+9+12：资源加载、包体、构建产物
| 编号 | 优化项 | 说明 | 收益/代价 |
|---|---|---|---|
| C1 | 模板构建 `androidResources.noCompress` 对 `html/htm/js/mjs/css/json/svg/wasm/ttf/otf/woff/woff2` 不压缩 | 未压缩条目可被 AssetManager 直接读取/mmap，省运行时 inflate；WebView 首读更快（华为对比：内存/直读 74ms vs 1312ms，主要针对网络，但本地同样省 inflate） | www 仅约 45KB，体积代价可忽略 ★★ |
| C2 | 打包器重打包 `ApkModifier.repackApk` 对 `assets/www/**` 与 `assets/config.json` 同步 STORED，并继续 zipalign | 保证用户替换后的网页资源在最终 APK 也不压缩；STORED 依赖后续 zipalign 4 字节对齐（流程已有） | ★★ |
| C3 | resources.arsc、位图继续 STORED（现状已如此） | 保持可直接映射 | — |
| 不做 | Service Worker 离线缓存 | 内容已在本地 assets，SW 对本地虚拟域收益小且增加缓存一致性复杂度 | — |
| 不做 | gzip/brotli 预压缩 + 运行时解压 | 需自管解压与 Content-Encoding，复杂且与 C1 直读相悖；www 极小无必要 | — |

### 维度 6：内存
- D1 `onDestroy` 先把 WebView 从父容器移除再 `destroy()`，并清空引用（官方防泄漏姿势）。★★
- 渲染器在 Android 8+ 已独立沙盒进程；Bridge 长生命周期单例 `ConfigManager` 使用 applicationContext，不持 Activity。
- 不做静态/单例 WebView、不做复用池（见上）。

### 维度 7：稳定性
| 编号 | 优化项 | 说明 |
|---|---|---|
| E1 | `onRenderProcessGone`：移除并销毁崩溃 WebView，重建并重新加载入口页（官方恢复流程，不复用旧实例）★★★ |
| E2 | `onReceivedError` 主框架错误：记录并保证撤下 splash，避免错误页时永久紫屏 ★★ |
| E3 | `onReceivedSslError` 显式 `handler.cancel()`（默认即取消，显式防绕过）并记录 ★ |
| E4 | 配置损坏/缺失回退默认值（`AppConfig.fromJson`/`loadConfig` 已 try/catch，保留并补日志）★ |
| E5 | JS 异常上报（B5）接入错误日志 ★★ |

### 维度 8：安全（在现有强基线之上增量）
现状已具备：虚拟 HTTPS 域（无 file://）、`allowFileAccess/allowContentAccess/allowFileAccessFromFileURLs/
allowUniversalAccessFromFileURLs=false`、`MIXED_CONTENT_NEVER_ALLOW`、明文流量禁止、移除 searchBoxJavaBridge_ 等
危险接口、Bridge 来源校验 + iframe 禁用 + 能力分级。
增量：
- F1 离线包 `setBlockNetworkLoads(true)`（A5，同时是安全项）。
- F2 `setJavaScriptCanOpenWindowsAutomatically(false)`；SSL 错误不继续（E3）。
- F3 **CSP 内容安全策略**：在 `shouldInterceptRequest` 对 HTML 响应注入 Content-Security-Policy 头（无需改用户 HTML）。
  因内置页与常见打包页使用内联 `<style>/<script>`（实测 index.html 内联、且无 eval/Function），默认策略允许
  `'self' 'unsafe-inline'`、**不含 `unsafe-eval`**，禁止 `object-src`、限制 frame/base；联网能力随权限。提供
  config 开关可关闭/自定义。★★

### 维度 10：配置驱动
- `WebViewConfig.cacheMode` 真正生效（A6）；新增可选 `csp`（字符串，空=用默认策略，"off"=关闭）、
  离线断网由 `permissions.internet` 派生。`ignoreUnknownKeys=true`，旧 config 与打包器无需强制升级。

### 维度 11：可观测性（量化埋点）
- 新增 `StartUpTracer`（单对象，`android.util.Log`，tag `TplStart`），记录阶段时间戳并在首帧/结束输出汇总：
  进程启动、startUpWebView 发起/回调、Activity onCreate、splash 首帧、WebView 构造前后、loadUrl、
  onPageStarted、首帧回调、onPageFinished、splash 隐藏、render gone、主框架错误。
- Bridge 埋点 tag `TplBridge`（受 config.debuggable 控制）。
- 查看方式：`adb logcat -s TplStart TplBridge`（详见文末）。

---

## 2. 修改文件清单（预期）

模板模块（app-template，需经 template-v* 由 CI 重新生成 release 模板）：
- `build.gradle.kts`：webkit 1.16.0；androidResources.noCompress 网页文本/字体/wasm。
- `TemplateApp.kt`：startUpWebView 后台预热 + Safe Browsing 预热 + 进程起点埋点。
- `MainActivity.kt`：启动时序重排、首帧回调撤 splash、看门狗、WebSettings 调优/断网/缓存、
  onRenderProcessGone 重建、主框架错误/SSL、CSP 注入、全链路埋点、onDestroy 规范清理。
- `AppConfig.kt`：新增可选 csp 等字段与 cacheMode 映射。
- `bridge/AppBridge.kt`：字符串转义、耗时埋点、reportJsError 入口。
- `assets/www/__bridge.js`：clearTimeout、全局 JS 异常上报。
- 新增 `StartUpTracer.kt`。

打包器主模块（:app，随主应用版本生效）：
- `feature/packager/domain/engine/ApkModifier.kt`：repack 对 assets/www 与 config.json 采用 STORED。

文档：
- 本文件 `app-template/docs/perf-optimization-plan.md`。

不改动：Bridge 9 个能力模块的方法实现、权限体系、图标替换链路、Manifest 权限集。

---

## 3. 兼容性与不破坏现有能力的约束
- minSdk 26：startUpWebView 由 androidx 封装按内核/系统版本降级；startSafeBrowsing 加 API27 守卫；
  postVisualStateCallback 用框架 API23+（WebView 自带），均在 minSdk 之上或有守卫。
- Bridge 协议（`AppBridgeNative.call` / `__bridge_callback__` / `__bridge_event__`）保持不变，仅新增
  `reportJsError`，旧页面无感知。
- config.json 新字段全部带默认值且 ignoreUnknownKeys；打包器现有注入逻辑不受影响。
- 联网类打包应用：A5 不触发（声明了 INTERNET），CSP connect-src 放行 https/wss，正常联网。

## 4. 模板 APK 重新生成条件（重要）
app-template 改动**不会**自动进入 `app/src/main/assets/packager/template-base.apk`（该文件是手工入库的 release 产物）。
落地发版需：
1. 合入源码后打 `template-v*` tag → CI（`.github/workflows/template-release.yml`）用官方密钥执行
   `:app-template:assembleRelease` 并发布模板 APK；
2. 下载该 release 产物，替换 `app/src/main/assets/packager/template-base.apk` 并提交；
3. 再随主应用版本发布。
本地无官方密钥（app/minime.jks、app/keystore.properties）无法产出 release 模板，仅可用
`:app-template:assembleDebug` 做编译/结构验证；最终用户 APK 由打包器用 default.keystore 重签，模板自身签名会被剥离，
但 release 模板带 R8/资源收缩，仍应以 CI release 产物为准。

## 5. 性能埋点查看方式
```
adb logcat -s TplStart TplBridge
# 关键行：TplStart: 阶段=... 累计=Xms；结束汇总含 WebView构造/首帧/可交互 各段耗时
```
优化前后对比同一设备、同一安装形态（建议清数据后冷启动 3 次取中位数）。

---

## 6. 可配置启动动画与零白屏衔接（新增能力）

目标：用户可在打包器自定义冷启动动画（启动图、时长 500~5000ms、过渡 fade/zoom/slide/dissolve、
背景色、点击跳过），且从系统冷启动第一帧到主页全程不出现白屏。

### 6.1 三层像素级衔接
1. **系统冷启动第一帧**：主题 `windowBackground=@drawable/splash_bg`（layer-list：兜底色
   `@color/splash_background` + 位图 `@drawable/splash_img`，gravity=fill）。打包器构建期把
   “背景色 + 用户启动图”合成为一张全幅位图，按**同扩展名覆盖**编译产物里的 splash_img.png，
   不改动已编译主题与 resources.arsc 结构（与图标“按扩展名解析资源”的教训一致）。
2. **WebView 首帧前**：MainActivity 仅挂载背景透明的 WebView（`setBackgroundColor(TRANSPARENT)`），
   windowBackground 自然透出，无白屏。
3. **HTML 启动页**：先加载 `assets/www/splash.html`，其首帧用同一张图、`background-size:cover`、
   同背景色，与 windowBackground 对齐；按 config 播放过渡、`fetch` 预载入口页与 `__bridge.js`，
   到时或点击跳过后 `location.replace` 到入口页。进入入口页前原生把 WebView 背景设为启动背景色，
   切换间隙不露白。原生看门狗（动画时长+3.5s，封顶 12s）在启动页卡死时强制进入入口页。

### 6.2 配置契约（assets/config.json 的 splash 段）
`enabled / duration(ms) / autoHide / backgroundColor / image(文件名，自定义图为 splash.png) /
transition(fade|zoom|slide|dissolve) / skipOnTap`。模板 `AppConfig.SplashConfig` 新增
`image / transition / skipOnTap`，全部带默认值、`ignoreUnknownKeys`，旧配置与旧模板前向兼容。

### 6.3 资源与文件
- 模板：`res/values/themes.xml`（windowBackground 指向 splash_bg）、`res/drawable/splash_bg.xml`、
  默认位图 `res/drawable-nodpi/splash_img.png`（编译后为 `res/drawable-nodpi-v4/splash_img.png`）、
  `assets/www/splash.html`。
- 打包器（:app）：扩展 `SplashSettings(transition/backgroundColor/skipOnTap)`、概览页启动动画卡片、
  新增构建步骤“注入启动动画”与 `SplashInjector`（1080×1920 背景填充+centerCrop 合成、覆盖
  splash_img、写 assets/www/splash.png 与 splash.html、写 config.json splash 段）；旧模板缺资源时
  告警跳过、不中断构建。
- 不做 Lottie/视频、不做编辑器内预览；不强制 `setLayerType(HARDWARE)`（Manifest 已开硬件加速，
  全屏 WebView 强制硬件层反而增内存）。

### 6.4 端到端验证（debug 模板实测）
`:app-template:assembleDebug` 产物中：`res/drawable-nodpi-v4/splash_img.png`、`res/drawable/splash_bg.xml`、
`assets/www/splash.html` 均存在；`aapt2 dump resources` 显示主题 windowBackground=@drawable/splash_bg、
splash_bg 位图 src=@drawable/splash_img；assets/www 与 config.json 均为 Stored 不压缩。
release 模板仍需按第 4 节走 template-v* CI 重新生成后替换入库。
