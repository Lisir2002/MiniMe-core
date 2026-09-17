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
    val tile: Color = Color.Transparent,
    val vector: ImageVector? = null,
    val res: Int? = null,
    val glyph: Color = Color.Unspecified,
    val fullBleed: Boolean = false,
)

/** 阿里 iconfont 文件集合：自带背景与配色，全幅铺砖，不上色。 */
private fun brand(ext: String, res: Int) = FileTypeSpec(res = res, fullBleed = true)

private fun specFor(ext: String): FileTypeSpec {
    val e = ext.lowercase().trimStart('.')
    return when (e) {
        "html", "htm" -> brand(e, R.drawable.ic_file_html)
        "css" -> brand(e, R.drawable.ic_file_css)
        "js", "mjs", "cjs" -> brand(e, R.drawable.ic_file_js)
        "json" -> brand(e, R.drawable.ic_file_json)
        "php" -> brand(e, R.drawable.ic_file_php)
        "txt", "log" -> brand(e, R.drawable.ic_file_txt)
        "pdf" -> brand(e, R.drawable.ic_file_pdf)
        "csv" -> brand(e, R.drawable.ic_file_csv)
        "xls", "xlsx" -> brand(e, R.drawable.ic_file_xls)
        "doc", "docx" -> brand(e, R.drawable.ic_file_doc)
        "ppt", "pptx" -> brand(e, R.drawable.ic_file_ppt)
        "sql" -> brand(e, R.drawable.ic_file_sql)
        "mp3", "aac" -> brand(e, R.drawable.ic_file_mp3)
        "avi", "mov", "flv", "mp4", "mkv", "webm" -> brand(e, R.drawable.ic_file_mov)
        "gif", "png", "jpg", "jpeg", "webp", "bmp" -> brand(e, R.drawable.ic_file_gif)
        "svg" -> brand(e, R.drawable.ic_file_svg)
        "iso" -> brand(e, R.drawable.ic_file_iso)
        "dll" -> brand(e, R.drawable.ic_file_dll)
        "jar" -> brand(e, R.drawable.ic_file_jar)
        "psd" -> brand(e, R.drawable.ic_file_psd)
        "ttf" -> brand(e, R.drawable.ic_file_ttf)
        "kt", "kts" -> FileTypeSpec(Color(0xFF7F52FF), res = R.drawable.ic_kotlin)
        "py" -> FileTypeSpec(Color(0xFF3776AB), res = R.drawable.ic_python)
        "js", "mjs", "cjs" -> FileTypeSpec(Color(0xFFF7DF1E), res = R.drawable.ic_javascript, glyph = Color(0xFF1E1E1E))
        "ts", "tsx", "jsx" -> FileTypeSpec(Color(0xFF3178C6), res = R.drawable.ic_typescript)
        "go" -> FileTypeSpec(Color(0xFF00ADD8), res = R.drawable.ic_go)
        "rs" -> FileTypeSpec(Color(0xFF000000), res = R.drawable.ic_rust, glyph = Color(0xFFFF6600))
        "md", "markdown" -> FileTypeSpec(Color(0xFF083FA5), res = R.drawable.ic_markdown)
        "dockerfile", "docker" -> FileTypeSpec(Color(0xFF2496ED), res = R.drawable.ic_docker)
        "gradle" -> FileTypeSpec(Color(0xFF02303A), res = R.drawable.ic_gradle)
        "db", "sqlite", "sqlite3" -> FileTypeSpec(Color(0xFF003B57), res = R.drawable.ic_sqlite)
        "java" -> FileTypeSpec(Color(0xFFE76F00), vector = Icons.Rounded.DataObject)
        "c", "h" -> FileTypeSpec(Color(0xFFA8B9CC), res = R.drawable.ic_c, glyph = Color(0xFF00599C))
        "cpp", "cc", "cxx", "hpp" -> FileTypeSpec(Color(0xFF00599C), res = R.drawable.ic_cplusplus)
        "swift" -> FileTypeSpec(Color(0xFFF05138), res = R.drawable.ic_swift)
        "rb", "ruby" -> FileTypeSpec(Color(0xFFCC342D), res = R.drawable.ic_ruby)
        "lua" -> FileTypeSpec(Color(0xFF000080), res = R.drawable.ic_lua)
        "toml", "xml", "plist" -> FileTypeSpec(Color(0xFFA074C4), vector = Icons.Rounded.DataObject)
        "yaml", "yml", "ini", "conf", "env", "properties" -> FileTypeSpec(Color(0xFF8AA6CC), vector = Icons.Rounded.Settings)
        "sh", "bash", "zsh" -> FileTypeSpec(Color(0xFF4EAA25), vector = Icons.Rounded.Terminal)
        "bat", "cmd", "ps1" -> FileTypeSpec(Color(0xFF0078D4), vector = Icons.Rounded.Terminal)
        "wav", "flac", "m4a" -> FileTypeSpec(Color(0xFFEC4899), vector = Icons.Rounded.MusicNote)
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
    if (s.fullBleed) {
        androidx.compose.material3.Icon(
            painterFor(s),
            contentDescription = null,
            modifier = modifier.size(AppSizing.IconXl + 8.dp),
        )
        return
    }
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
