package io.github.ninock.pocketcode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ninock.pocketcode.runtime.ClaudeCapabilities
import io.github.ninock.pocketcode.runtime.ClaudeSessionControls

internal enum class PaletteSection(val title: String) {
    MODES("Mode"),
    SESSION("Session"),
    COMMANDS("Skills and commands"),
    AGENTS("Subagents"),
    MCP("MCP servers"),
    TOOLS("Tools"),
}

internal sealed interface PaletteAction {
    /** Runs right away and clears the message box. */
    data class Run(val run: () -> Unit) : PaletteAction

    /** Puts text in the message box so the user can finish and send it. */
    data class Insert(val text: String) : PaletteAction

    /** Flips a switch and keeps the palette open for more changes. */
    data class Toggle(val checked: Boolean, val enabled: Boolean = true, val onChange: (Boolean) -> Unit) : PaletteAction
}

internal data class PaletteItem(
    val key: String,
    val section: PaletteSection,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val action: PaletteAction,
    /** A switch next to an insertable item, e.g. turning a skill off. */
    val toggle: PaletteAction.Toggle? = null,
) {
    /** `/plan` → `plan`; items without a slash (tools, servers) have no command name. */
    val commandName: String? get() = label.takeIf { it.startsWith("/") }?.drop(1)
}

/** Everything the chat needs to show the `/` command palette. */
internal data class ChatCommandState(
    val items: List<PaletteItem>,
    val footer: String?,
    val planMode: Boolean,
    val onExitPlanMode: () -> Unit,
    val modelSheetOpen: Boolean,
    val onModelSheetOpenChange: (Boolean) -> Unit,
)

internal fun filterPalette(items: List<PaletteItem>, query: String): List<PaletteItem> {
    val needle = query.trim()
    if (needle.isEmpty()) return items
    // Sections stay together; inside each, name prefix matches come first.
    return items
        .mapNotNull { item ->
            val name = item.label.removePrefix("/")
            val rank = when {
                name.startsWith(needle, ignoreCase = true) -> 0
                name.contains(needle, ignoreCase = true) -> 1
                item.description.contains(needle, ignoreCase = true) -> 2
                else -> return@mapNotNull null
            }
            item to rank
        }
        .sortedWith(compareBy({ it.first.section.ordinal }, { it.second }))
        .map { it.first }
}

/** The palette item a bare `/name` message should trigger instead of being sent. */
internal fun paletteCommandFor(items: List<PaletteItem>, prompt: String): PaletteItem? {
    val trimmed = prompt.trim()
    if (!trimmed.startsWith("/") || trimmed.any(Char::isWhitespace)) return null
    return items.firstOrNull {
        it.commandName.equals(trimmed.drop(1), ignoreCase = true) && it.action !is PaletteAction.Insert
    }
}

