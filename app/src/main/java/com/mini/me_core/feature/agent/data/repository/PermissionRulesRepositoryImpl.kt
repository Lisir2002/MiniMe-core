package com.mini.me_core.feature.agent.data.repository

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionFile
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRule
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionScope
import com.mini.me_core.feature.agent.domain.execution.permission.ProjectRuleValidator
import com.mini.me_core.feature.agent.domain.execution.permission.toPermissionFile
import com.mini.me_core.feature.agent.domain.execution.permission.toRuleList
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具授权规则的持久化。全局规则存 app 私有目录 (`filesDir/minime/permissions.json`)，
 * 项目级规则存工作区目录 (`.minime/permissions.json`)，方便团队通过 git 共享。
 *
 * 文件格式为紧凑的 `Tool(pattern)` 风格，示例：
 * ```json
 * {
 *   "permissions": {
 *     "allow": ["Bash(git pull)", "writeFile"],
 *     "deny": ["Bash(rm -rf /)"]
 *   }
 * }
 * ```
 *
 * 安全要点：全局规则存在 app 私有目录，AI 无法篡改；项目级规则存在工作区内，
 * 可被 AI 修改，但作为项目级声明式配置这是有意为之（可 git 追踪/回滚）。
 *
 * 并发模式参考 [McpConfigRepositoryImpl]：Mutex 保护文件 IO + MutableStateFlow 缓存。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("ClassOrdering") // 行为保持搬迁：companion/属性顺序沿用原 domain 类，不重排
