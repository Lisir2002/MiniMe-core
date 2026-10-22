# MiniMe-core 变更记录

## 版本线（三条，互不干扰）

| 线 | 谁定 | 格式 | 当前取值 |
|---|---|---|---|
| **软件发版版本**（git tag / Release） | **维护者确认** | **四段式 `x.x.x.x`**，A=颠覆性预留 / B=框架级结构性预留 / C=框架级重构 / **D=默认发版 +1** | 体系 `0.0.1.x`，已发至 `v0.0.1.10` |
| **引擎版本**（`MiniMeEngine.VERSION`） | 引擎自定 | **二段式 `主.次`，步长 +0.1，逢九进一** | `1.2`（R3；R3.5 纯结构重整不变） |
| 主题包版本（`manifest.version`） | 主题作者自定 | 自由 | 默认包 `1.0.0` |

### 发版三条硬规矩（2026-10-21 维护者裁定）

1. **软件发版一律走项目四段式规范**，禁止自创三段式 SemVer。
2. **按项目规矩不允许发 rc tag**（CI `android-release.yml` 拒绝预发布 tag，只发正式版）——本批次不打 `-rcN` tag。
3. **大更新期间需要中间版本做测试迭代**：每次发版前**必须先询问维护者、由维护者确认发版版本号**，确认后才打 tag。不得自行决定版本号与是否发版。

> 引擎版本是另一条线，**只用于主题包 `manifest.engineVersion` 兼容判定**，不得当软件发版版本用。
> 二段式逢九进一：R1 `1.0` → R2 `1.1` … R9 `1.8` → R10 `1.9` → 转正 `2.0`（不存在 `1.10`）。

## R4.1 主题调节与工坊：运行时覆写层（引擎侧底座）— 已完成，待发版

**引擎版本 `1.2`（不变）** —— 按批次 charter 拆分，`1.3` 随 R4.3（导入导出 + 防改坏收尾）落定；主题包 `engineVersion` 区间 `>=1.0 <2.0` 不变
单测：**:theme-engine 134 → 147（OverlayApplierTest 13 项）+ :theme-ui 37 → 44（ThemeManagerTest +7 项）= 191 项全绿** ｜ :minime-ui 7 项不变 ｜ **`:app` 侧门禁全绿**（compileReleaseKotlin / testReleaseUnitTest / detekt / lintRelease）
**软件发版状态：未发版** —— R4 整批完成后按维护者裁定发 `0.0.1.11-rc1`（本次发版绕过 rc tag 规矩，CI tag 守卫加一次性 allowlist）。

### 交付范围（对照 charter §7 R4.1 行）

| 交付项 | 状态 | 落点 |
|---|---|---|
| 覆写层装配 | ✅ | `compiler/OverlayApplier`：覆写 Map → 合成 `overrides` set 挂 `resolutionOrder` **最末**（modifier < overlay），走既有编译流水线 |
| 共享类型判定 | ✅ | `schema/TokenTypeCheck`：解析/别名/覆写三处共用的类型一致性判定单一真源（`AliasResolver` 原私有 `matchesType` 抽出委托） |
| 运行时入口 | ✅ | `runtime/ThemeManager`：`overlays: StateFlow` + `applyOverrides(entries)` / `resetOverrides(paths)` + 解析缓存 + 缓存键含覆写 |
| 防改坏 | ✅ | 编译失败 → 快照保持上一版（G1）+ **覆写表回滚到上一版**（overlays 与快照一致）+ `ThemeStatus.Failed` 报 §5.2 错误码 |
| 会话语义 | ✅ | 覆写纯内存态（不写 Store/Repository，默认包零污染）；`select` / `importAndApply` / `initialize` 建立新基础即清覆写 |

### 验收逐条（对照 charter §5，R4.1 覆盖项）

| 验收项 | 状态 | 覆盖 |
|---|---|---|
| 1 覆写 primitive 后语义别名换值（§5.1 规则 1 运行时证明） | ✅ JVM | `OverlayApplierTest.overridingPrimitiveRevaluesSemanticAliases`（accent 跟随 systemBlue）+ `ThemeManagerTest.applyOverridesAppliesWithAliasFollowing` |
| 2 复合字段（glass）覆写正确 | ✅ JVM | `OverridingGlassRecipeReplacesComposite` / `OverridingGlassScalarTokenRevaluesCompositeFieldAlias`（tintAlpha 字段别名跟随）+ spring/typography 覆写 |
| 6 一键还原 / 子系统重置 / 覆写不污染默认包 | ✅ JVM | `ResetOverridesRestoresBaseValues` / `ResetOverridesByPathKeepsTheRest` / `OverlayDoesNotLeakIntoBasePackObject` |
| 3/4/5/7/8 | ⏳ | round-trip（R4.3）/ WCAG 爆炸回滚面板化（R4.2–R4.3）/ 真机零 ANR（R4.2 后）/ lint 目录外零新增（随 R4.2 UI）/ 引擎版本 1.3（R4.3） |

### 关键实现决策与偏离（R4.1）

