package com.jarves.mh.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.data.WorkspaceFileOps
import com.jarves.mh.model.FileExportPhase
import com.jarves.mh.model.FileExportState
import com.jarves.mh.model.FileUploadState
import com.jarves.mh.model.UploadConflict
import com.jarves.mh.model.UploadConflictChoice
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.ui.theme.PocketBlue
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import java.util.Locale
import kotlin.math.roundToInt

private val PocketPurple = Color(0xFFB79CFF)
private val PocketPink = Color(0xFFF59AC0)
private val PocketAmber = Color(0xFFFFC85C)
private val PocketTeal = Color(0xFF5ED3D0)
private val PocketRed = Color(0xFFFF7A7A)

private enum class ExportTarget { PROJECT, SELECTION }

/** Callbacks for changing project files by hand from the Files tab and the file editor. */
data class FileManagerActions(
    val onEditFile: (WorkspaceEntry) -> Unit = {},
    val onStartEditing: () -> Unit = {},
    val onStopEditing: () -> Unit = {},
    val onSaveEdits: (overwrite: Boolean) -> Unit = {},
    val onDismissSaveConflict: () -> Unit = {},
    val onDelete: (paths: List<String>) -> Unit = {},
    val onRename: (path: String, newName: String) -> Unit = { _, _ -> },
    val onCreate: (parentDir: String, name: String, isDirectory: Boolean) -> Unit = { _, _, _ -> },
    val onUpload: (uris: List<Uri>, directory: String) -> Unit = { _, _ -> },
    /** Null cancels the upload. */
    val onResolveUploadConflict: (UploadConflictChoice?) -> Unit = {},
    val onCancelUpload: () -> Unit = {},
)

private enum class RowAction { EDIT, RENAME, SAVE, DELETE }

/** The dialog open over the Files tab. */
private sealed interface FileDialog {
    data class Rename(val entry: WorkspaceEntry) : FileDialog
    data class Delete(val entries: List<WorkspaceEntry>) : FileDialog
    data class Create(val isDirectory: Boolean) : FileDialog
}

