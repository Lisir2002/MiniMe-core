package com.mini.me_core.feature.settings.data.repository

import android.util.Log
import com.mini.me_core.BuildConfig
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.datalayer.store.KvEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F5.2 设置导出/导入标准化管理器。
 *
 * 统一 JSON 格式：版本号 + 导出时间 + appVersion + 分类键值对。
 * - 按分类（key 前缀）选择性导出
 * - 可选 AES-GCM 密码加密（.minimebak）
 * - 导入前解析校验 + 逐项冲突对比
 *
 * 设计参考：KVStore.getAll(namespace) 读取所有设置项，按 key 前缀归组到分类。
 */
@Singleton
class SettingsExportManager @Inject constructor(
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "SettingsExport"
        private const val SETTINGS_NS = "settings"
        private const val FORMAT_VERSION = 1
        private const val ENCRYPTED_MAGIC = "MINIME_BAK_V1"
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12

        /** 导出分类定义：id + 匹配的 key 前缀。 */
        val CATEGORIES: List<ExportCategory> = listOf(
            ExportCategory(
                id = "appearance",
                keyPrefixes = listOf("theme_mode", "dark_theme_enabled", "splash_style"),
            ),
            ExportCategory(
                id = "ai_provider",
                keyPrefixes = listOf(
                    "default_provider_id", "default_model",
                    "compaction_provider_id", "compaction_model",
                    "vision_provider_id", "vision_model",
                    "default_policy", "auto_downgrade", "viewimage_unknown_guard",
                ),
            ),
            ExportCategory(
                id = "terminal",
                keyPrefixes = listOf(
                    "active_profile_id", "custom_profiles_json", "storage_share_enabled",
                    "execution_mode", "ssh_active_", "remote_host", "remote_port",
                    "remote_username", "remote_password", "remote_workspace_path",
                ),
            ),
            ExportCategory(
                id = "norm_flow",
                keyPrefixes = listOf(
                    "norm_flow", "step_inject", "tool_guard", "file_observation",
                    "reasoning_budget", "usage_card", "sop_summary", "playbook_auto",
                    "idle_converge", "guard_dangerous", "guard_large", "guard_path",
                ),
            ),
            ExportCategory(
                id = "system",
                keyPrefixes = listOf(
                    "keepalive_enabled", "filter_", "log_", "secure_screen",
                ),
            ),
        )

        fun categoryById(id: String): ExportCategory? = CATEGORIES.firstOrNull { it.id == id }
    }

    /** 导出分类。 */
    data class ExportCategory(
        val id: String,
        val keyPrefixes: List<String>,
    )

    @Serializable
    private data class ExportFileDto(
        val version: Int = FORMAT_VERSION,
        val exportTime: String,
        val appVersion: String,
        val encrypted: Boolean = false,
        val categories: Map<String, Map<String, ExportValueDto>> = emptyMap(),
    )

    @Serializable
    private data class ExportValueDto(
        val type: String, // string / int / bool / json
        val value: JsonPrimitive,
    )

    /** 导入解析结果。 */
    data class ParsedSettings(
        val fileVersion: Int,
        val exportTime: String,
        val appVersion: String,
        val encrypted: Boolean,
        /** category -> key -> value */
        val categories: Map<String, Map<String, ExportedValue>>,
    )

    data class ExportedValue(
        val type: String,
        val stringValue: String?,
        val intValue: Long?,
        val boolValue: Boolean?,
    ) {
        fun display(): String = when (type) {
            "bool" -> if (boolValue == true) "true" else "false"
            "int" -> intValue?.toString() ?: ""
            "string" -> stringValue ?: ""
            "json" -> stringValue ?: ""
            else -> stringValue ?: ""
        }
    }

    /** 单条冲突项：当前值 vs 导入值。 */
    data class ConflictItem(
        val category: String,
        val key: String,
        val current: ExportedValue?,
        val incoming: ExportedValue,
    )

    /** 导入结果统计。 */
    data class ImportResult(
        val applied: Int,
        val skipped: Int,
        val failed: Int,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ── 导出 ─────────────────────────────────────────────────────

    /**
     * 导出选中分类的设置为 JSON 字符串。
     * @param selectedCategoryIds 选中的分类 id 集合；空集合 = 全选
     * @param password 非空则 AES-GCM 加密，输出 Base64 密文
     */
    suspend fun export(
        selectedCategoryIds: Set<String>,
        password: String?,
    ): String = withContext(Dispatchers.IO) {
        val allEntries = kv.getAll(SETTINGS_NS)
        val effectiveSelection = if (selectedCategoryIds.isEmpty()) {
            CATEGORIES.map { it.id }.toSet()
        } else {
            selectedCategoryIds
        }

        val categorized = mutableMapOf<String, MutableMap<String, ExportValueDto>>()
        for (entry in allEntries) {
            val cat = categoryFor(entry.key) ?: continue
            if (cat.id !in effectiveSelection) continue
            val dto = entry.toExportValueDto() ?: continue
            categorized.getOrPut(cat.id) { mutableMapOf() }[entry.key] = dto
        }

        val file = ExportFileDto(
            version = FORMAT_VERSION,
            exportTime = Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            encrypted = password != null,
            categories = categorized,
        )
        val plainJson = json.encodeToString(ExportFileDto.serializer(), file)

        if (!password.isNullOrEmpty()) {
            encryptToString(plainJson, password)
        } else {
            plainJson
        }
    }

    /** 根据 key 判断属于哪个分类（按前缀匹配）。 */
    private fun categoryFor(key: String): ExportCategory? {
        for (cat in CATEGORIES) {
            for (prefix in cat.keyPrefixes) {
                if (key == prefix || key.startsWith(prefix)) return cat
            }
        }
        return null
    }

    private fun KvEntry.toExportValueDto(): ExportValueDto? {
        return when (type) {
            "string" -> stringVal?.let { ExportValueDto("string", JsonPrimitive(it)) }
            "int" -> intVal?.let { ExportValueDto("int", JsonPrimitive(it)) }
            "bool" -> boolVal?.let { ExportValueDto("bool", JsonPrimitive(it != 0L)) }
            "json" -> jsonVal?.let { ExportValueDto("json", JsonPrimitive(it)) }
            else -> null
        }
    }

    // ── 导入解析 ──────────────────────────────────────────────────

    /**
     * 解析导入内容。自动检测是否加密（magic header）。
     * @throws IllegalArgumentException 格式错误或密码错误
     */
    suspend fun parseImport(raw: String, password: String?): ParsedSettings = withContext(Dispatchers.IO) {
        val trimmed = raw.trim()
        val plain = if (trimmed.startsWith(ENCRYPTED_MAGIC)) {
            if (password.isNullOrEmpty()) {
                throw IllegalArgumentException("encrypted_required")
            }
            decryptToString(trimmed, password)
        } else {
            trimmed
        }

        val dto = runCatching {
            json.decodeFromString(ExportFileDto.serializer(), plain)
        }.getOrElse {
            Log.w(TAG, "Parse failed: ${it.message}")
            throw IllegalArgumentException("invalid_format")
        }

        val categories = dto.categories.mapValues { (_, entries) ->
            entries.mapValues { (_, v) -> v.toExportedValue() }
        }
        ParsedSettings(
            fileVersion = dto.version,
            exportTime = dto.exportTime,
            appVersion = dto.appVersion,
            encrypted = dto.encrypted,
            categories = categories,
        )
    }

    private fun ExportValueDto.toExportedValue(): ExportedValue {
        return when (type) {
            "bool" -> ExportedValue("bool", null, null, value.booleanOrNull ?: false)
            "int" -> ExportedValue("int", null, value.longOrNull ?: value.contentOrNull?.toLongOrNull(), null)
            "json" -> ExportedValue("json", value.contentOrNull, null, null)
            else -> ExportedValue("string", value.contentOrNull, null, null)
        }
    }

    /**
     * 对比导入项与当前值，返回冲突列表（当前值 != 导入值的项）。
     */
    fun computeConflicts(parsed: ParsedSettings): List<ConflictItem> {
        val conflicts = mutableListOf<ConflictItem>()
        for ((category, entries) in parsed.categories) {
            for ((key, incoming) in entries) {
                val current = currentValue(key)
                if (current != null && current != incoming) {
                    conflicts.add(ConflictItem(category, key, current, incoming))
                }
            }
        }
        return conflicts
    }

    private fun currentValue(key: String): ExportedValue? {
        val entry = kv.get(SETTINGS_NS, key) ?: return null
        return when (entry.type) {
            "bool" -> ExportedValue("bool", null, null, entry.boolVal?.let { it != 0L })
            "int" -> ExportedValue("int", null, entry.intVal, null)
            "json" -> ExportedValue("json", entry.jsonVal, null, null)
            else -> ExportedValue("string", entry.stringVal, null, null)
        }
    }

    /**
     * 应用导入。
     * @param keepKeys 需要保留当前值的 key 集合（其余用导入值覆盖）
     */
    suspend fun applyImport(
        parsed: ParsedSettings,
        keepKeys: Set<String>,
    ): ImportResult = withContext(Dispatchers.IO) {
        var applied = 0
        var skipped = 0
        var failed = 0
        for ((_, entries) in parsed.categories) {
            for ((key, value) in entries) {
                if (key in keepKeys) {
                    skipped++
                    continue
                }
                runCatching {
                    when (value.type) {
                        "bool" -> kv.putBool(SETTINGS_NS, key, value.boolValue ?: false)
                        "int" -> kv.putInt(SETTINGS_NS, key, value.intValue ?: 0L)
                        "json" -> kv.putJson(SETTINGS_NS, key, value.stringValue ?: "{}")
                        else -> kv.putString(SETTINGS_NS, key, value.stringValue ?: "")
                    }
                    applied++
                }.onFailure { failed++ }
            }
        }
        ImportResult(applied, skipped, failed)
    }

    // ── AES-GCM 加解密 ────────────────────────────────────────────

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** 加密：输出 "MINIME_BAK_V1" + base64(salt + iv + ciphertext)。 */
    internal fun encryptToString(plain: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(salt.size + iv.size + cipherBytes.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherBytes, 0, combined, salt.size + iv.size, cipherBytes.size)
        return "$ENCRYPTED_MAGIC${Base64.getEncoder().encodeToString(combined)}"
    }

    internal fun decryptToString(encoded: String, password: String): String {
        val body = encoded.removePrefix(ENCRYPTED_MAGIC)
        val combined = Base64.getDecoder().decode(body)
        val salt = combined.copyOfRange(0, SALT_BYTES)
        val iv = combined.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val cipherBytes = combined.copyOfRange(SALT_BYTES + IV_BYTES, combined.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(cipherBytes)
        return String(plain, Charsets.UTF_8)
    }
}
