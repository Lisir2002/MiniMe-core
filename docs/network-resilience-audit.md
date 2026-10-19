# 网络韧性优化审计：为什么「网络很好」还显示「网络波动，正在重试」

> 2026-10 网络韧性优化四连提交的调研与根因记录。
> 用户原话：「我的网络是很好的情况下，依旧会显示网络波动，重试」。
>
> 提交序列：
> 1. `fix(agent)` 重写重试逻辑（RetryPolicy：full jitter + RetryBudget + 渐进首字节 + RetryReason + 吐字后不重试）
> 2. `perf(network)` OkHttp 栈调优（pingInterval 30s + retryOnConnectionFailure 显式化）
> 3. `feat(network)` 内置多线路（EndpointHealthRegistry + ModelEndpointRouter + 端点级故障转移）
> 4. `docs` 本文件 + AGENTS.md 教训行

## 1. 联网调研结论（2026-10）

| 议题 | 业界标准做法 | 本项目落地 |
|---|---|---|
| 哪些错该重试 | 429/Retry-After、408、5xx/529 重试；400/401/403/404 不重试 | `isRetriableNetworkError` 原有，本次保留并补 `RetryReason` 分类 |
| 退避算法 | Full jitter 指数退避（AWS 推荐）：`delay = random(0, min(cap, base·2^n))`，防惊群且期望延迟约降 50% | `backoffDelayMillis` 重写；旧 `exponentialDelayMillis` 保留兼容测试 |
| 重试预算 | 次数 + 单次封顶 + 累计上限三道闸，超限上抛交上层 | `RetryBudget(maxAttempts/maxDelayPerAttemptMs/maxTotalDelayMs)` |
| 服务端指令 | `Retry-After` 优先，但封顶防无限等待 | 封顶 60s 且计入总预算 |
| 连接保活 | OkHttp `pingInterval`（移动端 30s）是长连接半死的标准解法 | Commit 2 两个 client 均加 |
| 熔断 | 连续失败阈值（5 次/60s 量级）→ 开 30-60s → 半开探测 | `EndpointHealthRegistry`（3 次/60s） |
| 多端点 | 健康排序 + 自动故障转移，用户显式配置优先 | `ModelEndpointRouter`（Commit 3） |

## 2. 根因链（六环，全部有源码实证）

用户「网络很好仍报波动」不是单一 bug，是六环叠加：

1. **OkHttp 未配 `pingInterval`**（Commit 2 修）：HTTP/2 长连接在移动端 WiFi↔4G 切换 / NAT 超时后往往已半死，但双方都不知情。请求发到死连接上，要等 readTimeout（120s）或 TCP 重传超时才发现，期间 UI 显示「网络波动，正在重试」——**好网络也中招，这是用户直接体感的主因**。
2. **首字节 watchdog 固定 60s 耐心**（Commit 1 修）：把「服务端排队/慢思考」误杀成连接中断（底层表现为 `Software caused connection abort`）。误杀后重试还是 60s，于是一次次被砍，形成重试风暴。修复：渐进式耐心 60s→120s→180s，让慢但活着的服务端第二次成功。
3. **退避无 jitter**（Commit 1 修）：多会话/多客户端同步重试形成惊群，把刚恢复的服务再次打垮。修复：full jitter。
4. **无重试预算**（Commit 1 修）：总时长失控，最坏把一次故障放大成分钟级卡顿。修复：累计退避 15s 上限，超限上抛交上层故障转移。
5. **mid-stream 重试 bug**（Commit 1 修，真 bug）：`streamWithStaircaseRetry` 的 `attemptOnce { }` 从未消费 `onProduced` 标记——注释声称「吐字后不重试」，实现却整体重来。后果：**用户看到内容重复输出、token 双倍计费**（三个适配器全中）。
6. **UI 一律报「网络波动」**（Commit 1 修）：不区分连接不稳 / 服务端慢 / 限速，用户被误导去排查网络。修复：`RetryReason` 六分类 + 分档文案。

## 3. 修复架构

### 3.1 Commit 1：重试逻辑重写（`RetryPolicy.kt`）

