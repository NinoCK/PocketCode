package io.github.ninock.pocketcode.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Above this size the editor skips syntax colours: lexing on every keystroke would lag. */
internal const val MaxHighlightChars = 200_000

/**
 * Above this size only the text around the visible lines is coloured. The field lays out every
 * style again on each keystroke, at a cost that grows with styles times lines, so colouring a
 * whole large file makes typing crawl.
 */
internal const val FullStylingChars = 8_000

/** Characters coloured beyond each edge of the visible text in a large file. */
private const val StyleMargin = 3_000

/** Lines longer than this force word wrap; unwrapped they would be wider than a layout can measure. */
internal const val MaxUnwrappedLineLength = 2_000

/**
 * Code text for both the editor (one paragraph) and the viewer (one text per line). Every line
 * keeps its full line height, untrimmed, so rows are spaced the same in both.
 */
internal fun codeTextStyle(fontSize: Float, color: Color) = TextStyle(
    color = color,
    fontFamily = FontFamily.Monospace,
    fontSize = fontSize.sp,
    lineHeight = (fontSize * 1.5f).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Spacing shared by the editor and viewer so code sits in the same place in both. */
internal val GutterStart = 12.dp
internal val GutterEnd = 10.dp
internal val CodeStart = 8.dp
internal val CodeEnd = 16.dp
internal val CodeVerticalPadding = 12.dp

internal fun longestLine(text: CharSequence, tabSize: Int = 4): Int {
    var longest = 0
    var column = 0
    for (c in text) {
        when (c) {
            '\n' -> {
                if (column > longest) longest = column
                column = 0
            }
            '\t' -> column += tabSize - column % tabSize
            else -> column++
        }
    }
    return maxOf(longest, column)
}

/** Lexes the editor text once per change; the colours and bracket matching share the result. */
internal class HighlightCache(private val language: CodeLanguage) {
    private var text: String? = null
    private var tokens: TokenList? = null

    fun tokensFor(current: CharSequence): TokenList? {
        if (language.lexer == null || current.length > MaxHighlightChars) return null
        val cached = text
        if (cached != null && cached.length == current.length && cached.contentEquals(current)) return tokens
        val snapshot = current.toString()
        return highlight(snapshot, language).also {
            tokens = it
            text = snapshot
        }
    }
}

/**
 * Everything the editor screen drives besides the text: scroll positions, the latest layout, focus,
 * and the edit commands behind the key bar, menu and keyboard shortcuts.
 */
@Stable
internal class CodeEditorState(
    val text: TextFieldState,
    val language: CodeLanguage,
    val indent: IndentStyle,
) {
    val scroll = ScrollState(0)
    val horizontalScroll = ScrollState(0)
    val focusRequester = FocusRequester()
    var focused by mutableStateOf(false)
        internal set
    /**
     * Reads the text field's latest layout (a snapshot read, so callers redraw on relayout). Kept
     * out of snapshot state itself: the field hands over a getter on every pass, and storing each
     * one would invalidate every reader once per frame.
     */
    private var layoutGetter: (() -> TextLayoutResult?)? = null
    private var layoutAttached by mutableStateOf(false)
    internal var viewportHeight by mutableIntStateOf(0)
    internal var viewportWidth by mutableIntStateOf(0)
    internal val highlighter = HighlightCache(language)

    /** Text range coloured in a large file, kept a margin beyond the visible lines; null colours everything. */
    internal var styleWindow: IntRange? by mutableStateOf(null)
        private set

    /** The offsets of the visible lines, or null before the first layout. */
    internal fun visibleRange(): IntRange? {
        val layout = layout ?: return null
        val top = scroll.value.toFloat()
        val first = layout.getLineStart(layout.getLineForVerticalPosition(top))
        val last = layout.getLineEnd(layout.getLineForVerticalPosition(top + viewportHeight))
        return first..last
    }

    /** Moves [styleWindow] once the visible text gets near its edge (each move restyles the whole field). */
    internal fun followVisible(visible: IntRange) {
        val window = styleWindow
        val length = text.text.length
        if (length <= FullStylingChars) {
            if (window != null) styleWindow = null
            return
        }
        if (window != null) {
            val slack = StyleMargin / 3
            val topCovered = window.first == 0 || visible.first >= window.first + slack
            val bottomCovered = window.last >= length || visible.last <= window.last - slack
            if (topCovered && bottomCovered) return
        }
        val margin = maxOf(StyleMargin, visible.last - visible.first)
        styleWindow = (visible.first - margin).coerceAtLeast(0)..(visible.last + margin)
    }

    /** Lines too long to show unwrapped, checked when the editor opens. */
    val hasLongLines: Boolean = longestLine(text.text) > MaxUnwrappedLineLength

    val lines: LineIndex by derivedStateOf { LineIndex(text.text) }

    /** The bracket at the caret and its partner, for highlighting. */
    val bracketPair: Pair<Int, Int>? by derivedStateOf {
        val selection = text.selection
        if (!selection.collapsed) {
            null
        } else {
            val current = text.text
            CodeEditing.matchingBracket(current, selection.start, highlighter.tokensFor(current))
        }
    }

    val layout: TextLayoutResult? get() = if (layoutAttached) layoutGetter?.invoke() else null

    internal fun attachLayout(getter: () -> TextLayoutResult?) {
        layoutGetter = getter
        if (!layoutAttached) layoutAttached = true
    }

    fun apply(edit: CodeEdit) {
        text.edit {
            replace(edit.start, edit.end, edit.text)
            selection = TextRange(edit.selectionStart.coerceIn(0, length), edit.selectionEnd.coerceIn(0, length))
        }
    }

    /** Runs a [CodeEditing] command on the current text and selection. */
    fun run(command: (CharSequence, Int, Int) -> CodeEdit?) {
        val selection = text.selection
        command(text.text, selection.min, selection.max)?.let(::apply)
    }

    /** Inserts [symbol] from the key bar as if typed, with the same pairing and indenting. */
    fun type(symbol: String) {
        val selection = text.selection
        val current = text.text
        val smart = if (symbol.length == 1) {
            CodeEditing.smartType(current, selection.min, selection.max, symbol, selection.min, selection.max, language, indent)
        } else {
            null
        }
        apply(smart ?: CodeEdit(selection.min, selection.max, symbol, selection.min + symbol.length))
    }

    fun select(start: Int, end: Int = start) {
        text.edit { selection = TextRange(start.coerceIn(0, length), end.coerceIn(0, length)) }
    }

    private var stickyX = 0f
    private var stickyOffset = -1

    /** Moves the caret a character left or right, or a visual line up or down keeping its column. */
    fun moveCaret(dx: Int, dy: Int) {
        val selection = text.selection
        val target = when {
            dx < 0 -> if (selection.collapsed) selection.start - 1 else selection.min
            dx > 0 -> if (selection.collapsed) selection.start + 1 else selection.max
            else -> verticalTarget(selection.start, dy)
        }.coerceIn(0, text.text.length)
        select(target)
        stickyOffset = if (dy != 0) target else -1
    }

    private fun verticalTarget(offset: Int, dy: Int): Int {
        val layout = layout
        if (layout == null || layout.layoutInput.text.length != text.text.length) {
            val lines = lines
            val line = lines.lineOf(offset)
            val target = line + dy
            if (target < 0) return 0
            if (target >= lines.lineCount) return text.text.length
            return minOf(lines.startOf(target) + (offset - lines.startOf(line)), lines.endOf(target))
        }
        val line = layout.getLineForOffset(offset)
        val target = line + dy
        if (target < 0) return 0
        if (target >= layout.lineCount) return text.text.length
        if (stickyOffset != offset) stickyX = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
        val y = (layout.getLineTop(target) + layout.getLineBottom(target)) / 2
        return layout.getOffsetForPosition(Offset(stickyX, y))
    }

    /** Scrolls [offset] into view, a third of the way down the editor when it was off screen. */
    suspend fun reveal(offset: Int, wrap: Boolean) {
        val layout = layout ?: return
        val at = offset.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(at)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val viewport = viewportHeight
        if (viewport > 0 && (top < scroll.value || bottom > scroll.value + viewport)) {
            scroll.animateScrollTo((top - viewport / 3f).roundToInt().coerceIn(0, scroll.maxValue))
        }
        if (!wrap && viewportWidth > 0) {
            val x = layout.getHorizontalPosition(at, usePrimaryDirection = true)
            val margin = viewportWidth / 6
            if (x < horizontalScroll.value + margin || x > horizontalScroll.value + viewportWidth - margin) {
                horizontalScroll.animateScrollTo((x - viewportWidth / 3f).roundToInt().coerceIn(0, horizontalScroll.maxValue))
            }
        }
    }

    /** Hardware keyboard shortcuts; true when the key was handled. */
    fun onKey(event: KeyEvent, onSave: () -> Unit, onFind: (replace: Boolean) -> Unit, onGoToLine: () -> Unit): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        when {
            event.key == Key.Tab && !ctrl && !event.isAltPressed ->
                if (event.isShiftPressed) run { t, s, e -> CodeEditing.outdent(t, s, e, indent) } else run { t, s, e -> CodeEditing.indent(t, s, e, indent) }
            ctrl && event.key == Key.S -> onSave()
            ctrl && event.key == Key.F -> onFind(false)
            ctrl && event.key == Key.H -> onFind(true)
            ctrl && event.key == Key.G -> onGoToLine()
            ctrl && event.key == Key.Slash -> run { t, s, e -> CodeEditing.toggleComment(t, s, e, language) }
            ctrl && event.key == Key.D -> run(CodeEditing::duplicateLines)
            ctrl && event.isShiftPressed && event.key == Key.K -> run(CodeEditing::deleteLines)
            event.isAltPressed && event.key == Key.DirectionUp -> run { t, s, e -> CodeEditing.moveLines(t, s, e, down = false) }
            event.isAltPressed && event.key == Key.DirectionDown -> run { t, s, e -> CodeEditing.moveLines(t, s, e, down = true) }
            else -> return false
        }
        return true
    }
}

