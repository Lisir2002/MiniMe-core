package com.mini.me_core.feature.terminal.domain

/**
 * 容器内文件类型枚举（纯 Kotlin，无 Android 依赖，便于单测）。
 */
enum class ContainerFileType {
    FOLDER,
    TEXT,
    IMAGE,
    SCRIPT,
    ARCHIVE,
    CONFIG,
    EXECUTABLE,
    AUDIO,
    OTHER,
    ;

    companion object {
        private val TEXT_EXT = setOf("txt", "md", "log", "conf")
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
        private val SCRIPT_EXT = setOf("sh", "bash", "py", "js", "ts")
        private val ARCHIVE_EXT = setOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz")
        private val CONFIG_EXT = setOf("json", "xml", "yaml", "yml", "ini", "toml", "cfg")
        private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "flac")

        fun classify(entry: ContainerFileEntry): ContainerFileType {
            if (entry.isDir) return FOLDER
            val name = entry.name
            val ext = name.substringAfterLast('.', "").lowercase()
            // 无扩展名但有可执行权限（stat 权限字符串第 3/6/9 位含 x）→ EXECUTABLE
            if (ext.isEmpty() && entry.permissions.length >= 9) {
                val perms = entry.permissions
                val hasExec = perms[2] == 'x' || perms[5] == 'x' || perms[8] == 'x'
                if (hasExec) return EXECUTABLE
            }
            return when (ext) {
                in TEXT_EXT -> TEXT
                in IMAGE_EXT -> IMAGE
                in SCRIPT_EXT -> SCRIPT
                in ARCHIVE_EXT -> ARCHIVE
                in CONFIG_EXT -> CONFIG
                in AUDIO_EXT -> AUDIO
                else -> OTHER
            }
        }
    }
}
