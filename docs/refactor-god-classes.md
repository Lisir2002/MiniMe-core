# God class 拆分建议（P3 · 持续重构）

本文不是"必须立刻做"的缺陷清单，而是给后续重构的**可执行路线图**。
原则与项目既有节奏一致：**行为保持搬迁**（先把一组内聚方法原样搬到新协作者类，门面保留
对外 API 并委派），每步都可独立提交、独立回滚，不伴随行为变更。

## 现状（按行数排序的前 10 个源文件）

| 文件 | 行数 | 主要职责堆叠 |
|---|---|---|
| `browser/domain/BrowserController.kt` | 2,613 | WebView 接管 + UI 绑定 + 用户 API + 开发者工具 + 模型/工具操作 + 多标签页 + 指纹 + 宏录制 + 会话 |
| `agent/presentation/AIAgentViewModel.kt` | 1,836 | 请求编排 + 流式状态机 + 任务分组 + 队列 + 权限/提问回调 |
| `agent/.../tool/browser/BrowserAgentTool.kt` | 1,809 | 浏览器工具的 20+ 个子命令解析与分发 |
| `agent/.../workflow/StatefulAgentWorkflow.kt` | 1,767 | 工作流状态迁移 + 步骤执行 + 上下文维护 |
| `settings/.../component/SettingsScreen.kt` | 1,674 | 设置页路由壳（含大量 Section 分支） |
| `settings/.../component/AboutSection.kt` | 1,526 | 关于页：统计 + 更新 + 备份 + 诊断 |
| `terminal/.../component/TerminalContainerScreen.kt` | 1,488 | 终端容器 UI + 会话管理 + 手势 |
| `settings/.../component/ProviderEditorScreen.kt` | 1,430 | Provider 编辑表单 + 校验 + 模型探测 |
| `terminal/.../component/BundleInstallCard.kt` | 1,342 | 安装卡片 UI + 安装流程编排 |
| `settings/.../component/ThemeSettingsScreen.kt` | 1,308 | 主题配置 UI + 预览 + 导入导出 |

`BrowserController` 已经走过一轮搬迁（现有协作者：`BrowserDownloadControl`、
`BrowserFingerprintControl`、`BrowserOperationController`、`BrowserJs*` 系列等），
说明这条路径在本项目是验证过且低风险的——剩下的大块按同样方式继续即可。

## 拆分方案（按性价比排序）

### 1. `AIAgentViewModel`（1,836）— 最高性价比

| 目标类 | 搬迁内容 | 预估行数 |
|---|---|---|
| `AgentStreamReducer` | `setStreamingText` / `setStreamingReasoning` / `updateStreamingTask` / `capStreamingText` / `setRetryState` 一族（L515–700） | ~250 |
| `AgentRequestQueue` | `enqueueAgentRequest` / `removeQueuedRequest` / `processNextInQueue`（L738–811） | ~120 |
| `AgentTaskGroupBuilder` | `buildTaskGroups` / `buildSubGroups` / `subGroupType` / `deriveTaskTitle`（L372–478） | ~150 |
| `AgentRequestExecutor` | `executeAgentRequestStream` 单函数就 L825–1170（345 行），拆出流式事件处理与工具结果落库两段 | ~350 |

搬迁后 ViewModel 只保留状态容器与对外入口，预计降到 800 行以内。

**建议顺序**：先搬 `AgentTaskGroupBuilder`（纯函数、无状态、最安全），再 `AgentStreamReducer`（有状态但边界清晰），最后碰 `executeAgentRequestStream`（改动最大，需配合既有单测）。

### 2. `BrowserController`（2,613）

已在搬迁中的最大剩余块是「模型/工具操作」（L641–1239，约 600 行 suspend 操作）：

| 目标类 | 搬迁内容 | 预估行数 |
|---|---|---|
| `BrowserModelOperations` | 模型/工具操作区（L641–1239）按「快照 / 交互 / 表单」再分三个文件 | ~600（分 3） |
| `BrowserTabManager` | 多标签页 new/switch/close/list（L1371–1472） | ~110 |
| `BrowserMacroStore` | 宏录制/回放（L2262–2305） | ~60 |
| `BrowserSessionStore` | 会话管理（L2393–2469） | ~90 |

搬迁后降到 ~1,500 行，再按同样方式继续。

### 3. `BrowserAgentTool`（1,809）

20+ 子命令解析与分发 → 按命令族拆为多个 `Browser*Command` 类 + 一个分发器（分发器只做
`when (command) -> 委派`）。这是纯机械搬迁，风险最低。

### 4. 设置/终端/主题类 UI（1,300–1,700 量级）

UI 大文件的通用拆法（不追求一次性拆完，按页面局部推进）：

- 把"纯展示区块"抽成 `@Composable` 子组件文件（如 `AboutSection` → `AboutStatsCard` / `AboutUpdateCard` / `AboutBackupCard`）。
- 把"流程编排"（如 `BundleInstallCard` 的安装流程）搬进 UseCase/Controller，UI 只消费状态。
- `SettingsScreen` 的路由壳：每个 Section 已独立成文件，只需把剩余的分支表抽成 `SettingsRoute` 映射。

## 门禁（防止反弹）

好消息：**规模类门禁其实已经存在**，只是此前 detekt 从未进 CI 执行，形同虚设。
`config/detekt/detekt.yml` 现状：

| 规则 | 阈值 | 状态 |
|---|---|---|
| `complexity: LargeClass` | 600 行/类 | active ✅ |
| `complexity: LongMethod` | 60 行/方法 | active ✅ |
| `complexity: TooManyFunctions` | 文件 30 / 类 25 | active ✅ |
| `complexity: LongParameterList` | 函数 8 / 构造 8 | active ✅ |
| `complexity: CyclomaticComplexMethod` | 20 | active ✅ |
| `complexity: NestedBlockDepth` | 5 | active ✅ |

配合 `config/detekt/baseline.xml` 的"存量记账"模式，**新增**的超大类 / 超长方法 / 过多函数
会被 detekt 拦下——前提是 detekt 真的在跑。因此本轮修复把 `:app:detekt` 接进了 ci.yml
（首轮 `continue-on-error`，观察稳定后转硬门禁，见 `docs/maintainer-runbook.md` 第 7 节）。

唯一没有现成规则的是**文件行数**（detekt 1.23.x 无 `FileSize`，该规则属 detekt 2.x）。
如需文件级门禁，用一行 CI 脚本补上即可（与 detekt 版本解耦）：

```bash
# CI 中：新增/修改文件超过 800 行即告警（不阻断，先观察）
git diff --name-only origin/main...HEAD | grep '\.kt$' | while read -r f; do
  n=$(wc -l < "$f"); [ "$n" -gt 800 ] && echo "::warning::$f 行数 $n 超过 800"
done
```

## 验证方式

每步搬迁提交都必须满足：

1. `./gradlew :app:compileReleaseKotlin` 通过；
2. `./gradlew :app:testReleaseUnitTest` 全绿（无新增失败）；
3. 若为纯搬迁，**不新增也不删除**任何行为分支（diff 应表现为"方法整体位移"）；
4. 涉及 UI 的，手动过一遍对应页面主流程（本项目无 UI 自动化测试，人工验证不可省）。
