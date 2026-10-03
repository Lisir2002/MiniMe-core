# MiniMe Git 功能模块深度优化设计文档

> 版本：v1.0
> 日期：2026-10-03
> 范围：应用内 Git 功能模块（feature/git/ + feature/credentials/）
> 基于：Git 功能模块深度审计报告 v1.0

---

## 目录

1. [文档概述](#1-文档概述)
2. [第一部分：安全漏洞修复（P0）](#2-第一部分安全漏洞修复p0)
3. [第二部分：风险点修复（P1/P2）](#3-第二部分风险点修复p1p2)
4. [第三部分：功能扩展设计](#4-第三部分功能扩展设计)
5. [第四部分：UI/UX 优化设计](#5-第四部分uiux-优化设计)
6. [第五部分：安全加固设计](#6-第五部分安全加固设计)
7. [第六部分：实施路线图](#7-第六部分实施路线图)
8. [第七部分：验收标准](#8-第七部分验收标准)

---

## 1. 文档概述

### 1.1 背景

当前 Git 模块已具备基础的版本控制能力（状态查看、暂存提交、分支管理、提交日志、diff 查看、凭据管理），架构设计质量较高，但在安全性、功能完整度、用户体验方面仍有较大优化空间。

### 1.2 设计原则

- **安全第一**：所有文件操作和命令执行必须经过路径校验和参数转义
- **渐进增强**：高优先级功能优先实现，低优先级功能按需扩展
- **用户友好**：危险操作必须确认，长操作必须有进度反馈，错误必须有友好提示
- **性能优先**：大仓库场景下必须有超时保护和降级策略
- **向后兼容**：新增功能不破坏现有 API 和 UI 交互

### 1.3 涉及文件

| 层级 | 文件路径 | 改动类型 |
|------|----------|----------|
| 领域层 | `feature/git/domain/GitRepository.kt` | 新增方法 + 安全修复 |
| 领域层 | `feature/git/domain/GitPathValidator.kt` | 新增（路径校验工具） |
| 领域层 | `feature/git/domain/GitOperationLogger.kt` | 新增（操作审计日志） |
| 视图模型 | `feature/git/presentation/GitViewModel.kt` | 新增状态 + 操作方法 |
| UI 层 | `feature/git/presentation/component/GitScreen.kt` | 新增对话框 + 空状态重构 |
| UI 层 | `feature/git/presentation/component/GitStatusTab.kt` | 新增 stash/reset 入口 |
| UI 层 | `feature/git/presentation/component/GitBranchesTab.kt` | 新增合并/变基/清理入口 |
| UI 层 | `feature/git/presentation/component/GitLogTab.kt` | 新增搜索/cherry-pick/blame 入口 |
| UI 层 | `feature/git/presentation/component/MergeConflictResolver.kt` | 新增（冲突解决器） |
| UI 层 | `feature/git/presentation/component/GitBlameViewer.kt` | 新增（blame 查看器） |
| UI 层 | `feature/git/presentation/component/GitIgnoreEditor.kt` | 新增（.gitignore 编辑器） |
| 凭据层 | `feature/credentials/domain/GitCredentialStore.kt` | 新增（加密存储） |

---

## 2. 第一部分：安全漏洞修复（P0）

### 2.1 工作区文件路径遍历漏洞修复

#### 2.1.1 问题描述

`GitRepository.worktreeFileContent(path)` 直接使用 `java.io.File(workspacePath, path)` 读取文件，若 path 包含 `../` 可逃逸工作区目录，读取任意系统文件。

**攻击向量**：用户在 diff 查看时，构造恶意文件路径 `../../etc/passwd`，可读取系统敏感文件。

#### 2.1.2 设计方案

新增 `GitPathValidator` 工具类，提供路径规范化和工作区内校验：

```kotlin
object GitPathValidator {
    /**
     * 校验 path 是否在 workspaceRoot 目录内。
     * 步骤：
     * 1. 拼接 workspaceRoot + path
     * 2. canonicalPath 规范化（解析 ../ 和符号链接）
     * 3. 校验规范化后的路径是否以 workspaceRoot.canonicalPath 开头
     *
     * @throws IllegalArgumentException 路径逃逸工作区时抛出
     */
    fun requireWithinWorkspace(workspaceRoot: File, path: String): File {
        val resolved = File(workspaceRoot, path).canonicalFile
        val rootCanonical = workspaceRoot.canonicalFile
        require(resolved.path.startsWith(rootCanonical.path + File.separator) ||
                resolved.path == rootCanonical.path) {
            "路径逃逸工作区: $path"
        }
        return resolved
    }

    /** 安全版本：校验失败返回 null 而非抛出 */
    fun resolveWithinWorkspaceOrNull(workspaceRoot: File, path: String): File? =
        runCatching { requireWithinWorkspace(workspaceRoot, path) }.getOrNull()
}
```

#### 2.1.3 实现步骤

1. 新增 `GitPathValidator.kt` 工具类
2. 修改 `worktreeFileContent()`：调用 `requireWithinWorkspace()` 校验，失败返回空串并记录警告日志
3. 修改 `showFileContent()`：虽然走 git 命令，但 path 参数也需校验（防止 git 命令路径注入）
4. 所有接受用户输入 path 的方法统一添加校验

#### 2.1.4 验收标准

- [ ] path 为 `../../etc/passwd` 时返回空串，不读取文件
- [ ] path 为正常相对路径时正常读取
- [ ] 符号链接指向工作区外时被拦截
- [ ] 单元测试覆盖：正常路径、逃逸路径、符号链接、空路径

---

### 2.2 `git show` 命令参数转义修复

#### 2.2.1 问题描述

`showFileContent(ref, path)` 中使用 `"$ref:$path"` 直接拼接命令字符串，ref 和 path 未经过 `shellQuote` 转义。若包含空格、`;`、`|` 等特殊字符，可能导致命令注入。

#### 2.2.2 设计方案

**方案 A（推荐）**：使用 `--` 分隔符 + 单独参数

```kotlin
suspend fun showFileContent(ref: String, path: String): String {
    // git show <ref> -- <path> 形式，ref 和 path 作为独立参数经 shellQuote 转义
    val out = git("show", "$ref:$path")  // 旧形式
    // 改为：
    val out = gitRaw(arrayOf("show", "${ref}:${path}"))  // 仍需转义
}
```

实际上 `ref:path` 是 git 的单个参数（revision path 语法），需要整体转义。正确做法：

```kotlin
suspend fun showFileContent(ref: String, path: String): String {
    // 先校验 ref 格式（只允许 hash、分支名、tag 名、HEAD 等安全字符）
    require(ref.matches(Regex("^[a-zA-Z0-9/_.-~^@{}]+$"))) {
        "非法的 ref 格式: $ref"
    }
    // ref:path 作为单个参数，经 shellQuote 转义
    val out = git("show", "$ref:$path")
    return if (out.startsWith("fatal:") || out.startsWith("error:")) "" else out
}
```

#### 2.2.3 实现步骤

1. 新增 `GitRefValidator`：校验 ref 只包含安全字符（字母、数字、`/`、`_`、`.`、`-`、`~`、`^`、`@`、`{`、`}`）
2. 修改 `showFileContent()`：添加 ref 格式校验
3. 检查所有接受 ref 参数的方法（`commitFiles`、`checkout` 等）统一添加校验
4. path 参数统一走 `GitPathValidator` 校验

#### 2.2.4 验收标准

- [ ] ref 包含 `;rm -rf /` 时被拦截
- [ ] ref 包含空格时被拦截
- [ ] 正常 ref（hash、分支名、HEAD~1）正常工作
- [ ] path 包含特殊字符时经 shellQuote 安全传递

---

## 3. 第二部分：风险点修复（P1/P2）

### 3.1 分支切换未提交改动检测

#### 3.1.1 问题描述

`checkoutBranch()` 直接执行 `git checkout`，若当前工作区有未暂存或未提交改动，git 可能报错或导致改动丢失（取决于改动是否与目标分支冲突）。

#### 3.1.2 设计方案

**三态检测 + 用户确认**：

```kotlin
// GitRepository 新增
data class WorkingTreeState(
    val hasStagedChanges: Boolean,      // 已暂存未提交
    val hasUnstagedChanges: Boolean,    // 未暂存改动
    val hasUntrackedFiles: Boolean,     // 未跟踪文件
    val isClean: Boolean                 // 工作区干净
)

suspend fun workingTreeState(): WorkingTreeState {
    val status = status()
    return WorkingTreeState(
        hasStagedChanges = status.staged.isNotEmpty(),
        hasUnstagedChanges = status.unstaged.isNotEmpty(),
        hasUntrackedFiles = status.untracked.isNotEmpty(),
        isClean = status.staged.isEmpty() && status.unstaged.isEmpty() && status.untracked.isEmpty()
    )
}
```

**UI 确认对话框**（三选项）：
- **暂存并切换**：执行 `git stash push -m "auto-stash before checkout <branch>"`，然后切换
- **放弃改动并切换**：执行 `git checkout -- .`（仅未暂存）+ `git clean -fd`（未跟踪），然后切换（危险，红色警告）
- **取消**：不切换

#### 3.1.3 实现步骤

1. `GitRepository` 新增 `workingTreeState()` 方法
2. `GitViewModel` 新增 `pendingCheckout` 状态（待确认的切换目标）
3. `GitScreen` 新增 `CheckoutConfirmDialog` 对话框组件
4. `checkoutBranch()` 修改：先检测工作区状态，不干净则弹出确认框
5. 新增 `stashAndCheckout()`、`discardAndCheckout()` 两个方法

#### 3.1.4 验收标准

- [ ] 工作区干净时直接切换，无弹窗
- [ ] 有未提交改动时弹出确认框
- [ ] 「暂存并切换」成功创建 stash 并切换
- [ ] 「放弃改动」有红色警告，二次确认后才执行
- [ ] stash 列表可在后续查看和恢复

---

### 3.2 大仓库操作超时保护

#### 3.2.1 问题描述

`git log --graph`、`loadAllRefs`、`git status` 在超大仓库（万级提交、十万级文件）可能执行数十秒甚至数分钟，容器命令无超时机制，导致 UI 永久 loading。

#### 3.2.2 设计方案

**CommandEngine 增加超时参数**：

```kotlin
// CommandEngine 扩展
suspend fun runCommandSyncWithTimeout(
    cmd: String,
    cwd: String,
    timeoutMs: Long = 30_000  // 默认30秒
): CommandResult {
    // 使用 withTimeoutOrNull 包裹
    return withTimeoutOrNull(timeoutMs) {
        runCommandSyncWithExit(cmd, cwd)
    } ?: CommandResult(exitCode = -1, output = "命令执行超时（${timeoutMs}ms）")
}
```

**GitRepository 分级超时**：

| 操作类型 | 超时时间 | 超时处理 |
|----------|----------|----------|
| status / branches / tags | 15s | 返回空结果，toast 提示「仓库过大，建议使用终端」 |
| log / graph（首页100条） | 30s | 返回已加载部分，hasMore=false |
| graphAppend（加载更多） | 30s | 保留已加载，toast 提示加载超时 |
| commitFiles | 10s | 返回空列表 |
| diff（showFileContent） | 10s | 返回空串，diff 显示超时 |
| pull / push / clone | 120s | 失败 toast，不静默成功 |
| init / add / commit / branch | 15s | 失败抛出异常 |

#### 3.2.3 实现步骤

1. `CommandEngine` 新增 `runCommandSyncWithTimeout()` 方法
2. `GitRepository` 所有方法改用带超时的命令执行
3. 新增 `GitTimeoutException` 异常类型，区分超时与其他错误
4. `GitViewModel` 捕获超时异常，友好提示并保留已有数据
5. UI 层增加「仓库过大」提示卡片，建议用户使用终端操作

#### 3.2.4 验收标准

- [ ] 模拟慢命令（`sleep 60`）时30秒后超时返回
- [ ] 超时后 UI 不永久 loading，显示友好提示
- [ ] 超时不丢失已加载的数据（如 graph 已加载的提交）
- [ ] pull/push 超时时间为120秒，其他操作30秒

---

### 3.3 危险操作二次确认

#### 3.3.1 问题描述

`deleteBranch`、`deleteRemoteBranch`、`deleteTag`、`push --force` 等危险操作直接执行，无确认弹窗，误操作可能导致不可逆的数据丢失。

#### 3.3.2 设计方案

**危险操作分级确认**：

| 操作 | 危险等级 | 确认方式 |
|------|----------|----------|
| 删除本地分支（已合并） | 低 | 普通确认对话框 |
| 删除本地分支（未合并） | 中 | 警告对话框 + 输入分支名确认 |
| 删除远程分支 | 高 | 红色警告对话框 + 输入分支名确认 + 显示影响说明 |
| 删除标签 | 低 | 普通确认对话框 |
| 强制推送（--force） | 高 | 红色警告 + 输入 `FORCE` 确认 + 显示将覆盖的提交 |
| reset --hard | 极高 | 红色警告 + 输入 `RESET` 确认 + 显示将丢失的改动 |
| clean -fd | 高 | 红色警告 + 显示将删除的文件列表 |

**确认对话框组件**：

```kotlin
@Composable
fun DangerousActionDialog(
    title: String,
    message: String,
    confirmText: String,         // 按钮文字（如「删除」）
    requireInput: String? = null, // 需要输入确认的文本（如分支名）
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

#### 3.3.3 实现步骤

1. 新增 `DangerousActionDialog` 通用组件
2. `deleteRemoteBranch()` 修改：弹出高危险确认框，要求输入分支名
3. `deleteBranch()` 修改：未合并分支要求输入分支名，已合并且普通确认
4. 新增 `forcePush()` 方法：带 `--force-with-lease`（更安全），确认框要求输入 `FORCE`
5. 新增 `resetToCommit(hash, mode)` 方法：--hard 模式要求输入 `RESET`
6. 所有危险操作记录到审计日志

#### 3.3.4 验收标准

- [ ] 删除远程分支时弹出红色警告，要求输入分支名
- [ ] 输入错误分支名时确认按钮禁用
- [ ] 强制推送要求输入 `FORCE`，并显示将覆盖的提交数量
- [ ] reset --hard 要求输入 `RESET`，并显示将丢失的文件数量
- [ ] 所有危险操作记录到审计日志

---

### 3.4 并发 refresh 竞态修复

#### 3.4.1 问题描述

`refresh()` 和 `loadBranches()` 可能并发执行，两者都会更新 `state.graph`，导致 refs 标注闪烁或状态不一致。`refresh()` 重置 `branchesLoaded=false` 后触发 `loadBranches()`，但若上一次 `loadBranches()` 仍在运行，会产生竞态。

#### 3.4.2 设计方案

**单一协程串行化 + 版本号机制**：

```kotlin
class GitViewModel {
    private var refreshVersion = 0  // 刷新版本号，竞态检测用

    fun refresh() {
        if (_state.value.busy) return
        val version = ++refreshVersion  // 递增版本号
        _state.update { it.copy(loading = true, toast = null, branchesLoaded = false, branchesLoading = false) }
        viewModelScope.launch {
            // ... 执行刷新 ...
            // 完成前检查版本号，若已被新的 refresh 覆盖则丢弃结果
            if (version != refreshVersion) return@launch
            _state.update { /* 更新状态 */ }
            loadBranches(version)  // 传递版本号
        }
    }

    private fun loadBranches(parentVersion: Int = 0) {
        // ... 加载完成后检查版本号
        if (parentVersion != refreshVersion) return  // 过期结果丢弃
    }
}
```

#### 3.4.3 实现步骤

1. 新增 `refreshVersion` 原子计数器
2. `refresh()` 递增版本号，传递给后续异步操作
3. `loadBranches()` 接受版本号参数，完成后校验
4. 所有异步状态更新前校验版本号，过期结果丢弃
5. `checkoutBranch()` 等写操作也递增版本号，使之前的只读操作结果失效

#### 3.4.4 验收标准

- [ ] 连续快速点击刷新按钮，最终状态与最后一次刷新一致
- [ ] 刷新过程中切换分支，不会出现旧分支数据闪烁
- [ ] 单元测试模拟并发 refresh，验证最终状态一致性

---

### 3.5 diff 算法内存边界加固

#### 3.5.1 问题描述

`MAX_DIFF_LINES=2000` 只限制了行数，但 LCS 算法内存复杂度为 O(n·m)，在 2000×2000 边界仍可能占用 16MB+ 内存。此外，单行超长文件（如 minified JS，一行100KB）也可能导致内存压力。

#### 3.5.2 设计方案

**三重保护机制**：

1. **行数限制**：`MAX_DIFF_LINES = 2000`（已有）
2. **字符数限制**：新增 `MAX_DIFF_CHARS = 500_000`（约500KB），任一侧超过则降级
3. **单行长度限制**：新增 `MAX_LINE_LENGTH = 50_000`（约50KB），超长行截断显示

**降级策略**：

```kotlin
data class DiffData(
    // ...
    val isBinary: Boolean = false,
    val isLarge: Boolean = false,       // 行数超限
    val isTooLarge: Boolean = false,    // 字符数超限（更严重）
    val truncatedLines: Int = 0         // 被截断的行数
)
```

- `isLarge`：显示「文件过大，仅显示前2000行差异」，提供「在编辑器中打开」按钮
- `isTooLarge`：不计算 diff，直接显示「文件过大（>500KB），无法在应用内查看差异，请使用编辑器或终端」

#### 3.5.3 实现步骤

1. 新增 `MAX_DIFF_CHARS` 和 `MAX_LINE_LENGTH` 常量
2. `computeDiff()` 增加字符数检测，超过则返回 `isTooLarge=true`
3. 增加单行长度检测，超长行截断并记录 `truncatedLines`
4. `DiffViewer` UI 增加降级状态显示
5. 提供「在编辑器中打开」按钮，跳转到统一查看器

#### 3.5.4 验收标准

- [ ] 600KB 文件 diff 时显示「文件过大」提示，不计算 diff
- [ ] 2000行以内但500KB以上的文件正确降级
- [ ] 单行100KB的文件被截断，不导致 OOM
- [ ] 降级状态提供「在编辑器中打开」入口

---

### 3.6 容器未就绪恢复机制增强

#### 3.6.1 问题描述

容器未就绪时 `refresh()` 只显示 toast，没有重试按钮或自动恢复机制。用户需要手动离开页面再进入才能重新检测。

#### 3.6.2 设计方案

**三态显示 + 主动重试**：

```kotlin
data class GitUiState(
    // ...
    val containerReady: Boolean = true,
    val containerNotReadyHint: String? = null
)
```

**UI 状态**：
1. **检测中**：显示 loading
2. **容器未就绪**：显示专用卡片「容器未启动」+ 「启动容器并重试」按钮 + 提示文案（引导用户去终端页初始化）
3. **非 Git 仓库**：显示空状态（已有，增强为多入口）
4. **正常**：显示 Git 操作界面

**自动恢复**：监听容器就绪事件（若 CommandEngine 提供回调），就绪后自动 refresh。

#### 3.6.3 实现步骤

1. `GitViewModel` 新增 `containerReady` 状态
2. `refresh()` 修改：容器未就绪时设置状态而非仅 toast
3. `GitScreen` 新增 `ContainerNotReadyCard` 组件
4. 新增「启动容器并重试」按钮，调用容器启动 API（若可用）
5. 若 CommandEngine 提供就绪回调，注册监听自动刷新

#### 3.6.4 验收标准

- [ ] 容器未就绪时显示专用卡片，而非仅 toast
- [ ] 提供「重试」按钮，点击后重新检测
- [ ] 容器启动后自动刷新（若支持事件监听）
- [ ] 提示文案引导用户去终端页初始化容器

---

## 4. 第三部分：功能扩展设计

### 4.1 高优先级功能

#### 4.1.1 git stash 暂存功能

**功能描述**：临时保存当前工作区改动，方便切换分支或清理工作区。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun stashList(): List<GitStash>          // 查看 stash 列表
suspend fun stashPush(message: String?): String   // 创建 stash
suspend fun stashPop(index: Int): String          // 恢复并删除 stash
suspend fun stashApply(index: Int): String        // 恢复但保留 stash
suspend fun stashDrop(index: Int): String         // 删除 stash
suspend fun stashClear(): String                   // 清空所有 stash

data class GitStash(
    val index: Int,           // stash@{0}
    val branch: String,       // 创建时所在分支
    val message: String,      // 描述信息
    val commitHash: String    // 对应的提交 hash
)
```

**UI 设计**：
- Status Tab 顶部增加「暂存改动」按钮（有未提交改动时可用）
- 新增 Stash Tab 或在 Branches Tab 中增加 Stash 子区域
- Stash 列表项显示：index、分支、消息、创建时间
- 每项操作：恢复（pop）、应用（apply）、删除（drop）
- 创建 stash 时可选「是否包含未跟踪文件」（`-u` 参数）

**实现步骤**：
1. `GitRepository` 新增 stash 相关方法
2. `GitViewModel` 新增 stash 状态和操作方法
3. `GitStatusTab` 增加「暂存改动」入口
4. 新增 `GitStashTab` 或在现有 Tab 中集成
5. 新增 `StashCreateDialog`（输入消息 + 选项）

---

#### 4.1.2 merge / rebase 合并功能

**功能描述**：合并指定分支到当前分支，或将当前分支变基到目标分支。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun merge(branch: String, noFF: Boolean = false): String
suspend fun rebase(branch: String): String
suspend fun abortMerge(): String     // 取消合并
suspend fun abortRebase(): String    // 取消变基
suspend fun continueRebase(): String // 解决冲突后继续变基
suspend fun mergeStatus(): MergeStatus // 当前合并/变基状态

data class MergeStatus(
    val isMerging: Boolean,
    val isRebasing: Boolean,
    val mergingBranch: String?,
    val rebasingBranch: String?,
    val conflictedFiles: List<String>  // 冲突文件列表
)
```

**UI 设计**：
- Branches Tab 每项增加「合并」「变基」操作按钮
- 合并对话框：选择目标分支 + 选项（--no-ff / --squash）+ 预览
- 变基对话框：警告「变基会改写提交历史，已推送的分支不建议变基」+ 确认
- 冲突状态：Status Tab 顶部显示红色横幅「正在合并/变基，有 N 个冲突文件」+ 「中止」「继续」按钮
- 冲突文件在 Status Tab 中高亮显示，点击进入冲突解决器

**实现步骤**：
1. `GitRepository` 新增 merge/rebase 相关方法
2. `GitViewModel` 新增合并状态和操作方法
3. `GitBranchesTab` 增加合并/变基入口
4. 新增 `MergeDialog` 和 `RebaseDialog`
5. Status Tab 增加合并/变基状态横幅
6. 冲突文件检测和冲突解决器集成（见 4.1.3）

---

#### 4.1.3 合并冲突可视化解决器

**功能描述**：合并冲突时提供可视化界面，方便用户选择保留哪一侧的改动或手动编辑。

**设计方案**：

```kotlin
// 冲突解决器状态
data class ConflictFile(
    val path: String,
    val status: ConflictStatus,  // UNRESOLVED / RESOLVED
    val oursContent: String,     // 当前分支版本（冲突标记 <<<<<<< HEAD 到 =======）
    val theirsContent: String,   // 目标分支版本（======= 到 >>>>>>>）
    val resolvedContent: String? // 解决后的内容
)

enum class ConflictResolution {
    ACCEPT_OURS,    // 保留当前分支
    ACCEPT_THEIRS,   // 保留目标分支
    ACCEPT_BOTH,     // 保留双方
    MANUAL_EDIT      // 手动编辑
}
```

**UI 设计**（三栏布局）：
- 左侧：「当前分支」版本（绿色标题）
- 中间：「合并结果」编辑区（可手动编辑）
- 右侧：「目标分支」版本（蓝色标题）
- 顶部：文件路径 + 冲突块数量 + 导航（上一个/下一个冲突块）
- 底部操作栏：
  - 「保留当前」按钮（应用到当前冲突块）
  - 「保留目标」按钮
  - 「保留双方」按钮
  - 「标记已解决」按钮（执行 `git add`）
- 冲突块高亮：当前冲突块黄色背景，已解决块绿色边框

**实现步骤**：
1. 新增 `MergeConflictResolver.kt` 组件
2. 冲突解析器：解析 `<<<<<<<` / `=======` / `>>>>>>>` 标记，拆分 ours/theirs
3. 三栏 diff 展示（复用现有 DiffViewer 组件）
4. 快捷操作按钮（接受当前/接受目标/接受双方）
5. 手动编辑功能（复用统一编辑器）
6. 标记已解决（`git add`）+ 下一个冲突文件导航
7. 全部解决后提供「完成合并」按钮（`git commit` 或 `git rebase --continue`）

---

#### 4.1.4 reset 到指定提交

**功能描述**：将当前分支重置到指定提交，支持 --soft / --mixed / --hard 三种模式。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun resetToCommit(hash: String, mode: ResetMode): String

enum class ResetMode {
    SOFT,   // 仅移动 HEAD，暂存区和工作区不变
    MIXED,  // 移动 HEAD + 重置暂存区（默认）
    HARD    // 移动 HEAD + 重置暂存区 + 重置工作区（危险）
}
```

**UI 设计**：
- Log Tab 每项提交增加「重置到此提交」操作（长按菜单或更多按钮）
- Reset 对话框：
  - 显示目标提交信息（hash、作者、消息、时间）
  - 三种模式选择（单选按钮 + 说明）：
    - Soft：「保留所有改动在暂存区」
    - Mixed：「保留改动在工作区，取消暂存」（默认）
    - Hard：「丢弃所有改动」（红色警告）
  - --hard 模式要求输入 `RESET` 确认
  - 预览：显示将被取消的提交数量和将受影响的文件数量
- 重置后自动刷新状态

**实现步骤**：
1. `GitRepository` 新增 `resetToCommit()` 方法
2. `GitViewModel` 新增 reset 操作方法
3. `GitLogTab` 增加 reset 入口
4. 新增 `ResetDialog` 组件
5. --hard 模式集成危险操作确认（见 3.3）
6. 重置后刷新状态和提交日志

---

#### 4.1.5 cherry-pick 拣选提交

**功能描述**：将指定提交的改动应用到当前分支。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun cherryPick(hash: String): String
suspend fun cherryPickAbort(): String      // 取消 cherry-pick
suspend fun cherryPickContinue(): String   // 解决冲突后继续

data class CherryPickStatus(
    val isCherryPicking: Boolean,
    val sourceHash: String?,
    val conflictedFiles: List<String>
)
```

**UI 设计**：
- Log Tab 每项提交增加「拣选到此分支」操作
- Cherry-pick 对话框：显示源提交信息 + 目标分支（当前）+ 确认
- 冲突时：与 merge/rebase 共用冲突解决器
- 成功后 toast 提示「已拣选提交 <hash>」

**实现步骤**：
1. `GitRepository` 新增 cherry-pick 相关方法
2. `GitViewModel` 新增 cherry-pick 状态和操作
3. `GitLogTab` 增加 cherry-pick 入口
4. 新增 `CherryPickDialog`
5. 冲突状态与 merge/rebase 统一处理

---

### 4.2 中优先级功能

#### 4.2.1 git blame 查看器

**功能描述**：查看文件每行最后一次修改的提交、作者和时间。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun blame(path: String): List<BlameLine>

data class BlameLine(
    val lineNumber: Int,
    val commitHash: String,
    val shortHash: String,
    val author: String,
    val date: String,
    val summary: String,   // 提交消息首行
    val content: String     // 该行内容
)
```

**UI 设计**：
- 文件 diff 查看器顶部增加「Blame」Tab（与 Diff 并列）
- 三栏布局：
  - 左侧窄栏：提交 hash（短）+ 作者 + 日期（可点击跳转到提交详情）
  - 右侧宽栏：行号 + 代码内容（带语法高亮）
- 同一提交的连续行用相同背景色区分（交替配色）
- 点击 blame 信息跳转到该提交的详情页
- 支持按作者/提交过滤高亮

**实现步骤**：
1. `GitRepository` 新增 `blame()` 方法（解析 `git blame --porcelain` 输出）
2. `GitViewModel` 新增 blame 状态和加载方法
3. 新增 `GitBlameViewer.kt` 组件
4. 与 DiffViewer 集成（Tab 切换）
5. 语法高亮复用现有 highlightCode
6. 点击 blame 信息跳转到提交详情

---

#### 4.2.2 提交历史搜索

**功能描述**：按作者、提交消息、文件、日期范围搜索提交历史。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun searchCommits(
    query: String? = null,        // 搜索消息（grep）
    author: String? = null,       // 按作者过滤
    path: String? = null,         // 按文件路径过滤
    since: String? = null,        // 起始日期（YYYY-MM-DD）
    until: String? = null,        // 截止日期
    limit: Int = 100
): List<GitCommit>
```

**UI 设计**：
- Log Tab 顶部增加搜索栏（点击搜索图标展开）
- 搜索选项：
  - 关键词（提交消息）
  - 作者（下拉选择，从已有提交作者中提取）
  - 文件路径（输入 + 自动补全）
  - 日期范围（起始/截止日期选择器）
- 搜索结果替换正常 log 列表，显示「搜索结果：N 条提交」+ 清除搜索按钮
- 搜索条件可保存为常用筛选

**实现步骤**：
1. `GitRepository` 新增 `searchCommits()` 方法（构建 `git log --grep/--author/--since/--until -- <path>` 命令）
2. `GitViewModel` 新增搜索状态和方法
3. `GitLogTab` 增加搜索栏和筛选面板
4. 作者列表从已有提交中提取（去重）
5. 搜索结果与正常 log 列表切换
6. 搜索条件 URL 编码，支持分享

---

#### 4.2.3 .gitignore 可视化编辑器

**功能描述**：可视化管理 .gitignore 规则，提供常用模板和语法高亮。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun readGitignore(): String          // 读取 .gitignore（不存在返回空）
suspend fun writeGitignore(content: String)  // 写入 .gitignore
suspend fun checkIgnored(path: String): Boolean  // 检查某路径是否被忽略

// 常用模板
object GitignoreTemplates {
    val KOTLIN = listOf("*.class", "*.jar", ".gradle/", "build/", "local.properties")
    val ANDROID = listOf("*.apk", "*.aab", "*.keystore", "*.jks", "captures/")
    val FLUTTER = listOf(".dart_tool/", ".flutter-plugins", "build/", ".packages")
    val NODE = listOf("node_modules/", "npm-debug.log", "dist/")
    val PYTHON = listOf("__pycache__/", "*.pyc", ".venv/", "env/")
    val MACOS = listOf(".DS_Store")
    val WINDOWS = listOf("Thumbs.db", "desktop.ini")
}
```

**UI 设计**：
- Settings Tab 或更多菜单中增加「.gitignore 管理」入口
- 编辑器页面：
  - 顶部：文件路径 + 「从模板添加」下拉按钮
  - 中部：代码编辑器（带 .gitignore 语法高亮，注释灰色，规则白色）
  - 底部：「测试路径」输入框（输入路径显示是否被忽略）+ 保存按钮
- 模板选择对话框：多选常用模板，确认后追加到 .gitignore
- 保存前显示 diff（旧内容 vs 新内容）

**实现步骤**：
1. `GitRepository` 新增 .gitignore 相关方法
2. 新增 `GitignoreTemplates` 模板数据
3. 新增 `GitignoreEditorScreen.kt` 组件
4. .gitignore 语法高亮（简单规则：注释行灰色，其他正常）
5. 模板选择对话框
6. 路径测试功能（调用 `git check-ignore`）
7. 保存前 diff 预览

---

#### 4.2.4 reflog 查看与恢复

**功能描述**：查看 git 操作历史（reflog），支持从误操作中恢复（如误 reset --hard、误删分支）。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun reflog(limit: Int = 50): List<ReflogEntry>

data class ReflogEntry(
    val index: Int,           // HEAD@{0}
    val oldHash: String,      // 操作前 hash
    val newHash: String,      // 操作后 hash
    val action: String,       // 操作类型（commit/checkout/reset/merge/rebase...）
    val description: String,  // 操作描述
    val timestamp: String     // 操作时间
)
```

**UI 设计**：
- Log Tab 增加「操作历史」切换（与「提交历史」并列）
- Reflog 列表项显示：index、操作类型图标、描述、时间、新旧 hash
- 每项操作：
  - 「查看此状态」：临时 checkout 到该 hash（detached HEAD）
  - 「恢复到此状态」：`git reset --hard <hash>`（危险，需确认）
  - 「创建分支」：从该 hash 创建新分支（安全的恢复方式）
- 顶部提示「reflog 记录本地操作历史，可用于恢复误操作，默认保留90天」

**实现步骤**：
1. `GitRepository` 新增 `reflog()` 方法（解析 `git reflog` 输出）
2. `GitViewModel` 新增 reflog 状态和加载方法
3. `GitLogTab` 增加「提交历史/操作历史」切换
4. 新增 `ReflogList` 组件
5. 恢复操作集成危险确认（reset --hard）
6. 「创建分支」安全恢复方式

---

#### 4.2.5 子模块（submodule）管理

**功能描述**：管理 git 子模块的添加、初始化、更新、删除。

**设计方案**：

```kotlin
// GitRepository 新增
suspend fun submoduleList(): List<GitSubmodule>
suspend fun submoduleAdd(url: String, path: String): String
suspend fun submoduleInit(path: String? = null): String   // 初始化（null=全部）
suspend fun submoduleUpdate(path: String? = null, init: Boolean = true, recursive: Boolean = true): String
suspend fun submoduleRemove(path: String): String

data class GitSubmodule(
    val path: String,
    val url: String,
    val commitHash: String,   // 当前锁定的提交
    val initialized: Boolean, // 是否已初始化（有内容）
    val upToDate: Boolean     // 是否与远程一致
)
```

**UI 设计**：
- 新增「子模块」Tab 或在 Branches Tab 中增加子模块区域
- 子模块列表项显示：路径、URL、锁定 commit、状态（未初始化/已初始化/有更新）
- 每项操作：初始化、更新、打开（进入子模块仓库）、删除
- 顶部「添加子模块」按钮：输入 URL + 路径
- 批量操作：全部初始化、全部更新

**实现步骤**：
1. `GitRepository` 新增 submodule 相关方法
2. `GitViewModel` 新增子模块状态和操作
3. 新增 `GitSubmoduleTab` 或集成到现有 Tab
4. 子模块添加对话框
5. 子模块状态检测（`.gitmodules` 解析 + 目录检查）
6. 子模块更新进度反馈

---

### 4.3 低优先级功能

#### 4.3.1 交互式 rebase

**功能描述**：可视化交互式变基，支持压缩（squash）、重排（reorder）、修改消息（reword）、删除（drop）提交。

**设计方案**：
- Log Tab 选择起始提交后进入「交互式 rebase」模式
- 提交列表可拖拽排序，每项有操作下拉：pick/squash/fixup/reword/edit/drop
- 预览 rebase 后的提交序列
- 执行前显示警告「将改写提交历史」
- 冲突时复用冲突解决器

**实现要点**：
- 使用 `git rebase -i` 的 todo 文件格式
- 生成 todo 文件后通过 `GIT_SEQUENCE_EDITOR` 环境变量指定编辑器脚本
- 或直接操作 `.git/rebase-merge/git-rebase-todo` 文件

---

#### 4.3.2 git bisect 二分查找

**功能描述**：通过二分查找定位引入 bug 的提交。

**设计方案**：
- 引导式流程：
  1. 选择「已知好的提交」（bug 不存在）
  2. 选择「已知坏的提交」（bug 存在）
  3. bisect 自动 checkout 到中间提交
  4. 用户测试后标记「好」或「坏」
  5. 重复直到定位到引入 bug 的提交
- 状态显示：当前测试提交、剩余范围、预计步数
- 操作按钮：「标记为好」「标记为坏」「跳过此提交」「终止 bisect」
- 结果显示：定位到的提交信息 + 「查看提交详情」「创建修复分支」

---

#### 4.3.3 git worktree 多工作区

**功能描述**：同一仓库创建多个工作目录，每个工作目录可检出不同分支，实现并行开发。

**设计方案**：
- 新增「工作区管理」入口
- worktree 列表：路径、分支、是否锁定
- 操作：添加 worktree（选择分支 + 路径）、删除 worktree、打开（在文件管理器中打开）
- 与应用内「工作目录」功能集成，可快速切换到某个 worktree

---

#### 4.3.4 提交签名验证

**功能描述**：显示提交的 GPG/SSH 签名状态，验证提交者身份。

**设计方案**：
- Log Tab 提交项增加签名状态图标（已签名/未签名/验证失败）
- 提交详情页显示签名信息：签名类型、签名者、密钥 ID、验证状态
- 已签名提交用绿色边框或图标标记
- 配置项：「仅显示已签名提交」筛选

---

## 5. 第四部分：UI/UX 优化设计

### 5.1 空状态增强

#### 5.1.1 问题

当前「非 Git 仓库」空状态只有一个「初始化 Git 仓库」按钮，功能单一，用户可能需要克隆远程仓库或选择其他工作区。

#### 5.1.2 设计方案

**四入口空状态**：

```
┌─────────────────────────────────────┐
│  📁 当前工作区不是 Git 仓库          │
│  选择以下操作开始使用版本控制         │
├─────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐        │
│  │  🆕       │  │  📥       │        │
│  │ 初始化仓库 │  │ 克隆远程  │        │
│  │           │  │ 仓库      │        │
│  └──────────┘  └──────────┘        │
│  ┌──────────┐  ┌──────────┐        │
│  │  📂       │  │  ❓       │        │
│  │ 选择其他  │  │ 为什么不是│        │
│  │ 工作区    │  │ Git 仓库？│        │
│  └──────────┘  └──────────┘        │
├─────────────────────────────────────┤
│  💡 提示：Git 仓库需要 .git 目录     │
│  当前工作区路径：/path/to/workspace  │
└─────────────────────────────────────┘
```

**各入口功能**：
1. **初始化仓库**：执行 `git init`（现有功能）
2. **克隆远程仓库**：输入仓库 URL + 目标路径，执行 `git clone`（需容器网络）
3. **选择其他工作区**：跳转到工作区选择页面，让用户选择已有的 Git 仓库目录
4. **为什么不是 Git 仓库？**：展开帮助说明，解释 Git 仓库的判定条件和常见原因

**克隆对话框**：
- 仓库 URL 输入框（支持 HTTPS/SSH）
- 目标目录（默认当前工作区下的仓库名）
- 选项：`--depth 1` 浅克隆、`--recursive` 递归子模块
- 克隆进度显示（接收对象、解析增量等）

---

### 5.2 操作进度反馈

#### 5.2.1 问题

clone、大仓库 pull/push 等长操作没有进度反馈，用户不知道是否在正常执行，可能重复点击导致并发问题。

#### 5.2.2 设计方案

**进度状态机**：

```kotlin
data class GitOperationProgress(
    val operation: GitOperationType,  // CLONE / PULL / PUSH / FETCH
    val phase: String,                 // 当前阶段（接收对象/解析增量/应用补丁...）
    val progress: Int? = null,        // 百分比（0-100，null=不确定）
    val speed: String? = null,         // 传输速度（如 1.2MB/s）
    val received: String? = null,      // 已接收数据量
    val total: String? = null          // 总数据量
)

enum class GitOperationType {
    CLONE, PULL, PUSH, FETCH, SUBMODULE_UPDATE
}
```

**UI 设计**：
- 操作进行中时，顶部显示进度卡片（可折叠）：
  - 操作类型图标 + 名称
  - 进度条（有百分比时显示具体数值，无百分比时显示不确定动画）
  - 当前阶段文字 + 速度/数据量
  - 「后台运行」按钮（最小化为小图标，不阻塞页面）
  - 「取消」按钮（发送中断信号）
- 操作完成后进度卡片变为绿色「完成」，3秒后自动消失
- 操作失败后进度卡片变为红色「失败」+ 错误信息 + 「重试」按钮

**实现要点**：
- 解析 git 命令的 stderr 输出（git 的进度信息输出到 stderr）
- 正则匹配 `Receiving objects: 50% (123/246), 1.2 MiB | 500.0 KiB/s`
- 进度解析在后台线程，避免阻塞 UI
- 取消操作通过 `Process.destroy()` 实现

---

### 5.3 提交详情页增强

#### 5.3.1 问题

当前展开提交只显示文件列表，缺少提交统计、父提交跳转、diff 内联展开等功能。

#### 5.3.2 设计方案

**提交详情卡片**（展开后）：

```
┌─────────────────────────────────────┐
│  abc1234  修复登录页面白屏问题       │
│  张三  2小时前  2 个父提交           │
├─────────────────────────────────────┤
│  📊 统计：+128 -45 行  8 个文件     │
│  🔗 父提交：def5678, ghi9012        │
├─────────────────────────────────────┤
│  📁 修改文件（8）                    │
│  ├ M src/login/LoginScreen.kt   [展开]│
│  ├ A src/login/LoginViewModel.kt [展开]│
│  ├ D src/legacy/OldLogin.kt     [展开]│
│  └ ...                              │
└─────────────────────────────────────┘
```

**增强功能**：
1. **提交统计**：显示增删行数、修改文件数（调用 `git show --stat`）
2. **父提交跳转**：显示父提交 hash，点击跳转到该提交详情
3. **diff 内联展开**：每个文件项右侧「展开」按钮，点击后在下方内联显示该文件的 diff（复用 DiffViewer 的行级渲染）
4. **复制 commit hash**：长按 hash 复制到剪贴板
5. **操作菜单**：cherry-pick、reset 到此提交、创建分支、查看 blame

---

### 5.4 分支管理增强

#### 5.4.1 问题

当前分支列表只显示名称和当前状态，缺少最后提交时间、ahead/behind 数量、合并状态等信息。

#### 5.4.2 设计方案

**分支列表项增强**：

```
┌─────────────────────────────────────┐
│  🌟 main  (当前)                     │
│  最后提交：2小时前  张三             │
│  ↑2 ↓0  与 origin/main 相差2个提交   │
│  [合并] [变基] [推送] [更多]         │
├─────────────────────────────────────┤
│  feature/login                       │
│  最后提交：1天前  李四               │
│  ✓ 已合并到 main                     │
│  [切换] [合并到当前] [删除] [更多]    │
└─────────────────────────────────────┘
```

**增强功能**：
1. **最后提交信息**：显示最后提交的作者和相对时间
2. **ahead/behind 数量**：与远程跟踪分支的领先/落后数量
3. **合并状态**：是否已合并到主分支（绿色 ✓ 标记）
4. **批量操作**：「清理已合并分支」按钮，一键删除所有已合并到 main 的本地分支
5. **分支排序选项**：按名称/最后提交时间/ahead 数量排序
6. **分支筛选**：仅显示本地/仅显示远程/仅显示未合并

---

## 6. 第五部分：安全加固设计

### 6.1 凭据存储加密

#### 6.1.1 问题

当前 `credential.helper=store` 以明文形式存储凭据到 `~/.git-credentials` 文件，若容器被攻破或文件被恶意读取，凭据将泄露。

#### 6.1.2 设计方案

**加密凭据存储方案**：

```kotlin
// 新增 EncryptedCredentialStore
object EncryptedCredentialStore {
    private const val KEY_ALIAS = "minime_git_credentials"

    /** 使用 Android Keystore 生成/获取 AES 密钥 */
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: createNewKey()
    }

    /** 加密凭据并写入文件 */
    fun encryptAndStore(credentials: List<GitCredential>)

    /** 从文件读取并解密凭据 */
    fun decryptAndLoad(): List<GitCredential>

    /** 自定义 credential.helper：git 请求凭据时调用此脚本解密返回 */
    fun getHelperScriptPath(): String
}
```

**实现方案**：
1. 使用 Android Keystore 生成 AES-256 密钥（硬件级保护，不可导出）
2. 凭据用 AES/GCM/NoPadding 加密后存储到文件
3. 自定义 `credential.helper` 指向一个脚本，git 请求凭据时脚本通过 IPC 调用应用解密并返回
4. 增加「凭据超时自动锁定」：应用退到后台 N 分钟后清除内存中的解密密钥，下次使用需重新验证（指纹/密码）

**降级方案**：若设备不支持 Android Keystore（极少见），降级为使用用户设置的主密码派生密钥（PBKDF2）。

---

### 6.2 操作审计日志

#### 6.2.1 问题

所有写操作（push、force push、delete remote branch、reset --hard）没有审计日志，误操作后无法追溯。

#### 6.2.2 设计方案

**GitOperationLogger**：

```kotlin
data class GitOperationLog(
    val id: Long,
    val timestamp: Long,
    val operation: String,        // 操作类型（PUSH_FORCE/DELETE_REMOTE_BRANCH/RESET_HARD...）
    val target: String,           // 操作目标（分支名/tag名/commit hash）
    val result: OperationResult,  // SUCCESS / FAILED
    val errorMessage: String?,    // 失败原因
    val workingDirectory: String, // 操作时的工作区
    val gitUser: String           // 操作时的 git 用户
)

enum class OperationResult { SUCCESS, FAILED }
```

**记录范围**：
- 所有远程修改操作：push、force push、delete remote branch、delete remote tag
- 所有不可逆本地操作：reset --hard、clean -fd、branch -D（强制删除未合并分支）
- 所有配置修改：setUserIdentity、setRepoUrl、credential 增删改

**存储与查看**：
- 存储到本地数据库（Room），保留最近 90 天
- Git 页面「更多」菜单中增加「操作日志」入口
- 日志列表：时间、操作类型、目标、结果、详情（可展开）
- 支持按操作类型/结果/时间范围筛选
- 支持导出为 JSON 文件

---

### 6.3 网络代理隔离

#### 6.3.1 问题

git 命令的网络请求（clone/pull/push/fetch）没有走应用的代理路由系统，与模型请求的代理隔离策略不一致。

#### 6.3.2 设计方案

**Git 网络代理路由**：

```kotlin
// GitRepository 新增
suspend fun configureProxy(proxyConfig: ProxyConfig?) {
    if (proxyConfig == null) {
        // 清除代理配置
        git("config", "--unset", "http.proxy")
        git("config", "--unset", "https.proxy")
    } else {
        // 设置代理（仅当前仓库，--local）
        git("config", "--local", "http.proxy", proxyConfig.httpUrl)
        git("config", "--local", "https.proxy", proxyConfig.httpsUrl)
        // 配置不走代理的域名（NO_PROXY）
        git("config", "--local", "http.noProxy", proxyConfig.noProxy.joinToString(","))
    }
}
```

**集成策略**：
1. git 网络操作默认走直连（与模型主请求一致，不需要代理）
2. 若用户在代理设置中开启「Git 走代理」，则自动配置 git 的 http.proxy
3. 代理随用随开：执行 pull/push/clone 前配置代理，完成后清除（与模型代理工具一致）
4. 克隆私有仓库时若失败，自动提示「是否尝试使用代理」

---

### 6.4 安全模式

#### 6.4.1 问题

没有全局的安全开关，用户可能在不知情的情况下执行危险操作（force push、reset --hard）。

#### 6.4.2 设计方案

**安全模式开关**：

```kotlin
data class GitSecurityConfig(
    val safeMode: Boolean = true,           // 安全模式（默认开启）
    val requireConfirmForForcePush: Boolean = true,
    val requireConfirmForResetHard: Boolean = true,
    val requireConfirmForDeleteRemote: Boolean = true,
    val blockForcePushToProtectedBranches: Boolean = true,
    val protectedBranches: List<String> = listOf("main", "master", "release/*"),
    val autoStashBeforeCheckout: Boolean = true  // 切换分支前自动 stash
)
```

**安全模式行为**：
- 开启时：所有危险操作必须二次确认，force push 到受保护分支被阻止
- 关闭时：危险操作仍需确认，但可以跳过（增加「不再提示」选项）
- 受保护分支：main、master、release/* 等分支禁止 force push 和删除（需先在设置中移除保护）

**UI 入口**：Git 页面「更多」→「安全设置」，包含上述所有选项。

---

## 7. 第六部分：实施路线图

### 阶段一：安全修复（P0 + P1，预计 3-5 天）

| 任务 | 优先级 | 预计工时 |
|------|--------|----------|
| 工作区路径遍历漏洞修复 | P0 | 0.5天 |
| git show 命令参数转义修复 | P0 | 0.5天 |
| 分支切换未提交改动检测 | P1 | 1天 |
| 大仓库操作超时保护 | P1 | 1天 |
| 危险操作二次确认 | P1 | 1天 |
| 并发 refresh 竞态修复 | P1 | 0.5天 |

**交付物**：安全修复完成，所有 P0/P1 风险关闭，单元测试覆盖。

---

### 阶段二：核心功能扩展（高优先级，预计 7-10 天）

| 任务 | 预计工时 | 依赖 |
|------|----------|------|
| git stash 暂存功能 | 1.5天 | 阶段一 |
| merge / rebase 合并功能 | 2天 | 阶段一 |
| 合并冲突可视化解决器 | 3天 | merge/rebase |
| reset 到指定提交 | 1天 | 危险操作确认 |
| cherry-pick 拣选提交 | 1天 | 阶段一 |
| diff 内存边界加固 | 0.5天 | - |
| 容器未就绪恢复机制 | 0.5天 | - |

**交付物**：核心版本控制功能完整，支持日常开发工作流。

---

### 阶段三：体验增强（中优先级，预计 5-7 天）

| 任务 | 预计工时 |
|------|----------|
| git blame 查看器 | 1.5天 |
| 提交历史搜索 | 1天 |
| .gitignore 可视化编辑器 | 1.5天 |
| reflog 查看与恢复 | 1天 |
| 空状态增强（四入口） | 0.5天 |
| 操作进度反馈 | 1天 |
| 提交详情页增强 | 0.5天 |
| 分支管理增强 | 0.5天 |

**交付物**：用户体验显著提升，覆盖常用高级功能。

---

### 阶段四：安全加固 + 高级功能（低优先级，预计 5-7 天）

| 任务 | 预计工时 |
|------|----------|
| 凭据存储加密 | 2天 |
| 操作审计日志 | 1天 |
| 网络代理隔离 | 1天 |
| 安全模式 | 0.5天 |
| 子模块管理 | 1天 |
| 交互式 rebase | 1.5天 |
| git bisect 二分查找 | 1天 |
| git worktree 多工作区 | 1天 |
| 提交签名验证 | 0.5天 |

**交付物**：企业级安全能力，高级用户功能完整。

---

### 总工时估算

| 阶段 | 工时 | 累计 |
|------|------|------|
| 阶段一：安全修复 | 3-5天 | 3-5天 |
| 阶段二：核心功能 | 7-10天 | 10-15天 |
| 阶段三：体验增强 | 5-7天 | 15-22天 |
| 阶段四：安全加固+高级功能 | 5-7天 | 20-29天 |

**总计：约 20-29 个工作日**（单人开发，含测试和联调）

---

## 8. 第七部分：验收标准

### 8.1 安全验收

- [ ] 路径遍历攻击（`../../etc/passwd`）被拦截，返回空结果
- [ ] git 命令参数包含特殊字符时经 shellQuote 安全转义，无命令注入
- [ ] 所有危险操作（force push/reset --hard/delete remote）有二次确认
- [ ] 凭据存储加密（若实现），明文文件不可直接读取
- [ ] 操作审计日志记录所有远程修改和不可逆操作

### 8.2 功能验收

- [ ] stash 功能完整：创建/列表/恢复/应用/删除/清空
- [ ] merge/rebase 功能完整，冲突时进入冲突解决器
- [ ] 冲突解决器支持：接受当前/接受目标/接受双方/手动编辑
- [ ] reset 支持三种模式（soft/mixed/hard），--hard 需输入确认
- [ ] cherry-pick 功能完整，冲突时可解决
- [ ] blame 查看器显示每行的提交/作者/时间，可跳转提交详情
- [ ] 提交搜索支持：关键词/作者/文件/日期范围
- [ ] .gitignore 编辑器支持：模板插入/语法高亮/路径测试
- [ ] reflog 查看与恢复：可从误操作中恢复

### 8.3 性能验收

- [ ] 所有 git 命令有超时保护，不永久 loading
- [ ] 大仓库（万级提交）首页加载 < 5秒，超时后友好提示
- [ ] diff 计算有行数和字符数双重保护，不导致 OOM
- [ ] 并发 refresh 无竞态，最终状态一致
- [ ] 分页加载提交流畅，滚动无卡顿

### 8.4 用户体验验收

- [ ] 空状态提供四入口（初始化/克隆/选择工作区/帮助）
- [ ] 长操作（clone/pull/push）有进度反馈和取消按钮
- [ ] 提交详情显示统计信息，文件 diff 可内联展开
- [ ] 分支列表显示最后提交时间、ahead/behind、合并状态
- [ ] 所有错误提示友好，不显示原始 git 报错堆栈
- [ ] 操作成功/失败有 toast 反馈

### 8.5 兼容性验收

- [ ] 新增功能不破坏现有 API 和 UI 交互
- [ ] 所有新组件适配深色/浅色主题
- [ ] 所有新组件适配动效强度设置（关闭动效时无动画）
- [ ] 与统一查看器/编辑器集成（diff 过大时可跳转编辑器）
- [ ] 与代理系统集成（git 网络请求可走代理）

---

> 文档结束。本设计文档覆盖审计报告中所有 P0/P1/P2 风险、功能扩展、UI/UX 优化和安全加固方向，共 6 大部分、30+ 具体设计项、4 阶段实施路线。
