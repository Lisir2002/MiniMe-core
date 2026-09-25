package com.mini.me_core.feature.terminal.domain

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一条容器快照记录。 */
data class ContainerSnapshot(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val archivePath: String,
)

/**
 * F3.8 容器快照管理：tar 打包 rootfs（排除临时文件），存到容器外部 app 私有目录 snapshots/。
 * 最多保留 [MAX_SNAPSHOTS] 个，超出自动删最旧。
 */
@Singleton
class ContainerSnapshotManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val installer: ContainerInstaller,
) {
    companion object { private const val TAG = "ContainerSnapshot" }

    private val snapshotDir: File
        get() = File(context.filesDir, "container_snapshots").apply { mkdirs() }

    fun list(): List<ContainerSnapshot> = snapshotDir.listFiles { f -> f.extension == "tar" }
        ?.map { f ->
            ContainerSnapshot(
                id = f.nameWithoutExtension,
                name = f.nameWithoutExtension,
                description = "",
                createdAt = f.lastModified(),
                sizeBytes = f.length(),
                archivePath = f.absolutePath,
            )
        }
        ?.sortedByDescending { it.createdAt }
        ?: emptyList()

    /** 创建快照：tar 打包当前 rootfs，排除 /tmp、/var/cache/apk 等临时文件。 */
    suspend fun create(name: String, description: String): Result<ContainerSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rootfs = installer.rootfsDirFor(ContainerProfile.BUILTIN_ALPINE)
                val id = "snap-${System.currentTimeMillis()}"
                val target = File(snapshotDir, "$id.tar")
                // tar 打包 rootfs，排除临时目录
                val excludeArgs = listOf(
                    "--exclude=tmp", "--exclude=proc", "--exclude=sys",
                    "--exclude=dev", "--exclude=var/cache/apk"
                ).joinToString(" ")
                val pb = ProcessBuilder(
                    "/system/bin/tar", "-cf", target.absolutePath,
                    *excludeArgs.split(" ").toTypedArray(),
                    "-C", rootfs.parent, rootfs.name
                )
                pb.redirectErrorStream(true)
                val p = pb.start()
                p.inputStream.readBytes()
                val code = p.waitFor()
                if (code != 0) throw IllegalStateException("tar exit=$code")
                // 超限自动删最旧
                val snaps = list().toMutableList()
                if (snaps.size >= 5) {
                    snaps.lastOrNull()?.let { File(it.archivePath).delete() }
                }
                ContainerSnapshot(id, name.ifBlank { id }, description,
                    System.currentTimeMillis(), target.length(), target.absolutePath)
            }.onFailure { FileLogger.w(TAG, "创建快照失败", it) }
        }

    /** 恢复快照：解压 tar 覆盖 rootfs（恢复前调用方应先确认）。 */
    suspend fun restore(snapshot: ContainerSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfs = installer.rootfsDirFor(ContainerProfile.BUILTIN_ALPINE)
            val pb = ProcessBuilder(
                "/system/bin/tar", "-xf", snapshot.archivePath,
                "-C", rootfs.parent
            )
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.inputStream.readBytes()
            val code = p.waitFor()
            if (code != 0) throw IllegalStateException("tar restore exit=$code")
        }
    }

    suspend fun delete(snapshot: ContainerSnapshot) = withContext(Dispatchers.IO) {
        File(snapshot.archivePath).delete()
    }
}
