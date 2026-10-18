package io.github.ninock.pocketcode.runtime

import android.content.Context
import android.content.Intent
import android.system.Os
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.ninock.pocketcode.model.AgentKind
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class ClaudeAuthStatus { IDLE, STARTING, AWAITING_BROWSER, COMPLETING, SUCCESS, ERROR }

data class ClaudeAuthState(
    val status: ClaudeAuthStatus = ClaudeAuthStatus.IDLE,
    /** Sign-in URL that redirects back to Claude Code's localhost listener; no code to paste. */
    val authorizationUrl: String? = null,
    /** Fallback sign-in URL that ends on a page showing a one-time code to paste back. */
    val manualUrl: String? = null,
    /** True once Claude Code is showing its "Paste code here" prompt. */
    val acceptsCode: Boolean = false,
    val message: String? = null,
)

/**
 * Drives the official `claude setup-token` flow on the device.
 *
 * Claude Code starts its own PKCE OAuth flow with a callback listener on
 * 127.0.0.1 and hands the browser URL to `$BROWSER`. A tiny helper script
 * captures that URL so the app can open it in the Android browser; after the
 * user signs in, claude.ai redirects to the on-device listener and Claude Code
 * prints its long-lived subscription token, which is handed to [onToken] for
 * Keystore-encrypted storage. The manual "paste the code" path of the same
 * official flow remains available when the localhost redirect cannot complete.
 * PocketCode never builds its own OAuth request.
 */
