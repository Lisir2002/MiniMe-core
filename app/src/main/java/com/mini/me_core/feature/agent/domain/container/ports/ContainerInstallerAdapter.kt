package com.mini.me_core.feature.agent.domain.container.ports

import com.mini.me_core.core.container.api.ContainerInstallerPort
import com.mini.me_core.core.container.api.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import java.io.File
import javax.inject.Inject

/**
 * [ContainerInstallerPort] 的薄适配器：纯委托到 @Singleton 的 [ContainerInstaller]。
 */
class ContainerInstallerAdapter @Inject constructor(
    private val delegate: ContainerInstaller,
) : ContainerInstallerPort {

    override val rootfsDir: File get() = delegate.rootfsDir

    override val minimeDir: File get() = delegate.minimeDir

    override fun rootfsDirFor(profile: ContainerProfile): File = delegate.rootfsDirFor(profile)

    override fun deleteCustomRootfs(profile: ContainerProfile) = delegate.deleteCustomRootfs(profile)

    override fun resetBuiltinRootfs() = delegate.resetBuiltinRootfs()

    override fun resetBuiltinX86Rootfs() = delegate.resetBuiltinX86Rootfs()
}
