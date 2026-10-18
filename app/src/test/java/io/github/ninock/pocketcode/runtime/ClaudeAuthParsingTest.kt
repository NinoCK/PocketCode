package io.github.ninock.pocketcode.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are raw PTY output of the real Linux `claude setup-token`
 * (Claude Code 2.1.288, 1000x50 terminal) run against a fake OAuth server, so
 * the tokens in them are fake. They were also replaced with obvious placeholders
 * of the same length so the recordings never carry a real-looking credential.
 */
class ClaudeAuthParsingTest {
    private val redirectToken =
        "sk-ant-oat01-FAKE-TEST-TOKEN-REDIRECT-FAKE-TEST-TOKEN-REDIRECT-FAKE-TEST-TOKEN-REDIRECT-FAKE-TEST-TOKEN-REDI"
    private val manualToken =
        "sk-ant-oat01-FAKE-TEST-TOKEN-MANUAL-FAKE-TEST-TOKEN-MANUAL-FAKE-TEST-TOKEN-MANUAL-FAKE-TEST-TOKEN-MANUAL-FAK"

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/claude-setup-token/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    /** Replays output the way the controller reads it: in arbitrary chunks. */
    private fun replay(bytes: ByteArray, chunk: Int, onRead: (ClaudeSetupScreenRead) -> Unit = {}): VtScreen {
        val screen = VtScreen(50, 1000)
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunk, bytes.size - offset)
            screen.feed(bytes.copyOfRange(offset, offset + count), count)
            offset += count
            onRead(readClaudeSetupScreen(screen.text(), outputComplete = false))
        }
        return screen
    }

    @Test
    fun `reads the token after a browser redirect sign-in`() {
        for (chunk in listOf(1, 7, 64, 4096, 1 shl 20)) {
            val tokens = mutableListOf<String>()
            val screen = replay(fixture("browser-redirect.bin"), chunk) { read -> read.token?.let(tokens::add) }
            // A partially drawn token must never be accepted.
            assertTrue("chunk $chunk saw ${tokens.distinct()}", tokens.all { it == redirectToken })
            assertEquals(redirectToken, readClaudeSetupScreen(screen.text(), outputComplete = true).token)
        }
    }

    @Test
    fun `reads the token after the manual code path`() {
        // Claude Code's diff renderer skips unchanged cells here: the token is
        // written as "sk-ant-" + cursor move + "at01-...", reusing an "o" that
        // was already on screen. Escape stripping cannot recover that.
        for (chunk in listOf(1, 13, 512, 1 shl 20)) {
            val tokens = mutableListOf<String>()
            val screen = replay(fixture("manual-code.bin"), chunk) { read -> read.token?.let(tokens::add) }
            assertTrue("chunk $chunk saw ${tokens.distinct()}", tokens.all { it == manualToken })
            assertEquals(manualToken, readClaudeSetupScreen(screen.text(), outputComplete = true).token)
        }
    }

    @Test
    fun `detects the code prompt and manual url while waiting`() {
        val bytes = fixture("manual-code.bin")
        val waiting = bytes.copyOfRange(0, String(bytes, Charsets.ISO_8859_1).indexOf("prompted") + "prompted".length + 8)
        val screen = VtScreen(50, 1000).apply { feed(waiting) }
        val read = readClaudeSetupScreen(screen.text(), outputComplete = false)
        assertTrue(read.acceptsCode)
        assertNull(read.token)
        val url = requireNotNull(read.manualUrl)
        assertTrue(url.startsWith("https://claude.com/cai/oauth/authorize?code=true&"))
        assertTrue(url.endsWith("&state=zJqXQEHQqjHtd6NE5jxoT8esam1M1bI0zyp5zKmBx2c"))
        assertTrue("redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback" in url)
    }

    @Test
    fun `ignores the manual url until its frame is complete`() {
        val url = "https://claude.com/cai/oauth/authorize?code=true&state=" + "a".repeat(20)
        assertNull(readClaudeSetupScreen("Browser didn't open?\n$url", outputComplete = false).manualUrl)
    }

    @Test
    fun `ignores the localhost redirect url`() {
        val state = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"
        val automatic = "https://claude.com/cai/oauth/authorize?code=true&redirect_uri=http%3A%2F%2Flocalhost%3A45123%2Fcallback" +
            "&code_challenge=c&state=$state"
        assertNull(extractClaudeManualAuthUrl("$automatic\nPaste code here if prompted >"))
    }

    @Test
    fun `reconstructs a manual url wrapped across terminal rows`() {
        val state = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"
        val url = "https://claude.com/cai/oauth/authorize?code=true&client_id=x&response_type=code" +
            "&redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback&scope=user%3Ainference" +
            "&code_challenge=challenge123&code_challenge_method=S256&state=$state"
        val screen = VtScreen(10, 120).apply { feed(url + "\r\n\r\nPaste code here if prompted >") }
        assertEquals(url, extractClaudeManualAuthUrl(screen.text()))
    }

    @Test
    fun `reports invalid codes clearly`() {
        val message = extractClaudeAuthError("OAuth error: Invalid code. Please make sure the full code was copied\nPress Enter to retry.")
        assertTrue(message!!.contains("rejected that code"))
        assertNull(extractClaudeAuthError("Paste code here if prompted >"))
    }

    @Test
    fun `screen model positions words with cursor moves`() {
        val screen = VtScreen(5, 40).apply { feed("\u001B[2GPaste\u001B[8Gcode\u001B[13Ghere\r\n") }
        assertEquals(" Paste code here", screen.lines()[0])
        assertFalse(screen.text().contains('\u001B'))
    }

    @Test
    fun `redacts tokens from diagnostics`() {
        assertEquals("token: sk-ant-…", redactClaudeSecrets("token: $manualToken"))
    }
}
