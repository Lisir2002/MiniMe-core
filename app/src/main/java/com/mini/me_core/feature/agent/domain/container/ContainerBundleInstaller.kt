package com.mini.me_core.feature.agent.domain.container

import com.mini.me_core.core.environment.EnvironmentDetector
import com.mini.me_core.core.model.MiniMeLog
import com.mini.me_core.core.container.ApkStdoutParser
import com.mini.me_core.feature.agent.domain.container.progress.InstallPhase
import com.mini.me_core.feature.agent.domain.container.progress.ParallelPrefetchManager
import com.mini.me_core.core.container.PrefetchConcurrencyPolicy
import com.mini.me_core.feature.agent.domain.container.progress.ProgressSource
import com.mini.me_core.feature.agent.domain.container.progress.RealProgressAggregator
import com.mini.me_core.feature.terminal.data.bundle.BundleInstallState
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundle
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundleId
import com.mini.me_core.feature.terminal.data.bundle.TerminalBundles
import com.mini.me_core.feature.terminal.data.repository.TerminalBundleRepository
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import com.mini.me_core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** 从 LinuxContainerEngine 抽出的 bundle 安装/卸载/自定义包族（同包 extension，不改语义）。 */

internal suspend fun LinuxContainerEngine.installBundle(id: TerminalBundleId) {
        val bundle = TerminalBundles.byId(id) ?: throw IllegalArgumentException("未知 bundle: $id")
        if (!containerInstaller.isInstalledFor(currentProfile)) throw IllegalStateException("容器未初始化，请先初始化 rootfs")
        bundleOpMutex.withLock {
            // F3：记录当前 Job 供 cancelCurrentBundleOp() 取消。
            currentBundleOpJob = coroutineContext[kotlinx.coroutines.Job]
            // 已装直接返回
            if (bundleRepository.isInstalledSnapshot(id)) return
            bundleRepository.emitInstalling(id, line = "准备中…")
            _initProgress.value = ContainerInitState.BundleInstalling(bundleId = id, line = "准备中…")

            // ── Level 2+3 融合：开始会话（Prefetch + Aggregator） ──
            val conResult = concurrencyPolicy.calculate()
            val slotsN = conResult.slots
            val pkgs = bundle.packages.trim().split(Regex("""\s+""")).filter(String::isNotBlank)
            val prefetch = ParallelPrefetchManager(
                slotsCount = slotsN,
                runSync = ::execForManager,
                streamShell = { cmd, to -> streamExecNoInstall(cmd, projectPath = null, timeoutMs = to) },
            )
            progressAggregator.startInstallSession(
                slots = slotsN,
                totalDependsEstimate = pkgs.size * 6,
                bundleId = id,
            )
            // 监听 Prefetch 槽流 → Aggregator
            val slotsCollectJob = initScope.launch {
                prefetch.slots.collect { s -> progressAggregator.onSlotsFromPrefetch(s) }
            }
            var exitCode: Int? = null
            var failedReason: String? = null
            // S3：把 prefetch fire-and-forget 的 Job 存下来；失败分支/exitCode!=0 立刻 cancel
            var prefetchJob: kotlinx.coroutines.Job? = null
            try {
                // Level 3a：先查依赖图 → 并行预取（fail-open：单包失败交给 apk 兜底）
                runCatching {
                    val depends = prefetch.resolveDependencies(pkgs, timeoutMs = LinuxContainerEngine.APK_LIST_TIMEOUT_MS)
                    // 预取异步跑；完成后让 Aggregator flush 失败合并摘要（Fix C 汇总不刷屏）
                    prefetchJob = initScope.launch {
                        val fin = runCatching { prefetch.prefetch(depends) }
                            .getOrNull()
                        if (fin is ParallelPrefetchManager.PrefetchEvent.Finished) {
                            runCatching {
                                progressAggregator.flushPrefetchFailures(fin.failedPackages)
                            }.onFailure { t -> MiniMeLog.w(LinuxContainerEngine.TAG, "flush failures err", t) }
                        }
                    }
                    // 给预取 800ms 头启动时间（让 curl 先拿 Content-Length，slot 立刻从 WAITING→DLING，UI 首帧不空方块）
                    kotlinx.coroutines.delay(800)
                }.onFailure { t ->
                    MiniMeLog.w(LinuxContainerEngine.TAG, "prefetch prepare 跳过：${t.message}")
                }

                // F4：hook 内容从 assets/bundle-hooks/<hookFileName> 读，不再内联在 Kotlin。
                val hookContent: String? = bundle.hookFileName?.let { fn ->
                    runCatching { context.assets.open("bundle-hooks/$fn").bufferedReader().use { it.readText() } }
                        .onFailure { MiniMeLog.w(LinuxContainerEngine.TAG, "读取 hook 失败 $fn", it) }
                        .getOrNull()
                }
                val hookLines = hookContent?.lineSequence()?.count() ?: 0
                val script = buildString {
                    // D2 Fix：apk exit=2 自愈（Alpine apk exit=2 = 包名找不到/约束不满足，
                    // 常见于 APKINDEX 还没同步或裸名 <-> 带版本名的 world 冲突）。
                    // 策略：set -e 先不立即退出，apk add 失败时先 `apk update` 再重试一次，
                    // 最后 `set -e` 以真实 exit code 判定。
                    append("set +e\n")
                    append(apkMirrorAndUpdateScriptOnce())
                    append("apk add --no-cache ${bundle.packages}\n")
                    append("APK_EXIT=\$?\n")
                    append("if [ \$APK_EXIT -ne 0 ]; then\n")
                    append("  echo \"[retry] 首次 apk add exit=\$APK_EXIT，apk update 后再试一次…\"\n")
                    append("  apk update >/dev/null 2>&1\n")
                    append("  apk fix --no-cache >/dev/null 2>&1 || true\n")
                    append("  apk add --no-cache ${bundle.packages}\n")
                    append("  APK_EXIT=\$?\n")
                    append("fi\n")
                    hookContent?.let { hook ->
                        append("if [ \$APK_EXIT -eq 0 ]; then\n")
                        append("# post-install hook for ").append(id.stableKey).append('\n')
                        append(hook.prependIndent("  ")).append('\n')
                        append("  APK_EXIT=\$?\n")
                        append("fi\n")
                    }
                    append("exit \$APK_EXIT\n")
                }
                // 告诉 Aggregator：apk OK 之后进入 POST_HOOK 阶段，行数在这里
                if (hookLines > 0) {
                    // 我们还没到 OK（INSTALL phase），这里先把计划行数存一下，等 OK 语义命中再 enterPostHook
                }
                streamExecNoInstall(script, projectPath = null, timeoutMs = LinuxContainerEngine.APK_ONE_BUNDLE_TIMEOUT_MS).collect { event ->
                    when (event) {
                        is CommandEvent.Line -> {
                            // E1：从 apk 输出粗估进度（Fetching=下载中 ~0.5，OK: N MiB=接近完成 ~0.9）。
                            val estProgress = when {
                                event.text.startsWith("OK:") -> 0.9f
                                event.text.startsWith("Fetching") -> 0.5f
                                else -> null
                            }
                            bundleRepository.emitInstalling(id, line = event.text, progress = estProgress)
                            _initProgress.value = ContainerInitState.BundleInstalling(bundleId = id, line = event.text)
                            progressAggregator.onApkLine(event.text)
                            // S4：shell 里只要出现 WARNING fetching ... IO ERROR 就立刻 cancel 预取（避免继续占网）
                            val t = event.text
                            val lower = t.lowercase()
                            if ((lower.contains("warning: fetching") || lower.contains("io error")) && prefetchJob?.isActive == true) {
                                MiniMeLog.w(LinuxContainerEngine.TAG, "apk 行内出现镜像 IO WARNING → 立刻 cancel prefetch")
                                prefetchJob?.cancel("apk stdout saw IO WARNING fetching")
                                runCatching { prefetch.shutdown("apk stdout IO WARNING") }
                            }
                            // 解析 OK 语义后，如果有 postHook 行数就切 phase 到 POST_HOOK
                            val sem = ApkStdoutParser.parse(event.text).semantic
                            if (sem is ApkStdoutParser.Semantic.Ok && hookLines > 0) {
                                initScope.launch { progressAggregator.enterPostHook(totalLines = hookLines.coerceAtLeast(1)) }
                            }
                            if (sem is ApkStdoutParser.Semantic.Installing && hookLines == 0) {
                                // noop
                            }
                            if (sem is ApkStdoutParser.Semantic.PostLine && hookLines > 0) {
                                initScope.launch { progressAggregator.advancePostHookLine() }
                            }
                        }
                        is CommandEvent.Exit -> {
                            exitCode = event.code
                            // S3：exitCode≠0 立刻 cancel prefetch（用户截图 FAILED 后还 5K/s 占网）
                            if (event.code != null && event.code != 0) {
                                MiniMeLog.w(LinuxContainerEngine.TAG, "apk exit=${event.code} → 立刻 cancel prefetch + shutdown（回收并发槽/Socket）")
                                prefetchJob?.cancel("apk exit=${event.code} != 0")
                                runCatching { prefetch.shutdown("apk exit=${event.code}") }
                            }
                            initScope.launch {
                                progressAggregator.onExitCode(
                                    code = event.code,
                                    postHookDone = true,
                                )
                            }
                        }
                        is CommandEvent.TimedOut -> {
                            MiniMeLog.w(LinuxContainerEngine.TAG, "apk 命令超时，立刻 cancel prefetch + shutdown")
                            prefetchJob?.cancel("apk timeout")
                            runCatching { prefetch.shutdown("apk timeout") }
                        }
                    }
                }
            } catch (e: Exception) {
                MiniMeLog.w(LinuxContainerEngine.TAG, "安装 bundle ${id.stableKey} 异常", e)
                failedReason = "安装 ${bundle.displayName} 失败：${e.message}"
                // S3：异常分支（比如 apk 解析炸/取消/etc.）也立刻释放预取资源
                prefetchJob?.cancel("installBundle exception: ${e.message}")
                runCatching { prefetch.shutdown("installBundle exception: ${e.message}") }
            } finally {
                // S3：finally 最后兜底 cancel + shutdown（成功/失败/异常 三分支都要释放）
                runCatching { prefetchJob?.cancel("installBundle finally") }
                // finally 时 cleanupPartialCache → 无论成功/失败，.part 半截文件一定删除；
                // 只有 exit==0 且未失败，我们才清理 /var/cache/apk/*.apk（已装完，释放手机存储）
                val ok = (exitCode == 0 && failedReason == null)
                runCatching {
                    initScope.launch {
                        // cleanup 包在 initScope(SupervisorIO)：避免阻塞主线程；超时设 3s 防止卡住结束流程
                        kotlinx.coroutines.withTimeoutOrNull(3500) {
                            prefetch.cleanupPartialCache(cleanupInstalledApks = ok, timeoutMs = 2500)
                        }
                    }
                }
                // 原来的 shutdown 放后面（内部再跑一次 cleanupPartialCache，幂等）
                runCatching { prefetch.shutdown("installBundle finally (ok=$ok)") }
                slotsCollectJob.cancel()
                progressAggregator.endSession(
                    // S3：若 exit 非 0 或 catch 进了 failedReason → 强制以 FAILED 快照结束会话，
                    // UI 的「X 槽并行 · Y KB/s」会立刻归零，不会再显示「好像还在下载」的假速率。
                    forceFailSnapshot = (exitCode != null && exitCode != 0) || failedReason != null,
                )
                if (exitCode == 0 && failedReason == null) {
                    bundleRepository.markInstalled(id, bundle)
                    MiniMeLog.i(LinuxContainerEngine.TAG, "Bundle 安装成功：${bundle.displayName} (${bundle.packages})")
                } else {
                    val base = failedReason
                        ?: "安装 ${bundle.displayName} 失败（exit=$exitCode），请在终端设置中重试"
                    // E3：失败 reason 自动追加常见原因，便于用户自助排查。
                    val reason = "$base\n常见原因：1)容器网络未就绪 2)镜像源不通 3)磁盘不足"
                    MiniMeLog.w(LinuxContainerEngine.TAG, reason)
                    bundleRepository.markFailed(id, reason)
                }
                // ⚠️ 无论成功/失败/异常，最后都复位 _initProgress 回 Ready。
                val cur = _initProgress.value
                if (cur is ContainerInitState.BundleInstalling || cur is ContainerInitState.BundleUninstalling) {
                    _initProgress.value = ContainerInitState.Ready(migratedFromLegacyProvisioned = false)
                }
                if (failedReason != null) throw IllegalStateException(failedReason)
                if (exitCode != 0) {
                    val reason = "安装 ${bundle.displayName} 失败（exit=$exitCode），请在终端设置中重试"
                    throw IllegalStateException(reason)
                }
            }
        }
        currentBundleOpJob = null
    }

    /** 批量安装多个 Bundle（AI 推荐组合一键安装使用）。遇到任一失败即停并向上抛。 */
