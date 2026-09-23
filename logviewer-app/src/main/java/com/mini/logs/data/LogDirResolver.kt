package com.mini.logs.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 日志目录解析器。动态计算主应用可能写入日志的两个位置，不写死路径。
 *
 * 与主应用 FileLogger.resolveLogDir() 逻辑保持一致：
 * 1. 公共外部存储：Documents/MiniMe-core/logs/（主应用有权限时写入）
 * 2. 外部私有目录：Android/data/com.mini.me_core/files/logs/（主应用无权限时回退）
 *
 * 注意：主应用的第三层回退是内部存储 context.filesDir/logs/，
 * 其他应用无法读取，因此附属应用不支持该路径。
 */
object LogDirResolver {

    /** 主应用包名，用于构建私有目录路径。 */
    private const val HOST_APP_PACKAGE = "com.mini.me_core"

    /** 与主应用 LogConfig.DEFAULT_PUBLIC_ROOT_DIR 一致。 */
    private const val PUBLIC_ROOT_DIR = "MiniMe-core"

    /** 与主应用 LogConfig.DEFAULT_LOG_SUBDIR 一致。 */
    private const val LOG_SUBDIR = "logs"

    /**
     * 日志来源枚举。
     */
    enum class LogSource {
        /** 自动检测：两个目录都扫描，合并去重。 */
        AUTO,
        /** 仅外部公共存储（Documents/MiniMe-core/logs/）。 */
        EXTERNAL_PUBLIC,
        /** 仅主应用外部私有目录（Android/data/com.mini.me_core/files/logs/）。 */
        APP_PRIVATE,
    }

    /**
     * 获取公共外部存储日志目录。
     * 等价于主应用：Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)/MiniMe-core/logs/
     */
    @Suppress("DEPRECATION") // targetSdk=28 下仍可用
    fun getPublicDir(context: Context): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(base, PUBLIC_ROOT_DIR), LOG_SUBDIR)
    }

    /**
     * 获取主应用外部私有目录。
     * 等价于主应用：context.getExternalFilesDir(null)/logs/（但用主应用包名构建路径）。
     */
    fun getPrivateDir(context: Context): File {
        // 主应用的外部私有目录：/storage/emulated/0/Android/data/com.mini.me_core/files/logs/
        val externalStorage = Environment.getExternalStorageDirectory()
        return File(
            File(File(externalStorage, "Android/data"), HOST_APP_PACKAGE),
            "files/$LOG_SUBDIR"
        )
    }

    /**
     * 根据用户选择的来源，返回需要扫描的目录列表。
     */
    fun getDirsForSource(context: Context, source: LogSource): List<File> {
        return when (source) {
            LogSource.AUTO -> listOf(getPublicDir(context), getPrivateDir(context))
            LogSource.EXTERNAL_PUBLIC -> listOf(getPublicDir(context))
            LogSource.APP_PRIVATE -> listOf(getPrivateDir(context))
        }
    }

    /**
     * 检查目录是否存在且有日志文件。
     */
    fun hasLogs(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt")
        }?.isNotEmpty() == true
    }

    /**
     * 获取目录的显示名称。
     */
    fun getDirDisplayName(dir: File): String {
        return dir.absolutePath
    }
}
