package app.kodex.client.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.KodexApi
import app.kodex.client.network.TotpEnrollmentDto
import app.kodex.client.platform.rememberUrlOpener
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * This account's security: change the password, and turn the second factor on or off.
 *
 * Enrollment shows the shared secret and hands the `otpauth://` URI to the system rather than drawing
 * a QR code — on a phone the authenticator is on the same device, so a tap that opens it beats a code
 * you would have to scan with a second device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val user by session.currentUser.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlOpener()

    var enrollment by remember { mutableStateOf<TotpEnrollmentDto?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    // Disabling also needs a current code, so it reuses the same field rather than a second dialog.
    var disabling by remember { mutableStateOf(false) }

    val enabled = user?.totpEnabled == true

    // Leaving enrollment half-done (or having it activated) should not keep a stale secret on screen.
    LaunchedEffect(enabled) {
        if (enabled) {
            enrollment = null
            code = ""
        }
    }

    fun run(message: String, block: suspend (String, String) -> Unit) {
        val s = server ?: return
        busy = true
        scope.launch {
            runCatching { block(s.baseUrl, s.apiKey) }.fold(
                onSuccess = {
                    snackbar?.show(message)
                    code = ""
                    disabling = false
                    // The flag lives on the user record, so re-read it rather than guessing locally.
                    runCatching { session.refreshCurrentUser() }
                },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SectionHeader("Password")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        user?.email.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { changingPassword = true }, enabled = !busy) { Text("Change password") }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Two-factor authentication")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (enabled) "On — sign-in asks for a code from your authenticator."
                        else "Off — sign-in needs only your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Applies to signing in with your password. This app stays connected with its API key, which is not a second-factor path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    when {
                        enabled && disabling -> {
                            CodeField(code, onChange = { code = it }, label = "Code from your authenticator")
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { run("Two-factor turned off") { b, k -> api.totpDisable(b, k, code) } },
                                    enabled = !busy && code.length == 6,
                                ) { Text("Turn off") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { disabling = false; code = "" }, enabled = !busy) { Text("Cancel") }
                            }
                        }

                        enabled -> OutlinedButton(onClick = { disabling = true }, enabled = !busy) { Text("Turn off") }

                        enrollment != null -> {
                            val e = enrollment!!
                            Text("1. Add this account to your authenticator", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { openUrl(e.otpauthUri) }, enabled = !busy) { Text("Open in authenticator app") }
                            Spacer(Modifier.height(8.dp))
                            Text("Or enter this key by hand:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(e.secret, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("2. Enter the code it shows", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            CodeField(code, onChange = { code = it }, label = "6-digit code")
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { run("Two-factor turned on") { b, k -> api.totpActivate(b, k, code) } },
                                    enabled = !busy && code.length == 6,
                                ) { Text("Turn on") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { enrollment = null; code = "" }, enabled = !busy) { Text("Cancel") }
                            }
                        }

                        else -> Button(
                            onClick = {
                                val s = server ?: return@Button
                                busy = true
                                scope.launch {
                                    runCatching { api.totpEnroll(s.baseUrl, s.apiKey) }.fold(
                                        onSuccess = { enrollment = it },
                                        onFailure = { snackbar?.show(it.friendlyMessage()) },
                                    )
                                    busy = false
                                }
                            },
                            enabled = !busy,
                        ) { Text("Set up") }
                    }
                }
            }
        }
    }

    if (changingPassword) {
        ChangePasswordDialog(onDismiss = { changingPassword = false }) { current, new ->
            changingPassword = false
            run("Password changed") { b, k -> api.changeOwnPassword(b, k, current, new) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** Six digits, nothing else — the field filters rather than letting the server reject the submission. */
@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(6)) },
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = current.isNotBlank() && next.length >= 8 && next == repeat
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                PasswordField(current, { current = it }, "Current password")
                Spacer(Modifier.height(8.dp))
                PasswordField(next, { next = it }, "New password", supporting = "At least 8 characters")
                Spacer(Modifier.height(8.dp))
                PasswordField(repeat, { repeat = it }, "Repeat new password", isError = repeat.isNotEmpty() && repeat != next)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current, next) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
