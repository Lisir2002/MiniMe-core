# MiniMe-core 数据库加密全新架构设计

> 版本：v1.1
> 日期：2026-09-28（v1.0）；2026-09-29（v1.1 按实现修订）
> 状态：**已实现**（随 v0.0.0.37 全部落地；有 2 处主动偏离，见 §12 对照）
>
> v1.1 修订说明：原设计里「迁移改用逐表事务拷贝」「数据库新增零修改核心代码」两条
> 与最终实现不同，已在 §4.4 / §6.1 / §8 / §12 标注实际做法与理由。**以代码为准。**

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
3. **插件化注册**：数据库通过注册表注册（实现为**半插件化**，见 §6.1）
4. **简化升级**：旧版升级用 `sqlcipher_export` 整体搬运，所有返回结果行的语句走 `rawExecSQL`
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
| KeyRotationMigrator | 密钥形态迁移（明文→加密；入参为旧/新口令 provider，轮换只换 provider） |
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

### 4.4 KeyRotationMigrator（密钥形态迁移引擎，原 LegacyMigrationEngine）

> 现行实现见 `datalayer/encryption/KeyRotationMigrator.kt`：以 `sqlcipher_export` 为主体，
> 形态差异收敛为「旧口令 provider / 新口令 provider」两个入参（明文→加密即 `PLAIN` → `dekProvider`）。

> ⚠️ **本节已按实现修订（原设计写的是「逐表事务拷贝」）**。实际采用
> `ATTACH + sqlcipher_export`，语句一律用 `rawExecSQL` 执行。理由见下方「为什么最终选
> `sqlcipher_export`」。这是本文档唯一一处**实现主动偏离设计、且经评审认为代码更优**的地方。

#### 设计思路

形态差异被收敛成两个入参 —— `PassphraseProvider from`（旧形态）/ `to`（新形态）：
明文→加密即 `PLAIN`（空口令）→ `dekProvider`；未来 DEK 轮换只是换一对 provider，**迁移主体不动**。

```
1. 用 from 口令打开源库（打不开 = 它不处于源形态 → 放弃迁移，不当损坏库重建）
   └─ 抓源库逐表行数快照（比对基准）
2. ATTACH 临时库（.enc.tmp）并附 to 口令
3. SELECT sqlcipher_export('encrypted')   ← 必须 rawExecSQL
4. DETACH
5. 关闭源库
6. 校验目标库：表数 > 0 **且逐表行数与源库快照完全一致**
7. 备份源形态库为 <name>.pre_enc.bak（永久保留，可人工恢复）
8. renameTo 临时库 → 主库名（原子替换）
9. 写入 migration_<dbId>_completed = true
```

**为什么最终选 `sqlcipher_export`（而不是原设计的逐表事务拷贝）**
- 原设计的归因不准确：error 100 "another row available" 的根因是**用 `execSQL` 执行返回结果行的语句**，
  不是 ATTACH/sqlcipher_export 本身。`rawExecSQL` 不消费结果行，天然规避。
- `sqlcipher_export` 由 Zetetic 官方实现，自动搬运 schema / 触发器 / 虚拟表 / 索引 / BLOB，
  边界情况远少于手写逐表拷贝（后者要自己处理 FTS5 虚拟表、外键顺序、ROWID 冲突、类型亲和性差异）。
- 代价是「校验」变得更重要，故第 6 步强制逐表 `COUNT(*)` 比对（见下）。

**第 6 步：数据校验（不可省略）**
- 只判「表数 > 0」挡不住「表在、数据没了」这种静默丢数据；
- 做法是 export 前抓源库 `表名 → COUNT(*)` 快照，export 后对目标库再抓一次逐表比对；
- 任一表缺失或行数不符 → 抛异常中止迁移，**源库原样保留**、临时文件删除、不标记完成；
- 少数虚拟表（contentless FTS5 等）拒绝 `COUNT(*)`，记 `n/a` 跳过，不判失败。

