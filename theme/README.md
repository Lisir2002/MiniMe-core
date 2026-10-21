# minime-core

MiniMe 主题引擎内核（R3.5）。引擎纯 JVM 零 UI 依赖，可脱离 Android 完整单测；示例页面群落 `:minime-ui`（library，嵌入主程序，**本仓库无 application 模块、无可装机产物**）。

对应设计文档：

- [MiniMe-core 主题引擎白皮书](file:///workspace/MiniMe-core-主题引擎白皮书.md)（§5 令牌规范 / §6 十子系统 / §6.8 玻璃 / §10 验证页面）
- [MiniMe-core 分批实施计划](file:///workspace/MiniMe-core-分批实施计划.md)（§3 R1–R10）
- [主题引擎审计报告](file:///workspace/MiniMe-core-主题引擎审计报告.md) / [安全审计报告](file:///workspace/MiniMe-core-安全审计报告.md)
- 变更记录：[CHANGELOG.md](CHANGELOG.md)

## 模块

| 模块 | 说明 |
|---|---|
| `:theme-engine` | 令牌数据 + DTCG 解析 + 编译器 + 内置 fallback（**纯 JVM**，可脱离 Android 完整单测） |
| `:theme-ui` | 运行时（`ThemeManager`）+ 十子系统提供层 + M3 映射层 + 绘制原语（`draw` 包：squircle / 连续圆角 / 发丝线） |
| `:minime-ui` | 示例页面群（Android library，`org.minime.core.ui.gallery`）：`pages/` 六个分区页面 + `nav/` 可选导航部件（侧边栏 / 顶栏，参考导航）。**嵌入主程序使用**，页面即单元、状态外置、零 Context 持有（白皮书 §10.1.1 五原则） |

## 包结构（无环）

```
api       契约叶包：MiniMeEngine / CompileErrorCode / ThemeCompileException / ImportWarning / Immutable
  ↑
schema    值模型 + 解析器：TokenType / TokenValue / ThemePack / DtcgParser / SemVer
  ↑
tokens    十子系统快照 + 词汇表 + SnapshotBuilder
  ↑
fallback  内置兜底快照（常量构造，零 IO）
  ↑
compiler  导入期校验 + 合并器 + 别名解析 + 编译器 + 加载器（§4.2「导入器」）
```

`:theme-ui` 内 `draw` 包 = 自绘原语单一真源：`SquircleMath`（lamé 超椭圆纯数学，JVM 可测）+ `ContinuousCornerShape`（Path LRU 缓存，只读契约）+ `drawHairline`（发丝线，厚度取自 `elevation.hairline`）。组件族 R6–R9 复用，禁第二套实现。

## Gallery 分区（白皮书 §10，落 `:minime-ui` 的 `pages/`）

| 分区 | 状态 | 内容 |
|---|---|---|
| 令牌总览 | ✅ R3 | 172 条逐行（机器核对 `TokenCoverageTest`），shape 区含 squircle vs 几何圆角 A/B |
| 调试面板 | ✅ R3 | 主题组信息 + 修饰符四开关（dark / highContrast / reducedMotion / reducedTransparency） |
| 组件目录 / 页面原型 / 动效检验 / 主题工坊 | ⏳ 占位 | R6 / R8 / R5 / R4 依次落地 |

导航：宽屏（≥840dp）右侧边栏常驻；窄屏顶栏菜单开抽屉。顶栏主题入口常驻可见（D10）。
侧边栏与顶栏在 `nav/` 包作为**可选聚合部件**交付——宿主可整体采用，也可只取页面自组导航（D12）。

## 快速开始

```kotlin
// —— Application.onCreate：initialize() 必须在 setContent 之前，否则首帧会先 fallback 再跳变 ——
val manager = MiniMeThemeBootstrap.create(this)
manager.initialize()

setContent {
    MiniMeTheme(manager) { App() }   // 首帧即目标主题
}

// —— 切换（后台编译 + 节流：连点只让最后一次生效） ——
manager.select("ocean", listOf("dark"))

// —— 消费：十子系统提供层（组件唯一消费面） ——
@Composable
fun Card(…) {
    val color = MiniMeTokens.LocalColor.current
    val motion = MiniMeTokens.LocalMotion.current
    …
}

// —— 引擎内核也可单独使用（纯 JVM，无 Android 依赖） ——
val result = ThemeLoader().load(json)
if (result.degraded) log.w("主题降级: ${result.error?.code}")
```

## 编译器三规则（白皮书 §5.1）

1. **先合并、后解析别名** —— 按 `resolutionOrder` 把选中集合并成一张扁平表，别名统一在合并后解析。
   保证 `color.semantic.bg.canvas = {color.primitive.systemBackground}` 在 dark 下拿到 `#000000`
   （先解析会遗漏修饰符覆盖，语义层换肤失效）。
2. **互斥修饰符单选、可叠加修饰符追加** —— 按序合并、后者覆盖。互斥单选由调用方（R2 `ThemeManager`
   按系统上下文）保证；编译器只校验激活修饰符已声明且无重复。
3. **修饰符不得引用 modifier** —— 修饰符集内只允许字面量（标量字面量 / 复合型全字段字面量），
   顶层与复合字段别名均拒绝。合并期因此不存在待解析引用，规则 1 的确定性得以保证。

## 主题包结构

严格遵守 **DTCG Format**（`version` / `$value` / `$type` / `$description` / `$extensions` / 别名 `{path}` / 组级类型继承 / 嵌套与扁平等价）；`sets` + `resolutionOrder` 属于 **MiniMe Resolver 稳定子集**（DTCG Resolver draft 官方明示勿实现，只取其差异集思想并显式裁剪）。

```jsonc
{
  "version": "2025.10",
  "manifest": { "name": "…", "version": "1.0.0", "author": "…", "engineVersion": ">=1.0 <2.0" },
  "sets": {
    "core": { "color.primitive.systemBlue": { "$value": "#007AFF", "$type": "color" } },
    "dark": { "color.primitive.systemBlue": { "$value": "#0A84FF", "$type": "color" } }
  },
  "resolutionOrder": [{ "set": "core" }, { "modifier": "dark" }],
  "$extensions": {
    "minime": {
      "editor":   { "syntax.keyword": "#C7254E" },       // 开放词汇组（可缺省）
      "glass":    { "consumers": ["color.semantic.text.primary"] },   // 保留键：玻璃表面消费声明
      "schema":   { "spring": { "fields": ["stiffness", "damping", "initialVelocity"] } }  // 保留键：自定义型字段声明
    }
  }
}
```

- **正则色板**：`#RRGGBB`（视为不透明）/ `#RRGGBBAA`（末位 alpha）/ `#RGB` / `#ARGB`。
- **引擎自定义类型**：`spring`（stiffness/damping/initialVelocity）与 `glass`（blur/saturation/tint/tintAlpha/hairline）——DTCG 无对应原生型，字段 schema 在 `$extensions.minime.schema` 声明并纳入导入白名单；复合型字段允许 `{alias}`。
- **体积上限**：`sets` 子树紧凑序列化后 ≤ 512KB。

## 导入校验（白皮书 §5.2，逐行）

| 行 | 规则 | 结果 |
|---|---|---|
| 1 | schema / manifest / `engineVersion` 兼容区间 / `resolutionOrder` 引用完整性 / 扩展组冲突 / schema 声明一致 | 拒绝 |
| 2 | 词汇表内令牌声明类型一致（词汇表外路径允许自定义） | 拒绝 |
| 3 | 别名拓扑：断链 / 循环 / 类型不符 / 复合必填缺失 | 拒绝 |
| 4 | 玻璃表面消费令牌 opaque（`glass.enabled` 生效时） | 拒绝 |
| 5 | WCAG 对比度（文本×背景 4.5:1、强调×表面 3:1、玻璃包络） | **警告不阻断** |
| 6 | 体积上限 512KB | 拒绝 |
| 7 | 禁代码执行（纯数据模型，天然保证） | — |

失败码见 `org.minime.core.api.CompileErrorCode`；降级路径见 `ThemeLoader` 三档。

## 构建与测试

```bash
gradle test                     # 三模块全部单测（178 项，JVM 自包含，无需设备）
gradle :minime-ui:assembleDebug # 产出 AAR：minime-ui/build/outputs/aar/minime-ui-debug.aar
gradle :minime-ui:lintDebug     # lint（abortOnError = true）
```

| 模块 | 单测 | 说明 |
|---|---|---|
| `:theme-engine` | 134 | 纯 JVM（结果在 `build/test-results/test/`） |
| `:theme-ui` | 37 | Android library（`testDebugUnitTest`，含 squircle 数学 9 项） |
| `:minime-ui` | 7 | `TokenCoverageTest` 3（172 条双向集合相等）+ `GalleryEmbedRobolectricTest` 4（Robolectric + Compose UI 测试：侧边栏可达 / A/B 并排 / 主题切换 / 全量滚动） |

> `:minime-ui` 的 Compose UI 测试只跑 **debug 变体**（release 变体已禁用单测：测试载体清单 `ui-test-manifest` 仅 debug 生效）。Robolectric 屏幕配置 `w840dp-h800dp`（Robolectric 无真实字体度量，默认小屏放不下侧边栏六项）。

> 沙箱内 `./gradlew` 需下载 Gradle 发行版（网络受限）；本机已装 Gradle 9.3.0 时直接用 `gradle`。

## 版本（三条线，互不干扰）

| 线 | 格式 | 当前取值 |
|---|---|---|
| **软件发版版本**（git tag / Release） | **四段式 `x.x.x.x`**（项目规范：A=颠覆性预留 / B=框架级结构性预留 / C=框架级重构 / **D=默认发版 +1**） | 体系 `0.0.1.x`，已发至 `v0.0.1.10`；**R3.5 待维护者确认后发版** |
| **引擎版本** `MiniMeEngine.VERSION` | **二段式 `主.次`，步长 +0.1，逢九进一** | `1.2`（`ThemeCompiler.ENGINE_VERSION` 同源） |
| 主题包版本 `manifest.version` | 自由 | 默认包 `1.0.0` |

### 发版三条硬规矩

1. 软件发版一律走**项目四段式规范**，禁止自创三段式 SemVer。
2. **不允许发 rc tag**（项目 CI 拒绝预发布 tag，只发正式版）。
3. **发版前必须先询问维护者、由维护者确认发版版本号**，确认后才打 tag。

引擎版本只用于主题包 `engineVersion` 兼容判定（`MiniMeEngine.COMPAT_RANGE = ">=1.0 <2.0"`），
**不得当软件发版版本用**；二段式逢九进一：`1.0 → 1.1 … 1.9 → 2.0`（不存在 `1.10`）。

## 发版

```bash
# ① 先向维护者确认版本号  ② 只在 main 主线上打 tag（严禁功能分支发版）
git tag -a v0.0.1.11 -m "…"
```
