package app.kodex.client.ui.recents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.AppSettings
import app.kodex.client.network.KodexApi
import app.kodex.client.network.ServerEvent
import app.kodex.client.ui.OnServerEvent
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.isoEpochMillis

/** One page is plenty for a badge: past this the number stops being information anyway. */
private const val BADGE_SCAN_SIZE = 50

/**
 * How many chapters have turned up since the user last opened the Recents tab, for the bottom-nav
 * badge.
 *
 * Counted on the client because the server has no notion of an "unseen" update — `/updates` is just a
 * feed. The mark is a timestamp per server (see [AppSettings.updatesSeenAt]) and this counts entries
 * newer than it, so opening the tab clears the badge without needing anything written back.
 *
 * Only the first page is scanned, so the count saturates; callers cap the label anyway.
 */
@Composable
fun rememberNewUpdateCount(session: SessionManager, api: KodexApi, appSettings: AppSettings): Int {
    val server by session.activeServer.collectAsStateSafe()
    val seenMark by appSettings.updatesSeenMark.collectAsStateSafe()
    var count by remember { mutableIntStateOf(0) }

    // Bumped by SSE so the badge appears while the app is open, not only on a cold start.
    var reload by remember { mutableIntStateOf(0) }
    OnServerEvent(ServerEvent.LIBRARY_SCAN_COMPLETED, ServerEvent.BOOK_ADDED) { reload++ }

    LaunchedEffect(server?.id, seenMark, reload) {
        val s = server
        if (s == null) {
            count = 0
            return@LaunchedEffect
        }
        val seenAt = appSettings.updatesSeenAt(s.id)
        // Never seen: no badge. Showing the whole backlog the first time would be noise, not news.
        if (seenAt <= 0L) {
            count = 0
            return@LaunchedEffect
        }
        count = runCatching { api.updates(s.baseUrl, s.apiKey, page = 0, size = BADGE_SCAN_SIZE).content }
            .getOrDefault(emptyList())
            .count { (isoEpochMillis(it.foundDate) ?: 0L) > seenAt }
    }
    return count
}
