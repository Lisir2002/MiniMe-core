package com.mini.me_core.feature.packager.domain.repository

import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.ProjectType
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 项目存储端口。实现（JSON 文件持久化 + 内存缓存）位于 data 层。
 * 数据存储在应用私有目录 files/packager/projects/ 下。
 */
interface ProjectStore {

    val projects: StateFlow<List<Project>>

    /** 模版 APK 存储目录 */
    val templateDir: File

    /** 签名密钥存储目录 */
    val signatureDir: File

    /**
     * 创建新项目
     *
     * @param name 应用名称
     * @param packageName 包名
     * @param type 项目类型
     * @param versionName 版本名
     * @param htmlContent HTML 内容（P0 阶段直接存储为 index.html）
     */
    suspend fun createProject(
        name: String,
        packageName: String,
        type: ProjectType,
        versionName: String = Project.DEFAULT_VERSION_NAME,
        htmlContent: String = ""
    ): Project

    /** 更新项目配置 */
    suspend fun updateProject(project: Project): Project

    /** 删除项目 */
    suspend fun deleteProject(projectId: String)

    /** 根据 ID 获取项目 */
    fun getProjectById(projectId: String): Project?

    /** 检查包名是否已存在 */
    fun isPackageNameExists(packageName: String): Boolean

    /** 生成唯一包名（如果已存在则追加数字后缀） */
    fun generateUniquePackageName(basePackageName: String): String

    /** 获取项目的 www 目录 */
    fun getWwwDir(projectId: String): File

    /** 获取项目的构建目录 */
    fun getBuildsDir(projectId: String): File

    /** 更新项目的最后构建状态 */
    suspend fun updateBuildStatus(projectId: String, status: BuildStatus)
}
