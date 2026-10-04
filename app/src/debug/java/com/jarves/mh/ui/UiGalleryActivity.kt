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
 * setup-antigravity. The file and file-edit screens take `--es sample` with one of: kt, py, tsx, html,
 * css, json, yaml, md, sh, go, rs, big (or bigN for N copies of kt), bigtxt.
 */
class UiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "projects"
        val sample = intent.getStringExtra("sample") ?: "kt"
        val theme = when (intent.getStringExtra("theme")) {
            "dark" -> AppThemeMode.DARK
            "light" -> AppThemeMode.LIGHT
            else -> AppThemeMode.SYSTEM
        }
        setContent { PocketTheme(theme) { UiGallery(screen, sample) } }
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
private fun UiGallery(screen: String, sample: String) {
    var editorPreferences by remember { mutableStateOf(com.jarves.mh.ui.editor.EditorPreferences()) }
    var draft by remember {
        mutableStateOf(if (screen == "file-edit") androidx.compose.foundation.text.input.TextFieldState(gallerySample(sample).second) else null)
    }
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
                    val (path, source) = gallerySample(sample)
                    workspaceState.copy(
                        openedFilePath = path,
                        openedFileContent = source,
                        openedFileEditable = true,
                        fileDraft = draft,
                        editorPreferences = editorPreferences,
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
                fileActions = FileManagerActions(
                    onStartEditing = { draft = androidx.compose.foundation.text.input.TextFieldState(gallerySample(sample).second) },
                    onStopEditing = { draft = null },
                    onEditorPreferencesChange = { editorPreferences = it },
                ),
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


private const val Q = "\"\"\""

/** Sample files for the file viewer and editor screens, keyed by `--es sample`. */
private fun gallerySample(name: String): Pair<String, String> {
    name.removePrefix("big").toIntOrNull()?.let { copies ->
        val source = gallerySample("kt").second
        return "app/src/main/java/com/example/weather/Generated$copies.kt" to buildString {
            repeat(copies) { append("// ---- copy ").append(it).append(" ----\n").append(source) }
        }
    }
    return gallerySampleNamed(name)
}

private fun gallerySampleNamed(name: String): Pair<String, String> = when (name) {
    "py" -> "scripts/fetch_weather.py" to """#!/usr/bin/env python3
${Q}Fetch the forecast and cache it for the widget.${Q}
import json
from dataclasses import dataclass


@dataclass
class Forecast:
    city: str
    temps: list[float]

    def average(self) -> float:
        return sum(self.temps) / len(self.temps) if self.temps else 0.0


@cache(ttl=600)
def load(city: str, days: int = 3) -> Forecast:
    # Ask the API, falling back to the cached copy
    url = f"https://api.example.com/v1/{city}?days={days}"
    data = json.loads(fetch(url) or "{}")
    return Forecast(city, [d["max"] for d in data.get("daily", [])])


if __name__ == "__main__":
    print(load("Lisbon").average(), None, True)
"""
    "tsx" -> "web/src/App.tsx" to """import { useState } from "react";
import type { Forecast } from "./api";

interface Props {
  city: string;
  onRefresh?: () => void;
}

const UNITS = /^(metric|imperial)$/i;

export default function App({ city, onRefresh }: Props) {
  const [data, setData] = useState<Forecast | null>(null);
  const label = `Weather in ${'$'}{city.toUpperCase()}`;

  async function refresh() {
    const res = await fetch(`/api/forecast?city=${'$'}{city}`);
    setData(await res.json());
    onRefresh?.();
  }

  return (
    <main className="app">
      <h1>{label}</h1>
      <Button onClick={refresh} disabled={!data}>
        Refresh
      </Button>
    </main>
  );
}
"""
    "html" -> "web/index.html" to """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Weather &amp; more</title>
  <style>
    body { margin: 0; font-family: system-ui, sans-serif; }
    .card:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, .2); }
  </style>
</head>
<body>
  <!-- App root -->
  <div id="app" class="card" hidden>Loading…</div>
  <script type="module">
    const el = document.getElementById("app");
    el.hidden = false;
  </script>
</body>
</html>
"""
    "css" -> "web/src/styles.scss" to """:root {
  --accent: #f28c52;
  --gap: 12px;
}

/* Cards on the dashboard */
.card {
  padding: var(--gap) calc(var(--gap) * 2);
  border-radius: 1.5rem;
  background: url(images/sky.png) no-repeat;

  &:hover > .title {
    color: var(--accent) !important;
  }

  @media (max-width: 600px) {
    padding: 8px;
  }
}
"""
    "json" -> "package.json" to """{
  "name": "weather-web",
  "version": "1.2.0",
  "private": true,
  "scripts": {
    "dev": "vite --port 5173",
    "build": "tsc && vite build"
  },
  "dependencies": {
    "react": "^18.3.1"
  },
  "workspaces": ["web", "api"],
  "retries": 3,
  "sourceMap": null
}
"""
    "yaml" -> ".github/workflows/build.yml" to """name: Build
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest   # pinned image
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: "17"
          cache: gradle
      - run: |
          ./gradlew assembleDebug
          echo "done"
"""
    "md" -> "README.md" to """---
title: Weather Widget
---
# Weather Widget

A **home-screen widget** that shows the _forecast_ for your city.

## Setup

1. Clone the repo
2. Run `./gradlew installDebug`
- [x] Dark mode
- [ ] Hourly view

> Data comes from [Open-Meteo](https://open-meteo.com).

```kotlin
val widget = WeatherWidget(context)
widget.refresh()
```
"""
    "sh" -> "scripts/deploy.sh" to """#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${'$'}{HOME}/apps/weather"  # where builds go
VERSION=${'$'}(git describe --tags)

deploy() {
  local target="${'$'}1"
  echo "Deploying ${'$'}VERSION to ${'$'}{target}"
  if [[ -z "${'$'}target" ]]; then
    return 1
  fi
  cat <<EOF > "${'$'}APP_DIR/release.txt"
version=${'$'}VERSION
EOF
}

deploy "${'$'}{1:-staging}"
"""
    "go" -> "server/main.go" to """package main

import (
	"fmt"
	"net/http"
)

type Server struct {
	Port int
}

func (s *Server) Start() error {
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, `{"ok": true}`)
	})
	return http.ListenAndServe(fmt.Sprintf(":%d", s.Port), nil)
}
"""
    "rs" -> "src/lib.rs" to """use std::collections::HashMap;

/// Caches forecasts per city.
#[derive(Debug, Default)]
pub struct Cache<'a> {
    items: HashMap<&'a str, f64>,
}

impl<'a> Cache<'a> {
    pub fn get(&self, city: &'a str) -> Option<f64> {
        println!("lookup {}", city);
        self.items.get(city).copied()
    }
}
"""
    "bigtxt" -> "notes/generated.txt" to gallerySample("big").second
    "big" -> "app/src/main/java/com/example/weather/Generated.kt" to buildString {
        val source = gallerySample("kt").second
        repeat(220) { append("// ---- copy ").append(it).append(" ----\n").append(source) }
    }
    else -> "app/src/main/java/com/example/weather/WeatherWidget.kt" to """package com.example.weather

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Home-screen widget showing today's forecast.
 */
@Suppress("unused")
class WeatherWidget(private val context: Context) {
    private val cache = mutableMapOf<String, Double>()
    val maxEntries = 32

    fun refresh(city: String = "Lisbon"): Boolean {
        val temp = cache[city] ?: return false
        println("Temperature in ${'$'}city is ${'$'}{temp.toInt()}°C")
        return temp > 0.5 && city.isNotBlank()
    }

    // TODO: move to a repository
    companion object {
        const val MAX_CITIES = 0x10
    }
}

@Composable
fun WidgetPreview() = WeatherWidget(LocalContext.current)
"""
}
