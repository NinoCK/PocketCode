package io.github.ninock.pocketcode.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ninock.pocketcode.network.GitHubRepository
import io.github.ninock.pocketcode.model.Project
import io.github.ninock.pocketcode.model.ProjectKind
import io.github.ninock.pocketcode.model.projectSlug
import io.github.ninock.pocketcode.ui.theme.LocalDarkTheme
import io.github.ninock.pocketcode.ui.theme.LocalPocketExtraColors

/**
 * Home: the project list. A large collapsing title, projects in one grouped list, and a floating
 * "+" menu that holds every way to start or bring in a project.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProjectsScreen(
    state: AppUiState,
    listState: LazyListState,
    onOpen: (Project) -> Unit,
    onCreate: (String) -> Unit,
    onCreateQuickProject: () -> Unit,
    onImportZip: (Uri) -> Unit,
    onCloneGit: (String) -> Unit,
    onStartGitHubLogin: () -> Unit,
    onGenerateNewGitHubCode: () -> Unit,
    onRefreshGitHub: () -> Unit,
    onDisconnectGitHub: () -> Unit,
    onCloneGitHub: (GitHubRepository) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showGitDialog by rememberSaveable { mutableStateOf(false) }
    var showGitHubDialog by rememberSaveable { mutableStateOf(false) }
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val projects = state.projects
    val importBusy = state.projectImporting || state.gitCloneRunning
    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportZip(uri)
    }
    val openGitHub = {
        showGitHubDialog = true
        if (state.githubAuthStatus == GitHubAuthStatus.CONNECTED && state.githubRepositories.isEmpty()) onRefreshGitHub()
    }
    LaunchedEffect(state.appUpdate?.versionCode) {
        if (state.appUpdate != null) showUpdateDialog = true
    }
    BackHandler(enabled = fabMenuExpanded) { fabMenuExpanded = false }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("Projects") },
                    subtitle = {
                        Text(
                            when (projects.size) {
                                0 -> "PocketCode"
                                1 -> "1 project"
                                else -> "${projects.size} projects"
                            },
                        )
                    },
                    actions = {
                            IconButton(onClick = onToggleTheme) {
                            Icon(if (LocalDarkTheme.current) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Toggle theme")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.appUpdate?.let { update ->
                    item(key = "update") {
                        UpdateBanner(
                            versionName = update.versionName,
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier.animateItem().padding(bottom = 14.dp),
                        )
                    }
                }
                if (importBusy) {
                    item(key = "import-progress") {
                        ImportProgressCard(
                            message = state.projectImportMessage ?: state.gitCloneMessage
                                ?: if (state.gitCloneRunning) "Cloning repository…" else "Importing project…",
                            modifier = Modifier.animateItem().padding(bottom = 14.dp),
                        )
                    }
                }
                if (projects.isEmpty()) {
                    item(key = "empty") {
                        EmptyProjects(
                            importBusy = importBusy,
                            onQuick = onCreateQuickProject,
                            onNew = { showCreate = true },
                            onImport = { showImportSheet = true },
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    item(key = "header") {
                        Text(
                            "Your projects",
                            modifier = Modifier.animateItem().padding(start = 8.dp, top = 4.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    itemsIndexed(projects, key = { _, project -> project.id }) { index, project ->
                        ProjectRow(
                            project = project,
                            index = index,
                            count = projects.size,
                            taskRunning = state.isRunning && state.activeProject?.id == project.id,
                            terminalRunning = state.projectTerminalRunning && state.activeProject?.id == project.id,
                            onOpen = { onOpen(project) },
                            onRename = { onRenameProject(project.id, it) },
                            onDelete = { onDeleteProject(project.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        // Dims everything above the bottom bar behind the open "+" menu; tapping it closes the menu.
        AnimatedVisibility(
            visible = fabMenuExpanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = null, indication = null) { fabMenuExpanded = false },
            )
        }
        if (projects.isNotEmpty()) {
            CreateProjectFabMenu(
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                actions = listOf(
                    FabAction("Quick project", Icons.Rounded.AutoAwesome, onCreateQuickProject),
                    FabAction("New project", Icons.Rounded.Add) { showCreate = true },
                    FabAction("Import ZIP", Icons.Rounded.FolderZip) { importZipLauncher.launch("*/*") },
                    FabAction("Clone Git URL", Icons.Rounded.Link) { showGitDialog = true },
                    FabAction("GitHub repository", Icons.Rounded.Code, openGitHub),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp),
            )
        }
    }

    if (showImportSheet) {
        ImportSheet(
            onDismiss = { showImportSheet = false },
            onZip = { showImportSheet = false; importZipLauncher.launch("*/*") },
            onGit = { showImportSheet = false; showGitDialog = true },
            onGitHub = { showImportSheet = false; openGitHub() },
            githubLogin = state.githubLogin,
        )
    }
    if (showCreate) CreateProjectDialog(onDismiss = { showCreate = false }, onCreate = { onCreate(it); showCreate = false })
    if (showGitDialog) CloneGitDialog(
        running = state.gitCloneRunning,
        message = state.gitCloneMessage,
        onDismiss = { showGitDialog = false },
        onClone = { onCloneGit(it); showGitDialog = false },
    )
    if (showGitHubDialog) GitHubDialog(
        state = state,
        onDismiss = { showGitHubDialog = false },
        onStartLogin = onStartGitHubLogin,
        onGenerateNewCode = onGenerateNewGitHubCode,
        onRefresh = onRefreshGitHub,
        onDisconnect = { onDisconnectGitHub(); showGitHubDialog = false },
        onClone = { showGitHubDialog = false; onCloneGitHub(it) },
    )
    if (showUpdateDialog && state.appUpdate != null) UpdateDialog(
        state = state,
        onDismiss = { showUpdateDialog = false },
        onInstall = onInstallUpdate,
    )
}

