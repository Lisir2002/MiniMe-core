package com.mini.me_core.feature.agent.presentation.component.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * MiniMe 自实现轻量级语法高亮器。
 *
 * 设计目标（对应设计文档 F2.1）：
 *  - 不引入第三方高亮库（不依赖 dev.snipme:highlights），控制 APK 体积；
 *  - 基于正则表达式做单遍扫描，按优先级 注释 > 字符串 > 数字 > 关键字 > 函数调用 > 类型 着色；
 *  - 支持 14 种常用语言：Kotlin / Python / JavaScript / TypeScript / Java / Bash /
 *    SQL / JSON / YAML / Markdown / XML / C / C++ / Go / Rust；
 *  - 高亮器只产出「token 区间 + 类别」，颜色由调用方（[CodeBlock]）从 MaterialTheme
 *    语义色映射，保证深色 / 浅色 / 换主题时颜色自动跟随，不在高亮器内硬编码颜色。
 */
object MiniMeSyntaxHighlighter {

    /** 高亮 token 类别。 */
    enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER, FUNCTION, TYPE }

    /** 一段高亮区间，[start] 包含、[end] 不包含。 */
    data class Range(val start: Int, val end: Int, val type: TokenType)

    /** 每种语言的词法规则。 */
    private data class LangSpec(
        val keywords: Set<String>,
        /** 单行注释正则（可多条）。 */
        val lineComments: List<Regex>,
        /** 块注释起止；null 表示不支持块注释。 */
        val blockCommentStart: Regex? = null,
        val blockCommentEnd: Regex? = null,
        /** 字符串字面量正则（含三引号原始字符串）。 */
        val strings: List<Regex>,
    )

    // ---------- 14 种语言关键字表 ----------

    private val KOTLIN_KW = setOf(
        "val", "var", "fun", "class", "interface", "object", "typealias", "package",
        "import", "if", "else", "when", "for", "while", "do", "return", "break",
        "continue", "try", "catch", "finally", "throw", "data", "class", "sealed",
        "enum", "override", "open", "private", "public", "protected", "internal",
        "companion", "init", "this", "super", "null", "true", "false", "is", "as",
        "in", "out", "by", "lazy", "suspend", "coroutine", "launch", "run", "let",
        "apply", "also", "with", "typeof", "where", "field", "property", "const"
    )

    private val PYTHON_KW = setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
        "return", "break", "continue", "try", "except", "finally", "raise", "with",
        "lambda", "pass", "None", "True", "False", "and", "or", "not", "in", "is",
        "print", "self", "yield", "global", "nonlocal", "assert", "del", "async",
        "await", "def", "match", "case", "async"
    )

    private val JS_KW = setOf(
        "var", "let", "const", "function", "class", "extends", "new", "if", "else",
        "for", "while", "do", "return", "break", "continue", "switch", "case",
        "default", "try", "catch", "finally", "throw", "typeof", "instanceof", "in",
        "of", "new", "this", "super", "null", "undefined", "true", "false", "async",
        "await", "yield", "import", "from", "export", "default", "static", "get", "set"
    )

    private val TYPESCRIPT_KW = JS_KW + setOf(
        "interface", "type", "enum", "namespace", "declare", "implements", "readonly",
        "public", "private", "protected", "abstract", "as", "keyof", "infer",
        "satisfies", "is", "string", "number", "boolean", "any", "unknown", "never",
        "void", "undefined", "null"
    )

    private val JAVA_KW = setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements",
        "enum", "package", "import", "if", "else", "for", "while", "do", "return",
        "break", "continue", "switch", "case", "default", "try", "catch", "finally",
        "throw", "throws", "new", "this", "super", "null", "true", "false", "static",
        "final", "void", "int", "long", "short", "byte", "float", "double", "char",
        "boolean", "abstract", "synchronized", "volatile", "transient", "native",
        "instanceof", "var", "record", "sealed", "permits"
    )

    private val BASH_KW = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
        "case", "esac", "function", "return", "exit", "local", "export", "readonly",
        "declare", "echo", "cd", "ls", "pwd", "cp", "mv", "rm", "mkdir", "cat",
        "grep", "sed", "awk", "sudo", "chmod", "chown", "source", "alias", "shift",
        "break", "continue", "in", "select", "time"
    )

    private val SQL_KW = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set",
        "delete", "create", "table", "alter", "drop", "index", "view", "join",
        "inner", "left", "right", "full", "outer", "on", "as", "and", "or", "not",
        "null", "is", "in", "between", "like", "group", "by", "order", "having",
        "limit", "offset", "union", "all", "distinct", "case", "when", "then", "else",
        "end", "exists", "primary", "key", "foreign", "references", "default", "unique",
        "count", "sum", "avg", "min", "max", "asc", "desc", "int", "varchar", "text",
        "boolean", "timestamp", "date"
    )

    private val CPP_KW = setOf(
        "include", "define", "ifdef", "ifndef", "endif", "pragma", "using", "namespace",
        "class", "struct", "enum", "union", "typedef", "template", "typename", "if",
        "else", "for", "while", "do", "return", "break", "continue", "switch", "case",
        "default", "try", "catch", "throw", "new", "delete", "this", "nullptr", "true",
        "false", "const", "static", "virtual", "override", "public", "private",
        "protected", "void", "int", "char", "short", "long", "float", "double",
        "unsigned", "signed", "auto", "bool", "size_t", "auto", "concept", "requires"
    )

    private val GO_KW = setOf(
        "package", "import", "func", "return", "var", "const", "type", "struct",
        "interface", "chan", "go", "defer", "select", "case", "switch", "if", "else",
        "for", "range", "break", "continue", "goto", "fallthrough", "map", "slice",
        "len", "cap", "make", "new", "append", "copy", "delete", "panic", "recover",
        "nil", "true", "false", "iota", "string", "int", "int64", "float64", "bool",
        "byte", "rune"
    )

    private val RUST_KW = setOf(
        "let", "mut", "fn", "struct", "enum", "trait", "impl", "pub", "use", "mod",
        "crate", "self", "Self", "super", "as", "where", "for", "while", "loop",
        "if", "else", "match", "return", "break", "continue", "ref", "move", "dyn",
        "async", "await", "const", "static", "type", "extern", "crate", "mod",
        "true", "false", "Some", "None", "Ok", "Err", "Box", "Vec", "String",
        "i32", "i64", "u32", "u64", "f32", "f64", "bool", "str", "usize", "isize"
    )

    // 通用字符串正则
    private val STR_DQ = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"?")
    private val STR_SQ = Regex("'(?:\\\\.|[^'\\\\\\n])*'?")
    private val STR_TICK = Regex("`(?:\\\\.|[^`\\\\])*`")
    private val STR_TRIPLE = Regex("\"\"\"[\\s\\S]*?(?:\"\"\"|$)")

    private val specs: Map<String, LangSpec> = buildMap {
        put("kotlin", LangSpec(KOTLIN_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK, STR_TRIPLE)))
        put("py", LangSpec(PYTHON_KW, listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ, Regex("\"\"\"[\\s\\S]*?(?:\"\"\"|$)"), Regex("'''[\\s\\S]*?(?:'''|$)"))))
        put("python", LangSpec(PYTHON_KW, listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ, Regex("\"\"\"[\\s\\S]*?(?:\"\"\"|$)"), Regex("'''[\\s\\S]*?(?:'''|$)"))))
        put("js", LangSpec(JS_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("javascript", LangSpec(JS_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("ts", LangSpec(TYPESCRIPT_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("typescript", LangSpec(TYPESCRIPT_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("java", LangSpec(JAVA_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("bash", LangSpec(BASH_KW, listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ)))
        put("sh", LangSpec(BASH_KW, listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ)))
        put("shell", LangSpec(BASH_KW, listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ)))
        put("sql", LangSpec(SQL_KW, listOf(Regex("--[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_SQ, Regex("`[^`]*`"))))
        put("json", LangSpec(emptySet(), emptyList(), null, null, listOf(STR_DQ)))
        put("yaml", LangSpec(emptySet(), listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ)))
        put("yml", LangSpec(emptySet(), listOf(Regex("#[^\\n]*")), null, null,
            listOf(STR_DQ, STR_SQ)))
        put("md", LangSpec(emptySet(), emptyList(), null, null, listOf(STR_TICK)))
        put("markdown", LangSpec(emptySet(), emptyList(), null, null, listOf(STR_TICK)))
        put("xml", LangSpec(emptySet(), emptyList(), Regex("<!--"), Regex("-->"), listOf(STR_DQ, STR_SQ)))
        put("c", LangSpec(CPP_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ)))
        put("cpp", LangSpec(CPP_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ)))
        put("c++", LangSpec(CPP_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ)))
        put("go", LangSpec(GO_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("rust", LangSpec(RUST_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
        put("rs", LangSpec(RUST_KW, listOf(Regex("//[^\\n]*")), Regex("/\\*"), Regex("\\*/"),
            listOf(STR_DQ, STR_SQ, STR_TICK)))
    }

    /** 归一化语言标识到 key。 */
    private fun specFor(language: String?): LangSpec? {
        if (language.isNullOrBlank()) return null
        return specs[language.trim().lowercase()]
    }

    /** 主入口：对 [code] 做单遍扫描，返回高亮区间列表。 */
    fun tokenize(code: String, language: String?): List<Range> {
        val spec = specFor(language) ?: return emptyList()
        val ranges = mutableListOf<Range>()
        var i = 0
        val n = code.length
        while (i < n) {
            val rest = code.substring(i)
            // 1) 块注释
            val bc = spec.blockCommentStart
            if (bc != null) {
                val m = bc.findAt(rest, 0)
                if (m != null && m.range.first == 0) {
                    val endRe = spec.blockCommentEnd!!.find(rest, m.range.last + 1)
                    val end = if (endRe != null) endRe.range.last + 1 else n - i
                    ranges.add(Range(i, i + end, TokenType.COMMENT))
                    i += end
                    continue
                }
            }
            // 2) 单行注释
            var matchedLineComment = false
            for (lc in spec.lineComments) {
                val m = lc.findAt(rest, 0)
                if (m != null && m.range.first == 0) {
                    ranges.add(Range(i, i + m.range.last + 1, TokenType.COMMENT))
                    i += m.range.last + 1
                    matchedLineComment = true
                    break
                }
            }
            if (matchedLineComment) continue
            // 3) 字符串
            var matchedString = false
            for (sr in spec.strings) {
                val m = sr.findAt(rest, 0)
                if (m != null && m.range.first == 0 && m.value.isNotEmpty()) {
                    ranges.add(Range(i, i + m.value.length, TokenType.STRING))
                    i += m.value.length
                    matchedString = true
                    break
                }
            }
            if (matchedString) continue
            // 4) 数字
            val numM = NUMBER_RE.findAt(rest, 0)
            if (numM != null && numM.range.first == 0) {
                ranges.add(Range(i, i + numM.value.length, TokenType.NUMBER))
                i += numM.value.length
                continue
            }
            // 5) 标识符（关键字 / 函数 / 类型）
            val idM = IDENT_RE.findAt(rest, 0)
            if (idM != null && idM.range.first == 0) {
                val word = idM.value
                val type = when {
                    word in spec.keywords -> TokenType.KEYWORD
                    // 后随 ( 视为函数调用
                    rest.startsWith("(", word.length) -> TokenType.FUNCTION
                    // 首字母大写视为类型
                    word.first().isUpperCase() -> TokenType.TYPE
                    else -> null
                }
                if (type != null) ranges.add(Range(i, i + word.length, type))
                i += word.length
                continue
            }
            // 其它字符原样跳过
            i++
        }
        return ranges
    }

    private val NUMBER_RE = Regex("(0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(\\.\\d+)?([eE][+-]?\\d+)?)")
    private val IDENT_RE = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * 用 [colorFor] 把区间上色，构建可直接渲染的 [AnnotatedString]。
     * 颜色由调用方按主题语义色提供，高亮器本身不持有颜色。
     */
    fun build(code: String, ranges: List<Range>, colorFor: (TokenType) -> Color): AnnotatedString =
        buildAnnotatedString {
            append(code)
            for (r in ranges) {
                if (r.start < 0 || r.end > code.length || r.start >= r.end) continue
                addStyle(SpanStyle(color = colorFor(r.type)), r.start, r.end)
            }
        }

    /** 判断是否支持该语言（用于决定是否显示语言标签）。 */
    fun isSupported(language: String?): Boolean = specFor(language) != null
}

/** 在字符串 [s] 从 [start] 处查找匹配，返回的 MatchResult 的索引是相对 [s] 的。 */
private fun Regex.findAt(s: CharSequence, start: Int): MatchResult? = find(s, start)
