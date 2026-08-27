package dev.icedtea.kodex.ui.main

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
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
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.ui.collectAsStateSafe

/**
 * "More" is the app's hub: account/server summary, entry points to Downloads / Settings / About, and
 * the multi-server controls. Signing out returns to the login screen (all saved servers are kept).
 */
@Composable
fun MoreTab(
    session: SessionManager,
    appSettings: dev.icedtea.kodex.data.AppSettings,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditServer: () -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLibraries: () -> Unit = {},
    onOpenLabels: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenUsers: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenServerActions: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
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
                            Icons.Outlined.Edit,
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
                Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                HubRow(Icons.Outlined.Download, "Downloads", "Active and finished chapter downloads", onOpenDownloads)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Outlined.Settings, "Settings", "Series behaviour and reader defaults", onOpenSettings)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Outlined.Lock, "Security", "Password and two-factor authentication", onOpenSecurity)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Outlined.Palette, "Appearance", "Theme, colours, dark mode", onOpenAppearance)
                HorizontalDivider(Modifier.padding(start = 56.dp))
                HubRow(Icons.Outlined.Info, "About", "Version and licence", onOpenAbout)
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
                    HubRow(Icons.AutoMirrored.Filled.LibraryBooks, "Libraries", "Add, edit, refresh, delete libraries", onOpenLibraries)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Outlined.LocalOffer, "Labels", "Create and manage metadata labels", onOpenLabels)
                    if (isAdmin) {
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                        HubRow(Icons.Outlined.Extension, "Plugins", "Install and manage content sources", onOpenPlugins)
                    }
                }
            }
        }

        // Server administration, admin-only: everything here affects the whole server, not this account.
        if (isAdmin) {
            Text(
                "Server",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Card(Modifier.fillMaxWidth()) {
                Column {
                    HubRow(Icons.Outlined.Group, "Users", "Accounts, roles, limits and passwords", onOpenUsers)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Outlined.Schedule, "Tasks", "The background queue", onOpenTasks)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Outlined.Backup, "Backup", "Stored archives and the auto-backup schedule", onOpenBackup)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Outlined.Wifi, "Network", "Proxy, DNS over HTTPS, Cloudflare solver", onOpenNetwork)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.AutoMirrored.Outlined.Article, "Logs", "Recent server log output", onOpenLogs)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    HubRow(Icons.Outlined.Build, "Server actions", "Refresh all, deep scan, shut down", onOpenServerActions)
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
