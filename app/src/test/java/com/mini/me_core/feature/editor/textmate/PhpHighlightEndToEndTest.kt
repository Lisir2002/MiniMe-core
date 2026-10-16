package com.mini.me_core.feature.editor.textmate

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import org.eclipse.tm4e.core.grammar.IGrammar
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * TextMate 高亮端到端回归（0.0.1.5 PHP 高亮失效修复的防护）。
 *
 * 复现用户报告的故障链：PHP 文件在阅读器与编辑器均无高亮。
 * 根因是 xml.json 结构毒药（详见 [GrammarAssetsStructureTest] 类注释），
 * 导致 include text.xml 的 22 个 grammar 注册失败，tokenize 静默退化为纯文本。
 *
 * 本测试走真实引擎链路（sora GrammarRegistry + tm4e tokenize + assets 真实文件），
 * 断言：
 *  1. 内置 326 个 grammar 全部可加载（findGrammar 非 null）
 *  2. PHP 各语法元素拿到预期 TextMate 作用域（与 harness 实证一致）
 *  3. xml.json 波及的语言（html/ruby/perl/blade/erb/markdown/xml）高亮正常
 *  4. 本轮直接修复的 racket/stata/wikitext 高亮正常
 *
 * 注意：GrammarRegistry/FileProviderRegistry 为进程级单例，本类只加载一次；
 * 断言用的作用域均事先经最小 grammar 探针与全量 harness 实证，非猜测值。
 */
class PhpHighlightEndToEndTest {

    companion object {
        private lateinit var registry: GrammarRegistry

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val assetsRoot = locateAssetsRoot()
            // 与线上 TextMateManager 相同的注册方式，只是 FileResolver 指向本地 assets 目录
            FileProviderRegistry.getInstance().addFileProvider(object : FileResolver {
                override fun resolveStreamByPath(path: String): InputStream? {
                    val file = File(assetsRoot, path)
                    return if (file.isFile) FileInputStream(file) else null
                }
            })
            registry = GrammarRegistry.getInstance()
            registry.loadGrammars("editor/languages.json")
        }

