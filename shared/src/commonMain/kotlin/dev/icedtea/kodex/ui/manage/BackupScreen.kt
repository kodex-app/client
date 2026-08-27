package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.network.BackupFileDto
import dev.icedtea.kodex.network.BackupSettingsDto
import dev.icedtea.kodex.network.BackupSettingsRequest
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.ui.InlineLoadError
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.relativeTime
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

private val FREQUENCIES = listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "CUSTOM" to "Custom")

/**
 * Backups the server keeps, plus the auto-backup schedule.
 *
 * Deliberately scoped to server-side archives: uploading an archive to restore and downloading one to
 * the device both need a file picker the app doesn't have, so those two stay on the web UI. Everything
 * that operates on what the server already holds — list, restore, delete, schedule — is here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<BackupFileDto>?>(null) }
    var settings by remember { mutableStateOf<BackupSettingsDto?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // Separate slots: the schedule and the stored-file list are fetched independently, so one
    // failing must not be masked by the other succeeding.
    var filesError by remember { mutableStateOf<String?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<BackupFileDto?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupFileDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        runCatching { api.backupFiles(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { files = it; filesError = null },
            onFailure = { filesError = it.friendlyMessage() },
        )
        runCatching { api.backupSettings(s.baseUrl, s.apiKey) }.fold(
            onSuccess = { settings = it; settingsError = null },
            onFailure = { settingsError = it.friendlyMessage() },
        )
    }

    fun act(message: String, block: suspend (String, String) -> Unit) {
        val s = server ?: return
        scope.launch {
            runCatching { block(s.baseUrl, s.apiKey) }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsSectionHeader("Automatic backups")
            when (val cfg = settings) {
                null -> if (settingsError != null) InlineLoadError(settingsError!!) { reload++ }
                    else CircularProgressIndicator(Modifier.padding(16.dp))
                else -> AutoBackupCard(cfg) { request ->
                    act("Schedule saved") { b, k -> api.saveBackupSettings(b, k, request) }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSectionHeader("Stored backups")
            Card(Modifier.fillMaxWidth()) {
                Column {
                    when (val list = files) {
                        null -> if (filesError != null) InlineLoadError(filesError!!) { reload++ }
                            else CircularProgressIndicator(Modifier.padding(16.dp))
                        else -> if (list.isEmpty()) {
                            Text(
                                "No backups on the server yet. One appears here after the first scheduled run.",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            list.forEachIndexed { i, file ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                                BackupFileRow(
                                    file,
                                    onRestore = { restoring = file },
                                    onDelete = { confirmDelete = file },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    restoring?.let { file ->
        RestoreDialog(file, onDismiss = { restoring = null }) { password ->
            restoring = null
            val s = server ?: return@RestoreDialog
            scope.launch {
                runCatching { api.restoreStoredBackup(s.baseUrl, s.apiKey, file.name, password) }.fold(
                    onSuccess = { snackbar?.show("Restore staged — restart the server to apply it") },
                    onFailure = { snackbar?.show(it.friendlyMessage()) },
                )
            }
        }
    }

    confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete backup?") },
            text = { Text("${file.name} is removed from the server. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act("Backup deleted") { b, k -> api.deleteBackupFile(b, k, file.name) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AutoBackupCard(initial: BackupSettingsDto, onSave: (BackupSettingsRequest) -> Unit) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var frequency by remember(initial) { mutableStateOf(initial.frequency) }
    var hours by remember(initial) { mutableStateOf(initial.customIntervalHours.toString()) }
    var thumbnails by remember(initial) { mutableStateOf(initial.includeThumbnails) }
    var keep by remember(initial) { mutableStateOf(initial.keepCount.toString()) }
    // Left blank the stored password is kept; the server distinguishes null (keep) from "" (clear).
    var password by remember(initial) { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Back up automatically", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (enabled) {
                Spacer(Modifier.height(12.dp))
                Text("How often", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    FREQUENCIES.forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (frequency == "CUSTOM") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        label = { Text("Every N hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keep,
                    onValueChange = { keep = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    label = { Text("Keep how many") },
                    supportingText = { Text("Older backups are pruned past this count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include thumbnails", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Much larger archives; they can be regenerated instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = thumbnails, onCheckedChange = { thumbnails = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password.orEmpty(),
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(if (initial.passwordSet) "Change encryption password" else "Encryption password (optional)") },
                    supportingText = {
                        Text(if (initial.passwordSet) "Leave blank to keep the current one" else "Blank leaves archives unencrypted")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSave(
                        BackupSettingsRequest(
                            enabled = enabled,
                            frequency = frequency,
                            customIntervalHours = hours.toIntOrNull() ?: initial.customIntervalHours,
                            includeThumbnails = thumbnails,
                            keepCount = keep.toIntOrNull() ?: initial.keepCount,
                            password = password,
                        ),
                    )
                },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun BackupFileRow(file: BackupFileDto, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(humanSize(file.size), file.createdAt?.let { relativeTime(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, contentDescription = "Restore") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RestoreDialog(file: BackupFileDto, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Column {
                Text(
                    "${file.name} replaces the server's current database when it next starts. " +
                        "Nothing changes until you restart the server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    supportingText = { Text("Only if this archive was encrypted") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password.ifBlank { null }) }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Byte count as MB/GB — archives are never small enough for bytes or kB to be useful. */
private fun humanSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "${((mb / 1024) * 10).toLong() / 10.0} GB" else "${(mb * 10).toLong() / 10.0} MB"
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
