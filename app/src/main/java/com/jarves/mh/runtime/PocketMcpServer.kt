package com.jarves.mh.runtime

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Name agents see for PocketCode's built-in tools (`mcp__pocketcode__…`). */
internal const val POCKET_MCP_SERVER_NAME = "pocketcode"

/** What a tool call returns to the agent. */
data class PocketToolResult(val text: String, val isError: Boolean = false)

/** A tool PocketCode offers agents over MCP. [inputSchema] is a JSON Schema object. */
class PocketTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val call: suspend (JSONObject) -> PocketToolResult,
)

/**
 * A minimal MCP server (Streamable HTTP transport, JSON responses only) that lets
 * agents running in the Linux runtime call into the app, for example to start the
 * live preview. It listens on loopback only, and every request must carry the
 * per-launch bearer token, since any app on the phone can reach loopback ports.
 */
class PocketMcpServer(private val tools: List<PocketTool>, private val version: String) {
    private val token = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    @Volatile private var socket: ServerSocket? = null

    val url: String? get() = socket?.let { "http://127.0.0.1:${it.localPort}/mcp" }

    /** Claude Code `--mcp-config` content pointing at this server, or null when it is not running. */
    fun claudeMcpConfig(): String? {
        val endpoint = url ?: return null
        return JSONObject().put(
            "mcpServers",
            JSONObject().put(
                POCKET_MCP_SERVER_NAME,
                JSONObject()
                    .put("type", "http")
                    .put("url", endpoint)
                    .put("headers", JSONObject().put("Authorization", "Bearer $token")),
            ),
        ).toString()
    }

    @Synchronized
    fun start(): PocketMcpServer {
        if (socket != null) return this
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                Thread({ client.use(::serve) }, "pocket-mcp-client").apply { isDaemon = true }.start()
            }
        }, "pocket-mcp").apply { isDaemon = true }.start()
        return this
    }

    @Synchronized
    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun serve(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val output = client.getOutputStream()
        val request = runCatching { readRequest(input) }.getOrNull() ?: return
        val (status, body) = runCatching { route(request) }.getOrElse { error ->
            Log.w("PocketMcp", "Request failed", error)
            500 to jsonRpcError(null, -32603, error.message ?: "Internal error").toString()
        }
        val bytes = body?.toByteArray() ?: ByteArray(0)
        val header = buildString {
            append("HTTP/1.1 $status ${reason(status)}\r\n")
            if (body != null) append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun route(request: HttpRequest): Pair<Int, String?> {
        if (request.path.substringBefore('?') != "/mcp") return 404 to null
        val authorization = request.headers["authorization"].orEmpty()
        val expected = "Bearer $token"
        if (!MessageDigest.isEqual(authorization.toByteArray(), expected.toByteArray())) return 401 to null
        // No server-initiated stream: GET is not offered, and there is no session to DELETE.
        if (request.method != "POST") return 405 to null
        val text = request.body.decodeToString().trim()
        return if (text.startsWith("[")) {
            val batch = JSONArray(text)
            val replies = JSONArray()
            for (index in 0 until batch.length()) handle(batch.getJSONObject(index))?.let(replies::put)
            if (replies.length() == 0) 202 to null else 200 to replies.toString()
        } else {
            handle(JSONObject(text))?.let { 200 to it.toString() } ?: (202 to null)
        }
    }

    /** One JSON-RPC message; null for notifications and responses, which get no reply. */
    private fun handle(message: JSONObject): JSONObject? {
        if (!message.has("id") || !message.has("method")) return null
        val id = message.get("id")
        val params = message.optJSONObject("params") ?: JSONObject()
        return when (message.optString("method")) {
            "initialize" -> jsonRpcResult(
                id,
                JSONObject()
                    .put("protocolVersion", params.optString("protocolVersion").ifBlank { PROTOCOL_VERSION })
                    .put("capabilities", JSONObject().put("tools", JSONObject()))
                    .put("serverInfo", JSONObject().put("name", POCKET_MCP_SERVER_NAME).put("version", version))
                    .put(
                        "instructions",
                        "Tools provided by the PocketCode Android app that hosts this Linux environment. " +
                            "Use preview_start to run the project's web dev server and show it in the app's Preview tab.",
                    ),
            )
            "ping" -> jsonRpcResult(id, JSONObject())
            "tools/list" -> jsonRpcResult(
                id,
                JSONObject().put(
                    "tools",
                    JSONArray().apply {
                        tools.forEach { tool ->
                            put(
                                JSONObject()
                                    .put("name", tool.name)
                                    .put("description", tool.description)
                                    .put("inputSchema", tool.inputSchema),
                            )
                        }
                    },
                ),
            )
            "tools/call" -> {
                val tool = tools.firstOrNull { it.name == params.optString("name") }
                    ?: return jsonRpcError(id, -32602, "Unknown tool: ${params.optString("name")}")
                val result = runCatching { runBlocking { tool.call(params.optJSONObject("arguments") ?: JSONObject()) } }
                    .getOrElse { PocketToolResult(it.message ?: "Tool failed", isError = true) }
                jsonRpcResult(
                    id,
                    JSONObject()
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", result.text)))
                        .put("isError", result.isError),
                )
            }
            else -> jsonRpcError(id, -32601, "Method not found")
        }
    }

    private fun jsonRpcResult(id: Any, result: JSONObject) =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)

    private fun jsonRpcError(id: Any?, code: Int, message: String) =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", message))

    private class HttpRequest(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
            else -> {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                require(length in 0..MAX_BODY) { "Request too large" }
                readExactly(input, length)
            }
        }
        return HttpRequest(parts[0].uppercase(), parts[1], headers, body)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val body = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: break
            if (size == 0) {
                while (!readLine(input).isNullOrEmpty()) Unit
                break
            }
            require(body.size() + size <= MAX_BODY) { "Request too large" }
            body.write(readExactly(input, size))
            readLine(input)
        }
        return body.toByteArray()
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) break
            read += count
        }
        return bytes
    }

    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.size() == 0) null else line.toString(Charsets.UTF_8.name())
            if (byte == '\n'.code) break
            if (byte != '\r'.code) line.write(byte)
            require(line.size() <= 8_192) { "Header too long" }
        }
        return line.toString(Charsets.UTF_8.name())
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        else -> "Internal Server Error"
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        const val MAX_BODY = 1 shl 20
    }
}
