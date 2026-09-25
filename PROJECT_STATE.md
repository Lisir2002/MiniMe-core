# MiniMe-core 项目状态与深度审计档案

> 本文档是项目的"外部记忆"。上下文压缩后，读取此文件即可恢复全部关键信息。
> 最后更新：2026-09-25

---

## 一、项目基本信息

| 项 | 值 |
|---|---|
| 仓库本地路径 | `/home/user/Doubao/chats/38443803293593090/minimme-audit` |
| GitHub 仓库 | `Lisir2002/MiniMe-core` |
| 技术栈 | Kotlin + Jetpack Compose + Hilt + Room + KSP |
| JDK 路径 | `/home/user/jdk/jdk-17.0.12+7` |
| 编译命令 | `JAVA_HOME=/home/user/jdk/jdk-17.0.12+7 ./gradlew :app:compileDebugKotlin` |
| push 命令 | `git push https://<token>@github.com/Lisir2002/MiniMe-core.git main` |
| 最新发布版本 | `v0.0.0.16`（APK 31MB） |
| 最新 commit | `244c5e0`（Tab 样式统一） |
| 运行中子 agent | `o_000cXJ7krAa`（UI 组件样式全面统一 P0-P2 + 追加任务） |

---

## 二、用户硬约束（最高优先级）

1. **发版构建**：必须使用 GitHub Actions 云端 runner，禁止本地 `flutter build`。流程：推送 tag → 云端自动构建 → 自动上传 APK 到 Release。本地只做 analyze/test/lint 和 git 操作。
2. **发版前验证**：推送 tag 前必须通过 analyze 0 error/warning、custom_lint 0 issue、test 全通过、CHANGELOG 已按模版更新。
3. **版本号规则**：语义化 `major.minor.patch+build`，build number 必须自增且与 tag 一致；破坏性改动升 minor，bugfix 升 patch。
4. **Tag 命名**：统一 `v{version}` 格式（如 `v0.0.53`），禁止其他格式。
5. **CHANGELOG 强制**：发版必须同时更新 CHANGELOG.md，按项目已有模版分档（新功能/修复/改进），不得空发。
6. **CI 工作流**：必须包含 Gradle/Pub 缓存、签名配置、release 构建、自动上传 APK 到 Release、构建失败通知。
7. **构建产物校验**：APK 构建后校验签名正确、非 debug 包、大小在合理范围（约 80-90MB，实际当前约 31MB）。
8. **CI 失败处理**：构建失败时不得手动本地构建绕过，必须修复 CI 问题后重新 push tag；超过 2 次失败向用户报告。
9. **密钥安全**：所有密钥通过 GitHub Secrets 注入，禁止硬编码；keystore 文件加密存储，不得提交到仓库。
10. **缺少密钥令牌**必须直接向用户索要，不得跳过、不得用占位符、不得降级为本地构建。
11. **执行方式**：串行执行不并行（并行编译导致 Gradle OOM + KSP 缓存损坏，已验证 2 次）。质量优先做慢做透。
12. **所有文件/配置放用户目录**，本地只做编译验证，全量编译在 GitHub 云端 CI。
13. **禁止 emoji** 在发版说明和 CHANGELOG 中。
14. **发版说明详细**，不要几个字概括。

---

## 三、发版规范（AGENTS.md 最高优先级约束）

- **安装包命名**：`MiniMe-{版本}-{变体}.apk`（如 `MiniMe-v0.0.0.16-release.apk`）
- **Release 标题**：`MiniMe {版本} — {≤20字概括}`
- **Release 正文格式**：
  1. 一段小字简介（100 字左右）
  2. 标准化日志：新功能 / 改进 / 修复 / 已知问题 / 安装包
  3. 只保留面向用户的日志，内部技术细节不写
- **禁止 emoji**
- CHANGELOG.md 同步更新

---

## 四、深度审计结论

### 4.1 整体架构

- 单 Activity（MainActivity）+ Navigation Compose 多页面
- 核心功能：AI 聊天（AIAgentViewModel）、终端、Git、浏览器、文件管理、设置
- 数据层：KVStore（键值存储）+ Room（数据库）+ Repository 模式
- 依赖注入：Hilt

### 4.2 UI 组件体系（审计发现三套并存）

