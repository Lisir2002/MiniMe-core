# MiniMe 插件体系设计文档

> 版本：1.0.0-draft
> 状态：设计阶段（仅文档，未落地代码）
> 最后更新：2026-09-26

---

## 1. 概述

### 1.1 目标与定位

MiniMe 插件体系的**首要目标是瘦身安装包**，其次是构建可扩展的功能生态。

核心应用（Core）只保留最基础的能力：AI 对话、模型管理、基础设置、终端模拟器、插件框架。所有非核心能力以插件形式按需下载、按需加载，用户只安装自己需要的插件。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **瘦身优先** | 任何可延迟加载的能力都不进核心包，核心包目标 ≤25MB |
| **按需加载** | 插件不预装，用户首次使用对应功能时提示下载 |
| **类型统一** | 四类插件共用同一套框架、清单格式、生命周期、权限系统 |
| **安全隔离** | 插件运行在受限沙箱中，不能直接访问核心数据 |
| **用户可控** | 用户可随时启用/禁用/卸载/撤销权限，所有操作可审计 |
| **离线可用** | 已下载的插件在无网络时正常工作，仅更新需要网络 |
| **可扩展** | 插件类型可新增，不需要修改核心框架 |
| **降级优雅** | 插件缺失或加载失败时，核心提供最小可用降级方案 |

### 1.3 瘦身目标

| 体积来源 | 当前（预估） | 插件化后 | 节省 |
|----------|-------------|----------|------|
| tree-sitter 16 grammar | ~2-3MB | 核心留0个，全放查看器插件 | ~2-3MB |
| PDFium | ~10-20MB | 不内置，放查看器插件 | ~10-20MB |
| Office 解析器 | ~2-5MB | 不内置，放查看器插件 | ~2-5MB |
| 容器运行时/proot | ~5-15MB | 核心留终端，容器放环境插件 | ~5-15MB |
| 主题/字体资源 | ~1-2MB | 核心留默认，其余放主题插件 | ~1-2MB |
| 核心代码+资源 | ~15-20MB | 不变 | - |
| **合计** | **~35-65MB** | **~15-20MB** | **~20-45MB** |

### 1.4 术语定义

- **Core**：MiniMe 核心应用，包含插件框架
- **Plugin**：插件，一个可独立下载、安装、加载的功能包
- **Plugin Host**：插件宿主，Core 中负责插件生命周期管理的模块
- **Plugin Registry**：插件注册表，记录已安装插件的元数据和状态
- **Capability Registry**：能力注册表，所有工具/能力的统一注册中心
- **Plugin Manifest**：插件清单（plugin.json），插件的元数据描述
- **Plugin Market**：插件市场，插件的分发和发现渠道

---

## 2. 架构设计

> 本章所有决策均已与用户确认拍板。

### 2.1 整体分层架构

```
┌─────────────────────────────────────────────────┐
│                   UI 层                          │
│  设置页 · 插件管理 · 文件查看器 · 能力管理 · 主题  │
├─────────────────────────────────────────────────┤
│              Plugin Framework 层                 │
│  PluginHost · Registry · EventBus · Permission  │
│  Downloader · Storage · Sandbox · I18n          │
├─────────────────────────────────────────────────┤
│              Core Service 层                     │
│  AI对话 · 模型管理 · 终端 · 基础设置 · 操作审计   │
├─────────────────────────────────────────────────┤
│              Data 层                             │
│  数据库 · 文件系统 · 网络 · 加密                  │
└─────────────────────────────────────────────────┘
         ↑ PluginContext（唯一通信通道）
┌─────────────────────────────────────────────────┐
│                插件层（运行在沙箱中）              │
│  查看器插件 · 主题插件 · 能力插件 · 环境插件       │
└─────────────────────────────────────────────────┘
```

**进程模型**：插件与 Core 运行在**同一进程**，通过 ClassLoader 隔离，插件崩溃由 try-catch 兜底，不影响主应用。不采用独立进程（插件 UI 需嵌入主界面，独立进程通信复杂）。

### 2.2 PluginHost 内部模块划分

PluginHost 为**单例**，内部模块均为单例，每个插件的状态存储在 PluginRegistry 中：

```
PluginHost（单例）
├── PluginScanner            # 扫描插件目录，读取清单
├── PluginValidator          # 校验清单/签名/校验和/兼容性
├── DependencyResolver       # 依赖解析（拓扑排序、循环检测）
├── PluginLoader             # 加载 dex/so/资源，创建 ClassLoader
├── PluginLifecycleManager   # 生命周期管理（初始化/启用/禁用/卸载）
├── PluginRegistry           # 插件注册表（id → 插件实例+状态）
├── PermissionManager        # 权限检查
├── EventBus                 # 事件总线（异步，提供 publishSync）
├── DownloadManager          # 下载中心
├── PluginStorage            # 存储管理（目录/registry.json/数据目录）
├── PluginSandbox            # 沙箱（文件/网络/调用限制）
└── PluginCrashHandler       # 崩溃捕获与自动禁用
```

### 2.3 插件加载机制

按插件类型分为三种加载方式：

| 类型 | 加载方式 | 说明 |
|------|----------|------|
| 纯资源插件（主题） | 读取 JSON/图片/字体，Core 解析后直接应用 | 零代码，零风险 |
| Native 插件（查看器 so/环境） | `System.load()` 加载 .so，JNI 调用 | 需 ABI 匹配（arm64-v8a） |
| 代码插件（能力插件） | `DexClassLoader` 加载 .dex，反射调用入口 | 能力全面，支持复杂逻辑 |

**能力插件技术路线（已确认）**：
1. **打包工具链**：提供 Gradle 插件模板（`apply plugin: 'minime-plugin'`），自动打包 dex + 资源 + 签名
2. **版本兼容**：插件声明 `minCoreVersion`，不兼容的拒绝加载
3. **内部类隐藏**：Core 用 R8/ProGuard 混淆隐藏内部类，只暴露 PluginContext 接口
4. **UI 嵌入**：插件提供 Composable 函数引用，Core 直接调用（方案B，灵活，需 Compose 运行时兼容）

### 2.4 PluginContext 通信架构

PluginContext 为**接口**，Core 内部提供实现类。插件只能通过 PluginContext 与 Core 交互，这是唯一通信通道：

```
插件代码
  │
  ├─ pluginContext.getString()              → I18n 模块
  ├─ pluginContext.getSettings()            → 插件配置系统
  ├─ pluginContext.getFileAccess()          → 权限检查 → 文件沙箱
  ├─ pluginContext.getNetwork()             → 权限检查 → 网络代理
  ├─ pluginContext.getTerminal()            → 权限检查 → 终端沙箱
  ├─ pluginContext.getCapabilityRegistry()  → 能力注册表
  ├─ pluginContext.getEventBus()            → 事件总线
  ├─ pluginContext.getLogger()              → 日志系统
  ├─ pluginContext.showToast()              → UI 通知
  └─ pluginContext.registerSettingsPage()   → 设置页自动生成
```

