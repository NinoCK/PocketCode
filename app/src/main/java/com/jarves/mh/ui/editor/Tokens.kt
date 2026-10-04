package com.jarves.mh.ui.editor

/** What a stretch of source text is, which decides its colour. Plain text has no token. */
enum class TokenKind {
    KEYWORD,
    TYPE,
    FUNCTION,
    STRING,
    ESCAPE,
    NUMBER,
    CONSTANT,
    COMMENT,
    ANNOTATION,
    PROPERTY,
    TAG,
    ATTRIBUTE,
    VARIABLE,
    REGEX,
    META,
    HEADING,
    EMPHASIS,
    STRONG,
    LINK,
    INSERTED,
    DELETED,
}

private val TokenKinds = TokenKind.entries.toTypedArray()

/**
 * Highlighted ranges of a text in ascending, non-overlapping order, packed into one int array.
 * Adjacent ranges of the same kind are merged as they are added, which keeps the number of
 * styles handed to the text layout down.
 */
class TokenList(initialCapacity: Int = 64) {
    private var data = IntArray(initialCapacity.coerceAtLeast(4) * 3)

    var size = 0
        private set

    fun start(index: Int): Int = data[index * 3]

    fun end(index: Int): Int = data[index * 3 + 1]

    fun kind(index: Int): TokenKind = TokenKinds[data[index * 3 + 2]]

    fun add(start: Int, end: Int, kind: TokenKind) {
        var from = start
        if (size > 0) {
            val last = (size - 1) * 3
            val lastEnd = data[last + 1]
            if (from < lastEnd) from = lastEnd
            if (end <= from) return
            if (lastEnd == from && data[last + 2] == kind.ordinal) {
                data[last + 1] = end
                return
            }
        }
        if (end <= from) return
        if ((size + 1) * 3 > data.size) data = data.copyOf(data.size * 2)
        val at = size * 3
        data[at] = from
        data[at + 1] = end
        data[at + 2] = kind.ordinal
        size++
    }

    /** Index of the first token that ends after [offset], or [size] when there is none. */
    fun firstEndingAfter(offset: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (end(mid) <= offset) low = mid + 1 else high = mid
        }
        return low
    }

    /** The kind of the token covering [offset], or null for plain text. */
    fun kindAt(offset: Int): TokenKind? {
        val index = firstEndingAfter(offset)
        return if (index < size && start(index) <= offset) kind(index) else null
    }

    companion object {
        val Empty = TokenList(1)
    }
}

/** Splits text into [TokenList] ranges. Lexers can work on part of a text (embedded languages). */
internal fun interface Lexer {
    fun lex(text: CharSequence, start: Int, end: Int, out: TokenList)
}

/** Colours [text] as [language]. Text without a lexer comes back with no tokens. */
fun highlight(text: CharSequence, language: CodeLanguage): TokenList {
    val lexer = language.lexer ?: return TokenList.Empty
    val out = TokenList((text.length / 12).coerceIn(16, 1 shl 16))
    lexer.lex(text, 0, text.length, out)
    return out
}

// --- Scanning helpers shared by the lexers ------------------------------------------------------

internal fun CharSequence.startsWithAt(prefix: String, index: Int, ignoreCase: Boolean = false): Boolean {
    if (index < 0 || index + prefix.length > length) return false
    for (i in prefix.indices) {
        if (!this[index + i].equals(prefix[i], ignoreCase)) return false
    }
    return true
}

/** First index of [needle] at or after [from] and before [end], or -1. */
internal fun CharSequence.indexOfIn(needle: String, from: Int, end: Int, ignoreCase: Boolean = false): Int {
    if (needle.isEmpty()) return from
    val first = needle[0]
    var i = from
    val last = end - needle.length
    while (i <= last) {
        if (this[i].equals(first, ignoreCase) && startsWithAt(needle, i, ignoreCase)) return i
        i++
    }
    return -1
}

/** Index of the line break ending the line that holds [from] (or [end]). */
internal fun CharSequence.lineEndFrom(from: Int, end: Int): Int {
    var i = from
    while (i < end && this[i] != '\n') i++
    return i
}

internal fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetter()

internal fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

internal fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/**
 * End of the number literal starting at [start]: decimals with `_` separators, fractions,
 * exponents, `0x`/`0b`/`0o` prefixes and trailing type suffixes such as `L`, `f` or `u8`.
 */
internal fun CharSequence.numberEnd(start: Int, end: Int): Int {
    var i = start
    if (i + 1 < end && this[i] == '0' && this[i + 1] in "xXbBoO") {
        i += 2
        while (i < end && (isHexDigit(this[i]) || this[i] == '_')) i++
    } else {
        while (i < end && (this[i].isDigit() || this[i] == '_')) i++
        if (i + 1 < end && this[i] == '.' && this[i + 1].isDigit()) {
            i++
            while (i < end && (this[i].isDigit() || this[i] == '_')) i++
        }
        if (i < end && this[i] in "eE") {
            var j = i + 1
            if (j < end && this[j] in "+-") j++
            if (j < end && this[j].isDigit()) {
                i = j
                while (i < end && this[i].isDigit()) i++
            }
        }
    }
    while (i < end && this[i].isLetterOrDigit()) i++
    return i
}

/** End of the backslash escape at [start] (`\n`, `é`, `\x41`, `\u{1F600}`). */
internal fun CharSequence.escapeEnd(start: Int, end: Int): Int {
    if (start + 1 >= end) return end
    val c = this[start + 1]
    var i = start + 2
    when (c) {
        'u' -> {
            if (i < end && this[i] == '{') {
                while (i < end && this[i] != '}' && this[i] != '\n') i++
                if (i < end && this[i] == '}') i++
            } else {
                val stop = minOf(end, i + 4)
                while (i < stop && isHexDigit(this[i])) i++
            }
        }
        'x' -> {
            val stop = minOf(end, i + 2)
            while (i < stop && isHexDigit(this[i])) i++
        }
        in '0'..'7' -> {
            val stop = minOf(end, i + 2)
            while (i < stop && this[i] in '0'..'7') i++
        }
    }
    return i
}
