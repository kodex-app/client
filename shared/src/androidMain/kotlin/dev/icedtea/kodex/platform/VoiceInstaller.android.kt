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
 * declares. Resolved up front: an engine that ships a fixed set of voices answers nothing, and a
 * button that opens nothing is worse than no button. (Resolving it at all needs the `TTS_SERVICE`
 * entry in the app manifest's `queries` — engines are separate apps, invisible without it.)
 *
 * Launched for a result, not merely started, purely to learn when the user comes back: the screen
 * reports nothing useful in the result itself, and whether they installed anything is answered by
 * re-reading the engine, not by a result code.
 */
@Composable
actual fun rememberVoiceInstaller(onReturn: () -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onReturn)
    val intent = remember(context) {
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .takeIf { runCatching { it.resolveActivity(context.packageManager) }.getOrNull() != null }
    } ?: return null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        callback.value()
    }
    return { runCatching { launcher.launch(intent) } }
}
