# MiniMe-core 深度风险审计报告

- **审计对象**：MiniMe-core（Android 多模块项目，tag `v0.0.0.46`）
- **审计日期**：2026-10-02
- **审计基线 CI Run**：`Android Release` #37011040580（tag `v0.0.0.46`，审计时处于 `in_progress`）
- **仓库可见性**：**Public（公开）** — 经 GitHub API 核实 `private: false`
- **审计方法**：直接读取仓库源码 / 工作流 / 配置文件 + GitHub REST API（分支保护、Dependabot、仓库元信息），未修改任何项目文件
- **硬性规则复核**：① 主应用不发 rc 版；② 版本号四段式 `x.x.x.x`，D 段单调递增

---

## 一、总体结论

| 维度 | 评级 | 一句话结论 |
|---|---|---|
| CI/CD 流水线 | 🟡 中 | 主 release 链路门禁设计较完善（RC 拒绝、versionCode 单调、applicationId 锁定、签名指纹比对），但缺并发控制、R8 mapping 留存过短 |
| 安全 | 🔴 严重 | **Release 签名 keystore 与明文密码随公开仓库分发**，任何人可伪造官方签名包 |
| 版本与发布管理 | 🟡 中 | tag 驱动 + 三重门禁思路正确，但「D 段 +1」无自动化强制（历史已跳号），且 AGENTS.md 与现行 RC 策略自相矛盾 |
| 代码质量与技术债 | 🟡 中 | lint baseline 渐进式门禁合理；但 detekt 配而不跑、无覆盖率门禁、229 个存量 error 靠 baseline 压制 |
| 依赖与供应链 | 🟠 高 | **无真实 CVE 扫描**（dependency-audit 名不副实）、Dependabot 关闭、无依赖锁定、引入 jitpack |
| 构建与配置 | 🟡 中 | targetSdk=28（EOL）、cleartext 全局放行、allowBackup=true 纳入凭据库 |
| Git 与仓库卫生 | 🟠 高 | **main 分支无任何保护规则**、直推 main + tag 即发版、历史中 ~100MB tree-sitter 死代码 |

**总体风险评级：🟠 中-高（需要近期收敛）**

> 该项目在工程纪律上明显强于一般个人项目：版本号与提交历史解耦、applicationId 三重防线、RC 拒绝门禁、签名指纹比对、CHANGELOG 一致性校验等设计都值得肯定。**但存在一个「自曝密钥」级别的致命问题，叠加无分支保护与无真实依赖扫描，使整体风险被推到中-高。**

---

## 二、🔴 严重（必须立即修复）

### S1. Release 签名私钥 + 明文密码随公开仓库分发

- **所在文件**：
  - `app/minime.jks`（3,560 字节，已被 git 跟踪）
  - `app/keystore.properties`（已被 git 跟踪，见下）
  - `app/branding.gradle.kts:30-41`（签名策略说明）
  - `.github/workflows/android-release.yml:38-44`
- **证据**：
  ```properties
  # app/keystore.properties
  storeFile=minime.jks
  storePassword=9d4d4f44c1eaf5bcd57c68c5c4dab893
  keyAlias=minime
  keyPassword=9d4d4f44c1eaf5bcd57c68c5c4dab893
  ```
  仓库 `git ls-files` 确认 `app/minime.jks`、`app/keystore.properties` 均被跟踪；仓库为 **Public**，任何人 clone 即可下载签名私钥与口令。`SECURITY.md:52` 与 `branding.gradle.kts:37-38` 已「维护者知情决策」地承认这一点。
- **风险描述**：私钥口令明文且公开。任何人均可用同一把 `minime.jks` 签名 APK，使其与官方包具有**相同签名指纹**。
- **影响**：
  - 攻击者可伪造「官方签名」的恶意更新包，绕过 Android 签名校验；
  - 官方 CI 的「签名指纹门禁」（`Verify APK Signature`）对第三方分发完全无效——它只校验 CI 自己产出的包，挡不住别人用公开私钥签名；
  - 一旦分发面扩大（上架、转发群），此密钥等同于已泄露，无法撤回。
- **修复建议**：
  1. 立即把 `app/minime.jks`、`app/keystore.properties` 从仓库移除，改由 CI secrets 注入（`actions/upload` 前从 secret 还原 keystore，恢复 `android-release.yml` 旧的「keystore 还原」策略）；
  2. **轮换密钥**：当前私钥应视为已泄露。若仍需兼容老用户升级，可短期保留旧密钥出一个「迁移版」，再切到新密钥；若仅小范围侧载分发，可评估一次性要求用户卸载重装；
  3. 同步更新 `SECURITY.md:52`、`branding.gradle.kts`、`.gitignore:14-15` 中「已入库」的说明。

