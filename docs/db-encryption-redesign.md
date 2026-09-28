# MiniMe-core 数据库加密全新架构设计

> 版本：v1.0
> 日期：2026-09-28
> 状态：设计评审中

## 1. 问题诊断

### 1.1 当前架构问题

经过多版本修复，现有数据库加密架构仍存在以下根本性问题：

| 问题 | 根因 | 影响 |
|------|------|------|
| 迁移反复失败 | SQLCipher ATTACH/sqlcipher_export/PRAGMA 语句返回结果行未消费，error 100 "another row available" | 升级后数据库无法加密，重试3次永久失败 |
| V2解密失败 | MasterKey被重置或DEK密文损坏，GCM tag校验失败 | 凭据数据丢失，API Key清空 |
| 密钥管理分散 | DEKManager（凭据）和AndroidDatabaseKeyProvider（数据库）各自管理MasterKey，两个独立alias | 逻辑重复，维护成本高，一致性难保证 |
| 可扩展性差 | LibName硬编码枚举，迁移引擎硬编码6个Schema | 新增数据库需修改多处核心代码 |
| 明文/加密双轨 | RoutingDriverFactory动态切换，迁移状态机复杂 | 崩溃恢复复杂，边界情况多 |
| 无自愈能力 | 密钥损坏后直接降级返回空串 | 数据静默丢失 |

### 1.2 "another row available" 根因深度分析

SQLite error 100 (SQLITE_ROW) 表示执行语句后有结果行未消费。SQLCipher中以下语句都会返回结果行：
- `ATTACH DATABASE ... KEY ...`
- `SELECT sqlcipher_export(...)`
- `PRAGMA schema.user_version = ...`
- `DETACH DATABASE`

当前方案用 `executeAndDrain()` 包装，但在某些边界情况下（如ATTACH失败、连接状态异常）仍会残留结果行，导致后续操作失败。已迭代到v7迁移逻辑版本，说明这是架构性问题，不是简单bug。

## 2. 设计目标

### 2.1 核心原则

1. **一次性加密，永久加密**：新安装直接创建加密数据库，消除明文→加密迁移需求
2. **统一密钥管理**：单一MasterKey，统一管理所有DEK，消除重复逻辑
3. **插件化注册**：数据库通过注册表注册，新增数据库零修改核心代码
4. **简化升级**：旧版升级使用更可靠的逐表事务拷贝，避免ATTACH/sqlcipher_export
5. **自愈优先**：密钥损坏时有恢复机制，不轻易丢失数据
6. **开放扩展**：预留新增数据库、新增加密算法、新增密钥存储的出入口

### 2.2 非目标

- 不改变SQLCipher作为底层加密引擎（成熟稳定，AES-256-CBC + HMAC-SHA512）
- 不引入端到端加密（用户密码加密），保持当前设备级加密模型
- 不做跨设备密钥同步（超出当前范围）

## 3. 新架构总览

### 3.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  (Repository / DAO / SQLDelight Queries)                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│              DatabaseRegistry (数据库注册表)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ AgentDb  │ │CredsDb   │ │SettingsDb│ │WorkspaceDb│ ...   │
│  │(注册)     │ │(注册)     │ │(注册)     │ │(注册)      │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│           EncryptedDatabaseManager (加密数据库管理器)          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  getDriver(dbName) → SqlDriver                      │    │
│  │  - 检查加密状态                                       │    │
│  │  - 如需升级：执行 LegacyMigration                    │    │
│  │  - 创建加密驱动                                      │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
│ UnifiedKey   │  │ Legacy       │  │ Encrypted    │
│ Manager      │  │ Migration    │  │ DriverFactory│
│ (统一密钥管理)│  │ (旧版升级迁移)│  │ (加密驱动工厂)│
└──────────────┘  └──────────────┘  └──────────────┘
        │
┌───────▼──────┐
│ Android      │
│ Keystore     │
│ (MasterKey)  │
└──────────────┘
```

### 3.2 模块职责

| 模块 | 职责 |
|------|------|
| DatabaseRegistry | 数据库定义注册，运行时查询，支持插件扩展 |
| UnifiedKeyManager | 统一密钥管理，MasterKey + 所有DEK，支持轮换/备份/恢复 |
| EncryptedDatabaseManager | 数据库生命周期管理，驱动创建，升级触发 |
| LegacyMigrationEngine | 旧版明文数据库升级加密（仅升级时使用一次） |
| EncryptedDriverFactory | SQLCipher加密驱动创建，统一passphrase编码 |

## 4. 核心模块详细设计

### 4.1 DatabaseRegistry（数据库注册表）

#### 设计思路

将硬编码的 `LibName` 枚举改为接口化注册，每个数据库实现 `DatabaseDefinition` 接口，在应用启动时注册到注册表。

#### 接口定义

```kotlin
/**
 * 数据库定义接口。
 * 每个数据库实现此接口，注册到 DatabaseRegistry。
 * 新增数据库只需实现此接口并注册，无需修改核心代码。
 */
