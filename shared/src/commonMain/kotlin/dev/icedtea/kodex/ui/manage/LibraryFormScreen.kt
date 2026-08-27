package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.auth.SessionManager
import dev.icedtea.kodex.data.LibraryNavPrefs
import dev.icedtea.kodex.data.loadLibraryNavPrefs
import dev.icedtea.kodex.data.saveLibraryNavPrefs
import dev.icedtea.kodex.network.CreateLibraryRequest
import dev.icedtea.kodex.network.DirectoryListing
import dev.icedtea.kodex.network.KodexApi
import dev.icedtea.kodex.network.LibraryDto
import dev.icedtea.kodex.network.RefreshSettingsDto
import dev.icedtea.kodex.network.SourceDescriptor
import dev.icedtea.kodex.network.UpdateLibraryRequest
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import dev.icedtea.kodex.ui.rememberSnackbar
import kotlinx.coroutines.launch

/** Refresh cadences the server accepts, with their labels. */
private val REFRESH_INTERVALS = listOf(
    "NONE" to "Never",
    "EVERY_3H" to "Every 3 hours",
    "EVERY_6H" to "Every 6 hours",
    "EVERY_12H" to "Every 12 hours",
    "EVERY_24H" to "Daily",
    "WEEKLY" to "Weekly",
)

