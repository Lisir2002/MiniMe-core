package com.mini.me_core.feature.git.domain.model

/**
 * Git 领域模型。所有字段均由 [com.mini.me_core.feature.git.domain.GitRepository] 解析
 * `git status --porcelain` / `git log` / `git for-each-ref` 的输出得到，纯数据、无 Android 依赖。
 */

/** 单个文件的改动：路径 + git status 的 XY 两字符状态码 + 是否已暂存（由 X 列决定）。 */
data class GitFileChange(
    val path: String,
    /** 形如 "M"/"A"/"D"/"R"/"??"，来自 porcelain 输出。 */
    val statusCode: String,
    val staged: Boolean
)

data class GitCommit(
    val hash: String,
    val shortHash: String,
    val author: String,
    val date: String,
    val message: String
)

data class GitBranch(
    val name: String,
    val current: Boolean,
    val remote: Boolean
)

data class GitTag(
    val name: String,
    val shortHash: String
)

/**
 * 一条 stash 条目，由 `git stash list` 解析得到。
 *
 * [index] 即 `stash@{N}` 中的 N（0 为最新），后续 pop/apply/drop 都用它定位；[branch] 是 stash
 * 创建时所在分支；[message] 为 stash 说明（`-m` 指定的自定义消息，或 WIP 时取基线提交的主题）；
 * [commitHash] 为基线提交哈希（自定义消息的 stash 无独立基线哈希时为空串）。
 */
data class GitStash(
    val index: Int,
    val branch: String,
    val message: String,
    val commitHash: String
)

/** `git status` 的聚合视图：分支跟踪信息 + 分组后的文件改动。 */
data class GitStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val staged: List<GitFileChange>,
    val unstaged: List<GitFileChange>,
    val untracked: List<String>
) {
    val hasChanges: Boolean
        get() = staged.isNotEmpty() || unstaged.isNotEmpty() || untracked.isNotEmpty()
}

enum class GitTab { STATUS, BRANCHES, LOG }
