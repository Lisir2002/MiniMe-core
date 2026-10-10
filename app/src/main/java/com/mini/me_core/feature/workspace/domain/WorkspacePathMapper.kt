package com.mini.me_core.feature.workspace.domain

import com.mini.me_core.core.container.api.ContainerInstallerPort
import com.mini.me_core.core.container.api.ContainerProfile
import com.mini.me_core.core.security.SecurityAuditSink
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.settings.data.repository.ContainerSettingsRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路径越界异常：映射结果逃逸出允许的授权根时抛出（由调用方的 guard/工具链转成拒绝结果）。
 */
class PathTraversalException(message: String) : SecurityException(message)

/**
 * 授权根判定的纯逻辑（无 IO，可直接单元测试）。
 *
 * 边界规则：[childCanon] 「等于 [rootCanon]」或「以 rootCanon + 分隔符 开头」才视为在根内。
 * 刻意不用简单 startsWith——`/data/x/workspace` 不能放行 `/data/x/workspace-evil` 这类
 * 兄弟前缀绕过；[File.canonicalPath] 已解析 `..` 与符号链接。
 */
object PathBoundary {

    fun isWithin(childCanon: String, rootCanon: String): Boolean {
        val child = childCanon.trim()
        val root = rootCanon.trim().trimEnd(File.separatorChar)
        if (root.isEmpty()) return false
        if (child == root) return true
        return child.startsWith(root + File.separatorChar)
    }
}

/**
 * 在「容器内路径」与「宿主真实路径」之间互转，让 AI 只看到 / 只使用容器路径。
 *
 * 背景：容器是 PRoot 以当前 profile 的 rootfs 目录为根（`-r rootfs`）跑起来的，
 * 当前工作区目录又被 bind 成容器内的 [CONTAINER_ROOT]（`~/workspace`，展开为 `$HOME/workspace`）。因此「容器内路径」到「宿主真实文件」有三条确定映射：
 * - `~/workspace[/…]` → 宿主工作区目录（写它即写宿主，且容器内可见——bind mount）；
 * - `/root/.minime[/…]` → 宿主 AI 配置目录（skill / mcp.json，独立于 rootfs）；
 * - 其它容器绝对路径 `/etc/…`、`/root/…` → 当前 profile rootfs 目录下对应文件（只读授权区）。
 *
 * **安全边界**：[toHostFile] 对最终结果取 [File.canonicalPath]（解析 `..` 与符号链接），
 * 断言其仍在本次映射所对应的授权根内（分隔符边界，杜绝兄弟前缀绕过）；越界抛
 * [PathTraversalException]。读、写都校验；写操作进一步收紧——只允许落到工作区 /
 * AI 配置目录，不允许直接写 rootfs 系统区。
 */
