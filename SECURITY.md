# Security Policy

## 支持的版本

| 版本 | 安全更新 |
|------|----------|
| v0.0.0.24 及以上 (main) | 当前维护版本 |
| v0.0.0.1 - v0.0.0.23 | 不再接收安全更新 |

## 报告安全漏洞

如果您发现了安全漏洞，请**不要**在公开的 GitHub Issue 中报告。请通过以下方式联系维护者：

1. GitHub Security Advisory（推荐）：在仓库的 `Security` → `Advisories` 页面提交私密报告
2. 邮件：`security@minime-core.local`（示例地址，请通过 GitHub 仓库主页获取最新联系方式）

请在报告中包含：
- 漏洞的详细描述和影响范围
- 复现步骤（如果可能）
- 受影响的版本和 commit
- 任何相关的概念验证代码

我们会在收到报告后尽快确认，并在修复后公开致谢（如您同意）。

## 安全架构概述

### 凭据加密
- **应用层加密**：`CredentialEncryptor` 使用 Android Keystore MasterKey（AES-256-GCM）wrap per-DEK，对 API Key、Git Token、SSH 密码等敏感字段进行列级加密；新增可注入的 `AesGcmByteCipher`（随机 12B IV，`IV‖密文‖tag`，末尾常量时间校验认证标签）作为纵深防御件
- **加密格式**：V2 前缀 + MasterKey-wrapped DEK 双层加密，支持从 V1 明文格式迁移
- **旧格式解密 fail-closed**：V1 旧格式在 Base64 非法、密钥缺失或 GCM 认证失败时一律抛出类型化异常，**绝不把密文误当明文回吐**；连续失败计数达阈值标记为损坏
- **生物识别保护（当前未真正生效）**：开关目前仅记录用户偏好，MasterKey 尚未绑定 `setUserAuthenticationRequired(true)`，`isBiometricEffectivelyEnabled()` 显式返回 false，设置页常驻“未生效”提示；在 BiometricPrompt 接线完成前不假装有生物锁
- **紧急解锁通道**：验证 SSH 密码后可紧急重置 MasterKey（emergencyResetMasterKey），事件经 core 安全端口 `SecurityAuditSink` 脱敏审计，core 层不再反向依赖 feature 层
- **数据库加密**：基于 SQLCipher 对 SQLite 数据库文件做 AES-256 全库加密，首次访问时自动迁移加密（快照→逐表拷贝→多重校验→原子替换→清理），支持崩溃恢复。打开失败时严格区分“连接/密钥态瞬时错误”（passphrase 被清除、加密错误、临时锁等）与“文件级真实损坏”：瞬时错误直接上抛、**不清库**；真实损坏先快照隔离，任何自动重建空库都须经显式策略批准（默认拒绝）
- **备份加密**：用户口令经 PBKDF2WithHmacSHA256 派生密钥，迭代次数钳制在安全区间 [120000, 2000000]（不信任文件头自报值，越界即判篡改）；AES-GCM 分块流式加解密，尾部 hold-back 认证标签与 HMAC，在 `doFinal` 常量时间校验通过前不返回任何明文，口令错误或文件篡改即失败
- **备份口令来源**：自动到公共目录的备份口令改为应用私有目录下的 32 字节 SecureRandom 随机密钥，**不再由公开签名证书哈希派生**（证书对任何拿到 APK 的人可见）；公共导出要求用户强口令，且仅实现整包（FULL）加密，移除了未接线的“仅凭据加密”虚假选项
- **落盘凭据文件权限**：`git-credentials` 及凭据临时响应文件收紧为 owner-only（0600，去执行位），令牌不写入日志、不出现在远程 URL 回显中

### 密钥管理
- MasterKey 存储在 Android Keystore，alias 为 `minime_credential_masterkey`，不可导出
- per-DEK（32 字节 SecureRandom）经 MasterKey wrap 后存储在 SharedPreferences（Base64 密文）
- CredentialEncryptor 使用独立 alias `minime_credential_key`
- passphrase 使用后立即 `fill(0)` 擦除内存
- 密钥相关代码严格 fail-close，绝不静默降级为明文

### 终端/容器隔离
- 基于 PRoot + Alpine Linux 的本地容器，非 root 权限运行
- 凭据通过 IPC 桥接动态请求，避免持久化落盘
- **双层危险命令守卫**：
  - 权限引擎层：`DangerousCommandGuard` 静态守卫（B1 跨段管道、B2 chmod/chown token 序列、B3 设备/磁盘正则兜底），在权限引擎与工具入口双层拦截
  - 规范流程层：4 个 ToolGuard 实现（DangerousCommandGuard、LargeFileGuard、PathBoundaryGuard、FileObservationGuard），可在规范流程设置中独立开关
