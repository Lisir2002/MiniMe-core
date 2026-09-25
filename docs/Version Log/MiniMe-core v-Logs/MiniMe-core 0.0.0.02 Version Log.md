# MiniMe-core 0.0.0.2 Version Log

> 安全加固版本：落地数据库全库加密基础设施（P1 阶段），修复 SQLCipher 空实现与加密失败静默降级两项 P0 安全缺陷。默认仍为明文，用户可在设置页手动开启加密；P2/P3 阶段将逐步推进默认加密与强制加密。

**发布日期**：2026-09-22

### 新功能

- 数据库全库加密支持（SQLCipher 4.5.4）：6 个物理库可选 AES-256 加密，默认明文，设置页一键开启后自动迁移全部存量数据。
- 数据库加密迁移引擎：明文↔加密无损迁移，7 步流程（迁移前快照 → 类型感知逐表拷贝 → 行数+校验和+抽样三重校验 → renameTo 原子替换 → 清理），支持反向迁移（加密→明文）。
- 迁移崩溃恢复：启动时自动检测迁移中断状态并安全回滚，覆盖快照/拷贝/校验/替换全流程 10 个崩溃时间点，操作仅限文件系统不打开数据库（< 100ms）。
- 双层密钥管理：Android Keystore MasterKey（AES-256-GCM，硬件-backed）包裹每库独立 DEK，包裹密文存 SharedPreferences，密钥不落明文、不出 Keystore。
- 新增 `SECURITY.md` 安全策略文档。

### 改进

- 数据层 L0 引擎新增 `RoutingDriverFactory`：根据每库加密状态动态选择明文/加密驱动，业务层、迁移引擎、备份全部无感知。
- Application 启动流程集成 `CrashRecovery`：首次数据库访问前执行崩溃恢复，确保迁移中断后数据不损坏。
- 安全设置页新增「数据库加密」区块：加密状态显示、开启/关闭开关、迁移进度条（当前库/当前表/百分比）、失败重试按钮。
- 新增 `net.zetetic:android-database-sqlcipher:4.5.4` 依赖与 ProGuard keep 规则。
- CI 新增 `CipherDriverFactory` 空实现静态检测门禁，阻止加密占位实现合入。

### 安全

- 修复 P0：SQLCipher 加密为空实现 — `CipherDriverFactory.create()` 从 `error()` 占位改为完整实现（`SupportFactory` + fail-close + passphrase 用后 `fill(0)` 内存擦除）。
- 修复 P0：加密初始化失败静默降级明文 — 密钥管理严格 fail-close，密钥获取/驱动创建任何环节失败抛 `DatabaseEncryptionException`，绝不回退明文或写空串。
- 修正 `README.md` / `AGENTS.md` 中「SQLCipher 加密读写」虚假声明，改为真实状态描述（P1 可选加密，默认明文）。
