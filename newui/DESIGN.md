# MiniMe-core · newui 设计规范（DESIGN.md）

> 适用范围：`com.mini.me_core.newui.designsystem` 下全部 Compose 组件、令牌与页面骨架。
> 本文是 newui 设计系统的**唯一事实源文档**：组件 KDoc 中的 `§x.y` 引用均指向本文件对应小节。
> 颜色 / 间距 / 圆角 / 尺寸 / 布局 / 动效的**数值**一律以 `tokens/*.json` 与生成产物 `AppTokens.kt` 为准，本文档只做语义说明。

---

## 1. 设计原则

newui 采用 **iOS 简约风（systemGroupedBackground 分组列表）设计语言**，在 Android（Jetpack Compose + Material3）上还原 iOS Human Interface Guidelines 的视觉节奏：克制配色、无厚重阴影、靠底色分层、紧凑触摸目标。所有原则服务于一句话——**统一、克制、可复用**。

### 1.1 iOS 简约风设计语言概述

- 浅色模式以「系统分组底 + 白色成员卡片」为骨架：页面底色 `#F2F2F7`（`systemGroupedBackground`），卡片浮于其上为纯白 `#FFFFFF`。
- 强调色定板为 iOS 系统蓝 `#0A84FF`，点缀浅蓝 `#5AC8FA`；状态色沿用 iOS 语义绿 / 橙 / 红。
- 深色模式反转为纯黑底 `#000000` + 深灰卡片 `#1C1C1E`，强调蓝提亮一档为 `#3D9BFF`。
- 去装饰：不滥用渐变、投影、拟物高光；视觉层级通过**底色差**而非阴影表达。

### 1.2 systemGroupedBackground 三级层次

iOS 分组背景语义映射为三级表面，组件不得自创第四层：

| 层次 | 浅色值 | 深色值 | 用途 |
| --- | --- | --- | --- |
| surface（系统分组底） | `#F2F2F7` | `#000000` | 页面最底层背景 |
| surfaceDim（次要面） | `#E9E9EE` | `#2C2C2E` | 凹陷区 / 分隔面 / 未选 chip |
| card（成员表面） | `#FFFFFF` | `#1C1C1E` | 分组卡片、可点击成员行 |

> 深色 `card` 对应 iOS `secondarySystemGroupedBackground`，`surfaceDim` 对应 `tertiarySystemBackground`，二者刻意拉开明度差以保证卡片可识别。

### 1.3 SF Pro 排版尺度映射

排版不硬编码 `sp`，统一走 `AppType.*` 并映射到 M3 `MaterialTheme.typography.*`，对齐 SF Pro 类型尺度（详见 §3.2）。页面统一 `MaterialTheme.typography.*` 取字号 / 字重 / 行高，禁止业务方散落 `TextStyle(17.sp)`。

### 1.4 44dp 紧凑顶栏规范

对齐 iOS HIG 导航栏：内容行 **44dp** 高（= `AppSizing.TouchTarget`），返回 / 操作图标钮 **40dp**（= `AppSizing.IconButton`），图标本体 **20dp**（= `AppSizing.IconM`）。彻底替代 M3 默认 64dp 顶栏；仅在需要 M3 原生规格时显式选 `Standard=64dp`。见 §2.3 与 §4。

### 1.5 扁平无阴影卡片靠底色分层

默认静止态 `AppElevation.Z0 = 0dp`，层级由 surface / surfaceDim / card 三档底色承担。`AppCard` 仅在需要轻微抬升时用 `Z1=1dp`，滚动吸附才用 `Z2=3dp`；**禁止**业务自行堆 elevation 制造「浮层堆」。

### 1.6 一切从基础代码出发，不独造车轮

- 优先复用 M3 原语（`Surface` / `Text` / `Icon` / `OutlinedTextField` / `Scaffold`），newui 只做**令牌化薄封装**，不发明并行组件体系。
- 组件源码禁止硬编码颜色 / 间距 / 圆角 / 尺寸 / 时长数字，一切数值从令牌读取（§2）。
- 新组件先在 primitive/component 找是否已有可组合件；确无空缺再新增，并在 §3.12 登记。

---

## 2. 令牌体系

