package com.mini.me_core.newui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.R
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing

private data class FileTypeSpec(
    val tile: Color,
    val vector: ImageVector? = null,
    val res: Int? = null,
    val glyph: Color = Color.White,
)

private fun specFor(ext: String): FileTypeSpec {
    val e = ext.lowercase().trimStart('.')
    return when (e) {
        "kt", "kts" -> FileTypeSpec(Color(0xFF7F52FF), res = R.drawable.ic_kotlin)
        "py" -> FileTypeSpec(Color(0xFF3776AB), res = R.drawable.ic_python)
        "js", "mjs", "cjs" -> FileTypeSpec(Color(0xFFF7DF1E), res = R.drawable.ic_javascript, glyph = Color(0xFF1E1E1E))
        "ts", "tsx", "jsx" -> FileTypeSpec(Color(0xFF3178C6), res = R.drawable.ic_typescript)
        "go" -> FileTypeSpec(Color(0xFF00ADD8), res = R.drawable.ic_go)
        "rs" -> FileTypeSpec(Color(0xFF000000), res = R.drawable.ic_rust, glyph = Color(0xFFFF6600))
        "html", "htm" -> FileTypeSpec(Color(0xFFE34F26), res = R.drawable.ic_html5)
        "md", "markdown" -> FileTypeSpec(Color(0xFF083FA5), res = R.drawable.ic_markdown)
        "dockerfile", "docker" -> FileTypeSpec(Color(0xFF2496ED), res = R.drawable.ic_docker)
        "gradle" -> FileTypeSpec(Color(0xFF02303A), res = R.drawable.ic_gradle)
        "db", "sqlite", "sqlite3" -> FileTypeSpec(Color(0xFF003B57), res = R.drawable.ic_sqlite)
        "java" -> FileTypeSpec(Color(0xFFE76F00), vector = Icons.Rounded.DataObject)
        "c", "h" -> FileTypeSpec(Color(0xFF555555), vector = Icons.Rounded.DataObject)
        "cpp", "cc", "cxx", "hpp" -> FileTypeSpec(Color(0xFF00599C), vector = Icons.Rounded.DataObject)
        "swift" -> FileTypeSpec(Color(0xFFF05138), vector = Icons.Rounded.DataObject)
        "css" -> FileTypeSpec(Color(0xFF1572B6), vector = Icons.Rounded.DataObject)
        "json", "toml", "xml", "plist" -> FileTypeSpec(Color(0xFFA074C4), vector = Icons.Rounded.DataObject)
        "yaml", "yml", "ini", "conf", "env", "properties" -> FileTypeSpec(Color(0xFF8AA6CC), vector = Icons.Rounded.Settings)
        "sh", "bash", "zsh" -> FileTypeSpec(Color(0xFF4EAA25), vector = Icons.Rounded.Terminal)
        "bat", "cmd", "ps1" -> FileTypeSpec(Color(0xFF0078D4), vector = Icons.Rounded.Terminal)
        "txt", "log" -> FileTypeSpec(Color(0xFF9CA3AF), vector = Icons.Rounded.Description)
        "pdf" -> FileTypeSpec(Color(0xFFE8442E), vector = Icons.Rounded.PictureAsPdf)
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> FileTypeSpec(Color(0xFF8B5CF6), vector = Icons.Rounded.Image)
        "mp3", "wav", "flac", "m4a" -> FileTypeSpec(Color(0xFFEC4899), vector = Icons.Rounded.MusicNote)
        "mp4", "mov", "mkv", "webm" -> FileTypeSpec(Color(0xFFEF4444), vector = Icons.Rounded.Movie)
        "zip", "tar", "gz", "rar", "7z" -> FileTypeSpec(Color(0xFFB8860B), vector = Icons.Rounded.FolderZip)
        else -> FileTypeSpec(Color(0xFF9CA3AF), vector = Icons.Rounded.Description)
    }
}

@Composable
private fun painterFor(s: FileTypeSpec): Painter = when {
    s.res != null -> painterResource(s.res)
    s.vector != null -> rememberVectorPainter(s.vector)
    else -> rememberVectorPainter(Icons.Rounded.Description)
}

@Composable
fun FileTypeIcon(ext: String, modifier: Modifier = Modifier) {
    val s = specFor(ext)
    Box(
        modifier
            .size(AppSizing.IconXl + 8.dp)
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(s.tile),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painterFor(s),
            contentDescription = null,
            tint = s.glyph,
            modifier = Modifier.size(AppSizing.IconXl),
        )
    }
}
