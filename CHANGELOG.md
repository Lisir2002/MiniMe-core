# Changelog

本文件记录 MiniMe-core 的用户与开发者可见变更（**第 2 层 · 开发者视角**），采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范；版本号遵循四段式语义（见 [AGENTS.md](./AGENTS.md#版本号规范)）。三层日志的分工与口径见 **AGENTS.md「版本日志」**。

- 最新版本置顶，倒序排列。
- 日期用 ISO 8601（`YYYY-MM-DD`）。
- 条目按「效果」而非「实现」撰写；内部噪音（纯格式、纯测试、非行为 refactor）不收录。
- **Breaking Change 必须用 ⚠️ 显著标注并附迁移说明。**

## [0.0.0.2-rc1] - 2026-09-14

> 预发行（未转正）。首个独立设计系统样例页 + iOS 简约主风格落地，前端 UI 层无破坏性变更。

### Added

- `[ui]` 新增 `newui` 设计系统样例页（DesignGallery）：汇集原子/分子组件族，可视化验证 iOS 简约主风格（iOS 蓝 `#0A84FF` 主色、`#F2F2F7` 浅底 / `#000000` 深底、label 分级文字）。
- `[ui]` 新增排版令牌（AppType）：Large Title / Section Header 等 iOS 风格排版落地，样板页按令牌渲染。

### Changed

- `[ui]` 颜色 / 度量 / 圆角 / 阴影全面令牌化：主色调改 iOS 蓝，圆角 `AppRadius`、阴影 `AppElevation`、间距 / 图标 `AppSpacing` / `AppSizing` 集中在生成令牌；AppChip / AppKeyCap / AppCheckRow 去除圆角、阴影硬编码。
- `[ui]` 滑扫组件 AppSwipeAction 照 iOS `swipeActions` 重绘：扁平纯色块、整条圆角容器、无阴影 / 无描边 / 无渐变；保留互斥展开、全滑触发（Dismiss）、阻尼回弹；新增呈现阶段回调 `AppSwipePhase`（对齐 iOS 27 `onPresentationChanged`）。

## [0.0.0.1] - 2026-09-14

> ⚠️ 首个版本（项目初始化），承载完整能力基线，无迁移前状态可比。

### Added

- `[core]` 初始化 MiniMe-core：运行于 Android 真机/虚拟机的 AI 编程工具（`app` / `terminal-emulator` / `terminal-view` / `newui` 四模块 + 双层数据仓库 `datalayer`）。
- `[agent]` 内置 AI Agent，可读写文件、执行 Shell、运行构建；支持多 Provider 适配（Anthropic / OpenAI / Gemini）。
- `[container]` 内置 PRoot + Alpine Linux 容器与终端（arm64-v8a / x86_64 双架构，运行时按宿主选择）。
- `[terminal]` 本地 Termux 终端（`terminal-emulator` + `terminal-view`）+ `newui` 全新设计系统/UI 层。
- `[remote]` 远程 SSH 后端（exec channel + SFTP 文件 + 交互终端会话）。
- `[mcp]` MCP（Model Context Protocol）客户端 + 内置 MCP 服务器（应用成为「客户端 + 服务器」双角色，手机当开发后端）。
- `[git]` Git 集成与可视化操作；`credentials` 集中式凭据管理（UI Git / AI Bash / 终端 git 三端共用）。
- `[backup]` AES 加密备份与恢复；`workspace` 工作区与文档管理。

### Changed

- `[build]` 四段式版本号由 Git Tag 动态推导（`0.0.0.1`），唯一官方签名密钥入库，CI 一键发版。
- `[db]` 数据层采用 SQLDelight V2 六库拓扑（KV / Document / Queue / Blob / TimeSeries + 业务聚合）。

### Security

- `[security]` 凭据加密存储（`CredentialEncryptor` / `DEKManager`）+ HostKey 校验 + AES 备份加密，敏感信息不落明文。