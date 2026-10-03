package com.mini.me_core.feature.editor.snippets

import com.mini.me_core.feature.editor.snippets.SnippetParser.parseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SnippetParser] 占位符语法解析的纯算法单测。 */
class SnippetParserTest {

    @Test
    fun `plain text has no tabstops`() {
        val p = parseBody(listOf("just text"))
        assertEquals("just text", p.expandedText)
        assertTrue(p.tabStops.isEmpty())
        assertTrue(p.variables.isEmpty())
    }

    @Test
    fun `numbered placeholder with default`() {
        val p = parseBody(listOf("for \${1:item} in \${2:iterable}:"))
        assertEquals("for item in iterable:", p.expandedText)
        assertEquals(2, p.tabStops.size)

        val t1 = p.tabStops[0]
        assertEquals(1, t1.index)
        assertEquals("item", t1.defaultValue)
        assertFalse(t1.isFinal)
        assertEquals("item", p.expandedText.substring(t1.start, t1.end))

        val t2 = p.tabStops[1]
        assertEquals(2, t2.index)
        assertEquals("iterable", t2.defaultValue)
    }

    @Test
    fun `final cursor placeholder zero`() {
        val p = parseBody(listOf("a", "    \${0:# TODO}"))
        assertTrue(p.hasFinalStop)
        val zero = p.tabStops.first { it.index == 0 }
        assertTrue(zero.isFinal)
    }

    @Test
    fun `multiple same index are grouped`() {
        val p = parseBody(listOf("\${1:name} = \${1:name}"))
        val ones = p.tabStopsOf(1)
        assertEquals(2, ones.size)
        assertEquals("name", ones[0].defaultValue)
        assertEquals("name", ones[1].defaultValue)
    }

    @Test
    fun `choice placeholder`() {
        val p = parseBody(listOf("lang = \${1|java,kotlin,go|}"))
        val t = p.tabStops.first()
        assertEquals(1, t.index)
        assertEquals(listOf("java", "kotlin", "go"), t.choices)
        assertEquals("java", t.defaultValue)
        assertEquals("lang = java", p.expandedText)
    }

    @Test
    fun `bare placeholder without default`() {
        val p = parseBody(listOf("func(\$1)"))
        val t = p.tabStops.first()
        assertEquals(1, t.index)
        assertEquals("", t.defaultValue)
        assertEquals("func()", p.expandedText)
    }

    @Test
    fun `variables are extracted`() {
        val p = parseBody(listOf("// Created by \$TM_FILENAME on \$TM_CURRENT_LINE"))
        val names = p.variables.map { it.name }
        assertTrue("应含 TM_FILENAME", "TM_FILENAME" in names)
        assertTrue("应含 TM_CURRENT_LINE", "TM_CURRENT_LINE" in names)
    }

    @Test
    fun `braced variable form`() {
        val p = parseBody(listOf("\${TM_SELECTED_TEXT}"))
        assertEquals(1, p.variables.size)
        assertEquals("TM_SELECTED_TEXT", p.variables[0].name)
    }

    @Test
    fun `multiline body joined with newline`() {
        val p = parseBody(listOf("for x in y:", "    pass"))
        assertEquals("for x in y:\n    pass", p.expandedText)
        assertEquals("for x in y:\n    pass", p.rawBody)
    }

    @Test
    fun `unescaped dollar before letter is literal`() {
        val p = parseBody(listOf("cost is 50\$"))
        assertEquals("cost is 50$", p.expandedText)
    }

    @Test
    fun `unbalanced braces throws`() {
        val ex = assertThrows(SnippetParser.SnippetParseException::class.java) {
            parseBody(listOf("\${1:oops"))
        }
        assertTrue(ex.message!!.contains("}"))
    }

    @Test
    fun `empty body throws`() {
        assertThrows(SnippetParser.SnippetParseException::class.java) {
            parseBody(emptyList())
        }
    }

    @Test
    fun `nested placeholder in default`() {
        val p = parseBody(listOf("\${1:outer \${2:inner} tail}"))
        val t2 = p.tabStops.first { it.index == 2 }
        assertEquals("inner", t2.defaultValue)
        val t1 = p.tabStops.first { it.index == 1 }
        assertEquals("outer inner tail", t1.defaultValue)
    }
}
