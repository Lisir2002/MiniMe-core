package com.mini.me_core.feature.proxy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 远程订阅 YAML 解析安全单测：必须使用 SafeConstructor，恶意 !! 类型标签不得被实例化。
 */
class ClashSafeYamlTest {

    @Test
    fun normalYamlMapParsed() {
        val yaml = ClashProxyManager.newSafeYaml()
        val root = yaml.load<Any?>("proxies: []\nmode: rule\n")
        assertTrue(root is Map<*, *>)
        assertEquals("rule", (root as Map<*, *>)["mode"])
    }

    @Test
    fun evilTagDocumentRejected() {
        // SafeConstructor 拒绝任意 Java 类型标签（!!com.xxx.Payload），不得实例化危险对象
        val doc = "key: !!com.evil.Payload {x: 1}"
        val result = runCatching { ClashProxyManager.newSafeYaml().load<Any?>(doc) }
        assertTrue("恶意标签文档应被拒绝，实际: $result", result.isFailure)
    }

    @Test
    fun harmlessCustomTagRejected() {
        // 即使是无害的自定义标签，SafeConstructor 也应拒绝（锁死标签白名单）
        val doc = "x: !!weird.Tag hello"
        val result = runCatching { ClashProxyManager.newSafeYaml().load<Any?>(doc) }
        assertTrue(result.isFailure)
    }
}
