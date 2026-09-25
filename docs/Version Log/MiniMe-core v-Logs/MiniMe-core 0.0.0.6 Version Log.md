# MiniMe-core 0.0.0.6 Version Log

> CI 全链路修复 + 工具卡片样式统一版本：修复 Android Release CI 的 bash 语法错误确保全量构建通过，统一对话流中所有工具调用卡片的渲染样式（消除 `TOOL→REASONING→REPLY` 序列下的兜底简洁样式）。本版本同时清理了非标准版本号 `manual-116`（手动触发 CI 时误创建的 Release，其 CI 修复变更已合并入本版本）。

**发布日期**：2026-09-22

### 修复

- 修复 `android-release.yml` 第 180 行 `declare -A` 关联数组语法错误：元素分隔符从分号 `;` 改为空格，bash 严格模式下不再报 `syntax error near unexpected token`，Android Release CI 全量构建（assembleRelease + 签名 + 上传 APK）现已通过。
- 统一工具卡片渲染样式：修复 `TOOL→REASONING→REPLY` 消息序列时，工具调用因 REASONING 触发 `flushPendingTools()` 而走兜底独立 TOOL 单元（绿色圆点 + usage 简洁样式）的问题。现在 REASONING 不再触发 flush，暂存的工具调用作为独立 `EmbeddedToolAccordion` 渲染单元放在思考过程之前，所有工具卡片统一使用带标题（"N 次工具调用"）+ 锤子图标的样式，渲染顺序保持不变（工具调用 → 思考过程 → AI 文本）。
