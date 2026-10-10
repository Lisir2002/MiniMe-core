package com.mini.me_core.feature.git.domain

import com.mini.me_core.feature.git.domain.model.GitBranch
import com.mini.me_core.feature.git.domain.model.GitCommit
import com.mini.me_core.feature.git.domain.model.GitFileChange
import com.mini.me_core.feature.git.domain.model.GitGraph
import com.mini.me_core.feature.git.domain.model.GitGraphRef
import com.mini.me_core.feature.git.domain.model.GraphCommit
import com.mini.me_core.feature.git.domain.model.GitStatus
import com.mini.me_core.feature.git.domain.model.GitTag

/**
 * 容器内 git 命令执行端口。实现（容器命令调用 + 输出解析 + 工作区文件直读）位于 data 层。
 *
 * 直接复用 LinuxContainerEngine（cwd = 当前工作区，绑定挂载到 ~/workspace），
 * 不经 agent 工具链 / 权限引擎——Git 页是用户主动操作。所有输出解析为纯领域模型。
 */
interface GitRepository {

    /** 当前工作区是否处于一个 git 工作树内。SSH 未连接等异常时返回 false 而非抛出，避免 UI 崩溃。 */
    suspend fun isRepo(): Boolean

    /** 容器是否就绪可执行 git 命令；未就绪时返回引导文案（供 Git 页在刷新前提示用户去终端页初始化）。 */
    fun notReadyHint(): String?

    /** 在当前工作区初始化 git 仓库（`git init`）。据退出码判成败，失败抛 [GitCommandFailureException]。 */
    suspend fun initRepo(): String

    /** 是否已配置至少一个远程仓库（`git remote` 输出非空）。拉取/推送前据此门控。 */
    suspend fun hasRemote(): Boolean

    /** `git status` 聚合视图。 */
    suspend fun status(): GitStatus

    /** 本地 + 远程分支列表，当前分支高亮。 */
    suspend fun branches(): List<GitBranch>

    /**
     * 仅拉本地分支的 refs-by-commit 映射（`for-each-ref refs/heads`，大仓库亚秒级），
     * 供首屏轻量快照的 graph 标注当前分支位置。远程分支与标签标注延迟到 BRANCHES tab 全量加载。
     */
    suspend fun localRefsOnly(): Map<String, List<GitGraphRef>>

    /** 最近 [limit] 条提交。 */
    suspend fun log(limit: Int = 50): List<GitCommit>

    suspend fun graph(
        limit: Int = 100,
        refs: Map<String, List<GitGraphRef>> = emptyMap()
    ): GitGraph

    suspend fun graphAppend(
        existingCommits: List<GraphCommit>,
        refs: Map<String, List<GitGraphRef>> = emptyMap(),
        limit: Int = 100
    ): GitGraph

    /**
     * 一次拉取全部分支/标签及其指向的提交哈希，解析为三份切面：branches 列表、tags 列表、refsByCommit 映射。
     */
    data class AllRefs(
        val branches: List<GitBranch>,
        val tags: List<GitTag>,
        val refsByCommit: Map<String, List<GitGraphRef>>
    )

    suspend fun loadAllRefs(): AllRefs

    /** 某次提交改动的文件清单。返回空列表表示该提交无文件改动（如空提交）。 */
    suspend fun commitFiles(hash: String): List<GitFileChange>

    suspend fun stage(path: String): String
    suspend fun unstage(path: String): String
    suspend fun stageAll(): String
    suspend fun unstageAll(): String
    suspend fun commit(message: String): String

    /** 拉取：凭据由容器 `credential.helper` 链自动注入。失败由实现据退出码抛 [GitCommandFailureException]。 */
    suspend fun pull(): String

    /** 推送：有上游则 `git push`；无上游时自动首推建关联。失败抛 [GitCommandFailureException]。 */
    suspend fun push(): String

    /** 本地标签列表，按创建时间倒序（最新在前）。 */
    suspend fun listTags(): List<GitTag>

    /** 创建新分支。startPoint 为基准分支名（null/空 → 从当前 HEAD）；checkout=true 则创建并切换。 */
    suspend fun createBranch(name: String, startPoint: String?, checkout: Boolean): String

    /** 安全删除本地分支（`git branch -d`）：仅删除已合并到上游的分支。 */
    suspend fun deleteBranch(name: String): String

    /** 重命名本地分支。当前分支也可重命名：传单参数 `git branch -m <new>`。 */
    suspend fun renameBranch(oldName: String, newName: String): String

    /** 创建轻量标签（`git tag <name>`），指向当前 HEAD。 */
    suspend fun createTag(name: String): String

    /** 删除本地标签（`git tag -d <name>`）。 */
    suspend fun deleteTag(name: String): String

    /** 删除远程分支（`git push <remote> --delete <branch>`）。 */
    suspend fun deleteRemoteBranch(ref: String): String

    /** 切换到指定分支或标签。远程分支用 `git checkout -b <local> <remote>` 创建本地跟踪分支。 */
    suspend fun checkout(branch: String, isRemote: Boolean): String

    /** 写入提交署名，优先项目级：无项目级署名时写 global。空值跳过对应项。 */
    suspend fun setUserIdentity(name: String, email: String)

    /** 读取 git 当前实际生效的 user.name。失败返回空串。 */
    suspend fun getUserName(): String

    /** 读取 git 当前实际生效的 user.email。失败返回空串。 */
    suspend fun getUserEmail(): String

    /** 写入仓库地址 remote.origin.url，仅写项目级（--local）。 */
    suspend fun setRepoUrl(url: String)

    /** 读取 git 当前实际生效的 remote.origin.url。失败返回空串。 */
    suspend fun getRepoUrl(): String

    /** 读取指定 ref（提交/分支/标签）下某文件的完整内容（`git show <ref>:<path>`）。 */
    suspend fun showFileContent(ref: String, path: String): String

    /** 读取工作区当前文件内容。文件不存在或读取失败返回空串。 */
    suspend fun worktreeFileContent(path: String): String
}
