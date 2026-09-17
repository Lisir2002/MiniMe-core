package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.YoutubeSearchedFor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing

/**
 * 文件类型专属图标：按扩展名映射到「彩色圆角砖 + 专属图形」，
 * 不使用字母角标。文件卡片/附件面板统一调用。
 */
private data class FileTypeSpec(val icon: ImageVector, val tile: Color, val glyph: Color)

private fun specFor(ext: String): FileTypeSpec {
    val e = ext.lowercase().trimStart('.')
    return when (e) {
        "kt", "kts" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF7F52FF), Color.White)
        "java" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFFE76F00), Color.White)
        "py" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF3776AB), Color.White)
        "js", "mjs", "cjs" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFFF7DF1E), Color(0xFF1E1E1E))
        "ts", "tsx", "jsx" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF3178C6), Color.White)
        "go" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF00ADD8), Color.White)
        "rs" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFFDEA584), Color(0xFF1E1E1E))
        "c", "h" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF555555), Color.White)
        "cpp", "cc", "cxx", "hpp" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFF00599C), Color.White)
        "swift" -> FileTypeSpec(Icons.Rounded.Code, Color(0xFFF05138), Color.White)
        "html", "htm" -> FileTypeSpec(Icons.Rounded.Language, Color(0xFFE34C26), Color.White)
        "css" -> FileTypeSpec(Icons.Rounded.Palette, Color(0xFF1572B6), Color.White)
        "json", "toml", "xml", "plist" -> FileTypeSpec(Icons.Rounded.DataObject, Color(0xFFA074C4), Color.White)
        "yaml", "yml", "ini", "conf", "env", "properties" -> FileTypeSpec(Icons.Rounded.Settings, Color(0xFF8AA6CC), Color.White)
        "sh", "bash", "zsh" -> FileTypeSpec(Icons.Rounded.Terminal, Color(0xFF4EAA25), Color.White)
        "bat", "cmd", "ps1" -> FileTypeSpec(Icons.Rounded.Terminal, Color(0xFF0078D4), Color.White)
        "md", "markdown" -> FileTypeSpec(Icons.Rounded.MenuBook, Color(0xFF6B7280), Color.White)
        "txt", "log" -> FileTypeSpec(Icons.Rounded.Description, Color(0xFF9CA3AF), Color.White)
        "pdf" -> FileTypeSpec(Icons.Rounded.PictureAsPdf, Color(0xFFE8442E), Color.White)
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> FileTypeSpec(Icons.Rounded.Image, Color(0xFF8B5CF6), Color.White)
        "mp3", "wav", "flac", "m4a" -> FileTypeSpec(Icons.Rounded.MusicNote, Color(0xFFEC4899), Color.White)
        "mp4", "mov", "mkv", "webm" -> FileTypeSpec(Icons.Rounded.Movie, Color(0xFFEF4444), Color.White)
        "zip", "tar", "gz", "rar", "7z" -> FileTypeSpec(Icons.Rounded.FolderZip, Color(0xFFB8860B), Color.White)
        "db", "sqlite", "sqlite3" -> FileTypeSpec(Icons.Rounded.Storage, Color(0xFF0EA5E9), Color.White)
        "gradle" -> FileTypeSpec(Icons.Rounded.Build, Color(0xFF02303A), Color(0xFF00C2FF))
        "dockerfile", "docker" -> FileTypeSpec(Icons.Rounded.YoutubeSearchedFor, Color(0xFF2496ED), Color.White)
        else -> FileTypeSpec(Icons.Rounded.BugReport, Color(0xFF9CA3AF), Color.White)
    }
}

/** 文件类型砖图标：与文件卡片 28dp 尺寸对齐。 */
@Composable
fun FileTypeIcon(
    ext: String,
    modifier: Modifier = Modifier,
) {
    val s = specFor(ext)
    Box(
        modifier
            .size(AppSizing.IconXl + 8.dp)
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(s.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            s.icon,
            contentDescription = null,
            tint = s.glyph,
            modifier = Modifier.size(AppSizing.IconXl),
        )
    }
}
