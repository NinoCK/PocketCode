package io.github.ninock.pocketcode.runtime

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.ninock.pocketcode.model.ChatMessage
import io.github.ninock.pocketcode.model.ChangeItem
import io.github.ninock.pocketcode.model.DiffLine
import io.github.ninock.pocketcode.model.DiffLineType
import io.github.ninock.pocketcode.model.ProviderKind
import io.github.ninock.pocketcode.model.ProjectKind
import io.github.ninock.pocketcode.model.ProviderProfile
import io.github.ninock.pocketcode.model.RiskLevel
import io.github.ninock.pocketcode.model.RuntimeEvent
import io.github.ninock.pocketcode.model.ToolRequest
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

internal object ProviderRuntimeErrorDetector {
    fun detect(line: String, subscription: Boolean = false): String? {
        val json = runCatching { JSONObject(line) }.getOrNull()
        // Replies, tool output and questions can mention "rate limit" or "expired";
        // only error reports and non-JSON diagnostics are checked.
        when (json?.optString("type")) {
            "user", "stream_event", "control_request", "control_cancel_request" -> return null
            // Claude Code marks a failed API call with an `error` field on the assistant message.
            "assistant" -> if (json.optString("error").isBlank()) return null
            "result" -> if (!json.optBoolean("is_error")) return null
            "system" -> if (json.optString("subtype") == "init") return null
        }
        val combined = buildString {
            append(line)
            json?.let {
                append(' ')
                append(it.optString("error"))
                append(' ')
                append(it.optString("message"))
                append(' ')
                append(it.optString("result"))
            }
        }.lowercase()
        if (subscription) {
            val status = json?.takeIf { it.optString("subtype") == "api_retry" }?.optInt("error_status")
            return when {
                "http 429" in combined || "rate limit" in combined || "usage limit" in combined || status == 429 ->
                    "Your Claude subscription's usage limit was reached. Try again after it resets."
                "authentication_failed" in combined ||
                    "authentication failed" in combined ||
                    "invalid api key" in combined ||
                    "oauth token" in combined && "expired" in combined ||
                    "http 401" in combined ||
                    "http 403" in combined ||
                    status == 401 || status == 403 ->
                    "Claude rejected the subscription token. Sign in with Claude again from Agent → AI provider."
                else -> null
            }
        }
        return when {
            "user not found" in combined -> "User not found. Check the API key and provider account."
            "authentication_failed" in combined ||
                "authentication failed" in combined ||
                "invalid api key" in combined ||
                "http 401" in combined ||
                "http 403" in combined ||
                "http 429" in combined ||
                "expired" in combined ||
                "quota" in combined ||
                "rate limit" in combined ||
                (json?.optString("subtype") == "api_retry" && json.optInt("error_status") in listOf(401, 403, 429)) ->
                "The provider rejected the saved API key."
            else -> null
        }
    }
}

/**
 * Claude Code print-mode command line used for every coding task. The prompt
 * goes in on stdin as a stream-json user message ([claudeUserMessage]), and
 * permission prompts, including AskUserQuestion, come back as control requests.
 */
internal fun claudePrintCommand(
    executable: String,
    model: String,
    subscription: Boolean,
    effort: String = "",
    controls: ClaudeSessionControls = ClaudeSessionControls(),
    appendSystemPrompt: String = "",
    /** Guest path of an `--mcp-config` file for PocketCode's built-in tools. */
    pocketMcpConfigPath: String? = null,
): List<String> = buildList {
    add(executable)
    // --bare limits Claude Code to ANTHROPIC_API_KEY/apiKeyHelper and never
    // reads CLAUDE_CODE_OAUTH_TOKEN, so a subscription run must not use it or
    // every request goes out without credentials.
    if (!subscription) add("--bare")
    add("-p")
    add("--input-format")
    add("stream-json")
    add("--permission-prompt-tool")
    add("stdio")
    add("--output-format")
    add("stream-json")
    add("--include-partial-messages")
    add("--verbose")
    // A subscription on "default" lets Claude Code pick its plan's default model.
    if (!subscription || (model.isNotBlank() && model != ProviderKind.CLAUDE.defaultModel)) {
        add("--model")
        add(model)
    }
    if (subscription && effort in io.github.ninock.pocketcode.model.ClaudeEffortLevels.map { it.first } && effort != "default") {
        add("--effort")
        add(effort)
    }
    addAll(claudeControlArguments(controls))
    if (pocketMcpConfigPath != null) {
        add("--mcp-config")
        add(pocketMcpConfigPath)
        // Bash is already auto-approved, and these tools do less than Bash can.
        add("--allowedTools")
        add("mcp__$POCKET_MCP_SERVER_NAME")
    }
    val systemPrompt = listOfNotNull(
        appendSystemPrompt.takeIf(String::isNotBlank),
        POCKET_PREVIEW_SYSTEM_PROMPT.takeIf { pocketMcpConfigPath != null },
        PLAN_MODE_SYSTEM_PROMPT.takeIf { controls.planMode },
    ).joinToString("\n\n")
    if (systemPrompt.isNotBlank()) {
        add("--append-system-prompt")
        add(systemPrompt)
    }
    add("--max-turns")
    add("25")
}

private const val POCKET_MCP_CONFIG_FILE = "pocketcode-mcp.json"

internal const val POCKET_PREVIEW_SYSTEM_PROMPT =
    "PocketCode shows web apps in its Preview tab. To run a dev server for the user to see, call " +
        "mcp__${POCKET_MCP_SERVER_NAME}__preview_start (optionally with the command) instead of starting it with Bash: " +
        "servers started from Bash stop when your turn ends, while preview_start keeps the server running and opens it in the app. " +
        "Use preview_status to read the server's URL and recent output, and preview_stop to stop it."

