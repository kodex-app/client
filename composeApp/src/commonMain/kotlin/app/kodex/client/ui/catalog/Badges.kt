package app.kodex.client.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Distinct, readable badge colours so WEB / LOCAL / COMIC / BOOK / 18+ read at a glance.
private val TealBg = Color(0xFF0F766E)
private val IndigoBg = Color(0xFF4338CA)
private val BlueBg = Color(0xFF1D4ED8)
private val PurpleBg = Color(0xFF7E22CE)
private val GreenBg = Color(0xFF15803D)
private val PinkBg = Color(0xFFBE185D)
private val AmberBg = Color(0xFFB45309)
private val SlateBg = Color(0xFF475569)

/** Container/content colours for a known badge label (kind / library type / adult), else a neutral pill. */
@Composable
fun badgeStyle(label: String): Pair<Color, Color> = when (label.uppercase()) {
    "COMIC" -> TealBg to Color.White
    "BOOK", "NOVEL", "EPUB", "PDF" -> IndigoBg to Color.White
    // Library type, matching the web UI's Libraries page (WEB purple, LOCAL blue).
    "WEB" -> PurpleBg to Color.White
    "LOCAL" -> BlueBg to Color.White
    "18+", "ADULT", "NSFW" -> PinkBg to Color.White
    "NEW" -> AmberBg to Color.White
    // Library visibility state — muted slate rather than a colour that competes with type/kind.
    "HIDDEN", "NOT ON HOME" -> SlateBg to Color.White
    "MIXED", "UNKNOWN" -> AmberBg to Color.White
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

/** A small colour-coded pill. Pass a known [label] to auto-colour, or override [container]/[content]. */
@Composable
fun ColorBadge(
    label: String,
    modifier: Modifier = Modifier,
    container: Color? = null,
    content: Color? = null,
) {
    val (bg, fg) = badgeStyle(label)
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container ?: bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content ?: fg,
    )
}