令牌是设计系统的**唯一事实源**。源文件为 W3C DTCG 格式的 `tokens/color.json`、`tokens/dimensions.json`，经 **Style Dictionary** 生成 `token/generated/AppTokens.kt`（文件头标注「禁止手改」）。组件只能引用 `AppColor / AppSpacing / AppRadius / AppSizing / AppLayout / AppMotion` 等对象。

### 2.1 生成管线与单一事实源

```
tokens/color.json  ┐
tokens/dimensions.json ├─ Style Dictionary ─► token/generated/AppTokens.kt
                    ┘            （@generated，禁止手改）
```

- 改色 / 改尺寸只改 `tokens/*.json`，重新跑 Style Dictionary 生成；**不允许**直接改 `AppTokens.kt`。
- 明暗色不写死在组件里：组件统一走 `appPalette()` / `LocalAppPalette.current`（由 `AppTheme` 注入明暗实例），语义状态色 `AppColor.Status*` 明暗通用可直引。
- 红线：组件源码禁止出现 `Color(0xFF…)`、`16.dp`、`150L` 等字面量；Code Review 见即打回。

### 2.2 颜色令牌（AppColor / AppPalette）

**Brand 主色与表面**（`tokens/color.json → color.brand`）：

| 令牌 | 浅色色值 | 语义 |
| --- | --- | --- |
| `BrandPrimary` | `#0A84FF` | 强调主色（iOS 系统蓝） |
| `BrandSurface` | `#F2F2F7` | 系统分组底 |
| `BrandSurfaceDim` | `#E9E9EE` | 次要面 / 分隔 |
| `BrandCard` | `#FFFFFF` | 分组卡片 / 成员表面 |
| `BrandInk` | `#111114` | 一级文字 label.primary |
| `BrandAccent` | `#5AC8FA` | 点缀浅蓝 |

**Label / Status / Separator**：

| 令牌 | 色值 | 语义 |
| --- | --- | --- |
| `LabelSecondary` | `#8E8E93` | 二级文字 |
| `LabelTertiary` | `#C7C7CC` | 三级文字 |
| `StatusSuccess` / `Warning` / `Danger` / `Info` | `#34C759` / `#FF9F0A` / `#FF3B30` / `#0A84FF` | 状态色（明暗通用） |
| `SeparatorOnLight` / `SeparatorOnDark` | `#DCDCE0` / `#38383A` | 分割线（明暗各一） |

**OnDark 系列**（`color.onDark`，暗色对应值）：`OnDarkSurface #000000`、`OnDarkSurfaceDim #2C2C2E`、`OnDarkSurfaceRaised #1C1C1E`、`OnDarkInk #FFFFFF`、`OnDarkPrimary #3D9BFF`、`OnDarkSecondaryLabel #98989D`。

**Semantic 覆盖层**（`color.semantic`）：

| 令牌 | 色值 | 语义 |
| --- | --- | --- |
| `OnPrimary` / `OnPrimaryDark` | `#FFFFFF` / `#000000` | 主色上的内容色（浅=白，深=黑） |
| `PrimaryOverlay12` / `PrimaryOverlay14` | `#1F0A84FF` / `#240A84FF` | primary 12% / 14% 覆盖层（浅） |
| `PrimaryOverlay12Dark` / `PrimaryOverlay14Dark` | `#1F3D9BFF` / `#243D9BFF` | 对应暗色覆盖层 |

> 组件取色口诀：明暗相关 → `appPalette()`；纯语义状态 → `AppColor.Status*`；主色覆盖层 → `appPalette().primaryOverlay12/14`。

### 2.3 布局与骨架令牌（AppLayout + AppShell 五槽位）

页面骨架是令牌的直接消费方，故在此定义其槽位契约（KDoc 中 `§2.3` / `§2.3.2` / `§2.3.4` / `§2.3.5` 即指本节）。

**布局令牌 `AppLayout`**：

| 令牌 | 值 | 语义 |
| --- | --- | --- |
| `PageHorizontal` | `16.dp` | 页面左右水平边距 |
| `BlockGap` | `16.dp` | 块 / 区块垂直间距 |
| `RowGap` | `8.dp` | 行内元素间距 |
| `ContentMaxWidth` | `720.dp` | 宽屏内容最大宽度（居中） |
| `DividerThickness` | `1.dp` | 分割线统一厚度 |

