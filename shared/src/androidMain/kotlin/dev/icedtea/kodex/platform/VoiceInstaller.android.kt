package dev.icedtea.kodex.platform

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android's own "install voice data" screen, which every speech engine that can download languages
 * declares. Addressed to [engine] where it has one, because an implicit launch lands on whichever
 * engine the system favours — on a phone whose vendor engine is that favourite, the reader installs
 * a language into an engine the app is not speaking through. The implicit form stays as the
 * fallback, for an engine that declares no screen of its own.
 *
 * Resolved up front either way: an engine that ships a fixed set of voices answers nothing, and a
 * button that opens nothing is worse than no button. (Resolving it at all needs the `TTS_SERVICE`
 * entry in the app manifest's `queries` — engines are separate apps, invisible without it.)
 *
 * Launched for a result, not merely started, purely to learn when the user comes back: the screen
 * reports nothing useful in the result itself, and whether they installed anything is answered by
 * re-reading the engine, not by a result code.
 */
@Composable
actual fun rememberVoiceInstaller(engine: String?, onReturn: () -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onReturn)
    val intent = remember(context, engine) {
        val candidates = listOfNotNull(
            engine?.let { Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(it) },
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        )
        candidates.firstOrNull { runCatching { it.resolveActivity(context.packageManager) }.getOrNull() != null }
    } ?: return null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        callback.value()
    }
    return { runCatching { launcher.launch(intent) } }
}
