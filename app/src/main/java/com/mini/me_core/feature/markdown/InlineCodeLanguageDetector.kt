package com.mini.me_core.feature.markdown

/**
 * 行内代码语言自动检测器（纯逻辑，不依赖 Android / TextMate 运行时）。
 *
 * 从 [InlineCodeHighlighter] 抽取而来，便于在 JVM 单元测试中直接覆盖各语言识别分支。
 * 「该 scopeName 的 grammar 是否已加载」这一外部副作用由调用方通过 [grammarAvailable] 注入。
 */
internal object InlineCodeLanguageDetector {

    /** Markdown grammar 的 scopeName（普通版与增强版前缀）。 */
    private val MARKDOWN_SCOPES = setOf(
        "text.html.markdown",
        "text.html.markdown.enhanced",
    )

    /**
     * 按优先级检测代码片段的语言 scopeName。
     *  1. 上下文推断（邻近代码块语言）
     *  2. 特征关键字匹配
     *  3. 注释符号/括号启发式
     *  4. 兜底返回 null（纯文本）
     *
     * @param code 反引号内部的代码文本
     * @param contextLanguage 上下文推断出的语言 scopeName，可为 null
     * @param grammarAvailable 判定某 scopeName 的 grammar 是否已加载可用
     */
    fun detect(
        code: String,
        contextLanguage: String?,
        grammarAvailable: (String) -> Boolean,
    ): String? {
        // 1. 上下文已给出可信语言（且不是 markdown 自身，grammar 已加载）
        if (!contextLanguage.isNullOrEmpty() &&
            contextLanguage !in MARKDOWN_SCOPES &&
            grammarAvailable(contextLanguage)
        ) {
            return contextLanguage
        }

        // 2. 特征关键字匹配
        matchByFeatures(code)?.let { return it }

        // 3. 启发式：注释符号
        matchByHeuristics(code)?.let { return it }

        return null
    }

    /** 特征关键字匹配，覆盖常见语言。 */
    fun matchByFeatures(code: String): String? {
        // Python
        if (Regex("""\b(def\s+\w+|class\s+\w+\s*\(|import\s+\w+|from\s+\w+\s+import|print\s*\()""").containsMatchIn(code)
        ) return "source.python"

        // Rust
        if (Regex("""\b(fn\s+\w+|let\s+mut\s+|struct\s+\w+|impl\s+|::|<[A-Z]\w*>\s*\{)""").containsMatchIn(code)
        ) return "source.rust"

        // Go
        if (Regex("""\b(func\s+\w+\s*\(|package\s+\w+|import\s+\(|:=|interface\s+\{)""").containsMatchIn(code)
        ) return "source.go"

        // PHP
        if (code.trimStart().startsWith("<?php") || Regex("""\$\w+\s*=|->\w+\s*\(""").containsMatchIn(code)
        ) return "source.php"

        // Java
        if (Regex("""\b(public\s+class|public\s+static\s+void|System\.out\.|private\s+\w+\s+\w+\s*;)""").containsMatchIn(code)
        ) return "source.java"

        // C/C++（在 JS 之前判断，避免 #include 误判）
        if (code.trimStart().startsWith("#include") || Regex("""\b(std::|cout|cin|namespace\s+\w+|printf\s*\()""").containsMatchIn(code)
        ) return "source.cpp"

        // TypeScript（在 JS 之前，因为 TS 多了类型标注）
        if (Regex("""\b(interface\s+\w+\s*\{|type\s+\w+\s*=|:\s*(string|number|boolean|void)\b|enum\s+\w+)""").containsMatchIn(code)
        ) return "source.ts"

        // JavaScript
        if (Regex("""\b(const\s+\w+|let\s+\w+|=>|function\s*\w*\s*\(|require\s*\(|module\.exports|console\.(log|error))""").containsMatchIn(code)
        ) return "source.js"

        // HTML
        if (Regex("""</?\w+|<div|<span|<body|<head|<!doctype""", RegexOption.IGNORE_CASE).containsMatchIn(code)
        ) return "text.html.basic"

        // CSS
        if (Regex("""[.#]?\w+\s*\{[^}]*:\s*[\w#.%]+\s*;""").containsMatchIn(code)
        ) return "source.css"

        // SQL
        if (Regex("""\b(select|from|where|insert\s+into|create\s+table|order\s+by|group\s+by)\b""", RegexOption.IGNORE_CASE).containsMatchIn(code)
        ) return "source.sql"

        return null
    }

    /** 启发式：注释符号与括号风格。 */
    fun matchByHeuristics(code: String): String? {
        // 整段都是 # 注释 → Python / Shell
        val hashCommentLines = code.lineSequence().count { it.trimStart().startsWith("#") }
        val hasDoubleSlash = code.lineSequence().any { it.trimStart().startsWith("//") }

        if (hasDoubleSlash) {
            // // 注释多见于 C 系 / JS / Java；已有特征匹配兜底，这里仅在无其它线索时倾向 C++
            return "source.cpp"
        }
        if (hashCommentLines > 0 && (code.contains("echo ") || code.contains("export ") || code.contains("$"))) {
            return "source.shell"
        }
        // -- 注释 + 大写关键字 → SQL
        if (code.lineSequence().any { it.trimStart().startsWith("--") } &&
            Regex("""\b(select|from|where)\b""", RegexOption.IGNORE_CASE).containsMatchIn(code)
        ) return "source.sql"

        return null
    }
}
