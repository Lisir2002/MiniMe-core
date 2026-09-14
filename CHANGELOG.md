# Changelog

本文件记录 MiniMe-core 的用户与开发者可见变更（**第 2 层 · 开发者视角**），采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范；版本号遵循四段式语义（见 [AGENTS.md](./AGENTS.md#版本号规范)）。三层日志的分工与口径见 **AGENTS.md「版本日志」**。

- 最新版本置顶，倒序排列。
- 日期用 ISO 8601（`YYYY-MM-DD`）。
- 条目按「效果」而非「实现」撰写；内部噪音（纯格式、纯测试、非行为 refactor）不收录。
- **Breaking Change 必须用 ⚠️ 显著标注并附迁移说明。**

## [0.0.0.2-rc11] - 2026-09-14

> 预发行（未转正）。对话流组件族细化全部落地：新增思考折叠块 / 计划审批卡 / 附件卡 / 工具链时间线 / 结果摘要卡；工具调用卡补齐三档审批 + 超时降级 + 记忆徽标；消息行接入左滑操作；Markdown 渲染补齐标题 / 引用 / 有序列表 / 表格 / 链接；MCP App 就绪态画布强化真实感。DesignGallery 控制条改 `FlowRow` 修复演示按钮积压。无破坏性变更。纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppThinkingBlock` 思考折叠块（分子组）：Agent 推理过程流式实时追加（对齐 reasoning delta 事件），超过行数阈值自动折叠，与正文视觉分离（缩进 + 弱化底色）。
- `[ui]` 新增 `AppPlanCard` 计划审批卡（分子组）：计划标题 + 步骤清单（完成 / 进行中 / 待定三态）+ 待定选择提问 + 批准 / 继续细化操作，对齐 PlanTool 审批链路。
- `[ui]` 新增 `AppAttachmentCard` 附件卡（分子组）：图片 / 文件类型缩略展示、大小与容器路径元信息、点击预览，适配 sendFile 展示型工具。
- `[ui] 新增 `AppToolChainTimeline` 工具链时间线（分子组）：一次任务内连续多次工具调用的串联视图——节点状态色点 + 连接线 + 每步标题 / 服务器徽标 / 耗时，头部汇总「N 次调用 · 总耗时」，替代 N 张全卡堆叠刷屏（对齐 LangChain / Vercel AI SDK step 时间线范式）。
- `[ui]` 新增 `AppToolSummaryCard` 结果摘要卡（分子组）：工具链收尾后对 N 个工具结果做意图归纳的过渡卡，`Summarizing` 态带打字指示器，`Done` 态正文超行折叠展开，头部保留「N 个结果」计数徽标。
- `[ui]` `AppMarkdownText` 补齐渲染：新增标题（`#`~`###`）、引用块（`>`）、有序列表（`1.`）、表格（`|` 分隔）、行内链接（`[text](url)`）。
- `[ui]` `AppMcpAppCard` 就绪态画布真实感强化：模拟浏览器镀铬（红黄绿圆点 + 地址胶囊）+ 指标瓷砖（构建次数 / 平均耗时 / 失败率）+ 柱状图，直观呈现交互式界面效果。
- `[ui]` `AppMessageRow` 接入滑扫操作：消息行左滑露出复制 / 重试 / 删除操作按钮（对齐 iOS `swipeActions`），同批只开一项、点击内容收起。
- `[ui]` DesignGallery「AI 对话流」新增「工具链」「结果摘要」「审批超时」演示场景；控制条由 `Row` 改 `FlowRow` 自动换行，修复 13 个演示按钮横向积压；新增气泡 / 指示器 / Markdown 分子直出区与跑马灯示例。

### Changed

- `[ui]` `AppToolCallCard` 审批链路补齐：三档选择（拒绝 / 本次放行 / 始终允许 → 记忆）全链路闭环，新增审批超时降级展示态与「已记忆为始终允许」徽标态。

### Fixed

- `[ui]` 修复 `AppMarkdownText` 缺失 `androidx.compose.ui.draw.clip` 导入导致的编译失败。

## [0.0.0.2-rc10] - 2026-09-14

> 预发行（未转正）。对话流组件族深化「工具 / 技能 / MCP 调用」的特殊样式表现：工具调用卡补齐**实时输出（Streaming）**与**人工审批（Intervention）**两个 surface（对齐 LobeHub 六 surface 与 assistant-ui `requires-action`），新增 **MCP App** 交互式界面渲染卡（对齐 MCP Apps 官方 extension 的沙箱 iframe 模式），技能调用卡补成功态耗时；无破坏性变更。纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppMcpAppCard` MCP App 卡（分子组）：工具声明 `_meta.ui.resourceUri` 后 Host 渲染沙箱 iframe 交互式界面（MCP Apps 规范）。头部含 `APP` 徽标 + MCP 服务器胶囊 + `ui://…` 地址；16:9 深色画布按 `Loading / Ready / Error` 三态展示（加载骨架 / 交互式仪表盘占位 / 失败重试）；底部 `sandbox · iframe` 安全提示 + 刷新 / 全屏操作（Portal 占位）。
- `[ui]` DesignGallery「AI 对话流」新增「待审批」「MCP App」两个交互演示：待审批工具可**允许→执行→成功**或**拒绝→失败**闭环；MCP App 演示加载→就绪过渡与刷新重载。

### Changed

- `[ui]` `AppToolCallCard` 打磨：状态机新增 `AwaitingApproval`（琥珀警示 + 卡片尾部**允许 / 拒绝**操作行，对齐 LobeHub humanIntervention 与 assistant-ui `requires-action`，可承载工具权限审批链路）；新增 `streamOutput`——`Running` 态直接内联等宽终端块 + 闪烁方块光标（Streaming surface，适配 Shell stdout 实时可见）。
- `[ui]` `AppSkillCallCard` 补成功态耗时显示（`· 900ms` / `1.6s`），与工具卡元信息对齐。

## [0.0.0.2-rc9] - 2026-09-14

> 预发行（未转正）。修复 rc8 CI 在 Android SDK 安装阶段失败的问题（新版 cmdline-tools 已移除独立 `tools` 包）；功能内容与 rc8 一致（对话流组件族）。纯 CI 配置 + UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Fixed

- `[ci]` 修复 `android-release.yml`：`setup-android` 的 `packages` 移除已废弃的 `tools`（`sdkmanager tools` 报 `Failed to find package 'tools'` 导致 rc8 构建失败）；`platform-tools` 自带，无需显式安装。

## [0.0.0.2-rc8] - 2026-09-14

> 预发行（未转正）。newui 设计系统新增「AI 对话流」组件族（消息气泡状态机 / 消息行 / 流标记 / 滚动容器 / 轻量 Markdown 渲染），并在 DesignGallery 内置可交互演示；无破坏性变更。纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppChatBubble` 对话气泡（分子组）：内置消息状态机（`Pending → Streaming → Complete / Error`），流式回复带闪烁光标，失败态内联重试按钮，颜色归一化到 `AppColor` 令牌。
- `[ui]` 新增 `AppMessageRow` 消息行：双侧头像（AI/用户）、头部信息（姓名/时间戳）、长按操作区（复制/重试/删除），同角色连续消息支持 `grouped` 合并隐藏头像。
- `[ui]` 新增 `AppChatMarker` 消息流标记：日期分隔、系统消息、工具调用卡三种形态，工具卡支持运行中状态指示。
- `[ui]` 新增 `AppMessageScroller` 滚动容器：反向 `LazyColumn` 列表，新消息自动跟随滚动，顶部加载历史 + 底部跳底按钮。
- `[ui]` 新增 `AppMarkdownText` 轻量 Markdown 渲染：支持段落、粗体、行内代码、代码块、无序列表，适配 AI 回复常见排版。
- `[ui]` DesignGallery 新增「AI 对话流」交互演示区块：流式回复 / 模拟失败重试 / 工具调用三种场景一键触发。

### Changed

- `[ui]` DesignGallery 对话流演示区接入 `AppMessageScroller` + 状态机驱动，支持真实流式逐字回复与失败重试闭环。

## [0.0.0.2-rc7] - 2026-09-14

> 预发行（未转正）。newui 设计系统弹窗/通知/快捷键全系归一到 `AppColor` 令牌并强化交互，新增「可搜索下拉筛选」「激活筛选 token 行」两个筛选组件；无破坏性变更。纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppDropdownFilter` 可搜索下拉筛选（分子组）：参考 iOSDropDown 交互，触发胶囊内联展示当前已选（多选折叠为「label · 已选 N 项」），点击展开带内置搜索框的下拉面板；支持单选/多选语义、选中项高亮半字重 + 尾部品牌弹簧勾、实时关键字过滤 + 无匹配态，激活态描边/箭头平滑回转。
- `[ui]` 新增 `AppFilterToken` / `AppFilterTokens` 激活筛选 token 行（参考 iOS 26 `.searchable(tokens:)`）：已生效条件以「分组名 · 值」内联展示，品牌小圆点高亮，可逐个移除（✕）+「清除全部」批量撤销，空态自动隐藏。

### Changed

- `[ui]` `AppDialog` / `AppDialogs`（含评分、确认等变体）、`AppDialogsAdvanced` 弹窗家族统一归一到 `AppColor` 令牌（标题 `BrandInk`、正文 `LabelSecondary`、确认键 `BrandPrimary`、卡底 `BrandCard`、描边 `SeparatorOnLight`），入场动画改为弹簧回弹缩放 + 淡入，阴影 `AppElevation.Z4`。
- `[ui]` `AppNotificationItem` 强化为 iOS 简约通知行：图标 `accentColor@12%` 浅底 + 按压弹簧缩放反馈（替换 M3 涟漪），标题 `BrandInk`/正文 `LabelSecondary`、未读半字重 + 强调色指示点。
- `[ui]` `AppKeyCap` / `AppKeyCombo` 快捷键强化：键帽纵向渐变底 + `SeparatorOnLight` 发丝描边 + 底部强调色光缝，等宽字体；组合以 `+` 连接、间距令牌排版。
- `[ui]` 现有筛选组件族（`AppFilterField` / `AppChecklistFilter` / `AppRangeFilter` / `AppRangeValuePill` / `AppDateFilter` / `AppFilterSheet`、`AppFilterChip`）颜色全部从 `MaterialTheme.colorScheme` 迁移到 `AppColor` 令牌，与全系 iOS 简约风格一致。

## [0.0.0.2-rc6] - 2026-09-14

> 预发行（未转正）。newui 设计系统「工作台图标」「终端日志窗口」两处按 iOS 简约风格深化并强化，无破坏性变更；纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppTerminalLog` 终端日志面板（分子组），替换样板页原单行跑马灯展示：深色控制台卡（`AppColor.OnDarkSurface`）+ 等宽字体，标题栏呼吸态状态点 + 「● 运行中」小字；正文按 `LogLevel` 分级染色（`StatusSuccess`/`StatusWarning`/`StatusDanger`）并自动滚动到底，底部纵向渐隐 scrim + 闪烁光标示意仍在线；用 `animateContentSize` 让新日志流入时高度平滑伸展。

### Changed

- `[ui]` `AppDock` 深化为 iOS 简约图标坞：选中态染 `BrandPrimary` + 浅蓝胶囊底 + 指示点弹簧缩放并淡入淡出，未选中态灰标无底；悬浮气泡标签用 `Box` 叠加不参与测量，消除选中/未选中布局跳变；本次强化新增：容器 `AppElevation.Z2` 悬浮阴影、按压浅色调层反馈（`collectIsPressedAsState`）。

## [0.0.0.2-rc5] - 2026-09-14

> 预发行（未转正）。修复滑扫按钮不可见的真根因（rc3/rc4 修复了真实但独立的缺陷，均未触达本遮挡源），无破坏性变更。

### Fixed

- `[ui]` 修复 AppSwipeAction 左滑后操作按钮整条不可见的**真根因**：内容层修饰符链中 `background(surface)` 位于 `offset{}` 之前——`offset` 只平移链中位于其内侧的节点，背景矩形固定在初始位置绘制、不随拖拽移动，把底层动作栏永久盖住（真机表现：内容文字滑开、右侧只剩一条背景空白）。修正链序为 `fillMaxWidth() → offset{} → background → clickable → anchoredDraggable`（对齐 M3 `SwipeToDismissBox` 标准链序），背景随内容整体平移、右缘自然露出动作栏。同步清理 rc4 遗留的「透明度渐入」过期注释。
  - 根因佐证：jetpackcompose.cn 拖动篇「错误示例2（background 在 offset 前面）不跟手」；语义树单测断言布局位置、不感知兄弟节点像素遮挡，故此前 CI 三连绿而真机不可见（语义测试天然盲区，本缺陷属像素级遮挡）。

## [0.0.0.2-rc4] - 2026-09-14

> 预发行（未转正）。重构滑扫按钮揭示机制为 iOS 原生的「位置驱动顺缝露出」，无破坏性变更。

### Changed

- `[ui]` AppSwipeAction 滑扫按钮揭示从「透明度整体淡入」改为「位置驱动逐格揭开」：按钮绘制在底层子画布，顶层不透明内容层平移多少即从右缘揭开多少（对齐 iOS `swipeActions` 的 `UIScrollView` 天然露出手感）；删除 `AppSwipeButton` 的 `graphicsLayer{ alpha }` 淡入，`LocalSwipeReveal` 仅保留为可点击门控（`enabled = reveal > 0.05f`）。

## [0.0.0.2-rc3] - 2026-09-14

> 预发行（未转正）。修复滑扫组件的可见性缺陷，无破坏性变更。

### Fixed

- `[ui]` 修复 AppSwipeAction 滑扫展开后操作按钮整条不可见：底层动作栏改用 `matchParentSize()`（原 `fillMaxSize()` 在 `verticalScroll` 等高度无界父级下把动作栏压成 0 高，导致内容层左移但蓝/红按钮不绘制）。两个滑动场景测试补按钮高度 > 0 断言，防回归。

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