package com.jarves.mh.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen viewer for a project file, sliding over the workspace. Text files can be switched
 * into an editor: [draft] holds the unsaved text (owned by the ViewModel so it survives rotation)
 * and is null while the file is only viewed. Leaving the editor with unsaved changes asks first.
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
    onClose: () -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onSave: (overwrite: Boolean) -> Unit,
    onDismissSaveConflict: () -> Unit,
) {
    val fileName = filePath.substringAfterLast('/')
    val isMarkdown = fileName.substringAfterLast('.', "").equals("md", ignoreCase = true)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty by remember(draft, content) {
        derivedStateOf { draft != null && content != null && !draft.text.contentEquals(content) }
    }
    val leaveEditor: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onStopEditing()
        }
    }

    // Composed after the workspace's own handler, so back leaves the editor before closing the file.
    BackHandler(enabled = draft != null, onBack = leaveEditor)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    Text(
                        when {
                            draft == null -> filePath
                            dirty -> "Editing · unsaved changes"
                            else -> "Editing"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (draft != null) {
                        IconButton(onClick = leaveEditor) { Icon(Icons.Rounded.Close, "Stop editing") }
                    } else {
                        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close file") }
                    }
                },
                actions = {
                    if (draft != null) {
                        IconButton(onClick = { draft.undoState.undo() }, enabled = draft.undoState.canUndo) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, "Undo")
                        }
                        IconButton(onClick = { draft.undoState.redo() }, enabled = draft.undoState.canRedo) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, "Redo")
                        }
                        if (saving) {
                            LoadingIndicator(Modifier.padding(horizontal = 14.dp).size(32.dp))
                        } else {
                            Button(onClick = { onSave(false) }, enabled = dirty, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                                Text("Save")
                            }
                        }
                    } else {
                        if (!content.isNullOrEmpty() && !binary) {
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
                        if (editable) {
                            IconButton(onClick = onStartEditing) { Icon(Icons.Rounded.Edit, "Edit file") }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (draft == null && notice != null && content != null && !binary) NoticeBanner(notice)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                    draft != null -> FileEditor(draft)
                    binary -> ExpressiveEmptyState(
                        Icons.Rounded.Description,
                        "Binary file",
                        "This file can't be shown as text. Use Save to device in the Files list to open it in another app.",
                    )
                    content == null -> ExpressiveEmptyState(Icons.Rounded.Description, "Can't open this file", notice ?: "The file could not be read.")
                    isMarkdown -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                        item { MarkdownText(markdown = content, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    else -> NumberedLines(content)
                }
            }
        }
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
private fun FileEditor(draft: TextFieldState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft) { focus.requestFocus() }
    Surface(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BasicTextField(
            state = draft,
            modifier = Modifier.fillMaxSize().focusRequester(focus).padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
    }
}

@Composable
private fun NumberedLines(content: String) {
    val lines = remember(content) { content.lines() }
    Surface(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines.size) { index ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.width(48.dp).padding(end = 12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        lines[index],
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
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
