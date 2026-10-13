# 维护者操作手册（需人工在 GitHub 端执行）

本手册收录**无法通过代码提交完成**、必须在 GitHub 网页端或本地一次性执行的操作。
审计与修复系列的自动化部分已通过提交落地，剩余项集中在这里，按优先级排序。

---

## P0 — 建议 24 小时内完成

### 1. 填入官方签名指纹白名单（阻塞应用内更新）

**为什么**：`UpdateSignatureWhitelist.kt` 的官方指纹集合当前为空。在填入之前，应用内更新会
拒绝安装**一切**更新包（fail-closed，安全但功能不可用）。

**怎么做**：

```bash
# 取当前官方 keystore 的证书 SHA-256（本地有 keystore 时）
keytool -list -v -keystore app/minime.jks -alias androiddebugkey | grep "SHA256:"
```

把输出填到 `app/src/main/java/.../UpdateSignatureWhitelist.kt` 的官方指纹集合中，提交并发版。
同时确认 CI Secret `EXPECTED_SIGNING_CERT_FINGERPRINT` 与之一致。

### 2. 处理历史泄露的签名密钥（决策项）

见 [SECURITY.md → 签名密钥历史泄露事件与轮换计划](../SECURITY.md#签名密钥历史泄露事件与轮换计划)。
短期动作（填入白名单）与上一步相同；是否更换证书属于产品决策，建议在大版本评估。

---

## P1 — 建议本周内完成

### 3. 开启 main 分支保护

`Settings → Branches → Add branch ruleset`（或 classic branch protection），对 `main`：

| 开关 | 取值 | 理由 |
|---|---|---|
| Require a pull request before merging | ✅ | 禁止直推 |
| Required approvals | ≥ 1 | 双人复核（建议 2） |
| Dismiss stale pull request approvals | ✅ | 新提交后旧批准失效 |
| Require status checks to pass | ✅ | 至少勾 `Build & Test`（ci.yml） |
| Require branches to be up to date | ✅ | 避免合并后红 |
| Require signed commits | 建议 ✅ | 追溯性 |
| Block force pushes / deletions | ✅ | 防历史改写 |
| Include administrators | 建议 ✅ | 避免例外绕过 |

### 4. 收紧 tag 权限

`Settings → Rules → Rulesets → New ruleset → Target: tags`：

- 限制 `v*` tag 只能由维护者创建（防止随机发版流水线被触发）
- 开启 `Restrict deletions`

### 5. 处理 Dependabot alerts

`Security → Dependabot alerts`：逐条 triage。已由本次修复处理的两条：

- BouncyCastle 1.75 → **1.78.1**（CVE-2024-29857 / 30171 / 30172 / 34447）
- snakeyaml 2.2 → **2.4**

其余告警按「同主版本优先、跨主版本需回归测试」处理。

### 6. 确认 Secrets 完整

`Settings → Secrets and variables → Actions` 应有：

```
SIGNING_KEYSTORE_BASE64
SIGNING_STORE_PASSWORD
SIGNING_KEY_PASSWORD
SIGNING_KEY_ALIAS
EXPECTED_SIGNING_CERT_FINGERPRINT
```

**自检**：任何一项缺失或不一致，`android-release.yml` 会在签名阶段 fail-closed（不会静默产出 debug 签名包）。

---

## P2 — 首轮 CI 观察后收敛

### 7. detekt 从「观察」转为「硬门禁」

本次已在 ci.yml 接入 `:app:detekt`，但首轮设为 `continue-on-error: true`
（原因：detekt 1.23.x 内置 Kotlin 编译器为 2.0.x，而本项目为 Kotlin 2.2.21，跨版本分析稳定性未验证）。

**转硬门禁步骤**：

1. 连续观察 3~5 次 CI 运行的 detekt artifact，确认 findings 数稳定且无误报。
2. 若发现大量误报（典型症状：规则报在无法解析的类型上），回退到只保留稳定规则集，或等 detekt 2.0.0 GA（官方支持 Kotlin 2.2）。
3. 确认后删除 ci.yml 中 detekt 步骤的 `continue-on-error: true`。

### 8. 覆盖率门禁收紧

`jacocoTestReport` 当前只出报告、不阻断。待基线稳定后：

1. 从 artifact 读取首轮行覆盖率作为基线。
2. 新增 `JacocoCoverageVerification` 任务，先对安全关键包（permission / guard / encryption / backup）设底线，再逐步扩展到全模块。
3. 挂到 CI 的 check 上。

---

## 附：一次性验证命令

```bash
# 本地跑一遍与 CI 相同的门禁（需先准备好 keystore.properties 兜底）
./gradlew :app:compileReleaseKotlin
./gradlew :app:testReleaseUnitTest
./gradlew :app:lintRelease
./gradlew :app:detekt
./gradlew :app:jacocoTestReport

# 验证 APK 签名（V2+V3 双 scheme）是否被权威工具接受
apksigner verify --print-certs --min-sdk-version 26 app-release.apk
```
