package com.mini.me_core.feature.packager.domain.repository

import com.mini.me_core.feature.packager.domain.model.BuildRecord
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 构建记录存储端口。实现（JSON 文件持久化 + 内存缓存）位于 data 层。
 *
 * 每个项目的构建记录存储在项目目录下的 builds/ 目录中。
 */
interface BuildStore {

    val buildRecords: StateFlow<Map<String, List<BuildRecord>>>

    /** 获取指定项目的构建记录列表 */
    fun getBuildsForProject(projectId: String): List<BuildRecord>

    /** 加载指定项目的所有构建记录 */
    suspend fun loadBuilds(projectId: String): List<BuildRecord>

    /** 保存构建记录 */
    suspend fun saveBuildRecord(record: BuildRecord): BuildRecord

    /** 获取构建目录 */
    fun getBuildDir(record: BuildRecord): File

    /** 获取构建日志文件路径 */
    fun getLogFile(record: BuildRecord): File

    /** 获取输出 APK 文件路径 */
    fun getOutputApkFile(record: BuildRecord): File

    /** 删除构建记录及其产物 */
    suspend fun deleteBuildRecord(record: BuildRecord)
}
