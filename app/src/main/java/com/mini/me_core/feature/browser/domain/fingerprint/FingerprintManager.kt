package com.mini.me_core.feature.browser.domain.fingerprint

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 指纹配置管理器（单例）。
 *
 * 职责：
 * - 指纹配置的 CRUD（创建/读取/更新/删除）
 * - 当前激活配置的管理和切换
 * - 配置状态（active/cooling/retired）管理
 * - 使用统计（使用次数、最后使用时间）
 * - 配置变更的状态流通知（UI 可观察）
 *
 * 线程安全：使用 ConcurrentHashMap 和 StateFlow，支持多线程并发访问。
 *
 * 持久化：当前版本为内存存储，后续版本将接入数据库持久化。
 * 预置配置在初始化时自动生成，确保首次使用即有可用配置。
 */
@Singleton
class FingerprintManager @Inject constructor() {

    // ===== 配置存储 =====

    /** 所有配置（id -> profile） */
    private val profiles = ConcurrentHashMap<String, FingerprintProfile>()

    /** 当前激活的配置 ID */
    private val _currentProfileId = MutableStateFlow<String?>(null)
    val currentProfileId: StateFlow<String?> = _currentProfileId.asStateFlow()

    /** 当前激活的配置（派生流） */
    val currentProfile: StateFlow<FingerprintProfile?> = MutableStateFlow<FingerprintProfile?>(null).also { flow ->
        // 注意：这里简化处理，实际应通过 combine 派生
        // 初始化时设置
    }

    // ===== 初始化 =====

    init {
        // 预置配置：真实指纹（无伪装）
        val real = FingerprintProfile.realFingerprint()
        profiles[real.id] = real
        _currentProfileId.value = real.id

        // 预置配置：3 个随机生成的伪装指纹
        val presets = listOf(
            FingerprintGenerator.generate(
                template = FingerprintGenerator.Template.REGION_US,
                name = "美国 Chrome 主流",
            ),
            FingerprintGenerator.generate(
                template = FingerprintGenerator.Template.HIGH_END,
                name = "高端 Windows 游戏本",
            ),
            FingerprintGenerator.generate(
                template = FingerprintGenerator.Template.MOBILE,
                name = "移动端 Android",
            ),
        )
        presets.forEach { profiles[it.id] = it }
    }

    // ===== 查询操作 =====

    /** 获取所有配置 */
    fun getAll(): List<FingerprintProfile> = profiles.values.toList()

    /** 获取可用配置（active 且不在冷却期） */
    fun getAvailable(): List<FingerprintProfile> =
        profiles.values.filter { it.isAvailable }.sortedByDescending { it.score }

    /** 按 ID 获取配置 */
    fun getById(id: String): FingerprintProfile? = profiles[id]

    /** 获取当前激活的配置 */
    fun getCurrent(): FingerprintProfile? =
        _currentProfileId.value?.let { profiles[it] }

    /** 按状态筛选 */
    fun getByStatus(status: String): List<FingerprintProfile> =
        profiles.values.filter { it.status == status }.toList()

    /** 按地区筛选 */
    fun getByRegion(region: String): List<FingerprintProfile> =
        profiles.values.filter { it.region.equals(region, ignoreCase = true) }.toList()

    /** 按浏览器筛选 */
    fun getByBrowser(browser: String): List<FingerprintProfile> =
        profiles.values.filter { it.browser.equals(browser, ignoreCase = true) }.toList()

    // ===== 创建操作 =====

    /**
     * 添加配置。
     *
     * @param profile 要添加的配置
     * @return 添加后的配置（如 ID 已存在则覆盖）
     */
    fun add(profile: FingerprintProfile): FingerprintProfile {
        profiles[profile.id] = profile
        return profile
    }

    /**
     * 生成并添加一个随机配置。
     *
     * @param template 生成模板
     * @param name 配置名称
     * @return 生成并添加后的配置
     */
    fun createRandom(
        template: FingerprintGenerator.Template? = null,
        name: String? = null,
    ): FingerprintProfile {
        val profile = FingerprintGenerator.generate(template = template, name = name)
        profiles[profile.id] = profile
        return profile
    }

    // ===== 更新操作 =====

