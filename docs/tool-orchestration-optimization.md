# 工具编排优化方案：快速 · 高效 · 省 Token · 思路清晰

> 日期：2026-10-18 ｜ 状态：方案待评审 ｜ 依据：开源项目公开实践调研 + 本地源码实证
>
> 遵循仓库红线：**渐进式增强，不做大爆炸式替换**。本方案分 5 个批次，每批独立门禁、独立可回滚。

---

## 0. TL;DR

**本地现状（源码实证）**：41 个工具在**所有模式下全量下发**（`ToolRegistry.getAvailableTools(mode)` 直接返回全部，PLAN 模式也不例外）；**未使用任何 prompt caching**（`AnthropicAdapter` 的 system 为单字符串、无 `cache_control`）；**无 observation masking**（`ContextCompactor` 只有 90% 阈值触发的 LLM 摘要一条路）；工具命名 4 种风格混杂（`Bash` / `readFile` / `websearch` / `job_start`）；`network_proxy` 单工具 384 字符、9 个 action。

**已具备的好底子**（不要重复造）：拓扑分层并行调度（`ToolDependencyScheduler` + workflow 层 `async/awaitAll`）、大输出溢出磁盘（`ToolOutputStore` head+tail 40K）、错误分类自动重试、system prompt 分 10+ Source 组装。

**路线图**（按「低风险纯收益 → 大杠杆需观察」排序）：

| 批次 | 主题 | 预期收益 | 风险 | 工作量 |
|---|---|---|---|---|
| A | 缓存前缀稳定化 + 描述瘦身 | 命中缓存后输入成本降 90%；描述 token -40% | 低 | 1-2 天 |
| C | 并行调用强化（nudge + 配额） | 轮次 -60%、延迟 5x（业界生产数据） | 低 | 1-2 天 |
| D | Observation masking 压缩层 | 历史段成本 -52%（JetBrains 实测） | 低-中 | 2-3 天 |
| B | 工具 deferred loading（最大杠杆） | 工具定义 token -85%；选错率下降 | 中 | 3-5 天 |
| E | 原子化拆分 + 命名治理 | 选择准确率、长期可维护 | 中 | 渐进 |

---

## 1. 开源调研结论

### 1.1 Claude Code：决策矩阵比工具数量更重要