internal suspend fun LinuxContainerEngine.installBundlesOrdered(ids: Iterable<TerminalBundleId>) {
        for (id in ids) installBundle(id)
    }

    /**
     * 卸载一个 Bundle：apk del --purge <packages> + 清 bundle 标记。
     *
     * 注意：不自动清理被依赖的其他包（其他 bundle 可能 share python3 等），
     * 也不回滚 post-install hook 的副作用（git config / shell 切换）。
     * UI 层给用户文案提示"卸载后会释放 X MB，git/bash 配置需手动恢复"。
     */
internal suspend fun LinuxContainerEngine.uninstallBundle(id: TerminalBundleId) {
        val bundle = TerminalBundles.byId(id) ?: throw IllegalArgumentException("未知 bundle: $id")
        if (!containerInstaller.isInstalledFor(currentProfile)) {
            // 容器没装自然没包装过，直接 NotInstalled
            bundleRepository.markUninstalled(id)
            return
        }
        bundleOpMutex.withLock {
            if (!bundleRepository.isInstalledSnapshot(id)) return
            bundleRepository.emitUninstalling(id)
            _initProgress.value = ContainerInitState.BundleUninstalling(bundleId = id)
            var exitCode: Int? = null
            try {
                streamExecNoInstall(
                    "apk del --purge ${bundle.packages}",
                    projectPath = null,
                    timeoutMs = LinuxContainerEngine.APK_ONE_BUNDLE_TIMEOUT_MS
                ).collect { event ->
                    when (event) {
                        is CommandEvent.Line -> Unit
                        is CommandEvent.TimedOut -> Unit
                        is CommandEvent.Exit -> exitCode = event.code
                    }
                }
            } catch (e: Exception) {
                MiniMeLog.w(LinuxContainerEngine.TAG, "卸载 bundle ${id.stableKey} 异常", e)
                // 异常不回退 state：实际可能是网络断等，让用户下次再试 apk del / 或重置容器。
                bundleRepository.markUninstalled(id)
                return@withLock
            } finally {
                // F1：apk del 后验真——跑 apk info 确认主包已不在 world，才 markUninstalled；
                // 仍在则 markFailed 并恢复 Installed，避免 UI 假卸载。
                val pkgs = bundle.packages.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                var stillInstalled: List<String> = emptyList()
                runCatching {
                    val installedNow = mutableSetOf<String>()
                    streamExecNoInstall(
                        "apk info 2>/dev/null",
                        projectPath = null,
                        timeoutMs = LinuxContainerEngine.APK_LIST_TIMEOUT_MS
                    ).collect { event ->
                        if (event is CommandEvent.Line) {
                            val line = event.text.trim()
                            if (line.isNotBlank()) installedNow.add(line)
                        }
                    }
                    stillInstalled = pkgs.filter { p ->
                        installedNow.any { it == p || it.startsWith("$p-") }
                    }
                }.onFailure { MiniMeLog.w(LinuxContainerEngine.TAG, "卸载后验真 apk info 失败", it) }

                if (stillInstalled.isEmpty()) {
                    bundleRepository.markUninstalled(id)
                } else {
                    bundleRepository.markFailed(id, "卸载失败：包仍存在（${stillInstalled.joinToString(", ")}）")
                    bundleRepository.markInstalled(id, bundle)
                }
                MiniMeLog.i(LinuxContainerEngine.TAG, "Bundle 卸载：${bundle.displayName} (exit=$exitCode, stillInstalled=$stillInstalled)")
                // 无论成功/异常退出 withLock 前都复位 Ready，避免 UI 永远停在 BundleUninstalling。
                val cur = _initProgress.value
                if (cur is ContainerInitState.BundleInstalling || cur is ContainerInitState.BundleUninstalling) {
                    _initProgress.value = ContainerInitState.Ready(migratedFromLegacyProvisioned = false)
                }
            }
        }
    }

    /** 自定义 apk 包批量安装（高级折叠区入口）。返回失败列表；全成功返回 emptyList。 */
