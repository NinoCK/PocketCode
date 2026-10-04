package com.jarves.mh.ui.editor

import com.jarves.mh.ui.editor.TokenKind.ATTRIBUTE
import com.jarves.mh.ui.editor.TokenKind.COMMENT
import com.jarves.mh.ui.editor.TokenKind.CONSTANT
import com.jarves.mh.ui.editor.TokenKind.EMPHASIS
import com.jarves.mh.ui.editor.TokenKind.ESCAPE
import com.jarves.mh.ui.editor.TokenKind.FUNCTION
import com.jarves.mh.ui.editor.TokenKind.HEADING
import com.jarves.mh.ui.editor.TokenKind.KEYWORD
import com.jarves.mh.ui.editor.TokenKind.LINK
import com.jarves.mh.ui.editor.TokenKind.META
import com.jarves.mh.ui.editor.TokenKind.NUMBER
import com.jarves.mh.ui.editor.TokenKind.STRING
import com.jarves.mh.ui.editor.TokenKind.STRONG
import com.jarves.mh.ui.editor.TokenKind.TAG
import com.jarves.mh.ui.editor.TokenKind.VARIABLE

/**
 * HTML and XML: tags, attributes, comments, entities and declarations. With [html], the bodies of
 * `<script>` and `<style>` elements are coloured as script and CSS.
 */
internal class MarkupLexer(private val html: Boolean) : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var i = start
        while (i < end) {
            val c = text[i]
            i = when {
                c == '<' && text.startsWithAt("<!--", i) -> region(text, i, "-->", end, out, COMMENT)
                c == '<' && text.startsWithAt("<![CDATA[", i) -> region(text, i, "]]>", end, out, STRING)
                c == '<' && (text.startsWithAt("<!", i) || text.startsWithAt("<?", i)) -> region(text, i, ">", end, out, META)
                c == '<' && i + 1 < end && (isIdentStart(text[i + 1]) || text[i + 1] == '/') -> tag(text, i, end, out)
                c == '&' -> entity(text, i, end, out)
                else -> i + 1
            }
        }
    }

    private fun region(text: CharSequence, i: Int, close: String, end: Int, out: TokenList, kind: TokenKind): Int {
        val found = text.indexOfIn(close, i + 2, end)
        val stop = if (found < 0) end else found + close.length
        out.add(i, stop, kind)
        return stop
    }

    private fun entity(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        var p = i + 1
        if (p < end && text[p] == '#') p++
        val nameStart = p
        while (p < end && p - nameStart < 32 && isIdentPart(text[p])) p++
        if (p > nameStart && p < end && text[p] == ';') {
            out.add(i, p + 1, ESCAPE)
            return p + 1
        }
        return i + 1
    }

    private fun tag(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val closing = text[i + 1] == '/'
        var p = i + if (closing) 2 else 1
        val nameStart = p
        while (p < end && (isIdentPart(text[p]) || text[p] in "-:.")) p++
        val name = text.substring(nameStart, p)
        out.add(nameStart, p, TAG)
        var afterEquals = false
        while (p < end) {
            val c = text[p]
            when {
                c == '>' -> {
                    p++
                    break
                }
                c == '/' && p + 1 < end && text[p + 1] == '>' -> return p + 2
                c == '<' -> return p
                c.isWhitespace() -> p++
                c == '=' -> {
                    afterEquals = true
                    p++
                }
                c == '"' || c == '\'' -> {
                    val close = text.indexOfIn(c.toString(), p + 1, end)
                    val stop = if (close < 0) end else close + 1
                    out.add(p, stop, STRING)
                    p = stop
                    afterEquals = false
                }
                else -> {
                    val wordStart = p
                    while (p < end && !text[p].isWhitespace() && text[p] !in "=>/<\"'") p++
                    if (p == wordStart) p++
                    out.add(wordStart, p, if (afterEquals) STRING else ATTRIBUTE)
                    afterEquals = false
                }
            }
        }
        if (html && !closing) {
            val element = name.lowercase()
            if (element == "script" || element == "style") {
                val closeTag = text.indexOfIn("</$element", p, end, ignoreCase = true)
                val contentEnd = if (closeTag < 0) end else closeTag
                val embedded = if (element == "script") CodeLanguages.TypeScript else CodeLanguages.Css
                embedded.lexer?.lex(text, p, contentEnd, out)
                return contentEnd
            }
        }
        return p
    }
}

