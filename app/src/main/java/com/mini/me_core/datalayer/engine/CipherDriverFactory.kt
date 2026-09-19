package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.AuxDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb
import net.sqlcipher.database.SupportFactory

/**
 * SQLCipher 全盘加密驱动工厂（设计 §8 / §12.2）—— 已真实接线。
 *
 * 四个物理库（AgentDb / CredentialsDb / WorkspaceDb / AuxDb）全部走本驱动，无明文 driver。
 *
 * 密钥：[SqlCipherKeyManager] 提供独立 32 字节随机 DB passphrase（Android Keystore 包装），
 *       首启生成、后续读取；不复用字段级 DEK、禁止硬编码、禁止用户密码派生。
 *
 * 存量迁移（明文库 → 加密库，事务化）：
 *   由 [SqlCipherMigration] 在 driver 打开前执行——
 *   「快照 → 开明文库导出 → 建加密库导入 → PRAGMA quick_check 校验 → 替换文件」；
 *   任一步失败自动 [MigrationEngine.restoreSnapshot] 回退，不破坏 targetSdk=28 行为。
 *
 * 接线方式：DataLayerModule.provideDriverFactory 由 PlainDriverFactory 换成本类；
 * 业务/迁移/备份代码只依赖 [DatabaseDriverFactory]，无感。
 */
class CipherDriverFactory(
    private val context: Context,
    @Suppress("unused") private val pathProvider: DatabasePathProvider,
    private val keyManager: SqlCipherKeyManager,
) : DatabaseDriverFactory {

    override fun create(lib: LibName): SqlDriver {
        val factory = SupportFactory(keyManager.getOrCreateKey())
        val name = lib.fileName
        return when (lib) {
            LibName.AGENT -> AndroidSqliteDriver(AgentDb.Schema, context, name, factory)
            LibName.CREDENTIALS -> AndroidSqliteDriver(CredentialsDb.Schema, context, name, factory)
            LibName.WORKSPACE -> AndroidSqliteDriver(WorkspaceDb.Schema, context, name, factory)
            LibName.AUX -> AndroidSqliteDriver(AuxDb.Schema, context, name, factory)
        }
    }
}
