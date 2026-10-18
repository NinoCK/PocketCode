package io.github.ninock.pocketcode.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninock.pocketcode.ui.editor.CodeEdit
import io.github.ninock.pocketcode.ui.editor.CodeEditing
import io.github.ninock.pocketcode.ui.editor.CodeEditor
import io.github.ninock.pocketcode.ui.editor.CodeEditorState
import io.github.ninock.pocketcode.ui.editor.CodeLanguages
import io.github.ninock.pocketcode.ui.editor.CodeSearch
import io.github.ninock.pocketcode.ui.editor.CodeVerticalPadding
import io.github.ninock.pocketcode.ui.editor.CodeViewer
import io.github.ninock.pocketcode.ui.editor.EditorKeyBar
import io.github.ninock.pocketcode.ui.editor.EditorPreferences
import io.github.ninock.pocketcode.ui.editor.FindBar
import io.github.ninock.pocketcode.ui.editor.FindState
import io.github.ninock.pocketcode.ui.editor.GoToLineDialog
import io.github.ninock.pocketcode.ui.editor.LineIndex
import io.github.ninock.pocketcode.ui.editor.MaxUnwrappedLineLength
import io.github.ninock.pocketcode.ui.editor.SearchOptions
import io.github.ninock.pocketcode.ui.editor.SearchResult
import io.github.ninock.pocketcode.ui.editor.longestLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SearchRequest(val open: Boolean, val query: String, val options: SearchOptions, val text: CharSequence?)

