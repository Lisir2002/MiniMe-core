# MiniMe-core 0.0.0.9 Version Log

> 令牌体系完善与搜索升级版本：全量落地第三层 Component Tokens（13 种组件令牌 + 动态圆角/字号/粗细映射），非标圆角值现在跟随圆角风格切换，顶栏颜色跟随主题预设，设置搜索功能视觉与交互全面升级，全量审计修复 36 处令牌遗漏。

**发布日期**：2026-09-23

### 改进

- 顶栏颜色跟随主题预设变化：顶栏背景从固定卡片白改为页面底色，切换 6 套主题预设（Default/Cyber/Sunset/Forest/Ocean/Mono）后顶栏与页面融为一体，全局所有页面顶栏同步生效
- 设置搜索视觉与交互升级：搜索框聚焦时品牌色边框与光标、顶栏展开/收起平滑动画（跟随动效强度）、实时结果计数（"找到 N 项"）、搜索历史持久化（KVStore，最多 10 条，可清空）、空结果友好提示、结果按设置分组展示、输入框一键清空按钮、键盘搜索键支持、点击结果自动退出搜索模式
- 全量令牌体系完善：新增第三层 Component Tokens（13 种组件令牌，覆盖卡片/文字/按钮/输入框/列表项/标签/顶栏/分割线/徽章/空状态/对话框/工具卡/气泡），新增 LocalCornerRadius 动态圆角（Sharp/Rounded/Pill 三档），圆角/字号/字体粗细/阴影/内边距大规模令牌化替换

### 修复

- 非标圆角值现在跟随圆角风格切换：18dp/24dp/28dp/3dp/2dp/20dp 等非标圆角通过 CornerScale.map() 动态映射，Sharp 模式全变直角、Pill 模式全变胶囊、Rounded 模式保持原值，CircleShape 保持圆形不变
- 全量审计修复 36 处令牌遗漏：输入栏 ❯ 符号/光标/工具栏图标色改为主题色、TaskAccordion/FileDiffSheet 的 diff 三色（增/改/删）改为语义色、环境状态徽章改为 success/error、备份状态横幅改为 errorContainer/successContainer/warningContainer、代理状态色改为语义色、TaskCard 阴影改为 PrimitiveElevation
