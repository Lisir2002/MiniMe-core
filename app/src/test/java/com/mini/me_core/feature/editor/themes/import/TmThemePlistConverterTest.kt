package com.mini.me_core.feature.editor.themes.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmThemePlistConverterTest {

    private val samplePlist = """
        <?xml version="1.0" encoding="UTF-8"?>
        <plist version="1.0">
        <dict>
            <key>name</key>
            <string>Monokai</string>
            <key>settings</key>
            <array>
                <dict>
                    <key>settings</key>
                    <dict>
                        <key>background</key><string>#272822</string>
                        <key>foreground</key><string>#F8F8F2</string>
                        <key>caret</key><string>#F8F8F0</string>
                    </dict>
                </dict>
                <dict>
                    <key>name</key><string>Comment</string>
                    <key>scope</key><string>comment</string>
                    <key>settings</key>
                    <dict><key>foreground</key><string>#75715E</string></dict>
                </dict>
                <dict>
                    <key>name</key><string>String</string>
                    <key>scope</key><string>string</string>
                    <key>settings</key>
                    <dict><key>foreground</key><string>#E6DB74</string></dict>
                </dict>
            </array>
        </dict>
        </plist>
    """.trimIndent()

    @Test
    fun convert_extractsGlobalEditorColors() {
        val json = TmThemePlistConverter.convert(samplePlist)
        assertTrue(json.contains("#272822"))
        assertTrue(json.contains("#F8F8F2"))
        assertTrue(json.contains("#F8F8F0"))
    }

    @Test
    fun convert_emitsTokenColorsForScopes() {
        val json = TmThemePlistConverter.convert(samplePlist)
        assertTrue(json.contains("comment"))
        assertTrue(json.contains("#75715E"))
        assertTrue(json.contains("string"))
        assertTrue(json.contains("#E6DB74"))
    }

    @Test
    fun convert_darkBackground_detectedAsDark() {
        val json = TmThemePlistConverter.convert(samplePlist)
        assertTrue(Regex("\"type\"\\s*:\\s*\"dark\"").containsMatchIn(json))
    }

    @Test
    fun convert_lightBackground_detectedAsLight() {
        val plist = samplePlist.replace("#272822", "#FFFFFF")
        val json = TmThemePlistConverter.convert(plist)
        assertTrue(Regex("\"type\"\\s*:\\s*\"light\"").containsMatchIn(json))
    }

    @Test
    fun isLightColor_whiteIsLight() {
        assertTrue(TmThemePlistConverter.isLightColor("#FFFFFF"))
        assertTrue(TmThemePlistConverter.isLightColor("#FFFF00"))
    }

    @Test
    fun isLightColor_blackIsDark() {
        assertFalse(TmThemePlistConverter.isLightColor("#000000"))
    }

    @Test
    fun slugify_basic() {
        assertEquals("my-theme", TmThemePlistConverter.slugify("My Theme!"))
        assertEquals("custom", TmThemePlistConverter.slugify("Custom 主题"))
    }
}
