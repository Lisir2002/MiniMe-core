# Changelog

> **项目原创声明**：MiniMe-core 为原创项目，全部代码由本项目团队独立设计与开发，未基于任何现有项目 fork 或衍生。

本文件记录 MiniMe-core 的用户与开发者可见变更（**第 2 层 · 开发者视角**），采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范；版本号遵循四段式语义（见 [AGENTS.md](./AGENTS.md#版本号规范)）。三层日志的分工与口径见 **AGENTS.md「版本日志」**。

- 最新版本置顶，倒序排列。
- 日期用 ISO 8601（`YYYY-MM-DD`）。
- 条目按「效果」而非「实现」撰写；内部噪音（纯格式、纯测试、非行为 refactor）不收录。
- **Breaking Change 必须用 ⚠️ 显著标注并附迁移说明。**

## v0.0.0.2-rc55 — 2026-09-17

### 改进
- **图标样板页骨架收口**：改用统一 `AppShell` 容器（标题/返回/insets 由容器接管），不再自绘顶栏；项目 AGENTS 新增"禁止手写页面骨架"硬规则。

## v0.0.0.2-rc54 — 2026-09-17

### 新功能
- **图标样板网格化**：改为两列图标砖，统一新主题圆角卡片；补全旧版高频图标（删除/历史/分享/链接/定时/语言/对话/图片/拍照/上/下箭头/水平更多/停止等），每砖标注替换的旧版图标名。

## v0.0.0.2-rc53 — 2026-09-17

### 新功能
- **模型能力标签彩色化**：识图=蓝、工具=绿、推理=紫，放在模型名后与简介区分；简介单独一行灰色小字。

### 改进
- **图标样板入口**：移到样板页底部「图标样板 →」按钮，更易找到。

## v0.0.0.2-rc52 — 2026-09-17

### 新功能
- **图标样板子页**：样板页右下角「图标」按钮进入，按类别展示新版图标，每个标注对应替换的旧版图标名。

### 改进
- **模型选择弹窗重排**：卡片改紧凑两行式（名称+角标，说明与能力合并为一行小字），减少臃肿；选中态用主色圆点+对勾并带缩放动画；选中 id 与当前模型联动，重开弹窗正确高亮；标题右侧显示模型总数。

## v0.0.0.2-rc51 — 2026-09-17

### 改进
- **模型选择弹层能力标识**：每个模型项下方按真实能力显示「识图 / 工具 / 推理」小标签，字段对齐生产 ModelMetadata（supportsVision / supportsTools / supportsReasoning）。

## v0.0.0.2-rc50 — 2026-09-17

### 新功能
- **模型选择弹层 AppModelPickerSheet**：底部弹层按 provider 分组列出可选模型，选中项主色高亮+对勾，支持角标与说明文案；输入框模型 chip 点击弹出，选中后回填模型名。

## v0.0.0.2-rc49 — 2026-09-17

### 修复
- **历史消息顺序与头像**：加载更早消息时改为「用户提问（蓝色、右侧带头像）→ 模型回复」的正常问答顺序；用户气泡收窄到 260dp 靠右，不再拉宽。

## v0.0.0.2-rc48 — 2026-09-17

### 改进
- **输入框微调**：构建模式配色改为土黄色；上下文进度条改细（4dp），百分比移到进度条右侧显示，超过九成变红。

## v0.0.0.2-rc47 — 2026-09-17

### 改进
- **输入框视觉反馈增强**：模式配色定为构建=青、计划=蓝、自动=深红；上下文进度条改为带动画填充、中央叠加百分比文字（超过九成变红、文字随填充量切换反色）；发送按钮换成向上箭头图标。

## v0.0.0.2-rc46 — 2026-09-17

### 改进
- **输入框布局微调**：模式按钮移到底栏左侧（上传按钮旁），并按状态着色——构建为中性、计划为信息蓝、自动为危险红；上下文用量从文字提示改为输入区下方一条 2dp 细进度条（正常主色、超九成变红），不增加整体高度；移除 MCP/计划占位按钮。

## v0.0.0.2-rc45 — 2026-09-17

### 改进
- **输入框更多设置按钮**：底部上传按钮旁新增「更多设置」图标，点击可切换功能按钮行（模式/思考/技能/MCP/计划）的显示与隐藏，该行初始为隐藏。

## v0.0.0.2-rc44 — 2026-09-17

### 改进
- **输入框功能按钮行重构**：顶部改为横向可滚动的功能按钮行，模式、思考强度、技能常驻；「更多/收起」按钮就在这一行内展开或收起额外功能项，不再使用下方弹出面板。演示页移除附件 chip 占位。

### 修复
- 修正横滚所需 rememberScrollState 的导入包名导致的编译错误。

## v0.0.0.2-rc43 — 2026-09-17

### 改进
- **输入框更多功能收纳**：思考强度（低/中/高）、技能/Playbook 入口默认隐藏，收进一个折叠面板；底部新增与上传按钮同款的圆形开关钮，点击展开/收起，后续可继续往面板里加功能项。
- 演示页移除排队请求 chip（该能力后续统一接入时再上）。

### 修复
- 修正思考强度分段控件参数名导致的编译错误。

## v0.0.0.2-rc42 — 2026-09-17

### 改进
- **输入框 AppComposer 对齐生产真实契约**：删除项目中并无接入口的「联网开关」与伪「深度开关」；改为与生产 `ChatInputBar` 同构的能力集合——行为模式（构建/计划/自动）三档、思考强度（低/中/高）三档循环、技能入口、附件面板（文件/图片/拍照）、斜杠命令浮层、排队请求 chips、上下文 token 提示、provider/model 选择、流式发送/停止切换。回调全部上抛，便于后续统一接入真实数据。

### 修复
- 补全新组件枚举/数据类在演示页的导入，修复编译失败。


## [0.0.0.2-rc41] - 2026-09-17

> 预发行。消息气泡布局与输入框对比度修复，纯 `:newui` component / sample 层。

### Fixed

- `[newui]` 消息气泡被拉成整宽、用户气泡未右对齐：内容列去掉 `weight(1f)`，改为包裹内容并限最大宽 300dp；短消息贴边、长消息换行后仍不撑满。
- `[newui]` `AppComposer` 激活开关加描边/加粗、附件 chips 加描边，提升浅色下的对比度与可识别性。

## [0.0.0.2-rc40] - 2026-09-17

> 预发行。新增对话输入框组件，纯 `:newui` component / sample 层。

### Added

- `[newui]` `AppComposer`：iOS 简约风卡片式对话输入框。顶部附件 chips（可删）、多行自适应文本区、底部工具行（+ / 附件 / 联网开关 / 深度开关）、右侧模型选择与发送/停止按钮（流式时变红停止）。纯展示 + 回调，语音输入暂未内置。

## [0.0.0.2-rc39] - 2026-09-17

> 预发行。对话流组件接口补齐，纯 `:newui` component / sample 层。

### Added

- `[newui]` `AppTestResultCard` 新增 `onRetry`（重跑测试）与 `onOpenFailure`（点击失败用例）回调，组件保持纯展示 + 回调，由上层/Agent 注入实际动作。

## [0.0.0.2-rc38] - 2026-09-17

> 预发行。对话流组件布局修复，纯 `:newui` component 层。

### Fixed

- `[newui]` 建议追问 chips（`AppFollowUpChips`）与澄清卡选项（`AppClarifyCard`）改用 `FlowRow` 自动换行，修复长选项被挤出屏幕、文字竖排折行的问题。

## [0.0.0.2-rc37] - 2026-09-17

> 预发行。对话流新增 9 类适配组件并接入演示剧情，纯 `:newui` component / sample 层 additive 改动。

### Added

- `[newui]` `AppTestResultCard` 测试结果卡：通过/失败数，失败用例可展开。
- `[newui]` `AppErrorCard` 编译/运行错误卡：文件:行号、错误摘要与「让我修」操作。
- `[newui]` `AppFollowUpChips` 建议追问 chips。
- `[newui]` `AppTurnSummaryBar` 回合总结条（文件/工具/耗时/token）。
- `[newui]` `AppAcceptChangesBar` 全部接受 / 全部拒绝批量操作条。
- `[newui]` `AppClarifyCard` 反问/澄清卡，带快速选项。
- `[newui]` `AppCommitChip` 已提交 hash + 可撤销标记。
- `[newui]` `AppWebSearchCard` 联网搜索命中列表。
- `[newui]` `AppModelBadge` 模型/档位小字徽章。
- `[newui]` 演示剧情新增一轮：反问 → 联网搜索 → 测试失败 → 错误卡 → 修复后全绿 → 回合总结 → 批量接受 → 提交 → 建议追问的完整闭环。

## [0.0.0.2-rc36] - 2026-09-17

> 预发行。对话流组件交互/反馈强化，纯 `:newui` component 层 additive 改动，无工作流 / prompt / schema / 运行行为变化。

### Added

- `[newui]` 引用来源卡（`AppCitationCard`）来源行可点击，直接用系统浏览器打开 URL。
- `[newui]` Diff 卡（`AppDiffCard`）头部新增复制按钮，一键把整段 diff（含文件头与 +/- 前缀）写入剪贴板。

### Changed

- `[newui]` Git 状态 chip（`AppGitStatusChip`）当存在未提交改动时高亮：图标、分支名、「N 改」计数统一用主色并加浅底，脏文件一眼可见。

## [0.0.0.2-rc35] - 2026-09-17

> 预发行。修复 rc34 引入的崩溃：对话流每条 AI 流式回复结束时主线程 NPE。纯 `:newui` 修复。

### Fixed

- `[newui]` 修复 `AppMessageRow`「停止生成」按钮在流式结束时崩溃：`AnimatedVisibility` 退出动画帧会在 `onStop` 回调已被置空后仍重组，原实现对其使用 `!!` 强解导致 `NullPointerException`（每条 AI 流式回复收尾必现）。改为退出帧安全判空，回调为空时不渲染按钮。

## [0.0.0.2-rc34] - 2026-09-17

> 预发行。`:newui` 对话流扩展 5 类适配组件并接入演示剧情，纯 `:newui` component / sample 层 additive 改动；app 模块生产界面未迁移，无 AI 工作流 / prompt / schema / 运行行为变化。

### Added

- `[newui]` 新增 `AppDiffCard`：文件行级 diff 卡，文件头带 `+N/-N` 红绿计数，新增浅绿 / 删除浅红行底、等宽字体，默认折叠、展开限高内部滚动。
- `[newui]` 新增 `AppTodoCard`：实时任务清单卡，Pending 空心圆 / Running 旋转 / Done 绿勾，头部带 `已完成/总数`。
- `[newui]` 新增 `AppCitationCard`：引用来源折叠卡，展开后列标题 + URL + 摘要。
- `[newui]` 新增 `AppGitStatusChip`：当前分支 + 脏文件数小 chip。
- `[newui]` `AppMessageRow` 新增 `onRegenerate`（操作条「重新生成」）与 `onStop`（流式时常驻「停止生成」）。
- `[newui]` 对话流演示接入：进入工作分支 chip、Todo 逐条推进、联网引用来源、核心文件行级 diff、最终回复可停止/重新生成。

## [0.0.0.2-rc33] - 2026-09-16

> 预发行。`:newui` 对话流演示页「本次改动的文件」折叠卡展开态限高与内部滚动优化，纯 sample 层改动；app 模块未动，无 AI 工作流 / prompt / schema 变化。

### Changed

- `[newui]` 结尾「本次改动的文件」折叠卡展开后文件列表限高（约 3 张），超出部分在卡片内部纵向滚动，不再把整屏撑长。

## [0.0.0.2-rc32] - 2026-09-16

> 预发行。`:newui` 对话流演示页两处布局修正，纯 sample 层与 `AppMessageRow` 展示槽位的 additive 改动；app 模块生产界面未迁移，无 AI 工作流 / prompt / schema / 运行行为变化。

### Changed

- `[newui]` AI 消息的思考过程不再悬在头像之上：`AppMessageRow` 新增 `leadingContent` 槽位，思考块渲染在「头像 + 姓名行」下方、与回复气泡同列对齐；演示页移除原先用 padding 硬让出头像宽的外挂式布局。
- `[newui]` 文件卡片从对话开头收敛到结尾：开场用户消息不再附带需求文档/原型两张文件卡；结尾新增「本次改动的文件」折叠卡（默认收起），展开后逐张列出本次改动的源码文件。

## [0.0.0.2-rc31] - 2026-09-16

> 预发行。`:newui` 设计系统内部结构重构：按"复用边界"重排分层、把业务复合组件从通用层剥离、统一最小触控目标、接入视觉回归、解耦令牌生成对 Node 的编译期依赖。app 模块生产界面未迁移（仅嵌入 DesignGallery），无 AI 工作流 / prompt / schema / 运行行为变化。

### Changed

- `[newui]` 组件分层由旧 atom/molecule/organism/template 四层改为 **primitive / component / layout 三层 + 平级 `composite/` 业务层**：`component/atom/`→`designsystem/primitive/`、`component/molecule/`→`designsystem/component/`、`organism/`+`template/`+`slot/`→`designsystem/layout/`。全部 90+ 文件随 `git mv` 保留历史，跨文件导入与测试包路径同步更新。
- `[newui]` 业务复合组件（`AppChatBubble`/`AppToolCallCard`/`AppToolChainTimeline`/`AppToolSummaryCard`/`AppSkillCallCard`/`AppMcpAppCard`/`AppPlanCard`/`AppTerminalLog`/`AppChatMarker`）从通用层迁入新包 `com.mini.me_core.newui.composite/`，与未来可独立发 AAR 的通用层隔离；通用层只保留无业务含义的纯可复用组件。
- `[newui]` 新增统一最小触控目标 `Modifier.touchTarget()`（primitive/AppTouchTarget.kt，默认 48dp + M3 居中热区），`AppIconButton` 由原来被 `.size(40dp)` 压窄的 40dp 热区改为走统一 48dp 触控封装。
- `[newui]` 令牌生成任务解耦：移除"每次编译都挂 Node"的全局 `afterEvaluate`，`generateDesignTokens` 声明正规 inputs/outputs 由 Gradle 自动判断；新增 `verifyDesignTokens` 任务供 CI 对账"令牌源 JSON 与提交产物一致"。

### Added

- `[newui]` 接入 Roborazzi 截图金标依赖，并提供 `AppButtonsScreenshotTest` 视觉回归示例（仅对通用组件拍金标，业务 composite 不拍）。

### Fixed

- `[docs]` 同步 `AGENTS.md` 与 `newui/DESIGN.md` 的分层归属纪律与组件路径，避免业务组件被塞回通用层。

## [0.0.0.2-rc30] - 2026-09-16

> 预发行。对话流完整演示页（ChatFlowGallery）的布局修复与播放控制栏美化，纯 `:newui` sample 层改动，app 模块未动，无 AI 工作流 / prompt / schema 变化。

### Changed

- `[newui]` 对话流演示页底部播放控制栏重排：原四个按钮等宽挤一行导致「自动播放」「展开全部」四字标签被压成竖排、四种按钮样式视觉权重混乱；改为主操作「自动播放」（Primary + PlayArrow 图标）独占一行满宽，次行「单步」（FilledTonal + SkipNext）与「展开全部」（Outlined + FastForward）等宽分担，低频「重置」收为 40dp 纯图标钮（RestartAlt，带无障碍描述）。

### Fixed

- `[newui]` 修复对话流演示页所有卡片（工具调用 / 终端日志 / 思考过程 / 消息气泡 / 附件 / 时间线等）左右紧贴屏幕边缘的问题：消息节点统一加 `AppSpacing.Lg` 水平留白，与底部控制栏对齐。
- `[newui]` 修复用户附件与消息分离、错误出现在对话流开头的问题：附件由独立列表项改为并入用户消息节点（`Msg.attachments`），在用户气泡下方同组渲染。
- `[newui]` 修复 AI 思考过程与正文回复分属两个独立容器的问题：思考过程并入 AI 消息节点（`Msg.thinking`），渲染于气泡上方、与气泡左沿对齐（让出头像位），共用头像与角色名；流式演出顺序为先思考后正文。

## [0.0.0.2-rc29] - 2026-09-16

> 预发行。newui 设计系统 P0-P3 全量优化（18项）+ 旧品牌残留深度清理 + 原创声明。newui 模块补全 atom/organism/template 三层组件、收口三态与页面布局卫生、统一骨架/对话框/菜单、清理硬编码颜色与弹簧常量、拆分画廊巨型文件、补充动效令牌与 UI 测试、建立设计规范文档；全项目清理旧仓库名/旧容器路径/旧包名残留；README/CHANGELOG 新增原创声明并清空品牌更名史。

### Added

- `[newui]` 新增原子层组件 4 个：`AppText`（Title/Body/Caption 变体）、`AppIconButton`（40dp）、`AppDivider`、`AppSurface`，均基于 M3 基础组件封装；atom 层由 3 个扩充至 7 个。
- `[newui]` 新增组织层组件 2 个：`ChatMessageList`（组合 AppChatBubble + AppMessageScroller）、`SettingsSectionGroup`（组合 AppSectionHeader + AppCard + AppMenuRow）。
- `[newui]` 新增模板层组件 2 个：`ListPageTemplate`（Loading/Empty/Error/Content 四态密封切换，内置重试按钮）、`DetailPageTemplate`（可滚动内容区 + 统一页面边距）。
- `[newui]` 新增三态收口 `layout/AppState.kt`：`AppUiState<T>` 密封接口（Loading/Empty/Error/Content 强穷尽）+ `AppLoadingState`/`AppEmptyState`/`AppErrorState` 三个公共组件，禁止各页面自造加载/空/错误态。
- `[newui]` 新增页面布局卫生 `layout/AppPage.kt`：`AppPage` 门面统一左右留白（PageHorizontal）、区块间距（BlockGap）、行间距（RowGap）；`Modifier.pageContentPadding()` 与 `Modifier.pageMaxWidth()` 收口宽屏最大内容宽，禁止页面散落表外数值。
- `[newui]` 新增 `DESIGN.md`（373行）：设计原则（iOS 简约风/三级层次/44dp 紧凑顶栏/扁平分层）、令牌体系（颜色/布局/间距/动效）、组件分层规范与速查索引、AppShell 五槽位布局规范、**无障碍规范**（44dp 触摸目标/图标语义/语义化角色/对比度/动态字体/键盘焦点导航）、废弃组件迁移指引、动效规范与红线 + 附录令牌↔代码落点速查表。
- `[newui]` 新增 UI 测试 7 个文件：AppDialog、AppTextField、AppTabs、AppSegmentedToggle、AppMarkdownTextParse、AppSwipeAction、AppSwipeGalleryReplica，共 14+ 测试用例。
- `[newui]` 模块 API 版本标注：134 个公共组件 KDoc 统一标注 `@since 0.1.0-experimental`，标记当前为实验性 API。

### Changed

- `[newui]` `AppShell` 新增 `topBarStyle` 参数（`Compact`=44dp / `Standard`=64dp，默认 Compact），统一页面骨架；`SlotSet` 标记 `@Deprecated`，五槽位以 AppShell 为唯一事实源。
- `[newui]` `AppDialog` 增强：新增 `scrimAlpha`、`containerColor`、`tonalElevation` 三个可选参数；`AppDialogs.kt`（9函数）和 `AppDialogsAdvanced.kt`（9函数）共 18 个旧对话框函数全部标记 `@Deprecated`，迁移指引见 DESIGN.md §3.9。
- `[newui]` `AppMenu` 统一为唯一推荐（基于 M3 `DropdownMenu`），`12.dp` 阴影替换为 `AppElevation.Z4`；`AppMenus.kt`（AppActionSheet/AppSelectField/AppActionSheetItem）3 个旧菜单函数标记 `@Deprecated`。
- `[newui]` `DesignGallery` 从 2326 行巨型文件拆分为 5 个专题文件（`SampleData`/`GalleryComponents`/`ChatSamples`/`FormSamples`/`FeedbackSamples`），入口精简为 72 行；连同 `ChatFlowGallery`，sample 层共 7 个文件、职责单一。
- `[newui]` 深色模式层次优化：`DarkPalette.surfaceDim` 改为 `#2C2C2E`（iOS tertiarySystemBackground），与 `card`(#1C1C1E) 区分，修复深色下卡片与背景层次糊在一起的问题。
- `[newui]` 动效令牌扩充：`AppMotion` 新增 `EasingStandard`/`EasingEmphasized`/`EasingDecelerate` 三条缓动曲线 + `standardSpring()`/`emphasizedSpring()`/`noBounceSpring()` 三个弹簧预设 + `standardTween()`/`emphasizedTween()` 两个补间预设；19 个文件中的硬编码 `Spring.DampingRatio*`/`Spring.Stiffness*` 全部替换为令牌。
- `[newui]` `AppCard` 统一底色：有/无 `onClick` 统一使用 `MaterialTheme.colorScheme.surface`，移除 `surfaceVariant` 分支，可点击性改由涟漪表达。
- `[newui]` 20 个文件中的 `Color.White`/`Color.Black` 全部替换为语义令牌；`AppPalette` 同步新增 `onPrimary`、`primaryOverlay12`、`primaryOverlay14` 三个语义字段（含深色模式对应值）。
- `[docs]` README 中英文新增「项目声明」章节，明确原创声明；CHANGELOG 新增原创声明引用块。
- `[docs]` `backup-and-restore.md` 简化包名变更描述，删除旧包名列表，保留功能性说明。
- `[ci]` `android-release.yml` 注释清理品牌更名史描述，保留功能性包名迁移映射表。

### Fixed

- `[brand]` 清理旧仓库名 `mini_me_core-R` 残留 3 处（app-settings-guide.md / environment-guides.md）。
- `[brand]` 清理容器旧路径 `~/.mini_me_core/` → `~/.minime/` 残留 30+ 处（docs + prompts），修复文档路径与代码实际路径不一致导致 AI 操作指向错误目录的问题。
- `[brand]` 清理旧包名 `com.core.mini_me_core` → `com.mini.me_core` 残留 5 处。
- `[brand]` 清理 `.gitignore` 中 `/.rcode/` 旧品牌工具目录（目录已不存在，无效残留）。
- `[brand]` 清理工具名 `mini_me_core-wrap-android-buildtools` → `minime-wrap-android-buildtools`（与代码中实际名称对齐）。

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