**五槽位模型（§2.3.2 `BlockSlotKind`）**：`TOP_APP_BAR` / `TOP_TABS` / `CONTENT` / `BOTTOM_TABS` / `SIDE_RAIL`。槽位只承载定位契约（`SlotKey` / `BlockSlot.subKeys`），真正渲染由 `AppShell` 的命名插槽 lambda 完成。

**声明式装配（§2.3.5）**：`AppShell(title, onNavigateBack, navigationIcon, topBarActions, topTabs, bottomBar, sideRail, topBarStyle, content)`——屏幕只挂载、不写骨架。`topTabs / bottomBar / sideRail` 均可选，不传不渲染、不占布局。窗口 insets 由壳统一处理（见 §4）。

### 2.4 空间 / 圆角 / 尺寸令牌

**间距 `AppSpacing`**：`Tiny 2` · `Xs 4` · `Sm 8` · `Md 12` · `Lg 16` · `Section 20` · `Xl 24` · `Xxl 32`（单位 dp）。其中 `Section=20` 专为卡片 / 列表组之间留白，介于 `Lg` 与 `Xl` 之间。

**圆角 `AppRadius`**：`None 0` · `Sm 8` · `Md 12` · `Lg 16` · `Pill 999`（dp，`Pill` 用于胶囊 / 头像）。另 `AppStroke.Thin=1dp` 为描边专用；`AppElevation`：`Z0 0` / `Z1 1` / `Z2 3` / `Z3 8` / `Z4 12`。

**尺寸 `AppSizing`**：

| 令牌 | 值 | 语义 |
| --- | --- | --- |
| `TouchTarget` | `44.dp` | 最小可触摸区（无障碍底线） |
| `IconButton` | `40.dp` | 图标按钮热区 |
| `IconBlock` | `40.dp` | 图标色块规格 |
| `IconXs / IconS / IconM / IconL / IconXl` | `16 / 18 / 20 / 24 / 28.dp` | 图标五档刻度 |

### 2.5 动效令牌（AppMotion）

时长 `Fast 150L` / `Med 250L` / `Slow 400L`（ms）；缓动 `EasingStandard` / `EasingEmphasized` / `EasingDecelerate`；弹簧 `standardSpring()` / `emphasizedSpring()` / `noBounceSpring()`。完整语义与使用规范见 §6。

---

## 3. 组件规范

组件按 **primitive → component → layout 三层 + 业务 composite** 组织（2026-09 重排，废弃旧 atom/molecule/organism/template 隐喻），下层不反向依赖上层。

### 3.1 分层总览

| 层 | 目录 | 职责 | 代表 |
| --- | --- | --- | --- |
| primitive 基元 | `designsystem/primitive/` | 不可再分的基础原语 | `AppCard` `AppChip` `AppIcon` `IconContainer` `AppText` `AppIconButton` `AppTouchTarget` |
| component 通用组件 | `designsystem/component/` | 无业务含义、可复用的纯通用控件（未来独立发 AAR） | `AppButton` `AppTextField` `AppDialog` `AppMenu` `AppTabs` `AppSegmentedToggle` `AppSwitch` `AppBadge` 等约 66 个 |
| layout 页面骨架 | `designsystem/layout/` | 页面级壳/骨架/三态 | `AppShell` `AppPage` `AppState` `ChatMessageList` `SettingsSectionGroup` `ListPageTemplate` `DetailPageTemplate` |
| composite 业务复合 | `composite/`（与 designsystem 平级） | 本 App 业务复合组件，依赖领域模型，**不进可发布设计系统** | `AppChatBubble` `AppToolCallCard` `AppMcpAppCard` `AppPlanCard` `AppTerminalLog` 等 |

> 归位判据：换个 App 还用得上、且入参只有基础类型/令牌/lambda → `component/`；只服务本产品业务 → `composite/`；定义页面结构 → `layout/`。禁止把业务卡片塞回 `component/`。

### 3.2 排版尺度（AppText，§3.2.1 / §3.2.2 / §3.2.3）

`AppText` 是 M3 `Text` 的薄封装，默认色取 `appPalette().ink`，默认 style 为 `bodyMedium`。三个便捷变体：

| 变体 | 排版令牌 | 用途 |
| --- | --- | --- |
| `AppTextTitle`（§3.2.1） | `titleMedium` + SemiBold | 区块 / 卡片主标题 |
| `AppTextBody`（§3.2.2） | `bodyMedium` | 长段落 / 对话气泡正文 |
| `AppTextCaption`（§3.2.3） | `bodySmall` + `labelSecondary` | 时间戳 / 副标题 / 占位提示 |

