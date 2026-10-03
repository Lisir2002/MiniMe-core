# 发版标准操作流程（SOP）

> 本文档定义 MiniMe-core 项目的标准发版流程，确保每次发版都经过完整验证，零错误发布。

## 一、发版前准备

### 1.1 确认发版内容
- [ ] 确认本次发版包含的功能/修复已全部合并到 main 分支
- [ ] 确认 CHANGELOG 已按模板更新（新功能/改进/修复分类）
- [ ] 确认版本号符合语义化版本规则（major.minor.patch+build，四段式）

### 1.2 版本号规则
- 格式：`vX.Y.Z` 四段式（如 `v0.0.0.48`）
- 破坏性改动升 minor，bugfix 升 patch
- build number 必须自增且与 tag 一致
- **禁止** rc/beta/alpha/dev 等预发布后缀（主应用只发正式版）

### 1.3 CHANGELOG 格式规范
- 条目结构：`**4-9字小标题**：20-40字简练说明`
- 分类顺序：新功能 → 改进 → 移除 → 修复 → 安全 → 已知问题
- **禁止** emoji
- 示例：
  ```
  ## v0.0.0.48 (2026-10-03)

  ### 新功能
  - **Git暂存管理**：支持 stash 创建/查看/恢复/删除，带描述信息
  - **危险操作确认**：分支切换/强制推送等危险操作二次确认

  ### 修复
  - **Snippet解析**：修复 $1 裸占位符解析失败导致单元测试报错
  ```

---

## 二、发版前验证（强制）

### 2.1 运行发版前验证清单脚本

```bash
# 完整验证（包含本地编译/测试/Lint，约5-15分钟）
python3 scripts/ci/pre-release-check.py --version v0.0.0.48

# 快速检查（跳过本地构建，仅用于快速检查，不推荐正式发版）
python3 scripts/ci/pre-release-check.py --version v0.0.0.48 --skip-local-build
```

### 2.2 验证清单内容

| 序号 | 检查项 | 说明 | 必须通过 |
|------|--------|------|----------|
| 1 | 工作区干净 | 无未提交的修改 | ✅ |
| 2 | 当前分支是 main | 禁止在其他分支发版 | ✅ |
| 3 | 与远程同步 | 无未推送/未拉取的提交 | ✅ |
| 4 | CHANGELOG 已更新 | 包含当前版本号 | ✅ |
| 5 | 版本号格式正确 | 四段式，无预发布后缀 | ✅ |
| 6 | CI 预检查通过 | strings/Compose/import 等检查 | ✅ |
| 7 | 本地 release 编译通过 | compileReleaseKotlin | ✅ |
| 8 | 本地 release 单测通过 | testReleaseUnitTest | ✅ |
| 9 | 本地 release Lint 通过 | lintRelease | ✅ |

### 2.3 验证失败处理
- 任何一项检查失败，**禁止发版**
- 修复所有问题后，重新运行验证脚本
- 连续 2 次验证失败，向用户报告并寻求帮助

---

## 三、执行发版

### 3.1 打 Tag 并推送

```bash
# 创建带注释的 tag
git tag -a v0.0.0.48 -m "v0.0.0.48: 简要描述本次发版的主要内容"

# 推送 tag 到远程（触发 android-release.yml）
git push origin v0.0.0.48
```

### 3.2 Tag 命名规范
- 统一 `v{version}` 格式（如 `v0.0.0.48`）
- **禁止**其他格式（如 `release-0.0.0.48`、`v0.0.0.48-rc1`）

### 3.3 实时监控 CI 构建

```bash
# 查看最新的 release 构建状态
gh run list --limit 5

# 查看构建步骤进展
gh run view <run_id> -v

# 查看失败日志（如果失败）
gh run view <run_id> --log-failed
```

**监控要点：**
- 构建通常需要 10-15 分钟
- 关注关键步骤：签名密钥恢复 → 单测 → Lint → 构建 APK → 签名校验 → 上传 Release
- 任何步骤失败，立即查看日志并修复

