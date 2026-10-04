package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.ui.sanitizeTerminalOutput
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class PreviewServerStatus { IDLE, STARTING, RUNNING, FAILED, STOPPED }

data class PreviewServerState(
    val projectId: String? = null,
    val status: PreviewServerStatus = PreviewServerStatus.IDLE,
    val command: String? = null,
    val url: String? = null,
    /** Recent server output with terminal escape codes removed. */
    val log: String = "",
    val exitCode: Int? = null,
) {
    val active: Boolean get() = status == PreviewServerStatus.STARTING || status == PreviewServerStatus.RUNNING
}

/**
 * Runs a project's web dev server as an app-owned Linux process, separate from the
 * project terminal and from agent turns, so the preview keeps running after the
 * agent finishes. One server runs at a time.
 */
class PreviewServerManager(private val context: Context, private val scope: CoroutineScope) {
    private val installer = RuntimeInstaller(context)
    private val _state = MutableStateFlow(PreviewServerState())
    val state: StateFlow<PreviewServerState> = _state
    @Volatile private var process: Process? = null
    private var watcher: Job? = null

    /** Starts [command] in [guestRoot], replacing any running preview. */
    @Synchronized
    fun start(projectId: String, workspace: File, guestRoot: String, command: String) {
        stopProcess()
        if (!installer.isInstalled()) {
            _state.value = PreviewServerState(projectId, PreviewServerStatus.FAILED, command, log = "Linux environment is not ready yet.")
            return
        }
        _state.value = PreviewServerState(projectId, PreviewServerStatus.STARTING, command)
        saveCommand(projectId, command)
        val launched = runCatching {
            val installed = installer.installedRuntime()
            installer.process(
                proot = installed.proot,
                rootfs = installed.rootfs,
                workspace = workspace,
                // Dev servers must not try to open a desktop browser.
                environment = mapOf("BROWSER" to "none", "FORCE_COLOR" to "0", "NO_COLOR" to "1"),
                guestCommand = listOf("/usr/bin/bash", "-lc", "cd -- ${shellQuote(guestRoot)} || exit 1\n$command"),
                guestWorkspacePath = guestRoot,
                outputFile = File(context.cacheDir, "preview-server.log").apply { delete() },
            )
        }.getOrElse { error ->
            _state.value = PreviewServerState(projectId, PreviewServerStatus.FAILED, command, log = error.message ?: "Could not start the server.")
            return
        }
        process = launched
        watcher = scope.launch(Dispatchers.IO) { watch(launched, command) }
    }

    @Synchronized
    fun stop() {
        if (!stopProcess()) return
        _state.update { it.copy(status = PreviewServerStatus.STOPPED) }
    }

    /** Waits until the server reports a URL or stops, up to [timeoutMillis]. */
    suspend fun awaitSettled(timeoutMillis: Long): PreviewServerState =
        withTimeoutOrNull(timeoutMillis) { state.first { it.status != PreviewServerStatus.STARTING } } ?: state.value

    private fun stopProcess(): Boolean {
        val running = process ?: return false
        process = null
        watcher?.cancel()
        watcher = null
        // Kills the whole process group, including the server proot started.
        running.destroy()
        Thread {
            Thread.sleep(1_500)
            if (running.isAlive) running.destroyForcibly()
        }.start()
        return true
    }

    private suspend fun watch(process: Process, command: String) {
        val native = process as? NativeSpawnProcess ?: return
        val expectedPort = expectedPreviewPort(command)
        val output = StringBuilder()
        var offset = 0L
        var lastProbe = 0L
        while (scope.isActive && this.process === process && (process.isAlive || native.outputFile.length() > offset)) {
            val available = native.outputFile.length() - offset
            if (available > 0) {
                val bytes = ByteArray(minOf(available, 16L * 1024).toInt())
                val count = RandomAccessFile(native.outputFile, "r").use { file ->
                    file.seek(offset)
                    file.read(bytes)
                }
                if (count > 0) {
                    offset += count
                    output.append(bytes.decodeToString(0, count))
                    if (output.length > MAX_LOG * 2) output.delete(0, output.length - MAX_LOG)
                }
            }
            val log = sanitizeTerminalOutput(output.toString()).takeLast(MAX_LOG)
            val current = _state.value
            var url = current.url
            if (url == null) {
                url = detectPreviewUrl(log)?.let(::reachableUrl)
                val now = System.currentTimeMillis()
                if (url == null && expectedPort != null && now - lastProbe > 1_000) {
                    lastProbe = now
                    url = reachableUrl(expectedPort)
                }
            }
            if (log != current.log || url != current.url) {
                _state.update {
                    it.copy(
                        log = log,
                        url = url,
                        status = if (url != null) PreviewServerStatus.RUNNING else it.status,
                    )
                }
            }
            if (available <= 0) delay(200)
        }
        if (this.process !== process) return
        val exit = runCatching { process.waitFor() }.getOrDefault(-1)
        synchronized(this) {
            if (this.process !== process) return
            this.process = null
            _state.update {
                it.copy(
                    status = if (exit == 0 && it.url != null) PreviewServerStatus.STOPPED else PreviewServerStatus.FAILED,
                    exitCode = exit,
                    log = sanitizeTerminalOutput(output.toString()).takeLast(MAX_LOG),
                )
            }
        }
    }

