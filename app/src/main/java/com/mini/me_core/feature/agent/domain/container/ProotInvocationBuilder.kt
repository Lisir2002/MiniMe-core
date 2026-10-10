package com.mini.me_core.feature.agent.domain.container

import com.mini.me_core.core.container.api.ContainerProfile
import com.mini.me_core.core.container.api.ProotInvocation
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import java.io.File

/**
 * proot 命令行/参数/绑定装配，消费 [ProotInvocation]（行为保持搬迁 / 签名保持）。
 *
 * 负责把「当前 profile + rootfs + 标准绑定（[ContainerBindRegistry]）+ 桥令牌
 * （[RcbBridgeInjector]）+ 代理 env」装配成完整 argv/env 并据此启动进程。
 * argv 元素顺序、绑定顺序、env 键值与回退原生 shell 路径均与原实现逐字一致。
 */
@Suppress("ClassOrdering") // 协作者分组，行为保持（primary constructor 语法上在类头，无法满足 detekt 属性→构造的逻辑顺序）
internal class ProotInvocationBuilder(
    private val installer: ContainerInstaller,
    private val proxyManager: ClashProxyManager,
    private val bridgeInjector: RcbBridgeInjector,
    private val profileProvider: () -> ContainerProfile,
    private val storageShareEnabledProvider: () -> Boolean,
    private val shellProvider: () -> String,
    private val onMcpConfigChange: () -> Unit,
) {
    private companion object {
        const val TAG = "LinuxContainerEngine"
    }

    private val binds = ContainerBindRegistry(installer)

    /**
     * 构造 PRoot 调用的公共前缀 argv：proot 二进制 + rootfs + 标准绑定 + 伪 root + 各目录绑定，
     * 但**不含**最终的客户机命令（由各调用方自行追加 `/bin/sh -c …` 或 `exec` 形式）。
     */
    fun buildBaseProotArgv(projectPath: String?): MutableList<String> {
        val profile = profileProvider()
        val rootfs = installer.rootfsDirFor(profile).absolutePath
        // 按 profile + 宿主架构取正确架构的 proot 可执行（x86_64 宿主跑 x86_64 容器 → x86_64 原生 proot）
        val prootBin = installer.prootBinFor(profile).absolutePath

        val argv = mutableListOf(
            prootBin,
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/system",  // 绑定 /system 让宿主动态库可用
            "-0"              // 伪 root，apk 等需要
        )

        binds.appendQemuTranslation(argv, profile)
        binds.appendWorkspace(argv, projectPath)
        binds.appendMinime(argv)

        // 容器侧 mcp.json 变更只标记「待确认」，不在这里触发任何宿主重载。
        onMcpConfigChange()

        binds.appendStorageShared(argv, rootfs, storageShareEnabledProvider())
        binds.appendDownload(argv, rootfs)
        binds.appendProfileExtras(argv, profile)

        return argv
    }

    /** 容器内进程的标准环境变量（proot loader / 动态库 / PATH / HOME 等）。 */
    fun buildContainerEnv(): Map<String, String> {
        val profile = profileProvider()
        val (bridgeAddr, bridgeToken) = bridgeInjector.ensureSession()
        val env = mapOf(
            // Android proot 必需的环境变量
            "PROOT_TMP_DIR" to installer.prootTmpDir.absolutePath, // Android 没有 /tmp
            // Termux proot 的 loader 分离，必须用 PROOT_LOADER/_32 指向，否则无法注入子进程而起不来。
            // 按 profile + 宿主架构取对应架构的 loader（x86_64 宿主 → x86_64 原生 loader）。
            "PROOT_LOADER" to installer.prootLoaderFor(profile).absolutePath,
            "PROOT_LOADER_32" to installer.prootLoader32For(profile).absolutePath,
            // Termux proot 动态链接 libtalloc.so.2 / libandroid-shmem.so，需让 linker64 能找到它们；
            // libc.so/liblog.so 走系统默认路径(/system/lib64)。
            "LD_LIBRARY_PATH" to "${installer.prootLibDirFor(profile).absolutePath}:/system/lib64:/system/lib",
            // 说明（statx / seccomp）：旧 proot 5.1.0 的 seccomp 过滤表没有 statx，Node 用 statx 解析
            // 模块路径会拿到未翻译的 ~/workspace/xxx → ENOENT「Cannot find module」。Termux proot
            // (5.1.107.x) 的 seccomp 过滤表已包含 statx，默认 seccomp 模式即可正确翻译，故此处
            // **刻意不设 PROOT_NO_SECCOMP**——这正是 Termux 自己用 proot 的方式；强制全量 ptrace
            // (PROOT_NO_SECCOMP=1) 反而在本设备触发过 ptrace(PEEKDATA) I/O error。
            "PATH" to "/root/.minime/bin:/usr/bin:/bin:/usr/sbin:/sbin",
            "HOME" to "/root",
            // 容器 ⇄ 宿主桥端点：让 rcb-* 命令能连回宿主 App（见 RcbBridge）。
            // 令牌为本会话一次性随机值：会话内首个 proot 进程启动时生成并注入 env，
            // 会话结束 [RcbBridgeInjector.endSession] 立即失效；剪贴板读写另由宿主按次确认门控。
            "RCB_BRIDGE_ADDR" to bridgeAddr,
            "RCB_BRIDGE_TOKEN" to bridgeToken,
            // git 全局配置指向持久挂载里的 .gitconfig（/root/.minime 绑定到宿主 filesDir/minime，
            // 跨 rootfs 升级不丢）。git-credentials 同放该目录，credential.helper=store 经此读。
            // 让终端/AI/UI 三端 git 都读同一份配置与凭据，详见 GitCredentialsFileSync。
            "GIT_CONFIG_GLOBAL" to "/root/.minime/.gitconfig",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8"
        )
        // 网络代理（VPN）：启用时注入 http/https/all_proxy → 127.0.0.1:7890（容器内 mihomo 与宿主
        // 共享 loopback，同一实例），NO_PROXY 保护本地服务；未启用则并入空 map 等价不改动。
        return env + proxyManager.exportContainerEnv()
    }

    /**
     * 构造进入容器执行 [command] 的完整 PRoot 调用（argv + env）。
     * 暴露给终端会话：Termux TerminalSession 需要把可执行文件与参数分开传入。
     */
    fun buildProotInvocation(command: String, projectPath: String?): ProotInvocation {
        val argv = buildBaseProotArgv(projectPath)
        // 用 defaultShell：bash 装好后 AI 命令与终端会话都走 bash；装机期间/失败回退 /bin/sh。
        argv.add(shellProvider())
        argv.add("-c")
        argv.add(command)
        return ProotInvocation(argv, buildContainerEnv())
    }

    /**
     * 构造「在容器内直接 exec 某程序（保留分离流）」的 PRoot 调用。
     * 用 `sh -c 'exec "$0" "$@"' program arg1 arg2 …` 把参数原样交给 execvp，规避引号问题。
     */
    private fun buildStdioInvocation(
        program: String,
        programArgs: List<String>,
        projectPath: String?,
        extraEnv: Map<String, String>
    ): ProotInvocation {
        val argv = buildBaseProotArgv(projectPath)
        argv.add("/bin/sh")
        argv.add("-c")
        argv.add("exec \"\$0\" \"\$@\"")
        argv.add(program)
        argv.addAll(programArgs)
        return ProotInvocation(argv, buildContainerEnv() + extraEnv)
    }

    /**
     * 以长驻进程方式在容器内执行命令（如 `sshd -D`）。调用前需保证容器已安装。
     * 不读取/消费输出流，由调用方决定如何处理（通常丢弃）。
     */
    fun startProotProcess(command: String, projectPath: String?): Process {
        val pb = buildProcessBuilder(buildProotInvocation(command, projectPath))
        pb.redirectErrorStream(true)
        return pb.start()
    }

    /**
     * 以长驻进程方式在容器内启动一个程序并保留**分离的** stdin/stdout/stderr，供调用方
     * 双向流式通信（如 MCP stdio server）。**不** redirectErrorStream，stderr 独立保留，
     * 保证 stdout 是干净的协议流。[extraEnv] 叠加到容器默认环境（覆盖同名项）。
     */
    fun startStdioProcess(
        program: String,
        programArgs: List<String>,
        projectPath: String?,
        extraEnv: Map<String, String> = emptyMap()
    ): Process {
        val invocation = buildStdioInvocation(program, programArgs, projectPath, extraEnv)
        val pb = buildProcessBuilder(invocation)
        // 刻意不 redirectErrorStream：stdout 留给 JSON-RPC，stderr 由调用方单独消费。
        return pb.start()
    }

    /**
     * 启动进程。rootfs/proot 安装就绪则用 PRoot 进入容器；
     * 否则回退到 Android 原生 shell（rootfs 缺失时的兜底）。
     */
    fun startContainerProcess(command: String, projectPath: String?): Process {
        val profile = profileProvider()
        val useProot = installer.isInstalledFor(profile)

        val processBuilder = if (useProot) {
            buildProcessBuilder(buildProotInvocation(command, projectPath))
        } else {
            FileLogger.w(TAG, "PRoot 未安装，回退到原生 shell")
            buildNativeProcess(command, projectPath)
        }

        // Redirect stderr to stdout so we capture everything in one stream
        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    private fun buildProcessBuilder(invocation: ProotInvocation): ProcessBuilder {
        val processBuilder = ProcessBuilder(invocation.argv)
        processBuilder.environment().putAll(invocation.env)
        return processBuilder
    }

    private fun buildNativeProcess(command: String, projectPath: String?): ProcessBuilder {
        // Fallback to Android's native shell
        val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
        if (projectPath != null) {
            processBuilder.directory(File(projectPath))
        }
        return processBuilder
    }
}
