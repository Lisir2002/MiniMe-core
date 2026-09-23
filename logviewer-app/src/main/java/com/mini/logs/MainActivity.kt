package com.mini.logs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mini.me_core.core.theme.AIEditorTheme
import com.mini.logs.ui.crash.CrashScreen
import com.mini.logs.ui.logs.LogsScreen
import com.mini.logs.ui.settings.SettingsScreen
import com.mini.logs.ui.stats.StatsScreen

/**
 * 底部导航 4 个 Tab：日志 / 统计 / 崩溃 / 设置。
 */
sealed class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Logs : BottomTab("logs", "日志", Icons.Rounded.Article)
    data object Stats : BottomTab("stats", "统计", Icons.Rounded.BarChart)
    data object Crash : BottomTab("crash", "崩溃", Icons.Rounded.BugReport)
    data object Settings : BottomTab("settings", "设置", Icons.Rounded.Settings)

    companion object {
        val all = listOf(Logs, Stats, Crash, Settings)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIEditorTheme {
                LogViewerRoot()
            }
        }
    }
}

@Composable
fun LogViewerRoot() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.all.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Logs.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomTab.Logs.route) { LogsScreen() }
            composable(BottomTab.Stats.route) { StatsScreen() }
            composable(BottomTab.Crash.route) { CrashScreen() }
            composable(BottomTab.Settings.route) { SettingsScreen() }
        }
    }
}
