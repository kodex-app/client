package dev.icedtea.kodex.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * How much of the window a reader's settings sheet may take, against the 0.75 the app's other sheets
 * use. These two are panels rather than pickers — a dozen controls read in one scroll — and the page
 * behind is one nobody is reading while adjusting how it looks.
 */
internal const val SETTINGS_SHEET_FRACTION = 0.9f

/**
 * Put on the scrolling middle of a settings sheet, above its `verticalScroll`, so scrolling the
 * settings cannot dismiss the sheet.
 *
 * Material hands the sheet whatever a nested scroll leaves unconsumed, which is how a list dragged
 * down from its top edge pulls the sheet shut. That reads fine for a long list and badly for a panel
 * of controls: every flick that runs past an end of the settings was closing the sheet mid-adjustment.
 * Swallowing the leftovers keeps the two gestures apart — the settings scroll, and the sheet is still
 * dragged by its handle or its footer.
 */
@Composable
internal fun rememberSheetScrollGuard(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput) available else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
    }
}

/**
 * Sheet title. Scrolls with the settings rather than being pinned above them: a panel this size needs
 * its height for controls, and the sheet's own drag handle already says what can be grabbed.
 */
@Composable
internal fun ReaderSettingsHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Names a run of cards in a long sheet, so a scroll past it still reads as sections rather than a list. */
@Composable
internal fun ReaderSettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
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

/** Label for a control too wide for a row's control column — the swatch pickers, which span both. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * How a settings row divides: name on the left, control on the right.
 *
 * The control gets the larger share because it is the part that has to hold real words. Even so it is
 * finite — roughly 190dp on a phone — which is what decides between a segmented control and a select
 * at each call site: three segments leave about 60dp each, so "Justify" fits there and "Continuous"
 * does not. A setting whose options outgrow that becomes a select, which shows one value and puts the
 * rest in a menu, and so never has a width it cannot honour.
 */
private const val LABEL_WEIGHT = 0.35f
private const val CONTROL_WEIGHT = 0.65f

/**
 * One row of a settings card: label left, control right, caption (when there is one) under both.
 *
 * Two lines are allowed for the label — a row stays readable when a long name wraps, and shortening
 * every name to fit one line costs more than the wrap does.
 */
@Composable
private fun SettingRow(
    label: String,
    caption: String? = null,
    control: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                Modifier.weight(LABEL_WEIGHT).padding(end = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.weight(CONTROL_WEIGHT),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = control,
            )
        }
        if (caption != null) Caption(caption)
    }
}

/**
 * A small, fixed set of short choices as one segmented control in the row's control column. Short is
 * the constraint: see [LABEL_WEIGHT] for what that column can hold, and use [ReaderSettingsSelect]
 * for anything longer.
 *
 * The tick Material puts in the selected segment is dropped — it costs half the label's room, and a
 * filled segment already says which one is on.
 */
@Composable
internal fun ReaderSettingsSegmented(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    caption: String? = null,
    onSelect: (String) -> Unit,
) {
    SettingRow(label, caption) {
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
    }
}

/**
 * A list too long to lay out: the current value on a tonal field, the rest in a menu.
 *
 * What the fonts need. There are the book's own, every face the server ships, and every one the user
 * has uploaded — a chip row for that is a horizontal scroll with most of its options off the right
 * edge, where nothing tells you they are there or how many.
 */
@Composable
internal fun ReaderSettingsSelect(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    caption: String? = null,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(label, caption) {
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                // The stepper's tint, so the two controls read as the same kind of thing.
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().clickable { open = true },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        // A stored value the list doesn't carry still shows itself rather than a blank.
                        options.firstOrNull { it.first == value }?.second ?: value,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, text) ->
                    val selected = id == value
                    DropdownMenuItem(
                        text = {
                            Text(
                                text,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(id)
                        },
                        trailingIcon = if (selected) {
                            { Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** A tonal minus / value / plus pill in the control column. */
@Composable
internal fun ReaderSettingsStepper(
    label: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingRow(label) {
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

/** Continuous value: the slider in the control column with its readout pinned to the right of it. */
@Composable
internal fun ReaderSettingsSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    SettingRow(label) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            valueText,
            // Fixed width: the readout changes as you drag, and a width that changes with it would
            // shove the slider sideways under your thumb.
            Modifier.widthIn(min = 44.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ReaderSettingsToggle(
    label: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(label, caption = description) {
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