Anthropic 五原则落成的工程决策（[besthub 深度分析](https://www.besthub.dev/articles/claude-code-applying-anthropic-s-5-principles-with-an-8-point-checklist-da2f412c1df0)、[iceyao 解析](https://www.iceyao.com.cn/post/2026-03-26-claude-code%E6%B7%B1%E5%BA%A6%E6%8A%80%E6%9C%AF%E8%A7%A3%E6%9E%90/)）：

- **高频稳定动作 → 原子化**（Read/Write/Edit/Glob/Grep/Bash 七个工具覆盖约 90% 日常任务）；**长尾 → Bash fallback**（不可能给每种命令造工具）；**知识注入 → Skill**（SKILL.md 是「菜单」不是「食谱」，description 聚焦 when-to-use）。
- 子 agent **跳过 CLAUDE.md**——临时工加载完整项目手册是纯浪费。
- 社区统计 ~60 个内部工具名，**大多数开发者实际只用 6 个**。

### 1.2 Anthropic 官方 advanced tool use（2025-11，最硬的一手数据）

[Anthropic 工程博客](https://www.anthropic.com/engineering/advanced-tool-use)：

- **Tool Search Tool（deferred loading）**：5 个 MCP server 共 58 个工具 ≈ **55K tokens**，全量前置；改用 `defer_loading: true` + 搜索工具按需展开后仅 **8.7K tokens（省 85%）**，且**不破坏 prompt caching**（deferred 工具完全不在初始 prompt 中）。准确率：Opus 4 从 49% → 74%，Opus 4.5 从 79.5% → 88.1%。Anthropic 内部实测工具定义最多吃掉 **134K tokens**。
- **Programmatic Tool Calling**：让 Claude 写代码编排 20+ 个工具调用，省掉 19 次推理往返；复杂研究任务 token 从 43,588 → 27,297（**省 37%**）。
- **Tool Use Examples**：JSON Schema 只表达「结构合法」，表达不了「使用模式」（何时带可选参数、什么组合合理）——这是纯 schema 工具描述的结构性盲区。
- 命名空间按服务分组（`github.*`、`slack.*`），搜索匹配 name/description/参数名。

### 1.3 MCP 官方：Progressive Discovery 三层模式

[MCP Client Best Practices](https://modelcontextprotocol.io/docs/2025-06-18/develop/clients/client-best-practices)：

- **Catalog（轻量搜索）→ Inspect（取单个工具完整定义）→ Execute**。工具定义占上下文 **1-5%** 即应切换渐进发现。
- 检索策略可选关键词（BM25/regex）、embedding、小模型 subagent、混合。

### 1.4 JetBrains：Observation Masking 是性价比之王

[JetBrains Research（2025-12）](https://blog.jetbrains.com/)，在 SWE-agent / OpenHands 上用 250 轮轨迹实测：

- **保留最近 10 轮完整，更早的工具输出替换为结构化占位符，保留完整的动作/推理骨架**：Qwen3-Coder 480B 上 **成本 -52%**，solve rate 反而 **+2.6%**，**零额外 LLM 计算**——纯 LLM 摘要成本接近但 runtime +15%（摘要把「什么时候该停」的信号也糊掉了）。
- 压缩安全层级：**激进压缩原始工具输出**（占典型会话 60-80% token，一次性消费）> 谨慎压缩推理痕迹 > **永不压缩**用户指令、活跃错误、待办状态、当前文件路径。
- 关键运维事实：**compaction 事件会摧毁已缓存的前缀**，压缩策略必须与缓存 TTL 对齐，否则省了 token 毁了缓存。

### 1.5 并行工具调用：便宜且巨大的延迟红利

- 生产实测（[Zylos](https://zylos.ai/research/2026-04-23-parallel-tool-calling-optimization-ai-agents)）：延迟 30+s → 6s（**~5x**），完成轮次 **-60%**，token **~-50%**。收益来自两部分：合并 N 次模型往返（batching）+ 工具并发执行（executor 并行）。
- 工程纪律（[technologyonthe.net](https://www.technologyonthe.net/2026/10/parallel-tool-calls-for-production-llm.html)）：只并行**真正独立**的调用；prompt 里要显式给模型「batching nudge + 多调用回合的示例」，否则模型默认一次只发一个；**设 batch 上限**（防一次 30 个调用打爆下游）；写操作串行；**每个 call ID 都必须有结果**（partial failure 时其余照常返回）。
- KV 缓存视角（[Utilix](https://www.utilix.tech/blog/kv-cache-reuse-agent-loop-latency)）：稳定内容（system + 工具目录）在前、易变内容（时间戳、本轮结果）在后，是缓存命中率最高的单点改动；**不要每轮 reshuffle/过滤工具列表**。

> **调研内部的张力及本地取舍**：Utilix 说「别按轮过滤工具列表」（会制造缓存未命中），Anthropic 说「deferred loading」。二者不矛盾——Anthropic 方案中 deferred 工具**完全不在初始 prompt**，搜索工具本身是稳定前缀，展开只发生在搜索命中之后。本地实现必须同样保证「核心工具 + catalog 工具」的前缀**逐字节稳定**，展开部分只追加在尾部（见 B2 设计约束）。

### 1.6 Cline / Roo：上下文收集与模式治理

- Cline 三层架构（[分析](http://www.hqwc.cn/a/374030.html)）：Context Collector 按优先级加权收集上下文（当前编辑器权重 10 / git diff 7 / 终端近 3 条 5）→ Prompt Orchestrator 用 AST 提取签名与调用关系、压缩无关代码 → Action Executor。实测上下文 token 比同类低 42%、理解准确率高 28%。
- Roo 的教训（[TokRepo](https://tokrepo.com/fr/workflows/roo-code-ai-coding-agent-with-custom-modes-baddacd0)）：**自定义模式从 2-3 个开始**，过多模式导致决策瘫痪；每个自定义 system prompt 都是每请求的固定税，保持精简。

### 1.7 成本结构常识（为什么值得做）

- 单次请求构成（[moltbook 实证](https://moltbook.com/post/975c10af-0d63-4d50-b4fd-5a7944379f95)）：system prompt + 工具定义 8-12K（稳定、可缓存）；工具结果每次 5-50K，**读 5 个文件可烧掉 200K 窗口的 81%**。
- 200K 窗口有效可用仅 140-160K；超 200K 后 lost-in-the-middle 造成 20-30% 性能退化。
- 成本随会话长度**四次方**增长（每轮重发全量历史），真正的成本单位是「整次 run」而非单次 prompt。

---

## 2. 本地现状清单（源码实证）

| # | 维度 | 现状 | 位置 |
|---|---|---|---|
| L1 | 工具数量 | 41 个，description 合计约 2,457 字符（不含 parameters schema） | `execution/tool/` |
| L2 | 下发策略 | **所有模式全量下发**，PLAN 模式也不例外（注释明示「让 AI 知道这些工具存在」） | `ToolRegistry.kt:36` |
| L3 | Prompt caching | **无**。`AnthropicAdapter` system 为单字符串、tools 无 `cache_control`；OpenAI/Gemini 适配器同样无缓存打点 | `core/provider/` |
| L4 | 并行执行 | **已有**：`ToolDependencyScheduler` 拓扑分层 + workflow `coroutineScope/async/awaitAll` | `StatefulAgentWorkflow.kt:1287-1321` |
| L5 | 输出截断 | **已有**：head+tail 各 20K 预览，超出溢出磁盘并回指针 | `ToolOutputStore.kt` |
| L6 | 历史压缩 | 仅 LLM 摘要一条路（90% 阈值触发），**无 observation masking** | `ContextCompactor.kt` |
| L7 | 工具命名 | 4 种风格：`Bash`（Pascal）、`readFile`/`switchMode`（camel）、`websearch`/`job_start`/`network_proxy`（snake 全小写） | 全库 |
| L8 | 巨型 action 工具 | `network_proxy` 384 字符、9 个 action；`device_storage` 4 action；`switch_container_arch` 198 字符 | 各工具文件 |
| L9 | 描述写法 | 部分含示例 JSON、注意事项、边界条件——「食谱」而非「菜单」 | `network_proxy` 等 |
| L10 | system prompt 组装 | 10+ Source 按 importance+order 混排，含当前时间等易变内容 | `SystemPromptProvider.kt` |
| L11 | SubAgent 工具面 | 白名单也是全量（`getAvailableTools(AgentMode.AUTO)`） | `SubAgentRunner.kt:165` |
| L12 | 命名空间 | 无分组前缀，41 个平铺名称 | `ToolRegistry` |

---

## 3. 差距矩阵

| 维度 | 业界实践 | 本地 | 差距 |
|---|---|---|---|
| 工具暴露 | 核心 6-10 常驻 + 其余 deferred | 41 全量、全模式 | **大** |
| Prompt caching | 稳定前缀 + cache 打点 | 无 | **大** |
| Observation masking | 旧输出占位符化（-52% 成本） | 无（只有 LLM 摘要） | **大** |
| 工具命名 | 命名空间一致 | 4 风格混杂 | 中 |
| 并行调用 | batching + nudge + cap | 执行层已有，模型侧无引导 | 中 |
| 描述风格 | when-to-use 菜单 | 部分食谱式 | 中 |
| action 粒度 | 原子化或 defer 低频 action | 巨型枚举工具 | 中 |

---

## 4. 优化方案（5 批次）

### 批次 A：缓存前缀稳定化 + 描述瘦身（低风险，先做）

**A1. Anthropic prompt caching 打点**
- 现状：L3。
- 方案：`AnthropicMessageRequest` 的 `system` 支持分段（text block 数组），对「静态规则 + 工具定义」末尾打 `cache_control: {"type": "ephemeral"}`；多工具时在 tools 数组打一个 breakpoint。OpenAI/Gemini 走其自动缓存（保持前缀稳定即受益）。
- 收益：多轮会话命中后输入 token 成本约降 90%（Anthropic Sonnet $3.00 → $0.30/MTok 量级）。
- 验收：AILogger 记录 `cached_tokens`；同一会话第 3 轮起 cache hit rate > 0。
- 注意：缓存 TTL 5 分钟，批次 D 的压缩事件会击穿缓存——D3 处理。

**A2. system prompt 易变内容后置**
- 现状：L10（当前时间等易变 Source 与静态规则混排）。
- 方案：`StepInjectionAssembler` 产出分两段——**稳定段**（静态规则、项目规则、技能清单）+ **易变段**（当前时间、workspace 路径、goal 提示、playbook 阶段）。稳定段在前（可缓存），易变段在后。
- 收益：缓存命中率提升；同时修复「timestamp 悄悄杀死缓存」的经典故障（[sombrainc 反模式清单](https://sombrainc.com/?p=8162)）。
- 验收：同一会话相邻两轮 system prompt 的前 N 字节（稳定段）完全一致。

**A3. Top 10 冗长工具描述重写为「菜单式」**
- 现状：L9。`network_proxy`（384）、`switchMode`（218）、`switch_container_arch`（198）、`Bash`（177）、`device_storage`（175）居前。
- 方案：规约=「何时用我 + 输入要点 + 失败时怎么办」；**示例 JSON、边界条件、注意事项移出 description**（示例进 A4 的 examples 字段或技能文档）。每条压到 80 字符以内为目标。
- 收益：描述 token 约 -40%，同时减少「食谱式描述」对模型自主性的干扰（Claude Code 设计观察：description 是菜单不是食谱）。
- 验收：脚本统计 Top 10 描述字符数下降 ≥40%；抽样人工评审无语义损失。

**A4. 工具命名规约落地（渐进，带 alias）**
- 现状：L7 四风格混杂。
- 方案：新规约统一 **snake_case**（与 MCP 生态一致）；存量工具**分批迁移 + 旧名 alias 过渡期**（`ToolRegistry` 支持 `registerAlias`，权限记忆模式、playbook 引用、MCP 适配同步兼容）。优先迁移高频工具（`Bash`→`bash` 等），每批门禁后推送。
- 风险：模型对旧名已有习惯、用户权限记忆含旧名——alias 机制保证双名可用，过渡 2-3 个版本后移除。
- 验收：新工具 100% 符合规约；存量工具迁移有 alias 覆盖测试。

### 批次 C：并行调用强化（低风险，与 A 并行做）

**C1. system prompt 增加 batching nudge**
- 方案：静态规则中增加明确指令——「相互独立的调用必须在**同一回合**全部发出；需要前序结果的调用必须等结果返回后再发」，并附一个多调用回合示例（[promptabcd 实证](https://promptabcd.com/blog/tool-call-batching-inside-the-loop)：没有 nudge，模型默认一次一个；给了示例后 6 次查询 12s → 3s）。
- 验收：统计每回合 tool_calls 均数（改造前基线 vs 改造后）。

**C2. batch 配额与写操作串行策略确认**
- 现状：L4 执行层已有拓扑分层；需确认 workflow 对「同层写操作」的串行化策略与 batch 上限。
- 方案：单回合上限 8 个调用（超出分 chunk）；同层含写操作能力（WRITE_WORKSPACE/EXECUTE_COMMANDS）时降为串行；每个 call ID 保证有结果（partial failure 语义）。
- 验收：新增测试覆盖「一批中含失败写 + 后续依赖写被标记 skipped」场景。

### 批次 D：Observation Masking 压缩层（低-中风险）

**D1. 新增 masking 层（优先于 LLM 摘要触发）**
- 现状：L6。
- 方案：三级级联 **L1 截断（已有 ToolOutputStore）→ L2 masking（新增）→ L3 LLM 摘要（已有）**。masking 规则：超出最近 10 轮的工具输出，在**历史消息组装时**替换为占位符 `[输出已归档：路径 X，大小 N 字符，可用 readFile/read_output 按需取回]`；动作（tool_use）与推理骨架保留。
- 收益：历史段 token -52%（JetBrains 实测），零额外 LLM 调用。
- 红线遵守：**永不 mask** 用户指令、活跃错误、todo 状态、当前文件路径（压缩安全层级）。

**D2. 压缩与缓存 TTL 对齐**
- 问题：masking/摘要会改写历史消息 → 击穿缓存前缀（[moltbook](https://moltbook.com/post/975c10af-0d63-4d50-b4fd-5a7944379f95)：每次 compaction 都触发全价重读）。
- 方案：压缩事件后，下一轮请求**先重建稳定前缀再追加**，并把「刚发生过压缩」的会话标记为短窗口（避免压缩后 5 分钟内再次压缩造成连续击穿）。
- 验收：监控压缩频率与 cache hit rate 的联合曲线。

### 批次 B：工具 Deferred Loading（最大杠杆，需观察期）

**B1. 工具分组元数据**
- 方案：`AgentTool` 增加 `exposure` 属性：`CORE`（默认常驻，约 10 个：readFile/writeFile/editFile/search/bash/todo/plan/askUser 等高频稳定动作）与 `DEFERRED`（其余 31 个：浏览器、git、job、playbook、schedule、image、storage、proxy 等场景化工具）。`ToolRegistry.getAvailableTools` 语义保持（能力中心等 UI 仍可见全量）。

**B2. catalog 元工具（本地版 Tool Search）**
- 方案：新增 `tool_catalog` 工具（~500 token 描述，常驻）：入参为自然语言 query，返回**匹配工具名 + 一行描述**（Layer 1 Catalog）；命中后 adapter 在**下一轮**把该工具的完整定义追加进 tools 数组尾部（Layer 2 Inspect）；模型随后正常调用（Layer 3 Execute）。检索用 BM25/关键词（name+description+参数名匹配，Anthropic 官方指出搜索匹配这些字段）。
- **设计硬约束**（化解 1.5 节的调研张力）：核心工具 + catalog 的前缀**逐字节稳定**，展开只追加在 tools 数组尾部；每轮展开集合按「最近 N 轮命中」累积，避免同一工具反复展开导致的列表抖动。
- 收益：单轮工具定义 token 从 ~4-6K 降至 ~1-1.5K（-85%，Anthropic 数据）；选错率下降（49%→74% 量级）。
- 观察期：灰度开关（设置项），对比同任务 token 消耗与完成率，1-2 个版本后默认开启。

**B3. MCP 工具同步 defer**
- 现状：MCP 动态工具目前与内置工具同待遇。
- 方案：MCP 工具默认 `DEFERRED`；`ManageMcpTool` 的「常用工具」可标记为 CORE。

**B4. SubAgent 工具面收窄**
- 现状：L11。
- 方案：SubAgent 默认 CORE-only + catalog（对齐 Claude Code 子 agent 的精简工具面与跳过系统手册思路）。

### 批次 E：原子化与命名空间治理（长期渐进）

- **E1. 巨型 action 工具拆分或 defer**：`network_proxy` 9 个 action——高频 2-3 个（status/use/release）保留内联，其余走 deferred；或按 Claude Code 决策矩阵拆为原子工具（长尾交给 Bash fallback）。同类：`device_storage`、`switch_container_arch`。
- **E2. 命名空间分组**：工具名加分组前缀（`browser_*`、`git_*`、`job_*`、`net_*`），与 Anthropic「按服务分组、单查询命中一组」实践一致；借 A4 的 alias 机制分批迁移。
- **E3. Tool Use Examples 字段**：`AgentTool` 增加 `examples: List<String>`（调用示例），随 deferred 展开下发——弥补 schema 表达不了使用模式的盲区（Anthropic 1.2 节）。
- **E4. 能力中心 UI 分组**：`ToolUiModel` 按分组展示，用户可视角按分组开关 DEFERRED 工具的自动展开偏好。

---

## 5. 度量方案（没有度量就没有优化）

| 指标 | 采集点 | 基线（改造前） | 目标 |
|---|---|---|---|
| 单轮工具定义 token | adapter 序列化后估算 | ~4-6K/轮 | B 后 ≤1.5K |
| 缓存命中 token 占比 | Anthropic `usage.cached_tokens` / AILogger | 0% | ≥60%（多轮会话） |
| 工具选错率 | `TOOL_NOT_FOUND` + `INVALID_ARGS` 占比 | 待测基线 | -30% |
| 每任务回合数 / 墙钟时长 | workflow 统计 | 待测基线 | 回合 -40% |
| 任务完成率（金丝雀） | 同任务集对照 | 基线 | 不下降 |
| 上下文利用率 | `estimateTokens` / context limit | — | 稳定在 30-40%（业界建议区间） |

---

## 6. 落地顺序与风险控制

1. **顺序**：A → C → D → B → E。A/C/D 是低风险纯收益且互相独立；B 是最大杠杆但改变模型可见面，必须带灰度开关与观察期；E 长期渐进。
2. **每批独立门禁**：四门禁全绿 + 对应单测（缓存打点、masking 占位符、catalog 检索、alias 双名）后提交推送，遵守仓库提交纪律。
3. **回滚**：每批一个设置开关或一个 commit 边界，异常时单批回退，不影响其他批。
4. **红线遵守**：不替换 `ToolRegistry`/workflow 既有架构；所有改动是增量元数据与增量层（alias、exposure、catalog、masking），存量路径默认行为不变。

---

## 7. 参考来源

- [Anthropic — Introducing advanced tool use（Tool Search / PTC / Tool Use Examples）](https://www.anthropic.com/engineering/advanced-tool-use)
- [MCP 官方 — Client Best Practices（Progressive Discovery 三层模式）](https://modelcontextprotocol.io/docs/2025-06-18/develop/clients/client-best-practices)
- [JetBrains Research — Cutting Through the Noise（Observation Masking）](https://blog.jetbrains.com/)
- [Claude Code 五原则落地分析（决策矩阵 / Skill 渐进披露）](https://www.besthub.dev/articles/claude-code-applying-anthropic-s-5-principles-with-an-8-point-checklist-da2f412c1df0)
- [上下文工程指南（精选 3-5 工具 / 命名空间 / 描述即性能杠杆）](https://www.heyuan110.com/zh/posts/ai/2026-03-10-context-engineering-guide)
- [Token Optimization: A Systems Guide（volatile prefix / global tool registry 反模式）](https://sombrainc.com/?p=8162)
- [KV Cache Reuse and Agent Loop Latency（稳定前缀 / 批量调用）](https://www.utilix.tech/blog/kv-cache-reuse-agent-loop-latency)
- [Tool Call Batching Inside the Loop（nudge 的实证）](https://promptabcd.com/blog/tool-call-batching-inside-the-loop)
- [Parallel Tool Calls for Production Agents（partial failure / batch cap）](https://www.technologyonthe.net/2026/10/parallel-tool-calls-for-production-llm.html)
- [Zylos — Parallel Tool Calling Optimization（生产数据：5x / -60% / -50%）](https://zylos.ai/research/2026-04-23-parallel-tool-calling-optimization-ai-agents)
- [Why Your Coding Agent Fails With Too Many Tools（172 工具 141K vs 2.6K tokens）](https://www.milik.ai/articles/230d906c66)
- [Context Windows Are RAM, Not Storage（工具结果占 81% 的实证）](https://moltbook.com/post/975c10af-0d63-4d50-b4fd-5a7944379f95)
- [Cline 三层架构分析（Context Collector 权重 / AST 压缩）](http://www.hqwc.cn/a/374030.html)
- [Roo Code 自定义模式（2-3 个模式起 / 每模式都是固定税）](https://tokrepo.com/fr/workflows/roo-code-ai-coding-agent-with-custom-modes-baddacd0)
