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
| 数据库 | SQLDelight 2.2.1（6 库拓扑，`datalayer/`；支持可选 SQLCipher AES-256 加密，默认明文，设置页可开启）；旧 Room 数据层已完全移除 |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson |
| 终端 | Termux terminal-emulator + terminal-view（JNI libtermux.so） |
| 容器 | PRoot + Alpine Linux 3.21 rootfs（arm64-v8a / x86_64 双架构，运行时按宿主选择） |
| 远程 SSH | SSHJ 0.38.0（exec channel + shell channel） |

## 关键命令

```bash
# 日常开发冒烟（AI 改完编译型代码默认跑这个；debug buildType 快，不跑 R8）
./gradlew :app:assembleDebug
# Release 链路验证 / 发布包（双 ABI 通用包：arm64-v8a + x86_64，真机与模拟器通用）
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
- **禁止随意修改本 AGENTS.md**（用户指定的纪律内容；如需修订先说明原因并保留原意）。

## 资产同步纪律

项目中的 `app/src/main/assets/prompts/`、`app/src/main/assets/docs/` 是 AI Agent 的核心知识来源，必须与代码保持同步：

- **AI 工作流相关改动 → 检查 prompts**：任何与 AI 工作流相关的改动（工具新增/删除/重命名/参数签名变化、agent 行为变化、提示词逻辑调整等），都必须检查 `app/src/main/assets/prompts/` 下的提示词是否需要同步更新，确保模型看到的工具定义与行为说明与实际一致。AI 应自行在 `prompts/` 目录中查找对应的提示词文件；若不存在则新建。
- **功能、工具变化 → 检查 docs**：任何功能新增/删除/行为变化或工具变更，还要检查 `app/src/main/assets/docs/` 下是否有对应使用文档需要更新（如新功能的使用说明、工具行为变化的提示）。
- **UI 变化 → 必须更新对应使用文档**：任何 UI 变化（新增页面、改交互、调布局、改文案）**必须**同步更新 `app/src/main/assets/docs/` 下对应的使用文档，确保用户可见的说明与实际界面一致。AI 应自行在 `docs/` 目录中查找对应的文档；若不存在则新建。
- **UI 文案 → 必须同步 strings.xml**：任何新增或修改用户可见的中文文案（按钮、标题、提示、Toast 等），**必须**将其提取为 string resource 写入 `app/src/main/res/values/strings.xml`（中文）和 `app/src/main/res/values-en/strings.xml`（英文翻译），并在 `.kt` 代码中用 `stringResource(R.string.xxx)` 或 `context.getString(R.string.xxx)` 引用。**禁止在 .kt 文件中硬编码中文 UI 文案。** 命名规范：语义化英文全小写下划线分隔，通用文案用 `common_` 前缀跨页面复用。
- **模块文档 / 设计文档（已停用）**：曾经的 `docs/modules/`（模块开发文档）与 `docs/plan-docs/`（设计文档）目录已按维护者决定**整体删除**，对应纪律（模块文档同步、设计文档前置）随之停用，`.githooks/pre-commit` 校验已停用（`spec-check.sh` 已删除）。新增/删除 feature 模块不再要求配套模块文档。

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
  - **版本规则为四段式 `x.x.x.x(-rcN)`**（从 `0.0.0.1` 重启迭代：A 段=颠覆性/预留、B 段=框架级结构性/预留、C 段=框架级重构、D 段=默认发版递增）。**默认（维护者无特别通知时）每次发版版本按 `0.0.0.1` 的 D 段 +1 单调递增**（`0.0.0.1 → 0.0.0.2 → 0.0.0.3 …`）；框架重构正式版 C 段 +1 且 D 段归零，仅在维护者明确通知后执行。
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
5. **Release 正文人工更新（强制）**：CI 自动生成的 Release 正文是带占位符的草稿（`_（请补充...）_`），**必须在发版后手动更新为完整格式**。正文内容必须与 `docs/Version Log/MiniMe-core v-Logs/` 下对应版本文档完全一致，包含完整简介和所有分类条目。未更新正文的 Release 视为发版未完成。
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

#### 2. 发版页面标题格式

`{软件名} {版本号} — {更新内容概括}`

- 更新内容概括：≤20字，用**用户语言**描述本次最核心的1～2个变化，禁止内部术语。
- 示例：`MiniMe v0.0.0.15 — 模型级采样参数独立调节 & 默认模型选择体验升级`
- 反例：`v0.0.0.15`（无概括）、`Release v0.0.0.15`（无意义前缀）、`feat: sampling params override`（开发语言）。

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

> **云端构建的完整运维手册**（CI 全流程 6 阶段 / 实时监控 GitHub API 命令 / 产物校验清单 / 签名 secrets 配置与回退说明）：见 **[docs/ci-release.md](./docs/ci-release.md)**。AI 或维护者推 Tag 发版后，必须按该手册实时监控并校验产物。

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

### MCP（Model Context Protocol）

- **客户端（已实施）**：应用实现了 MCP 客户端（`feature/agent/domain/mcp/`），可连接远程 HTTP / 本地 stdio 服务器并动态注册其提供的工具（`McpManager` / `McpClient` / `McpTool`）。
- **服务器（已实施）**：内置 MCP 服务器（`feature/agent/domain/mcp/server/`）使应用成为「客户端 + 服务器」双角色：`McpServerManager` 管理开关/端口/token/审批，`McpHttpServer` 用 Ktor CIO 起 Streamable HTTP 端点（`POST/GET/DELETE /mcp`，Bearer 鉴权 + SSE），`McpServerSession` 解析 JSON-RPC（initialize / tools/list / tools/call / ping），`AgentToolMcpAdapter` 把 `ToolRegistry` 中允许暴露的 `AgentTool` 映射为 MCP 工具并复用 `ToolPermissionManager` 审批，把 App 能力开放给外部 MCP 客户端（手机当开发后端）。

### 依赖注入

Hilt 被广泛使用。各 Feature 模块定义自己的 DI 模块（如 `AgentModule.kt`、`RepositoryModule.kt`、`BackupModule.kt`）向实现提供接口。

## 数据库与迁移

数据层为 **SQLDelight V2 六库拓扑**（`datalayer/`），旧 Room 数据层（世代0 巨型单库 `LegacyAgentDatabase`、世代1 按域拆 5 库、全部 DAO/`@Entity` 注解/迁移基建/Room gradle 依赖）已从程序**完全剔除**。

**V2 分层**：
- L0 引擎 `datalayer/engine`：ConnectionPool + RoutingDriverFactory（根据每库加密状态动态选择 Plain/CipherDriverFactory）+ DbEncryptionMigrationEngine（7 步明文↔加密迁移）+ CrashRecovery（启动时崩溃回滚）+ DatabaseKeyProvider（Android Keystore MasterKey → per-DB DEK）。Cipher 即 SQLCipher AES-256 加密，默认明文，可在设置页开启。
- L1 迁移 `datalayer/migration`：MigrationEngine / HeavyMigration / CodeMigration（必须保留）。
- L2 门面 `datalayer/repository/*`：`AgentRepository`（业务聚合）+ 泛型 KV / Document / Queue / Blob / TimeSeries store。
- DI：`datalayer/di/DataLayerModule.kt` 独立提供全部 V2 driver / 6 库 / 仓储（不依赖任何旧数据层）。
- SQL 定义：`app/src/main/sqldelight/<域>/`，查询类/数据类落在 `com.mini.me_core.datalayer.sqldelight.*`。

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
| APK 装不上/装后崩溃 | ABI 不符 | 通用包含 arm64-v8a + x86_64；若宿主为其它 ABI（少见），走无容器降级（AI 核心仍可用） |
| 版本号对不上 | 手改 `versionName` | 靠 Git Tag 动态推导，代码中勿手写版本号 |
| 提交被 commit-msg 阻断 | 提交信息不合 Conventional Commits | 按 `type(scope): subject` 重写提交信息 |
| **设置项重启后丢失** | **KVStore putJson 写 jsonVal 列，但 getString 读 stringVal 列，读写列不匹配** | **putJson 必须配 getJson；putBool 配 getBool；putInt 配 getInt；putString 配 getString** |
| **容器初始化状态重启后丢失** | **Singleton 的 MutableStateFlow 初始值硬编码（如 Idle），未从磁盘标记恢复** | **init 块中必须根据磁盘状态（文件标记/DB/KV）初始化 StateFlow，不能硬编码默认值** |
| **配置导出内容为空** | **exportConfig 中用 getString 读取实际用 putBool/putInt 写入的键** | **导出时必须用与写入时一致的类型读取方法** |

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
