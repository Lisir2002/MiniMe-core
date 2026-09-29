# MiniMe-QBot 附属应用设计文档（第一阶段：登录）

> 文档版本：v1.5
> 状态：**部分落地**（第一阶段登录链路代码已落地，真机 V1/V2/V3 待实测）
> 最后更新：2026-09-29
> 替代关系：本文档**全面替代**已废弃的 `qq-bot-integration-design.md`（旧方案为 LLBot + OneBot 11 反向 WS 嵌入主应用容器，整体舍弃）

**落地进度**

| 阶段 | 状态 | 说明 |
|------|------|------|
| 第一阶段：登录 | **代码已落地** | 运行环境安装、NapCat 供给与进程管理、OneBot 11 客户端、二维码获取、登录状态机、登录态加密持久化、前台服务保活、单页登录屏 UI、首次启动实时日志与阶段进度均已完成；`./gradlew :qbot-app:assembleDebug` 通过 |
| 第一阶段待闭环 | 待实测 | §十一 V1（PRoot 内 NapCat + 官方 QQ 客户端可登录）、V2（国产 ROM 后台存活率）、V3（首次初始化耗时/体积）只能由真机验证关闭 |
| 第二～五阶段 | 未开始 | 见 §九 |

**应用标识**

| 项 | 值 |
|----|----|
| 应用名称 | `MiniMe-QBot` |
| 包名 / namespace / applicationId | `com.mini.qbot` |
| Gradle 模块 | `:qbot-app` |
| 版本 | 从 `0.0.1` 起独立递增（不共享主应用版本号段） |
| targetSdk | `28`（运行 Linux 二进制约束） |

## 一、背景与目标

### 1.1 目标

为 MiniMe 生态提供独立附属应用 **MiniMe-QBot**（与 `MiniMe Logs` 同级，包名 `com.mini.qbot`），使其能够以**普通个人 QQ 号**登录（非官方机器人账号），并以标准协议对外提供能力。

第一阶段只解决一件事：

> **让个人 QQ 号成功登录，并让登录状态可控、可观测、可持久、可自动恢复。**

### 1.2 为什么第一阶段只做登录

- 登录是后续一切能力（消息收发、AI 回复、群管理、Agent 集成）的**唯一前置**，是整条链路的"根"。
- 登录依赖大量**不稳定外部条件**（第三方协议端、Linux 运行时、Android 保活、平台风控），必须先把它做成扎实的基础设施，否则上层任何功能都建在流沙上。
- 登录层同时也是**协议端可插拔边界**的定型位置：登录一旦稳定，上层只需消费 OneBot 11 契约，不必关心底层协议端是谁。

### 1.3 第一阶段非目标（明确不做）

| 不做项 | 归属阶段 |
|--------|----------|
| 消息收发（私聊 / 群聊 / @） | 第二阶段 |
| Agent AI 回复、会话上下文 | 第三阶段 |
| 群管理（禁言/踢人/审批/欢迎/违禁词） | 第四阶段 |
| 聊天记录页面与独立数据模型 | 第四阶段 |
| 与 MiniMe-core Agent 的集成 | 第三阶段 |
| 密码登录、多账号 | 后续阶段（按需） |
| **风控策略层（限速 / 熔断 / 内容审核）** | **后续专题**（见 §九） |

### 1.4 形态决策：附属应用，而非并入主应用

| 维度 | 并入 MiniMe-core | 独立附属应用（选定） |
|------|------------------|----------------------|
| 风险隔离 | 协议端崩溃/被平台打击会波及主应用 | 完全隔离，主应用零风险 |
| 体积与冷启动 | 直接加重主应用 APK 与启动耗时 | 主应用不受影响 |
| 生命周期 | 与主应用强绑，难独立迭代 | 独立发版、独立迭代 |
| 权限与合规 | 主应用被迫申请额外权限 | 权限收敛在附属应用内 |
| 分发 | 随主应用强制捆绑 | 用户按需安装 |

代价：附属应用**无法复用**主应用进程内的单例与 Keystore 密钥（Android 应用沙箱隔离），需自建运行时与密钥体系。这是接受的设计成本。

---

## 二、关键决策

### 2.1 协议选型：OneBot 11（唯一契约层）

**决策：应用与协议端之间只以 OneBot 11 为契约，不直接耦合任何协议端内部实现。**

理由：

1. **生态最成熟**：OneBot 11 是事实标准，协议端实现最多、文档最全、可替换性最强。
2. **抗生态波动**：QQ 协议端生态极其动荡（go-cqhttp 已停、OpenShamrock 原仓库 2024-08 归档、各家协议端轮番停更）。把契约固定在 OneBot 11，协议端死了换一个即可，上层代码零改动。
3. **与后续阶段解耦**：第二阶段做消息、第三阶段接 Agent，都只消费 OneBot 11 事件与 API。

使用的 OneBot 11 能力（第一阶段仅需极小子集）：

| 用途 | 接口 | 说明 |
|------|------|------|
| 登录信息校验 | `get_login_info` | 登录成功后校验 QQ 号与昵称 |
| 运行状态 | `get_status` | 判定协议端是否就绪 |
| 登录态 | `get_login_state` / 协议端原生登录接口 | 获取/驱动登录流程 |

> 注：OneBot 11 标准未定义"扫码登录"接口，扫码能力属**协议端私有扩展**（见 §4.3）。因此登录能力需通过 `ProtocolEndpoint` 抽象层隔离，标准接口走 OneBot 11，私有接口按协议端适配。

### 2.2 协议端选型：可插拔，默认 **NapCat**（Linux arm64）

> **本节已按调研证据修订**（v1.2）：原默认选型 LLBot 在 arm64 上无法证实，改为 NapCat。

