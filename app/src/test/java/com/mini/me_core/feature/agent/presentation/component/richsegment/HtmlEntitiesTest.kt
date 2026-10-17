package com.mini.me_core.feature.agent.presentation.component.richsegment

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEntitiesTest {

    @Test
    fun namedEntitiesAreDecoded() {
        assertEquals("a & b", HtmlEntities.decode("a &amp; b"))
        assertEquals("<tag>", HtmlEntities.decode("&lt;tag&gt;"))
        assertEquals("\"q\"", HtmlEntities.decode("&quot;q&quot;"))
        assertEquals("it's", HtmlEntities.decode("it&#39;s"))
    }

    @Test
    fun numericDecimalAndHexAreDecoded() {
        assertEquals("A", HtmlEntities.decode("&#65;"))
        assertEquals("A", HtmlEntities.decode("&#x41;"))
        assertEquals("→", HtmlEntities.decode("&#8594;"))
    }

    @Test
    fun unknownEntityIsLeftVerbatim() {
        assertEquals("a &notreal; b", HtmlEntities.decode("a &notreal; b"))
    }

    @Test
    fun doubleAmpersandDecodesOnlyOnePass() {
        // 单遍语义：&amp;amp; -> &amp; （不循环解码）
        assertEquals("&amp;", HtmlEntities.decode("&amp;amp;"))
    }

    @Test
    fun loneAmpersandIsUntouched() {
        assertEquals("a & b", HtmlEntities.decode("a & b"))
        assertEquals("", HtmlEntities.decode(""))
    }

    @Test
    fun outOfRangeNumericIsLeftVerbatim() {
        assertEquals("&#99999999;", HtmlEntities.decode("&#99999999;"))
        assertEquals("&#xFFFFFF;", HtmlEntities.decode("&#xFFFFFF;"))
    }

    @Test
    fun cjkTextWithEntitiesIsDecoded() {
        assertEquals("参数 a=1 & b=2 已就绪", HtmlEntities.decode("参数 a=1 &amp; b=2 已就绪"))
    }
}