/** CSS, SCSS and Less: selectors, at-rules, properties, values, units, colours and variables. */
internal class CssLexer(private val lineComments: Boolean) : Lexer {
    private enum class Mode { SELECTOR, AT_RULE, DECLARATION }

    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var i = start
        var depth = 0
        var statementStart = true
        var mode = Mode.SELECTOR
        var inValue = false
        while (i < end) {
            val c = text[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (text.startsWithAt("/*", i)) {
                val close = text.indexOfIn("*/", i + 2, end)
                val stop = if (close < 0) end else close + 2
                out.add(i, stop, COMMENT)
                i = stop
                continue
            }
            if (lineComments && text.startsWithAt("//", i) && (i == 0 || text[i - 1] != ':')) {
                val stop = text.lineEndFrom(i, end)
                out.add(i, stop, COMMENT)
                i = stop
                continue
            }
            if (statementStart) {
                statementStart = false
                inValue = false
                mode = if (depth > 0 && !opensBlock(text, i, end)) Mode.DECLARATION else Mode.SELECTOR
            }
            when {
                c == '"' || c == '\'' -> i = quoted(text, i, end, out)
                c == '{' -> {
                    depth++
                    statementStart = true
                    i++
                }
                c == '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    statementStart = true
                    i++
                }
                c == ';' -> {
                    statementStart = true
                    i++
                }
                c == '@' && i + 1 < end && isIdentStart(text[i + 1]) -> {
                    val e = identEnd(text, i + 1, end)
                    val variable = nextNonBlank(text, e, end) == ':' && mode != Mode.SELECTOR
                    out.add(i, e, if (variable) VARIABLE else KEYWORD)
                    if (!variable) mode = Mode.AT_RULE
                    i = e
                }
                c == '$' && i + 1 < end && isIdentStart(text[i + 1]) -> {
                    val e = identEnd(text, i + 1, end)
                    out.add(i, e, VARIABLE)
                    if (nextNonBlank(text, e, end) == ':') mode = Mode.DECLARATION
                    i = e
                }
                mode == Mode.DECLARATION -> {
                    if (c == ':' && !inValue) {
                        inValue = true
                        i++
                    } else if (!inValue) {
                        val e = if (isCssIdentStart(text, i, end)) identEnd(text, i, end) else i + 1
                        out.add(i, e, if (text.startsWithAt("--", i)) VARIABLE else TokenKind.ATTRIBUTE)
                        i = e
                    } else {
                        i = value(text, i, end, out)
                    }
                }
                mode == Mode.AT_RULE -> i = when {
                    c.isDigit() || (c == '.' && i + 1 < end && text[i + 1].isDigit()) -> number(text, i, end, out)
                    isCssIdentStart(text, i, end) -> {
                        val e = identEnd(text, i, end)
                        out.add(i, e, if (nextNonBlank(text, e, end) == ':') TokenKind.ATTRIBUTE else if (nextNonBlank(text, e, end) == '(') FUNCTION else CONSTANT)
                        e
                    }
                    else -> i + 1
                }
                else -> i = selector(text, i, end, out)
            }
        }
    }

    /** Whether the statement at [from] opens a nested block (`&:hover {`) rather than declaring (`color: red;`). */
    private fun opensBlock(text: CharSequence, from: Int, end: Int): Boolean {
        var p = from
        while (p < end) {
            when (text[p]) {
                '{' -> return true
                ';', '}' -> return false
                '"', '\'' -> {
                    val close = text.indexOfIn(text[p].toString(), p + 1, end)
                    p = if (close < 0) end else close
                }
                '#' -> if (p + 1 < end && text[p + 1] == '{') p++
            }
            p++
        }
        return false
    }

    private fun selector(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val c = text[i]
        return when {
            (c == '.' || c == '#') && i + 1 < end && isCssIdentStart(text, i + 1, end) -> {
                val e = identEnd(text, i + 1, end)
                out.add(i, e, FUNCTION)
                e
            }
            c == ':' -> {
                var p = i + 1
                if (p < end && text[p] == ':') p++
                val e = if (p < end && isCssIdentStart(text, p, end)) identEnd(text, p, end) else p
                out.add(i, e, FUNCTION)
                e
            }
            c == '[' -> {
                val close = text.indexOfIn("]", i, text.lineEndFrom(i, end))
                if (close < 0) return i + 1
                var p = i + 1
                while (p < close && (isIdentPart(text[p]) || text[p] == '-')) p++
                out.add(i + 1, p, TokenKind.ATTRIBUTE)
                val eq = text.indexOfIn("=", p, close)
                if (eq >= 0) out.add(eq + 1, close, STRING)
                close + 1
            }
            c == '&' || c == '*' -> {
                out.add(i, i + 1, KEYWORD)
                i + 1
            }
            c.isDigit() -> number(text, i, end, out)
            isCssIdentStart(text, i, end) -> {
                val e = identEnd(text, i, end)
                out.add(i, e, TAG)
                e
            }
            else -> i + 1
        }
    }

    private fun value(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val c = text[i]
        return when {
            c.isDigit() || (c == '.' && i + 1 < end && text[i + 1].isDigit()) -> number(text, i, end, out)
            c == '#' && i + 1 < end && isHexDigit(text[i + 1]) -> {
                var e = i + 1
                while (e < end && isIdentPart(text[e])) e++
                out.add(i, e, NUMBER)
                e
            }
            c == '!' -> {
                val e = identEnd(text, i + 1, end)
                out.add(i, e, KEYWORD)
                e
            }
            isCssIdentStart(text, i, end) -> {
                val e = identEnd(text, i, end)
                val word = text.substring(i, e)
                when {
                    e < end && text[e] == '(' && word.equals("url", ignoreCase = true) -> {
                        out.add(i, e, FUNCTION)
                        val close = text.indexOfIn(")", e, text.lineEndFrom(e, end))
                        if (close > e + 1) out.add(e + 1, close, STRING)
                        if (close < 0) e else close
                    }
                    e < end && text[e] == '(' -> {
                        out.add(i, e, FUNCTION)
                        e
                    }
                    word.startsWith("--") -> {
                        out.add(i, e, VARIABLE)
                        e
                    }
                    else -> {
                        out.add(i, e, CONSTANT)
                        e
                    }
                }
            }
            else -> i + 1
        }
    }

    private fun number(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        var e = text.numberEnd(i, end)
        if (e < end && text[e] == '%') e++
        out.add(i, e, NUMBER)
        return e
    }

    private fun quoted(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val quote = text[i]
        var p = i + 1
        while (p < end && text[p] != quote && text[p] != '\n') {
            if (text[p] == '\\') p++
            p++
        }
        val stop = if (p < end && text[p] == quote) p + 1 else p.coerceAtMost(end)
        out.add(i, stop, STRING)
        return stop
    }

    private fun isCssIdentStart(text: CharSequence, i: Int, end: Int): Boolean {
        val c = text[i]
        if (isIdentStart(c)) return true
        return c == '-' && i + 1 < end && (isIdentStart(text[i + 1]) || text[i + 1] == '-')
    }

    private fun identEnd(text: CharSequence, from: Int, end: Int): Int {
        var e = from
        while (e < end && (isIdentPart(text[e]) || text[e] == '-')) e++
        return e
    }

    private fun nextNonBlank(text: CharSequence, from: Int, end: Int): Char {
        var p = from
        while (p < end && (text[p] == ' ' || text[p] == '\t')) p++
        return if (p < end) text[p] else '\u0000'
    }
}

