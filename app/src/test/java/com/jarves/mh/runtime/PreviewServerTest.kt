package com.jarves.mh.runtime

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewServerTest {
    private fun workspace(vararg files: Pair<String, String>): File =
        Files.createTempDirectory("preview").toFile().apply {
            files.forEach { (path, content) -> File(this, path).apply { parentFile?.mkdirs() }.writeText(content) }
        }

    @Test
    fun packageScriptsPickDevFirstAndInstallWhenNeeded() {
        val dir = workspace("package.json" to """{"scripts":{"start":"node server.js","dev":"vite"}}""")
        assertEquals("npm install && npm run dev", detectPreviewCommand(dir))
        File(dir, "node_modules").mkdirs()
        File(dir, "pnpm-lock.yaml").writeText("")
        assertEquals("pnpm run dev", detectPreviewCommand(dir))
    }

    @Test
    fun staticSitesAndDjangoAreServedWithPython() {
        assertEquals(
            "python3 -m http.server 8000 --bind 127.0.0.1",
            detectPreviewCommand(workspace("index.html" to "<h1>hi</h1>")),
        )
        assertEquals(
            "python3 -m http.server 8000 --bind 127.0.0.1 --directory public",
            detectPreviewCommand(workspace("public/index.html" to "<h1>hi</h1>")),
        )
        assertEquals("python3 manage.py runserver 127.0.0.1:8000", detectPreviewCommand(workspace("manage.py" to "")))
        assertNull(detectPreviewCommand(workspace("README.md" to "")))
    }

    @Test
    fun printedUrlsAndCommandPortsAreFound() {
        assertEquals("http://127.0.0.1:5173/", detectPreviewUrl("  ➜  Local:   http://localhost:5173/\n  ➜  Network: use --host"))
        assertEquals("http://127.0.0.1:8000/", detectPreviewUrl("Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ..."))
        assertNull(detectPreviewUrl("compiling…"))
        assertEquals(4321, expectedPreviewPort("npx astro dev --port 4321"))
        assertEquals(8000, expectedPreviewPort("python3 -m http.server --bind 127.0.0.1"))
        assertEquals(9000, expectedPreviewPort("python3 -m http.server 9000"))
        assertEquals(3001, expectedPreviewPort("PORT=3001 npm start"))
        assertNull(expectedPreviewPort("npm run dev"))
    }

    @Test
    fun claudeGetsTheBuiltInMcpServerOnlyWhenConfigured() {
        val plain = claudePrintCommand("claude", "sonnet", subscription = false)
        assertTrue("--mcp-config" !in plain)
        val withTools = claudePrintCommand("claude", "sonnet", subscription = false, pocketMcpConfigPath = "/pocket-bridge/x.json")
        assertEquals("/pocket-bridge/x.json", withTools[withTools.indexOf("--mcp-config") + 1])
        assertEquals("mcp__pocketcode", withTools[withTools.indexOf("--allowedTools") + 1])
        assertTrue(withTools[withTools.indexOf("--append-system-prompt") + 1].contains("mcp__pocketcode__preview_start"))
    }

    @Test
    fun mcpServerAnswersAuthorizedJsonRpcOnly() {
        var received: JSONObject? = null
        val server = PocketMcpServer(
            listOf(
                PocketTool("preview_start", "Start", JSONObject().put("type", "object")) { arguments ->
                    received = arguments
                    PocketToolResult("Status: running")
                },
            ),
            version = "test",
        ).start()
        try {
            val config = JSONObject(server.claudeMcpConfig()!!).getJSONObject("mcpServers").getJSONObject("pocketcode")
            assertEquals("http", config.getString("type"))
            val url = config.getString("url")
            val auth = config.getJSONObject("headers").getString("Authorization")

            assertEquals(401, post(url, null, """{"jsonrpc":"2.0","id":1,"method":"ping"}""").first)

            val init = post(url, auth, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""")
            assertEquals(200, init.first)
            assertEquals("2025-03-26", JSONObject(init.second).getJSONObject("result").getString("protocolVersion"))

            assertEquals(202, post(url, auth, """{"jsonrpc":"2.0","method":"notifications/initialized"}""").first)

            val list = JSONObject(post(url, auth, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").second)
            assertEquals("preview_start", list.getJSONObject("result").getJSONArray("tools").getJSONObject(0).getString("name"))

            val call = JSONObject(
                post(url, auth, """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"preview_start","arguments":{"command":"npm run dev"}}}""").second,
            )
            assertEquals("npm run dev", received?.getString("command"))
            val result = call.getJSONObject("result")
            assertEquals("Status: running", result.getJSONArray("content").getJSONObject(0).getString("text"))
            assertEquals(false, result.getBoolean("isError"))
        } finally {
            server.close()
        }
    }

    private fun post(url: String, authorization: String?, body: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val text = (if (code < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
        connection.disconnect()
        return code to text
    }
}
