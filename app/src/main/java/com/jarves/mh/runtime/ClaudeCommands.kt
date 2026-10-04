package com.jarves.mh.runtime

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A skill, custom command or built-in prompt command Claude Code can run as `/name`. */
data class ClaudeCommandInfo(
    val name: String,
    val description: String = "",
    /** Skills can also be invoked by Claude itself, so they can be switched off. */
    val isSkill: Boolean = false,
)

data class ClaudeAgentInfo(val name: String, val description: String = "")

data class McpServerInfo(val name: String, val status: String = "")

/**
 * What Claude Code has loaded for a project. Names come from the stream-json
 * `system/init` event of the last run; descriptions and the list before the
 * first run come from the skill, command and agent files on disk.
 */
data class ClaudeCapabilities(
    val commands: List<ClaudeCommandInfo> = emptyList(),
    val agents: List<ClaudeAgentInfo> = emptyList(),
    val mcpServers: List<McpServerInfo> = emptyList(),
    val tools: List<String> = emptyList(),
    /** True once a real run has reported its init event for this project. */
    val reportedByClaude: Boolean = false,
) {
    fun findCommand(name: String): ClaudeCommandInfo? = commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** Command palette switches. They apply from the next message, like model and effort. */
data class ClaudeSessionControls(
    val planMode: Boolean = false,
    val subagentsEnabled: Boolean = true,
    val disabledTools: Set<String> = emptySet(),
    val disabledMcpServers: Set<String> = emptySet(),
    val disabledSkills: Set<String> = emptySet(),
    val disabledAgents: Set<String> = emptySet(),
) {
    fun toJson(): String = JSONObject()
        .put("planMode", planMode)
        .put("subagentsEnabled", subagentsEnabled)
        .put("disabledTools", JSONArray(disabledTools.sorted()))
        .put("disabledMcpServers", JSONArray(disabledMcpServers.sorted()))
        .put("disabledSkills", JSONArray(disabledSkills.sorted()))
        .put("disabledAgents", JSONArray(disabledAgents.sorted()))
        .toString()

    companion object {
        fun fromJson(raw: String?): ClaudeSessionControls {
            val json = raw?.takeIf(String::isNotBlank)?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return ClaudeSessionControls()
            return ClaudeSessionControls(
                planMode = json.optBoolean("planMode", false),
                subagentsEnabled = json.optBoolean("subagentsEnabled", true),
                disabledTools = json.optJSONArray("disabledTools").names().toSet(),
                disabledMcpServers = json.optJSONArray("disabledMcpServers").names().toSet(),
                disabledSkills = json.optJSONArray("disabledSkills").names().toSet(),
                disabledAgents = json.optJSONArray("disabledAgents").names().toSet(),
            )
        }
    }
}

/** Built-in commands that only make sense in an interactive Claude Code session. */
private val InteractiveOnlyCommands = setOf(
    "clear", "compact", "config", "context", "cost", "doctor", "exit", "heapdump", "help",
    "login", "logout", "model", "permissions", "resume", "status", "agents", "mcp",
)

/** Tools that change files or run commands, blocked while plan mode is on. */
private val PlanModeBlockedTools = listOf("Edit", "Write", "NotebookEdit", "Bash", "ExitPlanMode")

internal const val PLAN_MODE_SYSTEM_PROMPT =
    "PocketCode plan mode is on: research the project read-only and do not change files or run commands. " +
        "ExitPlanMode is unavailable here. When the plan is ready, write it as your final reply; " +
        "the user switches to Work mode to carry it out."

/** Claude Code flags for the palette switches, appended to the print-mode command. */
internal fun claudeControlArguments(controls: ClaudeSessionControls): List<String> = buildList {
    val denied = linkedSetOf<String>()
    if (controls.planMode) {
        add("--permission-mode")
        add("plan")
        denied += PlanModeBlockedTools
    }
    denied += controls.disabledTools
    controls.disabledMcpServers.forEach { denied += "mcp__${mcpToolPrefix(it)}" }
    controls.disabledSkills.forEach { denied += "Skill($it)" }
    if (!controls.subagentsEnabled) {
        // The subagent tool was renamed from Task to Agent; deny both spellings.
        denied += "Agent"
        denied += "Task"
    } else {
        controls.disabledAgents.forEach {
            denied += "Agent($it)"
            denied += "Task($it)"
        }
    }
    if (denied.isNotEmpty()) {
        add("--disallowedTools")
        add(denied.joinToString(","))
    }
}

/** MCP tool names use the server name with anything outside `[A-Za-z0-9_-]` replaced by `_`. */
internal fun mcpToolPrefix(serverName: String): String = serverName.replace(Regex("[^A-Za-z0-9_-]"), "_")

/** `/name args` → `name` when the message starts with a slash command, else null. */
internal fun slashCommandName(prompt: String): String? {
    val trimmed = prompt.trimStart()
    if (!trimmed.startsWith("/")) return null
    val name = trimmed.drop(1).takeWhile { !it.isWhitespace() }
    return name.takeIf { it.isNotBlank() && Regex("[A-Za-z0-9][A-Za-z0-9_:.-]*").matches(it) }
}

/** Parses Claude Code's stream-json `system/init` event. */
internal fun parseClaudeInit(json: JSONObject, onDisk: ClaudeCapabilities = ClaudeCapabilities()): ClaudeCapabilities {
    val skills = json.optJSONArray("skills").names().toSet()
    val commandNames = (json.optJSONArray("slash_commands").names() + skills)
        .map { it.removePrefix("/") }
        .filter { it.isNotBlank() && it !in InteractiveOnlyCommands }
        .distinct()
    val commands = commandNames.map { name ->
        val known = onDisk.findCommand(name)
        ClaudeCommandInfo(
            name = name,
            description = known?.description.orEmpty(),
            isSkill = name in skills || known?.isSkill == true,
        )
    }
    val agents = json.optJSONArray("agents").names().map { name ->
        ClaudeAgentInfo(name, onDisk.agents.firstOrNull { it.name == name }?.description.orEmpty())
    }
    val servers = buildList {
        val array = json.optJSONArray("mcp_servers") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            when (item) {
                is JSONObject -> item.optString("name").takeIf(String::isNotBlank)
                    ?.let { add(McpServerInfo(it, item.optString("status"))) }
                is String -> if (item.isNotBlank()) add(McpServerInfo(item))
            }
        }
    }
    val tools = json.optJSONArray("tools").names().filterNot { it.startsWith("mcp__") }
    return ClaudeCapabilities(
        commands = commands,
        agents = agents,
        mcpServers = servers,
        tools = tools,
        reportedByClaude = true,
    )
}

