package org.minime.core.ui.runtime

import android.content.Context
import java.io.File

/**
 * Android 侧持久化实现（§8.2「首帧不闪」）。
 *
 * **为什么用 SharedPreferences 而不是 DataStore**：DataStore 是异步 Flow，
 * 冷启动时 `setContent` 之前拿不到值 → 首帧会先呈现 fallback 再跳变（观感撕裂）。
 * SharedPreferences 的读是**同步**的，满足"首帧即目标主题"的硬要求。
 *
 * 写入用 `commit()`（同步落盘）而非 `apply()`：选择结果极小（两个短字符串），
 * 同步落盘的耗时可忽略，却换来了「进程被杀后选择不丢」的确定性。
 */
class SharedPreferencesThemeStore(
    context: Context,
    prefsName: String = "minime_theme",
) : ThemeStore {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun currentPackId(): String? = prefs.getString(KEY_PACK_ID, null)

    override fun currentModifiers(): List<String> =
        (prefs.getString(KEY_MODIFIERS, null) ?: "")
            .split(MODIFIER_SEPARATOR)
            .filter { it.isNotBlank() }

    override fun saveSelection(packId: String, modifiers: List<String>) {
        prefs.edit()
            .putString(KEY_PACK_ID, packId)
            .putString(KEY_MODIFIERS, modifiers.joinToString(MODIFIER_SEPARATOR))
            .commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_PACK_ID = "pack_id"
        const val KEY_MODIFIERS = "modifiers"
        const val MODIFIER_SEPARATOR = ","
    }
}

/**
 * 主题包内容存储：`filesDir/theme-packs/<packId>.json`。
 *
 * 同步读写（小文件），冷启动直接读；[packId] 经 [isSafeId] 校验，
 * 防止路径穿越（`../../`）写入或读取应用私有目录之外的位置。
 */
class FileThemePackRepository(
    context: Context,
    dirName: String = "theme-packs",
) : ThemePackRepository {

    private val dir = File(context.filesDir, dirName)

    override fun load(packId: String): String? {
        if (!isSafeId(packId)) return null
        val file = File(dir, "$packId.json")
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    override fun save(packId: String, json: String) {
        require(isSafeId(packId)) { "非法主题包标识: $packId（只允许字母、数字、点、下划线、连字符）" }
        if (!dir.exists()) dir.mkdirs()
        File(dir, "$packId.json").writeText(json)
    }

    override fun remove(packId: String) {
        if (!isSafeId(packId)) return
        File(dir, "$packId.json").delete()
    }

    override fun installed(): Set<String> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".json") }
            .map { it.name.removeSuffix(".json") }
            .toSet()

    /** 包标识白名单校验：拒绝任何可能穿越路径或引入分隔符的字符 */
    internal fun isSafeId(packId: String): Boolean =
        packId.isNotBlank() && packId.length <= 64 && packId.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
}