### 2.5 注册表统一架构

四类插件各有注册表，抽象统一泛型基类：

```
PluginRegistry<T>（泛型基类）
├── register(pluginId: String, item: T)
├── unregister(pluginId: String)
├── get(id: String): T?
├── getAll(): List<T>
├── getByType(type: String): List<T>
└── addListener(listener: RegistryChangeListener)

具体注册表：
├── FileViewerRegistry    : PluginRegistry<FileViewerFactory>
├── ThemeRegistry         : PluginRegistry<ThemePackage>
├── CapabilityRegistry    : PluginRegistry<CapabilityDefinition>
└── EnvironmentRegistry   : PluginRegistry<EnvironmentProfile>
```

> 重复能力注册冲突问题暂不处理，后续按需设计。

### 2.6 事件总线架构

- **默认异步**：发布后立即返回，订阅者在后台处理，UI 更新需切主线程
- **同步选项**：提供 `publishSync()` 给需要同步的场景
- **命名空间**：`system:*`（系统事件）、`plugin:<id>:*`（插件事件，只能发布自己命名空间）

系统事件：
- `system:theme_changed` — 主题切换
- `system:language_changed` — 语言切换
- `system:model_changed` — 默认模型变更
- `system:low_memory` — 低内存
- `system:network_changed` — 网络状态变更
- `system:plugin_installed/enabled/disabled` — 插件生命周期事件

### 2.7 权限检查架构

```
每次插件调用受限 API：
  │
  ├─ 1. 检查插件是否声明了该权限（plugin.json）
  ├─ 2. 检查用户是否授权了该权限（registry.json）
  ├─ 3. 敏感权限（settings_write / model_config）二次确认
  ├─ 4. 执行操作
  └─ 5. 记录审计日志（复用操作审计系统）
```

- 安装时展示权限列表并整体授权
- 敏感权限运行时二次确认
- 用户可随时在插件管理页面撤销权限

### 2.8 存储架构

插件存储在**应用私有目录**下：

```
<filesDir>/plugins/
├── registry.json              # 全局插件注册表
├── <plugin-id>/               # 每个插件一个目录
│   ├── plugin.json
│   ├── *.dex / *.so
│   └── 资源文件
├── .cache/                    # 下载缓存
└── .disabled/                 # 已禁用标记

<filesDir>/plugin-data/
└── <plugin-id>/
    ├── settings.json          # 插件设置
    ├── data/                  # 业务数据
    ├── cache/                 # 缓存（低内存时可清理）
    └── logs/                  # 插件日志
```

> 注意：开发环境文件（项目代码、JDK/NDK、编译产物）放用户目录，与应用运行时插件存储是两回事。

---

## 3. 插件分类体系

### 3.1 文件查看器插件（file-viewer）

**定位**：统一承载所有文件渲染能力，包括语法高亮、PDF、Office、图片、代码编辑。

**设计思路**：文件查看器插件 = 文件类型注册表 + 多个渲染器。一个插件可以包含全部或部分渲染能力。

#### 2.1.1 插件包结构

```
file-viewer-plugin.zip
├── plugin.json                  # 清单
├── viewer.dex                   # Kotlin 层渲染器入口（可选，纯资源插件不需要）
├── grammars/                    # tree-sitter grammar（.so）
│   ├── libts-kotlin.so
│   ├── libts-python.so
│   └── ...
├── pdf/                         # PDF 渲染
│   ├── libpdfium.so
│   └── config.json
├── office/                      # Office 轻量预览
│   ├── liboffice-parser.so
│   └── templates/
├── image/                       # 高级图片格式支持
│   ├── libimage-codecs.so
│   └── config.json
├── editor/                      # 代码编辑器核心（text_buffer、多光标等）
│   ├── libeditor-core.so
│   └── keymap.json
├── themes/                      # 代码高亮主题
│   ├── monokai.json
│   ├── dracula.json
│   └── default.json
└── preview.png                  # 市场展示预览图
```

#### 2.1.2 清单扩展字段

```json
{
  "type": "file-viewer",
  "viewer": {
    "supportedTypes": [
      { "mime": "text/x-kotlin", "extensions": [".kt", ".kts"], "renderer": "tree-sitter", "grammar": "kotlin" },
      { "mime": "application/pdf", "extensions": [".pdf"], "renderer": "pdfium" },
      { "mime": "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "extensions": [".docx"], "renderer": "office" },
      { "mime": "image/svg+xml", "extensions": [".svg"], "renderer": "image" }
    ],
    "editorEnabled": true,
    "maxFileSize": 104857600,
    "defaultTheme": "default"
  }
}
```

#### 2.1.3 核心交互

- Core 内置最小文本查看器（~100KB），无插件时降级为纯文本
- 插件安装后，将 `supportedTypes` 注册到 Core 的 `FileViewerRegistry`
- 用户打开文件时，Core 按 MIME/扩展名查找渲染器，调用插件
- 插件可声明 `maxFileSize`，超出时 Core 提示"文件过大，使用纯文本查看"

#### 2.1.4 子插件化（可选）

一个"全量查看器"插件体积较大（~15-25MB），可以拆分为多个小插件：
- `com.minime.viewer.code`：仅语法高亮（~2-3MB）
- `com.minime.viewer.pdf`：仅 PDF（~10-15MB）
- `com.minime.viewer.office`：仅 Office（~2-5MB）

用户按需下载，Core 的注册表自动合并多个查看器插件的能力。

---

### 3.2 主题插件（theme）

**定位**：统一承载视觉外观定制，包括配色、字体、形状、启动动画、图标风格。

#### 2.2.1 插件包结构

```
theme-plugin.zip
├── plugin.json
├── colors.json                  # 完整色板（light + dark）
├── typography.json              # 字体配置
├── shapes.json                  # 圆角/间距/阴影/高度
├── splash.json                  # 启动动画参数
├── icons.json                   # 图标风格配置（可选）
├── fonts/                       # 字体文件
│   ├── body-regular.ttf
│   ├── body-bold.ttf
│   ├── mono-regular.ttf
│   └── mono-bold.ttf
├── preview-light.png            # 亮色预览图
├── preview-dark.png             # 暗色预览图
└── README.md                    # 主题说明
```

#### 2.2.2 清单扩展字段

```json
{
  "type": "theme",
  "theme": {
    "modes": ["light", "dark", "auto"],
    "defaultMode": "auto",
    "supportsDynamicColor": false,
    "fontFamilies": ["body", "mono", "display"],
    "splashEnabled": true,
    "shapePreset": "rounded",
    "density": "comfortable"
  }
}
```

