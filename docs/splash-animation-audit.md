# 启动动画审计与强化方案：6 秒粒子动画该不该「就绪即退」

> 2026-10 启动动画（`core/splash`）针对性审计 + 联网调研 + 分级强化方案。
> 用户原话：「针对化审计启动动画设计，联网学习一下，给出强化方案」。
>
> 本文只出方案并分批评审落地。落地状态：**A（接线真实初始化）✅ `ef78809` / B（性能优化）✅ `b16dd4d` / D（测试与收尾）✅ `236dc6c` / C（系统级 SplashScreen 分层）⏸ 待用户拍板（见 §7、§10）**。A/B/D 三批均四门禁（compile/test/detekt/lint，压内存参数）全绿。
> 审计对象：`app/src/main/java/com/mini/me_core/core/splash/` 9 文件 1525 行，
> 集成点 `MainActivity.kt:293-317`、`MiniMeCore.kt:240`、`ThemeSettingsRepository.kt:41-47`。

## 1. 现状架构（源码实证）

| 组件 | 文件 | 职责 | 关键参数 |
|---|---|---|---|
| SplashStyle | SplashStyle.kt:9-13 | 4 风格枚举 | PARTICLE(默认) / MINIMAL / CLASSIC / OFF，KV key `splash_style` |
| AppInitState | AppInitState.kt | 初始化就绪门闩 | `markReady()` 幂等，1.5s 超时兜底 |
| SplashQualityLevel | SplashPerformanceDetector.kt:24-26 | 设备分档 | HIGH 1800 粒子/40 碎片/6s/3D+裂纹；MEDIUM 1200/25/5s；LOW 600/12/4s |
| SplashStage | SplashAnimationState.kt | 五段时间轴 | EXPLODE→CONVERGE→HOLD→SHATTER→FADE_OUT，HIGH 总 6.0s |
| ParticleSystem | ParticleSystem.kt | 粒子物理（FlatArray 零每帧分配） | 文本位图采样定目标点 |
| ParticleSplashCanvas | ParticleSplashCanvas.kt | 每帧绘制 | glow sprite + 核心圆 + 背景光晕 + 碎裂闪光 |
| GlassShatterEffect | GlassShatterEffect.kt | 玻璃碎裂 3D | 半平面裁剪生成碎片，5 层材质 |
| MinimalSplashScreen | MinimalSplashScreen.kt | 简约风 | 径向渐变漂移 + 文字淡入 2.5s，尊重动画缩放 |
| 集成 | MainActivity.kt:293-317 | splash 覆盖层叠在 AppNavigation 之上 | `showSplash` 为 `remember`（非 saveable） |

**就绪信号链**：`MiniMeCore.kt:240` 在「核心服务启动」后调 `AppInitState.markReady()`——
但全仓检索证实**没有任何代码消费这个信号**（无 `waitUntilReady` 调用方）。
splash 退出完全由动画自身时间轴决定（`ParticleSplashScreen.kt:129`：`elapsedMs >= totalDurationMs`）。

## 2. 联网调研结论（2026-10，官方文档 + 行业基准）

| 议题 | 业界标准做法 | 来源 |
|---|---|---|
| 系统级启动画面 | Android 12 SplashScreen API：进程创建期由系统窗口顶住，**App 绘制首帧前不消失**；compat 库（androidx.core:core-splashscreen）下探 API 23 | developer.android.com/guide/topics/ui/splash-screen |
| 退场时机 | 「只有在 app 视觉上稳定时才关闭 splash」；就绪控制用 `setKeepVisibleCondition`，退出动画用 `setOnExitAnimationListener` | 同上 |
| 图标动画时长 | AVD 动画图标建议 **≤1000ms**；启动超过 1s 应循环而非定格 | 同上 |
| 品牌元素规格 | 品牌图 200×80dp；图标 240×240dp（160dp 圆内）/ 288×288dp（192dp 圆内）；窗口背景单色且 day/night 双资源 | 同上 |
| 动画的价值 | 官方用户研究：**观看动画时感知启动时间更短**——动画不是敌人，无意义拖延才是 | 同上 |
| 冷启动预算 | 中端机 ~2s 为「不觉慢」阈值；**>3s 首启放弃率 +32%**；Google 建议 1s 内可交互；Vitals >5s 标记。国内细分：高端 ≤400ms / 中端 ≤800ms / 低端 ≤1200ms 为理想 | industry benchmarks 2024-2026 |
| Compose 每帧性能 | 每帧状态读取触发重组；应把读取延迟到 **Draw 阶段**（drawBehind/Canvas lambda）跳过组合+布局；draw 操作合并减少 RenderNode；用 Perfetto 查 RenderThread fence stall | developer.android.com/jetpack/compose/performance |