/**
 * Full-screen viewer for a project file, sliding over the workspace. Code is coloured by language
 * and can be searched; text files can be switched into the code editor. [draft] holds the unsaved
 * text (owned by the ViewModel so it survives rotation) and is null while the file is only viewed.
 * Leaving the editor with unsaved changes asks first.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FileViewerScreen(
    filePath: String,
    content: String?,
    loading: Boolean,
    binary: Boolean,
    notice: String?,
    editable: Boolean,
    draft: TextFieldState?,
    saving: Boolean,
    saveConflict: Boolean,
    preferences: EditorPreferences,
    onClose: () -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onSave: (overwrite: Boolean) -> Unit,
    onDismissSaveConflict: () -> Unit,
    onPreferencesChange: (EditorPreferences) -> Unit,
) {
    val fileName = filePath.substringAfterLast('/')
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var goToLineOpen by remember { mutableStateOf(false) }
    var showSource by rememberSaveable(filePath) { mutableStateOf(false) }
    var flashLine by remember { mutableIntStateOf(-1) }

    val language = remember(filePath, content) { CodeLanguages.forFile(fileName, content?.take(256)) }
    val isMarkdown = language === CodeLanguages.Markdown
    val contentLines = remember(content) { content?.let(::LineIndex) }
    val viewerLongLines = remember(content) { content != null && longestLine(content) > MaxUnwrappedLineLength }
    val find = remember(filePath) { FindState() }
    val listState = remember(filePath) { LazyListState() }
    val editor = remember(draft, language) {
        draft?.let { CodeEditorState(it, language, CodeEditing.detectIndent(it.text, language)) }
    }
    val wrap = preferences.wordWrap || (editor?.hasLongLines ?: viewerLongLines)
    val dirty by remember(draft, content) {
        derivedStateOf { draft != null && content != null && !draft.text.contentEquals(content) }
    }
    val searchable = !loading && !binary && content != null
    val showingPreview = isMarkdown && !showSource && editor == null && !find.open
    val leaveEditor: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onStopEditing()
        }
    }

    // Composed after the workspace's own handler, so back leaves the editor before closing the file,
    // and closes the find bar before leaving the editor.
    BackHandler(enabled = draft != null, onBack = leaveEditor)
    BackHandler(enabled = find.open) { find.close() }

    fun revealMatch(index: Int, select: Boolean) {
        val result = find.result
        if (index !in 0 until result.count) return
        val start = result.start(index)
        if (editor != null) {
            if (select) editor.select(start, result.end(index))
            scope.launch {
                withFrameNanos { }
                editor.reveal(start, wrap)
            }
        } else if (contentLines != null) {
            val line = contentLines.lineOf(start)
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == line } || visible.lastOrNull()?.index == line) {
                scope.launch { listState.animateScrollToItem(maxOf(0, line - visible.size / 3)) }
            }
        }
    }

    // Matches follow the query, the options and (while editing) the text. A new query jumps to the
    // first match after the caret or the top of the screen, like a browser's find in page.
    LaunchedEffect(find, editor, content) {
        var lastQuery: Pair<String, SearchOptions>? = null
        snapshotFlow { SearchRequest(find.open, find.query.text.toString(), find.options, editor?.text?.text ?: content) }
            .collectLatest { request ->
                val text = request.text
                if (!request.open || request.query.isEmpty() || text == null) {
                    find.result = SearchResult.Empty
                    find.current = -1
                    lastQuery = null
                    return@collectLatest
                }
                if (text.length > 50_000) delay(120)
                val snapshot = text.toString()
                val result = withContext(Dispatchers.Default) { CodeSearch.findAll(snapshot, request.query, request.options) }
                find.result = result
                val query = request.query to request.options
                if (query != lastQuery) {
                    lastQuery = query
                    val anchor = editor?.text?.selection?.min
                        ?: contentLines?.startOf(listState.firstVisibleItemIndex)
                        ?: 0
                    find.current = result.nextFrom(anchor)
                    revealMatch(find.current, select = false)
                } else if (find.current >= result.count) {
                    find.current = -1
                }
            }
    }

    fun goToMatch(forward: Boolean) {
        val result = find.result
        if (result.count == 0) return
        val current = find.current.takeIf { it in 0 until result.count }
        val index = if (editor != null) {
            val selection = editor.text.selection
            val selected = result.indexOf(selection.min, selection.max)
            when {
                selected >= 0 -> if (forward) (selected + 1) % result.count else (selected - 1 + result.count) % result.count
                forward -> result.nextFrom(selection.max)
                else -> result.previousFrom(selection.min)
            }
        } else {
            when {
                current != null -> if (forward) (current + 1) % result.count else (current - 1 + result.count) % result.count
                forward -> result.nextFrom(contentLines?.startOf(listState.firstVisibleItemIndex) ?: 0)
                else -> result.previousFrom(contentLines?.startOf(listState.firstVisibleItemIndex) ?: 0)
            }
        }
        find.current = index
        revealMatch(index, select = true)
    }

    fun replaceCurrent() {
        val target = editor ?: return
        val result = find.result
        if (result.count == 0) return
        val selection = target.text.selection
        val index = result.indexOf(selection.min, selection.max).takeIf { it >= 0 }
            ?: find.current.takeIf { it in 0 until result.count }
        if (index == null) {
            goToMatch(forward = true)
            return
        }
        val query = find.query.text.toString()
        val start = result.start(index)
        val replacement = CodeSearch.replacementFor(target.text.text, start, result.end(index), query, find.replacement.text.toString(), find.options)
        target.apply(CodeEdit(start, result.end(index), replacement, start + replacement.length))
        val updated = CodeSearch.findAll(target.text.text, query, find.options)
        find.result = updated
        find.current = updated.nextFrom(start + replacement.length)
        revealMatch(find.current, select = true)
    }

    fun replaceAll() {
        val target = editor ?: return
        val (edit, count) = CodeSearch.replaceAll(target.text.text, find.query.text.toString(), find.replacement.text.toString(), find.options) ?: return
        target.apply(edit)
        Toast.makeText(context, "Replaced $count ${if (count == 1) "match" else "matches"}", Toast.LENGTH_SHORT).show()
    }

    val openFind: (Boolean) -> Unit = { replace ->
        val seed = editor?.text?.let { text ->
            val selection = text.selection
            if (selection.collapsed) null else text.text.subSequence(selection.min, selection.max)
        }
        find.show(replace && editor != null, seed)
    }

    fun goToLine(line: Int) {
        goToLineOpen = false
        if (editor != null) {
            val offset = editor.lines.startOf(line - 1)
            editor.select(offset)
            editor.focusRequester.requestFocus()
            scope.launch {
                withFrameNanos { }
                editor.reveal(offset, wrap)
            }
        } else {
            val visible = listState.layoutInfo.visibleItemsInfo.size
            scope.launch {
                listState.scrollToItem(maxOf(0, line - 1 - visible / 3))
                flashLine = line - 1
                delay(1_500)
                flashLine = -1
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (find.open) {
                FindBar(
                    find = find,
                    allowReplace = editor != null,
                    onNext = { goToMatch(forward = true) },
                    onPrevious = { goToMatch(forward = false) },
                    onReplace = ::replaceCurrent,
                    onReplaceAll = ::replaceAll,
                    onClose = { find.close() },
                )
                return@Scaffold
            }
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    val status = if (editor != null) {
                        val caret = editor.text.selection.start
                        val lines = editor.lines
                        val line = lines.lineOf(caret)
                        val position = "Ln ${line + 1}, Col ${caret - lines.startOf(line) + 1}"
                        if (dirty) "Unsaved changes · $position" else "$position · ${language.displayName}"
                    } else {
                        filePath
                    }
                    Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (draft != null) {
                        IconButton(onClick = leaveEditor) { Icon(Icons.Rounded.Close, "Stop editing") }
                    } else {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close file") }
                    }
                },
                actions = {
                    if (searchable) {
                        IconButton(onClick = { openFind(false) }) { Icon(Icons.Rounded.Search, "Find in file") }
                    }
                    if (editor == null && !content.isNullOrEmpty() && !binary) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(content))
                            copied = true
                            scope.launch { delay(2000); copied = false }
                        }) {
                            AnimatedContent(targetState = copied, label = "copyIcon") { done ->
                                Icon(
                                    if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    "Copy file contents",
                                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (editor == null && editable) {
                        IconButton(onClick = onStartEditing) { Icon(Icons.Rounded.Edit, "Edit file") }
                    }
                    if (searchable) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                            FileMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                editor = editor,
                                isMarkdown = isMarkdown,
                                showingPreview = showingPreview,
                                preferences = preferences,
                                wrapLocked = editor?.hasLongLines ?: viewerLongLines,
                                languageLabel = language.displayName,
                                onGoToLine = { goToLineOpen = true },
                                onToggleSource = { showSource = !showSource },
                                onPreferencesChange = onPreferencesChange,
                            )
                        }
                    }
                    if (editor != null) {
                        if (saving) {
                            LoadingIndicator(Modifier.padding(horizontal = 14.dp).size(32.dp))
                        } else {
                            Button(onClick = { onSave(false) }, enabled = dirty, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                                Text("Save")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (editor == null && notice != null && content != null && !binary) NoticeBanner(notice)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                    editor != null -> CodeSurface(verticalPadding = CodeVerticalPadding) {
                        CodeEditor(
                            state = editor,
                            preferences = preferences,
                            find = find,
                            onPreferencesChange = onPreferencesChange,
                            onSave = { if (dirty && !saving) onSave(false) },
                            onFind = openFind,
                            onGoToLine = { goToLineOpen = true },
                        )
                    }
                    binary -> ExpressiveEmptyState(
                        Icons.Rounded.Description,
                        "Binary file",
                        "This file can't be shown as text. Use Save to device in the Files list to open it in another app.",
                    )
                    content == null || contentLines == null -> ExpressiveEmptyState(Icons.Rounded.Description, "Can't open this file", notice ?: "The file could not be read.")
                    showingPreview -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                        item { MarkdownText(markdown = content, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    else -> CodeSurface(verticalPadding = 0.dp) {
                        CodeViewer(
                            content = content,
                            language = language,
                            lines = contentLines,
                            preferences = preferences,
                            find = find,
                            listState = listState,
                            flashLine = flashLine,
                            onPreferencesChange = onPreferencesChange,
                        )
                    }
                }
            }
            if (editor != null && editor.focused && !find.open) EditorKeyBar(editor)
        }
    }

    if (goToLineOpen) {
        val lineCount = editor?.lines?.lineCount ?: contentLines?.lineCount ?: 1
        val currentLine = editor?.let { it.lines.lineOf(it.text.selection.start) + 1 } ?: (listState.firstVisibleItemIndex + 1)
        GoToLineDialog(lineCount = lineCount, currentLine = currentLine, onGo = ::goToLine, onDismiss = { goToLineOpen = false })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to $fileName haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onStopEditing() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (saveConflict) {
        AlertDialog(
            onDismissRequest = onDismissSaveConflict,
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("File changed on disk") },
            text = {
                Text("$fileName was changed or removed after you opened it, possibly by the agent or a terminal command. Save anyway and replace that version with yours?")
            },
            confirmButton = { Button(onClick = { onSave(true) }) { Text("Save anyway") } },
            dismissButton = { TextButton(onClick = onDismissSaveConflict) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CodeSurface(verticalPadding: Dp, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.padding(vertical = verticalPadding)) { content() }
    }
}

/** Overflow menu: navigation, line commands while editing, and the view settings. */
@Composable
private fun FileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    editor: CodeEditorState?,
    isMarkdown: Boolean,
    showingPreview: Boolean,
    preferences: EditorPreferences,
    wrapLocked: Boolean,
    languageLabel: String,
    onGoToLine: () -> Unit,
    onToggleSource: () -> Unit,
    onPreferencesChange: (EditorPreferences) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp)) {
        if (!showingPreview) {
            DropdownMenuItem(text = { Text("Go to line") }, onClick = { onDismiss(); onGoToLine() })
        }
        if (editor != null) {
            val commands = listOf<Pair<String, (CharSequence, Int, Int) -> CodeEdit?>>(
                "Toggle comment" to { t, s, e -> CodeEditing.toggleComment(t, s, e, editor.language) },
                "Duplicate line" to CodeEditing::duplicateLines,
                "Delete line" to CodeEditing::deleteLines,
                "Move line up" to { t, s, e -> CodeEditing.moveLines(t, s, e, down = false) },
                "Move line down" to { t, s, e -> CodeEditing.moveLines(t, s, e, down = true) },
            )
            for ((label, command) in commands) {
                DropdownMenuItem(text = { Text(label) }, onClick = { onDismiss(); editor.run(command) })
            }
        }
        if (isMarkdown && editor == null) {
            DropdownMenuItem(text = { Text(if (showingPreview) "Show source" else "Show preview") }, onClick = { onDismiss(); onToggleSource() })
        }
        if (!showingPreview) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(if (wrapLocked) "Word wrap (long lines)" else "Word wrap") },
                onClick = { onPreferencesChange(preferences.copy(wordWrap = !preferences.wordWrap)) },
                enabled = !wrapLocked,
                trailingIcon = { Checkbox(checked = preferences.wordWrap || wrapLocked, onCheckedChange = null, enabled = !wrapLocked) },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", Modifier.weight(1f))
                        IconButton(
                            onClick = { onPreferencesChange(preferences.copy(fontSize = (preferences.fontSize - 1).coerceAtLeast(EditorPreferences.MinFontSize))) },
                            enabled = preferences.fontSize > EditorPreferences.MinFontSize,
                        ) { Icon(Icons.Rounded.Remove, "Smaller text") }
                        Text("${preferences.fontSize.toInt()}", Modifier.width(24.dp), fontSize = 14.sp, maxLines = 1)
                        IconButton(
                            onClick = { onPreferencesChange(preferences.copy(fontSize = (preferences.fontSize + 1).coerceAtMost(EditorPreferences.MaxFontSize))) },
                            enabled = preferences.fontSize < EditorPreferences.MaxFontSize,
                        ) { Icon(Icons.Rounded.Add, "Larger text") }
                    }
                },
                onClick = {},
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                if (editor != null) "$languageLabel · ${editor.indent.label}" else languageLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
