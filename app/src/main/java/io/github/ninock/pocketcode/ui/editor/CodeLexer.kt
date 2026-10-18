package io.github.ninock.pocketcode.ui.editor

import io.github.ninock.pocketcode.ui.editor.TokenKind.ANNOTATION
import io.github.ninock.pocketcode.ui.editor.TokenKind.COMMENT
import io.github.ninock.pocketcode.ui.editor.TokenKind.CONSTANT
import io.github.ninock.pocketcode.ui.editor.TokenKind.ESCAPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.FUNCTION
import io.github.ninock.pocketcode.ui.editor.TokenKind.KEYWORD
import io.github.ninock.pocketcode.ui.editor.TokenKind.META
import io.github.ninock.pocketcode.ui.editor.TokenKind.NUMBER
import io.github.ninock.pocketcode.ui.editor.TokenKind.PROPERTY
import io.github.ninock.pocketcode.ui.editor.TokenKind.REGEX
import io.github.ninock.pocketcode.ui.editor.TokenKind.STRING
import io.github.ninock.pocketcode.ui.editor.TokenKind.TAG
import io.github.ninock.pocketcode.ui.editor.TokenKind.TYPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.VARIABLE

/** How code is embedded in a string literal. */
internal enum class Interpolation {
    NONE,

    /** `${expr}` (JavaScript templates). */
    DOLLAR_BRACE,

    /** `$name` and `${expr}` (Kotlin, Groovy, Dart). */
    DOLLAR,

    /** `$name`, `${name}`, `$(command)` and `$1`-style specials (shell). */
    SHELL,

    /** `{expr}` with `{{` and `}}` escapes (Python f-strings, C# `$"..."`). */
    BRACE,

    /** `#{expr}` (Ruby). */
    HASH_BRACE,

    /** `\(expr)` (Swift). */
    SWIFT,

    /** `$name` and `{$expr}` (PHP). */
    PHP,

    /** `$(VAR)`, `${VAR}` and `$@`-style automatic variables (Makefile). */
    MAKE,
}

internal class StringRule(
    val open: String,
    val close: String = open,
    val multiline: Boolean = false,
    val escapes: Boolean = true,
    val interpolation: Interpolation = Interpolation.NONE,
    /** A doubled closing quote is a literal quote (SQL `''`, C# `@""`). */
    val doubledClose: Boolean = false,
)

/** A word that turns the string literal right after it raw and/or interpolated (`r"..."`, `f'...'`). */
internal class StringPrefix(val raw: Boolean = false, val interpolation: Interpolation? = null)

/** Everything that differs between the C-family, scripting and shell languages the [CodeLexer] reads. */
internal class CodeSpec(
    val keywords: Set<String>,
    /** Keywords only when another word follows (`data class`, `type Alias =`), so variables named `data` stay plain. */
    val softKeywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    /** Built-in functions and commands, coloured as functions even without a call. */
    val builtins: Set<String> = emptySet(),
    val lineComments: List<String> = listOf("//"),
    val blockComment: Pair<String, String>? = "/*" to "*/",
    val nestedComments: Boolean = false,
    /** Ordered longest delimiter first. */
    val strings: List<StringRule> = listOf(StringRule("\""), StringRule("'")),
    /** Keyed by the lowercase prefix. */
    val stringPrefixes: Map<String, StringPrefix> = emptyMap(),
    /** Keywords whose next word names a function (`fun`, `def`). */
    val functionKeywords: Set<String> = emptySet(),
    /** Keywords whose next word names a type (`class`, `struct`). */
    val typeKeywords: Set<String> = emptySet(),
    val annotations: Boolean = false,
    /** `@` starts a decorator only at the start of a line (Python, where `@` is also an operator). */
    val decoratorsAtLineStart: Boolean = false,
    /** `#include`-style directives at the start of a line. */
    val preprocessor: Boolean = false,
    /** `$name` outside strings (shell, PHP, PowerShell). */
    val dollarVariables: Interpolation = Interpolation.NONE,
    /** Ruby `@ivar`, `@@cvar`, `$global` and `:symbol`. */
    val rubySigils: Boolean = false,
    val regexLiterals: Boolean = false,
    val jsx: Boolean = false,
    val capitalizedTypes: Boolean = true,
    val caseInsensitive: Boolean = false,
    /** Keywords count only as the first word of a line (Dockerfile instructions). */
    val keywordsAtLineStart: Boolean = false,
    /** Characters allowed in identifiers besides letters, digits and `_`. */
    val identifierExtras: String = "",
    /** `#` starts a comment only at the start of a word (shell, where `$#` and `a#b` are not comments). */
    val wordComments: Boolean = false,
    /** Rust lifetimes, `r#"raw"#` strings, `#[attributes]` and `macro!` calls. */
    val rust: Boolean = false,
    /** C++ `R"delim(raw)delim"` strings. */
    val cppRawStrings: Boolean = false,
    /** Lua `[[long strings]]` and `--[[long comments]]`. */
    val luaLongBrackets: Boolean = false,
    /** Ruby `=begin` / `=end` comments. */
    val rubyBlockComments: Boolean = false,
    val heredocs: Boolean = false,
    /** `NAME=value` colours NAME (shell, .env). */
    val assignments: Boolean = false,
    /** Makefile `target:` and `VAR =` lines. */
    val makeRules: Boolean = false,
    /** PHP `<?php` and `?>` tags. */
    val phpTags: Boolean = false,
)