/** Auto-indent, bracket and quote pairing for what the user types (IME or hardware keyboard). */
@OptIn(ExperimentalFoundationApi::class)
private class SmartInput(private val state: CodeEditorState) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val (start, end, inserted) = typedChange() ?: return
        val before = originalSelection
        val edit = CodeEditing.smartType(
            originalText, start, end, inserted, before.min, before.max, state.language, state.indent,
        ) ?: return
        revertAllChanges()
        replace(edit.start, edit.end, edit.text)
        selection = TextRange(edit.selectionStart, edit.selectionEnd)
    }

    /**
     * The one real change in this edit as (start, end, inserted) in the original text, or null.
     * Keyboards commit the word being composed together with the next key, so a composing word
     * re-committed unchanged is skipped, and "word" becoming "word(" counts as typing "(".
     */
    private fun TextFieldBuffer.typedChange(): Triple<Int, Int, CharSequence>? {
        var found: Triple<Int, Int, CharSequence>? = null
        for (i in 0 until changes.changeCount) {
            val range = changes.getRange(i)
            val original = changes.getOriginalRange(i)
            val inserted = asCharSequence().subSequence(range.min, range.max)
            val replaced = originalText.subSequence(original.min, original.max)
            if (inserted.contentEquals(replaced)) continue
            if (found != null) return null
            found = if (replaced.isNotEmpty() && inserted.length == replaced.length + 1 && inserted.startsWith(replaced)) {
                Triple(original.max, original.max, inserted.subSequence(replaced.length, inserted.length))
            } else {
                Triple(original.min, original.max, inserted)
            }
        }
        return found
    }
}

