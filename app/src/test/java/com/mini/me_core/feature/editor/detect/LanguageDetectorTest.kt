package com.mini.me_core.feature.editor.detect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [LanguageDetector.detect] 优先级链测试：
 * 文件名 > 扩展名 > shebang > modeline > 内容特征 > 兜底纯文本。
 *
 * 测试环境内 TextMateManager 未初始化，全量注册表分支自动跳过，
 * 正好钉住内置快速路径的优先级与兜底语义。
 */
class LanguageDetectorTest {

    @Test
    fun dockerfile_matchesByExactFilename() {
        assertEquals("source.dockerfile", LanguageDetector.detect("Dockerfile"))
        assertEquals("source.dockerfile", LanguageDetector.detect("dockerfile"))
    }

    @Test
    fun makefile_matchesByExactFilename() {
        assertEquals("source.makefile", LanguageDetector.detect("Makefile"))
    }

    @Test
    fun wellKnownDotfiles_matchByFilename() {
        assertEquals("source.ignore", LanguageDetector.detect(".gitignore"))
        assertEquals("source.dotenv", LanguageDetector.detect(".env"))
    }

    @Test
    fun extension_kotlin() {
        assertEquals("source.kotlin", LanguageDetector.detect("Main.kt"))
        assertEquals("source.kotlin", LanguageDetector.detect("build.gradle.kts"))
    }

    @Test
    fun extension_pythonAndCss() {
        assertEquals("source.python", LanguageDetector.detect("train.py"))
        assertEquals("source.css", LanguageDetector.detect("styles.css"))
    }

    @Test
    fun extension_isCaseInsensitive() {
        assertEquals("source.r", LanguageDetector.detect("analysis.R"))
        assertEquals("source.python", LanguageDetector.detect("Script.PY"))
    }

    @Test
    fun shebang_envPython_resolvesToPython() {
        val scope = LanguageDetector.detect("script", "#!/usr/bin/env python3\nprint(1)")
        assertEquals("source.python", scope)
    }

    @Test
    fun shebang_directBash_resolvesToShell() {
        val scope = LanguageDetector.detect("run", "#!/bin/bash\necho hi")
        assertEquals("source.shell", scope)
    }

    @Test
    fun vimModeline_detectsPython() {
        val content = "// code\n// vim: set ft=python:\n"
        assertEquals("source.python", LanguageDetector.detect("random", content))
    }

    @Test
    fun emacsModeline_detectsRuby() {
        val content = "/* -*- mode: ruby -*- */\n"
        assertEquals("source.ruby", LanguageDetector.detect("random", content))
    }

    @Test
    fun contentJson_detectedByQuotedKey() {
        assertEquals("source.json", LanguageDetector.detect("data", """{"name":"x","v":1}"""))
    }

    @Test
    fun contentXml_detectedByDeclaration() {
        assertEquals("text.xml", LanguageDetector.detect("doc", "<?xml version=\"1.0\"?>\n<root/>"))
    }

    @Test
    fun contentYamlFrontMatter_detected() {
        assertEquals("source.yaml", LanguageDetector.detect("page", "---\ntitle: hi\n---\n"))
    }

    @Test
    fun contentPythonFeatureLine_detected() {
        assertEquals("source.python", LanguageDetector.detect("scratch", "def foo():\n  return 1"))
    }

    @Test
    fun unknownExtension_noContent_fallsBackToPlainText() {
        assertEquals("text.plain", LanguageDetector.detect("mystery.xyz"))
    }

    @Test
    fun noExtensionNoShebang_noContent_fallsBackToPlainText() {
        assertEquals("text.plain", LanguageDetector.detect("README"))
    }

    @Test
    fun filenameMatchBeatsExtension() {
        // docker-compose.yml 在文件名表中映射为 yaml；这里验证精确文件名优先于扩展默认。
        assertEquals("source.yaml", LanguageDetector.detect("docker-compose.yml"))
    }

    @Test
    fun emptyContent_doesNotCrashAndFallsBack() {
        assertEquals("source.python", LanguageDetector.detect("a.py"))
        assertEquals("text.plain", LanguageDetector.detect("a.unknown", ""))
    }
}
