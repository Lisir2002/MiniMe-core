# MiniMe-core 应用打包器 设计文档

> 版本：v1.0 | 日期：2026-10-06 | 状态：设计阶段

## 一、概述

### 1.1 定位

在主应用 MiniMe-core 中集成「应用打包器」功能，用户在手机上即可将 HTML/Vue/React 等前端项目打包为可安装的 Android APK。

### 1.2 核心技术方案

**预编译模版 + 资源替换 + 重签名**（方案 C）

- 预编译通用模版 APK（即 app-template 模块产物），内置在主应用 assets 中
- 构建时解压模版 APK，替换网页资源、图标、修改包名和应用名
- 重新打包并签名，输出最终 APK

### 1.3 关键决策

| 决策项 | 结论 |
|--------|------|
| 构建方式 | 预编译模版 + 资源替换（方案 C） |
| 包名 | 不共用模版包名，每个项目单独命名 |
| 入口位置 | 侧边栏入口，进入独立页面 |
| P0 范围 | 项目列表 + 新建 HTML 单文件 + 基础构建 + 下载 APK |
| 模版 APK | 内置在主应用 assets 中（约 300KB） |

---

## 二、整体架构

### 2.1 模块划分

```
app-packager/                    # 应用打包器模块
├── ui/                          # 页面与 UI
│   ├── ProjectListPage.kt       # 项目列表页
│   ├── NewProjectWizard.kt      # 新建项目向导
│   ├── ProjectDetailPage.kt     # 项目详情页（Tab）
│   ├── BuildProgressPage.kt     # 构建过程页
│   ├── TemplateMarketPage.kt    # 模板市场页（P2）
│   └── SignatureManagePage.kt   # 签名管理页（P1）
├── engine/                      # 构建引擎
│   ├── ApkBuilder.kt            # 构建编排器
│   ├── ApkModifier.kt           # APK 资源修改器（解压/替换/重打包）
│   ├── ManifestEditor.kt        # 二进制 AXML 包名/应用名修改器
│   ├── ApkSigner.kt             # APK 签名器（封装 apksigner）
│   └── ZipAligner.kt            # ZIP 对齐器
├── model/                       # 数据模型
│   ├── Project.kt               # 项目配置
│   ├── BuildRecord.kt           # 构建记录
│   └── Signature.kt             # 签名信息
├── store/                       # 数据存储
│   ├── ProjectStore.kt          # 项目持久化
│   └── BuildStore.kt            # 构建记录持久化
└── util/
    ├── PinyinUtil.kt            # 拼音转换（包名自动生成）
    └── IconGenerator.kt         # 应用图标生成
```

### 2.2 数据存储目录结构

```
应用私有目录/files/packager/
├── projects/
│   ├── {project_id}/
│   │   ├── config.json          # 项目配置
│   │   ├── www/                 # 网页源文件
│   │   │   └── index.html
│   │   ├── icon.png             # 自定义应用图标（可选）
│   │   └── builds/              # 构建产物
│   │       └── {timestamp}/
│   │           ├── build.log
│   │           └── output.apk
├── templates/
│   └── template-base.apk        # 预编译模版 APK（从 assets 复制）
├── signatures/
│   └── default.keystore         # 默认签名密钥
└── cache/
    └── temp/                    # 构建临时目录
```

---

## 三、页面设计

### 3.1 入口：侧边栏

在主应用侧边栏中添加「应用打包器」入口，点击进入独立的项目列表页。

```
侧边栏
├── 对话历史
├── 收藏
├── ...
├── 应用打包器    ← 新增入口
├── 终端
├── Git
├── 浏览器
└── 设置
```

### 3.2 项目列表页

**布局：**

```
┌─────────────────────────────┐
│ ← 应用打包器            [+] │
├─────────────────────────────┤
│                             │
│  ┌───────────────────────┐  │
│  │ 📱 我的博客            │  │
│  │ 纯HTML · v1.0.0       │  │
│  │ 上次构建: 2小时前 ✓    │  │
│  │ [立即构建] [配置] [⋮] │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │
│  │ 🌐 工具集合站          │  │
│  │ Vue · v0.1.0          │  │
│  │ 上次构建: 昨天 ✗       │  │
│  │ [立即构建] [配置] [⋮] │  │
│  └───────────────────────┘  │
│                             │
│         暂无更多项目          │
│                             │
│    [+ 新建项目]              │
└─────────────────────────────┘
```

