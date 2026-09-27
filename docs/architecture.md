# MiniMe-core 架构文档

## 模块依赖图

```
┌─────────────────────────────────────────────────────────────┐
│                        app (主应用)                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  feature/ (功能模块)                                   │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐     │  │
│  │  │  agent  │ │ browser │ │settings │ │terminal │ ... │  │
│  │  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘     │  │
│  │       │            │            │            │         │  │
│  │       └────────────┴────────────┴────────────┘         │  │
│  │                    │                                    │  │
│  │              core/ (核心层)                             │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐               │  │
│  │  │   data   │ │   ui     │ │  util    │               │  │
│  │  └──────────┘ └──────────┘ └──────────┘               │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │terminal-emulator│  │ terminal-view   │ (外部依赖)        │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

## 数据流图

```
用户操作 → UI层 (Compose) → ViewModel → Repository → DataSource
                ↑                                        │
                │                                        ↓
                └──────── StateFlow ◄────────────── 数据库/网络/文件
```

### 数据层架构

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  (Compose Screens + ViewModels)                 │
└──────────────────────┬──────────────────────────┘
                       │ StateFlow
┌──────────────────────▼──────────────────────────┐
│              Repository Layer                    │
│  (业务逻辑编排 + 数据聚合)                        │
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

## 模块说明

### feature/agent (Agent 核心)
- **domain**: 业务逻辑层，包含 30 个子目录
  - `provider/`: 模型供应商适配（OpenAI/Anthropic等）
  - `tool/`: 工具系统（文件/终端/Git等）
  - `container/`: 容器管理（PRoot Linux 容器）
  - `mcp/`: MCP 协议支持
  - `session/`: 会话管理
  - `workflow/`: Agent 工作流
  - `zth/`: 零幻觉容忍系统
- **presentation**: UI 层（Compose）
- **data**: 数据层

### feature/browser (内置浏览器)
- **domain**: BrowserController（已拆分 JS 常量到 BrowserJsScripts）
- **presentation**: 浏览器 UI

### feature/settings (设置)
- **presentation/component**: 设置页面组件（已统一目录）

### feature/terminal (终端)
- **domain**: 终端引擎
- **presentation**: 终端 UI

### core/ (核心)
- **data**: 数据层基础
- **theme/components**: 共享 UI 组件（已统一目录）
- **util**: 工具类
- **security**: 安全相关
- **performance**: 性能监控

## 关键技术栈

| 领域 | 技术 |
|------|------|
| UI | Jetpack Compose + Material3 |
| 依赖注入 | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 网络 | Retrofit + OkHttp + Ktor |
| 数据库 | SQLDelight + SQLCipher |
| 序列化 | Kotlinx Serialization + Gson |
| 构建 | Gradle + KSP + Version Catalog |
| 静态检查 | detekt |
| 容器 | PRoot (用户态 Linux) |
| 终端 | Termux terminal-emulator + terminal-view |

## 构建配置

- **JDK**: 17
- **minSdk**: 26
- **targetSdk**: 28 (PRoot W^X 限制)
- **ABI**: release arm64-v8a only
- **R8**: fullMode 启用
- **构建缓存**: 启用
- **配置缓存**: 启用
