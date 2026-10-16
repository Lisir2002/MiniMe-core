package com.mini.me_core.feature.editor.textmate

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TextMate grammar 资产结构校验（回归防护，R 规格见提交说明）。
 *
 * 背景（0.0.1.5）：`xml.json` 一条规则的 `end`/`name` 属性被错塞进 `captures` map，
 * 产生 String 值 → tm4e 在 tokenize 该规则时抛 ClassCastException → 整个 grammar
 * 注册失败 → 所有 include `text.xml` 的语言（php/ruby/perl/blade/erb…共 22 种）
 * 高亮静默退化为纯文本（sora 兜底），用户侧表现为「PHP 无法高亮」。
 *
 * 经最小 grammar 探针实证（独立 JVM 单用例，逐一复现各结构形态），tm4e 的耐受表：
 *
 * | 结构形态                 | tokenize 行为                       | 定性             |
 * |------------------------|-----------------------------------|----------------|
 * | captures 值为 String    | ClassCastException                | **毒药**         |
 * | captures 值为 null      | NullPointerException              | **毒药**         |
 * | captures 值为 单元素数组 | 正常高亮（jinja.json 存量形态）      | 安全，不可禁      |
 * | captures 键非数字        | 静默接受                           | 安全            |
 * | repository 值为数组      | ClassCastException                | **毒药**         |
 * | repository 值为字符串    | ClassCastException                | **毒药**         |
 * | begin 无 end            | 不崩（begin 后永不退出）             | 语义缺陷，存量通病 |
 * | match 与 begin/end 混写 | 不崩（match 先生效）                | 存量通病         |
 * | include 指向不存在键     | 不崩（规则静默失效）                 | 存量通病         |
 * | 嵌套 repository         | 不崩（tm4e 只查顶层，嵌套内静默失效）   | 存量通病         |
 *
 * 本测试只阻断「毒药」类结构（tokenize 必抛异常、高亮整体失效）与索引一致性；
 * 存量上游 grammar 的通病不在阻断范围，避免误报。
 */
class GrammarAssetsStructureTest {

    companion object {
        /** assets 根目录（含 editor/languages.json）。Gradle 单测 cwd 为模块目录，逐级向上兜底。 */
        private val assetsRoot: File by lazy {
            var dir: File? = File(System.getProperty("user.dir")).canonicalFile
            while (dir != null) {
                val candidate = File(dir, "app/src/main/assets")
                if (File(candidate, "editor/languages.json").isFile) return@lazy candidate
                dir = dir.parentFile
            }
            throw IllegalStateException(
                "未定位到 app/src/main/assets（user.dir=${System.getProperty("user.dir")}）"
            )
        }

        private val grammarsDir: File get() = File(assetsRoot, "editor/grammars")
        private val indexFile: File get() = File(assetsRoot, "editor/languages.json")

        /** 毒药形态在源码中的 JSON 路径，如 "xml.json#repository.comments.patterns[0].captures.end"。 */
        private val violations = mutableListOf<String>()

        private fun v(file: String, path: String, kind: String, detail: String) {
            violations += "$file#$path [$kind] $detail"
        }
    }

    @Test
    fun grammarAssets_noTokenizePoisonStructures() {
        violations.clear()
        val files = grammarsDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        assertTrue("grammars 目录为空或不存在: $grammarsDir", files.isNotEmpty())

        for (file in files.sortedBy { it.name }) {
            val root = try {
                JsonParser.parseString(file.readText())
            } catch (e: Exception) {
                v(file.name, "<root>", "json-parse-error", e.message ?: e.javaClass.simpleName)
                continue
            }
            if (!root.isJsonObject) {
                v(file.name, "<root>", "root-not-object", root.toString().take(60))
                continue
            }
            val doc = root.asJsonObject
            if (!doc.has("scopeName") || doc.get("scopeName").asString.isEmpty()) {
                v(file.name, "<root>", "missing-scopeName", doc.toString().take(60))
            }
            // 顶层 patterns（对象或数组均可）与顶层 repository 逐层校验
            doc.get("patterns")?.let { scanRule(it, file.name, "patterns") }
            doc.get("repository")?.let { repo ->
                if (!repo.isJsonObject) {
                    v(file.name, "repository", "repo-not-object", repo.toString().take(60))
                } else {
                    for ((key, value) in repo.asJsonObject.entrySet()) {
                        if (!value.isJsonObject) {
                            v(file.name, "repository.$key", "repo-entry-not-object", value.toString().take(60))
                        }
                        scanRule(value, file.name, "repository.$key")
                    }
                }
            }
        }

        report("grammar 结构毒药（captures 为 null/字符串、repository 值非对象）")
    }