class ClaudeRuntimeBridge(
    private val context: Context,
    /** `--mcp-config` content for PocketCode's built-in tools, or null when unavailable. */
    private val pocketMcpConfig: () -> String? = { null },
    /** Claude Code `--effort` for subscription runs. */
    private val effort: () -> String = { "" },
    /** Command palette switches (plan mode, disabled tools, MCP servers, skills, subagents). */
    private val controls: () -> ClaudeSessionControls = { ClaudeSessionControls() },
    private val secretFor: (ProviderProfile) -> String?,
) : RuntimeBridge {
    private val installer = RuntimeInstaller(context)
    private val eventBus = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
    override val events: Flow<RuntimeEvent> = eventBus
    private val capabilityBus = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Project ids whose Claude Code capabilities changed after a run reported them. */
    val capabilityUpdates: Flow<String> = capabilityBus
    @Volatile private var activeProjectId: String? = null
    private val pending = ConcurrentHashMap<String, PendingPermission>()
    /** Permission prompts and questions waiting for the user, by control request id. */
    private val controlRequests = ConcurrentHashMap<String, PendingControl>()
    private val stdinLock = Any()
    @Volatile private var activeStdin: java.io.OutputStream? = null
    private val toolNames = ConcurrentHashMap<String, String>()
    private val seenToolCalls = ConcurrentHashMap.newKeySet<String>()
    private val finishedSessions = ConcurrentHashMap.newKeySet<String>()
    private val projectRoots = ConcurrentHashMap<String, String>()
    @Volatile private var activeProcess: Process? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var userStopRequested: Boolean = false
    @Volatile private var activeProjectSlug: String? = null
    @Volatile private var taskStartedAtElapsedRealtime: Long = 0L
    @Volatile private var lastForegroundProgressAt: Long = 0L
    @Volatile private var foregroundResultPosted: Boolean = false
    private val streamedText = StringBuilder()
    private val streamedThinking = StringBuilder()
    private var lastReasoningTokens = 0
    private var lastReasoningUpdateAt = 0L
    private var lastThinkingUpdateAt = 0L
    private var currentThinkingBlockId = 0L
    /** Usage of each API response in the running session, by message id. */
    private val messageUsage = ConcurrentHashMap<String, io.github.ninock.pocketcode.model.TokenUsage>()
    private val usageReported = ConcurrentHashMap.newKeySet<String>()

    override suspend fun startSession(projectId: String, projectSlug: String, projectKind: ProjectKind, prompt: String, conversationHistory: List<ChatMessage>, provider: ProviderProfile): String = withContext(Dispatchers.IO + NonCancellable) {
        val sessionId = UUID.randomUUID().toString()
        finishedSessions.remove(sessionId)
        activeSessionId = sessionId
        userStopRequested = false
        activeProjectSlug = projectSlug
        activeProjectId = projectId
        taskStartedAtElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        lastForegroundProgressAt = 0L
        foregroundResultPosted = false
        toolNames.clear()
        seenToolCalls.clear()
        lastReasoningTokens = 0
        lastReasoningUpdateAt = 0L
        lastThinkingUpdateAt = 0L
        currentThinkingBlockId = 0L
        streamedThinking.clear()
        messageUsage.clear()
        eventBus.emit(RuntimeEvent.SessionStarted(sessionId))
        pushForegroundProgress("Starting Claude Code…")
        val secret = secretFor(provider).orEmpty().ifBlank { localServerPlaceholderKey(provider) }
        if (secret.isBlank()) {
            val message = if (provider.kind == ProviderKind.CLAUDE) {
                "No Claude subscription token is saved. Add one from Agent → AI provider."
            } else {
                "No API key is saved for ${provider.kind.title}."
            }
            eventBus.emit(RuntimeEvent.SessionFailed(sessionId, message))
            return@withContext sessionId
        }

        var formatGateway: LocalFormatGateway? = null
        var openRouterGateway: OpenRouterRoutingGateway? = null
        runCatching {
            RuntimeTaskController.stopAction = {
                userStopRequested = true
                val running = activeProcess
                if (running != null) {
                    Thread {
                        running.destroy()
                        Thread.sleep(500)
                        if (running.isAlive) running.destroyForcibly()
                    }.start()
                }
            }
            startForegroundRuntime(projectSlug)
            // Setup and release checks happen once in the app-start loading flow.
            // Sending a prompt must never perform network update checks or put setup
            // messages into the conversation.
            val installed = installer.installedRuntime()
            installer.ensureSettingsAndHooks()
            val workspace = ensureWorkspace(projectId)
            createCheckpoint(projectId, workspace)
            val before = snapshot(workspace)
            formatGateway = if (provider.kind.protocol in setOf(
                    io.github.ninock.pocketcode.model.ProviderProtocol.OPENAI_CHAT,
                    io.github.ninock.pocketcode.model.ProviderProtocol.OPENAI_RESPONSES,
                )) LocalFormatGateway(provider, secret).start() else null
            openRouterGateway = if (
                provider.kind == ProviderKind.LLM_ROUTER && provider.openRouterProviders.isNotEmpty()
            ) OpenRouterRoutingGateway(provider, secret).start() else null
            val subscription = provider.kind.protocol == io.github.ninock.pocketcode.model.ProviderProtocol.CLAUDE_LOGIN
            val launch = RuntimeLaunchConfigBuilder.build(
                provider,
                authToken = secret,
                localGatewayUrl = formatGateway?.url ?: openRouterGateway?.url,
            )
            Log.d("ClaudeBridge", "Provider: ${provider.kind}, Model: ${provider.model}, BaseUrl: ${provider.baseUrl}")
            Log.d("ClaudeBridge", "Launch environment keys: ${launch.environment.keys}")

            // Build a context-aware prompt that includes conversation history
            val guestWorkspacePath = "/workspace/$projectSlug"
            // Claude Code only expands `/skill args` at the very start of the prompt,
            // so a known slash command goes first and the context moves to the system prompt.
            val slashCommand = slashCommandName(prompt)?.takeIf { name ->
                readCapabilities(projectId).findCommand(name) != null
            }
            val contextPrompt = if (slashCommand != null) {
                prompt.trimStart()
            } else {
                buildContextPrompt(prompt, conversationHistory, guestWorkspacePath, projectKind)
            }
            val systemContext = if (slashCommand != null) {
                buildContextPrompt(null, conversationHistory, guestWorkspacePath, projectKind)
            } else ""

            val command = claudePrintCommand(
                executable = launch.executable,
                model = launch.environment["ANTHROPIC_MODEL"] ?: provider.model,
                subscription = subscription,
                effort = effort(),
                controls = controls(),
                appendSystemPrompt = systemContext,
                pocketMcpConfigPath = writePocketMcpConfig(),
            )
            Log.d("ClaudeBridge", "Launching command: $command")
            val process = installer.process(
                installed.proot,
                installed.rootfs,
                workspace,
                launch.environment,
                command,
                guestWorkspacePath = guestWorkspacePath,
            )
            activeProcess = process
            synchronized(stdinLock) { activeStdin = process.outputStream }
            if (!writeControl(claudeUserMessage(contextPrompt)) && !userStopRequested) {
                error("Could not send the message to Claude Code")
            }
            if (userStopRequested) process.destroy()
            coroutineScope {
                val permissionWatcher = launch { watchPermissionRequests(sessionId) }
                var lastDiagnostic = ""
                val pendingOutput = StringBuilder()
                val nativeProcess = process as? NativeSpawnProcess
                    ?: error("Unsupported Android runtime process")
                var outputOffset = 0L
                while (process.isAlive || nativeProcess.outputFile.length() > outputOffset) {
                    val available = nativeProcess.outputFile.length() - outputOffset
                    if (available <= 0) {
                        delay(50)
                        continue
                    }
                    val bytes = ByteArray(minOf(available, 16L * 1024).toInt())
                    val count = RandomAccessFile(nativeProcess.outputFile, "r").use { file ->
                        file.seek(outputOffset)
                        file.read(bytes)
                    }
                    if (count > 0) {
                        outputOffset += count
                        pendingOutput.append(bytes.decodeToString(0, count))
                        var newline = pendingOutput.indexOf("\n")
                        while (newline >= 0) {
                            val line = pendingOutput.substring(0, newline).trimEnd('\r')
                            pendingOutput.delete(0, newline + 1)
                            if (line.isNotBlank()) {
                                Log.d("ClaudeBridge", "OUTPUT: $line")
                                ProviderRuntimeErrorDetector.detect(line, subscription)?.let { reason ->
                                    process.destroyForcibly()
                                    throw ProviderSessionException(reason)
                                }
                                if (!consumeClaudeEvent(sessionId, line)) {
                                    lastDiagnostic = line.takeLast(500)
                                    terminalStatus(line)?.let { (title, detail) ->
                                        eventBus.emit(RuntimeEvent.RuntimeLog(sessionId, title, detail))
                                    }
                                }
                            }
                            newline = pendingOutput.indexOf("\n")
                        }
                    }
                }
                pendingOutput.toString().trim().takeIf(String::isNotBlank)?.let { line ->
                    Log.d("ClaudeBridge", "TRAILING OUTPUT: $line")
                    if (!consumeClaudeEvent(sessionId, line)) lastDiagnostic = line.takeLast(500)
                }
                val exit = process.waitFor()
                Log.d("ClaudeBridge", "Process exited with code $exit")
                permissionWatcher.cancelAndJoin()
                pending.values.filter { it.request.sessionId == sessionId }.forEach { permission ->
                    permission.response.writeText("deny")
                    pending.remove(permission.request.approvalId)
                }
                controlRequests.entries.removeAll { it.value.sessionId == sessionId }
                val changed = changedFiles(workspace, before)
                if (changed.isNotEmpty()) {
                    Log.d("ClaudeBridge", "Changed files: $changed")
                    saveChangedPaths(projectId, changed)
                    val details = loadPendingChanges(projectId)
                    eventBus.emit(RuntimeEvent.FilesChanged(sessionId, details))
                } else if (!File(checkpointDir(projectId), "changes.json").isFile) {
                    acceptLastChanges(projectId)
                }
                if (exit == 0) {
                    emitCompletedOnce(sessionId)
                    finishForegroundRuntime(
                        completed = true,
                        projectName = projectSlug,
                        detail = "Claude Code finished the task in $projectSlug.",
                    )
                } else {
                    if (userStopRequested) throw ProviderSessionException("Stopped by user")
                    error(lastDiagnostic.ifBlank { "Claude Code stopped with exit code $exit" })
                }
            }
        }.onFailure { error ->
            Log.e("ClaudeBridge", "Session failed", error)
            val message = friendlyError(error)
            emitFailureOnce(sessionId, message)
            if (userStopRequested) {
                cancelForegroundRuntime()
            } else {
                finishForegroundRuntime(
                    completed = false,
                    projectName = projectSlug,
                    detail = message,
                )
            }
        }
        // Runs that end without Claude Code's `result` envelope still used tokens.
        emitUsageOnce(sessionId, UsageParsing.sum(messageUsage.values))
        formatGateway?.close()
        openRouterGateway?.close()
        closeStdin()
        controlRequests.entries.removeAll { it.value.sessionId == sessionId }
        activeProcess = null
        activeSessionId = null
        RuntimeTaskController.stopAction = null
        sessionId
    }

    override suspend fun respondToApproval(request: ToolRequest, approved: Boolean) = withContext(Dispatchers.IO) {
        controlRequests.remove(request.approvalId)?.let { control ->
            writeControl(
                if (approved) controlAllow(request.approvalId, control.input)
                else controlDeny(request.approvalId, "The user rejected this action."),
            )
            eventBus.emit(
                if (approved) RuntimeEvent.ToolApproved(request.sessionId, request.approvalId)
                else RuntimeEvent.ToolRejected(request.sessionId, request.approvalId),
            )
            return@withContext
        }
        val permission = pending.remove(request.approvalId) ?: return@withContext
        permission.response.writeText(if (approved) "allow" else "deny")
        eventBus.emit(
            if (approved) RuntimeEvent.ToolApproved(request.sessionId, request.approvalId)
            else RuntimeEvent.ToolRejected(request.sessionId, request.approvalId),
        )
    }

    /**
     * Sends the user's answers to an AskUserQuestion prompt, keyed by question
     * text. Null skips the questions and lets Claude carry on without them.
     */
    suspend fun answerQuestion(requestId: String, answers: Map<String, String>?) = withContext(Dispatchers.IO) {
        val control = controlRequests.remove(requestId) ?: return@withContext
        writeControl(
            if (answers != null) {
                controlAllow(requestId, answeredQuestionInput(control.input, answers))
            } else {
                controlDeny(requestId, "The user skipped these questions. Continue with your best judgement.")
            },
        )
        eventBus.emit(RuntimeEvent.QuestionAnswered(control.sessionId, requestId, answers))
        pushForegroundProgress(if (answers != null) "Continuing with your answers…" else "Continuing…")
    }

    private suspend fun handleControlRequest(sessionId: String, json: JSONObject) {
        val requestId = json.optString("request_id").takeIf(String::isNotBlank) ?: return
        val request = json.optJSONObject("request") ?: return
        if (request.optString("subtype") != "can_use_tool") {
            writeControl(controlError(requestId, "PocketCode does not handle ${request.optString("subtype")} requests"))
            return
        }
        val toolName = request.optString("tool_name").ifBlank { "Tool" }
        val input = request.optJSONObject("input") ?: JSONObject()
        if (toolName == ASK_USER_QUESTION_TOOL) {
            val question = parseAskUserQuestion(requestId, sessionId, input)
            if (question == null) {
                writeControl(controlDeny(requestId, "The question had nothing to show the user."))
                return
            }
            controlRequests[requestId] = PendingControl(sessionId, toolName, input)
            eventBus.emit(RuntimeEvent.QuestionAsked(sessionId, question))
            pushForegroundProgress("Claude has a question for you")
        } else {
            controlRequests[requestId] = PendingControl(sessionId, toolName, input)
            eventBus.emit(RuntimeEvent.ToolRequested(sessionId, toolPermissionRequest(requestId, sessionId, toolName, input)))
            pushForegroundProgress("Claude is waiting for your approval")
        }
    }

    /** Claude Code withdrew a prompt, e.g. because the turn was interrupted. */
    private suspend fun cancelControlRequest(sessionId: String, json: JSONObject) {
        val requestId = json.optString("request_id")
        val control = controlRequests.remove(requestId) ?: return
        eventBus.emit(
            if (control.toolName == ASK_USER_QUESTION_TOOL) RuntimeEvent.QuestionAnswered(sessionId, requestId, null)
            else RuntimeEvent.ToolRejected(sessionId, requestId),
        )
    }

    /** Writes one line to Claude Code's stdin; false when the process is gone. */
    private fun writeControl(line: String): Boolean = synchronized(stdinLock) {
        val stdin = activeStdin ?: return@synchronized false
        runCatching {
            stdin.write((line + "\n").toByteArray())
            stdin.flush()
        }.onFailure { Log.w("ClaudeBridge", "Could not write to Claude Code", it) }.isSuccess
    }

    private fun closeStdin() = synchronized(stdinLock) {
        runCatching { activeStdin?.close() }
        activeStdin = null
    }

    override suspend fun stopSession(sessionId: String) = withContext(Dispatchers.IO) {
        if (activeSessionId == sessionId) {
            userStopRequested = true
            activeProcess?.destroy()
            delay(500)
            if (activeProcess?.isAlive == true) activeProcess?.destroyForcibly()
            emitFailureOnce(sessionId, "Stopped by user")
        }
    }

    override suspend fun stopActiveSession() {
        activeSessionId?.let { stopSession(it) }
    }

    override suspend fun undoLastChanges(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val checkpoint = checkpointDir(projectId)
        val backup = File(checkpoint, "project")
        val manifest = File(checkpoint, "changes.json")
        if (!backup.isDirectory || !manifest.isFile) return@withContext false
        val workspace = ensureWorkspace(projectId)
        val paths = runCatching {
            val array = JSONArray(manifest.readText())
            (0 until array.length()).map(array::getString)
        }.getOrElse { return@withContext false }.filterNot(::isInternalRuntimePath)

        paths.forEach { path ->
            val target = safeWorkspaceFile(workspace, path)
            val original = safeWorkspaceFile(backup, path)
            if (original.isFile) {
                target.parentFile?.mkdirs()
                original.copyTo(target, overwrite = true)
            } else if (target.isFile) {
                target.delete()
            }
        }
        checkpoint.deleteRecursively()
        true
    }

    override suspend fun acceptLastChanges(projectId: String) {
        withContext(Dispatchers.IO) {
            checkpointDir(projectId).deleteRecursively()
        }
    }

    override suspend fun loadPendingChanges(projectId: String): List<ChangeItem> = withContext(Dispatchers.IO) {
        val workspace = ensureWorkspace(projectId)
        val paths = readChangedPaths(projectId).filterNot(::isInternalRuntimePath)
        if (paths.isEmpty()) emptyList() else buildChangeDetails(projectId, workspace, paths)
    }

    override suspend fun undoFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (isInternalRuntimePath(path) || path !in readChangedPaths(projectId)) return@withContext false
        val workspace = ensureWorkspace(projectId)
        val backup = File(checkpointDir(projectId), "project")
        val target = safeWorkspaceFile(workspace, path)
        val original = safeWorkspaceFile(backup, path)
        if (original.isFile) {
            target.parentFile?.mkdirs()
            original.copyTo(target, overwrite = true)
        } else if (target.isFile) {
            target.delete()
        }
        removeChangedPath(projectId, path)
        true
    }

    override suspend fun acceptFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (isInternalRuntimePath(path) || path !in readChangedPaths(projectId)) return@withContext false
        val workspace = ensureWorkspace(projectId)
        val backup = File(checkpointDir(projectId), "project")
        val current = safeWorkspaceFile(workspace, path)
        val baseline = safeWorkspaceFile(backup, path)
        if (current.isFile) {
            baseline.parentFile?.mkdirs()
            current.copyTo(baseline, overwrite = true)
        } else if (baseline.isFile) {
            baseline.delete()
        }
        removeChangedPath(projectId, path)
        true
    }

    /** Writes the built-in MCP config into the shared bridge folder; returns its guest path. */
    private fun writePocketMcpConfig(): String? {
        val config = pocketMcpConfig() ?: return null
        val file = File(context.filesDir, "runtime-bridge/$POCKET_MCP_CONFIG_FILE")
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(config)
            "/pocket-bridge/$POCKET_MCP_CONFIG_FILE"
        }.getOrNull()
    }

    private suspend fun watchPermissionRequests(sessionId: String) {
        val bridge = File(context.filesDir, "runtime-bridge")
        while (kotlin.coroutines.coroutineContext.isActive) {
            bridge.listFiles { file -> file.name.endsWith(".request") }.orEmpty().forEach { file ->
                val approvalId = file.name.removeSuffix(".request")
                runCatching {
                    val json = JSONObject(file.readText())
                    val toolName = json.optString("tool_name", "Tool")
                    val input = json.optJSONObject("tool_input") ?: JSONObject()
                    val command = input.optString("command").ifBlank { null }
                    val paths = listOf("file_path", "path", "notebook_path")
                        .mapNotNull { key -> input.optString(key).takeIf(String::isNotBlank) }
                    val explanation = input.optString("description")
                        .ifBlank { command.orEmpty() }
                        .ifBlank { "$toolName running in project" }

                    Log.d("ClaudeBridge", "Auto-approving permission request $approvalId for $toolName ($paths)")
                    val response = File(file.parentFile, "$approvalId.response")
                    response.writeText("allow")

                    eventBus.emit(RuntimeEvent.ToolCompleted(sessionId, toolName, explanation))
                }.onFailure {
                    File(file.parentFile, "$approvalId.response").writeText("allow")
                }
            }
            delay(50)
        }
    }

    private suspend fun consumeClaudeEvent(sessionId: String, line: String): Boolean {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return false
        consumeClaudeJsonEvent(sessionId, json)
        return true
    }

    private suspend fun consumeClaudeJsonEvent(sessionId: String, json: JSONObject) {
        when (json.optString("type")) {
            "stream_event" -> json.optJSONObject("event")?.let { consumeClaudeJsonEvent(sessionId, it) }
            "control_request" -> handleControlRequest(sessionId, json)
            "control_cancel_request" -> cancelControlRequest(sessionId, json)
            "system" -> when (json.optString("subtype")) {
                "init" -> rememberCapabilities(json)
                "thinking_tokens" -> emitReasoningProgress(sessionId, json.optInt("estimated_tokens"))
                "permission_denied" -> eventBus.emit(
                    RuntimeEvent.RuntimeLog(
                        sessionId,
                        "Permission denied",
                        sanitizeForDisplay(json.optString("decision_reason").ifBlank { json.optString("message") }),
                    ),
                )
            }
            "content_block_start" -> {
                val block = json.optJSONObject("content_block")
                when (block?.optString("type")) {
                    "thinking" -> {
                        currentThinkingBlockId += 1
                        streamedThinking.clear()
                        emitReasoningSummary(sessionId, "", startsNewBlock = true)
                        block.optString("thinking").takeIf(String::isNotBlank)?.let {
                            streamedThinking.append(it)
                            emitReasoningSummary(sessionId, it, force = true)
                        }
                    }
                    "text" -> streamedText.clear()
                }
            }
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                when (delta?.optString("type")) {
                    "thinking_delta" -> delta.optString("thinking").takeIf(String::isNotEmpty)?.let {
                        streamedThinking.append(it)
                        emitReasoningSummary(sessionId, streamedThinking.toString())
                    }
                    "text_delta", "" -> delta.optString("text").takeIf(String::isNotEmpty)?.let {
                        streamedText.append(it)
                        eventBus.emit(RuntimeEvent.AssistantDelta(sessionId, it))
                    }
                }
            }
            "content_block_stop" -> {
                if (streamedThinking.isNotBlank()) {
                    emitReasoningSummary(sessionId, streamedThinking.toString(), force = true, isFinal = true)
                }
            }
            "assistant" -> {
                val message = json.optJSONObject("message") ?: return
                message.optString("id").takeIf(String::isNotBlank)?.let { id ->
                    message.optJSONObject("usage")?.let { messageUsage[id] = UsageParsing.fromUsageBlock(it, message.optString("model")) }
                }
                val content = message.optJSONArray("content") ?: return
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    when (block.optString("type")) {
                        "text" -> if (streamedText.isEmpty()) {
                            block.optString("text").takeIf(String::isNotBlank)?.let {
                                eventBus.emit(RuntimeEvent.AssistantDelta(sessionId, it))
                            }
                        }
                        "thinking" -> block.optString("thinking").takeIf(String::isNotBlank)?.let {
                            if (currentThinkingBlockId == 0L || streamedThinking.toString() != it) {
                                currentThinkingBlockId += 1
                                streamedThinking.clear()
                                streamedThinking.append(it)
                                emitReasoningSummary(
                                    sessionId,
                                    it,
                                    force = true,
                                    startsNewBlock = true,
                                    isFinal = true,
                                )
                            }
                        }
                        "tool_use" -> emitToolStarted(sessionId, block)
                    }
                }
                streamedText.clear()
                // Some Anthropic-compatible providers omit Claude Code's final
                // `result` envelope. An assistant end_turn is still authoritative;
                // tool_use means the agent must remain active for another turn.
                if (message.optString("stop_reason") == "end_turn") {
                    emitCompletedOnce(sessionId)
                    terminateActiveProcessGracefully()
                }
            }
            "user" -> {
                val content = json.optJSONObject("message")?.optJSONArray("content") ?: return
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") == "tool_result") {
                        val toolId = block.optString("tool_use_id")
                        val toolName = toolNames.remove(toolId) ?: "Tool"
                        val result = block.optString("content")
                            .ifBlank { if (block.optBoolean("is_error")) "Tool failed" else "Completed successfully" }
                        eventBus.emit(RuntimeEvent.ToolCompleted(sessionId, toolName, sanitizeForDisplay(result)))
                    }
                }
            }
            "result" -> {
                emitUsageOnce(sessionId, UsageParsing.fromClaudeResult(json) ?: UsageParsing.sum(messageUsage.values))
                if (json.optBoolean("is_error")) {
                    val message = json.optString("result").ifBlank { "Claude Code reported an error" }
                    throw IllegalStateException(message)
                }
                // The structured result is Claude Code's authoritative terminal event.
                // Update the UI immediately instead of waiting for a PRoot/Node wrapper
                // that may remain alive after the answer has already completed.
                emitCompletedOnce(sessionId)
                terminateActiveProcessGracefully()
            }
        }
    }

    private suspend fun emitReasoningSummary(
        sessionId: String,
        text: String,
        force: Boolean = false,
        startsNewBlock: Boolean = false,
        isFinal: Boolean = false,
    ) {
        val summary = sanitizeForDisplay(text).trim().take(2_000)
        if (summary.isBlank() && !startsNewBlock) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (startsNewBlock || isFinal || force || now - lastThinkingUpdateAt >= 150) {
            lastThinkingUpdateAt = now
            eventBus.emit(
                RuntimeEvent.ReasoningSummary(
                    sessionId = sessionId,
                    summary = summary,
                    blockId = currentThinkingBlockId,
                    startsNewBlock = startsNewBlock,
                    isFinal = isFinal,
                ),
            )
            pushForegroundProgress("Thinking…")
        }
    }

    private suspend fun emitReasoningProgress(sessionId: String, tokens: Int) {
        if (tokens <= 0) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (tokens - lastReasoningTokens >= 25 || now - lastReasoningUpdateAt >= 500) {
            lastReasoningTokens = tokens
            lastReasoningUpdateAt = now
            eventBus.emit(RuntimeEvent.ReasoningProgress(sessionId, tokens))
            pushForegroundProgress("Thinking…")
        }
    }

    private suspend fun emitToolStarted(sessionId: String, block: JSONObject) {
        val id = block.optString("id")
        if (id.isNotBlank() && !seenToolCalls.add(id)) return
        val name = block.optString("name", "Tool")
        if (id.isNotBlank()) toolNames[id] = name
        val input = block.optJSONObject("input") ?: JSONObject()
        val detail = when (name) {
            "Bash" -> input.optString("command").ifBlank { input.optString("description") }
            "Write", "Edit", "Read", "NotebookEdit" -> input.optString("file_path").ifBlank { input.optString("notebook_path") }
            "Glob" -> input.optString("pattern")
            "Grep" -> input.optString("pattern").let { pattern ->
                input.optString("path").takeIf(String::isNotBlank)?.let { "$pattern in $it" } ?: pattern
            }
            else -> input.optString("description").ifBlank { "Running $name" }
        }
        eventBus.emit(RuntimeEvent.ToolStarted(sessionId, name, sanitizeForDisplay(detail.ifBlank { "Running $name" })))
        pushForegroundProgress("Running $name · ${detail.replace(Regex("\\s+"), " ").trim().take(80).ifBlank { name }}")
    }

    private fun terminalStatus(line: String): Pair<String, String>? = null

    private suspend fun emitCompletedOnce(sessionId: String) {
        if (finishedSessions.add(sessionId)) {
            eventBus.emit(RuntimeEvent.SessionCompleted(sessionId))
            // Post the completion notification immediately. Waiting for process
            // teardown is unsafe: the PRoot/Node wrapper can hang after the answer
            // is already done, which would freeze the notification on its last step.
            finishForegroundRuntime(
                completed = true,
                projectName = activeProjectSlug ?: "your project",
                detail = "Claude Code finished the task.",
            )
        }
    }

    private suspend fun emitUsageOnce(sessionId: String, usage: io.github.ninock.pocketcode.model.TokenUsage?) {
        if (usage == null || usage.isEmpty || !usageReported.add(sessionId)) return
        eventBus.emit(RuntimeEvent.UsageReported(sessionId, usage))
    }

    private suspend fun emitFailureOnce(sessionId: String, reason: String) {
        if (finishedSessions.add(sessionId)) {
            eventBus.emit(RuntimeEvent.SessionFailed(sessionId, reason))
            if (userStopRequested) {
                cancelForegroundRuntime()
            } else {
                finishForegroundRuntime(
                    completed = false,
                    projectName = activeProjectSlug ?: "your project",
                    detail = reason,
                )
            }
        }
    }

    private fun sanitizeForDisplay(value: String): String {
        return value
            .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "sk-••••")
            .replace(Regex("(?i)(authorization|api[_-]?key)\\s*[:=]\\s*[^\\s,}]+"), "${'$'}1: ••••")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(600)
    }

    /** Project instructions and recent history, followed by [currentPrompt] when there is one. */
    private fun buildContextPrompt(currentPrompt: String?, history: List<ChatMessage>, guestWorkspacePath: String, projectKind: ProjectKind): String {
        // Filter out the current prompt (last user message), system greeting, and any error messages
        val priorMessages = history
            .filter { msg ->
                (msg.fromUser || !msg.text.startsWith("Hi! Tell me")) &&
                !msg.text.startsWith("Failed to") &&
                !msg.text.startsWith("Error:") &&
                !msg.text.contains("API Error")
            }
            .dropLast(1) // Drop the current prompt which was just added
            .recentWithinCharacterBudget(MAX_CONVERSATION_HISTORY_CHARACTERS)

        val sb = StringBuilder()
        sb.appendLine("<project_workspace>")
        if (projectKind == ProjectKind.QUICK_PROJECT) {
            sb.appendLine("This is a lightweight project workspace at $guestWorkspacePath.")
            sb.appendLine("Respond conversationally, and use terminal or file tools whenever they are useful for the request.")
            sb.appendLine("Keep every file and command inside this project workspace.")
        } else {
            sb.appendLine("The current working directory $guestWorkspacePath is the project root.")
            sb.appendLine("Create and edit project files directly in this directory. Do not create another outer project folder unless the user explicitly asks for one.")
            sb.appendLine("When giving commands to the user, make them runnable from this project root.")
        }
        sb.appendLine("If this is an Android project, the phone already provides JDK 17, Android SDK 36, ARM64 Build Tools 35.0.0, Gradle 8.14.3, and an offline Maven repository.")
        sb.appendLine("For newly created Android projects, use AGP 8.11.0, Kotlin 1.9.22, compileSdk 36, and Java 17 so the preinstalled offline toolchain can build immediately.")
        sb.appendLine("The bundled Maven cache handles the base toolchain; Gradle may download project-specific libraries normally. Set android.useAndroidX=true for AndroidX or Compose projects.")
        sb.appendLine("PocketCode globally configures Gradle to use the SDK's ARM64 aapt2. Do not use the x86_64 Maven aapt2, investigate its architecture, or add android.aapt2FromMavenOverride to the project.")
        sb.appendLine("Use the installed `gradle` command for Android builds; do not ask the user to install Android Studio, an SDK, Gradle, ADB, or Termux.")
        sb.appendLine("For local servers, give a clear start command and never use a kill command that searches its own command text with pgrep, because it can terminate the terminal itself.")
        sb.appendLine("</project_workspace>")
        sb.appendLine()
        if (priorMessages.isEmpty()) {
            currentPrompt?.let(sb::appendLine)
            return sb.toString()
        }
        sb.appendLine("<conversation_history>")
        sb.appendLine("The following is our prior conversation in this project. Continue naturally from where we left off.")
        sb.appendLine()
        for (msg in priorMessages) {
            val role = if (msg.fromUser) "User" else "Assistant"
            sb.appendLine("$role: ${msg.text}")
            if (msg.attachments.isNotEmpty()) {
                sb.appendLine("Attached files:")
                msg.attachments.forEach { attachment ->
                    if (attachment.isGitDiff) sb.appendLine("- Git diff of $guestWorkspacePath/${attachment.relativePath} (was shown inline)")
                    else sb.appendLine("- ${attachment.displayName}: $guestWorkspacePath/${attachment.relativePath} (${attachment.mimeType})")
                }
            }
            sb.appendLine()
        }
        sb.appendLine("</conversation_history>")
        if (currentPrompt == null) return sb.toString()
        sb.appendLine()
        sb.appendLine("Now, respond to this new message from the user:")
        sb.appendLine(currentPrompt)
        return sb.toString()
    }

    /**
     * Skills, commands, agents, MCP servers and tools for the command palette:
     * what the last run in this project reported, or the `.claude` folders on
     * disk before Claude Code has run there.
     */
    suspend fun loadCapabilities(projectId: String): ClaudeCapabilities = withContext(Dispatchers.IO) {
        readCapabilities(projectId)
    }

    private fun readCapabilities(projectId: String): ClaudeCapabilities {
        val onDisk = runCatching {
            scanClaudeConfigDirs(
                listOf(
                    File(ensureWorkspace(projectId), ".claude"),
                    File(context.filesDir, "runtime/ubuntu/root/.claude"),
                ),
            )
        }.getOrDefault(ClaudeCapabilities())
        val reported = capabilitiesFile(projectId).takeIf(File::isFile)
            ?.let { runCatching { claudeCapabilitiesFromJson(it.readText()) }.getOrNull() }
        return reported?.withDescriptionsFrom(onDisk) ?: onDisk
    }

    private suspend fun rememberCapabilities(json: JSONObject) {
        val projectId = activeProjectId ?: return
        runCatching {
            val file = capabilitiesFile(projectId)
            file.parentFile?.mkdirs()
            file.writeText(parseClaudeInit(json).toJson())
        }.onFailure { Log.w("ClaudeBridge", "Could not save Claude Code capabilities", it) }
        capabilityBus.emit(projectId)
    }

    private fun capabilitiesFile(projectId: String) =
        File(context.filesDir, "claude-capabilities/${projectId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun ensureWorkspace(projectId: String): File {
        val base = File(context.filesDir, "workspaces/$projectId").apply { mkdirs() }.canonicalFile
        val rootPath = projectRoots[projectId].orEmpty()
        if (rootPath.isBlank()) return base
        val selected = File(base, rootPath).canonicalFile
        require(selected.toPath().startsWith(base.toPath())) { "Unsafe project root" }
        return selected.apply { mkdirs() }
    }

    fun configureProjectRoot(projectId: String, rootPath: String) {
        val normalized = rootPath.trim().trim('/')
        require(normalized.isBlank() || (!normalized.contains("..") && !normalized.startsWith('/'))) {
            "Unsafe project root"
        }
        val previous = projectRoots.put(projectId, normalized).orEmpty()
        if (previous != normalized) checkpointDir(projectId).deleteRecursively()
    }

    private fun checkpointDir(projectId: String) = File(context.filesDir, "checkpoints/$projectId/latest")

    private fun createCheckpoint(projectId: String, workspace: File) {
        val checkpoint = checkpointDir(projectId)
        // Keep the original baseline until every pending file is accepted or undone.
        if (File(checkpoint, "project").isDirectory && File(checkpoint, "changes.json").isFile) return
        checkpoint.deleteRecursively()
        val backup = File(checkpoint, "project").apply { mkdirs() }
        val workspacePath = workspace.canonicalFile.toPath()
        workspace.walkTopDown()
            .onEnter { directory ->
                val relative = if (directory == workspace) "" else directory.relativeTo(workspace).invariantSeparatorsPath
                directory == workspace || (!isInternalRuntimePath(relative) &&
                    !java.nio.file.Files.isSymbolicLink(directory.toPath()) &&
                        runCatching { directory.canonicalFile.toPath().startsWith(workspacePath) }.getOrDefault(false)
                    )
            }
            .filter {
                it.isFile &&
                    !isInternalRuntimePath(it.relativeTo(workspace).invariantSeparatorsPath) &&
                    !java.nio.file.Files.isSymbolicLink(it.toPath())
            }
            .forEach { source ->
                val relative = source.relativeTo(workspace).invariantSeparatorsPath
                val destination = safeWorkspaceFile(backup, relative)
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
            }
    }

    private fun saveChangedPaths(projectId: String, paths: List<String>) {
        val manifest = File(checkpointDir(projectId), "changes.json")
        manifest.parentFile?.mkdirs()
        val merged = (readChangedPaths(projectId) + paths)
            .filterNot(::isInternalRuntimePath)
            .distinct()
            .sorted()
        manifest.writeText(JSONArray(merged).toString())
    }

    private fun readChangedPaths(projectId: String): List<String> {
        val manifest = File(checkpointDir(projectId), "changes.json")
        if (!manifest.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(manifest.readText())
            (0 until array.length()).map(array::getString)
        }.getOrDefault(emptyList())
    }

    private fun removeChangedPath(projectId: String, path: String) {
        val remaining = readChangedPaths(projectId).filterNot { it == path }
        if (remaining.isEmpty()) {
            checkpointDir(projectId).deleteRecursively()
        } else {
            File(checkpointDir(projectId), "changes.json").writeText(JSONArray(remaining).toString())
        }
    }

    private fun buildChangeDetails(projectId: String, workspace: File, paths: List<String>): List<ChangeItem> {
        val backup = File(checkpointDir(projectId), "project")
        return paths.map { path ->
            val beforeFile = safeWorkspaceFile(backup, path).takeIf(File::isFile)
            val afterFile = safeWorkspaceFile(workspace, path).takeIf(File::isFile)
            if (listOfNotNull(beforeFile, afterFile).any { it.length() > MAX_DIFF_FILE_BYTES }) {
                return@map ChangeItem(
                    path = path,
                    additions = 0,
                    deletions = 0,
                    diffLines = listOf(
                        DiffLine(
                            DiffLineType.INFO,
                            "File is too large to preview safely. Undo and Keep still work.",
                        ),
                    ),
                )
            }
            val before = beforeFile?.readBytes() ?: ByteArray(0)
            val after = afterFile?.readBytes() ?: ByteArray(0)
            val binary = before.any { it == 0.toByte() } || after.any { it == 0.toByte() }
            val (additions, deletions) = lineChanges(before, after)
            ChangeItem(
                path = path,
                additions = additions,
                deletions = deletions,
                diffLines = buildDiffLines(before, after),
                binary = binary,
            )
        }
    }

    private fun buildDiffLines(beforeBytes: ByteArray, afterBytes: ByteArray): List<DiffLine> {
        if (beforeBytes.any { it == 0.toByte() } || afterBytes.any { it == 0.toByte() }) {
            return listOf(DiffLine(DiffLineType.INFO, "Binary file changed"))
        }
        val before = textLines(beforeBytes)
        val after = textLines(afterBytes)
        if (before.size > MAX_RENDERED_DIFF_LINES || after.size > MAX_RENDERED_DIFF_LINES) {
            return listOf(
                DiffLine(
                    DiffLineType.INFO,
                    "Diff is too large to display (${before.size} → ${after.size} lines). Undo and Keep still work.",
                ),
            )
        }

        val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
        for (oldIndex in before.lastIndex downTo 0) {
            for (newIndex in after.lastIndex downTo 0) {
                lcs[oldIndex][newIndex] = if (before[oldIndex] == after[newIndex]) {
                    lcs[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < before.size || newIndex < after.size) {
            when {
                oldIndex < before.size && newIndex < after.size && before[oldIndex] == after[newIndex] -> {
                    result += DiffLine(DiffLineType.CONTEXT, before[oldIndex], oldIndex + 1, newIndex + 1)
                    oldIndex++
                    newIndex++
                }
                newIndex < after.size && (oldIndex == before.size || lcs[oldIndex][newIndex + 1] >= lcs[oldIndex + 1][newIndex]) -> {
                    result += DiffLine(DiffLineType.ADDITION, after[newIndex], null, newIndex + 1)
                    newIndex++
                }
                oldIndex < before.size -> {
                    result += DiffLine(DiffLineType.DELETION, before[oldIndex], oldIndex + 1, null)
                    oldIndex++
                }
            }
        }
        return collapseUnchangedLines(result)
    }

    private fun collapseUnchangedLines(lines: List<DiffLine>): List<DiffLine> {
        val changedIndexes = lines.indices.filter { lines[it].type != DiffLineType.CONTEXT }
        if (changedIndexes.isEmpty()) return lines
        val visible = BooleanArray(lines.size)
        changedIndexes.forEach { changed ->
            for (index in maxOf(0, changed - DIFF_CONTEXT_LINES)..minOf(lines.lastIndex, changed + DIFF_CONTEXT_LINES)) {
                visible[index] = true
            }
        }
        val result = mutableListOf<DiffLine>()
        var index = 0
        while (index < lines.size) {
            if (visible[index]) {
                result += lines[index++]
            } else {
                val start = index
                while (index < lines.size && !visible[index]) index++
                result += DiffLine(DiffLineType.INFO, "… ${index - start} unchanged lines …")
            }
        }
        return result
    }

    private fun lineChanges(beforeBytes: ByteArray, afterBytes: ByteArray): Pair<Int, Int> {
        if (beforeBytes.any { it == 0.toByte() } || afterBytes.any { it == 0.toByte() }) {
            return (if (afterBytes.isNotEmpty()) 1 else 0) to (if (beforeBytes.isNotEmpty()) 1 else 0)
        }
        val before = textLines(beforeBytes)
        val after = textLines(afterBytes)
        if (before.size > MAX_DIFF_LINES || after.size > MAX_DIFF_LINES) {
            return maxOf(0, after.size - before.size) to maxOf(0, before.size - after.size)
        }
        var previous = IntArray(after.size + 1)
        before.forEach { oldLine ->
            val current = IntArray(after.size + 1)
            after.forEachIndexed { index, newLine ->
                current[index + 1] = if (oldLine == newLine) {
                    previous[index] + 1
                } else {
                    maxOf(previous[index + 1], current[index])
                }
            }
            previous = current
        }
        val common = previous[after.size]
        return (after.size - common) to (before.size - common)
    }

    private fun textLines(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val lines = bytes.decodeToString().split('\n')
        return if (lines.lastOrNull().isNullOrEmpty()) lines.dropLast(1) else lines
    }

    private fun safeWorkspaceFile(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/')) { "Unsafe workspace path" }
        val file = File(root, relative)
        val rootPath = root.canonicalFile.toPath()
        val parentPath = (file.parentFile ?: root).canonicalFile.toPath()
        require(parentPath.startsWith(rootPath)) { "Workspace path escapes project" }
        return file
    }

    private fun snapshot(root: File): Map<String, String> = root.walkTopDown()
        .onEnter { directory ->
            directory == root || !isInternalRuntimePath(directory.relativeTo(root).invariantSeparatorsPath)
        }
        .filter { it.isFile && !isInternalRuntimePath(it.relativeTo(root).invariantSeparatorsPath) }
        .associate { it.relativeTo(root).path to digest(it) }

    private fun changedFiles(root: File, before: Map<String, String>): List<String> {
        val after = snapshot(root)
        return (before.keys + after.keys).distinct().filter { before[it] != after[it] }.sorted()
    }

    private fun isInternalRuntimePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized == ".claude.json" || IGNORED_DIRECTORY_NAMES.any { directory ->
            normalized == directory || normalized.startsWith("$directory/") || normalized.contains("/$directory/")
        }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun classifyRisk(tool: String, command: String?): RiskLevel {
        val preview = "${tool.lowercase()} ${command.orEmpty().lowercase()}"
        return when {
            listOf("rm -rf", "git push", "git reset", "sudo", "curl ").any(preview::contains) -> RiskLevel.HIGH
            tool in listOf("Write", "Edit", "NotebookEdit", "Bash") -> RiskLevel.REVIEW
            else -> RiskLevel.SAFE
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is ProviderSessionException -> message
            message.contains("user not found", true) -> "User not found. Check the API key and provider account."
            message.contains("checksum", true) -> "Runtime verification failed. Nothing unverified was executed."
            message.contains("HTTP 401", true) || message.contains("authentication", true) -> "The provider rejected the saved API key."
            message.isBlank() -> "The real Claude Code runtime could not start."
            else -> message.take(500)
        }
    }

    /**
     * Mirrors what Claude Code is doing right now into the foreground-service
     * notification, so the notification panel shows the real task progress.
     * Throttled because each update is a service round-trip.
     */
    private fun pushForegroundProgress(detailRaw: String) {
        if (activeSessionId == null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastForegroundProgressAt < FOREGROUND_PROGRESS_MIN_INTERVAL_MS) return
        lastForegroundProgressAt = now
        val detail = detailRaw.replace(Regex("\\s+"), " ").trim().take(110)
        val elapsedMs = taskStartedAtElapsedRealtime.takeIf { it > 0 }?.let { now - it } ?: 0L
        val text = if (elapsedMs > 0L) "$detail · ${formatElapsedShort(elapsedMs)}" else detail
        runCatching {
            context.startService(
                android.content.Intent(context, RuntimeExecutionService::class.java)
                    .setAction(RuntimeExecutionService.ACTION_PROGRESS)
                    .putExtra(RuntimeExecutionService.EXTRA_PROJECT_NAME, activeProjectSlug)
                    .putExtra(RuntimeExecutionService.EXTRA_DETAIL, text),
            )
        }
    }

    private fun formatElapsedShort(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /**
     * Destroys the CLI process, escalating to a force kill if the PRoot/Node
     * wrapper ignores the graceful signal. Without this, a hung wrapper would
     * block session cleanup forever after the answer was already delivered.
     */
    private fun terminateActiveProcessGracefully() {
        // Stream-json input keeps Claude Code waiting for another message; end it.
        closeStdin()
        val running = activeProcess ?: return
        Thread {
            runCatching {
                running.destroy()
                Thread.sleep(3_000)
                if (running.isAlive) running.destroyForcibly()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startForegroundRuntime(projectName: String) {
        ContextCompat.startForegroundService(
            context,
            android.content.Intent(context, RuntimeExecutionService::class.java)
                .setAction(RuntimeExecutionService.ACTION_START)
                .putExtra(RuntimeExecutionService.EXTRA_PROJECT_NAME, projectName),
        )
    }

    private fun finishForegroundRuntime(completed: Boolean, projectName: String, detail: String) {
        // The first completion/failure post wins; later cleanup must not duplicate it.
        if (foregroundResultPosted) return
        foregroundResultPosted = true
        runCatching {
            context.startService(
                android.content.Intent(context, RuntimeExecutionService::class.java)
                    .setAction(
                        if (completed) RuntimeExecutionService.ACTION_COMPLETE
                        else RuntimeExecutionService.ACTION_FAILED,
                    )
                    .putExtra(RuntimeExecutionService.EXTRA_PROJECT_NAME, projectName)
                    .putExtra(RuntimeExecutionService.EXTRA_DETAIL, detail),
            )
        }.onFailure { error ->
            Log.w("ClaudeBridge", "Could not post task result notification", error)
            context.stopService(android.content.Intent(context, RuntimeExecutionService::class.java))
        }
    }

    private fun cancelForegroundRuntime() {
        if (foregroundResultPosted) return
        foregroundResultPosted = true
        runCatching {
            context.startService(
                android.content.Intent(context, RuntimeExecutionService::class.java)
                    .setAction(RuntimeExecutionService.ACTION_CANCELLED),
            )
        }.onFailure {
            context.stopService(android.content.Intent(context, RuntimeExecutionService::class.java))
        }
    }

    private data class PendingPermission(val request: ToolRequest, val response: File)
    private data class PendingControl(val sessionId: String, val toolName: String, val input: JSONObject)
    private class ProviderSessionException(message: String) : IllegalStateException(message)

    companion object {
        private const val MAX_DIFF_LINES = 2_000
        private const val MAX_RENDERED_DIFF_LINES = 600
        private const val MAX_DIFF_FILE_BYTES = 1_000_000L
        private const val DIFF_CONTEXT_LINES = 3
        private const val FOREGROUND_PROGRESS_MIN_INTERVAL_MS = 750L
        private const val MAX_CONVERSATION_HISTORY_CHARACTERS = 160_000
        private val IGNORED_DIRECTORY_NAMES = setOf(
            ".claude",
            ".git",
            ".gradle",
            ".idea",
            ".next",
            ".dart_tool",
            ".venv",
            "build",
            "dist",
            "node_modules",
            "target",
            "venv",
        )
    }
}
