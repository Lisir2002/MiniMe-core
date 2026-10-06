package com.mini.me_core.feature.packager.domain.model

/**
 * 项目类型
 */
enum class ProjectType(val displayName: String) {
    HTML("纯HTML"),
    VUE("Vue"),
    REACT("React"),
    TEMPLATE("模板");

    companion object {
        fun fromName(name: String): ProjectType =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: HTML
    }
}

/**
 * 构建状态
 */
enum class BuildStatus {
    SUCCESS,
    FAILED,
    BUILDING;

    val displayName: String
        get() = when (this) {
            SUCCESS -> "成功"
            FAILED -> "失败"
            BUILDING -> "构建中"
        }
}

/**
 * 项目配置
 *
 * @param id 项目唯一ID（UUID）
 * @param name 应用名称
 * @param packageName 包名（唯一，如 com.minime.app.xxx）
 * @param versionName 版本号，如 "1.0.0"
 * @param versionCode 版本号整数，如 1000000
 * @param type 项目类型
 * @param iconPath 自定义图标路径（可选，null 表示使用模版默认图标）
 * @param bridgeEnabled Bridge 能力总开关
 * @param permissions 声明的权限列表
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 * @param lastBuildAt 最后构建时间戳（可选）
 * @param lastBuildStatus 最后构建状态（可选）
 */
data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val type: ProjectType,
    val iconPath: String? = null,
    val bridgeEnabled: Boolean = true,
    val permissions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastBuildAt: Long? = null,
    val lastBuildStatus: BuildStatus? = null
) {
    companion object {
        /** 默认版本名 */
        const val DEFAULT_VERSION_NAME = "1.0.0"

        /** 默认版本号（1.0.0 → 1000000） */
        const val DEFAULT_VERSION_CODE = 1_000_000

        /** 包名前缀 */
        const val PACKAGE_PREFIX = "com.minime.app."

        /**
         * 从版本名解析版本号
         * 格式：x.y.z → x*1_000_000 + y*1_000 + z
         */
        fun parseVersionCode(versionName: String): Int {
            val parts = versionName.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 1_000_000 + minor * 1_000 + patch
        }

        /**
         * 校验包名是否符合 Java 包名规范
         * - 必须以字母开头
         * - 只能包含字母、数字、下划线
         * - 至少两段（用 . 分隔）
         * - 每段不能是 Java 关键字
         */
        fun isValidPackageName(packageName: String): Boolean {
            if (packageName.isBlank()) return false
            val segments = packageName.split(".")
            if (segments.size < 2) return false
            val javaKeywords = setOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double",
                "else", "enum", "extends", "final", "finally", "float", "for", "goto",
                "if", "implements", "import", "instanceof", "int", "interface", "long",
                "native", "new", "package", "private", "protected", "public", "return",
                "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while"
            )
            return segments.all { segment ->
                segment.isNotEmpty() &&
                    segment[0].isLetter() &&
                    segment.all { it.isLetterOrDigit() || it == '_' } &&
                    segment !in javaKeywords
            }
        }
    }
}