| 协议端 | 实现语言 | 需 QQ 客户端 | **arm64 Linux** | 状态 | 结论 |
|--------|---------|-------------|-----------------|------|------|
| **NapCat** | Node/TS | 需官方 QQ Linux 客户端 | ✅ **明确支持**（官方支持矩阵含 Linux Arm64；官方 QQ 提供 arm64 Linux 安装包；已有 aarch64 Debian 设备实跑并扫码登录的案例） | 维护中 | **默认选型** |
| LLBot（LuckyLilliaBot） | Rust / Node | 否（纯协议） | ⚠️ **未证实**（官方定位为 Windows / Docker，Docker 镜像为 `linux/amd64`） | 维护中 | 备选（Windows/Docker 场景） |
| Lagrange.Core | C# (.NET) | 否（NTQQ 协议） | ⚠️ 未证实（需自验证 .NET arm64 部署） | 维护中 | 备选 |
| OpenShamrock | Kotlin/Java (Xposed) | 需官方手机 QQ + Root | 原生 Android | **原仓库已归档**（2024-08），仅社区 fork | 不在第一方案内（见 §2.3 R3） |

**决策**：第一阶段以 **NapCat（Linux arm64）** 为默认协议端，代码只依赖 OneBot 11 + `ProtocolEndpoint` 抽象，协议端切换为配置项。

**关键推论（影响 §2.3）**：NapCat 是 Node 应用且**依赖官方 QQ Linux 客户端**，二者都无法收敛为"单文件静态二进制"，因此 **R2（静态二进制直挂）不成立**，运行时**定案走 R1（内嵌 Linux 运行时）**。

**待验证项（唯一剩余）**：在 Android 的 PRoot 环境下承载 NapCat + 官方 QQ Linux arm64 客户端能否成功登录。验证方式：见 §11「待验证清单」V1。

### 2.3 运行时选型：内嵌 Linux 运行时承载协议端

协议端是 Linux 进程，必须解决"在 Android 上跑 Linux 二进制"的问题。候选：

| 方案 | 说明 | 体积/冷启动 | 可行性 | 结论 |
|------|------|-------------|--------|------|
| **R1 内嵌 Linux 运行时**（PRoot + Alpine rootfs） | 完整用户空间，可运行 Node、可注入官方 QQ Linux arm64 客户端 | 重 | 高（主应用已验证该链路；ARM 设备已有 NapCat 实跑案例） | **定案方案** |
| **R2 静态二进制直挂执行** | 协议端以静态 musl 二进制随 APK 分发，直接 exec | 最轻 | **不成立** | **已否决**（默认协议端 NapCat 需 Node + 官方 QQ 客户端，无法静态化，见 §2.2） |
| **R3 Root + Shamrock** | 复用官方手机 QQ 登录，行为最接近真人 | 最轻 | 需 Root + LSPosed + 特定 QQ 版本；上游已归档 | 可选高级路径（非默认） |
| **R4 外部协议端** | 协议端跑在 PC/服务器 | 无 | 高 | 与"附属应用独立可用"目标冲突，排除 |

**决策（已按证据收敛）**：
- **定案走 R1**：R2 因默认协议端无法静态化而不成立，R1 成为唯一基线（与主应用已有的容器链路一致，不要求 Root）。
- R1 需在运行时内解决：Node 环境 + 官方 QQ Linux arm64 客户端注入（这是 §11 的 V1 验证内容）。
- R3 作为面向 Root 用户的**可选增强**，独立成阶段，不进第一方案。

**约束提醒**：主应用为支持运行 Linux 二进制而将 `targetSdk` 锁定在 28（绕过 Android 10+ W^X 限制）。附属应用走 R1，**同样需要锁定 `targetSdk = 28`**，这是必须接受的既定约束。

### 2.4 登录方式：扫码登录为主

| 方式 | 复杂度 | 安全性 | 第一阶段 |
|------|--------|--------|----------|
| **扫码登录** | 低 | 高（无密码落盘） | **唯一实现** |
| 密码 + 设备锁/短信 | 高（滑块、验证码、设备锁多分支） | 中（密码需落盘/临时持有） | 排除 |

**决策**：第一阶段**只做扫码登录**。密码登录依赖的验证分支多、失败面大，待扫码链路稳定后按需补充。

### 2.5 命名与包名

- 应用名 **MiniMe-QBot**，包名 **`com.mini.qbot`**，与 `com.mini.me_core`（主应用）、`com.mini.logs`（MiniMe Logs）构成同一命名家族，符合仓库命名纪律。
- `namespace` 与 `applicationId` 保持一致（与 MiniMe Logs 的做法对齐）。

### 2.6 版本策略：独立命名空间

应用版本从 `0.0.1` 起独立递增，**不与主应用的 `0.0.0.x` 号段混用**。

| 项 | 规则 |
|----|------|
| Tag 前缀 | `qbot-v0.0.1`（参照 `logviewer-v0.0.8` 先例） |
| versionName | 由 `gitVersionName()` 按 `qbot-v*` 前缀过滤后动态推导，**不手写** |
| versionCode | 由该前缀对应的**独立公式**生成，保证在本应用内单调递增 |
| 发版校验 | CI 走 `check-release-format.py --app qbot` 分支 |

#### 2.6.1 ⚠️ 必须先修的前置缺陷：`git describe` 未做前缀过滤（主应用已受此害）

现有 `app/build.gradle.kts` 的版本推导用的是裸 `git describe --tags --always --dirty`：

```
arrayOf("git", "describe", "--tags", "--always", "--dirty")
```

`git describe` 默认**不区分 tag 来源**，会选取**最近的任意 tag**。这意味着：

