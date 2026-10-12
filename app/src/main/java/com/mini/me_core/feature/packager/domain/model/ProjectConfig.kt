package com.mini.me_core.feature.packager.domain.model

import kotlinx.serialization.Serializable
import java.io.File

/**
 * project.json 持久化 DTO（分组 schema）
 *
 * 与领域模型 [Project]（扁平字段）解耦：文件内按 meta / buildConfig / bridge /
 * display / splash / webview / icon 分组组织，未来字段扩展只在分组内追加，
 * 不影响旧文件解析（[ignoreUnknownKeys]）。
 *
 * 图标路径约定：[IconConfig.iconPath] 存储相对项目目录的文件名（如 "icon.png"），
 * 不存绝对路径；绝对路径与相对路径的互转集中在 [toProject] / [fromProject] 完成，
 * 调用方（ProjectStoreImpl）只需传入项目目录。
 *
 * @param schemaVersion schema 版本号，当前为 1，预留后续格式迁移
 */
@Serializable
data class ProjectConfig(
    val schemaVersion: Int = 1,
    val meta: MetaConfig,
    val buildConfig: BuildConfigDto,
    val permissions: List<String> = emptyList(),
    val bridge: BridgeDto,
    val display: DisplaySettings = DisplaySettings(),
    val splash: SplashSettings = SplashSettings(),
    val icon: IconConfig = IconConfig(),
    val webview: WebViewSettings = WebViewSettings()
) {

    /**
     * 还原为领域模型 [Project]。
     *
     * @param projectDir 项目目录（files/packager/projects/{id}），用于把相对图标路径
     *   拼接回绝对路径，保持 Project.iconPath 为绝对路径（UI / ApkBuilder 依赖）
     */
    fun toProject(projectDir: File): Project = Project(
        id = meta.id,
        name = meta.name,
        packageName = buildConfig.packageName,
        versionName = buildConfig.versionName,
        versionCode = buildConfig.versionCode,
        type = ProjectType.fromName(meta.type),
        iconPath = icon.iconPath?.let { File(projectDir, it).absolutePath },
        bridgeEnabled = bridge.enabled,
        bridgeCapabilities = bridge.capabilities,
        permissions = permissions,
        webViewSettings = webview,
        displaySettings = display,
        splashSettings = splash,
        createdAt = meta.createdAt,
        updatedAt = meta.updatedAt,
        lastBuildAt = meta.lastBuildAt,
        lastBuildStatus = meta.lastBuildStatus?.let { raw ->
            runCatching { BuildStatus.valueOf(raw) }.getOrNull()
        }
    )

    companion object {
        /**
         * 从领域模型 [Project] 构造持久化 DTO。
         *
         * 图标固定落在项目根目录，故绝对路径仅取 [File.name] 作为相对文件名持久化。
         */
        fun fromProject(project: Project): ProjectConfig = ProjectConfig(
            meta = MetaConfig(
                id = project.id,
                name = project.name,
                type = project.type.name,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                lastBuildAt = project.lastBuildAt,
                lastBuildStatus = project.lastBuildStatus?.name
            ),
            buildConfig = BuildConfigDto(
                packageName = project.packageName,
                versionName = project.versionName,
                versionCode = project.versionCode
            ),
            permissions = project.permissions,
            bridge = BridgeDto(
                enabled = project.bridgeEnabled,
                capabilities = project.bridgeCapabilities
            ),
            display = project.displaySettings,
            splash = project.splashSettings,
            icon = IconConfig(
                iconPath = project.iconPath?.let { absolute ->
                    // 图标固定在项目根目录，持久化仅存文件名
                    File(absolute).name.takeIf { it.isNotBlank() }
                }
            ),
            webview = project.webViewSettings
        )
    }
}

/** 项目元信息 */
@Serializable
data class MetaConfig(
    val id: String,
    val name: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastBuildAt: Long? = null,
    val lastBuildStatus: String? = null
)

/** 构建相关配置（包名 / 版本） */
@Serializable
data class BuildConfigDto(
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)

/** JS Bridge 开关 */
@Serializable
data class BridgeDto(
    val enabled: Boolean,
    val capabilities: BridgeCapabilities = BridgeCapabilities()
)

/** 应用图标配置（相对项目目录的文件名） */
@Serializable
data class IconConfig(
    val iconPath: String? = null
)
