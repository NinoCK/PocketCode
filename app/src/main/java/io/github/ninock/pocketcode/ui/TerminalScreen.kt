package io.github.ninock.pocketcode.ui

import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninock.pocketcode.ui.theme.AppThemeMode
import io.github.ninock.pocketcode.ui.theme.LocalDarkTheme
import io.github.ninock.pocketcode.ui.theme.PocketGreen
import io.github.ninock.pocketcode.ui.theme.PocketOrange

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalScreen(
    lines: List<TerminalOutputLine>,
    isRunning: Boolean,
    onRun: (String) -> Unit,
    onInput: (String) -> Unit = {},
    onInterrupt: (() -> Unit)? = null,
    onClear: () -> Unit,
    onToggleTheme: () -> Unit,
    themeMode: AppThemeMode,
    title: String = "Linux Terminal",
    subtitle: String = "Ubuntu 24.04 · PRoot Sandbox",
    liveOutput: String = "",
    currentCommand: String? = null,
    commandDraft: String? = null,
    onCommandDraftConsumed: () -> Unit = {},
    promptPath: String = "/workspace",
    onStop: (() -> Unit)? = null,
    showThemeAction: Boolean = false,
    showQuickCommands: Boolean = true,
    compactHeader: Boolean = false,
) {
    var commandInput by remember { mutableStateOf(TextFieldValue()) }
    var commandHistory by remember { mutableStateOf(emptyList<String>()) }
    var historyIndex by remember { mutableStateOf(-1) }
    var altActive by rememberSaveable { mutableStateOf(false) }
    var ctrlActive by rememberSaveable { mutableStateOf(false) }
    val terminalScrollState = rememberScrollState()
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val scope = rememberCoroutineScope()
    val terminalPromptPath = if (promptPath == "/workspace") "~" else "~${promptPath.removePrefix("/workspace")}" 
    val openTerminalKeyboard = {
        inputFocusRequester.requestFocus()
        scope.launch {
            delay(80)
            keyboardController?.show()
        }
    }
    val submitCommand = {
        if (commandInput.text.isNotBlank()) {
            val submitted = commandInput.text
            if (isRunning) {
                onInput(submitted)
            } else {
                onRun(submitted)
                commandHistory = (commandHistory + submitted).takeLast(50)
                historyIndex = -1
            }
            commandInput = TextFieldValue()
        }
    }

    LaunchedEffect(commandDraft) {
        if (!commandDraft.isNullOrBlank()) {
            commandInput = TextFieldValue(commandDraft, TextRange(commandDraft.length))
            historyIndex = -1
            onCommandDraftConsumed()
        }
    }

    // Auto-scroll to bottom whenever scrollable content grows
    LaunchedEffect(Unit) {
        snapshotFlow { terminalScrollState.maxValue }
            .collect { maxValue ->
                terminalScrollState.scrollTo(maxValue)
            }
    }
    // Also trigger scroll when key state changes (e.g. isRunning toggling)
    LaunchedEffect(lines.size, isRunning, commandInput.text.length) {
        delay(100)
        terminalScrollState.scrollTo(terminalScrollState.maxValue)
    }

    val quickCommands = listOf(
        "uname -a",
        "ls -la",
        "pwd",
        "node -v",
        "python3 --version",
        "df -h",
        "free -m",
        "claude --version",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (compactHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear output") }
                }
            } else {
                TopAppBar(
                    title = { Text(title) },
                    subtitle = { Text(subtitle, maxLines = 1) },
                    actions = {
                        IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear output") }
                        if (showThemeAction) {
                            IconButton(onClick = onToggleTheme) {
                                Icon(if (LocalDarkTheme.current) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, contentDescription = "Toggle theme")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (compactHeader) Modifier else Modifier.imePadding()),
        ) {
            if (showQuickCommands && !keyboardVisible) {
                // Quick command chips are useful in the standalone terminal, but
                // project terminal space is reserved for the actual project session.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickCommands.forEach { cmd ->
                        SuggestionChip(
                            onClick = { onRun(cmd) },
                            label = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = null,
                        )
                    }
                }
            }

            val isDark = LocalDarkTheme.current
            val terminalBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow
            val promptGreen = PocketGreen
            val commandTextColor = MaterialTheme.colorScheme.onSurface
            val outputTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f)
            val emptyStateColor = MaterialTheme.colorScheme.onSurfaceVariant

            // Console output area
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = terminalBg,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(terminalScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (lines.isEmpty()) {
                                Text(
                                    "PocketCode Terminal ready.\nType a bash command below or tap a quick command chip above.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = emptyStateColor,
                                )
                            }

                            lines.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TerminalCommandPrompt(promptPath = terminalPromptPath, command = item.command, isDark = isDark)
                                    if (item.output.isNotEmpty()) {
                                        Text(
                                            text = sanitizeTerminalOutput(item.output),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = if (item.exitCode != 0) MaterialTheme.colorScheme.error else outputTextColor,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }

                            if (isRunning && currentCommand != null) {
                                TerminalCommandPrompt(promptPath = terminalPromptPath, command = currentCommand, isDark = isDark)
                            }

                            if (liveOutput.isNotBlank()) {
                                Text(
                                    sanitizeTerminalOutput(liveOutput),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = outputTextColor,
                                )
                            }

                        }
                    }
                    run {
                        // While a process is running, keep the input surface visually
                        // empty until the user types. A permanent prompt/cursor here
                        // changed the console height and made auto-scroll look like a
                        // full terminal refresh on every blink.
                        val prefix = if (isRunning) "" else "root@pocket:$terminalPromptPath# "
                        val prefixVisualTransformation = remember(prefix, isDark) {
                            VisualTransformation { text ->
                                val transformed = buildAnnotatedString {
                                    withStyle(SpanStyle(color = promptGreen, fontWeight = FontWeight.Bold)) {
                                        append(prefix)
                                    }
                                    withStyle(SpanStyle(color = commandTextColor, fontWeight = FontWeight.SemiBold)) {
                                        append(text.text)
                                    }
                                }
                                val offsetMapping = object : OffsetMapping {
                                    override fun originalToTransformed(offset: Int): Int =
                                        (offset + prefix.length).coerceIn(0, prefix.length + text.length)

                                    override fun transformedToOriginal(offset: Int): Int =
                                        (offset - prefix.length).coerceIn(0, text.length)
                                }
                                TransformedText(transformed, offsetMapping)
                            }
                        }

                        BasicTextField(
                            value = commandInput,
                            onValueChange = { next ->
                                if (ctrlActive && next.text.length > commandInput.text.length) {
                                    val inserted = next.text.substring(
                                        commandInput.selection.start.coerceAtMost(next.text.length),
                                        next.selection.end.coerceAtMost(next.text.length),
                                    )
                                    if (inserted.any { it.equals('c', ignoreCase = true) }) {
                                        if (isRunning) {
                                            onInterrupt?.invoke()
                                        } else {
                                            // A shell with no foreground process uses
                                            // Ctrl+C to cancel the current command line.
                                            commandInput = TextFieldValue()
                                        }
                                        ctrlActive = false
                                    } else {
                                        commandInput = next
                                        ctrlActive = false
                                    }
                                } else {
                                    commandInput = next
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(inputFocusRequester),
                            singleLine = false,
                            visualTransformation = prefixVisualTransformation,
                            cursorBrush = SolidColor(promptGreen),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = commandTextColor,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { submitCommand() },
                                onDone = { submitCommand() },
                                onGo = { submitCommand() },
                            ),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // Keyboard helper row. These operate on the command draft, so they are
            // useful even when the phone keyboard does not expose terminal keys.
            if (!keyboardVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalKeyButton("↑", "Previous command") {
                        commandHistory.getOrNull(if (historyIndex < 0) commandHistory.lastIndex else (historyIndex - 1).coerceAtLeast(0))?.let {
                            historyIndex = if (historyIndex < 0) commandHistory.lastIndex else (historyIndex - 1).coerceAtLeast(0)
                            commandInput = TextFieldValue(it, TextRange(it.length))
                        }
                    }
                    TerminalKeyButton("↓", "Next command") {
                        if (historyIndex >= 0) {
                            historyIndex = (historyIndex + 1).takeIf { it < commandHistory.size } ?: -1
                            commandInput = TextFieldValue(commandHistory.getOrNull(historyIndex) ?: "", TextRange((commandHistory.getOrNull(historyIndex) ?: "").length))
                        }
                    }
                    TerminalIconKeyButton(Icons.Default.ArrowBack, "Move cursor left") {
                        commandInput = commandInput.copy(selection = TextRange((commandInput.selection.start - 1).coerceAtLeast(0)))
                    }
                    TerminalIconKeyButton(Icons.Default.ArrowForward, "Move cursor right") {
                        commandInput = commandInput.copy(selection = TextRange((commandInput.selection.end + 1).coerceAtMost(commandInput.text.length)))
                    }
                    TerminalKeyButton("ALT", "Alt modifier", active = altActive, fixedWidth = true) { altActive = !altActive }
                    TerminalKeyButton("ESC", "Escape") { commandInput = TextFieldValue() }
                    TerminalKeyButton("CTRL", "Control modifier; press C to interrupt", active = ctrlActive, fixedWidth = true) {
                        ctrlActive = !ctrlActive
                        if (ctrlActive) openTerminalKeyboard()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TerminalKeyButton(
    label: String,
    description: String,
    active: Boolean = false,
    fixedWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val modifier = Modifier.height(40.dp).semantics { contentDescription = description }
    if (fixedWidth) {
        // Sticky modifiers (ALT, CTRL) are real toggle buttons.
        ToggleButton(
            checked = active,
            onCheckedChange = { onClick() },
            modifier = modifier.width(80.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1) }
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TerminalIconKeyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TerminalCommandPrompt(promptPath: String, command: String, isDark: Boolean = true) {
    val promptGreen = if (isDark) PocketGreen else Color(0xFF0D7A3E)
    val commandColor = if (isDark) Color(0xFFF0F6FC) else MaterialTheme.colorScheme.onSurface
    val promptText = remember(promptPath, command, isDark) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = promptGreen, fontWeight = FontWeight.Bold)) {
                append("root@pocket:$promptPath# ")
            }
            withStyle(SpanStyle(color = commandColor, fontWeight = FontWeight.SemiBold)) {
                append(command)
            }
        }
    }
    Text(
        text = promptText,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        softWrap = true,
    )
}
