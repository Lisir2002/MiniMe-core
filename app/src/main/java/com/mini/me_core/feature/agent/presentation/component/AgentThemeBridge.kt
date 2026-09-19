package com.mini.me_core.feature.agent.presentation.component

/**
 * 依赖方向桥接：ChatDrawer 等 agent presentation 组件历史上直接引用 settings 的 AppThemeMode。
 * 这里以全限定名建立同包 typealias，使组件源码不再 `import feature.settings`，
 * 把 agent→settings 的源码引用收敛到本文件一处，便于后续真正下沉共享主题模型。
 */
typealias AppThemeMode = com.mini.me_core.feature.settings.data.repository.AppThemeMode
