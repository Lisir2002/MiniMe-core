package com.mini.me_core.feature.credentials.data

import java.io.File

/**
 * 把含明文 token 的落盘/临时文件权限收紧到 0600（仅属主可读写）。
 *
 * 纯 JVM（[File.setReadable]/[File.setWritable] 在 Android 与 JVM 均可调用），
 * 供 [GitCredentialsFileSync] 与 [CredentialRequestBridge] 复用，也便于单测断言权限收紧。
 */
object SecuredFiles {

    /**
     * 收紧文件到属主独占：属主可读可写，group/other 不可读不可写，且去执行位。
     * 返回调用后的文件（便于链式断言）。权限调整失败时静默忽略（不阻断写入主流程）。
     */
    fun tightenToOwnerOnly(file: File): File {
        runCatching<Unit> {
            // 先清掉 group/other 全部权限，再确保属主可读写。
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
        return file
    }
}
