package app.kodex.client.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kodex.client.auth.SessionManager
import app.kodex.client.ui.collectAsStateSafe

/**
 * "More" is the app's hub: account/server summary, entry points to Downloads / Settings / About, and
 * the multi-server controls. Signing out returns to the login screen (all saved servers are kept).
 */
@Composable
fun MoreTab(
    session: SessionManager,
    appSettings: app.kodex.client.data.AppSettings,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditServer: () -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLibraries: () -> Unit = {},
    onOpenLabels: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
) {
    val server by session.activeServer.collectAsStateSafe()
    val user by session.currentUser.collectAsStateSafe()
    val incognito by appSettings.incognitoMode.collectAsStateSafe()
    val isManager = user?.let { it.isAdmin || it.isManager } == true
    val isAdmin = user?.isAdmin == true

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Column(Modifier.size(48.dp)) {}
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        server?.label ?: "Not signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    server?.let {
                        Text(
                            it.displayHost,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    user?.let {
                        Text(
                            it.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (it.roles.isNotEmpty()) {
                            Text(
                                it.roles.joinToString(" · ") { r -> r.lowercase().replaceFirstChar(Char::uppercase) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                // Edit the connection you're signed in to (name, address, account).
                server?.let {
                    IconButton(onClick = onEditServer) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit server connection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Global incognito reading toggle — when on, no reader saves progress or history.
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(app.kodex.client.ui.icons.IncognitoIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Incognito mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("Don't save reading progress or history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = incognito, onCheckedChange = { appSettings.setIncognitoMode(it) })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column {
                HubRow(app.kodex.client.ui.icons.DownloadIcon, "Downloads", "Active and finished chapter downloads", onOpenDownloads)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Filled.Settings, "Settings", "Series behaviour and reader defaults", onOpenSettings)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Filled.Star, "Appearance", "Theme, colours, dark mode", onOpenAppearance)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Filled.Info, "About", "Version and licence", onOpenAbout)
            }
        }

        if (isManager) {
            Text(
                "Manage",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Card(Modifier.fillMaxWidth()) {
                Column {
                    HubRow(Icons.AutoMirrored.Filled.List, "Libraries", "Add, edit, refresh, delete libraries", onOpenLibraries)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Filled.Star, "Labels", "Create and manage metadata labels", onOpenLabels)
                    if (isAdmin) {
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                        HubRow(Icons.Filled.Settings, "Plugins", "Install and manage content sources", onOpenPlugins)
                    }
                }
            }
        }

        OutlinedButton(onClick = { session.signOut() }, modifier = Modifier.fillMaxWidth()) {
            Text("Switch or add server")
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
