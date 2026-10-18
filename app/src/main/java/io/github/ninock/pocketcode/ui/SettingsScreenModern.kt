package io.github.ninock.pocketcode.ui

import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import android.os.Build
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninock.pocketcode.BuildConfig
import io.github.ninock.pocketcode.data.ApiKeyInfo
import io.github.ninock.pocketcode.model.AgentKind
import io.github.ninock.pocketcode.model.DEEPSEEK_HARNESS_PROVIDERS
import io.github.ninock.pocketcode.model.DSH_PROTOCOL_PROVIDERS
import io.github.ninock.pocketcode.model.DevStack
import io.github.ninock.pocketcode.model.ProviderKind
import io.github.ninock.pocketcode.model.ProviderProfile
import io.github.ninock.pocketcode.model.providersForAgent
import io.github.ninock.pocketcode.network.ConnectionValidation
import io.github.ninock.pocketcode.network.DiscoveredModel
import io.github.ninock.pocketcode.network.ModelDiscoveryResult
import io.github.ninock.pocketcode.runtime.AntigravityAuthStatus
import io.github.ninock.pocketcode.ui.theme.AppThemeMode
import io.github.ninock.pocketcode.ui.theme.PocketOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onSaveProvider: (ProviderProfile, String) -> Unit,
    onDiscoverModels: suspend (ProviderProfile, String) -> ModelDiscoveryResult,
    onValidateProvider: suspend (ProviderProfile, String, List<DiscoveredModel>) -> ConnectionValidation,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onPing: () -> Unit,
    onClearTerminal: () -> Unit,
    getSavedApiKey: (ProviderKind) -> String,
    getSavedApiKeys: (ProviderKind) -> List<ApiKeyInfo>,
    onAddApiKey: (ProviderKind, String, String) -> List<ApiKeyInfo>,
    onActivateApiKey: (ProviderKind, String) -> List<ApiKeyInfo>,
    onRemoveApiKey: (ProviderKind, String) -> List<ApiKeyInfo>,
    onInstallDevStack: (DevStack) -> Unit = {},
    onRemoveDevStack: (DevStack) -> Unit = {},
    onSelectAgent: (AgentKind) -> Unit = {},
    onInstallAgent: (AgentKind) -> Unit = {},
    onCheckAgentUpdates: () -> Unit = {},
    onUpdateAgent: (AgentKind) -> Unit = {},
    onStartAntigravityLogin: () -> Unit = {},
    onSubmitAntigravityCode: (String) -> Unit = {},
    onLogoutAntigravity: () -> Unit = {},
    onRefreshAntigravityModels: () -> Unit = {},
    onSetAntigravityModel: (String) -> Unit = {},
    onSetAntigravityEffort: (String) -> Unit = {},
    claudeSignIn: ClaudeSignInActions = ClaudeSignInActions(),
    onSetClaudeModel: (String) -> Unit = {},
    onSetClaudeEffort: (String) -> Unit = {},
    initialDebugUpdateManifestUrl: String = "",
    onSetDebugUpdateManifestUrl: (String) -> Unit = {},
    onClearDebugUpdateManifestUrl: () -> Unit = {},
) {
    val context = LocalContext.current
    var terminalCleared by remember { mutableStateOf(false) }
    var showReliabilityHelp by rememberSaveable { mutableStateOf(false) }
    var stackPendingRemoval by remember { mutableStateOf<DevStack?>(null) }
    var showUsage by rememberSaveable { mutableStateOf(false) }
    if (showUsage) {
        UsageScreen(records = state.usageRecords, projects = state.projects, onBack = { showUsage = false })
        return
    }

    stackPendingRemoval?.let { stack ->
        AlertDialog(
            onDismissRequest = { stackPendingRemoval = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Remove ${stack.label}?") },
            text = { Text("This removes the toolchain and its runtime caches to free storage. Your projects and source files are not deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        stackPendingRemoval = null
                        onRemoveDevStack(stack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { stackPendingRemoval = null }) { Text("Cancel") } },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                subtitle = { Text("PocketCode ${BuildConfig.VERSION_NAME}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "agent-header") { SectionHeader("Coding agent") }
            item(key = "agent") {
                Box(Modifier.padding(horizontal = 4.dp)) {
                    AgentSettingsSection(
                        state = state,
                        onSaveProvider = onSaveProvider,
                        onDiscoverModels = onDiscoverModels,
                        onValidateProvider = onValidateProvider,
                        onPing = onPing,
                        getSavedApiKey = getSavedApiKey,
                        getSavedApiKeys = getSavedApiKeys,
                        onAddApiKey = onAddApiKey,
                        onActivateApiKey = onActivateApiKey,
                        onRemoveApiKey = onRemoveApiKey,
                        onSelectAgent = onSelectAgent,
                        onInstallAgent = onInstallAgent,
                        onCheckAgentUpdates = onCheckAgentUpdates,
                        onUpdateAgent = onUpdateAgent,
                        onStartAntigravityLogin = onStartAntigravityLogin,
                        onSubmitAntigravityCode = onSubmitAntigravityCode,
                        onLogoutAntigravity = onLogoutAntigravity,
                        onRefreshAntigravityModels = onRefreshAntigravityModels,
                        onSetAntigravityModel = onSetAntigravityModel,
                        onSetAntigravityEffort = onSetAntigravityEffort,
                        claudeSignIn = claudeSignIn,
                        onSetClaudeModel = onSetClaudeModel,
                        onSetClaudeEffort = onSetClaudeEffort,
                    )
                }
            }

            item(key = "usage-header") { SectionHeader("Usage") }
            item(key = "usage") {
                val today = remember(state.usageRecords) {
                    val day = java.time.LocalDate.now()
                    io.github.ninock.pocketcode.data.UsageAggregation.total(state.usageRecords.filter { it.day() == day })
                }
                GroupedRow(
                    0,
                    1,
                    "Tokens and cost",
                    supporting = if (state.usageRecords.isEmpty()) "By chat, project and day" else "Today: ${formatUsageShort(today.usage)}",
                    icon = Icons.Rounded.Insights,
                    onClick = { showUsage = true },
                    trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }

            item(key = "appearance-header") { SectionHeader("Appearance") }
            item(key = "appearance") { ThemeChooser(state.themeMode, onSetThemeMode) }

            item(key = "tools-header") { SectionHeader("Developer tools") }
            itemsIndexed(DevStack.entries, key = { _, stack -> "stack-${stack.name}" }) { index, stack ->
                DevStackRow(
                    stack = stack,
                    index = index,
                    count = DevStack.entries.size,
                    state = state,
                    onInstall = { onInstallDevStack(stack) },
                    onRemove = { stackPendingRemoval = stack },
                )
            }

            item(key = "runtime-header") { SectionHeader("Linux runtime") }
            item(key = "runtime-env") {
                GroupedRow(0, 4, "Ubuntu 20.04 PRoot", supporting = "ARM64 (aarch64) sandbox on this phone", icon = Icons.Rounded.Terminal)
            }
            item(key = "runtime-agents") {
                val installed = AgentKind.entries.mapNotNull { agent -> state.installedAgentVersions[agent]?.let { "${agent.title} $it" } }
                GroupedRow(
                    1,
                    4,
                    "Installed agents",
                    supporting = installed.joinToString(" · ").ifBlank { "No verified agent installation" },
                    icon = Icons.Rounded.SmartToy,
                )
            }
            item(key = "runtime-clear") {
                GroupedRow(
                    2,
                    4,
                    "Clear terminal history",
                    supporting = if (terminalCleared) "Terminal history cleared" else "Removes saved commands and output",
                    icon = Icons.Rounded.DeleteSweep,
                    onClick = { onClearTerminal(); terminalCleared = true },
                    trailing = { if (terminalCleared) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
            item(key = "runtime-reliability") {
                GroupedRow(
                    3,
                    4,
                    "Runtime reliability",
                    supporting = "Help when large builds stop unexpectedly",
                    icon = Icons.Rounded.Memory,
                    onClick = { showReliabilityHelp = !showReliabilityHelp },
                    trailing = { Icon(if (showReliabilityHelp) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                    below = {
                        AnimatedVisibility(showReliabilityHelp) {
                            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Android Developer options may provide a child-process restriction toggle that lets long builds keep running.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilledTonalButton(
                                    onClick = {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                                            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Open Developer options") }
                            }
                        }
                    },
                )
            }

            if (BuildConfig.DEBUG) {
                item(key = "debug-header") { SectionHeader("Debug") }
                item(key = "debug") {
                    DebugUpdateChannelSection(
                        initialUrl = initialDebugUpdateManifestUrl,
                        onSave = onSetDebugUpdateManifestUrl,
                        onClear = onClearDebugUpdateManifestUrl,
                    )
                }
            }

            item(key = "about-header") { SectionHeader("About") }
            item(key = "about-app") {
                GroupedRow(
                    0,
                    2,
                    "PocketCode",
                    supporting = "Local AI coding workspace",
                    icon = Icons.Rounded.Info,
                    trailing = {
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
            item(key = "about-privacy") {
                GroupedRow(
                    1,
                    2,
                    "Privacy policy",
                    supporting = "How local data and AI provider requests are handled",
                    icon = Icons.Rounded.PrivacyTip,
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
                    },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open privacy policy", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

/** Dark / light / system as an Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeChooser(mode: AppThemeMode, onSetThemeMode: (AppThemeMode) -> Unit) {
    val options = listOf(
        Triple(AppThemeMode.SYSTEM, "System", Icons.Rounded.PhoneAndroid),
        Triple(AppThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
        Triple(AppThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
    )
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Text(
                when (mode) {
                    AppThemeMode.SYSTEM -> "Follows your phone's dark mode"
                    AppThemeMode.LIGHT -> "Always light"
                    AppThemeMode.DARK -> "Always dark"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                options.forEachIndexed { index, (option, label, icon) ->
                    ToggleButton(
                        checked = mode == option,
                        onCheckedChange = { onSetThemeMode(option) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) {
                        Icon(icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Colours follow your wallpaper." else "Colours use the PocketCode orange palette.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DevStackRow(
    stack: DevStack,
    index: Int,
    count: Int,
    state: AppUiState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val installed = stack in state.installedDevStacks
    val installing = state.devStackInstalling == stack
    val removing = installing && state.devStackRemoving
    GroupedRow(
        index = index,
        count = count,
        headline = stack.label,
        supporting = stack.installsSummary,
        icon = devStackIcon(stack),
        trailing = {
            Spacer(Modifier.width(8.dp))
            when {
                removing -> Text("Removing…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                installing -> Text("${(state.devStackProgress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                installed && stack == DevStack.WEB -> Text("Included", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                installed -> TextButton(onClick = onRemove, enabled = state.devStackInstalling == null) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
                else -> FilledTonalButton(onClick = onInstall, enabled = state.devStackInstalling == null, shapes = ButtonDefaults.shapes()) { Text("Add") }
            }
        },
        below = {
            AnimatedVisibility(installing) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearWavyProgressIndicator(progress = { state.devStackProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        state.devStackBytes?.let { (downloaded, total) ->
                            buildString {
                                append("${formatTransferMb(downloaded)} of ${formatTransferMb(total)}")
                                state.devStackBytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                                    append(" · ${formatTransferSpeed(speed)} · ${formatTransferEta(downloaded, total, speed)} left")
                                }
                            }
                        } ?: (state.devStackMessage ?: "Processing…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

private fun devStackIcon(stack: DevStack): ImageVector = when (stack) {
    DevStack.WEB -> Icons.Rounded.Language
    DevStack.PYTHON -> Icons.Rounded.DataObject
    DevStack.ANDROID -> Icons.Rounded.Android
    DevStack.CPP -> Icons.Rounded.Memory
    DevStack.PHP -> Icons.Rounded.Code
}

private fun formatTransferMb(bytes: Long): String = "%.1f MB".format(bytes.coerceAtLeast(0L) / 1_048_576.0)

private fun formatTransferSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576L -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
    else -> "%.0f KB/s".format(bytesPerSecond / 1_024.0)
}

private fun formatTransferEta(downloaded: Long, total: Long, bytesPerSecond: Long): String {
    val seconds = ((total - downloaded).coerceAtLeast(0L) / bytesPerSecond.coerceAtLeast(1L)).coerceAtLeast(1L)
    return if (seconds >= 60L) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}

@Composable
private fun SettingsAccordion(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun AntigravityConnectionSettings(
    state: AppUiState,
    code: String,
    onCode: (String) -> Unit,
    onStartLogin: () -> Unit,
    onSubmitCode: () -> Unit,
    onLogout: () -> Unit,
    onRefreshModels: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetEffort: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val auth = state.antigravityAuth
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Official Antigravity CLI", fontWeight = FontWeight.SemiBold)
            Text(
                auth.message ?: if (auth.status == AntigravityAuthStatus.SIGNED_IN) {
                    auth.accountEmail?.let { "Connected as $it" } ?: "Google account connected"
                } else "Sign in using Google's browser flow.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (auth.status) {
                AntigravityAuthStatus.SIGNED_IN -> OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Log out of Antigravity")
                }
                AntigravityAuthStatus.STARTING, AntigravityAuthStatus.COMPLETING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                AntigravityAuthStatus.AWAITING_CODE -> {
                    auth.authorizationUrl?.let { url ->
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(url)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy Google sign-in URL") }
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCode,
                        label = { Text("One-time authorization code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onSubmitCode, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Complete sign-in")
                    }
                }
                AntigravityAuthStatus.SIGNED_OUT, AntigravityAuthStatus.ERROR -> Button(
                    onClick = onStartLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (auth.status == AntigravityAuthStatus.ERROR) "Reconnect with Google" else "Sign in with Google") }
            }
        }
    }

    if (auth.status == AntigravityAuthStatus.SIGNED_IN) {
        Text("Model", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.antigravityModel,
            onValueChange = onSetModel,
            label = { Text("Antigravity model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onRefreshModels,
            enabled = !state.antigravityModelsLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.antigravityModelsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Refresh models")
        }
        state.antigravityModels.forEach { model ->
            Row(
                Modifier.fillMaxWidth().clickable { onSetModel(model) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model, Modifier.weight(1f), fontSize = 12.sp)
                SelectionDot(state.antigravityModel == model)
            }
        }
        Text("Reasoning effort", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("low", "medium", "high").forEach { effort ->
                OutlinedButton(onClick = { onSetEffort(effort) }, modifier = Modifier.weight(1f)) {
                    Text(effort.replaceFirstChar(Char::uppercase))
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(12.dp)) {
        Text(
            "Antigravity runs with automatic tool approval. It can edit files and execute commands inside the selected project. Review generated changes before keeping them.",
            Modifier.fillMaxWidth().padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ConnectionSettings(
    state: AppUiState,
    selectedKind: ProviderKind,
    baseUrl: String,
    model: String,
    dshApi: String,
    apiKey: String,
    models: List<DiscoveredModel>,
    isDiscovering: Boolean,
    isValidating: Boolean,
    status: String?,
    statusOk: Boolean,
    savedKeys: List<ApiKeyInfo>,
    newKeyName: String,
    newApiKey: String,
    newKeyVisible: Boolean,
    onPing: () -> Unit,
    onProvider: (ProviderKind) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onDshApi: (String) -> Unit,
    onNewKeyName: (String) -> Unit,
    onNewApiKey: (String) -> Unit,
    onToggleNewKey: () -> Unit,
    onAddKey: () -> Unit,
    onActivateKey: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
    onModels: () -> Unit,
    onValidate: () -> Unit,
) {
    val visibleKinds = remember(state.agentKind) { providersForAgent(state.agentKind) }
    var providerExpanded by rememberSaveable { mutableStateOf(false) }
    var addKeyExpanded by rememberSaveable(savedKeys.isEmpty()) { mutableStateOf(savedKeys.isEmpty()) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(
                when (state.apiPingStatus) {
                    ApiPingStatus.OK -> Color(0xFF58C9A3)
                    ApiPingStatus.FAILED -> MaterialTheme.colorScheme.error
                    ApiPingStatus.PINGING -> PocketOrange
                    ApiPingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }, CircleShape,
            ))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Active connection", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.provider.model.ifBlank { "Not configured" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.activeApiKeyName?.let { name ->
                    Text("Key: $name", fontSize = 11.sp, color = PocketOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.apiPingMessage?.let {
                    Text(it, fontSize = 11.sp, color = if (state.apiPingStatus == ApiPingStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = onPing, enabled = state.apiPingStatus != ApiPingStatus.PINGING, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (state.apiPingStatus == ApiPingStatus.PINGING) "Testing…" else "Test")
            }
        }
    }

    Text("Provider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { providerExpanded = !providerExpanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (providerExpanded) PocketOrange else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selectedKind.title, fontWeight = FontWeight.SemiBold)
                Text(selectedKind.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(if (providerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Choose provider")
        }
    }
    AnimatedVisibility(providerExpanded) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
            Column {
                visibleKinds.forEachIndexed { index, kind ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onProvider(kind)
                        providerExpanded = false
                    }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.title, fontWeight = FontWeight.Medium)
                        Text(kind.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    SelectionDot(selectedKind == kind)
                }
                if (index != visibleKinds.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
            }
        }
    }

    if (selectedKind.fixedBaseUrl) {
        Text(
            selectedKind.defaultBaseUrl,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        OutlinedTextField(baseUrl, onBaseUrl, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    if (state.agentKind == AgentKind.DEEPSEEK_HARNESS && selectedKind in DSH_PROTOCOL_PROVIDERS) {
        Text("Gateway protocol", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            Column {
                listOf("anthropic-messages", "openai-completions", "openai-responses").forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onDshApi(option) }.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option, Modifier.weight(1f), fontSize = 13.sp)
                        SelectionDot(dshApi == option)
                    }
                }
            }
        }
    }
    Text("Model", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(model, onModel, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = onModels, enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isDiscovering, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        if (isDiscovering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(if (models.isEmpty()) Icons.Default.Search else Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(if (models.isEmpty()) "Find available models" else "Available models (${models.size})")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("API keys", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${savedKeys.size} saved · automatic failover enabled", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { addKeyExpanded = !addKeyExpanded }) {
            Text(if (addKeyExpanded) "Cancel" else "Add key")
        }
    }
    if (savedKeys.isNotEmpty()) {
        Text("Saved API keys", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            Column {
                savedKeys.forEachIndexed { index, key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onActivateKey(key.id) }.padding(start = 13.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(key.name, fontWeight = FontWeight.Medium)
                            Text(
                                if (key.isActive) "Active now · tap another key to switch" else "Tap to make active",
                                fontSize = 11.sp,
                                color = if (key.isActive) PocketOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SelectionDot(key.isActive)
                        IconButton(onClick = { onRemoveKey(key.id) }) {
                            Icon(Icons.Default.DeleteSweep, "Remove ${key.name}", Modifier.size(18.dp))
                        }
                    }
                    if (index != savedKeys.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp))
                }
            }
        }
    }
    AnimatedVisibility(addKeyExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                newKeyName,
                onNewKeyName,
                label = { Text("Key name") },
                placeholder = { Text("Work, Personal, Backup…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                newApiKey,
                onNewApiKey,
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (newKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onToggleNewKey) {
                        Icon(if (newKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide new key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onAddKey()
                    addKeyExpanded = false
                },
                enabled = newKeyName.isNotBlank() && newApiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Save API key")
            }
        }
    }
    if (status != null) {
        Text(status, fontSize = 12.sp, color = if (statusOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = onValidate,
        enabled = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank() && !isDiscovering && !isValidating,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (isValidating) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isValidating) "Checking connection" else "Test connection and save")
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(if (selected) 2.dp else 1.dp, if (selected) PocketOrange else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(9.dp).background(PocketOrange, CircleShape))
    }
}



@Composable
private fun DebugUpdateChannelSection(
    initialUrl: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    val isOverridden = initialUrl.isNotBlank()
    SettingsAccordion(
        title = "Update channel",
        subtitle = if (isOverridden) "Overridden · debug only" else "Default GitHub release",
        icon = Icons.Default.Tune,
        expanded = expanded,
        onClick = { expanded = !expanded },
    ) {
        Text(
            "Debug builds only. Paste the temporary manifest URL from Cloudflare Tunnel, ngrok, or any HTTPS server hosting pocketcode-update.json and a newer APK.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Manifest URL") },
            placeholder = { Text("https://your-tunnel.example/pocketcode-update.json") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSave(url) },
                enabled = url.startsWith("https://"),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isOverridden) "Replace" else "Use & check")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = isOverridden,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
        }
        if (isOverridden) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Current: $initialUrl",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
