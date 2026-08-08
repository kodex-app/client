package app.kodex.client.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Named aliases for the Material icons this app uses.
 *
 * These were previously hand-traced vector paths, because `material-icons-core` ships only a small
 * subset and the rest had to be redrawn by hand. With `material-icons-extended` on the classpath the
 * real glyphs are available, so each name now points at the canonical icon — no more re-drawn paths
 * drifting from Material's own artwork.
 *
 * The aliases are kept (rather than using `Icons.Filled.*` at every call site) because they say what
 * the icon *means* here — `IncognitoIcon` rather than `VisibilityOff`, `OrientationIcon` rather than
 * `StayCurrentPortrait` — and they keep the mapping in one place if a glyph is ever swapped.
 */
val DownloadIcon: ImageVector get() = Icons.Filled.Download

val FilterIcon: ImageVector get() = Icons.Filled.FilterList

val SelectAllIcon: ImageVector get() = Icons.Filled.CheckBox

val InvertSelectionIcon: ImageVector get() = Icons.Filled.InvertColors

val MarkUnreadIcon: ImageVector get() = Icons.Filled.MarkAsUnread

val LabelIcon: ImageVector get() = Icons.AutoMirrored.Filled.Label

val OpenInWebIcon: ImageVector get() = Icons.AutoMirrored.Filled.OpenInNew

/** Cycles the reader's screen orientation (auto → portrait → landscape). */
val OrientationIcon: ImageVector get() = Icons.Filled.StayCurrentPortrait

val PauseIcon: ImageVector get() = Icons.Filled.Pause

/** The ebook reader's *book contents* action — an open book, distinct from the chapter list. */
val BookContentsIcon: ImageVector get() = Icons.AutoMirrored.Filled.MenuBook

/** Un-favourited half of Browse's star toggle. */
val StarBorderIcon: ImageVector get() = Icons.Filled.StarBorder

/** "This row opens something" affordance on list rows. */
val ChevronRightIcon: ImageVector get() = Icons.Filled.ChevronRight

/** Minus half of the ebook reader's steppers. */
val MinusIcon: ImageVector get() = Icons.Filled.Remove

val BookmarkIcon: ImageVector get() = Icons.Filled.Bookmark

val BookmarkBorderIcon: ImageVector get() = Icons.Filled.BookmarkBorder

val SkipPreviousIcon: ImageVector get() = Icons.Filled.SkipPrevious

val SkipNextIcon: ImageVector get() = Icons.Filled.SkipNext

val LanguageIcon: ImageVector get() = Icons.Filled.Language

/** Incognito reading — nothing is recorded. */
val IncognitoIcon: ImageVector get() = Icons.Filled.VisibilityOff
