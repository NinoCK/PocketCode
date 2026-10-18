package io.github.ninock.pocketcode.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** What a finished stretch of hands-free speech asked for. */
internal sealed interface SpokenCommand {
    /** Plain dictation. */
    data class Text(val text: String) : SpokenCommand
    /** Ended with "send it": [text] is what came before it. */
    data class Send(val text: String) : SpokenCommand
    /** Ended with "stop listening". */
    data class Stop(val text: String) : SpokenCommand
}

internal object SpokenCommands {
    private val SendPhrase = Regex("""(?:^|[\s,.;:!?])send it[\s.,;:!?]*$""", RegexOption.IGNORE_CASE)
    private val StopPhrase = Regex("""(?:^|[\s,.;:!?])stop listening[\s.,;:!?]*$""", RegexOption.IGNORE_CASE)

    /**
     * Only a phrase at the very end of an utterance counts, so "send it to the server" stays
     * dictation. Recognisers add the pause-ending punctuation themselves, so it's ignored.
     */
    fun parse(heard: String): SpokenCommand {
        val text = heard.trim()
        SendPhrase.find(text)?.let { return SpokenCommand.Send(text.substring(0, it.range.first).trimEnd(' ', ',', ';', ':')) }
        StopPhrase.find(text)?.let { return SpokenCommand.Stop(text.substring(0, it.range.first).trimEnd(' ', ',', ';', ':')) }
        return SpokenCommand.Text(text)
    }
}

/**
 * Speech-to-text for the chat composer. Words stream into the prompt as they are recognised and
 * are appended after whatever was already typed.
 *
 * A tap dictates until the next pause. Hands-free mode keeps listening across pauses, sends the
 * message when the user says "send it", and stops on "stop listening", a tap, or a long silence.
 */