SF Pro 尺度经 `AppType` 映射到 M3 槽位：`LargeTitle 34/41 Bold`、`Title1 28/34 Bold`、`Title2 22/28 SemiBold`、`Title3 20/25 SemiBold`、`Headline 17/22 SemiBold`、`Body 17/22 Regular`、`Callout 16/21 Regular`、`Subhead 15/20 Regular`、`Footnote 13/18 Regular`、`Caption1 12/16`、`Caption2 11/13`、`SectionHeader 13/16 SemiBold`。

### 3.3 基元层（primitive/）

| 组件 | 用途 | 关键规格 |
| --- | --- | --- |
| `AppCard`（§3.12） | 基础分组卡片；无 `onClick` 纯展示，有时走可点击 Surface | `AppRadius.Md` 圆角 + `AppElevation.Z1`；展示态取 `surfaceVariant`，可点击取 `surface` |
| `AppChip`（§3.12） | 标签 / 胶囊，可带前导图标 | `AppRadius.Pill`；选中取 `surfaceVariant`，未选取 onSurface 8% |
| `AppIcon`（§3.6） | 统一图标，tint 取 `LocalContentColor` | 默认 `AppSizing.IconM`，五档刻度见 §3.6.2 |
| `IconContainer`（§3.6.3） | 40dp 色块 + 白图标，作设置 / 入口行前缀 | 用 `AppSizing.IconBlock` |
| `AppText`（§3.2） | 统一文字薄封装 | 默认一级 ink，禁 `Color.Black` / 裸 `.sp` |
| `AppDivider` | 列表 / 区块内行分隔 | 厚度恒为 `AppLayout.DividerThickness=1dp`，色取 `appPalette().separator` |
| `AppIconButton`（§3.12） | 图标按钮 | 命中热区走 `Modifier.touchTarget()`（最小 48dp，见 primitive/AppTouchTarget），图标 `AppSizing.IconM` |
| `AppSurface` | 统一表面容器（明暗感知底色 + 圆角） | 是卡片 / 弹层底色的统一入口，禁止业务自取 `Color` 当底 |

### 3.4 通用组件层（component/）

列举主要控件及用途（完整清单见 §3.12）：

| 组件 | 用途 |
| --- | --- |
| `AppButton`（§3.12） | 按钮统一封装，四种变体；新增页面首选，**禁止裸 `Box.clickable` 当按钮** |
| `AppTextField`（§3.12） | 文本输入：`OutlinedTextField` + 统一填充 / 描边 / 圆角 |
| `AppDialog`（§3.12） | 弹窗统一封装，收敛圆角 / 边距令牌（替代旧 `AppDialogs*`） |
| `AppMenu` / `AppMenuItem` / `AppMenuRow`（§3.12） | 下拉 / 上下文菜单与菜单项，行高 `TouchTarget` |
| `AppTabs` / `AppSegmentedToggle` | 顶部分页 Tab / 分段选择器 |
| `AppChatBubble` | 聊天气泡（用户 / 助手双形态）——已迁至 `composite/`（业务组件），通用层不再保留 |
| `AppSwitch` / `AppCheckbox` / `AppSlider` | 开关 / 复选 / 滑杆 |
| `AppBadge` / `AppBadgeDot` / `AppStatusDot` | 徽标 / 状态点，语义色走 `AppColor.Status*` |
| `AppSearchBar` / `AppSearchableDropdown` | 搜索栏 / 可搜索下拉 |
| `AppSectionHeader`（§3.12 / §3.7.3） | 区块标题：左竖条装饰 + 标题 + 可选右槽 |
| `AppSectionGroup`（§3.12） | 分组容器：标题 + 卡片化内容，行间 `AppDivider` |
| `AppSwitch` / `AppChip` 系 | 过滤 chips / filter tokens / dropdown filter |

### 3.5 layout 层（页面骨架）

