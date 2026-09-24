# MiniMe-core 仓库深度审计报告

- 审计日期：2026-09-25
- 审计范围：架构 / 安全 / CI/CD / 测试 / 资产一致性 / 数据库
- 审计方式：全仓库源码静态阅读 + 交叉核对（代码 vs prompts / docs / AGENTS.md / CI 配置）

---

## 一、总览

MiniMe-core 是运行在 Android 真机/虚拟机的 AI 编程工具（PRoot + Alpine 容器、终端、SSH 远程执行、MCP 双角色、Git 集成、加密备份恢复）。仓库整体工程质量**高**：

- Feature-based + DDD 分层清晰，依赖注入（Hilt）规范。
- 数据层 SQLDelight V2 六库拓扑 + 可选 SQLCipher 加密 + 崩溃恢复，迁移链路完整。
- 凭据加密体系（Android Keystore MasterKey → 内存 DEK → AES-GCM）设计成熟，启动不阻塞。
- CI 发版链路带 versionCode 单调校验 + 双 ABI 校验 + 用户层发版说明生成，运维手册完备。
- 测试覆盖纯逻辑层充分（约 53 个测试文件）。

但审计发现 **2 个高危缺陷**（一个安全控制形同虚设、一个数据丢失）、若干中低危问题，详见「四、问题清单」。

---

## 二、架构审计（正向结论）

### 2.1 分层与模块
- `core/` 跨功能基建（FileLogger、CredentialEncryptor、DEKManager、HostKeyManager、数据注册表）。
- `feature/` 按域划分：agent / git / settings / terminal / workspace / credentials / backup，职责边界清楚。
- `datalayer/` 三档分层（L0 引擎 → L1 迁移 → L2 仓储门面），业务不直接触碰驱动。

### 2.2 AI Agent 与工具系统
- `ToolRegistry` + `AgentModule` 注册 40+ 工具；`ToolPermissionManager` / `ToolPermissionPolicyEngine`（7 层策略）治理权限；`StatefulAgentWorkflow` 承载 PLAN/BUILD/AUTO 三模式与工具编排。
- 工具与提示词资产对齐良好（见 2.6）。

### 2.3 MCP 双角色
- 客户端：HTTP / stdio 服务器接入，动态注册远端工具。
- 服务器：Ktor CIO 起 Streamable HTTP（POST/GET/DELETE `/mcp`），Bearer token 鉴权（常量时间比较）、工具黑名单、远程调用强制审批（可关），`AgentToolMcpAdapter` 复用本地 `ToolPermissionManager`。设计对齐 MCP 规范（initialize/tools/list/tools/call/ping）。

### 2.4 远程 SSH 链路
- `HostKeyManager` 实现 TOFU（首次接受 + known_hosts 严格校验，密钥变更抛异常阻断），SSH、SFTP 均复用同一 verifier，无 Promiscuous 直连。

### 2.5 数据库与迁移
- 7 步明文↔加密迁移（`DbEncryptionMigrationEngine`）、启动崩溃回滚（`CrashRecovery`）、`V1toV2FullMigrator` 一次性移植（幂等）。
- 迁移 SQL 均规避 `;` 字面量（符合 AGENTS.md 纪律）。

### 2.6 资产一致性（prompts / docs / strings）
- 逐项核对 `AgentModule.kt` 注册的 40+ 工具与 `60-tools-and-paths.md`：`gitops`、`device_storage`、`playbook_*`、`intent_analyze`、`network_proxy`、`job_*`、`schedule` 等**均已正确记录**，参数与语义与代码一致，未发现缺口。
- `strings.xml`（中文）与 `values-en`（英文）双份维护，`.kt` 未发现硬编码中文 UI 文案。

---

## 三、CI/CD 审计

- `android-release.yml` 单 job 6 阶段：版本信息 → 单测门禁 → assembleRelease → Rename APK（含 ABI 双架构校验）→ 生成用户层 Release 说明 + 上传 → Run Summary。
- versionCode 由 Tag 四段式映射推导，CI 额外做单调递增校验；Release 标题/正文按用户语言规范生成，禁止 emoji。
- 产物校验清单（ABI / 签名 / SHA256）在 `docs/ci-release.md` 有完整手册。
- **风险提示**：正式签名 keystore（`app/minime.jks` + `app/keystore.properties`，含口令明文）按维护者决定**入库公开**（见 .gitignore 注释与 ci-release.md）。属知情决策，但需知悉其连锁风险（见 M1）。