- 权限引擎：`ToolPermissionPolicyEngine` 基于 severityTier（0-3 四级）进行命令分类与拦截决策

### AI 权限护栏与命令执行
- **AUTO 模式 fail-closed**：不可静态判定的 shell 命令（命令替换、here-doc、未闭合引号、绝对路径重定向等，即 `analysis.analyzable=false`）一律降级为逐次确认（ASK，不可记忆），仅内置只读白名单自动放行，其余按 NORMAL 同规则判定（DENY 优先）
- **派生执行穿透**：危险命令与灾难性 `rm` 判定会向内穿透一层派生包装——`eval`、`bash/sh -c`、`sudo`、`xargs`、`env VAR=…`，对内层命令串重新做危险分析
- **路径归一**：`$HOME`、`${HOME}`、`~`、`~user` 在比对黑名单前统一归一到容器实际家目录 `/root`，防止 `rm -rf $HOME/…` 一类写法绕过
- **禁止整工具放行**：项目级权限文件（`.minime/permissions.json`）不允许对 shell/terminal 写“整工具一律 ALLOW”（如 `Bash(*)`、首 token 纯通配），非法规则加载时忽略并写脱敏审计；整工具放行必须用户显式操作
- **凭据路径降级**：只读白名单命中后，若参数涉及 `.ssh`、`.aws`、`.env`、`*.pem`、`id_rsa`、`credentials`、`.netrc`、`shadow` 等凭据片段，或出现未展开的裸 `$VAR`/`${VAR}`，仍降级为逐次确认
- **下载即执行阻断**：识别“先下载后执行”——同命令行/跨命令段的 `curl -o f && sh f`、`wget -O f; bash f`（按下载产物文件名关联后续解释执行）、进程替换 `<(curl …)`、以及内联解释器联网后 `exec/eval`，默认阻断；shell 关键字（break/echo 等）不误伤
- **外部输入不可信**：MCP 动态工具的 description 与返回文本在进入模型上下文前经 `McpContentSanitizer` 净化（中和 “ignore previous / always allow / 切换 AUTO / 已授权” 等指令模式并加不可信围栏）；上下文压缩摘要明确声明工具返回与外部内容不得作为授权、切换模式或“用户已授权”的依据

### 文件路径边界
- 所有容器文件工具（read/write/edit/create/list）的路径先做 canonical/realPath 规范化，再断言落在允许根（rootfs、workspace、授权共享区）内，采用“等于根或以 根+分隔符 开头”的分隔符边界，阻断 `../` 目录穿越与符号链接逃逸、`/workspace-evil` 兄弟前缀
- 写操作更严格：禁止落到 rootfs 系统区；未选择工作区（projectRoot 为空）时文件工具 fail-closed 阻断而非放行；路径边界护栏默认开启，越界事件写脱敏安全审计

### 应用自更新校验
- 安装更新包前由 `ApkInstallVerifier` 顺序校验：官方签名证书 SHA-256 白名单 → 包名一致 → versionCode 不降级（≥ 已装版本）→ 声明大小 → 整包 SHA-256 摘要，全部使用常量时间比较；任一不符返回拒绝原因，**绝不触发安装 Intent**
- GitHub 直连与第三方加速镜像采用完全相同的校验标准（校验不感知下载来源）
- 白名单唯一来源为 `feature/update/data/UpdateSignatureWhitelist.kt`；**首次正式签名发布前须由维护者用 `apksigner verify --print-certs` 填入真实指纹并置 `WHITELIST_FILLED=true`，在此之前应用内更新通道对所有包 fail-closed 拒绝安装**

### 网络服务与远程访问
- **内置 FTP 服务端 secure by default**：默认仅绑定回环 `127.0.0.1`；取消弱默认口令（历史弱口令启动时自动迁移为 SecureRandom 强口令，绝不写日志）；匿名账号强制只读、不授予写权限；“局域网访问”默认关闭，开启需用户在 UI 二次确认（开启即对全网卡明文暴露）
- **明文 FTP 客户端**：默认拒绝静默明文登录（`FtpCleartextPolicy`），存在 SFTP/FTPS 等加密通道时不降级，确需明文需按主机逐次确认
- **SSH 主机密钥 fail-closed + TOFU**：known_hosts 文件存在但读取/解析失败时拒绝连接（不再吞错为空列表放行任意密钥）；首次连接回显 SHA256 指纹并由用户选择“本次信任/永久信任/拒绝”，未配置确认回调（后台/自动重连）默认拒绝；指纹比对使用常量时间比较，密钥变更阻断
- **命令注入防护**：远程终端、SSH 连接、SFTP 文件操作中所有外部路径/配置拼入 shell 前统一单引号转义（`ShellQuote.quote`）
- **SSH 通道串行化**：exec/shell channel 的开取纳入同一互斥锁，消除并发竞态；远程命令非零退出上抛类型化异常，并先消费 stderr，杜绝假成功与流泄漏
- **代理订阅解析**：远程/订阅的 Clash YAML 一律使用 `SafeConstructor` 解析，锁死任意 `!!类型` 标签反序列化