class ClaudeAuthController(
    private val context: Context,
    private val onToken: (String) -> Unit,
) {
    private val installer = RuntimeInstaller(context)
    private val mutableState = MutableStateFlow(ClaudeAuthState())
    val state: StateFlow<ClaudeAuthState> = mutableState.asStateFlow()
    private val authOutput = File(context.cacheDir, "claude-auth-output.log")
    private val bridgeDir = File(context.filesDir, "runtime-bridge")
    private val browserUrlFile = File(bridgeDir, BROWSER_URL_FILE)
    @Volatile private var process: Process? = null
    @Volatile private var cancelled = false

    init {
        // The terminal output can contain the token; never let it outlive a crash.
        authOutput.delete()
        browserUrlFile.delete()
    }

    suspend fun beginLogin() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext
        if (!installer.isAgentInstalled(AgentKind.CLAUDE_CODE)) {
            mutableState.value = ClaudeAuthState(ClaudeAuthStatus.ERROR, message = "Install Claude Code before signing in.")
            return@withContext
        }
        cancelled = false
        mutableState.value = ClaudeAuthState(ClaudeAuthStatus.STARTING, message = "Starting Claude sign-in…")
        authOutput.delete()
        browserUrlFile.delete()
        val runtime = installer.installedRuntime()
        installBrowserHelper(runtime.rootfs)
        startForegroundKeepAlive()
        val workspace = File(context.filesDir, "workspaces/claude-auth").apply { mkdirs() }
        val running = installer.process(
            runtime.proot,
            runtime.rootfs,
            workspace,
            mapOf(
                // Claude Code runs `$BROWSER "<url>"` for the localhost-redirect URL.
                "BROWSER" to BROWSER_HELPER_GUEST_PATH,
                "TERM" to "xterm-256color",
                "NO_COLOR" to "1",
                "DISABLE_AUTOUPDATER" to "1",
            ),
            listOf(RuntimeInstaller.CLAUDE_GUEST_PATH, "setup-token"),
            guestWorkspacePath = "/workspace/claude-auth",
            emulateHardLinks = false,
            outputFile = authOutput,
            pseudoTerminal = true,
            ptyRows = PTY_ROWS,
            // Wide enough that the ~450 character sign-in URL and the token
            // render on single rows instead of being wrapped.
            ptyColumns = PTY_COLUMNS,
        )
        process = running
        val native = running as? NativeSpawnProcess ?: error("Unsupported Claude authentication process")
        var offset = 0L
        // Claude Code redraws only changed cells, so rebuild the screen instead
        // of stripping escape codes from the byte stream.
        val screen = VtScreen(PTY_ROWS, PTY_COLUMNS)
        var exited = false
        var exitCode: Int? = null
        val startedAt = System.currentTimeMillis()
        try {
            while (true) {
                check(System.currentTimeMillis() - startedAt < LOGIN_TIMEOUT_MS) {
                    "Claude sign-in timed out. Start a new sign-in attempt."
                }
                capturedBrowserUrl()?.let { url ->
                    if (mutableState.value.authorizationUrl == null) {
                        mutableState.value = mutableState.value.copy(
                            status = ClaudeAuthStatus.AWAITING_BROWSER,
                            authorizationUrl = url,
                            message = "Sign in to Claude in your browser, then come back to PocketCode.",
                        )
                    }
                }
                val available = native.outputFile.length() - offset
                if (available <= 0) {
                    if (exited) {
                        // All output has been read; the final frame is complete.
                        readClaudeSetupScreen(screen.text(), outputComplete = true).token?.let(::succeed)
                        break
                    }
                    if (!running.isAlive) {
                        // Claude Code exits right after showing the token. waitFor()
                        // also lets the PTY pump flush that final frame to the file,
                        // so read once more before concluding.
                        exitCode = runCatching { running.waitFor() }.getOrNull()
                        exited = true
                        continue
                    }
                    delay(100)
                    continue
                }
                val bytes = ByteArray(minOf(available, 16L * 1024).toInt())
                val count = RandomAccessFile(native.outputFile, "r").use { file ->
                    file.seek(offset)
                    file.read(bytes)
                }
                if (count <= 0) continue
                offset += count
                screen.feed(bytes, count)
                val read = readClaudeSetupScreen(screen.text(), outputComplete = false)
                if (read.token != null) {
                    succeed(read.token)
                    break
                }
                val current = mutableState.value
                val manualUrl = current.manualUrl ?: read.manualUrl
                val acceptsCode = current.acceptsCode || read.acceptsCode
                if (manualUrl != current.manualUrl || acceptsCode != current.acceptsCode) {
                    mutableState.value = current.copy(
                        status = if (current.status == ClaudeAuthStatus.STARTING) ClaudeAuthStatus.AWAITING_BROWSER else current.status,
                        manualUrl = manualUrl,
                        acceptsCode = acceptsCode,
                        message = current.message.takeUnless { current.status == ClaudeAuthStatus.STARTING }
                            ?: "Sign in to Claude in your browser.",
                    )
                }
                read.error?.let { error(it) }
            }
            if (mutableState.value.status != ClaudeAuthStatus.SUCCESS && !cancelled) {
                val lastScreen = screen.lines().map(String::trim).filter(String::isNotEmpty).takeLast(3)
                    .joinToString(" / ") { redactClaudeSecrets(it) }.take(160)
                Log.w(LOG_TAG, "setup-token ended without a readable token (exit $exitCode): $lastScreen")
                error(
                    "Claude sign-in ended without a readable token (exit $exitCode). Start a new sign-in attempt." +
                        if (lastScreen.isNotBlank()) "\nLast screen: $lastScreen" else "",
                )
            }
        } catch (error: Throwable) {
            if (mutableState.value.status != ClaudeAuthStatus.SUCCESS && !cancelled) {
                mutableState.value = ClaudeAuthState(
                    ClaudeAuthStatus.ERROR,
                    message = error.message?.take(400) ?: "Claude sign-in failed",
                )
            }
        } finally {
            if (running.isAlive) running.destroy()
            runCatching { running.outputStream.close() }
            // Contains the token and one-time code material; never keep it.
            native.outputFile.delete()
            browserUrlFile.delete()
            process = null
            stopForegroundKeepAlive(connected = mutableState.value.status == ClaudeAuthStatus.SUCCESS)
        }
    }

    private fun succeed(token: String) {
        onToken(token)
        mutableState.value = ClaudeAuthState(ClaudeAuthStatus.SUCCESS, message = "Claude subscription connected")
    }

    /** Sends the one-time code from the manual sign-in page to the waiting CLI. */
    suspend fun submitCode(code: String) = withContext(Dispatchers.IO) {
        val value = code.trim().replace(Regex("\\s+"), "")
        require(value.isNotBlank()) { "Paste the code shown after signing in to Claude" }
        require('#' in value) { "That doesn't look like a complete Claude code. Copy the whole code from the page." }
        val running = process ?: error("Start Claude sign-in again")
        check(running.isAlive) { "The sign-in session expired. Start again." }
        check(mutableState.value.acceptsCode) { "Claude Code is not ready for a code yet. Try again in a moment." }
        // Claude Code's Ink input treats one write as a single keypress batch;
        // send the text first and the Enter key (CR in raw mode) separately.
        running.outputStream.write(value.toByteArray())
        running.outputStream.flush()
        delay(250)
        running.outputStream.write("\r".toByteArray())
        running.outputStream.flush()
        mutableState.value = mutableState.value.copy(
            status = ClaudeAuthStatus.COMPLETING,
            message = "Creating your Claude subscription token…",
        )
    }

    fun cancel() {
        cancelled = true
        process?.destroy()
        mutableState.value = ClaudeAuthState()
    }

    private fun capturedBrowserUrl(): String? {
        if (!browserUrlFile.isFile) return null
        val url = runCatching { browserUrlFile.readText().trim() }.getOrNull() ?: return null
        // Only an https Claude authorization URL may be opened from this file.
        return url.takeIf { it.startsWith("https://") && "/oauth/authorize?" in it && "code_challenge=" in it }
    }

    private fun installBrowserHelper(rootfs: File) {
        bridgeDir.mkdirs()
        val helper = File(rootfs, BROWSER_HELPER_GUEST_PATH.removePrefix("/"))
        helper.parentFile?.mkdirs()
        helper.writeText(
            """#!/bin/sh
# PocketCode: receives Claude Code's sign-in URL instead of launching a
# desktop browser. The app opens it in the Android browser.
url="${'$'}1"
case "${'$'}url" in
  \"*\") url="${'$'}{url#\"}"; url="${'$'}{url%\"}" ;;
esac
umask 077
printf '%s\n' "${'$'}url" > /pocket-bridge/$BROWSER_URL_FILE.tmp && mv -f /pocket-bridge/$BROWSER_URL_FILE.tmp /pocket-bridge/$BROWSER_URL_FILE
exit 0
""",
        )
        Os.chmod(helper.absolutePath, 0b111101101)
    }

    /**
     * The callback listener runs inside this app while the user is in the
     * browser. A foreground service keeps Android from freezing the process
     * before claude.ai redirects back to it.
     */
    private fun startForegroundKeepAlive() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RuntimeExecutionService::class.java)
                    .setAction(RuntimeExecutionService.ACTION_START)
                    .putExtra(RuntimeExecutionService.EXTRA_TITLE, "Connecting your Claude subscription")
                    .putExtra(RuntimeExecutionService.EXTRA_DETAIL, "Waiting for you to finish signing in to Claude…")
                    .putExtra(RuntimeExecutionService.EXTRA_CAN_STOP, false),
            )
        }
    }

    private fun stopForegroundKeepAlive(connected: Boolean) {
        runCatching {
            val intent = Intent(context, RuntimeExecutionService::class.java)
            if (connected) {
                // The user is usually still in the browser; tell them it's done.
                intent.setAction(RuntimeExecutionService.ACTION_COMPLETE)
                    .putExtra(RuntimeExecutionService.EXTRA_TITLE, "Claude subscription connected")
                    .putExtra(RuntimeExecutionService.EXTRA_DETAIL, "Return to PocketCode to start coding.")
            } else {
                intent.setAction(RuntimeExecutionService.ACTION_CANCELLED)
            }
            context.startService(intent)
        }
    }

    private companion object {
        const val BROWSER_HELPER_GUEST_PATH = "/opt/pocket/claude-auth-browser.sh"
        const val BROWSER_URL_FILE = "claude-auth-url"
        const val LOGIN_TIMEOUT_MS = 15 * 60 * 1_000L
        const val PTY_ROWS = 50
        const val PTY_COLUMNS = 1000
        const val LOG_TAG = "ClaudeAuth"
    }
}

