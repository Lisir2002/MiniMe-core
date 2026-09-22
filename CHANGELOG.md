# Changelog

本文件记录 MiniMe-core 的用户与开发者可见变更（**第 2 层 · 开发者视角**），采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范；版本号遵循四段式语义（见 [AGENTS.md](./AGENTS.md#版本号规范)）。三层日志的分工与口径见 **AGENTS.md「版本日志」**。

- 最新版本置顶，倒序排列。
- 日期用 ISO 8601（`YYYY-MM-DD`）。
- 条目按「效果」而非「实现」撰写；内部噪音（纯格式、纯测试、非行为 refactor）不收录。
- **Breaking Change 必须用 ⚠️ 显著标注并附迁移说明。**

## [Unreleased]

### Added

- _（新增）_

### Changed

- _（变更：兼容性改进）_

### Deprecated

- _（废弃：将在未来版本移除）_

### Removed

- _（移除）_

### Fixed

- _（修复：用户可感知的缺陷）_

### Security

- _（安全：漏洞修复与加固）_

## [0.0.0.2] - 2026-09-22

> 安全加固版本：落地数据库全库加密基础设施（P1 阶段），修复 SQLCipher 空实现与加密失败静默降级两项 P0 安全缺陷。默认仍为明文，用户可在设置页手动开启加密；P2/P3 阶段将逐步推进默认加密与强制加密。

### Added

- `[db]` 数据库全库加密支持（SQLCipher 4.5.4）：6 个物理库可选 AES-256 加密，默认明文，设置页一键开启后自动迁移全部存量数据。
- `[db]` 数据库加密迁移引擎：明文↔加密无损迁移，7 步流程（迁移前快照 → 类型感知逐表拷贝 → 行数+校验和+抽样三重校验 → renameTo 原子替换 → 清理），支持反向迁移（加密→明文）。
- `[db]` 迁移崩溃恢复：启动时自动检测迁移中断状态并安全回滚，覆盖快照/拷贝/校验/替换全流程 10 个崩溃时间点，操作仅限文件系统不打开数据库（< 100ms）。
- `[db]` 双层密钥管理：Android Keystore MasterKey（AES-256-GCM，硬件-backed）包裹每库独立 DEK，包裹密文存 SharedPreferences，密钥不落明文、不出 Keystore。
- `[security]` 新增 `SECURITY.md` 安全策略文档。

### Changed

- `[db]` 数据层 L0 引擎新增 `RoutingDriverFactory`：根据每库加密状态动态选择明文/加密驱动，业务层、迁移引擎、备份全部无感知。
- `[db]` Application 启动流程集成 `CrashRecovery`：首次数据库访问前执行崩溃恢复，确保迁移中断后数据不损坏。
- `[settings]` 安全设置页新增「数据库加密」区块：加密状态显示、开启/关闭开关、迁移进度条（当前库/当前表/百分比）、失败重试按钮。
- `[build]` 新增 `net.zetetic:android-database-sqlcipher:4.5.4` 依赖与 ProGuard keep 规则。
- `[ci]` CI 新增 `CipherDriverFactory` 空实现静态检测门禁，阻止加密占位实现合入。

### Security

- `[security]` 修复 P0：SQLCipher 加密为空实现 — `CipherDriverFactory.create()` 从 `error()` 占位改为完整实现（`SupportFactory` + fail-close + passphrase 用后 `fill(0)` 内存擦除）。
- `[security]` 修复 P0：加密初始化失败静默降级明文 — 密钥管理严格 fail-close，密钥获取/驱动创建任何环节失败抛 `DatabaseEncryptionException`，绝不回退明文或写空串。
- `[docs]` 修正 `README.md` / `AGENTS.md` 中「SQLCipher 加密读写」虚假声明，改为真实状态描述（P1 可选加密，默认明文）。

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