internal fun ClaudeCapabilities.toJson(): String = JSONObject()
    .put("reportedByClaude", reportedByClaude)
    .put("commands", JSONArray().apply {
        commands.forEach { put(JSONObject().put("name", it.name).put("description", it.description).put("skill", it.isSkill)) }
    })
    .put("agents", JSONArray().apply {
        agents.forEach { put(JSONObject().put("name", it.name).put("description", it.description)) }
    })
    .put("mcpServers", JSONArray().apply {
        mcpServers.forEach { put(JSONObject().put("name", it.name).put("status", it.status)) }
    })
    .put("tools", JSONArray(tools))
    .toString()

internal fun claudeCapabilitiesFromJson(raw: String): ClaudeCapabilities? = runCatching {
    val json = JSONObject(raw)
    ClaudeCapabilities(
        commands = json.optJSONArray("commands").objects().map {
            ClaudeCommandInfo(it.optString("name"), it.optString("description"), it.optBoolean("skill"))
        }.filter { it.name.isNotBlank() },
        agents = json.optJSONArray("agents").objects().map {
            ClaudeAgentInfo(it.optString("name"), it.optString("description"))
        }.filter { it.name.isNotBlank() },
        mcpServers = json.optJSONArray("mcpServers").objects().map {
            McpServerInfo(it.optString("name"), it.optString("status"))
        }.filter { it.name.isNotBlank() },
        tools = json.optJSONArray("tools").names(),
        reportedByClaude = json.optBoolean("reportedByClaude"),
    )
}.getOrNull()