interface DatabaseDefinition {
    /** 数据库唯一标识（用于DEK存储、日志、状态追踪） */
    val id: String

    /** 物理文件名（数据契约，不可随意更改） */
    val fileName: String

    /** SQLDelight Schema */
    val schema: SqlSchema<QueryResult.Value<Unit>>

    /** 数据库版本（用于升级检测） */
    val version: Int

    /** 是否需要加密（默认全部加密） */
    val encryptionRequired: Boolean
        get() = true

    /** 数据库描述（用于日志和调试） */
    val description: String
        get() = id
}
```

#### 注册表实现

```kotlin
/**
 * 数据库注册表。
 * 应用启动时注册所有数据库，运行时通过id查询。
 * 支持插件动态注册（预留扩展口）。
 */
class DatabaseRegistry {
    private val definitions = mutableMapOf<String, DatabaseDefinition>()
    private val lock = Any()

    /** 注册数据库定义（启动时调用，或插件加载时调用） */
    fun register(definition: DatabaseDefinition) {
        synchronized(lock) {
            require(!definitions.containsKey(definition.id)) {
                "数据库重复注册: ${definition.id}"
            }
            definitions[definition.id] = definition
        }
    }

    /** 根据id获取数据库定义 */
    fun get(id: String): DatabaseDefinition? =
        synchronized(lock) { definitions[id] }

    /** 获取所有已注册数据库 */
    fun getAll(): List<DatabaseDefinition> =
        synchronized(lock) { definitions.values.toList() }

    /** 检查数据库是否已注册 */
    fun isRegistered(id: String): Boolean =
        synchronized(lock) { definitions.containsKey(id) }
}
```

#### 内置数据库注册

```kotlin
// 内置数据库定义（示例）
object AgentDatabase : DatabaseDefinition {
    override val id = "agent"
    override val fileName = "minime_agent_v3.db"
    override val schema = AgentDb.Schema
    override val version = 3
    override val description = "会话与Agent工作流数据库"
}

object CredentialsDatabase : DatabaseDefinition {
    override val id = "credentials"
    override val fileName = "minime_credentials_v2.db"
    override val schema = CredentialsDb.Schema
    override val version = 2
    override val description = "凭据与密钥数据库"
}

// ... 其他数据库

// 启动时注册
fun registerBuiltinDatabases(registry: DatabaseRegistry) {
    registry.register(AgentDatabase)
    registry.register(CredentialsDatabase)
    registry.register(SettingsDatabase)
    registry.register(WorkspaceDatabase)
    registry.register(T2iDatabase)
    registry.register(InfraDatabase)
}
```

### 4.2 UnifiedKeyManager（统一密钥管理器）

#### 设计思路

合并现有 `DEKManager` 和 `AndroidDatabaseKeyProvider`，统一管理：
- 单一MasterKey（Android Keystore，alias: `minime_master_key`）
- 所有DEK（数据库DEK + 凭据DEK + 未来扩展）
- DEK存储在加密的SharedPreferences中（EncryptedSharedPreferences）
- 支持密钥轮换、备份、恢复

#### 密钥层级

```
Android Keystore (硬件-backed TEE/StrongBox)
  └── MasterKey ("minime_master_key", AES-256-GCM, 不出Keystore)
        └── EncryptedSharedPreferences (Jetpack Security, 用MasterKey加密)
              ├── dek_agent → Base64(IV + AES-GCM(DEK_agent))
              ├── dek_credentials → Base64(IV + AES-GCM(DEK_credentials))
              ├── dek_settings → ...
              ├── dek_credentials_field → ... (凭据字段加密DEK)
              └── dek_<future> → ... (预留扩展)
```

#### 接口定义

```kotlin
/**
 * 统一密钥管理器。
 * 管理MasterKey和所有DEK，提供加密/解密/轮换/恢复能力。
 */
interface UnifiedKeyManager {
    /** 获取或创建指定用途的DEK（32字节AES-256） */
    suspend fun getOrCreateDek(purpose: String): ByteArray

    /** 获取已存在的DEK，不存在返回null */
    suspend fun getDek(purpose: String): ByteArray?