    /**
     * The command to offer for [workspace]: the one last used for [projectId], else
     * one guessed from the project files, else null.
     */
    fun suggestCommand(projectId: String, workspace: File): String? =
        savedCommand(projectId) ?: detectPreviewCommand(workspace)

    private fun commandFile(projectId: String) = File(context.filesDir, "preview/$projectId.cmd")

    private fun savedCommand(projectId: String): String? =
        commandFile(projectId).takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)

    private fun saveCommand(projectId: String, command: String) {
        runCatching { commandFile(projectId).apply { parentFile?.mkdirs() }.writeText(command) }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private companion object {
        const val MAX_LOG = 12_000
    }
}

/** Guesses how to serve the project in [workspace], or null when nothing looks servable. */
internal fun detectPreviewCommand(workspace: File): String? {
    val packageJson = File(workspace, "package.json")
    if (packageJson.isFile) {
        val scripts = runCatching { JSONObject(packageJson.readText()).optJSONObject("scripts") }.getOrNull()
        val manager = when {
            File(workspace, "pnpm-lock.yaml").isFile -> "pnpm"
            File(workspace, "yarn.lock").isFile -> "yarn"
            File(workspace, "bun.lockb").isFile || File(workspace, "bun.lock").isFile -> "bun"
            else -> "npm"
        }
        val script = listOf("dev", "start", "serve", "preview").firstOrNull { scripts?.has(it) == true }
        if (script != null) {
            val run = if (script == "start" && manager != "bun") "$manager start" else "$manager run $script"
            val install = if (File(workspace, "node_modules").isDirectory) "" else "$manager install && "
            return install + run
        }
    }
    if (File(workspace, "manage.py").isFile) return "python3 manage.py runserver 127.0.0.1:8000"
    val staticRoot = listOf("", "public", "dist", "site", "docs").firstOrNull { File(workspace, "$it/index.html".removePrefix("/")).isFile }
    if (staticRoot != null) {
        val directory = if (staticRoot.isEmpty()) "" else " --directory $staticRoot"
        return "python3 -m http.server 8000 --bind 127.0.0.1$directory"
    }
    return null
}

/** Last loopback URL a server printed, normalized to an address the WebView can load. */
internal fun detectPreviewUrl(output: String): String? {
    val match = Regex("https?://(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1?\\]):(\\d{2,5})(?:/[^\\s]*)?")
        .findAll(output)
        .lastOrNull()
        ?: return null
    val port = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return "http://127.0.0.1:$port/"
}

/** A port named on the command line (`--port 5173`, `-p 3000`, `http.server 8000`, `PORT=…`). */
internal fun expectedPreviewPort(command: String): Int? {
    val patterns = listOf(
        Regex("""(?:--port|-p)[=\s]+(\d{2,5})"""),
        Regex("""\bPORT=(\d{2,5})"""),
        Regex("""http\.server(?:\s+(\d{2,5}))?"""),
        Regex("""runserver(?:\s+[\w.]+:(\d{2,5}))?"""),
    )
    for (pattern in patterns) {
        val match = pattern.find(command) ?: continue
        val port = match.groupValues.getOrNull(1)?.toIntOrNull()
            ?: if ("http.server" in match.value) 8000 else if ("runserver" in match.value) 8000 else null
        if (port != null && port in 1..65535) return port
    }
    return null
}

/** [url] or its IPv6 loopback twin, whichever accepts connections; null when neither does. */
private fun reachableUrl(url: String): String? {
    val port = Regex(":(\\d+)/").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return reachableUrl(port)
}

private fun reachableUrl(port: Int): String? = when {
    canConnect("127.0.0.1", port) -> "http://127.0.0.1:$port/"
    canConnect("::1", port) -> "http://[::1]:$port/"
    else -> null
}

private fun canConnect(host: String, port: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(host, port), 300) }
    true
}.getOrDefault(false)