| 体系 | 位置 | 状态 |
|---|---|---|
| 新版通用组件 | `core/theme/components/`（10个） | 推荐使用 |
| 过渡版 | `core/theme/AppComponents.kt`（6个） | 与新版重复，待合并 |
| 旧版 | `core/theme/CyberComponents.kt` | 仍被6文件引用，待删除 |

**同类型组件多种实现**：
- 开关行：4 种实现
- 按钮：3 种 + 29 文件直接用 Material3
- 卡片：2 种 + 38 文件直接用
- 空状态：4 种
- Chip/Badge：3 种
- 对话框：无统一组件（22 文件 AlertDialog + 42 文件 ModalBottomSheet）
- 输入框：无统一组件（36 文件 OutlinedTextField）

### 4.3 规范流程系统（NormFlow）

- **存储**：NormFlowSettingsRepository（KVStore，约 20 个开关键）
- **注入**：SystemPromptProvider（10 个注入源 + StepInjectionAssembler 预算裁剪 800 字符）
- **静态规则**：12 篇静态规则 md + 6 篇 SOP md
- **护栏**：FileObservationGuard（唯一实现）+ 3 个新增护栏（危险命令/大文件/路径边界）
- **7 项主规范**：stepInject / toolGuard / reasoningBudget / usageCard / sopSummary / playbookAuto / idleConverge
- **页面**：NormFlowSection（三段式仪表盘布局，分组卡片化+主题色蓝/红/绿）

### 4.4 MCP 系统

- **工具服务**（McpManager 客户端）+ **开放服务**（McpServerManager 服务器）已合并为 McpCenterScreen（Tab 切换）
- 协议常量已提取，createTransport 已抽取
- 支持 token 掩码、端口自动保存、长按菜单、自动重试、session 超时清理

### 4.5 模型供应商系统

- 已取消内置供应商，只保留自定义
- 模型能力标签体系：高价值（识图/思考/工具调用）+ 中价值 + 低价值
- 输入输出上下文长度可每个模型自定义
- 供应商设置与模型设置已分离，禁止数据污染
- 默认模型和模型服务商两个设置页面已合二为一（Tab 切换）

---

## 五、已完成的重大改造（时间线）

### 1. MCP 双功能合并（已发布 v0.0.0.16 前）
- McpCenterScreen 创建（Tab 切换外部工具/开放服务）
- 总览状态卡、协议常量提取、失败详情展开、token 掩码
- 页面内添加按钮、token 重生成确认、安全横幅
- 增量更新、createTransport 抽取、端口自动保存、长按菜单
- 空状态按钮、自动重试、session 超时清理、工具名按服务器分组
- 4 个 commit（d22e877→6e9bd80）

### 2. 规范流程系统全面改造（已发布 v0.0.0.16）
- P0：SOP 开关 bug 修复、三段式页面重组、step 注入拆 5 子开关
- P1：推理强度选择、空转阈值可调、内置规范查看器、预设方案
- P2：3 个新护栏、用量卡片配置、注入诊断面板、护栏拦截日志
- P3：分层规则管理、配置导入导出、聊天规范指示器
- 进阶布局：仪表盘式首页、分组卡片化+主题色、开关行信息摘要、预设方案卡片化、注入诊断浮层
- 3 个 commit（4ca72a2→061a204）
- 发版 v0.0.0.16：CI 云端构建成功，APK 31MB

### 3. 顶栏 Tab 样式统一（已 push，commit 244c5e0）
- 创建通用组件 AppSegmentedControl（胶囊式，≤4 等分，>4 可滚动）
- 替换 9 个页面：RemoteServerScreen(4)、TerminalBundleManagerScreen(2)、SkillDetailScreen(6)、NormFlowAssetViewer(2)、ModelManagementScreen(2)、CredentialListSection(2)、GitScreen(5)、CapabilityCenterScreen(6)、FileDiffSheet(6)

### 4. 输入框间距收紧（代码已改，编译修复中）
- 三层 padding 叠加：ChatInputField 底部 6dp + Column spacedBy 4dp + ChatInputToolbar 顶部 4dp = 14dp
- 已修改：去掉 spacedBy、输入框 vertical 6dp→4dp、工具栏顶部 padding 归零

