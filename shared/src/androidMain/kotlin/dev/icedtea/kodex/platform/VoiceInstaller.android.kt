package dev.icedtea.kodex.platform

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's own "install voice data" screen, which every speech engine that can download languages
 * declares. Resolved up front: an engine that ships a fixed set of voices answers nothing, and a
 * button that opens nothing is worse than no button.
 */
@Composable
actual fun rememberVoiceInstaller(): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember(context) {
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { runCatching { it.resolveActivity(context.packageManager) }.getOrNull() != null }
    } ?: return null
    return { runCatching { context.startActivity(intent) } }
}
