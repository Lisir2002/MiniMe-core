package com.mini.me_core.datalayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [escapeSqlLike] 边界与转义正确性测试。
 *
 * 配合 `LIKE ... ESCAPE '\'` 使用：用户输入中的 `\` `%` `_` 必须被转义，
 * 否则会被 SQLite 当通配符，导致搜索结果膨胀甚至全表扫描。
 */
class SqlLikeTest {

    @Test
    fun emptyInput_staysEmpty() {
        assertEquals("", escapeSqlLike(""))
    }

    @Test
    fun plainTextWithoutWildcards_isUnchanged() {
        assertEquals("agent", escapeSqlLike("agent"))
        assertEquals("Minime v2.0.1", escapeSqlLike("Minime v2.0.1"))
    }

    @Test
    fun backslash_isDoubled() {
        assertEquals("a\\\\b", escapeSqlLike("a\\b"))
    }

    @Test
    fun percent_isEscaped() {
        assertEquals("100\\%", escapeSqlLike("100%"))
    }

    @Test
    fun underscore_isEscaped() {
        assertEquals("user\\_name", escapeSqlLike("user_name"))
    }

    @Test
    fun allThreeWildcards_areEscapedInOrder() {
        // 反斜杠必须最先转义，否则后续插入的转义符会被二次转义。
        assertEquals("\\%\\_\\\\", escapeSqlLike("%_\\"))
    }

    @Test
    fun repeatedWildcards_areAllEscaped() {
        assertEquals("\\%\\%\\%", escapeSqlLike("%%%"))
        assertEquals("a\\_\\_b", escapeSqlLike("a__b"))
    }

    @Test
    fun specialAndUnicodeCharacters_passedThroughExceptWildcards() {
        // 非 LIKE 特殊字符不应被改动；Unicode 原样保留；_ 和 % 被转义。
        assertEquals("café\\_ü\\%", escapeSqlLike("café_ü%"))
    }
}
