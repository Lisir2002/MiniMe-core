package com.mini.me_core.feature.packager.domain.engine

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * APK 构建编排器
 *
 * 负责整个构建流程的编排：
 * 1. 初始化构建环境
 * 2. 解压模版 APK
 * 3. 替换网页资源
 * 4. 修改 AndroidManifest.xml（包名、应用名）
 * 5. 重新打包
 * 6. ZIP 对齐
 * 7. APK 签名
 * 8. 输出最终 APK
 *
 * 构建过程通过 [BuildProgressCallback] 回调进度和日志。
 */
class ApkBuilder(
    private val context: Context,
    private val projectStore: ProjectStore
) {
    /** 构建步骤定义 */
    enum class BuildStep(val displayName: String) {
        INIT("初始化构建环境"),
        EXTRACT("解压模版 APK"),
        REPLACE_WWW("替换网页资源"),
        MODIFY_MANIFEST("修改包名与应用名"),
        REPACK("重新打包"),
        ALIGN("ZIP 对齐"),
        SIGN("APK 签名"),
        OUTPUT("输出 APK")
    }

    /** 构建进度回调 */
    interface BuildProgressCallback {
        /** 构建步骤开始 */
        fun onStepStart(step: BuildStep, index: Int, total: Int)

        /** 构建步骤完成 */
        fun onStepComplete(step: BuildStep, index: Int, total: Int)

        /** 构建日志输出 */
        fun onLog(message: String)

        /** 构建完成 */
        fun onSuccess(apkFile: File, apkSize: Long)

        /** 构建失败 */
        fun onError(error: String)
    }

    private val steps = BuildStep.entries.toTypedArray()

    companion object {
        private const val TAG = "ApkBuilder"
    }

    /**
     * 执行构建
     *
     * @param project 项目配置
     * @param outputDir 输出目录
     * @param callback 进度回调
     */
    suspend fun build(
        project: Project,
        outputDir: File,
        callback: BuildProgressCallback
    ) = withContext(Dispatchers.IO) {
        val logFile = File(outputDir, "build.log")
        val logWriter = PrintWriter(FileWriter(logFile, true))
        val tempDir = File(outputDir, "temp")

        try {
            // ===== 构建前预校验 =====
            log("========== 构建前校验 ==========", callback, logWriter)
            val validationError = validateProject(project)
            if (validationError != null) {
                log("校验失败: $validationError", callback, logWriter)
                callback.onError("构建前校验失败: $validationError")
                return@withContext
            }
            log("校验通过", callback, logWriter)
            log("", callback, logWriter)

            log("========== 构建开始 ==========", callback, logWriter)
            log("项目: ${project.name} (${project.packageName})", callback, logWriter)
            log("版本: ${project.versionName} (${project.versionCode})", callback, logWriter)
            log("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", callback, logWriter)
            log("", callback, logWriter)

            // Step 1: 初始化
            callback.onStepStart(BuildStep.INIT, 0, steps.size)
            tempDir.apply { mkdirs() }
            val templateApk = getTemplateApk()
            log("模版 APK: ${templateApk.absolutePath} (${templateApk.length()} bytes)", callback, logWriter)
            log("临时目录: ${tempDir.absolutePath}", callback, logWriter)
            callback.onStepComplete(BuildStep.INIT, 0, steps.size)

            // Step 2: 解压
            callback.onStepStart(BuildStep.EXTRACT, 1, steps.size)
            val extractedDir = File(tempDir, "extracted")
            ApkModifier.extractApk(templateApk, extractedDir)
            log("解压完成，文件数: ${countFiles(extractedDir)}", callback, logWriter)
            callback.onStepComplete(BuildStep.EXTRACT, 1, steps.size)

            // Step 3: 替换网页资源
            callback.onStepStart(BuildStep.REPLACE_WWW, 2, steps.size)
            val wwwDir = projectStore.getWwwDir(project.id)
            ApkModifier.replaceWwwAssets(extractedDir, wwwDir)
            log("网页资源替换完成，源目录: ${wwwDir.absolutePath}", callback, logWriter)
            callback.onStepComplete(BuildStep.REPLACE_WWW, 2, steps.size)

            // Step 4: 修改 AndroidManifest.xml
            callback.onStepStart(BuildStep.MODIFY_MANIFEST, 3, steps.size)
            val manifestFile = File(extractedDir, "AndroidManifest.xml")
            if (manifestFile.exists()) {
                val modified = ManifestEditor.modifyManifest(
                    manifestFile = manifestFile,
                    newPackageName = project.packageName,
                    newAppName = project.name,
                    newVersionName = project.versionName,
                    newVersionCode = project.versionCode
                )
                if (modified) {
                    log("AndroidManifest.xml 修改完成: 包名=${project.packageName}, 应用名=${project.name}, 版本=${project.versionName}(${project.versionCode})", callback, logWriter)
                } else {
                    log("警告: 未在 AndroidManifest.xml 中找到目标字符串，可能模版已被修改", callback, logWriter)
                }
            } else {
                log("警告: AndroidManifest.xml 不存在", callback, logWriter)
            }
            callback.onStepComplete(BuildStep.MODIFY_MANIFEST, 3, steps.size)

            // Step 4.5: 修改 resources.arsc 中的应用名称
            // 应用名称实际存储在 resources.arsc 全局字符串池，仅改 AXML 不生效
            val arscFile = File(extractedDir, "resources.arsc")
            if (arscFile.exists()) {
                val arscModified = ArscEditor.modifyAppName(
                    arscFile = arscFile,
                    oldName = "应用模版",
                    newName = project.name
                )
                if (arscModified) {
                    log("resources.arsc 应用名称修改完成: ${project.name}", callback, logWriter)
                } else {
                    // 尝试备用旧名称
                    val fallback = ArscEditor.modifyAppName(
                        arscFile = arscFile,
                        oldName = "MiniMe Template",
                        newName = project.name
                    )
                    log(
                        if (fallback) "resources.arsc 应用名称修改完成(备用匹配): ${project.name}"
                        else "警告: resources.arsc 应用名称修改失败",
                        callback, logWriter
                    )
                }
            } else {
                log("警告: resources.arsc 不存在", callback, logWriter)
            }

            // Step 5: 重新打包
            callback.onStepStart(BuildStep.REPACK, 4, steps.size)
            val unsignedApk = File(tempDir, "unsigned.apk")
            ApkModifier.repackApk(extractedDir, unsignedApk)
            log("重新打包完成: ${unsignedApk.length()} bytes", callback, logWriter)
            callback.onStepComplete(BuildStep.REPACK, 4, steps.size)

            // Step 6: ZIP 对齐（临时跳过，排查 invalid stored block lengths 问题）
            callback.onStepStart(BuildStep.ALIGN, 5, steps.size)
            val alignedApk = unsignedApk // 临时：直接使用未对齐的 APK
            log("ZIP 对齐: 临时跳过（排查压缩损坏问题）", callback, logWriter)
            callback.onStepComplete(BuildStep.ALIGN, 5, steps.size)

            // Step 7: APK 签名
            callback.onStepStart(BuildStep.SIGN, 6, steps.size)
            val signedApk = File(tempDir, "signed.apk")
            val keystoreFile = File(projectStore.signatureDir, "default.keystore")
            ApkSigner.signApk(alignedApk, signedApk, keystoreFile)
            log("APK 签名完成: ${signedApk.length()} bytes", callback, logWriter)
            callback.onStepComplete(BuildStep.SIGN, 6, steps.size)

            // Step 8: 输出
            callback.onStepStart(BuildStep.OUTPUT, 7, steps.size)
            val finalApkName = "${project.packageName.substringAfterLast('.')}-v${project.versionName}.apk"
            val finalApk = File(outputDir, finalApkName)
            signedApk.copyTo(finalApk, overwrite = true)
            log("最终 APK: ${finalApk.absolutePath}", callback, logWriter)
            log("APK 大小: ${finalApk.length()} bytes (${formatSize(finalApk.length())})", callback, logWriter)

            // 同时复制到公共存储目录（与日志同目录 Download/MiniMe-core/apk/，方便用户获取分析）
            val publicApkDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MiniMe-core/apk")
            publicApkDir.mkdirs()
            val publicApk = File(publicApkDir, finalApkName)
            finalApk.copyTo(publicApk, overwrite = true)
            log("已复制到公共APK目录: ${publicApk.absolutePath}", callback, logWriter)

            // 清理临时目录
            tempDir.deleteRecursively()
            log("临时文件已清理", callback, logWriter)

            // 构建后验证
            log("", callback, logWriter)
            log("========== 构建后验证 ==========", callback, logWriter)
            val outputValidationError = validateOutputApk(finalApk)
            if (outputValidationError != null) {
                log("验证失败: $outputValidationError", callback, logWriter)
                callback.onError("构建后验证失败: $outputValidationError")
                return@withContext
            }
            log("验证通过: APK 格式正确，包含签名", callback, logWriter)

            log("", callback, logWriter)
            log("========== 构建成功 ==========", callback, logWriter)
            callback.onStepComplete(BuildStep.OUTPUT, 7, steps.size)
            callback.onSuccess(finalApk, finalApk.length())

        } catch (e: Exception) {
            val errorMsg = "构建失败: ${e.javaClass.simpleName}: ${e.message}"
            FileLogger.e(TAG, errorMsg, e)
            log("", callback, logWriter)
            log("========== 构建失败 ==========", callback, logWriter)
            log(errorMsg, callback, logWriter)
            log("堆栈跟踪:", callback, logWriter)
            e.stackTrace.forEach { log("  at $it", callback, logWriter) }
            // 失败时也清理临时文件
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                log("临时文件已清理", callback, logWriter)
            }
            callback.onError(errorMsg)
        } finally {
            logWriter.flush()
            logWriter.close()
        }
    }

    /**
     * 构建前预校验
     * @return 错误信息，null 表示校验通过
     */
    private fun validateProject(project: Project): String? {
        if (project.name.isBlank()) return "应用名称不能为空"
        if (project.packageName.isBlank()) return "包名不能为空"
        if (!Project.isValidPackageName(project.packageName)) return "包名格式不正确"
        if (project.versionName.isBlank()) return "版本号不能为空"

        // 检查模版 APK
        val templateApk = File(projectStore.templateDir, "template-base.apk")
        if (!templateApk.exists()) {
            // 尝试从 assets 复制
            return try {
                context.assets.open("packager/template-base.apk").use { input ->
                    templateApk.parentFile?.mkdirs()
                    FileOutputStream(templateApk).use { output -> input.copyTo(output) }
                }
                null
            } catch (e: Exception) {
                "模版 APK 不存在且无法从 assets 复制: ${e.message}"
            }
        }
        if (templateApk.length() < 1024) return "模版 APK 文件过小，可能已损坏"

        // 检查网页资源目录
        val wwwDir = projectStore.getWwwDir(project.id)
        if (!wwwDir.exists()) return "网页资源目录不存在"
        val indexHtml = File(wwwDir, "index.html")
        if (!indexHtml.exists()) return "index.html 不存在，请先创建网页文件"

        return null
    }

    /**
     * 构建后验证
     * @return 错误信息，null 表示验证通过
     */
    private fun validateOutputApk(apkFile: File): String? {
        if (!apkFile.exists()) return "输出 APK 不存在"
        val size = apkFile.length()
        if (size < 1024) return "输出 APK 过小 ($size bytes)，可能构建失败"
        if (size > 100 * 1024 * 1024) return "输出 APK 过大 ($size bytes)，超出合理范围"

        // 验证 ZIP 格式
        return try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entries = zip.entries().toList()
                if (entries.isEmpty()) return "APK 中没有任何文件"
                // 检查关键文件
                val hasManifest = entries.any { it.name == "AndroidManifest.xml" }
                if (!hasManifest) return "APK 中缺少 AndroidManifest.xml"
                null
            }
            // 检查 V2/V3 签名块（APK Signing Block）
            // V2 签名存储在 ZIP 内容和中央目录之间的签名块中，不在 META-INF 目录
            if (!hasV2SignatureBlock(apkFile)) {
                return "APK 中缺少 V2 签名块"
            }
            null
        } catch (e: Exception) {
            "APK 格式验证失败: ${e.message}"
        }
    }

    /**
     * 检查 APK 中是否存在 V2/V3 签名块（APK Signing Block）
     *
     * 通过查找签名块末尾的魔数 "APK Sig Block 42" 来判断。
     * 签名块结构：... + 大小(8字节) + 魔数(8字节)，位于中央目录之前。
     */
    private fun hasV2SignatureBlock(apkFile: File): Boolean {
        return try {
            val magic = "APK Sig Block 42".toByteArray(Charsets.UTF_8)
            val fileSize = apkFile.length()
            // 签名块通常不会太大，读取文件末尾的 1MB 进行搜索
            val searchSize = minOf(fileSize, 1024 * 1024).toInt()
            val buffer = ByteArray(searchSize)

            java.io.RandomAccessFile(apkFile, "r").use { raf ->
                raf.seek(fileSize - searchSize)
                raf.readFully(buffer)
            }

            // 在缓冲区中搜索魔数
            var found = false
            for (i in 0 until buffer.size - magic.size) {
                var match = true
                for (j in magic.indices) {
                    if (buffer[i + j] != magic[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    found = true
                    break
                }
            }
            found
        } catch (e: Exception) {
            FileLogger.w("ApkBuilder", "检查 V2 签名块失败: ${e.message}")
            false
        }
    }

    /**
     * 获取模版 APK 文件
     * 如果私有目录中没有，从 assets 复制
     */
    private fun getTemplateApk(): File {
        val templateDir = projectStore.templateDir
        val templateApk = File(templateDir, "template-base.apk")

        if (!templateApk.exists()) {
            // 从 assets 复制模版 APK
            context.assets.open("packager/template-base.apk").use { input ->
                FileOutputStream(templateApk).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return templateApk
    }

    /**
     * 记录日志（同时输出到回调、日志文件和 FileLogger）
     */
    private fun log(message: String, callback: BuildProgressCallback, logWriter: PrintWriter) {
        callback.onLog(message)
        logWriter.println(message)
        FileLogger.i(TAG, message)
    }

    /**
     * 统计目录下的文件数
     */
    private fun countFiles(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { if (it.isFile) count++ }
        return count
    }

    /**
     * 格式化文件大小
     */
    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
