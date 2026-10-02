# MiniMe-core 测试覆盖长期计划

本文档描述 MiniMe-core 的测试覆盖现状、分层测试策略、模块优先级与长期维护约定。
目标不是追求覆盖率数字，而是让**高风险、易回归的业务逻辑**有可执行、可回归的测试守门。

---

## 1. 当前测试覆盖现状

测试统一位于 `app/src/test/`，技术栈为 **JUnit 4 + JVM 纯单测**（`org.junit.Assert`），
配合 SQLDelight `JdbcSqliteDriver` 内存库做持久化逻辑测试。当前**未引入** MockK / Mockito / Truth，
对依赖接口一律手写 Fake / Stub；未使用 Robolectric runner（`unitTests.isReturnDefaultValues = true`
让 `android.util.Log` 等桩方法返回默认值而非抛异常）。

### 1.1 已有测试覆盖的模块

| 模块域 | 代表测试类 | 覆盖内容 |
| --- | --- | --- |
| 安全 / 加密备份 | `BackupCryptoTest`、`UserPasswordBackupCryptoTest`、`SecurityAuditLogCodecTest`、`SecurityScoreCalculatorTest` | 加密流往返、口令错误、损坏头、审计日志编解码、安全评分权重与阈值 |
| 密钥身份一致性 | `KeyIdentityConsistencyTest` | `LibName` / `DatabaseDefinition` / `CipherPassphrase.purpose` 三处逐字一致、区域无关 |
| 数据库迁移 | `MigrationEnginePreOpenTest` 等 4 个 | 升级前快照、版本决策、不可读库隔离 |
| SQLDelight 查询 | `AgentPagedQueryRegressionTest` | 分页查询编译与语义回归 |
| Agent 权限 / 守卫 | `DangerousCommandGuardTest`、`ShellCommandParserTest`、`BuiltInSafeCommandsTest`、`FileObservationGuardTest` | 危险命令识别、命令解析、文件观测边界 |
| Agent 输入 / 工作流 | `UserInputParserTest`、`GoalStaleDetectorTest`、`LoopGuardTrackerTest`、`HookDispatcherTest` | 输入解析、目标过期、循环守卫、Hook 分发 |
| Agent 工具 | `ToolRegistryTest`、`ToolResultCacheTest`、`GitOpsCommitRuleTest`、`CheckEnvironmentToolTest` | 工具注册、结果缓存、提交规则 |
| 备份策略 | `BackupPolicyTest`、`AutoBackupRotationTest`、`SentinelLogicTest` | 保留份数轮转、哨兵逻辑 |
| 更新 / 设置 | `VersionComparatorTest`、`ReleaseJsonParserTest`、`SettingsSearchHistoryManagerTest` | 版本比较、发布 JSON、搜索历史 |
| 渲染 / 展示派生 | `TaskStateDeriverTest` 等一组 Deriver、`RichTextSegmenterTest`、`FormatTokenCountTest` | 状态派生逻辑（纯函数） |

### 1.2 本次新增覆盖的模块

