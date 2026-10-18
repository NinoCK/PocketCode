@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.ninock.pocketcode.ui

import io.github.ninock.pocketcode.ui.theme.LocalPocketExtraColors
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import io.github.ninock.pocketcode.model.ClaudeEffortLevels
import io.github.ninock.pocketcode.model.ClaudeSubscriptionModels
import io.github.ninock.pocketcode.runtime.ClaudeAuthState
import io.github.ninock.pocketcode.runtime.ClaudeAuthStatus
import io.github.ninock.pocketcode.ui.theme.PocketOrange

/** Callbacks for the on-device Claude subscription sign-in. */
data class ClaudeSignInActions(
    val onStart: () -> Unit = {},
    val onReopenBrowser: () -> Unit = {},
    val onOpenManual: () -> Unit = {},
    val onSubmitCode: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
)


/**
 * "Sign in with Claude": runs the official `claude setup-token` flow on the
 * phone, opens the Claude sign-in page in the browser, and stores the token it
 * creates. A one-time code path is offered when the browser can't return.
 */
@Composable
internal fun ClaudeSignInPanel(
    auth: ClaudeAuthState,
    hasStoredToken: Boolean,
    actions: ClaudeSignInActions,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var showCodeEntry by rememberSaveable { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (auth.status) {
            ClaudeAuthStatus.IDLE, ClaudeAuthStatus.ERROR, ClaudeAuthStatus.SUCCESS -> {
                if (auth.status == ClaudeAuthStatus.SUCCESS || (hasStoredToken && auth.status == ClaudeAuthStatus.IDLE)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = LocalPocketExtraColors.current.success)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (auth.status == ClaudeAuthStatus.SUCCESS) "Claude subscription connected" else "A subscription token is saved",
                            fontSize = 13.sp,
                            color = LocalPocketExtraColors.current.success,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (auth.status == ClaudeAuthStatus.ERROR) {
                    auth.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = {
                        code = ""
                        showCodeEntry = false
                        actions.onStart()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            auth.status == ClaudeAuthStatus.ERROR -> "Try signing in again"
                            hasStoredToken || auth.status == ClaudeAuthStatus.SUCCESS -> "Sign in again with Claude"
                            else -> "Sign in with Claude"
                        },
                    )
                }
                Text(
                    "Opens claude.ai in your browser. Claude Code on this phone creates a long-lived subscription token, encrypted with Android Keystore.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ClaudeAuthStatus.STARTING, ClaudeAuthStatus.COMPLETING -> {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    auth.message ?: if (auth.status == ClaudeAuthStatus.STARTING) "Starting Claude sign-in…" else "Finishing sign-in…",
                    fontSize = 13.sp,
                )
                TextButton(onClick = actions.onCancel) { Text("Cancel") }
            }
            ClaudeAuthStatus.AWAITING_BROWSER -> {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    auth.message ?: "Sign in to Claude in your browser.",
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = actions.onReopenBrowser,
                    enabled = auth.authorizationUrl != null || auth.manualUrl != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open the sign-in page again")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (!showCodeEntry) {
                    TextButton(
                        onClick = {
                            showCodeEntry = true
                            actions.onOpenManual()
                        },
                        enabled = auth.manualUrl != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Browser didn't come back? Sign in with a code instead", fontSize = 12.sp)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "After approving in the browser, Claude shows an authentication code. Copy it and paste it here.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it },
                                label = { Text("Authentication code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = actions.onOpenManual, modifier = Modifier.weight(1f)) {
                                    Text("Open page", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        actions.onSubmitCode(code)
                                        code = ""
                                    },
                                    enabled = code.isNotBlank() && auth.acceptsCode,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Complete", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                TextButton(onClick = actions.onCancel) { Text("Cancel") }
            }
        }
    }
}

/** Model and effort for Claude subscription runs (Claude Code `--model` / `--effort`). */
@Composable
internal fun ClaudeSubscriptionOptions(
    model: String,
    effort: String,
    onModel: (String) -> Unit,
    onEffort: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        StepSlider(
            title = "Model",
            caption = when (model) {
                "default" -> "Your plan's default"
                "best" -> "Most capable available"
                "opusplan" -> "Opus plans · Sonnet builds"
                else -> "claude --model $model"
            },
            stops = ModelSliderOrder.mapNotNull { value -> ClaudeSubscriptionModels.firstOrNull { it.first == value } },
            selected = model,
            recommended = "opus",
            onSelect = onModel,
        )
        StepSlider(
            title = "Effort",
            caption = when (effort) {
                "low" -> "Fast · light reasoning"
                "medium" -> "Balanced"
                "high" -> "Deeper reasoning"
                "xhigh" -> "Very deep reasoning"
                "max" -> "Deepest · uses more of your limit"
                "ultracode" -> "Multi-agent · uses limits fastest"
                else -> "Claude Code decides"
            },
            stops = ClaudeEffortLevels.filter { it.first != "default" },
            selected = effort,
            recommended = "high",
            onSelect = onEffort,
        )
    }
}

/** `--model` values from fastest to most capable; "default" is reached with Reset instead. */
private val ModelSliderOrder = listOf("haiku", "sonnet", "opusplan", "opus", "fable", "best")

/**
 * Faster ↔ Smarter stepped slider, after the Claude desktop effort control: a
 * pill track with a dot per stop, a tick under the recommended stop and a white
 * thumb that snaps to the nearest stop. Tap a stop or drag the thumb. A value
 * outside [stops] (i.e. "default") rests the thumb hollow on the recommended
 * stop; "Reset" returns to it.
 */
@Composable
private fun StepSlider(
    title: String,
    caption: String,
    stops: List<Pair<String, String>>,
    selected: String,
    recommended: String,
    onSelect: (String) -> Unit,
) {
    val count = stops.size
    val recommendedIndex = stops.indexOfFirst { it.first == recommended }.coerceAtLeast(0)
    val explicitIndex = stops.indexOfFirst { it.first == selected }
    val isDefault = explicitIndex < 0
    val selectedIndex = if (isDefault) recommendedIndex else explicitIndex
    val valueLabel = if (isDefault) "Default" else stops[selectedIndex].second

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markColor = muted.copy(alpha = 0.45f)
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Gesture handlers outlive recompositions, so they read these through state.
    val currentIndex by rememberUpdatedState(selectedIndex)
    val currentIsDefault by rememberUpdatedState(isDefault)
    val currentStops by rememberUpdatedState(stops)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, color = muted)
            Spacer(Modifier.width(8.dp))
            Text(valueLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!isDefault) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "Reset",
                    fontSize = 11.sp,
                    color = muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Use the default $title") { onSelect("default") }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                caption,
                fontSize = 11.sp,
                color = PocketOrange,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Faster", fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
            Text("Smarter", fontSize = 12.sp, color = muted)
        }
        Spacer(Modifier.height(6.dp))

        if (count < 2) return@Column
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(SliderThumbSize + 4.dp),
        ) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val thumbPx = with(density) { SliderThumbSize.toPx() }
            // Stop centres run from one thumb-radius in from each end of the track.
            val inset = with(density) { (SliderTrackHeight / 2).toPx() }
            val span = (widthPx - 2 * inset).coerceAtLeast(1f)
            fun xFor(index: Int) = inset + span * index / (count - 1)
            fun indexAt(x: Float) = ((x - inset) / span * (count - 1)).roundToInt().coerceIn(0, count - 1)

            val scope = rememberCoroutineScope()
            val thumbX = remember { Animatable(xFor(selectedIndex)) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(selectedIndex, widthPx) {
                if (!dragging) thumbX.animateTo(xFor(selectedIndex), spring(stiffness = Spring.StiffnessMediumLow))
            }

            fun pick(index: Int) {
                if (index != currentIndex || currentIsDefault) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnSelect(currentStops[index].first)
                }
            }

            Canvas(
                Modifier
                    .matchParentSize()
                    .semantics {
                        contentDescription = "$title, from faster to smarter"
                        stateDescription = valueLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            selectedIndex.toFloat(),
                            0f..(count - 1).toFloat(),
                            steps = (count - 2).coerceAtLeast(0),
                        )
                        setProgress { target ->
                            pick(target.roundToInt().coerceIn(0, count - 1))
                            true
                        }
                    }
                    .pointerInput(count, widthPx) {
                        detectTapGestures { offset -> pick(indexAt(offset.x)) }
                    }
                    .pointerInput(count, widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                scope.launch { thumbX.snapTo(offset.x.coerceIn(inset, inset + span)) }
                            },
                            onDragEnd = {
                                dragging = false
                                scope.launch { thumbX.animateTo(xFor(currentIndex), spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { thumbX.animateTo(xFor(currentIndex)) }
                            },
                        ) { change, delta ->
                            change.consume()
                            val x = (thumbX.value + delta).coerceIn(inset, inset + span)
                            scope.launch { thumbX.snapTo(x) }
                            pick(indexAt(x))
                        }
                    },
            ) {
                val trackH = SliderTrackHeight.toPx()
                val cy = size.height / 2
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, cy - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2),
                )
                for (i in 0 until count) {
                    val x = xFor(i)
                    if (i == recommendedIndex) {
                        val half = 4.5.dp.toPx()
                        drawLine(markColor, Offset(x, cy - half), Offset(x, cy + half), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        drawCircle(markColor, radius = 2.dp.toPx(), center = Offset(x, cy))
                    }
                }
            }
            Box(
                Modifier
                    .offset { IntOffset((thumbX.value - thumbPx / 2).roundToInt(), 0) }
                    .align(Alignment.CenterStart)
                    .size(SliderThumbSize)
                    .shadow(if (isDefault) 0.dp else 3.dp, CircleShape)
                    .background(if (isDefault) trackColor else Color.White, CircleShape)
                    .border(
                        if (isDefault) 2.dp else 0.5.dp,
                        if (isDefault) muted.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.08f),
                        CircleShape,
                    ),
            )
        }

        // "Recommended" sits centred under its tick, clamped to the track edges.
        Spacer(Modifier.height(4.dp))
        Text(
            "Recommended",
            fontSize = 11.sp,
            color = muted,
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0))
                    val width = constraints.maxWidth
                    val inset = (SliderTrackHeight / 2).toPx()
                    val centre = inset + (width - 2 * inset) * recommendedIndex / (count - 1)
                    layout(width, placeable.height) {
                        placeable.place((centre - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0)), 0)
                    }
                },
        )
    }
}

