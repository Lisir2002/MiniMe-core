package com.mini.me_core.feature.packager.data.repository

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.BridgeCapabilities
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.DisplaySettings
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.ProjectConfig
import com.mini.me_core.feature.packager.domain.model.ProjectType
import com.mini.me_core.feature.packager.domain.model.SplashSettings
import com.mini.me_core.feature.packager.domain.model.WebViewSettings
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 项目存储实现
 *
 * 负责项目配置的持久化（JSON 文件）和内存缓存。
 * 数据存储在应用私有目录 files/packager/projects/ 下。
 *
 * 持久化格式：项目目录下的 project.json（分组 schema，见 [ProjectConfig]）。
 * 旧版扁平格式 config.json 在加载时自动迁移为 project.json，并将旧文件重命名为
 * config.legacy.json.bak 留档；所有写入均采用「临时文件 + rename」原子替换。
 */
@Suppress("ClassOrdering") // 行为保持搬迁：companion/属性顺序沿用原 domain 类，不重排
@Singleton
class ProjectStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ProjectStore {
    companion object {
        private const val TAG = "ProjectStore"

        /** 默认 HTML 模板 */
        private const val DEFAULT_HTML = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>我的应用</title>
</head>
<body>
    <h1>Hello World</h1>
    <p>在这里开始编写你的 HTML 应用</p>
</body>
</html>"""
    }

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
    }

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    /** 项目根目录 */
    private val projectsDir: File
        get() = File(context.filesDir, "packager/projects").apply { mkdirs() }

    /** 模版 APK 存储目录 */
    override val templateDir: File
        get() = File(context.filesDir, "packager/templates").apply { mkdirs() }

    /** 签名密钥存储目录 */
    override val signatureDir: File
        get() = File(context.filesDir, "packager/signatures").apply { mkdirs() }

    init {
        // 初始化时加载所有项目
        loadAll()
    }

    /**
     * 加载所有项目。
     *
     * 每个项目目录的解析优先级：
     * 1. project.json —— 现行分组格式，直接解析；
     * 2. config.json —— 旧扁平格式，解析后迁移为 project.json，旧文件重命名备份；
     * 3. 两者都不存在 —— 跳过该目录。
     */
    private fun loadAll() {
        val list = mutableListOf<Project>()
        var failed = 0
        var migrated = 0
        projectsDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val projectJson = File(dir, "project.json")
            val legacyJson = File(dir, "config.json")
            runCatching {
                when {
                    projectJson.exists() -> {
                        val config = json.decodeFromString<ProjectConfig>(projectJson.readText())
                        list.add(config.toProject(dir))
                    }
                    legacyJson.exists() -> {
                        // 旧格式迁移：扁平 StoredProject → Project → 分组 ProjectConfig
                        val stored = json.decodeFromString<LegacyStoredProject>(legacyJson.readText())
                        val project = stored.toProject()
                        writeProjectConfig(dir, ProjectConfig.fromProject(project))
                        // 旧文件留档备份（已存在则覆盖）
                        val backup = File(dir, "config.legacy.json.bak")
                        if (backup.exists()) backup.delete()
                        legacyJson.renameTo(backup)
                        migrated++
                        FileLogger.i(TAG, "迁移旧配置: ${project.name} (${project.id}) -> project.json")
                        list.add(project)
                    }
                    else -> {
                        FileLogger.w(TAG, "目录缺少配置文件，跳过: ${dir.absolutePath}", null)
                    }
                }
            }.onFailure {
                failed++
                FileLogger.w(TAG, "加载项目配置失败: ${dir.absolutePath}", it)
            }
        }
        // 按更新时间降序排列
        list.sortByDescending { it.updatedAt }
        _projects.value = list
        FileLogger.d(
            TAG,
            "加载完成，共 ${list.size} 个项目" +
                (if (failed > 0) "，$failed 个加载失败" else "") +
                (if (migrated > 0) "，$migrated 个已迁移" else "")
        )
    }

    override suspend fun createProject(
        name: String,
        packageName: String,
        type: ProjectType,
        versionName: String,
        htmlContent: String
    ): Project = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val versionCode = Project.parseVersionCode(versionName)

        val project = Project(
            id = id,
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            type = type,
            createdAt = now,
            updatedAt = now
        )

        // 创建项目目录
        val projectDir = File(projectsDir, id).apply { mkdirs() }

        // 保存配置（project.json，原子写入）
        writeProjectConfig(projectDir, ProjectConfig.fromProject(project))

        // 保存 HTML 内容（为空时使用默认模板）
        val wwwDir = File(projectDir, "www").apply { mkdirs() }
        val htmlToSave = if (htmlContent.isNotEmpty()) htmlContent else DEFAULT_HTML
        File(wwwDir, "index.html").writeText(htmlToSave)

        // 更新内存缓存
        _projects.value = listOf(project) + _projects.value

        FileLogger.i(TAG, "创建项目: ${project.name} (${project.packageName}), 类型=${project.type}")
        project
    }

    /**
     * 更新项目配置
     */
    override suspend fun updateProject(project: Project): Project = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val projectDir = File(projectsDir, updated.id)
        if (projectDir.exists()) {
            writeProjectConfig(projectDir, ProjectConfig.fromProject(updated))
        }
        _projects.value = _projects.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    /**
     * 删除项目
     */
    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val project = _projects.value.find { it.id == projectId }
        val projectDir = File(projectsDir, projectId)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        _projects.value = _projects.value.filter { it.id != projectId }
        FileLogger.i(TAG, "删除项目: ${project?.name ?: projectId}")
    }

    /**
     * 根据 ID 获取项目
     */
    override fun getProjectById(projectId: String): Project? =
        _projects.value.find { it.id == projectId }

    /**
     * 检查包名是否已存在
     */
    override fun isPackageNameExists(packageName: String): Boolean =
        _projects.value.any { it.packageName == packageName }

    /**
     * 生成唯一包名（如果已存在则追加数字后缀）
     */
    override fun generateUniquePackageName(basePackageName: String): String {
        var candidate = basePackageName
        var counter = 1
        while (isPackageNameExists(candidate)) {
            candidate = "$basePackageName$counter"
            counter++
        }
        return candidate
    }

    /**
     * 获取项目的 www 目录
     */
    override fun getWwwDir(projectId: String): File =
        File(projectsDir, "$projectId/www").apply { mkdirs() }

    /**
     * 获取项目的构建目录
     */
    override fun getBuildsDir(projectId: String): File =
        File(projectsDir, "$projectId/builds").apply { mkdirs() }

    /**
     * 更新项目的最后构建状态
     */
    override suspend fun updateBuildStatus(projectId: String, status: BuildStatus) {
        val project = getProjectById(projectId) ?: return
        updateProject(project.copy(lastBuildStatus = status, lastBuildAt = System.currentTimeMillis()))
    }

    /**
     * 原子写入 project.json：
     * 先序列化到同目录下的 project.json.tmp，再 rename 覆盖目标文件。
     * 同一文件系统内 rename(2) 是原子的，进程中断不会留下半截 JSON。
     */
    private fun writeProjectConfig(projectDir: File, config: ProjectConfig) {
        projectDir.mkdirs()
        val target = File(projectDir, "project.json")
        val tmp = File(projectDir, "project.json.tmp")
        tmp.writeText(json.encodeToString(config), Charsets.UTF_8)
        if (!tmp.renameTo(target) && target.exists()) {
            // 兜底：目标已存在且首次 rename 被拒（跨设备等异常），删除后重试
            target.delete()
            if (!tmp.renameTo(target)) {
                error("project.json 写入失败: ${target.absolutePath}")
            }
        }
    }

    /**
     * 旧版扁平配置（config.json）的序列化格式。
     *
     * 仅用于加载时迁移读取，不再用于写入；新格式见 [ProjectConfig]。
     */
    @Serializable
    private data class LegacyStoredProject(
        val id: String,
        val name: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val type: String,
        val iconPath: String? = null,
        val bridgeEnabled: Boolean = true,
        val bridgeCapabilities: BridgeCapabilities = BridgeCapabilities(),
        val permissions: List<String> = emptyList(),
        val webViewSettings: WebViewSettings = WebViewSettings(),
        val displaySettings: DisplaySettings = DisplaySettings(),
        val splashSettings: SplashSettings = SplashSettings(),
        val createdAt: Long,
        val updatedAt: Long,
        val lastBuildAt: Long? = null,
        val lastBuildStatus: String? = null
    ) {
        fun toProject(): Project = Project(
            id = id,
            name = name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            type = ProjectType.fromName(type),
            iconPath = iconPath,
            bridgeEnabled = bridgeEnabled,
            bridgeCapabilities = bridgeCapabilities,
            permissions = permissions,
            webViewSettings = webViewSettings,
            displaySettings = displaySettings,
            splashSettings = splashSettings,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastBuildAt = lastBuildAt,
            lastBuildStatus = lastBuildStatus?.let {
                runCatching { BuildStatus.valueOf(it) }.getOrNull()
            }
        )
    }
}
