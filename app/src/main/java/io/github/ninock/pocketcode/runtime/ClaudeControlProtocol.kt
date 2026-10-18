package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.AgentQuestion
import io.github.ninock.pocketcode.model.AgentQuestionItem
import io.github.ninock.pocketcode.model.AgentQuestionOption
import io.github.ninock.pocketcode.model.RiskLevel
import io.github.ninock.pocketcode.model.ToolRequest
import org.json.JSONObject

/*
 * Claude Code's stream-json control protocol, as used by the Agent SDK. With
 * `--permission-prompt-tool stdio`, every permission prompt arrives on stdout as
 * a `control_request` and waits for a `control_response` on stdin. That is also
 * the only way print mode offers AskUserQuestion: the answers go back as the
 * tool's `answers` input.
 */

internal const val ASK_USER_QUESTION_TOOL = "AskUserQuestion"

/** One stdin line carrying a user message. */
internal fun claudeUserMessage(text: String): String = JSONObject()
    .put("type", "user")
    .put("message", JSONObject().put("role", "user").put("content", text))
    .toString()

internal fun controlAllow(requestId: String, updatedInput: JSONObject): String =
    controlSuccess(requestId, JSONObject().put("behavior", "allow").put("updatedInput", updatedInput))

internal fun controlDeny(requestId: String, message: String): String =
    controlSuccess(requestId, JSONObject().put("behavior", "deny").put("message", message))

internal fun controlError(requestId: String, error: String): String = JSONObject()
    .put("type", "control_response")
    .put("response", JSONObject().put("subtype", "error").put("request_id", requestId).put("error", error))
    .toString()

private fun controlSuccess(requestId: String, response: JSONObject): String = JSONObject()
    .put("type", "control_response")
    .put("response", JSONObject().put("subtype", "success").put("request_id", requestId).put("response", response))
    .toString()

/** The questions in an AskUserQuestion tool input, or null when there are none. */
internal fun parseAskUserQuestion(requestId: String, sessionId: String, input: JSONObject): AgentQuestion? {
    val array = input.optJSONArray("questions") ?: return null
    val questions = (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val question = item.optString("question").takeIf(String::isNotBlank) ?: return@mapNotNull null
        val optionArray = item.optJSONArray("options")
        val options = (0 until (optionArray?.length() ?: 0)).mapNotNull { optionIndex ->
            val option = optionArray?.optJSONObject(optionIndex) ?: return@mapNotNull null
            option.optString("label").takeIf(String::isNotBlank)
                ?.let { AgentQuestionOption(it, option.optString("description")) }
        }
        AgentQuestionItem(
            question = question,
            header = item.optString("header"),
            options = options,
            multiSelect = item.optBoolean("multiSelect"),
        )
    }
    return questions.takeIf { it.isNotEmpty() }?.let { AgentQuestion(requestId, sessionId, it) }
}

/** The tool input with the user's answers added, keyed by question text as Claude Code expects. */
internal fun answeredQuestionInput(input: JSONObject, answers: Map<String, String>): JSONObject {
    val updated = JSONObject(input.toString())
    val answerJson = JSONObject()
    answers.forEach { (question, answer) -> answerJson.put(question, answer) }
    return updated.put("answers", answerJson)
}

/** An approval card for a tool Claude Code wants permission to use. */
internal fun toolPermissionRequest(requestId: String, sessionId: String, toolName: String, input: JSONObject): ToolRequest {
    val paths = listOf("file_path", "path", "notebook_path", "url")
        .mapNotNull { key -> input.optString(key).takeIf(String::isNotBlank) }
    val command = input.optString("command").takeIf(String::isNotBlank)
    val readableName = if (toolName.startsWith("mcp__")) {
        // mcp__server__tool → server · tool
        toolName.removePrefix("mcp__").split("__", limit = 2).joinToString(" · ")
    } else toolName
    val explanation = input.optString("description").takeIf(String::isNotBlank)
        ?: command
        ?: input.optString("query").takeIf(String::isNotBlank)?.let { "Search for \"$it\"" }
        ?: input.optString("prompt").takeIf(String::isNotBlank)
        ?: "Claude Code wants to use $readableName."
    return ToolRequest(
        approvalId = requestId,
        sessionId = sessionId,
        toolName = readableName,
        explanation = explanation.take(600),
        affectedPaths = paths,
        commandPreview = command,
        risk = if (toolName == "Bash" || toolName.startsWith("mcp__")) RiskLevel.HIGH else RiskLevel.REVIEW,
    )
}
