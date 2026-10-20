package org.minime.core.ui.runtime

/**
 * 主题选择持久化（§8.2「首帧不闪」）。
 *
 * **硬要求：同步可读**——冷启动必须在 `setContent` 之前拿到上一次的选择，
 * 否则首帧会先呈现 fallback 再跳变（观感撕裂）。因此本接口刻意做成同步阻塞式，
 * 不用 DataStore（异步 Flow，首帧拿不到值）。
 *
 * 实现侧：Android 用 `SharedPreferences`（同步 `getString`），测试用 [InMemoryThemeStore]。
 */
interface ThemeStore {

    /** 上次选中的主题包标识（内置默认包为 [ThemeManager.BUILT_IN]）；无选择返回 null */
    fun currentPackId(): String?

    /** 上次激活的修饰符（如 ["dark"]）；互斥单选由调用方按系统上下文保证 */
    fun currentModifiers(): List<String>

    /** 保存选择（同步落盘） */
    fun saveSelection(packId: String, modifiers: List<String>)

    /** 清除选择（回退到内置默认包） */
    fun clear()
}

/** 内存实现：单测与预览用，进程内保持一致 */
class InMemoryThemeStore(
    private var packId: String? = null,
    private var modifiers: List<String> = emptyList(),
) : ThemeStore {
    override fun currentPackId(): String? = packId
    override fun currentModifiers(): List<String> = modifiers
    override fun saveSelection(packId: String, modifiers: List<String>) {
        this.packId = packId
        this.modifiers = modifiers
    }
    override fun clear() {
        packId = null
        modifiers = emptyList()
    }
}
