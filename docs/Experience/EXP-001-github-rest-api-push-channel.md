# EXP-001：沙箱网络阻断下的 GitHub 操作通道（git HTTPS 中断、资产下载被重置）

> 一句话：当沙箱对 github.com 的 TLS 连接被中断时，推送/发版/拉资产分别走什么替代通道，以及各通道上的具体坑。

| 元数据 | 值 |
|---|---|
| 状态 | 活跃 |
| 严重度 | 阻塞 |
| 分类 | 网络 / 工具链 |
| 创建日期 | 2026-10-20 |
| 最后验证 | 2026-10-20（当日实战：5 次提交 + 1 次删改 Tag + 1 次 Release 创建全部经此通道完成） |
| 创建人 | AI 协同会话（维护者确认） |
| 关联 | v0.0.1.10 发版全过程；安全修复推送 `e84604f`→`83633b5`→`ef94be0` |

## 1. 问题现象

在沙箱环境操作 GitHub 仓库时，按域名分成两种命运：

| 通道 | 现象 |
|---|---|
| `git clone` / `git ls-remote` / `git push`（走 github.com HTTPS） | `could not read Username: terminal prompts disabled`，重试后 `GnuTLS recv error (-110)`——TLS 连接被重置，**git 协议整体不可用** |
| Release 资产下载（`browser_download_url`，302 重定向到对象存储 CDN） | `curl` exit 56（连接被重置）；改走 `api.github.com/repos/.../assets/{id}` + `Accept: application/octet-stream` + Range 分块，单块 8MB 挂起 4 分钟零字节——**资产 CDN 整体不可用** |
| `api.github.com`（REST API） | **完全正常**，所有读/写操作均可执行 |

## 2. 根因分析

沙箱网络对非白名单域名实施 TLS 中断/重置（DNS 劫持类沙箱策略）。github.com 主域与其对象存储 CDN 都不在白名单，而 api.github.com 是独立域名且被放行。**结论：一切"必须实时传大字节"的操作（git 协议、资产下载）不可行；一切"JSON 小报文"操作（REST API）可行。**

## 3. 解决方案

### 3.1 代码推送：用 REST API 走 git 对象链

替代 `git push` 的五步（每次提交重复前三步）：

```bash
# ① 读基准 commit（保证 fast-forward）
GET  /repos/{owner}/{repo}/git/ref/heads/main        # 拿 HEAD sha
# ② 为每个变更文件建 blob
POST /repos/{owner}/{repo}/git/blobs
     {"content": "<文件全文>", "encoding": "utf-8"}   # 返回 blob sha
# ③ 建 tree（以远端 HEAD 的 tree 为 base_tree，只列变更文件）
POST /repos/{owner}/{repo}/git/trees
     {"base_tree": "<HEAD tree sha>",
      "tree": [{"path": "a/b.kt", "mode": "100644", "type": "blob", "sha": "<blob sha>"}]}
# ④ 建 commit
POST /repos/{owner}/{repo}/git/commits
     {"message": "<提交信息>", "tree": "<tree sha>", "parents": ["<HEAD sha>"]}
# ⑤ 移动分支指针（force=false，防止覆盖他人提交）
PATCH /repos/{owner}/{repo}/git/refs/heads/main
      {"sha": "<commit sha>", "force": false}
```

**推送前对齐快照**：先用 blob SHA 算法（`sha1("blob " + len + "\0" + content)`）本地计算未改文件与远端 blob 比对，确认本地快照 == 远端 HEAD，避免 API 提交把别人的改动覆盖掉。

### 3.2 打 Tag：annotated tag 是两步，且 ref 创建有隐藏字段要求

```bash
# 第一步：创建 tag 对象
POST /repos/{owner}/{repo}/git/tags
     {"tag": "v0.0.1.10", "message": "<注释>", "object": "<commit sha>",
      "type": "commit", "tagger": {"name": "<名>", "email": "<邮箱>", "date": "<ISO 时间>"}}
# 第二步：创建 ref —— 坑：payload 必须同时带外层 "tag" 字段
POST /repos/{owner}/{repo}/git/refs
     {"ref": "refs/tags/v0.0.1.10", "sha": "<tag object sha>", "tag": "v0.0.1.10"}
#                                                              ^ 少了它报 422: "tag" wasn't supplied
```

### 3.3 删 Tag/Ref：204 空响应体是坑

```bash
DELETE /repos/{owner}/{repo}/git/refs/tags/v0.0.1.10   # 成功返回 204，响应体为空
```

**坑**：直接 `json.loads(resp.text)` 会抛 `JSONDecodeError`。API 助手必须容错：空体返回 None；404/422 以错误标记返回（如 `{"__error": ...}`），不要崩在解析上。

### 3.4 读文件与建 Release

- 读仓库文件：`GET /repos/{owner}/{repo}/contents/{path}?ref=<ref>`，带 `Accept: application/vnd.github.raw+json` 直接拿原文（否则是 base64）。
- 建 Release：`POST /repos/{owner}/{repo}/releases`，`tag_name` 指向已存在的 tag；`prerelease` 按版本号是否含 `-rc` 判定。

### 3.5 资产下载被阻断时：不死磕，改三角验证

下载不通就换验证思路——用「构建配置 + CI 工作流 + 资产元数据」三方互证（完整方法见 [EXP-004](./EXP-004-verification-without-local-build.md)），不把时间耗在被重置的连接上。

## 4. 验证方法

- 推送后：`GET /repos/{owner}/{repo}/commits/main` 确认 HEAD sha 与本地构建的 commit 一致；确认目标 CI run 被新 commit 触发。
- 打 Tag 后：`GET /repos/{owner}/{repo}/releases/tags/{tag}` 能查到即链路通。
- 删 Tag 后：`GET .../git/refs/tags/{tag}` 返回 404。

## 5. 预防与规则沉淀

- 本经验已固化进 [AGENTS.md 本地分块编译验证与云端全量分工](../../AGENTS.md#本地分块编译验证与云端全量分工编译验证规约)的"无 SDK 降级路径"：本地不能编译验证时，推送后必须盯云端门禁，红即修。
- API 助手的三个容错点（外层 `tag` 字段、204 空体、`force=false`）已在会话脚本中实现，复用时直接抄，不要重新试错。

## 6. 更新记录（追加式，只增不改）

| 日期 | 更新内容 | 触发事件 |
|---|---|---|
| 2026-10-20 | 初版 | v0.0.1.10 发版全程：git 协议失败改走 API 推送，期间踩中 tag 422、DELETE 204、资产 CDN 阻断三个坑 |
