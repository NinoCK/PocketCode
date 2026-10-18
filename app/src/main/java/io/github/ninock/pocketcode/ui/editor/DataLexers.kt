package io.github.ninock.pocketcode.ui.editor

import io.github.ninock.pocketcode.ui.editor.TokenKind.COMMENT
import io.github.ninock.pocketcode.ui.editor.TokenKind.CONSTANT
import io.github.ninock.pocketcode.ui.editor.TokenKind.DELETED
import io.github.ninock.pocketcode.ui.editor.TokenKind.ESCAPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.HEADING
import io.github.ninock.pocketcode.ui.editor.TokenKind.INSERTED
import io.github.ninock.pocketcode.ui.editor.TokenKind.KEYWORD
import io.github.ninock.pocketcode.ui.editor.TokenKind.META
import io.github.ninock.pocketcode.ui.editor.TokenKind.NUMBER
import io.github.ninock.pocketcode.ui.editor.TokenKind.PROPERTY
import io.github.ninock.pocketcode.ui.editor.TokenKind.STRING
import io.github.ninock.pocketcode.ui.editor.TokenKind.TYPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.VARIABLE

/** JSON, JSON with comments and JSON5: keys are told apart from string values. */
internal object JsonLexer : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var i = start
        while (i < end) {
            val c = text[i]
            i = when {
                c == '/' && text.startsWithAt("//", i) -> {
                    val e = text.lineEndFrom(i, end)
                    out.add(i, e, COMMENT)
                    e
                }
                c == '/' && text.startsWithAt("/*", i) -> {
                    val close = text.indexOfIn("*/", i + 2, end)
                    val e = if (close < 0) end else close + 2
                    out.add(i, e, COMMENT)
                    e
                }
                c == '"' || c == '\'' -> string(text, i, end, out)
                c == '-' || c.isDigit() -> {
                    val digits = if (c == '-') i + 1 else i
                    if (digits < end && (text[digits].isDigit() || text[digits] == '.')) {
                        val e = text.numberEnd(digits, end)
                        out.add(i, e, NUMBER)
                        e
                    } else {
                        i + 1
                    }
                }
                isIdentStart(c) || c == '$' -> {
                    var e = i + 1
                    while (e < end && (isIdentPart(text[e]) || text[e] == '$')) e++
                    when (text.substring(i, e)) {
                        "true", "false", "null" -> out.add(i, e, CONSTANT)
                        "NaN", "Infinity" -> out.add(i, e, NUMBER)
                        else -> if (followedByColon(text, e, end)) out.add(i, e, PROPERTY)
                    }
                    e
                }
                else -> i + 1
            }
        }
    }

    private fun string(text: CharSequence, i: Int, end: Int, out: TokenList): Int {
        val quote = text[i]
        var p = i + 1
        while (p < end && text[p] != quote && text[p] != '\n') {
            if (text[p] == '\\') p++
            p++
        }
        val stop = if (p < end && text[p] == quote) p + 1 else p.coerceAtMost(end)
        if (followedByColon(text, stop, end)) {
            out.add(i, stop, PROPERTY)
            return stop
        }
        var segment = i
        var q = i + 1
        while (q < stop - 1) {
            if (text[q] == '\\') {
                out.add(segment, q, STRING)
                val e = text.escapeEnd(q, stop)
                out.add(q, e, ESCAPE)
                segment = e
                q = e
            } else {
                q++
            }
        }
        out.add(segment, stop, STRING)
        return stop
    }

    private fun followedByColon(text: CharSequence, from: Int, end: Int): Boolean {
        var p = from
        while (p < end && text[p].isWhitespace()) p++
        return p < end && text[p] == ':'
    }
}