### 5. 规范指示器 4 项优化（代码已改，编译修复中）
- 单击展开规范概览浮层（7 项主规范状态 + 前往设置）
- 长按快速切换总开关
- 状态显示启用数量"规范运行中 · X/7 项"
- 跳转目标修正为直接打开规范流程 section

---

## 六、当前进行中的任务

### 子 agent o_000cXJ7krAa（UI 组件样式全面统一）
**进度 45%**，P0-1 和 P0-2 已完成编译通过，正在修复预存编译错误。

**P0（进行中）**：
- P0-1：AppComponents.kt 合并到 components/ 目录，AppSectionHeader 参数迁移 ✅
- P0-2：AppListItem 增加 Switch 支持，替换 SwitchRow/GroupSwitchRow ✅
- 修复编译错误：ChatInputToolbar/ChatInputBar/ChatInputField/NormFlowIndicatorBar 🔄
- P0-3：删除 CyberComponents.kt
- P0-4：AppDialog 统一组件
- P0-5：AppBottomSheet 统一组件

**P1（待执行）**：
- AppTextField 统一组件
- AppSearchBar 统一组件
- AppErrorState 统一组件
- AppStatusDot 统一组件

**P2（待执行）**：
- AppButton/AppCard 推广
- 空状态统一
- IconButton/进度指示器统一

**追加任务 A**：规范指示器 4 项功能确认（代码已改，验证编译+功能完整）
**追加任务 B**：供应商卡片布局紧凑化（减少垂直 padding 30-40%、右侧开关+按钮收紧、图标缩小）

---

## 七、关键文件路径索引

### 规范流程
- `feature/settings/data/repository/NormFlowSettingsRepository.kt` — 20 个开关 KV 存储
- `feature/settings/presentation/component/NormFlowSection.kt` — 三段式仪表盘页面
- `feature/agent/presentation/component/NormFlowIndicatorBar.kt` — 聊天界面指示器
- `feature/agent/presentation/component/NormFlowIndicatorViewModel.kt` — 指示器 ViewModel
- `feature/agent/domain/SystemPromptProvider.kt` — 10 注入源 + 预算裁剪

### MCP
- `feature/mcp/presentation/McpCenterScreen.kt` — 合并后的中心页面
- `feature/mcp/data/McpManager.kt` — 客户端管理
- `feature/mcp/data/McpServerManager.kt` — 服务器管理

### 模型管理
- `feature/settings/presentation/component/ModelManagementScreen.kt` — 供应商+默认模型 Tab 页
- 供应商卡片实现（待定位，可能在同目录 ProviderModelRow 或类似文件）

### 聊天界面
- `feature/agent/presentation/component/AIChatPanel.kt` — 主聊天面板
- `feature/agent/presentation/component/ChatInputBar.kt` — 输入框容器
- `feature/agent/presentation/component/ChatInputField.kt` — 输入框
- `feature/agent/presentation/component/ChatInputToolbar.kt` — 输入框下方工具栏
- `MainActivity.kt` — 导航路由 + ViewModel 注入

### UI 组件
- `core/theme/components/` — 新版通用组件（10个）
- `core/theme/AppComponents.kt` — 过渡版（待合并）
- `core/theme/CyberComponents.kt` — 旧版（待删除）
- `core/theme/components/AppSegmentedControl.kt` — Tab 统一组件

### 配置
- `AGENTS.md` — 发版规范最高优先级
- `CHANGELOG.md` — 变更日志
- `app/src/main/res/values/strings.xml` — 中文字符串
- `app/src/main/res/values-en/strings.xml` — 英文字符串

---

## 八、GitHub Token

用户多次明文暴露 Token，建议轮换。当前 Token 在历史对话中，push 失败时检查凭证有效性。

---

## 九、已验证做不通的事

- 两个子 agent 并行编译 → Gradle OOM + KSP 缓存损坏（验证 2 次）
- 本地全量构建 → 用户禁止，必须云端 CI

---

## 十、后续待办（用户提及但未开始）

- 外观设置里的背景图卡片移除（接口预留，UI 清除）—— 已提及，待执行
- 工具服务和开放服务已合并完成
- 崩溃页面针对性强化扩展 —— 已讨论方案，待执行
- 设置页面功能摆设清理 —— 已部分完成
- 日志查看器（附属应用）智能目录识别 —— 已完成并发布 logviewer-v0.0.6