internal suspend fun LinuxContainerEngine.installCustomPackages(pkgs: List<String>): List<String> {
        if (pkgs.isEmpty()) return emptyList()
        if (!containerInstaller.isInstalledFor(currentProfile)) throw IllegalStateException("容器未初始化，请先初始化 rootfs")
        val argLine = pkgs.joinToString(" ")
        _initProgress.value = ContainerInitState.BundleInstalling(bundleId = null, line = "安装自定义包：$argLine …")
        var exitCode: Int? = null
        var lastLine: String? = null
        var failed: List<String> = pkgs
        try {
            bundleOpMutex.withLock {
                try {
                    val script = buildString {
                        // D2 Fix：exit=2 自愈，同 installBundle
                        append("set +e\n")
                        append(apkMirrorAndUpdateScriptOnce())
                        append("apk add --no-cache ").append(argLine).append('\n')
                        append("APK_EXIT=\$?\n")
                        append("if [ \$APK_EXIT -ne 0 ]; then\n")
                        append("  echo \"[retry] 首次 apk add exit=\$APK_EXIT，apk update 后再试一次…\"\n")
                        append("  apk update >/dev/null 2>&1\n")
                        append("  apk fix --no-cache >/dev/null 2>&1 || true\n")
                        append("  apk add --no-cache ").append(argLine).append('\n')
                        append("  APK_EXIT=\$?\n")
                        append("fi\n")
                        append("exit \$APK_EXIT\n")
                    }
                    streamExecNoInstall(script, projectPath = null, timeoutMs = LinuxContainerEngine.APK_CUSTOM_TIMEOUT_MS).collect { event ->
                        when (event) {
                            is CommandEvent.Line -> {
                                lastLine = event.text
                                // 同步更新 _initProgress 的 line，让 UI 能看到最新输出行
                                _initProgress.value = ContainerInitState.BundleInstalling(bundleId = null, line = event.text)
                            }
                            is CommandEvent.TimedOut -> exitCode = null
                            is CommandEvent.Exit -> exitCode = event.code
                        }
                    }
                } catch (e: Exception) {
                    lastLine = e.message
                    exitCode = -1
                }
            }
            if (exitCode == 0) {
                failed = emptyList()
            } else {
                // ⚠️ 以前这里写：failed = pkgs（apk 非 0 退出就把所有输入包都标失败）。
                // 但用户日志显示：「zsh zsh-vcs」输入两个包，apk 退出码非 0（报 "1 error; 15 MiB in 24 packages"），
                // 而 apk info 字符数从 230 → 295，证明 zsh 和 22 个依赖都已经装进去了，只有 zsh-vcs
                // （Alpine 3.21 根本没有这个包名）报错——把 zsh 也列为失败是错误的。
                // 修复：apk 非 0 退出时，逐个查每个输入包是否真的在 apk world 里，只把真正不存在的包列入 failed。
                val installedWorld: Set<String> = runCatching {
                    execCaptured("apk info 2>/dev/null", projectPath = null, timeoutMs = LinuxContainerEngine.APK_LIST_TIMEOUT_MS)
                        .output.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && LinuxContainerEngine.APK_PKG_NAME_REGEX.matches(it) }
                        .toSet()
                }.getOrDefault(emptySet())
                failed = pkgs.filter { pkg ->
                    // apk world 里完全没包含任何一个与 pkg 名字匹配的已安装条目，才算真正失败
                    installedWorld.none { installed -> installed == pkg || installed.startsWith("$pkg-") }
                }
                if (failed.size < pkgs.size) {
                    // 有部分包其实成功了，把成功的那些也加到自定义快照里（不要漏掉）
                    val okPkgs = pkgs - failed.toSet()
                    if (okPkgs.isNotEmpty()) {
                        bundleRepository.addCustomSnapshots(okPkgs)
                        MiniMeLog.i(LinuxContainerEngine.TAG, "自定义包部分成功（exit=$exitCode，部分失败）：已安装=$okPkgs，失败=$failed")
                    }
                }
            }
            if (failed.isEmpty()) {
                // 全成功：加到自定义包快照（UI 列表/磁盘标记都更新）
                bundleRepository.addCustomSnapshots(pkgs)
                MiniMeLog.i(LinuxContainerEngine.TAG, "自定义包安装成功：$argLine")
            } else if (failed.size == pkgs.size) {
                // 全部失败，记录 warn（部分成功的 warn 已经在上面分支里打了）
                MiniMeLog.w(LinuxContainerEngine.TAG, "自定义包安装全部失败($lastLine)：$argLine")
            }
        } finally {
            // ⚠️ 无论成功/失败/异常，finally 都无条件复位 _initProgress 回 Ready。
            // 不再依赖 containerInstaller.isInstalledFor(currentProfile) 条件：
            //   - currentProfile 是 @Volatile，操作期间若被 profile flow 异步更新，条件可能假
            //   - 一旦跳过，UI 永久停在 BundleInstalling，用户看到"装完还在转圈"
            // 只要当前状态仍在 BundleInstalling/Uninstalling，就复位成 Ready；
            // Idle / ExtractingRootfs / DeployingProot / Failed 这些非本操作的状态不破坏。
            val cur = _initProgress.value
            if (cur is ContainerInitState.BundleInstalling || cur is ContainerInitState.BundleUninstalling) {
                _initProgress.value = ContainerInitState.Ready(migratedFromLegacyProvisioned = false)
            }
            // 成功/失败后都刷新一次"真实的"自定义包清单（减去和 bundle 包重叠的，留下用户真正装的）
            runCatching { refreshCustomPackagesSnapshot() }
        }
        return failed
    }

    /** 卸载一个自定义包。成功返回 true。 */
