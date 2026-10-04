package com.jarves.mh.runtime

import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSubscriptionLaunchTest {
    @Test
    fun `subscription runs do not use bare mode`() {
        // Claude Code ignores CLAUDE_CODE_OAUTH_TOKEN under --bare.
        val command = claudePrintCommand("/usr/local/bin/claude", "default", subscription = true)
        assertFalse("--bare" in command)
        assertFalse("--model" in command)
        // The prompt goes in on stdin; permission prompts and questions come back as control requests.
        assertEquals(listOf("-p", "--input-format", "stream-json", "--permission-prompt-tool", "stdio"), command.subList(1, 6))
    }

    @Test
    fun `subscription runs pass an explicit model alias through`() {
        val command = claudePrintCommand("/usr/local/bin/claude", "sonnet", subscription = true)
        assertEquals("sonnet", command[command.indexOf("--model") + 1])
    }

    @Test
    fun `subscription runs pass the chosen effort`() {
        val command = claudePrintCommand("/usr/local/bin/claude", "opus", subscription = true, effort = "ultracode")
        assertEquals("ultracode", command[command.indexOf("--effort") + 1])
        assertEquals("opus", command[command.indexOf("--model") + 1])
    }

    @Test
    fun `default or unknown effort is left to Claude Code`() {
        for (effort in listOf("default", "", "turbo")) {
            assertFalse("--effort" in claudePrintCommand("/usr/local/bin/claude", "default", subscription = true, effort = effort))
        }
        // Effort is a subscription option; gateways may not accept the flag's request field.
        assertFalse("--effort" in claudePrintCommand("/usr/local/bin/claude", "glm-5", subscription = false, effort = "high"))
    }

    @Test
    fun `api key runs keep bare mode and model`() {
        val command = claudePrintCommand("/usr/local/bin/claude", "glm-5", subscription = false)
        assertTrue("--bare" in command)
        assertEquals("glm-5", command[command.indexOf("--model") + 1])
    }

    @Test
    fun `subscription launch passes the token as CLAUDE_CODE_OAUTH_TOKEN`() {
        val config = RuntimeLaunchConfigBuilder.build(ProviderProfile(ProviderKind.CLAUDE), authToken = "sk-ant-oat01-test")
        assertEquals("sk-ant-oat01-test", config.environment["CLAUDE_CODE_OAUTH_TOKEN"])
    }

    @Test
    fun `subscription errors are not reported as a bad api key`() {
        val auth = """{"type":"system","subtype":"api_retry","attempt":1,"error_status":401,"error":"authentication_failed"}"""
        assertTrue(ProviderRuntimeErrorDetector.detect(auth, subscription = true)!!.contains("subscription token"))
        val limit = """{"type":"system","subtype":"api_retry","attempt":1,"error_status":429,"error":"rate_limit"}"""
        assertTrue(ProviderRuntimeErrorDetector.detect(limit, subscription = true)!!.contains("usage limit"))
    }
}