/**
 * Skills, commands and agents defined on disk in `.claude` folders, so the
 * palette has something to show before Claude Code has run in a project.
 */
internal fun scanClaudeConfigDirs(claudeDirs: List<File>): ClaudeCapabilities {
    val commands = linkedMapOf<String, ClaudeCommandInfo>()
    val agents = linkedMapOf<String, ClaudeAgentInfo>()
    claudeDirs.filter(File::isDirectory).forEach { dir ->
        File(dir, "skills").listFiles().orEmpty().sortedBy { it.name }.forEach { skillDir ->
            val file = File(skillDir, "SKILL.md")
            if (!file.isFile) return@forEach
            val meta = readFrontmatter(file)
            if (meta["user-invocable"] == "false") return@forEach
            val name = meta["name"]?.takeIf(String::isNotBlank) ?: skillDir.name
            commands.putIfAbsent(name, ClaudeCommandInfo(name, meta["description"].orEmpty(), isSkill = true))
        }
        val commandRoot = File(dir, "commands")
        commandRoot.walkTopDown().maxDepth(3).filter { it.isFile && it.extension == "md" }.sortedBy { it.path }.forEach { file ->
            // Subfolders namespace commands as folder:name.
            val name = file.relativeTo(commandRoot).invariantSeparatorsPath.removeSuffix(".md").replace('/', ':')
            commands.putIfAbsent(name, ClaudeCommandInfo(name, readFrontmatter(file)["description"].orEmpty(), isSkill = true))
        }
        File(dir, "agents").listFiles().orEmpty().filter { it.isFile && it.extension == "md" }.sortedBy { it.name }.forEach { file ->
            val meta = readFrontmatter(file)
            val name = meta["name"]?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
            agents.putIfAbsent(name, ClaudeAgentInfo(name, meta["description"].orEmpty()))
        }
    }
    return ClaudeCapabilities(commands = commands.values.toList(), agents = agents.values.toList())
}

/** Keeps Claude Code's reported names and fills in descriptions found on disk. */
internal fun ClaudeCapabilities.withDescriptionsFrom(onDisk: ClaudeCapabilities): ClaudeCapabilities {
    if (!reportedByClaude) return onDisk
    return copy(
        commands = commands.map { command ->
            if (command.description.isNotBlank()) command
            else command.copy(description = onDisk.findCommand(command.name)?.description.orEmpty())
        },
        agents = agents.map { agent ->
            if (agent.description.isNotBlank()) agent
            else agent.copy(description = onDisk.agents.firstOrNull { it.name == agent.name }?.description.orEmpty())
        },
    )
}

/** Reads simple `key: value` lines from a Markdown file's YAML frontmatter. */
internal fun readFrontmatter(file: File): Map<String, String> = runCatching {
    file.bufferedReader().use { reader ->
        if (reader.readLine()?.trim() != "---") return@use emptyMap()
        val values = linkedMapOf<String, String>()
        var lines = 0
        while (lines++ < 60) {
            val line = reader.readLine() ?: break
            if (line.trim() == "---") break
            val colon = line.indexOf(':')
            if (colon <= 0 || line.first().isWhitespace()) continue
            val value = line.substring(colon + 1).trim().removeSurrounding("\"").removeSurrounding("'")
            values[line.substring(0, colon).trim()] = value.take(300)
        }
        values
    }
}.getOrDefault(emptyMap())

/** Names from an array of strings or of objects with a `name` field. */
private fun JSONArray?.names(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        when (val item = opt(index)) {
            is String -> item
            is JSONObject -> item.optString("name")
            else -> null
        }?.takeIf(String::isNotBlank)
    }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