    /** 轮换指定用途的DEK（旧DEK保留用于解密旧数据，新数据用新DEK） */
    suspend fun rotateDek(purpose: String): ByteArray

    /** 检查DEK是否已初始化 */
    fun isDekInitialized(purpose: String): Boolean

    /** 用指定DEK加密数据，返回Base64密文 */
    suspend fun encrypt(purpose: String, plaintext: ByteArray): String

    /** 用指定DEK解密数据，失败抛出异常 */
    suspend fun decrypt(purpose: String, ciphertextB64: String): ByteArray

    /** MasterKey是否可用 */
    fun isMasterKeyAvailable(): Boolean

    /** 紧急重置（清除所有DEK，加密数据将不可读） */
    suspend fun emergencyReset()
}
```

#### 关键设计决策

1. **EncryptedSharedPreferences存储DEK**：使用Jetpack Security的EncryptedSharedPreferences，自动用MasterKey加密键值，比手动AES-GCM更可靠，减少自定义加密代码出错概率。

2. **DEK用途命名空间**：`purpose` 参数区分不同用途的DEK，如 `db_agent`、`db_credentials`、`field_api_key`。新增用途无需修改代码。

3. **密钥轮换保留旧DEK**：轮换时旧DEK不删除，用于解密历史数据，新数据用新DEK。DEK存储格式支持版本号：`v2:<base64>`。

4. **MasterKey容错**：MasterKey获取失败时，重试3次（间隔100ms），仍失败则抛异常，不静默降级。

### 4.3 EncryptedDatabaseManager（加密数据库管理器）

#### 设计思路

统一管理所有加密数据库的生命周期：
- 首次创建：直接创建加密数据库
- 升级检测：检测旧版文明文数据库，触发LegacyMigration
- 驱动创建：统一使用SQLCipher加密驱动
- 状态追踪：记录每个数据库的加密状态

#### 接口定义

```kotlin
/**
 * 加密数据库管理器。
 * 统一管理所有数据库的创建、升级、驱动获取。
 */
class EncryptedDatabaseManager(
    private val context: Context,
    private val registry: DatabaseRegistry,
    private val keyManager: UnifiedKeyManager,
    private val migrationEngine: LegacyMigrationEngine,
) {
    /**
     * 获取指定数据库的SqlDriver。
     * 如检测到旧版文明文数据库，先执行升级加密。
     * 所有数据库默认加密，不再有明文/加密路由选择。
     */
    suspend fun getDriver(dbId: String): SqlDriver {
        val definition = registry.get(dbId)
            ?: throw IllegalArgumentException("数据库未注册: $dbId")

        // 检测并执行旧版升级（仅第一次，后续跳过）
        if (migrationEngine.needsMigration(definition)) {
            migrationEngine.migrateToEncrypted(definition)
        }

        // 创建加密驱动
        return createEncryptedDriver(definition)
    }

    private suspend fun createEncryptedDriver(definition: DatabaseDefinition): SqlDriver {
        val dek = keyManager.getOrCreateDek("db_${definition.id}")
        val passphrase = encodePassphrase(dek)
        dek.fill(0)
        try {
            return AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = definition.fileName,
                factory = SupportFactory(passphrase.toByteArray(Charsets.UTF_8)),
            )
        } finally {
            passphrase.toByteArray().fill(0)
        }
    }

    private fun encodePassphrase(dek: ByteArray): String =
        Base64.encodeToString(dek, Base64.NO_WRAP)
}
```

### 4.4 LegacyMigrationEngine（旧版升级迁移引擎）

#### 设计思路

这是本次重构的核心改进。放弃使用 `ATTACH + sqlcipher_export` 的复杂方案，改用更简单可靠的：

1. 打开明文数据库（系统SQLite，非SQLCipher）
2. 创建新的加密数据库（SQLCipher）
3. 在一个事务中，逐表拷贝schema和数据
4. 校验数据一致性
5. 原子替换文件
6. 失败时保留明文库，可回滚

**为什么不用ATTACH+sqlcipher_export？**
- ATTACH在SQLCipher中行为复杂，容易出现"another row available"
- sqlcipher_export是表值函数，返回结果行必须消费
- PRAGMA带schema前缀也会返回结果行
- 逐表拷贝虽然代码多一点，但逻辑简单，边界情况少，可靠性高

#### 迁移流程

```
1. 检查明文库是否存在
   ├─ 不存在 → 跳过（新安装或已升级）
   └─ 存在 → 继续

2. 创建加密临时库（.enc.tmp）
   └─ 用SQLCipher创建空库，执行schema.create