#### 2.2.3 配色方案格式（colors.json）

```json
{
  "light": {
    "primary": "#FF6B35",
    "onPrimary": "#FFFFFF",
    "primaryContainer": "#FFD8C8",
    "onPrimaryContainer": "#3A0C00",
    "secondary": "#77564C",
    "onSecondary": "#FFFFFF",
    "secondaryContainer": "#FFDBD1",
    "onSecondaryContainer": "#2C150D",
    "tertiary": "#6C5C2E",
    "onTertiary": "#FFFFFF",
    "tertiaryContainer": "#F6E0A6",
    "onTertiaryContainer": "#231A00",
    "error": "#BA1A1A",
    "onError": "#FFFFFF",
    "errorContainer": "#FFDAD6",
    "onErrorContainer": "#410002",
    "background": "#FFFBFF",
    "onBackground": "#201A19",
    "surface": "#FFFBFF",
    "onSurface": "#201A19",
    "surfaceVariant": "#F5DED7",
    "onSurfaceVariant": "#53433F",
    "outline": "#85736E",
    "outlineVariant": "#D8C2BC"
  },
  "dark": { "...": "..." }
}
```

#### 2.2.4 字体配置（typography.json）

```json
{
  "body": {
    "regular": "fonts/body-regular.ttf",
    "bold": "fonts/body-bold.ttf",
    "sizeScale": 1.0,
    "lineHeightScale": 1.0
  },
  "mono": {
    "regular": "fonts/mono-regular.ttf",
    "bold": "fonts/mono-bold.ttf",
    "sizeScale": 0.95
  },
  "display": {
    "regular": "fonts/body-bold.ttf",
    "sizeScale": 1.1
  }
}
```

#### 2.2.5 启动动画参数（splash.json）

```json
{
  "enabled": true,
  "durationMs": 6000,
  "skippable": true,
  "particleCount": 1800,
  "particleRadius": { "min": 0.5, "max": 1.2 },
  "particleColor": "primary",
  "glowAlpha": { "min": 0.3, "max": 0.5 },
  "text": "MiniMe",
  "textFont": "display",
  "animation": "particle-converge-shatter",
  "backgroundGradient": ["surface", "primaryContainer"],
  "shatterEffect": "glass-3d"
}
```

#### 2.2.6 核心交互

- 主题切换**实时生效**，不需要重启（Compose colorScheme 状态化）
- 字体加载后缓存到内存，切换主题时不重新加载已加载字体
- 暗色/亮色可独立配置，也可跟随系统
- 主题插件禁用后，自动回退到 Core 默认主题

---

### 3.3 能力工具插件（capability）

**定位**：统一承载 AI 可调用的工具/能力，后续将 MCP 协议与能力中心合二为一，所有工具统一注册、统一管理。

#### 2.3.1 核心概念：能力（Capability）

一个能力 = 可被 AI 调用的工具 + 元数据（名称/描述/参数 Schema/权限/执行器）。

能力的来源：
- MCP Server 提供的工具
- 插件注册的自定义工具
- Core 内置的基础工具（文件读写、终端等）

所有来源统一注册到 `CapabilityRegistry`，AI 调用时不区分来源。

#### 2.3.2 插件包结构

```
capability-plugin.zip
├── plugin.json
├── capability.dex               # 执行逻辑（Kotlin 编译为 dex）
├── capabilities/                # 每个能力一个目录
│   ├── git-advanced/
│   │   ├── manifest.json        # 能力元数据
│   │   └── icon.png
│   └── http-client/
│       ├── manifest.json
│       └── icon.png
├── shared/                      # 多个能力共享的库
│   └── libhttp.so
└── README.md
```

#### 2.3.3 能力清单（manifest.json）

```json
{
  "id": "git-advanced",
  "name": "高级 Git 工具",
  "nameEn": "Advanced Git Tools",
  "description": "提供 Git 历史浏览、分支管理、冲突解决等高级操作",
  "version": "1.0.0",
  "category": "development",
  "tags": ["git", "vcs", "version-control"],
  "icon": "icon.png",
  "tools": [
    {
      "name": "git_log",
      "description": "查看提交历史",
      "parameters": {
        "type": "object",
        "properties": {
          "path": { "type": "string", "description": "仓库路径" },
          "max_count": { "type": "integer", "default": 20 }
        },
        "required": ["path"]
      },
      "permissions": ["file_read", "terminal_exec"],
      "timeoutMs": 30000,
      "handler": "com.minime.plugin.git.GitLogHandler"
    }
  ]
}
```

#### 2.3.4 清单扩展字段

```json
{
  "type": "capability",
  "capability": {
    "capabilityManifestDir": "capabilities",
    "entryClass": "com.minime.plugin.PluginEntry",
    "sharedLibraries": ["shared/libhttp.so"],
    "mcpServers": [
      {
        "name": "example-mcp",
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-example"],
        "env": { "API_KEY": "${settings.api_key}" }
      }
    ]
  }
}
```

#### 2.3.5 核心交互

- 插件启用时，将所有能力注册到 `CapabilityRegistry`
- MCP Server 类型的能力，由 Core 负责启动进程、管理生命周期
- AI 调用工具时，从注册表查找执行器，在沙箱中执行
- 插件禁用时，从注册表移除所有能力
- 能力管理页面统一展示所有来源的能力，可单独启用/禁用

---

### 3.4 环境容器插件（environment）

**定位**：承载容器运行时、基础镜像、特定语言/工具链环境。

#### 2.4.1 插件包结构

```
environment-plugin.zip
├── plugin.json
├── runtime/                     # 容器运行时（proot 等）
│   ├── libproot.so
│   └── config.json
├── images/                      # 容器镜像（分层 tar）
│   └── ubuntu-base/
│       ├── manifest.json
│       └── layers/
├── profiles/                    # 环境配置
│   ├── python-dev.json
│   └── node-dev.json
└── README.md
```

#### 2.4.2 清单扩展字段

```json
{
  "type": "environment",
  "environment": {
    "runtime": "proot",
    "runtimeVersion": "5.4.0",
    "architectures": ["arm64-v8a"],
    "images": [
      {
        "id": "ubuntu-base",
        "name": "Ubuntu 基础环境",
        "base": "ubuntu:22.04",
        "size": 157286400,
        "packages": ["git", "curl", "wget", "build-essential"]
      }
    ],
    "profiles": [
      {
        "id": "python-dev",
        "name": "Python 开发环境",
        "image": "ubuntu-base",
        "packages": ["python3", "python3-pip", "python3-venv"],
        "env": { "PYTHONUNBUFFERED": "1" }
      }
    ]
  }
}
```

#### 2.4.3 核心交互