/** Colours the displayed text (around the visible lines in a large file); the stored text is never changed. */
private class SyntaxColors(private val state: CodeEditorState, private val theme: CodeTheme) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val tokens = state.highlighter.tokensFor(asCharSequence()) ?: return
        val window = if (length > FullStylingChars) state.styleWindow ?: 0..StyleMargin * 2 else null
        var i = if (window == null) 0 else tokens.firstEndingAfter(window.first)
        val stop = window?.last ?: Int.MAX_VALUE
        val text = asCharSequence()
        // Same-styled tokens with only whitespace between them share one style (`private val`,
        // consecutive comment lines): fewer styles make every relayout cheaper.
        var runStyle: SpanStyle? = null
        var runStart = 0
        var runEnd = 0
        while (i < tokens.size && tokens.start(i) < stop) {
            val style = theme.style(tokens.kind(i))
            if (style != null) {
                val start = tokens.start(i)
                if (style === runStyle && isBlank(text, runEnd, start)) {
                    runEnd = tokens.end(i)
                } else {
                    runStyle?.let { addStyle(it, runStart, runEnd) }
                    runStyle = style
                    runStart = start
                    runEnd = tokens.end(i)
                }
            }
            i++
        }
        runStyle?.let { addStyle(it, runStart, runEnd) }
    }

    private fun isBlank(text: CharSequence, from: Int, to: Int): Boolean {
        for (p in from until to) if (!text[p].isWhitespace()) return false
        return true
    }
}

