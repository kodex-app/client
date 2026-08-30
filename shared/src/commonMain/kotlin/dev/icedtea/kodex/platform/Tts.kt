package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The device's text-to-speech voice, used by the ebook reader's "Read aloud".
 *
 * A WebView has no `speechSynthesis` on either platform (Android's omits it entirely, WKWebView only
 * speaks through the accessibility stack), so the reader can't do what the web UI does and let the
 * page speak for itself. Instead `reader.js` hands one block of text over at a time and this engine
 * says it, reporting back the characters it is on so the page can highlight the word — see the
 * read-aloud section of `reader.js`.
 */
data class TtsVoice(
    /** Platform-stable identifier; what gets persisted. */
    val id: String,
    /** What the picker shows — usually the language, plus a variant when a language has several. */
    val name: String,
    /** BCP-47 tag, so the reader can preselect a voice matching the book. */
    val locale: String,
)

/** What the voice reports while it reads. */
sealed interface TtsEvent {
    /** Characters `[start, end)` of the text handed to [TtsEngine.speak] are being spoken now. */
    data class Range(val start: Int, val end: Int) : TtsEvent

    /** The utterance finished by itself — the reader answers with the next block. */
    data object Done : TtsEvent

    data class Failed(val message: String) : TtsEvent
}

interface TtsEngine {
    /**
     * Whether a voice is ready. Android initialises its engine asynchronously and a device may have
     * none installed at all, so this starts false and the reader hides "Read aloud" until it flips.
     */
    val available: StateFlow<Boolean>

    /** Progress of the utterance currently being spoken. Hot — events while nobody listens are lost. */
    val events: SharedFlow<TtsEvent>

    /** Voices installed on this device. Meaningful once [available] is true. */
    fun voices(): List<TtsVoice>

    /**
     * Speaks [text], cutting off anything already speaking. [lang] is the book's language (BCP-47),
     * used to pick a voice when the user hasn't chosen one; [rate] is a multiplier of the engine's
     * normal speed.
     */
    fun speak(text: String, lang: String, rate: Float, voiceId: String?)

    fun stop()
}

@Composable
expect fun rememberTtsEngine(): TtsEngine

/** Speed multipliers the reader offers; 1× is the engine's own normal pace. */
val TTS_RATES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
