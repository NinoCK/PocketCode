package com.jarves.mh.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarves.mh.model.ActivityItem
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ChatAttachment
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.DiffLine
import com.jarves.mh.model.DiffLineType
import com.jarves.mh.model.FileExportPhase
import com.jarves.mh.model.ProjectChat
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ToolRequest
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.ui.theme.LocalPocketExtraColors
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class WorkspaceTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHAT("Chat", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    FILES("Files", Icons.Outlined.Folder, Icons.Rounded.Folder),
    TERMINAL("Terminal", Icons.Outlined.Terminal, Icons.Rounded.Terminal),
    CHANGES("Changes", Icons.Rounded.Code, Icons.Rounded.Code),
    PREVIEW("Preview", Icons.Outlined.Preview, Icons.Rounded.Preview),
}

/** Tabs shown in the workspace bar. Changes is reviewed elsewhere and stays hidden. */
private val VisibleWorkspaceTabs = WorkspaceTab.entries.filter { it != WorkspaceTab.CHANGES }

// ---------------------------------------------------------------------------------------------
// Workspace shell
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WorkspaceScreen(
    state: AppUiState,
    onSetClaudeModel: (String) -> Unit = {},
    onSetClaudeEffort: (String) -> Unit = {},
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onApproval: (Boolean) -> Unit,
    onRefreshFiles: () -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onCloseFile: () -> Unit,
    onUndoChanges: () -> Unit,
    onKeepChanges: () -> Unit,
    onUndoFileChange: (String) -> Unit,
    onKeepFileChange: (String) -> Unit,
    onCreateChat: () -> Unit,
    onSwitchChat: (String) -> Unit,
    onTerminalRun: (String) -> Unit,
    onTerminalInput: (String) -> Unit,
    onTerminalInterrupt: () -> Unit,
    onTerminalPrepare: (String) -> Unit,
    onTerminalDraftConsumed: () -> Unit,
    onTerminalOpened: () -> Unit,
    onTerminalStop: () -> Unit,
    onTerminalClear: () -> Unit,
    onTerminalConfirm: () -> Unit,
    onTerminalCancel: () -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    onExportProject: (Uri, Boolean) -> Unit,
    onExportSelection: (Uri, Boolean) -> Unit,
    onSaveFile: (Uri, String) -> Unit,
    onCancelExport: () -> Unit,
    onDismissExport: () -> Unit,
    onOpenExportLocation: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onSelectionChange: (Map<String, WorkspaceEntry>) -> Unit,
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onExtractZip: (Uri, String, Boolean) -> Unit,
    onImportZipProject: (Uri) -> Unit,
    onBuildAndRunAndroid: () -> Unit,
    fileActions: FileManagerActions = FileManagerActions(),
    initialTab: WorkspaceTab = WorkspaceTab.CHAT,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onAddAttachments)
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MaxChatAttachments), onAddAttachments)
    var pendingZip by rememberSaveable { mutableStateOf<Uri?>(null) }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingZip = uri }
    var showAttachSheet by rememberSaveable { mutableStateOf(false) }
    val unknownAppsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            onBuildAndRunAndroid()
        } else {
            Toast.makeText(context, "Allow app installs to run Android projects", Toast.LENGTH_LONG).show()
        }
    }
    val chatListState = rememberLazyListState()
    var userScrolledUp by rememberSaveable { mutableStateOf(false) }
    val chatItemCount = state.messages.size +
        (if (state.liveProcess.isNotEmpty() || state.liveThinking || state.awaitingAgent) 1 else 0) +
        (if (state.pendingApproval != null) 1 else 0)

    LaunchedEffect(state.activeChatId) {
        userScrolledUp = false
        if (chatItemCount > 0) chatListState.scrollToItem(chatItemCount - 1)
    }
    // Pause auto-follow while the user scrolls up to read; resume once they are back at the bottom.
    LaunchedEffect(chatListState.isScrollInProgress) {
        if (chatListState.isScrollInProgress) {
            if (chatListState.canScrollForward) userScrolledUp = true
        } else if (!chatListState.canScrollForward) {
            userScrolledUp = false
        }
    }
    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.text?.length,
        state.liveProcess.size,
        state.liveProcess.lastOrNull()?.detail,
        state.pendingApproval,
    ) {
        if (!state.isRunning || chatItemCount <= 0 || userScrolledUp || chatListState.isScrollInProgress) return@LaunchedEffect
        if (!chatListState.canScrollForward) chatListState.scrollToItem(chatItemCount - 1)
    }

    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showChats by rememberSaveable { mutableStateOf(false) }
    // Folder chips (extracted ZIPs) open in the Files tab; files open in another app.
    val openAttachment: (ChatAttachment) -> Unit = { attachment ->
        if (attachment.isDirectory) {
            selectedTab = WorkspaceTab.FILES
            onOpenDirectory(attachment.relativePath)
        } else {
            onOpenAttachment(attachment)
        }
    }

    if (showAttachSheet) {
        AttachSourceSheet(
            canAddFiles = state.pendingAttachments.size < MaxChatAttachments,
            onDismiss = { showAttachSheet = false },
            onPhotos = {
                showAttachSheet = false
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = {
                showAttachSheet = false
                fileLauncher.launch(arrayOf("*/*"))
            },
            onZip = {
                showAttachSheet = false
                zipLauncher.launch(ZipPickerMimeTypes)
            },
        )
    }
    pendingZip?.let { uri ->
        ZipImportSheet(
            uri = uri,
            guestRoot = "/workspace/${state.activeProject?.slug.orEmpty()}",
            filesTabDir = state.workspaceCurrentDir,
            canImportAsProject = !state.isRunning && !state.projectTerminalRunning && !state.projectImporting,
            onDismiss = { pendingZip = null },
            onExtract = { parentDir, merge ->
                pendingZip = null
                onExtractZip(uri, parentDir, merge)
            },
            onImportAsProject = {
                pendingZip = null
                onImportZipProject(uri)
            },
        )
    }
    val activeChat = state.projectChats.firstOrNull { it.id == state.activeChatId }
    val motion = MaterialTheme.motionScheme

    if (showChats) {
        ChatSwitcherSheet(
            chats = state.projectChats,
            activeChatId = state.activeChatId,
            switchingEnabled = !state.isRunning,
            onDismiss = { showChats = false },
            onCreate = {
                onCreateChat()
                showChats = false
                selectedTab = WorkspaceTab.CHAT
            },
            onSwitch = { chatId ->
                onSwitchChat(chatId)
                showChats = false
                selectedTab = WorkspaceTab.CHAT
            },
        )
    }
    state.pendingTerminalCommand?.let { command ->
        AlertDialog(
            onDismissRequest = onTerminalCancel,
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Run potentially destructive command?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This command can delete files, rewrite Git history, or change the project significantly.")
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp)) {
                        Text(command, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onTerminalConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Run anyway") }
            },
            dismissButton = { TextButton(onClick = onTerminalCancel) { Text("Cancel") } },
        )
    }

    // The file viewer slides over the workspace; tab, scroll position and chat state live above it.
    AnimatedContent(
        targetState = state.openedFilePath,
        contentKey = { it != null },
        transitionSpec = {
            val opening = targetState != null
            (slideInHorizontally(motion.defaultSpatialSpec()) { if (opening) it / 5 else -it / 5 } + fadeIn(motion.defaultEffectsSpec()))
                .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { if (opening) -it / 5 else it / 5 } + fadeOut(motion.fastEffectsSpec()))
        },
        label = "fileViewer",
    ) { openedPath ->
        if (openedPath != null) {
            BackHandler {
                onCloseFile()
                selectedTab = WorkspaceTab.FILES
            }
            FileViewerScreen(
                filePath = openedPath,
                content = state.openedFileContent,
                loading = state.fileContentLoading,
                binary = state.openedFileBinary,
                notice = state.openedFileNotice,
                editable = state.openedFileEditable,
                draft = state.fileDraft,
                saving = state.fileSaving,
                saveConflict = state.fileSaveConflict,
                onClose = {
                    onCloseFile()
                    selectedTab = WorkspaceTab.FILES
                },
                onStartEditing = fileActions.onStartEditing,
                onStopEditing = fileActions.onStopEditing,
                onSave = fileActions.onSaveEdits,
                onDismissSaveConflict = fileActions.onDismissSaveConflict,
            )
            return@AnimatedContent
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            state.activeProject?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { Toast.makeText(context, state.activeProject?.name.orEmpty(), Toast.LENGTH_LONG).show() },
                            ),
                        )
                    },
                    subtitle = {
                        Text(
                            "${activeChat?.title ?: "Chat"} · ${if (state.agentKind == AgentKind.ANTIGRAVITY) state.agentKind.title else state.provider.kind.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Projects") } },
                    actions = {
                        AnimatedVisibility(visible = state.isRunning, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            LoadingIndicator(Modifier.size(32.dp))
                        }
                        if (state.androidProjectDetected) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                        unknownAppsLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                    } else {
                                        onBuildAndRunAndroid()
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                                enabled = !state.androidBuildRunning && !state.isRunning && !state.projectTerminalRunning,
                            ) {
                                if (state.androidBuildRunning) LoadingIndicator(Modifier.size(24.dp))
                                else Icon(Icons.Rounded.PlayArrow, "Build and run Android app")
                            }
                        }
                        IconButton(onClick = { showChats = true }) { Icon(Icons.Rounded.History, "Project chats") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!keyboardVisible) {
                    ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        VisibleWorkspaceTabs.forEach { tab ->
                            val selected = selectedTab == tab
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = {
                                    selectedTab = tab
                                    if (tab == WorkspaceTab.FILES) onRefreshFiles()
                                    if (tab == WorkspaceTab.TERMINAL) onTerminalOpened()
                                },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "workspaceTabs",
            ) { tab ->
                when (tab) {
                    WorkspaceTab.CHAT -> ChatTab(
                        messages = state.messages,
                        approval = state.pendingApproval,
                        liveProcess = state.liveProcess,
                        isRunning = state.isRunning,
                        onSend = onSend,
                        onStop = onStop,
                        onApproval = onApproval,
                        listState = chatListState,
                        taskStartedAtMillis = state.workSegmentStartedAtMillis ?: state.taskStartedAtMillis,
                        thinkingActive = state.liveThinking,
                        awaitingAgent = state.awaitingAgent,
                        agentKind = state.agentKind,
                        pendingAttachments = state.pendingAttachments,
                        archiveProgress = state.archiveExtractionMessage ?: state.projectImportMessage?.takeIf { state.projectImporting },
                        onAttach = { showAttachSheet = true },
                        onRemoveAttachment = onRemoveAttachment,
                        onOpenAttachment = openAttachment,
                        onRunInTerminal = { command ->
                            selectedTab = WorkspaceTab.TERMINAL
                            onTerminalOpened()
                            onTerminalPrepare(command)
                        },
                        claudeOptions = if (state.agentKind == AgentKind.CLAUDE_CODE && state.provider.kind == ProviderKind.CLAUDE) {
                            ClaudeChatOptions(state.provider.model, state.claudeEffort, onSetClaudeModel, onSetClaudeEffort)
                        } else {
                            null
                        },
                    )
                    WorkspaceTab.FILES -> WorkspaceFilesTab(
                        files = state.workspaceFiles,
                        currentDir = state.workspaceCurrentDir,
                        selection = state.workspaceSelection,
                        artifacts = state.workspaceArtifacts,
                        loading = state.filesLoading,
                        export = state.fileExport,
                        exportBlockedReason = when {
                            state.fileExport?.phase.let { it == FileExportPhase.SCANNING || it == FileExportPhase.WRITING } -> "An export is already running"
                            state.isRunning || state.projectTerminalRunning -> "Stop the running task before exporting"
                            else -> null
                        },
                        projectSlug = state.activeProject?.slug ?: "project",
                        suggestedProjectRoot = state.suggestedProjectRoot,
                        onRefresh = onRefreshFiles,
                        onOpenDirectory = onOpenDirectory,
                        onSelectionChange = onSelectionChange,
                        onOpenFile = onOpenFile,
                        onUseSuggestedProjectRoot = onUseSuggestedProjectRoot,
                        onExportProject = onExportProject,
                        onExportSelection = onExportSelection,
                        onSaveFile = onSaveFile,
                        onCancelExport = onCancelExport,
                        onDismissExport = onDismissExport,
                        onOpenExportLocation = onOpenExportLocation,
                        upload = state.fileUpload,
                        uploadConflict = state.uploadConflict,
                        changeBlockedReason = state.fileChangesBlockedReason,
                        actions = fileActions,
                    )
                    WorkspaceTab.TERMINAL -> TerminalScreen(
                        lines = state.projectTerminalLines,
                        isRunning = state.projectTerminalRunning,
                        onRun = onTerminalRun,
                        onInput = onTerminalInput,
                        onInterrupt = onTerminalInterrupt,
                        onClear = onTerminalClear,
                        onToggleTheme = {},
                        themeMode = state.themeMode,
                        title = "Project terminal",
                        subtitle = "${state.projectTerminalCwd} · Ubuntu PRoot",
                        liveOutput = state.projectTerminalLiveOutput,
                        currentCommand = state.projectTerminalCommand,
                        commandDraft = state.projectTerminalDraft,
                        onCommandDraftConsumed = onTerminalDraftConsumed,
                        promptPath = state.projectTerminalCwd,
                        onStop = onTerminalStop,
                        showThemeAction = false,
                        showQuickCommands = false,
                        compactHeader = true,
                    )
                    WorkspaceTab.CHANGES -> ChangesTab(state.changes, onUndoChanges, onKeepChanges, onUndoFileChange, onKeepFileChange)
                    WorkspaceTab.PREVIEW -> PreviewTab(state.previewReady, state.previewUrl)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Read-only history (another project is busy)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReadOnlyProjectScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSwitchChat: (String) -> Unit,
    onContinueHere: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val project = state.readOnlyProject ?: return
    val activeChat = state.readOnlyProjectChats.firstOrNull { it.id == state.readOnlyChatId }
    val listState = rememberLazyListState()
    var showChats by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.readOnlyChatId) {
        if (state.readOnlyMessages.isNotEmpty()) listState.scrollToItem(state.readOnlyMessages.lastIndex)
    }
    if (showChats) {
        ChatSwitcherSheet(
            chats = state.readOnlyProjectChats,
            activeChatId = state.readOnlyChatId,
            switchingEnabled = true,
            allowCreate = false,
            onDismiss = { showChats = false },
            onCreate = {},
            onSwitch = { chatId ->
                onSwitchChat(chatId)
                showChats = false
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text("${activeChat?.title ?: "Chat"} · History", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Projects") } },
                actions = { IconButton(onClick = { showChats = true }) { Icon(Icons.Rounded.History, "Project chats") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ChatTab(
                messages = state.readOnlyMessages,
                approval = null,
                liveProcess = emptyList(),
                isRunning = false,
                onSend = {},
                onStop = {},
                onApproval = {},
                listState = listState,
                taskStartedAtMillis = null,
                thinkingActive = false,
                agentKind = state.agentKind,
                pendingAttachments = emptyList(),
                onAttach = {},
                onRemoveAttachment = {},
                onOpenAttachment = {},
                onRunInTerminal = {},
                readOnly = true,
                readOnlyBlocked = state.isRunning || state.projectTerminalRunning,
                onContinueHere = onContinueHere,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chat switcher
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatSwitcherSheet(
    chats: List<ProjectChat>,
    activeChatId: String?,
    switchingEnabled: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onSwitch: (String) -> Unit,
    allowCreate: Boolean = true,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.padding(start = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (allowCreate) {
                    Button(onClick = onCreate, enabled = switchingEnabled, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New chat")
                    }
                }
            }
            if (!switchingEnabled) {
                Text(
                    "Finish the running task before switching chats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                    val active = chat.id == activeChatId
                    GroupedRow(
                        index = index,
                        count = chats.size,
                        headline = chat.title,
                        supporting = if (active) "Current chat" else "Saved conversation",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = if (switchingEnabled) ({ onSwitch(chat.id) }) else null,
                        container = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        trailing = { if (active) Icon(Icons.Rounded.Check, "Current", tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------------------------

private val ChatSuggestions = listOf(
    "Explain this project",
    "Add a README",
    "Find and fix bugs",
    "Write unit tests",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatTab(
    messages: List<ChatMessage>,
    approval: ToolRequest?,
    liveProcess: List<ActivityItem>,
    isRunning: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onApproval: (Boolean) -> Unit,
    listState: LazyListState,
    taskStartedAtMillis: Long?,
    thinkingActive: Boolean,
    agentKind: AgentKind,
    pendingAttachments: List<ChatAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onRunInTerminal: (String) -> Unit,
    archiveProgress: String? = null,
    readOnly: Boolean = false,
    readOnlyBlocked: Boolean = false,
    onContinueHere: () -> Unit = {},
    claudeOptions: ClaudeChatOptions? = null,
    awaitingAgent: Boolean = false,
) {
    val view = LocalView.current
    // Keep the screen on while the agent works in this chat; released when it finishes or the tab closes.
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
        onDispose { view.keepScreenOn = false }
    }
    var prompt by rememberSaveable { mutableStateOf("") }
    val chatScope = rememberCoroutineScope()
    val readerAtBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val showLive = liveProcess.isNotEmpty() || thinkingActive || awaitingAgent
    val conversationEmpty = messages.isEmpty() && !showLive && approval == null

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f)) {
            if (conversationEmpty) {
                ChatEmptyState(
                    agentName = agentKind.title,
                    suggestions = if (readOnly) emptyList() else ChatSuggestions,
                    onSuggestion = { prompt = it },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        if (message.workItems.isNotEmpty()) {
                            WorkBlock(
                                items = message.workItems,
                                headline = "Worked for ${formatDuration((message.workedMillis / 1_000L).coerceAtLeast(1L))}",
                                running = false,
                                stopped = message.workItems.lastOrNull()?.title?.startsWith("Task stopped") == true,
                                modifier = Modifier.animateItem(),
                            )
                        } else {
                            MessageBubble(
                                message = message,
                                onRunInTerminal = onRunInTerminal,
                                onOpenAttachment = onOpenAttachment,
                                showWorkedFor = messages.getOrNull(index - 1)?.workItems.isNullOrEmpty(),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (showLive) {
                        item(key = "live-process") {
                            val elapsed = taskStartedAtMillis?.let { rememberLiveElapsedSeconds(it).toLong() } ?: 0L
                            WorkBlock(
                                items = liveProcess,
                                headline = if (isRunning) "Working · ${formatDuration(elapsed)}" else "Finished · ${formatDuration(elapsed)}",
                                running = isRunning,
                                stopped = false,
                                thinking = thinkingActive,
                                idleStatus = "Waiting for ${agentKind.title}",
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    approval?.let { request -> item(key = "approval") { ApprovalCard(request, onApproval, Modifier.animateItem()) } }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !readerAtBottom && !conversationEmpty,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                FilledTonalButton(
                    onClick = { chatScope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
                    shapes = ButtonDefaults.shapes(),
                    elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 3.dp),
                ) {
                    Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Latest")
                }
            }
        }
        if (readOnly) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Read-only history", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (readOnlyBlocked) "Another project has a running task. You can read this chat but not send messages."
                            else "The other task finished. Open this project to keep chatting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!readOnlyBlocked) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = onContinueHere, shapes = ButtonDefaults.shapes()) { Text("Open") }
                    }
                }
            }
        } else {
            Composer(
                prompt = prompt,
                onPromptChange = { prompt = it },
                agentName = agentKind.title,
                isRunning = isRunning,
                pendingAttachments = pendingAttachments,
                archiveProgress = archiveProgress,
                claudeOptions = claudeOptions,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment,
                onSend = {
                    onSend(prompt)
                    prompt = ""
                },
                onStop = onStop,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatEmptyState(agentName: String, suggestions: List<String>, onSuggestion: (String) -> Unit) {
    ExpressiveEmptyState(
        icon = Icons.Rounded.AutoAwesome,
        title = "What should we build?",
        body = "Ask $agentName to write code, fix bugs, or explain this project.",
        polygon = MaterialShapes.Flower,
    ) {
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onSuggestion(suggestion) },
                        label = { Text(suggestion) },
                        shape = RoundedCornerShape(16.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Composer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    agentName: String,
    isRunning: Boolean,
    pendingAttachments: List<ChatAttachment>,
    archiveProgress: String?,
    claudeOptions: ClaudeChatOptions?,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val canSend = prompt.isNotBlank() || pendingAttachments.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)) {
        AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingAttachments.forEach { attachment ->
                    AttachmentChip(attachment = attachment, onOpen = null, onRemove = { onRemoveAttachment(attachment.id) })
                }
            }
        }
        AnimatedVisibility(visible = archiveProgress != null) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    archiveProgress.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (claudeOptions != null) {
            ClaudeChatOptionsChip(options = claudeOptions, enabled = !isRunning, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = !isRunning && archiveProgress == null) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = "Add photos, files or a ZIP",
                        tint = if (pendingAttachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp).padding(horizontal = 4.dp, vertical = 13.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (prompt.isEmpty()) {
                                Text("Message $agentName…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(4.dp))
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                    label = "sendStop",
                ) { running ->
                    if (running) {
                        FilledIconButton(
                            onClick = onStop,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Rounded.Stop, "Stop task") }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = canSend,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Rounded.ArrowUpward, "Send") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRunInTerminal: (String) -> Unit,
    onOpenAttachment: (ChatAttachment) -> Unit,
    showWorkedFor: Boolean,
    modifier: Modifier = Modifier,
) {
    if (message.fromUser) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.text.isNotBlank()) {
                        SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                    }
                    message.attachments.forEach { attachment ->
                        AttachmentChip(attachment = attachment, onOpen = { onOpenAttachment(attachment) }, onRemove = null)
                    }
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                MarkdownText(markdown = message.text, color = MaterialTheme.colorScheme.onSurface, onRunCode = onRunInTerminal)
            }
            message.attachments.forEach { attachment ->
                AttachmentChip(attachment = attachment, onOpen = { onOpenAttachment(attachment) }, onRemove = null)
            }
            if (showWorkedFor && message.workedMillis > 0L) {
                Text(
                    "Worked for ${formatDuration((message.workedMillis / 1_000L).coerceAtLeast(1L))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: ChatAttachment, onOpen: (() -> Unit)?, onRemove: (() -> Unit)?) {
    val icon = when {
        attachment.isDirectory -> Icons.Rounded.Folder
        attachment.mimeType.startsWith("image/") -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }
    Surface(
        modifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = if (onRemove == null) 12.dp else 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 180.dp)) {
                Text(attachment.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (attachment.isDirectory) "Folder · ${formatFileSize(attachment.sizeBytes)}" else formatFileSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Remove attachment", Modifier.size(16.dp)) }
            }
        }
    }
}

/**
 * The agent's steps for one turn. Finished work collapses to a one-line summary; live work stays open
 * and marks the step in progress with a loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkBlock(
    items: List<ActivityItem>,
    headline: String,
    running: Boolean,
    stopped: Boolean,
    modifier: Modifier = Modifier,
    thinking: Boolean = false,
    idleStatus: String = "Preparing",
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = running || expanded
    val latest = items.lastOrNull()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !running) { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    LoadingIndicator(Modifier.size(36.dp))
                } else {
                    ShapeBadge(
                        icon = if (stopped) Icons.Rounded.Stop else Icons.Rounded.Check,
                        polygon = MaterialShapes.Cookie9Sided,
                        size = 36.dp,
                        container = if (stopped) MaterialTheme.colorScheme.errorContainer else LocalPocketExtraColors.current.successContainer,
                        content = if (stopped) MaterialTheme.colorScheme.onErrorContainer else LocalPocketExtraColors.current.onSuccessContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (stopped) "Stopped · ${headline.removePrefix("Worked for ")}" else headline, style = MaterialTheme.typography.titleSmall)
                    val preview = when {
                        thinking && latest == null -> "Thinking…"
                        latest != null -> compactActivityText(latest)
                        else -> null
                    }
                    if (preview != null && !open) {
                        Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (items.isNotEmpty()) {
                        Text(
                            "${items.size} step${if (items.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!running && items.isNotEmpty()) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Hide steps" else "Show steps", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (open) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Harness status, not agent output: nothing here is recorded as a step.
                    if (items.isEmpty()) {
                        ActivityStepRow(null, if (thinking) "Thinking…" else "$idleStatus…", inProgress = running)
                    }
                    items.forEachIndexed { index, item ->
                        ActivityStepRow(item, compactActivityText(item), inProgress = running && !item.isComplete && index == items.lastIndex)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActivityStepRow(item: ActivityItem?, text: String, inProgress: Boolean) {
    var showDetail by rememberSaveable { mutableStateOf(false) }
    val detail = item?.let(::activityDetail)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = detail != null) { showDetail = !showDetail }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (inProgress) LoadingIndicator(Modifier.size(22.dp))
                else Icon(activityIcon(item), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                // A null item is a harness status line; keep it visually apart from agent steps.
                fontStyle = if (item == null) FontStyle.Italic else null,
                color = if (inProgress && item != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (showDetail) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = showDetail && detail != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            if (item?.isCommand == true) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(detail.orEmpty(), Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
                }
            } else {
                MarkdownText(
                    markdown = detail.orEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApprovalCard(request: ToolRequest, onApproval: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    icon = Icons.Rounded.Shield,
                    polygon = MaterialShapes.Pentagon,
                    size = 44.dp,
                    container = MaterialTheme.colorScheme.tertiary,
                    content = MaterialTheme.colorScheme.onTertiary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Review this action", style = MaterialTheme.typography.titleMedium)
                    Text(request.toolName, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(request.explanation, style = MaterialTheme.typography.bodyMedium)
            request.affectedPaths.forEach { path ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)) {
                    Text(path, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onApproval(false) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Reject") }
                Button(onClick = { onApproval(true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Allow once") }
            }
        }
    }
}

private fun activityIcon(item: ActivityItem?): ImageVector {
    if (item == null) return Icons.Rounded.AutoAwesome
    val task = item.title.removePrefix("Running ").removeSuffix(" completed").trim()
    return when {
        item.isCommand || task.equals("Bash", ignoreCase = true) -> Icons.Rounded.Terminal
        task.equals("Write", ignoreCase = true) || task.equals("Edit", ignoreCase = true) || task.equals("NotebookEdit", ignoreCase = true) -> Icons.Rounded.Edit
        task.equals("Read", ignoreCase = true) -> Icons.Rounded.Description
        task.equals("Glob", ignoreCase = true) || task.equals("Grep", ignoreCase = true) -> Icons.Rounded.Search
        task.contains("file", ignoreCase = true) -> Icons.Rounded.Description
        else -> Icons.Rounded.AutoAwesome
    }
}

private fun compactActivityText(item: ActivityItem): String =
    "${activityName(item)} · ${activityDetail(item).replace(Regex("\\s+"), " ").take(105)}"

private fun activityDetail(item: ActivityItem): String {
    if (item.title == "Think" && item.detail.contains("reasoning tokens processed", true)) {
        return "Reviewed the request and planned the next action"
    }
    return item.detail.ifBlank { item.title }
}

private fun activityName(item: ActivityItem): String =
    item.title.removePrefix("Running ").removeSuffix(" completed").replaceFirstChar { it.uppercase() }

@Composable
private fun rememberLiveElapsedSeconds(startedAtMillis: Long): Int {
    var seconds by remember(startedAtMillis) {
        mutableIntStateOf(((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(0))
    }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            delay(1_000)
            seconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
        }
    }
    return seconds
}

private fun formatDuration(totalSeconds: Long): String = when {
    totalSeconds >= 3_600 -> "${totalSeconds / 3_600}h ${(totalSeconds % 3_600) / 60}m"
    totalSeconds >= 60 -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
    else -> "${totalSeconds}s"
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "%.1f MB".format(bytes / 1_048_576.0)
}

// ---------------------------------------------------------------------------------------------
// Changes (kept for the hidden Changes tab)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChangesTab(
    changes: List<ChangeItem>,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    onUndoFile: (String) -> Unit,
    onKeepFile: (String) -> Unit,
) {
    var expandedPath by rememberSaveable { mutableStateOf<String?>(null) }
    if (changes.isEmpty()) {
        ExpressiveEmptyState(Icons.Rounded.Code, "No changes yet", "Ask your agent to update the project.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(changes, key = { _, change -> change.path }) { index, change ->
            val expanded = expandedPath == change.path
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = groupedShape(index, changes.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.animateContentSize()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expandedPath = if (expanded) null else change.path }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(change.path, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (expanded) "Hide diff" else "Tap to review diff",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("+${change.additions}", color = LocalPocketExtraColors.current.success, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Text("-${change.deletions}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    if (expanded) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            change.diffLines.forEach { line -> DiffLineRow(line) }
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { expandedPath = null; onUndoFile(change.path) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                                Text("Undo file")
                            }
                            Button(onClick = { expandedPath = null; onKeepFile(change.path) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                                Text("Keep file")
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUndo, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Undo task") }
                Button(onClick = onKeep, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Keep changes") }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val success = LocalPocketExtraColors.current
    val marker = when (line.type) {
        DiffLineType.ADDITION -> "+"
        DiffLineType.DELETION -> "-"
        DiffLineType.CONTEXT -> " "
        DiffLineType.INFO -> "·"
    }
    val background = when (line.type) {
        DiffLineType.ADDITION -> success.successContainer.copy(alpha = 0.5f)
        DiffLineType.DELETION -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val foreground = when (line.type) {
        DiffLineType.ADDITION -> success.onSuccessContainer
        DiffLineType.DELETION -> MaterialTheme.colorScheme.onErrorContainer
        DiffLineType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }
    val oldNumber = line.oldLine?.toString().orEmpty().padStart(4)
    val newNumber = line.newLine?.toString().orEmpty().padStart(4)
    Text(
        text = "$oldNumber $newNumber  $marker ${line.text}",
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 8.dp, vertical = 2.dp),
        color = foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        softWrap = false,
    )
}

// ---------------------------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewTab(ready: Boolean, url: String?) {
    var address by rememberSaveable(url) { mutableStateOf(if (ready) url.orEmpty() else "") }
    var activeUrl by rememberSaveable(url) { mutableStateOf(if (ready) url else null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val navigate = {
        val normalized = normalizePreviewUrl(address)
        if (normalized == null) {
            addressError = "Use a local URL such as localhost:3000"
        } else {
            addressError = null
            address = normalized
            activeUrl = normalized
        }
    }
    LaunchedEffect(ready, url) {
        if (ready && !url.isNullOrBlank() && activeUrl == null) {
            normalizePreviewUrl(url)?.let {
                address = it
                activeUrl = it
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    addressError = null
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("localhost:3000") },
                shape = RoundedCornerShape(28.dp),
                leadingIcon = {
                    Box(
                        Modifier.size(10.dp).background(
                            if (activeUrl != null) LocalPocketExtraColors.current.success else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                    )
                },
                trailingIcon = { IconButton(onClick = navigate) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Open URL") } },
                isError = addressError != null,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
            )
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = { webView?.reload() ?: navigate() },
                enabled = address.isNotBlank(),
                shapes = IconButtonDefaults.shapes(),
            ) { Icon(Icons.Rounded.Refresh, "Refresh preview") }
        }
        AnimatedVisibility(visible = addressError != null || loading) {
            if (addressError != null) {
                Text(
                    addressError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 28.dp, bottom = 4.dp),
                )
            } else {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
        val targetUrl = activeUrl
        if (targetUrl == null) {
            ExpressiveEmptyState(
                Icons.Rounded.Preview,
                "Preview not running",
                "Enter a localhost URL above, or start a local web server in the project terminal.",
                polygon = MaterialShapes.Arch,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(bottom = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loading = newProgress < 100
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val target = request?.url ?: return true
                                    if (!target.isLoopbackPreviewUrl()) {
                                        addressError = "External navigation is blocked in project preview"
                                        return true
                                    }
                                    address = target.toString()
                                    return false
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val target = request?.url ?: return blockedPreviewResponse()
                                    return if (target.isLoopbackPreviewUrl()) null else blockedPreviewResponse()
                                }
                            }
                            loadUrl(targetUrl)
                        }
                    },
                    update = { current ->
                        webView = current
                        if (current.url != targetUrl) current.loadUrl(targetUrl)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun normalizePreviewUrl(input: String): String? {
    val raw = input.trim()
    if (raw.isBlank()) return null
    val withScheme = if ("://" in raw) raw else "http://$raw"
    val parsed = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
    if (!parsed.isLoopbackPreviewUrl() || parsed.host.isNullOrBlank()) return null
    return if (parsed.host == "0.0.0.0") {
        parsed.buildUpon().encodedAuthority(
            buildString {
                append("127.0.0.1")
                if (parsed.port >= 0) append(":${parsed.port}")
            },
        ).build().toString()
    } else {
        parsed.toString()
    }
}

private fun Uri.isLoopbackPreviewUrl(): Boolean =
    scheme in setOf("data", "blob", "about") ||
        (scheme in setOf("http", "https", "ws", "wss") && host in setOf("127.0.0.1", "localhost", "0.0.0.0"))

private fun blockedPreviewResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