/** YAML: keys, list markers, anchors, tags, quoted and plain scalars, block scalars and comments. */
internal object YamlLexer : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var line = start
        var blockIndent = -1
        while (line < end) {
            val lineEnd = text.lineEndFrom(line, end)
            var p = line
            while (p < lineEnd && text[p] == ' ') p++
            val indent = p - line
            val blank = p >= lineEnd || text[p] == '\r'
            if (blockIndent >= 0) {
                if (blank || indent > blockIndent) {
                    if (!blank) out.add(p, lineEnd, STRING)
                    line = lineEnd + 1
                    continue
                }
                blockIndent = -1
            }
            if (blank) {
                line = lineEnd + 1
                continue
            }
            when {
                text[p] == '#' -> out.add(p, lineEnd, COMMENT)
                text[p] == '%' && indent == 0 -> out.add(p, lineEnd, META)
                indent == 0 && (isMarker(text, p, lineEnd, "---") || isMarker(text, p, lineEnd, "...")) -> {
                    out.add(p, p + 3, META)
                    if (value(text, p + 3, lineEnd, out)) blockIndent = indent
                }
                else -> {
                    while (p < lineEnd && text[p] == '-' && (p + 1 == lineEnd || text[p + 1] == ' ')) {
                        out.add(p, p + 1, KEYWORD)
                        p++
                        while (p < lineEnd && text[p] == ' ') p++
                    }
                    val colon = keyEnd(text, p, lineEnd)
                    if (colon >= 0) {
                        var keyEnd = colon
                        while (keyEnd > p && text[keyEnd - 1] == ' ') keyEnd--
                        out.add(p, keyEnd, PROPERTY)
                        p = colon + 1
                    }
                    if (value(text, p, lineEnd, out)) blockIndent = indent
                }
            }
            line = lineEnd + 1
        }
    }

    private fun isMarker(text: CharSequence, p: Int, lineEnd: Int, marker: String): Boolean =
        text.startsWithAt(marker, p) && (p + 3 >= lineEnd || text[p + 3] == ' ' || text[p + 3] == '\r')

    /** The `:` ending a mapping key that starts at [p], or -1 when the line holds no key. */
    private fun keyEnd(text: CharSequence, p: Int, lineEnd: Int): Int {
        if (p >= lineEnd) return -1
        var q = p
        val c = text[q]
        if (c == '"' || c == '\'') {
            val close = text.indexOfIn(c.toString(), q + 1, lineEnd)
            if (close < 0) return -1
            q = close + 1
            while (q < lineEnd && text[q] == ' ') q++
            return if (q < lineEnd && text[q] == ':') q else -1
        }
        if (c in "[{|>&*!#") return -1
        while (q < lineEnd) {
            if (text[q] == ':' && (q + 1 == lineEnd || text[q + 1] == ' ' || text[q + 1] == '\r')) return q
            if (text[q] == '#' && q > p && text[q - 1] == ' ') return -1
            q++
        }
        return -1
    }

    /** Colours the value part of a line; true when it opens a `|` or `>` block scalar. */
    private fun value(text: CharSequence, from: Int, end: Int, out: TokenList): Boolean {
        var p = from
        var first = true
        var flow = 0
        while (p < end) {
            val c = text[p]
            when {
                c == ' ' || c == '\t' || c == '\r' || c == ',' -> p++
                c == '[' || c == '{' -> {
                    flow++
                    p++
                }
                c == ']' || c == '}' -> {
                    flow = (flow - 1).coerceAtLeast(0)
                    p++
                }
                c == '#' && (p == from || text[p - 1] == ' ') -> {
                    out.add(p, end, COMMENT)
                    return false
                }
                (c == '&' || c == '*') && p + 1 < end && !text[p + 1].isWhitespace() -> {
                    val e = wordEnd(text, p, end)
                    out.add(p, e, VARIABLE)
                    p = e
                }
                c == '!' -> {
                    val e = wordEnd(text, p, end)
                    out.add(p, e, TYPE)
                    p = e
                }
                first && (c == '|' || c == '>') -> {
                    var e = p + 1
                    while (e < end && (text[e] in "+-" || text[e].isDigit())) e++
                    out.add(p, e, KEYWORD)
                    val rest = text.substring(e, end).trim()
                    if (rest.isEmpty() || rest.startsWith("#")) {
                        if (rest.isNotEmpty()) out.add(text.indexOfIn("#", e, end), end, COMMENT)
                        return true
                    }
                    p = e
                }
                c == '"' || c == '\'' -> {
                    var q = p + 1
                    while (q < end && text[q] != c) {
                        if (c == '"' && text[q] == '\\') q++
                        q++
                    }
                    val stop = (q + 1).coerceAtMost(end)
                    out.add(p, stop, if (followedByKeyColon(text, stop, end)) PROPERTY else STRING)
                    p = stop
                }
                else -> {
                    var e = p
                    while (e < end) {
                        val ch = text[e]
                        if (ch == '\r') break
                        if (ch == '#' && e > from && text[e - 1] == ' ') break
                        if (ch == ':' && (e + 1 == end || text[e + 1] == ' ')) break
                        if (flow > 0 && (ch == ',' || ch == ']' || ch == '}')) break
                        e++
                    }
                    var trimmed = e
                    while (trimmed > p && text[trimmed - 1] == ' ') trimmed--
                    val scalar = text.substring(p, trimmed)
                    val kind = when {
                        e < end && text[e] == ':' -> PROPERTY
                        scalar in YamlConstants -> CONSTANT
                        YamlNumber.matches(scalar) -> NUMBER
                        else -> STRING
                    }
                    out.add(p, trimmed, kind)
                    p = if (e < end && text[e] == ':') e + 1 else e.coerceAtLeast(p + 1)
                }
            }
            first = false
        }
        return false
    }

    private fun followedByKeyColon(text: CharSequence, from: Int, end: Int): Boolean {
        var p = from
        while (p < end && text[p] == ' ') p++
        return p < end && text[p] == ':' && (p + 1 == end || text[p + 1] == ' ')
    }

    private fun wordEnd(text: CharSequence, from: Int, end: Int): Int {
        var e = from + 1
        while (e < end && !text[e].isWhitespace() && text[e] !in ",[]{}") e++
        return e
    }

    private val YamlConstants = setOf(
        "true", "false", "True", "False", "TRUE", "FALSE", "yes", "no", "Yes", "No", "on", "off", "On", "Off",
        "null", "Null", "NULL", "~",
    )
    private val YamlNumber = Regex("[-+]?(\\d[\\d_]*(\\.\\d*)?([eE][-+]?\\d+)?|\\.\\d+|0x[0-9a-fA-F]+|0o[0-7]+|\\.inf|\\.nan)")
}