---

## 四、发版后验证

### 4.1 确认 Release 创建成功

```bash
# 查看 Release 信息
gh release view v0.0.0.48

# 确认 APK 已上传
gh release view v0.0.0.48 --json assets
```

### 4.2 验证 APK 完整性

| 检查项 | 验证方法 | 预期结果 |
|--------|----------|----------|
| APK 存在 | Release 页面有 APK 下载链接 | ✅ |
| 非 debug 包 | CI 中 Verify APK is Release 步骤通过 | ✅ |
| 签名正确 | CI 中 Verify APK Signature 步骤通过，证书指纹匹配 | ✅ |
| ABI 正确 | 仅含 arm64-v8a，无 x86_64 | ✅ |
| 大小合理 | 约 80-90MB | ✅ |

### 4.3 下载测试（可选但推荐）
- 下载 APK 到本地，安装到测试设备
- 验证主要功能正常
- 验证本次发版的功能/修复确实生效

---

## 五、发版失败处理

### 5.1 CI 构建失败
1. 立即查看失败日志：`gh run view <run_id> --log-failed`
2. 根据 [CI 错误排查手册](CI错误排查手册.md) 定位和修复问题
3. 提交修复到 main 分支
4. 删除失败的 tag：
   ```bash
   git push origin :v0.0.0.48
   git tag -d v0.0.0.48
   ```
5. 等待 main CI 完全通过
6. 重新打 tag 并推送
7. **禁止**手动本地构建绕过 CI

### 5.2 连续失败处理
- 连续 2 次 CI 构建失败，向用户报告
- 报告内容：失败原因、已尝试的修复、需要用户协助的事项
- 不得在未报告的情况下继续反复尝试

### 5.3 发版后发现严重问题
1. 立即在 Release 页面标记为预发布或删除 Release
2. 评估影响范围和严重程度
3. 紧急修复后发补丁版本（升 patch 号）
4. 在 CHANGELOG 中记录回滚/修复说明

---

## 六、CI 工作流设计（参考）

### 6.1 main CI（ci.yml）
- 触发：main push / PR
- 任务：预检查 + 编译 + 单测 + Lint
- 不打包，无需官方签名密钥（用 debug 密钥兜底配置）
- 目的：每次提交都验证代码质量，快速发现问题

### 6.2 Release CI（android-release.yml）
- 触发：tag 推送
- 任务：签名恢复 + 单测 + Lint + 打包 + 签名校验 + 上传 Release
- 需要官方签名密钥（从 GitHub Secrets 恢复）
- 目的：构建正式发布包并上传到 GitHub Release

### 6.3 密钥安全
- 所有密钥通过 GitHub Secrets 注入，禁止硬编码
- keystore 文件加密存储，不得提交到仓库
- 缺少密钥令牌必须直接向用户索要，不得跳过或用占位符

---

## 七、发版检查清单（快速参考）

### 发版前
- [ ] 所有功能/修复已合并到 main
- [ ] CHANGELOG 已更新（格式正确，无 emoji）
- [ ] 版本号正确（四段式，无预发布后缀）
- [ ] 运行 `python3 scripts/ci/pre-release-check.py --version vX.Y.Z` 全部通过
- [ ] main CI 最新构建状态为绿色

### 发版中
- [ ] `git tag -a vX.Y.Z -m "..."` 创建 tag
- [ ] `git push origin vX.Y.Z` 推送 tag
- [ ] 实时监控 CI 构建（每 2-3 分钟检查一次）
- [ ] 所有构建步骤通过

### 发版后
- [ ] Release 页面已创建，APK 已上传
- [ ] APK 签名验证通过（官方证书指纹匹配）
- [ ] 下载安装测试（可选但推荐）
- [ ] 向用户汇报发版结果（版本号、下载链接、主要更新内容）

---

## 八、更新记录

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-10-03 | v1.0 | 初始版本，基于 v0.0.0.48 发版经验整理标准流程 |