@Singleton
class WorkspacePathMapper @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val containerInstaller: ContainerInstallerPort,
    private val containerSettingsRepository: ContainerSettingsRepository,
    private val auditSink: SecurityAuditSink,
) {
    private val auditScope = CoroutineScope(Dispatchers.IO)
    /** 容器内真实 home 路径（容器就绪后查 $HOME 缓存），供展开 ~。未就绪前回退 /root。 */
    @Volatile
    var containerHome: String? = null
    companion object {
        /** AI 看到的工作区根路径。用 `~` 形式让提示词/工具描述更自然，内部用 [resolvedContainerRoot] 展开后匹配。 */
        const val CONTAINER_ROOT = "~/workspace"
        /** AI 配置目录在容器内的路径，绑定到宿主 [ContainerInstaller.minimeDir]（独立于 rootfs）。 */
        const val MINIME_ROOT = "/root/.minime"
        private const val TAG = "WorkspacePathMapper"
    }

    /**
     * 当前选中的 profile（缓存，避免同步读 DataStore）。启动首帧为内置 Alpine，等同改动前。
     */
    @Volatile
    private var currentProfile: ContainerProfile = ContainerProfile.BUILTIN_ALPINE

    init {
        CoroutineScope(Dispatchers.IO).launch {
            containerSettingsRepository.activeProfileIdFlow.collect { id ->
                currentProfile = resolveProfile(id)
            }
        }
    }

    private suspend fun resolveProfile(id: String): ContainerProfile = when (id) {
        ContainerProfile.BUILTIN_ID -> ContainerProfile.BUILTIN_ALPINE
        ContainerProfile.BUILTIN_X86_ID -> ContainerProfile.BUILTIN_ALPINE_X86
        else -> containerSettingsRepository.customProfilesFlow
            .first()
            .firstOrNull { it.id == id } ?: ContainerProfile.BUILTIN_ALPINE
    }

    /** 当前工作区在宿主上的根目录。 */
    private fun hostRoot(): File = File(workspaceRepository.currentPath())

    /** [CONTAINER_ROOT] 展开后的绝对路径（`$HOME/workspace`），供路径匹配使用。`containerHome` 未就绪时回退 `/root`。 */
    private fun resolvedContainerRoot(): String =
        (containerHome ?: "/root").trimEnd('/') + "/workspace"

    /** 容器 rootfs 在宿主上的根目录（容器内 `/` 即此目录，随当前 profile 变化）。 */
    private fun rootfsRoot(): File = containerInstaller.rootfsDirFor(currentProfile)

    /** AI 配置目录在宿主上的根（容器内 `/root/.minime` 即此目录，独立于 rootfs）。 */
    private fun minimeRoot(): File = containerInstaller.minimeDir

    /** 映射结果连同其授权根（用于 canonical 边界断言）。 */
    private data class Mapped(val file: File, val authorizedRoot: File, val role: RootRole)
    private enum class RootRole { WORKSPACE, MINIME, ROOTFS }

    private fun mapUnchecked(path: String): Mapped {
        val root = hostRoot()
        val wsRoot = resolvedContainerRoot()
        val p = path.trim().let {
            if (it.startsWith("~/")) {
                val home = containerHome
                if (home != null) home.trimEnd('/') + "/" + it.removePrefix("~/") else "/root/" + it.removePrefix("~/")
            } else it
        }
        return when {
            p == wsRoot || p == "$wsRoot/" || p == CONTAINER_ROOT || p == "$CONTAINER_ROOT/" ->
                Mapped(root, root, RootRole.WORKSPACE)
            p.startsWith("$wsRoot/") ->
                Mapped(File(root, p.removePrefix("$wsRoot/")), root, RootRole.WORKSPACE)
            p == MINIME_ROOT || p == "$MINIME_ROOT/" ->
                Mapped(minimeRoot(), minimeRoot(), RootRole.MINIME)
            p.startsWith("$MINIME_ROOT/") ->
                Mapped(File(minimeRoot(), p.removePrefix("$MINIME_ROOT/")), minimeRoot(), RootRole.MINIME)
            p.startsWith("/") ->
                Mapped(File(rootfsRoot(), p.removePrefix("/")), rootfsRoot(), RootRole.ROOTFS)
            else ->
                Mapped(File(root, p), root, RootRole.WORKSPACE)
        }
    }

    /**
     * 把 AI 提供的路径解析为宿主真实文件（读语义）。兼容以下写法：
     * - 容器绝对路径 `~/workspace[/…]` → 映射到宿主工作区；
     * - `/root/.minime[/…]` → 映射到宿主 AI 配置目录；
     * - 其它容器绝对路径 `/etc/…`、`/root/…` → 映射到 rootfs 内对应文件（只读授权）；
     * - 相对路径 `src/Main.kt` → 挂到宿主工作区根下。
     *
     * `/root/.minime` 必须先于通用 `/`→rootfs 规则匹配。
     *
     * @throws PathTraversalException 规范化后的路径逃逸出授权根（`../`、符号链接、兄弟前缀）。
     */
    fun toHostFile(path: String): File = toHostFile(path, forWrite = false)

    /**
     * 写语义版本：除 canonical 边界断言外，还拒绝落到 rootfs 系统区（写只允许工作区 / AI 配置目录）。
     */
    fun toHostFile(path: String, forWrite: Boolean): File {
        val mapped = mapUnchecked(path)
        val canonical = mapped.file.canonicalPath
        val rootCanon = mapped.authorizedRoot.canonicalPath
        if (!PathBoundary.isWithin(canonical, rootCanon)) {
            FileLogger.w(TAG, "路径越界被拒绝: '$path' -> $canonical（授权根 $rootCanon）")
            auditScope.launch {
                auditSink.recordSecurityEvent(
                    category = "SECURITY",
                    action = "PATH_TRAVERSAL_BLOCKED",
                    success = false,
                    message = "toHostFile '$path' -> $canonical 越出 $rootCanon",
                )
            }
            throw PathTraversalException("路径越界：$path 解析后逃逸出授权根 $rootCanon")
        }
        if (forWrite && mapped.role == RootRole.ROOTFS) {
            FileLogger.w(TAG, "写操作拒绝落到 rootfs: '$path' -> $canonical")
            auditScope.launch {
                auditSink.recordSecurityEvent(
                    category = "SECURITY",
                    action = "ROOTFS_WRITE_BLOCKED",
                    success = false,
                    message = "写 '$path' -> $canonical 被限制（仅 workspace/minime 可写）",
                )
            }
            throw PathTraversalException("写操作被限制在工作区 / AI 配置目录内，rootfs 系统区只读：$path")
        }
        FileLogger.v(TAG, "toHostFile '$path' -> $canonical")
        return File(canonical)
    }

    /**
     * 把宿主路径还原为容器路径：
     * - 位于工作区内 → `~/workspace/…`；
     * - 位于 AI 配置目录内 → `/root/.minime/…`；
     * - 位于 rootfs 内 → 去掉 rootfs 前缀的容器绝对路径；
     * - 其余原样返回。
     *
     * 同样先取 canonicalPath，并用分隔符边界判断。
     */
    fun toContainerPath(hostPath: String): String {
        val abs = File(hostPath).canonicalPath
        val rootPath = hostRoot().canonicalPath
        val minimePath = minimeRoot().canonicalPath
        val rootfsPath = rootfsRoot().canonicalPath
        return when {
            abs == rootPath -> CONTAINER_ROOT
            PathBoundary.isWithin(abs, rootPath) ->
                CONTAINER_ROOT + "/" + abs.removePrefix("$rootPath" + File.separator)
            abs == resolvedContainerRoot().let { File(it).canonicalPath } -> CONTAINER_ROOT
            PathBoundary.isWithin(abs, File(resolvedContainerRoot()).canonicalPath) ->
                CONTAINER_ROOT + "/" + abs.removePrefix(File(resolvedContainerRoot()).canonicalPath + File.separator)
            abs == minimePath -> MINIME_ROOT
            PathBoundary.isWithin(abs, minimePath) ->
                MINIME_ROOT + "/" + abs.removePrefix("$minimePath" + File.separator)
            abs == rootfsPath -> "/"
            PathBoundary.isWithin(abs, rootfsPath) ->
                "/" + abs.removePrefix("$rootfsPath" + File.separator)
            else -> hostPath
        }
    }
}
