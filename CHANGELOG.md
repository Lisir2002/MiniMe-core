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

## [0.0.0.6] - 2026-09-22

> CI 全链路修复 + 工具卡片样式统一版本：修复 Android Release CI 的 bash 语法错误确保全量构建通过，统一对话流中所有工具调用卡片的渲染样式（消除 `TOOL→REASONING→REPLY` 序列下的兜底简洁样式）。本版本同时清理了非标准版本号 `manual-116`（手动触发 CI 时误创建的 Release，其 CI 修复变更已合并入本版本）。

### Fixed

- `[ci]` 修复 `android-release.yml` 第 180 行 `declare -A` 关联数组语法错误：元素分隔符从分号 `;` 改为空格，bash 严格模式下不再报 `syntax error near unexpected token`，Android Release CI 全量构建（assembleRelease + 签名 + 上传 APK）现已通过。
- `[ui]` 统一工具卡片渲染样式：修复 `TOOL→REASONING→REPLY` 消息序列时，工具调用因 REASONING 触发 `flushPendingTools()` 而走兜底独立 TOOL 单元（绿色圆点 + usage 简洁样式）的问题。现在 REASONING 不再触发 flush，暂存的工具调用作为独立 `EmbeddedToolAccordion` 渲染单元放在思考过程之前，所有工具卡片统一使用带标题（"N 次工具调用"）+ 锤子图标的样式，渲染顺序保持不变（工具调用 → 思考过程 → AI 文本）。

## [0.0.0.5] - 2026-09-22

> 混合模式 v2 样式迭代版本：去掉对话流外层多余气泡容器（TaskAccordion 白色卡片/外层头部/子容器标签头全部去除），消息直接在页面背景上展示；消息尾部新增时间戳显示；输入栏优化（">" 符号与光标同行、边距收紧、模式按钮恢复文字标签）。

### Changed

- `[ui]` 去掉对话流外层气泡：TaskAccordion 白色大卡片改透明（去边框/阴影），去掉外层头部（图标/标题/N条/时间/展开箭头）和"用户消息"/"回复"标签头（图标/文字/序号/箭头），消息直接在页面背景上展示，组间距 16dp，展开/折叠逻辑保留且默认展开。
- `[ui]` 消息尾部新增时间戳显示（HH:mm 格式，10sp 弱化色，右对齐，复用已有 `timestamp` 字段），快捷按钮常驻轻量化（图标间距收紧至 4dp）。
- `[ui]` 输入栏优化：左侧 ">" 符号与输入框改同一行（Row 垂直居中），上下边距收紧至 10dp，模式按钮从圆形图标恢复为胶囊形文字标签（BUILD/PLAN/AUTO，复用项目 ChatAccent 三色语义色，点击切换逻辑保留）。

## [0.0.0.4] - 2026-09-22

> 混合模式对话流样式打磨 + CI 修复版本：以 Cursor/Claude 设计经验为参考，将对话流重构为「用户品牌色气泡 + AI 无气泡左侧竖线 + 工具独立暗色块」的混合模式，同步修复 CI 全部失败（YAML 语法错误 + android-actions 不兼容），CI 现已全绿。

### Changed

- `[ui]` 对话流混合模式样式打磨：用户消息轻量品牌色气泡（#3B82F6，右下小圆角指向用户），AI 回复去掉气泡改左侧 2dp 蓝色竖线标识，工具调用独立暗色块（等宽字体 + ANSI 颜色编码：命令绿/输出灰/错误红），思考过程默认折叠条，流量统计轻量化（10sp 右对齐弱化色），去掉 TaskAccordion 外层浅绿/浅蓝容器背景。
- `[ui]` 暗色/亮色模式配色统一调整：暗色页面底 #0F172A、AI 文字 #E2E8F0、工具块 #1E293B；亮色页面底 #F8FAFC、AI 文字 #0F172A、工具块 #F1F5F9；文字对比度均 ≥4.5:1。
- `[ui]` 输入栏视觉升级：圆角 20dp、左侧蓝色 ❯ 符号、BUILD 按钮改为 28dp 圆形品牌色图标按钮、工具栏图标统一 20dp 弱化色。
- `[ui]` Markdown 渲染样式调整：代码块背景 #1E293B、引用块 2dp 蓝色竖线、行内代码浅灰底，适配 AI 无气泡新样式。

### Fixed

- `[ci]` 修复 CI 全部失败：根因是 `ci.yml` 第 69 行 `name: Security gate: CipherDriverFactory not stub` 未加引号，冒号+空格被严格 YAML 解析器当作嵌套 mapping，导致 workflow 解析立即失败（零 job 创建）。同时替换 `android-actions/setup-android@v3`（与 GitHub Runner Node 24 不兼容），改用 ubuntu-latest 预装 SDK + sdkmanager 手动补装方案。受影响文件：ci.yml / android-release.yml / dependency-audit.yml / weekly-health-check.yml。CI 现已全绿（Build release + Run unit tests 全部通过）。

## [0.0.0.3] - 2026-09-22

> Agent-First 对话流重构版本：以智能体为中心的任务协作界面，新增工具调用卡片（ToolCallCard）与任务卡片（TaskCard），覆盖 6 种工具状态与 7 种任务状态，配套错误诊断、重试机制与样式清爽化。所有新组件通过 feature flag 双开关控制，默认关闭，现有行为完全不变。

### Added

- `[ui]` 新增 Agent-First 工具调用卡片（ToolCallCard）：6 种状态（待执行/运行中/成功/失败/已取消/超时），折叠/展开、实时输出自动滚动与「↓有新输出」提示、大输出截断、复制命令/输出、环境状态条。
- `[ui]` 新增 Agent-First 任务卡片（TaskCard）：7 种状态（空闲/规划中/运行中/等待审批/已完成/失败/已取消），步骤列表与进度条（Running 脉冲高亮）、子分组渲染（用户/回复/工具/思考）、文件产物区域、底部操作栏（停止/查看日志/重试任务）。
- `[agent]` 新增工具调用重试（retryTool）与任务重试（retryTask）：失败的工具调用可一键重试，失败的 Agent 任务可从原始用户请求重新执行。
- `[agent]` 新增工具执行错误诊断：内置 8 种常见错误模式识别（apk 数据库锁/命令未找到/权限不足/磁盘不足等），提供修复建议与一键复制修复命令（建议性，不自动执行）。
- `[ui]` 新增 feature flag 双开关（`useNewToolCallCard` / `useNewTaskCard`）：独立控制新组件启用，默认关闭，可逐步灰度。

### Changed

- `[ui]` AI 对话气泡轻量化：左对齐限宽 90%、去除 1dp 边框改 2dp 轻投影、内边距增大至 16/12dp、圆角增大至 14dp。
- `[ui]` 暗色色阶重定义：页面底 #060D17、AI 气泡 #0E1A2B、工具气泡 #152438、输入栏 #1A2D44；亮色页面底 #F8FAFC、工具气泡 #F1F5F9；文字对比度均 ≥4.5:1。
- `[ui]` 连续同角色消息视觉分组：组内 4dp 间距与小圆角、组间 16dp 间距与大圆角，单条消息四角大圆角。
- `[ui]` 输入栏视觉分离：输入框 12dp 圆角 + 0.5dp 淡边框、发送/附件/模式切换按钮图标统一 20dp。

### Fixed

- `[agent]` 修复 P0：Agent 任务/工具调用 Running 状态推导失效 — `TaskGroup.isStreaming` 仅在文本流式时为 true，工具执行期间状态推导额外检查 `runningTool` 匹配，确保工具执行期间正确显示 Running 状态而非误判为 Completed。

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