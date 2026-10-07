package com.mini.me_core.feature.packager.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.packager.domain.engine.ApkAnalyzer
import com.mini.me_core.feature.packager.domain.engine.ApkBuilder
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.ProjectType
import com.mini.me_core.feature.packager.domain.repository.BuildStore
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import com.mini.me_core.feature.packager.util.PinyinUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 应用打包器 ViewModel
 *
 * 管理项目列表、新建项目向导、构建过程等状态。
 */
@HiltViewModel
class PackagerViewModel @Inject constructor(
    application: Application,
    private val projectStore: ProjectStore,
    private val buildStore: BuildStore
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PackagerViewModel"
    }

    // ========== 项目列表 ==========
    val projects: StateFlow<List<Project>> = projectStore.projects

    // ========== 当前选中项目 ==========
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    // ========== 新建项目状态 ==========
    data class NewProjectState(
        val projectType: ProjectType = ProjectType.HTML,
        val appName: String = "",
        val packageName: String = "",
        val versionName: String = Project.DEFAULT_VERSION_NAME,
        val packageNameError: String? = null,
        val isCreating: Boolean = false
    )

    private val _newProjectState = MutableStateFlow(NewProjectState())
    val newProjectState: StateFlow<NewProjectState> = _newProjectState.asStateFlow()

    // ========== 构建状态 ==========
    data class BuildState(
        val isBuilding: Boolean = false,
        val currentStep: ApkBuilder.BuildStep? = null,
        val currentStepIndex: Int = 0,
        val totalSteps: Int = 0,
        val logs: List<String> = emptyList(),
        val resultApk: File? = null,
        val resultApkSize: Long = 0,
        val error: String? = null
    )

    private val _buildState = MutableStateFlow(BuildState())
    val buildState: StateFlow<BuildState> = _buildState.asStateFlow()

    // ========== 构建记录 ==========
    private val _buildRecords = MutableStateFlow<List<BuildRecord>>(emptyList())
    val buildRecords: StateFlow<List<BuildRecord>> = _buildRecords.asStateFlow()

    // ========== APK 分析结果 ==========
    private val _apkAnalysis = MutableStateFlow<ApkAnalyzer.ApkAnalysis?>(null)
    val apkAnalysis: StateFlow<ApkAnalyzer.ApkAnalysis?> = _apkAnalysis.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val apkBuilder = ApkBuilder(getApplication(), projectStore)

    // ========== 项目列表操作 ==========

    /**
     * 加载项目的构建记录
     */
    fun loadBuildRecords(projectId: String) {
        viewModelScope.launch {
            val records = buildStore.loadBuilds(projectId)
            _buildRecords.value = records
        }
    }

    /**
     * 选中项目
     */
    fun selectProject(project: Project) {
        _currentProject.value = project
        loadBuildRecords(project.id)
    }

    /**
     * 更新项目配置
     */
    fun updateProject(project: Project) {
        viewModelScope.launch {
            val updated = projectStore.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            if (_currentProject.value?.id == updated.id) {
                _currentProject.value = updated
            }
        }
    }

    /**
     * 切换权限
     */
    fun togglePermission(projectId: String, permission: String, enabled: Boolean) {
        val project = getProjectById(projectId) ?: return
        val newPermissions = if (enabled) {
            project.permissions + permission
        } else {
            project.permissions - permission
        }
        updateProject(project.copy(permissions = newPermissions))
    }

    /**
     * 更新 Bridge 能力模块
     */
    fun updateBridgeCapability(projectId: String, module: String, enabled: Boolean) {
        val project = getProjectById(projectId) ?: return
        val caps = project.bridgeCapabilities.let {
            when (module) {
                "ui" -> it.copy(ui = enabled)
                "device" -> it.copy(device = enabled)
                "file" -> it.copy(file = enabled)
                "network" -> it.copy(network = enabled)
                "data" -> it.copy(data = enabled)
                else -> it
            }
        }
        updateProject(project.copy(bridgeCapabilities = caps))
    }

    /**
     * 根据 ID 获取项目
     */
    fun getProjectById(projectId: String): Project? =
        projectStore.getProjectById(projectId)

    /**
     * 获取项目的 www 目录
     */
    fun getWwwDir(projectId: String): File =
        projectStore.getWwwDir(projectId)

    /**
     * 删除项目
     */
    fun deleteProject(projectId: String) {
        FileLogger.i(TAG, "删除项目: $projectId")
        viewModelScope.launch {
            projectStore.deleteProject(projectId)
            if (_currentProject.value?.id == projectId) {
                _currentProject.value = null
            }
        }
    }

    // ========== 新建项目向导 ==========

    /**
     * 开始新建项目向导
     */
    fun startNewProject() {
        _newProjectState.value = NewProjectState()
    }

    /**
     * 更新项目类型
     */
    fun updateProjectType(type: ProjectType) {
        _newProjectState.value = _newProjectState.value.copy(projectType = type)
    }

    /**
     * 更新应用名称（自动生成包名）
     */
    fun updateAppName(name: String) {
        val packageName = if (name.isNotBlank()) {
            projectStore.generateUniquePackageName(PinyinUtil.generatePackageName(name))
        } else {
            ""
        }
        _newProjectState.value = _newProjectState.value.copy(
            appName = name,
            packageName = packageName,
            packageNameError = null
        )
    }

    /**
     * 更新包名（手动编辑）
     */
    fun updatePackageName(packageName: String) {
        val error = when {
            packageName.isBlank() -> "包名不能为空"
            !Project.isValidPackageName(packageName) -> "包名格式不正确（如 com.example.app）"
            projectStore.isPackageNameExists(packageName) -> "包名已存在"
            else -> null
        }
        _newProjectState.value = _newProjectState.value.copy(
            packageName = packageName,
            packageNameError = error
        )
    }

    /**
     * 更新版本号
     */
    fun updateVersionName(version: String) {
        _newProjectState.value = _newProjectState.value.copy(versionName = version)
    }

    /**
     * 创建项目
     */
    suspend fun createProject(): Project? {
        val state = _newProjectState.value
        if (state.appName.isBlank() || state.packageName.isBlank()) return null
        if (state.packageNameError != null) return null

        FileLogger.i(TAG, "创建项目: ${state.appName} (${state.packageName}), 类型=${state.projectType}")
        _newProjectState.value = state.copy(isCreating = true)
        return try {
            projectStore.createProject(
                name = state.appName,
                packageName = state.packageName,
                type = state.projectType,
                versionName = state.versionName,
                htmlContent = ""
            )
        } finally {
            _newProjectState.value = _newProjectState.value.copy(isCreating = false)
        }
    }

    // ========== 构建 ==========

    /**
     * 开始构建
     */
    fun startBuild(project: Project) {
        if (_buildState.value.isBuilding) return

        FileLogger.i(TAG, "开始构建: ${project.name} (${project.packageName}) v${project.versionName}")
        _buildState.value = BuildState(isBuilding = true)

        viewModelScope.launch {
            val buildsDir = projectStore.getBuildsDir(project.id)
            val buildRecord = BuildRecord.createBuilding(
                projectId = project.id,
                versionName = project.versionName,
                versionCode = project.versionCode
            )
            buildStore.saveBuildRecord(buildRecord)

            val buildDir = buildStore.getBuildDir(buildRecord)

            apkBuilder.build(
                project = project,
                outputDir = buildDir,
                callback = object : ApkBuilder.BuildProgressCallback {
                    override fun onStepStart(step: ApkBuilder.BuildStep, index: Int, total: Int) {
                        _buildState.value = _buildState.value.copy(
                            currentStep = step,
                            currentStepIndex = index,
                            totalSteps = total
                        )
                    }

                    override fun onStepComplete(step: ApkBuilder.BuildStep, index: Int, total: Int) {
                        // 步骤完成不需要额外处理
                    }

                    override fun onLog(message: String) {
                        _buildState.value = _buildState.value.copy(
                            logs = _buildState.value.logs + message
                        )
                    }

                    override fun onSuccess(apkFile: File, apkSize: Long) {
                        _buildState.value = _buildState.value.copy(
                            isBuilding = false,
                            resultApk = apkFile,
                            resultApkSize = apkSize
                        )
                        viewModelScope.launch {
                            val updated = buildRecord.markSuccess(apkFile.absolutePath, apkSize, buildStore.getLogFile(buildRecord).absolutePath)
                            buildStore.saveBuildRecord(updated)
                            projectStore.updateBuildStatus(project.id, BuildStatus.SUCCESS)
                            loadBuildRecords(project.id)
                        }
                    }

                    override fun onError(error: String) {
                        _buildState.value = _buildState.value.copy(
                            isBuilding = false,
                            error = error
                        )
                        viewModelScope.launch {
                            val updated = buildRecord.markFailed(error, buildStore.getLogFile(buildRecord).absolutePath)
                            buildStore.saveBuildRecord(updated)
                            projectStore.updateBuildStatus(project.id, BuildStatus.FAILED)
                            loadBuildRecords(project.id)
                        }
                    }
                }
            )
        }
    }

    /**
     * 重置构建状态
     */
    fun resetBuildState() {
        _buildState.value = BuildState()
    }

    /**
     * 分析 APK 文件
     */
    fun analyzeApk(apkFile: File) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _apkAnalysis.value = null
            try {
                val analysis = withContext(Dispatchers.IO) {
                    ApkAnalyzer.analyze(apkFile)
                }
                _apkAnalysis.value = analysis
                FileLogger.d(TAG, "APK 分析完成: 包名=${analysis.packageName}")
            } catch (e: Exception) {
                FileLogger.e(TAG, "APK 分析失败: ${e.message}", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * 清除 APK 分析结果
     */
    fun clearApkAnalysis() {
        _apkAnalysis.value = null
    }

    /**
     * 安装 APK（通过 Intent）
     */
    fun installApk(apkFile: File) {
        val context = getApplication<Application>()
        try {
            if (!apkFile.exists()) {
                FileLogger.e(TAG, "APK 文件不存在: ${apkFile.absolutePath}")
                android.widget.Toast.makeText(context, "APK 文件不存在，请重新构建", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            FileLogger.d(TAG, "安装 APK: ${apkFile.name} (${apkFile.length()} bytes)")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            FileLogger.e(TAG, "安装 APK 失败: ${e.message}", e)
            android.widget.Toast.makeText(context, "安装失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
