package com.mini.me_core.feature.packager.domain.model

import java.io.File

/**
 * `.minime/` 统一配置目录布局（纯函数，不依赖 Android，可在 JVM 单测中断言）。
 *
 * 项目根目录约定：
 * ```
 * 项目根/
 * ├── .minime/          ← 统一配置目录（以 . 开头，默认在文件浏览器中隐藏）
 * │   ├── config.json
 * │   ├── icon.png
 * │   ├── splash.png    ← 图片模式启动图
 * │   ├── splash.html   ← HTML 模式自定义启动页（可选）
 * │   └── build/        ← 构建产物（APK、日志）
 * └── www/              ← 用户网页源码
 * ```
 *
 * 旧布局（config.json / icon.png / splash.png 直接放在项目根）在首次打开时被检测到并迁移到
 * `.minime/`；迁移判定与目标路径计算全部收敛在本对象，便于单测。
 */
object MinimeLayout {

    /** 配置目录名 */
    const val DIR_NAME = ".minime"

    const val CONFIG_FILE = "config.json"
    const val ICON_FILE = "icon.png"
    const val SPLASH_IMAGE_FILE = "splash.png"
    const val SPLASH_HTML_FILE = "splash.html"
    const val BUILD_DIR = "build"

    /** 项目根下的 .minime 目录 */
    fun minimeDir(projectRoot: File): File = File(projectRoot, DIR_NAME)

    fun configFile(projectRoot: File): File = File(minimeDir(projectRoot), CONFIG_FILE)

    fun iconFile(projectRoot: File): File = File(minimeDir(projectRoot), ICON_FILE)

    fun splashImageFile(projectRoot: File): File = File(minimeDir(projectRoot), SPLASH_IMAGE_FILE)

    fun splashHtmlFile(projectRoot: File): File = File(minimeDir(projectRoot), SPLASH_HTML_FILE)

    fun buildDir(projectRoot: File): File = File(minimeDir(projectRoot), BUILD_DIR)

    /** www 源码目录（项目根下，不随 .minime 迁移） */
    fun wwwDir(projectRoot: File): File = File(projectRoot, "www")

    /**
     * 迁移待办：返回需要从项目根迁移到 .minime 的旧文件集合；已是新布局时返回空集。
     *
     * 判定规则：项目根存在旧版 config.json（且 .minime/config.json 不存在）即视为旧布局；
     * icon.png / splash.png 若在项目根存在也一并列入迁移。
     */
    fun pendingMigrations(projectRoot: File): List<File> {
        if (!projectRoot.isDirectory) return emptyList()
        val newConfig = configFile(projectRoot)
        val legacyConfig = File(projectRoot, CONFIG_FILE)
        // 新布局已就位（无论 config 是否存在于 .minime），不做迁移
        if (minimeDir(projectRoot).isDirectory && newConfig.exists()) return emptyList()
        // 没有旧 config，说明不是旧布局
        if (!legacyConfig.isFile) return emptyList()
        val result = mutableListOf<File>()
        if (legacyConfig.isFile) result.add(legacyConfig)
        File(projectRoot, ICON_FILE).takeIf { it.isFile }?.let(result::add)
        File(projectRoot, SPLASH_IMAGE_FILE).takeIf { it.isFile }?.let(result::add)
        return result
    }

    /**
     * 把 pendingMigrations 中的旧文件迁入 .minime（同名覆盖），并创建 build/ 目录。
     * 返回实际迁移的文件数；任何单文件失败不阻断其余文件。非 JVM 语义由调用方在 IO 线程执行。
     */
    fun migrate(projectRoot: File): Int {
        val pending = pendingMigrations(projectRoot)
        if (pending.isEmpty()) return 0
        val targetDir = minimeDir(projectRoot).apply { mkdirs() }
        var moved = 0
        pending.forEach { src ->
            runCatching {
                val target = File(targetDir, src.name)
                if (target.exists()) target.delete()
                if (src.renameTo(target) || src.copyTo(target, overwrite = true).let { true }) {
                    moved++
                }
            }
        }
        buildDir(projectRoot).mkdirs()
        return moved
    }

    /** 文件名以 `.` 开头的目录/文件默认在文件浏览器中隐藏 */
    fun isHidden(name: String): Boolean = name.startsWith(".")
}