/**
 * INI-style files: `.properties`, `.ini`, `.env`, `.editorconfig`, `.gitconfig` and TOML.
 * Sections, keys, comments and typed values (strings, numbers, booleans, dates, `$VARS`).
 */
internal class IniLexer(private val toml: Boolean, private val properties: Boolean) : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var line = start
        while (line < end) {
            var lineEnd = text.lineEndFrom(line, end)
            var p = line
            while (p < lineEnd && (text[p] == ' ' || text[p] == '\t')) p++
            if (p >= lineEnd) {
                line = lineEnd + 1
                continue
            }
            val c = text[p]
            when {
                c == '#' || c == ';' || (properties && c == '!') -> out.add(p, lineEnd, COMMENT)
                c == '[' && !properties -> {
                    val close = text.indexOfIn("]", p, lineEnd)
                    val stop = if (close < 0) lineEnd else if (close + 1 < lineEnd && text[close + 1] == ']') close + 2 else close + 1
                    out.add(p, stop, TYPE)
                    trailingComment(text, stop, lineEnd, out)
                }
                else -> {
                    if (text.startsWithAt("export ", p)) {
                        out.add(p, p + 6, KEYWORD)
                        p += 7
                    }
                    val separator = separator(text, p, lineEnd)
                    if (separator >= 0) {
                        var keyEnd = separator
                        while (keyEnd > p && (text[keyEnd - 1] == ' ' || text[keyEnd - 1] == '\t')) keyEnd--
                        out.add(p, keyEnd, PROPERTY)
                        p = separator + 1
                    }
                    val stop = if (properties) {
                        if (p < lineEnd) out.add(p, lineEnd, STRING)
                        lineEnd
                    } else {
                        value(text, p, end, lineEnd, out)
                    }
                    if (stop > lineEnd) lineEnd = text.lineEndFrom(stop, end)
                }
            }
            line = lineEnd + 1
        }
    }

    private fun separator(text: CharSequence, from: Int, lineEnd: Int): Int {
        var q = from
        while (q < lineEnd) {
            val c = text[q]
            if (c == '=' || (properties && c == ':')) return q
            if (c == '"' || c == '\'' || c == '[' || c == '{') return if (toml && (c == '"' || c == '\'')) quotedKeySeparator(text, q, lineEnd) else -1
            q++
        }
        return -1
    }

    private fun quotedKeySeparator(text: CharSequence, quote: Int, lineEnd: Int): Int {
        val close = text.indexOfIn(text[quote].toString(), quote + 1, lineEnd)
        if (close < 0) return -1
        val eq = text.indexOfIn("=", close, lineEnd)
        return if (eq >= 0 && text.substring(close + 1, eq).isBlank()) eq else -1
    }

    /** Colours a value from [from]; multi-line TOML strings may run past [lineEnd]. Returns where it stopped. */
    private fun value(text: CharSequence, from: Int, end: Int, lineEnd: Int, out: TokenList): Int {
        var p = from
        var stop = lineEnd
        while (p < stop) {
            val c = text[p]
            when {
                c == '#' || (c == ';' && !toml) -> {
                    if (p == from || text[p - 1] == ' ' || text[p - 1] == '\t') {
                        out.add(p, stop, COMMENT)
                        return stop
                    }
                    p++
                }
                c == '"' || c == '\'' -> {
                    val triple = toml && text.startsWithAt("$c$c$c", p)
                    val close = if (triple) "$c$c$c" else c.toString()
                    val limit = if (triple) end else stop
                    var q = p + close.length
                    var found = -1
                    while (q < limit) {
                        if (c == '"' && text[q] == '\\') {
                            q += 2
                            continue
                        }
                        if (text.startsWithAt(close, q)) {
                            found = q
                            break
                        }
                        q++
                    }
                    val e = if (found < 0) limit else found + close.length
                    out.add(p, e, STRING)
                    p = e
                    if (p > stop) stop = text.lineEndFrom(p, end)
                }
                c == '$' && p + 1 < stop && (text[p + 1] == '{' || isIdentStart(text[p + 1])) -> {
                    var e = p + 1
                    if (text[e] == '{') {
                        val close = text.indexOfIn("}", e, stop)
                        e = if (close < 0) stop else close + 1
                    } else {
                        while (e < stop && isIdentPart(text[e])) e++
                    }
                    out.add(p, e, VARIABLE)
                    p = e
                }
                c.isDigit() || ((c == '-' || c == '+') && p + 1 < stop && text[p + 1].isDigit()) -> {
                    var e = p + 1
                    while (e < stop && (text[e].isLetterOrDigit() || text[e] in "_.:-+")) e++
                    out.add(p, e, NUMBER)
                    p = e
                }
                isIdentStart(c) -> {
                    var e = p + 1
                    while (e < stop && (isIdentPart(text[e]) || text[e] in "-.")) e++
                    val word = text.substring(p, e)
                    when {
                        word.equals("true", true) || word.equals("false", true) || word in IniWords -> out.add(p, e, CONSTANT)
                        toml && nextIsEquals(text, e, stop) -> out.add(p, e, PROPERTY)
                        !toml -> out.add(p, e, STRING)
                    }
                    p = e
                }
                else -> p++
            }
        }
        return stop
    }

    private fun nextIsEquals(text: CharSequence, from: Int, end: Int): Boolean {
        var p = from
        while (p < end && text[p] == ' ') p++
        return p < end && text[p] == '='
    }

    private fun trailingComment(text: CharSequence, from: Int, lineEnd: Int, out: TokenList) {
        val hash = text.indexOfIn("#", from, lineEnd)
        if (hash >= 0) out.add(hash, lineEnd, COMMENT)
    }

    private companion object {
        val IniWords = setOf("yes", "no", "on", "off", "none", "null", "inf", "nan")
    }
}

/** Unified diffs and patches: added, removed, hunk and file header lines. */
internal object DiffLexer : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        var line = start
        while (line < end) {
            val lineEnd = text.lineEndFrom(line, end)
            if (lineEnd > line) {
                val kind = when {
                    text.startsWithAt("+++", line) || text.startsWithAt("---", line) -> HEADING
                    text.startsWithAt("diff ", line) || text.startsWithAt("index ", line) -> HEADING
                    text.startsWithAt("@@", line) -> META
                    text[line] == '+' -> INSERTED
                    text[line] == '-' -> DELETED
                    text[line] == '\\' -> COMMENT
                    else -> null
                }
                if (kind != null) out.add(line, lineEnd, kind)
            }
            line = lineEnd + 1
        }
    }
}
