package dev.icedtea.kodex.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun TtsMediaControls(
    active: Boolean,
    playing: Boolean,
    title: String,
    subtitle: String,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    // The notification outlives any one composition pass, so it must call whatever the callbacks are
    // *now* rather than the ones captured when read aloud started.
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestSkip by rememberUpdatedState(onSkip)
    val latestStop by rememberUpdatedState(onStop)

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Android 13+ hides notifications until the user has allowed them. Asked for at the moment read
    // aloud starts, where the reason is obvious; a refusal costs only the controls — the book still
    // gets read, and the service still keeps the process alive.
    LaunchedEffect(active) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    DisposableEffect(active) {
        if (active) {
            TtsRemote.handler = object : TtsRemoteHandler {
                override fun playPause() = latestPlayPause()
                override fun skip(delta: Int) = latestSkip(delta)
                override fun stop() = latestStop()
            }
        }
        onDispose {
            // Both directions matter: the handler goes first so a press landing during teardown can't
            // reach a reader that is on its way out, then the service (and its notification) go with it.
            TtsRemote.handler = null
            context.stopService(Intent(context, TtsPlaybackService::class.java))
        }
    }

    LaunchedEffect(active, playing, title, subtitle) {
        if (!active) return@LaunchedEffect
        // startForegroundService, not startService: the service must be allowed to go foreground.
        // Guarded because these updates also fire from the notification's own buttons, i.e. with the
        // app in the background — where Android 12+ refuses to *start* a foreground service. An
        // already-running one is only being handed new state, but a refusal there must cost the
        // notification an update, never crash the reader that is still speaking.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                TtsPlaybackService.updateIntent(context, title, subtitle, playing),
            )
        }
    }
}
