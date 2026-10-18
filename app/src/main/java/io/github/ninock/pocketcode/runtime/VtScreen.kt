package io.github.ninock.pocketcode.runtime

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Minimal VT100/xterm screen model for reading what a TUI actually shows.
 *
 * Modern TUIs (including Claude Code) redraw only the cells that changed and
 * move the cursor with escape sequences, so stripping escapes from the raw
 * byte stream glues unrelated text together or drops characters. Replaying the
 * stream onto a grid reproduces the screen a person would see.
 */
internal class VtScreen(private val rows: Int, private val columns: Int, private val maxScrollback: Int = 400) {
    private val grid = Array(rows) { CharArray(columns) { ' ' } }
    private val scrollback = ArrayDeque<String>()
    private var row = 0
    private var col = 0
    private var savedRow = 0
    private var savedCol = 0
    private var wrapPending = false
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private enum class State { GROUND, ESCAPE, CSI, OSC, OSC_ESCAPE, STRING, STRING_ESCAPE, CHARSET }
    private var state = State.GROUND
    private val params = StringBuilder()

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pendingBytes = ByteArray(0)

    fun feed(bytes: ByteArray, count: Int = bytes.size) {
        val input = ByteBuffer.wrap(pendingBytes + bytes.copyOf(count))
        val output = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, output, false)
        pendingBytes = ByteArray(input.remaining()).also { input.get(it) }
        output.flip()
        while (output.hasRemaining()) process(output.get())
    }

    fun feed(text: String) = text.forEach(::process)

    /** Scrollback followed by the visible screen, one entry per row, trailing blanks trimmed. */
    fun lines(): List<String> = scrollback.toList() + grid.map { String(it).trimEnd() }

    fun text(): String = lines().joinToString("\n")

    private fun process(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESCAPE -> escape(c)
            State.CSI -> {
                if (c in '@'..'~') {
                    csi(c, params.toString())
                    state = State.GROUND
                } else if (params.length < 64) {
                    params.append(c)
                }
            }
            State.OSC -> when (c) {
                '\u0007' -> state = State.GROUND
                '\u001B' -> state = State.OSC_ESCAPE
                else -> Unit
            }
            State.OSC_ESCAPE -> state = if (c == '\\') State.GROUND else State.OSC
            State.STRING -> if (c == '\u001B') state = State.STRING_ESCAPE
            State.STRING_ESCAPE -> state = if (c == '\\') State.GROUND else State.STRING
            State.CHARSET -> state = State.GROUND
        }
    }

    private fun ground(c: Char) {
        when (c) {
            '\u001B' -> state = State.ESCAPE
            '\r' -> { col = 0; wrapPending = false }
            '\n', '\u000B', '\u000C' -> { lineFeed(); wrapPending = false }
            '\b' -> { if (col > 0) col--; wrapPending = false }
            '\t' -> col = minOf(columns - 1, (col / 8 + 1) * 8)
            else -> if (c.code >= 0x20 && c != '\u007F') put(c)
        }
    }

    private fun put(c: Char) {
        if (wrapPending) {
            col = 0
            lineFeed()
            wrapPending = false
        }
        grid[row][col] = c
        if (col == columns - 1) wrapPending = true else col++
    }

    private fun escape(c: Char) {
        state = State.GROUND
        when (c) {
            '[' -> { params.setLength(0); state = State.CSI }
            ']' -> state = State.OSC
            'P', 'X', '^', '_' -> state = State.STRING
            '(', ')', '*', '+' -> state = State.CHARSET
            '7' -> { savedRow = row; savedCol = col }
            '8' -> { row = savedRow; col = savedCol; wrapPending = false }
            'D' -> lineFeed()
            'E' -> { col = 0; lineFeed() }
            'M' -> reverseIndex()
            'c' -> { clearAll(); row = 0; col = 0 }
            else -> Unit
        }
    }

    private fun csi(final: Char, raw: String) {
        val private = raw.startsWith('?') || raw.startsWith('>') || raw.startsWith('<') || raw.startsWith('=')
        val values = raw.trimStart('?', '>', '<', '=').takeWhile { it.isDigit() || it == ';' || it == ':' }
            .split(';').map { it.substringBefore(':').toIntOrNull() }
        fun arg(index: Int, default: Int) = values.getOrNull(index)?.takeIf { it > 0 } ?: default
        fun argOrZero(index: Int) = values.getOrNull(index) ?: 0
        if (private && final != 'J' && final != 'K') return
        wrapPending = false
        when (final) {
            'A' -> row = maxOf(0, row - arg(0, 1))
            'B' -> row = minOf(rows - 1, row + arg(0, 1))
            'C' -> col = minOf(columns - 1, col + arg(0, 1))
            'D' -> col = maxOf(0, col - arg(0, 1))
            'E' -> { row = minOf(rows - 1, row + arg(0, 1)); col = 0 }
            'F' -> { row = maxOf(0, row - arg(0, 1)); col = 0 }
            'G', '`' -> col = (arg(0, 1) - 1).coerceIn(0, columns - 1)
            'd' -> row = (arg(0, 1) - 1).coerceIn(0, rows - 1)
            'H', 'f' -> {
                row = (arg(0, 1) - 1).coerceIn(0, rows - 1)
                col = (arg(1, 1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> when (argOrZero(0)) {
                0 -> { clearRow(row, col, columns); for (r in row + 1 until rows) clearRow(r, 0, columns) }
                1 -> { for (r in 0 until row) clearRow(r, 0, columns); clearRow(row, 0, col + 1) }
                2 -> clearAll()
                3 -> scrollback.clear()
            }
            'K' -> when (argOrZero(0)) {
                0 -> clearRow(row, col, columns)
                1 -> clearRow(row, 0, col + 1)
                2 -> clearRow(row, 0, columns)
            }
            'X' -> clearRow(row, col, minOf(columns, col + arg(0, 1)))
            'P' -> {
                val n = minOf(arg(0, 1), columns - col)
                val line = grid[row]
                System.arraycopy(line, col + n, line, col, columns - col - n)
                line.fill(' ', columns - n, columns)
            }
            '@' -> {
                val n = minOf(arg(0, 1), columns - col)
                val line = grid[row]
                System.arraycopy(line, col, line, col + n, columns - col - n)
                line.fill(' ', col, col + n)
            }
            'L' -> repeat(arg(0, 1)) { insertLine() }
            'M' -> repeat(arg(0, 1)) { deleteLine() }
            'S' -> repeat(arg(0, 1)) { scrollUp() }
            'T' -> repeat(arg(0, 1)) { scrollDown() }
            'r' -> {
                scrollTop = (arg(0, 1) - 1).coerceIn(0, rows - 1)
                scrollBottom = (arg(1, rows) - 1).coerceIn(scrollTop, rows - 1)
                row = 0
                col = 0
            }
            's' -> { savedRow = row; savedCol = col }
            'u' -> { row = savedRow; col = savedCol }
            else -> Unit // SGR colors, modes and queries do not change text.
        }
    }

    private fun lineFeed() {
        if (row == scrollBottom) scrollUp() else if (row < rows - 1) row++
    }

    private fun reverseIndex() {
        if (row == scrollTop) scrollDown() else if (row > 0) row--
    }

    private fun scrollUp() {
        if (scrollTop == 0) {
            scrollback.addLast(String(grid[0]).trimEnd())
            while (scrollback.size > maxScrollback) scrollback.removeFirst()
        }
        for (r in scrollTop until scrollBottom) grid[r] = grid[r + 1]
        grid[scrollBottom] = CharArray(columns) { ' ' }
    }

    private fun scrollDown() {
        for (r in scrollBottom downTo scrollTop + 1) grid[r] = grid[r - 1]
        grid[scrollTop] = CharArray(columns) { ' ' }
    }

    private fun insertLine() {
        if (row !in scrollTop..scrollBottom) return
        for (r in scrollBottom downTo row + 1) grid[r] = grid[r - 1]
        grid[row] = CharArray(columns) { ' ' }
    }

    private fun deleteLine() {
        if (row !in scrollTop..scrollBottom) return
        for (r in row until scrollBottom) grid[r] = grid[r + 1]
        grid[scrollBottom] = CharArray(columns) { ' ' }
    }

    private fun clearRow(r: Int, from: Int, to: Int) {
        if (from < to) grid[r].fill(' ', from.coerceIn(0, columns), to.coerceIn(0, columns))
    }

    private fun clearAll() {
        for (r in 0 until rows) grid[r].fill(' ')
    }
}
