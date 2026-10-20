package org.minime.core.ui.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.fallback.FallbackTheme
import org.minime.core.tokens.ThemeSnapshot

/**
 * 主题管理器（R2 运行时，白皮书 §8.1 时序 + §8.2 性能设计点）。
 *
 * 职责边界：
 * - **持有**当前生效快照（`StateFlow<ThemeSnapshot>`）；
 * - **持久化**选择（[ThemeStore]，同步可读保证首帧不闪）；
 * - **缓存**已编译快照（内存，切换 = 对象替换，换肤路径零 I/O）；
 * - **后台**编译新包（导入不卡 UI）+ **合并**连续切换请求（节流）；
 * - **降级**：任何失败都保持上一个可用快照，绝不无主题（G1）。
 *
 * 不负责：包的下载/商店（二期）、覆写编辑（R4 工坊在其之上叠加覆写层）、
 * 系统深浅色监听（由调用方按系统上下文调用 [select] 传入修饰符）。
 */
class ThemeManager(
    private val store: ThemeStore,
    private val packs: ThemePackRepository,
    private val scope: CoroutineScope,
    private val compiler: ThemeCompiler = ThemeCompiler(),
    /** 内置默认包 JSON（classpath `/default-theme.json`；null = 无内置包，退到 fallback） */
    private val builtInJson: String? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _snapshot = MutableStateFlow<ThemeSnapshot>(FallbackTheme.snapshot)
    val snapshot: StateFlow<ThemeSnapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow<ThemeStatus>(ThemeStatus.Idle)
    val status: StateFlow<ThemeStatus> = _status.asStateFlow()

    /** 内存快照缓存：(packId, modifiers) → 编译产物。切换命中即对象替换（§8.2） */
    private val cache = HashMap<CacheKey, ThemeSnapshot>()

    /** 当前后台编译任务；新请求到达即取消旧任务（节流：只让最后一次生效） */
    private var pendingJob: Job? = null

    /** 当前**实际生效**的包标识（调试面板/工坊读取；失败时取降级后的包，而非失败目标） */
    val currentPackId: String get() = when (val s = _status.value) {
        is ThemeStatus.Ready -> s.packId
        is ThemeStatus.Loading -> s.packId
        is ThemeStatus.Failed -> s.fellBackTo ?: FALLBACK_ID
        ThemeStatus.Idle -> BUILT_IN
    }

    /** 上一次失败（供设置页提示「你选的包不可用」）；无失败则 null */
    val lastFailure: ThemeStatus.Failed? get() = _status.value as? ThemeStatus.Failed

    // ---------- 冷启动 ----------

    /**
     * 冷启动恢复：**同步**执行，必须在 `setContent` 之前调用完成（§8.2「首帧不闪」）。
     *
     * 实测（2026-10-21，JVM 桌面，196 条令牌）编译 p50 ≈ 2.4ms，
     * 因此「同步编译」足以在首帧前完成——无需引入磁盘快照缓存与序列化层
     * （收益不足以抵消复杂度与出错面，见 CHANGELOG 偏离记录）。
     *
     * 失败路径：顺序降级 缓存 → 内置包 → fallback，任何一步失败都不阻塞启动。
     */
    fun initialize() {
        val packId = store.currentPackId() ?: BUILT_IN
        val modifiers = store.currentModifiers()

        cache[CacheKey(packId, modifiers)]?.let {
            _snapshot.value = it
            return
        }

        val json = packs.load(packId) ?: builtInJson
        if (json == null) {
            // 无包可选（未安装且无内置）：保持 fallback 快照
            _snapshot.value = FallbackTheme.snapshot
            _status.value = ThemeStatus.Ready(
                packId = FALLBACK_ID, version = "fallback",
                modifiers = emptyList(), warnings = 0, degraded = true,
            )
            return
        }

        runCatching { compileInto(json, packId, modifiers) }
            .onSuccess { result ->
                _snapshot.value = result.snapshot
                _status.value = ThemeStatus.Ready(
                    packId, result.snapshot.meta.packVersion, modifiers,
                    warnings = result.warnings.size, degraded = false,
                )
            }
            .onFailure { error ->
                // 降级链：目标包失败 → 内置包 → fallback。前两者成功即视为「降级可用」
                val fellBack = builtInJson?.let { builtIn ->
                    runCatching {
                        val result = compileInto(builtIn, BUILT_IN, emptyList())
                        _snapshot.value = result.snapshot
                        _status.value = ThemeStatus.Ready(
                            BUILT_IN, result.snapshot.meta.packVersion, emptyList(),
                            warnings = result.warnings.size, degraded = true,
                        )
                    }.isSuccess
                } ?: false

                _status.value = ThemeStatus.Failed(
                    packId = packId,
                    modifiers = modifiers,
                    code = (error as? ThemeCompileException)?.code ?: CompileErrorCode.ENGINE_INTERNAL,
                    message = error.message ?: "冷启动编译失败",
                    fellBackTo = if (fellBack) BUILT_IN else null,
                )
            }
    }

    // ---------- 切换 ----------

    /**
     * 选择主题（后台编译 + 节流）。
     *
     * 节流语义：连续调用只让**最后一次**生效（取消在途任务）——
     * 用户连点主题列表时不排队编译，也不产生中间态闪变。
     *
     * 合并语义：编译成功后才发射新快照（§8.1「完成才允许切换」），
     * 失败则保持当前快照并把 [ThemeStatus.Failed] 交给设置页呈现。
     */
    fun select(packId: String, modifiers: List<String>) {
        pendingJob?.cancel()
        val key = CacheKey(packId, modifiers)

        cache[key]?.let { cached ->
            // 命中缓存：直接切换（零 I/O，对象替换）
            store.saveSelection(packId, modifiers)
            _snapshot.value = cached
            _status.value = ThemeStatus.Ready(
                packId, cached.meta.packVersion, modifiers,
                warnings = 0, degraded = false,
            )
            return
        }

        val json = packs.load(packId) ?: if (packId == BUILT_IN) builtInJson else null
        if (json == null) {
            _status.value = ThemeStatus.Failed(
                packId, modifiers, CompileErrorCode.PACK_NOT_FOUND,
                "主题包不存在: $packId",
            )
            return
        }

        _status.value = ThemeStatus.Loading(packId, modifiers)
        pendingJob = scope.launch {
            val outcome = runCatching {
                withContext(computeDispatcher) { compileInto(json, packId, modifiers) }
            }
            outcome.onSuccess { result ->
                store.saveSelection(packId, modifiers)
                _snapshot.value = result.snapshot
                _status.value = ThemeStatus.Ready(
                    packId, result.snapshot.meta.packVersion, modifiers,
                    warnings = result.warnings.size, degraded = false,
                )
            }.onFailure { error ->
                _status.value = ThemeStatus.Failed(
                    packId, modifiers,
                    (error as? ThemeCompileException)?.code ?: CompileErrorCode.ENGINE_INTERNAL,
                    error.message ?: "主题包编译失败",
                )
            }
        }
    }

    /**
     * 导入主题包并立即应用（R4 工坊与设置页共用同一路径）。
     *
     * @return 编译结果；失败抛 [ThemeCompileException]，由调用方呈现 §5.2 错误码与警告
     */
    suspend fun importAndApply(
        packId: String,
        json: String,
        modifiers: List<String> = emptyList(),
    ): org.minime.core.compiler.CompileResult = withContext(computeDispatcher) {
        val result = compileInto(json, packId, modifiers)
        packs.save(packId, json)
        store.saveSelection(packId, modifiers)
        _snapshot.value = result.snapshot
        _status.value = ThemeStatus.Ready(
            packId, result.snapshot.meta.packVersion, modifiers,
            warnings = result.warnings.size, degraded = false,
        )
        org.minime.core.compiler.CompileResult(result.snapshot, result.warnings)
    }

    // ---------- 内部 ----------

    /** 编译 + 子系统实例复用（§8.2）+ 写缓存 */
    private fun compileInto(json: String, packId: String, modifiers: List<String>): org.minime.core.compiler.CompileResult {
        val result = compiler.compileJson(json, modifiers)
        val reused = SnapshotReuser.reuse(_snapshot.value.takeIf { _status.value !is ThemeStatus.Idle }, result.snapshot)
        val final = if (reused === result.snapshot) result else org.minime.core.compiler.CompileResult(reused, result.warnings)
        cache[CacheKey(packId, modifiers)] = final.snapshot
        return final
    }

    /** 丢弃缓存（主题包更新后调用）；packId 为 null 时全清 */
    fun invalidate(packId: String? = null) {
        if (packId == null) {
            cache.clear()
            return
        }
        cache.keys.filter { it.packId == packId }.forEach { cache.remove(it) }
    }

    private data class CacheKey(val packId: String, val modifiers: List<String>)

    companion object {
        /** 内置默认包标识 */
        const val BUILT_IN = "built-in"
        /** 兜底快照标识（调试面板显示用，非真实包） */
        const val FALLBACK_ID = "fallback"
    }
}
