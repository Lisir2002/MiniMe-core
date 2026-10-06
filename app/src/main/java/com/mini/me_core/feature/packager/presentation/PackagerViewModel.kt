package com.mini.me_core.feature.packager.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.packager.domain.engine.ApkBuilder
import com.mini.me_core.feature.packager.domain.model.BuildRecord
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.domain.model.ProjectType
import com.mini.me_core.feature.packager.domain.repository.BuildStore
import com.mini.me_core.feature.packager.domain.repository.ProjectStore
import com.mini.me_core.feature.packager.util.PinyinUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // ========== 项目列表 ==========
    val projects: StateFlow<List<Project>> = projectStore.projects

    // ========== 当前选中项目 ==========
    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    // ========== 新建项目向导状态 ==========
    data class NewProjectState(
        val step: Int = 0,
        val projectType: ProjectType = ProjectType.HTML,
        val appName: String = "",
        val packageName: String = "",
        val versionName: String = Project.DEFAULT_VERSION_NAME,
        val htmlContent: String = "",
        val packageNameError: String? = null
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
     * 删除项目
     */
    fun deleteProject(projectId: String) {
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
     * 更新 HTML 内容
     */
    fun updateHtmlContent(content: String) {
        _newProjectState.value = _newProjectState.value.copy(htmlContent = content)
    }

    /**
     * 向导下一步
     */
    fun nextStep() {
        val state = _newProjectState.value
        if (state.step < 4) {
            _newProjectState.value = state.copy(step = state.step + 1)
        }
    }

    /**
     * 向导上一步
     */
    fun prevStep() {
        val state = _newProjectState.value
        if (state.step > 0) {
            _newProjectState.value = state.copy(step = state.step - 1)
        }
    }

    /**
     * 创建项目
     */
    suspend fun createProject(): Project? {
        val state = _newProjectState.value
        if (state.appName.isBlank() || state.packageName.isBlank()) return null
        if (state.packageNameError != null) return null

        return projectStore.createProject(
            name = state.appName,
            packageName = state.packageName,
            type = state.projectType,
            versionName = state.versionName,
            htmlContent = state.htmlContent
        )
    }

    // ========== 构建 ==========

    /**
     * 开始构建
     */
    fun startBuild(project: Project) {
        if (_buildState.value.isBuilding) return

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
     * 安装 APK（通过 Intent）
     */
    fun installApk(apkFile: File) {
        val context = getApplication<Application>()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                ),
                "application/vnd.android.package-archive"
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
