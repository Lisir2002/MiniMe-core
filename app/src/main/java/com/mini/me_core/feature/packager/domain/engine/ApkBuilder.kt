package com.mini.me_core.feature.packager.domain.engine

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.toSplashConfigPayload
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
        INJECT_SPLASH("注入启动动画"),
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
    @Suppress("CyclomaticComplexMethod", "LongMethod", "CognitiveComplexMethod")
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

            // Step 3.5: 替换应用图标（如果用户设置了自定义图标）
            // 模版图标为自适应图标：res/BW.xml 是二进制 XML，foreground 引用一张位图。
            // 直接用 PNG 覆盖 BW.xml 会破坏二进制 XML，必须通过 IconReplacer 定位
            // foreground 引用的位图资源并按安全区合成后写回。
            if (project.iconPath != null && File(project.iconPath).exists()) {
                val iconFile = File(project.iconPath)
                val replaced = IconReplacer.replaceIcon(extractedDir, iconFile)
                if (replaced) {
                    log("应用图标已替换: ${iconFile.name} (${iconFile.length()} bytes)", callback, logWriter)
                } else {
                    log("警告: 应用图标替换失败，保留模版默认图标", callback, logWriter)
                }
            }

            // Step 3.6: 更新 assets/config.json（应用配置：权限、Bridge、显示、启动页等）
            val configFile = File(extractedDir, "assets/config.json")
            if (configFile.exists()) {
                try {
                    val config = org.json.JSONObject(configFile.readText())

                    // 应用基本信息
                    config.getJSONObject("app").apply {
                        put("name", project.name)
                        put("packageName", project.packageName)
                        put("versionName", project.versionName)
                        put("versionCode", project.versionCode)
                    }

                    // 显示设置
                    config.getJSONObject("webview").apply {
                        put("immersive", project.displaySettings.immersiveMode)
                        put("orientation", project.displaySettings.screenOrientation)
                        put("statusBarColor", project.displaySettings.statusBarColor)
                        put("navigationBarColor", project.displaySettings.navigationBarColor)
                    }

                    // 权限配置
                    config.getJSONObject("permissions").apply {
                        put("internet", project.permissions.contains("android.permission.INTERNET"))
                        put("storage", project.permissions.any { it.contains("STORAGE") })
                        put("camera", project.permissions.contains("android.permission.CAMERA"))
                        put("microphone", project.permissions.contains("android.permission.RECORD_AUDIO"))
                        put("location", project.permissions.any { it.contains("LOCATION") })
                        put("bluetooth", project.permissions.any { it.contains("BLUETOOTH") })
                    }

                    // Bridge 能力配置
                    config.getJSONObject("bridge").apply {
                        put("enabled", project.bridgeEnabled)
                        val modules = mutableListOf<String>()
                        if (project.bridgeCapabilities.ui) modules.add("ui")
                        if (project.bridgeCapabilities.device) modules.add("device")
                        if (project.bridgeCapabilities.file) modules.add("file")
                        if (project.bridgeCapabilities.network) modules.add("connect")
                        if (project.bridgeCapabilities.data) modules.add("data")
                        put("modules", modules)
                    }

                    // 启动页设置（与模板侧 splash 段契约一致：含时长钳制、过渡、背景色、点击跳过、有无自定义图）
                    val splash = project.splashSettings
                    val hasSplashImage = splash.customImagePath != null &&
                        File(splash.customImagePath).exists()
                    val splashPayload = splash.toSplashConfigPayload(hasSplashImage)
                    config.getJSONObject("splash").apply {
                        put("enabled", splashPayload.enabled)
                        put("mode", splashPayload.mode)
                        put("duration", splashPayload.durationMs)
                        put("autoHide", splashPayload.autoHide)
                        put("backgroundColor", splashPayload.backgroundColor)
                        // 无自定义图时显式写 null，模板回退纯底色
                        if (splashPayload.image != null) {
                            put("image", splashPayload.image)
                        } else {
                            put("image", org.json.JSONObject.NULL)
                        }
                        put("transition", splashPayload.transition)
                        put("skipOnTap", splashPayload.skipOnTap)
                        put("showLoading", splashPayload.showLoading)
                        put("htmlTimeoutMs", splashPayload.htmlTimeoutMs)
                    }

                    configFile.writeText(config.toString(2))
                    log(
                        "assets/config.json 已更新: 权限=${project.permissions.size}项, " +
                            "Bridge=${if (project.bridgeEnabled) "启用" else "禁用"}, " +
                            "沉浸模式=${project.displaySettings.immersiveMode}",
                        callback,
                        logWriter
                    )
                } catch (e: Exception) {
                    log("警告: 更新 config.json 失败: ${e.message}", callback, logWriter)
                }
            } else {
                log("警告: assets/config.json 不存在，跳过配置更新", callback, logWriter)
            }

            // Step 3.7: 注入冷启动动画（合成启动位图、覆盖 splash_img、写入 www/splash.html|splash.png）
            // 必须在 replaceWwwAssets（Step3 会整盘重写 assets/www）之后执行；资源缺失时内部告警跳过，不中断构建。
            callback.onStepStart(BuildStep.INJECT_SPLASH, 3, steps.size)
            runCatching {
                // 主 App 运行时读不到 app-template 源码，splash.html 从自身 assets 拷出作注入来源
                val splashHtmlSource = File(tempDir, "splash.html")
                context.assets.open("packager/splash.html").use { input ->
                    FileOutputStream(splashHtmlSource).use { output -> input.copyTo(output) }
                }
                val customImage = project.splashSettings.customImagePath
                    ?.let { File(it) }
                    ?.takeIf { it.isFile }
                val userHtml = project.splashSettings.htmlSplashPath
                    ?.let { File(it) }
                    ?.takeIf { it.isFile }
                val result = SplashInjector.inject(
                    extractedDir = extractedDir,
                    settings = project.splashSettings,
                    customImageFile = customImage,
                    splashHtmlSource = splashHtmlSource,
                    userSplashHtmlFile = userHtml
                )
                if (result.windowImageOverwritten) {
                    log("冷启动窗地位图已覆盖（背景色=${project.splashSettings.backgroundColor}）", callback, logWriter)
                } else {
                    log("警告: 模版不含 splash_img 位图，跳过冷启动窗地位图覆盖（前向兼容）", callback, logWriter)
                }
                if (result.windowBackgroundPatched) {
                    log("原生主题 windowBackground 已按模式指向目标资源", callback, logWriter)
                }
                if (result.wwwSplashPngWritten) log("已写入 www/splash.png", callback, logWriter)
                if (result.splashHtmlWritten) log("已写入 www/splash.html", callback, logWriter)
            }.onFailure { e ->
                // 启动动画注入属于增强能力，任何异常都不阻断主流程
                log("警告: 启动动画注入失败，保留模版默认启动: ${e.message}", callback, logWriter)
            }
            callback.onStepComplete(BuildStep.INJECT_SPLASH, 3, steps.size)

            // Step 4: 修改 AndroidManifest.xml
            callback.onStepStart(BuildStep.MODIFY_MANIFEST, 4, steps.size)
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
            callback.onStepComplete(BuildStep.MODIFY_MANIFEST, 4, steps.size)

            // Step 4.5: 修改 resources.arsc 中的包名与应用名称
            // 包名必须与 AndroidManifest.xml 的 package 属性一致，否则系统安装时报
            // 「兼容性问题」；应用名称存储在全局字符串池，仅改 AXML 不生效。
            val arscFile = File(extractedDir, "resources.arsc")
            if (arscFile.exists()) {
                // 先改包名（必须与 Manifest 同步）
                val pkgModified = ArscEditor.modifyPackageName(
                    arscFile = arscFile,
                    newPackageName = project.packageName
                )
                if (pkgModified) {
                    log("resources.arsc 包名修改完成: ${project.packageName}", callback, logWriter)
                } else {
                    log("警告: resources.arsc 包名修改失败", callback, logWriter)
                }

                // 再改应用名称
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
            callback.onStepStart(BuildStep.REPACK, 5, steps.size)
            val unsignedApk = File(tempDir, "unsigned.apk")
            ApkModifier.repackApk(extractedDir, unsignedApk)
            log("重新打包完成: ${unsignedApk.length()} bytes", callback, logWriter)
            callback.onStepComplete(BuildStep.REPACK, 5, steps.size)

            // Step 6: ZIP 对齐
            // 所有未压缩（STORED）文件必须 4 字节对齐，否则部分设备（如 vivo）安装时
            // 会报「安装应用与当前系统有兼容性问题」。resources.arsc 尤其需要对齐。
            callback.onStepStart(BuildStep.ALIGN, 6, steps.size)
            val alignedApk = File(tempDir, "aligned.apk")
            ZipAligner.align(unsignedApk, alignedApk)
            log("ZIP 对齐完成: ${alignedApk.length()} bytes", callback, logWriter)
            callback.onStepComplete(BuildStep.ALIGN, 6, steps.size)

            // Step 7: APK 签名
            callback.onStepStart(BuildStep.SIGN, 7, steps.size)
            val signedApk = File(tempDir, "signed.apk")
            val keystoreFile = File(projectStore.signatureDir, "default.keystore")
            ApkSigner.signApk(alignedApk, signedApk, keystoreFile)
            log("APK 签名完成: ${signedApk.length()} bytes", callback, logWriter)
            callback.onStepComplete(BuildStep.SIGN, 7, steps.size)

            // Step 8: 输出
            callback.onStepStart(BuildStep.OUTPUT, 8, steps.size)
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
            callback.onStepComplete(BuildStep.OUTPUT, 8, steps.size)
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