/**
 * Markdown: headings, emphasis, inline code, links, lists, quotes and rules. Fenced code blocks
 * are coloured in their own language, and YAML front matter as YAML.
 */
internal object MarkdownLexer : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var line = start
        if (start == 0 && isDelimiterLine(text, 0, end, "---")) {
            val bodyStart = text.lineEndFrom(0, end) + 1
            var p = bodyStart
            while (p < end) {
                if (isDelimiterLine(text, p, end, "---") || isDelimiterLine(text, p, end, "...")) {
                    out.add(0, 3, META)
                    YamlLexer.lex(text, bodyStart, p, out)
                    out.add(p, p + 3, META)
                    line = text.lineEndFrom(p, end) + 1
                    break
                }
                p = text.lineEndFrom(p, end) + 1
            }
        }
        var fence: String? = null
        var fenceLanguage: CodeLanguage? = null
        var fenceBody = 0
        while (line < end) {
            val lineEnd = text.lineEndFrom(line, end)
            var p = line
            while (p < lineEnd && p - line < 4 && text[p] == ' ') p++
            val activeFence = fence
            if (activeFence != null) {
                if (text.startsWithAt(activeFence, p) && text.substring(p, lineEnd).trim().all { it == activeFence[0] }) {
                    fenceLanguage?.lexer?.lex(text, fenceBody, line, out)
                    out.add(p, lineEnd, META)
                    fence = null
                }
                line = lineEnd + 1
                continue
            }
            val c = if (p < lineEnd) text[p] else '\n'
            when {
                (c == '`' || c == '~') && text.startsWithAt("$c$c$c", p) -> {
                    var q = p
                    while (q < lineEnd && text[q] == c) q++
                    fence = text.substring(p, q)
                    fenceLanguage = CodeLanguages.forFence(text.substring(q, lineEnd))
                    fenceBody = lineEnd + 1
                    out.add(p, lineEnd, META)
                }
                c == '#' && isHeading(text, p, lineEnd) -> out.add(p, lineEnd, HEADING)
                c == '>' -> out.add(p, lineEnd, COMMENT)
                isRule(text, p, lineEnd) -> out.add(p, lineEnd, META)
                else -> {
                    val content = listMarkerEnd(text, p, lineEnd, out)
                    inline(text, content, lineEnd, out)
                }
            }
            line = lineEnd + 1
        }
        if (fence != null) fenceLanguage?.lexer?.lex(text, fenceBody, end, out)
    }

    private fun isDelimiterLine(text: CharSequence, at: Int, end: Int, delimiter: String): Boolean {
        if (!text.startsWithAt(delimiter, at)) return false
        val lineEnd = text.lineEndFrom(at, end)
        return text.substring(at + delimiter.length, lineEnd).isBlank()
    }

    private fun isHeading(text: CharSequence, p: Int, lineEnd: Int): Boolean {
        var q = p
        while (q < lineEnd && text[q] == '#') q++
        return q - p in 1..6 && (q == lineEnd || text[q] == ' ' || text[q] == '\t')
    }

    private fun isRule(text: CharSequence, p: Int, lineEnd: Int): Boolean {
        if (p >= lineEnd) return false
        val c = text[p]
        if (c != '-' && c != '*' && c != '_') return false
        var count = 0
        for (q in p until lineEnd) {
            when (text[q]) {
                c -> count++
                ' ', '\t', '\r' -> Unit
                else -> return false
            }
        }
        return count >= 3
    }

    /** Colours a list marker (`-`, `1.`, `- [x]`) and returns where the item's text starts. */
    private fun listMarkerEnd(text: CharSequence, p: Int, lineEnd: Int, out: TokenList): Int {
        if (p >= lineEnd) return p
        var q = p
        if (text[q] in "-*+") {
            q++
        } else {
            while (q < lineEnd && q - p < 9 && text[q].isDigit()) q++
            if (q == p || q >= lineEnd || text[q] !in ".)") return p
            q++
        }
        if (q < lineEnd && text[q] != ' ' && text[q] != '\t') return p
        out.add(p, q, KEYWORD)
        while (q < lineEnd && text[q] == ' ') q++
        if (q + 2 < lineEnd && text[q] == '[' && text[q + 2] == ']' && text[q + 1] in " xX") {
            out.add(q, q + 3, KEYWORD)
            q += 3
        }
        return q
    }

    private fun inline(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var i = start
        while (i < end) {
            val c = text[i]
            i = when {
                c == '\\' && i + 1 < end && text[i + 1] in "\\`*_{}[]()#+-.!|<>~" -> {
                    out.add(i, i + 2, ESCAPE)
                    i + 2
                }
                c == '`' -> {
                    var q = i
                    while (q < end && text[q] == '`') q++
                    val marker = text.substring(i, q)
                    val close = text.indexOfIn(marker, q, end)
                    if (close < 0) {
                        q
                    } else {
                        out.add(i, close + marker.length, STRING)
                        close + marker.length
                    }
                }
                c == '*' || c == '_' -> emphasis(text, i, end, out)
                c == '[' || (c == '!' && i + 1 < end && text[i + 1] == '[') -> link(text, i, end, out)
                c == '<' && i + 1 < end && (text[i + 1].isLetter() || text[i + 1] == '/') -> {
                    val close = text.indexOfIn(">", i, end)
                    if (close < 0) {
                        i + 1
                    } else {
                        val inner = text.substring(i + 1, close)
                        out.add(i, close + 1, if (inner.contains("://") || inner.contains('@')) LINK else TAG)
                        close + 1
                    }
                }
                c == 'h' && (text.startsWithAt("https://", i) || text.startsWithAt("http://", i)) && (i == 0 || !isIdentPart(text[i - 1])) -> {
                    var q = i
                    while (q < end && !text[q].isWhitespace() && text[q] !in ")>]") q++
                    out.add(i, q, LINK)
                    q
                }
                else -> i + 1
            }
        }
    }

    private fun emphasis(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val c = text[i]
        var q = i
        while (q < end && text[q] == c) q++
        val run = q - i
        // `snake_case` words are not emphasis, and a run must hug the text it wraps.
        if (c == '_' && i > 0 && isIdentPart(text[i - 1])) return q
        if (run > 3 || q >= end || text[q].isWhitespace()) return q
        val marker = text.substring(i, q)
        var search = q
        while (search < end) {
            val close = text.indexOfIn(marker, search, end)
            if (close < 0) return q
            val after = close + run
            val hugs = !text[close - 1].isWhitespace()
            val single = after >= end || text[after] != c
            val wordEnd = c != '_' || after >= end || !isIdentPart(text[after])
            if (close > q && hugs && single && wordEnd) {
                out.add(i, after, if (run >= 2) STRONG else EMPHASIS)
                return after
            }
            search = close + 1
        }
        return q
    }

    private fun link(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val open = if (text[i] == '!') i + 1 else i
        var depth = 0
        var p = open
        while (p < end) {
            if (text[p] == '[') depth++
            if (text[p] == ']') {
                depth--
                if (depth == 0) break
            }
            p++
        }
        if (p >= end) return open + 1
        val textEnd = p + 1
        if (textEnd < end && text[textEnd] == '(') {
            val close = text.indexOfIn(")", textEnd, end)
            if (close > 0) {
                out.add(i, textEnd, LINK)
                out.add(textEnd + 1, close, STRING)
                return close + 1
            }
        }
        if (textEnd < end && text[textEnd] == '[' || textEnd < end && text[textEnd] == ':') {
            out.add(i, textEnd, LINK)
            return textEnd
        }
        return open + 1
    }
}