- 今天：`logviewer-v0.0.8` 这类 tag 若成为最近 tag，主应用的正则 `^(\d+)\.(\d+)\.(\d+)\.(\d+)` **匹配失败** → 版本号静默回退到 `0.0.1-dev.N`（开发态），`versionCode` 也从 tag 公式退化为 `BASE + 提交数`。
- 明天：`qbot-v0.0.1` 会制造同样的干扰，而且 **QBot 越频繁发版，主应用越容易被"劫持"**。这正是 AGENTS 反复警告的"versionCode 回退 → 升级判定失效"的温床。

**必须在 QBot 打第一个 tag 之前修复**：

| 应用 | 应改为 |
|------|--------|
| 主应用 | `git describe --tags --always --dirty --match "v[0-9]*"` |
| MiniMe Logs | `... --match "logviewer-v[0-9]*"`（并同时去掉硬编码版本） |
| MiniMe-QBot | `... --match "qbot-v[0-9]*"` |

> 该修复同时消除主应用现有的潜在版本劫持风险，属"顺手关门"，建议独立提交。

#### 2.6.2 CI 侧配套改造（进度：全部完成）

| 脚本 / 流程 | 改造内容 | 状态 |
|-------------|----------|------|
| `app/build.gradle.kts` | 主应用版本推导加 `--match "v[0-9]*"`，隔离其它应用 tag | ✅ 已完成（§2.6.1 前置缺陷已修） |
| `scripts/gitops/check-release-format.py` | 增加 `qbot → "MiniMe-QBot"`、`APP_TAG_PREFIXES` 映射表、`--app qbot`；并把标题正则改为兼容**三段版本号**（顺带修掉 MiniMe Logs 三段版本号此前必然校验失败的问题） | ✅ 已完成 |
| `scripts/gitops/release-log.py` | 增加 `--app`（决定 tag 前缀与版本日志路径）与 `--path`（按目录隔离提交范围，避免把主应用提交混入附属应用日志）；默认 prev 按本应用 tag 前缀过滤 | ✅ 已完成 |
| `.github/workflows/qbot-release.yml` | 新建 QBot 构建/发布 workflow（tag `qbot-v*`，产物 `MiniMe-QBot-v{版本}-{变体}.apk`）；已含 `fetch-depth: 0`、签名校验、硬校验门禁 | ✅ 已创建（`:qbot-app` 与 `CHANGELOG-qbot.md` 均已就绪，可随首个 `qbot-v*` tag 启用） |
| `docs/Version Log/CHANGELOG-qbot.md` | QBot 独立版本日志（`release-log.py --app qbot` 的权威来源） | ✅ 已完成（已建骨架，含 `## [Unreleased]`；首个版本发版时录入条目） |
| `qbot-app/build.gradle.kts` | 版本推导为 `git describe --match "qbot-v[0-9]*"`，versionCode 用**独立的 `0.0.1` 递增公式**（不与主应用 `0.0.0.x` 号段混算） | ✅ 已完成（最小骨架落地，见 §2.6.3） |
| release workflow | versionCode 单调校验需**按 app 隔离** | ✅ 已完成（主应用 workflow 的 prev tag 限定为 `^v[0-9]`；QBot workflow 新增按 `qbot-v` 隔离的三段式 versionCode 单调校验） |