internal suspend fun LinuxContainerEngine.uninstallCustomPackage(pkg: String): Boolean {
        if (!containerInstaller.isInstalledFor(currentProfile)) {
            bundleRepository.removeCustomSnapshot(pkg)
            return true
        }
        var ok = false
        // 进入卸载前先把整体状态挂成 BundleUninstalling（bundleId=null 表示自定义包）
        _initProgress.value = ContainerInitState.BundleUninstalling(bundleId = null)
        try {
            bundleOpMutex.withLock {
                try {
                    streamExecNoInstall(
                        "apk del --purge $pkg",
                        projectPath = null,
                        timeoutMs = LinuxContainerEngine.APK_ONE_BUNDLE_TIMEOUT_MS
                    ).collect { event ->
                        if (event is CommandEvent.Exit) ok = (event.code == 0)
                    }
                } catch (e: Exception) {
                    MiniMeLog.w(LinuxContainerEngine.TAG, "卸载自定义包异常: $pkg", e)
                }
            }
        } finally {
            // 无论成功/失败/异常都复位到 Ready，避免永久停在 Uninstalling
            val cur = _initProgress.value
            if (cur is ContainerInitState.BundleInstalling || cur is ContainerInitState.BundleUninstalling) {
                _initProgress.value = ContainerInitState.Ready(migratedFromLegacyProvisioned = false)
            }
            bundleRepository.removeCustomSnapshot(pkg)
            // 刷新一次真实列表
            runCatching { refreshCustomPackagesSnapshot() }
        }
        return ok
    }

    /**
     * 通过 `apk info` 把容器当前已安装的 apk 世界包列出来，
     * 减去 7 个 bundle 覆盖的包，再把"用户自定义的"那部分写回 TerminalBundleRepository。
     * 由终端设置页进入时 / 每次自定义包操作后调用一次，保证 UI 列表与实际一致。
     */