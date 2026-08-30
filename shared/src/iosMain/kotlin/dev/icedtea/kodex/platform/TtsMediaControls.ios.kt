package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * iOS's transport controls: the lock screen, Control Centre and headphone buttons, all fed by
 * `MPRemoteCommandCenter` plus the Now Playing info the system shows beside them. They only appear
 * for an app with an active audio session — which the speech engine sets up (see `Tts.ios.kt`) — and
 * the app's Info.plist has to declare the `audio` background mode, or reading stops when the screen
 * locks and there is nothing left to control.
 */
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
    // The command handlers outlive any one composition pass, so they must call whatever the callbacks
    // are *now* rather than the ones captured when read aloud started.
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestSkip by rememberUpdatedState(onSkip)
    val latestStop by rememberUpdatedState(onStop)

    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }

        val center = MPRemoteCommandCenter.sharedCommandCenter()
        val registered = mutableListOf<Pair<MPRemoteCommand, Any>>()
        fun bind(command: MPRemoteCommand, action: () -> Unit) {
            command.enabled = true
            registered += command to command.addTargetWithHandler {
                action()
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        // play/pause arrive as separate commands from most accessories and as the toggle from a
        // headphone button; all three mean the same thing to a reader with one voice.
        bind(center.playCommand) { latestPlayPause() }
        bind(center.pauseCommand) { latestPlayPause() }
        bind(center.togglePlayPauseCommand) { latestPlayPause() }
        bind(center.stopCommand) { latestStop() }
        // Track skips are the closest thing the system offers to "next paragraph".
        bind(center.nextTrackCommand) { latestSkip(1) }
        bind(center.previousTrackCommand) { latestSkip(-1) }

        onDispose {
            registered.forEach { (command, target) ->
                command.removeTarget(target)
                command.enabled = false
            }
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        }
    }

    LaunchedEffect(active, playing, title, subtitle) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        if (!active) {
            center.nowPlayingInfo = null
            return@LaunchedEffect
        }
        center.nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to title,
            MPMediaItemPropertyArtist to subtitle,
            // A book has no duration to speak of; the rate is what makes the system show it as
            // playing rather than paused.
            MPNowPlayingInfoPropertyPlaybackRate to if (playing) 1.0 else 0.0,
        )
    }
}
