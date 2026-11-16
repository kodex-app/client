package dev.icedtea.kodex.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.icedtea.kodex.platform.rememberUrlOpener
import dev.icedtea.kodex.ui.collectAsStateSafe

/** The "update available" dialog; shown app-wide while [AppUpdateController.prompt] is set. */
@Composable
fun UpdateDialog(controller: AppUpdateController) {
    val prompt by controller.prompt.collectAsStateSafe()
    val state by controller.state.collectAsStateSafe()
    if (!prompt) return
    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.NeedsPermission -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    } ?: return
    val downloading = state as? UpdateState.Downloading
    val openUrl = rememberUrlOpener()

    AlertDialog(
        onDismissRequest = controller::dismiss,
        properties = DialogProperties(dismissOnClickOutside = downloading == null),
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${controller.build.versionName} → ${update.version}" + sizeLabel(update.apkSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.notes.isNotEmpty()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        val p = s.progress
                        if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (p == null) "Downloading…" else "Downloading… ${(p * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "Allow Kodex to install apps in the settings that just opened, then tap Update again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateState.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = controller::install, enabled = downloading == null) {
                Text(if (state is UpdateState.Failed) "Retry" else "Update")
            }
        },
        dismissButton = {
            if (downloading == null) {
                // Emitted straight into the dialog's button row alongside Update.
                if (update.pageUrl.isNotEmpty()) TextButton(onClick = { openUrl(update.pageUrl) }) { Text("Notes") }
                TextButton(onClick = controller::skip) { Text("Skip") }
                TextButton(onClick = controller::dismiss) { Text("Later") }
            }
        },
    )
}

/** One-line status for the About screen's "Check for updates" row. */
@Composable
fun updateStatusText(controller: AppUpdateController): String {
    val state by controller.state.collectAsStateSafe()
    return when (val s = state) {
        UpdateState.Idle -> "Checks GitHub (${controller.build.channel?.id} channel)"
        UpdateState.Checking -> "Checking…"
        UpdateState.UpToDate -> "You're on the latest build"
        is UpdateState.Available -> "${s.update.version} is available"
        is UpdateState.Downloading -> "Downloading ${s.update.version}…"
        is UpdateState.NeedsPermission -> "Waiting for install permission"
        is UpdateState.Failed -> s.message
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0) "" else " · ${(bytes / 1024 / 102.4).toInt() / 10.0} MB"
