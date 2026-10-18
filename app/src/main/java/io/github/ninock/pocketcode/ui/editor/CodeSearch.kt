package io.github.ninock.pocketcode.ui.editor

import java.util.regex.PatternSyntaxException

data class SearchOptions(val matchCase: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)

/** Non-empty matches in ascending order, as start/end pairs. */
class SearchResult internal constructor(
    private val ranges: IntArray,
    val count: Int,
    /** Why the query can't be searched (a bad regex), or null. */
    val error: String?,
    /** More matches exist than were collected. */
    val truncated: Boolean,
) {
    fun start(index: Int): Int = ranges[index * 2]

    fun end(index: Int): Int = ranges[index * 2 + 1]

    /** Index of the first match ending after [offset], or [count] when there is none. */
    fun firstEndingAfter(offset: Int): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (end(mid) <= offset) low = mid + 1 else high = mid
        }
        return low
    }

    /** Number of matches starting before [offset]. */
    private fun startingBefore(offset: Int): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (start(mid) < offset) low = mid + 1 else high = mid
        }
        return low
    }

    /** The first match starting at or after [offset], wrapping around; -1 when there are none. */
    fun nextFrom(offset: Int): Int {
        if (count == 0) return -1
        val index = startingBefore(offset)
        return if (index < count) index else 0
    }

    /** The last match starting before [offset], wrapping around; -1 when there are none. */
    fun previousFrom(offset: Int): Int {
        if (count == 0) return -1
        val index = startingBefore(offset)
        return if (index > 0) index - 1 else count - 1
    }

    /** Index of the match exactly covering [start]..[end], or -1. */
    fun indexOf(start: Int, end: Int): Int {
        val index = firstEndingAfter(start)
        return if (index < count && start(index) == start && end(index) == end) index else -1
    }

    companion object {
        val Empty = SearchResult(IntArray(0), 0, null, false)
    }
}

/** Find and replace for the editor and viewer: plain text or regex, case and whole-word options. */
object CodeSearch {
    const val MaxMatches = 10_000

    private fun pattern(query: String, options: SearchOptions): Regex {
        var source = if (options.regex) query else Regex.escape(query)
        if (options.wholeWord) source = "(?<![\\p{L}\\p{N}_])(?:$source)(?![\\p{L}\\p{N}_])"
        val flags = buildSet {
            add(RegexOption.MULTILINE)
            if (!options.matchCase) add(RegexOption.IGNORE_CASE)
        }
        return Regex(source, flags)
    }

    fun findAll(text: CharSequence, query: String, options: SearchOptions, limit: Int = MaxMatches): SearchResult {
        if (query.isEmpty()) return SearchResult.Empty
        val regex = try {
            pattern(query, options)
        } catch (e: PatternSyntaxException) {
            return SearchResult(IntArray(0), 0, e.description ?: "Invalid pattern", false)
        } catch (e: IllegalArgumentException) {
            return SearchResult(IntArray(0), 0, e.message ?: "Invalid pattern", false)
        }
        var ranges = IntArray(64)
        var count = 0
        var truncated = false
        try {
            for (match in regex.findAll(text)) {
                if (match.range.isEmpty()) continue
                if (count == limit) {
                    truncated = true
                    break
                }
                if (count * 2 + 2 > ranges.size) ranges = ranges.copyOf(ranges.size * 2)
                ranges[count * 2] = match.range.first
                ranges[count * 2 + 1] = match.range.last + 1
                count++
            }
        } catch (e: StackOverflowError) {
            return SearchResult(IntArray(0), 0, "Pattern is too complex", false)
        }
        return SearchResult(ranges, count, null, truncated)
    }

    /** The text that replaces the match at [start]..[end]: `$1` groups and `\n`/`\t` expand in regex mode. */
    fun replacementFor(text: CharSequence, start: Int, end: Int, query: String, replacement: String, options: SearchOptions): String {
        if (!options.regex) return replacement
        val match = runCatching { pattern(query, options).matchAt(text, start) }.getOrNull()
        if (match == null || match.range.last + 1 != end) return replacement
        return expand(replacement, match)
    }

    /** Replaces every match in one edit (a single undo step); null when nothing matches. */
    fun replaceAll(text: CharSequence, query: String, replacement: String, options: SearchOptions): Pair<CodeEdit, Int>? {
        val result = findAll(text, query, options, limit = Int.MAX_VALUE)
        if (result.count == 0) return null
        val regex = if (options.regex) pattern(query, options) else null
        val first = result.start(0)
        val last = result.end(result.count - 1)
        val out = StringBuilder()
        var cursor = first
        for (i in 0 until result.count) {
            out.append(text, cursor, result.start(i))
            val match = regex?.matchAt(text, result.start(i))
            out.append(if (match != null) expand(replacement, match) else replacement)
            cursor = result.end(i)
        }
        val replaced = out.toString()
        return CodeEdit(first, last, replaced, first + replaced.length) to result.count
    }

    internal fun expand(template: String, match: MatchResult): String {
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            val next = if (i + 1 < template.length) template[i + 1] else '\u0000'
            when {
                c == '$' && next == '$' -> {
                    out.append('$')
                    i += 2
                }
                c == '$' && next.isDigit() -> {
                    val group = next - '0'
                    if (group < match.groupValues.size) out.append(match.groupValues[group])
                    i += 2
                }
                c == '\\' && next == 'n' -> {
                    out.append('\n')
                    i += 2
                }
                c == '\\' && next == 't' -> {
                    out.append('\t')
                    i += 2
                }
                c == '\\' && next == '\\' -> {
                    out.append('\\')
                    i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