// Matched on the reconstructed screen, so a match must end where the text run ends.
private val CLAUDE_TOKEN = Regex("sk-ant-oat01-[A-Za-z0-9_-]{20,}(?![A-Za-z0-9_-])")
private val CLAUDE_AUTH_URL = Regex(
    """https://[A-Za-z0-9.-]+/[^\s"'<>]*oauth/authorize\?[^\s"'<>]*?[?&]state=[A-Za-z0-9_-]{43}(?:&[^\s"'<>]*)?(?![^\s"'<>])""",
)
private val CLAUDE_AUTH_URL_WRAPPED = Regex(
    """https://[A-Za-z0-9.-]+/[^\s"'<>]*oauth/authorize\?[^\s"'<>]*?[?&]state=[A-Za-z0-9_-]{43}""",
)

internal data class ClaudeSetupScreenRead(
    val token: String? = null,
    val manualUrl: String? = null,
    val acceptsCode: Boolean = false,
    val error: String? = null,
)

/**
 * Interprets the reconstructed `claude setup-token` screen. A frame can arrive
 * across several reads, so a value is only trusted once the text Claude Code
 * draws after it is also on screen (or the process has exited and every byte
 * has been read).
 */
internal fun readClaudeSetupScreen(text: String, outputComplete: Boolean): ClaudeSetupScreenRead {
    val tokenComplete = outputComplete || text.contains("Store this token", ignoreCase = true)
    // The manual URL and the code prompt belong to the same frame; the prompt is drawn last.
    val promptVisible = text.contains("Paste code here", ignoreCase = true)
    return ClaudeSetupScreenRead(
        token = if (tokenComplete) extractClaudeSetupToken(text) else null,
        manualUrl = if (promptVisible || outputComplete) extractClaudeManualAuthUrl(text) else null,
        acceptsCode = promptVisible,
        error = extractClaudeAuthError(text),
    )
}

