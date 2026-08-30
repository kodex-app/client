package dev.icedtea.kodex.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

@Composable
actual fun rememberTtsEngine(): TtsEngine {
    val context = LocalContext.current
    // The application context, not the activity's: the engine outlives a configuration change, and
    // holding an Activity in a service connection is how leaks start.
    val engine = remember(context.applicationContext) { AndroidTtsEngine(context.applicationContext) }
    DisposableEffect(engine) { onDispose { engine.shutdown() } }
    return engine
}

/**
 * Android's [TextToSpeech]. Word highlighting rides on `onRangeStart`, which the Google engine
 * (and any engine declaring the feature) reports per word — the same granularity foliate marks the
 * text at, so the ranges line up with the marks `reader.js` is holding.
 */
private class AndroidTtsEngine(context: Context) : TtsEngine {
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<TtsEvent> = _events.asSharedFlow()

    /**
     * The utterance we currently care about. Callbacks arrive on a binder thread and a stopped or
     * flushed utterance can still deliver one, so every event is checked against this — otherwise a
     * stale `Done` would advance the book a paragraph behind the reader's back.
     */
    @Volatile
    private var currentId: String? = null
    private var counter = 0L

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _available.value = true
            } else {
                _events.tryEmit(TtsEvent.Failed("No speech engine is installed on this device."))
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == currentId) _events.tryEmit(TtsEvent.Done)
                }

                @Deprecated("Kept because the base class still declares it abstract", ReplaceWith(""))
                override fun onError(utteranceId: String?) = onError(utteranceId, -1)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == currentId) _events.tryEmit(TtsEvent.Failed("The voice stopped unexpectedly."))
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    if (utteranceId == currentId) _events.tryEmit(TtsEvent.Range(start, end))
                }
            })
        }
    }

    /**
     * The whole read is inside one `runCatching`, not just the `getVoices()` call: every field of a
     * [Voice] is an engine-supplied platform type, so a device whose engine hands back a null name or
     * locale would otherwise throw an NPE straight through a composition. An empty list costs the
     * user the picker; an exception costs them the app.
     */
    override fun voices(): List<TtsVoice> = runCatching {
        (tts?.voices ?: emptySet<Voice?>())
            .asSequence()
            .filterNotNull()
            // A voice the engine hasn't downloaded yet speaks nothing; offering it is a dead end.
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .mapNotNull { voice ->
                val id: String = voice.name ?: return@mapNotNull null
                TtsVoice(id = id, name = voice.label(), locale = voice.locale?.toLanguageTag().orEmpty())
            }
            // Engines do repeat a name across locale variants, and a repeated id is a duplicate key
            // in the list that renders these.
            .distinctBy { it.id }
            .sortedBy { it.name }
            .toList()
    }.getOrDefault(emptyList())

    override fun speak(text: String, lang: String, rate: Float, voiceId: String?) {
        val engine = tts ?: return
        val id = (++counter).toString()
        currentId = id
        engine.setSpeechRate(rate.coerceIn(0.5f, 3f))
        val voice = voiceId?.let { wanted -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == wanted } }
        if (voice != null) {
            engine.voice = voice
        } else if (lang.isNotBlank()) {
            // No chosen voice: read the book in the language it is written in, when one is installed.
            runCatching { engine.setLanguage(Locale.forLanguageTag(lang)) }
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() {
        currentId = null
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        currentId = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}

/**
 * A readable name for a voice. The raw ones are engine identifiers (`en-us-x-sfg#male_1-local`), so
 * the language is shown and the variant tacked on only when there is one to tell voices apart by.
 */
private fun Voice.label(): String {
    val id = name.orEmpty()
    val variant = id.substringAfterLast('#', "").removeSuffix("-local").replace('_', ' ').trim()
    val language = locale?.let { it.displayName.ifBlank { it.toLanguageTag() } } ?: id
    return if (variant.isEmpty()) language else "$language · $variant"
}
