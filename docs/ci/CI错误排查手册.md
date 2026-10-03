# CI 错误排查手册

> 本文档整理 MiniMe-core 项目 CI 构建中常见的错误类型、根因分析和修复方法，帮助快速定位和解决 CI 问题。

## 一、CI 工作流概览

### 1.1 工作流分工

| 工作流 | 触发条件 | 主要任务 | 是否需要签名密钥 |
|--------|----------|----------|------------------|
| `ci.yml` | main push / PR | 预检查 + 编译 + 单测 + Lint | 否（用 debug 密钥兜底配置） |
| `android-release.yml` | tag 推送 | 签名恢复 + 单测 + Lint + 打包 + 签名校验 + 上传 Release | 是（从 GitHub Secrets 恢复） |
| `ci-failure-alert.yml` | CI 失败时 | 自动创建 Issue 追踪失败 | 否 |

### 1.2 CI 执行顺序（ci.yml）

1. Checkout（拉取完整历史，用于版本号推导）
2. Set up JDK 17
3. **CI pre-build check**（预检查，自动拦截常见错误）
4. Check skill script syntax（技能脚本语法检查）
5. Persistence static check（数据持久化静态检查）
6. Security gate（加密驱动安全门禁）
7. Generate debug keystore + keystore.properties（CI 配置兜底）
8. Set up Android SDK
9. Set up Gradle
10. **Compile release**（编译验证，不打包）
11. **Run unit tests (release)**（单元测试）
12. **Lint (release)**（Lint 检查）

---

## 二、常见错误类型与修复方法

### 2.1 单元测试失败

#### 错误表现
```
> Task :app:testReleaseUnitTest FAILED
SnippetParserTest > bare placeholder without default FAILED
    java.util.NoSuchElementException
```

#### 根因分析
新增功能时没有覆盖所有边界情况的测试用例。例如 SnippetParser 只处理了 `${1}` 带括号形式和 `$VAR_NAME` 变量形式，漏掉了 `$1` 裸占位符形式。

#### 修复方法
1. 查看失败的测试用例，理解预期行为
2. 修复实现代码，覆盖遗漏的边界情况
3. 添加新的测试用例防止回归

#### 预防措施
- 新增解析器/工具类时，必须覆盖所有边界情况的测试
- CI 预检查脚本可扩展增加测试覆盖率检查

---

### 2.2 Lint 错误 - LocalContextGetResourceValueCall

#### 错误表现
```
> Task :app:lintRelease FAILED
SnippetSettingsScreen.kt:128: Error: Avoid calling getString() directly
    from a Composable function [LocalContextGetResourceValueCall]
```

#### 根因分析
在 Composable 函数中通过 `LocalContext.current.getString()` 获取资源值。Compose Lint 规则要求使用 `stringResource()` 函数获取资源。

#### 修复方法
```kotlin
// 错误写法
@Composable
fun MyScreen() {
    val context = LocalContext.current
    Text(text = context.getString(R.string.hello))  // Lint 错误
}

// 正确写法（无参数）
@Composable
fun MyScreen() {
    Text(text = stringResource(R.string.hello))  // 正确
}

// 正确写法（带参数，需要在 lambda 中使用）
@Composable
fun MyScreen() {
    val context = LocalContext.current  // 在 Composable 顶层获取
    Button(onClick = {
        // lambda 不是 Composable 上下文，可以使用 context
        Toast.makeText(context, context.getString(R.string.hello), Toast.LENGTH_SHORT).show()
    }) {
        Text("Click")
    }
}
```

#### 预防措施
- CI 预检查脚本已自动检测此问题
- 新增 Composable 代码时，优先使用 `stringResource()`

---

### 2.3 编译错误 - @SuppressLint 缺少 import

#### 错误表现
```
> Task :app:compileReleaseKotlin FAILED
e: SnippetSettingsScreen.kt:128:25 Unresolved reference: SuppressLint
```

