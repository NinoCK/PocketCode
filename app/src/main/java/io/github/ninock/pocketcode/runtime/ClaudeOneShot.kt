package io.github.ninock.pocketcode.runtime

import android.content.Context
import io.github.ninock.pocketcode.model.AgentKind
import io.github.ninock.pocketcode.model.ProviderKind
import io.github.ninock.pocketcode.model.ProviderProfile
import io.github.ninock.pocketcode.model.ProviderProtocol
import io.github.ninock.pocketcode.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class OneShotResult(val text: String, val usage: TokenUsage?)

/**
 * Short, tool-less requests to Claude Code, used for drafting commit messages and pull request
 * descriptions. It signs in exactly like a chat run, so it works with any provider Claude Code
 * supports, including a Claude subscription.
 */
class ClaudeOneShot(
    private val context: Context,
    private val secretFor: (ProviderProfile) -> String?,
) {
    private val installer = RuntimeInstaller(context)

    /** Why drafting can't run with this setup, or null when it can. */
    fun unavailableReason(provider: ProviderProfile): String? = when {
        !installer.isAgentInstalled(AgentKind.CLAUDE_CODE) -> "Drafting uses Claude Code. Install it from Settings first."
        provider.kind == ProviderKind.OPENCODE_ZEN -> "Drafting isn't available with OpenCode Zen. Pick another provider in Settings."
        secretFor(provider).isNullOrBlank() && localServerPlaceholderKey(provider).isBlank() -> if (provider.kind == ProviderKind.CLAUDE) "Sign in to Claude in Settings first." else "Add an API key for ${provider.kind.title} in Settings first."
        else -> null
    }

    /**
     * Sends [input] with [instructions] as the system prompt and returns the reply. Runs in the
     * project folder so Claude Code trusts it, but no tools are available to it.
     */
    suspend fun run(
        provider: ProviderProfile,
        workspace: File,
        guestWorkspacePath: String,
        instructions: String,
        input: String,
    ): OneShotResult = withContext(Dispatchers.IO) {
        unavailableReason(provider)?.let { error(it) }
        val secret = secretFor(provider).orEmpty().ifBlank { localServerPlaceholderKey(provider) }
        val subscription = provider.kind.protocol == ProviderProtocol.CLAUDE_LOGIN
        val formatGateway = if (provider.kind.protocol == ProviderProtocol.OPENAI_CHAT || provider.kind.protocol == ProviderProtocol.OPENAI_RESPONSES) {
            LocalFormatGateway(provider, secret).start()
        } else null
        val routingGateway = if (provider.kind == ProviderKind.LLM_ROUTER && provider.openRouterProviders.isNotEmpty()) {
            OpenRouterRoutingGateway(provider, secret).start()
        } else null
        val outputFile = File(context.cacheDir, "oneshot-${System.nanoTime()}.log")
        try {
            val launch = RuntimeLaunchConfigBuilder.build(provider, authToken = secret, localGatewayUrl = formatGateway?.url ?: routingGateway?.url)
            val command = buildList {
                add(launch.executable)
                if (!subscription) add("--bare")
                addAll(listOf("-p", "--output-format", "json", "--max-turns", "1", "--tools", "", "--strict-mcp-config"))
                // "haiku" is the quick model on Anthropic; other providers map it to their own model.
                addAll(listOf("--model", "haiku"))
                // A subscription keeps Claude Code's own system prompt, which its sign-in expects;
                // API keys get a lean prompt so a draft costs a fraction of a cent.
                addAll(listOf(if (subscription) "--append-system-prompt" else "--system-prompt", instructions))
            }
            val installed = installer.installedRuntime()
            val process = installer.process(
                installed.proot,
                installed.rootfs,
                workspace,
                launch.environment,
                command,
                guestWorkspacePath = guestWorkspacePath,
                outputFile = outputFile,
            )
            runCatching {
                process.outputStream.bufferedWriter().use { it.write(input) }
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("The model took too long to answer")
            }
            parse(outputFile.readTailText(MAX_OUTPUT_BYTES))
        } finally {
            formatGateway?.close()
            routingGateway?.close()
            outputFile.delete()
        }
    }

    internal companion object {
        private const val TIMEOUT_SECONDS = 120L
        private const val MAX_OUTPUT_BYTES = 256 * 1024

        /** Reads Claude Code's `--output-format json` envelope out of the process output. */
        fun parse(output: String): OneShotResult {
            val envelope = output.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("{") }
                .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
                .lastOrNull { it.optString("type") == "result" }
                ?: error(
                    output.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty)?.take(240)
                        ?: "Claude Code returned nothing",
                )
            val usage = UsageParsing.fromClaudeResult(envelope)
            val text = envelope.optString("result").trim()
            if (envelope.optBoolean("is_error") || text.isEmpty()) {
                error(text.ifEmpty { "Claude Code couldn't write a draft" }.take(240))
            }
            return OneShotResult(text, usage)
        }
    }
}

/** Tidies a model's draft: drops code fences and wrapping quotes it may add despite instructions. */
internal fun cleanDraft(text: String): String {
    var draft = text.trim()
    if (draft.startsWith("```")) {
        draft = draft.removePrefix("```").substringAfter('\n', "").substringBeforeLast("```").trim()
    }
    if (draft.length >= 2 && draft.first() == '"' && draft.last() == '"' && draft.count { it == '"' } == 2) {
        draft = draft.substring(1, draft.length - 1).trim()
    }
    return draft
}