/** Extracts the token printed by `claude setup-token` after a successful sign-in. */
internal fun extractClaudeSetupToken(output: String): String? {
    if (!output.contains("token", ignoreCase = true)) return null
    return CLAUDE_TOKEN.findAll(output).map { it.value }.lastOrNull()
}

/** Extracts the manual (code-paste) authorization URL that Claude Code displays. */
internal fun extractClaudeManualAuthUrl(output: String): String? {
    CLAUDE_AUTH_URL.findAll(output)
        .map { it.value }
        .lastOrNull { "code_challenge=" in it && "state=" in it && "redirect_uri=http%3A%2F%2Flocalhost" !in it }
        ?.let { return it }
    // Fallback if the terminal still wrapped the URL across rows: the PKCE state
    // is 32 random bytes in base64url (43 characters) and the last parameter.
    val compact = output.replace(Regex("[\\r\\n\\t ]+"), "")
    return CLAUDE_AUTH_URL_WRAPPED.findAll(compact)
        .map { it.value }
        .lastOrNull { "code_challenge=" in it && "redirect_uri=http%3A%2F%2Flocalhost" !in it }
}

internal fun extractClaudeAuthError(output: String): String? {
    val line = output.lineSequence().map(String::trim).lastOrNull { it.startsWith("OAuth error:", ignoreCase = true) }
        ?: return null
    val detail = line.substringAfter(':').trim()
    return if (detail.contains("Invalid code", ignoreCase = true)) {
        "Claude rejected that code. Make sure you copied the whole code, then start sign-in again."
    } else {
        "Claude sign-in failed: ${detail.ifBlank { "unknown error" }}"
    }
}

private val CLAUDE_SECRET = Regex("sk-ant-[A-Za-z0-9_-]+")
internal fun redactClaudeSecrets(text: String): String = text.replace(CLAUDE_SECRET, "sk-ant-…")