1. **模型层合成集，而非 JSON 序列化**（对 charter §3「序列化为 overlay JSON 走 compileInto」的手段 refinement）——`compileInto` 的输入本就是 `ThemePack` 模型（`compileJson` = parse + compile），把覆写挂成合成集后直接走 `compiler.compile`，与走 JSON **同一流水线**（SetMerger 按序合并 → AliasResolver 先合并后解析 → 校验全行）。JSON 序列化推迟到 R4.3 导出 diff 主题包真正需要时再造——与 R2「不做磁盘快照缓存」同款决策：现在造序列化层收益（本批无消费方）不足以抵消出错面。覆写值经 `TokenTypeCheck`（与别名解析共用）+ 值类构造器（hex 规范化 / 单位白名单）保证一致性，不依赖解析器兜底。
2. **节流复用 `select` 的「取消前者」语义**（对 charter「150ms 节流」的手段 refinement）——验收项是「面板操作零 ANR」：连续覆写取消在途编译、只让最后一次生效，比 150ms debounce 更强（零排队且零尾延迟）；编译在 `Dispatchers.Default`（p50 2.4ms），主线程零负担。
3. **覆写路径限标准词汇表 172 条**——开放词汇（editor/terminal/chart 扩展组）由主题包整体提供，覆写涉及扩展组合并语义与冲突判定，推迟到后续批次（`SCHEMA_INVALID` 明示，不放行静默失败）。
4. **失败回滚覆写态**——防改坏不仅保快照，还把 `overlays` 表回滚到上一版，保证「面板 diff 显示 ↔ 实际生效」一致；被更新请求取消的在途编译不触发回滚（CancellationException 早退，新请求已持更新态）。
5. **覆写随基础包切换清零**——选包 = 用该包原初面貌（工坊会话结束）；导入同理（导入包的差异已成为包内字面量，无需再叠覆写）。
6. **解析缓存 `packFor(json)`**——连续覆写免重复解析同一份 JSON；`invalidate` 同步清（包更新后旧解析不残留）。
7. **合成集名冲突防御**——基础包含名为 `overrides` 的集时明示拒绝（否则 resolutionOrder 重复引用的校验报错难定位）。

---

## R2 运行时 + M3 映射 — 已完成，待发版

**引擎版本 `1.1`** ｜ 新增模块：`:theme-ui`（Android library，AGP 8.9.3 / Kotlin 2.2.21 / Compose BOM 2025.12.01 / compileSdk 35）
单测：**:theme-ui 28 项 + :theme-engine 131 项 = 159 项全绿** ｜ 仪器测试：**5 项已编写，需真机运行**
**软件发版状态：未发版** —— 按规矩须先向维护者确认版本号后再打 tag。

### 交付范围（分批实施计划 §3 R2 原文）

| 交付项 | 状态 | 落点 |
|---|---|---|
| `ThemeManager` | ✅ | `runtime/ThemeManager`：StateFlow + 内存快照缓存 + 后台编译 + 节流（后到者取消前到）+ 降级链 |
| 持久化 | ✅ | `runtime/ThemeStore`（接口，同步可读）+ `SharedPreferencesThemeStore`（Android）+ `InMemoryThemeStore`（测试） |
| 主题包存储 | ✅ | `runtime/ThemePackRepository` + `FileThemePackRepository`（含 packId 路径穿越校验）+ 内存实现 |
| 子系统实例复用 | ✅ | `runtime/SnapshotReuser`（§8.2 重组最小化，换肤性能主路径） |
| 十子系统提供层 | ✅ | `LocalTokens`：`staticCompositionLocalOf` ×10（默认值为 fallback 子系统，未包裹主题也可取到值） |
| 主题入口 | ✅ | `MiniMeTheme`（同时提供十个 local + M3 `MaterialTheme`）、`MiniMeThemeBootstrap`（Android 接入） |
| M3 映射层 | ✅ | `m3/M3Mapping`：color→`ColorScheme`、typography→`Typography`、shape→`Shapes`（§9.1 范围声明：仅此三个子系统） |

### 验收逐条（对照 §3 R2 验收原文）

| 验收项 | 状态 | 覆盖 |
|---|---|---|
| 换肤 ≤1 个组合 pass | 🟡 仪器测试已写 | `ThemeSwitchInstrumentedTest.identicalThemeDoesNotRecomposeConsumers` / `switchRecomposesChangedSubsystemOnly` |
| 无 Activity 重建 | 🟡 仪器测试已写 | `themeSwitchDoesNotRecreateContent`（换肤是数据替换，节点仍在） |
| 冷启动首帧即目标主题 | ✅ JVM 侧 + 🟡 仪器 | `ThemeManagerTest.initializeAppliesPersistedSelectionSynchronously`；仪器 `firstFrameShowsPersistedDarkTheme` |
| 导入 ≤200ms（典型包） | ✅ 实测 | `CompilePerformanceTest`（JVM p50 2.4ms）+ 仪器 `importTypicalPackWithinBudget` |
| 相等子系统复用跳过重组 | ✅ JVM 侧 + 🟡 仪器 | `SnapshotReuserTest`（亮→暗 ≥6 个子系统复用）+ `ThemeManagerTest.switchingToDarkReusesUnchangedSubsystemInstances` |