> §2.6 全部改造项已完成（多应用基建 + 模块骨架 + 版本日志 + workflow 按 app 隔离）。
> QBot 现可合规发版：录入 `CHANGELOG-qbot.md` 本版条目后，打 `qbot-v0.0.1` tag 即触发 CI。
> 完整发版规则见 AGENTS「发版流程」与 [docs/ci-release.md](file:///workspace/docs/ci-release.md)。

#### 2.6.3 最小骨架落地内容（已完成）

`:qbot-app` 已登记进 `settings.gradle.kts`，最小骨架（仅"能编译、能装、有占位页"，不含登录链路）：

| 项 | 落地内容 |
|----|----------|
| 模块与构建 | 新增 `:qbot-app`（与 `:logviewer-app` 同级）；`namespace` / `applicationId` = `com.mini.qbot`；`minSdk 26` / `targetSdk 28` / `compileSdk 36`；debug 加 `.debug` 后缀与 release 同机共存 |
| 版本推导 | `git describe --match "qbot-v[0-9]*"`；三段 versionCode 公式 `A*1_000_000 + B*1_000 + C`（`0.0.1` → `1`），无 tag 回退 `10_000_000 + 提交数` |
| 签名 | 复用主应用唯一官方密钥（`app/keystore.properties` → `app/minime.jks`），与主应用共用签名；不新增明文密钥 |
| 日志层 | 构建期 Copy 主应用日志层 11 个文件到 generated sourceSet（主应用只读、零改动）；Copy 任务后追加清单校验，缺失即 `GradleException` 并列出缺失项 |
| 应用骨架 | `QBotApplication`（Hilt 入口）+ `MainActivity`（`@AndroidEntryPoint`）+ `LoginScreen` 占位页；文案走 `strings.xml` 中英双份 |

> 上述骨架之上的第一阶段登录链路**已全部落地**（运行时 / 协议端 / 登录状态机 / 二维码 / 前台服务与保活），实际文件清单与供给脚本说明见 §八。

> 注意：MiniMe Logs 目前采用**硬编码** `versionCode/versionName`（`8` / `"0.0.8"`），本案**不沿用**该做法——AGENTS 要求版本以 Git Tag 为唯一事实源，硬编码易忘记递增。

### 2.7 日志层复用：沿用主应用日志层（源码复制）

按决策，MiniMe-QBot **直接沿用主应用的日志层实现**，采用与 MiniMe Logs 相同的 Gradle `Copy` 任务方式将日志源码暂存进本模块 sourceSet：主应用文件保持只读、零改动，构建时自动同步最新版本。

- 参照实现：[`logviewer-app/build.gradle.kts`](file:///workspace/logviewer-app/build.gradle.kts) 的 `stageReferencedSources` 任务。
- 复用范围：日志核心（`FileLogger` / `AILogger` / `LogLineParser` / `LogLevel` / `LogConfig` / `LogSanitizer` / `LogStats` / `Logger`）及日志基础设施（`LogFiles` / `LogLevelController` / `DiagnosticCleanup`）。
- **风险与缓解（已闭环）**：复制方案下，主应用日志文件若改名/移位，会产生编译期 unresolved。为把"定位成本高"降为"失败即报"，`Copy` 任务后追加**清单校验任务**：将待复制文件路径登记为显式清单，复制完成后逐个断言目标文件存在（缺失即 `throw GradleException` 并列出缺失项），使构建在最早的同步阶段失败并指明文件，而非拖到 Kotlin 编译期。
- **升级预案**：若复制项持续膨胀或频繁踩坑，改为抽取公共库模块（如 `:minime-log`），届时主应用与 MiniMe Logs 一并迁移，一次清掉历史债。

### 2.8 数据归属与访问：主应用持有，QBot 经 IPC 访问

**核心原则：数据与密钥由主应用唯一持有，QBot 不直接触碰主应用的数据层与加密库。**

理由（Android 层面的硬约束）：

1. **沙箱隔离**：两个独立 `applicationId` 的 APK 私有目录互不可见。
2. **密钥不可导出**：主应用数据层为 SQLCipher 加密，密钥由 Android Keystore（硬件 backing、与 UID 绑定、不可导出）派生。QBot **物理上无法获得**该密钥，即使拿到 DB 文件也只会得到"假损坏"（`file is not a database`）。
3. **单写者原则**：主应用每次打开库都会执行 `MigrationEngine` 的快照与 schema 自愈。若两个应用各自对同一库文件跑迁移，快照/回滚会互相踩踏，存在触发隔离重建甚至清库的风险。

因此：

| 明确否决 | 原因 |
|----------|------|
| `android:sharedUserId` 共 UID 共享目录 | API 29+ 已弃用，要求同签名，未来版本随时失效 |
| 公共存储目录共享加密库 | Android 10+ 分区存储隔离，且属安全降级 |
| QBot 直接打开主应用的加密数据库 | 密钥不可得，必然"假损坏" |

**访问方式（已定案，非"实施时再定"）**：采用 **AIDL 绑定服务（业务级接口）+ 签名级自定义权限**。

| 项 | 定案 |
|----|------|
| 传输机制 | 主应用导出一个 **AIDL bound service**，暴露**业务级**方法（不暴露裸 cursor / SQL） |
| 调用方鉴权 | 自定义权限 `com.mini.me_core.permission.ACCESS_QBOT_DATA`，`protectionLevel="signature"` |
| 写权限 | 独立权限 `...WRITE_QBOT_DATA`（同样 signature 级），且**默认不授予**，需用户在主应用显式开启 |
| 读写策略 | 默认**只读**；写入必须走显式授权开关 + 用户确认 |
| 调用方限制 | 依赖**同签名**天然限定：只有与主应用同签名的应用才能持有 signature 级权限 |
| 生命周期 | QBot 通过 `bindService` 按需绑定，主应用服务不可用时快速失败并降级提示 |

**由此产生的签名要求**：MiniMe-QBot 必须与主应用**共用签名**（与 MiniMe Logs 现状一致）。注意：签名 secrets 必须走独立配置且**不入库**（`:logviewer-app` 目前的明文密码写法不得沿用）。

**为何不用 ContentProvider**：主应用的数据访问是仓储（Repository）级业务逻辑，而非裸表游标；用 AIDL 暴露业务方法可避免把数据模型与 SQL 细节泄漏到进程边界之外。

**QBot 本地数据**：仅保留登录态等**极少量自身数据**（见 §五），使用 QBot 自己的 Keystore 密钥体系加密，与主应用数据完全分离。

> 第一阶段（登录）**不涉及**跨应用数据访问，IPC 访问层归属第三阶段（Agent 集成与统一管理）再落地。

---

## 三、总体架构

```
┌───────────────────────────────────────────────────────────────┐
│  MiniMe-QBot（独立附属应用 APK）                              │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  QBotLoginService（前台服务 · 生命周期宿主）              │  │
│  │                                                         │  │
│  │  ┌───────────────┐   ┌──────────────────────────────┐   │  │
│  │  │ RuntimeManager │   │ ProtocolProcessManager       │   │  │
│  │  │ 运行时安装校验  │   │ 协议端 启动/停止/重启/存活检测 │   │  │
│  │  │ 启停/健康检查   │   │ 日志采集（环形缓冲）           │   │  │
│  │  └───────┬───────┘   └──────────────┬───────────────┘   │  │
│  │          │                          │                   │  │
│  │          │            ┌─────────────▼───────────────┐   │  │
│  │          │            │ ProtocolEndpoint（抽象）      │   │  │
│  │          │            │  · OneBot11 标准接口          │   │  │
│  │          │            │  · 协议端私有登录接口适配      │   │  │
│  │          │            └─────────────┬───────────────┘   │  │
│  │          │                          │                   │  │
│  │  ┌───────▼──────────────────────────▼───────────────┐   │  │
│  │  │            LoginCoordinator（登录状态机）          │   │  │
│  │  │  QrCodeSource  ·  LoginStateRepository           │   │  │
│  │  └───────────────────────┬──────────────────────────┘   │  │
│  └──────────────────────────┼──────────────────────────────┘  │
│                             │                                 │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │  LoginScreen（Compose）+ LoginViewModel                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Linux 运行时（PRoot + rootfs）                          │  │
│  │  └── 协议端进程（NapCat）→ 本地回环 OneBot 11           │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

**通信方式**：协议端与应用同机通信，走**本地回环**（HTTP API + WebSocket），不暴露公网，不依赖外部网络。

**日志层**：通过 Gradle 源码复制沿用主应用日志层（§2.7），本应用内的运行日志与主应用同构。

**数据边界**：第一阶段本应用**自带全部所需数据**（仅登录态），与主应用无数据往来。第三阶段起，跨应用数据统一经 **IPC 访问层**由主应用提供（§2.8），图中未画出以免与第一阶段范围混淆。

---

## 四、登录详细设计（第一阶段核心）

### 4.1 首次登录时序

```
用户打开附属应用（首次）
  → 引导页：检查运行环境（运行时是否就绪 / rootfs 是否解压 / 协议端二进制是否存在）
  → 未就绪：初始化（下载/解压运行时与协议端，显示进度）
  → 就绪：启动 QBotLoginService（前台通知）
  → RuntimeManager 拉起协议端进程
  → 等待 ProtocolEndpoint 就绪（轮询 get_status / WS 连接成功）
  → 请求登录：ProtocolEndpoint 触发扫码登录
  → QrCodeSource 取二维码 → 渲染到页面（含倒计时）
  → 用户用手机 QQ 扫码 → 协议端上报状态变化
  → 状态机流转至 已登录
  → get_login_info 校验 → 持久化登录态 → 页面显示 QQ 号与昵称
```

### 4.2 登录状态机

```
NotInitialized ──初始化完成──► RuntimeReady
RuntimeReady ──启动协议端──► StartingProtocol
StartingProtocol ──接口就绪──► AwaitingLogin
StartingProtocol ──超时/失败──► Failed
AwaitingLogin ──请求二维码──► WaitingForQr
WaitingForQr ──取到二维码──► WaitingForScan
WaitingForQr ──失败──► Failed
WaitingForScan ──二维码过期──► WaitingForQr（自动/手动刷新）
WaitingForScan ──检测到扫码──► ScanConfirmed
ScanConfirmed ──登录成功──► LoggedIn
ScanConfirmed ──确认失败/超时──► WaitingForQr
LoggedIn ──掉线──► Disconnected ──自动重连成功──► LoggedIn
Disconnected ──重连耗尽──► AwaitingLogin（提示重新扫码）
任意状态 ──用户停止──► Stopped
```

状态持久化：状态机当前态 + 登录态落盘（见 §五），应用被杀后重启可按状态恢复（`LoggedIn` 直接走快速登录校验，免扫码）。

### 4.3 二维码获取：`QrCodeSource` 抽象

OneBot 11 标准不含扫码登录接口，协议端实现各异，必须抽象：

| 实现 | 来源 | 优先级 | 说明 |
|------|------|--------|------|
| `NativeApiQrCodeSource` | 协议端原生 HTTP 接口（登录接口返回二维码 base64/图片） | 首选 | 稳定、结构化 |
| `LogQrCodeSource` | 解析协议端 stdout / 日志中的二维码文本 | 兜底 | 脆弱，日志格式一变即失效；仅作降级 |

**决策**：优先实现 `NativeApiQrCodeSource`；`LogQrCodeSource` 仅作为该协议端无原生接口时的兜底，并对其做格式容错与失效告警。

### 4.4 登录态持久化与免扫码恢复

两个层面，职责分离：

| 层面 | 载体 | 内容 | 责任方 |
|------|------|------|--------|
| 协议端会话 | 运行时持久化目录（容器内 / 应用私有目录） | session token / device 信息 | 协议端自身 |
| 应用侧登录态 | 应用私有加密存储 | QQ 号、昵称、登录时间、协议端标识、最近状态 | `LoginStateRepository` |

**免扫码恢复流程**：应用重启 → 启动协议端 → 协议端用持久化 session 尝试快速登录 → 应用 `get_login_info` 校验 → 成功则直接进入 `LoggedIn`；失败则回落 `AwaitingLogin` 提示重新扫码。

### 4.5 登录成功校验

登录"成功"的判定不能只看协议端"已登录"信号，必须闭环校验：

1. `ProtocolEndpoint.getLoginInfo()` 返回的 QQ 号与页面展示一致；
2. `ProtocolEndpoint.getStatus()` 显示在线且运行正常；
3. 记录校验时间戳，作为"可信登录态"的凭据。

### 4.6 失败与恢复策略

| 场景 | 检测方式 | 处理 |
|------|---------|------|
| 运行时未就绪 | 首次启动环境检查失败 | 引导重新初始化 |
| 协议端进程崩溃 | 进程存活检测（周期探测） | 自动重启（退避：1s→2s→5s→10s→30s，上限 N 次） |
| 协议端启动超时 | 等待接口就绪超时 | 标记 `Failed`，展示日志入口 |
| 二维码取不到 | `QrCodeSource` 失败 | 降级到另一实现；仍失败则提示手动重试 |
| 二维码过期 | 超时计时器 | 自动刷新，连续失败则暂停并提示 |
| 登录后掉线 | 心跳 / 连接断开 | 自动重连；重连耗尽回落待登录 |
| 端口被占用 | 启动前端口检测 | 换端口或提示冲突 |

---

## 五、数据与安全

### 5.0 数据归属原则（贯穿）

| 数据类别 | 归属 | 存储位置 | 访问方式 |
|----------|------|----------|----------|
| 主应用业务数据（对话、设置、凭据等） | **主应用唯一持有** | 主应用加密库 | QBot 经 IPC 访问（第三阶段起） |
| QBot 自身数据（登录态、协议端配置、日志） | QBot 自己持有 | QBot 私有目录 | QBot 内部直接访问 |

原则：**QBot 永不直接打开主应用的加密数据库**（密钥不可得，见 §2.8）。

### 5.1 本地数据

第一阶段数据量极小，仅需：

| 数据 | 存储 | 加密 |
|------|------|------|
| 登录态（QQ 号 / 昵称 / 状态 / 时间） | 应用私有存储（KV / 轻量库） | 敏感字段加密 |
| 协议端运行配置（端口 / token / 启动参数） | 应用私有文件 | token 加密 |
| 运行日志 | 应用私有文件（环形保留） | 视内容而定，需做敏感信息脱敏 |

### 5.2 密钥体系（自建，不可复用主应用）

Android 应用沙箱隔离，附属应用**无法访问**主应用的 Keystore 与 `EncryptedSharedPreferences`。因此需**自建**一套同思路的密钥体系，**仅用于保护 QBot 自身的少量数据**（不涉及主应用数据）：

- MasterKey：Android Keystore（AES-256-GCM，硬件 backing），alias 独立命名（如 `minime_qbot_master_key`）；
- 字段级 DEK：存储于本应用私有的加密 SharedPreferences；
- 加密输出格式：AES-256-GCM，参考主应用凭据加密的格式约定（便于未来迁移）。

### 5.3 安全边界

- 所有端口**仅监听回环**，禁止 `0.0.0.0`；
- 协议端本地通信带 token 鉴权；
- 日志**不得**输出密码、token、session、二维码原始内容；
- 第一阶段**不开放任何对外网络服务**给主应用或第三方；
- 第三阶段开放的 IPC 访问层必须**限定调用方**（签名校验 / 权限声明），并默认只读，写操作需显式授权。

---

## 六、UI 设计（第一阶段：单页登录屏）

```
QBot 登录页
├── 顶部状态条
│   ├── 运行时：未初始化 / 就绪 / 异常
│   ├── 协议端：已停止 / 启动中 / 运行中 / 异常
│   └── 连接：未连接 / 已连接 / 掉线
├── 主区（随状态切换）
│   ├── 未初始化 → 「初始化运行环境」按钮 + 进度
│   ├── 初始化中 → 不确定进度条 + 当前阶段 + 首次启动耗时提示
│   ├── 供给中   → 确定进度条（i/n）+ 当前步骤说明 + 首次启动耗时提示
│   ├── 待登录   → 「获取登录二维码」按钮
│   ├── 等扫码   → 二维码 + 倒计时 + 「刷新二维码」
│   ├── 已登录   → QQ 头像 + 昵称 + QQ 号 + 「退出登录」
│   └── 异常     → 错误原因 + 重试 + 「查看日志」
└── 底部日志区（可折叠，**首次进入默认展开**，实时流式追加、最近 N 行、自动滚动）
```

**首次启动可观测性（本版新增）**：
- 运行时安装、协议端供给/进程、登录编排各层日志统一汇入 `log/QBotLogBus`（内存环形缓冲 + 脱敏），登录页日志区**实时**消费（不再是「跑完才一次性回显」）；
- 供给脚本按进度契约输出 `[qbot-step] i/n 说明` 阶段标记，客户端解析为主区**确定进度条**与步骤文案；
- 日志区默认展开，避免长耗时下载期间用户误以为「卡死」。

UI 纪律（沿用主应用规范）：
- 所有用户可见文案**必须**走 `strings.xml`（中英双份），禁止在 `.kt` 中硬编码中文；
- 页面遵循主应用 `ui-standards.md` 的主题、圆角、间距规范。

---

## 七、运行环境与保活

### 7.1 保活（第一阶段最小集）

第一阶段只需保证"登录态不因进程被杀而丢失/不可恢复"，保活做最小必要集：

| 层 | 机制 | 第一阶段 |
|----|------|---------|
| 前台服务 | `startForeground` 常驻通知（显示协议端/登录状态） | 需要 |
| 进程守护 | 周期存活检测 + 退避重启 | 需要 |
| 系统唤醒 | `WakeLock`（防 CPU 休眠断连） | 需要 |
| 开机自启 | `BOOT_COMPLETED` | 后续阶段 |
| 应用恢复 | `START_STICKY` | 需要 |

### 7.2 国产 ROM 适配

引导用户关闭电池优化、加入白名单；检测到被杀后在页面给出明确指引，而非静默失败。

---

## 八、模块与代码结构

新增独立 Gradle 应用模块 `:qbot-app`（与 `:logviewer-app` 同级；除**日志层源码复制**外，其余代码完全独立）：

```
:qbot-app
├── namespace / applicationId：com.mini.qbot
├── 应用显示名：MiniMe-QBot
├── versionName / versionCode：由 qbot-v* Tag 动态推导（§2.6）
├── targetSdk = 28（运行 Linux 二进制约束）
├── buildTypes：debug / release（release 签名独立，secrets 不入库）
├── 日志层：Gradle Copy 任务复制主应用日志源码（§2.7，参照 :logviewer-app）
└── sourceSet
    ├── QBotApplication（Hilt 入口）
    ├── runtime/        RuntimeManager、RuntimeInstaller、HealthCheck
    ├── protocol/       ProtocolProcessManager、ProtocolEndpoint、OneBot11 客户端、NativeApiQrCodeSource/LogQrCodeSource
    ├── login/          LoginCoordinator、LoginStateMachine、LoginStateRepository
    ├── service/        QBotLoginService（前台服务）
    ├── ui/             LoginScreen、LoginViewModel、组件
    ├── di/             QBotModule（Hilt）
    └── (第三阶段) ipc/  IPC 访问层（AIDL / ContentProvider / 本地 HTTP 客户端）
```

已在 `settings.gradle.kts` 的 `include(...)` 中登记 `:qbot-app`（与 `:logviewer-app` 同级，见 [settings.gradle.kts](file:///workspace/settings.gradle.kts#L44-L49)）。

命名纪律：统一 MiniMecore 命名体系；release 签名 secrets 不入库（吸取 `:logviewer-app` 明文密码的教训）。

### 8.1 第一阶段登录落地结构（已完成）

实际落地文件与职责（`qbot-app/src/main/java/com/mini/qbot/`）：

| 包 | 文件 | 职责 |
|----|------|------|
| `runtime/` | `QBotRuntimePaths` / `QBotRuntimeState` / `QBotRuntimeInstaller` / `QBotContainerExecutor` | 资产安装（PRoot + Alpine rootfs）、容器内命令执行；协议端数据目录持久映射为容器内 `/root/qbot`，rootfs 重装不丢登录态 |
| `protocol/` | `QBotProtocolConfig` / `QBotProtocolProcessManager` / `OneBotClient` / `QBotQrCodeSource` | 协议端配置（端口 / token，端口冲突自动后探）、NapCat 幂等供给与进程守护（退避重启上限 5 次）、OneBot 11 接口调用、二维码获取（原生接口优先、日志解析兜底） |
| `login/` | `QBotLoginState` / `QBotLoginRepository` / `QBotLoginCoordinator` | 登录状态机、登录态加密持久化、登录链路编排（免扫码恢复、掉线监控与恢复） |
| `log/` | `QBotLogBus` | 运行日志总线（内存环形缓冲 + token/base64 脱敏），汇聚运行时/协议端/登录各层可观测信息，供登录页实时订阅；写入同时经主应用日志层落盘 |
| `security/` | `QBotSecretStore` | 自建 Keystore 字段级加密（alias `minime_qbot_master_key`，与主应用密钥严格隔离） |
| `service/` | `QBotLoginService` | 前台服务保活（常驻通知 + WakeLock + `START_STICKY`），驱动登录协调器 |
| `ui/` | `QBotLoginViewModel` / `LoginScreen` | 单页登录屏（顶部状态条 + 随状态切换的主区 + 可折叠日志区），文案走 `strings.xml` 中英双份 |

> 启动时机：登录宿主前台服务仅由用户动作触发（「初始化运行环境 / 启动登录 / 重试」按钮经 `MainActivity` 拉起），不在应用打开时自动下载运行时。

### 8.2 协议端供给脚本（新增资产）

- **位置**：`qbot-app/src/main/assets/qbot/provision-protocol.sh`；运行时由 `QBotRuntimeInstaller` 提取到协议端数据目录，经 `proot -b` 映射为容器内可见。
- **运行位置**：PRoot 容器内，伪 root（`proot -0`）执行，工作目录即协议端数据目录。
- **契约**（环境变量，由 `QBotProtocolProcessManager` 注入，勿随意更改）：`QBOT_DATA_DIR` / `QBOT_ONEBOT_PORT` / `QBOT_ONEBOT_TOKEN` / `QBOT_WEBUI_PORT` / `QBOT_WEBUI_TOKEN` / `QBOT_ALPINE_MIRROR`。
- **进度契约**：脚本每阶段开始时输出 `[qbot-step] i/n 说明`，`QBotProtocolProcessManager` 解析为 `QBotProvisionProgress` 驱动登录页确定进度条；`QBotContainerExecutor.exec` 的 `onLine` 回调使脚本输出**逐行实时**进入日志总线。调整阶段须同步维护 `n` 并保持格式一致。
- **幂等**：成功完成后写出 `$QBOT_DATA_DIR/.provisioned`（内容为 `PROVISION_VERSION`），该版本号须与 `QBotProtocolProcessManager.PROVISION_VERSION` **严格一致**；调用方据此跳过重复供给。
- **目录约定**（登录链路依赖，勿改）：`napcat/`（NapCat 工作目录）、`napcat/cache/qrcode.png`（扫码二维码落盘位置）、`napcat/config/onebot11.json`（OneBot 配置）。
- **不确定性**：脚本依赖 NapCat 与官方 QQ Linux arm64 客户端（§11 V1）。若真机验证发现安装 / 启动方式变化，只需修订本脚本，代码侧契约不变。

---

## 九、阶段规划

| 阶段 | 目标 | 交付 |
|------|------|------|
| **第一阶段（本文档）** | **登录** | 附属应用可完成扫码登录，登录态可持久化/可恢复，状态可观测 |
| 第二阶段 | 消息链路 | OneBot 11 事件接收、私聊/群@触发、文本收发 |
| 第三阶段 | Agent 集成与统一管理 | 落地 **IPC 访问层**（主应用持有数据、QBot 经 IPC 访问）；接入主应用 Agent 实现 AI 回复；主应用可管理 QBot |
| 第四阶段 | 会话与群能力 | 会话上下文、聊天记录页面、群管理 |
| 第五阶段 | 高级能力 | 多账号、密码登录、Satori/Milky 协议 |
| **风控专题（贯穿，非独立阶段，延后）** | 降低封号风险 | 限速器、熔断退避、被动优先、内容审核、失败降频 |

> 风控策略层按决策**延后**，不在第一阶段实现；但第一阶段的数据模型与发送路径（未来）需预留挂载点。
>
> IPC 访问层按决策归属**第三阶段**：第一阶段 QBot 只持有自己的登录态数据，不与主应用发生数据往来。

---

## 十、风险闭环表

> 状态口径：**已闭环** = 已有确定结论或缓解措施，无需再决策；**待实测** = 只能由实验/实机验证关闭，已验证方案已在 §11 列明；**已接受** = 主动接受，不再处理。

| # | 风险 | 严重度 | 状态 | 结论 / 缓解措施 |
|---|------|--------|------|------------------|
| R1 | 协议端 arm64 可用性（原"待验证项"） | 高 | **已闭环** | 调研确认 **NapCat 支持 Linux arm64**，官方 QQ 亦有 arm64 Linux 包；默认协议端由 LLBot 改为 NapCat（§2.2） |
| R2 | 静态二进制不可用（R2 方案不成立） | 中 | **已闭环** | NapCat 需 Node + 官方 QQ 客户端，无法静态化 → R2 否决，运行时**定案 R1**（§2.2/§2.3） |
| R3 | 协议端停更/失效（生态动荡） | 高 | **已闭环** | 契约锁定 OneBot 11 + `ProtocolEndpoint` 抽象；已选定"默认 NapCat + 备选 LLBot/Lagrange"的多后端策略（§2.2） |
| R4 | 版本命名空间与其它应用冲突 | 高 | **已闭环（含前置缺陷修复）** | 独立前缀 `qbot-v*`；并发现主应用 `git describe` **未做前缀过滤**的既存缺陷，修复方案已定（§2.6.1） |
| R5 | CI 无法为多应用发版 | 中 | **已闭环（方案已定）** | 明确列出 3 处改造：`check-release-format.py` / `release-log.py` / release workflow（§2.6.2） |
| R6 | 日志层源码复制脆弱 | 中 | **已闭环** | 增加**清单校验任务**（缺失即 GradleException），把风险从"编译期难定位"降为"同步期即报"；并留抽公共库的升级预案（§2.7） |
| R7 | IPC 边界被越权调用 | 中 | **已闭环** | 定案 AIDL 业务级服务 + `signature` 级自定义权限 + 默认只读 + 写权限显式授予（§2.8） |
| R8 | 无法直接共享主应用数据与密钥 | 高 | **已闭环** | 设计上即禁止；数据归主应用、QBot 经 IPC 访问；第一阶段无此需求（§2.8/§5.0） |
| R9 | 扫码能力无标准接口 | 中 | **已闭环** | `QrCodeSource` 抽象，协议端原生接口优先、日志解析兜底（§4.3） |
| R10 | `targetSdk = 28` 约束 | 中 | **已接受** | 既定约束，与主应用保持一致（§2.3） |
| R11 | 运行时体积大 / 冷启动慢 | 中 | **已接受（第一阶段）** | R1 体积无法回避；以首次初始化进度、启动状态可见来缓解；R2 已否决，故无轻量化捷径 |
| R12 | Android 后台杀进程 | 高 | **待实测** | 保活组合已定（前台服务 + 守护 + WakeLock）；实际存活率需在国产 ROM 实机验证（§7.1、§11 V2） |
| R13 | PRoot 内承载 NapCat + 官方 QQ 客户端能否登录 | 高 | **待实测** | 全案唯一未消除的技术不确定性 → §11 V1（必须先验证，不通过则方案需重估） |
| R14 | 个人号登录封号 | 高 | **已接受（延后）** | 按决策延后为风控专题；第一阶段 UI 需明确提示风险 |

**闭环结论**：14 项风险中，**9 项已在设计层闭环、2 项主动接受、2 项待实测（V1/V2）、1 项延后**。唯一可能推翻方案的是 **R13**，因此它被列为实施前的第一道门（§11 V1）。

---

## 十一、待验证清单（实施前）

| 编号 | 验证项 | 为什么必须先做 | 通过标准 | 失败后果 |
|------|--------|----------------|----------|----------|
| **V1** | 在 Android（arm64）的 PRoot 运行时内，能否拉起 NapCat + 官方 QQ Linux arm64 客户端并完成扫码登录 | 全案最底层假设；不成立则 R1 + NapCat 组合不可用 | OneBot 11 `get_login_info` 返回正确 QQ 号 | 需重估方案：改协议端（Lagrange/LLBot 另验）或改运行时 |
| **V2** | 后台保活实际存活率（主流国产 ROM 各测至少一款） | 决定"免扫码恢复"是否真能成立 | 静置 12h 后进程存活、登录态有效 | 需强化保活或改交互预期（提示用户手动保活） |
| **V3** | 首次初始化耗时与运行时体积 | 决定首次启动 UX 与分发体积 | 给出可接受的耗时/体积基线 | 需优化 rootfs 裁剪或改分发策略 |

> V1 建议作为**第一阶段开工的第一个任务**，以最小验证脚本形态先跑通，再进入 UI 与状态机开发。

---

## 附录：参考资源

- OneBot 11 标准：https://11.onebot.dev/
- **NapCat 文档（默认协议端）**：https://napneko.github.io/
- NapCat 平台/架构支持矩阵：https://napneko.github.io/config/advanced
- NapCat 部署方式（含 Linux arm64）：https://doc.napneko.icu/guide/boot/Shell
- NapCat 在 ARM 开发板实跑案例：https://cloud.tencent.cn/developer/article/2742154
- 官方 QQ Linux 下载（含 arm64 包）：https://im.qq.com/rainbow/linuxQQDownload
- LLBot（备选）文档：https://github.com/LLOneBot/LuckyLilliaDoc
- Lagrange.Core（备选）：https://github.com/LagrangeDev/Lagrange.Core
- OpenShamrock（已归档，仅供背景了解）：https://github.com/whitechi73/OpenShamrock
- 主应用容器链路：`app/src/main/java/com/mini/me_core/feature/agent/domain/container/`
- 主应用 MCP 服务端（第三阶段接入候选）：`app/src/main/java/com/mini/me_core/feature/agent/domain/execution/mcp/server/`
- 主应用加密体系（数据不可共享的依据）：`app/src/main/java/com/mini/me_core/datalayer/encryption/`
- 附属应用先例（日志层源码复制、独立签名）：`logviewer-app/build.gradle.kts`
