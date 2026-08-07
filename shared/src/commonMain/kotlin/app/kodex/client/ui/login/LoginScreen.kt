package app.kodex.client.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.data.model.ServerConnection
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import kotlinx.coroutines.launch

/**
 * Entry screen: pick a previously-added server or add a new one. Saved servers are listed
 * most-recent-first (the top one is what launch auto-selects); adding a server mints an API key
 * from email + password and signs straight in.
 */
@Composable
fun LoginScreen(session: SessionManager) {
    val servers by session.servers.collectAsStateSafe()
    val scope = rememberCoroutineScope()

    var showForm by remember { mutableStateOf(servers.isEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandHeader()
                Spacer(Modifier.height(28.dp))

                if (servers.isNotEmpty() && !showForm) {
                    ServerPicker(
                        servers = servers,
                        busy = busy,
                        onSelect = { server ->
                            error = null
                            busy = true
                            scope.launch {
                                session.selectServer(server)
                                    .onFailure { error = it.friendlyMessage() }
                                busy = false
                            }
                        },
                        onRemove = { session.removeServer(it) },
                        onAddNew = { error = null; showForm = true },
                    )
                } else {
                    AddServerForm(
                        busy = busy,
                        canCancel = servers.isNotEmpty(),
                        onCancel = { error = null; showForm = false },
                        onSubmit = { label, url, email, password ->
                            error = null
                            busy = true
                            scope.launch {
                                session.addServer(label, url, email, password)
                                    .onFailure { error = it.friendlyMessage() }
                                busy = false
                            }
                        },
                    )
                }

                error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Text(
                    "K",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Kodex", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Sign in to your media server",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ServerPicker(
    servers: List<ServerConnection>,
    busy: Boolean,
    onSelect: (ServerConnection) -> Unit,
    onRemove: (ServerConnection) -> Unit,
    onAddNew: () -> Unit,
) {
    Text(
        "Your servers",
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    servers.forEachIndexed { index, server ->
        ServerCard(
            server = server,
            highlight = index == 0,
            enabled = !busy,
            onClick = { onSelect(server) },
            onRemove = { onRemove(server) },
        )
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onAddNew, enabled = !busy) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Add another server")
    }
}

@Composable
private fun ServerCard(
    server: ServerConnection,
    highlight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        server.label.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    server.displayHost,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    server.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "Remove ${server.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddServerForm(
    busy: Boolean,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSubmit: (label: String, url: String, email: String, password: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSubmit = url.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !busy

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add a server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("https://media.example.com") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onSubmit(label, url, email, password) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Sign in")
                }
            }
            if (canCancel) {
                TextButton(
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Back to saved servers") }
            }
        }
    }
}
