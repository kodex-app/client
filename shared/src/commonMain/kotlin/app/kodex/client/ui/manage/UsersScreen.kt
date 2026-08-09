package app.kodex.client.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import app.kodex.client.auth.SessionManager
import app.kodex.client.network.CreateUserRequest
import app.kodex.client.network.KodexApi
import app.kodex.client.network.UpdateUserLimitsRequest
import app.kodex.client.network.UserDto
import app.kodex.client.ui.catalog.ColorBadge
import app.kodex.client.ui.collectAsStateSafe
import app.kodex.client.ui.friendlyMessage
import app.kodex.client.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** The roles a user can hold, in increasing order of privilege. */
private val ROLES = listOf("USER", "MANAGER", "ADMIN")

/**
 * Admin user management: who exists, their roles and per-user WEB limits, plus create, reset password,
 * clear second factor and delete.
 *
 * You cannot delete yourself — the server rejects it, and offering the action would be a trap, so the
 * row for the signed-in account simply omits it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(session: SessionManager, api: KodexApi, onBack: () -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    val me by session.currentUser.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<UserDto>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var editingLimits by remember { mutableStateOf<UserDto?>(null) }
    var resettingPassword by remember { mutableStateOf<UserDto?>(null) }
    var confirmDelete by remember { mutableStateOf<UserDto?>(null) }

    LaunchedEffect(server?.id, reload) {
        val s = server ?: return@LaunchedEffect
        users = runCatching { api.users(s.baseUrl, s.apiKey) }.getOrNull()
    }

    /** Runs an admin action, reporting the server's own reason on failure (duplicate email, weak password…). */
    fun act(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.fold(
                onSuccess = { snackbar?.show(message); reload++ },
                onFailure = { snackbar?.show(it.friendlyMessage()) },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New user") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val list = users) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            isSelf = user.id == me?.id,
                            onEditLimits = { editingLimits = user },
                            onResetPassword = { resettingPassword = user },
                            onResetTotp = {
                                val s = server ?: return@UserRow
                                act("Second factor cleared") { api.resetUserTotp(s.baseUrl, s.apiKey, user.id) }
                            },
                            onDelete = { confirmDelete = user },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    if (creating) {
        CreateUserDialog(onDismiss = { creating = false }) { email, password, roles ->
            creating = false
            val s = server ?: return@CreateUserDialog
            act("User created") { api.createUser(s.baseUrl, s.apiKey, CreateUserRequest(email, password, roles)) }
        }
    }

    editingLimits?.let { target ->
        LimitsDialog(target, onDismiss = { editingLimits = null }) { request ->
            editingLimits = null
            val s = server ?: return@LimitsDialog
            act("Limits saved") { api.updateUserLimits(s.baseUrl, s.apiKey, target.id, request) }
        }
    }

    resettingPassword?.let { target ->
        PasswordDialog(
            title = "Reset password",
            subtitle = "Set a new password for ${target.email}.",
            onDismiss = { resettingPassword = null },
        ) { password ->
            resettingPassword = null
            val s = server ?: return@PasswordDialog
            act("Password reset") { api.resetUserPassword(s.baseUrl, s.apiKey, target.id, password) }
        }
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete user?") },
            text = { Text("${target.email} loses access immediately. Their reading progress is deleted with them.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    val s = server ?: return@TextButton
                    act("User deleted") { api.deleteUser(s.baseUrl, s.apiKey, target.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UserRow(
    user: UserDto,
    isSelf: Boolean,
    onEditLimits: () -> Unit,
    onResetPassword: () -> Unit,
    onResetTotp: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isSelf) "${user.email} (you)" else user.email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(limitsSummary(user), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                user.roles.sortedBy { ROLES.indexOf(it) }.forEach { ColorBadge(it) }
                // Admins bypass the stored flag, so show the effective permission, not the column.
                if (user.isAdmin || user.allowAdultContent) ColorBadge("Adult OK")
                if (user.totpEnabled) ColorBadge("2FA")
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "User actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit limits") }, onClick = { menu = false; onEditLimits() })
                DropdownMenuItem(
                    text = { Text("Reset password") },
                    onClick = { menu = false; onResetPassword() },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                )
                if (user.totpEnabled) {
                    DropdownMenuItem(text = { Text("Clear second factor") }, onClick = { menu = false; onResetTotp() })
                }
                if (!isSelf) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }
        }
    }
}

/** "WEB libraries: 3 · items: 500" — 0 means unlimited, which is what the server treats it as. */
private fun limitsSummary(user: UserDto): String {
    fun cap(n: Int) = if (n <= 0) "unlimited" else n.toString()
    return "WEB libraries: ${cap(user.webLibraryLimit)} · items per library: ${cap(user.webLibraryItemLimit)}"
}

@Composable
private fun CreateUserDialog(onDismiss: () -> Unit, onConfirm: (String, String, Set<String>) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf(setOf("USER")) }
    // Mirrors the server's own validation, so the dialog rejects what the API would reject anyway.
    val valid = email.contains("@") && password.length >= 8 && roles.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New user") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    supportingText = { Text("At least 8 characters") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Roles", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ROLES.forEach { role ->
                        FilterChip(
                            selected = role in roles,
                            onClick = { roles = if (role in roles) roles - role else roles + role },
                            label = { Text(role) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(email.trim(), password, roles) }, enabled = valid) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Per-user caps on WEB libraries. Blank means "leave unchanged" — the server ignores null fields. */
@Composable
private fun LimitsDialog(user: UserDto, onDismiss: () -> Unit, onConfirm: (UpdateUserLimitsRequest) -> Unit) {
    var libraries by remember { mutableStateOf(user.webLibraryLimit.toString()) }
    var items by remember { mutableStateOf(user.webLibraryItemLimit.toString()) }
    var adult by remember { mutableStateOf(user.allowAdultContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limits for ${user.email}") },
        text = {
            Column {
                OutlinedTextField(
                    value = libraries,
                    onValueChange = { libraries = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    label = { Text("WEB libraries") },
                    supportingText = { Text("0 = unlimited") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    label = { Text("Items per WEB library") },
                    supportingText = { Text("0 = unlimited") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow adult content", style = MaterialTheme.typography.bodyLarge)
                        if (user.isAdmin) {
                            Text(
                                "Admins are allowed regardless of this setting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = adult, onCheckedChange = { adult = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    UpdateUserLimitsRequest(
                        webLibraryLimit = libraries.toIntOrNull(),
                        webLibraryItemLimit = items.toIntOrNull(),
                        allowAdultContent = adult,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A single new-password field with the server's 8-character minimum enforced up front. */
@Composable
internal fun PasswordDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = password.length >= 8 && password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("New password") },
                    supportingText = { Text("At least 8 characters") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    singleLine = true,
                    label = { Text("Repeat password") },
                    isError = confirm.isNotEmpty() && confirm != password,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
