package com.jarves.mh.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Find & replace: the bar's fields and options, plus the matches in the open file. */
@Stable
internal class FindState {
    var open by mutableStateOf(false)
    var replaceOpen by mutableStateOf(false)
    val query = TextFieldState()
    val replacement = TextFieldState()
    var options by mutableStateOf(SearchOptions())
    var result by mutableStateOf(SearchResult.Empty)

    /** Index of the match last moved to, highlighted more strongly; -1 for none. */
    var current by mutableIntStateOf(-1)

    /** Bumped to put the keyboard focus in the query field. */
    var focusRequests by mutableIntStateOf(0)
        private set

    /** Opens the bar, starting from [seed] (the selected text) when it is a short single line. */
    fun show(replace: Boolean, seed: CharSequence?) {
        open = true
        if (replace) replaceOpen = true
        if (seed != null && seed.isNotEmpty() && seed.length <= 200 && !seed.contains('\n')) query.setTextAndSelectAll(seed.toString())
        focusRequests++
    }

    fun close() {
        open = false
        current = -1
    }
}

/** Search bar shown in place of the top app bar while finding. */
@Composable
internal fun FindBar(
    find: FindState,
    allowReplace: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(find.focusRequests) { focus.requestFocus() }
    val result = find.result
    val hasMatches = result.count > 0
    val count = when {
        find.query.text.isEmpty() -> null
        result.error != null -> "Error"
        result.count == 0 -> "0/0"
        find.current >= 0 -> "${find.current + 1}/${result.count}${if (result.truncated) "+" else ""}"
        else -> "${result.count}${if (result.truncated) "+" else ""}"
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close find") }
                SearchField(
                    state = find.query,
                    placeholder = "Find",
                    trailing = count,
                    error = result.error != null,
                    onSubmit = onNext,
                    onEscape = onClose,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                IconButton(onClick = onPrevious, enabled = hasMatches) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
                IconButton(onClick = onNext, enabled = hasMatches) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
                if (allowReplace) {
                    IconToggleButton(checked = find.replaceOpen, onCheckedChange = { find.replaceOpen = it }) {
                        Icon(Icons.Rounded.FindReplace, "Replace")
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionChip("Match case", find.options.matchCase) { find.options = find.options.copy(matchCase = it) }
                OptionChip("Whole word", find.options.wholeWord) { find.options = find.options.copy(wholeWord = it) }
                OptionChip("Regex", find.options.regex) { find.options = find.options.copy(regex = it) }
            }
            result.error?.let { error ->
                Text(
                    error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
            AnimatedVisibility(allowReplace && find.replaceOpen) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        state = find.replacement,
                        placeholder = "Replace with",
                        trailing = null,
                        error = false,
                        onSubmit = onReplace,
                        onEscape = onClose,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReplace, enabled = hasMatches) { Text("Replace") }
                    TextButton(onClick = onReplaceAll, enabled = hasMatches) { Text("All") }
                }
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onChange(!selected) }, label = { Text(label) })
}

@Composable
private fun SearchField(
    state: TextFieldState,
    placeholder: String,
    trailing: String?,
    error: Boolean,
    onSubmit: () -> Unit,
    onEscape: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerHigh,
        border = if (error) BorderStroke(1.dp, colors.error) else null,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                            onEscape()
                            true
                        } else {
                            false
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    onKeyboardAction = { onSubmit() },
                    cursorBrush = SolidColor(colors.primary),
                )
            }
            if (trailing != null) {
                Text(
                    trailing,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (error) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Symbols for the key bar, most useful first for each kind of file. */
private fun keySymbols(language: CodeLanguage): List<String> = when (language.id) {
    "python" -> ": ( ) [ ] { } = \" ' # _ . , < > + - * / % ! @ \\ | & ~"
    "markdown" -> "# * - _ ` [ ] ( ) > ! | ~ \\ . : < >"
    "html", "xml" -> "< > / = \" ' { } ( ) ! - & ; : # . _"
    "shell", "dockerfile", "makefile", "powershell" -> "$ { } ( ) [ ] \" ' | & ; > < - = * ~ / \\ # ! ? . _"
    "json" -> "{ } [ ] \" : , . -"
    "yaml", "toml", "ini", "env", "properties" -> ": - # \" ' [ ] { } = , . _ / $ & *"
    "text" -> ". , ? ! : ; - ' \" ( ) / @ # * _"
    else -> "{ } ( ) [ ] ; = \" ' < > / \\ | & ! ? : + - * % $ _ @ # ~ ^ , . `"
}.split(' ')

/**
 * Row above the keyboard while editing: undo and redo, then a scrolling strip of Tab, outdent,
 * comment, arrow keys (hold to repeat) and the symbols a phone keyboard hides.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorKeyBar(state: CodeEditorState, modifier: Modifier = Modifier) {
    val symbols = remember(state.language) { keySymbols(state.language) }
    val undo = state.text.undoState
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Key(onClick = { undo.undo() }, enabled = undo.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            Key(onClick = { undo.redo() }, enabled = undo.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Key(onClick = { state.run { t, s, e -> CodeEditing.indent(t, s, e, state.indent) } }) { KeyLabel("Tab") }
                Key(onClick = { state.run { t, s, e -> CodeEditing.outdent(t, s, e, state.indent) } }) {
                    Icon(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Outdent")
                }
                val comment = state.language.lineComment ?: state.language.blockComment?.first
                if (comment != null) {
                    Key(onClick = { state.run { t, s, e -> CodeEditing.toggleComment(t, s, e, state.language) } }) { KeyLabel(comment.take(2)) }
                }
                RepeatKey(onPress = { state.moveCaret(-1, 0) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Move left") }
                RepeatKey(onPress = { state.moveCaret(0, -1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                RepeatKey(onPress = { state.moveCaret(0, 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                RepeatKey(onPress = { state.moveCaret(1, 0) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Move right") }
                for (symbol in symbols) {
                    Key(onClick = { state.type(symbol) }) { KeyLabel(symbol) }
                }
            }
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 16.sp, maxLines = 1)
}

/** A key that acts on tap without taking focus from the editor (the keyboard stays up). */
@Composable
private fun Key(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    KeyBox(Modifier.clickable(enabled = enabled, onClick = onClick), enabled, content)
}

/** A key that repeats while held, like a hardware arrow key. */
@Composable
private fun RepeatKey(onPress: () -> Unit, content: @Composable () -> Unit) {
    val action by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val modifier = Modifier
        .indication(interaction, ripple())
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interaction.emit(press)
                    action()
                    val repeat = scope.launch {
                        delay(400)
                        while (true) {
                            action()
                            delay(45)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                    interaction.emit(PressInteraction.Release(press))
                },
            )
        }
    KeyBox(modifier, enabled = true, content)
}

@Composable
private fun KeyBox(modifier: Modifier, enabled: Boolean, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxHeight()
            .defaultMinSize(minWidth = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) color else color.copy(alpha = 0.38f), content = content)
    }
}

@Composable
internal fun GoToLineDialog(lineCount: Int, currentLine: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = currentLine.toString()
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val line = value.text.trim().toIntOrNull()
    val valid = line != null && line in 1..lineCount
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to line") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Line number (1–$lineCount)") },
                singleLine = true,
                isError = value.text.isNotBlank() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(line!!) }),
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { Button(onClick = { onGo(line!!) }, enabled = valid) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