        private fun locateAssetsRoot(): File {
            var dir: File? = File(System.getProperty("user.dir")).canonicalFile
            while (dir != null) {
                val candidate = File(dir, "app/src/main/assets")
                if (File(candidate, "editor/languages.json").isFile) return candidate
                dir = dir.parentFile
            }
            throw IllegalStateException(
                "未定位到 app/src/main/assets（user.dir=${System.getProperty("user.dir")}）"
            )
        }
    }

    private fun scopesOfLine(grammar: IGrammar, line: String): List<String> =
        grammar.tokenizeLine(line).tokens.flatMap { it.scopes }

    private fun grammarOrFail(scopeName: String): IGrammar =
        assertNotNull("grammar 未加载: $scopeName", registry.findGrammar(scopeName)).let {
            registry.findGrammar(scopeName)!!
        }

    // ── 1. 全量加载 ──

    @Test
    fun allBuiltinGrammars_loadSuccessfully() {
        val index = File(locateAssetsRoot(), "editor/languages.json").readText()
        val scopes = Regex("\"scopeName\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(index).map { it.groupValues[1] }.toList()
        assertTrue("languages.json 未解析到 scopeName（共 ${scopes.size} 个）", scopes.size >= 300)

        val failed = mutableListOf<String>()
        for (scope in scopes) {
            try {
                if (registry.findGrammar(scope) == null) failed += "$scope (null)"
            } catch (e: Exception) {
                failed += "$scope (${e.javaClass.simpleName}: ${e.message})"
            }
        }
        assertTrue(
            "以下 grammar 加载失败（结构毒药会导致 tokenize 整体抛异常）：\n" +
                failed.joinToString("\n"),
            failed.isEmpty()
        )
    }

    // ── 2. PHP 语法元素高亮（用户报告的场景） ──

    @Test
    fun php_syntaxElements_highlighted() {
        val php = grammarOrFail("source.php")
        val expectations = listOf(
            "class Foo extends Bar implements Baz {" to "storage.type.class.php",
            "    public function index(int \$id = 0): string {" to "storage.modifier.php",
            "    public function index(int \$id = 0): string {" to "entity.name.function.php",
            "        \$sql = \"SELECT * FROM users WHERE id = \$id\";" to "source.sql.embedded.php",
            "        \$sql = \"SELECT * FROM users WHERE id = \$id\";" to "keyword.other.DML.sql",
            "        // line comment" to "comment.line.double-slash.php",
            "        return view('home', ['id' => \$id]);" to "keyword.control.return.php",
            "        \$name = \"world\";" to "variable.other.php",
        )
        for ((line, expectedScope) in expectations) {
            val scopes = scopesOfLine(php, line)
            assertTrue(
                "PHP 行缺少作用域 [$expectedScope]（高亮失效）：[$line]\n实际作用域：$scopes",
                scopes.any { it == expectedScope }
            )
        }
    }

    // ── 3. xml.json 波及语言（22 个 include text.xml 的 grammar 中挑代表） ──

    @Test
    fun xmlAffectedLanguages_highlighted() {
        // 每个条目：scopeName → (样例行, 期望作用域)。作用域均为 harness 实证值。
        val cases = listOf(
            "text.html.basic" to ("<!DOCTYPE html><html><body><h1>Hi</h1></body></html>" to "entity.name.tag.html"),
            "text.html.php.blade" to ("<div>{{ \$name }}</div>" to "variable.other.php"),
            "text.html.php.blade" to ("<div>{{ \$name }}</div>" to "source.php"),
            "text.html.erb" to ("<% if true %><%= @name %><% end %>" to "source.ruby"),
            "text.html.erb" to ("<% if true %><%= @name %><% end %>" to "keyword.control.ruby"),
            "source.ruby" to ("def greet(name)" to "keyword.control.def.ruby"),
            "source.ruby" to ("def greet(name)" to "entity.name.function.ruby"),
            "source.perl" to ("my \$x = 42; print \"\$x\\n\";" to "variable.other.readwrite.global.perl"),
            "source.perl" to ("my \$x = 42; print \"\$x\\n\";" to "storage.modifier.perl"),
            "text.html.markdown" to ("# Title" to "markup.heading.markdown"),
            "text.xml" to ("<root attr=\"v\">text</root>" to "entity.name.tag.localname.xml"),
            "text.xml" to ("<root attr=\"v\">text</root>" to "string.quoted.double.xml"),
        )
        for ((scopeName, sample) in cases) {
            val (line, expectedScope) = sample
            val scopes = scopesOfLine(grammarOrFail(scopeName), line)
            assertTrue(
                "$scopeName 行缺少作用域 [$expectedScope]（xml.json 毒药波及）：[$line]\n实际作用域：$scopes",
                scopes.any { it == expectedScope }
            )
        }
    }

    // ── 4. 本轮直接修复的 grammar ──

    @Test
    fun directlyFixedGrammars_highlighted() {
        val cases = listOf(
            "source.racket" to ("(define (square x) (* x x)) ; comment" to "entity.name.function.racket"),
            "source.racket" to ("(define (square x) (* x x)) ; comment" to "comment.line.semicolon.racket"),
            "source.stata" to ("gen z = x + 1" to "keyword.functions.data.stata"),
            "source.stata" to ("gen z = x + 1" to "constant.numeric.integer.decimal.stata"),
            "source.wikitext" to ("== Heading ==" to "markup.heading.wikitext"),
            "source.wikitext" to ("'''bold''' and [[Link|text]]" to "markup.bold.wikitext"),
        )
        for ((scopeName, sample) in cases) {
            val (line, expectedScope) = sample
            val scopes = scopesOfLine(grammarOrFail(scopeName), line)
            assertTrue(
                "$scopeName 行缺少作用域 [$expectedScope]（本轮修复回退）：[$line]\n实际作用域：$scopes",
                scopes.any { it == expectedScope }
            )
        }
    }
}