/**
 * Project Files page. Folders are browsed one level at a time with a breadcrumb trail; every row
 * has a checkbox so any mix of files and folders can be ticked across folders and exported as a
 * ZIP or deleted. The download button beside refresh zips the whole project; the upload button
 * copies picked files into the folder being shown and "+" creates a file or folder there. Each row's
 * menu edits, renames, saves or deletes it. Navigation, the selection, export and upload progress
 * live in the ViewModel so they survive opening a file or switching tabs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceFilesTab(
    files: List<WorkspaceEntry>,
    currentDir: String,
    selection: Map<String, WorkspaceEntry>,
    artifacts: List<WorkspaceEntry>,
    loading: Boolean,
    export: FileExportState?,
    exportBlockedReason: String?,
    projectSlug: String,
    suggestedProjectRoot: String?,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onSelectionChange: (Map<String, WorkspaceEntry>) -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    onExportProject: (Uri, Boolean) -> Unit,
    onExportSelection: (Uri, Boolean) -> Unit,
    onSaveFile: (Uri, String) -> Unit,
    onCancelExport: () -> Unit,
    onDismissExport: () -> Unit,
    onOpenExportLocation: () -> Unit,
    upload: FileUploadState? = null,
    uploadConflict: UploadConflict? = null,
    changeBlockedReason: String? = null,
    actions: FileManagerActions = FileManagerActions(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val entries = remember(files) {
        files.sortedWith(
            compareByDescending<WorkspaceEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    }
    val selected = selection.keys

    var sheet by rememberSaveable { mutableStateOf<ExportTarget?>(null) }
    var includeBuildFiles by rememberSaveable { mutableStateOf(false) }
    // Captured when the save picker opens, read when it returns.
    var pendingIncludeBuildFiles by rememberSaveable { mutableStateOf(false) }
    var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }

    val projectLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) onExportProject(uri, pendingIncludeBuildFiles)
    }
    val selectionLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) onExportSelection(uri, pendingIncludeBuildFiles)
    }
    val fileLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val path = pendingSavePath
        pendingSavePath = null
        if (uri != null && path != null) onSaveFile(uri, path)
    }

    fun blocked(): Boolean {
        val reason = exportBlockedReason ?: return false
        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        return true
    }

    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    // The folder shown when the picker opened; the files land there even if the list changed meanwhile.
    var pendingUploadDir by rememberSaveable { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) actions.onUpload(uris, pendingUploadDir)
    }

    fun changeBlocked(): Boolean {
        val reason = changeBlockedReason ?: return false
        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        return true
    }

    fun startUpload() {
        if (changeBlocked()) return
        pendingUploadDir = currentDir
        uploadLauncher.launch(arrayOf("*/*"))
    }

    // Everything in this folder may already be included through a ticked ancestor.
    val currentIncludedWith = remember(currentDir, selected) {
        when {
            currentDir.isEmpty() -> null
            currentDir in selected -> currentDir
            else -> WorkspaceFileOps.coveringSelection(currentDir, selected)
        }
    }
    // "Select all" skips build/cache folders; they can still be ticked one by one.
    val selectable = remember(entries) {
        entries.filter { !WorkspaceFileOps.isExportExcludedName(it.name) }.ifEmpty { entries }
    }
    val selectAllState = when {
        currentIncludedWith != null -> ToggleableState.On
        selectable.isNotEmpty() && selectable.all { it.path in selected } -> ToggleableState.On
        entries.any { it.path in selected } -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    LaunchedEffect(sheet, selection.isEmpty()) {
        if (sheet == ExportTarget.SELECTION && selection.isEmpty()) sheet = null
    }
    BackHandler(enabled = currentDir.isNotEmpty()) {
        onOpenDirectory(currentDir.substringBeforeLast('/', ""))
    }
    BackHandler(enabled = currentDir.isEmpty() && selection.isNotEmpty()) {
        onSelectionChange(emptyMap())
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selection.isEmpty() && upload == null) 24.dp else 124.dp),
        ) {
            if (suggestedProjectRoot != null) {
                item(key = "suggested-root") {
                    SuggestedRootCard(suggestedProjectRoot, onUseSuggestedProjectRoot, Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp))
                }
            }
            if (currentDir.isEmpty() && artifacts.isNotEmpty()) {
                item(key = "build-outputs") {
                    BuildOutputsCard(
                        artifacts = artifacts,
                        modifier = Modifier.padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = if (suggestedProjectRoot == null) 12.dp else 0.dp,
                            bottom = 6.dp,
                        ),
                        onSave = { artifact ->
                            if (!blocked()) {
                                pendingSavePath = artifact.path
                                fileLauncher.launch(artifact.name to mimeTypeFor(artifact.name))
                            }
                        },
                    )
                }
            }
            stickyHeader(key = "folder-bar") {
                FolderBar(
                    projectSlug = projectSlug,
                    currentDir = currentDir,
                    itemCount = entries.size,
                    loading = loading,
                    selectAllState = selectAllState,
                    includedWith = currentIncludedWith,
                    canSelectAll = currentIncludedWith == null && entries.isNotEmpty(),
                    onNavigate = onOpenDirectory,
                    onRefresh = onRefresh,
                    onUpload = ::startUpload,
                    onCreate = { isDirectory -> if (!changeBlocked()) dialog = FileDialog.Create(isDirectory) },
                    onDownloadProject = { sheet = ExportTarget.PROJECT },
                    onSelectAll = {
                        onSelectionChange(
                            if (selectAllState == ToggleableState.On) {
                                selection.filterKeys { path -> entries.none { it.path == path } }
                            } else {
                                withAdded(selection, selectable)
                            },
                        )
                    },
                )
            }
            when {
                entries.isEmpty() && loading -> items(6, key = { "skeleton-$it" }) { SkeletonRow(it) }
                entries.isEmpty() -> item(key = "empty-$currentDir") { EmptyFolder(isRoot = currentDir.isEmpty(), onUpload = ::startUpload) }
                else -> items(entries, key = { "entry:${it.path}" }) { entry ->
                    val explicit = entry.path in selected
                    val includedWith = if (explicit) null else WorkspaceFileOps.coveringSelection(entry.path, selected)
                    FileRow(
                        entry = entry,
                        checked = explicit || includedWith != null,
                        includedByParent = includedWith != null,
                        skippedByDefault = entry.isDirectory && WorkspaceFileOps.isExportExcludedName(entry.name),
                        modifier = Modifier.animateItem(),
                        onOpen = { if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenFile(entry) },
                        onToggle = { onSelectionChange(toggled(selection, entry)) },
                        onLongPress = {
                            if (includedWith == null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectionChange(toggled(selection, entry))
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                // The editor itself explains when the agent is busy or the file can't be edited.
                                RowAction.EDIT -> actions.onEditFile(entry)
                                RowAction.RENAME -> if (!changeBlocked()) dialog = FileDialog.Rename(entry)
                                RowAction.SAVE -> if (!blocked()) {
                                    pendingSavePath = entry.path
                                    fileLauncher.launch(entry.name to mimeTypeFor(entry.name))
                                }
                                RowAction.DELETE -> if (!changeBlocked()) dialog = FileDialog.Delete(listOf(entry))
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty() && upload == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            SelectionBar(
                selection = selection.values,
                onClear = { onSelectionChange(emptyMap()) },
                onDelete = { if (!changeBlocked()) dialog = FileDialog.Delete(selection.values.toList()) },
                onExport = { sheet = ExportTarget.SELECTION },
            )
        }

        // Keep the last progress on screen while the bar slides away; quick uploads fade in late
        // enough that they rarely flash at all.
        var lastUpload by remember { mutableStateOf(upload) }
        if (upload != null) lastUpload = upload
        AnimatedVisibility(
            visible = upload != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200, delayMillis = 250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            lastUpload?.let { UploadBar(it, projectSlug, onCancel = actions.onCancelUpload) }
        }

        ExportOverlay(export = export, onCancel = onCancelExport, onDismiss = onDismissExport, onOpenLocation = onOpenExportLocation)
    }

    sheet?.let { target ->
        val zipName = when (target) {
            ExportTarget.PROJECT -> "$projectSlug.zip"
            ExportTarget.SELECTION -> selection.values.singleOrNull()?.let { "${it.name}.zip" } ?: "$projectSlug-selection.zip"
        }
        ExportSheet(
            target = target,
            projectSlug = projectSlug,
            zipName = zipName,
            selection = selection.values.toList(),
            includeBuildFiles = includeBuildFiles,
            onIncludeBuildFilesChange = { includeBuildFiles = it },
            onRemove = { path -> onSelectionChange(selection - path) },
            onDismiss = { sheet = null },
            onSave = {
                if (!blocked()) {
                    pendingIncludeBuildFiles = includeBuildFiles
                    sheet = null
                    when (target) {
                        ExportTarget.PROJECT -> projectLauncher.launch(zipName to "application/zip")
                        ExportTarget.SELECTION -> selectionLauncher.launch(zipName to "application/zip")
                    }
                }
            },
        )
    }

    when (val open = dialog) {
        null -> Unit
        is FileDialog.Rename -> NameDialog(
            title = if (open.entry.isDirectory) "Rename folder" else "Rename file",
            icon = Icons.Default.DriveFileRenameOutline,
            initialName = open.entry.name,
            confirmLabel = "Rename",
            takenNames = entries.filter { it.path != open.entry.path }.mapTo(hashSetOf()) { it.name },
            selectExtension = open.entry.isDirectory,
            onConfirm = { name ->
                dialog = null
                actions.onRename(open.entry.path, name)
            },
            onDismiss = { dialog = null },
        )
        is FileDialog.Create -> NameDialog(
            title = if (open.isDirectory) "New folder" else "New file",
            icon = if (open.isDirectory) Icons.Default.CreateNewFolder else Icons.AutoMirrored.Filled.NoteAdd,
            initialName = "",
            confirmLabel = "Create",
            takenNames = entries.mapTo(hashSetOf()) { it.name },
            selectExtension = true,
            supportingText = "In ${currentDir.ifEmpty { projectSlug }}",
            onConfirm = { name ->
                dialog = null
                actions.onCreate(currentDir, name, open.isDirectory)
            },
            onDismiss = { dialog = null },
        )
        is FileDialog.Delete -> DeleteDialog(
            entries = open.entries,
            onConfirm = {
                dialog = null
                actions.onDelete(open.entries.map { it.path })
            },
            onDismiss = { dialog = null },
        )
    }

    uploadConflict?.let { conflict ->
        UploadConflictDialog(conflict, projectSlug, actions.onResolveUploadConflict)
    }
}

// ---------------------------------------------------------------------------------------------
// Folder bar: breadcrumbs, new/upload, download-project, refresh and "select all"
// ---------------------------------------------------------------------------------------------

@Composable
private fun FolderBar(
    projectSlug: String,
    currentDir: String,
    itemCount: Int,
    loading: Boolean,
    selectAllState: ToggleableState,
    includedWith: String?,
    canSelectAll: Boolean,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onCreate: (isDirectory: Boolean) -> Unit,
    onDownloadProject: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(top = 4.dp)) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Breadcrumbs(projectSlug, currentDir, onNavigate, Modifier.weight(1f))
                CreateMenuButton(onCreate)
                FilledTonalIconButton(onClick = onUpload) {
                    Icon(Icons.Default.Upload, "Upload files to this folder", Modifier.size(20.dp))
                }
                FilledTonalIconButton(onClick = onDownloadProject) {
                    Icon(Icons.Default.Download, "Download project as ZIP", Modifier.size(20.dp))
                }
                RefreshButton(loading, onRefresh)
            }
            Row(Modifier.padding(start = 10.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = selectAllState, onClick = onSelectAll, enabled = canSelectAll)
                Text(
                    when {
                        includedWith != null -> "Included with ${includedWith.substringAfterLast('/')}"
                        selectAllState == ToggleableState.On -> "Unselect all"
                        else -> "Select all"
                    },
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (includedWith != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (itemCount == 1) "1 item" else "%,d items".format(Locale.US, itemCount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Breadcrumbs(projectSlug: String, currentDir: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(currentDir, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    val segments = if (currentDir.isEmpty()) emptyList() else currentDir.split('/')
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        Crumb(Icons.Default.Home, projectSlug, active = segments.isEmpty()) { onNavigate("") }
        segments.forEachIndexed { index, name ->
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Crumb(null, name, active = index == segments.lastIndex) {
                onNavigate(segments.take(index + 1).joinToString("/"))
            }
        }
    }
}

@Composable
private fun Crumb(icon: ImageVector?, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateMenuButton(onCreate: (isDirectory: Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Add, "New file or folder") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            DropdownMenuItem(
                text = { Text("New file") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                onClick = { open = false; onCreate(false) },
            )
            DropdownMenuItem(
                text = { Text("New folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                onClick = { open = false; onCreate(true) },
            )
        }
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onRefresh: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "refreshSpin",
    )
    IconButton(onClick = onRefresh, enabled = !loading) {
        Icon(
            Icons.Default.Refresh,
            "Refresh files",
            Modifier.graphicsLayer { rotationZ = if (loading) spin else 0f },
            tint = if (loading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: WorkspaceEntry,
    checked: Boolean,
    includedByParent: Boolean,
    skippedByDefault: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onAction: (RowAction) -> Unit,
) {
    val background by animateColorAsState(
        if (checked && !includedByParent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "rowBackground",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = !includedByParent)
        FileTypeBadge(entry)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                entry.name,
                fontSize = 15.sp,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entryDetails(entry),
                    Modifier.weight(1f, fill = false),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skippedByDefault && !checked) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Not in project ZIP",
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
        RowMenu(entry, onAction)
    }
}

@Composable
private fun RowMenu(entry: WorkspaceEntry, onAction: (RowAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun pick(action: RowAction) {
        open = false
        onAction(action)
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, "Options for ${entry.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { pick(RowAction.EDIT) },
                )
            }
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = { pick(RowAction.RENAME) },
            )
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { Text("Save to device") },
                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                    onClick = { pick(RowAction.SAVE) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { pick(RowAction.DELETE) },
            )
        }
    }
}

@Composable
private fun FileTypeBadge(entry: WorkspaceEntry, size: Int = 38) {
    val (icon, tint) = fileVisual(entry)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size((size * 0.55f).dp), tint = tint)
    }
}

@Composable
private fun fileVisual(entry: WorkspaceEntry): Pair<ImageVector, Color> {
    if (entry.isDirectory) {
        return if (WorkspaceFileOps.isExportExcludedName(entry.name)) {
            Icons.Default.FolderOff to PocketOrange.copy(alpha = 0.7f)
        } else {
            Icons.Default.Folder to PocketOrange
        }
    }
    return when (entry.name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "apk", "aab" -> Icons.Default.Android to PocketGreen
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "c", "cc", "cpp", "h", "hpp", "rs", "go",
        "swift", "dart", "php", "rb", "cs", "html", "htm", "css", "scss", "vue", "svelte", "sql",
        -> Icons.Default.Code to PocketBlue
        "json", "xml", "yaml", "yml", "toml", "gradle", "properties", "ini", "cfg", "conf", "lock", "pro",
        -> Icons.Default.DataObject to PocketPurple
        "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif" -> Icons.Default.Image to PocketPink
        "zip", "jar", "aar", "tar", "gz", "tgz", "7z", "rar", "xz", "zst" -> Icons.Default.FolderZip to PocketAmber
        "sh", "bash", "zsh", "bat", "cmd", "ps1" -> Icons.Default.Terminal to PocketTeal
        "keystore", "jks", "pem", "key", "p12", "crt" -> Icons.Default.Key to PocketRed
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun SkeletonRow(index: Int) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = index * 70), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val shade = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f * alpha)
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(shade))
        Spacer(Modifier.width(20.dp))
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(shade))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width((90 + (index * 37) % 110).dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shade))
            Box(Modifier.width(54.dp).height(9.dp).clip(RoundedCornerShape(6.dp)).background(shade))
        }
    }
}

