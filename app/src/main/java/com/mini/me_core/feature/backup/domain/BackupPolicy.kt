package com.mini.me_core.feature.backup.domain

import java.io.InputStream

/**
 * 恢复模式：决定备份中的数据如何与当前数据合并。
 *
 * - [MERGE]：现有 upsert 语义——按主键覆盖更新同 ID 记录，新增记录插入，**不删除**当前已有但备份中不存在的数据。
 * - [OVERWRITE]：先清空备份中包含的数据域（仅对应域，不动其他域），再整段插入。
 *   用于「整包替换当前状态」；仅清空备份实际携带内容的域，避免误伤其他数据。
 */
enum class RestoreMode { MERGE, OVERWRITE }

/**
 * 恢复预览：只解析备份包、不写入数据库，统计各数据域条数，并与当前库中条数对比。
 *
 * [backup] 为备份包内各域条数；[current] 为恢复前当前库中对应条数；
 * [hasChatHistory] 等标志位供 UI 决定展示哪些段。
 */
data class BackupPreview(
    val backup: RestoreStats,
    val current: RestoreStats,
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val encrypted: Boolean = false,
) {
    /** 备份是否携带聊天历史段。 */
    val hasChatHistory: Boolean get() = backup.chatSessions > 0 || backup.agentMessages > 0 || backup.todoItems > 0
}

/**
 * 备份完整性校验结果：导出后重新打开 tar 自检元数据与各 jsonl 可解析性。
 *
 * @param valid 包结构完整、metadata.json 存在且各 jsonl 可逐行解析。
 * @param entryCount tar 内条目数。
 * @param errorReason 校验失败原因（[valid]=false 时非空）。
 */
data class BackupIntegrity(
    val valid: Boolean,
    val entryCount: Int = 0,
    val errorReason: String? = null,
)

/**
 * 备份历史列表项：统一描述本机私有目录与外部公共目录的一份备份。
 *
 * @param epochMs 备份时间（epoch ms）。
 * @param sizeBytes 文件字节数。
 * @param source 来源（本机私有 / 外部安全区）。
 * @param fileName 文件名。
 * @param stats 该备份内各数据域条数（解析 metadata.json 得到；解析失败为 null）。
 * @param isSafetyPoint 是否为「恢复前自动安全点」（撤销用，不参与轮转删除）。
 */
data class BackupHistoryItem(
    val epochMs: Long,
    val sizeBytes: Long,
    val source: BackupSource,
    val fileName: String,
    val stats: RestoreStats?,
    val appVersion: String = "",
    val isSafetyPoint: Boolean = false,
)

/** 备份来源落点。 */
enum class BackupSource { LOCAL, EXTERNAL }

/**
 * 自动备份可配置项（持久化到 KVStore，重启不丢）。
 *
 * @param enabled 自动备份总开关。
 * @param keepMax 保留份数（3/5/7/10）。
 * @param intervalDays 周期性备份间隔天数；<=0 表示不周期性备份（仅升级前触发）。
 * @param backupOnUpgrade 升级前自动备份。
 * @param lastPeriodicBackupMs 上次周期性备份时间（epoch ms），用于判定是否到期。
 */
data class AutoBackupConfig(
    val enabled: Boolean = true,
    val keepMax: Int = 7,
    val intervalDays: Int = 0,
    val backupOnUpgrade: Boolean = true,
    val lastPeriodicBackupMs: Long = 0L,
) {
    companion object {
        val CHOICES_KEEP_MAX = listOf(3, 5, 7, 10)
        val CHOICES_INTERVAL_DAYS = listOf(0, 1, 3, 7, 14)
    }
}

/**
 * 周期性备份到期判定的**纯逻辑**（无 IO、无 Android 依赖，便于单元测试）。
 *
 * @param lastMs 上次周期性备份时间（epoch ms）；0 表示从未备份过。
 * @param intervalDays 配置的间隔天数；<=0 表示不启用周期备份。
 * @param nowMs 当前时间（epoch ms）。
 */
fun isPeriodicBackupDue(lastMs: Long, intervalDays: Int, nowMs: Long): Boolean {
    if (intervalDays <= 0) return false
    if (lastMs <= 0L) return true
    return nowMs - lastMs >= intervalDays * DAY_MS
}

internal const val DAY_MS = 24L * 60L * 60L * 1000L

/**
 * 备份口令强度评估的**纯逻辑**（无 Android 依赖，便于单元测试）。
 * 弱/中/强三档，供 UI 展示颜色与提示。
 */
enum class PasswordStrength { EMPTY, WEAK, MEDIUM, STRONG }

fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.EMPTY
    val length = password.length
    val hasLetter = password.any { it.isLetter() }
    val hasDigit = password.any { it.isDigit() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }
    var score = 0
    if (length >= 8) score++
    if (length >= 12) score++
    if (hasLetter && hasDigit) score++
    if (hasSymbol) score++
    return when {
        score >= 4 -> PasswordStrength.STRONG
        score >= 2 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.WEAK
    }
}