**对照本项目的三个硬差距**：
1. 官方「App ready 才退场」——本项目是固定 6s 时间轴，与初始化完成度**完全脱钩**（门闩没接线）；
2. 官方「图标动画 ≤1000ms、启动预算 ~2s」——本项目 HIGH 档 6s 是预算的 3 倍，且就绪后不提前退；
3. 官方「系统 splash 顶住进程创建期」——本项目 `themes.xml:5` 注释明确弃用系统 SplashScreen，进程创建→Compose 首帧之间只有一个纯色窗口，系统级免费窗口被浪费。

## 3. 问题清单（分级，全部带源码行号实证）

### P0 功能与行为正确性

1. **AppInitState 是死门闩**：`MiniMeCore.kt:240` 写、无人读。后果双向——
   初始化 300ms 就绪，用户被按头看满 6s；初始化 8s 才完，动画第 6s 放走用户，背后还在加载。
   KDoc 宣称的「控制启动画面保持/退出」从未接线。
2. **假进度**：`ParticleSplashScreen.kt:145-152`，progress = elapsedMs/总时长，阶段文案按
   0.20/0.45/0.70/0.85 硬编码阈值切换（「正在初始化浏览器」与浏览器实际状态无关），
   代码注释自认 "simulated init progress mapped to animation timeline"。
   假进度比没有进度更糟：它主动制造错误预期。
3. **无视「关闭动画」设置**：`ScaledAnimation` 语义为 scale≤0 → duration=0（无动画），
   `MinimalSplashScreen` 遵守；但 `ParticleSplashScreen` 主循环（:107-133 withFrameNanos）
   不读 `LocalAnimationScale`，只在其后 500ms 淡出用了一下（:139）。
   用户在设置里关动画后，粒子动画仍全速播放——全局动效一致性破窗。
4. **风格切换入口不存在**：`ThemeSettingsRepository.setSplashStyle` 全仓无调用方，
   `SplashStyle.labelRes` 无消费方。用户不改 KV 存储就无法切换风格；
   CLASSIC 在 `MainActivity.kt:311-315` 与 OFF 同行为（直接显示），「经典Logo」名不副实。
5. **配置变更重播**：MainActivity 无 `android:configChanges` 且 `showSplash` 是普通 `remember`（:298）。
   旋转屏幕 / 切换深浅色 / 改字体大小都会重建 Activity → 6s 动画从头再播一遍。

### P1 性能（都在冷启动关键路径的主线程上）

6. **每帧每粒子新建 shader**：`ParticleSplashCanvas.kt:170-179`，每个粒子每帧新建
   `RadialGradient` + `intArrayOf` + `floatArrayOf`。HIGH 档 1800 粒子 ≈ 每帧 1800 个 shader
   + 3600 个临时数组对象（60fps 下每秒 20 万+ 临时对象）——直接打爆 young gen。
   讽刺的是 `ParticleSystem` 的设计注释写着 "avoid per-frame allocation"，Canvas 层全丢了。
7. **全屏背景渐变每帧新建**：`ParticleSplashCanvas.kt:116-124`，每帧一次全屏 `RadialGradient` 分配。
8. **文本采样逐点 `getPixel`**：`ParticleSystem.kt:204-221`，双层循环逐点 JNI 读像素
   （1080p 屏约 10 万+ 次调用），发生在 `LaunchedEffect` 首次构造的主线程上。
   应改 `bitmap.getPixels(IntArray)` 一次批量读取。
9. **每帧重组整个覆盖层**：`elapsedMs`（mutableLongStateOf）每帧变 → 整棵 Box 子树
   每帧 recomposition，含两个 `Text`（:224-228 百分比还每帧做字符串拼接）。
   应把粒子绘制状态读取下沉到 Draw 阶段，UI 文本节流更新。
10. **碎裂特效每帧大量分配**：`GlassShatterEffect.kt:232-246/305-310/322-327`，每碎片每帧
    新建 3 个 `LinearGradient` + 1 个 `Path`；构造期 `:101-114` 是 O(n²) 半平面裁剪
    （35 个种子点两两裁剪），同样在主线程。
11. **splash 与主内容并行双渲染**：splash 覆盖层显示期间，下面的 AIChatPanel + ViewModel
    init + workspace 加载全速进行——冷启动同时扛两份重 UI，低端机（LOW 档）首帧成本翻倍。

### P2 体验与工程债

12. **6s 固定时长 vs ~2s 预算**：HIGH 6s / MEDIUM 5s / LOW 4s，均远超冷启动预算，
    且「就绪即退」缺失使其成为纯拖延。
