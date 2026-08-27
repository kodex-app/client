package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/** Opens [url] in the platform browser. Returns a callback usable from click handlers. */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit
