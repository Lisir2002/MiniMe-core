package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.AuxDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb

/**
 * 明文驱动工厂（自测期生效，设计 §8 / §12.2）。
 * 每个库经 AndroidSqliteDriver + FrameworkSQLiteOpenHelperFactory 创建（WAL + 单写者由 SQLDelight/SQLite 保证）。
 */
class PlainDriverFactory(
    private val context: Context,
    private val pathProvider: DatabasePathProvider,
) : DatabaseDriverFactory {

    private val helperFactory = FrameworkSQLiteOpenHelperFactory()

    override fun create(lib: LibName): SqlDriver {
        val name = lib.fileName
        return when (lib) {
            LibName.AGENT -> AndroidSqliteDriver(AgentDb.Schema, context, name, helperFactory)
            LibName.CREDENTIALS -> AndroidSqliteDriver(CredentialsDb.Schema, context, name, helperFactory)
            LibName.WORKSPACE -> AndroidSqliteDriver(WorkspaceDb.Schema, context, name, helperFactory)
            LibName.AUX -> AndroidSqliteDriver(AuxDb.Schema, context, name, helperFactory)
        }
    }
}
