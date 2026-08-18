package app.kodex.client.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The screen between two chapters, shared by both readers: what you just finished and what comes next.
 *
 * The image reader hands this to its pager as a real page, so you swipe onto it and swipe again to
 * commit. The ebook reader has no pager — its page is a WebView — so it lays the same screen over the
 * top and drives it with the same gestures. Tapping commits in both, so tap-readers aren't stranded.
 *
 * It paints [background] — the reader's own page background, so swiping onto it doesn't flash a
 * differently coloured screen between two pages — and derives its text colour from that background's
 * luminance, so a comic reader set to a white page doesn't render this text white-on-white.
 */
@Composable
internal fun ChapterTransitionPage(
    isNext: Boolean,
    currentTitle: String,
    siblingTitle: String,
    seriesTitle: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    // The sibling's content is fetched by the screen that replaces this one, so the spinner runs from
    // the moment the jump is committed until that screen takes over.
    var committing by remember { mutableStateOf(false) }
    val content = if (background.luminance() > 0.5f) Color(0xFF14161A) else Color(0xFFF2F3F5)
    val contentVariant = content.copy(alpha = 0.72f)
    Column(
        modifier.fillMaxSize()
            .background(background)
            .clickable(enabled = !committing) { committing = true; onContinue() }
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (isNext) {
            TransitionEntry("Finished:", currentTitle, seriesTitle, content, contentVariant)
            Spacer(Modifier.height(40.dp))
            TransitionEntry("Next:", siblingTitle, seriesTitle, content, contentVariant)
        } else {
            TransitionEntry("Previous:", siblingTitle, seriesTitle, content, contentVariant)
            Spacer(Modifier.height(40.dp))
            TransitionEntry("Current:", currentTitle, seriesTitle, content, contentVariant)
        }
        if (committing) {
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = content)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Loading pages…",
                    color = contentVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** One "label / title / series" block of the between-chapters screen. */
@Composable
private fun TransitionEntry(
    label: String,
    title: String,
    subtitle: String,
    content: Color,
    contentVariant: Color,
) {
    Text(
        label,
        color = content,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        title.ifBlank { "—" },
        color = content,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    if (subtitle.isNotBlank() && subtitle != title) {
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            color = contentVariant,
            style = MaterialTheme.typography.bodyMedium,
            // Series titles run long, and a single clipped line tells you nothing about which series
            // this is. Still capped, so a pathological title can't push the other block off-screen.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
