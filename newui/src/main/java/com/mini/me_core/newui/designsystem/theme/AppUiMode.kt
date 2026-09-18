package com.mini.me_core.newui.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * newui 自立的日 / 夜 / 自动模式接入口（与旧 app 主题 `AIEditorTheme` / `LocalAppDarkMode` 解耦）。
 *
 * - [Light]：强制浅色；
 * - [Dark]：强制深色（宿主/演示页可 `CompositionLocalProvider(LocalAppUiMode provides AppUiMode.Dark)` 单独夜览）；
 * - [Auto]：跟随系统（[AppTheme] 内部才读 `isSystemInDarkTheme()`）。
 */
enum class AppUiMode { Light, Dark, Auto }

/** 全局 UI 模式注入点，默认 [AppUiMode.Auto]。 */
val LocalAppUiMode = staticCompositionLocalOf { AppUiMode.Auto }

/** @Composable 包装：在组合树内安全读取当前 [AppUiMode]。 */
@Composable
fun appUiMode(): AppUiMode = LocalAppUiMode.current
