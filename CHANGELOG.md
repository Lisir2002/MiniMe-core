# Changelog

本文件记录 MiniMe-core 的用户与开发者可见变更（**第 2 层 · 开发者视角**），采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范；版本号遵循四段式语义（见 [AGENTS.md](./AGENTS.md#版本号规范)）。三层日志的分工与口径见 **AGENTS.md「版本日志」**。

- 最新版本置顶，倒序排列。
- 日期用 ISO 8601（`YYYY-MM-DD`）。
- 条目按「效果」而非「实现」撰写；内部噪音（纯格式、纯测试、非行为 refactor）不收录。
- **Breaking Change 必须用 ⚠️ 显著标注并附迁移说明。**

## [0.0.0.2-rc16] - 2026-09-15

> 预发行（未转正）。架构安全审计后的一轮加固：SFTP 强制 HostKey 校验、MCP DELETE 端点补鉴权、DEK 轮换改安全两阶段、备份/提取规则排除凭据库、CI 接入 OSV SCA；附带修复 CI 长期红屏的废弃 `tools` 包。纯安全与 CI 配置改动，无 UI / AI 工作流 / prompt / schema 变化。

### Security

- `[remote]` `SftpSyncClient` 构造函数改为必传 `HostKeyVerifier`，删除 `PromiscuousVerifier` 兜底；现有 3 个调用点（RemoteRepository）本就传 TOFU verifier，杜绝 SSH 中间人。
- `[mcp]` `McpHttpServer` 的 `DELETE /mcp` 端点补 Bearer 校验（此前 POST/GET 有鉴权，DELETE 裸奔）。
- `[core/security]` 修复 `CredentialEncryptor.scheduleRotateDek` 原实现"换新 DEK 却不重写已加密字段，导致存量凭据用新 DEK 解旧密文全部报废"的缺陷；改为暂存旧 DEK → 各 `CredentialFieldRewriter` 旧解新加 → 全部成功才落新 DEK，任一字段失败或重写器集合为空则不切换。
- `[backup]` `full_backup_rules.xml` / `data_extraction_rules.xml` 排除凭据库 `minime_credentials_v2.db`，防止 Android Auto Backup 与 adb backup 把加密凭据库带出设备。

### Added

- `[core/security]` 新增 `CredentialFieldRewriter` 接口（DEK 轮换字段重写契约，当前空集安全，后续各加密域按需 `@IntoSet` 接入）。
- `[ci]` `.github/workflows/dependency-audit.yml` 接入 `google/osv-scanner-action@v1.9.0` 真实 SCA。

### Fixed

- `[ci]` `ci.yml` / `dependency-audit.yml` / `weekly-health-check.yml` 移除已废弃的 `tools` 包（新版 cmdline-tools 无此包，`sdkmanager tools` 报 `Failed to find package 'tools'` 导致 CI 长期红屏），与 `android-release.yml` 对齐。

## [0.0.0.2-rc15] - 2026-09-15

> 预发行（未转正）。rc14 上机截图核查后的第二轮显示修复：跑马灯两份内容叠印重影、庆祝彩带挤成一条线、图标坞选中标签气泡永不显示、窄屏令牌速率行与图标坞横向重叠、终端末行被渐隐层遮盖，外加样板观感与文案修正。纯 UI 设计系统（`:newui`）改动，app 模块未动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Fixed

- `[ui]` 修复跑马灯叠印重影：`AppMarquee` 上轮只给子 `Box` 加 `wrapContentWidth(unbounded)`，Row 仍以有界宽度依次测量两份内容，第二份剩余空间不足时节点被压窄、内容居中到负 x 起点，两份内容在同一区域叠印；改为 Row 层 `wrapContentWidth(align = Start, unbounded = true)` 无界测量 + Start 摆放。
- `[ui]` 修复庆祝彩带挤成一条水平线：`AppConfetti` 全部粒子同帧从顶部出发、仅靠慢速差散开，最快粒子一周期落不到 60% 高度；改为每粒子独立 `phaseY` 错峰 + `fall`（0.85~1.35 倍周期），一周期从顶外（-0.15）完整飘到底外（1.20），任意时刻全高度均匀分布。
- `[ui]` 修复 `AppDock` 选中态悬浮标签气泡永不显示：Row 上的 `.clip(Pill)` 把 `offset` 画到胶囊边界外的气泡整体裁掉；移除硬 clip，改用带形 `background(shape)`，外观等价且不再裁剪界外绘制。
- `[ui]` 修复窄屏横向重叠：DesignGallery「令牌速率」演示行左列未占剩余空间，窄屏总宽超限时与 `AppDock` 互相叠压；左列加 `weight(1f)`，超限时压缩文本区。
- `[ui]` 修复终端日志末行被盖：`AppTerminalLog` 滚到底时最后一行 / 光标被底部 32dp 渐隐 scrim 遮盖看似裁切；正文补等高 bottom padding。

### Changed

- `[ui]` DesignGallery 观感微调：裸全宽进度条补「执行进度」标签；`AppSpotlightCard` 演示文案由顶对齐改垂直居中，消除卡内大片空白。
- `[ui]` 修正 17 处分区标题错别字：「分子组建族」→「分子组件族」。

## [0.0.0.2-rc14] - 2026-09-15

> 预发行（未转正）。新版 UI 设计系统深度核查修复轮：修复样板上多处「不能用 / 显示不对」的问题——附件演示第二个附件 key 复用导致 LazyColumn 崩溃、工具卡漏传审批参数导致三档审批/超时/记忆徽标不渲染、时间线连接线在滚动容器中塌陷不可见、跑马灯文本竖排、AppShell 顶栏/底栏背景未延伸进系统栏区域。纯 UI 设计系统（`:newui`）改动，app 模块未动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Fixed

- `[ui]` 修复附件演示崩溃（崩溃级）：`chatAttachment()` 第二个附件用 `id+1` 作 key 但 `chatSeq` 未同步自增，点击「附件」后任何演示项复用相同 key，LazyColumn key 冲突直接崩溃；改为每个附件独立自增取号。
- `[ui]` 修复工具卡审批功能不可用：`ChatItem.Tool` → `AppToolCallCard` 漏传 `onChoice` / `alwaysDisabled` / `alwaysDisabledReason` / `approvalExpired` / `approvalRemembered`，导致「待审批」三档 /「审批超时」降级 /「已记住」徽标不渲染；已补全。
- `[ui]` 修复时间线连接线塌陷：`AppToolChainTimeline` / `AppTimeline` 连接线用 `fillMaxHeight()`，在滚动容器（无限高度约束）下高度塌为 0、整条不可见；改用 `IntrinsicSize.Min` 固定行高（行间距移入行内 bottom padding 保连接线贯通）。
- `[ui]` 修复跑马灯文本竖排：`AppMarquee` 内容 Box 未被容器宽度约束，Text 被压到逐字符换行；补 `wrapContentWidth(unbounded = true)` 让内容按自然宽度展开。
- `[ui]` 修复系统栏背景：`AppShell` 的 `ShellTopBar` / 底栏 / 侧栏把 insets 挂在 `Surface` 上，导致背景未延伸进状态栏 / 导航栏（透出窗口底色）；改为 Surface 全尺寸绘制背景、内容行内移 insets。

## [0.0.0.2-rc13] - 2026-09-14

> 预发行（未转正）。页⾯骨架槽位收口（样板页先行）：新增统一壳 `AppShell`，把顶栏 / 顶栏 Tab / 内容区 / 底栏 / 侧栏五槽位固化为紧凑移动端壳并统一窗口 insets；顶栏规格唯一化为紧凑 `ShellTopBar`（44dp 内容行 + statusBarPadding），删除零消费的 M3 `AppTopBar`；精简剔除断点 / 停靠 / 多栏 / 装配图整套未落地理论；样板页改用 `AppShell`，同步修复顶栏与状态栏重合问题。纯 UI 设计系统（`:newui`）改动，app 模块未动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Added

- `[ui]` 新增 `AppShell` 统一页面骨架壳：提供 `title/onNavigateBack/topBarActions/topTabs/bottomBar/sideRail/content` 五槽位具名插槽；状态栏 / 导航栏 insets 在顶栏、底栏各自处理，content 不双算；compact 单栏为默认形态。

### Changed

- `[ui]` 顶栏规格统一为紧凑 `ShellTopBar`（44dp 内容行 + `statusBarsPadding` + 40/20dp 图标规)，成为全局唯一事实源，替代 M3 默认 64dp 顶栏。
- `[ui]` DesignGallery 改用 `AppShell` 承载，删除私有 `iOSNavBar`，顶栏与状态栏重合问题随之修复。

