package dev.icedtea.kodex.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Shared furniture for the two reader settings sheets (images and ebooks).
 *
 * Both sheets are the same shape — a title, grouped cards of controls, and a pinned footer that
 * saves or resets — so the widgets live here rather than being written twice with two sets of
 * paddings. Every control follows one rule: the label sits above or to the left of its control, the
 * control gets the width it needs, and nothing in a sheet is smaller than a comfortable thumb.
 */

/** Horizontal inset every part of a settings sheet lines up on. */
internal val SheetGutter = 20.dp

/**
 * Put on the scrolling middle of a settings sheet, above its `verticalScroll`, so scrolling the
 * settings cannot dismiss the sheet.
 *
 * Material hands the sheet whatever a nested scroll leaves unconsumed, which is how a list dragged
 * down from its top edge pulls the sheet shut. That reads fine for a long list and badly for a panel:
 * the scrolling part here is a short strip between a pinned header and a pinned footer, so it is
 * against one of its ends most of the time and every flick that runs past that end was closing the
 * sheet mid-adjustment. Swallowing the leftovers keeps the two gestures apart — the content scrolls,
 * and the sheet is still dragged by its handle, its header, its tabs or its footer.
 */
@Composable
internal fun rememberSheetScrollGuard(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput) available else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
    }
}

@Composable
internal fun ReaderSettingsHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = SheetGutter, end = SheetGutter, bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Pill tabs, for a sheet with more settings than fit a screen. Deliberately not a TabRow: an
 * underlined tab in a bottom sheet reads as a second app bar, while the pill reads as what it is —
 * a switch between two short pages of the same panel.
 */
@Composable
internal fun ReaderSettingsTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SheetGutter),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, label ->
                val active = index == selected
                val container by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                val content by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.weight(1f)
                        .clip(CircleShape)
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = content,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Rounded container grouping the controls that belong together. */
@Composable
internal fun ReaderSettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Label line shared by the stacked controls: name on the left, current value or a caption on the right. */
@Composable
private fun FieldLabel(text: String, value: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A small, fixed set of choices — two to four — as one segmented control filling the card's width.
 * The tick Material puts in the selected segment is dropped: it costs half the label's room on a
 * phone, and a filled segment already says which one is on.
 */
@Composable
internal fun ReaderSettingsSegmented(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    caption: String? = null,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (id, text) ->
                SegmentedButton(
                    selected = value == id,
                    onClick = { onSelect(id) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.weight(1f),
                    icon = {},
                    label = {
                        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        if (caption != null) Caption(caption)
    }
}

/** Open-ended choices (fonts, preload counts) — chips that scroll rather than a segment that shrinks. */
@Composable
internal fun ReaderSettingsChips(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    caption: String? = null,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, text) ->
                val selected = value == id
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(id) },
                    shape = CircleShape,
                    label = { Text(text, maxLines = 1) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Outlined.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
        if (caption != null) Caption(caption)
    }
}

/** Label on the left, a tonal minus / value / plus pill on the right. */
@Composable
internal fun ReaderSettingsStepper(
    label: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = CircleShape,
            // Tinted rather than another surface step: the card is already surfaceContainerHigh, and
            // in the light scheme the steps above it are the same colour.
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, enabled = canDecrease) {
                    Icon(Icons.Outlined.Remove, "Decrease $label", Modifier.size(18.dp))
                }
                Text(
                    value,
                    Modifier.widthIn(min = 52.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onIncrease, enabled = canIncrease) {
                    Icon(Icons.Outlined.Add, "Increase $label", Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Continuous value: name and readout on one line, the slider under them at full width. */
@Composable
internal fun ReaderSettingsSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column {
        FieldLabel(label, valueText)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
internal fun ReaderSettingsToggle(
    label: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Caption(description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** One tile of a [ReaderSettingsSwatches] row. [sample] is drawn on the tile when it is not blank. */
internal data class ReaderSwatch(
    val id: String,
    val label: String,
    val color: Color,
    val contentColor: Color = Color.Unspecified,
    val sample: String = "",
)

/**
 * Colour choices shown as the colours themselves — reading theme, page background. Naming them
 * ("Sepia", "Gray") tells you less than the swatch does, so the name is only the caption.
 */
@Composable
internal fun ReaderSettingsSwatches(
    label: String,
    value: String,
    options: List<ReaderSwatch>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel(label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { swatch ->
                val selected = swatch.id == value
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(swatch.color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelect(swatch.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (swatch.sample.isNotBlank()) {
                            Text(swatch.sample, color = swatch.contentColor, style = MaterialTheme.typography.titleMedium)
                        }
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                null,
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        swatch.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Pinned bottom of a settings sheet. It sits outside the scrolling area on purpose: a "Reset" you
 * have to scroll to find is a "Reset" nobody finds, and the same two buttons in the same place in
 * both readers means neither has to be hunted for twice.
 */
@Composable
internal fun ReaderSettingsFooter(note: String?, onSaveDefault: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            Modifier.fillMaxWidth().padding(start = SheetGutter, end = SheetGutter, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = onSaveDefault, modifier = Modifier.weight(1f)) { Text("Save as default") }
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = SheetGutter, end = SheetGutter, top = 10.dp),
            )
        }
    }
}
