# 上下文压缩与「网络不稳定」深度审计

- 日期：2026-10-19
- 触发：用户实测——某中文对话「上下文满了压缩几次」后，每次提问都报网络不稳定（重试/中断），新对话正常
- 结论：**根因为「压缩死锁」**——估算器低估中文 4 倍 + 压缩请求无预算上限 + 失败静默冒充成功，三者叠加使会话永远卡在超限载荷状态。本报告记录实证数据、根因链与已交付修复。

---

## 1. 症状与复现

| 维度 | 压缩过的会话 | 新对话 |
|---|---|---|
| 每轮请求载荷 | 超限（数倍于模型窗口） | 正常 |
| 供应商响应 | 超时 / 连接重置 / 5xx（网关行为各异） | 正常 |
| 应用层表现 | 重试风暴（最多 6 次）→「网络波动，正在重试」→ 失败 | 正常 |
| /compress  UI | 显示「压缩中 → 完成」 | — |

用户侧只看到「网络不稳定」，但网络本身没有问题——**是每个请求的载荷从一开始就注定被拒绝**。

## 2. 实证数据（回归测试实测，非推断）

构造 40 回合中文会话（641,440 字，CJK 口径 ≈ 64 万 token；默认模型窗口 128k，即 **5 倍超限**）：

| 指标 | 修复前 | 修复后 |
|---|---|---|
| 生产 token 估算（chars/4） | 160,360 | — |
| CJK 真实 token | ≈ 641,440 | ≈ 641,440 |
| 压缩请求 prompt 规模 | **622,705 tokens（超限 4.9 倍，被 API 拒绝）** | 分 10 次窗口调用，每次 ≤ 74,061（窗口内） |
| 压缩结果 | 静默返回原列表（一条没少） | 成功，列表变短 |
| 事件流 | `CompactionStarted, CompactionFinished`（**假报完成**） | `CompactionStarted, CompactionFinished`（真实完成） |
| DB 变化 | 零（无摘要行、无 compacted 标记） | 1 条生效摘要，head 全部标记 |
| 下一轮请求体 | 641,440 字（仍超限） | 8,107 字（有界） |

回归测试：`CompactionOversizedHeadTest`（3 例）、`CompactedSessionPayloadTest`（1 例）、`ModelContextPolicyTest`（4 例），全部绿。

## 3. 根因链（四环相扣）

### 环 1：token 估算按英文口径，中文低估约 4 倍

`ModelContextPolicy.estimateTokens(chars) = ceil(chars/4)`。主流 tokenizer 对中文约 **1 字 ≈ 1 token**（0.7~1.2）。后果：

- 压缩触发阈值（上下文 90%）按错的尺度计算——真实载荷已是窗口数倍时，应用还以为「未到阈值」；
- 尾随保留预算（`preserveRecentTokens`）同样失真，tail 保留量按中文算超编 4 倍。

### 环 2：压缩请求把整个 head 无预算拼进一条 user 消息

`ContextCompactor` 旧实现：`headContent = head.joinToString(...)` 全量拼进单次 `complete()` 调用。**会话本身多大，压缩请求就多大**——超限会话的压缩请求必然同样超限，被 API 以 `context_length_exceeded` 拒绝。这是死锁的核心：越需要压缩的时候，压缩越不可能成功。

### 环 3：失败被静默吞掉，还冒充成功

旧 catch 块：`FileLogger.e(...)` → `onEvent(CompactionFinished)` → `return messages.toList()`。UI 显示「压缩完成」，DB 分毫未动，用户以为压缩过了。且失败**不记冷却**，而自动压缩每轮 `CallLlm` 前都会触发——每轮都重试一次注定失败的压缩，叠加主请求的超时重试，故障被放大。

### 环 4：超限载荷触发重试风暴，表现为「网络不稳定」

`isRetriableNetworkError` 把 IOException / SocketTimeout / 5xx / 429 全部判为可重试（`MAX_NETWORK_RETRIES = 6`，首字节 watchdog 60s）。超限请求在供应商/网关侧的表现（排队超时、连接重置、网关 5xx）正好落进这些类别 → 用户看到「网络波动，正在重试 x/y」六轮后失败。新对话载荷小，不触发以上任何一环，故「新对话就没问题」。

## 4. 已交付修复（2026-10-19，四门禁全绿后提交）

| # | 修复 | 位置 | 性质 |
|---|---|---|---|
| 1 | **CJK-aware token 估算**：CJK 字符每字 1 token，其余维持 chars/4 | `ModelContextPolicy.estimateTokens(text)` | 触发阈值与 tail 预算改用同一把正确的尺子 |
| 2 | **滚动窗口式压缩**：head 超过压缩模型输入预算（上限 × 0.5）时，从旧到新逐窗调用压缩引擎合并，每次 prompt 恒定有界；预算内仍走原单次路径 | `ContextCompactor.summarizeHead / callSummary` | 打破死锁——压缩请求永不超限 |
| 3 | **失败显式化**：新增 `AgentEvent.CompactionFailed(reason)`，失败时 emit 它而**不再**冒充 `CompactionFinished`；空摘要同样按失败处理；失败记冷却（防每轮重试砸死） | `ContextCompactor`、`AgentWorkflow`、`AIAgentViewModel`（手动 /compress 落「压缩失败: 原因」消息） | UI/日志可区分成功与失败 |
| 4 | 病态大消息防御：单条消息即超预算时截断入窗 | `summarizeHead` | 防单条巨型消息击穿窗口 |

## 5. 后续优化方向（未实现，按优先级）

1. **P1 请求前硬护栏**：主请求发送前按 CJK 口径估算，仍超限时先降级（丢弃最旧的非摘要轮 / 截断超大工具输出）并提示用户，而不是发送注定被拒的载荷。当前依赖压缩成功兜底，压缩失败 + 载荷超限的组合仍会走到重试风暴。
2. **P1 窗口并行化**：多窗并发摘要 + 最终合并（当前顺序执行，10 窗 ≈ 10 次串行调用）。需评估供应商限速，建议并发 ≤ 3。
3. **P2 上下文超限错误专属文案**：HTTP 400 `context_length_exceeded` 目前归类为 UNKNOWN（不重试但文案不友好），应映射为「上下文超限，请压缩或新开会话」并引导 /compress。
4. **P2 压缩成本可观测**：窗口化后压缩从 1 次调用变为 N 次，用量卡片应按 taskId 展示压缩本身的 token 消耗。
5. **P3 锚定摘要体积上限**：摘要经多轮合并后仍可能缓慢膨胀，可在 `buildSummaryPrompt` 侧对 previousSummary 设上限并触发「再压缩一级」。

## 6. 复现与验证命令

```bash
# 死锁回归（修复后契约：超限会话压缩必须成功、每窗在窗口内、请求体有界）
./gradlew :app:testReleaseUnitTest --tests "com.mini.me_core.feature.agent.domain.execution.workflow.CompactionOversizedHeadTest"

# 失败路径（供应商持续故障 → CompactionFailed + DB 零变化）、空摘要按失败
# （同上一个类的另外两个用例）

# 估算器口径
./gradlew :app:testReleaseUnitTest --tests "com.mini.me_core.feature.settings.domain.model.ModelContextPolicyTest"
```

## 7. 教训（已同步 AGENTS.md 常见坑）

> **压缩/摘要类请求必须自带输入预算**：被压缩对象多大，摘要请求就多大的设计，会在最需要压缩时（会话已超限）必然失败。凡「对超限数据做归约」的链路，先给归约请求设预算（必要时逐段归约），并确认失败路径**不冒充成功**。
