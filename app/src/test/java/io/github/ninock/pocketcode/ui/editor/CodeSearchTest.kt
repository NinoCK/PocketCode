package io.github.ninock.pocketcode.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSearchTest {
    private val text = "val count = 1\nval Counter = count + 1\nrecount()"

    private fun starts(result: SearchResult) = (0 until result.count).map { result.start(it) }

    @Test
    fun plainSearchIgnoresCaseByDefault() {
        val result = CodeSearch.findAll(text, "count", SearchOptions())
        assertEquals(4, result.count)
        assertEquals(listOf(4, 18, 28, 40), starts(result))
    }

    @Test
    fun matchCaseAndWholeWord() {
        assertEquals(3, CodeSearch.findAll(text, "count", SearchOptions(matchCase = true)).count)
        assertEquals(listOf(4, 28), starts(CodeSearch.findAll(text, "count", SearchOptions(wholeWord = true))))
    }

    @Test
    fun regexSpecialCharactersAreLiteralUnlessRegexIsOn() {
        assertEquals(1, CodeSearch.findAll("a.b axb", "a.b", SearchOptions()).count)
        assertEquals(2, CodeSearch.findAll("a.b axb", "a.b", SearchOptions(regex = true)).count)
        assertEquals(listOf(0, 14), starts(CodeSearch.findAll(text, "^val", SearchOptions(regex = true))))
    }

    @Test
    fun invalidRegexReportsAnError() {
        val result = CodeSearch.findAll(text, "(open", SearchOptions(regex = true))
        assertEquals(0, result.count)
        assertNotNull(result.error)
    }

    @Test
    fun emptyMatchesAreSkipped() {
        assertEquals(0, CodeSearch.findAll("abc", "x*", SearchOptions(regex = true)).count)
    }

    @Test
    fun navigationWrapsAround() {
        val result = CodeSearch.findAll(text, "count", SearchOptions())
        assertEquals(1, result.nextFrom(5))
        assertEquals(0, result.nextFrom(41))
        assertEquals(0, result.previousFrom(18))
        assertEquals(3, result.previousFrom(4))
        assertEquals(2, result.indexOf(28, 33))
        assertEquals(-1, result.indexOf(28, 30))
    }

    @Test
    fun replaceAllExpandsRegexGroupsInOneEdit() {
        val (edit, count) = assertNotNullPair(CodeSearch.replaceAll("a=1, b=22", "(\\w)=(\\d+)", "$2:$1", SearchOptions(regex = true)))
        assertEquals(2, count)
        val result = "a=1, b=22".replaceRange(edit.start, edit.end, edit.text)
        assertEquals("1:a, 22:b", result)
        assertNull(CodeSearch.replaceAll("abc", "z", "y", SearchOptions()))
    }

    @Test
    fun plainReplacementIsLiteral() {
        assertEquals("$1\\n", CodeSearch.replacementFor("x", 0, 1, "x", "$1\\n", SearchOptions()))
        assertEquals("[x]\n", CodeSearch.replacementFor("x", 0, 1, "(x)", "[$1]\\n", SearchOptions(regex = true)))
    }

    @Test
    fun matchLimitTruncates() {
        val result = CodeSearch.findAll("a".repeat(50), "a", SearchOptions(), limit = 10)
        assertEquals(10, result.count)
        assertTrue(result.truncated)
    }

    private fun <A, B> assertNotNullPair(pair: Pair<A, B>?): Pair<A, B> {
        assertNotNull(pair)
        return pair!!
    }
}