> 🟡 = 已编写并**编译通过**，但需真机/模拟器执行（本机无设备与模拟器）。

### 关键实现决策与偏离（R2）

1. **不做磁盘快照缓存**（偏离 §8.2「内存 + 磁盘」）——实测编译 p50 **2.4ms**（196 条令牌，JVM 桌面，`CompilePerformanceTest` 固化），冷启动在 `setContent` 前同步编译即可满足「首帧即目标主题」；磁盘缓存需新增 TokenValue 序列化层，收益（ms 级）不足以抵消复杂度与出错面。若后续包规模显著增长（>1000 令牌）再引入。
2. **持久化用 SharedPreferences 而非 DataStore** —— DataStore 是异步 Flow，冷启动 `setContent` 前拿不到值会导致首帧跳变；SP 读同步，满足硬要求。写用 `commit()`（同步落盘）换取进程被杀时选择不丢。
3. **M3 映射走 `Xxx().copy(...)` 而非全参数构造** —— M3 的 `ColorScheme`/`Typography`/`Shapes` 字段集随 BOM 演进，全参数构造升版即断裂。且 `ColorScheme` 无无参构造，故以 `lightColorScheme()`/`darkColorScheme()` 为基底（按 `bg.canvas` 感知亮度自动选基底），未映射的 M3 角色也跟随主题深浅。
4. **`staticCompositionLocalOf` 而非 `compositionLocalOf`** —— 换肤是全树数据替换，值变则整树重组是预期行为；static 只记录读了 local 的组合，全局变更更快。
5. **提供层默认值为 fallback 子系统** —— 任何 composable 未包裹 `MiniMeTheme` 也能取到值（预览与独立组件友好），不静默降级到任意色。
6. **Android DEX 不允许方法名含空格** —— androidTest 会被 dex 化，故仪器测试方法名一律驼峰；纯 JVM 的 unit test 不经 dex，保留「反引号 + 空格」可读性命名。（D8 报 `Space characters in SimpleName ... are not allowed`）
7. **android.jar 的 `org.json` 是 stub** —— Android 自带 `org.json`（方法返回 null），会覆盖真实实现并让解析器拿到 null 的 `sets`；`:theme-ui` 的 unit test 与 androidTest 必须显式注入 `org.json:json`。生产环境用平台实现（无需打包）。
8. **环境**：本机无 Android SDK，已安装 `platforms;android-35` + `build-tools;35.0.0` + `platform-tools` 与 JDK 17（`/opt/android-sdk`，`local.properties` 指向）。

### 已知缺口（R2 不做）

- 无 `:gallery` 模块与可运行 app（R3）；
- 无运行时覆写层（R4 工坊在其之上叠加）；
- 系统深浅色监听未内置（由调用方按系统上下文调用 `select(packId, modifiers)`）。

---

## R4 期工程：subtree 集成 MiniMe-core 主程序仓库 — 已完成，待发版

**引擎版本 `1.2`（不变）** ｜ 2026-10-21 维护者裁定：引擎代码以 `git subtree` 形态合入主程序仓库 `Lisir2002/MiniMe-core` 的 `theme/` 子目录——单一 main、引擎 4 个提交历史完整保留、主程序构建统一管理
**集成前状态**：引擎在本地独立 git 仓库开发（4 提交从未推送）；裁定后删独立脚手架、改构建路径、并主程序 catalog 与 settings

### 集成改动清单

| # | 改动 | 说明 |
|---|---|---|
| 1 | subtree 合入 | `git subtree add --prefix=theme/ . main`，merge commit 保历史；引擎原有 `settings.gradle.kts`/`gradle/`/`gradlew`/`gradle.properties` 独立脚手架全部删除——构建统一走主程序根 |
| 2 | 插件声明改直接式 | 三模块 `alias(libs.plugins.*)` → `id(...) version "..."`（AGP 8.9.3 / Kotlin 2.2.21，与主程序根 `build.gradle.kts` 同源；主程序 `settings.gradle.kts` 的 `pluginManagement.resolutionStrategy` 负责插件模块映射） |
| 3 | 模块路径 | `project(":theme-engine")` → `project(":theme:theme-engine")`；`project(":theme-ui")` → `project(":theme:theme-ui")` |
| 4 | 版本目录统一 | 引擎依赖全部改走主程序 `gradle/libs.versions.toml`：新增 11 条（junit-jupiter / junit-platform-launcher / junit-vintage-engine / robolectric / androidx-test-ext-junit / androidx-test-runner / kotlinx-coroutines-test / androidx-core-ktx / androidx-compose-runtime / androidx-compose-foundation / androidx-compose-ui-test-junit4）；org.json 复用主 catalog `libs.json`（20240303）；`androidx-material3` 更名对齐主 catalog `androidx-compose-material3` |
| 5 | settings include | 主程序 `settings.gradle.kts` 增 `:theme:theme-engine` / `:theme:theme-ui` / `:theme:minime-ui`；`:app` 暂不依赖引擎（接入期排期） |
| 6 | compileSdk 差异 | 引擎三模块保持 **35**（主程序 app 为 36）：CI 门禁只构建 `:app`（`compileReleaseKotlin` + `testReleaseUnitTest` + `lintRelease`），不触碰引擎模块 SDK 校验；将来引擎进 CI 时统一 36（无 API 差异） |