@Stable
internal class VoiceDictation(
    private val context: Context,
    private val currentText: () -> String,
    private val onText: (String) -> Unit,
    private val onSend: () -> Unit,
) {
    var listening by mutableStateOf(false)
        private set

    /** Listening across pauses until "send it". */
    var handsFree by mutableStateOf(false)
        private set

    /** Microphone input level in 0..1, for the listening indicator. */
    var level by mutableFloatStateOf(0f)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var prefix = ""
    private val handler = Handler(Looper.getMainLooper())
    private var lastSpeechAt = 0L
    private var quickFailures = 0

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(handsFree: Boolean = false) {
        if (listening) {
            // A long-press while dictating switches the running session to hands-free.
            if (handsFree) this.handsFree = true
            return
        }
        if (!available) {
            Toast.makeText(context, "No speech recognition service on this phone", Toast.LENGTH_LONG).show()
            return
        }
        this.handsFree = handsFree
        lastSpeechAt = SystemClock.elapsedRealtime()
        quickFailures = 0
        listening = true
        listen()
    }

    fun stop() {
        if (!listening) return
        handsFree = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
    }

    /** Stops and drops anything still being recognised, e.g. once the prompt was sent. */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        handsFree = false
        if (!listening) return
        recognizer?.cancel()
        listening = false
        level = 0f
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        handsFree = false
        listening = false
        level = 0f
    }

    /** Starts one recognition pass. Hands-free mode chains passes, one per utterance. */
    private fun listen() {
        val existing = currentText()
        prefix = when {
            existing.isEmpty() -> ""
            existing.last().isWhitespace() -> existing
            else -> "$existing "
        }
        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        speech.setRecognitionListener(listener)
        val silence = if (handsFree) 4_000L else 2_500L
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Give long prompts room to breathe before recognition decides the user is done.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
        runCatching { speech.startListening(intent) }.onFailure {
            finish()
            Toast.makeText(context, "Couldn't start voice input", Toast.LENGTH_SHORT).show()
        }
    }

    /** In hands-free mode, listens again after a pause; otherwise the session is over. */
    private fun continueOrFinish(delayMillis: Long = RESTART_DELAY_MS) {
        if (!handsFree) {
            finish()
            return
        }
        if (SystemClock.elapsedRealtime() - lastSpeechAt > HANDS_FREE_IDLE_MS) {
            finish()
            Toast.makeText(context, "Hands-free stopped after a long silence", Toast.LENGTH_SHORT).show()
            return
        }
        level = 0f
        handler.postDelayed({ if (handsFree && listening) listen() }, delayMillis)
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        handsFree = false
        listening = false
        level = 0f
    }

    private fun emit(results: Bundle?): String? {
        if (!listening) return null
        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (!heard.isNullOrEmpty()) onText(prefix + heard)
        return heard
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            lastSpeechAt = SystemClock.elapsedRealtime()
        }
        override fun onRmsChanged(rmsdB: Float) {
            // Roughly -2..10 dB from most recognisers.
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            level = 0f
        }
        override fun onError(error: Int) {
            level = 0f
            if (!listening) return
            when (error) {
                // Silence or mumbling: hands-free just keeps listening.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> continueOrFinish()
                // The previous pass hadn't let go of the mic yet; try again shortly, but not forever.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (handsFree && ++quickFailures <= MAX_QUICK_FAILURES) {
                        continueOrFinish(delayMillis = 600L)
                    } else {
                        finish()
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) Toast.makeText(context, "Voice input is busy, try again", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    finish()
                    val message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input needs a network connection"
                        else -> "Voice input stopped"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onResults(results: Bundle?) {
            val heard = emit(results)
            if (!handsFree) {
                finish()
                return
            }
            quickFailures = 0
            if (!heard.isNullOrEmpty()) lastSpeechAt = SystemClock.elapsedRealtime()
            when (val command = SpokenCommands.parse(heard.orEmpty())) {
                is SpokenCommand.Text -> continueOrFinish()
                is SpokenCommand.Stop -> {
                    onText((prefix + command.text).trimEnd())
                    finish()
                }
                is SpokenCommand.Send -> {
                    val message = (prefix + command.text).trim()
                    onText(message)
                    finish()
                    if (message.isNotEmpty()) onSend()
                    else Toast.makeText(context, "Say your message before \"send it\"", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            emit(partialResults)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val RESTART_DELAY_MS = 250L
        const val HANDS_FREE_IDLE_MS = 2 * 60 * 1000L
        const val MAX_QUICK_FAILURES = 5
    }
}

/**
 * Returns the dictation plus its tap action. Call `dictation.start(handsFree = true)` through
 * [VoiceControls.startHandsFree] so the microphone permission is asked first.
 */
internal class VoiceControls(val toggle: () -> Unit, val startHandsFree: () -> Unit)

@Composable
internal fun rememberVoiceDictation(text: String, onText: (String) -> Unit, onSend: () -> Unit = {}): Pair<VoiceDictation, VoiceControls> {
    val context = LocalContext.current
    val latestText by rememberUpdatedState(text)
    val latestOnText by rememberUpdatedState(onText)
    val latestOnSend by rememberUpdatedState(onSend)
    val dictation = remember(context) { VoiceDictation(context, { latestText }, { latestOnText(it) }, { latestOnSend() }) }
    DisposableEffect(dictation) { onDispose { dictation.release() } }
    var wantHandsFree by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictation.start(handsFree = wantHandsFree)
        else Toast.makeText(context, "Allow microphone access to dictate prompts", Toast.LENGTH_LONG).show()
    }
    fun startWithPermission(handsFree: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            dictation.start(handsFree)
        } else {
            wantHandsFree = handsFree
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val controls = remember(dictation) {
        VoiceControls(
            toggle = { if (dictation.listening) dictation.stop() else startWithPermission(handsFree = false) },
            startHandsFree = { startWithPermission(handsFree = true) },
        )
    }
    return dictation to controls
}