- Core 内置终端模拟器，不包含容器运行时
- 插件安装后，注册运行时和可用镜像/环境配置
- 用户首次使用容器时提示下载环境插件
- 容器创建时从插件目录读取镜像和配置
- 插件卸载时，已创建的容器保留，但无法创建新容器

---

### 3.5 类型扩展机制

插件类型不是固定的四类，框架支持新增类型：

```
新增类型步骤：
1. 在 Core 中实现该类型的 PluginHandler（处理加载/初始化/注册）
2. 在 plugin.json 的 type 字段使用新类型名
3. 插件市场索引中添加新类型分类
```

框架本身不硬编码类型列表，所有类型通过 `PluginHandler` 注册。

---

## 4. 通用插件框架

### 4.1 插件清单格式（plugin.json）

所有类型插件共用同一清单格式，`type` 字段区分类型，各类型通过扩展字段声明特有配置。

#### 4.1.1 完整字段列表

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `schemaVersion` | integer | 是 | 清单格式版本，当前为 1 |
| `id` | string | 是 | 插件唯一标识，反向域名格式（com.minime.xxx） |
| `name` | string | 是 | 插件名称（中文） |
| `nameEn` | string | 否 | 插件名称（英文） |
| `type` | string | 是 | 插件类型：file-viewer / theme / capability / environment / 自定义 |
| `version` | string | 是 | 语义化版本（major.minor.patch） |
| `minCoreVersion` | string | 是 | 最低兼容 Core 版本 |
| `maxCoreVersion` | string | 否 | 最高兼容 Core 版本（不含则无上限） |
| `author` | string | 是 | 作者名称 |
| `authorUrl` | string | 否 | 作者主页 |
| `description` | string | 是 | 插件描述（中文） |
| `descriptionEn` | string | 否 | 插件描述（英文） |
| `license` | string | 否 | 开源协议（GPL-3.0 / MIT 等） |
| `homepage` | string | 否 | 插件主页 |
| `repository` | string | 否 | 源码仓库地址 |
| `keywords` | string[] | 否 | 搜索关键词 |
| `category` | string | 否 | 分类标签 |
| `tags` | string[] | 否 | 标签 |
| `permissions` | string[] | 否 | 申请的权限列表（见 3.6） |
| `entry` | string | 否 | 入口文件（dex/so/json），纯资源插件可不填 |
| `entryClass` | string | 否 | 入口类名（Kotlin/Java 插件） |
| `dependencies` | object[] | 否 | 依赖的其他插件 |
| `dependencies[].id` | string | 是 | 依赖插件 id |
| `dependencies[].versionRange` | string | 是 | 版本范围（如 >=1.0.0 <2.0.0） |
| `optionalDependencies` | object[] | 否 | 可选依赖（不存在时降级） |
| `conflicts` | string[] | 否 | 冲突的插件 id（不能同时启用） |
| `size` | integer | 否 | 解压后大小（字节），用于显示 |
| `checksum` | string | 否 | 插件包 SHA256（市场索引中提供） |
| `signature` | string | 否 | 插件包数字签名 |
| `changelog` | string | 否 | 当前版本更新说明 |
| `releaseNotes` | string | 否 | 完整更新日志（Markdown） |
| `icon` | string | 否 | 插件图标路径 |
| `previewImages` | string[] | 否 | 预览图路径列表 |
| `minSdkVersion` | integer | 否 | 最低 Android SDK 版本 |
| `supportedAbis` | string[] | 否 | 支持的 ABI（arm64-v8a / x86_64） |
| `typeConfig` | object | 是 | 类型特定配置（见各类型扩展字段） |
| `settingsSchema` | object | 否 | 插件设置页的表单 Schema（见 3.8） |
| `i18n` | object | 否 | 国际化资源（见 3.10） |
| `telemetry` | boolean | 否 | 是否允许匿名使用统计（默认 false） |

#### 4.1.2 完整示例

```json
{
  "schemaVersion": 1,
  "id": "com.minime.viewer.full",
  "name": "全量文件查看器",
  "nameEn": "Full File Viewer",
  "type": "file-viewer",
  "version": "1.2.0",
  "minCoreVersion": "0.0.1",
  "maxCoreVersion": "1.0.0",
  "author": "MiniMe Team",
  "authorUrl": "https://github.com/Lisir2002",
  "description": "包含 16 种语言语法高亮、PDF 查看、Office 预览、图片查看、代码编辑器",
  "descriptionEn": "16 language syntax highlighting, PDF viewer, Office preview, image viewer, code editor",
  "license": "GPL-3.0",
  "homepage": "https://github.com/Lisir2002/MiniMe-core",
  "repository": "https://github.com/Lisir2002/MiniMe-core",
  "keywords": ["查看器", "语法高亮", "PDF", "代码编辑", "viewer", "syntax", "pdf"],
  "category": "viewer",
  "tags": ["viewer", "editor", "pdf"],
  "permissions": ["file_read", "file_write"],
  "entry": "viewer.dex",
  "entryClass": "com.minime.viewer.FullViewerEntry",
  "dependencies": [],
  "optionalDependencies": [
    { "id": "com.minime.theme.dracula", "versionRange": ">=1.0.0" }
  ],
  "conflicts": ["com.minime.viewer.code-only"],
  "size": 15728640,
  "checksum": "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "signature": "RSA-SHA256:abcdef...",
  "changelog": "新增 Office 预览支持，修复大文件卡顿",
  "icon": "icon.png",
  "previewImages": ["preview-1.png", "preview-2.png"],
  "minSdkVersion": 26,
  "supportedAbis": ["arm64-v8a"],
  "typeConfig": {
    "supportedTypes": [ "...见 2.1.2..." ],
    "editorEnabled": true,
    "maxFileSize": 104857600
  },
  "settingsSchema": {
    "tabs": [
      {
        "id": "general",
        "title": "通用",
        "fields": [
          {
            "key": "default_theme",
            "type": "select",
            "title": "默认代码主题",
            "options": ["default", "monokai", "dracula"],
            "default": "default"
          },
          {
            "key": "font_size",
            "type": "slider",
            "title": "字体大小",
            "min": 10,
            "max": 24,
            "default": 14
          },
          {
            "key": "word_wrap",
            "type": "switch",
            "title": "自动换行",
            "default": false
          }
        ]
      }
    ]
  },
  "telemetry": false
}
```

### 4.2 插件包格式

- **格式**：ZIP（通用、工具链成熟、流式解压）
- **压缩**：DEFLATE（默认），存储时不压缩 .so 文件（已压缩）
- **编码**：UTF-8
- **路径限制**：不允许 `../` 路径穿越，解压时校验
- **最大包体**：建议 ≤100MB，超过时分卷或拆分插件
- **签名**：可选 RSA-SHA256 签名，官方插件必须签名