---

## 三、🟠 高（近期修复）

### H1. main 分支无任何保护规则，且直推 main + tag 即触发公开发版

- **证据**：GitHub API `GET /repos/Lisir2002/MiniMe-core/branches/main/protection` 返回 `404 Branch not protected`。
- **风险描述**：无分支保护 = 无「必须通过的 CI 检查」、无 PR 评审、可 force-push、可直接打 tag 推远端。而本仓库工作流是「直接在 main 提交、打 `v*` tag 即公开发 Release」（`AGENTS.md:140`、`android-release.yml:9-12`）。
- **影响**：任何一次误 push、误打 tag、或账号失陷，都会直接产出公开 Release APK，没有 merge 前的闸门兜底；与 S1（密钥公开）叠加时风险倍增。
- **修复建议**：
  1. 给 `main` 开启分支保护：要求 status check 通过（至少 `CI` workflow 的 build+test）、禁止 force-push、禁止删除；
  2. 打 tag 发版动作纳入人工确认（当前已靠 tag 拒绝门禁兜底，但建议在保护规则里限制谁能推 tag）。

### H2. 没有真实的依赖漏洞扫描（名不副实 + Dependabot 关闭）

- **证据**：
  - `dependency-audit.yml` 注释声称「Gradle 依赖安全漏洞扫描（dependency-check）」，但实际只执行 `./gradlew :app:dependencies` 打印依赖树（`dependency-audit.yml:64-110`），**全程没有 OWASP dependency-check / Snyk / 任何 CVE 匹配**；
  - GitHub API `GET /repos/.../dependabot/alerts` 返回 `403 Dependabot alerts are disabled for this repository`；
  - 仓库未配置 `dependabot.yml`，也未启用 secret/code scanning。
- **风险描述**：项目重度使用安全敏感库——BouncyCastle `1.75`、OkHttp `4.12.0`、Ktor `2.3.13`、SQLCipher `4.5.4`、sshj `0.38.0`、commons-compress `1.26.2`。这些库历史上 CVE 高发，却没有任何自动化告警。
- **影响**：已知漏洞长期不被发现；`SECURITY.md:66-67` 声称的「依赖审计」实际不成立。
- **修复建议**：
  1. 开启 GitHub Dependabot alerts + 自动更新 PR（Settings → Code security）；
  2. 在 `dependency-audit.yml` 真正接入 OWASP dependency-check 或 Dependabot，并对高危 CVE 设为失败门禁；
  3. 优先核对 BouncyCastle / OkHttp / commons-compress 当前版本的已知 CVE。

### H3. 依赖未锁定 + 引入 jitpack 第三方仓库

- **证据**：
  - `settings.gradle.kts:34-41` 仓库列表含 `maven { url = uri("https://jitpack.io") }`（plugin 与 dependencyResolution 两处都加）；
  - 仓库内无 `gradle/dependencies.lock`，构建脚本未使用 `--write-locks`，transitive 依赖每次构建可漂移。
- **风险描述**：jitpack 会按需从任意 GitHub 仓库构建产物，供应链可信度低于 mavenCentral/google；无 lockfile 意味着同一份 tag 在不同时间构建出的依赖闭包可能不同，破坏可重复性。
- **影响**：构建不可复现、供应链投毒面扩大。
- **修复建议**：启用 Gradle dependency locking（`gradle/dependencies.lock` 入库，CI 用 `--verify-locked-dependencies`）；评估 jitpack 是否必要，能用 mavenCentral 坐标替代则替换。

### H4. targetSdk 锁定 28（已 EOL），并在 lint 中刻意关闭告警

- **证据**：`app/build.gradle.kts:157` `targetSdk = 28`；`app/lint.xml:8` 与 `build.gradle.kts:331` 均 `disable += "ExpiredTargetSdkVersion"`。
- **风险描述**：Android 9（API 28）已停止安全维护约 6 年，享受不到 W^X、Scoped Storage、隐式 Intent 收敛、JobScheduler 加固等一系列默认安全增强。此为 PRoot 可执行性所迫的已知取舍（`SECURITY.md:51`、`AGENTS.md:96`），但应用本身是终端/容器/凭据仓库，暴露面被放大。
- **影响**：不进 Google Play（已知）；设备侧攻击面偏大；`ExpiredTargetSdkVersion` 被静默，外部审计看不到这一事实。
- **修复建议**：接受现状的同时，至少在 release notes / 安全页明示 targetSdk=28 的风险；中期评估 PRoot 替代方案（如新版 termux 已用 app_process + 隐藏的可执行方式突破 W^X），为抬升 targetSdk 留路。

