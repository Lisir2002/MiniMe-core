package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WebAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * MCP 应用生命周期状态：加载交互式界面 → 就绪（可交互） / 失败。
 */
enum class AppMcpAppState { Loading, Ready, Error }

/**
 * MCP App 卡（分子组 · AppMcpAppCard）：工具声明 `_meta.ui.resourceUri` 后，
 * Host 把服务端返回的 HTML 界面渲染进对话内的沙箱 iframe（MCP Apps 规范，
 * 2026-01 首个官方 MCP extension）。本卡是该模式的设计系统载体：
 *
 * - 头部（Inspector）：工具图标 + 标题 + MCP 服务器徽标 + `APP` 徽标 + [resourceUri]（`ui://…`）。
 * - 画布（View）：模拟 16:9 深色 iframe 视口，按 [AppMcpAppState] 展示
 *   加载骨架（[AppMcpAppState.Loading]）/ 就绪后的交互式仪表盘（[AppMcpAppState.Ready]）/
 *   失败重试（[AppMcpAppState.Error]）。
 * - 安全提示：底部 `sandbox · iframe` 胶囊，明示沙箱隔离（无 Cookie / 不可逃逸宿主）。
 * - 操作：[onReload] 刷新界面、[onExpand] 全屏（Portal 占位，真实链路接 MCP Apps 的 ui/ 双向通道）。
 *
 * 真实落地：Android 端用 WebView 承载 [resourceUri] 内容，本卡提供外层镀铬
 * （header + 视口 + 安全提示 + 操作），与 [AppToolCallCard] 的纯文本工具调用区分。
 */
@Composable
fun AppMcpAppCard(
    title: String,
    modifier: Modifier = Modifier,
    serverPrefix: String? = null,
    state: AppMcpAppState = AppMcpAppState.Loading,
    resourceUri: String? = null,
    onReload: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
) {
    val cardShape = RoundedCornerShape(AppRadius.Md)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(AppColor.BrandCard)
            .border(1.dp, AppColor.SeparatorOnLight, cardShape),
    ) {
        // 头部：Inspector 一行话
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(AppColor.BrandPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WebAsset,
                    contentDescription = null,
                    tint = AppColor.BrandPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppColor.BrandInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (serverPrefix != null) {
                        Spacer(Modifier.width(AppSpacing.Xs))
                        McpChip(text = serverPrefix, tint = AppColor.BrandPrimary)
                    }
                    Spacer(Modifier.width(AppSpacing.Xs))
                    McpChip(text = "APP", tint = AppColor.BrandAccent)
                }
                if (resourceUri != null) {
                    Text(
                        text = resourceUri,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = AppColor.LabelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.Sm))
            McpAppStatusBadge(state = state)
        }

        McpDivider()

        // 画布：沙箱 iframe 视口（模拟 16:9 深色面）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = AppSpacing.Md)
                .clip(RoundedCornerShape(AppRadius.Md))
                .background(AppColor.BrandInk),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                AppMcpAppState.Loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AppColor.BrandPrimary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "正在加载交互式界面…",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelTertiary,
                    )
                }

                AppMcpAppState.Ready -> MockDashboard()

                AppMcpAppState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = AppColor.StatusDanger,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "界面加载失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelTertiary,
                    )
                }
            }
        }

        // 底部：沙箱提示 + 资源地址 + 操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "sandbox · iframe",
                style = MaterialTheme.typography.labelSmall,
                color = AppColor.LabelSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Sm))
                    .background(AppColor.BrandSurfaceDim)
                    .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
            )
            Spacer(Modifier.weight(1f))
            if (onReload != null) {
                IconButton(onClick = onReload, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新界面",
                        tint = AppColor.LabelSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (onExpand != null) {
                IconButton(onClick = onExpand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "全屏查看",
                        tint = AppColor.LabelSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 状态徽标：加载旋转 / 就绪绿勾 / 失败红叉。 */
@Composable
private fun McpAppStatusBadge(state: AppMcpAppState) {
    when (state) {
        AppMcpAppState.Loading -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = AppColor.BrandPrimary,
            strokeWidth = 2.dp,
        )
        AppMcpAppState.Ready -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "就绪",
            tint = AppColor.StatusSuccess,
            modifier = Modifier.size(16.dp),
        )
        AppMcpAppState.Error -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "失败",
            tint = AppColor.StatusDanger,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 小胶囊徽标：MCP 服务器名 / `APP` 标识。 */
@Composable
private fun McpChip(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Sm))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = AppSpacing.Xs, vertical = 1.dp),
    )
}

/**
 * 就绪态占位仪表盘：深色画布内的简化柱状图，示意"工具返回的交互式 HTML 已可交互"，
 * 真实链路中此区域由 WebView 承载 MCP App 的实际界面。
 */
@Composable
private fun MockDashboard() {
    val bars = listOf(0.34f, 0.55f, 0.42f, 0.72f, 0.50f, 0.88f, 0.63f, 0.46f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, fraction ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (index % 3 == 0) AppColor.BrandPrimary.copy(alpha = 0.85f)
                            else AppColor.BrandAccent.copy(alpha = 0.65f),
                        ),
                )
            }
        }
        // 基线
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColor.LabelTertiary.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun McpDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColor.SeparatorOnLight),
    )
}
