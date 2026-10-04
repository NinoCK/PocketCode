package com.jarves.mh.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jarves.mh.model.ActivityItem
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.Project
import com.jarves.mh.model.ProjectChat
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.ToolRequest
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.network.ConnectionValidation
import com.jarves.mh.network.ModelDiscoveryResult
import com.jarves.mh.ui.theme.AppThemeMode
import com.jarves.mh.ui.theme.PocketTheme
import com.jarves.mh.update.AppUpdateInfo

/**
 * Debug-only screen gallery: renders redesigned screens with sample data so they can be checked
 * on an emulator without the Linux runtime. Launch with
 * `adb shell am start -n com.jarves.mh/.ui.UiGalleryActivity --es screen projects --es theme dark`.
 * Screens: projects, projects-empty, projects-busy, projects-update, terminal, settings,
 * chat, chat-empty, chat-live, files, project-terminal, preview, loading, setup-permissions,
 * setup-device, setup-install, setup-error, setup-provider, setup-credentials, setup-claude,
 * setup-antigravity.
 */
class UiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "projects"
        val theme = when (intent.getStringExtra("theme")) {
            "dark" -> AppThemeMode.DARK
            "light" -> AppThemeMode.LIGHT
            else -> AppThemeMode.SYSTEM
        }
        setContent { PocketTheme(theme) { UiGallery(screen) } }
    }
}

private val now = System.currentTimeMillis()

private val sampleProjects = listOf(
    Project(id = "weather", name = "Weather Widget", description = "Jetpack Compose home-screen widget", language = "Kotlin", updatedAtMillis = now - 5 * 60_000),
    Project(id = "quick-1", name = "Quick chat", description = "", language = "Any", kind = ProjectKind.QUICK_PROJECT, updatedAtMillis = now - 3 * 3_600_000),
    Project(id = "portfolio", name = "portfolio-site", description = "Static site built with Vite", language = "TypeScript", updatedAtMillis = now - 86_400_000),
    Project(id = "budget", name = "Budget Tracker", description = "Flask API and SQLite", language = "Python", updatedAtMillis = now - 4 * 86_400_000),
    Project(id = "scraper", name = "price-scraper", description = "Daily price alerts", language = "Python", updatedAtMillis = now - 9 * 86_400_000),
)

private val sampleSteps = listOf(
    ActivityItem("Read", "app/src/main/java/com/example/weather/SettingsScreen.kt"),
    ActivityItem("Grep", "isSystemInDarkTheme in app/src"),
    ActivityItem("Edit", "SettingsScreen.kt: add a dark mode switch bound to ThemePreferences"),
    ActivityItem("Running Bash", "./gradlew assembleDebug", isComplete = true, isCommand = true),
)

private val sampleMessages = listOf(
    ChatMessage(fromUser = true, text = "Add a dark mode toggle to the settings screen and remember the choice."),
    ChatMessage(fromUser = false, text = "", workItems = sampleSteps, workedMillis = 48_000),
    ChatMessage(
        fromUser = false,
        text = "Done. The settings screen now has a **Dark mode** switch.\n\n- The choice is saved in `ThemePreferences`\n- The app follows it on the next launch\n\n```bash\n./gradlew assembleDebug\n```",
        workedMillis = 48_000,
    ),
)

private val sampleFiles = listOf(
    WorkspaceEntry("app", "app", isDirectory = true, depth = 0, childCount = 4),
    WorkspaceEntry("gradle", "gradle", isDirectory = true, depth = 0, childCount = 1),
    WorkspaceEntry("build", "build", isDirectory = true, depth = 0, childCount = 3),
    WorkspaceEntry(".gradle", ".gradle", isDirectory = true, depth = 0, childCount = 2),
    WorkspaceEntry("README.md", "README.md", isDirectory = false, depth = 0, sizeBytes = 2_340, modifiedMillis = now - 600_000),
    WorkspaceEntry("build.gradle.kts", "build.gradle.kts", isDirectory = false, depth = 0, sizeBytes = 812, modifiedMillis = now - 7_200_000),
    WorkspaceEntry("settings.gradle.kts", "settings.gradle.kts", isDirectory = false, depth = 0, sizeBytes = 341, modifiedMillis = now - 7_200_000),
)

