package com.mini.me_core.feature.agent.domain.guard

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 工具护栏 multibinding（D1-3）：把每个 [ToolGuard] 实现汇集为 Set，
 * 供 workflow（guard 段）构造注入遍历执行。
 *
 * 新增护栏时在此追加一行 `@Binds @IntoSet` 绑定即可（模式对齐 HookModule / SlashCommandModule）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GuardModule {

    @Binds
    @IntoSet
    abstract fun bindFileObservationGuard(guard: FileObservationGuard): ToolGuard

    @Binds
    @IntoSet
    abstract fun bindDangerousCommandGuard(guard: DangerousCommandGuard): ToolGuard

    @Binds
    @IntoSet
    abstract fun bindLargeFileGuard(guard: LargeFileGuard): ToolGuard

    @Binds
    @IntoSet
    abstract fun bindPathBoundaryGuard(guard: PathBoundaryGuard): ToolGuard
}