### 4.3 存储结构

插件存储在用户目录下（非应用私有目录），方便用户管理和备份：

```
~/MiniMe/
├── plugins/
│   ├── registry.json              # 全局插件注册表
│   ├── com.minime.viewer.full/
│   │   ├── plugin.json            # 解压后的清单
│   │   ├── viewer.dex
│   │   ├── grammars/
│   │   └── ...
│   ├── com.minime.theme.dracula/
│   │   ├── plugin.json
│   │   ├── colors.json
│   │   └── fonts/
│   └── .disabled/                 # 已禁用插件的标记目录
│       └── com.minime.xxx.disabled
├── plugin-cache/                  # 下载缓存
│   ├── com.minime.viewer.full-1.2.0.zip
│   └── .tmp/                      # 下载中临时文件
└── plugin-data/                   # 插件私有数据
    ├── com.minime.viewer.full/
    │   ├── settings.json
    │   └── cache/
    └── com.minime.theme.dracula/
```

#### 4.3.1 registry.json 格式

```json
{
  "schemaVersion": 1,
  "plugins": {
    "com.minime.viewer.full": {
      "version": "1.2.0",
      "type": "file-viewer",
      "enabled": true,
      "installedAt": 1727328000000,
      "updatedAt": 1727328000000,
      "path": "plugins/com.minime.viewer.full",
      "permissionsGranted": ["file_read", "file_write"],
      "settings": { "default_theme": "monokai" },
      "signatureVerified": true,
      "source": "official-market"
    }
  },
  "lastMarketSync": 1727328000000
}
```

### 4.4 生命周期

#### 4.4.1 状态机

```
                    ┌──────────────┐
                    │  DOWNLOADED  │ 下载完成，未校验
                    └──────┬───────┘
                           │ 校验签名/校验和/清单
                    ┌──────▼───────┐
                    │   VERIFIED   │ 校验通过，未安装
                    └──────┬───────┘
                           │ 解压+检查依赖+用户授权
                    ┌──────▼───────┐
                    │  INSTALLED   │ 已安装，未启用
                    └──────┬───────┘
                           │ 启用
                    ┌──────▼───────┐
              ┌─────►   ENABLED    │ 已启用，运行中
              │     └──────┬───────┘
              │            │ 禁用
              │     ┌──────▼───────┐
              └─────┤  DISABLED    │ 已禁用，不加载
                    └──────┬───────┘
                           │ 卸载
                    ┌──────▼───────┐
                    │  UNINSTALLED │ 已卸载，文件已删除
                    └──────────────┘

异常状态：
  VERIFY_FAILED    校验失败，拒绝安装
  DEPENDENCY_MISSING 依赖缺失，无法启用
  CONFLICT         与已启用插件冲突
  LOAD_FAILED      加载失败（dex 加载异常等）
  CRASHED          运行时崩溃，自动禁用
```

#### 4.4.2 启动加载流程

```
应用启动
  │
  ├─ 1. 扫描 ~/MiniMe/plugins/ 目录
  │
  ├─ 2. 读取每个 plugin.json → 校验清单格式
  │     └─ 格式错误 → 标记 LOAD_FAILED，跳过
  │
  ├─ 3. 校验签名（官方插件必须验证）
  │     └─ 签名失败 → 标记 VERIFY_FAILED，跳过
  │
  ├─ 4. 校验 minCoreVersion / maxCoreVersion 兼容性
  │     └─ 不兼容 → 标记 INCOMPATIBLE，提示用户更新
  │
  ├─ 5. 校验 supportedAbis（设备 ABI 是否在列表中）
  │     └─ 不支持 → 标记 INCOMPATIBLE
  │
  ├─ 6. 依赖解析（拓扑排序，被依赖的先加载）
  │     ├─ 依赖缺失 → 标记 DEPENDENCY_MISSING
  │     └─ 循环依赖 → 标记 DEPENDENCY_ERROR
  │
  ├─ 7. 冲突检测（conflicts 字段）
  │     └─ 冲突 → 后加载的标记 CONFLICT
  │
  ├─ 8. 逐个加载 enabled=true 的插件
  │     ├─ 调用 PluginHandler.onLoad()
  │     ├─ 加载 dex/so/资源
  │     ├─ 调用插件入口 onInitialize(PluginContext)
  │     ├─ 插件注册能力/渲染器/主题等到对应注册表
  │     └─ 加载失败 → 标记 LOAD_FAILED，记录错误
  │
  ├─ 9. 全部加载完成 → 发出 PLUGINS_READY 事件
  │
  └─ 10. UI 刷新（设置页、文件查看器、能力列表等）
```

#### 4.4.3 生命周期钩子

插件入口类实现以下接口：

```kotlin
interface MiniMePlugin {
    /** 插件加载时调用（一次性，在启用时） */
    fun onInitialize(context: PluginContext)

    /** 插件启用时调用（每次从禁用→启用） */
    fun onEnabled()

    /** 插件禁用时调用（释放资源、取消注册） */
    fun onDisabled()

    /** 插件卸载前调用（清理数据，可询问用户是否保留数据） */
    fun onUninstall(keepData: Boolean)

    /** 应用配置变更时调用（主题切换/语言切换等） */
    fun onConfigurationChanged(config: PluginConfig)

    /** 低内存时调用，插件应释放可重建的缓存 */
    fun onLowMemory()
}
```

### 4.5 下载中心

#### 4.5.1 安装流程

```
用户点击安装
  │
  ├─ 1. 检查网络连接
  │     └─ 无网络 → 提示"需要网络下载插件"
  │
  ├─ 2. 下载插件包（支持断点续传）
  │     ├─ 主源：GitHub Releases
  │     ├─ 备用源：镜像站（自动切换）
  │     └─ 进度回调 → UI 显示下载进度
  │
  ├─ 3. 下载完成 → 校验 SHA256
  │     └─ 校验失败 → 删除文件，提示重新下载
  │
  ├─ 4. 校验签名（官方插件）
  │     └─ 签名失败 → 提示"插件来源不可信"，用户可选择强制安装
  │
  ├─ 5. 解压到临时目录
  │     ├─ 校验 plugin.json 格式
  │     ├─ 校验路径（无 ../ 穿越）
  │     └─ 校验必需文件存在
  │
  ├─ 6. 检查依赖
  │     ├─ 依赖已安装 → 继续
  │     ├─ 依赖未安装 → 提示"需要先安装 XXX"，提供一键安装
  │     └─ 依赖版本不兼容 → 提示升级依赖
  │
  ├─ 7. 检查冲突
  │     └─ 与已启用插件冲突 → 提示"与 XXX 冲突，是否禁用 XXX"
  │
  ├─ 8. 权限授权对话框
  │     ├─ 展示插件申请的权限列表
  │     ├─ 用户同意 → 继续
  │     └─ 用户拒绝 → 取消安装
  │
  ├─ 9. 移动到正式插件目录 → 更新 registry.json
  │
  ├─ 10. 初始化插件 → 注册到对应注册表
  │
  └─ 11. 通知 UI 刷新 → Toast 提示"安装成功"
```