### 验收逐条

| 验收项 | 状态 | 覆盖 |
|---|---|---|
| 全项目配置加载（catalog 引用 / 模块脚本 / 插件映射） | ✅ | 主 wrapper（Gradle 8.14）`help` 通过 |
| 引擎 178 项单测在主程序构建下全绿 | ✅ | `:theme:theme-engine:test` 134 + `:theme:theme-ui:testDebugUnitTest` 37 + `:theme:minime-ui:testDebugUnitTest` 7 |
| `:app` 侧零影响 | ✅ | 未改 `:app` 任何配置；CI 门禁任务不变 |
| 引擎历史完整可溯 | ✅ | `git log theme/` 可见 R1–R3.5 全部提交 |

### 关键实现决策与偏离（集成）

1. **编译走主程序 wrapper 8.14 而非引擎本地 9.3.0**——一个构建只能一个 Gradle 版本；AGP 8.9.3 在 8.14 下与 9.3.0 行为一致（已验证 178 项全绿）。
2. **删引擎独立脚手架而非保留双构建**——子目录 `settings.gradle.kts` 会被 IDE/工具误识别为独立构建，保留即双真源；统一构建是 subtree 集成的目的。
3. **catalog 命名向主程序对齐**（`androidx-compose-material3` 等）——主 catalog 是全构建唯一版本目录，命名不一致会造成「同名不同义」的维护陷阱。
4. **`:app` 不依赖引擎模块**——引擎 R6+ 才进入主程序 UI；提前依赖会让主程序 CI 背上 178 项与 Robolectric（用户已被告知并选择「CI 只跑 :app」的轻形态；引擎测试由本地/引擎 CI 跑）。

---

## R3.5 结构重整：Gallery 嵌入主程序（`:gallery` → `:minime-ui`）— 已完成，待发版

**引擎版本 `1.2`（不变）** ｜ 结构变更：删除 `:gallery`（Android application），新增 `:minime-ui`（Android library）——**本仓库从此无任何 application 模块，不再产出可装机 APK**
单测：**:theme-engine 134 + :theme-ui 37 + :minime-ui 7 = 178 项全绿** ｜ 门禁形态：仪器测试 9 项 **整体退役**，等价覆盖改为 **Robolectric JVM 自包含**（无需设备，CI/本地可跑）
**软件发版状态：未发版** —— 按规矩须先向维护者确认版本号后再打 tag。

### 交付范围（2026-10-21 维护者三板裁定落地）

| 裁定 | 落地 |
|---|---|
| ① 新层定名 `minime-ui`（不叫 gallery-ui） | 模块 `:minime-ui`，`namespace = org.minime.core.ui.gallery` |
| ② 按接入方真实消费形态整理（= MiniMe-core 主程序的旧版 UI 层引擎） | library 形态 + API 五原则（见白皮书 §10.1.1） |
| ③ 不做独立 UI App，只做主程序内置的示例页面群 | `:gallery` 整体删除（app 壳 / MainActivity / res / manifest / androidTest） |

### 搬迁明细（git mv 保历史，11 文件）

| 原（`:gallery`，`org.minime.gallery`） | 新（`:minime-ui`，`org.minime.core.ui.gallery`） |
|---|---|
| `sections/` ×5（SectionContent / TokensOverviewSection / TokenOverviewSpec / DebugPanelSection / PlaceholderSection） | `pages/` ×5（**页面即单元**，函数名保留） |
| `GalleryIcons.kt` / `ThemeModifiers.kt` | 库根包 |
| `GallerySidebar.kt` / `GalleryTopBar.kt` | `nav/` 包（**可选聚合部件**：参考导航，非库内导航状态） |
| `nav/GallerySection.kt` | `nav/GallerySection.kt`（`icon()` internal 扩展随迁） |
| `src/test/.../sections/TokenCoverageTest.kt` | `src/test/.../pages/TokenCoverageTest.kt`（JVM 照跑） |

### 验收逐条

