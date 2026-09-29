package com.mini.me_core.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 日志等级统一控制器（审计 H1）。
 *
 * 背景：`setMinLevel` 过去只作用于 [FileLogger]，[AILogger] 完全没有等级判定——
 * 用户把等级调到 ERROR 后，体量最大、隐私密度最高的 AI 会话日志（完整对话历史 /
 * 工具定义 / 原始 SSE）仍照写不误，形成"以为关了一半、实际关错了那一半"的口径不一致。
 *
 * 本对象是**唯一**的等级下发入口：设置项（LogSettingsRepository）只需调用 [apply]，
 * 由它同时下发到两套日志实现，保证口径永远一致。
 */
object LogLevelController {

    /** 把等级同时应用到 [FileLogger] 与 [AILogger]。 */
    fun apply(level: LogLevel) {
        FileLogger.setMinLevel(level)
        AILogger.setMinLevel(level)
    }
}

/**
 * 日志目录解析的共用实现（审计 M）。
 *
 * [FileLogger] 与 [AILogger] 曾各自复制一份「公共外部存储 → 外部私有 → 内部」的回退链，
 * 逐行雷同。这里收敛为单一实现，两者传入各自的子目录名即可。
 */
object LogDirResolver {

    /**
     * 解析某类日志的目录：优先公共外部存储 `Documents/<root>/<subdir>`（卸载后保留，
     * 需 WRITE_EXTERNAL_STORAGE，targetSdk=28 下可写）；权限未授予时回退外部私有目录，
     * 再回退内部存储。
     */
    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用且不受分区存储限制
    fun resolve(context: Context, subDir: String): File {
        if (hasExternalStorageWrite(context)) {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(File(base, LogConfig.publicRootDir), subDir)
            if (dir.exists() || dir.mkdirs()) return dir
        }
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, subDir).apply { mkdirs() }
    }

    private fun hasExternalStorageWrite(context: Context): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** 供 [FileLogger] 的 legacy 导出路径（API <29）复用同一权限判定。 */
    fun hasExternalStorageWritePermission(context: Context): Boolean = hasExternalStorageWrite(context)
}