#### 关键安全保证

- **源形态不可识别即放弃**：源库用 from 口令打不开时不迁移、不标记完成，交给上层 UNREADABLE 分支处置
  （绝不把它当损坏库重建 —— 那会静默清空数据）
- **校验不过即中止**：行数比对不一致时保留源库与现场，可人工恢复
- **原子替换**：renameTo 在同一文件系统是原子操作
- **可回滚**：替换前备份源形态库为 `.pre_enc.bak`
- **幂等性**：升级完成后标记，重复调用直接跳过
- **失败不丢数据**：任何步骤失败源库仍在，下次启动可重试
- **失败可降级**：不抛异常不崩溃，记录失败原因，应用继续启动（源库保持原形态可用）

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
- 源形态库备份为 `<name>.pre_enc.bak`，**永久保留**（设计原定保留 7 天后清理，
  实现未做清理：多占一点空间换取「任何时候都能人工恢复」，比定时删除更安全；
  后续若确认需要清理，应改为「迁移成功 N 天后再删」而非无条件删）
- 回滚时人工用备份覆盖主库即可
- ⚠️ 没有「回滚到明文模式」的开关：新架构全库默认加密，不提供明文降级路径

## 6. 扩展性设计

### 6.1 新增数据库

> ⚠️ **本节已按实现修订**：「零修改核心代码」**未达成**（半插件化）。
> 新增一个库仍需改 3 处：`LibName`（含 `dbId`）、`BuiltinDatabases`、`DataLayerModule.SCHEMA_MAP`；
> `DatabasePathProvider` 自动跟随。标识唯一真源已统一为 `LibName.dbId`（与 `DatabaseDefinition.id` 相等，
> 有单测守门：`KeyIdentityConsistencyTest`）。彻底插件化留待后续把 `ConnectionPool` 改为按
> `DatabaseDefinition` 工作、把 `LibName` 降级为兼容别名。

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
- **DEK 存在但解不开 ≠ 不存在**：一律抛异常，绝不生成新 DEK 覆盖（覆盖 = 旧数据永久不可解）
- **数据库密钥禁止直接 `rotateDek`**：换 DEK 而库文件未重加密会让该库立即不可读，
  必须走 `KeyRotationMigrator`（`sqlcipher_export` 整体重加密）
- **`emergencyReset` 只清非 `db_*` 的 DEK**：清数据库 DEK = 6 个库全部不可读，语义上不应波及
- 迁移失败保留源形态库，可重试或回滚；迁移成功前必须过「逐表行数比对」
- 迁移状态持久化，崩溃后可恢复

## 8. 与现有代码对比

| 维度 | 现有架构 | 新架构 |
|------|----------|--------|
| MasterKey数量 | 2个（db + credential） | 1个（统一） |
| DEK管理 | 2套独立逻辑 | 1套统一管理 |
| 数据库注册 | 硬编码枚举 | 接口化注册表（`LibName.dbId` 为标识唯一真源） |
| 新增数据库 | 修改5+处核心代码 | 改 `LibName`+`BuiltinDatabases`+`SCHEMA_MAP`（半插件化，见 §6.1） |
| 迁移方式 | 明文/加密双轨 + 反复重试失败 | `sqlcipher_export` + `rawExecSQL` + 逐表行数校验 |
| 明文/加密 | 双轨制，动态路由 | 全加密，无明文模式 |
| 密钥损坏处理 | 生成新DEK覆盖（旧数据永久不可解） | fail-close 抛异常，绝不覆盖 |
| 迁移失败 | 重试3次永久失败 | 可无限重试，保留明文库 |
| 代码量 | ~1500行（6个文件） | ~800行（5个文件） |

## 9. 实施计划

### 阶段一：基础框架（1-2天） ✅ 已完成

