package com.mini.me_core.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 日志目录解析公共工具：把 [FileLogger]（按天的通用应用日志）与 [AILogger]（按会话的 AI 交互日志）
 * 原本各自重复实现的「公共外部存储优先、私有目录兜底」解析逻辑与存储权限判断收敛到一处，
 * 消除两份实现的漂移风险（f 项去重）。
 *
 * 目录约定（卸载后仍保留）：
 *   - 公共外部存储 `Documents/MiniMe-core/<subdir>`（需 WRITE_EXTERNAL_STORAGE，targetSdk=28 下可写）；
 *   - 权限未授予时回退外部私有 `getExternalFilesDir(null)/<subdir>`（无需权限，卸载时清除）；
 *   - 外部私有不可用时再回退内部 `filesDir/<subdir>`。
 *
 * [FileLogger] 传 subdir = "logs"；[AILogger] 传 subdir = "ai-logs"。
 * 各日志器仍自行持有自身的 `logDir` 状态、ioExecutor 与清理逻辑，本类只负责「算出该用哪个目录」。
 */
internal object LogDirectoryResolver {

    // 公共外部存储根目录（卸载后仍保留）：/storage/emulated/0/Documents/MiniMe-core
    private const val PUBLIC_ROOT_DIR = "MiniMe-core"

    /**
     * 是否可写公共外部存储：外部存储已挂载且运行时已授予 WRITE_EXTERNAL_STORAGE。
     */
    fun hasExternalStorageWrite(context: Context): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 解析日志目录：优先公共外部存储 `Documents/MiniMe-core/<subdir>`（卸载后保留，需存储权限）；
     * 权限未授予时回退外部私有目录，再回退内部存储。
     */
    @Suppress("DEPRECATION") // targetSdk=28 下 getExternalStoragePublicDirectory 仍可用且不受分区存储限制
    fun resolveLogDir(context: Context, subdir: String): File {
        if (hasExternalStorageWrite(context)) {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(File(base, PUBLIC_ROOT_DIR), subdir)
            if (dir.exists() || dir.mkdirs()) return dir
        }
        // 回退：外部私有目录（卸载时清除，但无需权限）；再回退内部存储。
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, subdir).apply { mkdirs() }
    }

    /**
     * 外部存储权限在运行时被授予后调用：重新解析目录，若与 [currentDir] 不同则返回新目录
     * （调用方据此切换自身 logDir 并触发清理）；目录未变则返回 null（无需切换）。
     *
     * [init] 通常发生在 Application.onCreate（早于权限授予），因此需要在权限回调时重新解析。
     */
    fun resolveAfterPermissionGrant(context: Context, subdir: String, currentDir: File?): File? {
        val newDir = resolveLogDir(context, subdir)
        return if (currentDir == null || newDir.absolutePath != currentDir.absolutePath) newDir else null
    }
}
