package dev.icedtea.kodex.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.update.AppUpdateController
import dev.icedtea.kodex.update.LocalAppUpdate
import dev.icedtea.kodex.update.UpdateState
import dev.icedtea.kodex.update.updateStatusText

/** About: app identity, version, licence — plus self-update controls on GitHub builds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val updates = LocalAppUpdate.current
    val version = updates?.build?.versionName?.takeIf { it.isNotEmpty() } ?: "0.1.0"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Kodex", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Mobile client", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "A Compose Multiplatform client for the Kodex media server.\nLicensed under GPL-3.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "github.com/kodex-app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (updates != null && updates.enabled) UpdateSection(updates)
            }
        }
    }
}

@Composable
private fun UpdateSection(updates: AppUpdateController) {
    val state by updates.state.collectAsStateSafe()
    val auto by updates.autoCheck.collectAsStateSafe()
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 24.dp)) {
        HorizontalDivider()
        Column(
            Modifier.fillMaxWidth()
                .clickable(enabled = state != UpdateState.Checking && state !is UpdateState.Downloading) {
                    // Something already found: go straight to the dialog rather than re-checking.
                    if (state is UpdateState.Available || state is UpdateState.NeedsPermission) updates.reopen()
                    else updates.check(manual = true)
                }
                .padding(vertical = 12.dp),
        ) {
            Text("Check for updates", style = MaterialTheme.typography.bodyLarge)
            Text(
                updateStatusText(updates),
                style = MaterialTheme.typography.bodySmall,
                color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable { updates.setAutoCheck(!auto) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Check automatically", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Look for a new build when the app starts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = auto, onCheckedChange = updates::setAutoCheck)
        }
    }
}
