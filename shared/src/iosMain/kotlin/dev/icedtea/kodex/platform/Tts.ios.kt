package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
// `setActive` comes from an Objective-C category, so cinterop exposes it as an extension that has to
// be imported by name — unlike `setCategory`, which is on the class itself.
import platform.AVFAudio.setActive
import platform.Foundation.NSRange
import platform.darwin.NSObject

@Composable
actual fun rememberTtsEngine(): TtsEngine {
    val engine = remember { IosTtsEngine() }
    DisposableEffect(engine) { onDispose { engine.stop() } }
    return engine
}

/**
 * `AVSpeechSynthesizer`. Word highlighting comes from `willSpeakRangeOfSpeechString`, whose range is
 * into the very string handed to [speak] — the same offsets `reader.js` holds its foliate marks at.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosTtsEngine : TtsEngine {
    private val _available = MutableStateFlow(true)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<TtsEvent> = _events.asSharedFlow()

    private val synthesizer = AVSpeechSynthesizer()

    /**
     * The utterance the reader is waiting on. A cancelled one still reaches the delegate, and its
     * `didFinish` must not be mistaken for "block spoken, send the next".
     */
    private var current: AVSpeechUtterance? = null

    private val listener = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        // Both callbacks erase to (AVSpeechSynthesizer, AVSpeechUtterance) in Kotlin; the annotation
        // is what lets the two Objective-C selectors keep sharing that one signature.
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didFinishSpeechUtterance === current) {
                current = null
                _events.tryEmit(TtsEvent.Done)
            }
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didCancelSpeechUtterance === current) current = null
        }

        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            willSpeakRangeOfSpeechString: CValue<NSRange>,
            utterance: AVSpeechUtterance,
        ) {
            if (utterance !== current) return
            val (start, end) = willSpeakRangeOfSpeechString.useContents {
                location.toInt() to (location + length).toInt()
            }
            _events.tryEmit(TtsEvent.Range(start, end))
        }
    }

    init {
        synthesizer.delegate = listener
        // Playback, so the book keeps being read with the ringer switch flipped and mixes the way a
        // listener expects rather than being treated as a UI sound.
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
    }

    override fun voices(): List<TtsVoice> =
        AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .map { TtsVoice(id = it.identifier, name = "${it.name} (${it.language})", locale = it.language) }
            .sortedBy { it.name }

    override fun speak(text: String, lang: String, rate: Float, voiceId: String?) {
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        // AVSpeech rates are absolute, not multipliers, so scale the engine's own normal pace.
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * rate)
            .coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        utterance.voice = voiceId?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            // No chosen voice: read the book in the language it is written in, when one is installed.
            ?: lang.takeIf { it.isNotBlank() }?.let { AVSpeechSynthesisVoice.voiceWithLanguage(it) }
        current = utterance
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        current = null
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