### H5. allowBackup=true 把凭据库纳入备份，且数据库默认明文

- **证据**：
  - `AndroidManifest.xml:24` `android:allowBackup="true"`；
  - `app/src/main/res/xml/full_backup_rules.xml` 与 `data_extraction_rules.xml` 均 `include domain="database" path="."` + `include domain="sharedpref" path="."`；
  - `SECURITY.md:32`：数据库加密「默认明文，开启后执行 7 步迁移」。
- **风险描述**：应用存储 API Key、Git Token、SSH 密码等敏感凭据。`allowBackup=true` 下，`adb backup` / 云备份 / OEM 换机克隆会把整个 `databases/` 与 `shared_prefs/` 带走；而 SQLCipher 默认关闭，意味着未主动开启加密的用户，凭据库以**明文 SQLite** 进入备份链路。
- **影响**：备份恢复 / 换机克隆场景下凭据泄露。
- **修复建议**：对凭据相关 database（CredentialsDb）显式 `<exclude>` 出备份白名单，或在允许备份前强制 SQLCipher；评估是否对本应用直接 `allowBackup=false`（凭据类应用常见做法）。

---

## 四、🟡 中（计划修复）

### M1. R8 mapping.txt 仅保留 90 天，无法长期反混淆线上崩溃

- **所在文件**：`android-release.yml:319-326`（`retention-days: 90`）
- **证据**：
  ```yaml
  - name: Upload R8 mapping (Run artifact)
    with:
      name: r8-mapping-${{ github.ref_name }}
      retention-days: 90
  ```
- **风险描述**：注释自述「用于线上 crash stack 反混淆与版本回溯」，但 90 天后 artifact 被 GitHub 自动清除。一个已发布的版本会被用户使用数月甚至更久，崩溃上报却再也无法还原符号。
- **影响**：过期版本崩溃栈不可逆混淆。
- **修复建议**：把 mapping.txt 作为 Release Asset 永久挂到对应 GitHub Release，或推送到私有对象存储/持久 artifact；Run artifact 仅作短期兜底。

### M2. android-release.yml 无并发控制，重推同 tag 可能竞态

- **证据**：`ci.yml:30-32` 有 `concurrency: group: ci-${{ github.ref }} / cancel-in-progress: true`，但 `android-release.yml` **没有 concurrency 块**。
- **风险描述**：同一 tag 被误重推、或手动重跑时，可能与上一个 in-progress 的 release run 并发写同一个 Release。仓库虽有 `Preserve existing release content`（`android-release.yml:400-449`）兜底正文，但两次构建同时 `upload assets` 仍可能竞争。
- **修复建议**：给 release workflow 加 `concurrency: group: release-${{ github.ref }}`（不建议 cancel-in-progress，避免发版到一半被取消）。

### M3. 「D 段 +1 单调递增」无自动化强制，历史已跳号

- **证据**：
  - tag 序列中存在 `...0.0.0.25 → 0.0.0.30...`、`...0.0.0.36 → 0.0.0.42...` 两段跳空；
  - CI `Verify versionCode monotonic`（`android-release.yml:139-169`）只拦截 `cur_vc < prev_vc`（回退），**允许相等、允许跳跃**；
  - `check-version-consistency.py:142` 仅判断 `cur_version <= prev_version` 报错，并未校验「D 段恰好 +1」。
- **风险描述**：用户硬性规则是「D 段 +1 单调递增」，但 CI 与脚本都只保证「不回退」，跳号（如 46→100）可正常通过。规则靠人工纪律。
- **修复建议**：若确需严格 D+1，在 `check-version-consistency.py` 增加「同 C 段下 D 必须 = prev_D + 1；跨 C 段时 D 归零」的精确断言；若允许跳号则修订 AGENTS.md 表述以免误解。

### M4. AGENTS.md 与现行「主应用不发 RC」策略自相矛盾

