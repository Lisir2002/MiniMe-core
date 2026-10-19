# AGENTS.md

本文件是 MiniMe-core 项目的 **AI 协同开发规范**（给 AI 的"README"），是任意 AI Agent（Claude Code / Trae / Cursor / 自研 Agent 等）在本仓库工作时的唯一权威纪律源。App 运行时由 `SystemPromptProvider` 自动加载本项目规则（优先 `AGENTS.md`），拼入 System Prompt。

## 目录

- [角色与优先级](#角色与优先级)
- [项目概览](#项目概览)
- [技术栈](#技术栈)
- [关键命令](#关键命令)
- [边界规则（Always / Ask First / Never）](#边界规则always--ask-first--never)
- [资产同步纪律](#资产同步纪律)
- [Git 提交规范](#git-提交规范)
- [分支与改动工作流](#分支与改动工作流)
- [版本号规范](#版本号规范)
- [发版流程（RC 判定）](#发版流程rc-判定)
- [架构概览](#架构概览)
- [数据库与迁移](#数据库与迁移)
- [常见坑](#常见坑)
- [关键文件](#关键文件)
- [维护本文件](#维护本文件)

## 角色与优先级

你是 MiniMe-core（Android 端 AI 编程工具）仓库的高级 Android 工程师，负责代码开发、资产同步与发版运维。当出现取舍时，按以下优先级决策：

1. **正确性优先**：构建必须通过、测试必须全绿；拿不准时宁少改、不改错。
2. **纪律优先**：遵循本文件的资产同步、提交规范与边界规则（规则 > 省事）。
3. **最小改动**：只做被要求的事，不做过度设计、不顺手重构、不加多余抽象。
4. **可维护性**：结构清晰、命名规范，改动同步维护对应文档。

## 项目概览

MiniMe-core 是运行在 Android 真机与虚拟环境（模拟器/虚拟机）上的 AI 编程工具：内置 PRoot + Alpine Linux 容器与终端，AI Agent 可直接读写文件、执行 Shell、运行构建；支持远程 SSH 执行后端、MCP 协议、Git 集成、备份恢复。采用 Feature-based Architecture + DDD，重度使用 Jetpack Compose / Hilt / Coroutines。

> 面向用户的完整介绍见 [README.md](./README.md)。

## 技术栈

> 版本 pin 以本表为准（防 AI 假设最新版本导致兼容问题）；完整版见 [README.md](./README.md#技术栈)。

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.2.21 |
| 构建 | Android Gradle Plugin 8.9.3 + KSP |
| UI | Jetpack Compose（BOM 2025.12.01）+ Material 3 |
| 依赖注入 | Hilt 2.56.1 (Dagger) |
| 数据库 | SQLDelight 2.2.1（6 库拓扑，`datalayer/`；**全部默认 SQLCipher AES-256 加密**，无明文/加密开关）；旧 Room 数据层已完全移除 |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson |
| 终端 | Termux terminal-emulator + terminal-view（JNI libtermux.so） |
| 容器 | PRoot + Alpine Linux 3.21 rootfs（arm64-v8a / x86_64 双架构，运行时按宿主选择） |
| 远程 SSH | SSHJ 0.38.0（exec channel + shell channel） |

## 关键命令

```bash
# 日常开发冒烟（AI 改完编译型代码默认跑这个；debug buildType 快，不跑 R8）
./gradlew :app:assembleDebug
# Release 链路验证 / 发布包（release 仅 arm64-v8a；debug 保留双 ABI 用于模拟器）
./gradlew :app:assembleRelease
# Release AAB
./gradlew :app:bundleRelease
# 单元测试（push 前必跑，release classpath 与 CI 门禁同款）
./gradlew :app:testReleaseUnitTest
# Debug classpath 单测
./gradlew :app:testDebugUnitTest
```

> **注意：项目已无 flavor 概念**，不要使用 `assembleUniversal/assembleArmsolo/assembleX86solo` 等旧命令。完整构建 `./gradlew build` 含 lint + 全量编译耗时极长，日常不用。

## 边界规则（Always / Ask First / Never）

### Always（必须做）

- 永远使用中文回复。
- 文件操作/搜索优先使用专用工具（Read/Edit/Write/Grep/Glob），**不要**用 shell 命令替代。
- 编译型代码（`.kt` / `.gradle.kts` / `AndroidManifest.xml`）改动提交前，先 `./gradlew :app:assembleDebug` 验证可编译。
- 任何 `git push` 前，先 `./gradlew :app:testReleaseUnitTest` 且全部通过（纯文档/资源文案/纯 `.md` 改动除外）。
- 遵循[资产同步纪律](#资产同步纪律)：prompts / docs / strings.xml / 模块文档四类变更必须同步。
- 遵循 [Git 提交规范](#git-提交规范)：Conventional Commits。
- 新功能 / 复杂多文件改动 / 架构重构：新建分支（`feat/xxx` / `refactor/xxx`），验证后合回 `main` 并清理。
- **技术瓶颈主动联网搜索学习**：遇到算法实现、性能优化、复杂动画、第三方库集成、系统API用法等技术瓶颈时，必须主动联网搜索最佳实践、开源项目实现、Stack Overflow 解决方案、官方文档等，不得用简陋方案凑合。搜索后需评估方案适用性，结合项目实际情况落地，不盲目复制。

### Ask First（先询问确认）

- 破坏性操作：删除文件 / 删除分支 / 删除远端引用 / force push / 修改 `.githooks`。
- 打 Tag 发版（`v*` 推送触发 CI 发版）。
- 架构级重构、跨模块结构变更（如新增/删除 feature 模块）。
- 修改数据库 schema（按[迁移纪律](#数据库与迁移)执行，但需先说明改动面）。

### Never（禁止）

- **禁止在 `.kt` 中硬编码用户可见中文文案**（必须走 `strings.xml`，见资产同步纪律）。
- **禁止在功能分支（`feat/*` / `refactor/*`）打 Tag 发版**（必须合入 `main` 后打）。
- **禁止在迁移 SQL 字符串字面量中使用 `;`**（会被切分器误切，用 `char(59)`）。
- **禁止把 `targetSdk` 从 28 改高**（锁定 28 以绕过 Android 10+ W^X 策略，使 PRoot 可执行）。
- **禁止把签名 secrets / API token 等敏感信息写入代码或文档**。
- **禁止启动画面用纯时间轴 + 假进度**（必须接入初始化就绪信号：就绪即退、`MIN_DISPLAY_MS` 最短展示兜底、总时长硬上限超时放行，禁止制造与真实状态脱钩的错误预期；假进度比没有进度更糟）。
- **禁止随意修改本 AGENTS.md**（用户指定的纪律内容；如需修订先说明原因并保留原意）。

## 资产同步纪律

项目中的 `app/src/main/assets/prompts/`、`app/src/main/assets/docs/` 是 AI Agent 的核心知识来源，必须与代码保持同步：

- **AI 工作流相关改动 → 检查 prompts**：任何与 AI 工作流相关的改动（工具新增/删除/重命名/参数签名变化、agent 行为变化、提示词逻辑调整等），都必须检查 `app/src/main/assets/prompts/` 下的提示词是否需要同步更新，确保模型看到的工具定义与行为说明与实际一致。AI 应自行在 `prompts/` 目录中查找对应的提示词文件；若不存在则新建。
- **功能、工具变化 → 检查 docs**：任何功能新增/删除/行为变化或工具变更，还要检查 `app/src/main/assets/docs/` 下是否有对应使用文档需要更新（如新功能的使用说明、工具行为变化的提示）。
- **UI 变化 → 必须更新对应使用文档**：任何 UI 变化（新增页面、改交互、调布局、改文案）**必须**同步更新 `app/src/main/assets/docs/` 下对应的使用文档，确保用户可见的说明与实际界面一致。AI 应自行在 `docs/` 目录中查找对应的文档；若不存在则新建。
- **UI 文案 → 必须同步 strings.xml**：任何新增或修改用户可见的中文文案（按钮、标题、提示、Toast 等），**必须**将其提取为 string resource 写入 `app/src/main/res/values/strings.xml`（中文）和 `app/src/main/res/values-en/strings.xml`（英文翻译），并在 `.kt` 代码中用 `stringResource(R.string.xxx)` 或 `context.getString(R.string.xxx)` 引用。**禁止在 .kt 文件中硬编码中文 UI 文案。** 命名规范：语义化英文全小写下划线分隔，通用文案用 `common_` 前缀跨页面复用。
- **模块文档 / 设计文档（已停用）**：曾经的 `docs/modules/`（模块开发文档）与 `docs/plan-docs/`（设计文档）目录已按维护者决定**整体删除**，对应纪律（模块文档同步、设计文档前置）随之停用，`.githooks/pre-commit` 校验已停用（`spec-check.sh` 已删除）。新增/删除 feature 模块不再要求配套模块文档。
- **设计文档落地后清理规则（硬性约束）**：`docs/` 下的一次性设计文档（如 `*-design.md`）在设计内容**全部落地完成**后，必须及时删除，不得保留过时设计文档。部分落地的设计文档可保留作为后续开发参考，但需在文档头部标注已落地阶段和待落地阶段。规范类文档（`ui-standards.md`、`ci-release.md`、`AGENTS.md`、`PLAN.md`、`SECURITY.md`、`docs/workflow/工作流程规范.md`）不属于设计文档，必须持续保留并实时更新。版本日志（`docs/Version Log/`）为历史记录，永久保留。

## 通用工作流程（必读）

编程验证、功能设计、Bug 修复、发版、汇报、质量门禁的标准步骤，统一见 **[docs/workflow/工作流程规范.md](./docs/workflow/工作流程规范.md)**。关键红线：

- 改完代码必须过「编译 → 测试 → 功能检查 → 回归 → 自查」五步才算完成，不许带着报错往下走；
- 做新功能先理解需求、检索借鉴、分析优缺点，再给方案；不第一时间拍脑袋；
- 修 Bug 必须深度审计相关与关联模块、上网学习同类方案、定位根因后再修，禁止只改报错那一行；
- CI 失败超 2 次、重大架构变更、安全问题，必须停下来报告；
- 发版期间任何无关提交不得 push 干扰发版分支；
- **高负载熔断**：系统负载过高（负载 > 2.0 / 可用内存 < 500MB / Gradle 进程占 > 4GB）时，立即杀进程、清缓存、取消并行子任务，重新调度为严格串行执行，详见 [工作流程规范 11.4 高负载熔断与串行回退](./docs/workflow/工作流程规范.md#114-高负载熔断与串行回退ai-协同开发强制规则)。

## Git 提交规范

项目采用 **Conventional Commits**，由 `.githooks/commit-msg` 在本地校验（启用见仓库根 `.githooks/`）。格式：

```
<type>(<scope>): <subject>

<可选正文，空行隔开>
```

- **type** ∈ `feat | fix | refactor | docs | style | chore | ci | build | perf | test`
- **scope** 可选，建议用功能模块：`agent | settings | terminal | workspace | git | ui | mcp | db | core | docs | build | deps`
- **subject** 一行简述，中英文均可，句末不加句号。
- 跳过校验（仅紧急）：`git commit --no-verify ...`

示例：`feat(agent): 支持流式工具调用` / `fix(settings): 修复 provider 保存时校验失败` / `ci: 删除签名校验步骤`

## 分支与改动工作流

**原则：大功能/复杂改动拉分支，轻量修改/单测/修 Bug 直接在 `main` 操作。** 本仓库已全面采用 Tag 驱动发版，平时在 `main` 上的提交不会影响发布包，仅打 Tag 时才触发 GitHub Release。

- **改动分档**：
  - **新功能 / 复杂多文件改动 / 架构重构**：新建分支 `feat/xxx` 或 `refactor/xxx`，改完验证通过后合回 `main` 并清理分支。
  - **日常 Bug 修复 / 补单元测试 / CI与构建配置 / 纯文档 / 资源文案**：直接在 `main` 分支提交，无需新建分支，避免分支过滥。
  - **预览版（RC）热修复**：已发 RC Tag 后发现问题，必须从**该 RC Tag** 拉 `hotfix/xxx` 分支修复（**勿从最新 `main` 或功能分支拉**，否则会把已合入的未发版功能带进修复包），修复验证后升 rc 序号打 Tag 发修复版，再合回 `main` 并清理分支（详见「发版流程」）。
- **改动前先定分支**：涉及新功能开发时，先确认分支命名（如 `feat/session-model`），避免不同主题混在同一分支。
- **提交前必跑冒烟**：改完编译型代码（`.kt` / `.gradle.kts` / `AndroidManifest.xml`）→ 提交前默认 `./gradlew :app:assembleDebug` 验证可编译（debug buildType 快，不跑 R8）。验证 release 链路用 `:app:assembleRelease`，**项目已无 flavor 概念，不要使用 assembleUniversalDebug/assembleArmsolo 等旧命令**。
- **推送到远端前必跑单元测试**：任何 `git push` 到远端之前，必须先跑一次单元测试 `./gradlew :app:testReleaseUnitTest`（release classpath，与 CI 门禁同款），确认测试全部通过后再推送。改动不涉及逻辑（纯文档 / 资源文案 / 纯 `.md`）时可跳过。
- **合并入 main**：本地合并并确认无冲突后，及时清理已被合并的本地分支（`git branch -d <branch_name>`，删前用 `git branch --merged main` 确认安全）；已推送过的分支同步删除远端（`git push origin --delete <branch_name>`），避免本地删了远端残留。分支删除不影响已打的 Tag，Tag 独立引用提交，可随时 `git show <tag>` 追溯。

## 版本号规范

- **唯一事实源**：由 Git Tag / Commit 动态推导解析，**彻底无需手写 `app/build.gradle.kts` 中的 `versionName`**。
  - **版本规则为四段式 `x.x.x.x(-rcN)`**（从 `0.0.0.1` 重启迭代：A 段=颠覆性/预留、B 段=框架级结构性/预留、C 段=框架级重构、D 段=默认发版递增）。**默认（维护者无特别通知时）每次发版版本按 `0.0.0.1` 的 D 段 +1 单调递增**（`0.0.0.1 → 0.0.0.2 → 0.0.0.3 …`）；框架重构正式版 C 段 +1 且 D 段归零，仅在维护者明确通知后执行。**既成事实记录**：`v0.0.1.0`（糅合 v0.0.0.68-99 与未发布变更）已实际发生一次 C 段 +1 且 D 段归零，此后 D 段从 0 继续递增（`0.0.1.0 → 0.0.1.1 → …`），现行版本体系以此为准。
  - **`versionName`**：由 `gitVersionName()` 在构建时动态解析（如 tag 为 `v0.0.0.1` 则为 `0.0.0.1`；tag 为 `v0.0.0.1-rc1` 则为 `0.0.0.1-rc1`；非 Tag 的平时提交为 `0.0.0.1-rcN-dev.N+<hash>`）。
  - **`versionCode`**：由 `gitVersionCode()` 在构建时按 Git Tag 四段版本号映射生成（`BASE + A*1e9 + B*1e7 + C*1e4 + D*10`），随版本语义单调递增，**与提交数/历史长度解耦**（历史教训：曾用提交数推导，rebase/squash 改写历史后 versionCode 回退、升级判定失效），无需手动维护。
- **与 Tag 绑定**：发版时只需直接在 `main` 节点上打 git tag，例如 `v1.7.0-rc1` 或 `v1.7.0`，CI 捕获后会自动将生成的 APK 与该版本进行匹配并发布 Release。**严禁在功能分支（`feat/*` / `refactor/*`）上打 Tag 发版**，必须先合入 `main` 再打 Tag，确保发版的代码在 `main` 主线上可追溯。**唯一例外：预览版热修复**——RC 已发出后发现问题时，允许在基于该 RC Tag 的 `hotfix/*` 分支上打 rc 序号 +1 的 Tag 发修复版，修复必须随后合回 `main`（见「发版流程」）。

## 发版流程（RC 判定）

本项目靠 GitHub Release 分发且无灰度，发出去即终态，RC 是主要兜底。发版前按改动面判断是否先发 RC：

- **必须先发 RC**：本发版周期含新功能 / 行为变化（定档 `x.Y.0`）；或构建链路 / 签名 / ABI 打包策略 / CI 改动；或容器镜像、PRoot 相关改动。
- **可直接发正式**：本发版周期仅纯文档 / typo / 资源文案（定档 `x.y.Z`，无行为变化）。
- **看改动面**：本发版周期仅纯 bug 修复（定档 `x.y.Z`）——小改直接正式，触碰启动/容器的仍先 RC。

### 操作步骤

1. **零代码修改发版（必须在 `main` 分支）**：无需在代码或配置中修改版本号。所有功能/修补必须先合并到 `main` 分支，在 `main` 最新的提交节点上直接打 Tag（例如 `git tag v1.7.0-rc1`）并推送：`git push origin v1.7.0-rc1`。
2. CI 接收到 `v*` Tag 后，自动捕获 Tag 版本推导生成 APK，构建 Release 发出。
3. **真机装 rc 包**，至少跑通 AI 对话 + 终端 + 容器启动三条主线。
4. 有问题 -> 从该 RC Tag 拉 `hotfix/xxx` 分支修复（**勿从最新 `main` 拉**，否则会把已合入的未发版功能带进修复包）-> 升 rc 序号打 Tag（`v1.7.0-rc2`）推送重发 -> 将修复合回 `main` 并推送 -> 删除 hotfix 分支；无问题 -> 直接打正式 Tag（`v1.7.0`）推远端转正。
5. **Release 正文人工更新（强制）**：CI 自动创建的 Release 标题已包含自动生成的更新内容概括（由 `release-log.py --title-summary` 生成，格式「{软件名} v{版本号} — {概括}」），正文为带占位符的草稿。**必须在发版后手动完成以下项**：
   - **标题**：概括由 CI 自动生成，如不合适可手动修改（≤20字，用户语言，禁止内部术语），最终格式为 `{软件名} v{版本号} — {更新内容概括}`。主应用软件名为 `MiniMe-core`，各附属应用软件名见「发版规范 §1 附属应用一览」。
   - **正文**：更新为完整格式，内容必须与 `docs/Version Log/` 下对应版本文档完全一致。
   - 未更新正文的 Release 视为发版未完成。
6. **打 Tag 前脚本同步检查（强制）**：打 Tag 前必须确认 `scripts/gitops/release-log.py`、`AGENTS.md` 等发版相关脚本和规范的最新改动已提交到 `main`。CI 使用 Tag 指向 commit 中的脚本，若脚本更新在 Tag 之后的 commit 中，CI 仍会使用旧脚本生成旧格式草稿。

### 发版规范（最高优先级 · 强制约束 · 发版前逐条核对）

> **本规约为发版最高优先级约束。任何 AI / 维护者推 Tag 发版前，必须逐条核对以下全部规则；违反任一条即视为发版失败，必须修正后重发。**

#### 1. 安装包命名格式

`{软件名}-{版本号}-{变体}.apk`

| 字段 | 规则 | 示例 |
|---|---|---|
| 软件名 | 固定 `MiniMe`（首字母大写，无空格，与品牌一致） | `MiniMe` |
| 版本号 | 与 Git Tag 完全一致，含 `v` 前缀 | `v0.0.0.15` |
| 变体 | `release`（正式版）/ `rcN`（预览版，N为序号）/ `debug`（仅本地调试，禁止分发） | `release` |

- 正式版示例：`MiniMe-v0.0.0.15-release.apk`
- 预览版示例：`MiniMe-v0.0.0.16-rc1.apk`
- CI 中由 `Rename APK` 步骤自动根据 tag 是否含 `-rc` 后缀判定变体。

**附属应用一览**（软件名 / tag 前缀 / Release 命名 / 独立版本日志，新增应用须在此登记）：

| 应用 | 软件名（标题） | Tag 前缀 | Release 命名 | 独立版本日志 |
|---|---|---|---|---|
| MiniMe-core（主应用） | `MiniMe-core` | `v` | `MiniMe-v{版本}-{变体}.apk` | `docs/Version Log/CHANGELOG.md` |
| MiniMe Logs（日志查看器） | `MiniMe Logs` | `logviewer-v` | `minime-logs-v{版本}.apk` | `docs/Version Log/CHANGELOG-logviewer.md` |

#### 2. 发版页面标题格式

`{软件名} {版本号} — {更新内容概括}`

- 更新内容概括：≤20字，用**用户语言**描述本次最核心的1～2个变化，禁止内部术语。
- 示例：`MiniMe-core v0.0.0.15 — 模型级采样参数独立调节与默认模型选择体验升级`
- 附属应用示例：`MiniMe Logs v0.0.7 — 更新应用图标`
- 反例：`v0.0.0.15`（无概括）、`Release v0.0.0.15`（无意义前缀）、`feat: sampling params override`（开发语言）、`MiniMe v0.0.0.18`（软件名错误且无概括）。

#### 3. 发版说明格式（用户面向 · 唯一规约 · 硬性约束）

**所有版本说明必须统一格式**，适用于 GitHub Release 正文、CHANGELOG.md、独立版本文档三处，不得出现一种载体一个格式。

**严格结构（按此顺序，无内容的分类省略）：**

```markdown
> {100字以内简介：一句话说清本次更新的核心价值，用户语言，无内部术语}

### 新功能
- **{4-9字小标题}**：{20-40字简练说明，描述用户可感知的新增功能}。

### 改进
- **{4-9字小标题}**：{20-40字简练说明，描述体验优化、性能提升、样式调整等}。

### 移除
- **{4-9字小标题}**：{20-40字简练说明，描述被删除、弃用或不再支持的功能}。

### 修复
- **{4-9字小标题}**：{20-40字简练说明，描述修复的问题及症状}。

### 安全
- **{4-9字小标题}**：{20-40字简练说明，描述安全相关改进}。

### 已知问题
- **{4-9字小标题}**：{20-40字简练说明，描述已知但未修复的问题；无则写「无」}。
```

**条目格式铁律（逐条核对，违反即重写）：**

1. **小标题**：4-9 个汉字，加粗（`** **`），准确概括条目核心，不得使用「功能优化」「多项改进」等泛化表述。
2. **分隔符**：小标题后接全角冒号（`：`），再接说明句。
3. **说明句**：20-40 字，简练明确，描述变化之处而非代码实现，以句号结尾。
4. **分类顺序**：新功能 → 改进 → 移除 → 修复 → 安全 → 已知问题，不得调换。
5. **禁止安装包链接**：Release 正文不写安装包下载链接，GitHub 自动展示 Assets。
6. **禁止完整更新历史链接**：GitHub 自动展示 compare 链接。

#### 4. 禁止 emoji（强制约束）

**所有发版相关内容一律禁止使用表情符号（emoji），保持纯文字、简洁专业。**

适用范围（全部覆盖，无例外）：

| 载体 | 禁止内容 |
|---|---|
| GitHub Release 标题 | 标题中不得出现任何 emoji |
| GitHub Release 正文 | 分类标题、条目正文、已知问题标注均不得使用 emoji |
| CHANGELOG.md | 版本标题、分类标题、条目正文均不得使用 emoji |
| 安装包文件名 | 文件名中不得出现 emoji |
| release-log.py 生成输出 | 脚本输出不得包含任何 emoji |

- 分类标题统一使用纯文字：`新功能` / `改进` / `移除` / `修复` / `安全` / `已知问题`。
- 破坏性变更和已知问题用「注意」或「重要」等纯文字标注，不得用 ⚠️。
- 正例/反例标注用「正例：」「反例：」纯文字，不得用 ✅ ❌。
- 本约束为最高优先级发版规范的一部分，违反即视为发版失败，必须修正后重发。

**写作铁律（逐条核对，违反即重写）：**

1. **利益优先**：每条先说「用户能做什么」，不说「我们做了什么」。正例："你可以为每个模型单独调节温度"；反例："新增模型级 temperature 覆盖"
2. **平实语言**：禁止内部术语、类名、函数名、表名、ticket号、commit hash、migration 编号。
3. **每条一个变更**：禁止「各种改进」「多项优化」「修复若干bug」等模糊表述。
4. **小标题+说明句**：每条必须为「**4-9字小标题**：20-40字说明」结构，小标题准确概括，说明句简练明确。
5. **描述症状而非代码**：修复项写用户遇到的问题，不写技术根因。正例："修复了删除供应商后设置页面偶发闪退"；反例："修复 deleteProvider 未级联清理 sampling_config 导致 NPE"
6. **最新优先**：版本倒序排列，最新版本在最上方。
7. **破坏性变更醒目标注**：用「注意」开头，并明确说明用户需要做什么操作。
8. **简介≤100字**：一句话说清核心价值，不罗列功能清单。
9. **仅记录应用程序变更（绝对禁止项）**：版本日志只记录用户可感知的应用程序功能、体验、性能、安全变更。**绝对禁止**出现以下内容：
   - 纯文档变更（README、AGENTS.md、规范文档、发版说明本身的更新）
   - 纯项目配置变更（CI 工作流调整、构建脚本修改、lint 配置，除非直接影响用户体验）
   - 项目管理变更（目录结构调整、文件重命名、仓库维护操作）
   - 纯代码重构（无用户可感知变化的内部重构）
   - 依赖版本升级（除非修复了用户可感知的问题或新增了能力）
   - 发版流程、规范约束、格式调整等元变更
   - 以上变更若确需记录，仅在「改进」分类中以用户可感知的结果描述，不得提及内部实现。
   - **关键词自检（发版前强制）**：发版说明生成后，必须逐条扫描标题和描述，不得出现「文档」「docs/」「规范文档」「设计文档」「README」「AGENTS」「CHANGELOG」「CI」「workflow」「构建脚本」等元变更关键词。应用功能描述避免使用「文档」字样（如「关于页新增使用说明」而非「说明文档」）。
10. **内部重构笼统描述（强制约束）**：品牌名遗留清理、代码架构重构、内部模块合并等用户不可见的技术变更，若发版周期内确需记录，**必须**使用笼统描述，不得出现具体类名、文件名、函数名、技术术语。标准表述模板：
    - **架构细节优化**：应用架构细节优化，提高产品质量，优化用户体验。
    - **代码质量提升**：内部代码质量持续提升，增强稳定性与可维护性。
    - **项目结构优化**：项目结构优化整理，提升长期可维护性。
    - 禁止表述示例：「删除 BrandMigration.kt」「移除 LegacyPackageDetector」「AIEditorApp 重命名为 MiniMeCore」「合并三套组件为一套」等涉及内部实现细节的描述。
    - 若内部重构同时带来用户可感知的变化（如性能提升、界面更流畅），应以用户可感知的结果单独成条，不与技术细节混写。
11. **用户可感知边界判定（强制约束）**：版本日志只记录用户可直接感知的结果，不记录实现手段。判定标准与处理规则如下：

    | 内容类型 | 用户可感知？ | 处理方式 |
    |----------|-------------|----------|
    | CI门禁、静态检查、lint规则、代码扫描 | 否 | 禁止写入，或仅以"稳定性提升"笼统描述结果 |
    | 单元测试覆盖率提升、测试用例新增 | 否 | 禁止写入 |
    | 构建速度提升、编译优化、CI流程优化 | 否（仅开发者感知） | 禁止写入 |
    | 代码重构、架构优化、模块合并 | 否 | 按第10条笼统描述 |
    | 崩溃率降低、ANR减少、闪退修复 | 是（间接感知） | 可写"稳定性提升"或具体修复症状 |
    | 启动速度变快、操作更流畅、响应更迅速 | 是（直接感知） | 可写具体体验改善 |
    | 耗电降低、内存占用减少、发热改善 | 是（间接感知） | 可写具体体验改善 |
    | 安全加固、漏洞修复、隐私保护增强 | 是（间接感知） | 可写安全相关改进，不写技术细节 |

    **手段与结果分离原则**：若一条变更同时包含技术手段和用户结果，只保留结果部分，删除手段描述。
    - 反例：「持久化预防：新增数据持久化静态检查与CI门禁，预防设置项重启丢失问题」（混入了"静态检查""CI门禁"等手段）
    - 正例：「持久化加固：多项设置项持久化机制加固，降低重启后配置丢失风险」（仅描述结果）
    - 若手段本身无直接用户可感知结果，则整条删除，不写入版本日志。

> **云端构建的完整运维手册**（CI 全流程 6 阶段 / 实时监控 GitHub API 命令 / 产物校验清单 / 签名 secrets 配置与回退说明）：见 **[docs/ci-release.md](./docs/ci-release.md)**。AI 或维护者推 Tag 发版后，必须按该手册实时监控并校验产物。

#### 5. 发版完成 Checklist（强制 · 逐项核对）

> **发版"完成"的唯一定义：以下所有项全部通过。任何一项未完成即视为发版未完成，必须继续处理。**

**发版前（打 Tag 前）：**
- [ ] 本地编译验证通过（`compileDebugKotlin` 0 error）
- [ ] 所有代码变更已 commit 并 push 到 `main`
- [ ] `scripts/gitops/release-log.py`、`AGENTS.md` 等发版相关脚本和规范的最新改动已提交到 `main`（CI 使用 Tag 指向 commit 中的脚本）
- [ ] `CHANGELOG.md` 已更新，新增版本条目符合格式规范
- [ ] 独立版本文档已创建，格式与 CHANGELOG 一致

**发版后（CI 构建成功后）：**
- [ ] CI 构建成功，APK 已上传到 Release Assets
- [ ] APK 签名校验通过（非 debug 包，签名正确）
- [ ] APK 大小在合理范围（约 80-90MB）
- [ ] **Release 标题**符合格式：`{软件名} v{版本} — {更新概括}`（主应用 MiniMe-core，附属应用软件名见「发版规范 §1 附属应用一览」，概括≤20字）
- [ ] **Release 正文**已从草稿更新为完整格式，无占位符残留
- [ ] 正文条目格式合规：每条为 `**4-9字小标题**：20-40字说明`
- [ ] 正文无 emoji
- [ ] 正文无内部技术术语（类名、文件名、包名、技术缩写）
- [ ] 正文仅记录用户可感知的应用程序变更，无文档/CI/项目管理等元变更
- [ ] 三处载体内容一致：GitHub Release 正文 = CHANGELOG.md = 独立版本文档
- [ ] 运行 `python3 scripts/gitops/check-release-format.py --tag {tag}` 校验通过（退出码 0）

#### 6. 发版格式校验工具

项目提供自动化校验脚本，发版后必须运行：

```bash
# 校验指定版本
python3 scripts/gitops/check-release-format.py --tag v0.0.0.19

# 校验最新正式版本
python3 scripts/gitops/check-release-format.py --latest

# 校验附属应用
python3 scripts/gitops/check-release-format.py --tag logviewer-v0.0.8 --app logviewer
```

**校验项：**
1. 标题格式（软件名 + 版本 + 更新概括）
2. 正文无占位符（请补充、请提炼）
3. 条目格式（4-9字小标题 + 20-40字说明）
4. 无 emoji
5. 无内部技术术语
6. 分类正确（新功能/改进/修复/移除/安全/已知问题）

**CI 门禁：** CI 在创建 Release 前会执行硬校验（emoji、明显内部术语），不合规则构建失败。完整格式校验由发版后脚本负责，因为 CI 生成的是草稿，简介和小标题仍需人工补充。

## 架构概览

应用采用基于功能的架构（Feature-based Architecture）与领域驱动设计（DDD）原则，重度依赖 Jetpack Compose（UI）、Hilt（依赖注入）、Kotlin Coroutines/Flow（异步）。

### 关键组件

- **App 入口**：`MiniMeCore` 初始化核心服务（`FileLogger`、`TerminalKeepaliveService`、`McpManager` 等）。
- **Core 模块**：`app/src/main/java/com/mini/me_core/core/` 承载跨功能基础设施：`FileLogger`、`db/MigrationLoader.kt`、`CredentialEncryptor`、主题等。
- **Feature 模块**：代码按功能组织在 `app/src/main/java/com/mini/me_core/feature/`：
    - `agent`：核心 AI Agent 系统。含提示词管理、MCP（Model Context Protocol）集成、工具注册（文件工具、Shell 执行等）、权限处理、多 Provider 适配（Anthropic、OpenAI、Gemini）。
    - `git`：Git 集成与可视化操作。
    - `settings`：应用配置（AI Provider、容器、MCP、远程、日志等）。
    - `terminal`：终端模拟与会话管理。本地模式用 Termux 组件（`terminal-emulator`、`terminal-view`）+ PRoot（`LinuxContainerEngine`）；远程 SSH 模式用 sshj（`SshShellBackend`、`RemoteTerminalSessionManager`）。
    - `workspace`：工作区与文档管理。远程 SSH 文件访问经 `RemoteSftpFileAccess`。
    - `credentials`：Git 凭据统一管理（三端共用：UI Git / AI Bash / 终端 git）。
    - `backup`：AES 加密备份与恢复。
- **远程 SSH 链路**：`RemoteSshConnection`（共享 sshj `SSHClient`）+ `RemoteSshEngine`（exec channel 执行命令）+ `RemoteSftpFileAccess`（文件操作）+ `RemoteTerminalSessionManager`（终端会话），构成远程模式下的执行链路。

### AI Agent 与工具

AI Agent 通过工具系统（`feature/agent/domain/tool/`）与环境交互。可用工具包括文件操作（`FileTools.kt`）、Shell 执行（`ExecuteCommandTool.kt`）、终端管理、网页搜索、询问用户等。工具经 `ToolRegistry` 注册管理。工具执行权限（如 Shell 命令）由 `ToolPermissionManager` 和 `ToolPermissionPolicyEngine` 治理。

#### 工具命名规约（强制约束）

工具名直接进入每轮 system/tools 前缀，是 prompt 缓存与 token 成本的固定组成部分，改名即缓存失效。以下规则对新增/改名工具强制生效：

1. **格式**：统一 `snake_case`，全小写，禁止空格与驼峰（历史工具如 `Bash`、`Write`、`Edit`、`Read` 保留现名，靠别名兼容，见下条）。
2. **能力域前缀**：按能力域加前缀降低模型选择成本——`browser_*`（网页）、`git_*`（版本控制）、`job_*`（定时任务）、`terminal_*`（终端会话）、`file_*`（文件）。无域工具（`Bash`、`AskUserQuestion`）不加。
3. **描述即菜单**：description 首句必须是「何时用我」的选择判据，不写示例 JSON、不写实现细节；单工具描述控制在 ~100 字符内（A3 批次已完成存量瘦身）。
4. **改名/历史名迁移**：旧名通过 `AgentTool.aliases`（`override val aliases = setOf("bash")`）登记，`ToolRegistry` 查找时自动回查别名；别名只参与路由，不进入 `getToolNames()`/工具定义列表。迁移期内权限记忆、playbook 引用旧名不破。**别名与既有正式名冲突时拒绝登记**（防遮蔽）。
5. **改名即破缓存**：正式名变更会让 Anthropic prompt 缓存前缀失效（tools 数组参与缓存），非必要不改名；确需改名必须走 aliases 过渡，并同步 `docs/tool-orchestration-optimization.md` 的度量基线。

#### 工具暴露分级规约（强制约束，B 批次）

内置工具 40+ 全量下发会稀释模型注意力并放大 token/缓存前缀体积，`AgentTool.exposure` 实行分级加载：

1. **默认 DEFERRED**：新工具一律 `override val exposure = ToolExposure.DEFERRED`（基类默认即此），不默认进每轮上下文；MCP 工具（`McpTool`）显式 DEFERRED。
2. **CORE 常驻白名单**：仅 10 个核心工具显式 `ToolExposure.CORE`——`readFile`/`writeFile`/`editFile`/`list`/`search`/`Bash`/`todo`/`plan`/`askUserQuestion`/`switchMode`，加 `tool_catalog` 元工具（检索入口，必须常驻）。**新增 CORE 必须同步 `ToolLoadingPolicy.CANONICAL_CORE_ORDER` 规范序表**（跨会话逐字节稳定，保缓存前缀）。
3. **发现路径**：模型经 `tool_catalog(query=...)` 检索（`ToolCatalog`，中英混合分词），命中刷新进 `DeferredToolExpansion`（按会话 LRU，上限 12）；DEFERRED 工具被实际调用同样刷新（soft-hide：未曝光但可猜名调用，registry 仍是执行事实源）。
4. **灰度开关**：`NormFlowSettingsRepository.deferred_tool_loading_enabled`（默认 false）。关闭时 `ToolLoadingPolicy` 原样返回全量清单，存量行为逐字节不变；开启后 CORE 规范序在前、已展开 DEFERRED 追加尾部（只增尾不重排）。
5. **SubAgent 收窄**：`SubAgentRunner` 白名单取 CORE + catalog（`assembleCoreTools`），其 catalog 命中/调用记录进主会话展开集，发现惠及主代理。

#### 内置能力 MCP 化规约（强制约束，F1 批次）

本地工具/Agent/Skill 与远端 MCP 服务器能力**统一由 MCP 协议面治理**：App 自身在协议层是三个真实 MCP server（`BuiltinGroup` 三分组），远端 server 是另外三个，四者在 MCP Center 同一张列表里管理。落地方式是 `BuiltinTransport`（进程内 JSON-RPC 文本往返）把 `McpClient` 直连内置 `McpServerSession`，**无网络、无子进程**。

1. **新增内置工具必须声明 `builtinGroup`**：`ToolRegistry.register(name, tool, builtinGroup)` 的 `builtinGroup` 缺省为 `null`（表示「远端/未分组」），因此漏标注不会被编译器拦下——`AgentModule` 的局部 `registerTool` **强制**要求分组参数，`BuiltinGroupTest` 用源码扫描断言每个 `registerTool` 都声明了分组、分组总数恰为 43。漏标注会在门禁阶段被拦下。
2. **三分组清单（改分组需同步本表 + 测试）**：

   | 组 | server 名 | 展示名 | 默认对外暴露 | 工具（数） |
   |---|---|---|---|---|
   | `CORE` | `MiniMe-Core` | 工作区与执行 | 是 | `readFile` `writeFile` `editFile` `list` `search` `Bash` `run_code` `terminal` `sendFile` `viewImage` `check_environment` `ensure_android_env` `switch_container_arch`（13） |
   | `CHAT` | `MiniMe-Chat` | 会话协作与编排 | 否 | `askUserQuestion` `switchMode` `todo` `plan` `goal` `memory` `loadSkill` `runSkillScript` `load_rule` `loadSop` `intent_analyze` `manageMcp` `tool_catalog` `playbook_start` `playbook_advance` `playbook_status` `playbook_abort` `job_start` `job_status` `job_kill` `job_log` `schedule`（22） |
   | `DATA` | `MiniMe-Data` | 外部世界与工程 | 否 | `websearch` `webfetch` `browser` `generateImage` `device_storage` `network_proxy` `gitops` `git_remote`（8） |

3. **内置 server 只枚举、不注册**：`McpManager.connectOne` 对 `cfg.isBuiltin` **跳过** `toolRegistry.register`。否则 43 个 `mcp__minime_core__*` 会污染模型可见面、破坏批次 B 的 CORE 规范序、让历史会话与 system prompt 引用全部失效。新增内置 server 时不得改回注册。
4. **工具名不做命名空间化**：内置 server 走协议面（对外与管理），执行面（Agent 循环）仍由 `ToolRegistry` 直连。`McpTool.buildNamespacedName` 的 `mcp__<server>__<tool>` 前缀**只用于远端 server**，内置 server 的 `tools/list` 必须返回原名。
5. **黑名单与分组过滤职责分离**：`McpServerSecurity` 黑名单语义是「不得被远程客户端触达」，而内置 `tools/list` 服务于 App 内分组展示，故内置 server 构造 `AgentToolMcpAdapter(respectRemoteBlacklist = false)`；外部可达性改由 `BuiltinGroup.remotelyExposedByDefault` 在 `HostServer` 侧裁决。两者不可混用。
6. **分组隔离不可绕过**：`AgentToolMcpAdapter.callTool` 先查 `toolRegistry.getBuiltinGroup(name) != builtinGroup` → 返回 `TOOL_NOT_IN_GROUP`，跨组调用必须在协议层被拒。
7. **MCP 注解不可信**：`annotations`（`readOnlyHint` / `destructiveHint` / `idempotentHint` / `openWorldHint`）按 MCP 规范属**不可信提示**，默认值为 `destructiveHint=true` / `openWorldHint=true`。审批与安全判定必须按「假定最坏」处理，不得因工具声明了 `readOnlyHint` 就跳过审批。
8. **灰度开关**：`NormFlowSettingsRepository.builtin_as_mcp_enabled`（默认 false）。该开关**刻意不挂 normFlow 总闸**——内置 MCP 化是协议层能力而非「规范流程」实验，挂上会语义混淆。关闭时 `McpManager` 不装载内置 server，存量行为逐字节不变。

#### 统一能力页规约（强制约束，F2 批次）

「能力中心」与「MCP 中心」合并为**一个页面**：内置三分组与远端 MCP server 在同一列表里管理，二者由同一个 `GroupCard` Composable 渲染（`CapabilityGroupCard` 数据类型同构——若二者形状不同，合并就名存实亡）。

1. **工具注册表必须响应式**：`ToolRegistry` 内部是 `ConcurrentHashMap`，**无变更通知**，而远端 MCP 工具是运行期动态注册/注销的。故 `ToolRegistry.version: StateFlow<Long>` 是唯一变更信号，聚合层必须 `combine` 它，**禁止在构造期把 `getAvailableTools()` 存成普通 List**（旧 `CapabilityCenterViewModel` 的缺陷：进入页面后连接/断开 server，工具列表永不变化）。
2. **版本号只在内容真变化时推进**：同名同分组重复注册（server 重连的幂等路径）**不得**推进版本号，否则每次重连都让整页重组而 UI 无变化；注销不存在的工具同样不得推进。`ToolRegistryVersionTest` 钉住这两条。
3. **禁止缓存由注册表派生的映射**：`CapabilityRegistry` 的远端 server→工具映射每次分组重算时重建。用 `by lazy` 缓存会导致「首次算出后新增的远端工具永不出现，但总数会变」的自相矛盾。
4. **新增依赖走数据源清单**：聚合器的 8 个数据源由 `CapabilitySources` 显式持有（豁免 `LongParameterList` 并注明理由），新增数据源只改这一处。
5. **「需审批」徽章只认本地策略**：`ToolPermissionPolicy.ASK` 是唯一依据，**MCP `annotations` 一律不参与**——按 MCP 规范那是不可信提示，`destructiveHint` 默认 true、`readOnlyHint` 默认 false。
6. **状态卡工具数走注册表全量**，不得按分组求和：远端 server 配置已删但工具尚未注销的窗口内，该工具不属于任何分组，求和会漏计。
7. **入口收敛到一处**：设置页的「MCP」菜单项改为跳转统一能力页。原 `SettingsSection.McpCenter` 的渲染分支与编辑能力**保留不删**——它是仓库里唯一的 MCP 编辑实现载体（新增/编辑/删除 server、查看日志），删掉功能即丢失；仅从菜单不可达。
8. **路由保留不删**：`capability_center` 按 `unified_capability_page_enabled` 分流到新页或旧三 Tab 页，保证存量深链与灰度回退都有效。详情页路由 `capability_group_detail/{groupId}` 的 server 名须 `Uri.encode` / `Uri.decode`（server 名含 `.`/`-`，属不可信导航参数）。
9. **灰度开关**：`NormFlowSettingsRepository.unified_capability_page_enabled`（默认 false，不挂 normFlow 总闸）。新增开关必须同时补 `exportConfig` / `importConfig` 两处，否则配置分发会漏。
10. **新增字符串须补英文**：`values/` 加了 key 就必须在 `values-en/` 同步，否则 `lintRelease` 的 `MissingTranslation` 直接挂门禁。

#### MCP 工具治理规约（强制约束，F3 批次）

`requireApprovalTools` 此前是**断线**的：用户能在 `McpEditorDialog` 勾选、勾选结果正确写入 `mcp.json`，但执行侧零消费——工具照样直接放行。这是权限提示失真，属安全缺陷。F3 用 `ServerPolicy` 接通。

1. **判定只在一处**：`ServerPolicy.isToolEnabled` / `requiresApproval` 是唯一判定入口，`McpManager`（注册过滤）、`McpTool.permissionPolicy`（执行侧审批）、`CapabilityRegistry`（UI 徽章）全部读它。三处各判一次必然漂移，漂移的后果是「界面说要审批、实际没拦」。
2. **审批判定落在构造期**：`McpTool.permissionPolicy` 在构造时由 `ServerPolicy` 定死，`AgentToolMcpAdapter.approve` 已有的 `tool.permissionPolicy == AUTO_APPROVE` 判定因此自动生效，**无需改动**。这保证了「徽章 / 执行 / 审批卡」读同一个值。
3. **MCP `annotations` 一律不参与判定**：按规范它是不可信提示（`readOnlyHint` 默认 false、`destructiveHint` 默认 true、`openWorldHint` 默认 true），外部工具自称只读不构成保证。
4. **黑名单优先（拒绝优先）**，白名单与黑名单同时命中时按黑名单。依据是联网核实的生态约定：`mcp-cli` 明确 `disabledTools → BLOCKED（absolute precedence）`（security-first precedence model），`mcp-compress-router` 为 `The denylist wins on conflict`，`synthorg` 更严格——两表重叠直接**在加载期报配置错误**。不采纳少数「白名单优先」实现的理由：白名单是粗粒度的日常收窄，黑名单通常是踩坑后的针对性封禁；同时命中几乎总是说明用户曾想封禁它。
5. **空集合 ≠ 白名单为空**：`enabledTools` 为空表示「未启用白名单」。故 `ServerPolicy.hasWhitelist` 用 `isNotEmpty()` 判定；「配了白名单但一个都不给」应通过 `disabledTools` 表达。
6. **`ServerPolicy.from` 必须映射全部集合字段**：`from()` 是手写字段映射，加字段时极易漏（`enabledTools` 就漏过一次，导致用户配的白名单静默失效）。`ServerPolicyTest` 用反射断言配置与策略的 `Set<String>` 字段集**一一对应**，防止再次漏映射。
7. **白名单模式下 UI 操作的是 `enabledTools`**：`McpEditorDialog` 的每工具开关在两种模式下操作不同集合，但对用户是同一个「可用吗」的真值。切换白名单模式时**必须清空另一模式的集合**——两套集合共存会因「拒绝优先」产生难以预料的结果。
8. **`enabledTools` 须落盘**：`McpConfigRepositoryImpl` 的序列化与解析都要处理，存量配置缺失该键即为空集（行为不变）。

#### 灰度开关必须有 UI 出口（强制约束，G1 批次）

`builtin_as_mcp_enabled` / `unified_capability_page_enabled` / `deferred_tool_loading_enabled`
三个开关曾只有仓储层 setter、**零调用点**——用户开不起来，于是批次 B / F1 / F2 / F4
的成果在默认状态下**一条都看不到**。功能做完但用户无法触及，等于没做。

1. **开关落地即须有 UI 出口**：新增灰度开关时，同批必须提供可见入口，且放在
   「设置 → 关于 → 开发者选项」的**调试Tab**（`DevOptionsScreen`）而非普通设置——
   它们都会改变对外行为（哪些工具进模型上下文、哪些能力对外可达、页面形态切换），
   属于需要用户明确知情的高影响开关。
2. **延迟加载是两级串联开关**：`isDeferredToolLoadingActive()` 的生效条件是
   `normFlowEnabled && deferredToolLoadingEnabled`。故 UI 在总开关关闭时必须显式提示
   「需同时开启规范流总开关」，否则就是一个骗人的开关。判定已抽为纯函数
   `deferredToolLoadingEffective(normFlowEnabled, deferredEnabled)` 供直接单测。
3. **另两个开关刻意不挂 normFlow 总闸**：`isBuiltinAsMcpActive` 与
   `isUnifiedCapabilityPageActive` 只读自己的键。内置 MCP 化是协议层能力、
   统一页是页面形态，都不是「规范流程」实验的一部分；挂上会造成语义混淆
   （关normFlow 就连带让内置分组无法枚举）。
4. **开关状态与消费侧必须同源**：UI 订阅 `NormFlowSettingsRepository` 的既有 Flow，
   不另开一路 KV 读取。否则会出现「界面显示已开、实际读到的还是旧值」。
5. **不要 mock 被测对象本身**：判定「开关是否生效」时不要 mock
   `NormFlowSettingsRepository`——它就是被测对象，桩掉之后断言恒真、测试形同虚设
   （本批已踩过一次：mock 后 4 例假失败，根因是把「被测方法」也桩掉了）。
   正确做法是把判定抽成纯函数直接测，或用真实对象 + 假 KV。

#### 技能归 MCP 能力规约（强制约束，F4 批次）

技能此前只有 App 内消费路径（`load_skill` 工具 / 技能中心 UI），在协议层完全不可见——外部 MCP 客户端无法发现「这台设备会写文档、会跑构建」。F4 把技能接入 MCP 的 `prompts` / `resources` 两个原生方法。

1. **按 type 分派，只有两条映射**：`PROMPT` → `prompts`、`SCRIPT` → `resources`。分派谓词 `Skill.asPromptOrNull` / `asResourceOrNull` 是**唯一实现**（在 `SkillMcpAdapter.kt`），服务端 list/get、UI 徽章计数全部复用同一对函数。禁止在别处重写 `type == PROMPT` 判断——两处口径一旦分叉，就会出现「UI 显示 3 个 prompt、协议只返回 2 条」这类最难排查的数字不一致。
2. **`SkillType.MCP` 刻意不暴露**：它是「调用某个已连 MCP 工具」的包装，属能力的**消费方**。同时暴露会在协议层形成「技能暴露技能」的环。它的别名关系在 UI 上以 `mcpTool` 徽章显式展示，让用户能对上号。
3. **`list` 与 `get` 必须同口径**：四个方法共用分派谓词，保证「list 里出现的 get 一定取得到」。否则会出现悬空条目——客户端遍历 list 后逐个 get 必然报错。
4. **`resources/read` 只读不执行**：`resources` 的语义是「只读内容」。读到的是**脚本文本**（技能定义 + 入口脚本名），真正执行仍走 `runSkillScript`（需 ZTH 审批）。若在 `read` 里执行，等于让外部客户端借 `resources/read` 绕过审批，属安全缺陷。
5. **URI 是不可信输入**：`minime://skill/{id}` 用自定义 scheme 而非 `file://`（后者会被客户端当真实路径去打开，而技能可能内置、可能来自状态覆盖，并非磁盘普通文件）。`SkillResourceUri.parseId` 对异 scheme、空 id、多层路径一律返回 `null`，由上层按「资源不存在」处理，**不得抛出**。
6. **capabilities 按实际条件声明**：`initialize` 仅在 `hasSkills()` 为真时声明 `prompts` / `resources`。`hasSkills()` 的语义是「**当前**有可暴露技能」而非「接了技能源」——全部技能都是 MCP 型或都被禁用时不得声明。声明了能力却恒返回空列表，等于对外承诺一个永远空实现的接口，客户端会反复重试。
7. **未知名称回 `-32602` 而非 `-32601`**：方法存在、参数值无效，是 `-32602`（Invalid params）；`-32601`（Method not found）只用于协议层根本没有的方法。
8. **内置三分组 server 刻意不接技能**：`BuiltinMcpServerFactory` 构造会话时用默认的 `EmptySkillMcpAdapter`。内置分组是**工具维度**，技能是**用户维度**；让进程内 server 也暴露 prompts 会污染批次 B 的工具可见面。只有对外的 HostServer 装配 `DefaultSkillMcpAdapter`。
9. **技能仓库异常必须降级而非上抛**：`DefaultSkillMcpAdapter` 读技能列表用 `runCatching` 回退空列表。它位于 MCP 请求处理路径上，目录权限异常不应让整个 server 变成 500——退化成「当前无技能暴露」才是正确降级。
10. **数据源用 `listSkillsSync` 而非 Flow**：MCP 请求是同步一问一答，取不到也等不来 Flow；技能列表规模有限（几十条），同步读盘可接受。

### MCP（Model Context Protocol）

- **客户端（已实施）**：应用实现了 MCP 客户端（`feature/agent/domain/mcp/`），可连接远程 HTTP / 本地 stdio 服务器并动态注册其提供的工具（`McpManager` / `McpClient` / `McpTool`）。
- **服务器（已实施）**：内置 MCP 服务器（`feature/agent/domain/mcp/server/`）使应用成为「客户端 + 服务器」双角色：`McpServerManager` 管理开关/端口/token/审批，`McpHttpServer` 用 Ktor CIO 起 Streamable HTTP 端点（`POST/GET/DELETE /mcp`，Bearer 鉴权 + SSE），`McpServerSession` 解析 JSON-RPC（initialize / tools/list / tools/call / ping），`AgentToolMcpAdapter` 把 `ToolRegistry` 中允许暴露的 `AgentTool` 映射为 MCP 工具并复用 `ToolPermissionManager` 审批，把 App 能力开放给外部 MCP 客户端（手机当开发后端）。
- **内置能力 MCP 化（F1，见上节规约）**：`BuiltinMcpServerFactory` 把 43 个内置工具经**真实 MCP 往返**暴露为三个 server（`MiniMe-Core` / `MiniMe-Chat` / `MiniMe-Data`），与远端 server 在 MCP Center 同表管理。开关关闭时零行为变更。
- **技能经 MCP 暴露（F4，见上节规约）**：`McpServerSession` 支持 `prompts/list` / `prompts/get` / `resources/list` / `resources/read`，数据经 `SkillMcpAdapter` 端口（实现 `DefaultSkillMcpAdapter`）从 `SkillStateRepository` 取；`PROMPT` 型映射 prompts、`SCRIPT` 型映射 `minime://skill/{id}` resources、`MCP` 型作为远端工具别名不暴露。

### 依赖注入

Hilt 被广泛使用。各 Feature 模块定义自己的 DI 模块（如 `AgentModule.kt`、`RepositoryModule.kt`、`BackupModule.kt`）向实现提供接口。

## 数据库与迁移

数据层为 **SQLDelight V2 六库拓扑**（`datalayer/`），旧 Room 数据层（世代0 巨型单库 `LegacyAgentDatabase`、世代1 按域拆 5 库、全部 DAO/`@Entity` 注解/迁移基建/Room gradle 依赖）已从程序**完全剔除**。

**V2 分层**：
- L0 引擎 `datalayer/engine`：ConnectionPool + `datalayer/encryption` 加密栈（`UnifiedKeyManager` → `EncryptedDriverFactory` → `EncryptedDatabaseManager`）+ `MigrationEngine`（schema 版本迁移 + 迁移前快照/回滚）。**6 库全部默认加密**，已移除旧的双轨明文/加密路由（`RoutingDriverFactory` / `DbEncryptionMigrationEngine` / `CrashRecovery` / 分散密钥管理）。
- L1 迁移 `datalayer/migration`：MigrationEngine / HeavyMigration / CodeMigration（必须保留）。
- L2 门面 `datalayer/repository/*`：`AgentRepository`（业务聚合）+ 泛型 KV / Document / Queue / Blob / TimeSeries store。
- DI：`datalayer/di/DataLayerModule.kt` 独立提供全部 V2 driver / 6 库 / 仓储（不依赖任何旧数据层）。
- SQL 定义：`app/src/main/sqldelight/<域>/`，查询类/数据类落在 `com.mini.me_core.datalayer.sqldelight.*`。

### 密钥体系四条不变量（不可漂移）

6 库全部 SQLCipher 加密，**以下四条是跨模块的数据契约**：任何一侧（driver / probe / 迁移器 / 备份）擅自改动其一，都会出现「driver 能开、probe 打不开」的**假损坏**——日志报 `SQLiteException: file is not a database (SQLITE_NOTADB)` 而数据其实完好，进而触发隔离重建、历史数据被清空。

| # | 不变量 | 值 / 单点 | 落地位置 |
|---|--------|-----------|----------|
| 1 | DEK purpose 命名 | `db_<库标识>`（如 `db_agent`、`db_infra`）；库标识**唯一真源 = `LibName.dbId`**，与 `DatabaseDefinition.id` 逐字相等，**禁止**用 `name.lowercase()` 推导（该 API locale-sensitive） | `CipherPassphrase.purpose()` |
| 2 | SQLCipher passphrase | `Base64(DEK)`（NO_WRAP）的 UTF-8 字节；**不得**再用原始 DEK 字节、hex、或二次派生 | `CipherPassphrase.encode()` |
| 3 | Keystore MasterKey alias | `minime_master_key`（AES-256-GCM，硬件 backing，密钥不出 Keystore） | `AndroidUnifiedKeyManager` |
| 4 | DEK 存储位置 | EncryptedSharedPreferences 文件 `minime_unified_keys`，键名 `dek_<purpose>` | `AndroidUnifiedKeyManager` |

强制纪律：

1. **purpose / passphrase 只允许经 `CipherPassphrase` 构造**，禁止在其它文件里手写 `"db_$id"` 或 `Base64.encodeToString(dek, ...)`。历史教训：`EncryptedDriverFactory` 与 `AndroidVersionProbe` 各自拼串发生过漂移。
2. 新增任何「打开数据库」的代码路径（driver、版本探测、健康扫描、备份导出、迁移器）都必须与 driver 用**同一套** purpose + passphrase，否则该文件在别人眼里就是损坏文件。
3. **改密钥 = 改 provider，不改迁移代码**：密钥形态演进（明文→加密、轮换 DEK、换 KDF）统一走 `KeyRotationMigrator`，入参替换为「旧口令 provider / 新口令 provider」即可，迁移主体（`sqlcipher_export` 9 步）不重写。
4. 诊断不可读库时，日志必须带 `size=` 与 `head=`（前 8 字节 hex）：`head` 非 SQLite 魔数 `53 51 4c 69` 即可判定为加密库或真损坏，是区分「密钥不匹配」与「文件损坏」的第一手证据。
5. **DEK「存在但解不开」≠「不存在」**：一律 fail-close 抛异常，**绝不**生成新 DEK 覆盖——覆盖后旧密文永久不可解（6 库报废）。见 `UnifiedKeyManager.DekRead`。
6. **禁止对 `db_*` 直接 `rotateDek`**：库文件是整体加密的，换 DEK 而不重加密文件 = 该库立即不可读；必须走 `KeyRotationMigrator`。`emergencyReset` 同理只清非 `db_*` 的 DEK。
7. **外键强制开启（`PRAGMA foreign_keys=ON`）下，删父行前子行必须已清空**：所有「删除会话/工作区」类操作走 `AgentTx.deleteSessionCascade` 一个事务内按依赖序级联（先子后父）；新增引用 `agent_session`/`agent_message` 的子表时，级联语句与回归测试同步补齐。另注意：被 `safeDaoSuspend` 之类吞异常的删除失败是**静默**数据不一致（覆盖恢复曾因此假成功）。

**数据访问**：业务读写全部经 `datalayer/repository` 门面；`data/local/entity/*.kt` 为纯 Kotlin data class（已剥离 Room 注解），仅当 DTO 被领域/UI/Firebase 层复用（如 `V2Xxx.toEntity()`、Backup 的 `toDto/toEntity`）。

**一次性移植（旧 Room → V2）**：`core/db/V1toV2FullMigrator`（纯 `SQLiteDatabase.openDatabase`+游标，不依赖 Room）启动时把旧 5 个 Room 域库的数据搬到 V2 库（幂等，只跑一次，失败下次启动重试）。旧库文件历史数据由 `MigrationEngine` 的既有 SQLDelight 迁移链承接。

**备份/恢复**：`core/data/DataRegistry`（经 `datalayer/backup/SqlDelightDataProvider` 连 V2 driver，DataRegistryModule 提供）+ `feature/backup/data/BackupManagerImpl` 全量导出/导入。

**迁移器改造易错点**：SQLite 游标取值列名必须与旧库实际建表列精确一致；`SQLiteDatabase.query(table)` 无单参重载，须用 `rawQuery("SELECT * FROM t", null)`；`data class` 只允许一个 `companion object`，否则常量全 unresolved；`port` 等跨层类型要显式 `toInt()/toLong()`（V2 用 `Long`、备份 DTO 用 `Int`）。

## 数据持久化编码规范（强制约束 · 防止设置丢失）

本项目历史上多次出现"设置项重启后丢失"类 bug，根因均为持久化读写不一致或状态未从磁盘恢复。以下规则为**强制约束**，新增/修改任何持久化代码时必须逐条遵守。

### 规则 1：KVStore 读写类型必须严格匹配

KVStore 按类型拆列存储（`stringVal` / `intVal` / `boolVal` / `jsonVal`），**写入方法与读取方法必须一一对应**：

| 写入方法 | 存储列 | 必须配对的读取方法 |
|---|---|---|
| `putString()` | `stringVal` | `getString()` / `observeString()` |
| `putInt()` | `intVal` | `getInt()` / `observeInt()` |
| `putBool()` | `boolVal` | `getBool()` / `observeBool()` |
| `putJson()` | `jsonVal` | `getJson()` / `observeJson()` |

**绝对禁止**：用 `putJson()` 写入后用 `getString()` 读取（会读到 null，导致设置丢失）。

### 规则 2：Singleton 的 StateFlow 初始值必须从磁盘恢复

任何 `@Singleton` 类中暴露给 UI 的 `MutableStateFlow`，其初始值**不得硬编码为默认值**，必须在构造函数或 `init` 块中从磁盘（KVStore / DB / 文件标记）读取真实状态后初始化。

**正确模式**：
```kotlin
@Singleton
class XxxManager @Inject constructor(private val kv: KVStore) {
    private val _state = MutableStateFlow(loadFromKv())  // 构造时同步读磁盘
    val state: StateFlow<XxxSettings> = _state.asStateFlow()

    private fun loadFromKv(): XxxSettings {
        val raw = kv.getJson(NS, KEY) ?: return DEFAULT
        return runCatching { Json.decodeFromString(raw) }.getOrDefault(DEFAULT)
    }
}
```

**错误模式**（会导致冷启动后 UI 显示默认值，用户以为设置丢失）：
```kotlin
private val _state = MutableStateFlow(DEFAULT)  // ❌ 硬编码，未读磁盘
```

### 规则 3：导出/备份配置时必须用与写入一致的类型读取

`exportConfig()` / `snapshot()` 等导出方法中，读取每个键时必须使用与写入时相同类型的读取方法。写入用 `putBool()` 的键，导出时必须用 `getBool()`，不能用 `getString()`。

### 规则 4：旧版数据迁移必须覆盖

当持久化键名、存储位置或数据结构发生变化时，必须在读取逻辑中添加旧版数据回退迁移：
1. 先尝试读新版键
2. 若不存在，回退读旧版键
3. 读到旧版数据后自动写入新版键（一次性迁移）
4. 删除旧版键（可选）

### 规则 5：文件标记状态必须在 init 时扫描

使用 `.installed` / `.done` / `.provisioned` 等文件标记记录状态的类，必须在 `init` 块中扫描磁盘标记来初始化内存状态，不能依赖"上次运行时设置过"。

### 静态检查

项目根目录 `scripts/check-persistence.py` 为数据持久化静态检查脚本，CI 中自动运行。本地修改持久化代码后建议手动跑一次：
```bash
python3 scripts/check-persistence.py
```

检查项：
- `putJson()` 调用处是否有对应的 `getJson()` 读取（而非 `getString()`）
- `@Singleton` 类的 `MutableStateFlow` 初始值是否硬编码默认值（需人工确认是否已从磁盘恢复）
- `exportConfig`/`snapshot` 方法中读取类型是否与写入一致

## 命名规范（强制约束 · 统一 MiniMecore 命名体系）

本项目已完全独立，**新增代码统一使用 MiniMecore 命名，不得使用与项目不相关的字段命名**。所有类名、函数名、常量名、字符串、注释、文档必须遵守：

### 正确命名体系

| 类别 | 命名 |
|---|---|
| Application 入口类 | `MiniMeCore`（已有，保持不变） |
| 主题 Composable | `MiniMeTheme`（已有，保持不变） |
| GitHub 仓库 | `Lisir2002/MiniMe-core` |
| 包名（applicationId） | `com.mini.me_core` |
| 应用显示名 | `MiniMe-core` |
| 新增代码通用前缀 | `MiniMecore` / `minimecore` / `mini_me_core` |

### 约束规则

1. **新增代码**：类名、函数名、常量名、变量名统一使用 MiniMecore 命名体系，不得使用与项目无关的名称（如其他品牌名、缩写、临时命名）。
2. **修改代码**：触及含非 MiniMecore 命名的文件时，必须同步清理该文件内所有不规范命名，不得只改局部。
3. **文档/注释**：KDoc、行注释、README、docs/ 中统一使用 MiniMecore 命名，历史背景说明改用中性描述（如"历史版本"而非具体旧名称）。
4. **字符串/资源**：用户可见的字符串、资源文件名、资源 ID 统一使用 MiniMecore 相关命名，不得出现无关品牌名。
5. **包名稳定性**：`com.mini.me_core` 为唯一合法包名，CI 门禁和单元测试锁定此值，禁止任何形式的包名变更。
6. **审计要求**：每次发版前必须执行全项目命名规范扫描，确认无非 MiniMecore 命名残留后方可打 tag。

## 常见坑

| 症状 | 原因 | 处理 |
|---|---|---|
| 数据库迁移启动即失败 | 迁移 SQL 字面量含 `;` 被切分器误切 | 用 `char(59)` 代替字面量分号 |
| 构建命令报错/找不到任务 | 误用旧 flavor 命令 | 只用 `assembleDebug/assembleRelease/bundleRelease`（项目无 flavor） |
| PRoot 容器无法执行 | `targetSdk` 被改高破坏 W^X 绕过 | 保持 `targetSdk = 28`，勿"顺手修复" |
| APK 装不上/装后崩溃 | ABI 不符 | release 仅 arm64-v8a（debug 含 x86_64 供模拟器）；若宿主为其它 ABI，走无容器降级（AI 核心仍可用） |
| 版本号对不上 | 手改 `versionName` | 靠 Git Tag 动态推导，代码中勿手写版本号 |
| 提交被 commit-msg 阻断 | 提交信息不合 Conventional Commits | 按 `type(scope): subject` 重写提交信息 |
| **设置项重启后丢失** | **KVStore putJson 写 jsonVal 列，但 getString 读 stringVal 列，读写列不匹配** | **putJson 必须配 getJson；putBool 配 getBool；putInt 配 getInt；putString 配 getString** |
| **容器初始化状态重启后丢失** | **Singleton 的 MutableStateFlow 初始值硬编码（如 Idle），未从磁盘标记恢复** | **init 块中必须根据磁盘状态（文件标记/DB/KV）初始化 StateFlow，不能硬编码默认值** |
| **配置导出内容为空** | **exportConfig 中用 getString 读取实际用 putBool/putInt 写入的键** | **导出时必须用与写入时一致的类型读取方法** |
| **删除会话/工作区即崩溃** | `EncryptedDriverFactory` 强制 `PRAGMA foreign_keys=ON`（SQLite 默认 OFF），单表删 `agent_session` 父行时子表（agent_message 等）仍引用该行，FK 约束抛异常且 UI 未捕获 | 删除一律走 `AgentTx.deleteSessionCascade` 单事务级联（先子后父）；新增子表必须同步补级联语句，补回归测试 |
| **日志查看器看不见最新日志** | FileObserver(CLOSE_WRITE) 对常驻 BufferedWriter 永不触发——正常写日志期间文件从不 close，事件源实际是死的 | 查看器在前台用 `repeatOnLifecycle(RESUMED)` 轮询 + 读前 flush；凡「事件驱动的新鲜度承诺」，先确认该事件在正常运行时真的会发生 |
| **压缩过的会话每轮提问都「网络不稳定」** | **压缩死锁**：①token 估算按 chars/4，中文低估约 4 倍，真实载荷已超窗口数倍还以为没到阈值；②压缩请求把整个 head 无预算拼进一条消息——会话多大压缩请求就多大，超限会话的压缩请求必然同样超限被拒（实测 622k tokens vs 128k 窗口）；③失败被 catch 后原样返回却照样 emit 压缩完成事件，UI 假报成功、DB 分毫未动；④超限载荷在网关侧表现为超时/重置/5xx，落入可重试类别 → 重试风暴 | 估算改 CJK-aware（`ModelContextPolicy.estimateTokens(text)`，中文每字≈1 token）；压缩请求自带输入预算（上限×0.5），超预算走滚动窗口逐段摘要（`ContextCompactor.summarizeHead`）；失败 emit `CompactionFailed` 而非冒充完成，并记失败冷却 |
| **网络很好仍显示「网络波动，正在重试」** | **六环叠加**：①OkHttp 未配 pingInterval——HTTP/2 长连接在 WiFi↔4G 切换/NAT 超时后半死但双方不知情，请求打到死连接上等超时才暴露（主因）；②首字节 watchdog 固定 60s 把「服务端排队/慢思考」误杀成连接中断（底层 `Software caused connection abort`），重试仍 60s，于是一次次被砍成重试风暴；③退避无 jitter，多会话同步重试惊群；④无重试预算，一次故障被放大成分钟级卡顿；⑤`streamWithStaircaseRetry` 的 `attemptOnce` 从不消费 onProduced 标记——注释声称「吐字后不重试」实现却整体重来，用户看到内容重复输出、token 双倍计费（三适配器全中）；⑥UI 一律报「网络波动」，把限速/服务端慢也误导成网络问题 | OkHttp 双客户端 `.pingInterval(30, SECONDS)` 及时探出摘除死连接；重试改 full jitter 指数退避 + `RetryBudget` 三道闸（次数/单次 10s/累计 15s，超限上抛交上层故障转移）；渐进式首字节耐心 60s→120s→180s 放过慢但活着的服务端；吐字后失败直接上抛不重试；`RetryReason` 六分类 + UI 分档文案；内置多线路 `EndpointHealthRegistry`（3 次连续失败熔断 60s + EWMA 延迟排序）与 `ModelEndpointRouter`（用户配置 URL 永远首选、同类型官方 URL 次选、useFullUrl 直通不换），首字节前 CONNECTION 类失败端点级换线 ≤2 次 |

## app-template 模块开发约束

app-template 是「前端应用转安卓应用」的模版壳模块，内置默认欢迎页面用于功能演示和测试。

### 默认欢迎页面同步约束（强制）

1. **功能展示必须与实现状态一致**：`app-template/src/main/assets/www/index.html` 中展示的所有 Bridge 功能必须是已实现的方法，禁止展示标记为 `NOT_IMPLEMENTED` 的预留功能（Media/Location/Sensor 等模块的预留方法不得出现在测试按钮中）。
2. **随功能变更同步更新**：每次新增、修改或删除 Bridge 模块方法时，必须同步更新欢迎页面的测试按钮和功能展示，确保页面不落伍、不超前。
3. **版本号动态获取**：欢迎页面底部的版本号必须通过 `BuildConfig.VERSION_NAME` 动态注入（由 MainActivity 在 `onPageFinished` 中更新 `#app-version` 元素），禁止硬编码版本号。
4. **日志可复制**：测试结果输出框必须支持文本长按选择，并提供「复制日志」按钮。
5. **审计清单**：发版前必须审计欢迎页面，确认所有展示的功能在对应 Bridge 模块的 `getMethods()` 中存在且非 `NOT_IMPLEMENTED`。

### Bridge 开发约束

1. **@JavascriptInterface 线程安全**：`@JavascriptInterface` 注解的方法在后台线程执行，禁止直接调用 `webView.url`、`webView.evaluateJavascript` 等必须在主线程调用的方法。URL 校验使用 `AppBridge.updateUrl()` 缓存的 `currentUrl`。
2. **异常必须捕获**：Bridge 方法执行必须包裹 try-catch，通过 `BridgeResult.failure()` 返回错误信息，禁止抛出未捕获异常导致 WebView 返回 "Java exception was raised during method invocation"。
3. **来源校验**：所有 Bridge 调用必须经过 `isSourceAllowed()` 校验，仅允许本地虚拟域名 `https://appassets.androidplatform.net/` 的顶层页面调用。

## 关键文件

| 路径 | 作用 |
|---|---|
| `AGENTS.md` | 本规范（AI 纪律源，运行时被加载） |
| `docs/ci-release.md` | 云端构建发版运维手册 |
| `app/build.gradle.kts` | 构建配置 + 版本号动态推导（勿手写 versionName） |
| `app/src/main/java/com/mini/me_core/datalayer/repository/AgentRepository.kt` | V2 数据门面（`AgentRepository` 业务聚合 + `data/local/entity/*.kt` 纯 DTO，DOMAIN/UI/Firebase 复用） |
| `app/src/main/java/com/mini/me_core/core/db/V1toV2FullMigrator.kt` | 旧 Room 域库 → V2 一次性移植器（纯 SQLite，幂等，只跑一次） |
| `app/src/main/java/com/mini/me_core/core/data/DataRegistry.kt` | 数据注册表（备份/恢复单一事实源，经 `datalayer/backup/SqlDelightDataProvider` 连 V2） |
| `app/src/main/assets/prompts/` | 系统提示词资产（AI 行为来源，随工作流同步） |
| `app/src/main/assets/docs/` | 用户使用文档资产（运行时「设置 → 帮助」） |
| `app/src/main/java/com/mini/me_core/MiniMeCore.kt` | Application 入口（核心服务初始化） |
| `app/src/main/java/com/mini/me_core/MainActivity.kt` | 主 Activity（导航 + 全局凭据弹窗） |

## 维护本文件

- 本文件是**活文档**：当 AI 发现规则与实际做法不一致（如命令、路径、版本、目录结构变化）时，应主动提示维护者更新，不要默默沿用失效规则。
- **渐进披露**：本文件只放"每次会话都需要的纪律与命令"；深度操作手册（如云端构建细节）放到 `docs/ci-release.md` 等独立文档并链接，避免撑爆每次会话的上下文。
- **用户指定内容**：本文件中的纪律条目由用户/维护者设定，AI **不得擅自修改或删除**；确需修订时说明原因，保留原意，最小改动。