**功能点：**
- 项目卡片列表，显示图标、名称、类型、版本、最后构建状态
- 快捷操作：立即构建、配置、更多（重命名/删除/查看构建历史）
- 右上角「+」和底部「新建项目」按钮
- 下拉刷新

### 3.3 新建项目向导

分步式向导，共 5 步，顶部显示进度指示器。

**Step 1：选择项目类型**

```
┌─────────────────────────────┐
│ ← 新建项目 (1/5)      [跳过]│
├─────────────────────────────┤
│                             │
│  选择项目类型                 │
│                             │
│  ┌─────────┐ ┌─────────┐   │
│  │  📄     │ │  📦     │   │
│  │ 纯HTML  │ │  Vue    │   │
│  │ 单文件   │ │  dist   │   │
│  └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐   │
│  │  ⚛️     │ │  🎨     │   │
│  │  React  │ │  模板   │   │
│  │  build  │ │  创建   │   │
│  └─────────┘ └─────────┘   │
│                             │
│  P0 仅支持「纯HTML」，其余   │
│  类型将在后续版本支持         │
│                             │
│                    [下一步]  │
└─────────────────────────────┘
```

**Step 2：基本信息**

| 字段 | 说明 | 校验 |
|------|------|------|
| 应用名称 | 用户填写，中文/英文均可 | 非空，1-50 字符 |
| 包名 | 自动从应用名生成拼音，可编辑 | 必须符合 Java 包名规范：`com.xxx.xxx`，至少两段，每段以字母开头，仅含字母数字下划线 |
| 版本号 | 默认 `1.0.0` | 语义化版本 `x.y.z` |
| 应用图标 | 可选，从相册选择或自动生成 | 推荐 512x512 PNG |

**包名自动生成规则：**
- 应用名转拼音，小写，空格转下划线
- 前缀统一 `com.minime.app.`
- 例如：「我的博客」→ `com.minime.app.wodeboke`
- 如包名已存在，自动追加数字后缀

**Step 3：导入源文件**

P0 阶段：
- 选择本地 HTML 文件（从文件管理器选择）
- 或直接在编辑器中粘贴/编写 HTML 代码

后续版本：
- 导入 ZIP 压缩包（自动解压到 www 目录）
- 导入目录
- 从 Git 仓库克隆

**Step 4：能力配置**

P0 阶段简化为：
- Bridge 能力总开关（默认全开）
- 权限配置：常用权限快速选择（网络/存储/相机/定位/通知），每项带中文说明

后续版本：
- 完整的 Bridge 模块级开关（UI/设备/文件/网络/数据）
- 全量权限列表（30+ 项，带用途说明）
- 外观配置（状态栏/导航栏颜色、沉浸模式、横竖屏）

**Step 5：确认创建**

- 汇总展示所有配置信息
- 「创建项目」按钮，创建后自动跳转到项目详情页

### 3.4 项目详情页

顶部 Tab 切换：概览 / 源文件 / 配置 / 构建

**Tab 1：概览**
- 项目基本信息卡片（名称、包名、版本、类型、创建时间）
- 最近构建记录（最近 3 条，状态/时间/版本/大小）
- 快捷操作：立即构建、下载最新 APK、查看所有构建

**Tab 2：源文件**
- 文件列表（www 目录结构）
- 点击文件可预览（HTML 文本/图片）
- P0：替换 index.html 文件
- 后续：上传/删除/重命名文件，新建文件夹

**Tab 3：配置**
- 基本信息修改（应用名、版本号、图标）
- 包名不可修改（修改包名需要重新构建，且已安装的应用无法覆盖安装）
- Bridge 能力开关
- 权限配置
- 签名选择（P1）

**Tab 4：构建**
- 构建历史列表（时间、状态、版本号、产物大小）
- 成功的构建：下载 APK、查看日志、分享
- 失败的构建：查看日志、重新构建
- 「立即构建」按钮

### 3.5 构建过程页

点击「立即构建」后进入，显示实时构建进度。

```
┌─────────────────────────────┐
│ ← 构建中...                 │
├─────────────────────────────┤
│                             │
│      ⏳ 正在构建             │
│                         │
│      ████████░░ 70%         │
│                         │
│  ✓ 1. 初始化构建环境         │
│  ✓ 2. 解压模版 APK          │
│  ✓ 3. 替换网页资源          │
│  ⏳ 4. 修改包名与应用名      │
│  ○ 5. 替换应用图标          │
│  ○ 6. 重新打包              │
│  ○ 7. ZIP 对齐              │
│  ○ 8. APK 签名              │
│  ○ 9. 输出 APK              │
│                             │
│  [查看详细日志]  [后台运行]  │
└─────────────────────────────┘
```

