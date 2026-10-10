# MiniMe-core 架构文档

## 模块依赖图

仓库为单一 Gradle 工程，`settings.gradle.kts` 实际包含以下模块（第二阶段重构已删除空壳 `:core`，其两个工具类按同包并入 `:app`）：

```
                          ┌──────────────────────────────────────┐
                          │              :app (主应用)            │
                          │  namespace / applicationId:          │
                          │  com.mini.me_core                     │
                          │  ┌────────────────────────────────┐  │
                          │  │ feature/ (agent/browser/settings │  │
                          │  │  /terminal/git/packager/proxy    │  │
                          │  │  /backup/workspace ...)          │  │
                          │  │ core/ (container.api 中立端口、  │  │
                          │  │  theme、util、environment)       │  │
                          │  │ di/ (Hilt 绑定)                  │  │
                          │  └────────────────────────────────┘  │
                          └───────┬───────────────┬─────────────┘
                                  │               │
              ┌───────────────────┘               └────────────────────┐
              │                                                         │
   ┌────────────────────┐   ┌────────────────────┐   ┌─────────────────────────────┐
   │ :terminal-emulator  │   │  :terminal-view    │   │  :lint-checks               │
   │ (终端引擎库)         │   │ (终端渲染库)        │   │ (自定义 Lint 规则)           │
   └────────────────────┘   └────────────────────┘   └─────────────────────────────┘

   同工程内另产出两个独立 APK（不被 :app 依赖，各自独立签名与版本）：
   ┌────────────────────────────┐        ┌──────────────────────────────────────┐
   │ :logviewer-app            │        │ :app-template                       │
   │ com.mini.logs (离线日志查看)│        │ com.minime.template (WebView 壳模板)│
   └────────────────────────────┘        └──────────────────────────────────────┘
```

> 说明：`:terminal-emulator` / `:terminal-view` 源自 Termux 终端库，供主应用终端模块复用；`:lint-checks` 提供项目自定义静态检查规则。

## 数据流图

```
用户操作 → UI层 (Compose) → ViewModel → domain 端口接口 (Repository/Store)
                ↑                                        │
                │                                        ▼
                │                              data 实现 (XxxImpl)
                │                              @Binds 绑定，携带 IO/DB/网络
                │                                        │
                └──────── StateFlow ◄────────────── 数据库/网络/文件/容器
```

### 数据层架构

第二阶段重构后，数据访问统一遵循 **domain 留端口、data 留实现** 的约定：

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  (Compose Screens + ViewModels)                 │
└──────────────────────┬──────────────────────────┘
                       │ StateFlow / 调用
┌──────────────────────▼──────────────────────────┐
│        domain 层：端口接口（不依赖 Android）       │
│  McpConfigRepository / PermissionRulesRepository  │
│  SkillStateRepository / GitRepository           │
│  BuildStore / ProjectStore / 容器端口            │
└──────────────────────┬──────────────────────────┘
                       │ Hilt @Binds 注入具体实现