/** Pinch to change the text size; fires [onChange] as the fingers move and [onCommit] when they lift. */
@Composable
internal fun Modifier.pinchToZoom(fontSize: Float, onChange: (Float) -> Unit, onCommit: (Float) -> Unit): Modifier {
    val size by rememberUpdatedState(fontSize)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val base = size
            var scale = 1f
            var current = base
            var zoomed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoomed = true
                    scale *= event.calculateZoom()
                    val next = ((base * scale).coerceIn(EditorPreferences.MinFontSize, EditorPreferences.MaxFontSize) * 2).roundToInt() / 2f
                    if (next != current) {
                        current = next
                        change(next)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (zoomed) commit(current)
        }
    }
}

/**
 * The code editor: a line-number gutter beside a text field that colours syntax, highlights the
 * caret's line, its bracket pair and search matches, indents and pairs brackets as you type, and
 * handles keyboard shortcuts. Text and undo history live in [CodeEditorState.text].
 */
@Composable
internal fun CodeEditor(
    state: CodeEditorState,
    preferences: EditorPreferences,
    find: FindState,
    onPreferencesChange: (EditorPreferences) -> Unit,
    onSave: () -> Unit,
    onFind: (replace: Boolean) -> Unit,
    onGoToLine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = rememberCodeTheme()
    val density = LocalDensity.current
    val wrap = preferences.wordWrap || state.hasLongLines
    var fontSize by remember { mutableFloatStateOf(preferences.fontSize) }
    LaunchedEffect(preferences.fontSize) { fontSize = preferences.fontSize }
    val textStyle = codeTextStyle(fontSize, MaterialTheme.colorScheme.onSurface)
    LaunchedEffect(state) { state.focusRequester.requestFocus() }
    LaunchedEffect(state) {
        snapshotFlow { state.visibleRange() }.collect { visible -> if (visible != null) state.followVisible(visible) }
    }

    Row(
        modifier
            .fillMaxSize()
            .pinchToZoom(fontSize, onChange = { fontSize = it }, onCommit = { onPreferencesChange(preferences.copy(fontSize = it)) }),
    ) {
        LineNumbers(state, textStyle, theme, Modifier.fillMaxHeight())
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { state.viewportWidth = it.width }
                .then(if (wrap) Modifier else Modifier.horizontalScroll(state.horizontalScroll)),
        ) {
            BasicTextField(
                state = state.text,
                modifier = Modifier
                    .then(if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = with(density) { state.viewportWidth.toDp() }))
                    .fillMaxHeight()
                    .focusRequester(state.focusRequester)
                    .onFocusChanged { state.focused = it.isFocused }
                    .onPreviewKeyEvent { state.onKey(it, onSave, onFind, onGoToLine) },
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                inputTransformation = remember(state) { SmartInput(state) },
                outputTransformation = remember(state, theme) { SyntaxColors(state, theme) },
                onTextLayout = { getResult -> state.attachLayout(getResult) },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                scrollState = state.scroll,
                decorator = { innerTextField ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { state.viewportHeight = it.height }
                            .drawBehind { drawHighlights(state, find, theme, CodeStart.toPx()) }
                            .padding(start = CodeStart, end = CodeEnd),
                    ) { innerTextField() }
                },
            )
        }
    }
}