| 模块域 | 新增测试类 | 覆盖内容 |
| --- | --- | --- |
| 安全：口令构造 | `CipherPassphraseTest` | purpose 前缀、库/字段密钥区分、openParams 两态同源 |
| 安全：注册表 | `DatabaseRegistryTest` | 注册/查询/去重 fail-fast/线程安全语义 |
| 数据层：LIKE 转义 | `SqlLikeTest` | `%` `_` `\` 三类通配符转义、Unicode 透传 |
| 数据层：可靠队列 | `QueueStoreTest` | 入队自增 id、pending 时间闸门与排序、状态流转、删除 |
| 数据层：时序存储 | `TimeSeriesStoreTest` | 记录、按类型/区间查询、冷数据清理与跨类型隔离 |
| 语法高亮 | `MiniMeSyntaxHighlighterTest` | token 类别（关键字/字符串/注释/数字/函数/类型）与语言支持表 |
| 语言检测 | `LanguageDetectorTest` | 文件名/扩展名/shebang/modeline/内容特征优先级链与兜底 |
| Agent 重试 | `RetryPolicyTest` | 指数退避截断、瞬时错误分类（429/5xx/IO/Stream code）、不可重试错误 |

### 1.3 仍缺失或仅部分覆盖的模块

| 模块 | 现状 | 缺失原因 / 备注 |
| --- | --- | --- |
| `EncryptedDatabaseManager` | 无直接单测 | 构造依赖 `EncryptedDriverFactory` / `KeyRotationMigrator`，二者强耦合 Android `Context` + SQLCipher 原生库，纯 JVM 无法实例化 |
| `KeyRotationMigrator` | 无直接单测 | 同上：`context.getSharedPreferences` / `getDatabasePath` / `SQLiteDatabase.loadLibs` 需 Robolectric 或仪器化环境 |
| `EncryptedDriverFactory` | 无直接单测 | `SupportFactory` + `AndroidSqliteDriver` 构造即打开原生库，且 PRAGMA 回读依赖真实连接 |
| `UnifiedKeyManager` / `AndroidUnifiedKeyManager` | 无单测 | 依赖 Android Keystore + 加密 SharedPreferences |
| 代理会话 / Clash 管理 | 无单测 | `ProxySessionManager` 依赖 VpnService / `ClashProxyManager` 生命周期；其空闲超时夹取逻辑可用 Fake 补测（见计划） |
| Repository 业务封装 | 部分 | `AgentRepository` 等多为 SQLDelight 查询的薄封装，已有 `AgentPagedQueryRegressionTest` 钉住关键查询 |
| Compose UI | 无单测 | 界面渲染走 instrumentation / 截图测试，不在本计划范围内 |

---

## 2. 分层测试策略

按「稳定性 / 执行速度 / 依赖」分三层，越靠左越应该多写：

```
纯 JVM 单元测试  →  架构层测试（内存库 / Fake）  →  仪器化 / UI 测试
   快、多写              中速、按模块补                 慢、少量守门