@Singleton
class PermissionRulesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workspaceRepository: WorkspaceRepository
) : PermissionRulesRepository {
    private companion object {
        const val TAG = "PermissionRules"
        const val PERMISSIONS_FILE = "permissions.json"
        const val MINIME_DIR = ".minime"
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    }

    /** 全局权限文件：`filesDir/minime/permissions.json`，与 mcp.json 同级。 */
    private val globalFile: File
        get() = File(File(context.filesDir, "minime"), PERMISSIONS_FILE)

    /** 当前工作区的项目级权限文件：`workspacePath/.minime/permissions.json`。 */
    private fun projectFileForPath(workspacePath: String): File =
        File(File(workspacePath, MINIME_DIR), PERMISSIONS_FILE)

    // ── 内存缓存与响应式流 ──────────────────────────────────────

    private val globalState = MutableStateFlow<List<PermissionRule>?>(null)
    private val projectStates = ConcurrentHashMap<String, MutableStateFlow<List<PermissionRule>?>>()
    private val mutex = Mutex()

    private fun getProjectState(workspacePath: String): MutableStateFlow<List<PermissionRule>?> =
        projectStates.getOrPut(workspacePath) { MutableStateFlow(null) }

    // ── 懒加载 ──────────────────────────────────────────────────

    private suspend fun ensureGlobalLoaded() {
        if (globalState.value != null) return
        mutex.withLock {
            if (globalState.value != null) return
            val loaded = loadFromFile(globalFile)
            globalState.value = loaded
            // 一次性清理：ShellCommandParser 旧版不识别 fd 重定向，产生 pattern 为纯数字的垃圾授权规则（如 Bash(1)）。
            // 幂等过滤：pattern 为纯数字的规则一律移除，不误删正常规则（apk/git/cd 等均非纯数字）。
            val junk = loaded.filter { it.pattern.all { ch -> ch.isDigit() } && it.pattern.isNotEmpty() }
            if (junk.isNotEmpty()) {
                val cleaned = loaded - junk.toSet()
                withContext(Dispatchers.IO) { writeToFile(globalFile, cleaned) }
                globalState.value = cleaned
                FileLogger.i(TAG, "清理垃圾授权规则 ${junk.size} 条（fd 重定向解析缺陷产生的纯数字 pattern）")
            }
        }
    }

    private suspend fun ensureProjectLoaded(workspacePath: String) {
        val state = getProjectState(workspacePath)
        if (state.value != null) return
        mutex.withLock {
            if (state.value != null) return
            state.value = loadFromFile(projectFileForPath(workspacePath), enforceProjectRulePolicy = true)
        }
    }

    private suspend fun loadFromFile(file: File, enforceProjectRulePolicy: Boolean = false): List<PermissionRule> =
        withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext emptyList()
            runCatching {
                JSON.decodeFromString<PermissionFile>(file.readText()).toRuleList()
            }.getOrElse {
                FileLogger.w(TAG, "读取 ${file.name} 失败: ${it.message}")
                emptyList()
            }.let { rules ->
                if (!enforceProjectRulePolicy) return@let rules
                // 项目规则落在可被 AI 改写的工作区内：禁止整工具 shell ALLOW（防自我提权）。
                val (valid, invalid) = ProjectRuleValidator.filterProjectRules(rules)
                if (invalid.isNotEmpty()) {
                    FileLogger.w(
                        TAG,
                        "项目权限文件忽略 ${invalid.size} 条非法整工具 ALLOW 规则: " +
                            invalid.joinToString("; ") { "${it.rule.toolName}(${it.rule.pattern})[${it.reason}]" }
                    )
                }
                valid
            }
        }

    private fun writeToFile(file: File, rules: List<PermissionRule>) {
        file.parentFile?.mkdirs()
        file.writeText(JSON.encodeToString(PermissionFile.serializer(), rules.toPermissionFile()))
    }

    // ── 公共 API ────────────────────────────────────────────────

    override fun currentProjectName(): String? = workspaceRepository.current.value?.name

    override val currentProjectNameFlow: Flow<String?> = workspaceRepository.current.map { it?.name }

    override val currentProjectRulesFlow: Flow<List<PermissionRule>> =
        workspaceRepository.current.flatMapLatest { ws ->
            if (ws == null) flowOf(emptyList()) else projectRulesFlow(ws.name)
        }

    override val globalRulesFlow: Flow<List<PermissionRule>> = flow {
        ensureGlobalLoaded()
        emitAll(globalState.filterNotNull())
    }

    override suspend fun getGlobalRulesOnce(): List<PermissionRule> {
        ensureGlobalLoaded()
        return globalState.value ?: emptyList()
    }

    override suspend fun setGlobalRules(rules: List<PermissionRule>) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeToFile(globalFile, rules) }
            globalState.value = rules
        }
    }

    override fun projectRulesFlow(projectName: String): Flow<List<PermissionRule>> {
        val workspacePath = workspaceRepository.currentPath()
        val state = getProjectState(workspacePath)
        return flow {
            ensureProjectLoaded(workspacePath)
            emitAll(state.filterNotNull())
        }
    }

    override suspend fun loadEffectiveForCurrentProject(): List<PermissionRule> {
        ensureGlobalLoaded()
        val global = globalState.value ?: emptyList()
        val workspacePath = workspaceRepository.currentPath()
        ensureProjectLoaded(workspacePath)
        val project = getProjectState(workspacePath).value ?: emptyList()
        return project + global
    }

    override suspend fun add(scope: PermissionScope, rule: PermissionRule) {
        when (scope) {
            PermissionScope.GLOBAL -> editGlobal { if (rule !in it) it.add(rule) }
            PermissionScope.PROJECT -> {
                // 项目规则写入前同样过校验器：shell/terminal 整工具 ALLOW 拒绝落盘，防 AI 自我提权。
                val reason = ProjectRuleValidator.validate(rule)
                if (reason != null) {
                    FileLogger.w(TAG, "拒绝写入项目级整工具 ALLOW 规则[${rule.toolName} ${rule.pattern}]: $reason")
                    return
                }
                val workspacePath = workspaceRepository.currentPath()
                editProject(workspacePath) { if (rule !in it) it.add(rule) }
            }
        }
        FileLogger.i(TAG, "记忆授权规则[$scope]: ${rule.toolName} ${rule.pattern}")
    }

    override suspend fun removeGlobalRule(rule: PermissionRule) = editGlobal { it.remove(rule) }

    override suspend fun removeProjectRule(projectName: String, rule: PermissionRule) {
        val workspacePath = workspaceRepository.currentPath()
        editProject(workspacePath) { it.remove(rule) }
    }

    override suspend fun promoteToGlobal(projectName: String, rule: PermissionRule) {
        val workspacePath = workspaceRepository.currentPath()
        editProject(workspacePath) { it.remove(rule) }
        editGlobal { if (rule !in it) it.add(rule) }
        FileLogger.i(TAG, "提升为全局: ${rule.toolName} ${rule.pattern}")
    }

    // ── 内部写入 ────────────────────────────────────────────────

    private suspend fun editGlobal(mutate: (MutableList<PermissionRule>) -> Unit) {
        ensureGlobalLoaded()
        mutex.withLock {
            val list = (globalState.value ?: emptyList()).toMutableList()
            mutate(list)
            withContext(Dispatchers.IO) { writeToFile(globalFile, list) }
            globalState.value = list
        }
    }

    private suspend fun editProject(workspacePath: String, mutate: (MutableList<PermissionRule>) -> Unit) {
        ensureProjectLoaded(workspacePath)
        mutex.withLock {
            val state = getProjectState(workspacePath)
            val list = (state.value ?: emptyList()).toMutableList()
            mutate(list)
            withContext(Dispatchers.IO) { writeToFile(projectFileForPath(workspacePath), list) }
            state.value = list
        }
    }
}
