package com.jarves.mh.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jarves.mh.BuildConfig
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.DSH_PROTOCOL_PROVIDERS
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.inferredDshApiForUrl
import com.jarves.mh.model.providersForAgent
import com.jarves.mh.network.ConnectionValidation
import com.jarves.mh.network.DiscoveredModel
import com.jarves.mh.network.ModelDiscoveryResult
import com.jarves.mh.runtime.AntigravityAuthStatus
import com.jarves.mh.runtime.ClaudeAuthState
import com.jarves.mh.runtime.RuntimeExecutionService
import com.jarves.mh.runtime.RuntimeSetupService
import com.jarves.mh.runtime.supportsArm64Runtime
import com.jarves.mh.ui.theme.LocalDarkTheme
import com.jarves.mh.ui.theme.LocalPocketExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Shared setup building blocks
// ---------------------------------------------------------------------------------------------

/** The app glyph inside an Expressive cookie shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BrandMark(modifier: Modifier = Modifier, size: Dp = 56.dp, spinning: Boolean = false) {
    ShapeBadge(
        icon = Icons.Rounded.Terminal,
        modifier = modifier,
        polygon = MaterialShapes.Cookie9Sided,
        size = size,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        spinning = spinning,
    )
}

/**
 * Common layout for every first-run screen: brand bar, optional step progress, a spinning hero
 * shape, a large headline, scrollable content and actions pinned to the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupScaffold(
    title: String,
    heroIcon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    heroPolygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    heroContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    heroContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    step: Int? = null,
    stepCount: Int = 3,
    onBack: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SetupTopBar(onBack, onToggleTheme) },
        bottomBar = { if (actions != null) SetupActionBar(actions) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
        ) {
            if (step != null) {
                Spacer(Modifier.height(4.dp))
                StepProgress(step, stepCount)
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }
            AnimatedContent(
                targetState = heroIcon,
                transitionSpec = { (scaleIn(motion.defaultSpatialSpec(), 0.6f) + fadeIn()).togetherWith(fadeOut()) },
                label = "setupHero",
            ) { icon ->
                ShapeBadge(icon = icon, polygon = heroPolygon, size = 72.dp, container = heroContainer, content = heroContent, spinning = true)
            }
            Spacer(Modifier.height(20.dp))
            if (eyebrow != null) {
                Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (slideInVertically(motion.defaultSpatialSpec()) { it / 3 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "setupTitle",
            ) { text ->
                Text(text, style = MaterialTheme.typography.headlineLarge)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Brand title bar shared by every first-run screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupTopBar(onBack: (() -> Unit)?, onToggleTheme: (() -> Unit)?) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text("Mobile Harness", style = MaterialTheme.typography.titleMedium)
            }
        },
        navigationIcon = {
            AnimatedVisibility(onBack != null, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
                IconButton(onClick = { onBack?.invoke() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
        },
        actions = {
            if (onToggleTheme != null) {
                IconButton(onClick = onToggleTheme) {
                    Icon(if (LocalDarkTheme.current) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Toggle theme")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Actions pinned to the bottom of a first-run screen, above the navigation bar. */
@Composable
private fun SetupActionBar(actions: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = actions,
    )
}

/** Static hero, eyebrow, headline and subtitle at the top of a first-run page. */
@Composable
private fun SetupPageHeader(
    title: String,
    heroIcon: ImageVector,
    heroPolygon: RoundedPolygon,
    subtitle: String? = null,
    eyebrow: String? = null,
) {
    ShapeBadge(
        icon = heroIcon,
        polygon = heroPolygon,
        size = 72.dp,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        spinning = true,
    )
    Spacer(Modifier.height(20.dp))
    if (eyebrow != null) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}

/** Segmented step progress: the current segment is slightly thicker. */
@Composable
private fun StepProgress(step: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val color by animateColorAsState(
                if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "stepColor",
            )
            val height by animateDpAsState(if (index == step) 8.dp else 6.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "stepHeight")
            Box(Modifier.weight(1f).height(height).clip(CircleShape).background(color))
        }
    }
}

/** Full-width primary action used at the bottom of setup screens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = Icons.AutoMirrored.Rounded.ArrowForward,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        if (loading) {
            LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        if (icon != null && !loading) {
            Spacer(Modifier.width(10.dp))
            Icon(icon, null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
        }
    }
}

/** A selectable row inside a grouped list, with a radio button or checkbox on the end. */
@Composable
private fun ChoiceRow(
    index: Int,
    count: Int,
    selected: Boolean,
    headline: String,
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    badge: String? = null,
    control: @Composable () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "choiceContainer",
    )
    val shape = groupedShape(index, count)
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(headline, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                        )
                    }
                }
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            control()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = body)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = body)
    }
}

/** A circular monogram in a brand colour, for agents and providers. */
@Composable
private fun Monogram(text: String, accent: Color) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
    }
}

/** A small status chip: green for OK, red for a problem. */
@Composable
private fun StatusChip(text: String, ok: Boolean) {
    val extra = LocalPocketExtraColors.current
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (ok) extra.successContainer else MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (ok) extra.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
    )
}

