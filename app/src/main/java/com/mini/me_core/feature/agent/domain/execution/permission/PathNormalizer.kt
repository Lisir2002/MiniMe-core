package com.mini.me_core.feature.agent.domain.execution.permission

/**
 * 路径归一化（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 在比对「灾难性删除目标黑名单」前，把 shell 风格的家目录引用展开为容器真实路径，
 * 避免 `rm -rf $HOME`、`rm -rf ${HOME}`、`rm -rf ~`、`rm -rf ~user/.minime` 因字面量
 * 与黑名单（根目录、/root、/root 下通配等）不一致而绕过。
 *
 * 仅做字符串级展开，不触碰文件系统；不解析 `..` 软链（那是 PathBoundaryGuard 的职责）。
 */
object PathNormalizer {

    private val TILDE_RE = Regex("~([A-Za-z0-9_.-]*)")

    /**
     * 归一一个路径 token：
     * - `$HOME` / `${HOME}` → [ContainerPaths.CONTAINER_HOME]（/root）
     * - `~` 或 `~/...` → /root/...
     * - `~user` / `~user/...` → /home/user/...
     * 其余原样返回。
     */
    fun normalize(path: String): String {
        var s = path
        s = s.replace("\${HOME}", ContainerPaths.CONTAINER_HOME)
        s = s.replace("\$HOME", ContainerPaths.CONTAINER_HOME)
        // 仅对「以 ~ 开头」的 token 做家目录展开，避免误伤路径中其他 ~。
        if (s.startsWith("~")) {
            // 等价于 replaceFirst(input) { ... }，显式 find + replaceRange 避免重载歧义。
            val match = TILDE_RE.find(s)
            if (match != null) {
                val user = match.groupValues[1]
                val replacement =
                    if (user.isEmpty()) ContainerPaths.CONTAINER_HOME else "/home/$user"
                s = s.replaceRange(match.range, replacement)
            }
        }
        return s
    }
}