| 验收项 | 状态 | 覆盖 |
|---|---|---|
| 页面群 + 导航部件整体可用 | ✅ JVM | `GalleryEmbedRobolectricTest`（4 项）：侧边栏可达全部已建分区（六分区逐个点击 + 标记文本断言） |
| squircle vs 几何圆角 A/B 并排 | ✅ JVM | `squircle与几何圆角AB并排展示`（配对断言：两类标签均存在且数量相等） |
| 顶栏主题入口切换真实生效 | ✅ JVM | `顶栏主题入口切换真实生效`（点「切换亮暗色」→ 断言「包版本 1.0.0 · dark」） |
| 令牌页全量滚动无崩溃 | ✅ JVM | `令牌页全量滚动无崩溃`（10 次滚到页脚） |
| 令牌区与 §6.1.4 总表机器核对 | ✅ JVM | `TokenCoverageTest`（3 项，172 条双向集合相等） |
| 全量测试门禁 | ✅ | `gradle test` → 178 项 0 失败；`:minime-ui:lintDebug`（abortOnError）通过；`:minime-ui:assembleDebug` AAR 正常 |
| 包名零残留 | ✅ | 全仓扫描 `org.minime.gallery` 零命中 |

### 关键实现决策与偏离（R3.5）

1. **删独立 App**（偏离 R3 的「首个可装机界面」定位）——维护者裁定 Gallery 嵌入主程序、不做单独 UI App；`:gallery` application 整体删除，app 壳（GalleryApplication / MainActivity / res / manifest）不留。后果：本仓库不再有可装机产物，R3 产出的 11MB APK 作废（未发版，无tag，无损失）。
2. **API 五原则**（白皮书新增 §10.1.1）：页面即单元（库内无导航状态）/ 状态外置（ThemeManager 宿主注入，initialize 先于首帧）/ 零 Context 持有 / 可选聚合部件（nav/ 侧边栏顶栏是参考导航）/ 禁第二套实现。
3. **仪器测试 → Robolectric 移植**——删 app 后仪器测试失去 LAUNCHER 载体；整体移植为 JVM 自包含（`createComposeRule` + `RobolectricTestRunner`，vintage engine 跑在 JUnit Platform 下）。原 9 项中 4 项导航/A/B 由 `GalleryEmbedRobolectricTest` 等价覆盖（R2 换肤 5 项仍属接入期，随主程序 UI 落地）。
4. **`unitTests.isIncludeAndroidResources = true` + 官方 `ui-test-manifest`**——library 模块默认 false，Robolectric 会落到默认清单导致 ComponentActivity 无法解析；自建 debug 清单会与官方载体 manifest merger 冲突（exported 属性），故只用官方 `debugImplementation(libs.androidx.compose.ui.test.manifest)`。
5. **release 变体禁用单测**——`ui-test-manifest` 仅 debug 生效，release 跑 UI 测试必失败；`androidComponents.beforeVariants(selector().withBuildType("release")) { enableUnitTest = false }`，Compose UI 测试只跑 debug 变体（`testReleaseUnitTest` 任务已不存在）。
6. **Robolectric 无真实字体度量**——文本测得偏大使侧栏项偏高，默认 470dp 屏高放不下 6 个分区（后两项零高、点击落空）；`@Config(qualifiers = "w840dp-h800dp")` 解决。
7. **`hasText` 默认精确匹配坑**——移植时缩短断言串导致 `performScrollToNode` 找不到节点；全部改 `substring = true`。
8. **包名归位 `org.minime.core.ui.gallery`**——与模块定位（主程序 UI 层引擎的示例面）一致；`sections` → `pages` 更名同步体现「页面即单元」。
9. **版本线不动**——纯结构重整，引擎版本维持 `1.2`（不发版、不打 tag，等维护者确认版本号）。

### 已知缺口（R3.5 不做）

- 真机验收仍缺设备：Robolectric 只覆盖逻辑与语义树，真机观感 / 掉帧测量待接入主程序后随真机批次执行；
- 组件 / 原型 / 动效 / 工坊四分区仍为占位页（R6–R9）；
- 工坊分区（R4）的覆写层在 `:minime-ui` 落地时须遵守状态外置原则（宿主注入 ThemeManager）。

---

## R3 画廊骨架 + 令牌总览 + squircle — 已完成，待发版

> **已被 R3.5 接替**：本批的 `:gallery` application 形态与可装机 APK 在 2026-10-21 维护者裁定后作废（未发版、无 tag、无损失）；页面群整体搬迁至 `:minime-ui` library，见下节 R3.5。本批的引擎侧成果（squircle / 令牌总览 / 调试面板 / 覆盖测试）全部保留。

**引擎版本 `1.2`** ｜ 新增模块：`:gallery`（Android application，minSdk 26 / targetSdk 35）
单测：**:theme-engine 134 + :theme-ui 37 + :gallery 3 = 174 项全绿** ｜ 仪器测试：**9 项已编写（R2 5 + R3 4）并编译通过，需真机运行**
**首个可装机 APK**：`gallery/build/outputs/apk/debug/gallery-debug.apk`（11MB，`org.minime.gallery`，versionName `0.0.0.dev` 未发版标识）
**软件发版状态：未发版** —— 按规矩须先向维护者确认版本号后再打 tag。

### 交付范围（分批实施计划 §3 R3 原文）