- **layout**：`AppShell`（唯一页面壳）、`AppPage`（布局卫生）、`AppState`（三态）；`ChatMessageList`（由 `AppMessageRow` + `AppChatBubble` + `AppMessageScroller` 组合的消息流区块）；`SettingsSectionGroup`（由多个 `AppSectionGroup` 堆叠而成的设置整段）。layout 不直接写布局数字，只编排 component/composite 与 §4 布局令牌。
- **整页模板**：`ListPageTemplate`（列表整页：`AppShell` + 顶部搜索 / 过滤 + `AppSelectionList` + 空 / 加载三态）；`DetailPageTemplate`（详情整页：`AppShell` 紧凑顶栏 + 滚动内容 + 底部固定操作栏）。二者都基于 `AppShell`，不重复造骨架。
- 业务复合组件（聊天气泡、工具调用卡、MCP 卡、计划卡、终端日志等）见根包 `composite/`，不属本设计系统可发布部分。

### 3.6 图标规范（AppIcon，§3.6）

- 统一 Rounded 基调，`tint` 默认 `LocalContentColor.current`，随父级内容色自动着色。
- **尺寸刻度（§3.6.2）**：`IconXs 16` / `IconS 18` / `IconM 20` / `IconL 24` / `IconXl 28`，按容器语境取档，禁止随意写 `22.dp` 这类表外值。
- **图标块（§3.6.3 `IconContainer`）**：40dp 色块 + 白图标，用于设置项 / 入口行前缀。
- **contentDescription 语义（§3.6.7）**：`contentDescription: String?`，**传 `null` 表示装饰性图标**（无障碍服务跳过）；有独立语义的图标按钮必须传非空描述。

### 3.7 布局卫生（AppPage，§3.7）

页面级布局收口到 `layout/AppPage.kt`，组件内禁止表外数值：

- **页面左右留白（§3.7.1）**：`AppPage.horizontalPadding = AppLayout.PageHorizontal(16dp)`；`Modifier.pageContentPadding()` 一步加水平 16dp + 可配垂直间距。
- **区块 / 行间距（§3.7.2）**：`AppPage.verticalBlock()` 默认 `BlockGap=16dp`，`AppPage.rowGap = RowGap=8dp`。
- **装饰比例（§3.7.3）**：`AppSectionHeader` 左竖条宽度为装饰比例常量，**非独立 token**，不新增令牌。
- **宽屏居中**：`Modifier.pageMaxWidth()` = `widthIn(max = ContentMaxWidth 720dp)`。

### 3.8 三态（AppUiState）

页面级统一 sealed 三态（§3.8）：`AppUiState.Loading / Empty / Error(message, detail?) / Content(data)`，页面用 `when` 强穷尽渲染 `AppLoadingState` / 空态 / 错误态。**禁止各页自造 Loading / Empty / Error**。

### 3.9 废弃组件（迁移指引）

| 废弃 | 替代 | 说明 |
| --- | --- | --- |
| `SlotSet` | `AppShell` | 已 `@Deprecated`，旧调用兼容保留，新代码禁用 |
| `AppDialogs` / `AppDialogsAdvanced` | `AppDialog` | 旧的散弹式弹窗集合，统一收敛到 `AppDialog` |
| `AppMenus` | `AppMenu` | 旧菜单集合，统一收敛到 `AppMenu` |

### 3.10 形状尺度与最大内容宽（AppShapes）

圆角令牌映射到 M3 `Shapes`（§3.10 对齐 M3 形状尺度）：`extraSmall/small = AppRadius.Sm(8)`、`medium = AppRadius.Md(12)`、`large/extraLarge = AppRadius.Lg(16)`。M3 组件自动消费该形状尺度。宽屏内容最大宽 `ContentMaxWidth=720dp`（见 §3.7）。

### 3.11 触觉与轻反馈

`AppHaptics`（§3.12）收口触觉反馈，破坏性操作二次确认等统一走此，不在业务各处手写 `HapticFeedback`。

### 3.12 组件速查索引

下表为 KDoc 中 `§3.12 <组件名>` 的落点；行未列出的 molecule（`AppFAB` `AppToast` `AppAccordion` `AppProgressBar` `AppRingProgress` `AppSlider` `AppStepper` `AppTimeline` `AppPagination` `AppAvatar` `AppFileCard` `AppMarkdownText` `AppSwitch` 等）均遵循同一令牌约束：取色走 `appPalette()`、尺寸走令牌、动效走 `AppMotion`。