// ---------------------------------------------------------------------------------------------
// Project list
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ProjectGlyphShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Gem,
    MaterialShapes.Arch,
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Pentagon,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectRow(
    project: Project,
    index: Int,
    count: Int,
    taskRunning: Boolean,
    terminalRunning: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var showRename by rememberSaveable(project.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(project.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Pressing a row rounds its inner corners: the Expressive "shape morph" feedback.
    val inner by animateDpAsState(if (pressed) 24.dp else 6.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "rowCorner")
    val busy = taskRunning || terminalRunning

    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = groupedShape(index, count, outer = 28.dp, inner = inner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectGlyph(project, pressed)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (project.kind == ProjectKind.QUICK_PROJECT) "Quick project" else project.description.ifBlank { project.language },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                AnimatedVisibility(
                    visible = busy,
                    enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                ) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (taskRunning) "Task running" else "Terminal running",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!busy) {
                    Text(
                        "/workspace/${project.slug} · ${project.formattedUpdatedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Options for ${project.name}") }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; showRename = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        }
    }
    if (showRename) RenameProjectDialog(project.name, onDismiss = { showRename = false }, onRename = { onRename(it); showRename = false })
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Its chats, files, attachments, changes, and terminal history will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDelete = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A per-project shape and colour, so projects are recognisable at a glance. Spins a little on press. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProjectGlyph(project: Project, pressed: Boolean) {
    val seed = Math.floorMod(project.id.hashCode(), 997)
    val shape = ProjectGlyphShapes[seed % ProjectGlyphShapes.size].toShape()
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (seed % 3) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val rotation by animateFloatAsState(if (pressed) 40f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "glyphSpin")
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer { rotationZ = rotation }
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (project.kind == ProjectKind.QUICK_PROJECT) Icons.Rounded.AutoAwesome else Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -rotation },
            tint = content,
        )
    }
}

