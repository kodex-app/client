package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * Opens the system's screen for downloading speech voices, so a reader who finds only a handful of
 * languages in the picker has somewhere to go. Null where the platform has no such screen to open —
 * the caller then leaves the affordance out rather than showing a dead one.
 *
 * Newly installed voices show up the next time the picker is opened: the list is read from the
 * engine when the sheet enters composition, and this walks the reader out of the app to get them.
 */
@Composable
expect fun rememberVoiceInstaller(): (() -> Unit)?
