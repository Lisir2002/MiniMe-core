# MiniMe-core 0.0.0.4 Version Log

> 混合模式对话流样式打磨 + CI 修复版本：以 Cursor/Claude 设计经验为参考，将对话流重构为「用户品牌色气泡 + AI 无气泡左侧竖线 + 工具独立暗色块」的混合模式，同步修复 CI 全部失败（YAML 语法错误 + android-actions 不兼容），CI 现已全绿。

**发布日期**：2026-09-22

### 改进

- 对话流混合模式样式打磨：用户消息轻量品牌色气泡（#3B82F6，右下小圆角指向用户），AI 回复去掉气泡改左侧 2dp 蓝色竖线标识，工具调用独立暗色块（等宽字体 + ANSI 颜色编码：命令绿/输出灰/错误红），思考过程默认折叠条，流量统计轻量化（10sp 右对齐弱化色），去掉 TaskAccordion 外层浅绿/浅蓝容器背景。
- 暗色/亮色模式配色统一调整：暗色页面底 #0F172A、AI 文字 #E2E8F0、工具块 #1E293B；亮色页面底 #F8FAFC、AI 文字 #0F172A、工具块 #F1F5F9；文字对比度均 ≥4.5:1。
- 输入栏视觉升级：圆角 20dp、左侧蓝色 ❯ 符号、BUILD 按钮改为 28dp 圆形品牌色图标按钮、工具栏图标统一 20dp 弱化色。
- Markdown 渲染样式调整：代码块背景 #1E293B、引用块 2dp 蓝色竖线、行内代码浅灰底，适配 AI 无气泡新样式。

### 修复

- 修复 CI 全部失败：根因是 `ci.yml` 第 69 行 `name: Security gate: CipherDriverFactory not stub` 未加引号，冒号+空格被严格 YAML 解析器当作嵌套 mapping，导致 workflow 解析立即失败（零 job 创建）。同时替换 `android-actions/setup-android@v3`（与 GitHub Runner Node 24 不兼容），改用 ubuntu-latest 预装 SDK + sdkmanager 手动补装方案。受影响文件：ci.yml / android-release.yml / dependency-audit.yml / weekly-health-check.yml。CI 现已全绿（Build release + Run unit tests 全部通过）。
