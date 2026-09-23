package com.mini.logs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mini.me_core.core.theme.AIEditorTheme
import com.mini.logs.data.LogsViewModel
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
    // 存储权限状态：null=未决定, true=已授予, false=已拒绝
    private var storagePermissionGranted by mutableStateOf<Boolean?>(null)

    // 日志页 ViewModel（Activity 级，SAF launcher 回调需要访问）
    private val logsViewModel: LogsViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storagePermissionGranted = granted
        // 权限授予后刷新文件列表
        if (granted) logsViewModel.refreshFiles()
    }

    // SAF 手动选择日志目录
    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uri = data?.data
            if (uri != null) {
                logsViewModel.onSafDirectorySelected(uri, data.flags)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查存储权限（targetSdk=28，READ_EXTERNAL_STORAGE 即可读公共目录）
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            storagePermissionGranted = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        setContent {
            AIEditorTheme {
                LogViewerRoot(
                    storagePermissionGranted = storagePermissionGranted,
                    onRequestPermission = {
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    },
                    onPickLogDirectory = {
                        openTreeLauncher.launch(
                            com.mini.logs.data.SafDirectoryManager.buildOpenTreeIntent()
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LogViewerRoot(
    storagePermissionGranted: Boolean?,
    onRequestPermission: () -> Unit,
    onPickLogDirectory: () -> Unit,
) {
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
            composable(BottomTab.Logs.route) {
                LogsScreen(
                    storagePermissionGranted = storagePermissionGranted,
                    onRequestPermission = onRequestPermission,
                    onPickLogDirectory = onPickLogDirectory,
                )
            }
            composable(BottomTab.Stats.route) { StatsScreen() }
            composable(BottomTab.Crash.route) { CrashScreen() }
            composable(BottomTab.Settings.route) {
                SettingsScreen(onPickLogDirectory = onPickLogDirectory)
            }
        }
    }
}
