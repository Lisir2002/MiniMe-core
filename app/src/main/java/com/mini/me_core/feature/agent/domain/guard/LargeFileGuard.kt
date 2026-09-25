package com.mini.me_core.feature.agent.domain.guard

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大文件护栏（P2）：writeFile/editFile 写入内容超过 500KB 时 Advisory 警告（不阻断）。
 *
 * 由 guard_large_file_enabled 独立开关控制（默认关）。
 */
@Singleton
class LargeFileGuard @Inject constructor(
    private val settings: NormFlowSettingsRepository
) : ToolGuard {

    override val id = "large-file"

    override suspend fun guard(ctx: ToolGuardContext): ToolGuardResult {
        if (ctx.toolName != "writeFile" && ctx.toolName != "editFile") return ToolGuardResult.Pass
        if (!settings.isGuardLargeFileActive()) return ToolGuardResult.Pass

        val sizeBytes = when (ctx.toolName) {
            "writeFile" -> {
                (ctx.args["content"] as? JsonPrimitive)?.contentOrNull?.toByteArray()?.size ?: 0
            }
            "editFile" -> {
                // editFile 的 edits 是数组，每项含 new_string
                val edits = ctx.args["edits"] as? JsonArray
                edits?.sumOf { edit ->
                    (edit as? kotlinx.serialization.json.JsonObject)?.get("new_string")
                        ?.jsonPrimitive?.contentOrNull?.toByteArray()?.size ?: 0
                } ?: 0
            }
            else -> 0
        }

        if (sizeBytes > MAX_SIZE_BYTES) {
            return ToolGuardResult.Advisory(
                code = "LARGE_FILE",
                message = "写入内容较大：${sizeBytes / 1024}KB（阈值 ${MAX_SIZE_BYTES / 1024}KB），请确认是否需要分片写入。"
            )
        }
        return ToolGuardResult.Pass
    }

    private companion object {
        const val MAX_SIZE_BYTES = 500 * 1024
    }
}
