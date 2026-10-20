package org.minime.core.ui.runtime

/**
 * 主题包内容存取（冷启动恢复用）。
 *
 * 与 [ThemeStore] 的分工：
 * - [ThemeStore] 只存**选择**（packId + 修饰符），必须同步可读（首帧不闪）；
 * - 本接口存**包内容**（JSON 文本），按 packId 存取，冷启动同步读小文件。
 *
 * Android 侧实现为 `filesDir/theme-packs/<packId>.json`；单测用 [InMemoryThemePackRepository]。
 */
interface ThemePackRepository {

    /** 读取包内容；不存在返回 null */
    fun load(packId: String): String?

    /** 写入包内容（覆盖） */
    fun save(packId: String, json: String)

    /** 删除包内容 */
    fun remove(packId: String)

    /** 已安装的包标识 */
    fun installed(): Set<String>
}

/** 内存实现：单测与预览用 */
class InMemoryThemePackRepository(
    private val map: MutableMap<String, String> = LinkedHashMap(),
) : ThemePackRepository {
    override fun load(packId: String): String? = map[packId]
    override fun save(packId: String, json: String) { map[packId] = json }
    override fun remove(packId: String) { map.remove(packId) }
    override fun installed(): Set<String> = map.keys.toSet()
}
