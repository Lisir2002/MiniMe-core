# Security Policy

## 支持的版本

| 版本 | 安全更新 |
|------|----------|
| v0.0.0.14 及以上 (main) | 当前维护版本 |
| v0.0.0.1 - v0.0.0.13 | 不再接收安全更新 |

## 报告安全漏洞

如果您发现了安全漏洞，请**不要**在公开的 GitHub Issue 中报告。请通过以下方式联系维护者：

1. GitHub Security Advisory（推荐）：在仓库的 `Security` → `Advisories` 页面提交私密报告
2. 邮件：`security@minime-core.local`（示例地址，请通过 GitHub 仓库主页获取最新联系方式）

请在报告中包含：
- 漏洞的详细描述和影响范围
- 复现步骤（如果可能）
- 受影响的版本和 commit
- 任何相关的概念验证代码

我们会在收到报告后尽快确认，并在修复后公开致谢（如您同意）。

## 安全架构概述

### 凭据加密
- **应用层加密**：`CredentialEncryptor` 使用 Android Keystore MasterKey（AES-256-GCM）wrap per-DEK，对 API Key、Git Token、SSH 密码等敏感字段进行列级加密
- **加密格式**：V2 前缀 + MasterKey-wrapped DEK 双层加密，支持从 V1 明文格式迁移
- **生物识别保护**：可选择开启 MasterKey 的生物识别保护（setBiometricRequired）
- **紧急解锁通道**：验证 SSH 密码后可紧急重置 MasterKey（emergencyResetMasterKey）
- **数据库加密**：SQLCipher 4.5.4 已集成，可在设置页开启对 SQLite 数据库文件的 AES-256 全库加密。默认明文，开启后执行 7 步迁移（快照→逐表拷贝→三重校验→原子替换→清理），支持崩溃恢复
- **备份加密**：用户口令派生 PBKDF2WithHmacSHA256（210,000 次迭代）+ AES/GCM/NoPadding 流式加密，GCM 自带完整性校验，口令错误或文件篡改时解密失败

### 密钥管理
- MasterKey 存储在 Android Keystore，alias 为 `minime_credential_masterkey`，不可导出
- per-DEK（32 字节 SecureRandom）经 MasterKey wrap 后存储在 SharedPreferences（Base64 密文）
- CredentialEncryptor 使用独立 alias `minime_credential_key`
- passphrase 使用后立即 `fill(0)` 擦除内存
- 密钥相关代码严格 fail-close，绝不静默降级为明文

### 终端/容器隔离
- 基于 PRoot + Alpine Linux 的本地容器，非 root 权限运行
- 凭据通过 IPC 桥接动态请求，避免持久化落盘
- **双层危险命令守卫**：
  - 权限引擎层：`DangerousCommandGuard` 静态守卫（B1 跨段管道、B2 chmod/chown token 序列、B3 设备/磁盘正则兜底），在权限引擎与工具入口双层拦截
  - 规范流程层：4 个 ToolGuard 实现（DangerousCommandGuard、LargeFileGuard、PathBoundaryGuard、FileObservationGuard），可在规范流程设置中独立开关
- 权限引擎：`ToolPermissionPolicyEngine` 基于 severityTier（0-3 四级）进行命令分类与拦截决策

### 已知限制
- **targetSdk=28**：因 PRoot 需在 app 可写目录执行二进制（Android 10+ W^X 禁止），锁定 API 28，无法上架 Google Play，无法享受 API 29+ 默认安全加固
- **签名密钥**：release 签名 keystore 当前随仓库公开（维护者已知情决策），如未来对公开分发敏感应改用 CI secrets
- **PRoot 隔离**：PRoot 仅提供路径重映射，非完整 namespace/cgroups 容器隔离

## 安全开发规范

- 所有密钥/凭据相关代码必须 fail-close，禁止静默降级
- passphrase、DEK 等敏感字节使用后必须 `fill(0)` 擦除
- 日志中绝不输出密钥、密码、token 内容
- 公共函数必须有 KDoc 注释
- 异常处理明确，不吞异常（除非有明确的恢复策略并注释说明）
- 核心安全模块必须有单元测试覆盖

## 依赖安全

- 项目使用 `dependency-audit.yml` CI 工作流进行依赖审计
- 建议定期更新关键依赖：BouncyCastle、OkHttp、Ktor、SQLCipher
- 第三方 native 库（`libtermux.so`）应记录来源和编译选项