| 组件 | § | 一句话职责 |
| --- | --- | --- |
| `AppCard` | §3.12 / §3.3 | 基础分组卡片 |
| `AppChip` | §3.12 / §3.3 | 标签胶囊 |
| `AppButton` | §3.12 / §3.4 | 统一按钮，禁裸 clickable |
| `AppTextField` | §3.12 / §3.4 | 统一文本输入 |
| `AppDialog` | §3.12 / §3.4 | 统一弹窗 |
| `AppMenu` / `AppMenuRow` | §3.12 / §3.4 | 菜单与列表行 |
| `AppSectionGroup` | §3.12 / §3.4 | 卡片化分组容器 |
| `AppSectionHeader` | §3.12 / §3.7.3 | 竖条 + 区块标题 |
| `AppStatusDot` | §3.12 / §3.4 | 状态圆点 |
| `AppHaptics` | §3.12 / §3.11 | 统一触觉反馈 |
| `AppIconButton` | §3.12 / §3.3 | 40dp 图标钮 |

---

## 4. 布局规范

### 4.1 AppShell 统一页面骨架

所有页面以 `AppShell` 为唯一骨架（五槽位模型见 §2.3）：

- **顶栏风格 `topBarStyle`**：`AppTopBarStyle.Compact = 44dp`（默认，iOS HIG）/ `Standard = 64dp`（M3 默认）。Compact 是项目既成事实，成为该规格**唯一事实源**，替代 M3 默认顶栏。
- **顶栏内建规格**：Surface 背景延伸至状态栏；内容行高 = `TouchTarget(44dp)`；返回 / 操作图标钮 `IconButton(40dp)`，图标 `IconM(20dp)`；标题用 `titleMedium` + SemiBold。

### 4.2 五槽位装配

`topBar`（内建紧凑顶栏）→ `topTabs`（可选 Tab 行）→ `content`（权重 1f 占满剩余）→ `bottomBar`（可选，内容下方挤压布局，不覆盖内容）→ `sideRail`（可选，左排窄栏，移动端几乎不用，保留槽位契约）。除 `content` 外其余不传即不渲染、不占布局。

### 4.3 内容区 PaddingValues 消费模式

`content: @Composable (PaddingValues) -> Unit`：`AppShell` 当前向内容传 `PaddingValues(0.dp)`，窗口 insets 由壳在顶栏 / 底栏侧消化。**调用方**若需水平留白，用 `Modifier.pageContentPadding()` 或 `Modifier.padding(inner)` 消费，避免自行再算 insets 造成双算。

### 4.4 窗口 insets 处理

- 顶栏内容行：`Modifier.statusBarsPadding()` 把标题 / 图标下推到状态栏之下；Surface 本体不加 inset，使底色延伸到状态栏。
- 底栏：`Modifier.navigationBarsPadding()` 把内容上推到导航栏之上，避免悬浮手势遮挡。
- 业务页面**不要**重复 `systemBarsPadding()`；需要局部避让时显式叠加，且只在 `AppShell` 之外的浮层使用。

### 4.5 页面边距与最大宽度

- 左右水平边距恒为 `AppLayout.PageHorizontal = 16dp`（`Modifier.pageContentPadding()` 已内含）。
- 块间垂直间距默认 `BlockGap=16dp`，卡片组之间用 `AppSpacing.Section=20dp`。
- 宽屏 / 横屏内容居中限宽 `AppLayout.ContentMaxWidth = 720dp`（`Modifier.pageMaxWidth()`）。

---

## 5. 无障碍规范

### 5.1 触摸目标

- 任何可点击元素最小热区 **44dp**（`AppSizing.TouchTarget`）。`AppShell` 紧凑顶栏内容行即 44dp，满足 iOS HIG；图标钮 40dp 热区由外围点击区补足，不得把图标缩到触摸区之外。
- 列表行（`AppMenuRow` / `AppSelectionList`）行高不低于 `TouchTarget`。

### 5.2 图标语义（对应 §3.6.7）

`AppIcon.contentDescription: String?`：
- 有独立语义（如「返回」「删除」）→ 传非空字符串。
- 纯装饰（如图标旁已有文字说明）→ 传 `null`，无障碍服务跳过，避免朗读冗余。

### 5.3 语义化角色

- 按钮走 `AppButton` / `AppIconButton`，由 M3 自动带 `Role.Button`；**禁止**用 `Modifier.clickable` 裸拼可点区域冒充按钮（无语义、无波纹规范）。
- 开关 `AppSwitch`、复选 `AppCheckbox` 带 `Role.Switch / Role.Checkbox`；列表项标注选中态；`AppChip` 选中态通过 `selected` 暴露语义。
- 分组 `AppSectionGroup` / `AppSectionHeader` 为屏幕阅读器提供分组标题。

