package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.TokenUsage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageParsingTest {
    @Test
    fun readsClaudeResultWithModelUsage() {
        val result = JSONObject(
            """
            {"type":"result","subtype":"success","is_error":false,"total_cost_usd":0.0421,
             "usage":{"input_tokens":12,"output_tokens":300,"cache_read_input_tokens":4000,"cache_creation_input_tokens":900},
             "modelUsage":{
               "claude-sonnet-4-6":{"inputTokens":12,"outputTokens":300,"cacheReadInputTokens":4000,"cacheCreationInputTokens":900,"costUSD":0.04},
               "claude-haiku-4-5":{"inputTokens":500,"outputTokens":20,"cacheReadInputTokens":0,"cacheCreationInputTokens":0,"costUSD":0.0021}
             }}
            """.trimIndent(),
        )
        val usage = UsageParsing.fromClaudeResult(result)!!
        assertEquals(512L, usage.inputTokens)
        assertEquals(320L, usage.outputTokens)
        assertEquals(4000L, usage.cacheReadTokens)
        assertEquals(900L, usage.cacheWriteTokens)
        assertEquals(0.0421, usage.costUsd!!, 1e-9)
        assertEquals("claude-sonnet-4-6", usage.model)
    }

    @Test
    fun fallsBackToUsageBlockWithoutModelUsage() {
        val result = JSONObject("""{"type":"result","usage":{"input_tokens":5,"output_tokens":7}}""")
        val usage = UsageParsing.fromClaudeResult(result)!!
        assertEquals(TokenUsage(inputTokens = 5, outputTokens = 7), usage)
    }

    @Test
    fun emptyResultHasNoUsage() {
        assertNull(UsageParsing.fromClaudeResult(JSONObject("""{"type":"result","result":"hi"}""")))
    }

    @Test
    fun acceptsCamelCaseAndOpenAiStyleKeys() {
        val usage = UsageParsing.fromUsageBlock(JSONObject("""{"prompt_tokens":10,"completion_tokens":4}"""), "gpt")
        assertEquals(10L, usage.inputTokens)
        assertEquals(4L, usage.outputTokens)
        assertEquals("gpt", usage.model)
    }

    @Test
    fun sumsPerMessageUsageAndKeepsCostUnknown() {
        val total = UsageParsing.sum(listOf(TokenUsage(inputTokens = 1, outputTokens = 2), TokenUsage(inputTokens = 3, cacheReadTokens = 4)))!!
        assertEquals(4L, total.inputTokens)
        assertEquals(10L, total.totalTokens)
        assertNull(total.costUsd)
        assertNull(UsageParsing.sum(emptyList()))
    }

    @Test
    fun oneShotParsesJsonEnvelopeAfterNoise() {
        val output = "proot warning: something\n" +
            """{"type":"result","is_error":false,"result":"Add usage tracking","total_cost_usd":0.001,"usage":{"input_tokens":100,"output_tokens":8}}"""
        val result = ClaudeOneShot.parse(output)
        assertEquals("Add usage tracking", result.text)
        assertEquals(108L, result.usage!!.totalTokens)
    }

    @Test
    fun oneShotReportsErrors() {
        val error = runCatching { ClaudeOneShot.parse("""{"type":"result","is_error":true,"result":"Invalid API key"}""") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Invalid API key"))
        val nothing = runCatching { ClaudeOneShot.parse("command not found") }.exceptionOrNull()
        assertEquals("command not found", nothing?.message)
    }

    @Test
    fun cleanDraftStripsFencesAndQuotes() {
        assertEquals("Fix login crash", cleanDraft("```\nFix login crash\n```"))
        assertEquals("Fix login crash", cleanDraft("\"Fix login crash\""))
        assertEquals("Say \"hi\" and \"bye\"", cleanDraft("Say \"hi\" and \"bye\""))
    }
}
