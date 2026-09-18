package com.mini.me_core.newui.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mini.me_core.newui.designsystem.layout.AppShell
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 工具占位页统一壳：AppShell 包壳（自带标题顶栏，不画返回箭头），内容居中。
 * 浏览器 / 终端接入真实业务前先用此占位，不接 ServiceBrowserScreen / TerminalScreen。
 */
@Composable
private fun ToolPlaceholderScreen(
    title: String,
    subtitle: String,
) {
    AppShell(title = title) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = appPalette().ink,
            )
            Spacer(Modifier.height(AppSpacing.Md))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary,
            )
        }
    }
}

/** 浏览器占位页（未接入真实 ServiceBrowserScreen）。 */
@Composable
fun BrowserPlaceholderScreen() {
    ToolPlaceholderScreen(title = "浏览器", subtitle = "浏览器 · 占位页，待接入")
}

/** 终端占位页（未接入真实 TerminalScreen）。 */
@Composable
fun TerminalPlaceholderScreen() {
    ToolPlaceholderScreen(title = "终端", subtitle = "终端 · 占位页，待接入")
}
