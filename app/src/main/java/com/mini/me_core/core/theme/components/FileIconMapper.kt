package com.mini.me_core.core.theme.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 文件类型 → 视觉（图标 + 背景色 + 前景色）的统一映射结果。
 *
 * 由 [fileIconVisual] 构造，颜色全部取自 [MaterialTheme.colorScheme]，无硬编码色值。
 * 代码浏览器与容器文件管理器共用此映射，保证两处视觉一致。
 */
data class FileIconVisual(
    val icon: ImageVector,
    val iconBg: Color,
    val iconFg: Color,
)

/** 代码文件扩展名（kt/java/ts/js/py 及常见源码）。 */
private val CODE_EXT = setOf(
    "kt", "java", "kts", "ts", "tsx", "js", "jsx", "py",
    "c", "cc", "cpp", "h", "hpp", "go", "rs", "swift", "sh", "bash",
)

/** 文档扩展名（md/txt/pdf/doc 等）。 */
private val DOC_EXT = setOf(
    "md", "markdown", "txt", "pdf", "doc", "docx", "log", "conf", "rtf",
)

/** 配置文件扩展名（json/yaml/xml/gradle/toml 等）。 */
private val CONFIG_EXT = setOf(
    "json", "yaml", "yml", "xml", "gradle", "toml", "ini", "cfg", "properties",
)

/** 图片扩展名。 */
private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "ico")

/** 压缩包扩展名。 */
private val ARCHIVE_EXT = setOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz")

/** 音频扩展名。 */
private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac")

/** 视频扩展名。 */
private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "flv")

/** 可执行文件扩展名（无扩展名但带执行权限的情况由调用方通过 [executable] 传入）。 */
private val EXEC_EXT = setOf("exe", "bin", "out", "app", "elf", "run")

/**
 * 根据文件名与是否为目录，给出统一的文件图标视觉。
 *
 * @param name 文件名（含扩展名）。
 * @param isDir 是否为目录。
 * @param executable 是否为可执行文件（容器文件靠权限位判定，无扩展名时由调用方传入）。
 */
@Composable
fun fileIconVisual(
    name: String,
    isDir: Boolean,
    executable: Boolean = false,
): FileIconVisual {
    val scheme = MaterialTheme.colorScheme
    if (isDir) {
        return FileIconVisual(
            icon = Icons.Rounded.Folder,
            iconBg = scheme.primaryContainer,
            iconFg = scheme.onPrimaryContainer,
        )
    }
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in CODE_EXT -> FileIconVisual(
            icon = Icons.Rounded.Code,
            iconBg = scheme.secondaryContainer,
            iconFg = scheme.onSecondaryContainer,
        )

        ext in DOC_EXT -> FileIconVisual(
            icon = Icons.Rounded.Article,
            iconBg = scheme.tertiaryContainer,
            iconFg = scheme.onTertiaryContainer,
        )

        ext in CONFIG_EXT -> FileIconVisual(
            icon = Icons.Rounded.DataObject,
            iconBg = scheme.surfaceVariant,
            iconFg = scheme.onSurfaceVariant,
        )

        ext in IMAGE_EXT -> FileIconVisual(
            icon = Icons.Rounded.Image,
            iconBg = scheme.tertiaryContainer,
            iconFg = scheme.onTertiaryContainer,
        )

        ext in ARCHIVE_EXT -> FileIconVisual(
            icon = Icons.Rounded.FolderZip,
            iconBg = scheme.primaryContainer,
            iconFg = scheme.onPrimaryContainer,
        )

        ext in AUDIO_EXT -> FileIconVisual(
            icon = Icons.Rounded.AudioFile,
            iconBg = scheme.tertiaryContainer,
            iconFg = scheme.onTertiaryContainer,
        )

        ext in VIDEO_EXT -> FileIconVisual(
            icon = Icons.Rounded.Movie,
            iconBg = scheme.tertiaryContainer,
            iconFg = scheme.onTertiaryContainer,
        )

        executable || ext in EXEC_EXT -> FileIconVisual(
            icon = Icons.Rounded.Launch,
            iconBg = scheme.errorContainer,
            iconFg = scheme.onErrorContainer,
        )

        else -> FileIconVisual(
            icon = Icons.Rounded.Description,
            iconBg = scheme.surfaceVariant,
            iconFg = scheme.onSurfaceVariant,
        )
    }
}