#### 根因分析
使用了 `@SuppressLint` 注解但没有添加 `import android.annotation.SuppressLint`。通常是在修复 Lint 错误时添加注解但忘记加 import。

#### 修复方法
在文件顶部添加：
```kotlin
import android.annotation.SuppressLint
```

#### 预防措施
- CI 预检查脚本已自动检测此问题
- 使用 IDE 自动导入功能（Alt+Enter）

---

### 2.4 编译错误 - lambda 中调用 Composable 函数

#### 错误表现
```
> Task :app:compileReleaseKotlin FAILED
e: SnippetSettingsScreen.kt:130:18 @Composable invocations can only happen
    from the context of a @Composable function
```

#### 根因分析
在 onClick lambda 中直接调用 `LocalContext.current`（这是一个 Composable 函数），但 lambda 不是 Composable 上下文。

#### 修复方法
```kotlin
// 错误写法
@Composable
fun MyScreen() {
    Button(onClick = {
        val context = LocalContext.current  // 错误：lambda 中不能调用 Composable 函数
        Toast.makeText(context, "Hello", Toast.LENGTH_SHORT).show()
    }) {
        Text("Click")
    }
}

// 正确写法
@Composable
fun MyScreen() {
    val context = LocalContext.current  // 在 Composable 顶层获取
    Button(onClick = {
        Toast.makeText(context, "Hello", Toast.LENGTH_SHORT).show()  // lambda 中使用变量
    }) {
        Text("Click")
    }
}
```

#### 预防措施
- 记住：`LocalContext.current` 只能在 Composable 函数体中调用，不能在 lambda 中调用
- 需要在 lambda 中使用 context 时，在 Composable 顶层获取并保存为变量

---

### 2.5 main CI 签名密钥缺失

#### 错误表现
```
> Task :app:compileReleaseKotlin FAILED
* What went wrong:
A problem occurred evaluating project ':app'.
> release 正式签名密钥缺失：缺少 app/keystore.properties
```

#### 根因分析
`build.gradle.kts` 的 `signingConfigs.release` 块中有 `require(keystorePropertiesFile.exists())` 检查，这个检查在 Gradle 配置阶段就会执行，即使只跑 `compileReleaseKotlin`（不打包）也会触发。而 main CI 没有官方签名密钥（只在 tag 触发的 android-release.yml 中从 GitHub Secrets 恢复）。

#### 修复方法
ci.yml 中添加步骤，创建兜底的 `app/keystore.properties`（指向 debug keystore）：
```yaml
- name: Generate debug keystore + keystore.properties (CI config fallback)
  run: |
    # 生成 debug keystore
    keytool -genkey -v -keystore ~/.android/debug.keystore ...
    # 复制到 app/minime.jks
    cp ~/.android/debug.keystore app/minime.jks
    # 创建 keystore.properties
    cat > app/keystore.properties << EOF
    storeFile=minime.jks
    storePassword=android
    keyAlias=androiddebugkey
    keyPassword=android
    EOF
```

**注意**：ci.yml 只跑编译/单测/Lint，不跑 `assembleRelease`，所以用 debug 密钥兜底是安全的。真正的 release 打包签名由 android-release.yml 从 GitHub Secrets 恢复官方密钥完成。

#### 预防措施
- CI 工作流分层设计：main CI 只做验证不打包，release CI 才做打包签名
- 新增 CI 步骤时，注意 Gradle 配置阶段的检查（如 signingConfigs）

---

### 2.6 Lint 错误 - MissingTranslation

#### 错误表现
```
> Task :app:lintRelease FAILED
strings_git.xml:102: Error: "git_toast_timeout" is not translated
    in "en" (English) [MissingTranslation]
```

#### 根因分析
新增的字符串资源文件（如 `strings_git.xml`）中的字符串没有英文翻译。Android Lint 默认要求所有字符串资源都有英文翻译（如果项目配置了英文 locale）。

