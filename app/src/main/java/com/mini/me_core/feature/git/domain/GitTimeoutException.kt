package com.mini.me_core.feature.git.domain

/**
 * 某条 git 命令因超过限定时长被强制终止。
 *
 * 与 [GitCommandFailureException]（git 以非零退出码结束的真实业务失败）区分：本异常专门承载
 * 「命令跑太慢被看门狗 kill」这一类情形——凭据弹窗在途、远端无响应、超大仓库遍历等。上层可据此
 * 把提示文案从「失败 + 原因」切换为「操作超时，请重试」，而非把超时误判成仓库错误。
 *
 * 判定来源：[com.mini.me_core.feature.agent.domain.container.CommandResult.timedOut] 为 true
 * （此时退出码恒为 null）。未就绪引导、进程崩溃等同样表现为退出码 null 但 timedOut=false，
 * 不属于本异常，仍按普通失败处理，避免误报超时。
 *
 * @param operation 触发超时的 git 子命令名（如 "pull"、"log"），仅用于日志与提示。
 * @param timeoutMs 本次命令设定的超时上限（毫秒）。
 */
class GitTimeoutException(
    val operation: String,
    val timeoutMs: Long
) : Exception("git 操作[$operation] 超过 ${timeoutMs}ms 被强制终止")