3. 开启事务，逐表拷贝
   ├─ 获取所有用户表名（排除sqlite_系统表）
   ├─ 对每个表：
   │   ├─ 从明文库读取所有行
   │   └─ 批量插入加密库（INSERT OR REPLACE）
   └─ 提交事务

4. 数据校验
   ├─ 行数比对
   └─ 抽样数据比对

5. 原子替换
   ├─ 删除明文库sidecar（-wal/-shm）
   ├─ renameTo加密临时库 → 主库名
   └─ 删除明文库备份

6. 标记升级完成
   └─ 写入SharedPreferences: migration_<dbId>_completed = true
```

#### 关键安全保证

- **主库全程只读**：迁移过程中明文库只读取，不修改
- **事务原子性**：数据拷贝在一个事务中，失败回滚不影响临时库
- **原子替换**：renameTo在同一文件系统是原子操作
- **可回滚**：替换前保留明文库备份，替换失败可恢复
- **幂等性**：升级完成后标记，重复调用直接跳过
- **失败不丢数据**：任何步骤失败，明文库仍在，下次启动可重试

### 4.5 EncryptedDriverFactory（加密驱动工厂）

简化现有CipherDriverFactory，移除明文/加密路由逻辑，只负责创建加密驱动。

```kotlin
class EncryptedDriverFactory(
    private val context: Context,
    private val keyManager: UnifiedKeyManager,
) {
    suspend fun create(definition: DatabaseDefinition): SqlDriver {
        val dek = keyManager.getOrCreateDek("db_${definition.id}")
        val passphraseBytes = Base64.encodeToString(dek, Base64.NO_WRAP)
            .toByteArray(Charsets.UTF_8)
        dek.fill(0)
        try {
            return AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = definition.fileName,
                factory = SupportFactory(passphraseBytes),
            )
        } catch (e: UnsatisfiedLinkError) {
            throw DatabaseEncryptionException("SQLCipher原生库加载失败: ${definition.id}", e)
        } catch (e: Exception) {
            throw DatabaseEncryptionException("加密数据库打开失败: ${definition.id}", e)
        } finally {
            passphraseBytes.fill(0)
        }
    }
}
```

## 5. 旧数据迁移方案

### 5.1 升级路径

用户从旧版本（v0.0.0.30及之前）升级到新版本时：

1. **首次启动检测**：EncryptedDatabaseManager初始化时，检查每个数据库是否需要迁移
2. **按需迁移**：只有当数据库被访问时才触发迁移（懒加载，避免启动时一次性迁移所有库导致卡顿）
3. **迁移进度**：迁移过程中显示进度（大库可能需要几秒）
4. **迁移完成**：标记完成，后续启动直接使用加密库

### 5.2 密钥迁移

旧版本有两套密钥体系：
- `minime_db_master` + per-DB DEK（数据库加密）
- `minime_credential_masterkey` + DEK（凭据字段加密）

升级时：
1. 尝试读取旧版DEK（如果能解密成功）
2. 将旧DEK导入新的UnifiedKeyManager（用新MasterKey重新包裹）
3. 如果旧DEK解密失败（MasterKey已重置），生成新DEK，数据需要重新输入
4. 旧版MasterKey保留一段时间用于解密旧数据，后续可清理

### 5.3 回滚方案

如果升级后出现严重问题：
- 明文库备份保留7天（可配置）
- 提供开发者选项：回滚到明文模式（仅调试用）
- 回滚时将加密库数据导回明文库

## 6. 扩展性设计

### 6.1 新增数据库

新增数据库只需3步：
1. 实现 `DatabaseDefinition` 接口
2. 在启动时调用 `registry.register(...)`
3. 通过 `encryptedDatabaseManager.getDriver("new_db_id")` 获取驱动

无需修改：
- 密钥管理（自动创建新DEK）
- 驱动工厂（通用实现）
- 迁移引擎（通用实现）
- 状态追踪（自动注册）

### 6.2 新增加密算法

预留 `EncryptionProvider` 接口，未来可支持：
- SQLCipher（当前默认）
- SQLCipher + 自定义KDF迭代次数
- 其他加密引擎（如libsodium）

```kotlin
interface EncryptionProvider {
    val id: String
    fun createSupportFactory(passphrase: ByteArray): SupportFactory
}
```

### 6.3 新增密钥存储

预留 `KeyStoreProvider` 接口，未来可支持：
- Android Keystore（当前默认）
- StrongBox（硬件安全模块）
- 生物识别绑定密钥
- 用户密码派生密钥

### 6.4 插件化支持

数据库注册表支持运行时动态注册，未来插件系统可：
- 插件自带数据库定义
- 插件加载时注册数据库
- 插件卸载时清理数据库（可选）

## 7. 安全保证

### 7.1 密钥安全

- MasterKey存储在Android Keystore，不可导出，硬件-backed
- DEK用MasterKey加密后存储在EncryptedSharedPreferences
- DEK明文仅在内存中存在，使用后立即fill(0)擦除
- passphrase使用后立即擦除（SQLCipher clearPassphrase + 手动fill(0)双重保险）
- 日志中不输出任何密钥内容

### 7.2 数据安全

- 全库透明加密（AES-256-CBC + HMAC-SHA512，SQLCipher默认）
- 每个数据库独立DEK，一个库密钥泄露不影响其他库
- 数据库文件存储在应用私有目录，普通用户不可访问
- 迁移过程中主库只读，不损坏原数据

### 7.3 故障安全

- fail-close：加密驱动创建失败抛异常，绝不回退明文
- 迁移失败保留明文库，可重试或回滚
- 密钥损坏时有明确错误提示，不静默降级返回空数据
- 迁移状态持久化，崩溃后可恢复

## 8. 与现有代码对比

| 维度 | 现有架构 | 新架构 |
|------|----------|--------|
| MasterKey数量 | 2个（db + credential） | 1个（统一） |
| DEK管理 | 2套独立逻辑 | 1套统一管理 |
| 数据库注册 | 硬编码枚举 | 接口化注册表 |
| 新增数据库 | 修改5+处核心代码 | 实现接口+注册 |
| 迁移方式 | ATTACH+sqlcipher_export（复杂易错） | 逐表事务拷贝（简单可靠） |
| 明文/加密 | 双轨制，动态路由 | 全加密，无明文模式 |
| 密钥损坏处理 | 降级返回空串（丢数据） | 抛异常+恢复机制 |
| 迁移失败 | 重试3次永久失败 | 可无限重试，保留明文库 |
| 代码量 | ~1500行（6个文件） | ~800行（5个文件） |

## 9. 实施计划

### 阶段一：基础框架（1-2天）

1. 创建 `DatabaseDefinition` 接口和 `DatabaseRegistry`
2. 创建 `UnifiedKeyManager` 接口和Android实现
3. 创建 `EncryptedDriverFactory`
4. 单元测试：密钥管理、数据库注册

### 阶段二：迁移引擎（2-3天）

1. 实现 `LegacyMigrationEngine`（逐表事务拷贝）
2. 实现升级检测和状态追踪
3. 集成测试：明文→加密迁移、数据一致性校验
4. 边界测试：大库迁移、中断恢复、回滚

### 阶段三：集成替换（1-2天）

1. 将现有6个数据库改为注册式定义
2. 替换DataLayerModule中的依赖注入
3. 移除旧的DEKManager、AndroidDatabaseKeyProvider、CipherDriverFactory、RoutingDriverFactory、DbEncryptionMigrationEngine
4. 全量编译验证

### 阶段四：密钥迁移与兼容（1天）

1. 实现旧版DEK导入逻辑
2. 实现旧版MasterKey兼容解密
3. 升级测试：从旧版本升级数据完整性

### 阶段五：验证与发版（1天）

1. 全量测试：单元测试+集成测试+UI测试
2. 真机测试：升级迁移、密钥轮换、崩溃恢复
3. 发版（v0.0.0.31）

## 10. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 迁移过程中App被杀死 | 中 | 临时文件残留 | 启动时检测清理，幂等设计 |
| 旧版DEK解密失败 | 低 | 凭据数据丢失 | 提示用户重新输入，保留旧库可回滚 |
| SQLCipher原生库加载失败 | 极低 | 数据库无法打开 | 捕获UnsatisfiedLinkError，明确提示 |
| 大库迁移时间长 | 中 | 用户等待 | 显示进度，后台执行，可取消 |
| EncryptedSharedPreferences兼容性 | 低 | 密钥存储失败 | 降级到手动AES-GCM（已有实现） |

## 11. 总结

新架构的核心改进：

1. **消除"another row available"根因**：放弃ATTACH+sqlcipher_export，改用逐表事务拷贝，从根本上避免SQLCipher结果行未消费问题。

2. **统一密钥管理**：合并两套密钥体系，减少重复代码，降低维护成本。

3. **插件化注册**：新增数据库零修改核心代码，为未来插件系统奠定基础。

4. **简化状态机**：消除明文/加密双轨制，所有数据库默认加密，逻辑更简单。

5. **自愈与恢复**：迁移失败可重试可回滚，密钥损坏有明确处理，不静默丢数据。

6. **开放扩展**：预留加密算法、密钥存储、插件数据库的扩展口。