### 日志与备份隐私
- 日志默认只写应用私有 `filesDir` 目录，不再因存储权限切到公共 Documents；仅当用户显式导出到公共 Download 时逐行流式脱敏（命令行口令参数、URL 内嵌 `user:token@`、Bearer/sk-/ghp_ 等令牌、PEM 私钥块）
- 云备份（cloud-backup）与换机迁移（device-transfer）精确排除 DEK 信封偏好（`minime_unified_keys`）与凭据数据库（`minime_credentials_v2.db` 含 -wal/-shm），密钥材料不进入 Google 云或设备迁移

### 容器桥接与动态配置
- 容器→宿主桥接令牌（RCB bridge token）为每会话 SecureRandom 32 字节一次性令牌，同会话复用、会话结束即失效，两次会话必不同；认证使用常量时间比较
- 容器侧触发宿主剪贴板读写默认拒绝（capability denied），需宿主按次确认门控
- 容器侧 `mcp.json` 发生变更时只标记“待确认”，不自动触发宿主 MCP 重载，须宿主确认后才生效

### 构建、签名与发布
- release 签名改为懒加载：仅在 `keystore.properties`/环境变量/CI Secrets 提供时读取；通过 `gradle.taskGraph` 守卫，凡涉及正式包（任务名含 Release/Bundle 且非 Debug）而密钥缺失或证书 SHA-256 不在官方白名单时，构建立即 fail-closed 并提示从 CI Secrets 恢复，不占位、不回退 debug 签名
- 仓库构建脚本与 CI 工作流中不再含任何明文签名口令；三个发布工作流经 GitHub Secrets（`SIGNING_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_PASSWORD`、`SIGNING_KEY_ALIAS`、`EXPECTED_SIGNING_CERT_FINGERPRINT`）注入
- 为保证老用户可覆盖安装升级，本轮**未轮换**签名 keystore（见“已知限制”）

### 已知限制
- **targetSdk=28**：因 PRoot 需在 app 可写目录执行二进制（Android 10+ W^X 禁止），锁定 API 28，无法上架 Google Play，无法享受 API 29+ 默认安全加固
- **签名密钥历史暴露（未轮换）**：release 签名 keystore 与口令历史上曾随公开仓库可获取；本轮已移除全部明文口令并改由 CI Secrets 注入、增加证书指纹白名单与 fail-closed 构建守卫，但为保证老用户可覆盖升级**未更换证书**。历史上拿到旧 keystore 者在官方填入签名指纹白名单前仍存在伪造同签名包的理论风险，建议后续在合适的大版本评估密钥轮换与迁移方案
- **更新签名指纹白名单待填**：`UpdateSignatureWhitelist.kt` 的官方指纹集合当前为空，维护者发布首个正式签名构建前必须填入，在此之前应用内更新会拒绝安装一切更新包（fail-closed）
- **生物识别尚未真正接线**：MasterKey 未绑定 `setUserAuthenticationRequired(true)`，生物锁开关仅记录偏好，UI 已明确标注“未生效”
- **AUTO 模式写工具**：本轮对 shell 与外部/修改类能力做了 fail-closed 护栏；AUTO 下 writeFile/editFile 等文件写工具仍按原策略放行（路径边界护栏默认开启、禁止整工具 ALLOW 已部分缓解），是否改为逐次确认待产品决策
- **安全确认弹窗挂载位置**：SSH 首连指纹、FTP 局域网/明文风险三个确认弹窗当前挂在远程服务器设置页组合内；冷启动后早于打开该页发起的 SSH 会因确认回调未装配而 fail-closed 拒绝（安全但影响体验），后续将提升到远程页根节点
- **PRoot 隔离**：PRoot 仅提供路径重映射，非完整 namespace/cgroups 容器隔离

## 安全开发规范

- 所有密钥/凭据相关代码必须 fail-close，禁止静默降级
- passphrase、DEK 等敏感字节使用后必须 `fill(0)` 擦除
- 日志中绝不输出密钥、密码、token 内容
- 公共函数必须有 KDoc 注释
- 异常处理明确，不吞异常（除非有明确的恢复策略并注释说明）
- 核心安全模块必须有单元测试覆盖

## 依赖安全

- 项目使用 `dependency-audit.yml` CI 工作流进行依赖审计
- 建议定期更新关键依赖：BouncyCastle、OkHttp、Ktor、SQLCipher
- 第三方 native 库（`libtermux.so`）应记录来源和编译选项
