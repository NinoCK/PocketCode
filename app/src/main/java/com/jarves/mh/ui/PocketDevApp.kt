package com.jarves.mh.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import com.jarves.mh.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarves.mh.model.ActivityItem
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.ChatAttachment
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.DEEPSEEK_HARNESS_PROVIDERS
import com.jarves.mh.model.DSH_PROTOCOL_PROVIDERS
import com.jarves.mh.model.DiffLine
import com.jarves.mh.model.DiffLineType
import com.jarves.mh.model.Project
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProjectChat
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.inferredDshApiForUrl
import com.jarves.mh.model.providersForAgent
import com.jarves.mh.model.ToolRequest
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.model.FileExportPhase
import com.jarves.mh.model.projectSlug
import com.jarves.mh.runtime.RuntimeExecutionService
import com.jarves.mh.runtime.RuntimeSetupService
import com.jarves.mh.runtime.supportsArm64Runtime
import com.jarves.mh.runtime.AntigravityAuthStatus
import com.jarves.mh.runtime.ClaudeAuthState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jarves.mh.network.ConnectionValidation
import com.jarves.mh.network.DiscoveredModel
import com.jarves.mh.network.ModelDiscoveryResult
import com.jarves.mh.network.GitHubRepository
import com.jarves.mh.ui.theme.PocketBlue
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.jarves.mh.ui.theme.AppThemeMode
import com.jarves.mh.ui.theme.LocalDarkTheme
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExtendedFloatingActionButton

internal enum class RootScreen(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    PROJECTS("Projects", Icons.Outlined.Folder, Icons.Rounded.Folder),
    TERMINAL("Terminal", Icons.Outlined.Terminal, Icons.Rounded.Terminal),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Top-level screens. [depth] orders them for the forward/back shared-axis transition. */
private enum class AppDestination(val depth: Int) {
    LOADING(0),
    BACKGROUND_SETUP(0),
    RUNTIME_SETUP(0),
    RUNTIME_INSTALL(0),
    STARTUP_ERROR(0),
    ANTIGRAVITY_ONBOARDING(0),
    PROVIDER_SETUP(0),
    HOME(1),
    READ_ONLY_PROJECT(2),
    WORKSPACE(2),
}

private fun appDestination(state: AppUiState): AppDestination = when {
    state.startupStage == StartupStage.CHECKING -> AppDestination.LOADING
    !state.backgroundSetupComplete && state.startupStage == StartupStage.SETUP_REQUIRED -> AppDestination.BACKGROUND_SETUP
    state.startupStage == StartupStage.SETUP_REQUIRED -> AppDestination.RUNTIME_SETUP
    state.startupStage == StartupStage.INSTALLING && state.showDetailedSetupProgress -> AppDestination.RUNTIME_INSTALL
    state.startupStage == StartupStage.INSTALLING || state.startupStage == StartupStage.INITIALIZING -> AppDestination.LOADING
    state.startupStage == StartupStage.ERROR -> AppDestination.STARTUP_ERROR
    state.startupStage == StartupStage.MODEL_SETUP && state.agentKind == AgentKind.ANTIGRAVITY -> AppDestination.ANTIGRAVITY_ONBOARDING
    state.startupStage == StartupStage.MODEL_SETUP -> AppDestination.PROVIDER_SETUP
    state.startupStage == StartupStage.READY && !state.backgroundSetupComplete -> AppDestination.BACKGROUND_SETUP
    state.readOnlyProject != null -> AppDestination.READ_ONLY_PROJECT
    state.activeProject != null && state.workspaceVisible -> AppDestination.WORKSPACE
    else -> AppDestination.HOME
}

/** Sun/moon toggle that flips the visible theme relative to the device's dark-mode setting. */
@Composable
private fun rememberThemeToggle(viewModel: MainViewModel): () -> Unit {
    val systemDark = isSystemInDarkTheme()
    return remember(viewModel, systemDark) { { viewModel.toggleTheme(systemDark) } }
}

@Composable
fun PocketDevApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val projectsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeToast()
        }
    }
    val motion = MaterialTheme.motionScheme
    // Each screen keeps the state it was last shown with while it animates out.
    AnimatedContent(
        targetState = state,
        contentKey = ::appDestination,
        transitionSpec = {
            val from = appDestination(initialState)
            val to = appDestination(targetState)
            if (from.depth != to.depth) {
                // Shared axis: forward into a project, back out to the list.
                val forward = to.depth > from.depth
                (slideInHorizontally(motion.defaultSpatialSpec()) { width -> if (forward) width / 5 else -width / 5 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { width -> if (forward) -width / 5 else width / 5 } + fadeOut(motion.fastEffectsSpec()))
            } else {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            }
        },
        label = "appDestination",
    ) { state ->
        when (appDestination(state)) {
            AppDestination.LOADING -> StartupLoadingScreen(
                state = state,
                onToggleTheme = rememberThemeToggle(viewModel),
            )
            AppDestination.BACKGROUND_SETUP -> BackgroundTaskSetupScreen(
                // Users updating from a build without this flow already know the app.
                showWelcome = !state.onboardingComplete,
                onToggleTheme = rememberThemeToggle(viewModel),
                onContinue = viewModel::finishBackgroundSetup,
            )
            AppDestination.RUNTIME_SETUP -> RuntimeSetupPromptScreen(
                selectedStacks = state.selectedDevStacks,
                selectedAgent = state.agentKind,
                onToggleTheme = rememberThemeToggle(viewModel),
                onToggleStack = viewModel::toggleDevStack,
                onSelectAgent = viewModel::selectAgent,
                onDownload = viewModel::startRuntimeSetup,
            )
            AppDestination.RUNTIME_INSTALL -> RuntimeInstallationScreen(
                state = state,
                onToggleTheme = rememberThemeToggle(viewModel),
            )
            AppDestination.STARTUP_ERROR -> StartupErrorScreen(
                message = state.startupError,
                isOffline = state.startupErrorIsOffline,
                logs = state.startupLogs,
                onToggleTheme = rememberThemeToggle(viewModel),
                onRetry = viewModel::retryStartup,
            )
            AppDestination.ANTIGRAVITY_ONBOARDING -> AntigravityOnboardingScreen(
                state = state,
                onStartLogin = viewModel::startAntigravityLogin,
                onSubmitCode = viewModel::submitAntigravityCode,
                onContinue = viewModel::finishAntigravityOnboarding,
                onSelectAgent = viewModel::chooseOnboardingAgent,
                onToggleTheme = rememberThemeToggle(viewModel),
            )
            AppDestination.PROVIDER_SETUP -> ProviderSetupScreen(
                initial = state.provider,
                onboarding = true,
                agentKind = state.agentKind,
                initialStep = 1,
                onSave = viewModel::finishOnboarding,
                onDiscover = viewModel::discoverModels,
                onValidate = viewModel::validateProvider,
                onSelectAgent = viewModel::chooseOnboardingAgent,
                claudeAuth = state.claudeAuth,
                claudeSignIn = ClaudeSignInActions(
                    onStart = viewModel::startClaudeLogin,
                    onReopenBrowser = viewModel::reopenClaudeLogin,
                    onOpenManual = viewModel::openClaudeManualLogin,
                    onSubmitCode = viewModel::submitClaudeAuthCode,
                    onCancel = viewModel::cancelClaudeLogin,
                ),
                onToggleTheme = rememberThemeToggle(viewModel),
            )
            AppDestination.READ_ONLY_PROJECT -> ReadOnlyProjectScreen(
                state = state,
                onBack = viewModel::closeReadOnlyProject,
                onSwitchChat = viewModel::switchReadOnlyChat,
                onContinueHere = viewModel::activateReadOnlyProject,
            )
            AppDestination.WORKSPACE -> WorkspaceScreen(
                state = state,
                onSetClaudeModel = viewModel::setClaudeModel,
                onSetClaudeEffort = viewModel::setClaudeEffort,
                onBack = viewModel::closeProject,
                onSend = viewModel::sendPrompt,
                onStop = viewModel::stopTask,
                onApproval = viewModel::answerApproval,
                onRefreshFiles = viewModel::refreshProjectFiles,
                onOpenFile = viewModel::openFile,
                onCloseFile = viewModel::closeFile,
                onUndoChanges = viewModel::undoLastChanges,
                onKeepChanges = viewModel::keepLastChanges,
                onUndoFileChange = viewModel::undoFileChange,
                onKeepFileChange = viewModel::keepFileChange,
                onCreateChat = viewModel::createChat,
                onSwitchChat = viewModel::switchChat,
                onTerminalRun = viewModel::requestProjectTerminalCommand,
                onTerminalInput = viewModel::sendProjectTerminalInput,
                onTerminalInterrupt = viewModel::interruptProjectTerminalCommand,
                onTerminalPrepare = viewModel::prepareProjectTerminalCommand,
                onTerminalDraftConsumed = viewModel::consumeProjectTerminalDraft,
                onTerminalOpened = viewModel::openProjectTerminal,
                onTerminalStop = viewModel::stopProjectTerminalCommand,
                onTerminalClear = viewModel::clearProjectTerminal,
                onTerminalConfirm = viewModel::confirmProjectTerminalCommand,
                onTerminalCancel = viewModel::cancelProjectTerminalCommand,
                onUseSuggestedProjectRoot = viewModel::useSuggestedProjectRoot,
                onExportProject = viewModel::exportActiveProject,
                onExportSelection = viewModel::exportWorkspaceSelection,
                onSaveFile = viewModel::saveWorkspaceFile,
                onCancelExport = viewModel::cancelWorkspaceExport,
                onDismissExport = viewModel::dismissWorkspaceExport,
                onOpenExportLocation = viewModel::openExportLocation,
                onOpenDirectory = viewModel::openWorkspaceDirectory,
                onSelectionChange = viewModel::setWorkspaceSelection,
                onAddAttachments = viewModel::addChatAttachments,
                onRemoveAttachment = viewModel::removePendingAttachment,
                onOpenAttachment = viewModel::openChatAttachment,
                onExtractZip = viewModel::extractZipIntoWorkspace,
                onImportZipProject = viewModel::importZipProject,
                onBuildAndRunAndroid = viewModel::buildAndRunAndroidApp,
                fileActions = remember(viewModel) {
                    FileManagerActions(
                        onEditFile = viewModel::editFile,
                        onStartEditing = viewModel::startEditingFile,
                        onStopEditing = viewModel::stopEditingFile,
                        onSaveEdits = viewModel::saveOpenedFile,
                        onDismissSaveConflict = viewModel::dismissSaveConflict,
                        onDelete = viewModel::deleteWorkspaceEntries,
                        onRename = viewModel::renameWorkspaceEntry,
                        onCreate = viewModel::createWorkspaceEntry,
                        onUpload = viewModel::uploadToWorkspace,
                        onResolveUploadConflict = viewModel::resolveUploadConflict,
                        onCancelUpload = viewModel::cancelUpload,
                    )
                },
            )
            AppDestination.HOME -> RootScreenHost(state, viewModel, projectsListState)
        }
    }
}

