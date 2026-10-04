package com.jarves.mh.runtime

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeCommandsTest {
    @Test
    fun `default controls add no flags`() {
        assertEquals(emptyList<String>(), claudeControlArguments(ClaudeSessionControls()))
        val command = claudePrintCommand("/usr/local/bin/claude", "default", subscription = true)
        assertFalse("--append-system-prompt" in command)
        assertFalse("--disallowedTools" in command)
    }

    @Test
    fun `plan mode uses plan permission mode and blocks changes`() {
        val command = claudePrintCommand(
            "/usr/local/bin/claude", "default", subscription = true,
            controls = ClaudeSessionControls(planMode = true),
        )
        assertEquals("plan", command[command.indexOf("--permission-mode") + 1])
        val denied = command[command.indexOf("--disallowedTools") + 1].split(",")
        assertTrue(listOf("Edit", "Write", "NotebookEdit", "Bash", "ExitPlanMode").all { it in denied })
        assertTrue(command[command.indexOf("--append-system-prompt") + 1].contains("plan mode"))
    }

    @Test
    fun `disabled capabilities become deny rules`() {
        val args = claudeControlArguments(
            ClaudeSessionControls(
                disabledTools = setOf("WebFetch"),
                disabledMcpServers = setOf("my server"),
                disabledSkills = setOf("deploy"),
                disabledAgents = setOf("Explore"),
            ),
        )
        val denied = args[args.indexOf("--disallowedTools") + 1].split(",")
        assertEquals(listOf("WebFetch", "mcp__my_server", "Skill(deploy)", "Agent(Explore)", "Task(Explore)"), denied)
    }

    @Test
    fun `turning subagents off denies the whole tool`() {
        val args = claudeControlArguments(ClaudeSessionControls(subagentsEnabled = false, disabledAgents = setOf("Explore")))
        val denied = args[args.indexOf("--disallowedTools") + 1].split(",")
        assertEquals(listOf("Agent", "Task"), denied)
    }

    @Test
    fun `controls survive a json round trip`() {
        val controls = ClaudeSessionControls(
            planMode = true,
            subagentsEnabled = false,
            disabledTools = setOf("Bash"),
            disabledMcpServers = setOf("github"),
            disabledSkills = setOf("deploy"),
            disabledAgents = setOf("Plan"),
        )
        assertEquals(controls, ClaudeSessionControls.fromJson(controls.toJson()))
        assertEquals(ClaudeSessionControls(), ClaudeSessionControls.fromJson("not json"))
    }

    @Test
    fun `slash command names are read from the start of the prompt`() {
        assertEquals("review", slashCommandName("/review the auth code"))
        assertEquals("frontend:component", slashCommandName("  /frontend:component Button"))
        assertNull(slashCommandName("/workspace/app/src is broken"))
        assertNull(slashCommandName("please run /review"))
        assertNull(slashCommandName("/"))
    }

    @Test
    fun `init event lists commands agents servers and tools`() {
        val init = JSONObject(
            """
            {"type":"system","subtype":"init",
             "tools":["Bash","Read","Skill","mcp__github__create_issue"],
             "mcp_servers":[{"name":"github","status":"connected"}],
             "slash_commands":["compact","context","review","deploy"],
             "skills":["deploy"],
             "agents":["general-purpose","Explore"]}
            """.trimIndent(),
        )
        val capabilities = parseClaudeInit(init)
        assertTrue(capabilities.reportedByClaude)
        assertEquals(listOf("review", "deploy"), capabilities.commands.map { it.name })
        assertTrue(capabilities.findCommand("deploy")!!.isSkill)
        assertFalse(capabilities.findCommand("review")!!.isSkill)
        assertEquals(listOf("general-purpose", "Explore"), capabilities.agents.map { it.name })
        assertEquals(listOf(McpServerInfo("github", "connected")), capabilities.mcpServers)
        assertEquals(listOf("Bash", "Read", "Skill"), capabilities.tools)
        assertEquals(capabilities, claudeCapabilitiesFromJson(capabilities.toJson()))
    }

    @Test
    fun `skills commands and agents are found on disk`() {
        val claude = Files.createTempDirectory("claude").toFile()
        try {
            claude.resolve("skills/deploy").mkdirs()
            claude.resolve("skills/deploy/SKILL.md").writeText("---\nname: deploy\ndescription: Ship it\n---\nBody")
            claude.resolve("skills/hidden").mkdirs()
            claude.resolve("skills/hidden/SKILL.md").writeText("---\nuser-invocable: false\n---\n")
            claude.resolve("commands/frontend").mkdirs()
            claude.resolve("commands/frontend/component.md").writeText("---\ndescription: \"New component\"\n---\n")
            claude.resolve("agents").mkdirs()
            claude.resolve("agents/reviewer.md").writeText("---\nname: reviewer\ndescription: Reviews code\n---\n")

            val found = scanClaudeConfigDirs(listOf(claude))
            assertEquals(listOf("deploy", "frontend:component"), found.commands.map { it.name })
            assertEquals("Ship it", found.findCommand("deploy")!!.description)
            assertEquals("New component", found.findCommand("frontend:component")!!.description)
            assertEquals(listOf(ClaudeAgentInfo("reviewer", "Reviews code")), found.agents)
            assertFalse(found.reportedByClaude)

            val reported = parseClaudeInit(JSONObject("""{"slash_commands":["deploy"],"skills":["deploy"]}"""))
            assertEquals("Ship it", reported.withDescriptionsFrom(found).findCommand("deploy")!!.description)
        } finally {
            claude.deleteRecursively()
        }
    }
}
