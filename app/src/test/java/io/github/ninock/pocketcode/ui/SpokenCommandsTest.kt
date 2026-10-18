package io.github.ninock.pocketcode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenCommandsTest {
    @Test
    fun sendItAtTheEndSends() {
        assertEquals(SpokenCommand.Send("Add a dark mode toggle"), SpokenCommands.parse("Add a dark mode toggle send it"))
        assertEquals(SpokenCommand.Send("Fix the tests"), SpokenCommands.parse("Fix the tests, Send it."))
        assertEquals(SpokenCommand.Send(""), SpokenCommands.parse("send it"))
    }

    @Test
    fun sendItInTheMiddleIsDictation() {
        assertEquals(SpokenCommand.Text("send it to the server and log the response"), SpokenCommands.parse("send it to the server and log the response"))
        // "resend it" is a different word.
        assertEquals(SpokenCommand.Text("please resend it"), SpokenCommands.parse("please resend it"))
    }

    @Test
    fun stopListeningStops() {
        assertEquals(SpokenCommand.Stop("Rename the file"), SpokenCommands.parse("Rename the file stop listening"))
    }
}