---

## 四、问题清单（按严重度）

### H1（高）生物识别保护开启后实际不生效

- 位置：[CredentialEncryptor.kt](file:///workspace/app/src/main/java/com/mini/me_core/core/security/CredentialEncryptor.kt#L351-L397) `setBiometricRequired`
- 问题：line 359 创建新 MasterKey 时**硬编码 `biometricRequired = false`**，忽略了入参 `required`。用户开启「生物识别保护」后，state 行写入 `biometricRequired=true`（UI 显示已开启），但 Android Keystore 中的 MasterKey 实际**未**设置 `setUserAuthenticationRequired(true)`，任何人无需指纹/面容即可 unwrap DEK。
- 影响：声称的「生物识别保护」形同虚设（安全控制未落地）。
- 修复建议：`dekManager.getOrCreateMasterKey(biometricRequired = required)`。

### H2（高）DEK 轮换不重加密存量凭据，轮换后凭据静默丢失

- 位置：[CredentialEncryptor.kt](file:///workspace/app/src/main/java/com/mini/me_core/core/security/CredentialEncryptor.kt#L288-L338) `scheduleRotateDek`
- 问题：注释声明「逐表重写加密字段」「不影响存量凭据」，但实现**只**重写 `credential_encryption_state` 单行（`RotationReport.affectedTables` 仅 1 行），未对任何存量 `V2:` 密文用新 DEK 重加密。轮换后 `dekCached = newDek`，旧密文 GCM tag 校验失败 → 所有已保存的 API Key / Git Token 读取时返回空串。
- 触发路径：Settings → 安全 → 手动轮换 DEK（`SecuritySettingsViewModel.kt:134` 用户可触发）。
- 影响：静默数据丢失（用户需重新录入全部凭据），且与文档承诺相悖。
- 修复建议：轮换时遍历所有含加密字段的表用旧 DEK 解密→新 DEK 重加密，或明确禁止在存在凭据时轮换并二次确认。

### M1（中）正式签名 keystore 入库公开（知情决策，连锁风险需知悉）

- 位置：`app/minime.jks` + `app/keystore.properties`（含 storePassword/keyPassword 明文）。
- 问题：能读仓库者即可复制正式签名并冒充发布；同时 `SignatureKeyStore` 以「签名证书 SHA-256」派生外部备份加密密钥，签名公开后该密钥**可被推导**，`ExternalBackupStore` 写入公共目录的加密备份理论上可被持有仓库者解密（仍受 PBKDF2 210k 迭代保护）。
- 现状：维护者已知情并授权入库（.gitignore、ci-release.md 均有说明），CI 不再依赖 Secrets。
- 建议：若未来对公开分发敏感，应改回 CI Secrets 持有密钥；至少在文档中标注外部备份加密密钥强度依赖签名保密。

### M2（中）MCP 服务器 DELETE 端点无鉴权

- 位置：[McpHttpServer.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/agent/domain/mcp/server/McpHttpServer.kt#L107-L109)
- 问题：`DELETE /mcp` 直接回 `200 OK`，不校验 Bearer token。当前无状态变更（仅声明结束会话），但与其他端点鉴权策略不一致，也与设计文档「每个请求校验 token」矛盾。
- 建议：DELETE 与 POST/GET 走同一 `authenticate()`。

### M3（中）MCP 服务器 SSE 保活连接无并发限制

- 位置：[McpHttpServer.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/agent/domain/mcp/server/McpHttpServer.kt#L151-L167)
- 问题：`GET /mcp` 每个已认证请求持有一个无限 keepalive 协程（15s 心跳），无连接数上限。持有 token 的客户端（或局域网内被窃取的 token）可开大量连接耗尽服务器协程/端口资源。
- 建议：限制并发 SSE 连接数，超出返回 503。

### M4（中）全局允许明文流量

- 位置：[network_security_config.xml](file:///workspace/app/src/main/res/xml/network_security_config.xml) + `AndroidManifest.xml` `usesCleartextTraffic="true"`。
- 问题：全局放行 cleartext。用户配置 HTTP 的 AI provider 或自建服务时，API Key / 对话内容明文传输，同网段可嗅探。
- 现状：代码注释已说明属「用户可配置 base URL」的设计取舍。
- 建议：至少对默认域名（HTTPS 官方端点）做 `cleartextTrafficPermitted="false"` 域级限制，仅对用户自定义 HTTP 地址放行。

### L1（低）MCP token 明文持久化

- 位置：[McpServerManager.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/agent/domain/mcp/server/McpServerManager.kt#L81-L86)
- 问题：token 存 KVStore，数据库默认明文（SQLCipher 未开启时）落盘。token 可打开 App 的 MCP 能力。
- 建议：token 经 `CredentialEncryptor` 加密后落盘；或至少在 MCP 设置页提示开启库加密。

### L2（低）Git 凭据明文落盘应用私有目录

- 位置：[GitCredentialsFileSync.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/credentials/data/GitCredentialsFileSync.kt#L53-L68)
- 问题：`filesDir/minime/git-credentials` 明文存储 token（git-credential-store 格式所必需，且容器内 git 需要读取）。
- 现状：属设计权衡（三端共用凭据）；已核对备份规则（full_backup / data_extraction）**未包含**该目录，不会随云备份外泄。
- 建议：保持现状即可，但可考虑收紧文件权限（0600）并在文档注明。

### L3（低）MCP 请求日志含完整工具参数

- 位置：[McpHttpServer.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/agent/domain/mcp/server/McpHttpServer.kt#L118)
- 问题：`POST body` 前 200 字符入日志。JSON-RPC 参数可能含敏感内容（如 `network_proxy` 的 YAML/secret、写文件的敏感内容）。不含 token（在 Header），风险有限。
- 建议：对参数做 key 级脱敏后再入日志。

### L4（低）MCP 会话 id 未强校验

- 位置：[McpHttpServer.kt](file:///workspace/app/src/main/java/com/mini/me_core/feature/agent/domain/mcp/server/McpHttpServer.kt#L58-L59)
- 问题：会话 id 仅做连接管理，不校验客户端回传的 `Mcp-Session-Id`。属首期简化，无状态泄漏风险。
- 建议：后续版本按规范校验。

---

## 五、测试审计

覆盖良好（约 53 个测试文件，均在 `app/src/test`）：

| 领域 | 测试 |
|---|---|
| 数据层加密/迁移 | `DbEncryptionMigrationEngineTest`、`MigrationEngineSnapshotTest`、`CrashRecoveryTest`、`MigrationEnginePreOpenTest` |
| 备份加密 | `BackupCryptoTest`、`UserPasswordBackupCryptoTest`、`AutoBackupRotationTest`、`SentinelLogicTest` |
| 权限引擎 | `DangerousCommandGuardTest`、`ShellCommandParserTest`、`BuiltInSafeCommandsTest` |
| 工具系统 | `ToolRegistryTest`、`ToolResultCacheTest`、`GitOpsCommitRuleTest`、`CommandGuardsTest`、`CheckEnvironmentToolTest` |
| MCP 服务器 | `McpServerTest` |
| 其他核心逻辑 | `DataRegistryTest`、`DeltaAccumulatorTest`、`MessagePersistenceUseCaseTest`、`AgentPagedQueryRegressionTest` 等 |

**缺口**：
- `CredentialEncryptor`（含 H1/H2 两条路径）、`DEKManager`、`HostKeyManager`、`McpServerSecurity` 无直接单测（依赖 Android Keystore / 系统 API，纯 JVM 单测难度高）。若 H1/H2 有测试覆盖，此类回归应能被捕获——建议后续为「轮换后存量凭据可解」补测试（把加密逻辑抽为纯 JVM 可测模块）。

---

## 六、结论与建议优先级

1. **H1**：生物识别开关修正（一行改动，安全控制生效）。
2. **H2**：DEK 轮换前重加密存量凭据，或禁止存在凭据时轮换（防数据丢失）。
3. **M2/M3**：MCP 端点鉴权与连接数限制补齐。
4. **M1/M4**：属知情设计取舍，建议以文档/issue 形式留痕，便于未来决策。
5. 测试：为凭据加密关键路径补回归测试。

> 本报告仅基于静态阅读，未执行构建/单测/真机验证；涉及编译型代码的修复建议需按 AGENTS.md 纪律先过 `./gradlew :app:assembleDebug`。