#### 4.5.2 更新流程

```
检查更新（启动时/手动）
  │
  ├─ 1. 获取市场索引（JSON）
  ├─ 2. 对比本地版本与市场版本
  ├─ 3. 有更新 → 提示用户
  ├─ 4. 用户选择更新 → 下载新版本
  ├─ 5. 下载校验通过 → 禁用旧版本 → 替换文件 → 启用新版本
  └─ 6. 更新失败 → 回滚到旧版本
```

#### 4.5.3 下载源配置

| 源 | 地址 | 用途 |
|----|------|------|
| 主源 | GitHub Releases | 官方插件下载 |
| 备用源 | 镜像站（可配置） | 主源不可用时自动切换 |
| 本地源 | 用户目录/plugin-local/ | 开发者本地测试 |

### 4.6 权限系统

#### 4.6.1 权限列表

| 权限 | 标识 | 说明 | 授权方式 |
|------|------|------|----------|
| 读取文件 | `file_read` | 读取用户目录下的文件 | 安装时授权 |
| 写入文件 | `file_write` | 写入/修改/删除用户文件 | 安装时授权 |
| 网络访问 | `network` | 发起网络请求 | 安装时授权 |
| 终端执行 | `terminal_exec` | 在终端中执行命令 | 安装时授权 |
| 访问对话历史 | `chat_history` | 读取/搜索对话记录 | 安装时授权，运行时可撤销 |
| 调用能力工具 | `capability_invoke` | 调用其他插件注册的能力 | 安装时授权 |
| 读取设置 | `settings_read` | 读取应用设置 | 安装时授权 |
| 修改设置 | `settings_write` | 修改应用设置 | 安装时授权，敏感操作二次确认 |
| 访问模型配置 | `model_config` | 读取模型供应商/API Key | 安装时授权，运行时可撤销 |
| 发送通知 | `notification` | 发送系统通知 | 安装时授权 |
| 后台运行 | `background` | 在后台持续运行 | 安装时授权 |
| 访问位置 | `location` | 获取设备位置 | 运行时申请（Android 运行时权限） |

#### 4.6.2 权限设计要点

- 权限在 `plugin.json` 的 `permissions` 字段声明
- 安装时展示权限列表，用户逐项或整体授权
- 用户可在插件管理页面随时撤销权限
- 插件调用未授权 API 时，Core 拒绝并记录审计日志（复用操作审计系统）
- 敏感权限（`settings_write`、`model_config`）在运行时调用需要二次确认
- 权限最小化：Core 审计插件实际使用的权限，未使用的权限提示用户可撤销

### 4.7 插件间通信（事件总线）

插件之间不能直接调用，必须通过 Core 的事件总线：

```
插件A 发布事件 → Core 事件总线 → 订阅了该事件的插件B/C 收到回调
```

#### 4.7.1 事件命名规范

- 格式：`plugin:<plugin-id>:<event-name>`
- 插件只能发布自己命名空间的事件
- 系统事件命名空间：`system:<event-name>`
- 示例：
  - `system:theme_changed` — 主题切换
  - `system:language_changed` — 语言切换
  - `system:model_changed` — 默认模型变更
  - `plugin:com.minime.viewer.full:file_opened` — 查看器打开文件

#### 4.7.2 事件数据

事件携带 JSON 格式数据，大小限制 64KB。

### 4.8 插件配置系统

每个插件可以有自己的设置页面，通过 `settingsSchema` 声明表单结构，Core 自动渲染设置 UI。

#### 4.8.1 支持的表单控件

| 控件类型 | type | 字段 |
|----------|------|------|
| 开关 | `switch` | key, title, default, description |
| 滑块 | `slider` | key, title, min, max, step, default, unit |
| 下拉选择 | `select` | key, title, options[], default |
| 文本输入 | `text` | key, title, default, placeholder, maxLength |
| 数字输入 | `number` | key, title, default, min, max |
| 颜色选择 | `color` | key, title, default |
| 文件选择 | `file` | key, title, mimeTypes |
| 分组标题 | `group` | title, description |

#### 4.8.2 设置存储

- 插件设置存储在 `~/MiniMe/plugin-data/<plugin-id>/settings.json`
- Core 提供 `PluginContext.getSettings()` / `setSettings()` API
- 设置变更时发出 `plugin:<id>:settings_changed` 事件
- 卸载插件时可选择保留或删除设置

### 4.9 插件数据存储

每个插件有独立的数据目录：

```
~/MiniMe/plugin-data/<plugin-id>/
├── settings.json          # 插件设置（Core 管理）
├── data/                  # 插件业务数据（插件自行管理）
├── cache/                 # 缓存（Core 可在低内存时清理）
└── logs/                  # 插件日志（Core 统一收集）
```

- 插件只能访问自己的数据目录，不能访问其他插件的数据
- `cache/` 目录由 Core 管理，低内存或清理缓存时可删除
- `logs/` 目录的日志纳入应用的日志系统

### 4.10 国际化

#### 4.10.1 插件多语言资源

插件在 `plugin.json` 的 `i18n` 字段或独立的 `i18n/` 目录中提供多语言：

```
i18n/
├── zh-CN.json
├── en-US.json
└── ja-JP.json
```

```json
{
  "plugin_name": "全量文件查看器",
  "plugin_description": "包含语法高亮、PDF、Office 预览",
  "settings.default_theme": "默认代码主题",
  "error.file_too_large": "文件过大，使用纯文本查看"
}
```

#### 4.10.2 Core 交互

- Core 根据系统语言选择插件的语言资源
- 插件通过 `PluginContext.getString(key)` 获取本地化字符串
- 缺少对应语言时回退到英文，再回退到插件默认语言

### 4.11 安全机制

| 机制 | 说明 |
|------|------|
| **数字签名** | 官方插件用 MiniMe 私钥签名，第三方插件需用户确认"未知来源" |
| **Class 隔离** | DexClassLoader 隔离，插件不能直接访问 Core 的内部类 |
| **接口通信** | 插件只能通过 `PluginContext` 暴露的受限 API 与 Core 交互 |
| **崩溃隔离** | 插件代码运行在 try-catch 中，崩溃不影响主应用，自动标记 CRASHED |
| **文件沙箱** | 插件文件操作限制在插件目录 + 用户授权的目录 |
| **网络审计** | 插件的网络请求可通过 Core 代理，记录目标地址和数据量 |
| **权限审计** | 插件的敏感操作记录到操作审计日志 |
| **路径校验** | 解压时校验无 `../` 路径穿越 |
| **校验和** | 下载后校验 SHA256，防止篡改 |
| **版本兼容** | 不兼容版本拒绝加载，防止 API 不匹配导致崩溃 |