private val SliderTrackHeight = 24.dp
private val SliderThumbSize = 28.dp

/** Current Claude subscription model/effort plus setters, for the chat screen. */
internal data class ClaudeChatOptions(
    val model: String,
    val effort: String,
    val onModel: (String) -> Unit,
    val onEffort: (String) -> Unit,
)

/**
 * Compact "Opus · High" chip above the message box. Each message starts a new
 * Claude Code process, so a change applies from the next message; it is locked
 * while a reply is running because that process already has its settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClaudeChatOptionsChip(
    options: ClaudeChatOptions,
    enabled: Boolean,
    sheetOpen: Boolean,
    onSheetOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSheet = sheetOpen
    val modelLabel = ClaudeSubscriptionModels.firstOrNull { it.first == options.model }?.second ?: "Default"
    val effortLabel = ClaudeEffortLevels.firstOrNull { it.first == options.effort }?.second ?: "Default"
    val label = when {
        options.model == "default" && options.effort == "default" -> "Default model"
        options.effort == "default" -> modelLabel
        options.model == "default" -> "Default · $effortLabel"
        else -> "$modelLabel · $effortLabel"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClickLabel = "Change model and effort") { onSheetOpenChange(true) }
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(13.dp), tint = PocketOrange)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { onSheetOpenChange(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text("Claude Code", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Applies from your next message in this chat.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                ClaudeSubscriptionOptions(
                    model = options.model,
                    effort = options.effort,
                    onModel = options.onModel,
                    onEffort = options.onEffort,
                )
            }
        }
    }
}
