package com.mini.me_core.feature.packager.data.repository

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.model.BridgeCapabilities
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.DisplaySettings
import com.mini.me_core.feature.packager.domain.model.Project
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
     * 加载所有项目
     */
    private fun loadAll() {
        val list = mutableListOf<Project>()
        var failed = 0
        projectsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val configFile = File(dir, "config.json")
                if (configFile.exists()) {
                    runCatching {
                        val stored = json.decodeFromString<StoredProject>(configFile.readText())
                        list.add(stored.toProject())
                    }.onFailure {
                        failed++
                        FileLogger.w(TAG, "加载项目配置失败: ${configFile.absolutePath}", it)
                    }
                }
            }
        }
        // 按更新时间降序排列
        list.sortByDescending { it.updatedAt }
        _projects.value = list
        FileLogger.d(TAG, "加载完成，共 ${list.size} 个项目" + if (failed > 0) "，$failed 个加载失败" else "")
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

        // 保存配置
        val configFile = File(projectDir, "config.json")
        configFile.writeText(json.encodeToString(StoredProject.fromProject(project)))

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
            val configFile = File(projectDir, "config.json")
            configFile.writeText(json.encodeToString(StoredProject.fromProject(updated)))
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
     * 用于 JSON 序列化的项目存储格式
     */
    @Serializable
    private data class StoredProject(
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

        companion object {
            fun fromProject(project: Project): StoredProject = StoredProject(
                id = project.id,
                name = project.name,
                packageName = project.packageName,
                versionName = project.versionName,
                versionCode = project.versionCode,
                type = project.type.name,
                iconPath = project.iconPath,
                bridgeEnabled = project.bridgeEnabled,
                bridgeCapabilities = project.bridgeCapabilities,
                permissions = project.permissions,
                webViewSettings = project.webViewSettings,
                displaySettings = project.displaySettings,
                splashSettings = project.splashSettings,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                lastBuildAt = project.lastBuildAt,
                lastBuildStatus = project.lastBuildStatus?.name
            )
        }
    }
}
