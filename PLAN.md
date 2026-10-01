# MiniMe-core 设计文档总览（PLAN）

> 本文档是项目所有设计文档的引导目录，用于 AI 协同开发时快速定位设计依据。
> 所有功能变更前应先查阅对应设计文档，无设计文档的重大变更应先补设计再实施。
>
> 版本：v1.1 | 最后更新：2026-09-27

---

## 一、项目概述

| 项目 | 内容 |
|------|------|
| 项目名 | MiniMe-core |
| 平台 | Android（Kotlin + Jetpack Compose） |
| applicationId | com.mini.me_core |
| 定位 | AI 编程工具，内置 Linux 终端、AI Agent、MCP 协议、Git 集成、内置浏览器 |
| 数据库 | SQLCipher 全库加密 |
| 开源协议 | GPL-3.0 |
| 构建 | GitHub Actions 云端 CI，禁止本地 release build |

### 技术栈

- UI：Jetpack Compose + Material 3（自定义 colorScheme）
- 架构：MVVM + Repository + Room
- 异步：Kotlin Coroutines + Flow
- 依赖注入：Hilt / Dagger
- 原生层：NDK + CMake + tree-sitter（Native 查看器）
- 终端：Proot Linux 容器
- CI：GitHub Actions（android-release.yml）

---

## 二、设计文档索引

### 2.1 核心架构设计

| 文档 | 路径 | 状态 | 说明 |
|------|------|------|------|
| 架构文档 | `docs/architecture.md` | 已落地 | 模块依赖图与端到端数据流 |
| Native 查看器/编辑器设计 | `docs/native-viewer-design.md` | 已落地（第一阶段） | C++ so 自研查看器核心，tree-sitter 语法高亮，PDFium 内置，代码查看器/编辑器统一设计 |
| 插件体系设计 | `docs/plugin-system-design.md` | 设计阶段（未落地） | 四类插件分类（文件查看器/主题字体/MCP工具/环境容器），通用框架架构，按需下载瘦身 |

### 2.2 UI 与交互规范

| 文档 | 路径 | 状态 | 说明 |
|------|------|------|------|
| UI 设计规范 | `docs/ui-standards.md` | 已落地 | 页面槽位设计（顶栏唯一）、统一组件（AppCard/AppButton/AppChip/AppSegmentedControl）、颜色/间距/字体规范、禁止事项 |

### 2.3 工程与发版规范

| 文档 | 路径 | 状态 | 说明 |
|------|------|------|------|
| CI/发版规范 | `docs/ci-release.md` | 已落地 | 发版构建硬性规则、tag 命名、CHANGELOG 约束、APK 校验、CI 失败处理、密钥安全、签名策略（release 仅 arm64-v8a） |
| 发版说明格式约束 | 见 `docs/ci-release.md` + `scripts/gitops/check-release-format.py` | 已落地 | 4-9字小标题 + 20-40字说明，分档（新功能/修复/改进/移除），无 emoji，只写用户可感知变更 |

### 2.4 协同开发与安全规范

| 文档 | 路径 | 状态 | 说明 |
|------|------|------|------|
| AI 协同开发规范 | `AGENTS.md` | 已落地 | AI 开发行为准则、项目架构速查、关键命令、常见问题排查、发版规范最高优先级约束 |
| 安全策略 | `SECURITY.md` | 已落地 | 安全披露政策、密钥管理、签名策略、依赖安全 |
| 项目说明（中文） | `README.md` | 已落地 | 项目介绍、功能列表、构建指南 |
| 项目说明（英文） | `README.en.md` | 已落地 | 英文版项目说明 |

### 2.5 应用内 SOP 与说明文档

| 目录 | 路径 | 说明 |
|------|------|------|
| 标准操作流程 | `app/src/main/assets/sop/` | 发版/迁移/资产同步/git提交/故障排查/AI行为 6类 SOP |
| 应用内说明文档 | `app/src/main/assets/docs/` | 用户协议、隐私政策、GPL-3.0、各功能使用指南（中英文 readme） |
| 提示词体系 | `app/src/main/assets/prompts/` | AI Agent 身份/通信/项目规则/编码规范/安全等提示词 |
| Playbook | `app/src/main/assets/playbooks/` | bug修复/代码审查/功能开发 标准流程 |

### 2.6 版本日志

| 目录 | 路径 | 说明 |
|------|------|------|
| 版本日志总目录 | `docs/Version Log/` | 包含 CHANGELOG.md 和按软件分的版本文档 |
| MiniMe-core 版本日志 | `docs/Version Log/MiniMe-core v-Logs/` | 主应用版本日志，每个版本一个 md，命名 `MiniMe-core 0.0.0.X Version Log.md` |
| MiniMe-Logs 版本日志 | `docs/Version Log/MiniMe-Logs v-Logs/` | 附属应用（日志查看器）版本日志 |

> **设计文档清理规则**：一次性设计文档（`*-design.md`）在全部落地后必须删除；部分落地的保留并标注进度。规范文档和版本日志永久保留。

---

## 三、关键设计决策速查

### 3.1 命名规范

