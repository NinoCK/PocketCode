package io.github.ninock.pocketcode.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TabSize = 4

/**
 * Read-only code view: one lazily composed row per line, so even the largest viewable files open
 * at once. Coloured in the background, with line numbers, search highlights, selectable text,
 * optional word wrap and pinch-to-zoom.
 */
@Composable
internal fun CodeViewer(
    content: String,
    language: CodeLanguage,
    lines: LineIndex,
    preferences: EditorPreferences,
    find: FindState,
    listState: LazyListState,
    flashLine: Int,
    onPreferencesChange: (EditorPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = rememberCodeTheme()
    val density = LocalDensity.current
    val longest = remember(content) { longestLine(content, TabSize) }
    val wrap = preferences.wordWrap || longest > MaxUnwrappedLineLength
    val tokens by produceState<TokenList?>(null, content, language) {
        value = null
        value = withContext(Dispatchers.Default) { highlight(content, language) }
    }
    var fontSize by remember { mutableFloatStateOf(preferences.fontSize) }
    LaunchedEffect(preferences.fontSize) { fontSize = preferences.fontSize }
    val style = codeTextStyle(fontSize, MaterialTheme.colorScheme.onSurface)
    val numberStyle = style.copy(fontSize = style.fontSize * 0.85f, color = theme.gutterText)
    val measurer = rememberTextMeasurer()
    val charWidth = remember(style) { measurer.measure("0", style).size.width }
    val digitWidth = remember(numberStyle) { measurer.measure("0", numberStyle).size.width }
    val numberWidth = with(density) { (digitWidth * maxOf(2, lines.lineCount.toString().length)).toDp() } + GutterStart + GutterEnd

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pinchToZoom(fontSize, onChange = { fontSize = it }, onCommit = { onPreferencesChange(preferences.copy(fontSize = it)) }),
    ) {
        val listModifier = if (wrap) {
            Modifier.fillMaxSize()
        } else {
            val contentWidth = numberWidth + CodeStart + with(density) { (charWidth * longest).toDp() } + CodeEnd
            Modifier.fillMaxHeight().width(contentWidth).widthIn(min = maxWidth)
        }
        Box(if (wrap) Modifier.fillMaxSize() else Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                LazyColumn(state = listState, modifier = listModifier, contentPadding = PaddingValues(vertical = CodeVerticalPadding)) {
                    items(lines.lineCount) { index ->
                        val text = remember(index, content, tokens, theme, find.open, find.result, find.current) {
                            styledLine(content, lines.startOf(index), lines.endOf(index), tokens, theme, find.takeIf { it.open })
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (index == flashLine) theme.flash else Color.Transparent),
                            verticalAlignment = Alignment.Top,
                        ) {
                            DisableSelection {
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier.width(numberWidth).padding(end = GutterEnd).alignByBaseline(),
                                    style = numberStyle,
                                    textAlign = TextAlign.End,
                                )
                            }
                            Text(
                                text,
                                modifier = Modifier.weight(1f).padding(start = CodeStart, end = CodeEnd).alignByBaseline(),
                                style = style,
                                softWrap = wrap,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One line with tabs expanded to spaces, coloured, and with its search matches highlighted. */
private fun styledLine(content: String, start: Int, end: Int, tokens: TokenList?, theme: CodeTheme, find: FindState?): AnnotatedString {
    var stop = end
    if (stop > start && content[stop - 1] == '\r') stop--
    val tab = content.indexOf('\t', start)
    val map = if (tab in start until stop) IntArray(stop - start + 1) else null
    val display = if (map == null) {
        content.substring(start, stop)
    } else {
        buildString {
            for (i in start until stop) {
                map[i - start] = length
                val c = content[i]
                if (c == '\t') repeat(TabSize - length % TabSize) { append(' ') } else append(c)
            }
            map[stop - start] = length
        }
    }

    fun at(offset: Int) = if (map == null) offset - start else map[offset - start]

    return buildAnnotatedString {
        append(display)
        if (tokens != null) {
            var i = tokens.firstEndingAfter(start)
            while (i < tokens.size && tokens.start(i) < stop) {
                val style = theme.style(tokens.kind(i))
                val s = maxOf(start, tokens.start(i))
                val e = minOf(stop, tokens.end(i))
                if (style != null && s < e) addStyle(style, at(s), at(e))
                i++
            }
        }
        if (find != null) {
            val result = find.result
            var i = result.firstEndingAfter(start)
            while (i < result.count && result.start(i) < stop) {
                val s = maxOf(start, result.start(i))
                val e = minOf(stop, result.end(i))
                if (s < e) addStyle(SpanStyle(background = if (i == find.current) theme.currentMatch else theme.match), at(s), at(e))
                i++
            }
        }
    }
}
