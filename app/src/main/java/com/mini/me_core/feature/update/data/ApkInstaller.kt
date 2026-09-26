package com.mini.me_core.feature.update.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 调起系统包安装器安装已下载的 APK。
 *
 * 复用项目现有 FileProvider（authority = ${applicationId}.fileprovider）。
 */
object ApkInstaller {

    fun install(context: Context, filePath: String): Boolean {
        val apkFile = File(filePath)
        if (!apkFile.exists()) return false
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
