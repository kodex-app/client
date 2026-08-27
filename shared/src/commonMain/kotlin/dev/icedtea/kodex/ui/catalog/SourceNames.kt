package dev.icedtea.kodex.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.SeriesDto
import dev.icedtea.kodex.ui.collectAsStateSafe

/**
 * Content-source display names by provider id, cached for the process. The installed sources change
 * only when an admin installs a plugin, and every cover grid needs the same map — refetching it per
 * screen (and per return to a screen) would be a request for something that never moved.
 */
private val cache = mutableMapOf<String, Map<String, String>>()

@Composable
fun rememberSourceNames(session: SessionManager, api: KodexApi): Map<String, String> {
    val server by session.activeServer.collectAsStateSafe()
    var names by remember(server?.id) { mutableStateOf(server?.id?.let { cache[it] }.orEmpty()) }
    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        cache[s.id]?.let {
            names = it
            return@LaunchedEffect
        }
        val loaded = runCatching { api.contentSources(s.baseUrl, s.apiKey).associate { it.id to it.displayName } }
            .getOrDefault(emptyMap())
        // Only cache a real answer, so a failed load is retried rather than remembered as "no sources".
        if (loaded.isNotEmpty()) cache[s.id] = loaded
        names = loaded
    }
    return names
}

/**
 * The label for a series' content source, or null for a local series that has none. Falls back to the
 * raw provider id while the names are still loading — the same fallback the web uses, and better than
 * a badge that pops in late.
 */
fun sourceLabel(series: SeriesDto, sourceNames: Map<String, String>): String? =
    series.sourceProviderId?.takeIf { it.isNotBlank() }?.let { sourceNames[it] ?: it }