| 交付项 | 状态 | 落点 |
|---|---|---|
| gallery app 壳 | ✅ | `GalleryApplication`（引擎 initialize 先于首帧）+ `MainActivity`（edge-to-edge，窗口背景读快照 `bg.canvas`）+ 无网络权限 |
| 右侧边栏导航 | ✅ | `GallerySidebar`（六分区 + 主题摘要卡）/ `GalleryDrawer`（窄屏抽屉降级，黑 × `opacity.scrim`）/ `GalleryTopBar`（常驻主题入口 pill + 菜单） |
| 令牌总览区 | ✅ | `TokensOverviewSection`（~900 行）：172 条逐行，按类型分发 12 类渲染器（色板 / 实样 / 等比条 / 开关实样 / 九档透明度阶梯 / 玻璃拟态近似等） |
| squircle 自实现 | ✅ | `draw/SquircleMath`（lamé 超椭圆纯数学，零 androidx 依赖）+ `ContinuousCornerShape`（Path LRU 缓存 64 条，只读契约） |
| 基础绘制原语 | ✅ | `draw/ContinuousCornerShape.kt` 内 `drawHairline`（厚度 px 取自 `elevation.hairline` 令牌） |
| 调试面板（基础版） | ✅ | `DebugPanelSection`：主题组信息 + 修饰符四开关 + 随批次落地清单 |
| 占位分区 | ✅ | `PlaceholderSection` ×4（组件 / 原型 / 动效 / 工坊，明示批次与建设计划，R6–R9 同速生长） |

### 验收逐条（对照 §3 R3 验收原文）

| 验收项 | 状态 | 覆盖 |
|---|---|---|
| 侧边栏可达全部已建分区 | 🟡 仪器测试已写 | `GalleryNavigationInstrumentedTest.侧边栏可达全部已建分区`（六分区逐个点击 + 标题断言） |
| 令牌区与 §6.1.4 总表逐行一致（机器核对） | ✅ JVM | `TokenCoverageTest`：脚本从词汇表生成的 172 条展示清单与 `TokenVocabulary.ALL` **双向集合相等**（缺项 / 多项 / 重复任一即失败）+ 默认包端到端逐条可读 + 十子系统全覆盖 |
| squircle vs 几何圆角并排 A/B | ✅ JVM + 🟡 仪器 | `SquircleMathTest` 9 项数学性质 + 令牌页 shape 区并排渲染；仪器 `squircle与几何圆角AB并排展示` |
| 静态页真机无掉帧 | 🟡 仪器粗检 + 待真机 | `令牌页全量滚动无崩溃`（30 次滚到底）；静态页无动画，掉帧风险面小 |

> 🟡 = 已编写并**编译通过**，但需真机/模拟器执行（本机无设备与模拟器）。

### 关键实现决策与偏离（R3）

1. **squircle 落 `:theme-ui` 而非 `:gallery`**（偏离模块边界初判）—— 组件族 R6–R9 都要用连续圆角，放 gallery 会形成双真源；按 §10.1 硬纪律「禁第二套实现」移入 `:theme-ui` 的 `draw` 包，gallery 只 import。连带 `drawHairline` 原语同落。
2. **D10 常驻可见取代 debug-only**（偏离初版计划「gallery 仅 debug 入口」）—— 白皮书 v1.9 已拍板 D10 = 常驻可见（是产品功能）：顶栏主题入口 pill（sun/moon + 「亮色/暗色」）宽屏常驻、窄屏进顶栏，非调试彩蛋。
3. **曲率 G2 断为已知取舍** —— lamé 超椭圆近似下端点切向 G1 连续、曲率 G2 断（与 iOS 真 squircle 的解析连续曲率有差距）；白皮书 §3.6 妥协 2 已预警「不可用几何圆角糊弄」，本实现满足「非几何圆角」底线，G2 连续留待后续批次评估（若引入需重新选近似函数）。
4. **参数域 double 精度教训** —— float 的 `sin(π)≈1e-7` 经 `|sin|^0.4` 放大到 1e-3 量级，致角端点漂移 0.03px（测试实测暴露）；采样与变换全改 double 域 + `snapZero` 吸附零噪声后消除。教训：小指数幂次会放大 float 的端点误差，几何生成代码应在 double 域完成再回落 float。
5. **n=5 为默认平滑指数** —— 中点距顶点 0.13r vs 几何圆角 0.29r（更「方」，接近 iOS 观感）；A/B 的 B 侧用 n=2（退化标准圆）作几何圆角基准，两列并排直接可对比。
6. **Path 缓存 access-ordered LRU（64 条）** —— 令牌页 172 行滚动会持续请求同组 shape 路径；key=(w,h,r,n,segments) 命中率实测接近 100%，返回 Path **只读**契约（调用方不得 mutate）。
7. **gallery versionName `0.0.0.dev`** —— 未发版标识；R10 再开混淆（当前调试构建保留符号便于崩溃分析）；lint `abortOnError` 已开。
8. **引擎版本步进 `1.1 → 1.2`**（二段式逢九进一，非软件发版）；默认包 `engineVersion` 区间 `>=1.0 <2.0` 不动，兼容覆盖。

### 已知缺口（R3 不做）

