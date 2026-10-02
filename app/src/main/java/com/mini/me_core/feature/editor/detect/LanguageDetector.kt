package com.mini.me_core.feature.editor.detect

import com.mini.me_core.feature.editor.textmate.TextMateManager
import java.io.File

/**
 * 语言自动检测器。
 *
 * 检测策略（按优先级）：
 *  1. 文件名匹配（Dockerfile、Makefile 等无扩展名文件）
 *  2. Shebang 行（#!/usr/bin/env python3 → Python）
 *  3. 扩展名映射
 *  4. Vim modeline / Emacs file variable
 *  5. 内容特征（首行特征匹配）
 *  6. 兜底：Plain Text（text.plain）
 */
object LanguageDetector {

    /** 无扩展名文件名 → scopeName */
    private val filenameMap = mapOf(
        "dockerfile" to "source.dockerfile",
        "makefile" to "source.makefile",
        "gnumakefile" to "source.makefile",
        "cmakeLists.txt" to "source.cmake",
        "cmakelists.txt" to "source.cmake",
        "vagrantfile" to "source.ruby",
        "gemfile" to "source.ruby",
        "rakefile" to "source.ruby",
        "brewfile" to "source.ruby",
        "procfile" to "source.d",
        "justfile" to "source.just",
        "build" to "python.bazel",
        "workspace" to "python.bazel",
        ".gitignore" to "source.gitignore",
        ".gitattributes" to "source.gitignore",
        ".gitmodules" to "source.gitmodules",
        ".dockerignore" to "source.gitignore",
        ".env" to "source.dotenv",
        ".envrc" to "source.sh",
        "docker-compose.yml" to "source.yaml",
        "docker-compose.yaml" to "source.yaml",
    )

