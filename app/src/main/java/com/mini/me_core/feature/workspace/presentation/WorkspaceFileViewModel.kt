package com.mini.me_core.feature.workspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.agent.domain.tool.ToolEvent
import com.mini.me_core.feature.agent.domain.tool.ToolEventBus
import com.mini.me_core.feature.agent.domain.tool.ToolEventListener
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import com.mini.me_core.feature.workspace.domain.DelegatingFileAccess
import com.mini.me_core.feature.workspace.domain.FileEntry
import com.mini.me_core.feature.workspace.domain.model.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 侧边栏「工作目录 → 当前工作台」的文件浏览器数据源。
 *
 * 目录导航用「相对容器路径栈」表示：根为 `~/workspace`，进入子目录即在栈尾追加目录名，
 * 对应的容器路径 = `~/workspace/` + 栈内目录名按 `/` 连接。文件条目/子目录均经
 * [DelegatingFileAccess]（按执行模式转发本地/远程实现）读取，因此本地与远程 SSH 模式行为一致。
 *
 * 切换工作区时自动复位到根目录（目录栈清空）。
 *
 * 实时同步（问题 20）：AI 文件工具（writeFile/editFile/Bash 等）执行成功后会向单例
 * [ToolEventBus] 广播 file.* 事件。本 ViewModel 在 init 时订阅该总线，当事件涉及当前可见目录
 * 子树时调用 [refresh] 重列——只重列当前这一个目录，不做整棵工作区的递归扫描；目录栈（即
 * 展开/导航位置）在 refresh 中保持不变，因此刷新不改变用户当前所在目录与滚动位置。
 */
@HiltViewModel
class WorkspaceFileViewModel @Inject constructor(
    private val fileAccess: DelegatingFileAccess,
    private val workspaceRepository: WorkspaceRepository,
    private val toolEventBus: ToolEventBus
) : ViewModel() {

    /** 当前选中工作区，与侧边栏「所有工作台」/聊天页共享同一数据源。 */
    val currentWorkspace: StateFlow<Workspace?> = workspaceRepository.current

    /** 当前目录的「相对容器路径栈」（从工作区根开始，不含 `~/workspace` 前缀）。 */
    private val _dirStack = MutableStateFlow<List<String>>(emptyList())
    val dirStack: StateFlow<List<String>> = _dirStack.asStateFlow()

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 文件变更事件监听器：注册到 [toolEventBus]，ViewModel 销毁时反注册以防泄漏。 */
    private val fileChangeListener = ToolEventListener { event ->
        // 事件分发在总线协程上；这里只做同步的路径判定，命中即 fire-and-forget 触发重列，
        // 不阻塞总线后续监听者（StatefulAgentWorkflow 的缓存失效等）。
        if (shouldRefreshFor(event)) {
            refresh()
        }
    }

    init {
        // 工作区切换后复位到根目录并重列
        viewModelScope.launch {
            workspaceRepository.current.drop(1).collect {
                _dirStack.value = emptyList()
                refresh()
            }
        }
        // 问题 20：订阅 AI 文件操作事件，文件树实时跟随 AI 创建/删除/修改。
        toolEventBus.subscribe(fileChangeListener)
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        toolEventBus.unsubscribe(fileChangeListener)
    }

    /**
     * 判断文件变更事件是否需要重列「当前可见目录」。
     *
     * 规则：当前视图是单目录列表，只展示 [currentContainerPath] 的直接子项。只要变更发生在
     * 当前目录本身或其任意子层级下（含 AI 在新子目录里写文件、使该新目录出现在当前列表中），
     * 就重列；变更落在其它工作台/目录子树则跳过，避免无意义的 IO。
     *
     * [ToolEvent.FileSystemMutated]（Bash 等 shell 命令）无法静态定位具体文件，按保守策略：
     *  shell 工作目录即工作区，重列当前可见目录即可（仍是单目录，非整树扫描）。
     */
    private fun shouldRefreshFor(event: ToolEvent): Boolean {
        val changedPath: String? = when (event) {
            is ToolEvent.FileWritten -> event.path
            is ToolEvent.FileEdited -> event.path
            is ToolEvent.FileDeleted -> event.path
            is ToolEvent.FileSystemMutated -> return true
            else -> null
        }
        val target = changedPath?.trimEnd('/') ?: return false
        val current = currentContainerPath().trimEnd('/')
        // 变更命中当前目录本身，或位于当前目录子树内。
        return target == current || target.startsWith("$current/")
    }

    /** 当前目录的容器路径（如 `~/workspace` 或 `~/workspace/src`）。 */
    fun currentContainerPath(): String {
        val stack = _dirStack.value
        return if (stack.isEmpty()) "~/workspace" else "~/workspace/" + stack.joinToString("/")
    }

    /** 某条目在当前目录下的容器路径（供阅读页读取）。 */
    fun containerPathFor(entry: FileEntry): String {
        val stack = _dirStack.value + entry.name
        return "~/workspace/" + stack.joinToString("/")
    }

    /** 重新列出当前目录。 */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            val path = currentContainerPath()
            val list = runCatching { fileAccess.listFiles(path) }
                .getOrDefault(emptyList())
                .sortedWith(
                    compareBy<FileEntry> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            _entries.value = list
            _loading.value = false
        }
    }

    /** 进入子目录。 */
    fun enterDirectory(name: String) {
        _dirStack.value = _dirStack.value + name
        refresh()
    }

    /** 返回上一级；已在根目录时无操作。 */
    fun goUp() {
        if (_dirStack.value.isNotEmpty()) {
            _dirStack.value = _dirStack.value.dropLast(1)
            refresh()
        }
    }

    /** 回到工作区根目录。 */
    fun resetToRoot() {
        _dirStack.value = emptyList()
        refresh()
    }
}