```

### 2.1 纯 JVM 单元测试（主力，当前唯一落地层）
- 对象：无 Android framework 依赖的纯逻辑——解析器、策略、分类器、编解码、数学/边界、状态机。
- 约束：不访问真实文件系统以外的 Android API；如需时间，注入 `now: Long` 参数而非读 `System.currentTimeMillis()`。
- 风格：JUnit 4 + `Assert`，Arrange-Act-Assert，测试名用反引号中文描述场景。

### 2.2 架构层测试（内存 SQLite + Fake）
- 对象：Repository / Store / 查询映射逻辑。
- 做法：`JdbcSqliteDriver(IN_MEMORY)` + `XxxDb.Schema.create()`，对生成的查询做端到端验证；对端口接口手写 Fake。
- 价值：把「SQL 编译不过 / 语义漂移 / 映射错位」钉在 CI，而不是等线上崩溃。

### 2.3 仪器化 / Robolectric 测试（少量、守门）
- 对象：强依赖 `Context`、SharedPreferences、Keystore、SQLCipher 原生库的模块
  （`KeyRotationMigrator`、`EncryptedDriverFactory`、`UnifiedKeyManager`）。
- 做法：引入 Robolectric（依赖已在 `testImplementation`）跑带真实 SharedPreferences 的
  `needsMigration` / 状态标记逻辑；原生库相关的 `migrate` 主流程放仪器化测试或手工验证清单。
- 原则：这一层**只测关键闸门**（迁移状态机、fail-close 不回退明文），不追求全路径覆盖。

---

## 3. 优先级排序

排序依据：**数据安全风险 × 线上崩溃历史 × 回归频率**。

1. **数据库加密 / 密钥迁移链路**（最高）——一旦出错表现为「库打不开」的假损坏或静默清空用户数据。
2. **数据持久化查询与映射**——分页、队列、时序的语义错误直接影响功能正确性。
3. **Agent 权限与命令守卫**——危险命令放通即安全事故。
4. **Agent 网络重试 / 流式错误分类**——影响长连接稳定性与成本。
5. **编辑器语言检测 / 语法高亮**——体验相关，无数据风险。
6. **纯展示派生**——已有较多覆盖，随新功能顺带补。

---

## 4. 模块覆盖计划

| 模块 | 当前状态 | 目标 | 优先级 | 预估工作量 |
| --- | --- | --- | --- | --- |
| `CipherPassphrase` | 本次已覆盖 | 守住 purpose / openParams 不变量 | 已完成 | — |
| `DatabaseRegistry` | 本次已覆盖 | 注册/去重/查询语义 | 已完成 | — |
| `SqlLike.escapeSqlLike` | 本次已覆盖 | LIKE 转义正确性 | 已完成 | — |
| `Queue` / `TimeSeries` Store | 本次已覆盖 | 队列/时序核心 CRUD 与时间闸门 | 已完成 | — |
| `MiniMeSyntaxHighlighter.tokenize` | 本次已覆盖 | token 类别优先级 | 已完成 | — |
| `LanguageDetector.detect` | 本次已覆盖 | 优先级链与兜底 | 已完成 | — |
| `RetryPolicy` | 本次已覆盖 | 退避与错误分类 | 已完成 | — |
| `EncryptedDatabaseManager` | 无 | 用 Fake collaborator 钉住「未注册抛错 / 需迁移触发 migrate / 已迁移跳过」决策 | 高 | 中（需抽接口或 Robolectric） |
| `KeyRotationMigrator.needsMigration` | 无 | Robolectric + 真实 SharedPreferences：已完成/空文件/文件存在三态 | 高 | 中 |
| `KeyRotationMigrator.migrate` | 无 | 失败安全降级（源口令打不开→不标记、不删库） | 高 | 大（原生库依赖） |
| `EncryptedDriverFactory` | 无 | fail-close：`UnsatisfiedLinkError` → `DatabaseEncryptionException`，不回退明文 | 高 | 中 |
| `UnifiedKeyManager` | 无 | DEK 读写、轮换双份候选（current/prev）选择逻辑 | 高 | 大 |
| `ProxySessionManager` | 无 | 空闲超时夹取（1–30 分钟）、`isAutoCloseEnabled` 状态流转（Fake ClashManager） | 中 | 中 |
| `AgentRepository` / 其他 Store | 部分 | 关键查询补内存库回归（参照 `AgentPagedQueryRegressionTest`） | 中 | 中 |
| 编辑器 `AdaptiveHighlighter` / `CodeDocument` | 无 | token 增量刷新、脏行区间计算 | 低 | 大 |

---

## 5. CI 集成建议

- 命令：`./gradlew :app:testDebugUnitTest`（JDK 17）。在 PR 与合入主干前必须绿灯。
- 报告：保留 Gradle 默认的 `build/test-results/testDebugUnitTest/*.xml` 与
  `build/reports/tests/testDebugUnitTest/index.html`；CI 可上传为构建制品。
- **不强制覆盖率阈值**：当前阶段以「关键路径必须有测试」为准，用覆盖率工具（如 Jacoco）
  出趋势报告观察，不设硬性 fail 阈值，避免为凑数字写无意义测试。
- 对 SQLDelight 内存库测试，确保 CI 与本地一致使用 `JdbcSqliteDriver.IN_MEMORY`，
  不依赖任何 Android 资源或原生 `.so`。

---

## 6. 长期维护约定

1. **新增/修改业务逻辑必须附带测试**：PR 中若改动了纯逻辑类（解析、策略、状态机、查询映射），
   必须同时提交对应单测；Code Review 将其作为合并门禁。
2. **回归优先**：修 bug 时先写一个能复现该 bug 的失败测试，再修代码使其转绿，把事故钉住。
3. **测试即文档**：测试名用反引号中文描述场景（如 `pending_excludesNonPendingStatus`），
   不写「测试一下」这类无信息量命名。
4. **避免为凑数量写测试**：每个测试必须验证真实业务分支（正常路径 / 边界 / 异常），
   不重复断言同一行为。
5. **依赖隔离**：新写可测代码时优先面向接口 / 注入时钟与文件路径，便于用 Fake 替代，
   避免把 Android framework 直接耦合进纯逻辑。
6. **定期盘点**：每个迭代对照本文档第 4 节表格更新状态，把「已完成」项移除、补入新发现的高风险模块。
