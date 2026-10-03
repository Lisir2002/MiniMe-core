package com.mini.me_core.feature.editor.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeJsonSupportTest {

    private val indexJson = """
        {
          "themes": [
            {"name": "dark-plus", "path": "editor/themes/dark-plus.json", "displayName": "MiniMe Dark", "category": "dark", "isDark": true, "author": "MiniMe"},
            {"name": "light-plus", "path": "editor/themes/light-plus.json", "displayName": "MiniMe Light", "category": "light", "isDark": false},
            {"name": "hc-dark", "path": "editor/themes/hc-dark.json", "displayName": "HC Dark", "category": "highContrast", "isDark": true}
          ]
        }
    """.trimIndent()

    @Test
    fun parseIndex_readsAllEntries() {
        val list = ThemeJsonSupport.parseIndex(indexJson)
        assertEquals(3, list.size)
        assertEquals("dark-plus", list[0].name)
        assertEquals("MiniMe Dark", list[0].displayName)
        assertEquals(ThemeCategory.DARK, list[0].category)
        assertTrue(list[0].isDark)
        assertEquals("editor/themes/dark-plus.json", list[0].assetPath)
    }

    @Test
    fun parseIndex_lightCategory() {
        val list = ThemeJsonSupport.parseIndex(indexJson)
        assertEquals(ThemeCategory.LIGHT, list[1].category)
        assertFalse(list[1].isDark)
    }

    @Test
    fun parseIndex_highContrastCategory() {
        val list = ThemeJsonSupport.parseIndex(indexJson)
        assertEquals(ThemeCategory.HIGH_CONTRAST, list[2].category)
    }

    @Test(expected = ThemeParseException::class)
    fun parseIndex_invalidJson_throws() {
        ThemeJsonSupport.parseIndex("not json {")
    }

    @Test
    fun parseIndex_missingThemesArray_throws() {
        runCatching { ThemeJsonSupport.parseIndex("{}") }.onFailure { assertTrue(it is ThemeParseException) }
    }

    @Test
    fun extractPreviewColors_readsEditorColorsAndTokenColors() {
        val theme = """
            {
              "colors": {"editor.background": "#1E1E1E", "editor.foreground": "#D4D4D4"},
              "tokenColors": [
                {"scope": "keyword", "settings": {"foreground": "#569CD6"}},
                {"scope": "string", "settings": {"foreground": "#CE9178"}},
                {"scope": "comment", "settings": {"foreground": "#6A9955"}},
                {"scope": "constant.numeric", "settings": {"foreground": "#B5CEA8"}},
                {"scope": "entity.name.function", "settings": {"foreground": "#DCDCAA"}}
              ]
            }
        """.trimIndent()
        val p = ThemeJsonSupport.extractPreviewColors(theme)
        assertEquals("#1E1E1E", p.background)
        assertEquals("#D4D4D4", p.foreground)
        assertEquals("#569CD6", p.keyword)
        assertEquals("#CE9178", p.string)
        assertEquals("#6A9955", p.comment)
        assertEquals("#B5CEA8", p.number)
        assertEquals("#DCDCAA", p.function)
    }

    @Test
    fun extractPreviewColors_invalidJson_returnsEmpty() {
        val p = ThemeJsonSupport.extractPreviewColors("garbage")
        assertNotNull(p)
    }

    @Test
    fun normalizeHex_expandsShortForm() {
        assertEquals("#AABBCC", ThemeJsonSupport.normalizeHex("#abc"))
        assertEquals("#AABBCC", ThemeJsonSupport.normalizeHex("aabbcc"))
        assertEquals("#AABBCC", ThemeJsonSupport.normalizeHex("#aabbccdd"))
        assertNull(ThemeJsonSupport.normalizeHex("#12"))
        assertNull(ThemeJsonSupport.normalizeHex(null))
    }

    @Test
    fun validateThemeJson_validTheme_passes() {
        val theme = """
            {"colors": {"editor.background": "#000"}, "tokenColors": []}
        """.trimIndent()
        assertTrue(ThemeJsonSupport.validateThemeJson(theme).isEmpty())
    }

    @Test
    fun validateThemeJson_missingFields_reportsErrors() {
        val errors = ThemeJsonSupport.validateThemeJson("{}")
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun validateThemeJson_notJson_reportsError() {
        assertTrue(ThemeJsonSupport.validateThemeJson("not json").isNotEmpty())
    }
}