- **证据**：
  - 现行 CI `Reject pre-release tags`（`android-release.yml:124-133`）**硬拒绝** `-rc/-beta/-alpha/-dev` tag，与用户「主应用永远不发 rc 版」一致；
  - 但 `AGENTS.md:161-172` 仍大段描述「必须先发 RC」「打 `v1.7.0-rc1`」「RC 转正」流程，`branding.gradle.kts:13`、`build.gradle.kts:31` 也仍保留 rcN 解析分支。
- **风险描述**：新维护者/AI 按 AGENTS.md 操作会去打 rc tag，然后被 CI 直接拒掉，产生困惑与无效发布尝试。
- **修复建议**：把 AGENTS.md 的 RC 章节标记为「已废弃（主应用改为只发正式版）」，或仅保留 logviewer 等附属应用的 RC 流程。

### M5. 源码注释与实际签名策略不符（文档漂移）

- **证据**：
  - `app/build.gradle.kts:22-23` 注释写「keystore.properties 已 gitignore，不入库；CI 环境则跳过，release 产出 unsigned 包」——实际 keystore.properties **已入库**，且 `:139` 有 `require(...)` 缺失即失败，根本不会产 unsigned 包；
  - `app/build.gradle.kts:222-225` 注释称「没有 keystore.properties 则回退 debug keystore」，与 `:139` 的 require 硬失败矛盾；
  - `ci.yml:10` 注释仍写「额外有 keystore 还原」，与现行「keystore 随 checkout 自带」不符。
- **影响**：误导排查，新维护者对签名行为产生错误预期。
- **修复建议**：统一三处注释为「keystore 已入库（待按 S1 修复后改为 secrets），缺失即构建失败」。

### M6. detekt 已配置但从未在 CI 执行

- **证据**：`app/build.gradle.kts:528-534` 配置了 detekt，`config/detekt/detekt.yml` 存在；但 `grep detekt .github/workflows/` 无任何结果——release / ci / weekly 均未跑 detekt。
- **影响**：静态质量门禁形同虚设，配置成为死代码。
- **修复建议**：在 `ci.yml` 加 `./gradlew detekt` 门禁，或移除配置避免误导。

### M7. release 中引入 alpha 版安全库

- **证据**：`app/build.gradle.kts:475` `implementation("androidx.security:security-crypto:1.1.0-alpha06")`。
- **风险描述**：security-crypto 是凭据加密的关键依赖（EncryptedSharedPreferences 存 DEK），却使用 alpha 版进正式包。
- **修复建议**：评估升级到稳定版（当前已有 1.1.0 稳定线），锁定正式 release。

### M8. 全局 cleartext 放行，API Key 可走明文 HTTP

- **证据**：`AndroidManifest.xml:32` `usesCleartextTraffic="true"`；`res/xml/network_security_config.xml` `<base-config cleartextTrafficPermitted="true" />`。
- **风险描述**：应用把用户填的 AI provider Base URL 透传，若用户指向 http 端点，API Key 明文过网（配置文件已自知此风险）。对一个存 API Key 的应用，这是被动泄露面。
- **修复建议**：至少对 AI 请求层做「http 端点二次确认/警告」，或默认禁 cleartext、仅对用户显式添加的 LAN 域名放通（按 domain 白名单而非 base-config 全开）。

### M9. logviewer-release.yml 缺主应用的多重发布门禁

- **证据**：`logviewer-release.yml` 构建后只做 `apksigner verify --print-certs`（仅证明「已签名」，不比对官方指纹），**没有** RC 拒绝、versionCode 单调校验、applicationId 稳定性、ABI 校验、签名指纹比对；且未 `fetch-depth: 0`。
- **影响**：附属应用发版健壮性明显弱于主应用；`apksigner verify` 在密钥泄露语境下（S1）无实际防护。
- **修复建议**：把主 release 的门禁脚本复用到 logviewer，或抽取共用 composite action。

### M10. gradle wrapper 未配置 distributionSha256Sum

- **证据**：`gradle/wrapper/gradle-wrapper.properties` 无 `distributionSha256Sum`。
- **影响**：Gradle 发行包下载不做完整性校验，存在被中间人替换 `gradle-8.14-all.zip` 的理论供应链风险。
- **修复建议**：补上 `distributionSha256Sum`（gradle 官方校验和）。

---

## 五、🟢 低（建议关注）

### L1. lint-baseline.xml 压制 229 个 error，存在 baseline 腐化
- **文件**：`app/lint-baseline.xml`（498KB）；`build.gradle.kts:337-340`。
- **说明**：渐进式门禁设计合理（`abortOnError=true` 拦新增），但 229 个存量 error 长期靠 baseline 压制，建议定期收敛。