private data class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CreateProjectFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier.semantics { contentDescription = if (expanded) "Close menu" else "Create or import a project" },
                checked = expanded,
                onCheckedChange = onExpandedChange,
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                Icon(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    action.onClick()
                },
                icon = { Icon(action.icon, contentDescription = null) },
                text = { Text(action.label) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state, banners and sheets
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyProjects(
    importBusy: Boolean,
    onQuick: () -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spin = rememberInfiniteTransition(label = "heroSpin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "heroRotation")
    val heroShape = MaterialShapes.Cookie12Sided.toShape()
    Column(
        modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(168.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = -rotation },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("Build from your phone", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Chat with your coding agent, review its changes, and run your project, all on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onQuick,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text("Start a quick project", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = onNew,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text("New named project", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onImport, enabled = !importBusy, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Bring an existing project")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateBanner(versionName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialShapes.Sunny.toShape()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(shape).background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Update available", style = MaterialTheme.typography.titleMedium)
                Text("PocketCode $versionName is ready to install", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportProgressCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(14.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    onDismiss: () -> Unit,
    onZip: () -> Unit,
    onGit: () -> Unit,
    onGitHub: () -> Unit,
    githubLogin: String?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Bring an existing project",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            val options = listOf(
                Triple(Icons.Rounded.FolderZip, "ZIP file" to "Import files from a .zip on this phone", onZip),
                Triple(Icons.Rounded.Link, "Git URL" to "Clone a public HTTPS repository with its history", onGit),
                Triple(
                    Icons.Rounded.Code,
                    (githubLogin?.let { "GitHub · @$it" } ?: "GitHub") to "Browse your public and private repositories",
                    onGitHub,
                ),
            )
            options.forEachIndexed { index, (icon, labels, action) ->
                ListItem(
                    headlineContent = { Text(labels.first, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(labels.second) },
                    leadingContent = {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.clip(groupedShape(index, options.size, outer = 24.dp, inner = 6.dp)).clickable(onClick = action),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, null) },
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                AnimatedVisibility(visible = name.isNotBlank()) {
                    Text(
                        "Terminal folder: /workspace/${projectSlug(name)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameProjectDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("Rename project") },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text("Project name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        },
        confirmButton = { Button(onClick = { onRename(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CloneGitDialog(running: Boolean, message: String?, onDismiss: () -> Unit, onClone: (String) -> Unit) {
    var gitUrl by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        icon = { Icon(Icons.Rounded.Link, null) },
        title = { Text("Clone Git repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a public HTTPS repository URL. Its complete Git history and current branch are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = gitUrl,
                    onValueChange = { gitUrl = it },
                    label = { Text("HTTPS Git URL") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            Button(enabled = gitUrl.isNotBlank() && !running, onClick = { onClone(gitUrl) }) {
                Text(if (running) "Cloning…" else "Clone")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !running) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GitHubDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onStartLogin: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onClone: (GitHubRepository) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var search by rememberSaveable { mutableStateOf("") }
    val repositories = state.githubRepositories.filter { search.isBlank() || it.fullName.contains(search, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = { if (!state.gitCloneRunning) onDismiss() },
        icon = { Icon(Icons.Rounded.Code, null) },
        title = { Text(state.githubLogin?.let { "GitHub · @$it" } ?: "Connect GitHub") },
        text = {
            when (state.githubAuthStatus) {
                GitHubAuthStatus.DISCONNECTED, GitHubAuthStatus.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        state.githubMessage ?: "Sign in with GitHub's official CLI to browse public and private repositories.",
                        color = if (state.githubAuthStatus == GitHubAuthStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onStartLogin, modifier = Modifier.fillMaxWidth()) { Text("Sign in with GitHub") }
                }
                GitHubAuthStatus.STARTING -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(state.githubMessage ?: "Starting GitHub sign-in…")
                }
                GitHubAuthStatus.AWAITING_USER -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Enter this one-time code on the GitHub page that opened in your browser.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        onClick = { state.githubUserCode?.let { clipboard.setText(AnnotatedString(it)) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            state.githubUserCode.orEmpty(),
                            modifier = Modifier.padding(18.dp),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "Tap the code to copy it. PocketCode connects automatically after you approve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onGenerateNewCode, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generate new code")
                    }
                }
                GitHubAuthStatus.CONNECTED -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.githubMessage ?: "Select a repository",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onRefresh, enabled = !state.githubRepositoriesLoading) {
                            Icon(Icons.Rounded.Refresh, "Refresh repositories")
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search repositories") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.githubRepositoriesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(repositories, key = { _, repository -> repository.fullName }) { index, repository ->
                            Surface(
                                onClick = { onClone(repository) },
                                enabled = !state.gitCloneRunning,
                                modifier = Modifier.fillMaxWidth(),
                                shape = groupedShape(index, repositories.size, outer = 20.dp, inner = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (repository.private) Icons.Rounded.Key else Icons.Rounded.Code,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            repository.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${if (repository.private) "Private" else "Public"} · ${repository.defaultBranch}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.githubAuthStatus == GitHubAuthStatus.CONNECTED) TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            when {
                state.githubAuthStatus == GitHubAuthStatus.CONNECTED -> TextButton(onClick = onDisconnect) { Text("Disconnect") }
                state.githubAuthStatus != GitHubAuthStatus.STARTING -> TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun UpdateDialog(state: AppUiState, onDismiss: () -> Unit, onInstall: () -> Unit) {
    val update = state.appUpdate ?: return
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onInstall() }
    val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    val downloading = state.appUpdateStatus == AppUpdateStatus.DOWNLOADING
    val installing = state.appUpdateStatus == AppUpdateStatus.INSTALLING
    val total = state.appUpdateTotalBytes
    val downloaded = state.appUpdateDownloadedBytes
    val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    AlertDialog(
        onDismissRequest = { if (!installing) onDismiss() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update to ${update.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(update.notes.ifBlank { "Get the latest improvements and fixes for PocketCode." })
                if (update.sizeBytes > 0) {
                    Text(
                        "Download size: ${formatMegabytes(update.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!canInstall) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Allow ‘Install unknown apps’ for PocketCode. Without this permission, Android will not install the update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                if (downloading) {
                    if (total > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        if (total > 0) {
                            "Downloading ${formatMegabytes(downloaded)} / ${formatMegabytes(total)} · ${(progress * 100).toInt()}%"
                        } else {
                            "Downloading ${formatMegabytes(downloaded)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (installing) {
                    Text(
                        "Download verified. Opening Android installer…",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalPocketExtraColors.current.success,
                    )
                }
                state.appUpdateError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading && !installing,
                onClick = {
                    if (!canInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } else {
                        onInstall()
                    }
                },
            ) {
                Text(
                    when {
                        !canInstall -> "Grant permission"
                        downloading -> "Downloading…"
                        installing -> "Installing…"
                        else -> "Download and install"
                    },
                )
            }
        },
        dismissButton = { if (!installing) TextButton(onClick = onDismiss) { Text("Later") } },
    )
}