13. **未用 Android 12 SplashScreen API**：`themes.xml:5` 有意弃用（历史决策），
    代价是进程创建期只有纯色窗口（day/night 已正确分资源，这点没问题）。
14. **退出交接名不副实**：F1.6 注释宣称 "main content underneath fades in"，
    实现（:136-142）只有 overlay alpha→0，主内容并无淡入，是硬切。
15. **i18n 缺失**：`values-en/strings.xml` 只有 `splash_skip`；`splash_style_*` 4 条 +
    `splash_stage_*` 5 条无英文翻译，英文环境回退中文。
16. **零单元测试**：splash 包无任何测试；`SplashStage` 时间轴、`SplashQualityLevel` 分档
    等纯逻辑本可测。
17. **文档与代码不一致 + 死代码**：`SplashPerformanceDetector.kt:13-15` KDoc 写
    1200/800/400，实现是 1800/1200/600；`ParticleSystem.reset()`（:336-344）无调用方。
18. **风格首帧竞态**：`MainActivity.kt:295-297` `collectAsStateWithLifecycle(initialValue = PARTICLE)`，
    KV 异步首读落地前按默认 PARTICLE 起播，非默认风格用户会先起播一帧粒子再切换。

## 4. 强化方案总览（渐进式，四批独立可回滚）

| 批次 | 主题 | 风险 | 视觉变化 | 依赖 |
|---|---|---|---|---|
| A | 接线与行为正确（P0） | 低 | 无（节奏除外） | 无 |
| B | 性能优化（P1） | 低 | 几乎无 | 无 |
| C | 系统级 SplashScreen 分层 | 中 | 冷启动首屏多一层系统图标 | 需拍板 |
| D | 测试与收尾 | 低 | 无 | A/B 后 |

红线遵守：不删现有粒子动画、不做大爆炸式替换；每批独立提交、独立门禁、可单独回滚。

## 5. Batch A：接线与行为正确（建议先做）

**A1 门闩真正接线**
- `AppInitState` 增加 `suspend fun awaitReady(timeoutMs = TIMEOUT_MS)`（超时自动兜底放行，复用现有 Runnable 逻辑改挂起版）。
- `ParticleSplashScreen` 退出条件改为：
  `(elapsedMs >= minDisplayMs && AppInitState.isReady) || elapsedMs >= totalDurationMs(quality) || 用户点跳过`。
- 新增 `minDisplayMs = 1200ms`（防「就绪太快闪一下」），`totalDurationMs` 即最长展示。
- 落地效果：初始化 300ms 完成的常规冷启动从 6s 降到 ~1.2-2s；初始化慢时动画播完仍等待，
  skip 按钮与 1.5s 兜底保证不会卡死。
- `MinimalSplashScreen`（2.5s 固定）同步改。

**A2 真进度替代假进度**
- `MiniMeCore` 已有三阶段日志（[启动/1/3] 数据层 / [2/3] 核心服务 / [3/3] 异步预热），
  在 `AppInitState` 旁建 `StartupProgress`（object，`@Volatile phase: StartupPhase`）或复用
  StartupTracer 已有打点，splash 阶段文案改读真实 phase（5 条 `splash_stage_*` 文案保留，
  映射改为：phase1→本地数据 / phase2→AI 服务+终端+浏览器（并行初始化合并展示）/ phase3→界面）。
- 进度条改为真实加权（phase 完成度）；无细粒度权重时至少两档真实推进（50% / 100%）。

**A3 动效缩放接入**
- 主循环 `dt *= animScale`；`animScale <= 0f`（用户关闭动画）时跳过粒子动画，
  直接走退场（与 `ScaledAnimation.duration()==0` 语义对齐）。`MINIMAL` 已有静态分支可参照。

**A4 风格切换入口 + CLASSIC 正名**
- 设置页「外观」分区新增单选组（4 项，取 `SplashStyle.labelRes`），写 `setSplashStyle`。
- CLASSIC 更名「静态 Logo」，实现为「静态 Logo + 淡入」（复用 MinimalSplashScreen 静态分支的
  现成能力，不新建文件）；OFF 文案「关闭」不变。

**A5 防重播**
- `showSplash` 改 `rememberSaveable`；`savedInstanceState != null`（配置变更/进程重建归来）不重播。
  温启动本就快，重播无意义。

## 6. Batch B：性能（零视觉变化目标）

- **B1 粒子核心去每帧 shader**：`ParticleSplashCanvas.kt:166-182` 核心圆改实心
  `drawCircle(coreColor)`（外层 128px glow sprite 已提供柔光，观感差异极小）；
  若视觉评审不接受，退而用「2 色 × 3 档半径」预建 shader 缓存（1800 次/帧 → 6 次/构造）。