### 5.4 对比度

- 一级文字 `ink` 对 `surface` / `card` 对比度 **≥ 4.5:1**（WCAG AA 正文）。
- 二级 `labelSecondary #8E8E93` 仅用于辅助说明，不用于关键操作文字；主色按钮文字走 `OnPrimary #FFFFFF` / 深色 `OnPrimaryDark #000000`，保证与 `BrandPrimary` 对比达标。
- 禁止用 `LabelTertiary #C7C7CC` 承载可读正文。

### 5.5 动态字体

- 字号全部走 `MaterialTheme.typography.*` / `AppType.*`，**不硬编码 sp**，随系统字体缩放自适应。
- 多行文本用 `AppText(maxLines, overflow)` 控制溢出，避免大字号下标题挤压操作按钮。

### 5.6 键盘 / 焦点导航

- 可聚焦元素焦点顺序自上而下、自左而右，与视觉阅读顺序一致；`AppShell` 返回钮 → 标题区 → 内容 → 顶栏动作 → 底栏。
- 表单类 `AppTextField` 明确下一项焦点，避免焦点陷阱。

---

## 6. 动效规范

### 6.1 时长令牌

| 令牌 | 时长 | 适用 |
| --- | --- | --- |
| `AppMotion.Fast` | `150ms` | 微交互：按压、选中态切换、图标形变 |
| `AppMotion.Med` | `250ms` | 组件过渡：展开 / 收起、弹层淡入、Tab 切换 |
| `AppMotion.Slow` | `400ms` | 页面转场：整页进入 / 退出、列表大刷新 |

`tween` 便捷构造：`standardTween()`（Fast + Standard）、`emphasizedTween()`（Med + Emphasized）。

### 6.2 缓动曲线

| 令牌 | 贝塞尔 | 适用 |
| --- | --- | --- |
| `EasingStandard` | `CubicBezier(0.2, 0.0, 0, 1.0)` | 通用进 / 退场 |
| `EasingEmphasized` | `CubicBezier(0.2, 0.0, 0, 1.0)` | 强调性过渡 |
| `EasingDecelerate` | `CubicBezier(0.0, 0.0, 0.2, 1.0)` | 元素进场（先快后慢减速） |

### 6.3 弹簧参数

| 令牌 | 阻尼比 / 刚度 | 适用 |
| --- | --- | --- |
| `standardSpring()` | `MediumBouncy` + `Medium` | 常规交互反馈 |
| `emphasizedSpring()` | `MediumBouncy` + `MediumLow` | 更明显的弹性动画 |
| `noBounceSpring()` | `NoBouncy` + `MediumLow` | 开关 / 滑杆等需精确到位、无回弹处 |

### 6.4 动效红线

- 组件中**禁止硬编码** `spring(dampingRatio = …, stiffness = …)` 或 `tween(150)` 常量，统一走 `AppMotion` 的时长 / 缓动 / 弹簧函数。
- 尊重系统「减少动态效果」设置：可关闭 / 降级为淡入淡出或瞬时切换，不做强制循环动画（如持续 confetti / marquee 需可关）。
- 动效服务于状态反馈，不为动而动；同一页面同时动效元素不超过一处，避免视觉打架。

---

## 附录：令牌 ↔ 代码落点速查

| 主题 | 源文件 | 生成 / 落点 |
| --- | --- | --- |
| 颜色 | `tokens/color.json` | `AppTokens.kt#AppColor` + `theme/AppPalette.kt#appPalette()` |
| 间距 / 圆角 / 尺寸 / 布局 / 动效 | `tokens/dimensions.json` | `AppTokens.kt#AppSpacing/AppRadius/AppSizing/AppLayout/AppMotion` |
| 主题装配 | — | `theme/AppTheme.kt`（`AppLightScheme` / `AppDarkScheme` / `AppShapes` / `AppTypography`） |
| 排版 | — | `theme/AppType.kt` |
| 页面骨架 | — | `slot/AppShell.kt`（五槽位，§2.3 / §4） |
| 布局卫生 | — | `layout/AppPage.kt`（§3.7） |
| 三态 | — | `layout/AppState.kt`（§3.8） |
