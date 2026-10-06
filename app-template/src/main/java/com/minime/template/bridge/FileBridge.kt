package com.minime.template.bridge

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 文件 Bridge 模块
 * 提供文件读写、目录操作能力（仅应用私有目录，安全限制）
 */
class FileBridge(
    context: Context,
    moduleName: String
) : BridgeModule(context, moduleName) {

    private val appDir: File = context.filesDir

    override suspend fun execute(method: String, args: JsonObject): BridgeResult {
        return when (method) {
            "read" -> readFile(args)
            "write" -> writeFile(args)
            "list" -> listDir(args)
            "mkdir" -> mkdir(args)
            "delete" -> delete(args)
            "exists" -> exists(args)
            "getAppDir" -> getAppDir()
            else -> BridgeResult.failure("未知方法: $method", "METHOD_NOT_FOUND")
        }
    }

    override fun hasMethod(method: String): Boolean = method in getMethods()

    override fun getMethods(): List<String> = listOf(
        "read", "write", "list", "mkdir", "delete", "exists", "getAppDir"
    )

    /**
     * 安全路径解析：仅允许访问应用私有目录下的文件
     */
    private fun resolveSafePath(path: String): File? {
        val cleanPath = path.trimStart('/')
        val file = File(appDir, cleanPath)
        val canonicalAppDir = appDir.canonicalPath
        val canonicalFile = file.canonicalPath
        return if (canonicalFile.startsWith(canonicalAppDir)) file else null
    }

    private fun readFile(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 path 参数", "MISSING_PARAM")
        val file = resolveSafePath(path) ?: return BridgeResult.failure("路径越界，仅允许访问应用私有目录", "PATH_NOT_ALLOWED")
        if (!file.exists()) return BridgeResult.failure("文件不存在", "FILE_NOT_FOUND")
        if (!file.isFile) return BridgeResult.failure("路径不是文件", "NOT_A_FILE")
        return try {
            val content = file.readText()
            val data = buildJsonObject { put("content", content) }
            BridgeResult.success(data)
        } catch (e: Exception) {
            BridgeResult.failure("读取失败: ${e.message}", "READ_ERROR")
        }
    }

    private fun writeFile(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 path 参数", "MISSING_PARAM")
        val content = args["content"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 content 参数", "MISSING_PARAM")
        val file = resolveSafePath(path) ?: return BridgeResult.failure("路径越界，仅允许访问应用私有目录", "PATH_NOT_ALLOWED")
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            BridgeResult.success()
        } catch (e: Exception) {
            BridgeResult.failure("写入失败: ${e.message}", "WRITE_ERROR")
        }
    }

    private fun listDir(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: "."
        val dir = resolveSafePath(path) ?: return BridgeResult.failure("路径越界", "PATH_NOT_ALLOWED")
        if (!dir.exists()) return BridgeResult.failure("目录不存在", "DIR_NOT_FOUND")
        if (!dir.isDirectory) return BridgeResult.failure("路径不是目录", "NOT_A_DIR")
        val files = dir.listFiles()?.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("isDirectory", file.isDirectory)
                put("size", file.length())
                put("lastModified", file.lastModified())
            }
        } ?: emptyList()
        val data = buildJsonObject {
            put("files", kotlinx.serialization.json.JsonArray(files))
        }
        return BridgeResult.success(data)
    }

    private fun mkdir(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 path 参数", "MISSING_PARAM")
        val dir = resolveSafePath(path) ?: return BridgeResult.failure("路径越界", "PATH_NOT_ALLOWED")
        return if (dir.mkdirs() || dir.isDirectory) {
            BridgeResult.success()
        } else {
            BridgeResult.failure("创建目录失败", "MKDIR_ERROR")
        }
    }

    private fun delete(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 path 参数", "MISSING_PARAM")
        val file = resolveSafePath(path) ?: return BridgeResult.failure("路径越界", "PATH_NOT_ALLOWED")
        if (!file.exists()) return BridgeResult.failure("文件不存在", "FILE_NOT_FOUND")
        return if (file.deleteRecursively()) {
            BridgeResult.success()
        } else {
            BridgeResult.failure("删除失败", "DELETE_ERROR")
        }
    }

    private fun exists(args: JsonObject): BridgeResult {
        val path = args["path"]?.toString()?.trim('"') ?: return BridgeResult.failure("缺少 path 参数", "MISSING_PARAM")
        val file = resolveSafePath(path) ?: return BridgeResult.failure("路径越界", "PATH_NOT_ALLOWED")
        val data = buildJsonObject {
            put("exists", file.exists())
            put("isDirectory", file.isDirectory)
            put("isFile", file.isFile)
        }
        return BridgeResult.success(data)
    }

    private fun getAppDir(): BridgeResult {
        val data = buildJsonObject { put("path", appDir.absolutePath) }
        return BridgeResult.success(data)
    }
}
