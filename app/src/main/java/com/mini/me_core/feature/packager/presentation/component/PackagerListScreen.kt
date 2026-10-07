package com.mini.me_core.feature.packager.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.feature.packager.domain.model.BuildStatus
import com.mini.me_core.feature.packager.domain.model.Project
import com.mini.me_core.feature.packager.presentation.PackagerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用打包器 - 项目列表页
 *
 * 使用统一顶栏 AppTopAppBar 和统一卡片 AppCard，
 * 遵循项目设计规范。
 */
@Composable
fun PackagerListScreen(
    viewModel: PackagerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNewProject: () -> Unit,
    onOpenProject: (Project) -> Unit
) {
    val projects: List<Project> by viewModel.projects.collectAsState()
    val colors = LocalAppTheme.current.colors
    val cornerRadius = LocalCornerRadius.current

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = "应用打包器",
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = "返回"
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProject,
                containerColor = colors.brandPrimary,
                contentColor = colors.onBrandPrimary,
                shape = RoundedCornerShape(cornerRadius.lg)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "新建项目")
            }
        },
        containerColor = colors.surfacePage
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = PrimitiveSpacing.Md,
                    vertical = PrimitiveSpacing.Md
                ),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project) },
                        onBuild = { viewModel.startBuild(project) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalAppTheme.current.colors
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = colors.textTertiary
                )
            }
            Spacer(Modifier.height(PrimitiveSpacing.Lg))
            Text(
                text = "暂无项目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(PrimitiveSpacing.Xs))
            Text(
                text = "点击右下角按钮创建第一个项目",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary
            )
        }
    }
}

/**
 * 项目卡片（使用统一 AppCard 组件）
 */
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onBuild: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val cornerRadius = LocalCornerRadius.current
    val haptic = LocalHapticFeedback.current

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(PrimitiveSpacing.Md)
        ) {
            // 第一行：项目名称 + 状态徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                BuildStatusBadge(status = project.lastBuildStatus)
            }

            Spacer(Modifier.height(PrimitiveSpacing.Xs))

            // 第二行：包名
            Text(
                text = project.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(PrimitiveSpacing.Sm))

            // 第三行：类型 + 版本 + 构建时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius.sm))
                        .background(colors.brandPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = PrimitiveSpacing.Sm, vertical = 2.dp)
                ) {
                    Text(
                        text = project.type.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.brandPrimary
                    )
                }
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                Text(
                    text = "v${project.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                project.lastBuildAt?.let {
                    Spacer(Modifier.width(PrimitiveSpacing.Sm))
                    Text(
                        text = "构建: ${formatTime(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // 第四行：操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)
            ) {
                // 构建按钮（主按钮样式）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(cornerRadius.md))
                        .background(colors.brandPrimary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBuild()
                            }
                        )
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.onBrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "构建",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBrandPrimary
                    )
                }

                // 配置按钮（次按钮样式）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(cornerRadius.md))
                        .background(colors.surfaceSunken)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClick()
                            }
                        )
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "配置",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }

                // 删除按钮（图标按钮）
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDelete()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(cornerRadius.md))
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "删除",
                        tint = colors.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 构建状态徽章
 */
@Composable
private fun BuildStatusBadge(status: BuildStatus?) {
    if (status == null) return
    val colors = LocalAppTheme.current.colors
    val cornerRadius = LocalCornerRadius.current

    val (text, color) = when (status) {
        BuildStatus.SUCCESS -> "成功" to colors.success
        BuildStatus.FAILED -> "失败" to colors.error
        BuildStatus.BUILDING -> "构建中" to colors.brandPrimary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius.sm))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = PrimitiveSpacing.Sm, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 604800_000 -> "${diff / 86400_000}天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