### L2. weekly-health-check 全部 `continue-on-error`，永不失败
- **文件**：`weekly-health-check.yml:80,94,98,112,116`。
- **说明**：全量构建/测试/lint 都不阻断，仅产出 artifact。作为「观察窗」可接受，但回归问题不会主动告警（另有 `ci-failure-alert.yml`，需确认其是否覆盖）。

### L3. git 历史残留 ~100MB tree-sitter 死代码与 actionlint 二进制
- **证据**：`app/src/main/cpp/` 已不在工作树（tree-sitter 已被 sora-editor/TextMate 替代），但 `git rev-list --objects --all` 显示 `tree-sitter-cpp/parser.c`(25MB)、`tree-sitter-kotlin/parser.c`(22MB)、`actionlint`(6MB) 等仍在历史中，repo pack 达 26MB。
- **建议**：下次大版本发布时用 filter-repo 清理历史（注意会改写 commit 与 tag，需维护者窗口操作）。

### L4. step 注释与实际 ABI 策略不符
- **文件**：`android-release.yml:223` 注释写「双 ABI：arm64-v8a + x86_64」，但 `:307-310` 校验逻辑是「出现 x86_64 so 即失败」。
- **建议**：删改该过期注释。

### L5. aapt / apksigner 用 `head -1` 选 build-tools，多版本时不确定
- **文件**：`android-release.yml:239,255`。
- **说明**：当前只装 `build-tools;36.0.0`，风险低；若 runner 预装多版本，`head -1` 取到的可能不是预期版本。建议固定路径。

### L6. 无测试覆盖率门禁
- **说明**：CI 只跑 `testReleaseUnitTest`，无 JaCoCo / 覆盖率阈值。对核心安全模块（`SECURITY.md:62` 要求有单测）建议补覆盖率看板。

### L7. 本地 githooks 不强制启用
- **文件**：`.githooks/commit-msg`（Conventional Commits 校验）需手动 `git config core.hooksPath .githooks`；`pre-commit` 为空。
- **说明**：靠本地自觉；服务端无对应校验（无分支保护时尤甚）。

### L8. weekly-health-check 内嵌 base64 debug keystore
- **文件**：`weekly-health-check.yml:38-43`。
- **说明**：这是标准 debug 密钥（口令 android，公开周知），风险低；但把二进制 base64 塞 yml 不易维护，建议改为从 secret 注入或用 CI 自动生成。

---

## 六、方法与证据索引

- 主流水线：`.github/workflows/android-release.yml`（516 行，step13=Lint、step14=Build APK、step15=非 Debug 校验、step16=签名指纹校验、step17=重命名+ABI 校验、step18=上传 R8 mapping、step26=写 Run Summary）
- 构建脚本：`app/build.gradle.kts`、`app/branding.gradle.kts`、`gradle/libs.versions.toml`
- 版本脚本：`scripts/gitops/check-version-consistency.py`、`release-log.py`
- 安全：`SECURITY.md`、`app/src/main/AndroidManifest.xml`、`res/xml/network_security_config.xml`、`full_backup_rules.xml`、`data_extraction_rules.xml`
- GitHub API 核实：main 分支无保护、Dependabot 关闭、仓库 public、Run #37011040580 in_progress。

---

## 七、总体风险评级与 Top 3 优先修复项

**总体评级：🟠 中-高**

工程纪律扎实（版本号与历史解耦、包名三重防线、RC 硬拒绝、签名指纹比对、CHANGELOG 门禁），但被「公开私钥」+「无分支保护」+「无真实依赖扫描」三处系统性短板拉低。

**Top 3 优先修复：**

1. **【S1】把 Release 签名 keystore/口令移出公开仓库并轮换私钥。**
   这是当前唯一「可被直接利用」的致命项——任何人均可伪造官方签名包。短期若无法轮换，至少先把私钥挪进 CI secrets，阻断「随手 clone 即得密钥」。

2. **【H1+H2】开启 main 分支保护 + 启用真实依赖扫描（Dependabot / OWASP dependency-check）。**
   一个管住「谁能发版」，一个管住「依赖里有没有已知洞」；二者配合把「直推 main 即公开发布」这条最危险的链路补上闸门。

3. **【H5+M1】收敛备份/凭据暴露面，并把 R8 mapping 永久留存。**
   对凭据库关闭云备份或强制 SQLCipher 默认开；mapping.txt 挂到 Release Asset 而非 90 天 artifact，保证长期可反混淆线上崩溃。
