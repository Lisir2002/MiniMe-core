package com.mini.me_core.datalayer.encryption

import java.io.File

/**
 * 数据库「删库重建空库」决策策略。
 *
 * 背景：过去 [EncryptedDriverFactory] 把「连接态/密钥态瞬时错误」（passphrase cleared、
 * SQLCipher 未正确 attach key、临时锁等）也判为库损坏，直接删库重建空库——一次瞬时故障
 * 就可能让用户整库数据被清空。现拆分为：
 *  - 只有**真正的文件级损坏**（file is not a database / malformed）才允许考虑重建；
 *  - 是否真的删库，必须经本策略显式批准；默认 [DenyingRebuildPolicy] 拒绝任何自动删库，
 *    仅做「先快照再隔离」（把坏文件备份到 corrupted_backup），由用户/上层确认后再重建。
 */
interface DatabaseRebuildPolicy {

    /**
     * 决策：在打开 [dbFile] 失败、且判定为**真实文件损坏**时，是否允许删库并用同一密钥重建空库。
     *
     * 返回 true 才会执行 deleteDatabaseFiles + 重开；返回 false 则保留坏文件（已快照）、
     * 直接向上抛异常，绝不自动清空。
     */
    fun allowAutoRebuild(dbFile: File, error: Exception): Boolean
}

/**
 * 默认策略：拒绝自动删库重建（fail-closed）。
 *
 * 任何「删库重建空库」都必须由用户显式确认或上层注入允许策略。本实现只保证坏文件被快照隔离，
 * 不主动删除。
 */
class DenyingRebuildPolicy : DatabaseRebuildPolicy {
    override fun allowAutoRebuild(dbFile: File, error: Exception): Boolean = false
}
