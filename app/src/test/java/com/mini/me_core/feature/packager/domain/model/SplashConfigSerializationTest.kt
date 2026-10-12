package com.mini.me_core.feature.packager.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * project.json 持久化 DTO 对新增启动页字段的兼容测试。
 *
 * - 新字段（transition/backgroundColor/skipOnTap）能随 SplashSettings 序列化并还原；
 * - 旧 project.json 不含这些字段时，靠默认值 + ignoreUnknownKeys 无缝反序列化，
 *   无需破坏性迁移。
 */
class SplashConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleProject(
        splash: SplashSettings = SplashSettings()
    ) = Project(
        id = "p1",
        name = "Demo",
        packageName = "com.minime.app.demo",
        versionName = "1.0.0",
        versionCode = 1_000_000,
        type = ProjectType.HTML,
        splashSettings = splash,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `new splash fields round-trip through ProjectConfig`() {
        val splash = SplashSettings(
            enabled = true,
            customImagePath = null,
            delayMs = 2000,
            transition = "zoom",
            backgroundColor = "#1A2B3C",
            skipOnTap = true
        )
        val config = ProjectConfig.fromProject(sampleProject(splash))
        val text = json.encodeToString(ProjectConfig.serializer(), config)
        val restored = json.decodeFromString(ProjectConfig.serializer(), text)

        val back = restored.toProject(File("/tmp/dummy"))
        assertEquals(splash.copy(customImagePath = null), back.splashSettings)
    }

    @Test
    fun `legacy splash json without new fields uses defaults`() {
        // 旧 project.json 的 splash 段只有老字段
        val legacySplashJson = """
            {
              "enabled": true,
              "customImagePath": null,
              "delayMs": 1000,
              "fullscreen": true
            }
        """.trimIndent()
        val splash = json.decodeFromString(SplashSettings.serializer(), legacySplashJson)

        assertEquals(1000, splash.delayMs)
        assertFalse(splash.enabled == false)
        // 新字段取默认值
        assertEquals("fade", splash.transition)
        assertEquals("#FFFFFF", splash.backgroundColor)
        assertFalse(splash.skipOnTap)
        assertNull(splash.customImagePath)
    }
}