private val sampleTerminal = listOf(
    TerminalOutputLine(command = "node -v", output = "v20.17.0"),
    TerminalOutputLine(command = "ls", output = "app  build.gradle.kts  gradle  README.md  settings.gradle.kts"),
    TerminalOutputLine(command = "git status --short", output = " M app/src/main/java/com/example/weather/SettingsScreen.kt"),
)

@Composable
private fun UiGallery(screen: String) {
    val workspaceState = AppUiState(
        projects = sampleProjects,
        activeProject = sampleProjects.first(),
        workspaceVisible = true,
        projectChats = listOf(ProjectChat(id = "c1", title = "Dark mode toggle"), ProjectChat(id = "c2", title = "Widget layout")),
        activeChatId = "c1",
        messages = sampleMessages,
        workspaceFiles = sampleFiles,
        workspaceArtifacts = listOf(
            WorkspaceEntry("app/build/outputs/apk/debug/app-debug.apk", "app-debug.apk", isDirectory = false, depth = 5, sizeBytes = 8_400_000, modifiedMillis = now - 900_000),
        ),
        androidProjectDetected = true,
        projectTerminalLines = sampleTerminal,
        projectTerminalCwd = "/workspace/weather-widget",
        provider = ProviderProfile(ProviderKind.CLAUDE),
        agentKind = AgentKind.CLAUDE_CODE,
        installedAgentVersions = mapOf(AgentKind.CLAUDE_CODE to "2.1.288"),
        installedDevStacks = setOf(DevStack.WEB, DevStack.ANDROID),
    )
    when (screen) {
        "loading" -> StartupLoadingScreen(AppUiState())
        "setup-permissions" -> BackgroundTaskSetupScreen(onContinue = {})
        "setup-device" -> RuntimeSetupPromptScreen(selectedStacks = setOf(DevStack.PYTHON), onToggleStack = {}, onDownload = {})
        "setup-install" -> RuntimeInstallationScreen(
            AppUiState(
                startupStage = StartupStage.INSTALLING,
                startupProgress = 0.42f,
                startupMessage = "Downloading the Claude Code runtime…",
                startupBytes = 61_000_000L to 141_000_000L,
                selectedDevStacks = setOf(DevStack.PYTHON),
                startupLogs = listOf("$ verify ubuntu-core.tar.zst", "ok  68.8 MB", "$ download claude-code-2.1.288"),
            ),
        )
        "setup-error" -> StartupErrorScreen(
            message = "The runtime download stopped because the connection dropped.",
            isOffline = false,
            logs = listOf("$ download claude-code-2.1.288", "error: connection reset by peer"),
            onRetry = {},
        )
        "setup-provider" -> ProviderSetupScreen(
            initial = ProviderProfile(ProviderKind.DEEPSEEK),
            onboarding = true,
            agentKind = AgentKind.DEEPSEEK_HARNESS,
            initialStep = 1,
            onSave = { _, _ -> },
            onDiscover = { _, _ -> ModelDiscoveryResult.Failure("Not available in the gallery") },
            onValidate = { _, _, _ -> ConnectionValidation.Failure("Not available in the gallery") },
            onSelectAgent = {},
            onToggleTheme = {},
        )
        "setup-credentials" -> ProviderSetupScreen(
            initial = ProviderProfile(ProviderKind.DEEPSEEK),
            onboarding = true,
            agentKind = AgentKind.DEEPSEEK_HARNESS,
            initialStep = 2,
            onSave = { _, _ -> },
            onDiscover = { _, _ -> ModelDiscoveryResult.Failure("Not available in the gallery") },
            onValidate = { _, _, _ -> ConnectionValidation.Failure("Not available in the gallery") },
            onSelectAgent = {},
            onToggleTheme = {},
        )
        "setup-claude" -> ProviderSetupScreen(
            initial = ProviderProfile(ProviderKind.CLAUDE),
            onboarding = true,
            initialStep = 2,
            onSave = { _, _ -> },
            onDiscover = { _, _ -> ModelDiscoveryResult.Failure("Not available in the gallery") },
            onValidate = { _, _, _ -> ConnectionValidation.Failure("Not available in the gallery") },
            onSelectAgent = {},
            onToggleTheme = {},
        )
        "setup-antigravity" -> AntigravityOnboardingScreen(
            state = AppUiState(),
            onStartLogin = {},
            onSubmitCode = {},
            onContinue = {},
            onSelectAgent = {},
            onToggleTheme = {},
        )
        "chat", "chat-empty", "chat-live", "chat-attachments", "files", "files-upload", "files-conflict", "file", "file-edit",
        "project-terminal", "preview" -> {
            val state = when (screen) {
                "chat-empty" -> workspaceState.copy(messages = emptyList())
                "files-upload" -> workspaceState.copy(
                    fileUpload = com.jarves.mh.model.FileUploadState(
                        id = 1,
                        directory = "app/build/output",
                        fileName = "release-notes.txt",
                        filesDone = 1,
                        filesTotal = 3,
                        bytesDone = 2_600_000,
                        bytesTotal = 4_100_000,
                    ),
                )
                "files-conflict" -> workspaceState.copy(
                    uploadConflict = com.jarves.mh.model.UploadConflict("app/config", listOf("settings.json"), otherFiles = 2),
                )
                "file", "file-edit" -> {
                    val source = "package com.example.weather\n\nclass WeatherWidget {\n    fun refresh() {\n        // TODO\n    }\n}\n"
                    workspaceState.copy(
                        openedFilePath = "app/src/main/java/com/example/weather/WeatherWidget.kt",
                        openedFileContent = source,
                        openedFileEditable = true,
                        fileDraft = if (screen == "file-edit") {
                            androidx.compose.foundation.text.input.TextFieldState(source.replace("// TODO", "updateViews()"))
                        } else {
                            null
                        },
                    )
                }
                "chat-attachments" -> workspaceState.copy(
                    pendingAttachments = listOf(
                        com.jarves.mh.model.ChatAttachment(
                            displayName = "landing-page",
                            relativePath = "uploads/landing-page",
                            mimeType = com.jarves.mh.model.ChatAttachment.DIRECTORY_MIME_TYPE,
                            sizeBytes = 48_213,
                        ),
                        com.jarves.mh.model.ChatAttachment(displayName = "mockup.png", relativePath = "attachments/c/mockup.png", mimeType = "image/png", sizeBytes = 312_000),
                    ),
                    archiveExtractionMessage = "Extracting assets.zip · 175 files",
                )
                "chat-live" -> workspaceState.copy(
                    messages = sampleMessages.take(1),
                    isRunning = true,
                    liveProcess = sampleSteps.dropLast(1) + ActivityItem("Running Bash", "./gradlew testDebugUnitTest", isComplete = false, isCommand = true),
                    taskStartedAtMillis = now - 42_000,
                    pendingApproval = ToolRequest(
                        sessionId = "s",
                        toolName = "Bash",
                        explanation = "Claude Code wants to delete the old build folder before rebuilding.",
                        affectedPaths = listOf("app/build"),
                        risk = com.jarves.mh.model.RiskLevel.HIGH,
                    ),
                )
                else -> workspaceState
            }
            WorkspaceScreen(
                state = state,
                onBack = {},
                onSend = {},
                onStop = {},
                onApproval = {},
                onRefreshFiles = {},
                onOpenFile = {},
                onCloseFile = {},
                onUndoChanges = {},
                onKeepChanges = {},
                onUndoFileChange = {},
                onKeepFileChange = {},
                onCreateChat = {},
                onSwitchChat = {},
                onTerminalRun = {},
                onTerminalInput = {},
                onTerminalInterrupt = {},
                onTerminalPrepare = {},
                onTerminalDraftConsumed = {},
                onTerminalOpened = {},
                onTerminalStop = {},
                onTerminalClear = {},
                onTerminalConfirm = {},
                onTerminalCancel = {},
                onUseSuggestedProjectRoot = {},
                onExportProject = { _, _ -> },
                onExportSelection = { _, _ -> },
                onSaveFile = { _, _ -> },
                onCancelExport = {},
                onDismissExport = {},
                onOpenExportLocation = {},
                onOpenDirectory = {},
                onSelectionChange = {},
                onAddAttachments = {},
                onRemoveAttachment = {},
                onOpenAttachment = {},
                onExtractZip = { _, _, _ -> },
                onImportZipProject = {},
                onBuildAndRunAndroid = {},
                initialTab = when (screen) {
                    "files", "files-upload", "files-conflict", "file", "file-edit" -> WorkspaceTab.FILES
                    "project-terminal" -> WorkspaceTab.TERMINAL
                    "preview" -> WorkspaceTab.PREVIEW
                    else -> WorkspaceTab.CHAT
                },
            )
        }
        else -> HomeGallery(screen, workspaceState)
    }
}

