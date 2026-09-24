package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * Opens the system's screen for downloading speech voices, so a reader who finds only a handful of
 * languages in the picker has somewhere to go. Null where the platform has no such screen to open —
 * the caller then leaves the affordance out rather than showing a dead one.
 *
 * [onReturn] fires when the user comes back. It is the whole point of routing this through the
 * platform rather than firing an intent and forgetting: the picker is still open behind the install
 * screen, holding the list it read on the way in, so without a signal a voice just downloaded is
 * nowhere to be seen and the button looks broken.
 */
@Composable
expect fun rememberVoiceInstaller(onReturn: () -> Unit): (() -> Unit)?