/**
 * Create or edit a library, covering what the web's form does: identity, location, per-user
 * visibility, the refresh schedule, and — for LOCAL — what a scan indexes.
 *
 * Laid out as one scrolling form with section headings rather than the web's stepper: a wizard earns
 * its keep on a wide screen, but on a phone it only hides fields behind extra taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFormScreen(
    session: SessionManager,
    api: KodexApi,
    existing: LibraryDto?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val server by session.activeServer.collectAsStateSafe()
    val snackbar = rememberSnackbar()
    val scope = rememberCoroutineScope()
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "LOCAL") }
    var mediaKind by remember { mutableStateOf(existing?.mediaKind ?: "COMIC") }
    var root by remember { mutableStateOf(existing?.root ?: "") }
    var sourceId by remember { mutableStateOf(existing?.contentSourceId ?: "") }
    var sources by remember { mutableStateOf<List<SourceDescriptor>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Refresh + scan settings seeded from the server's own values, so saving an edit never silently
    // resets something that was configured elsewhere.
    var refreshInterval by remember { mutableStateOf(existing?.refreshInterval ?: "EVERY_6H") }
    var refreshOnStartup by remember { mutableStateOf(existing?.refreshOnStartup ?: false) }
    var forceModTime by remember { mutableStateOf(existing?.scanForceModifiedTime ?: false) }
    var scanCbx by remember { mutableStateOf(existing?.scanCbx ?: true) }
    var scanPdf by remember { mutableStateOf(existing?.scanPdf ?: true) }
    var scanEpub by remember { mutableStateOf(existing?.scanEpub ?: true) }
    var autoDownload by remember { mutableStateOf(existing?.autoDownload ?: false) }
    var exclusions by remember { mutableStateOf((existing?.scanDirectoryExclusions ?: emptySet()).joinToString(", ")) }
    var specialFolders by remember { mutableStateOf((existing?.specialFolders ?: emptySet()).joinToString(", ")) }

    // Visibility is a per-user view preference in nav.libraries, not a field on the Library record —
    // the same store the Libraries screen toggles, so the two can't disagree.
    var navPrefs by remember { mutableStateOf(LibraryNavPrefs()) }
    var hideFromNav by remember { mutableStateOf(false) }
    var hideFromHome by remember { mutableStateOf(false) }

    LaunchedEffect(server?.id) {
        val s = server ?: return@LaunchedEffect
        sources = runCatching { api.contentSources(s.baseUrl, s.apiKey) }.getOrDefault(emptyList())
        navPrefs = loadLibraryNavPrefs(api, s.baseUrl, s.apiKey)
        existing?.let {
            hideFromNav = navPrefs.isHidden(it.id)
            hideFromHome = navPrefs.isHiddenFromHome(it.id)
        }
    }

    if (picking) {
        FolderPicker(session, api, initial = root.ifBlank { null }, onCancel = { picking = false }, onPick = { root = it; picking = false })
        return
    }

    val isLocal = type == "LOCAL"
    val canSave = name.isNotBlank() && (if (isLocal) root.isNotBlank() else sourceId.isNotBlank())

    /** Comma-separated field to a set, dropping blanks so a trailing comma adds no empty entry. */
    fun parseList(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun save() {
        val s = server ?: return
        error = null
        saving = true
        scope.launch {
            // Fields that don't apply to this library type are sent as null, which the server reads
            // as "leave unchanged" rather than writing a meaningless value.
            val refresh = RefreshSettingsDto(
                refreshInterval = refreshInterval,
                refreshOnStartup = refreshOnStartup,
                scanForceModifiedTime = forceModTime.takeIf { isLocal },
                scanCbx = scanCbx.takeIf { isLocal },
                scanPdf = scanPdf.takeIf { isLocal },
                scanEpub = scanEpub.takeIf { isLocal },
                scanDirectoryExclusions = parseList(exclusions).takeIf { isLocal },
                specialFolders = parseList(specialFolders).takeIf { isLocal },
                autoDownload = autoDownload.takeIf { !isLocal },
            )
            val result = runCatching {
                if (isEdit) {
                    api.updateLibrary(
                        s.baseUrl, s.apiKey, existing.id,
                        UpdateLibraryRequest(
                            name = name,
                            root = root.ifBlank { null },
                            contentSourceId = sourceId.ifBlank { null },
                            refresh = refresh,
                        ),
                    )
                    existing.id
                } else {
                    api.createLibrary(
                        s.baseUrl, s.apiKey,
                        CreateLibraryRequest(
                            name = name,
                            type = type,
                            mediaKind = mediaKind,
                            root = root.ifBlank { null },
                            contentSourceId = sourceId.ifBlank { null },
                            refresh = refresh,
                        ),
                    ).id
                }
            }
            result.fold(
                onSuccess = { id ->
                    // Visibility lives in a different store, and a new library only has an id now.
                    runCatching {
                        saveLibraryNavPrefs(
                            api, s.baseUrl, s.apiKey,
                            navPrefs.withHidden(id, hideFromNav).withHiddenFromHome(id, hideFromHome),
                        )
                    }
                    saving = false
                    onSaved()
                },
                onFailure = {
                    saving = false
                    error = it.friendlyMessage()
                    snackbar?.show("Couldn't save library.")
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit library" else "New library", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.size(12.dp))
            // Type and content kind decide the storage model, so the server won't let them change later.
            FormLabel("Type")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = isLocal, onClick = { type = "LOCAL" }, enabled = !isEdit, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Local folder") }
                SegmentedButton(selected = !isLocal, onClick = { type = "WEB" }, enabled = !isEdit, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Web source") }
            }

            Spacer(Modifier.size(12.dp))
            FormLabel("Content")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mediaKind == "COMIC", onClick = { mediaKind = "COMIC" }, enabled = !isEdit, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Comics") }
                SegmentedButton(selected = mediaKind == "BOOK", onClick = { mediaKind = "BOOK" }, enabled = !isEdit, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Books") }
            }
            if (isEdit) {
                Text(
                    "Type and content kind can't change after a library is created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(12.dp))
            if (isLocal) {
                OutlinedTextField(
                    root, { root = it }, label = { Text("Folder path") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { picking = true }) { Text("Browse") } },
                )
            } else {
                FormLabel("Source")
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(sources.firstOrNull { it.id == sourceId }?.displayName ?: "Choose a source")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        if (sources.isEmpty()) DropdownMenuItem(text = { Text("No sources installed") }, onClick = { open = false }, enabled = false)
                        sources.forEach { src -> DropdownMenuItem(text = { Text(src.displayName) }, onClick = { open = false; sourceId = src.id }) }
                    }
                }
            }

            Spacer(Modifier.size(20.dp))
            FormLabel("Visibility")
            ToggleRow("Hide from Libraries", "Keep it out of the Libraries tab", hideFromNav) { hideFromNav = it }
            ToggleRow("Hide from Home", "Keep its series out of Home's rows", hideFromHome) { hideFromHome = it }

            Spacer(Modifier.size(20.dp))
            FormLabel(if (isLocal) "Scanning" else "Updates")
            var intervalOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { intervalOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(REFRESH_INTERVALS.firstOrNull { it.first == refreshInterval }?.second ?: refreshInterval)
                }
                DropdownMenu(expanded = intervalOpen, onDismissRequest = { intervalOpen = false }) {
                    REFRESH_INTERVALS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { intervalOpen = false; refreshInterval = value })
                    }
                }
            }
            ToggleRow(
                if (isLocal) "Scan on startup" else "Update on startup",
                "Run once when the server starts",
                refreshOnStartup,
            ) { refreshOnStartup = it }

            if (!isLocal) {
                ToggleRow("Auto-download new chapters", "Download as soon as they're found", autoDownload) { autoDownload = it }
            }

            if (isLocal) {
                ToggleRow(
                    "Trust modified times",
                    "Skip files whose timestamp hasn't changed. Faster, but misses in-place edits",
                    forceModTime,
                ) { forceModTime = it }

                Spacer(Modifier.size(12.dp))
                FormLabel("File types to index")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = scanCbx, onClick = { scanCbx = !scanCbx }, label = { Text("CBZ/CBR") })
                    FilterChip(selected = scanPdf, onClick = { scanPdf = !scanPdf }, label = { Text("PDF") })
                    FilterChip(selected = scanEpub, onClick = { scanEpub = !scanEpub }, label = { Text("EPUB") })
                }

                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    exclusions, { exclusions = it },
                    label = { Text("Excluded folders") },
                    supportingText = { Text("Comma-separated folder names skipped during a scan.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    specialFolders, { specialFolders = it },
                    label = { Text("Special folders") },
                    supportingText = { Text("Comma-separated names treated as one-shots or specials.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                Spacer(Modifier.size(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.size(20.dp))
            Button(onClick = { save() }, enabled = canSave && !saving, modifier = Modifier.fillMaxWidth()) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (isEdit) "Save" else "Create library")
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Server-side directory browser (`GET /filesystem`) for choosing a LOCAL library root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPicker(session: SessionManager, api: KodexApi, initial: String?, onCancel: () -> Unit, onPick: (String) -> Unit) {
    val server by session.activeServer.collectAsStateSafe()
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var current by remember { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(current, server?.id) {
        val s = server ?: return@LaunchedEffect
        loading = true
        listing = runCatching { api.listDirectory(s.baseUrl, s.apiKey, current) }.getOrNull()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.path?.ifBlank { "Choose folder" } ?: "Choose folder", fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { listing?.path?.let { onPick(it) } }, enabled = listing?.path?.isNotBlank() == true, modifier = Modifier.weight(1f)) { Text("Use this folder") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading && listing == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                val l = listing
                LazyColumn(Modifier.fillMaxSize()) {
                    if (l?.parent != null) {
                        item {
                            Text("⬆  Up", Modifier.fillMaxWidth().clickable { current = l.parent }.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    items(l?.directories ?: emptyList(), key = { it.path }) { dir ->
                        Text("📁  ${dir.name}", Modifier.fillMaxWidth().clickable { current = dir.path }.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}
