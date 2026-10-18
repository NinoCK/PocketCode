package io.github.ninock.pocketcode.ui.editor

import kotlin.math.abs

/**
 * Replace `text[start, end)` with [text]; afterwards the selection is
 * [selectionStart]..[selectionEnd] in the new text.
 */
data class CodeEdit(val start: Int, val end: Int, val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/** How one indentation level is written. */
data class IndentStyle(val useTabs: Boolean, val size: Int) {
    val unit: String get() = if (useTabs) "\t" else " ".repeat(size)

    val label: String get() = if (useTabs) "Tabs" else "$size spaces"

    companion object {
        fun of(language: CodeLanguage) = IndentStyle(language.indentWithTabs, language.indentSize)
    }
}

/** Start offsets of every line, for line numbers and line lookups. */
class LineIndex(text: CharSequence) {
    private val starts: IntArray
    val length = text.length

    init {
        var count = 1
        for (i in 0 until text.length) if (text[i] == '\n') count++
        starts = IntArray(count)
        var line = 1
        for (i in 0 until text.length) if (text[i] == '\n') starts[line++] = i + 1
    }

    val lineCount: Int get() = starts.size

    /** Zero-based line holding [offset]. */
    fun lineOf(offset: Int): Int {
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= offset) low = mid else high = mid - 1
        }
        return low
    }

    fun startOf(line: Int): Int = starts[line.coerceIn(0, starts.size - 1)]

    /** Offset of the line break ending [line], or the text length for the last line. */
    fun endOf(line: Int): Int = if (line + 1 < starts.size) starts[line + 1] - 1 else length
}

/**
 * Text operations behind the editor's smart typing, key bar and menu. Each takes the current text
 * and selection and returns one [CodeEdit] (a single undo step), or null when nothing changes.
 */
object CodeEditing {
    private const val Openers = "([{"
    private const val Closers = ")]}"