    @Test
    fun languageIndex_consistentWithGrammarFiles() {
        violations.clear()
        val index = JsonParser.parseString(indexFile.readText()).asJsonObject
        val languages = index.getAsJsonArray("languages")
        assertTrue("languages.json 条目为空", languages.size() > 0)

        val scopes = mutableSetOf<String>()
        val indexedFiles = mutableSetOf<String>()
        for (element in languages) {
            val entry = element.asJsonObject
            val name = entry.get("name")?.asString ?: "<unnamed>"
            val scope = entry.get("scopeName")?.asString
            val grammarPath = entry.get("grammar")?.asString
            if (scope.isNullOrEmpty()) {
                v("languages.json", name, "missing-scopeName", entry.toString().take(60))
                continue
            }
            if (!scopes.add(scope)) {
                v("languages.json", name, "duplicate-scopeName", scope)
            }
            if (grammarPath.isNullOrEmpty()) {
                v("languages.json", name, "missing-grammar-path", entry.toString().take(60))
                continue
            }
            val grammarFile = File(assetsRoot, grammarPath)
            if (!grammarFile.isFile) {
                v("languages.json", name, "grammar-file-missing", grammarPath)
                continue
            }
            indexedFiles += grammarFile.name
            val doc = JsonParser.parseString(grammarFile.readText()).asJsonObject
            val fileScope = doc.get("scopeName")?.asString
            if (fileScope != scope) {
                v("languages.json", name, "scopeName-mismatch", "index=$scope file=$fileScope")
            }
        }

        // 磁盘 grammar 与索引互相不错位（多出/缺失均告警）
        val onDisk = grammarsDir.listFiles { f -> f.extension == "json" }
            ?.map { it.name }?.toSet() ?: emptySet()
        val unindexed = onDisk - indexedFiles
        val missing = indexedFiles - onDisk
        if (unindexed.isNotEmpty()) {
            v("languages.json", "-", "unindexed-grammars", unindexed.sorted().toString())
        }
        if (missing.isNotEmpty()) {
            v("languages.json", "-", "indexed-but-absent", missing.sorted().toString())
        }

        report("languages.json 索引一致性")
    }

    // ── 递归结构扫描 ──

    /**
     * 扫描一个规则节点（对象；单元素/多元素数组形态也兼容——tm4e 的 patterns
     * 与 captures 值均容忍数组，见类注释耐受表）。
     */
    private fun scanRule(node: JsonElement, file: String, path: String) {
        if (node.isJsonArray) {
            for (i in 0 until node.asJsonArray.size()) {
                scanRule(node.asJsonArray[i], file, "$path[$i]")
            }
            return
        }
        if (!node.isJsonObject) return
        val rule = node.asJsonObject

        // 毒药 1：captures 家族值为 null / 字符串（tm4e compile 时 CCE/NPE）
        for (capturesKey in listOf("captures", "beginCaptures", "endCaptures", "whileCaptures")) {
            val captures = rule.get(capturesKey) ?: continue
            if (!captures.isJsonObject) continue
            for ((group, value) in captures.asJsonObject.entrySet()) {
                when {
                    value.isJsonNull ->
                        v(file, "$path.$capturesKey.$group", "capture-null", "captures 值为 null → tokenize NPE")
                    value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                        v(file, "$path.$capturesKey.$group", "capture-string",
                            "\"${value.asString}\" → tokenize ClassCastException")
                }
            }
        }

        // 毒药 2：repository 值非对象（数组/字符串/null → tokenize CCE）
        val repo = rule.get("repository")
        if (repo != null && repo.isJsonObject) {
            for ((key, value) in repo.asJsonObject.entrySet()) {
                if (!value.isJsonObject) {
                    v(file, "$path.repository.$key", "repo-entry-not-object",
                        "${value.toString().take(40)} → tokenize ClassCastException")
                }
                scanRule(value, file, "$path.repository.$key")
            }
        }

        // patterns 递归（tm4e 容忍对象或数组两种形态）
        rule.get("patterns")?.let { scanRule(it, file, "$path.patterns") }
    }

    private fun report(title: String) {
        assertTrue(
            buildString {
                appendLine("$title 校验失败，共 ${violations.size} 处违令（修复前这些形态会导致整个 grammar tokenize 抛异常、高亮静默退化为纯文本）：")
                violations.forEach { appendLine("  - $it") }
            },
            violations.isEmpty()
        )
    }
}
