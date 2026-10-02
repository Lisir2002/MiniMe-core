# sora-editor + TextMate 全语言方案 — 迁移报告（阶段 0-2）

> 执行时间：2026-10-02
> 目标：全面抛弃 tree-sitter，落地 sora-editor + TextMate 全语言方案

---

## 阶段 0：全量语言清单 ✅

**输出**：`docs/editor-migration/phase0-language-list.md`

- 数据源：tm-grammars v1.32.22（Shiki 官方 TextMate grammar 集合）、VS Code 内置、GitHub Linguist
- **260 个 TextMate grammar 文件**（含 injection），覆盖 ~216 种独立语言类型
- 分类：编程语言（系统/脚本/函数式）、Web 前端、数据格式、配置构建、数据库、其他
- 频率分级：🔴 必备高频 45 种 / 🟡 次高频 63 种 / 🟢 低频 108 种
- 特别覆盖：PHP、HTML、JavaScript、TypeScript、Python、Java、Kotlin、C/C++、Rust、Go、Swift 等

---

## 阶段 1：基础架构搭建 ✅

### 1.1 依赖配置
- `gradle/libs.versions.toml`：
  - `soraEditor = "0.24.4"`（新 group ID `io.github.rosemoe`）
  - `desugarJdkLibs = "2.1.5"`
  - 新增 `sora-editor-bom` / `sora-editor` / `sora-language-textmate` / `desugar-jdk-libs`
- `app/build.gradle.kts`：
  - `isCoreLibraryDesugaringEnabled = true`（API<33 必需）
  - `coreLibraryDesugaring(libs.desugar.jdk.libs)`
  - `implementation(platform(libs.sora.editor.bom))` + editor + language-textmate

### 1.2 TextMateManager
- 路径：`feature/editor/textmate/TextMateManager.kt`
- 单例，Application.onCreate 异步预热段初始化（不阻塞首帧）
- 功能：注册 AssetsFileResolver、加载 5 套主题、加载 260 grammar、创建 TextMateLanguage、切换主题

### 1.3 Grammar 资源
- 路径：`assets/editor/grammars/*.json`（260 个文件）
- 体积：12 MB 未压缩（JSON 文本，APK 内 gzip 后约 2-3 MB）
- 对比 tree-sitter so：17.3 MB → 节省约 14+ MB

### 1.4 主题资源
- 路径：`assets/editor/themes/`
- 5 套：dark-plus（默认深色）、light-plus（默认浅色）、github-dark、github-light、dracula

### 验证
- ✅ `./gradlew :app:compileReleaseKotlin` 通过
- ✅ sora-editor 依赖正确解析
- ✅ TextMateManager 编译通过
- ✅ grammar/theme 资源已就位

---

## 阶段 2：共享内核 ✅

### 2.1 CodeDocument
- 路径：`feature/editor/core/CodeDocument.kt`
- 纯文本模型（View/Edit 无感切换，不绑定 UI）
- 功能：行级存储、脏标记 Flow、变更通知 Flow、文件读写、UTF-8 BOM 检测、大文件标记（>500KB）
- 工厂：`CodeDocument.fromFile()` 自动检测编码 + 语言

### 2.2 LanguageDetector
- 路径：`feature/editor/detect/LanguageDetector.kt`
- 检测优先级：文件名 → 扩展名 → Shebang → Vim/Emacs modeline → 内容特征 → Plain Text
- 覆盖 ~150 种扩展名映射 + 20 种 shebang 解释器
- 处理扩展名冲突（.m → objc/matlab 靠 shebang+内容区分）

### 2.3 AdaptiveHighlighter
- 路径：`feature/editor/core/AdaptiveHighlighter.kt`
- 在 sora-editor 原生增量高亮 + 后台 worker 之上做策略决策：
  - `FULL`：完整高亮（小文件/静止时）
  - `VISIBLE_ONLY`：仅可见区域（大文件/快速滚动时）
  - `OFF`：极端低内存兜底
- 滚动监控：500ms 采样窗口，>30 行/500ms 自动降级，静止 1.5s 恢复
- 不重复造轮子，只做策略层

### 验证
- ✅ `./gradlew :app:compileReleaseKotlin` 通过
- ✅ CodeDocument 可读写文件（fromFile 工厂方法）
- ✅ LanguageDetector 扩展名+shebang+modeline 检测链路完整
- ✅ AdaptiveHighlighter 策略切换 + 滚动监控编译通过

---

## 待后续阶段完成

- 替换 CodeViewerScreen 的 tree-sitter native 视图为 sora-editor Compose 包装
- 移除 `app/src/main/cpp/` tree-sitter CMake 构建
- 移除 `externalNativeBuild` 配置
- 开源许可页注明 sora-editor (LGPL-2.1)
- 移除 tree-sitter so 文件，验证 APK 体积下降