// ---------------------------------------------------------------------------------------------
// Loading and errors
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StartupLoadingScreen(state: AppUiState, onToggleTheme: () -> Unit = {}) {
    val view = LocalView.current
    // Runtime download + install can take 10+ minutes; keep the screen on while this screen is visible.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val messages = remember { listOf("Setting up your workspace", "Preparing your coding tools", "Almost ready") }
    var messageIndex by remember(state.startupStage) { mutableIntStateOf(0) }
    LaunchedEffect(messages) {
        while (true) {
            delay(3_000)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrandMark(size = 96.dp, spinning = true)
            Spacer(Modifier.height(28.dp))
            Text("Mobile Harness", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = messages[messageIndex],
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut()) },
                label = "startupMessage",
            ) { message ->
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            LoadingIndicator(Modifier.size(56.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StartupErrorScreen(
    message: String?,
    isOffline: Boolean,
    logs: List<String>,
    onToggleTheme: () -> Unit = {},
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SetupScaffold(
        title = if (isOffline) "You're offline" else "Setup didn't finish",
        subtitle = message ?: "Please try again.",
        heroIcon = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        heroPolygon = MaterialShapes.Ghostish,
        heroContainer = MaterialTheme.colorScheme.errorContainer,
        heroContent = MaterialTheme.colorScheme.onErrorContainer,
        onToggleTheme = onToggleTheme,
        actions = {
            if (isOffline) {
                SetupPrimaryButton(
                    text = "Open internet settings",
                    icon = null,
                    onClick = {
                        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
                        context.startActivity(Intent(action))
                    },
                )
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            } else {
                SetupPrimaryButton(text = "Try again", icon = Icons.Rounded.Refresh, onClick = onRetry)
            }
        },
    ) {
        if (logs.isNotEmpty()) {
            SetupLogPanel(logs)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                    Toast.makeText(context, "Setup log copied", Toast.LENGTH_SHORT).show()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy setup logs")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Welcome + background permissions
// ---------------------------------------------------------------------------------------------

private enum class WelcomePage { WELCOME, NOTIFICATIONS, BATTERY }

/**
 * First-run welcome flow: a welcome page, then the two settings that keep coding tasks running in
 * the background. Pages slide horizontally in the direction of travel.
 *
 * No separate step is needed for keeping the CPU awake during a task: WAKE_LOCK is an install-time
 * permission and [RuntimeExecutionService] takes and releases the wake lock on its own.
 */
@Composable
internal fun BackgroundTaskSetupScreen(
    showWelcome: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    fun notificationsAllowed(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun batteryUnrestricted(): Boolean = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val pages = remember(showWelcome) {
        if (showWelcome) WelcomePage.entries.toList() else listOf(WelcomePage.NOTIFICATIONS, WelcomePage.BATTERY)
    }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(pages.indices)]
    var notificationGranted by remember { mutableStateOf(notificationsAllowed()) }
    var batteryGranted by remember { mutableStateOf(batteryUnrestricted()) }

    fun refresh() {
        notificationGranted = notificationsAllowed()
        batteryGranted = batteryUnrestricted()
    }
    fun next() {
        if (pageIndex < pages.lastIndex) pageIndex += 1 else onContinue()
    }
    // Catches changes made from the notification shade or Settings outside the buttons below.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    BackHandler(enabled = pageIndex > 0) { pageIndex -= 1 }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (notificationGranted) {
            // Created only once notifications are allowed: for apps targeting API 32 or lower,
            // creating a channel is what makes Android 13+ show its own permission prompt.
            RuntimeExecutionService.ensureNotificationChannels(context)
            RuntimeSetupService.ensureNotificationChannel(context)
            if (page == WelcomePage.NOTIFICATIONS) next()
        }
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (batteryGranted && page == WelcomePage.BATTERY) next()
    }

    // The system permission dialog is skipped on purpose. Sideloaded builds target API 28, where
    // Android 13+ answers a POST_NOTIFICATIONS request with an instant denial and no dialog, so the
    // button goes straight to this app's notification settings on every build.
    fun openNotificationSettings() {
        runCatching {
            notificationSettingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }.onFailure {
            notificationSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }
    fun openBatterySettings() {
        runCatching { batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .onFailure {
                batteryLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
    }

    val permissionOffset = if (showWelcome) 1 else 0
    fun permissionEyebrow(target: WelcomePage) = "Step ${pages.indexOf(target) - permissionOffset + 1} of 2 · Permissions"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SetupTopBar(onBack = if (pageIndex > 0) ({ pageIndex -= 1 }) else null, onToggleTheme = onToggleTheme) },
        bottomBar = {
            SetupActionBar {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(210, delayMillis = 90)).togetherWith(fadeOut(tween(90))) },
                    label = "welcomeActions",
                ) { actionsPage ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (actionsPage) {
                            WelcomePage.WELCOME -> SetupPrimaryButton(text = "Get started", onClick = ::next)
                            WelcomePage.NOTIFICATIONS -> SetupPrimaryButton(
                                text = if (notificationGranted) "Next" else "Turn on notifications",
                                icon = if (notificationGranted) Icons.AutoMirrored.Rounded.ArrowForward else null,
                                onClick = { if (notificationGranted) next() else openNotificationSettings() },
                            )
                            WelcomePage.BATTERY -> SetupPrimaryButton(
                                text = if (batteryGranted) "Finish" else "Open battery settings",
                                icon = if (batteryGranted) Icons.Rounded.Check else null,
                                onClick = { if (batteryGranted) next() else openBatterySettings() },
                            )
                        }
                        // Fixed height so the page area never resizes when the secondary action changes.
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            when {
                                actionsPage == WelcomePage.WELCOME -> Text(
                                    "Takes less than a minute",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                actionsPage == WelcomePage.NOTIFICATIONS && !notificationGranted ->
                                    TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text("Continue without notifications") }
                                actionsPage == WelcomePage.BATTERY && !batteryGranted ->
                                    TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text("Continue without battery exemption") }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StepProgress(pageIndex, pages.size, Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp))
            AnimatedContent(
                targetState = pageIndex,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    // Push/pop: the page further along the flow always sits on top and travels the
                    // full width, while the page beneath shifts a quarter-width and fades.
                    val forward = targetState > initialState
                    val slide = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                    val fade = tween<Float>(durationMillis = 300)
                    val transform = if (forward) {
                        slideInHorizontally(slide) { width -> width }
                            .togetherWith(slideOutHorizontally(slide) { width -> -width / 4 } + fadeOut(fade))
                    } else {
                        (slideInHorizontally(slide) { width -> -width / 4 } + fadeIn(fade))
                            .togetherWith(slideOutHorizontally(slide) { width -> width })
                    }
                    transform.apply { targetContentZIndex = targetState.toFloat() }
                },
                label = "welcomePage",
            ) { index ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(24.dp))
                    when (pages[index]) {
                        WelcomePage.WELCOME -> WelcomePageContent()
                        WelcomePage.NOTIFICATIONS -> PermissionPageContent(
                            eyebrow = permissionEyebrow(WelcomePage.NOTIFICATIONS),
                            title = "Task notifications",
                            subtitle = "See live progress and get an alert when your agent finishes or needs you.",
                            heroIcon = Icons.Rounded.Notifications,
                            current = 0,
                            notificationGranted = notificationGranted,
                            batteryGranted = batteryGranted,
                            hint = "Turn on “Allow notifications” on the next screen, then come back.".takeUnless { notificationGranted },
                            note = "Only task progress, completion and error notifications are sent, and you can stop any task from its notification.",
                        )
                        WelcomePage.BATTERY -> PermissionPageContent(
                            eyebrow = permissionEyebrow(WelcomePage.BATTERY),
                            title = "Background reliability",
                            subtitle = "Let Mobile Harness keep working when you lock the phone or switch apps.",
                            heroIcon = Icons.Rounded.BatterySaver,
                            current = 1,
                            notificationGranted = notificationGranted,
                            batteryGranted = batteryGranted,
                            hint = "Find Mobile Harness on the next screen and set it to “Not optimized” or “Unrestricted”, then come back."
                                .takeUnless { batteryGranted },
                            note = "While a task runs, only the processor stays awake, never the screen. It is released when the task ends, after 90 minutes at most, and needs no extra permission.",
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomePageContent() {
    SetupPageHeader(
        title = "Welcome to Mobile Harness",
        subtitle = "A complete coding workspace in your pocket. AI agents, a real Linux terminal and your projects, all running on this phone.",
        eyebrow = "Welcome",
        heroIcon = Icons.Rounded.Terminal,
        heroPolygon = MaterialShapes.Cookie9Sided,
    )
    GroupedRow(0, 3, "AI coding agents", supporting = "Pick an agent and let it build, fix and explain code inside your projects.", icon = Icons.Rounded.SmartToy)
    GroupedRow(1, 3, "A real Linux terminal", supporting = "Ubuntu with Node.js, Git and the toolchains you choose.", icon = Icons.Rounded.Terminal)
    GroupedRow(2, 3, "Works in the background", supporting = "Lock your phone and get notified when a task finishes.", icon = Icons.Rounded.Notifications)
    Spacer(Modifier.height(16.dp))
    Text(
        "Next, two quick settings keep your tasks running while Mobile Harness is off screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionPageContent(
    eyebrow: String,
    title: String,
    subtitle: String,
    heroIcon: ImageVector,
    current: Int,
    notificationGranted: Boolean,
    batteryGranted: Boolean,
    hint: String?,
    note: String,
) {
    SetupPageHeader(title = title, subtitle = subtitle, eyebrow = eyebrow, heroIcon = heroIcon, heroPolygon = MaterialShapes.Clover4Leaf)
    val permissions = listOf(
        Triple(Icons.Rounded.Notifications, "Task notifications", notificationGranted),
        Triple(Icons.Rounded.BatterySaver, "Background reliability", batteryGranted),
    )
    permissions.forEachIndexed { index, (icon, headline, done) ->
        ChoiceRow(
            index = index,
            count = permissions.size,
            selected = index == current,
            headline = headline,
            supporting = when {
                done -> "Done"
                index == current -> "Required now"
                index < current -> "Skipped"
                else -> "Up next"
            },
            onClick = null,
            leading = { IconTile(icon) },
            control = {
                if (done) Icon(Icons.Rounded.Check, "Complete", Modifier.padding(end = 8.dp), tint = LocalPocketExtraColors.current.success)
            },
        )
    }
    if (hint != null) {
        Spacer(Modifier.height(16.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Shield, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "You can change these settings later. Android may still stop exceptionally heavy work when the device is low on memory.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    )
}

// ---------------------------------------------------------------------------------------------
// Device check + tool selection
// ---------------------------------------------------------------------------------------------

private data class DevStackVisuals(val icon: ImageVector, val accent: Color)

private fun devStackVisuals(stack: DevStack): DevStackVisuals = when (stack) {
    DevStack.WEB -> DevStackVisuals(Icons.Rounded.Language, Color(0xFF38BDF8))
    DevStack.PYTHON -> DevStackVisuals(Icons.Rounded.Terminal, Color(0xFFF2B531))
    DevStack.ANDROID -> DevStackVisuals(Icons.Rounded.Android, Color(0xFF3DDC84))
    DevStack.CPP -> DevStackVisuals(Icons.Rounded.Memory, Color(0xFFA78BFA))
    DevStack.PHP -> DevStackVisuals(Icons.Rounded.Dns, Color(0xFF818CF8))
}

private fun agentMonogram(agent: AgentKind): Pair<String, Color> = when (agent) {
    AgentKind.CLAUDE_CODE -> "CC" to Color(0xFFD97757)
    AgentKind.DEEPSEEK_HARNESS -> "DS" to Color(0xFF4D6BFE)
    AgentKind.ANTIGRAVITY -> "AG" to Color(0xFF4285F4)
}

@Composable
internal fun RuntimeSetupPromptScreen(
    selectedStacks: Set<DevStack>,
    selectedAgent: AgentKind = AgentKind.CLAUDE_CODE,
    onToggleTheme: () -> Unit = {},
    onToggleStack: (DevStack) -> Unit,
    onSelectAgent: (AgentKind) -> Unit = {},
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryInfo = remember { ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo) }
    val totalRamLabel = String.format(java.util.Locale.US, "%.1f", memoryInfo.totalMem.toDouble() / 1_073_741_824.0)
    val arm64 = supportsArm64Runtime(Build.SUPPORTED_ABIS, System.getProperty("os.arch"))
    // Android reports usable memory after hardware reservations, so RAM is informational only;
    // it must not reject nominal 4 GB phones.
    val compatible = arm64
    var currentStep by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    LaunchedEffect(currentStep) { scrollState.scrollTo(0) }
    if (currentStep > 0) BackHandler { currentStep = 0 }

    if (currentStep == 0) {
        SetupScaffold(
            title = if (compatible) "Ready to build on this phone" else "This phone isn't supported",
            subtitle = if (compatible) {
                "Your phone meets the requirements. Choose your coding tools next and Mobile Harness handles the setup."
            } else {
                "Mobile Harness needs a 64-bit ARM processor to run its Linux environment."
            },
            eyebrow = "Device check",
            heroIcon = Icons.Rounded.PhoneAndroid,
            heroPolygon = MaterialShapes.Sunny,
            onToggleTheme = onToggleTheme,
            scrollState = scrollState,
            actions = {
                SetupPrimaryButton(
                    text = if (compatible) "Choose your tools" else "Device not supported",
                    enabled = compatible,
                    onClick = { currentStep = 1 },
                )
                Text(
                    "You can change tools later",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            },
        ) {
            GroupedRow(0, 4, "System compatibility", supporting = if (compatible) "Your device is ready" else "Unsupported processor", icon = Icons.Rounded.PhoneAndroid, trailing = {
                StatusChip(if (compatible) "Ready" else "Unsupported", ok = compatible)
            })
            GroupedRow(1, 4, "Memory", supporting = "$totalRamLabel GB usable", icon = Icons.Rounded.Memory)
            GroupedRow(2, 4, "Processor", supporting = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a", icon = Icons.Rounded.Code, trailing = {
                if (!arm64) Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
            })
            GroupedRow(3, 4, "Download", supporting = "149–774 MB, based on the tools you pick", icon = Icons.Rounded.Storage)
        }
    } else {
        SetupScaffold(
            title = "Choose your tools",
            subtitle = "Start lightweight. You can install more toolchains later from Settings.",
            eyebrow = "Toolchain setup",
            heroIcon = Icons.Rounded.Code,
            heroPolygon = MaterialShapes.Cookie6Sided,
            onBack = { currentStep = 0 },
            onToggleTheme = onToggleTheme,
            scrollState = scrollState,
            actions = {
                Text(
                    toolchainDownloadSummary(selectedStacks, selectedAgent),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                SetupPrimaryButton(
                    text = if (compatible) "Install Mobile Harness" else "Device not supported",
                    icon = Icons.Rounded.Download,
                    enabled = compatible,
                    onClick = onDownload,
                )
            },
        ) {
            GroupedRow(
                0,
                1,
                "Core runtime",
                supporting = if (BuildConfig.OFFLINE_RUNTIME_BUNDLES) "Ubuntu · Node.js · npm · Git · 68.8 MB" else "Ubuntu · Node.js · npm · Git · 68.8 MB download",
                icon = Icons.Rounded.Terminal,
                trailing = { Icon(Icons.Rounded.Check, "Included", tint = LocalPocketExtraColors.current.success) },
            )
            SectionHeader("Coding agent")
            AgentKind.entries.forEachIndexed { index, agent ->
                AgentChoiceRow(index, AgentKind.entries.size, agent, selected = selectedAgent == agent) { onSelectAgent(agent) }
            }
            Text(
                "Only the selected agent is downloaded. You can install or switch agents later in Settings.",
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionHeader("Optional toolchains")
            DevStack.entries.forEachIndexed { index, stack ->
                val locked = stack == DevStack.WEB
                val selected = locked || stack in selectedStacks
                val visuals = devStackVisuals(stack)
                ChoiceRow(
                    index = index,
                    count = DevStack.entries.size,
                    selected = selected,
                    headline = stack.label,
                    supporting = when (stack) {
                        DevStack.WEB -> "Included with the core runtime"
                        DevStack.PYTHON -> "Scripts, automation and backends"
                        DevStack.ANDROID -> "Java and Kotlin build tools"
                        DevStack.CPP -> "Native apps and command-line tools"
                        DevStack.PHP -> "PHP sites and Laravel projects"
                    } + stackDownloadLabel(stack),
                    onClick = if (locked) null else ({ onToggleStack(stack) }),
                    leading = { IconTile(visuals.icon, tint = visuals.accent) },
                    control = { Checkbox(checked = selected, onCheckedChange = if (locked) null else ({ onToggleStack(stack) }), enabled = !locked) },
                )
            }
        }
    }
}

@Composable
private fun AgentChoiceRow(index: Int, count: Int, agent: AgentKind, selected: Boolean, onClick: () -> Unit) {
    val (mark, accent) = agentMonogram(agent)
    ChoiceRow(
        index = index,
        count = count,
        selected = selected,
        headline = agent.title,
        supporting = "${agent.subtitle} · ${agent.downloadNote}",
        badge = if (agent == AgentKind.DEEPSEEK_HARNESS) "Recommended" else null,
        onClick = onClick,
        leading = { Monogram(mark, accent) },
        control = { RadioButton(selected = selected, onClick = onClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSwitchSheet(selected: AgentKind, onSelect: (AgentKind) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Choose coding agent", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Switch if the current service is unavailable. Your existing sign-ins and keys stay saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
            )
            AgentKind.entries.forEachIndexed { index, agent ->
                AgentChoiceRow(index, AgentKind.entries.size, agent, selected = agent == selected) { onSelect(agent) }
            }
        }
    }
}

private const val CORE_RUNTIME_DOWNLOAD_MB = 69
private const val CLAUDE_RUNTIME_DOWNLOAD_MB = 72
private const val DSH_RUNTIME_DOWNLOAD_MB = 27
private const val AGY_RUNTIME_DOWNLOAD_MB = 40
private const val PYTHON_RUNTIME_DOWNLOAD_MB = 55
private const val ANDROID_RUNTIME_DOWNLOAD_MB = 570

private fun setupTimeEstimate(selected: Set<DevStack>): String {
    var minimumMinutes = 3
    var maximumMinutes = 5
    if (DevStack.PYTHON in selected) { minimumMinutes += 1; maximumMinutes += 2 }
    if (DevStack.ANDROID in selected) { minimumMinutes += 7; maximumMinutes += 10 }
    if (DevStack.CPP in selected) { minimumMinutes += 3; maximumMinutes += 5 }
    if (DevStack.PHP in selected) { minimumMinutes += 2; maximumMinutes += 4 }
    return "$minimumMinutes–$maximumMinutes minutes"
}

private fun stackDownloadLabel(stack: DevStack): String = when {
    stack == DevStack.WEB -> ""
    BuildConfig.OFFLINE_RUNTIME_BUNDLES && stack in setOf(DevStack.PYTHON, DevStack.ANDROID) -> " · included"
    !BuildConfig.OFFLINE_RUNTIME_BUNDLES && stack == DevStack.PYTHON -> " · 55 MB"
    !BuildConfig.OFFLINE_RUNTIME_BUNDLES && stack == DevStack.ANDROID -> " · 570 MB"
    else -> ""
}

private fun toolchainDownloadSummary(selected: Set<DevStack>, agent: AgentKind): String {
    if (BuildConfig.OFFLINE_RUNTIME_BUNDLES) return "All selected bundles are included in this offline app"
    val total = CORE_RUNTIME_DOWNLOAD_MB +
        when (agent) {
            AgentKind.CLAUDE_CODE -> CLAUDE_RUNTIME_DOWNLOAD_MB
            AgentKind.DEEPSEEK_HARNESS -> DSH_RUNTIME_DOWNLOAD_MB
            AgentKind.ANTIGRAVITY -> AGY_RUNTIME_DOWNLOAD_MB
        } +
        (if (DevStack.PYTHON in selected) PYTHON_RUNTIME_DOWNLOAD_MB else 0) +
        (if (DevStack.ANDROID in selected) ANDROID_RUNTIME_DOWNLOAD_MB else 0)
    val laterPackages = selected.intersect(setOf(DevStack.CPP, DevStack.PHP))
    return buildString {
        append("Download: ")
        append(total)
        append(" MB")
        if (laterPackages.isNotEmpty()) append(" · C/PHP packages download later")
        if (total >= 500) append(" · Wi-Fi recommended")
    }
}

// ---------------------------------------------------------------------------------------------
// Installation progress
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RuntimeInstallationScreen(state: AppUiState, onToggleTheme: () -> Unit = {}) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val progress = state.startupProgress.coerceIn(0f, 1f)
    SetupScaffold(
        title = "Building your workspace",
        subtitle = "You can leave Mobile Harness in the background and follow setup from the notification.",
        eyebrow = "Installing",
        heroIcon = Icons.Rounded.Download,
        heroPolygon = MaterialShapes.SoftBurst,
        onToggleTheme = onToggleTheme,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    state.startupBytes?.let { (downloaded, total) ->
                        Text(
                            "${formatMegabytes(downloaded)} / ${formatMegabytes(total)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Text(
                    state.startupMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 40.dp),
                )
                Text(
                    "Estimated ${setupTimeEstimate(state.selectedDevStacks)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SetupLogPanel(state.startupLogs.ifEmpty { listOf("$ ${state.startupMessage}") })
    }
}

/** Collapsible live setup log. Follows the newest line unless the reader scrolls back. */
@Composable
private fun SetupLogPanel(logs: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var followLatest by rememberSaveable { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(logs.size, logs.lastOrNull()) {
        if (expanded && followLatest) {
            delay(20)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress && expanded) followLatest = scrollState.maxValue - scrollState.value < 32
    }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (expanded) "Live setup log" else logs.lastOrNull().orEmpty(),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Collapse log" else "Expand log", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 200.dp).verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        logs.forEach { line -> Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        Text("▌", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                    if (!followLatest) {
                        TextButton(
                            onClick = {
                                followLatest = true
                                scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Jump to latest") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Antigravity sign-in
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AntigravityOnboardingScreen(
    state: AppUiState,
    onStartLogin: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onContinue: () -> Unit,
    onSelectAgent: (AgentKind) -> Unit,
    onToggleTheme: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var showAgentPicker by rememberSaveable { mutableStateOf(false) }
    if (showAgentPicker) {
        AgentSwitchSheet(
            selected = AgentKind.ANTIGRAVITY,
            onSelect = { agent -> showAgentPicker = false; onSelectAgent(agent) },
            onDismiss = { showAgentPicker = false },
        )
    }
    val status = state.antigravityAuth.status
    SetupScaffold(
        title = "Connect your Google account",
        subtitle = "Mobile Harness runs Google's official agy CLI in its private Linux environment. Google handles sign-in and agy keeps the session.",
        eyebrow = "Set up Antigravity",
        heroIcon = Icons.Rounded.Login,
        heroPolygon = MaterialShapes.Pill,
        onToggleTheme = onToggleTheme,
        actions = {
            when (status) {
                AntigravityAuthStatus.SIGNED_OUT, AntigravityAuthStatus.ERROR ->
                    SetupPrimaryButton(text = "Sign in with Google", icon = Icons.Rounded.Login, onClick = onStartLogin)
                AntigravityAuthStatus.AWAITING_CODE ->
                    SetupPrimaryButton(text = "Complete sign-in", enabled = code.isNotBlank(), onClick = { onSubmitCode(code); code = "" })
                AntigravityAuthStatus.SIGNED_IN -> SetupPrimaryButton(text = "Continue", onClick = onContinue)
                else -> SetupPrimaryButton(text = "Signing in…", onClick = {}, loading = true)
            }
            TextButton(onClick = { showAgentPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Use another coding agent") }
        },
    ) {
        when (status) {
            AntigravityAuthStatus.SIGNED_OUT, AntigravityAuthStatus.ERROR -> {
                state.antigravityAuth.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            AntigravityAuthStatus.STARTING, AntigravityAuthStatus.COMPLETING -> {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ContainedLoadingIndicator(Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (status == AntigravityAuthStatus.STARTING) "Starting the official Antigravity sign-in…" else "Completing Google sign-in…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            AntigravityAuthStatus.AWAITING_CODE -> {
                Text("Google sign-in opened in your browser. Copy the one-time code shown after you approve.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                state.antigravityAuth.authorizationUrl?.let { url ->
                    FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(url)) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy sign-in URL")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Authorization code") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AntigravityAuthStatus.SIGNED_IN -> {
                GroupedRow(0, 1, "Google account connected", supporting = state.antigravityAuth.accountEmail, icon = Icons.Rounded.Check)
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Automatic tool approval is on for Antigravity. It can edit project files and run commands without asking. Changes stay reviewable in Mobile Harness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Provider setup (onboarding)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ProviderSetupScreen(
    initial: ProviderProfile,
    onboarding: Boolean,
    agentKind: AgentKind = AgentKind.CLAUDE_CODE,
    initialStep: Int = if (onboarding) 0 else 1,
    onBack: (() -> Unit)? = null,
    onSave: (ProviderProfile, String) -> Unit,
    onDiscover: suspend (ProviderProfile, String) -> ModelDiscoveryResult,
    onValidate: suspend (ProviderProfile, String, List<DiscoveredModel>) -> ConnectionValidation,
    onSelectAgent: (AgentKind) -> Unit,
    onToggleTheme: (() -> Unit)? = null,
    claudeAuth: ClaudeAuthState = ClaudeAuthState(),
    claudeSignIn: ClaudeSignInActions = ClaudeSignInActions(),
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var selected by rememberSaveable { mutableStateOf(initial.kind) }
    var baseUrl by rememberSaveable { mutableStateOf(initial.baseUrl.ifBlank { initial.kind.defaultBaseUrl }) }
    var model by rememberSaveable { mutableStateOf(initial.model.ifBlank { initial.kind.defaultModel }) }
    var dshApi by rememberSaveable { mutableStateOf(initial.dshApi.ifBlank { "anthropic-messages" }) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showAgentPicker by rememberSaveable { mutableStateOf(false) }

    if (showAgentPicker) {
        AgentSwitchSheet(
            selected = agentKind,
            onSelect = { agent -> showAgentPicker = false; onSelectAgent(agent) },
            onDismiss = { showAgentPicker = false },
        )
    }
    val handleBack: (() -> Unit)? = when {
        step > 1 -> { { step = 1 } }
        !onboarding && onBack != null -> onBack
        else -> null
    }
    if (handleBack != null) BackHandler(onBack = handleBack)

    fun profile(): ProviderProfile {
        val url = if (selected.fixedBaseUrl) selected.defaultBaseUrl else baseUrl.trim()
        return ProviderProfile(selected, url, model.trim(), dshApi = dshApi)
    }

    when (step) {
        0 -> DeviceCheckStep(onToggleTheme = onToggleTheme, onContinue = { step = 1 })
        1 -> ProviderChoiceStep(
            selected = selected,
            agentKind = agentKind,
            onboarding = onboarding,
            onBack = handleBack,
            onToggleTheme = onToggleTheme,
            onSelected = {
                if (selected != it) {
                    selected = it
                    baseUrl = it.defaultBaseUrl
                    model = it.defaultModel
                    apiKey = ""
                }
            },
            onContinue = { step = 2 },
            onChangeAgent = { showAgentPicker = true },
        )
        else -> if (selected == ProviderKind.CLAUDE) {
            ClaudeSubscriptionCredentialsStep(
                token = apiKey,
                hasStoredToken = initial.kind == selected && initial.hasSecret,
                onToken = { apiKey = it },
                onSave = { onSave(profile(), apiKey) },
                onChangeAgent = { showAgentPicker = true },
                onBack = handleBack,
                onToggleTheme = onToggleTheme,
                claudeAuth = claudeAuth,
                claudeSignIn = claudeSignIn,
            )
        } else {
            ProviderCredentialsStep(
                provider = selected,
                agentKind = agentKind,
                baseUrl = baseUrl,
                model = model,
                dshApi = dshApi,
                apiKey = apiKey,
                onBaseUrl = {
                    baseUrl = it
                    if (agentKind == AgentKind.DEEPSEEK_HARNESS && selected == ProviderKind.CUSTOM) dshApi = inferredDshApiForUrl(it)
                },
                onModel = { model = it },
                onDshApi = { dshApi = it },
                onApiKey = { apiKey = it },
                hasStoredSecret = initial.kind == selected && initial.hasSecret,
                onDiscover = { onDiscover(profile(), apiKey) },
                onValidate = { models -> onValidate(profile(), apiKey, models) },
                onSave = { onSave(profile(), apiKey) },
                onChangeAgent = { showAgentPicker = true },
                onBack = handleBack,
                onToggleTheme = onToggleTheme,
            )
        }
    }
}

@Composable
private fun DeviceCheckStep(onToggleTheme: (() -> Unit)?, onContinue: () -> Unit) {
    val context = LocalContext.current
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryInfo = remember { ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo) }
    val totalRamGb = memoryInfo.totalMem.toDouble() / 1_073_741_824.0
    val totalRamLabel = String.format(java.util.Locale.US, "%.1f", totalRamGb)
    val arm64 = supportsArm64Runtime(Build.SUPPORTED_ABIS, System.getProperty("os.arch"))
    SetupScaffold(
        title = "Your phone is the workspace",
        subtitle = "Mobile Harness checks compatibility before downloading its private Linux runtime.",
        heroIcon = Icons.Rounded.PhoneAndroid,
        heroPolygon = MaterialShapes.Sunny,
        step = 0,
        onToggleTheme = onToggleTheme,
        actions = { SetupPrimaryButton(text = if (arm64) "Continue" else "This device is not supported", enabled = arm64, onClick = onContinue) },
    ) {
        GroupedRow(0, 3, "Memory", supporting = "$totalRamLabel GB usable · ${if (totalRamGb >= 7.5) "Full mode" else "Lite mode"}", icon = Icons.Rounded.Memory)
        GroupedRow(1, 3, "Processor", supporting = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown", icon = Icons.Rounded.Code, trailing = {
            StatusChip(if (arm64) "Supported" else "Unsupported", ok = arm64)
        })
        GroupedRow(2, 3, "Android", supporting = "Android ${Build.VERSION.RELEASE}", icon = Icons.Rounded.PhoneAndroid)
        Spacer(Modifier.height(16.dp))
        Text(
            "Only open projects you trust. The local Linux environment is a compatibility layer, not a hardened security sandbox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun providerMonogram(provider: ProviderKind): Pair<String, Color> = when (provider) {
    ProviderKind.CLAUDE -> "C" to Color(0xFFD97757)
    ProviderKind.ANTHROPIC -> "A" to Color(0xFFE7A26D)
    ProviderKind.LLM_ROUTER -> "OR" to Color(0xFF5B8DEF)
    ProviderKind.DEEPSEEK -> "DS" to Color(0xFF4D6BFE)
    ProviderKind.KIMI -> "K" to Color(0xFF8B7CF6)
    ProviderKind.OPENCODE_ZEN -> "Z" to Color(0xFF22C55E)
    ProviderKind.NVIDIA_NIM -> "NV" to Color(0xFF76B900)
    ProviderKind.CUSTOM -> "<>" to Color(0xFFF28C52)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderChoiceStep(
    selected: ProviderKind,
    agentKind: AgentKind,
    onboarding: Boolean,
    onBack: (() -> Unit)?,
    onToggleTheme: (() -> Unit)?,
    onSelected: (ProviderKind) -> Unit,
    onContinue: () -> Unit,
    onChangeAgent: () -> Unit,
) {
    val providers = remember(agentKind) { providersForAgent(agentKind) }
    SetupScaffold(
        title = "Connect your AI",
        subtitle = "Choose how ${agentKind.title} reaches its model. Keys are encrypted with Android secure storage.",
        eyebrow = if (onboarding) "Step 2 of 3" else null,
        heroIcon = Icons.Rounded.SmartToy,
        heroPolygon = MaterialShapes.Clover8Leaf,
        step = if (onboarding) 1 else null,
        onBack = onBack,
        onToggleTheme = onToggleTheme,
        actions = {
            SetupPrimaryButton(text = "Continue", onClick = onContinue)
            TextButton(onClick = onChangeAgent, modifier = Modifier.fillMaxWidth()) { Text("Use another coding agent") }
        },
    ) {
        providers.forEachIndexed { index, provider ->
            val (mark, accent) = providerMonogram(provider)
            ChoiceRow(
                index = index,
                count = providers.size,
                selected = selected == provider,
                headline = provider.title,
                supporting = provider.subtitle,
                badge = if (provider.experimental) "Beta" else null,
                onClick = { onSelected(provider) },
                leading = { Monogram(mark, accent) },
                control = { RadioButton(selected = selected == provider, onClick = { onSelected(provider) }) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderCredentialsStep(
    provider: ProviderKind,
    agentKind: AgentKind,
    baseUrl: String,
    model: String,
    dshApi: String,
    apiKey: String,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onDshApi: (String) -> Unit,
    onApiKey: (String) -> Unit,
    hasStoredSecret: Boolean,
    onDiscover: suspend () -> ModelDiscoveryResult,
    onValidate: suspend (List<DiscoveredModel>) -> ConnectionValidation,
    onSave: () -> Unit,
    onChangeAgent: () -> Unit,
    onBack: (() -> Unit)?,
    onToggleTheme: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var models by remember(baseUrl) { mutableStateOf(emptyList<DiscoveredModel>()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusDetails by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    val hasKey = apiKey.isNotBlank() || hasStoredSecret

    fun discoverModels(openWhenReady: Boolean = true) {
        scope.launch {
            isDiscovering = true
            status = null
            statusDetails = null
            when (val result = onDiscover()) {
                is ModelDiscoveryResult.Success -> {
                    models = result.models
                    statusOk = true
                    status = "Found ${result.models.size} available model${if (result.models.size == 1) "" else "s"}."
                    if (model.isBlank() && result.models.isNotEmpty()) onModel(result.models.first().id)
                    if (openWhenReady && result.models.isNotEmpty()) showModels = true
                }
                is ModelDiscoveryResult.Failure -> {
                    statusOk = false
                    status = result.message
                    statusDetails = result.providerMessage
                }
            }
            isDiscovering = false
        }
    }

    if (showModels) {
        ModelPickerSheet(
            models = models,
            selected = model,
            refreshing = isDiscovering,
            onRefresh = { discoverModels(openWhenReady = false) },
            onSelect = {
                onModel(it)
                status = null
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }

    SetupScaffold(
        title = provider.title,
        subtitle = when {
            agentKind == AgentKind.DEEPSEEK_HARNESS -> "DeepSeek Harness connects through this API endpoint."
            provider.protocol.name.startsWith("OPENAI") -> "Mobile Harness translates Claude Code requests for this provider."
            else -> "Claude Code connects through this API endpoint."
        },
        eyebrow = "Step 3 of 3 · Encrypted locally",
        heroIcon = Icons.Rounded.Key,
        heroPolygon = MaterialShapes.Gem,
        step = 2,
        onBack = onBack,
        onToggleTheme = onToggleTheme,
        actions = {
            SetupPrimaryButton(
                text = if (isValidating) "Checking" else "Continue",
                loading = isValidating,
                enabled = baseUrl.isNotBlank() && model.isNotBlank() && hasKey && !isDiscovering,
                onClick = {
                    scope.launch {
                        isValidating = true
                        status = "Checking API key, model, and agent settings…"
                        statusDetails = null
                        statusOk = true
                        when (val result = onValidate(models)) {
                            is ConnectionValidation.Success -> {
                                status = result.message
                                statusOk = true
                                onSave()
                            }
                            is ConnectionValidation.Failure -> {
                                status = result.message
                                statusDetails = result.providerMessage
                                statusOk = false
                            }
                        }
                        isValidating = false
                    }
                },
            )
            TextButton(onClick = onChangeAgent, modifier = Modifier.fillMaxWidth()) { Text("Use another coding agent") }
        },
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { onBaseUrl(it); status = null; statusDetails = null; models = emptyList() },
                    label = { Text("Base URL") },
                    supportingText = if (provider.fixedBaseUrl) ({ Text("Fixed by ${provider.title}") }) else null,
                    readOnly = provider.fixedBaseUrl,
                    enabled = !provider.fixedBaseUrl,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (agentKind == AgentKind.DEEPSEEK_HARNESS && provider in DSH_PROTOCOL_PROVIDERS && !provider.fixedProtocol) {
                    DshApiProtocolPicker(selected = dshApi, onSelected = { onDshApi(it); status = null })
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { onApiKey(it); status = null; statusDetails = null },
                    label = { Text("API key") },
                    placeholder = { Text(if (hasStoredSecret) "Saved securely. Leave blank to keep it" else "Enter your API key") },
                    supportingText = if (hasStoredSecret && apiKey.isBlank()) ({ Text("A saved key is ready to use") }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { onModel(it); status = null; statusDetails = null },
                    label = { Text("Model") },
                    supportingText = { Text("Pick an available model or enter an exact model ID.") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(
                    onClick = { if (models.isEmpty()) discoverModels() else showModels = true },
                    enabled = baseUrl.isNotBlank() && hasKey && !isDiscovering && !isValidating,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isDiscovering) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (models.isEmpty()) "Find available models" else "Available models (${models.size})")
                }
            }
        }
        AnimatedVisibility(status != null) {
            Column(Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)) {
                Text(
                    status.orEmpty(),
                    color = if (statusOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                statusDetails?.takeIf(String::isNotBlank)?.let { details ->
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelPickerSheet(
    models: List<DiscoveredModel>,
    selected: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(models, search) {
        val query = search.trim()
        if (query.isEmpty()) models else models.filter { it.id.contains(query, true) || it.displayName.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Available models", style = MaterialTheme.typography.headlineSmall)
                    Text("${filtered.size} of ${models.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Rounded.Refresh, "Refresh models")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search model name or ID") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching models", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(filtered, key = { _, option -> option.id }) { index, option ->
                        ChoiceRow(
                            index = index,
                            count = filtered.size,
                            selected = selected == option.id,
                            headline = option.displayName,
                            supporting = option.id.takeIf { it != option.displayName },
                            badge = if (option.isFree) "Free" else null,
                            onClick = { onSelect(option.id) },
                            leading = { IconTile(Icons.Rounded.SmartToy) },
                            control = { RadioButton(selected = selected == option.id, onClick = { onSelect(option.id) }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DshApiProtocolPicker(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("anthropic-messages", "openai-completions", "openai-responses")
    Column {
        Text("Gateway protocol", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelected(option) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == option, onClick = { onSelected(option) })
                Text(option, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            "Pick the protocol your gateway speaks; DeepSeek Harness routes it directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClaudeSubscriptionCredentialsStep(
    token: String,
    hasStoredToken: Boolean,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onChangeAgent: () -> Unit,
    onBack: (() -> Unit)?,
    onToggleTheme: (() -> Unit)?,
    claudeAuth: ClaudeAuthState,
    claudeSignIn: ClaudeSignInActions,
) {
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var pasteTokenExpanded by rememberSaveable { mutableStateOf(false) }
    val hasToken = token.isNotBlank() || hasStoredToken
    SetupScaffold(
        title = "Claude subscription",
        subtitle = "Connect a Claude Pro, Max, Team, or Enterprise subscription to Claude Code.",
        eyebrow = "Step 3 of 3 · Encrypted locally",
        heroIcon = Icons.Rounded.Key,
        heroPolygon = MaterialShapes.Gem,
        step = 2,
        onBack = onBack,
        onToggleTheme = onToggleTheme,
        actions = {
            when {
                pasteTokenExpanded -> SetupPrimaryButton(text = "Save and continue", enabled = hasToken, onClick = onSave)
                hasStoredToken -> SetupPrimaryButton(text = "Continue with saved token", onClick = onSave)
            }
            TextButton(onClick = onChangeAgent, modifier = Modifier.fillMaxWidth()) { Text("Use another coding agent") }
        },
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            ClaudeSignInPanel(auth = claudeAuth, hasStoredToken = hasStoredToken, actions = claudeSignIn, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { pasteTokenExpanded = !pasteTokenExpanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (pasteTokenExpanded) "Hide manual token entry" else "Have a token from another computer? Paste it")
        }
        AnimatedVisibility(pasteTokenExpanded) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("On a computer where Claude Code is installed, run:", style = MaterialTheme.typography.bodyMedium)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "claude setup-token",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text("Then paste the generated token here.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = token,
                        onValueChange = onToken,
                        label = { Text("Claude setup token") },
                        placeholder = { Text(if (hasStoredToken) "Saved securely. Leave blank to keep it" else "Paste token") },
                        supportingText = if (hasStoredToken && token.isBlank()) ({ Text("A saved subscription token is ready to use") }) else null,
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle token visibility")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
