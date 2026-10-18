package io.github.ninock.pocketcode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ninock.pocketcode.data.UsageAggregation
import io.github.ninock.pocketcode.data.UsageRecord
import io.github.ninock.pocketcode.data.UsageTotals
import io.github.ninock.pocketcode.model.Project
import io.github.ninock.pocketcode.model.TokenUsage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "950", "12.3k", "4.1M". */
internal fun formatTokens(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 999_950 -> "%.1fk".format(Locale.US, count / 1_000.0).replace(".0k", "k")
    else -> "%.1fM".format(Locale.US, count / 1_000_000.0).replace(".0M", "M")
}

internal fun formatCost(usd: Double): String = when {
    usd <= 0.0 -> "$0"
    usd < 0.01 -> "<$0.01"
    usd < 100 -> "$%.2f".format(Locale.US, usd)
    else -> "$%,.0f".format(Locale.US, usd)
}

/** "12.3k tokens · $0.42", for a line of supporting text. */
internal fun formatUsageShort(usage: TokenUsage): String = buildString {
    append(formatTokens(usage.totalTokens)).append(" tokens")
    usage.costUsd?.let { append(" · ").append(formatCost(it)) }
}

private enum class UsageRange(val label: String, val days: Long) { WEEK("7 days", 7), MONTH("30 days", 30), QUARTER("90 days", 90) }

private enum class UsageMetric { COST, TOKENS }

private fun UsageTotals.value(metric: UsageMetric): Double =
    if (metric == UsageMetric.COST) usage.costUsd ?: 0.0 else usage.totalTokens.toDouble()

private fun UsageTotals.label(metric: UsageMetric): String =
    if (metric == UsageMetric.COST) formatCost(usage.costUsd ?: 0.0) else formatTokens(usage.totalTokens)

