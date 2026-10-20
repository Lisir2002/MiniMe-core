# EXP-002：detekt baseline 突然失配——改 init 块结构导致存量抑制失效

> 一句话：没引入新问题，CI 的 detekt 门禁却报了 baseline 里早已登记的老问题——因为 finding 签名变了，存量抑制失配。

| 元数据 | 值 |
|---|---|
| 状态 | 活跃 |
| 严重度 | 主要 |
| 分类 | CI / 质量门禁 |
| 创建日期 | 2026-10-20 |
| 最后验证 | 2026-10-20（结构性修复后 main CI 全绿：run 39380954233） |
| 创建人 | AI 协同会话（维护者确认） |
| 关联 | commit `ef94be0`；`McpServerManager.kt` allowLan 功能 |

## 1. 问题现象

安全修复推送后，main 的 `ci.yml` 在 detekt 硬门禁失败（编译/单测/lint 均过）：

```
McpServerManager.kt:81:5: initializer blocks should be declared
before method declarations. [ClassOrdering]
```

该 ClassOrdering 问题**早已在 `config/detekt/baseline.xml` 登记**，本次改动只是往这个类里加了 allowLan 加载逻辑，并没有新写违反规则的代码。

## 2. 根因分析

detekt baseline 的抑制条目按 **finding 签名**匹配，签名包含：规则 ID + 文件路径 + **签名文本**。ClassOrdering 的签名文本含 init 块的全文。本次把 allowLan 加载逻辑写进 init 块 → init 块文本变化 → 签名失配 → baseline 里的存量抑制条目匹配不上 → 被压制的老问题重新暴露。

**关键认知：baseline 抑制的不是"位置"，是"内容指纹"。改类成员布局（尤其 init 块）等于可能让一批存量抑制静默失效。**

## 3. 解决方案

按优先级从高到低，**结构性修复永远优先于更新 baseline**：

1. **结构性修复（本次采用）**：把 init 块整体前移到所有方法声明之前，从结构上满足 ClassOrdering。init 在对象构造期执行，前移不改运行时行为。
2. 确认是误报且无法结构性满足时，才更新 baseline：`./gradlew detektBaseline` 重新生成登记。
3. **禁止**为了让门禁转绿而删规则、降严重级或调大 MaxIssues——那是把报警器拆了。

## 4. 验证方法

- 本地能跑 detekt 时：`./gradlew :app:detekt` 0 issue。
- 本地无构建环境时（本项目沙箱常态）：推送后盯云端 `ci.yml` 的 detekt 步骤转绿（见 [EXP-004](./EXP-004-verification-without-local-build.md)）。

## 5. 预防与规则沉淀

- **改动 init 块前先想 baseline 影响**：往已有类里加 init 逻辑时，优先用构造参数/懒加载/`by lazy` 等不碰 init 块位置的写法；必须写 init 块时，顺手确认它是否已在类头部。
- **推送前自检清单**（AI 协同会话固化）：改了任何类的成员顺序/init 块 → 在提交信息或自查清单里标注"可能影响 detekt baseline 签名"。
- 本条已纳入 [AGENTS.md 常见坑](../..//AGENTS.md#常见坑)与 CI 失败处置流程的关联经验。

## 6. 更新记录（追加式，只增不改）

| 日期 | 更新内容 | 触发事件 |
|---|---|---|
| 2026-10-20 | 初版 | v0.0.1.10 源码返工：allowLan 加载进 init 块致 baseline 失配，CI 门禁二次拦截 |