#### 修复方法
**方案一（推荐）**：模块专用字符串文件添加 `translatable="false"`：
```xml
<!-- strings_git.xml -->
<resources translatable="false">
    <string name="git_toast_timeout">操作超时，请重试</string>
</resources>
```

**方案二**：在 `values-en/strings.xml` 中添加英文翻译。

#### 预防措施
- CI 预检查脚本已自动检测此问题（所有 `strings_*.xml` 必须有 `translatable="false"`）
- 新增字符串资源文件时，必须在根元素添加 `translatable="false"`（模块专用）或提供英文翻译

---

### 2.7 Lint 错误 - ExtraTranslation

#### 错误表现
```
> Task :app:lintRelease FAILED
values-en/strings.xml:3: Error: "app_name" is translated here but not found
    in default locale [ExtraTranslation]
```

#### 根因分析
英文翻译文件（`values-en/strings.xml`）中有某个字符串的翻译，但默认 locale（`values/strings.xml`）中没有对应的字符串。通常是重构字符串结构（将字符串从主 `strings.xml` 移到模块专用文件）时，没有同步清理英文翻译。

#### 修复方法
**方案一（推荐，中文应用）**：删除 `values-en` 目录，统一不做英文翻译。

**方案二**：在 `values/strings.xml` 中添加对应的字符串，或从 `values-en/strings.xml` 中删除多余的翻译。

#### 预防措施
- CI 预检查脚本已自动检测此问题（禁止存在 `values-en` 目录）
- 重构字符串结构时，同步检查和清理英文翻译

---

## 三、CI 调试技巧

### 3.1 查看 CI 日志

```bash
# 查看最近的 CI 运行列表
gh run list --limit 5

# 查看某个运行的详细状态
gh run view <run_id>

# 查看某个运行的失败日志
gh run view <run_id> --log-failed

# 查看某个 job 的步骤状态
gh run view --job=<job_id>
```

### 3.2 本地复现 CI 错误

```bash
# 本地编译 release（复现编译错误）
./gradlew :app:compileReleaseKotlin --no-daemon

# 本地运行 release 单元测试（复现测试错误）
./gradlew :app:testReleaseUnitTest --no-daemon

# 本地运行 release Lint（复现 Lint 错误）
./gradlew :app:lintRelease --no-daemon

# 运行 CI 预检查脚本（复现预检查错误）
python3 scripts/ci/pre-build-check.py
```

### 3.3 发版前完整验证

```bash
# 运行发版前强制验证清单（包含本地编译/测试/Lint）
python3 scripts/ci/pre-release-check.py --version v0.0.0.48

# 快速检查（跳过本地构建，仅用于快速检查）
python3 scripts/ci/pre-release-check.py --version v0.0.0.48 --skip-local-build
```

---

## 四、CI 设计原则

### 4.1 分层设计
- **main CI**：只做验证（编译+单测+Lint），不打包，无需官方签名密钥
- **release CI**：做完整发版（签名恢复+单测+Lint+打包+签名校验+上传）

### 4.2 快速失败
- 预检查步骤放在最前面，几秒内拦截常见错误，避免等到十几分钟后才失败
- 编译、单测、Lint 按顺序执行，前一步失败不继续后面的步骤

### 4.3 签名安全
- 官方签名密钥只通过 GitHub Secrets 注入，不硬编码、不提交到仓库
- main CI 用 debug 密钥兜底配置（仅用于配置阶段通过，不用于打包）
- release CI 构建后验证签名证书指纹，防止用错误密钥打包

### 4.4 可维护性
- 所有 CI 检查逻辑尽量脚本化（`scripts/ci/` 目录），便于本地复现和调试
- CI 工作流文件中添加详细注释，说明每个步骤的目的和设计原因
- 常见错误整理到本文档，便于快速排查

---

## 五、更新记录

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-10-03 | v1.0 | 初始版本，整理 v0.0.0.48 发版过程中遇到的 7 类 CI 错误 |