@Composable
private fun HomeGallery(screen: String, workspaceState: AppUiState) {
    val state = when (screen) {
        "projects-empty" -> AppUiState()
        "projects-busy" -> AppUiState(
            projects = sampleProjects,
            activeProject = sampleProjects.first(),
            isRunning = true,
            gitCloneRunning = true,
            gitCloneMessage = "Cloning octocat/hello-world…",
        )
        // Shows the update banner; the update dialog opens on top automatically.
        "projects-update" -> AppUiState(projects = sampleProjects, appUpdate = AppUpdateInfo(6, "1.0.5", "", "", 0, ""))
        else -> workspaceState.copy(activeProject = null, workspaceVisible = false)
    }
    var tab by remember {
        mutableStateOf(
            when (screen) {
                "terminal" -> RootScreen.TERMINAL
                "settings" -> RootScreen.SETTINGS
                else -> RootScreen.PROJECTS
            },
        )
    }
    HomeScaffold(screen = tab, onSelect = { tab = it }) { current ->
        when (current) {
            RootScreen.PROJECTS -> ProjectsScreen(
                state = state,
                listState = rememberLazyListState(),
                onOpen = {},
                onCreate = {},
                onCreateQuickProject = {},
                onImportZip = {},
                onCloneGit = {},
                onStartGitHubLogin = {},
                onGenerateNewGitHubCode = {},
                onRefreshGitHub = {},
                onDisconnectGitHub = {},
                onCloneGitHub = {},
                onRenameProject = { _, _ -> },
                onDeleteProject = {},
                onToggleTheme = {},
                onInstallUpdate = {},
            )
            RootScreen.TERMINAL -> TerminalScreen(
                lines = sampleTerminal,
                isRunning = false,
                onRun = {},
                onClear = {},
                onToggleTheme = {},
                themeMode = state.themeMode,
                title = "Terminal",
                subtitle = "Ubuntu · PRoot sandbox",
            )
            RootScreen.SETTINGS -> SettingsScreen(
                state = state,
                onSaveProvider = { _, _ -> },
                onDiscoverModels = { _, _ -> ModelDiscoveryResult.Failure("Not available in the gallery") },
                onValidateProvider = { _, _, _ -> ConnectionValidation.Failure("Not available in the gallery") },
                onSetThemeMode = {},
                onPing = {},
                onClearTerminal = {},
                getSavedApiKey = { "" },
                getSavedApiKeys = { emptyList() },
                onAddApiKey = { _, _, _ -> emptyList() },
                onActivateApiKey = { _, _ -> emptyList() },
                onRemoveApiKey = { _, _ -> emptyList() },
            )
        }
    }
}
