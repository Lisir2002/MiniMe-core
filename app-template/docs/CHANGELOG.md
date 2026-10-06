# MiniMe Template 更新日志

## v0.0.0.7

**新功能**

- **权限管理**：新增权限检查、运行时申请、批量申请、打开应用设置、获取所有权限状态等 6 个 Bridge 方法，支持 JS 侧异步申请危险权限。
- **全量权限声明**：AndroidManifest 声明网络、硬件、蓝牙、NFC、位置、存储、联系人、日历、短信、电话、传感器、通知、系统等全量权限，覆盖已知可获取的所有权限。

**改进**

- **UiBridge绑定**：MainActivity 初始化时调用 uiBridge.attach(webView, window)，状态栏/导航栏/沉浸模式/权限申请等功能正式可用。

**修复**

- **Toast显示**：修复 ui.toast 在 IO 线程调用导致的 `Can't toast on a thread that has not called Looper.prepare()`，切换主线程显示。
- **震动权限**：AndroidManifest 缺少 VIBRATE 权限导致 ui.vibrate 调用失败，已补全权限声明。

## v0.0.0.6

**修复**

- **Bridge栈溢出**：修复 modules 的 by lazy 初始化中调用 registerModule 导致无限递归 StackOverflowError，全部 Bridge 方法无法调用。
- **异常透传**：call 方法最外层添加 try-catch，异常时返回具体堆栈信息，不再是通用错误提示。

**安全**

- **混淆加固**：加强 proguard 规则，保留 AppBridge、BridgeModule 子类、JavascriptInterface 注解、kotlinx.serialization，防止 R8 移除 Bridge 调用链。

## v0.0.0.5

**改进**

- **页面美化**：欢迎页面动态渐变背景、毛玻璃卡片、按钮点击波纹动画、功能状态标签，视觉与交互全面升级。

**修复**

- **Bridge调用崩溃**：修复后台线程调用 webView.url 导致的全部 Bridge 方法调用异常，改用 URL 缓存。
- **版本号显示**：欢迎页面底部版本号改为动态读取 BuildConfig，不再硬编码 v1.0.0。
- **日志可复制**：测试结果输出框添加文本选择和一键复制按钮，错误日志可直接复制。

## v0.0.0.4

**修复**

- **启动崩溃**：修复启动屏 View 被重复添加导致的 `The specified child already has a parent` 闪退。

## v0.0.0.3

**改进**

- **日志路径**：崩溃日志改存外部存储公共目录，用户可直接通过文件管理器查看。

## v0.0.0.2

**新功能**

- **崩溃日志**：新增 CrashHandler 全局捕获未处理异常，写入应用目录便于排查。

**改进**

- **依赖精简**：移除 AppCompat 依赖，Activity 改用平台原生基类，APK 体积进一步缩小。

**修复**

- **启动崩溃**：修复 Activity 继承 AppCompatActivity 但主题非 AppCompat 导致的启动闪退。
- **参数解析**：修复 Bridge 调用时 args 非对象类型导致的解析崩溃。

## v0.0.0.1

**新功能**

- **模版引擎**：WebViewAssetLoader 虚拟 HTTPS 域名加载本地 assets，替代 file:// 协议。
- **全量桥接**：9 大模块 JS Bridge，来源校验加能力分级，异步 Promise 调用。
- **路由支持**：SPA fallback 自动处理 history 模式，Vue React 项目开箱即用。
- **配置驱动**：assets/config.json 统一管理应用标识、权限、外观、启动屏配置。

**改进**

- **启动体验**：启动屏加载完成后淡出过渡，避免白屏闪烁。
- **文件安全**：文件操作仅允许应用私有目录，路径越界自动拦截。
- **事件系统**：生命周期、网络变化、返回键等原生事件可订阅。

**安全**

- **安全加固**：禁用 file 访问、移除危险接口、安全浏览、默认禁止明文 HTTP。

**已知问题**

- 媒体模块（相机、相册、录音、扫码）方法预留，后续版本逐步实现。
- 位置模块（GPS 定位）方法预留，后续版本逐步实现。
- 传感器模块（加速度、陀螺仪等）方法预留，后续版本逐步实现。
- 连接模块（蓝牙、NFC、WiFi）方法预留，后续版本逐步实现。