internal fun buildCommandPaletteItems(
    claudeAgent: Boolean,
    controls: ClaudeSessionControls,
    capabilities: ClaudeCapabilities,
    isRunning: Boolean,
    hasChanges: Boolean,
    canChangeModel: Boolean,
    onUpdateControls: ((ClaudeSessionControls) -> ClaudeSessionControls) -> Unit,
    onOpenModelSheet: () -> Unit,
    onNewChat: () -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTerminal: () -> Unit,
    onRefreshCapabilities: () -> Unit,
): List<PaletteItem> = buildList {
    if (claudeAgent) {
        add(
            PaletteItem(
                "plan", PaletteSection.MODES, "/plan", "Plan mode: Claude only reads the project and replies with a plan",
                Icons.Rounded.Checklist,
                PaletteAction.Toggle(controls.planMode) { on -> onUpdateControls { it.copy(planMode = on) } },
            ),
        )
        add(
            PaletteItem(
                "work", PaletteSection.MODES, "/work", "Work mode: Claude can edit files and run commands",
                Icons.Rounded.Construction,
                PaletteAction.Run { onUpdateControls { it.copy(planMode = false) } },
            ),
        )
        if (canChangeModel && !isRunning) {
            add(
                PaletteItem(
                    "model", PaletteSection.MODES, "/model", "Change the model and effort",
                    Icons.Rounded.Tune, PaletteAction.Run(onOpenModelSheet),
                ),
            )
        }
    }
    if (!isRunning) {
        add(PaletteItem("new", PaletteSection.SESSION, "/new", "Start a new chat in this project", Icons.Rounded.AddComment, PaletteAction.Run(onNewChat)))
    }
    if (isRunning) {
        add(PaletteItem("stop", PaletteSection.SESSION, "/stop", "Stop the running task", Icons.Rounded.Stop, PaletteAction.Run(onStop)))
    }
    if (hasChanges && !isRunning) {
        add(PaletteItem("undo", PaletteSection.SESSION, "/undo", "Undo the last task's file changes", Icons.AutoMirrored.Rounded.Undo, PaletteAction.Run(onUndo)))
    }
    add(PaletteItem("files", PaletteSection.SESSION, "/files", "Open the Files tab", Icons.Rounded.Folder, PaletteAction.Run(onOpenFiles)))
    add(PaletteItem("terminal", PaletteSection.SESSION, "/terminal", "Open the Terminal tab", Icons.Rounded.Terminal, PaletteAction.Run(onOpenTerminal)))
    if (!claudeAgent) return@buildList
    add(
        PaletteItem(
            "refresh", PaletteSection.SESSION, "/refresh", "Reload skills, subagents and MCP servers",
            Icons.Rounded.Refresh, PaletteAction.Run(onRefreshCapabilities),
        ),
    )

    val taken = map { it.commandName }.toSet()
    capabilities.commands.filter { it.name !in taken }.forEach { command ->
        add(
            PaletteItem(
                key = "command:${command.name}",
                section = PaletteSection.COMMANDS,
                label = "/${command.name}",
                description = command.description.ifBlank { if (command.isSkill) "Skill" else "Claude Code command" },
                icon = if (command.isSkill) Icons.Rounded.Bolt else Icons.Rounded.Code,
                action = PaletteAction.Insert("/${command.name} "),
                toggle = if (command.isSkill) {
                    PaletteAction.Toggle(command.name !in controls.disabledSkills) { on ->
                        onUpdateControls { it.copy(disabledSkills = it.disabledSkills.toggled(command.name, on)) }
                    }
                } else null,
            ),
        )
    }

    add(
        PaletteItem(
            "subagents", PaletteSection.AGENTS, "/subagents", "Let Claude hand work to subagents",
            Icons.Rounded.Groups,
            PaletteAction.Toggle(controls.subagentsEnabled) { on -> onUpdateControls { it.copy(subagentsEnabled = on) } },
        ),
    )
    capabilities.agents.forEach { agent ->
        add(
            PaletteItem(
                key = "agent:${agent.name}",
                section = PaletteSection.AGENTS,
                label = agent.name,
                description = agent.description.ifBlank { "Subagent" },
                icon = Icons.Rounded.SmartToy,
                action = PaletteAction.Insert("Use the ${agent.name} subagent to "),
                toggle = PaletteAction.Toggle(agent.name !in controls.disabledAgents, enabled = controls.subagentsEnabled) { on ->
                    onUpdateControls { it.copy(disabledAgents = it.disabledAgents.toggled(agent.name, on)) }
                },
            ),
        )
    }

    capabilities.mcpServers.forEach { server ->
        add(
            PaletteItem(
                key = "mcp:${server.name}",
                section = PaletteSection.MCP,
                label = server.name,
                description = server.status.ifBlank { "MCP server" }.replaceFirstChar(Char::uppercase),
                icon = Icons.Rounded.Hub,
                action = PaletteAction.Toggle(server.name !in controls.disabledMcpServers) { on ->
                    onUpdateControls { it.copy(disabledMcpServers = it.disabledMcpServers.toggled(server.name, on)) }
                },
            ),
        )
    }

    capabilities.tools.forEach { tool ->
        add(
            PaletteItem(
                key = "tool:$tool",
                section = PaletteSection.TOOLS,
                label = tool,
                description = if (controls.planMode && tool in PlanModeTools) "Off in plan mode" else "Built-in tool",
                icon = Icons.Rounded.Build,
                action = PaletteAction.Toggle(tool !in controls.disabledTools) { on ->
                    onUpdateControls { it.copy(disabledTools = it.disabledTools.toggled(tool, on)) }
                },
            ),
        )
    }
}

private val PlanModeTools = setOf("Edit", "Write", "NotebookEdit", "Bash", "ExitPlanMode")

/** Adds [name] to a set of disabled names when it is switched off, removes it when switched on. */
private fun Set<String>.toggled(name: String, enabled: Boolean): Set<String> = if (enabled) this - name else this + name

@Composable
internal fun CommandPalette(
    items: List<PaletteItem>,
    query: String,
    footer: String?,
    onDismiss: () -> Unit,
    onSelect: (PaletteItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(items, query) { filterPalette(items, query) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Commands",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close commands") }
            }
            if (filtered.isEmpty()) {
                Text(
                    "No command matches /$query",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    var previous: PaletteSection? = null
                    filtered.forEach { item ->
                        if (item.section != previous) {
                            previous = item.section
                            item(key = "section:${item.section}") { PaletteSectionHeader(item.section.title) }
                        }
                        item(key = item.key) { PaletteRow(item, onSelect) }
                    }
                }
            }
            footer?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PaletteSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun PaletteRow(item: PaletteItem, onSelect: (PaletteItem) -> Unit) {
    val toggle = item.action as? PaletteAction.Toggle ?: item.toggle
    val rowEnabled = (item.action as? PaletteAction.Toggle)?.enabled ?: true
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = rowEnabled) { onSelect(item) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (rowEnabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (toggle != null) {
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = toggle.checked,
                onCheckedChange = toggle.onChange,
                enabled = toggle.enabled,
            )
        }
    }
}

/** Shown above the message box while plan mode is on; tap to go back to Work mode. */
@Composable
internal fun PlanModeChip(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onExit,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Checklist, null, Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("Plan mode", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Close, "Switch to Work mode", Modifier.size(14.dp))
        }
    }
}
