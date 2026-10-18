package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.TokenUsage
import org.json.JSONObject

/** Reads token counts and cost out of the agents' JSON output. */
internal object UsageParsing {
    /** An Anthropic `usage` block, as found on assistant messages and Claude Code's `result`. */
    fun fromUsageBlock(usage: JSONObject?, model: String? = null): TokenUsage {
        if (usage == null) return TokenUsage(model = model)
        return TokenUsage(
            inputTokens = usage.long("input_tokens", "inputTokens", "prompt_tokens", "promptTokens"),
            outputTokens = usage.long("output_tokens", "outputTokens", "completion_tokens", "completionTokens"),
            cacheReadTokens = usage.long("cache_read_input_tokens", "cacheReadInputTokens", "cacheReadTokens", "cached_tokens"),
            cacheWriteTokens = usage.long("cache_creation_input_tokens", "cacheCreationInputTokens", "cacheWriteTokens"),
            model = model?.takeIf(String::isNotBlank),
        )
    }

    /**
     * Claude Code's final `result` event. `modelUsage` covers every model the run used (including
     * the small model Claude Code calls for housekeeping), so it wins over the main-model `usage`.
     */
    fun fromClaudeResult(json: JSONObject): TokenUsage? {
        val cost = json.optDouble("total_cost_usd", Double.NaN).takeUnless(Double::isNaN)
        val perModel = json.optJSONObject("modelUsage")
        val fromModels = perModel?.keys()?.asSequence()?.mapNotNull { name ->
            val entry = perModel.optJSONObject(name) ?: return@mapNotNull null
            name to TokenUsage(
                inputTokens = entry.long("inputTokens", "input_tokens"),
                outputTokens = entry.long("outputTokens", "output_tokens"),
                cacheReadTokens = entry.long("cacheReadInputTokens", "cache_read_input_tokens"),
                cacheWriteTokens = entry.long("cacheCreationInputTokens", "cache_creation_input_tokens"),
                costUsd = entry.optDouble("costUSD", Double.NaN).takeUnless(Double::isNaN),
            )
        }?.toList().orEmpty()
        val usage = if (fromModels.any { !it.second.isEmpty }) {
            // Name the run after the model that did most of the writing.
            val main = fromModels.maxByOrNull { it.second.outputTokens }?.first
            fromModels.map { it.second }.reduce(TokenUsage::plus).copy(model = main)
        } else {
            fromUsageBlock(json.optJSONObject("usage"))
        }
        val result = usage.copy(costUsd = cost ?: usage.costUsd)
        return result.takeUnless { it.isEmpty }
    }

    /**
     * Sums per-message usage. Claude Code repeats an assistant message once per content block,
     * so callers key messages by id and keep the latest block for each.
     */
    fun sum(messages: Collection<TokenUsage>): TokenUsage? =
        messages.takeIf { it.isNotEmpty() }?.reduce(TokenUsage::plus)?.takeUnless { it.isEmpty }

    private fun JSONObject.long(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { key -> if (has(key) && !isNull(key)) optLong(key, 0L) else null }?.coerceAtLeast(0L) ?: 0L
}