### 4.12 错误处理与降级

#### 4.12.1 降级策略

| 场景 | 降级方案 |
|------|----------|
| 文件查看器插件未安装 | Core 内置最小文本查看器（纯文本，无高亮） |
| PDF 插件未安装 | 提示"需要安装文件查看器插件查看 PDF"，提供下载按钮 |
| 主题插件加载失败 | 回退到 Core 默认主题 |
| 能力插件加载失败 | 该能力从注册表移除，AI 调用时提示"能力不可用" |
| 环境插件未安装 | 容器功能入口显示"需要安装环境插件"，提供下载按钮 |
| 插件运行时崩溃 | 自动禁用该插件，提示用户"插件 XXX 已崩溃，已自动禁用" |
| 依赖插件被禁用 | 依赖它的插件自动降级或禁用 |
| 网络不可用 | 已安装插件正常工作，仅无法下载/更新 |

#### 4.12.2 错误报告

- 插件加载失败时，记录详细错误到日志
- 插件崩溃时，收集崩溃堆栈，用户可选择上报
- 插件管理页面显示每个插件的状态和错误信息

### 4.13 版本与依赖管理

#### 4.13.1 语义化版本

- 插件版本：`major.minor.patch`
  - major：不兼容的 API 变更
  - minor：向后兼容的功能新增
  - patch：向后兼容的问题修复
- Core 版本同样遵循语义化版本
- `minCoreVersion` / `maxCoreVersion` 声明兼容范围

#### 4.13.2 依赖版本范围

支持语义化版本范围：
- `>=1.0.0` — 大于等于 1.0.0
- `>=1.0.0 <2.0.0` — 1.x 系列
- `^1.2.0` — 兼容 1.2.0 以上的 1.x
- `~1.2.0` — 兼容 1.2.x

#### 4.13.3 依赖解析

- 启动时按拓扑排序加载，被依赖的先加载
- 循环依赖检测，发现后拒绝加载
- 可选依赖缺失时，插件以降级模式运行（需插件自身处理）
- 依赖版本不满足时，提示用户升级依赖插件

### 4.14 插件市场协议

#### 4.14.1 市场索引格式

一个 JSON 文件，列出所有可用插件：

```json
{
  "schemaVersion": 1,
  "updatedAt": 1727328000000,
  "plugins": [
    {
      "id": "com.minime.viewer.full",
      "name": "全量文件查看器",
      "type": "file-viewer",
      "latestVersion": "1.2.0",
      "versions": [
        {
          "version": "1.2.0",
          "minCoreVersion": "0.0.1",
          "size": 15728640,
          "checksum": "sha256:...",
          "downloadUrl": "https://github.com/.../viewer-full-1.2.0.zip",
          "changelog": "新增 Office 预览"
        }
      ],
      "author": "MiniMe Team",
      "description": "...",
      "iconUrl": "https://...",
      "previewUrls": ["https://..."],
      "category": "viewer",
      "tags": ["viewer", "pdf"],
      "downloadCount": 12345,
      "rating": 4.8
    }
  ],
  "categories": [
    { "id": "viewer", "name": "文件查看" },
    { "id": "theme", "name": "主题外观" },
    { "id": "capability", "name": "能力工具" },
    { "id": "environment", "name": "环境容器" }
  ]
}
```

#### 4.14.2 市场功能

- 插件列表浏览（按分类/标签/搜索）
- 插件详情页（描述/预览图/权限/更新日志/评分）
- 一键安装/更新/卸载
- 已安装插件更新检测
- 官方/第三方筛选
- 下载量/评分展示

### 4.15 插件管理 UI

设置中新增"插件管理"页面：

```
插件管理
├── 顶部：搜索框 + 分类筛选（全部/查看器/主题/能力/环境）
├── 已安装（N个）
│   ├── 插件卡片：图标 + 名称 + 版本 + 类型标签 + 启用开关
│   ├── 点击进入详情：权限/设置/更新/卸载/崩溃日志
│   └── 排序：按名称/安装时间/大小
├── 可更新（M个）
│   └── 插件卡片 + "更新"按钮 + 更新说明
├── 插件市场入口
│   └── 跳转市场页面（内置浏览器）
└── 下载管理
    ├── 正在下载：进度条 + 取消
    └── 已下载未安装：安装按钮
```

### 4.16 调试与开发者模式

#### 4.16.1 开发者选项

- 从本地 ZIP 安装插件（绕过市场）
- 查看插件加载日志
- 查看插件注册表状态
- 模拟插件崩溃/禁用
- 插件性能监控（加载时间/内存占用）
- 插件 API 调用审计

#### 4.16.2 插件开发工具

- 插件模板生成器
- 清单格式校验工具
- 插件打包脚本（签名+压缩）
- 本地测试源（`~/MiniMe/plugin-local/`）

### 4.17 统计与遥测（可选）

- 插件可声明 `telemetry: true` 允许匿名使用统计
- 统计内容：安装量/启用率/使用频率/崩溃率
- 用户可在设置中全局关闭遥测
- 遥测数据不包含个人信息

### 4.18 备份与迁移

- 插件列表+设置可导出为备份文件
- 换机时导入备份，自动下载所有插件
- 插件数据目录可选择是否包含在备份中
- Core 版本升级时，自动检查插件兼容性

---

## 5. 核心 API 设计

### 5.1 PluginContext（插件运行上下文）

插件通过 `PluginContext` 与 Core 交互，这是唯一的通信通道：

```kotlin
interface PluginContext {
    // ── 基本信息 ──
    val pluginId: String
    val pluginVersion: String
    val coreVersion: String
    val deviceInfo: DeviceInfo

    // ── 受限 API ──
    fun getString(key: String): String           // 国际化
    fun getSettings(): PluginSettings            // 读取设置
    fun setSettings(key: String, value: Any)     // 写入设置
    fun getDataDir(): File                       // 插件数据目录
    fun getCacheDir(): File                      // 缓存目录
    fun getFileAccess(): FileAccess?             // 文件访问（需 file_read/file_write 权限）
    fun getNetwork(): NetworkAccess?             // 网络访问（需 network 权限）
    fun getTerminal(): TerminalAccess?           // 终端执行（需 terminal_exec 权限）
    fun getCapabilityRegistry(): CapabilityRegistry?  // 能力注册（需 capability_invoke）
    fun getEventBus(): EventBus                  // 事件总线
    fun getLogger(): PluginLogger                // 日志

    // ── UI 相关 ──
    fun showToast(message: String)
    fun showNotification(title: String, content: String)
    fun registerSettingsPage(schema: SettingsSchema)

    // ── 生命周期 ──
    fun reportError(error: Throwable)
    fun requestRestart(reason: String)
}
```