**构建成功后：**
- 显示成功动画
- 显示 APK 信息（文件名、大小、包名、版本、签名）
- 操作按钮：安装 APK、分享 APK、查看文件位置

**构建失败后：**
- 显示失败步骤和错误信息
- 操作按钮：查看详细日志、重新构建

---

## 四、构建引擎设计

### 4.1 构建流程

```
┌─────────────────────────────────────────────────────┐
│                    ApkBuilder.build()                │
├─────────────────────────────────────────────────────┤
│                                                     │
│  1. 初始化                                           │
│     ├─ 创建临时构建目录                               │
│     ├─ 复制模版 APK 到临时目录                        │
│     └─ 记录开始时间                                   │
│                                                     │
│  2. 解压 APK（ZipFile 流式读取）                     │
│     └─ 解压到 temp/extracted/                        │
│                                                     │
│  3. 替换网页资源                                     │
│     ├─ 删除 temp/extracted/assets/www/              │
│     └─ 复制项目 www/ 到 temp/extracted/assets/www/  │
│                                                     │
│  4. 修改 AndroidManifest.xml（二进制 AXML）          │
│     ├─ 修改 package 属性（包名）                      │
│     ├─ 修改 android:label 属性（应用名）              │
│     └─ 修改/添加 uses-permission（权限）              │
│                                                     │
│  5. 替换应用图标（P1）                                │
│     └─ 替换 res/mipmap-*/ic_launcher.png            │
│                                                     │
│  6. 重新打包 ZIP                                     │
│     ├─ 流式写入，不压缩 assets/（已压缩）             │
│     └─ 输出 temp/unsigned.apk                        │
│                                                     │
│  7. ZIP 对齐（zipalign）                             │
│     └─ 输出 temp/aligned.apk                         │
│                                                     │
│  8. APK 签名（apksigner）                            │
│     ├─ 使用指定签名密钥                               │
│     └─ 输出 temp/signed.apk                          │
│                                                     │
│  9. 输出最终 APK                                     │
│     ├─ 复制到项目 builds/{timestamp}/output.apk      │
│     ├─ 同时复制到 Download 目录（供用户安装）         │
│     └─ 清理临时目录                                   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 二进制 AXML 修改器（ManifestEditor）

这是最核心的技术难点。APK 中的 `AndroidManifest.xml` 是二进制 AXML 格式，不是文本 XML。

**AXML 格式结构：**

```
┌──────────────────────────────────────┐
│ AXML 文件结构                         │
├──────────────────────────────────────┤
│ 文件头 (8 bytes)                      │
│   - magic: 0x00080003               │
│   - file_size                         │
├──────────────────────────────────────┤
│ String Pool Chunk                     │
│   - chunk_header (8 bytes)           │
│   - string_count                      │
│   - style_count                       │
│   - flags (UTF-8 / UTF-16)          │
│   - strings_start_offset              │
│   - styles_start_offset               │
│   - string_offsets[] (string_count)  │
│   - style_offsets[] (style_count)    │
│   - string_data (变长)                │
│   - style_data (变长)                 │
├──────────────────────────────────────┤
│ Resource Map Chunk (可选)             │
├──────────────────────────────────────┤
│ XML Content Chunks (多个)             │
│   - START_NAMESPACE                  │
│   - START_ELEMENT                     │
│   - END_ELEMENT                       │
│   - TEXT                              │
└──────────────────────────────────────┘
```

**修改策略：**

包名和应用名都存储在 String Pool 中。修改策略分两种情况：

**情况 A：新字符串长度 ≤ 旧字符串长度**
- 直接在 String Pool 的字符串数据区原地替换
- 新字符串后面补零填充
- 不需要修改 string_offsets 和文件大小
- 这是最常见的情况（模版包名 `com.minime.template` 20 字符，大部分用户包名 ≤ 20 字符）

**情况 B：新字符串长度 > 旧字符串长度**
- 需要重建整个 String Pool
- 重新计算 string_offsets
- 更新 chunk size 和 file size
- 后续所有 chunk 的偏移量不变（因为 String Pool 在文件开头，大小变化只影响 file_size 字段，后续 chunk 是相对偏移还是绝对偏移需要确认）

**实现要点：**
1. 解析 String Pool，找到目标字符串的索引和数据偏移
2. 读取字符串内容，确认是目标包名/应用名
3. 根据长度选择替换策略
4. 写回文件
5. 校验：重新解析修改后的 AXML，确认包名已正确修改

**权限修改：**
- 添加权限需要在 XML Content 中插入新的 `uses-permission` 元素
- 这比修改字符串复杂，需要重建 XML Content Chunk
- P0 阶段可以先不修改权限，使用模版默认声明的全量权限
- P1 阶段再实现权限的增删

### 4.3 APK 签名器（ApkSigner）

**方案：集成 apksigner 的纯 Java 实现**

apksigner 是 Android SDK Build Tools 中的工具，本身是纯 Java 实现（`apksigner.jar`）。可以将其核心类打包到主应用中。

**替代方案：使用 BouncyCastle 手动实现签名**

如果 apksigner 体积太大或依赖复杂，可以使用 BouncyCastle 库手动实现 APK 签名：
- V1 签名（JAR 签名）：基于 BouncyCastle 的 CMS/PKCS7 实现
- V2 签名（APK Signature Scheme v2）：需要实现 APK 分块哈希和签名块格式

**推荐：P0 阶段使用 V1 签名**（兼容性最好，实现相对简单），P1 阶段再支持 V2/V3 签名。

**签名密钥管理：**
- 首次使用时自动生成默认签名密钥（RSA 2048，有效期 25 年）
- 密钥存储在应用私有目录 `signatures/default.keystore`
- 密钥密码用 Android Keystore 加密保存
- P1 阶段支持用户导入自定义签名和管理多个签名

### 4.4 ZIP 对齐（ZipAligner）

**方案：手动实现 4 字节对齐**

zipalign 的原理很简单：确保 APK 中所有未压缩的文件数据相对于文件起始位置的偏移量是 4 的倍数。这样系统可以使用 mmap 直接读取，减少内存占用。

实现步骤：
1. 读取 ZIP 文件的 Central Directory
2. 对每个未压缩的文件，计算其数据偏移量
3. 如果偏移量不是 4 的倍数，在文件数据前插入填充字节
4. 更新 Local File Header 和 Central Directory 中的偏移量
5. 写回文件

这个实现不复杂，约 200 行代码。

---

## 五、数据模型

### 5.1 Project（项目配置）

```kotlin
data class Project(
    val id: String,                    // UUID
    val name: String,                  // 应用名称
    val packageName: String,           // 包名（唯一）
    val versionName: String,           // 版本号，如 "1.0.0"
    val versionCode: Int,              // 版本号整数，如 1000000
    val type: ProjectType,             // 项目类型：HTML / VUE / REACT
    val iconPath: String?,             // 自定义图标路径（可选）
    val bridgeEnabled: Boolean,        // Bridge 能力总开关
    val permissions: List<String>,     // 声明的权限列表
    val signatureId: String?,          // 使用的签名 ID（P1）
    val createdAt: Long,               // 创建时间
    val updatedAt: Long,               // 最后更新时间
    val lastBuildAt: Long?,            // 最后构建时间
    val lastBuildStatus: BuildStatus?  // 最后构建状态
)

