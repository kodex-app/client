package dev.icedtea.kodex.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.network.LabelDto
import dev.icedtea.kodex.network.SeriesDetailDto
import dev.icedtea.kodex.network.UpdateSeriesMetadataRequest
import dev.icedtea.kodex.ui.sheetMaxHeight

private val STATUSES = listOf("UNKNOWN", "ONGOING", "COMPLETED", "PUBLISHING_FINISHED", "LICENSED", "CANCELLED", "ON_HIATUS")

/**
 * Fields that can be pinned against metadata providers, paired with the control they belong to. The
 * names are the server's own field keys — [SeriesDetailDto.lockedFields] holds exactly these strings.
 */
private val LOCKABLE = listOf(
    "title" to "Title",
    "status" to "Status",
    "publisher" to "Publisher",
    "language" to "Language",
    "genres" to "Genres",
    "tags" to "Tags",
    "summary" to "Summary",
)

/** Edit a series' editorial metadata (partial PATCH — only changed fields are sent). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesMetadataSheet(
    detail: SeriesDetailDto,
    /** Every label on the server, for the multi-select. Empty until they load (or if none exist). */
    labels: List<LabelDto>,
    onDismiss: () -> Unit,
    onSave: (UpdateSeriesMetadataRequest) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(detail.title) }
    var summary by remember { mutableStateOf(detail.summary) }
    var publisher by remember { mutableStateOf(detail.publisher) }
    var status by remember { mutableStateOf(detail.status ?: "UNKNOWN") }
    var language by remember { mutableStateOf(detail.language) }
    var genres by remember { mutableStateOf(detail.genres.joinToString(", ")) }
    var tags by remember { mutableStateOf(detail.tags.joinToString(", ")) }
    var labelIds by remember { mutableStateOf(detail.labels.map { it.id }.toSet()) }
    var locked by remember { mutableStateOf(detail.lockedFields) }

    ModalBottomSheet(modifier = Modifier.heightIn(max = sheetMaxHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Text("Edit series", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            StatusPicker(status) { status = it }
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(publisher, { publisher = it }, label = { Text("Publisher") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(language, { language = it }, label = { Text("Language (e.g. en)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(genres, { genres = it }, label = { Text("Genres (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            if (labels.isNotEmpty()) {
                Spacer(Modifier.size(16.dp))
                SectionTitle("Labels")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        FilterChip(
                            selected = label.id in labelIds,
                            onClick = { labelIds = if (label.id in labelIds) labelIds - label.id else labelIds + label.id },
                            label = { Text(label.name) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
            SectionTitle("Locked fields")
            Text(
                "A locked field is left alone when metadata is refreshed from a provider.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LOCKABLE.forEach { (key, label) ->
                    FilterChip(
                        selected = key in locked,
                        onClick = { locked = if (key in locked) locked - key else locked + key },
                        label = { Text(label) },
                        leadingIcon = { if (key in locked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            UpdateSeriesMetadataRequest(
                                title = title,
                                summary = summary,
                                publisher = publisher,
                                status = status,
                                language = language,
                                genres = genres.splitList(),
                                tags = tags.splitList(),
                                labelIds = labelIds.toList(),
                                lockedFields = locked.toList(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPicker(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Status", Modifier.weight(1f), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            TextButton(onClick = { open = true }) { Text(current.titleCase()) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                STATUSES.forEach { st -> DropdownMenuItem(text = { Text(st.titleCase()) }, onClick = { open = false; onSelect(st) }) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    )
}

private fun String.titleCase(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun String.splitList(): List<String> = split(",").map { it.trim() }.filter { it.isNotBlank() }
