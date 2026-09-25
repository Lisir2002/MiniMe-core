package com.mini.me_core.feature.agent.domain.guard

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路径边界护栏（P2）：禁止 writeFile/editFile 写入工作区根目录之外的路径。
 *
 * 由 guard_path_boundary_enabled 独立开关控制（默认关）。
 * projectRoot 为空时不拦截（未选择工作区）。
 */
@Singleton
class PathBoundaryGuard @Inject constructor(
    private val settings: NormFlowSettingsRepository
) : ToolGuard {

    override val id = "path-boundary"

    override suspend fun guard(ctx: ToolGuardContext): ToolGuardResult {
        if (ctx.toolName != "writeFile" && ctx.toolName != "editFile") return ToolGuardResult.Pass
        if (!settings.isGuardPathBoundaryActive()) return ToolGuardResult.Pass
        val projectRoot = ctx.projectRoot
        if (projectRoot.isBlank()) return ToolGuardResult.Pass

        val path = ctx.argString("path")?.trim() ?: return ToolGuardResult.Pass
        if (path.isBlank()) return ToolGuardResult.Pass

        // 规范化路径：处理相对路径、../ 等
        val normalizedPath = normalizePath(path, projectRoot)
        val normalizedRoot = File(projectRoot).canonicalPath

        if (!normalizedPath.startsWith(normalizedRoot)) {
            return ToolGuardResult.Block(
                code = "PATH_BOUNDARY",
                message = "路径越界：$path 不在工作区 $normalizedRoot 之内，已阻止写入。"
            )
        }
        return ToolGuardResult.Pass
    }

    /** 将用户路径规范化为绝对路径（基于 projectRoot 解析相对路径和 ../）。 */
    private fun normalizePath(path: String, projectRoot: String): String {
        val file = if (File(path).isAbsolute) File(path) else File(projectRoot, path)
        return file.canonicalPath
    }
}
