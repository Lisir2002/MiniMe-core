package com.mini.logs.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * SAF（Storage Access Framework）目录管理器。
 *
 * 当直接文件路径因 Android 11+/13+ 权限限制无法读取时，
 * 让用户通过系统文件选择器手动选择日志目录，通过 ContentResolver 读取。
 *
 * URI 权限通过 takePersistableUriPermission 持久化，重启后仍可访问。
 */
class SafDirectoryManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("logviewer_saf", Context.MODE_PRIVATE)

    /** 用户选择的目录 URI，null 表示未选择。 */
    fun getSavedTreeUri(): Uri? {
        val uriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try { Uri.parse(uriStr) } catch (e: Exception) { null }
    }

    /**
     * 用户选择目录后调用：保存 URI 并获取持久化权限。
     * @return true 成功，false 权限获取失败
     */
    fun saveTreeUri(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
            true
        } catch (e: SecurityException) {
            // 某些设备不支持持久化权限，仍然保存 URI（本次会话可用）
            prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
            false
        }
    }

    /** 清除保存的目录。 */
    fun clearTreeUri() {
        val uri = getSavedTreeUri()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    /**
     * 列出 SAF 目录中的日志文件。
     * @return DocumentFile 列表，按文件名降序
     */
    fun listLogFiles(): List<DocumentFile> {
        val treeUri = getSavedTreeUri() ?: return emptyList()
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!tree.isDirectory) return emptyList()
        return tree.listFiles()
            .filter { f ->
                f.isFile && f.name?.let {
                    it.startsWith("log-") && it.endsWith(".txt")
                } == true
            }
            .sortedByDescending { it.name ?: "" }
    }

    /**
     * 读取 SAF 文件的全部内容（或最后 maxLines 行）。
     */
    fun readLines(uri: Uri, maxLines: Int = 5000): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 对于大文件，读取最后部分；这里简化为全量读取后截取
                // 日志文件通常 5MB 以内，全量读取可接受
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                val allLines = text.split('\n')
                if (allLines.size <= maxLines) allLines
                else allLines.takeLast(maxLines)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取 SAF 文件的显示名称。
     */
    fun getDisplayName(uri: Uri): String {
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "unknown"
    }

    companion object {
        private const val KEY_TREE_URI = "saf_tree_uri"

        /**
         * 构建选择目录的 Intent（供 ActivityResultLauncher 使用）。
         */
        fun buildOpenTreeIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
        }
    }
}
