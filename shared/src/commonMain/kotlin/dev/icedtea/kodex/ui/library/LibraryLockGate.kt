package dev.icedtea.kodex.ui.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icedtea.kodex.data.LocalLibraryLock
import dev.icedtea.kodex.data.isLocked
import dev.icedtea.kodex.network.PinFailure
import dev.icedtea.kodex.network.pinFailure
import dev.icedtea.kodex.platform.nowMillis
import dev.icedtea.kodex.ui.collectAsStateSafe
import dev.icedtea.kodex.ui.friendlyMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows [content] unless it belongs to a PIN-locked library this device hasn't opened — then a PIN prompt
 * that opens just that library. [resolveLibraryId] finds the library (e.g. by loading the series or book); it only runs
 * when the user has a lock with at least one library, so unlocked users pay nothing.
 */
@Composable
fun LibraryLockGate(
    key: Any?,
    resolveLibraryId: suspend () -> String?,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val lock = LocalLibraryLock.current
    val status by lock.status.collectAsStateSafe()
    val token by lock.token.collectAsStateSafe()
    val guarded = status?.enabled == true && status?.lockedLibraryIds?.isNotEmpty() == true

    var resolved by remember(key) { mutableStateOf(false) }
    var libraryId by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key, guarded) {
        if (guarded && !resolved) {
            libraryId = runCatching { resolveLibraryId() }.getOrNull()
            resolved = true
        }
    }

    val locked = guarded && isLocked(libraryId, status, token)
    when {
        // Still finding out which library this is: don't flash the content. (An unknown status — not
        // loaded yet, or the server unreachable — shows the content; the gate closes once it arrives.)
        guarded && !resolved -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        locked -> LibraryUnlockScreen(onBack, libraryId)
        else -> content()
    }
}

@Composable
fun LibraryUnlockScreen(onBack: () -> Unit, libraryId: String?) {
    val lock = LocalLibraryLock.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Library locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter your PIN to open this library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PinEntry(Modifier.widthIn(max = 320.dp).fillMaxWidth(), submit = { lock.unlock(it, libraryId) })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Go back") }
        }
    }
}

/**
 * The app lock screen: covers the whole app until the PIN is entered (see [dev.icedtea.kodex.data.LibraryLockStore.appLocked]).
 * It swallows touches and Back so nothing underneath reacts; [onSignOut] is the way out for someone who
 * forgot the PIN: it leaves the server, and signing back in comes up locked again.
 */
@Composable
fun AppLockScreen(onSignOut: () -> Unit) {
    val lock = LocalLibraryLock.current
    dev.icedtea.kodex.platform.AppBackHandler(enabled = true) { }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Kodex is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter your PIN to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PinEntry(Modifier.widthIn(max = 320.dp).fillMaxWidth(), submit = { lock.unlockApp(it) })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

/** Masked 4–8 digit PIN field + action button. [submit] throwing a PIN error shows it inline. */
@Composable
fun PinEntry(modifier: Modifier = Modifier, label: String = "PIN", action: String = "Unlock", submit: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(waitUntil) {
        while (nowMillis() < waitUntil) {
            now = nowMillis()
            delay(500)
        }
        now = nowMillis()
    }
    val waitSeconds = ((waitUntil - now + 999) / 1000).coerceAtLeast(0)
    val valid = pin.length in 4..8 && waitSeconds == 0L

    fun go() {
        if (!valid || busy) return
        busy = true
        error = null
        val entered = pin
        scope.launch {
            runCatching { submit(entered) }.onFailure { e ->
                error = when (val f = e.pinFailure()) {
                    is PinFailure.Wrong -> if (f.remainingAttempts != null && f.remainingAttempts <= 3) {
                        "Wrong PIN — ${f.remainingAttempts} attempts left before a wait."
                    } else "Wrong PIN."
                    is PinFailure.Throttled -> {
                        waitUntil = nowMillis() + f.retryAfterSeconds * 1000
                        "Too many wrong PINs. Try again shortly."
                    }
                    null -> e.friendlyMessage()
                }
            }
            pin = ""
            busy = false
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter(Char::isDigit).take(8) },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { go() }),
            isError = error != null,
            supportingText = error?.let { msg -> { Text(if (waitSeconds > 0) "$msg (${waitSeconds}s)" else msg) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { go() }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(action)
        }
    }
}
