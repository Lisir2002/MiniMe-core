package com.mini.logs.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF（Storage Access Framework）目录管理器。
 *
 * 当直接文件路径因 Android 11+/13+ 权限限制无法读取时，
 * 让用户通过系统文件选择器手动选择日志目录，通过 ContentResolver 读取。
 *
 * 性能要点：**不使用 DocumentFile.listFiles()**（它对每个文件单独查询，
 * 文件多时在主线程造成明显卡顿），改为一次 ContentResolver.query() 批量获取。
 * 所有 I/O 均在 Dispatchers.IO 执行。
 */
class SafDirectoryManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("logviewer_saf", Context.MODE_PRIVATE)

    /** SAF 目录中的日志文件轻量引用。 */
    data class SafLogFile(val name: String, val uri: Uri)

    /** 用户选择的目录 URI，null 表示未选择。 */
    fun getSavedTreeUri(): Uri? {
        val uriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try { Uri.parse(uriStr) } catch (e: Exception) { null }
    }

    /**
     * 用户选择目录后调用：保存 URI 并获取持久化权限。
     * @param grantedFlags 系统返回 Intent 中实际授予的 flags（优先使用）
     * @return true 持久化成功；false 持久化失败但 URI 已保存（本次会话可用）
     */
    fun saveTreeUri(uri: Uri, grantedFlags: Int = 0): Boolean {
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()

        // 优先用系统实际授予的 flags；否则请求读权限
        val persistFlags = if (grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        return try {
            context.contentResolver.takePersistableUriPermission(uri, persistFlags)
            true
        } catch (e: SecurityException) {
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
     * 列出 SAF 目录中的日志文件（IO 线程，单次批量查询，不卡顿）。
     * @return SafLogFile 列表，按文件名降序
     */
    suspend fun listLogFiles(): List<SafLogFile> = withContext(Dispatchers.IO) {
        val treeUri = getSavedTreeUri() ?: return@withContext emptyList()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val result = mutableListOf<SafLogFile>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (name.startsWith("log-") && name.endsWith(".txt")) {
                        val childDocId = cursor.getString(idIdx)
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        result.add(SafLogFile(name, fileUri))
                    }
                }
            }
            result.sortedByDescending { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 读取 SAF 文件的内容，返回最后 maxLines 行（IO 线程）。
     */
    suspend fun readLines(uri: Uri, maxLines: Int = 5000): List<String> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader(Charsets.UTF_8).readText()
                    val allLines = text.split('\n')
                    if (allLines.size <= maxLines) allLines
                    else allLines.takeLast(maxLines)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    companion object {
        private const val KEY_TREE_URI = "saf_tree_uri"

        /** 构建选择目录的 Intent（供 ActivityResultLauncher 使用）。 */
        fun buildOpenTreeIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        }
    }
}