### Removed

- `[ui]` 删除零消费的 `AppTopBar` / `TopBarBackButton`（M3 64dp 版，与紧凑规格冲突）。
- `[ui]` 精简 `Slot.kt`：剔除断点装配（`AppBreakpoint`/`AppAdaptiveScope`）、停靠（`DockPlacement`）、多栏（`PaneSpec`）、装配图（`SlotAssembly`）、策略（`SlotStrategy`）整套未落地理论，保留五槽位 `BlockSlotKind` + `SlotKey` + `BlockSlot` 最小契约。

## [0.0.0.2-rc12] - 2026-09-14

> 预发行（未转正）。DesignGallery 样板页框架重写为紧凑简约的 iOS 风格：导航栏由超大内联标题收紧为单行紧凑标题；分区槽位卡（`Section`）内边距/行距/圆角收细；主容器分区间距压缩。视觉密度提升、布局更轻，用于校准组件在紧凑容态下的真实观感。纯 UI 设计系统（`:newui`）改动，无 AI 工作流 / prompt / schema / 资产同步影响。

### Changed

- `[ui]` `iOSNavBar` 导航栏收紧：独立两行的 `LargeTitle`(34sp) 改为返回箭头与标题同行的内联 `Title3`(20sp)、`maxLines=1`；内边距 `Lg/Sm` 收至 `Md/Xs`，去掉顶部冗余留白。
- `[ui]` `Section` 分区槽位卡收紧：卡片内边距 `Lg`(16dp) → `Sm`(8dp)、内部行距 `Sm`(8dp) → `Xs`(4dp)、圆角 `Lg`(16dp) → `Md`(12dp)；分区标题左右/上下留白同步收细。
- `[ui]` 样板页主容器分区垂直间距 `Xl`(32dp) → `Md`(12dp)、顶部留白 `Md` → `Sm`。

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