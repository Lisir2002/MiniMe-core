package guard

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asSequence

/**
 * 架构守卫测试（"牙"，编译期/测试期强制）。
 *
 * 规则来源：任务依赖规则 #1/#2/#3。本测试不依赖 Android SDK，只把每个新模块的
 * `src/main/java/**/*.kt` 当作文本扫描 import 列表；一旦命中禁止的包前缀就让构建失败。
 *
 * 这是「违规即失败」的兜底：Gradle 依赖图禁止做不到的部分（例如同仓 feature 包名互相可见），
 * 由这里在源码层兜住。
 */
class DependencyGuardTest {

    /** 仓库根目录：build-logic 位于 <repo>/build-logic，故根为上一级。 */
    private val repoRoot: Path = run {
        val fromProp = System.getProperty("repo.root")
        if (!fromProp.isNullOrBlank()) Path.of(fromProp).toRealPath()
        else Path.of("..").toRealPath()
    }

    /** 模块相对路径 → 该模块 src/main 下**禁止出现**的 import 前缀（精确到 `import <prefix>`）。 */
    private val rules: Map<String, List<String>> = mapOf(
        // 纯 Kotlin 核心模块：禁 Android / feature / datalayer。
        "core/model" to listOf(
            "android.", "com.mini.me_core.feature.", "com.mini.me_core.datalayer.",
        ),
        "core/agent-workflow" to listOf(
            "android.", "com.mini.me_core.feature.", "com.mini.me_core.datalayer.",
            // 架构规则 #2：只允许依赖 :core:model，禁止直接吃其它 core 子模块实现。
            "com.mini.me_core.core.network.",
            "com.mini.me_core.core.security.",
            "com.mini.me_core.core.util.",
        ),
        // Android 核心模块：禁反向依赖 feature / datalayer（domain 接口在 core/model 或由 :app 装配）。
        "core/security" to listOf(
            "com.mini.me_core.feature.", "com.mini.me_core.datalayer.",
        ),
        "core/container" to listOf(
            "com.mini.me_core.feature.", "com.mini.me_core.datalayer.",
        ),
        "core/network" to listOf(
            "com.mini.me_core.feature.", "com.mini.me_core.datalayer.",
        ),
        "core/database" to listOf(
            "com.mini.me_core.feature.",
        ),
        // feature 模块：禁止 feature → feature 直接依赖（架构规则 #1）。
        "feature/agent" to listOf("com.mini.me_core.feature.browser."),
        "feature/browser" to listOf("com.mini.me_core.feature.agent."),
        // :app 内部 feature 包方向守卫（文本扫描 app/src/main/java 下对应子树）。
        // backup 只允许依赖 agent.domain，禁止直连 agent.data（实体/datalayer 实现）。
        "app/src/main/java/com/mini/me_core/feature/backup" to listOf(
            "com.mini.me_core.feature.agent.data.",
        ),
        // settings 只允许依赖 agent.domain，禁止直连 agent.presentation（UI 组件已下沉共享 ui.markdown）。
        "app/src/main/java/com/mini/me_core/feature/settings" to listOf(
            "com.mini.me_core.feature.agent.presentation.",
        ),
        // agent/domain 只面向端口，禁止反向依赖 settings.data.repository（ExecutionMode/NormFlow/ZthTier 已端口化）。
        "app/src/main/java/com/mini/me_core/feature/agent/domain" to listOf(
            "com.mini.me_core.feature.settings.data.repository.",
        ),
    )

    @Test
    fun `every new module must not import banned packages`() {
        val violations = mutableListOf<String>()

        for ((moduleRel, bannedPrefixes) in rules) {
            // 支持两种键：模块相对路径（自动拼 /src/main/java），或已含 src/main/java 的子树直接用作根。
            val srcRoot = if (moduleRel.contains("src/main/java")) {
                repoRoot.resolve(moduleRel)
            } else {
                repoRoot.resolve("$moduleRel/src/main/java")
            }
            if (!Files.isDirectory(srcRoot)) {
                // 模块尚未建立（增量重构进行中）：跳过，不报错。
                continue
            }
            ktFiles(srcRoot).forEach { file ->
                val rel = repoRoot.relativize(file)
                Files.readAllLines(file).forEach { line ->
                    if (!line.startsWith("import ")) return@forEach
                    val imp = line.removePrefix("import ").trim()
                    // 容忍 import 别名与 static import 的尾部差异：只比对包前缀。
                    val pkg = imp.substringBefore(" as ").trim()
                    for (banned in bannedPrefixes) {
                        if (pkg == banned.dropLast(1) || pkg.startsWith(banned)) {
                            violations.add("$rel -> 禁止依赖 $banned :: $imp")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            fail(
                "架构守卫失败：发现 ${violations.size} 处违规依赖（模块只能按目标模块图依赖）：\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun `repoRoot must be resolvable`() {
        assertTrue("仓库根目录不存在: $repoRoot", Files.isDirectory(repoRoot))
        assertTrue(
            "settings.gradle.kts 不在仓库根，repoRoot 解析错误: $repoRoot",
            Files.exists(repoRoot.resolve("settings.gradle.kts"))
        )
    }

    private fun ktFiles(root: Path): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { stream: Stream<Path> ->
            stream.asSequence().filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }
    }
}
