# minime-core

MiniMe 主题引擎内核（R1）。纯 JVM 库，零 UI 依赖——可脱离 Android 完整单测。

对应设计文档：

- [MiniMe-core 主题引擎白皮书](file:///workspace/MiniMe-core-主题引擎白皮书.md)（§5 令牌规范 / §6 十子系统 / §6.8 玻璃）
- [MiniMe-core 分批实施计划](file:///workspace/MiniMe-core-分批实施计划.md)（§3 R1 引擎内核）
- [主题引擎审计报告](file:///workspace/MiniMe-core-主题引擎审计报告.md) / [安全审计报告](file:///workspace/MiniMe-core-安全审计报告.md)
- 变更记录：[CHANGELOG.md](CHANGELOG.md)

## 模块

| 模块 | 说明 |
|---|---|
| `:theme-engine` | 令牌数据 + DTCG 解析 + 编译器 + 内置 fallback（纯 JVM，JVM 17） |

R2+ 将新增 `:theme-ui`（Android library，Compose 运行时与提供层）。

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

## 快速开始

```kotlin
val loader = ThemeLoader()

// 1) 内置默认包（light 上下文）
val light = loader.loadBundledDefault()

// 2) 同一包在 dark / highContrast 等修饰符下编译
val dark = loader.loadBundledDefault(listOf("dark"))
val darkHc = loader.loadBundledDefault(listOf("dark", "highContrast"))

// 3) 导入第三方主题包 JSON（失败一律降级为内置 fallback，绝不让 App 处于无主题状态）
val result = loader.load(json)
if (result.degraded) log.w("主题降级: ${result.error?.code} ${result.error?.message}")
result.warnings.forEach { log.w("导入警告: ${it.code} ${it.message}") }

// 4) 消费：十子系统强类型访问器
val color = result.snapshot.color
Text(text = "…", color = color.textPrimary.toComposeColor(), style = result.snapshot.typography.body.toStyle())
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
gradle :theme-engine:test     # 128 项单测
gradle :theme-engine:build    # 编译 + 测试
```

> 沙箱内 `./gradlew` 需下载 Gradle 发行版（网络受限）；本机已装 Gradle 9.3.0 时直接用 `gradle`。

## 版本（三条线，互不干扰）

| 线 | 格式 | 当前取值 |
|---|---|---|
| **软件发版版本**（git tag / Release） | **四段式 `x.x.x.x`**（项目规范：A=颠覆性预留 / B=框架级结构性预留 / C=框架级重构 / **D=默认发版 +1**） | 体系 `0.0.1.x`，已发至 `v0.0.1.10`；**R1 待维护者确认后发版** |
| **引擎版本** `MiniMeEngine.VERSION` | **二段式 `主.次`，步长 +0.1，逢九进一** | `1.0`（`ThemeCompiler.ENGINE_VERSION` 同源） |
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