- 组件 / 原型 / 动效 / 工坊四分区为占位页（R6–R9）；
- 抽屉开合无动画（动效规格归 R5）；
- 令牌总览为只读展示，无运行时覆写（R4 工坊）；
- 仪器测试 9 项待真机执行；静态页真机掉帧测量待设备。

---

## R1 引擎内核（数据 + 编译）— 已完成，待发版

**引擎版本 `1.0`** ｜ 模块：`:theme-engine`（纯 JVM，零 UI 依赖）｜ 单测：**131 项全绿**
**软件发版状态：未发版** —— 按规矩须先向维护者确认版本号后再打 tag。

### 交付范围（分批实施计划 §3 R1 原文）

| 交付项 | 状态 | 落点 |
|---|---|---|
| `:theme-engine` 骨架 | ✅ | 四包无环：`api`（契约叶）← `schema` ← `tokens` ← `fallback` ← `compiler` |
| 十子系统 `@Immutable` data class | ✅ | `ColorTokens` / `TypographyTokens` / `ShapeTokens` / `SizeTokens` / `SpacingTokens` / `MotionTokens` / `InteractionTokens` / `ElevationTokens` / `OpacityTokens` / `GlassTokens` |
| DTCG Format 解析 | ✅ | `DtcgParser`（version / `$value` / `$type` / `$description` / `$extensions` / 别名 `{path}` / 组级类型继承 / 嵌套扁平等价） |
| 编译器三规则（§5.1） | ✅ | `SetMerger`（规则 2/3）+ `AliasResolver`（规则 1） |
| 复合型字段别名 | ✅ | `AliasResolver.resolveComposite`（spring / glass / typography / shadow 四型 + 必填校验） |
| 导入校验全行（§5.2） | ✅ | `ThemeValidator` + `DtcgParser` 体积上限 |
| 默认主题包（纯白 iOS） | ✅ | `theme-engine/src/main/resources/default-theme.json`（core + dark/highContrast/reducedMotion/reducedTransparency） |
| 内置 fallback 快照 | ✅ | `FallbackTheme.rawValues`（196 条常量，零 IO / 零 JSON / 零反射） |

### 验收逐条（对照 §3 R1 验收原文）

| 验收项 | 状态 | 覆盖测试 |
|---|---|---|
| 别名拓扑：断链 / 循环 / 类型不符拒绝 | ✅ | `AliasResolverTest`（断链诊断含引用方与目标、环路径输出、自引用环、顶层与复合字段类型不符） |
| 三规则逐条 | ✅ | `SetMergerTest`（后者覆盖 / 未激活跳过 / 可叠加按序 / 未声明与重复激活拒绝 / 规则 3 顶层与复合字段别名拒绝） |
| **「先合并后解析」专项**（dark 覆盖 primitive → 语义别名换值，含 `glass.thin.tintAlpha` 复合字段） | ✅ | `AliasResolverTest` 前两条 + `ThemeCompilerEndToEndTest`（dark 下 accent=#0A84FF、`glass.thin.tintAlpha`=0.45；扩展组 `chart.c1` 亦随语义色换值） |
| 玻璃 opaque 拒绝路径 | ✅ | `ThemeValidatorTest`（启用时半透明 → `GLASS_OPAQUE_VIOLATION`；未启用时跳过；引用缺失令牌 → `SCHEMA_INVALID`） |
| fallback 三路径（快照损坏 / 编译异常 / 包不存在） | ✅ | `ThemeLoaderTest`（9 项：null / 空白 / 坏 JSON / 缺 sets / 断链 / 引擎版本不兼容 + 内置包成功 + 最小包回填） |
| §6.1.4 总表逐行有令牌且端到端可读 | ✅ | `FallbackConsistencyTest`（172 词汇表路径全覆盖 + 十子系统条目分布 62/14/6/13/11/13/26/2/15/10）+ `SnapshotAccessorsTest`（十子系统访问器逐项取值） |
| 门禁绿（纯库批次，无 APK） | ✅ | `gradle :theme-engine:test` → 128 tests, 0 failed |

### 单测清单（128 项）

| 测试文件 | 数量 | 关注面 |
|---|---|---|
| `SemVerTest` | 9 | 版本号解析 / 排序 / 区间（空白与逗号分隔）/ rc 优先级 |
| `TokenValueTest` | 10 | hex 规范化与 `#RRGGBBAA` 解析 / alpha / source-over 合成 / 单位 / 复合 schema |
| `DtcgParserTest` | 30 | 结构 / 类型 / 别名语法 / 保留键白名单 / 512KB 上限 / `$extensions` 解析 |
| `SetMergerTest` | 8 | 规则 2/3 |
| `AliasResolverTest` | 13 | 规则 1 + 别名拓扑 + 复合型 + 扩展组别名 |
| `ThemeValidatorTest` | 21 | §5.2 全行 + WCAG 数学 |
| `ThemeCompilerEndToEndTest` | 11 | 默认包五上下文 + 扩展组 + WCAG golden 基线 |
| `ThemeLoaderTest` | 9 | 降级三路径 + 成功路径 |
| `FallbackConsistencyTest` | 6 | fallback 防漂移 + 词汇表自洽 |
| `SnapshotAccessorsTest` | 11 | 十子系统强类型访问器 |