/** Tokens and cost by day, project and chat. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UsageScreen(records: List<UsageRecord>, projects: List<Project>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var range by rememberSaveable { mutableStateOf(UsageRange.WEEK) }
    val anyCost = remember(records) { records.any { (it.usage.costUsd ?: 0.0) > 0.0 } }
    var metric by rememberSaveable { mutableStateOf(if (anyCost) UsageMetric.COST else UsageMetric.TOKENS) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var expandedProject by rememberSaveable { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val from = today.minusDays(range.days - 1)
    val inRange = remember(records, range, today) { records.filter { !it.day().isBefore(from) } }
    val days = remember(inRange, from, today) { UsageAggregation.byDay(inRange, from, today) }
    val total = remember(inRange) { UsageAggregation.total(inRange) }
    val todayTotal = remember(records, today) { UsageAggregation.total(records.filter { it.day() == today }) }
    val byProject = remember(inRange, metric) {
        UsageAggregation.byProject(inRange).entries.sortedByDescending { it.value.value(metric) }
    }
    val projectNames = remember(projects, records) {
        records.associate { it.projectId to it.projectName } + projects.associate { it.id to it.name }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                ExpressiveEmptyState(
                    icon = Icons.Rounded.Insights,
                    title = "No usage yet",
                    body = "Tokens and cost show up here after you send a message to Claude Code.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "range") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    UsageRange.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = range == option,
                            onClick = {
                                range = option
                                selectedDay = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, UsageRange.entries.size),
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            item(key = "summary") {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Last ${range.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (anyCost) formatCost(total.usage.costUsd ?: 0.0) else formatTokens(total.usage.totalTokens),
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (anyCost) "${formatTokens(total.usage.totalTokens)} tokens" else "tokens",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Text(
                            "${total.runs} ${if (total.runs == 1) "run" else "runs"} · Today ${formatUsageShort(todayTotal.usage)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        TokenBreakdown(total.usage)
                    }
                }
            }
            item(key = "chart") {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("By day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (anyCost) {
                                FilterChip(selected = metric == UsageMetric.COST, onClick = { metric = UsageMetric.COST }, label = { Text("Cost") })
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = metric == UsageMetric.TOKENS, onClick = { metric = UsageMetric.TOKENS }, label = { Text("Tokens") })
                            }
                        }
                        val selected = days.firstOrNull { it.first == selectedDay }
                        Text(
                            selected?.let { (day, totals) -> "${dayLabel(day, today)}: ${if (totals.runs == 0) "nothing" else formatUsageShort(totals.usage)}" }
                                ?: "Tap a bar to see that day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        DailyBars(days, metric, selectedDay, onSelect = { selectedDay = if (selectedDay == it) null else it })
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(shortDate(from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item(key = "projects-header") { SectionHeader("By project", Modifier.padding(top = 12.dp)) }
            if (byProject.isEmpty()) {
                item(key = "projects-empty") {
                    Text(
                        "Nothing in the last ${range.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            itemsIndexed(byProject, key = { _, entry -> "p:${entry.key}" }) { index, (projectId, totals) ->
                val expanded = expandedProject == projectId
                Surface(shape = groupedShape(index, byProject.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.animateContentSize()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { expandedProject = if (expanded) null else projectId }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(projectNames[projectId] ?: "Deleted project", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatTokens(totals.usage.totalTokens)} tokens · ${totals.runs} ${if (totals.runs == 1) "run" else "runs"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            totals.usage.costUsd?.let { Text(formatCost(it), style = MaterialTheme.typography.titleSmall) }
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.padding(start = 4.dp))
                        }
                        if (expanded) ProjectChats(inRange.filter { it.projectId == projectId })
                    }
                }
            }
            item(key = "days-header") { SectionHeader("Daily totals", Modifier.padding(top = 12.dp)) }
            val activeDays = days.filter { it.second.runs > 0 }.reversed()
            itemsIndexed(activeDays, key = { _, entry -> "d:${entry.first}" }) { index, (day, totals) ->
                GroupedRow(
                    index = index,
                    count = activeDays.size,
                    headline = dayLabel(day, today),
                    supporting = "${formatTokens(totals.usage.totalTokens)} tokens · ${totals.runs} ${if (totals.runs == 1) "run" else "runs"}",
                    trailing = { totals.usage.costUsd?.let { Text(formatCost(it), style = MaterialTheme.typography.titleSmall) } },
                )
            }
            item(key = "note") {
                Text(
                    "Costs are Claude Code's own estimates at API prices; a Claude subscription isn't billed per token. " +
                        "Commit message and pull request drafts count toward their project. DeepSeek Harness and Antigravity runs don't report usage yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun TokenBreakdown(usage: TokenUsage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(
            "Input" to usage.inputTokens,
            "Output" to usage.outputTokens,
            "Cache read" to usage.cacheReadTokens,
            "Cache write" to usage.cacheWriteTokens,
        ).forEach { (label, count) ->
            Column {
                Text(formatTokens(count), style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One thin bar per day, anchored to a baseline; tapping a day shows its numbers above the chart. */
@Composable
private fun DailyBars(days: List<Pair<LocalDate, UsageTotals>>, metric: UsageMetric, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val max = days.maxOfOrNull { it.second.value(metric) }?.takeIf { it > 0 } ?: 1.0
    val peak = days.maxByOrNull { it.second.value(metric) }?.second
    Column {
        if (peak != null && peak.runs > 0) {
            Text("Peak ${peak.label(metric)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().height(132.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (days.size > 45) 1.dp else 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { (day, totals) ->
                val fraction = (totals.value(metric) / max).toFloat().coerceIn(0f, 1f)
                val isSelected = day == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelect(day) }
                        .semantics { contentDescription = "${day}: ${totals.label(metric)}" },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (isSelected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    if (totals.runs > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (selected == null || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProjectChats(records: List<UsageRecord>) {
    val chats = records.groupBy { it.chatId }.map { (chatId, list) ->
        Triple(chatId, list.lastOrNull { it.chatTitle != null }?.chatTitle, UsageAggregation.total(list))
    }.sortedByDescending { it.third.usage.totalTokens }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        chats.forEach { (chatId, title, totals) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (chatId == null) "Commit & pull request drafts" else title ?: "Chat",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatTokens(totals.usage.totalTokens)} tokens · ${totals.runs} ${if (totals.runs == 1) "run" else "runs"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                totals.usage.costUsd?.let { Text(formatCost(it), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${shortDate(day)}"
}

private fun shortDate(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
