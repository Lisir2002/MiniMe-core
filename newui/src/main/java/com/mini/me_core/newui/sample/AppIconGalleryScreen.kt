package com.mini.me_core.newui.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
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
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

private data class IconEntry(
    val icon: ImageVector,
    val name: String,
    val old: String,
    val note: String,
)

private data class IconSection(val title: String, val items: List<IconEntry>)

private val sections = listOf(
    IconSection(
        "对话与输入",
        listOf(
            IconEntry(Icons.Rounded.Send, "发送", "KeyboardArrowUp / Send", "发送"),
            IconEntry(Icons.Rounded.Stop, "停止", "Stop / Cancel", "停止流式"),
            IconEntry(Icons.Rounded.Add, "新增", "Add", "附件/新增"),
            IconEntry(Icons.Rounded.Tune, "更多设置", "Tune / MoreHoriz", "更多设置"),
            IconEntry(Icons.Rounded.KeyboardArrowUp, "上箭头", "KeyboardArrowUp", "展开上"),
            IconEntry(Icons.Rounded.KeyboardArrowDown, "下箭头", "KeyboardArrowDown / ExpandMore", "展开下"),
            IconEntry(Icons.Rounded.Close, "关闭", "Close / Remove", "关闭"),
            IconEntry(Icons.Rounded.Check, "确认", "Check", "确认"),
            IconEntry(Icons.Rounded.Edit, "编辑", "Edit", "编辑"),
        ),
    ),
    IconSection(
        "模型与能力",
        listOf(
            IconEntry(Icons.Rounded.AutoAwesome, "AI/生成", "AutoAwesome", "AI/生成"),
            IconEntry(Icons.Rounded.Psychology, "思考", "Psychology", "思考强度"),
            IconEntry(Icons.Rounded.Build, "技能", "Construction", "技能/Playbook"),
            IconEntry(Icons.Rounded.Bolt, "执行", "Bolt", "快速/执行"),
            IconEntry(Icons.Rounded.Visibility, "显示", "Visibility", "显示"),
            IconEntry(Icons.Rounded.VisibilityOff, "隐藏", "VisibilityOff", "隐藏"),
        ),
    ),
    IconSection(
        "文件与代码",
        listOf(
            IconEntry(Icons.Rounded.Description, "文件", "Description / InsertDriveFile", "文件"),
            IconEntry(Icons.Rounded.Folder, "目录", "Folder", "目录"),
            IconEntry(Icons.Rounded.Code, "代码", "Code", "代码"),
            IconEntry(Icons.Rounded.Terminal, "终端", "Terminal", "终端"),
            IconEntry(Icons.Rounded.ContentCopy, "复制", "ContentCopy", "复制"),
            IconEntry(Icons.Rounded.Upload, "上传", "Upload", "上传"),
            IconEntry(Icons.Rounded.Download, "下载", "Download", "下载"),
        ),
    ),
    IconSection(
        "状态与反馈",
        listOf(
            IconEntry(Icons.Rounded.CheckCircle, "成功", "CheckCircle", "成功"),
            IconEntry(Icons.Rounded.Warning, "警告", "Warning", "警告"),
            IconEntry(Icons.Rounded.Info, "提示", "Info", "提示"),
            IconEntry(Icons.Rounded.Refresh, "刷新", "Refresh / Sync", "刷新/重试"),
        ),
    ),
    IconSection(
        "通用",
        listOf(
            IconEntry(Icons.Rounded.Search, "搜索", "Search", "搜索"),
            IconEntry(Icons.Rounded.Settings, "设置", "Settings", "设置"),
            IconEntry(Icons.Rounded.MoreVert, "更多", "MoreVert", "更多"),
            IconEntry(Icons.Rounded.Language, "语言", "Language / Public", "语言/网络"),
            IconEntry(Icons.Rounded.ChatBubble, "对话", "ChatBubble", "对话"),
            IconEntry(Icons.Rounded.Archive, "归档", "Archive", "归档"),
            IconEntry(Icons.Rounded.ExpandMore, "展开", "ExpandMore", "折叠展开"),
        ),
    ),
)

/** 新版 UI 图标样板页：每个图标标注它替换的旧版图标。 */
@Composable
fun AppIconGalleryScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(appPalette().surface)
            .padding(AppSpacing.Lg),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.padding(AppSpacing.Md))
        }
        sections.forEach { sec ->
            item {
                Text(
                    sec.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = appPalette().labelSecondary,
                    modifier = Modifier.padding(top = AppSpacing.Md, bottom = AppSpacing.Xs),
                )
            }
            items(sec.items) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.Xs)
                        .clip(RoundedCornerShape(AppRadius.Md))
                        .background(appPalette().card)
                        .border(AppStroke.Thin, appPalette().separator, RoundedCornerShape(AppRadius.Md))
                        .padding(AppSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(AppSizing.IconXl)
                            .clip(RoundedCornerShape(AppRadius.Md))
                            .background(appPalette().surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(entry.icon, contentDescription = null, tint = appPalette().ink, modifier = Modifier.size(AppSizing.IconM))
                    }
                    Spacer(Modifier.width(AppSpacing.Md))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = appPalette().ink)
                        Text("替换旧版：${entry.old}", style = MaterialTheme.typography.labelSmall, color = appPalette().labelTertiary)
                    }
                    Text(entry.note, style = MaterialTheme.typography.labelSmall, color = appPalette().labelSecondary)
                }
            }
        }
    }
}
