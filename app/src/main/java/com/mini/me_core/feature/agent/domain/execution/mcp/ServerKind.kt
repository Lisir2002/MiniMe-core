package com.mini.me_core.feature.agent.domain.execution.mcp

/**
 * G2：MCP server 的**形态**。
 *
 * 此前形态信息散落在配置上（`builtinGroup != null` / `command` 非空 / `url` 非空），
 * 消费方各自写一遍判定——三处判定一旦漂移，就会出现「界面说这是内置 server、
 * 实际走了远端连接」这类最难排查的偏差。收敛到单一枚举后，形态只在这里派生一次。
 *
 * 只定义**仓库里真实存在**的三种形态。规划文档 §5 曾列出 `HOST`，但本仓没有任何
 * 承载它的实现（宿主形态的工具不走 [McpServerConfig]，而是直接注册进 `ToolRegistry`），
 * 故**不定义**——加一个永不出现的枚举值，等于给后来者留一个会误用的空壳。
 */
enum class ServerKind {
    /** F1：进程内内置 server（[BuiltinGroup] + 内置 transport），`url`/`command` 均不适用。 */
    BUILTIN,

    /** 远端 HTTP（含 `url`）。 */
    REMOTE_HTTP,

    /** 远端/本地子进程 stdio（含 `command`）。 */
    REMOTE_STDIO;

    companion object {
        /**
         * 从配置派生形态。**唯一实现**——任何需要判断 server 形态的地方都应走这里。
         *
         * 判定顺序即优先级：内置 > stdio > HTTP。
         * - 内置优先：`builtinGroup` 非空即意味进程内 server，与传输字段互斥（配置解析
         *   已保证），但若二者意外同时存在，内置语义更具体，故先判。
         * - stdio 优先于 HTTP：`isStdio` 看 `command` 是否非空。一个同时带 `command` 与
         *   `url` 的畸形配置里，`command` 是实际被执行的连接方式，故以其为准。
         */
        fun derive(cfg: McpServerConfig): ServerKind = when {
            cfg.isBuiltin -> BUILTIN
            cfg.isStdio -> REMOTE_STDIO
            else -> REMOTE_HTTP
        }
    }
}