@Composable
private fun EmptyFolder(isRoot: Boolean, onUpload: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 28.dp, end = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(PocketOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = PocketOrange)
        }
        Spacer(Modifier.height(14.dp))
        Text(if (isRoot) "No files yet" else "This folder is empty", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (isRoot) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Ask your coding agent to create something in this project, or upload your own files.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onUpload) {
            Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Upload files here")
        }
    }
}

@Composable
private fun SuggestedRootCard(root: String, onUse: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Project folder detected", fontWeight = FontWeight.Bold)
            Text(
                "Use $root as the project root so Chat, Terminal, Changes, and Preview all run from the same folder.",
                fontSize = 13.sp,
            )
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("Use $root as project root") }
        }
    }
}

@Composable
private fun BuildOutputsCard(artifacts: List<WorkspaceEntry>, modifier: Modifier, onSave: (WorkspaceEntry) -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PocketGreen.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, PocketGreen.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp)) {
            Text("Built APKs", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PocketGreen)
            artifacts.take(3).forEach { artifact ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, Modifier.size(20.dp), tint = PocketGreen)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artifact.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            entryDetails(artifact),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onSave(artifact) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PocketGreen.copy(alpha = 0.18f),
                            contentColor = PocketGreen,
                        ),
                    ) {
                        Icon(Icons.Default.SaveAlt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Selection bar and export sheet
// ---------------------------------------------------------------------------------------------

@Composable
private fun SelectionBar(selection: Collection<WorkspaceEntry>, onClear: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear selection") }
            Column(Modifier.weight(1f).clickable(onClick = onExport)) {
                AnimatedContent(
                    targetState = selection.size,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn())
                            .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "selectedCount",
                ) { count ->
                    Text("$count selected", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Text(
                    selectionSummary(selection),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onExport, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Icon(Icons.Default.FolderZip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export ZIP")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    target: ExportTarget,
    projectSlug: String,
    zipName: String,
    selection: List<WorkspaceEntry>,
    includeBuildFiles: Boolean,
    onIncludeBuildFilesChange: (Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(
                when (target) {
                    ExportTarget.PROJECT -> "Download whole project"
                    ExportTarget.SELECTION -> if (selection.size == 1) "Export 1 item" else "Export ${selection.size} items"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(zipName, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            when (target) {
                ExportTarget.PROJECT -> Row(verticalAlignment = Alignment.CenterVertically) {
                    FileTypeBadge(WorkspaceEntry(path = projectSlug, name = projectSlug, isDirectory = true, depth = 0))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Every file and folder in $projectSlug", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Packed in a $projectSlug/ folder inside the ZIP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExportTarget.SELECTION -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(selection, key = { it.path }) { entry -> SelectedItemRow(entry) { onRemove(entry.path) } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include build outputs & caches", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (target == ExportTarget.SELECTION) {
                                "build, .gradle, .git, node_modules… inside the folders you picked. Folders you tick directly are always included."
                            } else {
                                "build, .gradle, .git, node_modules, APK files… Leave off to export only the source code."
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = includeBuildFiles, onCheckedChange = onIncludeBuildFilesChange)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = target == ExportTarget.PROJECT || selection.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose where to save", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SelectedItemRow(entry: WorkspaceEntry, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FileTypeBadge(entry, size = 34)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.path.substringBeforeLast('/', "").ifEmpty { "Project root" } + " · " + entryDetails(entry),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Remove ${entry.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rename, create, delete and upload
// ---------------------------------------------------------------------------------------------

/**
 * Asks for a file or folder name. Like desktop file managers, renaming a file preselects the name
 * without its extension unless [selectExtension]. Names in [takenNames] are refused up front.
 */
@Composable
private fun NameDialog(
    title: String,
    icon: ImageVector,
    initialName: String,
    confirmLabel: String,
    takenNames: Set<String>,
    selectExtension: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supportingText: String? = null,
) {
    val selectionEnd = initialName.lastIndexOf('.').takeIf { it > 0 && !selectExtension } ?: initialName.length
    var value by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, selectionEnd))) }
    val name = value.text.trim()
    val error = when {
        name.isEmpty() -> null
        name in takenNames -> "Something named $name already exists here"
        else -> WorkspaceFileOps.nameError(name)
    }
    val canConfirm = name.isNotEmpty() && error == null && name != initialName
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = (error ?: supportingText)?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(name) }),
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = canConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(entries: List<WorkspaceEntry>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val single = entries.singleOrNull()
    val folders = entries.count { it.isDirectory }
    val files = entries.size - folders
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (single != null) "Delete ${single.name}?" else "Delete ${entries.size} items?") },
        text = {
            Text(
                when {
                    single?.isDirectory == true -> "The folder and everything inside it will be permanently deleted."
                    single != null -> "The file will be permanently deleted."
                    files == 0 -> "These $folders folders and everything inside them will be permanently deleted."
                    folders == 0 -> "These $files files will be permanently deleted."
                    else -> "${plural(folders, "folder")} and ${plural(files, "file")} will be permanently deleted, including everything inside the folders."
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks what to do with uploads whose names are taken; dismissing cancels the whole upload. */
@Composable
private fun UploadConflictDialog(conflict: UploadConflict, projectSlug: String, onChoice: (UploadConflictChoice?) -> Unit) {
    val names = conflict.existingNames
    val folder = conflict.directory.ifEmpty { projectSlug }
    AlertDialog(
        onDismissRequest = { onChoice(null) },
        icon = { Icon(Icons.Default.Upload, null) },
        title = { Text(if (names.size == 1) "Replace ${names.single()}?" else "Replace ${names.size} files?") },
        text = {
            Text(
                buildString {
                    if (names.size == 1) {
                        append("A file with this name already exists in $folder.")
                    } else {
                        append("These already exist in $folder: ${names.take(4).joinToString()}")
                        append(if (names.size > 4) " and ${names.size - 4} more." else ".")
                    }
                    append(" Keep both saves yours under a new name, like ${numberedName(names.first())}.")
                    if (conflict.otherFiles > 0) append(" The other ${plural(conflict.otherFiles, "file")} upload either way.")
                },
            )
        },
        confirmButton = { Button(onClick = { onChoice(UploadConflictChoice.REPLACE) }) { Text("Replace") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(if (conflict.otherFiles > 0) UploadConflictChoice.SKIP else null) }) {
                    Text(if (conflict.otherFiles > 0) "Skip" else "Cancel")
                }
                TextButton(onClick = { onChoice(UploadConflictChoice.KEEP_BOTH) }) { Text("Keep both") }
            }
        },
    )
}

@Composable
private fun UploadBar(upload: FileUploadState, projectSlug: String, onCancel: () -> Unit) {
    val fraction = when {
        upload.bytesTotal > 0 -> upload.bytesDone.toFloat() / upload.bytesTotal
        upload.filesTotal > 0 -> upload.filesDone.toFloat() / upload.filesTotal
        else -> 0f
    }.coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(240), label = "uploadProgress")
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Upload, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Uploading ${upload.fileName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            if (upload.filesTotal > 1) append("${minOf(upload.filesDone + 1, upload.filesTotal)} of ${upload.filesTotal} · ")
                            append("to ${upload.directory.ifEmpty { projectSlug }}")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                drawStopIndicator = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Export progress
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExportOverlay(export: FileExportState?, onCancel: () -> Unit, onDismiss: () -> Unit, onOpenLocation: () -> Unit) {
    // Keep showing the last state while the overlay fades out.
    var last by remember { mutableStateOf(export) }
    if (export != null) last = export
    val running = export?.phase == FileExportPhase.SCANNING || export?.phase == FileExportPhase.WRITING
    BackHandler(enabled = export != null) { if (running) onCancel() else onDismiss() }
    AnimatedVisibility(visible = export != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
        val shown = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!running) onDismiss() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = 0.75f), initialScale = 0.9f),
                        exit = scaleOut(targetScale = 0.95f),
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExportStatus(shown, onCancel, onDismiss, onOpenLocation)
                }
            }
        }
    }
}

@Composable
private fun ExportStatus(export: FileExportState, onCancel: () -> Unit, onDismiss: () -> Unit, onOpenLocation: () -> Unit) {
    val (icon, tint) = when (export.phase) {
        FileExportPhase.DONE -> Icons.Default.Check to PocketGreen
        FileExportPhase.FAILED -> Icons.Default.ErrorOutline to PocketRed
        else -> (if (export.isArchive) Icons.Default.FolderZip else Icons.Default.SaveAlt) to MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = tint)
    }
    Spacer(Modifier.height(14.dp))
    Text(
        when (export.phase) {
            FileExportPhase.SCANNING -> "Collecting files"
            FileExportPhase.WRITING -> if (export.isArchive) "Creating ZIP" else "Saving file"
            FileExportPhase.DONE -> if (export.isArchive) "ZIP saved" else "File saved"
            FileExportPhase.FAILED -> "Export failed"
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        export.fileName,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    when (export.phase) {
        FileExportPhase.SCANNING -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(10.dp))
            Text(
                if (export.filesTotal == 0) "Scanning project…" else "%,d files found".format(Locale.US, export.filesTotal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        FileExportPhase.WRITING -> {
            val fraction = when {
                export.bytesTotal > 0 -> export.bytesDone.toFloat() / export.bytesTotal
                export.filesTotal > 0 -> export.filesDone.toFloat() / export.filesTotal
                else -> 0f
            }.coerceIn(0f, 1f)
            val animated by animateFloatAsState(fraction, tween(240), label = "exportProgress")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(animated * 100).roundToInt()}%",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatBytes(export.bytesDone)} of ${formatBytes(export.bytesTotal)}",
                    Modifier.padding(bottom = 5.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(10.dp))
            if (export.isArchive) {
                Text(
                    "%,d of %,d files".format(Locale.US, export.filesDone, export.filesTotal),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (export.currentPath.isNotEmpty()) {
                Text(
                    shortenPath(export.currentPath),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        FileExportPhase.DONE -> {
            Text(
                buildString {
                    if (export.isArchive) append("%,d files · ".format(Locale.US, export.filesDone))
                    append(formatBytes(export.bytesTotal))
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (export.skippedFiles > 0) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = PocketAmber)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${export.skippedFiles} unreadable ${if (export.skippedFiles == 1) "file was" else "files were"} skipped",
                        fontSize = 12.sp,
                        color = PocketAmber,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Done")
                }
                if (export.documentUri != null) {
                    Button(onClick = onOpenLocation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open folder", maxLines = 1)
                    }
                }
            }
        }
        FileExportPhase.FAILED -> {
            Text(
                export.error ?: "Something went wrong while writing the file.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** ACTION_CREATE_DOCUMENT with a per-launch file name and MIME type: input is (name, mimeType). */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Ticks or unticks [entry]; ticking a folder drops anything inside it that it now covers. */
private fun toggled(selection: Map<String, WorkspaceEntry>, entry: WorkspaceEntry): Map<String, WorkspaceEntry> =
    if (entry.path in selection) selection - entry.path else withAdded(selection, listOf(entry))

private fun withAdded(selection: Map<String, WorkspaceEntry>, added: List<WorkspaceEntry>): Map<String, WorkspaceEntry> {
    val merged = selection + added.associateBy { it.path }
    val keep = WorkspaceFileOps.normalizeSelection(merged.keys).toSet()
    return merged.filterKeys { it in keep }
}

private fun selectionSummary(selection: Collection<WorkspaceEntry>): String {
    val folders = selection.count { it.isDirectory }
    val files = selection.size - folders
    val fileBytes = selection.filter { !it.isDirectory }.sumOf { it.sizeBytes }
    return buildList {
        if (folders > 0) add(if (folders == 1) "1 folder" else "$folders folders")
        if (files > 0) add((if (files == 1) "1 file" else "$files files") + " (${formatBytes(fileBytes)})")
    }.joinToString(" · ")
}

private fun entryDetails(entry: WorkspaceEntry): String =
    if (entry.isDirectory) {
        when (entry.childCount) {
            0 -> "Empty folder"
            1 -> "1 item"
            else -> "%,d items".format(Locale.US, entry.childCount)
        }
    } else {
        listOf(formatBytes(entry.sizeBytes), relativeTime(entry.modifiedMillis)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

private fun mimeTypeFor(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "aab" -> "application/octet-stream"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}

private fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

/** How "Keep both" renames an upload: `config.json` becomes `config-2.json`, as [WorkspaceFileOps.uniqueName] does. */
private fun numberedName(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) "${name.substring(0, dot)}-2${name.substring(dot)}" else "$name-2"
}

private fun shortenPath(path: String, max: Int = 46): String =
    if (path.length <= max) path else "…" + path.takeLast(max - 1)

private fun relativeTime(millis: Long): String =
    if (millis <= 0L) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(Locale.US, bytes / 1_024.0)
    bytes < 1_073_741_824 -> "%.1f MB".format(Locale.US, bytes / 1_048_576.0)
    else -> "%.2f GB".format(Locale.US, bytes / 1_073_741_824.0)
}
