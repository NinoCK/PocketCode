package com.jarves.mh.runtime

import com.jarves.mh.model.AgentQuestionItem
import com.jarves.mh.model.AgentQuestionOption
import com.jarves.mh.model.RiskLevel
import com.jarves.mh.ui.questionAnswer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeControlProtocolTest {
    // Shape recorded from Claude Code 2.1 running with --permission-prompt-tool stdio.
    private val askInput = JSONObject(
        """
        {"questions":[{"question":"Which color do you prefer?","header":"Color",
          "options":[{"label":"Red","description":"Warm"},{"label":"Blue","description":"Cool"}],
          "multiSelect":false}]}
        """.trimIndent(),
    )

    @Test
    fun `user messages are stream-json lines`() {
        val json = JSONObject(claudeUserMessage("/review the \"auth\" code"))
        assertEquals("user", json.getString("type"))
        assertEquals("/review the \"auth\" code", json.getJSONObject("message").getString("content"))
        assertFalse(claudeUserMessage("a\nb").contains('\n'))
    }

    @Test
    fun `ask user question input is parsed`() {
        val question = parseAskUserQuestion("req-1", "s", askInput)!!
        assertEquals("req-1", question.requestId)
        val item = question.questions.single()
        assertEquals("Which color do you prefer?", item.question)
        assertEquals("Color", item.header)
        assertEquals(listOf(AgentQuestionOption("Red", "Warm"), AgentQuestionOption("Blue", "Cool")), item.options)
        assertFalse(item.multiSelect)
        assertNull(parseAskUserQuestion("req-2", "s", JSONObject("""{"questions":[]}""")))
    }

    @Test
    fun `answers go back as allowed input keyed by question`() {
        val response = JSONObject(
            controlAllow("req-1", answeredQuestionInput(askInput, mapOf("Which color do you prefer?" to "Blue"))),
        )
        assertEquals("control_response", response.getString("type"))
        val body = response.getJSONObject("response")
        assertEquals("success", body.getString("subtype"))
        assertEquals("req-1", body.getString("request_id"))
        val decision = body.getJSONObject("response")
        assertEquals("allow", decision.getString("behavior"))
        val updated = decision.getJSONObject("updatedInput")
        assertEquals("Blue", updated.getJSONObject("answers").getString("Which color do you prefer?"))
        assertEquals(1, updated.getJSONArray("questions").length())
        // The original input is not modified.
        assertFalse(askInput.has("answers"))
    }

    @Test
    fun `deny and error responses`() {
        val deny = JSONObject(controlDeny("r", "No")).getJSONObject("response").getJSONObject("response")
        assertEquals("deny", deny.getString("behavior"))
        assertEquals("No", deny.getString("message"))
        val error = JSONObject(controlError("r", "Unsupported")).getJSONObject("response")
        assertEquals("error", error.getString("subtype"))
    }

    @Test
    fun `mcp permission requests are readable`() {
        val request = toolPermissionRequest("r", "s", "mcp__github__create_issue", JSONObject("""{"title":"Bug"}"""))
        assertEquals("github · create_issue", request.toolName)
        assertEquals("r", request.approvalId)
        assertEquals(RiskLevel.HIGH, request.risk)
        val fetch = toolPermissionRequest("r", "s", "WebFetch", JSONObject("""{"url":"https://example.com","prompt":"Summarise"}"""))
        assertEquals(listOf("https://example.com"), fetch.affectedPaths)
        assertEquals("Summarise", fetch.explanation)
    }

    @Test
    fun `question answers combine picks and other text`() {
        val item = AgentQuestionItem("Q", "", listOf(AgentQuestionOption("A"), AgentQuestionOption("B")), multiSelect = true)
        assertEquals("A, B", questionAnswer(item, setOf("B", "A"), ""))
        assertNull(questionAnswer(item, emptySet(), "typed but not chosen"))
        assertTrue(questionAnswer(item, setOf("A", "\u0000other"), " Custom ")!!.endsWith("A, Custom"))
        assertNull(questionAnswer(item, setOf("\u0000other"), "  "))
    }
}
