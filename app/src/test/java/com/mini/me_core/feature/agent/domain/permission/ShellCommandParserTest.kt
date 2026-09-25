package com.mini.me_core.feature.agent.domain.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCommandParserTest {

    @Test
    fun singleSegment_isAnalyzable_andPrefixIsProgram() {
        val a = ShellCommandParser.analyze("git status -s")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("git", "status", "-s"), a.segments[0])
        assertEquals("git", ShellCommandParser.programPrefix(a.segments[0]))
    }

    @Test
    fun envAssignment_isStrippedFromPrefix() {
        val a = ShellCommandParser.analyze("FOO=bar BAZ=1 git push")
        assertEquals("git", ShellCommandParser.programPrefix(a.segments[0]))
        assertTrue(ShellCommandParser.matches("git", a.segments[0]))
    }

    @Test
    fun operators_splitIntoSegments() {
        val and = ShellCommandParser.analyze("npm install && git push")
        assertTrue(and.analyzable)
        assertEquals(2, and.segments.size)
        assertEquals("npm", ShellCommandParser.programPrefix(and.segments[0]))
        assertEquals("git", ShellCommandParser.programPrefix(and.segments[1]))

        val pipe = ShellCommandParser.analyze("cat a | grep b ; echo c")
        assertEquals(3, pipe.segments.size)
    }

    @Test
    fun commandSubstitution_isNotAnalyzable() {
        assertFalse(ShellCommandParser.analyze("echo \$(whoami)").analyzable)
        assertFalse(ShellCommandParser.analyze("echo `whoami`").analyzable)
        assertFalse(ShellCommandParser.analyze("(cd x && ls)").analyzable)
    }

    @Test
    fun absoluteOutputRedirect_isNotAnalyzable_butRelativeIs() {
        assertFalse(ShellCommandParser.analyze("echo hi > /etc/passwd").analyzable)
        assertFalse(ShellCommandParser.analyze("echo hi >>/sdcard/x").analyzable)
        assertTrue(ShellCommandParser.analyze("echo hi > out.txt").analyzable)
    }

    @Test
    fun operatorsInsideQuotes_areNotSplit() {
        val a = ShellCommandParser.analyze("echo \"a && b | c\"")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("echo", "a && b | c"), a.segments[0])
    }

    @Test
    fun unterminatedQuote_isNotAnalyzable() {
        assertFalse(ShellCommandParser.analyze("echo \"abc").analyzable)
    }

    @Test
    fun matches_isTokenPrefix() {
        assertTrue(ShellCommandParser.matches("git", listOf("git", "status")))
        assertTrue(ShellCommandParser.matches("git", listOf("git")))
        assertTrue(ShellCommandParser.matches("npm install", listOf("npm", "install", "lodash")))
        // 不同子命令不命中更具体的前缀规则
        assertFalse(ShellCommandParser.matches("git status", listOf("git", "push")))
        // 字面首 token：路径形式不被裸程序名规则命中
        assertFalse(ShellCommandParser.matches("node", listOf("/usr/bin/node", "x.js")))
    }

    @Test
    fun rememberablePrefix_dispatcher_includesSubcommand() {
        // 子命令分发器：连子命令一起记，使 git pull / git clone 各自独立授权。
        assertEquals("git pull", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("git pull").segments[0]))
        assertEquals("git clone", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("git clone https://x/y").segments[0]))
        assertEquals("npm install", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("npm install lodash --save").segments[0]))
        assertEquals("cargo build", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("cargo build --release").segments[0]))
    }

    @Test
    fun rememberablePrefix_dispatcher_withLeadingFlag_fallsBackToProgram() {
        // 程序名后紧跟 flag：无法稳定取子命令，退化为仅程序名（不误记带 flag 的怪前缀）。
        assertEquals("git", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("git --version").segments[0]))
    }

    @Test
    fun rememberablePrefix_nonDispatcher_isProgramOnly() {
        // 普通程序：仅记程序名，按整程序放行（cat 任意文件）。
        assertEquals("cat", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("cat README.md").segments[0]))
        assertEquals("ls", ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("ls -la /tmp").segments[0]))
    }

    @Test
    fun rememberablePrefix_distinctSubcommandsAreIndependent() {
        // 验证记忆的前缀互不串放：git pull 规则不命中 git push。
        val pullRule = ShellCommandParser.rememberablePrefix(ShellCommandParser.analyze("git pull").segments[0])
        assertEquals("git pull", pullRule)
        assertFalse(ShellCommandParser.matches(pullRule!!, ShellCommandParser.analyze("git push").segments[0]))
    }

    @Test
    fun rmCommand_fineGrainedHandling() {
        // 单文件删除记忆为完整前缀，不记忆为宽泛的 "rm"
        val singleFile = ShellCommandParser.analyze("rm temp.log").segments[0]
        assertEquals("rm temp.log", ShellCommandParser.rememberablePrefix(singleFile))

        // 带选项的单文件删除
        val forceFile = ShellCommandParser.analyze("rm -f cache.tmp").segments[0]
        assertEquals("rm -f cache.tmp", ShellCommandParser.rememberablePrefix(forceFile))

        // 递归删除不可记忆
        val recursiveDir = ShellCommandParser.analyze("rm -rf build").segments[0]
        assertEquals(null, ShellCommandParser.rememberablePrefix(recursiveDir))

        // 通配符删除不可记忆
        val wildcardFile = ShellCommandParser.analyze("rm *.class").segments[0]
        assertEquals(null, ShellCommandParser.rememberablePrefix(wildcardFile))

        // 无目标参数不可记忆
        val noTarget = ShellCommandParser.analyze("rm --help").segments[0]
        assertEquals(null, ShellCommandParser.rememberablePrefix(noTarget))
    }

    @Test
    fun fdRedirect_stderrMerge_isSingleSegment() {
        // 2>&1：fd 数字与 dup 算子均不入 token，不被切分为多段
        val a = ShellCommandParser.analyze("ls -la 2>&1")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("ls", "-la"), a.segments[0])
        assertEquals("ls", ShellCommandParser.programPrefix(a.segments[0]))
    }

    @Test
    fun fdRedirect_absoluteTarget_isNotAnalyzable() {
        // 1>/dev/null：fd 数字不入 token，目标为绝对路径则不可静态判定
        val a = ShellCommandParser.analyze("echo hi 1>/dev/null")
        assertFalse(a.analyzable)
        assertEquals(1, a.segments.size)
        // fd 数字 1 被吸收，目标文件 /dev/null 仍入 token（现有行为）
        assertFalse(a.segments[0].contains("1"))
        assertTrue(a.segments[0].contains("/dev/null"))
        assertEquals("echo", ShellCommandParser.programPrefix(a.segments[0]))
    }

    @Test
    fun fdRedirect_relativeTarget_isAnalyzable() {
        val a = ShellCommandParser.analyze("echo hi 1>out.txt")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertFalse(a.segments[0].contains("1"))
        assertTrue(a.segments[0].contains("out.txt"))
    }

    @Test
    fun fdRedirect_stderrToFile_isAnalyzable() {
        val a = ShellCommandParser.analyze("cat f 2>err.log")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertFalse(a.segments[0].contains("2"))
        assertTrue(a.segments[0].contains("err.log"))
    }

    @Test
    fun fdRedirect_multipleRedirects_noNumericTokens() {
        val a = ShellCommandParser.analyze("cmd 2>&1 1>out.txt")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertTrue(a.segments[0].none { it.all { ch -> ch.isDigit() } })
    }

    @Test
    fun fdRedirect_mergeAmpersand_relativeTarget() {
        // &> 合并重定向：开头的 & 不是后台算子，目标文件入 token
        val a = ShellCommandParser.analyze("cmd &>all.log")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("cmd", "all.log"), a.segments[0])
    }

    @Test
    fun fdRedirect_closeFd_isSingleSegment() {
        // 3>&-：关闭 fd，- 不入 token
        val a = ShellCommandParser.analyze("cmd 3>&-")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("cmd"), a.segments[0])
    }

    @Test
    fun fdRedirect_dupToStderr_isSingleSegment() {
        // >&2：dup 到 stderr，数字 2 不入 token
        val a = ShellCommandParser.analyze("cmd >&2")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("cmd"), a.segments[0])
    }

    @Test
    fun fdRedirect_insideQuotes_notTreatedAsOperator() {
        // 引号内的 2>&1 不被当作重定向算子
        val a = ShellCommandParser.analyze("echo \"2>&1\"")
        assertTrue(a.analyzable)
        assertEquals(1, a.segments.size)
        assertEquals(listOf("echo", "2>&1"), a.segments[0])
    }
}
