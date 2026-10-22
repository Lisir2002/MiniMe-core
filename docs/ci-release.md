# 云端构建 · 发布运维手册

> 本文是 [AGENTS.md](../AGENTS.md)「发版流程」的深水区延伸（渐进披露：AGENTS.md 只保留摘要，本文为完整操作手册）。
> 面向：AI / 维护者操作 GitHub Actions 发版、实时监控、产物校验、签名 secrets 配置。

## 触发方式

- **自动触发**：`git push origin v0.1.0-rcN` / `git push origin v0.1.0`，CI 接收 `v*` tag push 事件后自动启动。
- **手动触发**（仅测试用）：GitHub Actions 页面 → `android-release.yml` → Run workflow（workflow_dispatch），versionName = `manual-<run_number>`，**不作为正式发版**。

## 本地分块编译验证与云端分工

**本地只做分块编译验证（快、按变更面精准触发）；全量构建 / 全量单测 / detekt / 发版由本手册所述云端链路执行。** 完整规约（变更面 → 编译块路由表、各模块编译命令、无本地构建环境的降级路径）见 [AGENTS.md 本地分块编译验证与云端全量分工](../AGENTS.md#本地分块编译验证与云端全量分工编译验证规约)，此处仅列与发版相关的边界：

- 打 Tag 前，本地分块编译必须已过（或已确认云端 `ci.yml` 对对应提交全绿），**不许带着编译红打 Tag**。
- `v*` Tag 一旦推送即触发不可逆的正式发版（主应用只发正式版 tag，预发布 tag 默认被 `android-release.yml` 的 `Reject pre-release tags` 门禁直接拒绝；唯一例外是仓库变量 `ALLOW_RC_TAG` 一次性 allowlist，见下），推送前按「发版规范」逐条核对。
- 云端 `ci.yml`（push 到 main 触发）负责日常全量门禁：release 全量构建 → `:app:testReleaseUnitTest` → `:app:detekt`；本文链路（Tag 触发）负责发版产物：签名构建 → Release 页面 → APK 资产。

## CI 全流程（单 job `build`，6 个逻辑阶段）

> workflow 实际是**单 job 多 step**结构（jobs.build），下述 6 个阶段是按职责划分的逻辑阶段，对应 step 序列。

1. **variables** → `Display release tag info` + `Reject pre-release tags`（预发布 tag 拒绝门禁；命中仓库变量 `ALLOW_RC_TAG` allowlist 时放行并打 notice）+ `Verify versionCode monotonic`（versionCode 单调递增校验）+ `Determine release name`（手动触发用 `manual-<run_number>`）+ `Determine prerelease flag`（tag 含预发布后缀时标记 prerelease；仅 ALLOW_RC_TAG 放行的 rc tag 会命中该分支）
2. **build** → `:app:testReleaseUnitTest`（发版质量门禁）→ `:app:assembleRelease` → 正式签名构建 APK 到 `app/build/outputs/apk/release/app-release.apk` → `Rename APK` 重命名为 `dist/MiniMe-<版本号>-<变体>.apk`（如 `MiniMe-v0.0.15-release.apk`，**release 仅 arm64-v8a**，重命名同时做 ABI 校验：`lib/` 必须含 `arm64-v8a`；debug 变体保留双 ABI 用于模拟器开发）
3. **upload-mapping** → `Upload R8 mapping`（`actions/upload-artifact@v4`，artifact 名 `r8-mapping-<tag>`，90 天保留，`if-no-files-found: ignore` 不阻塞）
4. **create-release** → `Generate release notes` + `Create GitHub Release & Upload assets`（`softprops/action-gh-release@v2`，prerelease 取决于 tag 是否含预发布后缀；标题基础格式 `MiniMe <版本号>`，AI/维护者发版后补充「— 更新内容概括」；正文遵循 AGENTS.md「发版说明格式」统一规约，条目为「**4-9字小标题**：20-40字说明」结构）
5. **upload-apk** → 与 create-release 同 step 完成（`files: dist/MiniMe-*.apk` 挂到 Release Assets）
6. **summary** → `Write download URLs to Run Summary`（写入 Tag / Prerelease / APK 文件名 / SHA256 / Release 页面 / mapping artifact 名到 `$GITHUB_STEP_SUMMARY`）

## 实时监控命令（GitHub API）

```bash
# 1. 查询最新 run 状态（tag 触发）
curl -s -u "<owner>:<token>" \
  "https://api.github.com/repos/<owner>/<repo>/actions/workflows/android-release.yml/runs?per_page=5" \
  | python3 -c "import sys,json;[print(r['id'],r['status'],r.get('conclusion','-'),r['head_branch']) for r in json.load(sys.stdin)['workflow_runs']]"

# 2. 查询指定 run 的每个 job 进度
curl -s -u "<owner>:<token>" \
  "https://api.github.com/repos/<owner>/<repo>/actions/runs/<run_id>/jobs" \
  | python3 -c "import sys,json;[print(j['name'],j['status'],j.get('conclusion','-')) for j in json.load(sys.stdin)['jobs']]"

# 3. 查询 Release 是否已创建 + APK asset
curl -s -u "<owner>:<token>" \
  "https://api.github.com/repos/<owner>/<repo>/releases/tags/<tag>" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('tag_name'),d.get('prerelease'));[print(a['name'],a['size'],a['browser_download_url']) for a in d.get('assets',[])]"
```

## 预发布 tag 一次性 allowlist（ALLOW_RC_TAG）

主应用默认只发正式版 tag（`vX.Y.Z`），`android-release.yml` 的 `Reject pre-release tags` 门禁拒绝一切 `-rc/-beta/-alpha/-dev` 后缀。确需发 RC 的批次，由维护者裁定后按一次性 allowlist 放行：

- **仓库变量**（Settings → Secrets and variables → Actions → Variables）：`ALLOW_RC_TAG`，值为**不带 `v` 前缀**的版本号（如 `0.0.1.11-rc1`）；带/不带 `v` 前缀的 tag 均参与比对。
- **生效行为**：tag 命中 allowlist → 门禁打 `::notice::` 放行，`Determine prerelease flag` 将其标记为 pre-release（Release 页为 Prerelease）；未命中的预发布 tag 仍直接拒绝。
- **用后清理**：正式版 tag（转正）打出后，维护者应删除该仓库变量，恢复「只发正式版」默认纪律。
- **首次启用记录（R4 批次，2026-10-22 维护者裁定）**：R4（主题工坊 + diff 主题包）整批完成后发 `0.0.1.11-rc1` 做测试迭代，允许这一次绕过 rc tag 规矩；其余批次 rc tag 仍拒绝。

## 产物校验清单（构建完成后必跑）

1. **下载 APK** → `curl -sL -u "<owner>:<token>" -o MiniMe-<版本号>-<变体>.apk "<browser_download_url>"`
2. **ABI 校验** → `unzip -l <apk> | grep 'lib/.*\.so'` 必须含 `lib/arm64-v8a/*.so`（release 仅 arm64-v8a）；容器资产应含 `assets/container/arm/alpine-rootfs.bin`
3. **签名校验** → `keytool -printcert -jarfile <apk>` → Owner 必须为正式签名（非 `CN=Android Debug`）
4. **SHA256** → `sha256sum <apk>` 记录指纹
5. **Release 页面** → https://github.com/Lisir2002/MiniMe-core/releases/tag/<tag>

## 签名策略（唯一官方密钥，GitHub Secrets 注入）

> **历史沿革**：正式签名密钥曾短期随仓库分发，后按安全审计结论（见 `docs/risk-audit-v0.0.0.46.md`）从仓库移除，改为 **GitHub Secrets 注入**；`SECURITY.md` 记录了密钥历史暴露、为兼容性未轮换的残余风险。

正式 release 一律使用**唯一官方 keystore**（`app/minime.jks`，别名 `minime`）。密钥**不入库**（`.gitignore` 第 14-18 行排除 `app/minime.jks` 与 `app/keystore.properties`），由 CI 在构建前从 GitHub Secrets 恢复，保证本地与 CI 每一次 release 加载同一把密钥、签名指纹恒定：

| Secret | 用途 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `minime.jks` 的 base64 编码 |
| `SIGNING_STORE_PASSWORD` | keystore 的 storePassword |
| `SIGNING_KEY_PASSWORD` | key 的 keyPassword |
| `SIGNING_KEY_ALIAS` | 签名别名（`minime`） |
| `EXPECTED_SIGNING_CERT_FINGERPRINT` | 证书 SHA-256 白名单，用于 fail-closed 比对 |

- **CI 流程**：`android-release.yml` 第 4 步「Restore signing keystore from GitHub Secrets」将 base64 解码为 `app/minime.jks` 并生成 `app/keystore.properties`；**任一 Secret 缺失即 `::error::` 失败**（fail-closed），不存在无签名产出的可能。
- **本地构建**：`app/minime.jks` 与 `app/keystore.properties` 被 `.gitignore` 排除，需维护者从 CI 产物或其他维护者处本地放置；`app/build.gradle.kts` 的 release signingConfig 与 taskGraph 守卫保证缺失即构建失败，**不存在「回退到 debug keystore 当正式签名」**。
- **⚠️ 安全提示（残余风险，维护者已知情）**：该 keystore 与口令历史上曾随公开仓库可获取，为保证老用户覆盖升级**未轮换证书**；当前私钥已不在仓库中，但历史获取者仍有伪造同签名包的理论风险，密钥轮换评估见 `SECURITY.md`。
- **产物签名校验**：`keytool -printcert -jarfile <apk>` → Owner 应为 `CN=MiniMe-core, OU=Mobile, O=MiniMe, L=Beijing, ST=Beijing, C=CN`（密钥 SHA256 = `05:93:2E:DB:4D:94:BB:45:FB:81:9B:BA:D9:9F:70:01:0C:23:D8:82:CA:86:BF:D5:73:F0:0D:D7:03:80:24:03`），非 `CN=Android Debug`。