### 5.2 各类型插件的扩展接口

#### 5.2.1 文件查看器插件

```kotlin
interface FileViewerPlugin : MiniMePlugin {
    fun getSupportedTypes(): List<FileTypeSupport>
    fun createViewer(type: String): FileViewer?
    fun createEditor(type: String): FileEditor?
}
```

#### 5.2.2 主题插件

```kotlin
interface ThemePlugin : MiniMePlugin {
    fun getColorScheme(mode: ThemeMode): ColorScheme
    fun getTypography(): Typography
    fun getShapes(): Shapes
    fun getSplashConfig(): SplashConfig?
}
```

#### 5.2.3 能力工具插件

```kotlin
interface CapabilityPlugin : MiniMePlugin {
    fun getCapabilities(): List<CapabilityDefinition>
    fun createHandler(capabilityId: String): CapabilityHandler?
    fun getMcpServers(): List<McpServerConfig>
}
```

#### 5.2.4 环境容器插件

```kotlin
interface EnvironmentPlugin : MiniMePlugin {
    fun getRuntime(): ContainerRuntime?
    fun getImages(): List<ContainerImage>
    fun getProfiles(): List<EnvironmentProfile>
}
```

### 5.3 受限 API 权限检查

所有 `PluginContext` 的 API 在调用时检查权限：
- 有权限 → 返回正常对象
- 无权限 → 返回 null 或抛出 `SecurityException`
- 每次调用记录到审计日志

---

## 6. 实施路线图

### 阶段一：插件框架地基（P0）

**目标**：跑通插件的下载→安装→加载→启用→禁用→卸载全流程

**交付物**：
- plugin.json 清单格式定义 + 校验器
- 插件存储结构 + registry.json
- PluginHost 生命周期管理
- 下载中心（GitHub Releases + 镜像站备用）
- 权限系统（声明+授权+运行时检查）
- 插件管理 UI（列表+启用/禁用/卸载）
- 一个示例插件（纯资源，验证流程）

**不包含**：具体类型插件的实现、插件市场

### 阶段二：文件查看器插件（P0）

**目标**：将 tree-sitter、PDFium、Office 从核心包移出，做成文件查看器插件

**交付物**：
- FileViewerRegistry + 渲染器接口
- tree-sitter grammar 按需加载
- PDFium 插件化
- Core 最小文本查看器（降级方案）
- 首次打开文件时的下载提示
- 核心包体积验证（≤25MB）

### 阶段三：主题插件（P1）

**目标**：主题/字体/启动动画插件化，支持实时切换

**交付物**：
- ThemePlugin 接口 + 主题加载器
- 颜色/字体/形状/启动动画参数化
- 实时主题切换（不重启）
- 字体缓存机制
- 2-3 个官方主题插件示例

### 阶段四：能力工具插件（P2）

**目标**：MCP 与能力中心合并，统一能力注册，支持能力插件

**交付物**：
- CapabilityRegistry 统一注册中心
- MCP Server 作为能力来源接入
- CapabilityPlugin 接口
- 能力管理页面（统一展示所有来源）
- 2-3 个官方能力插件示例

### 阶段五：环境容器插件（P3）

**目标**：容器运行时和镜像插件化

**交付物**：
- ContainerRuntime 抽象
- 环境插件接口
- 容器创建从插件读取镜像
- 首次使用容器时的下载提示

### 阶段六：插件市场（P3）

**目标**：内置插件市场，支持浏览/搜索/安装/更新

**交付物**：
- 市场索引格式 + 托管（GitHub Pages）
- 市场 UI（列表/详情/搜索/分类）
- 更新检测 + 批量更新
- 插件评分/下载量展示

---

## 7. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| Android 动态加载代码限制 | 能力插件无法加载 dex | 阶段一先做纯资源插件验证；能力插件用声明式配置+Core 执行器，或用 JS/Lua 脚本引擎 |
| Google Play 政策限制 | 动态代码加载可能被拒 | 侧载 APK 不受限；若上架 Play，能力插件改用脚本引擎 |
| 插件安全风险 | 恶意插件窃取数据 | 权限系统+沙箱+签名+审计，第三方插件标记"未知来源" |
| 插件碎片化 | 多个查看器插件能力重叠 | 注册表合并+冲突检测+用户选择默认 |
| 首次使用等待 | 用户打开 PDF 时需要下载 | WiFi 下预下载常用插件；下载进度提示；离线降级 |
| 插件兼容性 | Core 升级后插件不工作 | minCoreVersion 校验+不兼容提示+插件作者更新机制 |
| 插件作者生态 | 初期插件少 | 官方先做 5-10 个核心插件；提供开发文档和模板 |
| 性能影响 | 插件加载增加启动时间 | 异步加载+懒加载（用到时才加载）+加载超时降级 |

---

## 附录 A：插件类型扩展指南

新增插件类型步骤：
1. 在 Core 中实现 `PluginHandler` 接口（处理该类型的加载/初始化/注册）
2. 在 `plugin.json` 的 `type` 字段使用新类型名
3. 在 `typeConfig` 中定义该类型的扩展字段
4. 在市场索引中添加新类型分类
5. 实现该类型的插件接口（如 `XxxPlugin : MiniMePlugin`）

## 附录 B：官方插件列表（规划）

| 插件 ID | 类型 | 说明 | 预估大小 |
|---------|------|------|----------|
| com.minime.viewer.full | file-viewer | 全量文件查看器（16语言+PDF+Office+图片+编辑器） | ~15-25MB |
| com.minime.viewer.code | file-viewer | 仅代码语法高亮（16语言） | ~2-3MB |
| com.minime.viewer.pdf | file-viewer | 仅 PDF 查看 | ~10-15MB |
| com.minime.theme.default | theme | 默认主题（Core 已内置，插件形式可选） | ~0.5MB |
| com.minime.theme.dracula | theme | Dracula 主题 | ~1MB |
| com.minime.theme.monokai | theme | Monokai 主题 | ~1MB |
| com.minime.capability.git | capability | 高级 Git 工具 | ~0.5MB |
| com.minime.capability.http | capability | HTTP 客户端工具 | ~0.3MB |
| com.minime.environment.ubuntu | environment | Ubuntu 基础环境 | ~150MB（镜像） |
| com.minime.environment.python | environment | Python 开发环境 | ~200MB（镜像） |

---

*本文档为设计阶段草案，所有内容均未落地代码。待用户明确指示后按阶段实施。*
