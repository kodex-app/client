package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * iOS has no screen an app may open for this: voices live under Settings ... Accessibility ...
 * Spoken Content, which is out of reach of a URL scheme. The picker leaves the row out — and the
 * system ships a voice for every language it supports, so the row has little to offer there anyway.
 */
@Composable
actual fun rememberVoiceInstaller(): (() -> Unit)? = null
