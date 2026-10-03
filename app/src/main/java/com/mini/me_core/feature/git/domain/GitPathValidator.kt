package com.mini.me_core.feature.git.domain

import java.io.File

/**
 * 工作区内路径的安全校验工具，防御路径遍历（path traversal）与符号链接逃逸。
 *
 * 所有把「外部传入的相对路径」拼到工作区根目录下读取/操作文件的入口，都必须先经过本工具规范化并
 * 确认结果仍落在 [workspaceRoot] 之内，否则 `../../etc/passwd`、指向工作区外的符号链接等会被解析到
 * 仓库边界之外。
 *
 * 关键：必须用 [File.getCanonicalFile] 做解析——它同时规范化 `.`/`..` 分量并**解析符号链接**，
 * 能挡住「工作区内一个指向外部的软链」这类仅靠 [File.getAbsoluteFile] 挡不住的逃逸。
 */
object GitPathValidator {

    /**
     * 把 [path] 拼到 [workspaceRoot] 下并做 canonical 规范化，校验结果仍在工作区之内。
     *
     * @throws IllegalArgumentException 当规范化后的路径落在 [workspaceRoot] 之外（含 `..` 上溯、
     *   符号链接指向外部、或绝对路径直接指到外部）时抛出，调用方据此拒绝该路径。
     * @return 规范化后的 [File]，可直接用于后续读写。
     */
    fun requireWithinWorkspace(workspaceRoot: File, path: String): File {
        val rootCanonical = workspaceRoot.canonicalFile
        val resolved = File(rootCanonical, path).canonicalFile
        // 前缀比较补上分隔符边界：避免 "/work-evil" 被误判成 "/work" 的子路径。
        val inside = resolved == rootCanonical ||
            resolved.path.startsWith(rootCanonical.path + File.separator)
        if (!inside) {
            throw IllegalArgumentException("路径逃逸工作区: 输入=$path 规范化=${resolved.path} 根=${rootCanonical.path}")
        }
        return resolved
    }

    /**
     * [requireWithinWorkspace] 的安全版本：校验失败（含 IO 异常、非法路径）返回 null，
     * 而不抛出。调用方据此静默降级（如返回空内容）并自行记录警告日志。
     */
    fun resolveWithinWorkspaceOrNull(workspaceRoot: File, path: String): File? =
        runCatching { requireWithinWorkspace(workspaceRoot, path) }.getOrNull()
}
