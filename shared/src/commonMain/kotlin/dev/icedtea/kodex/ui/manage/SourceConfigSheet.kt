package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.network.ConfigFieldDto
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.SourceConfigDto
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.sheetMaxHeight
import kotlinx.coroutines.launch

/**
 * A content source's own settings, built from the schema the plugin declares — the server sends the
 * fields, their types and current values, and this renders the matching control for each.
 *
 * SECRET fields never come back with a value, only a "set" flag. An untouched secret is therefore
 * omitted from the save entirely (the server keeps what it has); clearing one means sending "".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceConfigSheet(
    api: KodexApi,
    baseUrl: String,
    apiKey: String,
    providerId: String,
    onDismiss: () -> Unit,
    /** Called with a message to show once saving finishes, successfully or not. */
    onSaved: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<SourceConfigDto?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Only fields the user actually edited land here, which is what keeps untouched secrets untouched.
    val edited = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(providerId) {
        runCatching { api.sourceConfig(baseUrl, apiKey, providerId) }
            .onSuccess { config = it }
            .onFailure { failed = true }
    }

    ModalBottomSheet(modifier = Modifier.heightIn(max = sheetMaxHeight()), onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            val current = config
            Text(
                current?.displayName?.ifBlank { providerId } ?: providerId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            when {
                failed -> Text(
                    "This source has no settings, or they couldn't be loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                current == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }

                current.fields.isEmpty() -> Text(
                    "This source has nothing to configure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    current.fields.forEach { field ->
                        ConfigField(
                            field = field,
                            value = edited[field.key] ?: field.value,
                            onChange = { edited[field.key] = it },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !busy) { Text("Cancel") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { api.saveSourceConfig(baseUrl, apiKey, providerId, edited.toMap()) }
                                        .fold(
                                            onSuccess = { onSaved("Settings saved") },
                                            onFailure = { onSaved(it.friendlyMessage()) },
                                        )
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfigField(field: ConfigFieldDto, value: String, onChange: (String) -> Unit) {
    val label = field.label.ifBlank { field.key } + if (field.required) " *" else ""
    when (field.type.uppercase()) {
        "BOOLEAN" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = value.equals("true", ignoreCase = true), onCheckedChange = { onChange(it.toString()) })
        }

        "ENUM" -> {
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Box {
                    TextButton(onClick = { open = true }) { Text(value.ifBlank { field.defaultValue.orEmpty().ifBlank { "Choose" } }) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        field.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onChange(option) })
                        }
                    }
                }
            }
        }

        "SECRET" -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label) },
            supportingText = { Text(if (field.secretSet) "Stored — type to replace, or clear to remove" else "Not set") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        "INTEGER" -> OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '-' }) },
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label) },
            supportingText = field.defaultValue?.takeIf { it.isNotBlank() }?.let { { Text("Default: $it") } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
