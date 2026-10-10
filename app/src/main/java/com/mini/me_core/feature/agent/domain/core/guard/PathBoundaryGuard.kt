package com.mini.me_core.feature.agent.domain.core.guard

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路径边界护栏：禁止文件类工具读写工作区根目录之外的路径。
 *
 * 安全语义（secure by default / fail-closed）：
 *  - 默认开启（[NormFlowSettingsRepository.guardPathBoundaryEnabledFlow] 默认值为 true）；
 *  - 覆盖 readFile / writeFile / editFile 等文件类工具，不再只管写；
 *  - projectRoot 为空（未选择工作区）时对文件类工具直接 BLOCK，而不是放行；
 *  - 判定用「canonical 后等于根 或以 根+分隔符 开头」的边界，杜绝
 *    `/workspace-evil` 这类兄弟前缀绕过，`..` 与符号链接由 canonicalPath 解析。
 */
@Singleton
class PathBoundaryGuard @Inject constructor(
    private val settings: NormFlowSettingsRepository
) : ToolGuard {

    override val id = "path-boundary"

    /** 受本护栏约束的文件类工具名。 */
    private val fileTools = setOf("readFile", "writeFile", "editFile", "createFile", "listDir")

    override suspend fun guard(ctx: ToolGuardContext): ToolGuardResult {
        if (ctx.toolName !in fileTools) return ToolGuardResult.Pass
        if (!settings.isGuardPathBoundaryActive()) return ToolGuardResult.Pass

        val projectRoot = ctx.projectRoot
        // fail-closed：未选定工作区时文件类工具一律拒绝（旧行为是 Pass）。
        if (projectRoot.isBlank()) {
            return ToolGuardResult.Block(
                code = "PATH_BOUNDARY",
                message = "未设置工作区根目录，文件操作已拒绝（避免读写越界路径）。"
            )
        }

        val path = ctx.argString("path")?.trim().orEmpty()
        if (path.isBlank()) {
            return ToolGuardResult.Block(
                code = "PATH_BOUNDARY",
                message = "文件操作缺少 path 参数，已拒绝。"
            )
        }

        val normalizedPath = normalizePath(path, projectRoot)
        val normalizedRoot = File(projectRoot).canonicalPath.trimEnd(File.separatorChar)

        if (normalizedPath != normalizedRoot &&
            !normalizedPath.startsWith(normalizedRoot + File.separatorChar)
        ) {
            return ToolGuardResult.Block(
                code = "PATH_BOUNDARY",
                message = "路径越界：$path 不在工作区 $normalizedRoot 之内，已阻止。"
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