- `RetryReason` 枚举 + `classifyRetryReason(t)` 纯函数：CONNECTION / SLOW_SERVER / RATE_LIMIT / SERVER_BUSY / STREAM_API / UNKNOWN。
- `RetryBudget`：三道闸；`nextDelayMs` Retry-After 优先（封顶 60s 计入预算）。
- `backoffDelayMillis`：full jitter，`[random]` 可注入测试。
- `progressiveFirstByteTimeoutMs(attempt, requestTimeoutSec)`：60s→120s→180s，且不低于用户配置与 60s 下限。
- `streamWithStaircaseRetry`：`attemptOnce(attempt, onProduced)`；吐字后失败直接上抛（不重试）。
- `retryStaircase` / `streamWithStaircaseRetry` 的 `onRetry` 增加 `reason` 参数，一路透传到 UI：
  `AIStreamChunk.Retrying(reason)` → `AgentEvent.Retrying(reason)` → `RetryState(reason)` → `RetryingBubble(reason)` → 分档字符串。
- 文案：连接不稳定 / 服务端响应慢 / 触发限速 / 服务端繁忙 / 服务端流式错误（zh + en），`chat_retrying` 保留作 UNKNOWN 兜底。

### 3.2 Commit 2：OkHttp 栈（`di/AgentModule.kt`）

direct / proxy 两个 client 均加：

```kotlin
.pingInterval(30, TimeUnit.SECONDS)          // HTTP/2 长连接保活探测
.retryOnConnectionFailure(true)              // 显式声明（OkHttp 默认 true）
```

### 3.3 Commit 3：内置多线路（`core/network/`）

- `EndpointHealthRegistry`（@Singleton 内存态）：按端点 URL 记录连续失败数 / EWMA 延迟 / 熔断截止。3 次连续失败 → 熔断 60s → 自动半开探测。纯内存，进程重启重置（健康是易逝运行时数据，持久化会把几分钟前的抽风变成永久错误决策）。
- `ModelEndpointRouter`：候选 = [用户配置 URL（永远首选）, 同类型官方 URL（用户配置非官方时，同一把 Key 直连官方，**不引入任何未验证第三方线路**）]；`useFullUrl=true` 时用户填的是完整请求地址，**不换线路，原样返回**；健康分排序（熔断=0，其余 1000 - 失败罚分 - 延迟罚分）。
- 接入点一：`ModelCallRouter.createStandaloneProvider` 的 `provider.baseUrl` 改选路结果（无健康数据时原序返回，存量行为零变化）。
- 接入点二：`StatefulAgentWorkflow` 流式 `while(true)` 环在既有 provider 级故障转移**之前**加端点级换线（首字节前失败 + CONNECTION 类 + ≤2 次）：换线路比换 provider 更轻（用户配置不变、Key 不变）。成功/失败均记账（`recordSuccess(baseUrl, 耗时)` / `recordFailure(baseUrl)`）。

## 4. 测试

- `RetryPolicyTest` 15→36 例：jitter 边界（ZeroRandom/CeilingRandom 钉死上下界）、预算三道闸、Retry-After 封顶与降级、渐进耐心、吐字后不重试（`thrown === failure` 且仅 1 次尝试）、原因分类全枚举。
- `EndpointHealthRegistryTest` 10 例：熔断开/半开/恢复、EWMA、排序、无样本原序。
- `ModelEndpointRouterTest` 12 例：候选生成、规范化去重、useFullUrl 直通、熔断改选、nextEndpoint 边界。

## 5. 实测与边界

- 四门禁（compileReleaseKotlin + testReleaseUnitTest + detekt + lintRelease）每个提交独立全绿；全量单测 1525 例 0 失败。
- **边界说明**：官方 URL 候选对「中转站 Key」用户会拿到 401（不可重试，立即失败回上层），不会形成重试风暴；这是用户端点连续 3 次失败后的降级尝试，可接受。
- **未做（明确不做）**：不引入任何第三方公共中转端点（无法验证其安全性与 Key 处理方式，属红线）；不做 DNS over HTTPS 替换（公共 DNS 兜底已由 `PublicDnsFallback` 覆盖）。

## 6. 教训（已沉淀 AGENTS.md）

1. OkHttp 长连接必须配 `pingInterval`：移动端网络切换后半死连接是「好网络仍报波动」的主因，不配 ping 的请求要等超时才暴露。
2. 「吐字后不重试」的注释必须由代码强制：`attemptOnce` 不消费 `onProduced` 标记时，注释等于谎言——流式重试=内容翻倍+双倍计费。
3. 固定耐心的 watchdog 会把「服务端慢思考」误杀成连接中断：渐进式耐心（快首死、重试加码）兼顾体验与正确性。
4. 重试必须有总预算：无预算的指数退试会把一次故障放大成分钟级卡顿，超限上抛交给上层故障转移才是正解。