/** Caret line, search matches and the bracket pair, drawn under the text. */
private fun DrawScope.drawHighlights(state: CodeEditorState, find: FindState, theme: CodeTheme, textLeft: Float) {
    val layout = state.layout ?: return
    val length = layout.layoutInput.text.length
    val scroll = state.scroll.value.toFloat()
    clipRect {
        currentLineBand(state, layout, scroll)?.let { (top, bottom) ->
            drawRect(theme.lineHighlight, Offset(0f, top), Size(size.width, bottom - top))
        }
        translate(textLeft, -scroll) {
            val result = find.result
            if (find.open && result.count > 0) {
                val from = layout.getLineStart(layout.getLineForVerticalPosition(scroll))
                val to = layout.getLineEnd(layout.getLineForVerticalPosition(scroll + size.height))
                var i = result.firstEndingAfter(from)
                while (i < result.count && result.start(i) < to) {
                    val start = result.start(i).coerceAtMost(length)
                    val end = result.end(i).coerceAtMost(length)
                    if (start < end) drawPath(layout.getPathForRange(start, end), if (i == find.current) theme.currentMatch else theme.match)
                    i++
                }
            }
            state.bracketPair?.let { (a, b) ->
                for (offset in intArrayOf(a, b)) {
                    if (offset >= length) continue
                    val box = layout.getBoundingBox(offset)
                    drawRoundRect(theme.bracket, box.topLeft, box.size, CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
}

/** Top and bottom (in viewport coordinates) of the caret's whole line, or null when not shown. */
private fun currentLineBand(state: CodeEditorState, layout: TextLayoutResult, scroll: Float): Pair<Float, Float>? {
    val selection = state.text.selection
    if (!state.focused || !selection.collapsed) return null
    val length = layout.layoutInput.text.length
    val lines = state.lines
    val line = lines.lineOf(selection.start)
    val start = lines.startOf(line).coerceIn(0, length)
    val end = lines.endOf(line).coerceIn(0, length)
    val top = layout.getLineTop(layout.getLineForOffset(start)) - scroll
    val bottom = layout.getLineBottom(layout.getLineForOffset(end)) - scroll
    return top to bottom
}

/** Line numbers that scroll with the text (only for the first row of a wrapped line); tap one to select its line. */
@Composable
private fun LineNumbers(state: CodeEditorState, textStyle: TextStyle, theme: CodeTheme, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val numberStyle = remember(textStyle) { textStyle.copy(fontSize = textStyle.fontSize * 0.85f) }
    val digits by remember(state) { derivedStateOf { maxOf(2, state.lines.lineCount.toString().length) } }
    val digitWidth = remember(numberStyle) { measurer.measure("0", numberStyle).size.width }
    val density = LocalDensity.current
    Canvas(
        modifier
            .width(with(density) { (digitWidth * digits).toDp() } + GutterStart + GutterEnd)
            .pointerInput(state) {
                detectTapGestures { tap ->
                    val layout = state.layout ?: return@detectTapGestures
                    val lines = state.lines
                    val visual = layout.getLineForVerticalPosition(tap.y + state.scroll.value)
                    val line = lines.lineOf(layout.getLineStart(visual).coerceAtMost(lines.length))
                    val end = if (line + 1 < lines.lineCount) lines.startOf(line + 1) else lines.endOf(line)
                    state.select(lines.startOf(line), end)
                    state.focusRequester.requestFocus()
                }
            },
    ) {
        val layout = state.layout ?: return@Canvas
        val text = layout.layoutInput.text
        val scroll = state.scroll.value.toFloat()
        val lines = state.lines
        val caretLine = lines.lineOf(state.text.selection.start)
        val right = size.width - GutterEnd.toPx()
        clipRect {
            currentLineBand(state, layout, scroll)?.let { (top, bottom) ->
                drawRect(theme.lineHighlight, Offset(0f, top), Size(size.width, bottom - top))
            }
            val first = layout.getLineForVerticalPosition(scroll)
            val last = layout.getLineForVerticalPosition(scroll + size.height)
            for (visual in first..last) {
                val start = layout.getLineStart(visual)
                if (start > 0 && (start > text.length || text[start - 1] != '\n')) continue
                val line = lines.lineOf(start)
                val label = measurer.measure((line + 1).toString(), numberStyle)
                drawText(
                    label,
                    color = if (line == caretLine && state.focused) theme.gutterActiveText else theme.gutterText,
                    topLeft = Offset(right - label.size.width, layout.getLineBaseline(visual) - scroll - label.firstBaseline),
                )
            }
        }
    }
}

