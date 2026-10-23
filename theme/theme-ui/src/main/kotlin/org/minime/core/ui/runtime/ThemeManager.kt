package org.minime.core.ui.runtime

import kotlinx.coroutines.CancellationException
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
import org.minime.core.compiler.CompileResult
import org.minime.core.compiler.OverlayApplier
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.fallback.FallbackTheme
import org.minime.core.schema.DiffSerializer
import org.minime.core.schema.DtcgParser
import org.minime.core.schema.ThemePack
import org.minime.core.schema.TokenTypeCheck
import org.minime.core.schema.TokenValue
import org.minime.core.tokens.ThemeSnapshot
import org.minime.core.tokens.TokenVocabulary

/**
 * 主题管理器（R2 运行时，白皮书 §8.1 时序 + §8.2 性能设计点；R4 叠加覆写层）。
 *
 * 职责边界：
 * - **持有**当前生效快照（`StateFlow<ThemeSnapshot>`）；
 * - **持久化**选择（[ThemeStore]，同步可读保证首帧不闪）；
 * - **缓存**已编译快照（内存，切换 = 对象替换，换肤路径零 I/O）；
 * - **后台**编译新包（导入不卡 UI）+ **合并**连续请求（节流：只让最后一次生效）；
 * - **降级**：任何失败都保持上一个可用快照，绝不无主题（G1）；
 * - **覆写层**（R4）：[overlays] 内存态（路径 → 值），经 [OverlayApplier] 挂到
 *   resolutionOrder 最末走同一编译流水线（禁第二套合并逻辑）；失败回滚上一版覆写态。
 *
 * 不负责：包的下载/商店（二期）、系统深浅色监听（由调用方按系统上下文调用 [select] 传入修饰符）。
 *
 * 覆写语义约定：
 * - **内存态**：覆写不写回 [ThemeStore] / [ThemePackRepository]（默认包零污染）——
 *   diff 主题包的显式导出（[exportDiff]）只产文本，落盘经宿主 SAF（R4.3）；
 * - **随基础包切换清零**：[select] / [importAndApply] / [initialize] 建立新基础时
 *   覆写即会话结束（换包 = 用该包原初面貌）；
 * - **last-wins**：连续覆写（拖动色板/滑杆）取消在途编译，只让最后一次生效，
 *   与 [select] 同一节流语义（不排队编译，面板操作零 ANR）。
 * - **diff 导入即替换**（R4.3）：[importDiff] 以文件内容全量替换当前 overlays
 *   （非合并），复用 [recompileWithOverlay] 的防改坏回滚。
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

    /**
     * 当前运行时覆写（路径 → 值；空 map = 无覆写）。
     * 工坊面板读取本表做「当前生效值 vs 主题包原值」diff 高亮；
     * 编译失败时本表回滚到上一版（与快照保持一致的防改坏语义）。
     */
    private val _overlays = MutableStateFlow<Map<String, TokenValue>>(emptyMap())
    val overlays: StateFlow<Map<String, TokenValue>> = _overlays.asStateFlow()

    /**
     * 当前基础包的**无覆写态**快照（工坊「当前生效值 vs 主题包原值」diff 的原值源）。
     *
     * 只在无覆写编译成功时更新（initialize / select / importAndApply）；
     * 覆写编译（[recompileWithOverlay]）成功**不**更新——保持该基础包的原初面貌，
     * 与 [overlays] 配对即得完整 diff。null = 尚无可用基础包。
     */
    private val _baseSnapshot = MutableStateFlow<ThemeSnapshot?>(null)
    val baseSnapshot: StateFlow<ThemeSnapshot?> = _baseSnapshot.asStateFlow()

    /** 内存快照缓存：(packId, modifiers, overlay) → 编译产物（连同来源 json）。切换命中即对象替换（§8.2 零 I/O） */
    private val cache = HashMap<CacheKey, CacheEntry>()

    /** 当前后台编译任务；新请求到达即取消旧任务（节流：只让最后一次生效） */
    private var pendingJob: Job? = null

    /** 当前**实际生效**的基础包 JSON（覆写编译的输入；fallback 态为 null） */
    private var currentJson: String? = null

    /** 解析缓存：json → ThemePack（覆写编译免重复解析；[invalidate] 时清） */
    private var parsedJson: String? = null
    private var parsedPack: ThemePack? = null

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
     * 实测（2026-10-21，JVM 桌面，197 条令牌）编译 p50 ≈ 2.4ms，
     * 因此「同步编译」足以在首帧前完成——无需引入磁盘快照缓存与序列化层
     * （收益不足以抵消复杂度与出错面，见 CHANGELOG 偏离记录）。
     *
     * 失败路径：顺序降级 缓存 → 内置包 → fallback，任何一步失败都不阻塞启动。
     */
    fun initialize() {
        _overlays.value = emptyMap() // 冷启动无覆写（覆写是会话态，导出才落盘）
        val packId = store.currentPackId() ?: BUILT_IN
        val modifiers = store.currentModifiers()

        cache[CacheKey(packId, modifiers, emptyMap())]?.let { entry ->
            _snapshot.value = entry.snapshot
            _baseSnapshot.value = entry.snapshot
            currentJson = entry.json // 缓存命中零 I/O：来源 json 随条目带回（覆写编译的输入）
            return
        }

        val json = packs.load(packId) ?: builtInJson
        if (json == null) {
            // 无包可选（未安装且无内置）：保持 fallback 快照
            _snapshot.value = FallbackTheme.snapshot
            _baseSnapshot.value = FallbackTheme.snapshot
            _status.value = ThemeStatus.Ready(
                packId = FALLBACK_ID, version = "fallback",
                modifiers = emptyList(), warnings = 0, degraded = true,
            )
            return
        }

        runCatching { compileInto(json, packId, modifiers, emptyMap()) }
            .onSuccess { result ->
                currentJson = json
                _snapshot.value = result.snapshot
                _baseSnapshot.value = result.snapshot
                _status.value = ThemeStatus.Ready(
                    packId, result.snapshot.meta.packVersion, modifiers,
                    warnings = result.warnings.size, degraded = false,
                )
            }
            .onFailure { error ->
                // 降级链：目标包失败 → 内置包 → fallback。前两者成功即视为「降级可用」
                val fellBack = builtInJson?.let { builtIn ->
                    runCatching {
                        val result = compileInto(builtIn, BUILT_IN, emptyList(), emptyMap())
                        currentJson = builtIn
                        _snapshot.value = result.snapshot
                        _baseSnapshot.value = result.snapshot
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
     *
     * 覆写语义：选包 = 用该包**原初面貌**——成功时清空覆写（工坊会话结束）。
     */
    fun select(packId: String, modifiers: List<String>) {
        pendingJob?.cancel()
        val key = CacheKey(packId, modifiers, emptyMap())

        cache[key]?.let { entry ->
            // 命中缓存：直接切换（零 I/O，对象替换）
            currentJson = entry.json
            store.saveSelection(packId, modifiers)
            _overlays.value = emptyMap()
            _snapshot.value = entry.snapshot
            _baseSnapshot.value = entry.snapshot
            _status.value = ThemeStatus.Ready(
                packId, entry.snapshot.meta.packVersion, modifiers,
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
                withContext(computeDispatcher) { compileInto(json, packId, modifiers, emptyMap()) }
            }
            if (outcome.isFailure && outcome.exceptionOrNull() is CancellationException) return@launch
            outcome.onSuccess { result ->
                currentJson = json
                store.saveSelection(packId, modifiers)
                _overlays.value = emptyMap()
                _snapshot.value = result.snapshot
                _baseSnapshot.value = result.snapshot
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
    ): CompileResult = withContext(computeDispatcher) {
        val result = compileInto(json, packId, modifiers, emptyMap())
        packs.save(packId, json)
        store.saveSelection(packId, modifiers)
        currentJson = json
        _overlays.value = emptyMap()
        _snapshot.value = result.snapshot
        _baseSnapshot.value = result.snapshot
        _status.value = ThemeStatus.Ready(
            packId, result.snapshot.meta.packVersion, modifiers,
            warnings = result.warnings.size, degraded = false,
        )
        result
    }

    // ---------- R4 运行时覆写 ----------

    /**
     * 应用覆写（合并语义：同名路径后者覆盖前者；与既有覆写叠加）。
     *
     * 走 [OverlayApplier] → 同一编译流水线（后台线程 + last-wins 节流）：
     * 编译成功才发射新快照；失败回滚到上一版覆写态并报告 [ThemeStatus.Failed]
     * （防改坏：面板爆炸不会把主题留在半坏状态）。
     *
     * 空 map 为 no-op（无意义请求不起编译）。
     */
    fun applyOverrides(entries: Map<String, TokenValue>) {
        if (entries.isEmpty()) return
        val previous = _overlays.value
        val merged = previous + entries
        _overlays.value = merged
        recompileWithOverlay(target = merged, rollbackTo = previous)
    }

    /**
     * 重置覆写：[paths] 为空 = 全部还原；否则仅移除指定路径（子系统级重置）。
     * 无可重置项为 no-op。
     */
    fun resetOverrides(paths: Collection<String> = emptyList()) {
        val previous = _overlays.value
        if (previous.isEmpty()) return
        val remaining = if (paths.isEmpty()) emptyMap() else previous - paths.toSet()
        if (remaining == previous) return
        _overlays.value = remaining
        recompileWithOverlay(target = remaining, rollbackTo = previous)
    }

    /** 覆写编译：取消在途 → 后台编译 → 成功发射 / 失败回滚覆写态 */
    private fun recompileWithOverlay(target: Map<String, TokenValue>, rollbackTo: Map<String, TokenValue>) {
        pendingJob?.cancel()
        val packId = currentPackId
        val modifiers = _snapshot.value.meta.activeModifiers
        val json = currentJson
        if (json == null) {
            // fallback 态（无基础包）：覆写无处施加——回滚覆写态并报告（overlays 表与快照保持一致）
            _overlays.value = rollbackTo
            _status.value = ThemeStatus.Failed(
                packId, modifiers, CompileErrorCode.PACK_NOT_FOUND,
                "当前无主题包可覆写（fallback 态）",
            )
            return
        }

        _status.value = ThemeStatus.Loading(packId, modifiers)
        pendingJob = scope.launch {
            val outcome = runCatching {
                withContext(computeDispatcher) { compileInto(json, packId, modifiers, target) }
            }
            // 被更新的覆写请求取消 → 不回滚（新请求已持更新态），也不抢状态
            if (outcome.isFailure && outcome.exceptionOrNull() is CancellationException) return@launch
            outcome.onSuccess { result ->
                _snapshot.value = result.snapshot
                _status.value = ThemeStatus.Ready(
                    packId, result.snapshot.meta.packVersion, modifiers,
                    warnings = result.warnings.size, degraded = false,
                )
            }.onFailure { error ->
                _overlays.value = rollbackTo // 防改坏：回滚到上一版覆写态（与快照一致）
                _status.value = ThemeStatus.Failed(
                    packId, modifiers,
                    (error as? ThemeCompileException)?.code ?: CompileErrorCode.ENGINE_INTERNAL,
                    error.message ?: "覆写编译失败",
                )
            }
        }
    }

    // ---------- R4.3 diff 主题包 ----------

    /**
     * 导出：当前覆写表 + 基础包元信息 → diff 包文本（DTCG 2025.10 对齐 + minime 命名空间）。
     *
     * 纯函数编排（[DiffSerializer] 保纯、可 JVM 单测）：指纹对**当前基础包**计算
     * （语义指纹，同包不同序列化结果一致）；`exportedAt` 可注入保测试确定性。
     *
     * 边界：覆写为空仍导出（count=0，UI 层可提示「无可导出内容」）；
     * fallback 态（无基础包）抛 [IllegalStateException]——UI 侧导出钮在
     * overlays 为空时禁用，该态不可达（防御性断言）。
     */
    fun exportDiff(exportedAt: String = nowIso()): String {
        val json = currentJson ?: error("当前无主题包可导出 diff（fallback 态）")
        val base = DiffSerializer.DiffBaseMeta(
            packId = currentPackId,
            packVersion = _snapshot.value.meta.packVersion,
            fingerprint = DiffSerializer.computeFingerprint(packFor(json)),
        )
        return DiffSerializer.serialize(_overlays.value, base, exportedAt)
    }

    /**
     * 预览：纯解析、**零改态**（确认对话框只消费本结果，不碰 overlays/快照/状态）。
     *
     * 与 [importDiff] 严格分离：本方法不编译、不改态，坏包走 [DiffPreviewResult.Failed]；
     * 「应用必失败」的两类路径（词汇表外 / 类型不符）在 [DiffPreviewResult.Ok] 里标注，
     * 对话框据此禁用「应用」钮并给出可诊断信息（优于让用户点了才失败）。
     */
    fun previewDiff(json: String): DiffPreviewResult {
        val parsed = try {
            DiffSerializer.deserialize(json)
        } catch (e: DiffSerializer.DiffFormatException) {
            return DiffPreviewResult.Failed(e.code, e.message ?: "diff 包解析失败")
        }

        val vocabularyMisses = parsed.overlays.keys.filterNot { TokenVocabulary.isKnown(it) }
        val typeMisses = parsed.overlays.entries
            .filter { (path, value) ->
                val declared = TokenVocabulary.typeOf(path)
                declared != null && !TokenTypeCheck.matches(declared, value)
            }
            .map { it.key }

        val fileFingerprint = parsed.meta.baseFingerprint
        val fingerprint = if (fileFingerprint == null) {
            FingerprintState.UNKNOWN // 形态 B（纯 DTCG）或未声明指纹：无从比对
        } else {
            when (val current = currentJson?.let { DiffSerializer.computeFingerprint(packFor(it)) }) {
                null -> FingerprintState.UNKNOWN // fallback 态：无当前包可比
                fileFingerprint -> FingerprintState.MATCH
                else -> FingerprintState.MISMATCH
            }
        }

        return DiffPreviewResult.Ok(
            overlays = parsed.overlays,
            total = parsed.overlays.size,
            fingerprint = fingerprint,
            basePackId = parsed.meta.basePackId,
            basePackVersion = parsed.meta.basePackVersion,
            exportedAt = parsed.meta.exportedAt,
            sample = parsed.overlays.entries.take(SAMPLE_SIZE)
                .map { (path, value) -> "$path = ${summarizeTokenValue(value)}" },
            vocabularyMisses = vocabularyMisses,
            typeMisses = typeMisses,
        )
    }

    /**
     * 应用 diff 包：deserialize + **全量替换**当前 overlays（导入即替换，设计 §7；
     * 合并语义留真需求时再加，YAGNI）。
     *
     * 替换走 R4.1 同一覆写编译管线（[recompileWithOverlay]：后台 + last-wins +
     * 失败回滚）——[ImportResult.Applied] 仅表示「已进入应用流程」，编译失败经
     * [status] 的 [ThemeStatus.Failed] 暴露且 overlays 回滚到上一版（防改坏）。
     * 坏包（格式层）直接 [ImportResult.Rejected]，零改态。
     */
    fun importDiff(json: String): ImportResult {
        val parsed = try {
            DiffSerializer.deserialize(json)
        } catch (e: DiffSerializer.DiffFormatException) {
            return ImportResult.Rejected(e.code, e.message ?: "diff 包解析失败")
        }
        val previous = _overlays.value
        _overlays.value = parsed.overlays
        recompileWithOverlay(target = parsed.overlays, rollbackTo = previous)
        return ImportResult.Applied(parsed.overlays.size)
    }

    // ---------- 内部 ----------

    /** 编译（覆写挂载 + 子系统实例复用 §8.2 + 写缓存） */
    private fun compileInto(
        json: String,
        packId: String,
        modifiers: List<String>,
        overlay: Map<String, TokenValue>,
    ): CompileResult {
        val result = compiler.compile(OverlayApplier.apply(packFor(json), overlay), modifiers)
        val reused = SnapshotReuser.reuse(_snapshot.value.takeIf { _status.value !is ThemeStatus.Idle }, result.snapshot)
        val final = if (reused === result.snapshot) result else CompileResult(reused, result.warnings)
        cache[CacheKey(packId, modifiers, overlay)] = CacheEntry(json, final.snapshot)
        return final
    }

    /** 解析缓存（覆写路径免重复解析同一份 JSON）；与 [currentJson] 同步失效 */
    private fun packFor(json: String): ThemePack {
        val cached = parsedPack
        if (cached != null && parsedJson == json) return cached
        val pack = DtcgParser().parse(json)
        parsedJson = json
        parsedPack = pack
        return pack
    }

    /** 丢弃缓存（主题包更新后调用）；packId 为 null 时全清 */
    fun invalidate(packId: String? = null) {
        if (packId == null) {
            cache.clear()
            parsedJson = null
            parsedPack = null
            return
        }
        cache.keys.filter { it.packId == packId }.forEach { cache.remove(it) }
        if (currentPackId == packId) {
            parsedJson = null
            parsedPack = null
        }
    }

    private data class CacheKey(
        val packId: String,
        val modifiers: List<String>,
        val overlay: Map<String, TokenValue>,
    )

    /** 缓存条目：编译产物连同来源 json（命中切换零 I/O，且覆写编译可直接取基础包） */
    private data class CacheEntry(val json: String, val snapshot: ThemeSnapshot)

    companion object {
        /** 内置默认包标识 */
        const val BUILT_IN = "built-in"
        /** 兜底快照标识（调试面板显示用，非真实包） */
        const val FALLBACK_ID = "fallback"

        /** 预览样本条数（对话框样本列表上限，设计 §7） */
        private const val SAMPLE_SIZE = 8

        /** 导出时间默认值（ISO 8601 含偏移量；测试注入固定值保确定性） */
        private fun nowIso(): String = java.time.OffsetDateTime.now().toString()
    }
}