/**
 * One-pass lexer for C-family and scripting languages, driven by a [CodeSpec]. It knows comments,
 * strings (with escapes and interpolated code), numbers, annotations, keyword/type/constant
 * words, declarations (`fun name`, `class Name`), calls (`name(`), JS regex literals and JSX.
 */
internal class CodeLexer(private val spec: CodeSpec) : Lexer {
    override fun lex(text: CharSequence, start: Int, end: Int, out: TokenList) {
        Scan(text, end, out).code(start, null)
    }

    private inner class Scan(val text: CharSequence, val end: Int, val out: TokenList) {
        /** Kind of the next word after a declaring keyword (`fun` makes it FUNCTION); null otherwise. */
        private var declaration: TokenKind? = null

        /** `<...>` depth inside a pending declaration (`fun <T> name`). */
        private var angleDepth = 0

        /** Whether a `/` or `<` here starts a regex or JSX element rather than an operator. */
        private var expressionStart = true

        /** Only whitespace so far on the current line. */
        private var lineStart = true

        /** Nested interpolations and JSX elements, bounded so hostile input can't overflow the stack. */
        private var nesting = 0

        /** Lexes code from [from] until [stop] closes at depth zero, returning the stop's index (or [end]). */
        fun code(from: Int, stop: Char?): Int {
            val open = when (stop) {
                '}' -> '{'
                ')' -> '('
                else -> null
            }
            var depth = 0
            var i = from
            while (i < end) {
                val c = text[i]
                if (c == '\n') {
                    lineStart = true
                    i++
                    continue
                }
                if (c == ' ' || c == '\t' || c == '\r' || c == '\u000c') {
                    i++
                    continue
                }
                if (stop != null) {
                    if (c == open) {
                        depth++
                    } else if (c == stop) {
                        if (depth == 0) return i
                        depth--
                    }
                }
                val atLineStart = lineStart
                lineStart = false
                i = token(i, atLineStart).coerceAtLeast(i + 1)
            }
            return end
        }

        private fun token(i: Int, atLineStart: Boolean): Int {
            val c = text[i]
            commentEnd(i, atLineStart).let { if (it > 0) return it }
            if (spec.preprocessor && c == '#' && atLineStart) return preprocessor(i)
            if (spec.rust && c == '#' && i + 1 < end && (text[i + 1] == '[' || text.startsWithAt("![", i + 1))) {
                val e = bracketEnd(text.indexOfIn("[", i, end))
                out.add(i, e, META)
                return e
            }
            if (spec.makeRules && (i == 0 || text[i - 1] == '\n')) makeRule(i).let { if (it > 0) return it }
            if (spec.phpTags && (text.startsWithAt("<?", i) || text.startsWithAt("?>", i))) {
                var e = i + 2
                while (e < end && isIdentPart(text[e])) e++
                out.add(i, e, META)
                return e
            }
            if (spec.heredocs && c == '<' && text.startsWithAt("<<", i)) heredoc(i).let { if (it > 0) return it }
            if (spec.luaLongBrackets && c == '[') {
                val e = luaLongBracketEnd(i)
                if (e > 0) {
                    out.add(i, e, STRING)
                    expressionStart = false
                    return e
                }
            }
            for (rule in spec.strings) {
                if (!text.startsWithAt(rule.open, i)) continue
                if (spec.rust && rule.open == "'") rustLifetime(i).let { if (it > 0) return it }
                return string(i, i, rule, rule.escapes, rule.interpolation)
            }
            if (c.isDigit() || (c == '.' && i + 1 < end && text[i + 1].isDigit() && (i == 0 || !isIdentPart(text[i - 1])))) {
                val e = text.numberEnd(i, end)
                out.add(i, e, NUMBER)
                afterValue()
                return e
            }
            if (c == '$' && spec.dollarVariables != Interpolation.NONE) {
                val e = interpolation(i, spec.dollarVariables)
                if (e > 0) {
                    afterValue()
                    return e
                }
            }
            if (spec.rubySigils) rubySigil(i).let { if (it > 0) return it }
            if (c == '@' && spec.annotations && i + 1 < end && isIdentStart(text[i + 1]) && (!spec.decoratorsAtLineStart || atLineStart)) {
                var e = i + 1
                while (e < end && (isIdentPart(text[e]) || text[e] == '.')) e++
                out.add(i, e, ANNOTATION)
                return e
            }
            if (isIdentStart(c) || (c in spec.identifierExtras && c != '-')) return word(i, atLineStart)
            if (c == '/' && spec.regexLiterals && expressionStart) {
                val e = regexEnd(i)
                if (e > 0) {
                    out.add(i, e, REGEX)
                    afterValue()
                    return e
                }
            }
            if (c == '<' && spec.jsx && expressionStart && nesting < MaxNesting) {
                val e = jsxElement(i)
                if (e > 0) {
                    afterValue()
                    return e
                }
            }
            punctuation(c)
            return i + 1
        }

        private fun afterValue() {
            expressionStart = false
            declaration = null
            angleDepth = 0
        }

        private fun punctuation(c: Char) {
            expressionStart = c !in ")]}"
            if (declaration == null) return
            when (c) {
                '.', '*', '&', '?' -> Unit
                '<' -> angleDepth++
                '>' -> angleDepth = (angleDepth - 1).coerceAtLeast(0)
                ',' -> if (angleDepth == 0) declaration = null
                else -> {
                    declaration = null
                    angleDepth = 0
                }
            }
        }

        // --- Comments ------------------------------------------------------------------------

        /** End of a comment starting at [i], or -1 when none starts there. */
        private fun commentEnd(i: Int, atLineStart: Boolean): Int {
            if (spec.luaLongBrackets && text.startsWithAt("--[", i)) {
                val e = luaLongBracketEnd(i + 2)
                if (e > 0) {
                    out.add(i, e, COMMENT)
                    return e
                }
            }
            spec.blockComment?.let { (open, close) ->
                if (text.startsWithAt(open, i)) {
                    val e = blockCommentEnd(i, open, close)
                    out.add(i, e, COMMENT)
                    return e
                }
            }
            if (spec.rubyBlockComments && atLineStart && text.startsWithAt("=begin", i)) {
                var line = text.lineEndFrom(i, end)
                while (line < end) {
                    val next = line + 1
                    if (text.startsWithAt("=end", next)) {
                        line = text.lineEndFrom(next, end)
                        break
                    }
                    line = text.lineEndFrom(next, end)
                }
                out.add(i, line, COMMENT)
                return line
            }
            for (marker in spec.lineComments) {
                if (!text.startsWithAt(marker, i)) continue
                if (spec.wordComments && i > 0 && !text[i - 1].isWhitespace() && text[i - 1] !in ";|&(") continue
                val e = text.lineEndFrom(i, end)
                out.add(i, e, COMMENT)
                return e
            }
            return -1
        }

        private fun blockCommentEnd(i: Int, open: String, close: String): Int {
            var depth = 1
            var p = i + open.length
            while (p < end) {
                if (text.startsWithAt(close, p)) {
                    p += close.length
                    depth--
                    if (depth == 0) return p
                    continue
                }
                if (spec.nestedComments && text.startsWithAt(open, p)) {
                    p += open.length
                    depth++
                    continue
                }
                p++
            }
            return end
        }

        /** End of a Lua long bracket (`[[...]]`, `[==[...]==]`) opening at [at], or -1. */
        private fun luaLongBracketEnd(at: Int): Int {
            var p = at + 1
            var level = 0
            while (p < end && text[p] == '=') {
                level++
                p++
            }
            if (p >= end || text[p] != '[') return -1
            val close = "]" + "=".repeat(level) + "]"
            val found = text.indexOfIn(close, p + 1, end)
            return if (found < 0) end else found + close.length
        }

        // --- Strings ---------------------------------------------------------------------------

        /** Lexes a string whose token starts at [tokenStart] (any prefix) and whose quote is at [quoteAt]. */
        private fun string(tokenStart: Int, quoteAt: Int, rule: StringRule, escapes: Boolean, mode: Interpolation): Int {
            afterValue()
            var i = quoteAt + rule.open.length
            var segment = tokenStart
            while (i < end) {
                val c = text[i]
                if (text.startsWithAt(rule.close, i)) {
                    if (rule.doubledClose && text.startsWithAt(rule.close, i + rule.close.length)) {
                        i += rule.close.length * 2
                        continue
                    }
                    i += rule.close.length
                    out.add(segment, i, STRING)
                    return i
                }
                if (c == '\n' && !rule.multiline) break
                if (mode != Interpolation.NONE && nesting < MaxNesting && opensInterpolation(i, mode)) {
                    out.add(segment, i, STRING)
                    i = interpolation(i, mode).coerceAtLeast(i + 1)
                    segment = i
                    continue
                }
                if (escapes && c == '\\' && i + 1 < end) {
                    out.add(segment, i, STRING)
                    val e = if (text[i + 1] == '\n' || text[i + 1] == '\r') i + 2 else text.escapeEnd(i, end)
                    out.add(i, e, ESCAPE)
                    i = e
                    segment = i
                    continue
                }
                i++
            }
            out.add(segment, i, STRING)
            return i
        }

        private fun opensInterpolation(i: Int, mode: Interpolation): Boolean {
            val c = text[i]
            val next = if (i + 1 < end) text[i + 1] else '\u0000'
            return when (mode) {
                Interpolation.NONE -> false
                Interpolation.DOLLAR_BRACE -> c == '$' && next == '{'
                Interpolation.DOLLAR -> c == '$' && (next == '{' || isIdentStart(next))
                Interpolation.SHELL -> c == '$' && (next == '{' || next == '(' || isIdentStart(next) || next.isDigit() || next in "@#?$!*-")
                Interpolation.BRACE -> c == '{' || (c == '}' && next == '}')
                Interpolation.HASH_BRACE -> c == '#' && next == '{'
                Interpolation.SWIFT -> c == '\\' && next == '('
                Interpolation.PHP -> (c == '$' && isIdentStart(next)) || (c == '{' && next == '$')
                Interpolation.MAKE -> c == '$' && (next == '(' || next == '{' || isIdentPart(next) || next in "@<^?*%+|")
            }
        }

        /**
         * Lexes the interpolation (or the variable reference outside a string)
         * at [i]. Returns where it ends, or -1 when nothing starts there.
         */
        private fun interpolation(i: Int, mode: Interpolation): Int {
            val next = if (i + 1 < end) text[i + 1] else '\u0000'
            return when (mode) {
                Interpolation.NONE -> -1
                Interpolation.DOLLAR_BRACE, Interpolation.HASH_BRACE -> embeddedCode(i, 2, '}')
                Interpolation.DOLLAR -> if (next == '{') embeddedCode(i, 2, '}') else identifierVariable(i, i + 1)
                Interpolation.SWIFT -> embeddedCode(i, 2, ')')
                Interpolation.BRACE -> when {
                    text.startsWithAt("{{", i) || text.startsWithAt("}}", i) -> {
                        out.add(i, i + 2, ESCAPE)
                        i + 2
                    }
                    text[i] == '{' -> embeddedCode(i, 1, '}')
                    else -> -1
                }
                Interpolation.PHP -> if (text[i] == '{') embeddedCode(i, 1, '}') else identifierVariable(i, i + 1)
                Interpolation.MAKE -> {
                    val e = when (next) {
                        '(' -> bracketEnd(i + 1, '(', ')')
                        '{' -> bracketEnd(i + 1, '{', '}')
                        else -> minOf(end, i + 2)
                    }
                    out.add(i, e, VARIABLE)
                    e
                }
                Interpolation.SHELL -> when {
                    next == '(' && nesting < MaxNesting -> embeddedCode(i, 2, ')')
                    next == '{' -> {
                        val e = bracketEnd(i + 1, '{', '}')
                        out.add(i, e, VARIABLE)
                        e
                    }
                    isIdentStart(next) -> identifierVariable(i, i + 1)
                    next.isDigit() || next in "@#?$!*-" -> {
                        out.add(i, i + 2, VARIABLE)
                        i + 2
                    }
                    else -> -1
                }
            }
        }

        private fun identifierVariable(start: Int, nameStart: Int): Int {
            var e = nameStart
            while (e < end && isIdentPart(text[e])) e++
            if (e == nameStart) return -1
            out.add(start, e, VARIABLE)
            return e
        }

        /** `${`-style opener of [openLength] chars, code up to [close], then the closer. */
        private fun embeddedCode(i: Int, openLength: Int, close: Char): Int {
            out.add(i, i + openLength, KEYWORD)
            nesting++
            val savedLineStart = lineStart
            expressionStart = true
            val stop = code(i + openLength, close)
            lineStart = savedLineStart
            nesting--
            if (stop >= end) return end
            out.add(stop, stop + 1, KEYWORD)
            afterValue()
            return stop + 1
        }

        /** Index just past the bracket closing the one at [open] (same line or not), counting nesting. */
        private fun bracketEnd(open: Int, openChar: Char = '[', closeChar: Char = ']'): Int {
            if (open < 0) return end
            var depth = 0
            var p = open
            while (p < end) {
                val c = text[p]
                if (c == openChar) depth++
                if (c == closeChar) {
                    depth--
                    if (depth == 0) return p + 1
                }
                if (c == '\n' && openChar == '{') return p
                p++
            }
            return end
        }

        /** A Rust lifetime (`'a`) at [i] rather than a char literal; -1 when it is a literal. */
        private fun rustLifetime(i: Int): Int {
            if (i + 1 >= end || !isIdentStart(text[i + 1])) return -1
            if (i + 2 < end && text[i + 2] == '\'') return -1
            var e = i + 1
            while (e < end && isIdentPart(text[e])) e++
            if (e < end && text[e] == '\'') return -1
            out.add(i, e, ANNOTATION)
            return e
        }

        // --- Words -----------------------------------------------------------------------------

        private fun word(i: Int, atLineStart: Boolean): Int {
            var e = i + 1
            while (e < end && (isIdentPart(text[e]) || text[e] in spec.identifierExtras)) e++
            // Shell words like `apt-get` keep their dashes, but not a trailing one.
            while (e > i + 1 && text[e - 1] == '-') e--
            val word = text.substring(i, e)
            val key = if (spec.caseInsensitive) word.lowercase() else word

            prefixedString(i, e, key)?.let { return it }

            val pending = declaration
            val inGenerics = pending != null && angleDepth > 0
            if (!inGenerics) declaration = null
            expressionStart = false
            val memberAccess = i > 0 && text[i - 1] == '.' && (i < 2 || text[i - 2] != '.')
            var tokenEnd = e
            val kind: TokenKind? = when {
                isKeyword(key, e, atLineStart, memberAccess) -> {
                    if (!inGenerics) {
                        declaration = when (key) {
                            in spec.functionKeywords -> FUNCTION
                            in spec.typeKeywords -> TYPE
                            else -> pending
                        }
                    }
                    expressionStart = key !in ValueKeywords
                    KEYWORD
                }
                key in spec.constants -> CONSTANT
                pending == FUNCTION && !inGenerics -> {
                    if (receiverFollows(e)) {
                        declaration = FUNCTION
                        if (word[0].isUpperCase()) TYPE else null
                    } else {
                        FUNCTION
                    }
                }
                pending == TYPE && !inGenerics -> TYPE
                spec.rust && e < end && text[e] == '!' && (e + 1 >= end || text[e + 1] != '=') -> {
                    tokenEnd = e + 1
                    FUNCTION
                }
                key in spec.types -> TYPE
                key in spec.builtins -> FUNCTION
                isCall(e) -> FUNCTION
                spec.assignments && e < end && text[e] == '=' && (e + 1 >= end || text[e + 1] != '=') -> VARIABLE
                spec.capitalizedTypes && word[0].isUpperCase() ->
                    if (word.length > 1 && word.none { it.isLowerCase() }) CONSTANT else TYPE
                else -> null
            }
            if (kind != null) out.add(i, tokenEnd, kind)
            return tokenEnd
        }

        private fun isKeyword(key: String, wordEnd: Int, atLineStart: Boolean, memberAccess: Boolean): Boolean {
            val soft = key in spec.softKeywords
            if (!soft && key !in spec.keywords) return false
            if (memberAccess && key !in MemberKeywords) return false
            if (spec.keywordsAtLineStart && !atLineStart) return false
            if (!soft) return true
            // A soft keyword needs another word after it on the same line: `data class`, not `data = 1`.
            var p = wordEnd
            while (p < end && (text[p] == ' ' || text[p] == '\t')) p++
            return p < end && (isIdentPart(text[p]) || text[p] in "\"'`@")
        }

        /** A string literal behind a prefix word (`r"..."`, `f'...'`, `r#"..."#`, `R"(...)"`), or null. */
        private fun prefixedString(i: Int, e: Int, key: String): Int? {
            if (e >= end) return null
            val next = text[e]
            if (spec.rust && (key == "r" || key == "br") && (next == '#' || next == '"')) {
                var p = e
                while (p < end && text[p] == '#') p++
                if (p < end && text[p] == '"') {
                    val close = "\"" + "#".repeat(p - e)
                    val found = text.indexOfIn(close, p + 1, end)
                    val stop = if (found < 0) end else found + close.length
                    out.add(i, stop, STRING)
                    afterValue()
                    return stop
                }
            }
            if (spec.cppRawStrings && key.endsWith("R") && key in CppRawPrefixes && next == '"') {
                val paren = text.indexOfIn("(", e + 1, minOf(end, e + 18))
                if (paren > 0) {
                    val close = ")" + text.substring(e + 1, paren) + "\""
                    val found = text.indexOfIn(close, paren + 1, end)
                    val stop = if (found < 0) end else found + close.length
                    out.add(i, stop, STRING)
                    afterValue()
                    return stop
                }
            }
            if (next != '"' && next != '\'' && next != '`') return null
            val prefix = spec.stringPrefixes[key.lowercase()] ?: return null
            val rule = spec.strings.firstOrNull { text.startsWithAt(it.open, e) } ?: return null
            return string(i, e, rule, rule.escapes && !prefix.raw, prefix.interpolation ?: rule.interpolation)
        }

        /** A `.` follows (after any `<...>` type arguments): the word is an extension receiver, not the name. */
        private fun receiverFollows(wordEnd: Int): Boolean {
            var p = wordEnd
            if (p < end && text[p] == '<') {
                var depth = 0
                val limit = minOf(end, p + 80)
                while (p < limit) {
                    if (text[p] == '<') depth++
                    if (text[p] == '>' && --depth == 0) break
                    if (text[p] == '\n' || text[p] == '(') return false
                    p++
                }
                p++
            }
            while (p < end && text[p] == '?') p++
            return p < end && text[p] == '.'
        }

        /** `name(` or `name<T>(` follows. */
        private fun isCall(wordEnd: Int): Boolean {
            var p = wordEnd
            while (p < end && (text[p] == ' ' || text[p] == '\t')) p++
            if (p < end && text[p] == '<' && !spec.caseInsensitive) {
                // Generic call: skip a short, single-line type argument list.
                var depth = 0
                val limit = minOf(end, p + 80)
                while (p < limit) {
                    val c = text[p]
                    if (c == '<') depth++
                    if (c == '>') {
                        depth--
                        if (depth == 0) break
                    }
                    if (c == '\n' || c == ';' || c == '{' || c == '(' || c == '=') return false
                    p++
                }
                if (depth != 0) return false
                p++
            }
            return p < end && text[p] == '('
        }

        // --- Language extras --------------------------------------------------------------------

        private fun preprocessor(i: Int): Int {
            var p = i + 1
            while (p < end && (text[p] == ' ' || text[p] == '\t')) p++
            val nameStart = p
            while (p < end && isIdentPart(text[p])) p++
            out.add(i, p, META)
            val directive = text.substring(nameStart, p)
            if (directive == "include" || directive == "import") {
                var q = p
                while (q < end && (text[q] == ' ' || text[q] == '\t')) q++
                if (q < end && text[q] == '<') {
                    val close = text.indexOfIn(">", q, text.lineEndFrom(q, end))
                    if (close > 0) {
                        out.add(q, close + 1, STRING)
                        return close + 1
                    }
                }
            }
            return p
        }

        private fun rubySigil(i: Int): Int {
            val c = text[i]
            val next = if (i + 1 < end) text[i + 1] else '\u0000'
            return when {
                c == '@' && next == '@' && i + 2 < end && isIdentStart(text[i + 2]) -> identifierVariable(i, i + 2)
                c == '@' && isIdentStart(next) -> identifierVariable(i, i + 1)
                c == '$' && isIdentStart(next) -> identifierVariable(i, i + 1)
                c == ':' && isIdentStart(next) && (i == 0 || (text[i - 1] != ':' && !isIdentPart(text[i - 1]))) -> {
                    var e = i + 1
                    while (e < end && isIdentPart(text[e])) e++
                    if (e < end && text[e] in "?!") e++
                    out.add(i, e, CONSTANT)
                    e
                }
                else -> -1
            }
        }

        /** Makefile `target: deps` and `NAME = value` lines (word at column zero). */
        private fun makeRule(i: Int): Int {
            if (text[i] == '\t') return -1
            var e = i
            while (e < end && text[e] !in ":=?+!#\n \t") e++
            if (e == i) return -1
            var p = e
            while (p < end && (text[p] == ' ' || text[p] == '\t')) p++
            if (p >= end) return -1
            val assignment = text[p] == '=' || text.startsWithAt(":=", p) || text.startsWithAt("::=", p) ||
                text.startsWithAt("?=", p) || text.startsWithAt("+=", p) || text.startsWithAt("!=", p)
            return when {
                assignment -> {
                    out.add(i, e, PROPERTY)
                    e
                }
                text[p] == ':' -> {
                    out.add(i, e, FUNCTION)
                    e
                }
                else -> -1
            }
        }

        /** A shell here-document: the `<<EOF` marker, the rest of that line as code, then the body. */
        private fun heredoc(i: Int): Int {
            var p = i + 2
            val strip = p < end && text[p] == '-'
            if (strip) p++
            if (p < end && text[p] == '<') return -1
            while (p < end && text[p] == ' ') p++
            val quote = if (p < end && (text[p] == '\'' || text[p] == '"')) text[p] else null
            if (quote != null) p++
            val nameStart = p
            while (p < end && isIdentPart(text[p])) p++
            if (p == nameStart) return -1
            val delimiter = text.substring(nameStart, p)
            if (quote != null && p < end && text[p] == quote) p++
            out.add(i, p, STRING)
            val lineEnd = text.lineEndFrom(p, end)
            Scan(text, lineEnd, out).code(p, null)
            var line = lineEnd + 1
            while (line < end) {
                val stop = text.lineEndFrom(line, end)
                var content = line
                if (strip) while (content < stop && text[content] == '\t') content++
                var contentEnd = stop
                if (contentEnd > content && text[contentEnd - 1] == '\r') contentEnd--
                if (contentEnd - content == delimiter.length && text.startsWithAt(delimiter, content)) {
                    out.add(lineEnd + 1, line, STRING)
                    out.add(content, contentEnd, STRING)
                    lineStart = false
                    return stop
                }
                line = stop + 1
            }
            out.add(lineEnd + 1, end, STRING)
            return end
        }

        private fun regexEnd(i: Int): Int {
            var p = i + 1
            if (p >= end || text[p] == '/' || text[p] == '*' || text[p] == ' ' || text[p] == '\n') return -1
            var inClass = false
            while (p < end) {
                when (text[p]) {
                    '\n' -> return -1
                    '\\' -> p++
                    '[' -> inClass = true
                    ']' -> inClass = false
                    '/' -> if (!inClass) {
                        p++
                        while (p < end && text[p].isLetter()) p++
                        return p
                    }
                }
                p++
            }
            return -1
        }

        // --- JSX ---------------------------------------------------------------------------------

        /** A JSX element at [i] (tag, attributes and children); -1 when it doesn't look like one. */
        private fun jsxElement(i: Int): Int {
            var p = i + 1
            if (p >= end) return -1
            if (text[p] == '>') return jsxChildren(p + 1)
            if (!isIdentStart(text[p])) return -1
            nesting++
            try {
                var e = p
                while (e < end && (isIdentPart(text[e]) || text[e] in ".-:")) e++
                out.add(p, e, if (text[p].isUpperCase()) TYPE else TAG)
                p = e
                while (p < end) {
                    val c = text[p]
                    when {
                        c.isWhitespace() -> p++
                        c == '/' && p + 1 < end && text[p + 1] == '>' -> return p + 2
                        c == '>' -> return jsxChildren(p + 1)
                        c == '{' -> {
                            val close = code(p + 1, '}')
                            p = if (close < end) close + 1 else end
                        }
                        isIdentStart(c) -> {
                            var a = p
                            while (a < end && (isIdentPart(text[a]) || text[a] in "-:")) a++
                            out.add(p, a, TokenKind.ATTRIBUTE)
                            p = a
                        }
                        c == '=' -> p++
                        c == '"' || c == '\'' -> {
                            val close = text.indexOfIn(c.toString(), p + 1, end)
                            val stop = if (close < 0) end else close + 1
                            out.add(p, stop, STRING)
                            p = stop
                        }
                        else -> return p
                    }
                }
                return end
            } finally {
                nesting--
            }
        }

        private fun jsxChildren(from: Int): Int {
            var p = from
            while (p < end) {
                val c = text[p]
                when {
                    c == '<' && p + 1 < end && text[p + 1] == '/' -> {
                        var e = p + 2
                        while (e < end && (isIdentPart(text[e]) || text[e] in ".-:")) e++
                        if (e > p + 2) out.add(p + 2, e, if (text[p + 2].isUpperCase()) TYPE else TAG)
                        val close = text.indexOfIn(">", e, end)
                        return if (close < 0) end else close + 1
                    }
                    c == '<' && nesting < MaxNesting -> {
                        val e = jsxElement(p)
                        p = if (e > p) e else p + 1
                    }
                    c == '{' -> {
                        val close = code(p + 1, '}')
                        p = if (close < end) close + 1 else end
                    }
                    else -> p++
                }
            }
            return end
        }
    }

    private companion object {
        const val MaxNesting = 48

        /** Keywords that end an expression, so a following `/` divides. */
        val ValueKeywords = setOf("this", "super", "self", "true", "false", "null", "nil", "None", "True", "False", "undefined")

        /** Keywords still coloured after a dot (`Foo.class`, `Outer.this`). */
        val MemberKeywords = setOf("class", "this", "super", "new")

        val CppRawPrefixes = setOf("R", "u8R", "uR", "UR", "LR")
    }
}
