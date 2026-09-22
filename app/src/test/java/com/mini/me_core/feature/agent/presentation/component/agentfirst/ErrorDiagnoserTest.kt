package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorDiagnoserTest {

    // ===== APK 数据库锁 =====

    @Test
    fun unableToLockDatabase_returnsApkLockDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("ERROR: Unable to lock database: Permission denied")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("APK"))
        assertTrue(diagnosis.suggestion.contains("锁文件"))
        assertEquals("rm -f /lib/apk/db/lock", diagnosis.fixCommand)
    }

    @Test
    fun failedToOpenApkDatabase_returnsApkLockDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("ERROR: failed to open apk database")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("APK"))
    }

    // ===== 命令未找到 =====

    @Test
    fun commandNotFound_returnsCommandNotFoundDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("sh: gradlew: command not found")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("命令未找到"))
        assertTrue(diagnosis.title.contains("gradlew"))
        assertTrue(diagnosis.suggestion.contains("安装"))
        assertEquals("apk add gradlew", diagnosis.fixCommand)
    }

    @Test
    fun commandNotFoundWithoutShPrefix_returnsDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("docker: command not found")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("docker"))
    }

    // ===== 权限不足 =====

    @Test
    fun permissionDenied_returnsPermissionDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("Permission denied: /root/secret.txt")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("权限不足"))
        assertNull(diagnosis.fixCommand)
    }

    @Test
    fun operationNotPermitted_returnsPermissionDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("Operation not permitted")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("权限不足"))
    }

    // ===== 磁盘空间不足 =====

    @Test
    fun noSpaceLeft_returnsDiskSpaceDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("No space left on device")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("磁盘空间不足"))
        assertNotNull(diagnosis.fixCommand)
        assertTrue(diagnosis.fixCommand!!.contains("df -h"))
    }

    // ===== 命令执行超时 =====

    @Test
    fun commandTimeoutChinese_returnsTimeoutDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("命令执行超时（超过 60 秒已强制终止）。末尾输出...")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("超时"))
        assertTrue(diagnosis.suggestion.contains("timeout"))
    }

    @Test
    fun commandTimeoutEnglish_returnsTimeoutDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("Command execution timed out after 120s")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("超时"))
    }

    // ===== 网络连接失败 =====

    @Test
    fun connectionRefused_returnsNetworkDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("curl: (7) Failed to connect to localhost port 8080: Connection refused")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("网络连接失败"))
    }

    @Test
    fun couldNotResolveHost_returnsNetworkDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("curl: (6) Could not resolve host: example.com")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("网络连接失败"))
    }

    // ===== 语法错误 =====

    @Test
    fun syntaxError_returnsSyntaxDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("sh: syntax error near unexpected token `('")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("语法错误"))
    }

    // ===== 文件不存在 =====

    @Test
    fun noSuchFile_returnsFileNotFoundDiagnosis() {
        val diagnosis = ErrorDiagnoser.diagnose("cat: /etc/nonexistent: No such file or directory")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("文件或目录不存在"))
    }

    // ===== 无法识别 =====

    @Test
    fun normalOutput_returnsNull() {
        assertNull(ErrorDiagnoser.diagnose("Build successful in 10.5s"))
    }

    @Test
    fun blankContent_returnsNull() {
        assertNull(ErrorDiagnoser.diagnose(""))
    }

    @Test
    fun randomError_returnsNull() {
        assertNull(ErrorDiagnoser.diagnose("Some random error that we don't recognize"))
    }

    // ===== extractMissingCommand =====

    @Test
    fun extractMissingCommand_withShPrefix_returnsCommand() {
        assertEquals("gradlew", ErrorDiagnoser.extractMissingCommand("sh: gradlew: command not found"))
    }

    @Test
    fun extractMissingCommand_withoutShPrefix_returnsCommand() {
        assertEquals("docker", ErrorDiagnoser.extractMissingCommand("docker: command not found"))
    }

    @Test
    fun extractMissingCommand_noMatch_returnsNull() {
        assertNull(ErrorDiagnoser.extractMissingCommand("some other error"))
    }

    @Test
    fun extractMissingCommand_blank_returnsNull() {
        assertNull(ErrorDiagnoser.extractMissingCommand(""))
    }

    // ===== 优先级验证 =====

    @Test
    fun apkLockHasHigherPriorityThanPermission() {
        // "Unable to lock database: Permission denied" 应识别为 APK 锁，而非权限不足
        val diagnosis = ErrorDiagnoser.diagnose("ERROR: Unable to lock database: Permission denied")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("APK"))
    }

    @Test
    fun timeoutHasHigherPriorityThanGenericError() {
        // 超时文本应识别为超时，而非其他
        val diagnosis = ErrorDiagnoser.diagnose("命令执行超时，已强制终止")
        assertNotNull(diagnosis)
        assertTrue(diagnosis!!.title.contains("超时"))
    }
}
