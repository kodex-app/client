package app.kodex.client.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.kodex.client.auth.SessionManager
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/**
 * Edit the connection you're signed in to: its display name, address, and the account it uses.
 *
 * The saved API key is bound to the server that issued it, so moving the address requires the
 * password to mint a new one. A rename alone needs no password and keeps the existing key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectionScreen(session: SessionManager, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val current = server
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    if (current == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Server connection", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Not signed in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Seeded per connection so switching servers behind this screen re-seeds the form.
    var label by remember(current.id) { mutableStateOf(current.label) }
    var url by remember(current.id) { mutableStateOf(current.baseUrl) }
    var email by remember(current.id) { mutableStateOf(current.email) }
    var password by remember(current.id) { mutableStateOf("") }
    var saving by remember(current.id) { mutableStateOf(false) }
    var error by remember(current.id) { mutableStateOf<String?>(null) }

    val addressChanged = url.trim().trimEnd('/') != current.baseUrl.trimEnd('/')
    val emailChanged = email.trim() != current.email
    // Re-authentication is unavoidable when the address moves; it's optional otherwise.
    val passwordRequired = addressChanged
    val canSave = !saving && url.isNotBlank() && (!passwordRequired || password.isNotBlank())

    fun save() {
        error = null
        saving = true
        scope.launch {
            session.updateActiveServer(label.trim(), url.trim(), email.trim(), password)
                .onSuccess {
                    saving = false
                    snackbar?.show("Server connection updated")
                    onBack()
                }
                .onFailure {
                    saving = false
                    error = it.friendlyMessage()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server connection", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Display name") },
                supportingText = { Text("Shown in the server picker. Defaults to the host.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("https://kodex.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (passwordRequired) "Password" else "Password (optional)") },
                supportingText = {
                    Text(
                        when {
                            addressChanged ->
                                "The saved key only works on the old address, so a new one has to be minted."
                            emailChanged ->
                                "Signing in as a different account needs its password."
                            else ->
                                "Leave blank to keep the current key. Enter it to mint a fresh one."
                        },
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = { save() }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                }
                Text("Save")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Changes apply to this saved connection — your libraries and reading progress live on the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
