package dev.icedtea.kodex.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.nav.retain
import dev.icedtea.kodex.ui.theme.ThemeMode

/**
 * "More" is the app's hub: who you're signed in as and where, the two app-wide quick toggles
 * (incognito, light/dark), then grouped entry points — your account's pages, and for managers and
 * admins the Manage and Server sections. Signing out returns to the login screen (all saved servers
 * are kept). Inset groups with tinted icon tiles, matching the web UI's More page.
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
    onOpenMihonExtensions: () -> Unit = {},
    onOpenLnReaderPlugins: () -> Unit = {},
    onOpenMetadataProviders: () -> Unit = {},
    onOpenExtensionRepositories: () -> Unit = {},
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
    val themeMode by appSettings.themeMode.collectAsStateSafe()
    val isManager = user?.let { it.isAdmin || it.isManager } == true
    val isAdmin = user?.isAdmin == true
    // Retained, not remembered: opening any row unmounts this tab, so a plain rememberScrollState
    // would send you back to the top of the hub on every return. See RetainedState.kt.
    val scroll = retain("more:scroll") { ScrollState(0) }

    Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
    Column(
        // Capped and centred so the hub stays a column of rows on a tablet or the desktop window.
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ProfileCard(
            initial = (user?.email ?: server?.label).orEmpty().firstOrNull()?.uppercaseChar() ?: '?',
            title = user?.email ?: server?.label ?: "Not signed in",
            serverLine = server?.let { "${it.label} · ${it.displayHost}" },
            role = user?.let { if (it.isAdmin) "Administrator" else if (it.isManager) "Manager" else "Reader" },
            onEdit = server?.let { onEditServer },
        )

        SectionLabel("Quick settings")
        HubGroup {
            // Global incognito reading toggle — when on, no reader saves progress or history.
            HubRow(
                Icons.Outlined.VisibilityOff, TileIndigo, "Incognito mode", "Don't save reading progress or history",
                onClick = { appSettings.setIncognitoMode(!incognito) },
            ) {
                Switch(checked = incognito, onCheckedChange = { appSettings.setIncognitoMode(it) })
            }
            RowDivider()
            HubRow(
                if (themeMode == ThemeMode.DARK) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                TileAmber, "Theme", null, onClick = null,
            )
            // All three choices in view, under the label; colours, AMOLED and Monet live one tap away.
            ThemePicker(themeMode) { appSettings.setThemeMode(it) }
            RowDivider()
            HubRow(Icons.Outlined.Palette, TilePink, "Appearance", "Colour theme, AMOLED, dynamic colour", onOpenAppearance)
        }

        SectionLabel("Account")
        HubGroup {
            HubRow(Icons.Outlined.Download, TileBlue, "Downloads", "Active and finished chapter downloads", onOpenDownloads)
            RowDivider()
            HubRow(Icons.Outlined.Settings, TileSlate, "Settings", "Series behaviour and reader defaults", onOpenSettings)
            RowDivider()
            HubRow(Icons.Outlined.Lock, TileGreen, "Security", "Password and two-factor authentication", onOpenSecurity)
        }

        if (isManager) {
            SectionLabel("Manage")
            HubGroup {
                HubRow(Icons.AutoMirrored.Filled.LibraryBooks, TileTeal, "Libraries", "Add, edit, refresh, delete libraries", onOpenLibraries)
                RowDivider()
                HubRow(Icons.Outlined.LocalOffer, TileOrange, "Labels", "Create and manage metadata labels", onOpenLabels)
                if (isAdmin) {
                    RowDivider()
                    HubRow(Icons.Outlined.Extension, TilePurple, "Mihon extensions", "Comic and manga sources", onOpenMihonExtensions)
                    RowDivider()
                    HubRow(Icons.AutoMirrored.Outlined.MenuBook, TileIndigo, "LNReader plugins", "Novel sources", onOpenLnReaderPlugins)
                    RowDivider()
                    HubRow(Icons.Outlined.Source, TileCyan, "Metadata providers", "AniList, ComicVine, MangaUpdates and more", onOpenMetadataProviders)
                    RowDivider()
                    HubRow(Icons.Outlined.Storage, TileSlate, "Extension repositories", "Where the Mihon and LNReader lists come from", onOpenExtensionRepositories)
                }
            }
        }

        // Server administration, admin-only: everything here affects the whole server, not this account.
        if (isAdmin) {
            SectionLabel("Server")
            HubGroup {
                HubRow(Icons.Outlined.Group, TileBlue, "Users", "Accounts, roles, limits and passwords", onOpenUsers)
                RowDivider()
                HubRow(Icons.Outlined.Schedule, TileOrange, "Tasks", "The background queue", onOpenTasks)
                RowDivider()
                HubRow(Icons.Outlined.Backup, TileGreen, "Backup", "Stored archives and the auto-backup schedule", onOpenBackup)
                RowDivider()
                HubRow(Icons.Outlined.Wifi, TileCyan, "Network", "Proxy, DNS over HTTPS, Cloudflare solver", onOpenNetwork)
                RowDivider()
                HubRow(Icons.AutoMirrored.Outlined.Article, TileSlate, "Logs", "Recent server log output", onOpenLogs)
                RowDivider()
                HubRow(Icons.Outlined.Build, TileRed, "Server actions", "Refresh all, deep scan, shut down", onOpenServerActions)
            }
        }

        SectionLabel("App")
        HubGroup {
            HubRow(Icons.Outlined.Info, TileSlate, "About", "Version and licence", onOpenAbout)
        }

        // Always reachable, even when every request is failing: this is the way out of a dead
        // session without wiping the app. The connection stays saved for the sign-in screen.
        OutlinedButton(
            onClick = { session.signOut() },
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
        Text(
            "Saved servers are kept — switch or add one from the sign-in screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    }
}

/** Account card: initial avatar on a primary gradient, the account, the server it's on, and its role. */
@Composable
private fun ProfileCard(initial: Char, title: String, serverLine: String?, role: String?, onEdit: (() -> Unit)?) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(primary.copy(alpha = 0.09f))
            .border(1.dp, primary.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial.toString(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            serverLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            role?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 6.dp)
                        .border(1.dp, primary, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }
        }
        // Edit the connection you're signed in to (name, address, account).
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit server connection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** An inset group: rows on one rounded surface, divided by hairlines that start after the icon tile. */
@Composable
private fun HubGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 62.dp)
            .heightIn(min = 1.dp, max = 1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * One hub row: tinted icon tile, title and optional subtitle, then [trailing] — a chevron by default
 * when the row navigates. A null [onClick] makes it a plain label row (the theme picker's heading).
 */
@Composable
private fun HubRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = if (onClick != null) {
        { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
    } else null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** System / Light / Dark as three equal tiles, the current one outlined in the primary colour. */
@Composable
private fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.SettingsSuggest),
            Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
            Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
        ).forEach { (mode, label, icon) ->
            val active = mode == current
            val primary = MaterialTheme.colorScheme.primary
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.5.dp, if (active) primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onPick(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, contentDescription = null, tint = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// Icon-tile tints. Fixed rather than themed: they identify the destination, like the web's tiles.
private val TileBlue = Color(0xFF3B82F6)
private val TileTeal = Color(0xFF14B8A6)
private val TileCyan = Color(0xFF06B6D4)
private val TileGreen = Color(0xFF22C55E)
private val TileOrange = Color(0xFFF97316)
private val TileAmber = Color(0xFFF59E0B)
private val TileRed = Color(0xFFEF4444)
private val TilePink = Color(0xFFEC4899)
private val TilePurple = Color(0xFFA855F7)
private val TileIndigo = Color(0xFF6366F1)
private val TileSlate = Color(0xFF64748B)
