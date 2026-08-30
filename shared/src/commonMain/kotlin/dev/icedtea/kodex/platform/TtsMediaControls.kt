package dev.icedtea.kodex.platform

import androidx.compose.runtime.Composable

/**
 * System-level playback controls for read aloud: a notification on Android, the Now Playing / lock
 * screen on iOS.
 *
 * Composed by the ebook reader for as long as the read-aloud panel is open. It exists because the
 * voice keeps talking once the reader is no longer on screen — that is the point of listening — and
 * a book being read with the phone in a pocket needs a way to be paused that isn't "find the app
 * again". On Android it doubles as the thing that keeps the process alive: the notification belongs
 * to a foreground service, without which the system is free to kill a backgrounded app mid-chapter.
 *
 * The controls mirror the in-app panel exactly, so both drive the same reader state.
 */
@Composable
expect fun TtsMediaControls(
    /** Read aloud is on. False tears the controls down (and, on Android, stops the service). */
    active: Boolean,
    playing: Boolean,
    /** What is being read — the book or chapter title. */
    title: String,
    /** Secondary line: the series, or the chapter within the book. */
    subtitle: String,
    onPlayPause: () -> Unit,
    /** +1 / -1 — the next or previous paragraph, as the in-app bar's skip buttons. */
    onSkip: (Int) -> Unit,
    onStop: () -> Unit,
)