┌──────────────────────▼──────────────────────────┐
│      data 层：携带 IO/磁盘/DB 的 XxxImpl          │
│  (feature/*/data/repository 或 datalayer/...)    │
└──────────┬───────────────────┬──────────────────┘
           │                   │
┌──────────▼──────────┐ ┌──────▼──────────────┐
│   Local DataSource  │ │  Remote DataSource  │
│  (SQLDelight/DS)    │ │  (Retrofit/Ktor)    │
└──────────┬──────────┘ └─────────────────────┘
           │
┌──────────▼──────────┐
│    Database Layer   │
│  (SQLCipher 加密)   │
└─────────────────────┘
```

- domain 端口为普通 Kotlin 接口，不持有 Android IO/Context，便于测试与跨域复用。
- data 实现为 `@Singleton @Inject` 的 `XxxImpl`，通过独立 Hilt `@Module` 的 `@Binds` 绑定到端口：
  - agent 域：`AgentRepositoryBindModule`（`McpConfigRepositoryImpl` / `PermissionRulesRepositoryImpl` / `SkillStateRepositoryImpl`）。
  - git 域：`GitRepositoryBindModule`（`GitRepositoryImpl`）。
  - packager 域：`PackagerModule`（`BuildStoreImpl` / `ProjectStoreImpl`）。

## 中立端口层（core.container.api）

包 `com.mini.me_core.core.container.api` 是本轮（P3-12）新增的**中立端口层**，作用是解除 `terminal` / `workspace` 等域对 `feature/agent.domain.container` 具体实现的直接耦合。

- **模型与默认值**：`ContainerProfile` / `ContainerArch` / `RootfsSource` / `ContainerInitState` / `ConnectionState` / `RemoteConnectionConfig` / `ProotInvocation`；进度模型（`ProgressModels.kt`：`AggregateProgressState` / `InstallPhase` / `DownloadSlot` / `SlotStatus` / `LogLine` / `LogLineKind` / `FinishStats` / `ProgressSource` / `LogLineStore` / `RingBuffer`）、`SshModels.kt`、`GlobalInstallArchiveStore`、`friendlySshError`，以及常量 `DEFAULT_APK_MIRROR`。
- **端口接口**：`ContainerEnginePort` / `ContainerInstallerPort` / `SshConnectionPort`。
- **适配器与绑定**：实现适配器位于 `feature/agent/domain/container/ports/`（`LinuxContainerEngineAdapter` / `ContainerInstallerAdapter` / `RemoteSshConnectionAdapter`），内部委托到原 `@Singleton` 具体单例；由 `di/ContainerPortModule.kt` 以 `@Binds` 绑定到端口。消费方（terminal/workspace）注入端口即可拿到同一实例、同一作用域，不再直耦 agent 容器实现。

## 巨型类行为保持拆分

本轮对多个超大类做“薄门面 + 协作者”拆分：**保留原有 public 入口与对外行为不变**，仅把内部逻辑按职责下沉到协作者类。门面类继续作为唯一对外入口，协作者包路径如下。

| 门面类（public 入口不变） | 协作者（按职责） | 所在包 |
|---|---|---|
| `StatefulAgentWorkflow` | `ModelCallRouter` / `ResultSanitizer` / `EventTracer` / `ModeTransitionCoordinator` / `ToolBatchExecutor` | `feature/agent/domain/execution/workflow` |
| `AIAgentViewModel` | `ChatSessionController` / `AgentLoopRunner` / `PermissionApprovalHub` / `ComposerState` / `UsageStatsCollector` | `feature/agent/presentation` |
| `SettingsViewModel` | `SettingsUiStates` + `section/`（Provider / VisionCompaction / LogViewer / NormFlow / Theme / Container / Mcp / Permission） | `feature/settings/presentation` 及 `.../section` |
| `BrowserController` | `BrowserSecurityPolicy` / `BrowserDownloadControl` / `BrowserFingerprintControl`（`TabRegistry` / `JsAutomation` 有意保留在门面内） | `feature/browser/domain` |
| `BrowserAgentTool` | `BrowserToolEnvelope` / `BrowserToolNav` / `BrowserToolInspect` / `BrowserToolInteract` / `BrowserToolAntidetect` + `BrowserToolErrorClassifier` | `feature/agent/domain/execution/tool/browser` |
| `ClashProxyManager` | `MihomoProcessManager` / `ClashConfigSynthesizer` / `ClashSubscriptionService` / `ClashControlClient` / `ClashSecretStore` | `feature/proxy/domain` |
| `LinuxContainerEngine` | `RootfsManager` / `ProotInvocationBuilder` / `ContainerBindRegistry` / `RcbBridgeInjector` | `feature/agent/domain/container` |
| `AgentRepository` | 13 个按域拆分的 internal Store（`AgentSessionStore` / `AgentMessageStore` / `AgentCheckpointStore` / `AgentSkillStore` / `AgentPlanStore` / `AgentTodoStore` / `AgentTrajectoryStore` / `AgentJobScheduleStore` / `AgentModelConfigStore` / `AgentCompactionStore` / `AgentWakeStore` / `AgentZthStore` / `AgentTx`） | `datalayer/repository` |
| `BackupManagerImpl` | `BackupSnapshotStore` / `BackupExporter` / `BackupRestorer` | `feature/backup/data` |
| `ChatDrawer` | `ChatDrawerChrome` / `ChatDrawerSessionList` / `ChatDrawerWorkspace` | `feature/agent/presentation/component` |

## 模块说明

### :app（主应用，com.mini.me_core）

- **feature/agent**（Agent 核心）
  - `domain`：业务逻辑层，含 `provider/`（模型供应商适配）、`tool/`（工具系统）、`container/`（容器管理，PRoot Linux；其 `ports/` 为中立端口适配器）、`mcp/`、`session/`、`workflow/`、`zth/` 等。
  - `domain/container/ports`：`core.container.api` 三个端口的薄适配器实现。
  - `data/repository`：携带 IO 的 agent 仓库实现（`*Impl`）与 Hilt 绑定。
  - `presentation`：UI 层（Compose），含拆分后的会话/权限/输入/用量协作者与 `component/` 下的抽屉子组件。
- **feature/browser**（内置浏览器）：`domain` 的 `BrowserController` 已按安全/下载/指纹拆分；`presentation` 为浏览器 UI。
- **feature/settings**（设置）：`presentation` 的 `SettingsViewModel` 已按设置域拆为 `section/` 协作者。
- **feature/terminal**（终端）：`domain` 终端引擎，`presentation` 终端 UI；通过中立端口层消费容器能力。
- **feature/git / packager / proxy / backup / workspace**：各自遵循 domain 端口 + data 实现分层。
- **core/**：`core.container.api` 中立端口层、`theme`（令牌与组件）、`util`（日志/工具，含原 `:core` 并入类）、`environment`。
- **di/**：Hilt 模块，含 `ContainerPortModule`、各仓库 `@Binds` 绑定。

### :terminal-emulator / :terminal-view

源自 Termux 的终端引擎与渲染库，以 Gradle 库形式供主应用终端模块复用。

### :lint-checks

项目自定义 Lint 规则模块。

### :logviewer-app（独立 APK，com.mini.logs）

- **定位**：离线、只读的日志/崩溃查看器，读取主应用写出的聚合日志并在本地浏览、统计、导出，**不上传**。
- **权限事实**（以 `logviewer-app/src/main/AndroidManifest.xml` 为准）：仅声明 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`（`targetSdk=28` 直接访问公共外部存储），**未声明 `INTERNET`**；仅导出一个 `androidx.core.content.FileProvider`（authority `${applicationId}.fileprovider`，`exported=false`）用于本地分享导出。无网络能力。
- **实现事实**（`com.mini.logs.*`）：构建时把主应用 `core/util` 日志类（`FileLogger`/`AILogger`/`LogLineParser`/`LogFiles`/`LogLevelController`/`DiagnosticCleanup` 等）与主题令牌通过 `stageReferencedSources` 暂存进 `generated/source/referenced` 复用，零改动主应用源码；UI 分 `logs/`（列表、筛选、尾随）、`crash/`（崩溃聚合与详情）、`stats/`（趋势图表）、`settings/`。

### :app-template（独立 APK，com.minime.template）

- **定位**：WebView 宿主壳模板，是主应用「应用打包器」（`feature/packager`）把 HTML 项目打包成 APK 时内置并复用的模板产物；通过 JS Bridge 向本地网页暴露原生能力。
- **网络事实**（以 Manifest 为准）：声明了 `INTERNET` / `ACCESS_NETWORK_STATE` 及网络/Wi-Fi 相关权限，并按 `config.json` 声明全量权限集（硬件、蓝牙、NFC、位置、存储、联系人/日历/短信、电话、传感器、通知等）。因此它**确实具备网络能力**，并非离线应用。
- **实现事实**（`com.minime.template.*`）：`MainActivity` 用 `WebViewAssetLoader` 以虚拟 HTTPS 域 `appassets.androidplatform.net` 加载本地 `assets/www`（替代 `file://`），并做安全加固（禁 file://、关明文 HTTP、SPA history fallback）；JS 桥总入口 `bridge/AppBridge` 采用来源校验 + 能力分级 + 异步回调，各 `BridgeModule` 子类（`UiBridge`/`DeviceBridge`/`FileBridge`/`ConnectBridge`/`DataBridge`/`LocationBridge`/`MediaBridge`/`SensorBridge`/`EventBridge`）按 `config.json` 配置开关启用。与主应用共用官方签名密钥（`app/minime.jks`）独立签名发布。

## 关键技术栈

| 领域 | 技术 |
|------|------|
| UI | Jetpack Compose + Material3 |
| 依赖注入 | Hilt（domain 端口 + data 实现 `@Binds`） |
| 异步 | Kotlin Coroutines + Flow |
| 网络 | Retrofit + OkHttp + Ktor |
| 数据库 | SQLDelight + SQLCipher |
| 序列化 | Kotlinx Serialization + Gson |
| 构建 | Gradle + KSP + Version Catalog |
| 静态检查 | detekt + :lint-checks |
| 容器 | PRoot (用户态 Linux) |
| 终端 | Termux terminal-emulator + terminal-view |
| WebView 壳 | WebViewAssetLoader + 自研 JS Bridge（app-template） |

## 构建配置

- **JDK**: 17
- **minSdk**: 26（主应用与两个独立 APK 一致）
- **targetSdk**: 主应用 28（PRoot W^X 限制）；`:logviewer-app` 28；`:app-template` 34
- **ABI**: release arm64-v8a only
- **R8**: fullMode 启用（主应用与 app-template）
- **构建缓存 / 配置缓存**: 启用
- **签名**: 主应用、`:app-template`、`:logviewer-app` release 均复用主应用官方密钥（`app/minime.jks`），CI 从 Secrets 恢复，缺密钥/证书指纹不符即 fail-closed 拒绝出包
