package dev.icedtea.kodex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.LocalLibraryLock
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.UpdateLockRequest
import dev.icedtea.kodex.network.libraries
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.library.PinEntry
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

private val RELOCK_OPTIONS = listOf(0, 1, 5, 15, 60)
private fun relockLabel(m: Int) = if (m == 0) "Immediately" else "$m min"

/**
 * The library PIN lock, as stored on the server (shared with the web UI and the user's other devices):
 * set it up, pick the libraries it locks and how soon it relocks, change or remove the PIN.
 */
@Composable
fun LibraryLockSection(session: SessionManager, api: KodexApi) {
    val lock = LocalLibraryLock.current
    val status by lock.status.collectAsStateSafe()
    val token by lock.token.collectAsStateSafe()
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var libraries by remember { mutableStateOf<List<LibraryDto>>(emptyList()) }
    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        libraries = runCatching { api.libraries(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
    }
    var pinDialog by remember { mutableStateOf<String?>(null) } // "setup" | "change"
    var confirmRemove by remember { mutableStateOf(false) }

    fun act(message: String?, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { message?.let { snackbar?.show(it) } },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
        }
    }

    val enabled = status?.enabled == true
    val unlocked = !enabled || token != null
    val locked = status?.lockedLibraryIds.orEmpty()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !enabled -> {
                    Text(
                        "Keep chosen libraries out of Home, search, history and every list until you enter a PIN. " +
                            "Saved on the server, so it applies on all your devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { pinDialog = "setup" }) { Text("Set up PIN") }
                }

                !unlocked -> {
                    Text("PIN lock is on (${locked.size} libraries). Enter the PIN to change these settings.", style = MaterialTheme.typography.bodyMedium)
                    PinEntry(Modifier.fillMaxWidth(), submit = { lock.unlock(it) })
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PIN lock is on. Locked libraries open one at a time when you visit them.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { lock.lockNow() }) { Text("Lock all") }
                    }
                    Text("Libraries locked by the PIN", style = MaterialTheme.typography.labelLarge)
                    libraries.forEach { lib ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(lib.name, Modifier.weight(1f))
                            if (lib.id in locked && lib.id in status?.unlockedLibraryIds.orEmpty()) {
                                TextButton(onClick = { act(null) { lock.lockLibrary(lib.id) } }) { Text("Lock again") }
                            }
                            Switch(
                                checked = lib.id in locked,
                                onCheckedChange = { on -> act(null) { lock.setLibraryLocked(lib.id, on) } },
                            )
                        }
                    }
                    Text("Lock again after the app is in the background for", style = MaterialTheme.typography.labelLarge)
                    RelockChips(status?.relockAfterMinutes ?: 5) { m -> act(null) { lock.update(UpdateLockRequest(relockAfterMinutes = m)) } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lock the whole app")
                            Text(
                                "Ask for the PIN every time you come back to the app, and in the web UI after a while without activity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = status?.appLock == true,
                            onCheckedChange = { on -> act(null) { lock.update(UpdateLockRequest(appLock = on)) } },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pinDialog = "change" }) { Text("Change PIN") }
                        TextButton(onClick = { confirmRemove = true }) { Text("Remove PIN", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    pinDialog?.let { mode ->
        PinSetupDialog(
            setup = mode == "setup",
            libraries = libraries,
            onDismiss = { pinDialog = null },
            onSave = { pin, ids, relock ->
                pinDialog = null
                if (mode == "setup") act("Library PIN set") { lock.enable(pin, ids, relock) }
                else act("PIN changed") { lock.update(UpdateLockRequest(newPin = pin)) }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the library PIN?") },
            text = { Text("All libraries will be unlocked on every device.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; act("Library PIN removed") { lock.disable() } }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RelockChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RELOCK_OPTIONS.forEach { m ->
            FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(relockLabel(m)) })
        }
    }
}

@Composable
private fun PinSetupDialog(
    setup: Boolean,
    libraries: List<LibraryDto>,
    onDismiss: () -> Unit,
    onSave: (pin: String, ids: List<String>, relockAfterMinutes: Int) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var ids by remember { mutableStateOf(setOf<String>()) }
    var relock by remember { mutableIntStateOf(5) }
    val valid = pin.length in 4..8 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setup) "Set up PIN" else "Change PIN") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4 to 8 digits.", style = MaterialTheme.typography.bodySmall)
                PinField(pin, { pin = it }, "New PIN")
                PinField(confirm, { confirm = it }, "Confirm PIN", isError = confirm.isNotEmpty() && confirm != pin)
                if (setup) {
                    Spacer(Modifier.height(4.dp))
                    Text("Libraries to lock", style = MaterialTheme.typography.labelLarge)
                    libraries.forEach { lib ->
                        Row(
                            Modifier.fillMaxWidth().clickable { ids = if (lib.id in ids) ids - lib.id else ids + lib.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = lib.id in ids, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(lib.name)
                        }
                    }
                    Text("Relock after", style = MaterialTheme.typography.labelLarge)
                    RelockChips(relock) { relock = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pin, ids.toList(), relock) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
