package com.mini.me_core.core.util

import android.content.Context
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
 * 安全口径：日志**默认只写应用私有内部目录**（`filesDir/<subDir>`），任何第三方应用
 * 不可读。历史上默认写公共 Documents（卸载保留、文件管理器可见）会把含 token /
 * 命令 / 路径的日志暴露给任意应用，已废弃；公共目录只允许「用户显式导出日志」时
 * 一次性写入，且导出前全量脱敏（见 FileLogger.exportLogsToDownloads）。
 */
object LogDirResolver {

    /**
     * 解析日志目录：固定为应用私有内部目录 `filesDir/<subDir>`。
     * 不再因 WRITE_EXTERNAL_STORAGE 权限而自动切换到公共 Documents。
     */
    fun resolve(context: Context, subDir: String): File =
        File(context.filesDir, subDir).apply { mkdirs() }

    /**
     * 历史上用于权限授予后把日志切到公共目录；该行为已废弃，保留方法签名以兼容
     * 既有调用方——现在为 no-op，日志目录恒定为应用私有目录。
     */
    @Suppress("UnusedParameter") // 保留回调签名，函数为 no-op
    fun onExternalStorageGranted(context: Context) {
        // no-op：日志不再自动写入公共外部存储。
    }
}