    /**
     * 更新配置。
     *
     * @param id 配置 ID
     * @param transform 更新函数（接收旧配置，返回新配置）
     * @return 更新后的配置，如 ID 不存在返回 null
     */
    fun update(id: String, transform: (FingerprintProfile) -> FingerprintProfile): FingerprintProfile? {
        val old = profiles[id] ?: return null
        val new = transform(old)
        profiles[id] = new
        return new
    }

    /**
     * 切换当前激活的配置。
     *
     * @param id 要切换到的配置 ID
     * @return 切换结果（成功返回新配置，失败返回 null）
     */
    fun switchTo(id: String): FingerprintProfile? {
        val target = profiles[id] ?: return null
        if (!target.isAvailable) return null

        // 更新旧配置的使用统计
        _currentProfileId.value?.let { oldId ->
            update(oldId) { it.copy(totalUses = it.totalUses + 1, lastUsedAt = System.currentTimeMillis()) }
        }

        // 切换到新配置
        _currentProfileId.value = id
        update(id) { it.copy(lastUsedAt = System.currentTimeMillis()) }

        return target
    }

    /**
     * 将配置标记为冷却中。
     *
     * @param id 配置 ID
     * @param durationSeconds 冷却时长（秒）
     * @return 是否成功
     */
    fun cooldown(id: String, durationSeconds: Int = 300): Boolean {
        val profile = profiles[id] ?: return false
        val until = System.currentTimeMillis() + durationSeconds * 1000L
        profiles[id] = profile.copy(coolingUntil = until)
        return true
    }

    /**
     * 提前解除冷却。
     *
     * @param id 配置 ID
     * @return 是否成功
     */
    fun releaseCooldown(id: String): Boolean {
        val profile = profiles[id] ?: return false
        profiles[id] = profile.copy(coolingUntil = 0)
        return true
    }

    /**
     * 更新配置质量评分。
     *
     * @param id 配置 ID
     * @param score 新评分（0-100）
     */
    fun updateScore(id: String, score: Float) {
        update(id) { it.copy(score = score.coerceIn(0f, 100f)) }
    }

    // ===== 删除操作 =====

    /**
     * 删除配置。
     *
     * @param id 要删除的配置 ID
     * @param force 是否强制删除（当前激活的配置需要 force=true）
     * @return 是否成功删除
     */
    fun delete(id: String, force: Boolean = false): Boolean {
        val profile = profiles[id] ?: return false

        // 当前激活的配置不可删除（除非 force）
        if (_currentProfileId.value == id && !force) {
            return false
        }

        // 真实指纹配置不可删除
        if (id == FingerprintProfile.DEFAULT_ID) {
            return false
        }

        profiles.remove(id)
        return true
    }

    // ===== 统计操作 =====

    /** 配置总数 */
    val count: Int
        get() = profiles.size

    /** 可用配置数 */
    val availableCount: Int
        get() = profiles.values.count { it.isAvailable }

    /** 冷却中配置数 */
    val coolingCount: Int
        get() = profiles.values.count { it.isCooling }

    /**
     * 获取使用统计摘要。
     */
    fun getStats(): Map<String, Any> = mapOf(
        "total" to count,
        "available" to availableCount,
        "cooling" to coolingCount,
        "retired" to getByStatus("retired").size,
        "currentId" to (_currentProfileId.value ?: "none"),
    )

    // ===== 校验操作 =====

    /**
     * 校验指定配置的一致性。
     *
     * @param id 配置 ID（null 则校验当前配置）
     * @return 校验结果
     */
    fun validate(id: String? = null): FingerprintValidator.ValidationResult? {
        val profile = if (id == null) getCurrent() else getById(id)
        return profile?.let { FingerprintValidator.validate(it) }
    }

    /**
     * 校验所有配置，返回有问题的配置列表。
     */
    fun validateAll(): List<Pair<FingerprintProfile, FingerprintValidator.ValidationResult>> =
        profiles.values.map { it to FingerprintValidator.validate(it) }
            .filter { !it.second.valid }

    // ===== 重置操作 =====

    /**
     * 重置为初始状态（恢复预置配置）。
     * 注意：这会删除所有自定义配置。
     */
    fun reset() {
        profiles.clear()
        val real = FingerprintProfile.realFingerprint()
        profiles[real.id] = real
        _currentProfileId.value = real.id
    }
}