    /** 扩展名 → scopeName（覆盖最常见 ~100 种；其余走 TextMateManager 注册表查询）。 */
    private val extensionMap = mapOf(
        // Web
        "html" to "text.html.basic",
        "htm" to "text.html.basic",
        "xhtml" to "text.html.basic",
        "shtml" to "text.html.basic",
        "css" to "source.css",
        "scss" to "source.css.scss",
        "sass" to "source.sass",
        "less" to "source.css.less",
        "styl" to "source.stylus",
        "vue" to "source.vue",
        "svelte" to "source.svelte",
        "astro" to "source.astro",
        // JS/TS
        "js" to "source.js",
        "mjs" to "source.js",
        "cjs" to "source.js",
        "jsx" to "source.js.jsx",
        "ts" to "source.ts",
        "cts" to "source.ts",
        "mts" to "source.ts",
        "tsx" to "source.tsx",
        // Python
        "py" to "source.python",
        "pyi" to "source.python",
        "pyw" to "source.python",
        "ipy" to "source.python",
        // JVM
        "java" to "source.java",
        "kt" to "source.kotlin",
        "kts" to "source.kotlin",
        "scala" to "source.scala",
        "sbt" to "source.scala",
        "groovy" to "source.groovy",
        "gradle" to "source.groovy",
        // C/C++
        "c" to "source.c",
        "h" to "source.c",
        "cpp" to "source.cpp",
        "cc" to "source.cpp",
        "cxx" to "source.cpp",
        "c++" to "source.cpp",
        "hpp" to "source.cpp",
        "hh" to "source.cpp",
        "hxx" to "source.cpp",
        "m" to "source.objc",
        "mm" to "source.cpp",
        // Systems
        "rs" to "source.rust",
        "go" to "source.go",
        "swift" to "source.swift",
        "zig" to "source.zig",
        "dart" to "source.dart",
        // Scripting
        "rb" to "source.ruby",
        "rake" to "source.ruby",
        "php" to "source.php",
        "phtml" to "source.php",
        "php3" to "source.php",
        "php4" to "source.php",
        "php5" to "source.php",
        "pl" to "source.perl",
        "pm" to "source.perl",
        "lua" to "source.lua",
        "luau" to "source.luau",
        "sh" to "source.shell",
        "bash" to "source.shell",
        "zsh" to "source.shell",
        "fish" to "source.fish",
        "ps1" to "source.powershell",
        "psm1" to "source.powershell",
        "bat" to "source.batchfile",
        "cmd" to "source.batchfile",
        "r" to "source.r",
        "R" to "source.r",
        "jl" to "source.julia",
        // Data formats
        "json" to "source.json",
        "jsonc" to "source.json",
        "json5" to "source.json5",
        "jsonl" to "source.json",
        "yaml" to "source.yaml",
        "yml" to "source.yaml",
        "toml" to "source.toml",
        "ini" to "source.ini",
        "cfg" to "source.ini",
        "conf" to "source.ini",
        "properties" to "source.ini",
        "csv" to "text.csv",
        "tsv" to "text.tsv",
        "xml" to "text.xml",
        "xsd" to "text.xml",
        "proto" to "source.proto",
        // Docs
        "md" to "text.html.markdown",
        "markdown" to "text.html.markdown",
        "mdown" to "text.html.markdown",
        "mkd" to "text.html.markdown",
        "mdx" to "text.html.markdown",
        "adoc" to "text.asciidoc",
        "asciidoc" to "text.asciidoc",
        "rst" to "text.restructuredtext",
        "tex" to "text.tex",
        "bib" to "text.bibtex",
        "typ" to "text.typst",
        "mmd" to "source.mermaid",
        // Build/DevOps
        "tf" to "source.hcl",
        "tfvars" to "source.hcl",
        "hcl" to "source.hcl",
        "cmake" to "source.cmake",
        "ninja" to "source.ninja",
        "bzl" to "python.bazel",
        "dockerfile" to "source.dockerfile",
        "nginx" to "source.nginx",
        // Query
        "sql" to "source.sql",
        "prisma" to "source.prisma",
        "graphql" to "source.graphql",
        "gql" to "source.graphql",
        // Other common
        "clj" to "source.clojure",
        "cljs" to "source.clojure",
        "cljc" to "source.clojure",
        "edn" to "source.clojure",
        "ex" to "source.elixir",
        "exs" to "source.elixir",
        "erl" to "source.erlang",
        "hrl" to "source.erlang",
        "fs" to "source.fsharp",
        "fsx" to "source.fsharp",
        "ml" to "source.ocaml",
        "mli" to "source.ocaml",
        "hs" to "source.haskell",
        "lhs" to "source.haskell",
        "purs" to "source.purescript",
        "elm" to "source.elm",
        "sol" to "source.solidity",
        "vy" to "source.vyper",
        "vyper" to "source.vyper",
        "v" to "source.v",
        "nim" to "source.nim",
        "cr" to "source.crystal",
        "d" to "source.d",
        "zig" to "source.zig",
        "wasm" to "source.wat",
        "wat" to "source.wat",
        "wgsl" to "source.wgsl",
        "glsl" to "source.glsl",
        "frag" to "source.glsl",
        "vert" to "source.glsl",
        "cu" to "source.cpp",
        "ipynb" to "source.json",
        "diff" to "text.diff",
        "patch" to "text.diff",
        "log" to "text.log",
        "vim" to "source.viml",
        "el" to "source.elisp",
        "scm" to "source.scheme",
        "ss" to "source.scheme",
        "lisp" to "source.commonlisp",
        "rkt" to "source.racket",
        "tex" to "text.tex.latex",
        "sty" to "text.tex.latex",
        "cls" to "text.tex.latex",
    )

    /** Shebang interpreter → scopeName */
    private val shebangMap = mapOf(
        "python" to "source.python",
        "python2" to "source.python",
        "python3" to "source.python",
        "python3.11" to "source.python",
        "python3.12" to "source.python",
        "ruby" to "source.ruby",
        "perl" to "source.perl",
        "perl5" to "source.perl",
        "node" to "source.js",
        "nodejs" to "source.js",
        "deno" to "source.ts",
        "bun" to "source.js",
        "bash" to "source.shell",
        "sh" to "source.shell",
        "zsh" to "source.shell",
        "fish" to "source.fish",
        "pwsh" to "source.powershell",
        "powershell" to "source.powershell",
        "lua" to "source.lua",
        "Rscript" to "source.r",
        "R" to "source.r",
        "julia" to "source.julia",
        "tcl" to "source.tcl",
        "awk" to "source.awk",
        "node" to "source.js",
    )