    fun lineStartOf(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun lineEndOf(text: CharSequence, offset: Int): Int = text.lineEndFrom(offset.coerceIn(0, text.length), text.length)

    /** Whole lines touched by a selection; a selection ending at column 0 leaves that line out. */
    fun lineRange(text: CharSequence, selStart: Int, selEnd: Int): Pair<Int, Int> {
        val from = lineStartOf(text, selStart)
        var last = selEnd
        if (selEnd > selStart && selEnd > 0 && text[selEnd - 1] == '\n') last = selEnd - 1
        return from to lineEndOf(text, maxOf(last, selStart))
    }

    private fun leadingWhitespace(text: CharSequence, lineStart: Int, limit: Int = text.length): String {
        var p = lineStart
        while (p < limit && (text[p] == ' ' || text[p] == '\t')) p++
        return text.substring(lineStart, p)
    }

    /** The indentation a file already uses, falling back to the language's convention. */
    fun detectIndent(text: CharSequence, language: CodeLanguage): IndentStyle {
        var tabLines = 0
        var spaceLines = 0
        val deltas = IntArray(9)
        var previous = 0
        var line = 0
        var lines = 0
        while (line < text.length && lines < 5_000) {
            val end = text.lineEndFrom(line, text.length)
            var p = line
            if (p < end && text[p] == '\t') {
                tabLines++
            } else {
                while (p < end && text[p] == ' ') p++
                if (p < end && text[p] != '\r') {
                    val width = p - line
                    if (width > 0 && text[p] != '*') spaceLines++
                    val delta = abs(width - previous)
                    if (delta in 1..8 && text[p] != '*') deltas[delta]++
                    previous = width
                }
            }
            lines++
            line = end + 1
        }
        if (tabLines > spaceLines) return IndentStyle(useTabs = true, size = language.indentSize)
        if (spaceLines == 0) return IndentStyle.of(language)
        val size = listOf(language.indentSize, 2, 4, 8, 3).maxBy { deltas[it] }
        return IndentStyle(useTabs = false, size = if (deltas[size] == 0) language.indentSize else size)
    }

    // --- Smart typing ------------------------------------------------------------------------------

    /**
     * Rewrites one typed change: [inserted] replaced `text[start, end)` while the selection was
     * [selStart]..[selEnd]. Handles auto-indent on Enter, closing brackets and quotes, typing over
     * an existing closer, wrapping a selection, and deleting an empty pair or an indent level with
     * backspace. Returns the edit to make instead (relative to [text]), or null to keep it as typed.
     */
    fun smartType(
        text: CharSequence,
        start: Int,
        end: Int,
        inserted: CharSequence,
        selStart: Int,
        selEnd: Int,
        language: CodeLanguage,
        indent: IndentStyle,
    ): CodeEdit? {
        if (inserted.length == 1 && inserted[0] == '\n') return newline(text, start, end, language, indent)
        if (inserted.length == 1) return typedChar(text, start, end, inserted[0], selStart, selEnd, language)
        if (inserted.isEmpty() && end == start + 1 && selStart == end && selEnd == end) return backspace(text, start, indent, language)
        return null
    }

    private fun typedChar(text: CharSequence, start: Int, end: Int, c: Char, selStart: Int, selEnd: Int, language: CodeLanguage): CodeEdit? {
        val isQuote = c in language.autoCloseQuotes
        val opener = c in Openers && language.lexer != null
        if (end > start) {
            // Typing a bracket or quote over a selection wraps it.
            if ((opener || isQuote) && selStart == start && selEnd == end) {
                val close = if (opener) Closers[Openers.indexOf(c)] else c
                return CodeEdit(start, end, "$c${text.subSequence(start, end)}$close", start + 1, end + 1)
            }
            return null
        }
        val next = if (start < text.length) text[start] else '\n'
        val previous = if (start > 0) text[start - 1] else '\n'
        if ((c in Closers || isQuote) && next == c && previous != '\\' && language.lexer != null) {
            return CodeEdit(start, start, "", start + 1)
        }
        if (opener && (next.isWhitespace() || next in ")]};,:")) {
            return CodeEdit(start, start, "$c${Closers[Openers.indexOf(c)]}", start + 1)
        }
        if (isQuote && !isIdentPart(previous) && previous != c && previous != '\\' && (next.isWhitespace() || next in ")]};,:")) {
            return CodeEdit(start, start, "$c$c", start + 1)
        }
        if (c in Closers && language.blockOpeners.isNotEmpty()) return outdentCloser(text, start, c)
        return null
    }

    /** Typing `}` on a line holding only indentation lines it up with its opening line. */
    private fun outdentCloser(text: CharSequence, caret: Int, closer: Char): CodeEdit? {
        val lineStart = lineStartOf(text, caret)
        if (caret == lineStart) return null
        for (p in lineStart until caret) if (text[p] != ' ' && text[p] != '\t') return null
        val opener = Openers[Closers.indexOf(closer)]
        var depth = 0
        var p = lineStart - 1
        val limit = maxOf(0, lineStart - 100_000)
        while (p >= limit) {
            val ch = text[p]
            if (ch == closer) depth++
            if (ch == opener) {
                if (depth == 0) {
                    val target = leadingWhitespace(text, lineStartOf(text, p))
                    if (target == text.substring(lineStart, caret)) return null
                    return CodeEdit(lineStart, caret, target + closer, lineStart + target.length + 1)
                }
                depth--
            }
            p--
        }
        return null
    }

    private fun backspace(text: CharSequence, start: Int, indent: IndentStyle, language: CodeLanguage): CodeEdit? {
        val deleted = text[start]
        val next = if (start + 1 < text.length) text[start + 1] else '\u0000'
        val pairs = (deleted in Openers && next == Closers[Openers.indexOf(deleted)]) ||
            (deleted in language.autoCloseQuotes && next == deleted)
        if (pairs && language.lexer != null) return CodeEdit(start, start + 2, "", start)
        // Backspace in space indentation removes back to the previous indent stop.
        if (deleted != ' ' || indent.useTabs || indent.size < 2) return null
        val lineStart = lineStartOf(text, start)
        for (p in lineStart..start) if (text[p] != ' ') return null
        val width = start + 1 - lineStart
        val target = ((width - 1) / indent.size) * indent.size
        if (target >= width - 1) return null
        return CodeEdit(lineStart + target, start + 1, "", lineStart + target)
    }

    private fun newline(text: CharSequence, start: Int, end: Int, language: CodeLanguage, indent: IndentStyle): CodeEdit? {
        val lineStart = lineStartOf(text, start)
        val leading = leadingWhitespace(text, lineStart, start)
        if (language.continuesLists) listItem(text, lineStart, start, end, leading)?.let { return it }
        if (language.blockComment?.first == "/*") docComment(text, lineStart, start, end, leading)?.let { return it }
        var before = start - 1
        while (before >= lineStart && (text[before] == ' ' || text[before] == '\t')) before--
        val previous = if (before >= lineStart) text[before] else '\u0000'
        val next = if (end < text.length) text[end] else '\u0000'
        val opens = previous != '\u0000' && previous in language.blockOpeners
        if (opens && previous in Openers && next == Closers[Openers.indexOf(previous)]) {
            val inner = leading + indent.unit
            return CodeEdit(start, end, "\n$inner\n$leading", start + 1 + inner.length)
        }
        val newIndent = if (opens) leading + indent.unit else leading
        if (newIndent.isEmpty()) return null
        return CodeEdit(start, end, "\n$newIndent", start + 1 + newIndent.length)
    }

    private val ListItem = Regex("^(\\s*)([-*+]|(\\d{1,9})([.)]))(\\s+)(\\[[ xX]]\\s+)?")

    /** Enter in a Markdown list starts the next item, or ends the list on an empty item. */
    private fun listItem(text: CharSequence, lineStart: Int, start: Int, end: Int, leading: String): CodeEdit? {
        val before = text.substring(lineStart, start)
        val match = ListItem.find(before) ?: return null
        val rest = before.substring(match.range.last + 1)
        if (rest.isBlank() && lineEndOf(text, end) == end) return CodeEdit(lineStart, end, "", lineStart)
        val number = match.groupValues[3]
        val marker = if (number.isNotEmpty()) "${number.toLong() + 1}${match.groupValues[4]}" else match.groupValues[2]
        val task = if (match.groupValues[6].isNotEmpty()) "[ ] " else ""
        val insert = "\n$leading$marker${match.groupValues[5]}$task"
        return CodeEdit(start, end, insert, start + insert.length)
    }

    /** Enter inside a `/** ... */` comment continues it with ` * `. */
    private fun docComment(text: CharSequence, lineStart: Int, start: Int, end: Int, leading: String): CodeEdit? {
        val before = text.substring(lineStart + leading.length, start)
        val prefix = when {
            before.startsWith("/**") && !before.contains("*/") -> " * "
            before.startsWith("*") && !before.startsWith("*/") && !before.contains("*/") && insideBlockComment(text, lineStart) -> "* "
            else -> return null
        }
        val insert = "\n$leading$prefix"
        return CodeEdit(start, end, insert, start + insert.length)
    }

    private fun insideBlockComment(text: CharSequence, before: Int): Boolean {
        val from = maxOf(0, before - 20_000)
        val window = text.subSequence(from, before).toString()
        return window.lastIndexOf("/*") > window.lastIndexOf("*/")
    }

    // --- Commands ------------------------------------------------------------------------------

    /** Tab: indents every selected line, or inserts one indent (to the next stop) at the caret. */
    fun indent(text: CharSequence, selStart: Int, selEnd: Int, indent: IndentStyle): CodeEdit {
        if (selStart == selEnd || !text.subSequence(selStart, selEnd).contains('\n')) {
            val column = selStart - lineStartOf(text, selStart)
            val unit = if (indent.useTabs) "\t" else " ".repeat(indent.size - column % indent.size)
            return CodeEdit(selStart, selEnd, unit, selStart + unit.length)
        }
        val (from, to) = lineRange(text, selStart, selEnd)
        val lines = text.substring(from, to).split('\n')
        val result = lines.joinToString("\n") { if (it.isBlank()) it else indent.unit + it }
        val firstShift = if (lines.first().isBlank() || selStart == from) 0 else indent.unit.length
        return CodeEdit(from, to, result, selStart + firstShift, selEnd + result.length - (to - from))
    }

    /** Shift+Tab: removes one indent level from every selected line (or the caret's line). */
    fun outdent(text: CharSequence, selStart: Int, selEnd: Int, indent: IndentStyle): CodeEdit? {
        val (from, to) = lineRange(text, selStart, selEnd)
        val lines = text.substring(from, to).split('\n')
        var firstRemoved = 0
        val result = lines.mapIndexed { index, line ->
            val remove = when {
                line.startsWith('\t') -> 1
                else -> line.takeWhile { it == ' ' }.length.coerceAtMost(indent.size)
            }
            if (index == 0) firstRemoved = remove
            line.substring(remove)
        }.joinToString("\n")
        if (result.length == to - from) return null
        val newStart = maxOf(from, selStart - firstRemoved)
        val newEnd = maxOf(newStart, selEnd - (to - from - result.length))
        return CodeEdit(from, to, result, newStart, newEnd)
    }

    /** Comments the selected lines out with the language's line comment, or back in when all are. */
    fun toggleComment(text: CharSequence, selStart: Int, selEnd: Int, language: CodeLanguage): CodeEdit? {
        val marker = language.lineComment
        val (from, to) = lineRange(text, selStart, selEnd)
        if (marker == null) return toggleBlockComment(text, selStart, selEnd, from, to, language)
        val lines = text.substring(from, to).split('\n')
        val content = lines.filter { it.isNotBlank() }
        if (content.isEmpty()) {
            val insert = "$marker "
            return CodeEdit(selStart, selStart, insert, selStart + insert.length)
        }
        val commented = content.all { it.trimStart().startsWith(marker) }
        val result = if (commented) {
            lines.map { line ->
                val at = line.indexOf(marker)
                if (line.isBlank() || at < 0) {
                    line
                } else {
                    val after = at + marker.length
                    line.substring(0, at) + line.substring(if (after < line.length && line[after] == ' ') after + 1 else after)
                }
            }
        } else {
            val column = content.minOf { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
            lines.map { if (it.isBlank()) it else it.substring(0, column) + marker + " " + it.substring(column) }
        }.joinToString("\n")
        return reselect(from, to, result, lines.first(), result.substringBefore('\n'), selStart, selEnd)
    }

    private fun toggleBlockComment(text: CharSequence, selStart: Int, selEnd: Int, from: Int, to: Int, language: CodeLanguage): CodeEdit? {
        val (open, close) = language.blockComment ?: return null
        var a = if (selStart != selEnd) selStart else from
        var b = if (selStart != selEnd) selEnd else to
        if (selStart == selEnd) {
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
        }
        val target = text.substring(a, b)
        val result = if (target.startsWith(open) && target.endsWith(close) && target.length >= open.length + close.length) {
            target.substring(open.length, target.length - close.length).removePrefix(" ").removeSuffix(" ")
        } else {
            "$open $target $close"
        }
        return if (selStart != selEnd) CodeEdit(a, b, result, a, a + result.length) else CodeEdit(a, b, result, a + result.length)
    }

    /** Keeps a caret in place across a line rewrite, or selects all rewritten lines. */
    private fun reselect(from: Int, to: Int, result: String, oldFirst: String, newFirst: String, selStart: Int, selEnd: Int): CodeEdit {
        if (selStart != selEnd) return CodeEdit(from, to, result, from, from + result.length)
        val caret = (selStart + newFirst.length - oldFirst.length).coerceIn(from, from + newFirst.length)
        return CodeEdit(from, to, result, caret)
    }

    fun duplicateLines(text: CharSequence, selStart: Int, selEnd: Int): CodeEdit {
        val (from, to) = lineRange(text, selStart, selEnd)
        val block = text.substring(from, to)
        val shift = block.length + 1
        return CodeEdit(to, to, "\n$block", selStart + shift, selEnd + shift)
    }

    fun deleteLines(text: CharSequence, selStart: Int, selEnd: Int): CodeEdit {
        val (from, to) = lineRange(text, selStart, selEnd)
        return when {
            to < text.length -> CodeEdit(from, to + 1, "", from)
            from > 0 -> CodeEdit(from - 1, to, "", lineStartOf(text, from - 1))
            else -> CodeEdit(from, to, "", from)
        }
    }

    fun moveLines(text: CharSequence, selStart: Int, selEnd: Int, down: Boolean): CodeEdit? {
        val (from, to) = lineRange(text, selStart, selEnd)
        val block = text.substring(from, to)
        if (!down) {
            if (from == 0) return null
            val previousStart = lineStartOf(text, from - 1)
            val previous = text.substring(previousStart, from - 1)
            val shift = previous.length + 1
            return CodeEdit(previousStart, to, "$block\n$previous", selStart - shift, selEnd - shift)
        }
        if (to >= text.length) return null
        val nextEnd = lineEndOf(text, to + 1)
        val next = text.substring(to + 1, nextEnd)
        val shift = next.length + 1
        return CodeEdit(from, nextEnd, "$next\n$block", selStart + shift, selEnd + shift)
    }

    /**
     * The bracket next to [caret] (just before it, else just after) and its partner, skipping
     * brackets inside strings and comments when [tokens] are known. Null when there is no pair.
     */
    fun matchingBracket(text: CharSequence, caret: Int, tokens: TokenList?, limit: Int = 100_000): Pair<Int, Int>? {
        for (at in intArrayOf(caret - 1, caret)) {
            if (at < 0 || at >= text.length) continue
            val c = text[at]
            val open = Openers.indexOf(c)
            val close = Closers.indexOf(c)
            if (open < 0 && close < 0) continue
            if (isQuoted(tokens, at)) continue
            val forward = open >= 0
            val same = c
            val other = if (forward) Closers[open] else Openers[close]
            var depth = 0
            var p = at + if (forward) 1 else -1
            var steps = 0
            while (p in text.indices && steps < limit) {
                val ch = text[p]
                if ((ch == same || ch == other) && !isQuoted(tokens, p)) {
                    if (ch == same) {
                        depth++
                    } else if (depth == 0) {
                        return at to p
                    } else {
                        depth--
                    }
                }
                p += if (forward) 1 else -1
                steps++
            }
        }
        return null
    }

    private fun isQuoted(tokens: TokenList?, offset: Int): Boolean {
        val kind = tokens?.kindAt(offset) ?: return false
        return kind == TokenKind.STRING || kind == TokenKind.COMMENT || kind == TokenKind.REGEX || kind == TokenKind.ESCAPE
    }
}