internal fun formatMegabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

/**
 * Home shell: the Expressive short navigation bar and a fade-through transition between tabs.
 * Each tab draws its own top bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeScaffold(
    screen: RootScreen,
    onSelect: (RootScreen) -> Unit,
    showNavigation: Boolean = true,
    content: @Composable (RootScreen) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showNavigation) {
                ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    RootScreen.entries.forEach { tab ->
                        val selected = screen == tab
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "homeTabs",
        ) { tab -> content(tab) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreenHost(
    state: AppUiState,
    viewModel: MainViewModel,
    projectsListState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
) {
    var screen by rememberSaveable { mutableStateOf(RootScreen.PROJECTS) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val terminalLines by viewModel.terminalLines.collectAsStateWithLifecycle()
    val isTerminalRunning by viewModel.isTerminalRunning.collectAsStateWithLifecycle()
    val terminalLiveOutput by viewModel.terminalLiveOutput.collectAsStateWithLifecycle()
    val terminalCurrentCommand by viewModel.terminalCurrentCommand.collectAsStateWithLifecycle()
    val toggleTheme = rememberThemeToggle(viewModel)

    HomeScaffold(screen = screen, onSelect = { screen = it }, showNavigation = !keyboardVisible) { tab ->
        when (tab) {
            RootScreen.PROJECTS -> ProjectsScreen(
                state = state,
                listState = projectsListState,
                onOpen = viewModel::openProject,
                onCreate = viewModel::createProject,
                onCreateQuickProject = viewModel::createQuickProject,
                onImportZip = viewModel::importZipProject,
                onCloneGit = viewModel::clonePublicGitRepository,
                onStartGitHubLogin = viewModel::startGitHubLogin,
                onGenerateNewGitHubCode = viewModel::generateNewGitHubCode,
                onRefreshGitHub = viewModel::refreshGitHubRepositories,
                onDisconnectGitHub = viewModel::disconnectGitHub,
                onCloneGitHub = viewModel::cloneGitHubRepository,
                onRenameProject = viewModel::renameProject,
                onDeleteProject = viewModel::deleteProject,
                onToggleTheme = toggleTheme,
                onInstallUpdate = viewModel::installAppUpdate,
            )
            RootScreen.TERMINAL -> TerminalScreen(
                lines = terminalLines,
                isRunning = isTerminalRunning,
                onRun = viewModel::runTerminalCommand,
                onInput = viewModel::sendTerminalInput,
                onInterrupt = viewModel::interruptTerminalCommand,
                onClear = viewModel::clearTerminal,
                onToggleTheme = toggleTheme,
                themeMode = state.themeMode,
                title = "Terminal",
                subtitle = "Ubuntu · PRoot sandbox",
                liveOutput = terminalLiveOutput,
                currentCommand = terminalCurrentCommand,
                showThemeAction = false,
                showQuickCommands = true,
            )
            RootScreen.SETTINGS -> SettingsScreen(
                state = state,
                onSaveProvider = { profile, key -> viewModel.updateProvider(profile, key) },
                onDiscoverModels = viewModel::discoverModels,
                onValidateProvider = viewModel::validateProvider,
                onSetThemeMode = viewModel::setThemeMode,
                onPing = viewModel::pingApi,
                onClearTerminal = viewModel::clearTerminal,
                getSavedApiKey = viewModel::getSavedApiKey,
                getSavedApiKeys = viewModel::getSavedApiKeys,
                onAddApiKey = viewModel::addApiKey,
                onActivateApiKey = viewModel::activateApiKey,
                onRemoveApiKey = viewModel::removeApiKey,
                onInstallDevStack = viewModel::installDevStack,
                onRemoveDevStack = viewModel::removeDevStack,
                onSelectAgent = viewModel::selectAgent,
                onInstallAgent = viewModel::installAgent,
                onCheckAgentUpdates = viewModel::checkAgentUpdates,
                onUpdateAgent = viewModel::updateAgent,
                onStartAntigravityLogin = viewModel::startAntigravityLogin,
                onSubmitAntigravityCode = viewModel::submitAntigravityCode,
                onLogoutAntigravity = viewModel::logoutAntigravity,
                onRefreshAntigravityModels = viewModel::refreshAntigravityModels,
                onSetAntigravityModel = viewModel::setAntigravityModel,
                onSetAntigravityEffort = viewModel::setAntigravityEffort,
                claudeSignIn = ClaudeSignInActions(
                    onStart = viewModel::startClaudeLogin,
                    onReopenBrowser = viewModel::reopenClaudeLogin,
                    onOpenManual = viewModel::openClaudeManualLogin,
                    onSubmitCode = viewModel::submitClaudeAuthCode,
                    onCancel = viewModel::cancelClaudeLogin,
                ),
                onSetClaudeModel = viewModel::setClaudeModel,
                onSetClaudeEffort = viewModel::setClaudeEffort,
                initialDebugUpdateManifestUrl = viewModel.debugUpdateManifestUrl(),
                onSetDebugUpdateManifestUrl = viewModel::setDebugUpdateManifestUrl,
                onClearDebugUpdateManifestUrl = viewModel::clearDebugUpdateManifestUrl,
            )
        }
    }
}
