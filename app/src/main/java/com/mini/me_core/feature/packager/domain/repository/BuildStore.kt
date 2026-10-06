package com.mini.me_core.feature.packager.domain.repository

import android.content.Context
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 构建记录存储仓库
 *
 * 负责构建记录的持久化（JSON 文件）和内存缓存。
 * 每个项目的构建记录存储在项目目录下的 builds/ 目录中。
 */
class BuildStore(
    private val context: Context,
    private val projectStore: ProjectStore
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _buildRecords = MutableStateFlow<Map<String, List<BuildRecord>>>(emptyMap())
    val buildRecords: StateFlow<Map<String, List<BuildRecord>>> = _buildRecords.asStateFlow()

    /**
     * 获取指定项目的构建记录列表
     */
    fun getBuildsForProject(projectId: String): List<BuildRecord> =
        _buildRecords.value[projectId] ?: emptyList()

    /**
     * 加载指定项目的所有构建记录
     */
    suspend fun loadBuilds(projectId: String): List<BuildRecord> = withContext(Dispatchers.IO) {
        val buildsDir = projectStore.getBuildsDir(projectId)
        val list = mutableListOf<BuildRecord>()

        buildsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val recordFile = File(dir, "build.json")
                if (recordFile.exists()) {
                    runCatching {
                        val stored = json.decodeFromString<StoredBuildRecord>(recordFile.readText())
                        list.add(stored.toBuildRecord())
                    }
                }
            }
        }

        // 按开始时间降序排列
        list.sortByDescending { it.startTime }
        _buildRecords.value = _buildRecords.value + (projectId to list)
        list
    }

    /**
     * 保存构建记录
     */
    suspend fun saveBuildRecord(record: BuildRecord): BuildRecord = withContext(Dispatchers.IO) {
        val buildsDir = projectStore.getBuildsDir(record.projectId)
        // 构建目录名使用时间戳
        val buildDir = File(buildsDir, record.startTime.toString()).apply { mkdirs() }
        val recordFile = File(buildDir, "build.json")
        recordFile.writeText(json.encodeToString(StoredBuildRecord.fromBuildRecord(record)))

        // 更新内存缓存
        val currentList = _buildRecords.value[record.projectId] ?: emptyList()
        val updatedList = (listOf(record) + currentList.filterNot { it.id == record.id })
            .sortedByDescending { it.startTime }
        _buildRecords.value = _buildRecords.value + (record.projectId to updatedList)

        record
    }

    /**
     * 获取构建目录
     */
    fun getBuildDir(record: BuildRecord): File =
        File(projectStore.getBuildsDir(record.projectId), record.startTime.toString())

    /**
     * 获取构建日志文件路径
     */
    fun getLogFile(record: BuildRecord): File =
        File(getBuildDir(record), "build.log")

    /**
     * 获取输出 APK 文件路径
     */
    fun getOutputApkFile(record: BuildRecord): File =
        File(getBuildDir(record), "output.apk")

    /**
     * 删除构建记录及其产物
     */
    suspend fun deleteBuildRecord(record: BuildRecord) = withContext(Dispatchers.IO) {
        getBuildDir(record).deleteRecursively()
        val currentList = _buildRecords.value[record.projectId] ?: emptyList()
        val updatedList = currentList.filterNot { it.id == record.id }
        _buildRecords.value = _buildRecords.value + (record.projectId to updatedList)
    }

    /**
     * 用于 JSON 序列化的构建记录存储格式
     */
    @Serializable
    private data class StoredBuildRecord(
        val id: String,
        val projectId: String,
        val versionName: String,
        val versionCode: Int,
        val status: String,
        val startTime: Long,
        val endTime: Long? = null,
        val durationMs: Long? = null,
        val apkSize: Long? = null,
        val apkPath: String? = null,
        val logPath: String? = null,
        val errorMessage: String? = null
    ) {
        fun toBuildRecord(): BuildRecord = BuildRecord(
            id = id,
            projectId = projectId,
            versionName = versionName,
            versionCode = versionCode,
            status = runCatching { com.mini.me_core.feature.packager.domain.model.BuildStatus.valueOf(status) }
                .getOrElse { com.mini.me_core.feature.packager.domain.model.BuildStatus.FAILED },
            startTime = startTime,
            endTime = endTime,
            durationMs = durationMs,
            apkSize = apkSize,
            apkPath = apkPath,
            logPath = logPath,
            errorMessage = errorMessage
        )

        companion object {
            fun fromBuildRecord(record: BuildRecord): StoredBuildRecord = StoredBuildRecord(
                id = record.id,
                projectId = record.projectId,
                versionName = record.versionName,
                versionCode = record.versionCode,
                status = record.status.name,
                startTime = record.startTime,
                endTime = record.endTime,
                durationMs = record.durationMs,
                apkSize = record.apkSize,
                apkPath = record.apkPath,
                logPath = record.logPath,
                errorMessage = record.errorMessage
            )
        }
    }
}