enum class ProjectType { HTML, VUE, REACT, TEMPLATE }
enum class BuildStatus { SUCCESS, FAILED, BUILDING }
```

### 5.2 BuildRecord（构建记录）

```kotlin
data class BuildRecord(
    val id: String,                    // UUID
    val projectId: String,             // 所属项目 ID
    val versionName: String,           // 构建时的版本号
    val versionCode: Int,
    val status: BuildStatus,           // 构建状态
    val startTime: Long,               // 开始时间
    val endTime: Long?,                // 结束时间
    val durationMs: Long?,             // 构建耗时
    val apkSize: Long?,                // 产物大小（字节）
    val apkPath: String?,              // 产物路径
    val logPath: String?,              // 构建日志路径
    val errorMessage: String?          // 失败时的错误信息
)
```

---

## 六、分阶段落地计划

### P0：基础可用（当前目标）

**功能范围：**
- ✅ 侧边栏入口
- ✅ 项目列表页（增删查）
- ✅ 新建项目向导（HTML 单文件，基本信息，源文件粘贴/选择，简化能力配置）
- ✅ 项目详情页（概览 / 源文件 / 配置 / 构建 四个 Tab）
- ✅ 构建引擎：解压 → 替换 www 资源 → 修改包名/应用名 → 重打包 → 签名 → 输出
- ✅ 构建过程页（进度显示 + 日志）
- ✅ 构建历史管理
- ✅ APK 下载到 Download 目录
- ✅ 模版 APK 内置在 assets

**技术实现：**
- ManifestEditor：支持包名和应用名修改（情况 A 原地替换 + 情况 B 重建 String Pool）
- ApkSigner：V1 签名（BouncyCastle 实现）
- ZipAligner：手动实现 4 字节对齐
- 默认签名密钥自动生成

**不包含：**
- 图标替换（使用模版默认图标）
- 权限增删（使用模版默认全量权限）
- Vue/React 项目支持
- 模板市场
- 自定义签名管理

### P1：能力增强

- 应用图标替换（自定义图标 + 自动生成不同分辨率）
- 权限配置（增删权限，ManifestEditor 支持权限修改）
- 签名管理页面（生成/导入/切换签名）
- 源文件管理增强（多文件、目录、ZIP 导入）
- Bridge 模块级开关
- 外观配置（状态栏/导航栏颜色、沉浸模式）
- V2/V3 签名支持

### P2：生态扩展

- 模板市场（官方预设模板，在线更新）
- Vue/React 项目支持（dist/build 目录导入）
- Git 仓库导入
- 构建日志增强（可搜索、可导出）
- 项目导出/导入（备份分享）

### P3：高级功能

- 多渠道打包（一次构建多个渠道包）
- 应用加固集成
- 云端构建（可选，作为本地构建的补充）
- 应用商店发布辅助
- 构建性能优化（增量构建、缓存）

---

## 七、技术难点与风险

| 难点 | 风险等级 | 解决方案 |
|------|----------|----------|
| 二进制 AXML 包名修改 | 🔴 高 | 自研 ManifestEditor，参考开源实现，充分测试各种包名长度 |
| APK 签名（V1/V2） | 🟡 中 | P0 用 V1 签名（BouncyCastle），P1 再支持 V2 |
| 大文件内存溢出 | 🟡 中 | 流式处理 ZIP，分批读写，避免一次性加载整个 APK |
| 构建耗时 | 🟢 低 | 资源替换 + 签名通常 10-30 秒，显示进度条 |
| 签名密钥安全 | 🟡 中 | 密钥存应用私有目录，密码用 Android Keystore 加密 |
| 包名冲突 | 🟢 低 | 创建时校验包名唯一性，已存在自动追加数字后缀 |
| 安装覆盖问题 | 🟡 中 | 包名或签名变更后无法覆盖安装，需提示用户卸载旧版 |

---

## 八、与 app-template 模块的关系

| 维度 | app-template（附属应用） | app-packager（主应用功能） |
|------|--------------------------|---------------------------|
| 定位 | 独立的模版壳应用 | 主应用中的打包器功能 |
| 产物 | 模版 APK（被打包器使用） | 最终用户 APK |
| 构建 | GitHub Actions 云端构建 | 手机端本地构建 |
| 更新 | 发版更新模版 APK | 从 assets 读取，随主应用更新 |
| 包名 | `com.minime.template`（固定） | 用户自定义（每个项目不同） |

**模版 APK 更新机制：**
- 主应用每次发版时，将最新的 app-template APK 放入 assets
- 打包器首次使用时，将模版 APK 从 assets 复制到私有目录
- 后续构建直接使用私有目录中的模版 APK
- 主应用更新后，检测到模版 APK 版本变化，自动更新私有目录中的模版

---

## 九、验收标准

### P0 验收清单

- [ ] 侧边栏有「应用打包器」入口，点击进入项目列表页
- [ ] 可以创建 HTML 单文件项目（填写应用名、自动生成包名、粘贴/选择 HTML）
- [ ] 项目列表正确显示所有项目
- [ ] 项目详情页四个 Tab 正常切换
- [ ] 点击「立即构建」后显示构建进度
- [ ] 构建成功后生成 APK，包名和应用名正确
- [ ] APK 可以正常安装和运行
- [ ] 构建历史正确记录
- [ ] 构建失败时显示错误信息和日志
- [ ] 模版 APK 内置在 assets 中，无需网络下载
- [ ] 无内存泄漏，连续构建 5 次不崩溃
