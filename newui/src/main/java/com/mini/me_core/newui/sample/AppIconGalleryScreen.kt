package com.mini.me_core.newui.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

private data class IconEntry(val icon: ImageVector, val name: String, val old: String)
private data class IconSection(val title: String, val items: List<IconEntry>)

private val sections = listOf(
    IconSection(
        "对话与输入",
        listOf(
            IconEntry(Icons.Rounded.PlayArrow, "播放", "PlayArrow"),
            IconEntry(Icons.Rounded.Stop, "停止", "Stop / Cancel"),
            IconEntry(Icons.Rounded.Add, "新增", "Add"),
            IconEntry(Icons.Rounded.Tune, "更多设置", "Tune / MoreHoriz"),
            IconEntry(Icons.Rounded.MoreVert, "更多", "MoreVert"),
            IconEntry(Icons.Rounded.MoreHoriz, "水平更多", "MoreHoriz"),
            IconEntry(Icons.Rounded.Close, "关闭", "Close / Remove"),
            IconEntry(Icons.Rounded.Check, "确认", "Check"),
            IconEntry(Icons.Rounded.Edit, "编辑", "Edit"),
            IconEntry(Icons.Rounded.KeyboardArrowUp, "上箭头", "KeyboardArrowUp"),
            IconEntry(Icons.Rounded.KeyboardArrowDown, "下箭头", "KeyboardArrowDown / ExpandLess"),
            IconEntry(Icons.Rounded.ExpandMore, "展开", "ExpandMore"),
        ),
    ),
    IconSection(
        "模型与能力",
        listOf(
            IconEntry(Icons.Rounded.AutoAwesome, "AI/生成", "AutoAwesome"),
            IconEntry(Icons.Rounded.Psychology, "思考", "Psychology"),
            IconEntry(Icons.Rounded.Build, "技能", "Construction / Build"),
            IconEntry(Icons.Rounded.Bolt, "执行", "Bolt"),
            IconEntry(Icons.Rounded.Person, "用户", "Person"),
            IconEntry(Icons.Rounded.Visibility, "显示", "Visibility"),
            IconEntry(Icons.Rounded.VisibilityOff, "隐藏", "VisibilityOff"),
        ),
    ),
    IconSection(
        "文件与代码",
        listOf(
            IconEntry(Icons.Rounded.Description, "文件", "Description / InsertDriveFile"),
            IconEntry(Icons.Rounded.Folder, "目录", "Folder"),
            IconEntry(Icons.Rounded.Code, "代码", "Code"),
            IconEntry(Icons.Rounded.Terminal, "终端", "Terminal"),
            IconEntry(Icons.Rounded.ContentCopy, "复制", "ContentCopy"),
            IconEntry(Icons.Rounded.Upload, "上传", "Upload"),
            IconEntry(Icons.Rounded.Download, "下载", "Download"),
            IconEntry(Icons.Rounded.Image, "图片", "Image"),
            IconEntry(Icons.Rounded.PhotoCamera, "拍照", "PhotoCamera"),
            IconEntry(Icons.Rounded.Archive, "归档", "Archive / FolderZip"),
        ),
    ),
    IconSection(
        "状态与反馈",
        listOf(
            IconEntry(Icons.Rounded.CheckCircle, "成功", "CheckCircle"),
            IconEntry(Icons.Rounded.Warning, "警告", "Warning"),
            IconEntry(Icons.Rounded.Info, "提示", "Info"),
            IconEntry(Icons.Rounded.Refresh, "刷新", "Refresh / Sync"),
            IconEntry(Icons.Rounded.RestartAlt, "重置", "RestartAlt / Replay"),
            IconEntry(Icons.Rounded.Delete, "删除", "Delete"),
        ),
    ),
    IconSection(
        "通用导航",
        listOf(
            IconEntry(Icons.Rounded.Search, "搜索", "Search"),
            IconEntry(Icons.Rounded.Settings, "设置", "Settings"),
            IconEntry(Icons.Rounded.History, "历史", "History"),
            IconEntry(Icons.Rounded.Schedule, "定时", "Schedule / Timer"),
            IconEntry(Icons.Rounded.Share, "分享", "Share"),
            IconEntry(Icons.Rounded.Link, "链接", "Link"),
            IconEntry(Icons.Rounded.Language, "语言", "Language / Public"),
            IconEntry(Icons.Rounded.ChatBubble, "对话", "ChatBubble / Forum"),
        ),
    ),
)

/** 新版 UI 图标样板页：两列图标砖，每个标注替换的旧版图标。 */
@Composable
fun AppIconGalleryScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(appPalette().surface)
            .padding(horizontal = AppSpacing.Lg),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = AppSpacing.Lg)) {
                Text(
                    "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = appPalette().primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Sm))
                        .clickable { onBack() }
                        .padding(AppSpacing.Xs),
                )
                Spacer(Modifier.width(AppSpacing.Sm))
                Text("图标样板", style = MaterialTheme.typography.headlineSmall, color = appPalette().ink)
            }
            Text(
                "每个图标砖下方标注它替换的旧版图标名，统一使用新主题圆角与配色。",
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().labelTertiary,
                modifier = Modifier.padding(top = AppSpacing.Xs, bottom = AppSpacing.Sm),
            )
        }
        sections.forEach { sec ->
            item {
                Text(
                    sec.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = appPalette().labelSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = AppSpacing.Md, bottom = AppSpacing.Sm),
                )
            }
            sec.items.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                        pair.forEach { e -> IconTile(e, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(AppSpacing.Xl)) }
    }
}

@Composable
private fun IconTile(e: IconEntry, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(vertical = AppSpacing.Xs)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Md))
            .padding(AppSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(AppSizing.IconXl + AppSpacing.Md)
                .clip(RoundedCornerShape(AppRadius.Md))
                .background(appPalette().surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.icon, contentDescription = null, tint = appPalette().ink, modifier = Modifier.size(AppSizing.IconXl))
        }
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(e.name, style = MaterialTheme.typography.bodyMedium, color = appPalette().ink)
        Spacer(Modifier.height(1.dp))
        Text(e.old, style = MaterialTheme.typography.labelSmall, color = appPalette().labelTertiary)
    }
}
