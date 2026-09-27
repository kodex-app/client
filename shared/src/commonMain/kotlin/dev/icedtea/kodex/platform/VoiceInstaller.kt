package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * Opens the screen for downloading speech voices, so a reader who finds only a handful of languages
 * in the picker has somewhere to go. Null where the platform has no such screen to open — the caller
 * then leaves the affordance out rather than showing a dead one.
 *
 * [engine] is the [TtsProvider.id] currently speaking, because each engine has a download screen of
 * its own and installing into the wrong one is exactly the dead end this is here to solve: the voice
 * arrives, and the list — read from a different engine — is unchanged.
 *
 * [onReturn] fires when the user comes back. It is the whole point of routing this through the
 * platform rather than firing an intent and forgetting: the picker is still open behind the install
 * screen, holding the list it read on the way in, so without a signal a voice just downloaded is
 * nowhere to be seen and the button looks broken.
 */
@Composable
expect fun rememberVoiceInstaller(engine: String?, onReturn: () -> Unit): (() -> Unit)?