1. 创建 `DatabaseDefinition` 接口和 `DatabaseRegistry`
2. 创建 `UnifiedKeyManager` 接口和Android实现
3. 创建 `EncryptedDriverFactory`
4. 单元测试：密钥管理、数据库注册

### 阶段二：迁移引擎（2-3天） ✅ 已完成

1. 实现 `KeyRotationMigrator`（由 `LegacyMigrationEngine` 泛化：`sqlcipher_export` + provider 入参）
2. 实现升级检测和状态追踪
3. 集成测试：明文→加密迁移、数据一致性校验
4. 边界测试：大库迁移、中断恢复、回滚

### 阶段三：集成替换（1-2天） ✅ 已完成

1. 将现有6个数据库改为注册式定义
2. 替换DataLayerModule中的依赖注入
3. 移除旧的DEKManager、AndroidDatabaseKeyProvider、CipherDriverFactory、RoutingDriverFactory、DbEncryptionMigrationEngine
4. 全量编译验证

### 阶段四：密钥迁移与兼容（1天） ✅ 已完成

1. 实现旧版DEK导入逻辑
2. 实现旧版MasterKey兼容解密
3. 升级测试：从旧版本升级数据完整性

### 阶段五：验证与发版（1天） ✅ 已完成

1. 全量测试：单元测试+集成测试+UI测试
2. 真机测试：升级迁移、密钥轮换、崩溃恢复
3. 发版（v0.0.0.36 正式版 / v0.0.0.37 补齐剩余设计项）

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

1. **消除"another row available"根因**：保留 `ATTACH + sqlcipher_export`，但所有返回结果行的语句一律用
   `rawExecSQL` 执行（根因是 `execSQL` 消费结果行的方式，不是这两条语句本身）。

2. **统一密钥管理**：合并两套密钥体系，减少重复代码，降低维护成本。

3. **插件化注册**：新增数据库零修改核心代码，为未来插件系统奠定基础。

4. **简化状态机**：消除明文/加密双轨制，所有数据库默认加密，逻辑更简单。

5. **自愈与恢复**：迁移失败可重试可回滚，密钥损坏有明确处理，不静默丢数据。

6. **开放扩展**：预留加密算法、密钥存储、插件数据库的扩展口。

---

## 12. 设计与实现对照（v1.1 补充）

| 设计条目 | 实现状态 | 说明 |
|---|---|---|
| DEK 损坏 fail-close（§2.1.5 自愈优先） | ✅ 已实现 | `UnifiedKeyManager` 用 `DekRead.Present/Absent/Broken` 区分「不存在」与「解不开」，后者一律抛异常 |
| 密钥轮换保留旧 DEK（§4.2 决策 3） | ✅ 已实现（按用途分类） | `db_*` 直接拒绝轮换（须走 `KeyRotationMigrator`）；其余用途保留 `dek_<purpose>_prev`，`decrypt` 自动回退 |
| 迁移后数据校验（§4.4 第 4 步） | ✅ 已实现 | 逐表 `COUNT(*)` 前后比对（抽样比对未做：行数一致 + `sqlcipher_export` 官方实现已足够，性价比低） |
| 明文库备份保留 7 天（§5.3） | ❌ 未实现（有意保留现状） | `.pre_enc.bak` 永久保留，比定时删除更安全；如需清理应改为「成功 N 天后删」 |
| `EncryptionProvider` / `KeyStoreProvider`（§6.2/§6.3） | ❌ 未实现 | 纯预留扩展口，当前只支持 SQLCipher + Android Keystore |
| 迁移用逐表事务拷贝（§4.4 原设计） | ⚠️ **偏离**：改用 `sqlcipher_export` | 经评审认为代码更优，理由见 §4.4 |
| 新增数据库零修改核心代码（§6.1） | ⚠️ **偏离**：半插件化 | 标识已统一为 `LibName.dbId`，但仍需改 3 处，理由见 §6.1 |