    /**
     * 检测文件应使用的 TextMate scopeName。
     *
     * @param file 文件
     * @param firstLines 文件前 N 行文本（用于 shebang/modeline 检测；可为空）
     * @return scopeName，未识别返回 "text.plain"
     */
    fun detect(file: File, firstLines: String? = null): String {
        val name = file.name

        // 1. 文件名精确匹配（Dockerfile、Makefile 等）
        filenameMap[name.lowercase()]?.let { return it }

        // 2. 扩展名
        val ext = file.extension.lowercase()
        if (ext.isNotEmpty()) {
            extensionMap[ext]?.let { return it }
        }

        // 3. Shebang
        if (!firstLines.isNullOrEmpty()) {
            detectFromShebang(firstLines)?.let { return it }
            detectFromModeline(firstLines)?.let { return it }
            detectFromContent(firstLines)?.let { return it }
        }

        return "text.plain"
    }

    /** 便捷重载：仅传文件名和可选内容。 */
    fun detect(fileName: String, firstLines: String? = null): String {
        return detect(File(fileName), firstLines)
    }

    private fun detectFromShebang(content: String): String? {
        val firstLine = content.lineSequence().firstOrNull()?.trim() ?: return null
        if (!firstLine.startsWith("#!")) return null
        // 解析 /usr/bin/env python3 → python3
        val parts = firstLine.removePrefix("#!").trim().split(Regex("\\s+"))
        val interpreter = when {
            parts.size >= 2 && parts[0].endsWith("/env") -> parts[1].substringBefore('-')
            parts.size == 1 -> parts[0].substringAfterLast('/')
            else -> parts.last().substringAfterLast('/')
        }.substringBefore('.')
        return shebangMap[interpreter]
    }

    /** Vim modeline:  # vim: set ft=python: 或 Emacs:  -*- mode: python -*- */
    private fun detectFromModeline(content: String): String? {
        val lines = content.lineSequence().take(5).toList() +
                content.lineSequence().toList().takeLast(5)
        for (line in lines) {
            // Vim: vim: set ft=xxx
            Regex("""vim:\s*(?:set\s+)?ft=(\w+)""").find(line)?.let { m ->
                return extToScope(m.groupValues[1])
            }
            // Emacs: -*- mode: xxx -*-
            Regex("""-\*-\s*(?:mode:\s*)(\w+)\s*-\*-""").find(line)?.let { m ->
                return extToScope(m.groupValues[1])
            }
        }
        return null
    }

    /** 内容特征检测（XML/JSON/YAML 等）。 */
    private fun detectFromContent(content: String): String? {
        val trimmed = content.trimStart()
        // JSON
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            return "source.json"
        }
        // XML
        if (trimmed.startsWith("<?xml")) return "text.xml"
        // HTML
        if (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html")) return "text.html.basic"
        // YAML front matter
        if (trimmed.startsWith("---\n")) return "source.yaml"
        return null
    }

    /** 语言名 → scopeName 兜底映射（modeline 用）。 */
    private fun extToScope(lang: String): String? {
        return when (lang.lowercase()) {
            "py", "python" -> "source.python"
            "js", "javascript" -> "source.js"
            "ts", "typescript" -> "source.ts"
            "java" -> "source.java"
            "c" -> "source.c"
            "cpp", "c++" -> "source.cpp"
            "sh", "bash", "shell" -> "source.shell"
            "rb", "ruby" -> "source.ruby"
            "go" -> "source.go"
            "rs", "rust" -> "source.rust"
            "php" -> "source.php"
            "html" -> "text.html.basic"
            "css" -> "source.css"
            "json" -> "source.json"
            "yaml" -> "source.yaml"
            "md", "markdown" -> "text.html.markdown"
            "xml" -> "text.xml"
            else -> null
        }
    }
}