- 新增代码统一使用 `MiniMe` / `MiniMeCore` 命名，禁止使用旧品牌名（AIEditor 等）
- 字符串走 `strings.xml` 语义化命名
- 颜色走 `colorScheme`，禁止硬编码色值
- 无 emoji（代码、文档、UI 文案均禁止）

### 3.2 构建与发版

- Release 构建必须使用 GitHub Actions 云端 runner，禁止本地 `flutter build` / `gradlew assembleRelease`
- 本地只做 `analyze` / `test` / `compileDebugKotlin` 和 git 操作
- Tag 命名：`v{version}`（如 `v0.0.0.24`）
- 发版前必须通过：analyze 0 error、custom_lint 0 issue、test 全通过、CHANGELOG 已更新
- 发版说明格式：4-9字加粗小标题 + 冒号 + 20-40字说明句，分档排列
- 发版说明只包含用户可感知的应用程序变更，禁止包含项目文档/提交/CI 等非程序变更

### 3.3 UI 规范要点

- **顶栏唯一**：每个页面只有一个顶栏（AppTopAppBar），禁止双顶栏
- 搜索内联：搜索功能集成在顶栏或页面内，不单独开页面
- Tab 固定：顶部 Tab 用于分组，不用于页面导航
- 统一组件：AppCard / AppButton / AppChip / AppSegmentedControl / AppDialog / AppBottomSheet
- 弹窗统一：AppConfirmDialog / AppInputDialog / AppLoadingDialog / AppResultDialog
- 底部抽屉统一：AppSheetHeader / AppSheetActionItem / AppActionSheet

### 3.4 数据持久化

- 所有用户设置必须持久化（DataStore / Room），禁止仅内存存储导致重启丢失
- 外观设置、终端配置、容器状态等均需持久化
- 远程 SFTP 挂载 autoConnect 在冷启动时自动重连

### 3.5 数据库

- SQLCipher 全库加密
- 迁移必须使用 `rawQuery` + 完全消费结果集，禁止 `rawExecSQL`（会导致 error 100 another row available）
- ATTACH/DETACH 同样使用 rawQuery
- MIGRATION_LOGIC_VERSION 跟踪迁移逻辑版本

### 3.6 Native 层

- JNI 函数名：包名 `com.mini.me_core` 中下划线需转义为 `_1`，即 `Java_com_mini_me_1core_...`
- release 仅打 arm64-v8a，debug 包含 arm64-v8a + x86_64
- PDFium 直接内置，不做按需下载

---

## 四、AI 协同开发指引

### 4.1 变更前检查清单

1. **是否有对应设计文档？** 有 → 严格按设计文档实施；无 → 先讨论设计，重大变更补设计文档
2. **是否涉及 UI？** → 查阅 `docs/ui-standards.md`，使用统一组件，遵守顶栏唯一原则
3. **是否涉及发版？** → 查阅 `docs/ci-release.md`，遵守发版说明格式
4. **是否涉及命名？** → 使用 MiniMe 命名，禁止旧品牌名
5. **是否涉及数据持久化？** → 确保设置持久化，禁止仅内存

### 4.2 编译验证

- 编译命令：`JAVA_HOME=/home/user/jdk/jdk-17.0.12+7 ./gradlew :app:compileDebugKotlin`
- 子 agent 串行编译，同一时间只允许一个 Gradle 进程
- 使用 `--no-daemon` 避免 daemon 锁冲突
- 并发重命令（analyze/test/build）≤2 并错峰，失败递增退避

### 4.3 提交规范

- commit message 格式：`type(scope): 描述`
- type：feat / fix / refactor / perf / docs / chore / release
- 发版 commit：`release: v{version} — {概括}`
- 设计文档 commit：`docs: {描述}`

### 4.4 已知死路（禁止重试）

| 死路 | 原因 | 正确做法 |
|------|------|---------|
| sqlcipher_export 用 rawExecSQL | error 100 another row available | 用 rawQuery + 完全消费 |
| PRAGMA schema.user_version 用 rawExecSQL | 同样 error 100 | 用 rawQuery |
| ATTACH/DETACH 用 rawExecSQL | 可能同样 error 100 | 用 rawQuery |
| Gradle 并发编译 | daemon 锁冲突 + OOM | 串行编译，--no-daemon |
| 本地 release build | 违反发版硬性规则 | 推送 tag → 云端 CI 构建 |
| 在 Android 上运行有头桌面 QQ | 容器内无法运行桌面 QQ 客户端 | 用 NapCat 无头协议端（OneBot 11） |

---

## 五、文档维护规则

1. **实时更新**：设计文档随项目优化同步更新，禁止文档与代码脱节
2. **新增设计文档**：重大功能/架构变更必须先写设计文档，路径 `docs/{name}-design.md`
3. **更新本文档**：新增设计文档后必须更新本 PLAN.md 的索引
4. **版本日志**：每个发版版本在 `docs/Version Log/` 下创建对应版本文档
5. **约束优先级**：本文档列出的规范约束优先级高于一般实现习惯，AI 审计项目时不得遗漏

---

## 六、相关资源

| 资源 | 链接 |
|------|------|
| GitHub 仓库 | https://github.com/Lisir2002/MiniMe-core |
| 附属应用 | MiniMe-Logs（日志查看器） |
| UI 组件库 | 项目内 `core/theme/components/` |