- **B2 背景渐变缓存**：`:116-124` shader 提为 `remember(primaryArgb, bgArgb)` 单例，
  每帧只调 `paint.alpha`（颜色不透明、alpha 调制），删每帧分配。
- **B3 文本采样批量读取**：`ParticleSystem.kt:204` 区域改 `bitmap.getPixels(...)` 一次 JNI，
  循环内纯数组访问。
- **B4 碎裂特效池化 + 构造移出主线程**：`GlassShatterEffect` 的 3 shader / 1 Path 复用
  （reset 后重用）；`ParticleSystem` + `GlassShatterEffect` 构造（含 O(n²) 裁剪）
  移到 `Dispatchers.Default`，首帧先画纯色背景，构造完成再起播物理。
- **B5 recompose 瘦身**：进度文本/百分比按 100ms 节流；`elapsedMs` 的读取下沉到
  Canvas Draw 阶段（`withFrameNanos` 仍驱动物理更新，但绘制不经 recomposition），
  目标：动画期间主线程只剩 Draw + 物理 O(n)。

## 7. Batch C：系统级 SplashScreen 分层（需拍板后实施）

- 引入 `androidx.core:core-splashscreen`（minSdk=26 全覆盖 compat）。
- 主题拆分：`Theme.MiniMeCore.Splash`（parent `Theme.SplashScreen`，
  `windowSplashScreenBackground` 沿用 day/night 两色、`windowSplashScreenAnimatedIcon`
  用静态 logo 或轻量 AVD ≤1000ms、`postSplashScreenTheme` 指回 `Theme.MiniMeCore`）；
  `MainActivity.onCreate` 在 super 后立即 `installSplashScreen()`，
  `setKeepVisibleCondition { !AppInitState.isReady }`（1.5s 超时兜底已存在，ready 必达）。
- 分层定位：**系统 splash 顶住进程创建期（Hilt/Application/首帧前），Compose 粒子动画
  退居「品牌延展层」**——不删现有动画，双轨叠加，正是官方推荐的 launch sequence 结构。
- 边界与风险：targetSdk=28 不影响（SplashScreen 是系统版本特性，与 targetSdk 无关）；
  品牌图 200×80dp 属可选资源，无设计稿则跳过；`setKeepVisibleCondition` 推迟首帧是
  官方推荐的「稳定才交棒」，但会让系统 splash 最多停留 ~1.5s，需真机验证观感。

## 8. Batch D：测试与收尾

- D1 单测：`SplashStage` 时间轴（三档边界 / stageProgress / remainingSeconds）；
  `SplashQualityLevel` 分档阈值；A1 的退出判定抽成纯函数
  `shouldDismiss(elapsedMs, minDisplayMs, isReady, totalMs)` 单测覆盖。
- D2 i18n：补 `splash_style_*` 4 条 + `splash_stage_*` 5 条 en 翻译。
- D3 清理：KDoc 1200/800/400 改为与实现一致的 1800/1200/600；`reset()` 删除或补测试保留。
- D4 `AGENTS.md` 加教训行：「启动画面退出必须与初始化就绪挂钩，禁止纯时间轴 + 假进度」。

## 9. 验收标准（每批通用）

1. 四门禁全绿：`:app:compileReleaseKotlin` + `:app:testReleaseUnitTest` +
   `:app:detekt` + `:app:lintRelease`（压内存参数，以日志 BUILD SUCCESSFUL 为准）。
2. B 批附性能证据：Android Studio Profiler 确认动画期无每帧 shader/array 分配
   （A/B 前后对比 allocation 记录）。
3. A1 附行为证据：初始化就绪日志与 splash 退出日志时间差 ≤ minDisplayMs。
4. 冷启动不劣化：`adb shell am start -W` 中端机 TotalTime 对比基线。

## 10. 决策点（需用户拍板）

1. **批次顺序**：建议 A → B → D → C（C 涉及主题重构，放最后且单独评估）。
2. **C 批是否本轮做**：系统 SplashScreen 分层会改变冷启动首屏观感（多一层系统图标交接），
   虽为官方推荐架构，但属于「增强现有设计」而非修 bug——需你确认再动。
3. **A1 的节奏**：常规冷启动从 6s 降为「就绪即退 + 最短 1.2s」会明显改变现有默认体验
   （HIGH 档 6s 是你此前调过的品牌节奏），确认接受「品牌完整版只在初始化慢于 1.2s 时出现」。
4. **CLASSIC**：正名为「静态 Logo 淡入」并接入设置入口，还是直接删除该枚举项。
