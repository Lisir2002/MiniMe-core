# Version Log

本目录存放 MiniMe-core 项目的所有版本日志。

## 目录结构

```
Version Log/
├── CHANGELOG.md                           # 主应用（MiniMe-core）版本日志（所有版本汇总，倒序排列）
├── CHANGELOG-qbot.md                      # 附属应用（MiniMe-QBot）版本日志
├── CHANGELOG-qbot-injector.md             # 附属应用（MiniMe-QBot 环境注入器）版本日志
├── MiniMe-core v-Logs/                    # 主应用各版本独立日志
│   ├── MiniMe-core 0.0.0.01 Version Log.md
│   └── ...
├── MiniMe-Logs v-Logs/                    # 附属应用（MiniMe Logs）各版本独立日志
│   ├── MiniMe-Logs 0.0.01 Version Log.md
│   └── ...
├── MiniMe-QBot v-Logs/                    # 附属应用（MiniMe-QBot）各版本独立日志
│   ├── MiniMe-QBot 0.0.01-rc1 Version Log.md
│   └── ...
└── MiniMe-QBot Injector v-Logs/           # 附属应用（MiniMe-QBot 环境注入器）各版本独立日志
    ├── MiniMe-QBot Injector 0.0.01 Version Log.md
    └── ...
```

## 命名规范

### 文件名版本号（补零格式，仅用于文件命名）

- 文档命名：`{软件名} {补零版本号} Version Log.md`
- 补零规则：每段至少两位，不足补零
  - 主应用四段：`0.0.0.01`、`0.0.0.10`、`0.0.0.17`
  - 附属应用三段：`0.0.01`、`0.0.07`
- 示例：`MiniMe-core 0.0.0.17 Version Log.md`、`MiniMe-Logs 0.0.07 Version Log.md`
- 补零格式仅用于文件命名，方便文件管理器按名称正确排序

### 软件版本号（原格式，用于文档内容与实际发版）

- 文档内容中的版本号、Tag、APK 命名均使用原格式，不补零
- 主应用：`0.0.0.1`、`0.0.0.17`
- 附属应用：`0.0.1`、`0.0.7`
- Tag 命名：主应用 `v{版本号}`（如 `v0.0.0.17`）；附属应用 MiniMe Logs 用 `logviewer-v{版本号}`（如 `logviewer-v0.0.7`），MiniMe-QBot 用 `qbot-v{版本号}`（如 `qbot-v0.0.1`），QBot 环境注入器用 `qbot-injector-v{版本号}`（如 `qbot-injector-v0.0.1`）

## 格式规范

**所有版本日志的格式规范统一在 [AGENTS.md](../../AGENTS.md) 中定义**（见「发版规范」章节），包括：
- 文档结构与分类顺序
- 条目格式（4-9字小标题 + 20-40字说明）
- 禁止 emoji
- 仅记录用户可感知的应用程序变更
- 内部重构笼统描述
- 用户可感知边界判定

本目录下所有文档（CHANGELOG.md、各独立版本文档）以及 GitHub Release 正文必须严格遵循 AGENTS.md 中的格式规范，保持三处一致。

## 独立发版

**MiniMe-QBot（`qbot-v*`）与 MiniMe-QBot 环境注入器（`qbot-injector-v*`）各自独立发版**：谁有更新就推谁的 Tag，只发该应用。

- 两份版本日志（`CHANGELOG-qbot.md` 与 `CHANGELOG-qbot-injector.md`）各自更新，**不要求同批**。
- 两侧运行环境资源的兼容由**注入包契约号**（注入器写入注入包的 `manifest.json` 中的 `contractVersion`）保证：注入器仅更新环境资源（内容变化、契约号不变）时，旧版 QBot 可直接使用新注入包，无需同步发版。
- 仅当**资产种类 / 目录布局变化**（契约号 +1）时，才需 QBot 一并发版。
- CI 由同一个工作流（`.github/workflows/qbot-release.yml`）按 Tag 前缀分流：推 `qbot-v*` 只构建发布 QBot，推 `qbot-injector-v*` 只构建发布注入器（仅注入器需抓取体积资产）。

## 发版流程

1. 代码变更完成并通过编译验证
2. 更新 `CHANGELOG.md`，新增版本条目（遵循 AGENTS.md 格式规范）
3. 在对应软件的 `v-Logs/` 目录下创建独立版本日志文档（文件名用补零版本号，内容与 CHANGELOG 一致）
4. commit 并 push 到 main
5. 打 tag 触发云端 CI 构建
6. CI 构建成功后，更新 GitHub Release 标题和正文
7. 运行 `python3 scripts/gitops/check-release-format.py --tag {tag}` 校验格式
8. 校验通过后，发版完成