### 偏离与决策记录（与设计文档的差异，逐条说明）

1. **`@Immutable` 为引擎自定义标记** —— 联网核实 `androidx.annotation`（73 个注解）**不含** `Immutable`，Compose 版位于 `androidx.compose.runtime`。R1 纯 JVM 模块不引入 Compose，故自建 `org.minime.core.api.Immutable`（语义等价，KDoc 注明 R2 接入 `:theme-ui` 时可机械替换 import）。连带移除 `androidx.annotation` 依赖——引擎零外部注解依赖。
2. **纯 JVM 模块** —— §4.2 的 runtime/motion/shape/material/components/page 依赖 Compose，全部推迟到 R2+ 的 `:theme-ui`（Android library）。引擎内核保持「纯 JVM 可完整单测」。
3. **颜色 hex 采用 `#RRGGBBAA`（末位 alpha）** —— 与 iOS/Android 十六进制惯例一致；4 位简写按 `#ARGB`。实现初期误按 AARRGGBB 解析，导致 `#000000FF` alpha=0、对比度全链路 1:1，由端到端冒烟暴露并修复，`TokenValueTest` 固化。
4. **DTCG 类型名映射** —— DTCG 拼写 `fontFamily` / `cubicBezier` 与 Kotlin 枚举名（`FONT_FAMILY` / `CUBIC_BEZIER`）不同，改用显式 `RAW_NAMES` 表映射（大小写不敏感），避免依赖枚举名巧合。
5. **SemVer 区间支持空白与逗号两种分隔** —— 白皮书示例写作 `">=1.0.0 <2.0.0"`（空格），实现初期仅支持逗号，导致默认包 `engineVersion` 被误判非法；现两种均支持，并校验区间须被比较器完整覆盖（拒绝夹带非法字符）。
6. **体积上限按「紧凑重序列化」计** —— `sets` 子树经 org.json 紧凑序列化（去空白）后计字节；实测输入需约 620KB 才触发 512KB 上限（原始带缩进输入会先被压小）。此语义已写入测试注释。
7. **扩展组（`$extensions.minime.<group>`）为包级常驻** —— 不参与 `resolutionOrder`、无修饰符交互（白皮书 §3.7 的编辑器/终端/图表开放词汇由主题包整体提供）；合并于 set 之后进入同一张表，因此**扩展组内允许别名**（`chart.c1 = {color.semantic.accent}`，dark 下随语义色换值）。实现初期漏掉扩展组合并，被端到端测试发现（fallback 恰好同值掩盖了此缺陷）。
8. **`elevation.overlay` 提前到 R1** —— 白皮书列为二期 `ShadowValue` 相关，但 `elevation` 在十子系统中已占位（2 条：`hairline` + `overlay`），为避免「有槽位无值」，R1 一并落地。
9. **动效人格不建令牌** —— `personality`（ios/standard/custom）是消费方对六槽 spring 的**组合选择**，建令牌会与六槽形成双真源；由组件层读取组合（R2+）。
10. **空/空白输入归入「包不存在」** —— `ThemeLoader.load(null | 空白)` → `PACK_NOT_FOUND`；仅「非空但非法 JSON」→ `PACK_CORRUPT`。
11. **DTCG 版本取 preview-draft 偏离** —— 解析器读 `version` 字段但不做强制等值校验（模块 2025.10 stable 与官方 draft 仍在演进），避免未来格式升级直接拒包；结构规则（sets/resolutionOrder）由 MiniMe Resolver 稳定子集自行约束。
12. **WCAG 警告 golden 基线（light 20 条）** —— 默认包亮色下 `text.secondary/tertiary/quaternary/placeholder` 与五档背景的组合低于 4.5:1（最低 1.36:1，对应 iOS 实际观感的次级文本）。**这是已知且符合设计意图的**（iOS 系统色本身如此），记为警告不阻断；`ThemeCompilerEndToEndTest` 以 golden 断言固化，后续若调整色板须同步更新。
13. **色值校正（联网核实 Apple 系统色数据点）** —— `teal` 由初稿 `#30B0C7` 校正为 iOS `#5AC8FA`；分隔符透明度按 60%（非初稿 65%）；`scrimBright` 采用 0.35 阶梯。
14. **默认主题包 glass 默认关闭** —— 纯白风格下 `glass.enabled = false`（与 §6.8 玻璃为可选增强一致），玻璃材质配方与 tintAlpha 阶梯仍全量提供，`reducedTransparency` 修饰符将其进一步不透明化。

### 已知缺口（不属于 R1 范围）

- TokenVocabulary 仅覆盖标准词汇表 172 条；开放词汇（editor/terminal/chart 等 24 条）由扩展组提供、可缺省。
- 无运行时主题切换 / 持久化 / 预编译缓存（R2）。
- 无 UI、无 Compose 提供层、无 M3 映射（R2）。
- 主题包导入性能未压测（R2 门禁 ≤200ms）。
