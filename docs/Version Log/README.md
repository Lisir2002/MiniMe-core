# Version Log

本目录存放 MiniMe-core 项目的所有版本日志。

## 目录结构

```
Version Log/
├── CHANGELOG.md                    # 主应用（MiniMe-core）版本日志（所有版本汇总，倒序排列）
├── CHANGELOG-qbot.md               # 附属应用（QQ 机器人）版本日志
├── CHANGELOG-qbot-injector.md      # 附属应用（QBot 环境注入器）版本日志
├── MiniMe-core v-Logs/             # 主应用各版本独立日志
│   ├── MiniMe-core 0.0.0.01 Version Log.md
│   ├── MiniMe-core 0.0.0.02 Version Log.md
│   └── ...
└── MiniMe-Logs v-Logs/             # 附属应用（日志查看器）各版本独立日志
    ├── MiniMe-Logs 0.0.01 Version Log.md
    ├── MiniMe-Logs 0.0.02 Version Log.md
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

## 绑定发版（强制）

**MiniMe-QBot（`qbot-v*`）与 MiniMe-QBot 环境注入器（`qbot-injector-v*`）为绑定应用**：二者共享同一套运行环境资产，不可单独升级。

- **每次发版必须同批一起发布两款最新版**，即使其中一方当次无改动，也要一并重新打 Tag 发布。
- 两份版本日志（`CHANGELOG-qbot.md` 与 `CHANGELOG-qbot-injector.md`）须同批一并更新，并在各自头部互相交叉引用。
- 两枚 Tag 必须打在**同一个提交**上，一次 push 一并推送；CI 由**同一个工作流**（`.github/workflows/qbot-release.yml`）一次抓取资产、生成清单并同批构建两款 APK，推 `qbot-v*` 触发，缺少配对的 `qbot-injector-v*` Tag 时配对门禁失败。

## 发版流程

1. 代码变更完成并通过编译验证
2. 更新 `CHANGELOG.md`，新增版本条目（遵循 AGENTS.md 格式规范）
3. 在对应软件的 `v-Logs/` 目录下创建独立版本日志文档（文件名用补零版本号，内容与 CHANGELOG 一致）
4. commit 并 push 到 main
5. 打 tag 触发云端 CI 构建
6. CI 构建成功后，更新 GitHub Release 标题和正文
7. 运行 `python3 scripts/gitops/check-release-format.py --tag {tag}` 校验格式
8. 校验通过后，发版完成
