# MiniMe-core 0.0.0.7 Version Log

> UI 统一规范化版本：建立完整的 Design Token 体系（Primitive + Semantic），新增 9 个基础组件库，逐模块替换硬编码间距/圆角，新增主题设置页（6 套主题预设 + 9 项颜色自定义 + 背景图 + 显示偏好 + WCAG 对比度警告），支持外观模式切换和实时预览。

**发布日期**：2026-09-23

### 新功能

- Design Token 基础设施：PrimitiveColors（8色系×10色阶）、PrimitiveSpacing/Radius/Elevation/Alpha、SemanticColors（50个语义色字段，亮/暗双模式）、AppThemeState + LocalAppTheme，现有调用点零改动
- 基础组件库：AppCard（4变体）、AppListItem、AppChip（3变体×6色）、AppSectionHeader、AppDivider、AppEmptyState、AppButton（4变体×5色×3尺寸）、AppTopAppBar（API兼容）、AppBadge/AppStatusDot，每个组件含 @Preview
- 主题设置页第一期：外观模式（AUTO/LIGHT/DARK）、6 套主题预设（Default/Cyber/Sunset/Forest/Ocean/Mono）、实时聊天预览、KVStore 持久化
- 主题设置页第二期：9 项可自定义颜色（主色/背景/卡片/工具块/文字/边框/错误/成功）、WCAG 对比度警告（<4.5:1 提示）、背景图（系统 Photo Picker + 遮罩浓度 + 卡片透明度）、显示偏好（圆角风格/字体大小/动效强度）、恢复出厂主题

### 改进

- 逐模块硬编码 Token 化：agent/settings/terminal/git/proxy/workspace/capability/core/theme 共 33 个文件，~142 处间距/圆角替换为 Spacing/Radius Token，视觉零变化
- AboutSection 试点：9 个颜色字段替换为 Semantic Token 引用，视觉零变化

### 修复

- 主题切换时 Material3 组件颜色同步：自定义 SemanticColors 动态映射到 Material ColorScheme，MaterialTheme.colorScheme 组件自动跟随主题变化
