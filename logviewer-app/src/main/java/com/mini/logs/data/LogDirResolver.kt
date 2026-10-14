package com.mini.logs.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 日志目录解析器：动态计算主应用可能放日志的位置，不写死路径。
 *
 * 与主应用（MiniMe-core）的实际行为对齐（此前这里是错位的，导致「查看器读不到日志」）：
 *
 * 1. **公共下载目录 `Download/MiniMe-core/logs/`（主）**
 *    主应用「导出日志」「崩溃后自动补导出」的实际目标位置
 *    （对应 FileLogger.exportRelativePath = "Download/${publicRootDir}/${logSubdir}"）。
 *    主应用每 12 小时会自动导出一次、崩溃后下次启动立即补导出，
 *    所以这是最可能拿到日志的地方，自动检测时排在最前。
 *
 * 2. **公共文档目录 `Documents/MiniMe-core/logs/`（兼容）**
 *    早期版本导出过的位置，继续扫描，避免老用户看不到历史日志。
 *
 * 3. **主应用外部私有目录 `Android/data/com.mini.me_core/files/logs/`（兼容）**
 *
 * 注意：主应用日常日志恒定写在内部私有目录 `context.filesDir/logs/`，
 * 受 Android 沙箱限制其他应用无法读取，因此本应用只能读「已导出到公共存储」的副本。
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
        /** 自动检测：三个候选目录都扫描，合并去重（推荐，也是默认值）。 */
        AUTO,

        /** 仅公共下载目录（Download/MiniMe-core/logs/）——主应用当前的导出目标。 */
        DOWNLOAD,

        /** 仅公共文档目录（Documents/MiniMe-core/logs/）——历史导出位置。 */
        DOCUMENTS,

        /** 仅主应用外部私有目录（Android/data/com.mini.me_core/files/logs/）。 */
        APP_PRIVATE,
    }

    /**
     * 公共下载目录：Download/MiniMe-core/logs/
     * 与主应用导出目标一致（Android 10+ 走 MediaStore.Downloads，落盘路径相同）。
     */
    @Suppress("DEPRECATION") // 仅用于拼路径
    fun getDownloadDir(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(File(base, PUBLIC_ROOT_DIR), LOG_SUBDIR)
    }

    /** 公共文档目录：Documents/MiniMe-core/logs/（历史位置，保留兼容扫描）。 */
    @Suppress("DEPRECATION")
    fun getDocumentsDir(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(base, PUBLIC_ROOT_DIR), LOG_SUBDIR)
    }

    /** 主应用外部私有目录：/storage/emulated/0/Android/data/com.mini.me_core/files/logs/ */
    @Suppress("DEPRECATION")
    fun getPrivateDir(): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        return File(
            File(File(externalStorage, "Android/data"), HOST_APP_PACKAGE),
            "files/$LOG_SUBDIR"
        )
    }

    /** 兼容旧调用方：历史语义为「公共外部存储」，现指向文档目录。 */
    fun getPublicDir(context: Context): File = getDocumentsDir()

    /**
     * 根据用户选择的来源，返回需要扫描的目录列表（按优先级排序）。
     */
    fun getDirsForSource(source: LogSource): List<File> = when (source) {
        LogSource.AUTO -> listOf(getDownloadDir(), getDocumentsDir(), getPrivateDir())
        LogSource.DOWNLOAD -> listOf(getDownloadDir())
        LogSource.DOCUMENTS -> listOf(getDocumentsDir())
        LogSource.APP_PRIVATE -> listOf(getPrivateDir())
    }

    /** 兼容旧签名：忽略 context，仅按来源返回目录。 */
    fun getDirsForSource(context: Context, source: LogSource): List<File> = getDirsForSource(source)

    /**
     * 检查目录是否存在且有日志文件。
     */
    fun hasLogs(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt")
        }?.isNotEmpty() == true
    }

    /** 获取目录的显示名称。 */
    fun getDirDisplayName(dir: File): String = dir.absolutePath
}
