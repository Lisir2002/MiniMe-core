package com.mini.me_core.feature.editor.themes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEditorModelTest {

    private val baseTheme = """
        {
          "name": "dark-plus",
          "type": "dark",
          "colors": {
            "editor.background": "#1E1E1E",
            "editor.foreground": "#D4D4D4",
            "editorLineNumber.foreground": "#858585",
            "editor.lineHighlightBackground": "#2A2D2E",
            "editor.selectionBackground": "#264F78",
            "editorCursor.foreground": "#AEAFAD"
          },
          "tokenColors": [
            {"scope": "comment", "settings": {"foreground": "#6A9955"}},
            {"scope": "string", "settings": {"foreground": "#CE9178"}},
            {"scope": "constant.numeric", "settings": {"foreground": "#B5CEA8"}},
            {"scope": "keyword", "settings": {"foreground": "#569CD6"}},
            {"scope": "entity.name.function", "settings": {"foreground": "#DCDCAA"}}
          ]
        }
    """.trimIndent()

    @Test
    fun loadSnapshot_extractsEditorColors() {
        val snap = ThemeEditorModel.loadSnapshot(baseTheme)
        assertEquals("#1E1E1E", snap.values["editor_background"])
        assertEquals("#D4D4D4", snap.values["editor_foreground"])
        assertEquals("#858585", snap.values["line_number"])
    }

    @Test
    fun loadSnapshot_extractsSyntaxColors() {
        val snap = ThemeEditorModel.loadSnapshot(baseTheme)
        assertEquals("#6A9955", snap.values["comment"])
        assertEquals("#CE9178", snap.values["string"])
        assertEquals("#B5CEA8", snap.values["number"])
        assertEquals("#569CD6", snap.values["keyword"])
        assertEquals("#DCDCAA", snap.values["function"])
    }

    @Test
    fun buildThemeJson_appliesOverrides() {
        val snap = ThemeEditorModel.loadSnapshot(baseTheme)
        val out = ThemeEditorModel.buildThemeJson(
            snapshot = snap,
            overrides = mapOf("editor_background" to "#000000", "keyword" to "#FF0000"),
            newName = "my-custom",
            displayName = "My Custom",
        )
        assertTrue(out.contains("#000000"))
        assertTrue(out.contains("#FF0000"))
        assertTrue(out.contains("my-custom"))
        assertTrue(out.contains("My Custom"))
    }

    @Test
    fun buildThemeJson_preservesUnchangedColors() {
        val snap = ThemeEditorModel.loadSnapshot(baseTheme)
        val out = ThemeEditorModel.buildThemeJson(snap, emptyMap(), "x", "X")
        // 未覆盖的颜色仍保留
        assertTrue(out.contains("#6A9955"))
    }

    @Test
    fun buildThemeJson_invalidBase_returnsValidSkeleton() {
        val snap = ThemeEditorModel.loadSnapshot("garbage")
        val out = ThemeEditorModel.buildThemeJson(snap, emptyMap(), "new", "New")
        assertNotNull(out)
        assertTrue(out.contains("new"))
    }
}
